package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository.StateCheckpointRecord;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateReductionReceipt;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 持久化有状态图执行引擎接口与生产实现 (DurableStateGraphEngine)
 * 基于时序逻辑前置条件守卫 (Lemma 2)、半群代数并发状态规约器 (StateChannelReducer) 与 Bulk Synchronous Parallel (BSP) 超步推进，
 * 彻底消除虚拟线程并发写入丢失更新 (Lost Updates)，支持动态分支派发 (Dynamic Send) 与无损检查点自愈。
 *
 * @author Achilles
 * @since 2026-10-13
 */
public interface DurableStateGraphEngine {

    /**
     * 前置条件守卫接口 (Precondition Guard)
     */
    @FunctionalInterface
    interface PreconditionGuard {
        boolean verifyPreconditions(Map<String, Object> currentState);
    }

    /**
     * 超步执行产物 (纯 Java 21 Record)
     */
    record SuperstepExecutionResult(
            boolean completed,
            int executedSuperstep,
            Map<String, Object> mutatedState,
            List<String> nextActiveNodes,
            String errorCode,
            String message
    ) implements Serializable {

        public SuperstepExecutionResult {
            mutatedState = Map.copyOf(mutatedState != null ? mutatedState : Map.of());
            nextActiveNodes = List.copyOf(nextActiveNodes != null ? nextActiveNodes : List.of());
        }

        public static SuperstepExecutionResult success(int superstep, Map<String, Object> state, List<String> nextNodes) {
            return new SuperstepExecutionResult(false, superstep, state, nextNodes, null, "超步执行成功");
        }

        public static SuperstepExecutionResult finished(int superstep, Map<String, Object> state) {
            return new SuperstepExecutionResult(true, superstep, state, List.of(), null, "状态图执行达成终态");
        }

        public static SuperstepExecutionResult guardBlocked(int superstep, Map<String, Object> state, String node, String reason) {
            return new SuperstepExecutionResult(false, superstep, state, List.of(node), "ERR_PRECONDITION_GUARD_BLOCKED", reason);
        }
    }

    /**
     * 注册带前置条件守卫的节点动作
     */
    void registerGuardedNode(
            String nodeId,
            PreconditionGuard guard,
            Function<Map<String, Object>, Map<String, Object>> nodeAction,
            List<String> defaultOutboundNodes
    );

    /**
     * 注册返回信封包装的高阶节点动作（支持动态 Send 与增量 Delta）
     */
    void registerEnvelopeNode(
            String nodeId,
            PreconditionGuard guard,
            Function<Map<String, Object>, NodeExecutionEnvelope> envelopeAction,
            List<String> defaultOutboundNodes
    );

    /**
     * 步进执行一个超步 (BSP 屏障推进与半群代数状态归约)
     */
    SuperstepExecutionResult advanceSuperstep(String executionId, String branchId);

    /**
     * 初始化状态图会话并注入初始状态与初始活动节点
     */
    void initializeExecution(String executionId, String branchId, Map<String, Object> initialState, List<String> startNodes);

    /**
     * 故障中断恢复并从最后有效快照继续执行 (Crash-Recovery Replay)
     */
    SuperstepExecutionResult resumeFromLatestCheckpoint(String executionId, String branchId);

    /**
     * 配置全图状态模式 Schema
     */
    void setStateGraphSchema(StateGraphSchema schema);

    /**
     * 获取指定分支最近一次超步的状态规约凭单
     */
    Optional<StateReductionReceipt> getLastReductionReceipt(String executionId, String branchId);

    /**
     * 生产级有状态图引擎实现
     */
    @Component
    class DefaultDurableStateGraphEngine implements DurableStateGraphEngine {

        private static final Logger log = LoggerFactory.getLogger(DefaultDurableStateGraphEngine.class);

        private final StateCheckpointRepository checkpointRepository;
        private volatile StateGraphSchema schema;
        private volatile SuperstepStateReducerGovernor reducerGovernor;

        private record NodeDefinition(
                String nodeId,
                PreconditionGuard guard,
                Function<Map<String, Object>, NodeExecutionEnvelope> action,
                List<String> outboundNodes
        ) {}

        private final Map<String, NodeDefinition> registeredNodes = new ConcurrentHashMap<>();
        // executionId:branchId -> 当前活跃节点列表
        private final Map<String, List<String>> activeNodesByBranch = new ConcurrentHashMap<>();
        // executionId:branchId -> 当前状态快照
        private final Map<String, Map<String, Object>> liveStateByBranch = new ConcurrentHashMap<>();
        // executionId:branchId -> 超步计数
        private final Map<String, AtomicInteger> superstepCounters = new ConcurrentHashMap<>();
        // executionId:branchId -> 最近规约凭单
        private final Map<String, StateReductionReceipt> lastReceiptsByBranch = new ConcurrentHashMap<>();
        // 单调自增 Fencing Token 生成器
        private final AtomicLong fencingTokenGenerator = new AtomicLong(System.currentTimeMillis() * 1000L);

        public DefaultDurableStateGraphEngine(StateCheckpointRepository checkpointRepository) {
            this.checkpointRepository = checkpointRepository;
            this.schema = StateGraphSchema.builder().build();
            this.reducerGovernor = new SuperstepStateReducerGovernor(this.schema);
        }

        @Override
        public void setStateGraphSchema(StateGraphSchema schema) {
            this.schema = Objects.requireNonNull(schema, "schema 不能为空");
            this.reducerGovernor = new SuperstepStateReducerGovernor(schema);
        }

        @Override
        public Optional<StateReductionReceipt> getLastReductionReceipt(String executionId, String branchId) {
            String key = buildBranchKey(executionId, branchId);
            return Optional.ofNullable(lastReceiptsByBranch.get(key));
        }

        @Override
        public void registerGuardedNode(
                String nodeId,
                PreconditionGuard guard,
                Function<Map<String, Object>, Map<String, Object>> nodeAction,
                List<String> defaultOutboundNodes
        ) {
            Objects.requireNonNull(nodeId, "nodeId 不能为空");
            Objects.requireNonNull(nodeAction, "nodeAction 不能为空");
            Function<Map<String, Object>, NodeExecutionEnvelope> wrapper = state -> {
                Map<String, Object> output = nodeAction.apply(state);
                return NodeExecutionEnvelope.of(nodeId, output != null ? output : Map.of());
            };
            registerEnvelopeNode(nodeId, guard, wrapper, defaultOutboundNodes);
        }

        @Override
        public void registerEnvelopeNode(
                String nodeId,
                PreconditionGuard guard,
                Function<Map<String, Object>, NodeExecutionEnvelope> envelopeAction,
                List<String> defaultOutboundNodes
        ) {
            Objects.requireNonNull(nodeId, "nodeId 不能为空");
            Objects.requireNonNull(envelopeAction, "envelopeAction 不能为空");
            registeredNodes.put(nodeId, new NodeDefinition(
                    nodeId,
                    guard != null ? guard : state -> true,
                    envelopeAction,
                    defaultOutboundNodes != null ? defaultOutboundNodes : List.of()
            ));
            log.info("【状态图引擎】成功注册规约支持节点: nodeId={}, outbound={}", nodeId, defaultOutboundNodes);
        }

        @Override
        public void initializeExecution(
                String executionId,
                String branchId,
                Map<String, Object> initialState,
                List<String> startNodes
        ) {
            String key = buildBranchKey(executionId, branchId);
            superstepCounters.put(key, new AtomicInteger(0));
            liveStateByBranch.put(key, new ConcurrentHashMap<>(initialState != null ? initialState : Map.of()));
            activeNodesByBranch.put(key, new CopyOnWriteArrayList<>(startNodes != null ? startNodes : List.of()));

            // 保存第 0 超步快照
            StateCheckpointRecord initialRecord = StateCheckpointRecord.create(
                    "ckpt-" + UUID.randomUUID().toString().substring(0, 8),
                    executionId,
                    branchId,
                    0,
                    liveStateByBranch.get(key),
                    activeNodesByBranch.get(key),
                    1L
            );
            checkpointRepository.saveWithFencing(initialRecord);
        }

        @Override
        public SuperstepExecutionResult advanceSuperstep(String executionId, String branchId) {
            String key = buildBranchKey(executionId, branchId);
            AtomicInteger counter = superstepCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
            int currentStep = counter.incrementAndGet();

            List<String> activeNodes = activeNodesByBranch.get(key);
            if (activeNodes == null || activeNodes.isEmpty()) {
                log.info("【状态图引擎】无活跃节点，状态图顺利达成终态: branch={}, step={}", key, currentStep);
                return SuperstepExecutionResult.finished(currentStep, liveStateByBranch.getOrDefault(key, Map.of()));
            }

            Map<String, Object> currentState = liveStateByBranch.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
            List<String> nextActiveNodes = new CopyOnWriteArrayList<>();
            List<NodeExecutionEnvelope> envelopes = new CopyOnWriteArrayList<>();

            // 采用 Java 21 虚拟线程并发执行当前超步所有活跃节点
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Void>> futures = new ArrayList<>();

                for (String nodeId : activeNodes) {
                    NodeDefinition nodeDef = registeredNodes.get(nodeId);
                    if (nodeDef == null) {
                        log.warn("【状态图引擎】未找到节点定义，跳过: nodeId={}", nodeId);
                        continue;
                    }

                    futures.add(executor.submit(() -> {
                        // 1. 前置条件守卫校验 (Lemma 2)
                        boolean guardPassed = nodeDef.guard().verifyPreconditions(currentState);
                        if (!guardPassed) {
                            log.warn("【状态图引擎】节点前置条件守卫未通过，阻断执行: nodeId={}", nodeId);
                            nextActiveNodes.add(nodeId);
                            currentState.put("lastBlockedNode", nodeId);
                            currentState.put("lastBlockedReason", "前置槽位或指标未就绪");
                            return null;
                        }

                        // 2. 执行节点动作
                        NodeExecutionEnvelope envelope = nodeDef.action().apply(currentState);
                        if (envelope != null) {
                            envelopes.add(envelope);
                            // 收集动态派发路由
                            if (envelope.dynamicSends() != null) {
                                for (DynamicSendPacket send : envelope.dynamicSends()) {
                                    nextActiveNodes.add(send.targetNodeId());
                                }
                            }
                        }

                        // 3. 收集静态拓扑后续流转节点
                        nextActiveNodes.addAll(nodeDef.outboundNodes());
                        return null;
                    }));
                }

                for (var future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.error("【状态图引擎】超步执行并发异常: branch={}, step={}", key, currentStep, e);
                return new SuperstepExecutionResult(false, currentStep, currentState, activeNodes, "ERR_SUPERSTEP_EXECUTION", e.getMessage());
            }

            // 4. 超步屏障原子规约治理 (解决 putAll 并发覆盖，实现半群强汇聚)
            long fencingToken = fencingTokenGenerator.incrementAndGet();
            SuperstepStateReducerGovernor.ReductionOutcome outcome = reducerGovernor.reduceSuperstep(
                    executionId, currentStep, currentState, envelopes, fencingToken);

            // 更新状态为规约收敛后的不可变态
            currentState.clear();
            currentState.putAll(outcome.reducedState());
            lastReceiptsByBranch.put(key, outcome.receipt());

            // 更新下一轮活跃节点列表（去重）
            List<String> distinctNextNodes = new ArrayList<>(new LinkedHashSet<>(nextActiveNodes));
            activeNodesByBranch.put(key, distinctNextNodes);

            // 保存持久化不可变快照 (CAS Fencing Token 写屏障)
            StateCheckpointRecord record = StateCheckpointRecord.create(
                    "ckpt-" + UUID.randomUUID().toString().substring(0, 8),
                    executionId,
                    branchId,
                    currentStep,
                    currentState,
                    distinctNextNodes,
                    fencingToken
            );
            checkpointRepository.saveWithFencing(record);

            if (distinctNextNodes.isEmpty()) {
                log.info("【状态图引擎】全部节点执行完毕，进入终态: branch={}, step={}, conflicts={}",
                        key, currentStep, outcome.receipt().conflictCount());
                return SuperstepExecutionResult.finished(currentStep, currentState);
            }

            return SuperstepExecutionResult.success(currentStep, currentState, distinctNextNodes);
        }

        @Override
        public SuperstepExecutionResult resumeFromLatestCheckpoint(String executionId, String branchId) {
            String key = buildBranchKey(executionId, branchId);
            Optional<StateCheckpointRecord> latestOpt = checkpointRepository.getLatestCheckpoint(executionId, branchId);
            if (latestOpt.isEmpty()) {
                log.warn("【状态图引擎】未找到可恢复的历史快照: branch={}", key);
                return new SuperstepExecutionResult(false, 0, Map.of(), List.of(), "ERR_NO_CHECKPOINT", "无有效快照");
            }

            StateCheckpointRecord record = latestOpt.get();
            superstepCounters.put(key, new AtomicInteger(record.superstep()));
            liveStateByBranch.put(key, new ConcurrentHashMap<>(record.fullStateSnapshot()));
            activeNodesByBranch.put(key, new CopyOnWriteArrayList<>(record.pendingNodes()));

            log.info("【状态图引擎】从最后有效快照无损重放恢复成功: branch={}, superstep={}, pendingNodes={}",
                    key, record.superstep(), record.pendingNodes());

            return SuperstepExecutionResult.success(record.superstep(), record.fullStateSnapshot(), record.pendingNodes());
        }

        private String buildBranchKey(String executionId, String branchId) {
            return executionId + ":" + (branchId != null ? branchId : "main");
        }
    }
}
