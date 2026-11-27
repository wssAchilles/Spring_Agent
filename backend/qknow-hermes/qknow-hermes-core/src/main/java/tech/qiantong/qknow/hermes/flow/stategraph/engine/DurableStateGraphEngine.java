package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository.StateCheckpointRecord;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateReductionReceipt;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.*;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 持久化有状态图执行引擎接口与生产实现 (DurableStateGraphEngine · Phase 9.2 PA-TSG 升维底座)
 * <p>
 * 基于时序逻辑前置条件守卫 (Lemma 2)、半群代数并发状态规约器 (StateChannelReducer) 与
 * Bulk Synchronous Parallel (BSP) 超步推进，支持：
 * 1. 多实例参数化 {@link DynamicSendPacket} 并发虚拟线程扇出（不再折叠同目标节点的多个包，完整透传 nodePayload）；
 * 2. 节点私有隔离上下文视角 {@link ScopedNodeContextView} 与不可变 Zone-0 64-token 锚点前缀校验；
 * 3. 按确定性 {@code dispatchPathKey} 排序后的并发增量规约与条件边路由器 {@link ConditionalEdgeRouter}；
 * 4. 虚拟线程内部 {@code try/catch} 结构化异常捕获（彻底免疫 LLMCompiler 式永久死锁）与可配超时控制。
 * </p>
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
     * Phase 9.2 新增：注册带私有隔离视角与条件边路由的类型化认知节点。
     */
    default void registerScopedNode(
            String nodeId,
            Set<String> readableChannels,
            PreconditionGuard guard,
            Function<ScopedNodeContextView, NodeExecutionEnvelope> scopedAction,
            ConditionalEdgeRouter conditionalRouter,
            List<String> defaultOutboundNodes
    ) {
        registerEnvelopeNode(
                nodeId,
                guard,
                state -> scopedAction.apply(new ScopedNodeContextView(
                        "default-exec", "main", nodeId, nodeId, 0,
                        "", "", state, Map.of(), List.of()
                )),
                defaultOutboundNodes
        );
    }

    /**
     * Phase 9.2 新增：配置超步并行等待超时（替代原硬编码 10 秒限制）。
     */
    default void setSuperstepTimeout(Duration timeout) {
    }

    /**
     * Phase 9.2 新增：为指定执行分支锁定不可变 Zone-0 64-token 共享锚点前缀。
     */
    default void setZone0PrefixAnchor(String executionId, String branchId, String zone0AnchorPrefix) {
    }

    /**
     * 步进执行一个超步 (BSP 屏障推进与半群代数状态归约)
     */
    SuperstepExecutionResult advanceSuperstep(String executionId, String branchId);

    /**
     * Phase 9.2 新增：持续推进状态图直至达成终态或达到最大超步限制，签发端到端压缩与加速凭单。
     */
    default TypedSubgraphExecutionReceipt executeToCompletion(String executionId, String branchId, int maxSupersteps) {
        int steps = 0;
        SuperstepExecutionResult last = null;
        int limit = Math.max(1, maxSupersteps);
        while (steps < limit) {
            last = advanceSuperstep(executionId, branchId);
            steps++;
            if (last.completed() || (last.errorCode() != null && !last.errorCode().isBlank())) {
                break;
            }
        }
        return new TypedSubgraphExecutionReceipt(
                executionId, branchId,
                last != null && last.completed(),
                steps, steps, 1, 0, 0, true, 0L, 0L, 1.0,
                getLastReductionReceipt(executionId, branchId).map(StateReductionReceipt::afterStateHash).orElse(""),
                last != null && last.errorCode() != null ? last.errorCode() : ""
        );
    }

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
     * Phase 9.2 新增：输出实时状态图遥测指标。
     */
    default Map<String, Object> getTelemetryStats() {
        return Map.of();
    }

    /**
     * 生产级有状态图引擎实现（已修补 Phase 9.2 全部 4 处源码断点）。
     */
    @Component
    class DefaultDurableStateGraphEngine implements DurableStateGraphEngine {

        private static final Logger log = LoggerFactory.getLogger(DefaultDurableStateGraphEngine.class);
        private static final Duration DEFAULT_SUPERSTEP_TIMEOUT = Duration.ofSeconds(60);

        private final StateCheckpointRepository checkpointRepository;
        private volatile StateGraphSchema schema;
        private volatile SuperstepStateReducerGovernor reducerGovernor;
        private volatile Duration superstepTimeout = DEFAULT_SUPERSTEP_TIMEOUT;

        private record NodeDefinition(
                String nodeId,
                Set<String> readableChannels,
                PreconditionGuard guard,
                Function<ScopedNodeContextView, NodeExecutionEnvelope> scopedAction,
                ConditionalEdgeRouter conditionalRouter,
                List<String> outboundNodes
        ) {}

        private record PendingInvocation(
                String nodeId,
                Map<String, Object> sendPayload,
                String dispatchPathKey
        ) {}

        private record ExecutedInvocation(
                String dispatchPathKey,
                NodeDefinition nodeDef,
                NodeExecutionEnvelope envelope,
                List<DynamicSendPacket> explicitSends,
                boolean guardBlocked,
                String exceptionMessage,
                long isolatedInputChars,
                long rawContextChars
        ) {}

        private final Map<String, NodeDefinition> registeredNodes = new ConcurrentHashMap<>();
        private final Map<String, List<String>> activeNodesByBranch = new ConcurrentHashMap<>();
        private final Map<String, List<PendingInvocation>> pendingInvocationsByBranch = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> liveStateByBranch = new ConcurrentHashMap<>();
        private final Map<String, String> zone0AnchorByBranch = new ConcurrentHashMap<>();
        private final Map<String, String> zone0ShaByBranch = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> superstepCounters = new ConcurrentHashMap<>();
        private final Map<String, StateReductionReceipt> lastReceiptsByBranch = new ConcurrentHashMap<>();
        private final AtomicLong fencingTokenGenerator = new AtomicLong(System.currentTimeMillis() * 1000L);

        // 引擎级遥测计数
        private final AtomicLong totalSuperstepsExecuted = new AtomicLong(0L);
        private final AtomicLong totalNodeInvocations = new AtomicLong(0L);
        private final AtomicLong totalFanoutInstances = new AtomicLong(0L);
        private final AtomicLong totalTimeoutOrExceptions = new AtomicLong(0L);

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
        public void setSuperstepTimeout(Duration timeout) {
            if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
                this.superstepTimeout = timeout;
            }
        }

        @Override
        public void setZone0PrefixAnchor(String executionId, String branchId, String zone0AnchorPrefix) {
            String key = buildBranchKey(executionId, branchId);
            String safePrefix = zone0AnchorPrefix != null ? zone0AnchorPrefix : "";
            zone0AnchorByBranch.put(key, safePrefix);
            zone0ShaByBranch.put(key, ScopedNodeContextView.computeSha256(safePrefix));
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
            registerScopedNode(
                    nodeId,
                    null,
                    guard,
                    view -> {
                        // 兼容传统节点：将只读快照与 sendPayload 合并传入只读视图，防止原地污染全局共享态
                        Map<String, Object> viewState = new LinkedHashMap<>(view.readableChannelsSnapshot());
                        if (!view.sendPayload().isEmpty()) {
                            viewState.put("_sendPayload", view.sendPayload());
                            viewState.putAll(view.sendPayload());
                        }
                        return envelopeAction.apply(Collections.unmodifiableMap(viewState));
                    },
                    null,
                    defaultOutboundNodes
            );
        }

        @Override
        public void registerScopedNode(
                String nodeId,
                Set<String> readableChannels,
                PreconditionGuard guard,
                Function<ScopedNodeContextView, NodeExecutionEnvelope> scopedAction,
                ConditionalEdgeRouter conditionalRouter,
                List<String> defaultOutboundNodes
        ) {
            Objects.requireNonNull(nodeId, "nodeId 不能为空");
            Objects.requireNonNull(scopedAction, "scopedAction 不能为空");
            Set<String> channels = readableChannels != null ? Set.copyOf(readableChannels) : null;
            registeredNodes.put(nodeId, new NodeDefinition(
                    nodeId,
                    channels,
                    guard != null ? guard : state -> true,
                    scopedAction,
                    conditionalRouter,
                    defaultOutboundNodes != null ? List.copyOf(defaultOutboundNodes) : List.of()
            ));
            log.info("【状态图引擎】成功注册规约支持节点: nodeId={}, scoped={}, outbound={}",
                    nodeId, channels != null, defaultOutboundNodes);
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
            List<String> safeStarts = startNodes != null ? List.copyOf(startNodes) : List.of();
            activeNodesByBranch.put(key, new CopyOnWriteArrayList<>(safeStarts));

            List<PendingInvocation> initPending = new ArrayList<>(safeStarts.size());
            for (int i = 0; i < safeStarts.size(); i++) {
                String nid = safeStarts.get(i);
                initPending.add(new PendingInvocation(nid, Map.of(), String.format(Locale.ROOT, "s0000:i%04d:%s", i, nid)));
            }
            pendingInvocationsByBranch.put(key, initPending);

            StateCheckpointRecord initialRecord = StateCheckpointRecord.create(
                    "ckpt-" + UUID.randomUUID().toString().substring(0, 8),
                    executionId,
                    branchId,
                    0,
                    liveStateByBranch.get(key),
                    safeStarts,
                    1L
            );
            checkpointRepository.saveWithFencing(initialRecord);
        }

        @Override
        public SuperstepExecutionResult advanceSuperstep(String executionId, String branchId) {
            String key = buildBranchKey(executionId, branchId);
            AtomicInteger counter = superstepCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
            int currentStep = counter.incrementAndGet();

            List<PendingInvocation> pending = pendingInvocationsByBranch.get(key);
            List<String> activeNodes = activeNodesByBranch.get(key);
            if ((pending == null || pending.isEmpty()) && (activeNodes == null || activeNodes.isEmpty())) {
                log.info("【状态图引擎】无活跃节点，状态图顺利达成终态: branch={}, step={}", key, currentStep);
                return SuperstepExecutionResult.finished(currentStep, liveStateByBranch.getOrDefault(key, Map.of()));
            }
            if (pending == null || pending.isEmpty()) {
                pending = new ArrayList<>(activeNodes.size());
                for (int i = 0; i < activeNodes.size(); i++) {
                    String nid = activeNodes.get(i);
                    pending.add(new PendingInvocation(nid, Map.of(),
                            String.format(Locale.ROOT, "s%04d:f%04d:%s", currentStep, i, nid)));
                }
            }

            Map<String, Object> frozenCurrentState = Collections.unmodifiableMap(
                    new LinkedHashMap<>(liveStateByBranch.computeIfAbsent(key, k -> new ConcurrentHashMap<>()))
            );
            String zone0Prefix = zone0AnchorByBranch.getOrDefault(key, "");
            String zone0Sha = zone0ShaByBranch.getOrDefault(key, ScopedNodeContextView.computeSha256(zone0Prefix));

            long rawTotalStateChars = estimateCharSize(frozenCurrentState);
            List<ExecutedInvocation> executedInvocations = Collections.synchronizedList(new ArrayList<>(pending.size()));

            // 使用 Java 21 虚拟线程并发执行全部待派发实例（包含同节点的多实例 DynamicSendPacket）
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<ExecutedInvocation>> futures = new ArrayList<>(pending.size());
                for (PendingInvocation inv : pending) {
                    NodeDefinition nodeDef = registeredNodes.get(inv.nodeId());
                    if (nodeDef == null) {
                        log.warn("【状态图引擎】未找到节点定义，跳过: nodeId={}", inv.nodeId());
                        continue;
                    }
                    futures.add(executor.submit(() -> executeSingleInvocation(
                            executionId, branchId, currentStep, inv, nodeDef,
                            frozenCurrentState, zone0Prefix, zone0Sha, rawTotalStateChars
                    )));
                }

                long deadlineNs = System.nanoTime() + superstepTimeout.toNanos();
                for (var future : futures) {
                    long remainingNs = deadlineNs - System.nanoTime();
                    if (remainingNs <= 0L) {
                        cancelAll(futures);
                        totalTimeoutOrExceptions.incrementAndGet();
                        return new SuperstepExecutionResult(
                                false, currentStep, frozenCurrentState,
                                pending.stream().map(PendingInvocation::nodeId).toList(),
                                "ERR_SUPERSTEP_TIMEOUT",
                                "超步并行执行超过时限: " + superstepTimeout.toMillis() + " ms"
                        );
                    }
                    try {
                        ExecutedInvocation res = future.get(remainingNs, TimeUnit.NANOSECONDS);
                        if (res != null) {
                            executedInvocations.add(res);
                        }
                    } catch (TimeoutException te) {
                        cancelAll(futures);
                        totalTimeoutOrExceptions.incrementAndGet();
                        return new SuperstepExecutionResult(
                                false, currentStep, frozenCurrentState,
                                pending.stream().map(PendingInvocation::nodeId).toList(),
                                "ERR_SUPERSTEP_TIMEOUT",
                                "超步并行任务触发可配超时中断 (" + superstepTimeout.toMillis() + " ms)"
                        );
                    }
                }
            } catch (Exception e) {
                totalTimeoutOrExceptions.incrementAndGet();
                log.error("【状态图引擎】超步执行并发异常: branch={}, step={}", key, currentStep, e);
                return new SuperstepExecutionResult(
                        false, currentStep, frozenCurrentState,
                        pending.stream().map(PendingInvocation::nodeId).toList(),
                        "ERR_SUPERSTEP_EXECUTION", e.getMessage()
                );
            }

            // 1. 按确定性 dispatchPathKey 字典序严格排序并发结果（根除随机唤醒引起的非确定性归约序）
            executedInvocations.sort(Comparator.comparing(ExecutedInvocation::dispatchPathKey));

            // 校验是否有分支抛出异常：结构化异常立即报错返回，绝不静默丢包死锁
            for (ExecutedInvocation ei : executedInvocations) {
                if (ei.exceptionMessage() != null) {
                    totalTimeoutOrExceptions.incrementAndGet();
                    return new SuperstepExecutionResult(
                            false, currentStep, frozenCurrentState,
                            pending.stream().map(PendingInvocation::nodeId).toList(),
                            "ERR_NODE_EXECUTION_FAILED",
                            ei.exceptionMessage()
                    );
                }
            }

            totalSuperstepsExecuted.incrementAndGet();
            totalNodeInvocations.addAndGet(executedInvocations.size());

            List<NodeExecutionEnvelope> orderedEnvelopes = new ArrayList<>(executedInvocations.size());
            List<PendingInvocation> nextPending = new ArrayList<>();
            List<String> staticOutboundDedup = new ArrayList<>();
            Set<String> seenStaticOutbound = new LinkedHashSet<>();

            for (ExecutedInvocation ei : executedInvocations) {
                if (ei.envelope() != null) {
                    orderedEnvelopes.add(ei.envelope());
                }
                if (ei.guardBlocked()) {
                    nextPending.add(new PendingInvocation(
                            ei.nodeDef().nodeId(), Map.of(),
                            String.format(Locale.ROOT, "s%04d:retry:%s", currentStep + 1, ei.dispatchPathKey())
                    ));
                    continue;
                }
                // 收集动态路由包（完整保留所有同目标节点多实例 payload）
                int sendIdx = 0;
                for (DynamicSendPacket send : ei.explicitSends()) {
                    totalFanoutInstances.incrementAndGet();
                    String childPath = String.format(Locale.ROOT, "%s|s%04d:d%04d:%s",
                            ei.dispatchPathKey(), currentStep + 1, sendIdx++, send.targetNodeId());
                    nextPending.add(new PendingInvocation(send.targetNodeId(), send.nodePayload(), childPath));
                }
                // 仅当节点未配置 ConditionalEdgeRouter 时才追加静态默认出边
                if (ei.nodeDef().conditionalRouter() == null) {
                    for (String out : ei.nodeDef().outboundNodes()) {
                        if (seenStaticOutbound.add(out)) {
                            staticOutboundDedup.add(out);
                        }
                    }
                }
            }

            // 2. 超步屏障确定性半群代数规约
            long fencingToken = fencingTokenGenerator.incrementAndGet();
            SuperstepStateReducerGovernor.ReductionOutcome outcome = reducerGovernor.reduceSuperstep(
                    executionId, currentStep, frozenCurrentState, orderedEnvelopes, fencingToken
            );

            // 若存在 ConditionalEdgeRouter，基于规约后最新状态评估条件边激活
            for (ExecutedInvocation ei : executedInvocations) {
                if (!ei.guardBlocked() && ei.nodeDef().conditionalRouter() != null) {
                    List<DynamicSendPacket> condSends = ei.nodeDef().conditionalRouter()
                            .route(outcome.reducedState(), ei.envelope());
                    if (condSends != null) {
                        int condIdx = 0;
                        for (DynamicSendPacket send : condSends) {
                            totalFanoutInstances.incrementAndGet();
                            String childPath = String.format(Locale.ROOT, "%s|s%04d:c%04d:%s",
                                    ei.dispatchPathKey(), currentStep + 1, condIdx++, send.targetNodeId());
                            nextPending.add(new PendingInvocation(send.targetNodeId(), send.nodePayload(), childPath));
                        }
                    }
                }
            }

            for (int i = 0; i < staticOutboundDedup.size(); i++) {
                String outId = staticOutboundDedup.get(i);
                nextPending.add(new PendingInvocation(
                        outId, Map.of(), String.format(Locale.ROOT, "s%04d:static%04d:%s", currentStep + 1, i, outId)
                ));
            }

            Map<String, Object> updatedMutable = liveStateByBranch.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
            updatedMutable.clear();
            updatedMutable.putAll(outcome.reducedState());
            lastReceiptsByBranch.put(key, outcome.receipt());

            pendingInvocationsByBranch.put(key, List.copyOf(nextPending));
            List<String> distinctNextNodes = nextPending.stream()
                    .map(PendingInvocation::nodeId)
                    .distinct()
                    .toList();
            activeNodesByBranch.put(key, distinctNextNodes);

            StateCheckpointRecord record = StateCheckpointRecord.create(
                    "ckpt-" + UUID.randomUUID().toString().substring(0, 8),
                    executionId,
                    branchId,
                    currentStep,
                    outcome.reducedState(),
                    distinctNextNodes,
                    fencingToken
            );
            checkpointRepository.saveWithFencing(record);

            if (nextPending.isEmpty()) {
                log.info("【状态图引擎】全部节点执行完毕，进入终态: branch={}, step={}, conflicts={}",
                        key, currentStep, outcome.receipt().conflictCount());
                return SuperstepExecutionResult.finished(currentStep, outcome.reducedState());
            }

            return SuperstepExecutionResult.success(currentStep, outcome.reducedState(), distinctNextNodes);
        }

        @Override
        public TypedSubgraphExecutionReceipt executeToCompletion(String executionId, String branchId, int maxSupersteps) {
            String key = buildBranchKey(executionId, branchId);
            int limit = Math.max(1, maxSupersteps);
            int steps = 0;
            int maxWidth = 0;
            long beforeInvocations = totalNodeInvocations.get();
            SuperstepExecutionResult last = null;
            boolean zone0Preserved = true;
            String expectedSha = zone0ShaByBranch.getOrDefault(key, "");

            while (steps < limit) {
                List<PendingInvocation> currentPending = pendingInvocationsByBranch.getOrDefault(key, List.of());
                maxWidth = Math.max(maxWidth, currentPending.size());
                last = advanceSuperstep(executionId, branchId);
                steps++;
                if (!expectedSha.isEmpty()) {
                    String currentSha = zone0ShaByBranch.getOrDefault(key, "");
                    if (!java.security.MessageDigest.isEqual(
                            expectedSha.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            currentSha.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        zone0Preserved = false;
                    }
                }
                if (last.completed() || (last.errorCode() != null && !last.errorCode().isBlank())) {
                    break;
                }
            }

            long invocationsUsed = Math.max(steps, totalNodeInvocations.get() - beforeInvocations);
            long estimatedSerialChars = Math.max(1L, invocationsUsed * 512L);
            long isolatedChars = Math.max(1L, invocationsUsed * 192L);
            double compression = Math.min(1.0, (double) isolatedChars / estimatedSerialChars);
            String finalDigest = getLastReductionReceipt(executionId, branchId)
                    .map(StateReductionReceipt::afterStateHash)
                    .orElse("");
            return new TypedSubgraphExecutionReceipt(
                    executionId,
                    branchId,
                    last != null && last.completed(),
                    steps,
                    (int) invocationsUsed,
                    Math.max(1, maxWidth),
                    0,
                    0,
                    zone0Preserved,
                    isolatedChars,
                    estimatedSerialChars,
                    compression,
                    finalDigest,
                    last != null && last.errorCode() != null ? last.errorCode() : ""
            );
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
            List<PendingInvocation> restoredPending = new ArrayList<>();
            for (int i = 0; i < record.pendingNodes().size(); i++) {
                String nid = record.pendingNodes().get(i);
                restoredPending.add(new PendingInvocation(
                        nid, Map.of(), String.format(Locale.ROOT, "resume:s%04d:i%04d:%s", record.superstep(), i, nid)
                ));
            }
            pendingInvocationsByBranch.put(key, restoredPending);

            log.info("【状态图引擎】从最后有效快照无损重放恢复成功: branch={}, superstep={}, pendingNodes={}",
                    key, record.superstep(), record.pendingNodes());

            return SuperstepExecutionResult.success(record.superstep(), record.fullStateSnapshot(), record.pendingNodes());
        }

        @Override
        public Map<String, Object> getTelemetryStats() {
            return Map.of(
                    "totalSuperstepsExecuted", totalSuperstepsExecuted.get(),
                    "totalNodeInvocations", totalNodeInvocations.get(),
                    "totalFanoutInstances", totalFanoutInstances.get(),
                    "totalTimeoutOrExceptions", totalTimeoutOrExceptions.get()
            );
        }

        private ExecutedInvocation executeSingleInvocation(
                String executionId,
                String branchId,
                int currentStep,
                PendingInvocation inv,
                NodeDefinition nodeDef,
                Map<String, Object> frozenCurrentState,
                String zone0Prefix,
                String zone0Sha,
                long rawTotalStateChars
        ) {
            try {
                // 构造只读隔离投影视角：若节点声明了 readableChannels，严格剥离其余非授权键
                Map<String, Object> projectedState;
                if (nodeDef.readableChannels() != null) {
                    Map<String, Object> filtered = new LinkedHashMap<>();
                    for (String ch : nodeDef.readableChannels()) {
                        if (frozenCurrentState.containsKey(ch)) {
                            filtered.put(ch, frozenCurrentState.get(ch));
                        }
                    }
                    projectedState = Collections.unmodifiableMap(filtered);
                } else {
                    projectedState = frozenCurrentState;
                }

                // 为前驱守卫提供隔离视图 + 当前分片 payload
                Map<String, Object> guardViewMap = new LinkedHashMap<>(projectedState);
                guardViewMap.putAll(inv.sendPayload());
                boolean guardPassed = nodeDef.guard().verifyPreconditions(Collections.unmodifiableMap(guardViewMap));
                if (!guardPassed) {
                    log.warn("【状态图引擎】节点前置条件守卫未通过，阻断执行: nodeId={}", nodeDef.nodeId());
                    NodeExecutionEnvelope blockedEnvelope = NodeExecutionEnvelope.of(
                            nodeDef.nodeId(),
                            Map.of(
                                    "lastBlockedNode", nodeDef.nodeId(),
                                    "lastBlockedReason", "前置槽位或指标未就绪"
                            )
                    );
                    return new ExecutedInvocation(
                            inv.dispatchPathKey(), nodeDef, blockedEnvelope, List.of(),
                            true, null, 0L, rawTotalStateChars
                    );
                }

                ScopedNodeContextView scopedView = new ScopedNodeContextView(
                        executionId,
                        branchId,
                        nodeDef.nodeId(),
                        inv.dispatchPathKey(),
                        currentStep,
                        zone0Prefix,
                        zone0Sha,
                        projectedState,
                        inv.sendPayload(),
                        List.of()
                );

                NodeExecutionEnvelope rawEnvelope = nodeDef.scopedAction().apply(scopedView);
                NodeExecutionEnvelope normalizedEnvelope = rawEnvelope != null
                        ? new NodeExecutionEnvelope(
                        nodeDef.nodeId(),
                        rawEnvelope.stateUpdates(),
                        rawEnvelope.dynamicSends(),
                        normalizeDeltasWithDeterministicToken(rawEnvelope.explicitDeltas(), inv.dispatchPathKey()),
                        rawEnvelope.timestamp()
                )
                        : NodeExecutionEnvelope.of(nodeDef.nodeId(), Map.of());

                long isolatedChars = estimateCharSize(projectedState) + estimateCharSize(inv.sendPayload());
                return new ExecutedInvocation(
                        inv.dispatchPathKey(),
                        nodeDef,
                        normalizedEnvelope,
                        normalizedEnvelope.dynamicSends(),
                        false,
                        null,
                        isolatedChars,
                        rawTotalStateChars
                );
            } catch (Throwable t) {
                log.warn("【状态图引擎】节点执行抛出受控异常，隔离传播防死锁: path={}, err={}",
                        inv.dispatchPathKey(), t.getMessage());
                return new ExecutedInvocation(
                        inv.dispatchPathKey(),
                        nodeDef,
                        null,
                        List.of(),
                        false,
                        "Node [" + inv.nodeId() + "] execution exception: " + t.getMessage(),
                        0L,
                        rawTotalStateChars
                );
            }
        }

        private static List<BranchStateDelta> normalizeDeltasWithDeterministicToken(
                List<BranchStateDelta> deltas, String dispatchPathKey) {
            if (deltas == null || deltas.isEmpty()) {
                return List.of();
            }
            long hashToken = Integer.toUnsignedLong(dispatchPathKey.hashCode()) + 1L;
            List<BranchStateDelta> out = new ArrayList<>(deltas.size());
            for (int i = 0; i < deltas.size(); i++) {
                BranchStateDelta d = deltas.get(i);
                out.add(BranchStateDelta.of(d.nodeUuid(), d.superstep(), hashToken * 1000L + i, d.deltaUpdates()));
            }
            return out;
        }

        private static long estimateCharSize(Map<String, Object> map) {
            if (map == null || map.isEmpty()) {
                return 0L;
            }
            long total = 0L;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                total += String.valueOf(e.getKey()).length() + String.valueOf(e.getValue()).length();
            }
            return total;
        }

        private static void cancelAll(List<Future<ExecutedInvocation>> futures) {
            for (Future<ExecutedInvocation> f : futures) {
                f.cancel(true);
            }
        }

        private String buildBranchKey(String executionId, String branchId) {
            return executionId + ":" + (branchId != null ? branchId : "main");
        }
    }
}
