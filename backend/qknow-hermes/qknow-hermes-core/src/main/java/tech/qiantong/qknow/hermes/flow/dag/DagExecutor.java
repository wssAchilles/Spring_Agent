package tech.qiantong.qknow.hermes.flow.dag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.BaseNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.hermes.flow.factory.NodeFactory;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowEdgeDO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * DAG 并行执行器
 * 支持独立分支并行执行，依赖分支顺序执行
 */
@Slf4j
@Component
public class DagExecutor {

    private final NodeFactory nodeFactory;
    private final ExecutorService executorService;
    private final DagCheckpointManager checkpointManager;

    public DagExecutor(NodeFactory nodeFactory, DagCheckpointManager checkpointManager) {
        this.nodeFactory = nodeFactory;
        this.checkpointManager = checkpointManager;
        this.executorService = Executors.newFixedThreadPool(
                Math.min(Runtime.getRuntime().availableProcessors(), 8));
    }

    /**
     * 执行工作流
     *
     * @param flowNodes 节点列表
     * @param flowEdges 边列表
     * @param context   运行上下文
     * @return 所有节点的执行结果
     */
    public List<NodeRunResultBO> execute(List<KbFlowNodeDO> flowNodes, List<KbFlowEdgeDO> flowEdges,
                                          RuntimeContextBO context) {
        // 1. 验证 DAG
        if (DagUtils.hasCycle(flowNodes, flowEdges)) {
            throw new IllegalStateException("工作流存在环，无法执行");
        }

        // 2. 获取并行分组
        List<List<String>> parallelGroups = DagUtils.getParallelGroups(flowNodes, flowEdges);
        log.info("工作流共 {} 个节点，分为 {} 个执行组", flowNodes.size(), parallelGroups.size());

        // 3. 构建节点映射
        Map<String, KbFlowNodeDO> nodeMap = flowNodes.stream()
                .collect(Collectors.toMap(KbFlowNodeDO::getUuid, n -> n));

        // 4. 按组顺序执行
        List<NodeRunResultBO> allResults = new ArrayList<>();
        Map<String, NodeRunResultBO> resultMap = new ConcurrentHashMap<>();

        for (int groupIndex = 0; groupIndex < parallelGroups.size(); groupIndex++) {
            List<String> group = parallelGroups.get(groupIndex);
            log.info("执行第 {} 组，包含 {} 个节点: {}", groupIndex + 1, group.size(), group);

            if (group.size() == 1) {
                // 单节点直接执行
                NodeRunResultBO result = executeNode(group.get(0), nodeMap, flowEdges, context);
                allResults.add(result);
                resultMap.put(group.get(0), result);

                if (RuntimeStatusEnums.ERROR.getCode().equals(result.getStatus())) {
                    log.error("节点执行失败，终止工作流: {}", result.getNodeName());
                    break;
                }
            } else {
                // 多节点并行执行
                List<CompletableFuture<NodeRunResultBO>> futures = new ArrayList<>();
                for (String nodeUuid : group) {
                    CompletableFuture<NodeRunResultBO> future = CompletableFuture.supplyAsync(
                            () -> executeNode(nodeUuid, nodeMap, flowEdges, context),
                            executorService
                    );
                    futures.add(future);
                }

                // 等待所有节点完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // 收集结果
                boolean hasError = false;
                for (CompletableFuture<NodeRunResultBO> future : futures) {
                    try {
                        NodeRunResultBO result = future.get();
                        allResults.add(result);
                        resultMap.put(result.getNodeUuid(), result);

                        if (RuntimeStatusEnums.ERROR.getCode().equals(result.getStatus())) {
                            hasError = true;
                        }
                    } catch (Exception e) {
                        log.error("并行执行异常", e);
                        hasError = true;
                    }
                }

                if (hasError) {
                    log.error("并行执行组中存在失败节点，终止工作流");
                    break;
                }
            }
        }

        return allResults;
    }

    /**
     * 带断点续传的工作流执行
     * 支持从上次中断的位置恢复执行
     */
    public List<NodeRunResultBO> executeWithCheckpoint(String runtimeId, String flowId,
                                                        List<KbFlowNodeDO> flowNodes,
                                                        List<KbFlowEdgeDO> flowEdges,
                                                        RuntimeContextBO context) {
        // 尝试加载检查点
        DagCheckpointManager.DagCheckpoint checkpoint = checkpointManager.loadCheckpoint(runtimeId);
        int startGroupIndex = 0;
        Map<String, NodeRunResultBO> restoredResults = new LinkedHashMap<>();

        if (checkpoint != null) {
            startGroupIndex = checkpoint.getGroupIndex();
            restoredResults = checkpointManager.restoreCompletedResults(checkpoint);
            log.info("从检查点恢复: runtimeId={}, groupIndex={}, 已完成节点={}", runtimeId, startGroupIndex, restoredResults.size());
        }

        // 验证 DAG
        if (DagUtils.hasCycle(flowNodes, flowEdges)) {
            throw new IllegalStateException("工作流存在环，无法执行");
        }

        List<List<String>> parallelGroups = DagUtils.getParallelGroups(flowNodes, flowEdges);
        Map<String, KbFlowNodeDO> nodeMap = flowNodes.stream()
                .collect(Collectors.toMap(KbFlowNodeDO::getUuid, n -> n));

        List<NodeRunResultBO> allResults = new ArrayList<>(restoredResults.values());
        Map<String, NodeRunResultBO> resultMap = new LinkedHashMap<>(restoredResults);

        // 从断点位置开始执行
        boolean completedSuccessfully = true;
        for (int groupIndex = startGroupIndex; groupIndex < parallelGroups.size(); groupIndex++) {
            List<String> group = parallelGroups.get(groupIndex);
            // 跳过已完成的节点（从 checkpoint 恢复时）
            List<String> pending = group.stream()
                    .filter(uuid -> !resultMap.containsKey(uuid))
                    .toList();
            if (pending.isEmpty()) continue;

            if (pending.size() == 1) {
                NodeRunResultBO result = executeNode(pending.get(0), nodeMap, flowEdges, context);
                allResults.add(result);
                resultMap.put(group.get(0), result);

                if (RuntimeStatusEnums.ERROR.getCode().equals(result.getStatus())) {
                    checkpointManager.saveCheckpoint(runtimeId, flowId, groupIndex, resultMap);
                    log.error("节点执行失败，检查点已保存，终止工作流: {}", result.getNodeName());
                    completedSuccessfully = false;
                    break;
                }
            } else {
                List<CompletableFuture<NodeRunResultBO>> futures = new ArrayList<>();
                for (String nodeUuid : pending) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> executeNode(nodeUuid, nodeMap, flowEdges, context),
                            executorService));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                boolean hasError = false;
                for (CompletableFuture<NodeRunResultBO> future : futures) {
                    try {
                        NodeRunResultBO result = future.get();
                        allResults.add(result);
                        resultMap.put(result.getNodeUuid(), result);
                        if (RuntimeStatusEnums.ERROR.getCode().equals(result.getStatus())) {
                            hasError = true;
                        }
                    } catch (Exception e) {
                        log.error("并行执行异常", e);
                        hasError = true;
                    }
                }

                if (hasError) {
                    checkpointManager.saveCheckpoint(runtimeId, flowId, groupIndex, resultMap);
                    log.error("并行执行组中存在失败节点，检查点已保存，终止工作流");
                    completedSuccessfully = false;
                    break;
                }
            }

            // 每组执行完后保存检查点
            checkpointManager.saveCheckpoint(runtimeId, flowId, groupIndex + 1, resultMap);
        }

        // 只在全部成功时删除检查点
        if (completedSuccessfully) {
            checkpointManager.deleteCheckpoint(runtimeId);
        }
        return allResults;
    }

    /**
     * 执行单个节点
     */
    private NodeRunResultBO executeNode(String nodeUuid, Map<String, KbFlowNodeDO> nodeMap,
                                         List<KbFlowEdgeDO> flowEdges, RuntimeContextBO context) {
        KbFlowNodeDO nodeDefinition = nodeMap.get(nodeUuid);
        if (nodeDefinition == null) {
            return NodeRunResultBO.failure(nodeUuid, "未知节点", "节点定义不存在");
        }

        try {
            BaseNodeBO node = nodeFactory.createNode(nodeDefinition, flowEdges);
            return node.execute(context);
        } catch (Exception e) {
            log.error("节点执行异常: {}", nodeUuid, e);
            return NodeRunResultBO.failure(nodeUuid, nodeDefinition.getName(), e.getMessage());
        }
    }

    /**
     * 关闭执行器
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
