package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraph;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphNode;

import java.util.*;
import java.util.concurrent.*;

/**
 * Java 21 虚拟线程超步并发屏障执行器 (VirtualThreadSuperstepExecutor)
 * <p>
 * 基于 Google Pregel / BSP (Bulk Synchronous Parallel) 模型：
 * 1. 同一超步内的活跃节点集合通过 Java 21 虚拟线程轻量级并发推进；
 * 2. 建立同步屏障 (Superstep Synchronization Barrier)，确保全量节点完成或安全降级后统一评估出边；
 * 3. 隔离节点执行故障，支持局部自愈路由与超时熔断，大幅提升长程多任务吞吐量。
 * </p>
 */
@Slf4j
@Component
public class VirtualThreadSuperstepExecutor {

    public static final long DEFAULT_NODE_TIMEOUT_MS = 30_000L;
    public static final long DEFAULT_SUPERSTEP_TIMEOUT_MS = 60_000L;

    private final NodeSelfHealingRouter selfHealingRouter;
    private final BatchIterationSubgraphEngine iterationEngine;

    public VirtualThreadSuperstepExecutor() {
        this(new NodeSelfHealingRouter(), new BatchIterationSubgraphEngine());
    }

    public VirtualThreadSuperstepExecutor(NodeSelfHealingRouter selfHealingRouter,
                                          BatchIterationSubgraphEngine iterationEngine) {
        this.selfHealingRouter = selfHealingRouter != null ? selfHealingRouter : new NodeSelfHealingRouter();
        this.iterationEngine = iterationEngine != null ? iterationEngine : new BatchIterationSubgraphEngine();
    }

    /**
     * 超步并发执行结果 Record
     */
    public record SuperstepExecutionResult(
            int superstep,
            Map<String, NodeRunResultBO> nodeResults,
            long executionLatencyMs,
            int activeNodeCount,
            boolean allSucceeded
    ) {}

    /**
     * 单节点执行任务上下文接口
     */
    @FunctionalInterface
    public interface NodeExecutionWorker {
        NodeRunResultBO execute(StateGraphNode node, StateGraphContext context);
    }

    /**
     * 执行单超步所有活跃节点并发调度
     *
     * @param graph           状态图模型
     * @param context         状态图上下文
     * @param activeNodeUuids 当前超步活跃节点集合
     * @return 超步执行结果
     */
    public SuperstepExecutionResult executeSuperstep(
            StateGraph graph,
            StateGraphContext context,
            Set<String> activeNodeUuids) {
        return executeSuperstep(graph, context, activeNodeUuids, DEFAULT_SUPERSTEP_TIMEOUT_MS);
    }

    /**
     * 执行单超步所有活跃节点并发调度 (支持指定超时)
     *
     * @param graph              状态图模型
     * @param context            状态图上下文
     * @param activeNodeUuids    当前超步活跃节点集合
     * @param superstepTimeoutMs 超步同步屏障超时时间 (毫秒)
     * @return 超步执行结果
     */
    public SuperstepExecutionResult executeSuperstep(
            StateGraph graph,
            StateGraphContext context,
            Set<String> activeNodeUuids,
            long superstepTimeoutMs) {

        if (activeNodeUuids == null || activeNodeUuids.isEmpty()) {
            return new SuperstepExecutionResult(
                    context.getCurrentSuperstep().get(),
                    Collections.emptyMap(),
                    0L,
                    0,
                    true
            );
        }

        long startMs = System.currentTimeMillis();
        int step = context.getCurrentSuperstep().get();
        Map<String, NodeRunResultBO> resultsMap = new ConcurrentHashMap<>();

        log.debug("[VirtualThreadSuperstepExecutor] 开始调度超步 [{}], 并发节点数: {}", step, activeNodeUuids.size());

        // 使用 Java 21 原生虚拟线程池执行 BSP 并发屏障
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (String nodeUuid : activeNodeUuids) {
                StateGraphNode node = graph.getNode(nodeUuid);
                if (node == null) {
                    log.warn("[VirtualThreadSuperstepExecutor] 节点 [{}] 在拓扑图中不存在，跳过", nodeUuid);
                    continue;
                }

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    NodeRunResultBO result;
                    try {
                        if (node.isIterationSubgraph() && node.getSubGraph() != null) {
                            result = executeSubgraphNode(node, context);
                        } else {
                            result = selfHealingRouter.executeWithHealing(node, context);
                        }
                    } catch (Throwable t) {
                        log.error("[VirtualThreadSuperstepExecutor] 节点 [{}] 虚拟线程未捕获异常", nodeUuid, t);
                        result = NodeRunResultBO.failure(nodeUuid, node.getNodeName(), "未捕获异常: " + t.getMessage());
                    }

                    resultsMap.put(nodeUuid, result);
                    context.putNodeResult(nodeUuid, result);
                }, virtualExecutor);

                futures.add(future);
            }

            // 同步屏障 (Barrier)：等待超步内全部虚拟线程完成或超时
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            try {
                long waitTime = Math.max(100L, superstepTimeoutMs);
                allOf.get(waitTime, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                log.warn("[VirtualThreadSuperstepExecutor] 超步 [{}] 同步屏障等待超时 ({} ms)，切入安全隔离降级", step, superstepTimeoutMs);
                for (String nodeUuid : activeNodeUuids) {
                    StateGraphNode n = graph.getNode(nodeUuid);
                    String nName = n != null ? n.getNodeName() : nodeUuid;
                    resultsMap.putIfAbsent(nodeUuid, NodeRunResultBO.failure(nodeUuid, nName, "超步屏障超时熔断"));
                }
            } catch (Exception e) {
                log.error("[VirtualThreadSuperstepExecutor] 超步屏障异常中断", e);
            }
        }

        long latencyMs = System.currentTimeMillis() - startMs;
        boolean allSucceeded = resultsMap.values().stream()
                .allMatch(r -> r != null && (r.getStatus() == null || r.getStatus() != 2));

        log.debug("[VirtualThreadSuperstepExecutor] 超步 [{}] 并发调度结束, 耗时: {} ms, 节点成功率: {}/{}",
                step, latencyMs,
                resultsMap.values().stream().filter(r -> r != null && (r.getStatus() == null || r.getStatus() != 2)).count(),
                resultsMap.size());

        return new SuperstepExecutionResult(
                step,
                Collections.unmodifiableMap(resultsMap),
                latencyMs,
                activeNodeUuids.size(),
                allSucceeded
        );
    }

    private NodeRunResultBO executeSubgraphNode(StateGraphNode node, StateGraphContext context) {
        String key = node.getBatchItemsKey() != null ? node.getBatchItemsKey() : "items";
        Object val = context.getSharedState().get(key);
        List<Object> items = new ArrayList<>();
        if (val instanceof List<?> listVal) {
            items.addAll(listVal);
        }

        List<NodeRunResultBO> subResults = iterationEngine.executeMapReduce(
                items,
                context,
                (item, idx, ctx) -> {
                    NodeRunResultBO itemBO = new NodeRunResultBO();
                    itemBO.setNodeUuid("sub-item-" + idx);
                    itemBO.setStatus(1);
                    itemBO.setOutput(Map.of("item", item, "index", idx));
                    return itemBO;
                },
                true
        );

        boolean allOk = subResults.stream().allMatch(r -> r.getStatus() != null && r.getStatus() != 2);
        NodeRunResultBO resultBO = new NodeRunResultBO();
        resultBO.setNodeUuid(node.getNodeUuid());
        resultBO.setStatus(allOk ? 1 : 2);
        resultBO.setOutput(Map.of("subgraphCount", subResults.size(), "items", subResults));
        return resultBO;
    }
}
