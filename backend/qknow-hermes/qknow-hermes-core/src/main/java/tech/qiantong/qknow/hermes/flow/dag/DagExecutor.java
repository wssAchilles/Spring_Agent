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
 * DAG 反应式并行执行器
 * 支持独立分支并行执行、条件分支动态递归剪枝（零物理线程占用）、断点全量上下文挂起与恢复
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
     * 执行工作流（集成动态递归剪枝）
     *
     * @param flowNodes 节点列表
     * @param flowEdges 边列表
     * @param context   运行上下文
     * @return 所有节点的执行结果
     */
    public List<NodeRunResultBO> execute(List<KbFlowNodeDO> flowNodes, List<KbFlowEdgeDO> flowEdges,
                                          RuntimeContextBO context) {
        // 1. 验证 DAG 是否存在环路
        if (DagUtils.hasCycle(flowNodes, flowEdges)) {
            throw new IllegalStateException("工作流存在环，无法执行");
        }

        // 2. 获取并行分组
        List<List<String>> parallelGroups = DagUtils.getParallelGroups(flowNodes, flowEdges);
        log.info("工作流共 {} 个节点，分为 {} 个执行组", flowNodes.size(), parallelGroups.size());

        // 3. 构建节点映射与动态剪枝集合
        Map<String, KbFlowNodeDO> nodeMap = flowNodes.stream()
                .collect(Collectors.toMap(KbFlowNodeDO::getUuid, n -> n));
        Set<String> prunedNodes = ConcurrentHashMap.newKeySet();

        // 4. 按组顺序执行
        List<NodeRunResultBO> allResults = new ArrayList<>();
        Map<String, NodeRunResultBO> resultMap = new ConcurrentHashMap<>();

        for (int groupIndex = 0; groupIndex < parallelGroups.size(); groupIndex++) {
            List<String> group = parallelGroups.get(groupIndex);
            log.info("执行第 {} 组，包含 {} 个节点: {}", groupIndex + 1, group.size(), group);

            if (group.size() == 1) {
                // 单节点执行（含剪枝判定）
                NodeRunResultBO result = executeNodeWithPruning(group.get(0), nodeMap, flowEdges, context, prunedNodes);
                allResults.add(result);
                resultMap.put(group.get(0), result);

                if (isError(result)) {
                    log.error("节点执行失败，终止工作流: {}", result.getNodeName());
                    break;
                }
                if (isSuspended(result)) {
                    log.info("节点挂起，暂停工作流: {}", result.getNodeName());
                    break;
                }
            } else {
                // 多节点并行执行
                List<CompletableFuture<NodeRunResultBO>> futures = new ArrayList<>();
                for (String nodeUuid : group) {
                    CompletableFuture<NodeRunResultBO> future = CompletableFuture.supplyAsync(
                            () -> executeNodeWithPruning(nodeUuid, nodeMap, flowEdges, context, prunedNodes),
                            executorService
                    );
                    futures.add(future);
                }

                // 等待所有节点完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // 收集结果
                boolean hasError = false;
                boolean hasSuspended = false;
                for (CompletableFuture<NodeRunResultBO> future : futures) {
                    try {
                        NodeRunResultBO result = future.get();
                        allResults.add(result);
                        resultMap.put(result.getNodeUuid(), result);

                        if (isError(result)) {
                            hasError = true;
                        } else if (isSuspended(result)) {
                            hasSuspended = true;
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
                if (hasSuspended) {
                    log.info("并行执行组中存在挂起节点，暂停工作流");
                    break;
                }
            }
        }

        return allResults;
    }

    /**
     * 带断点续传的工作流执行
     * 支持从上次中断的位置恢复执行，并携带全量上下文变量
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
            // 恢复上下文环境变量
            Map<String, Object> restoredVars = checkpointManager.restoreVariables(checkpoint);
            if (context != null && context.getVariables() != null && !restoredVars.isEmpty()) {
                context.getVariables().putAll(restoredVars);
            }
            log.info("从检查点恢复: runtimeId={}, groupIndex={}, 已完成节点={}",
                    runtimeId, startGroupIndex, restoredResults.size());
        }

        // 验证 DAG
        if (DagUtils.hasCycle(flowNodes, flowEdges)) {
            throw new IllegalStateException("工作流存在环，无法执行");
        }

        List<List<String>> parallelGroups = DagUtils.getParallelGroups(flowNodes, flowEdges);
        Map<String, KbFlowNodeDO> nodeMap = flowNodes.stream()
                .collect(Collectors.toMap(KbFlowNodeDO::getUuid, n -> n));

        Set<String> prunedNodes = ConcurrentHashMap.newKeySet();
        // 初始化剪枝状态（从已有结果中提取）
        for (NodeRunResultBO r : restoredResults.values()) {
            if (RuntimeStatusEnums.SKIPPED.getCode().equals(r.getStatus())) {
                prunedNodes.add(r.getNodeUuid());
            }
            if (r.getNextNodeIds() != null) {
                prunedNodes.addAll(DagUtils.computePrunedNodes(r.getNodeUuid(), r.getNextNodeIds(), flowEdges));
            }
        }

        List<NodeRunResultBO> allResults = new ArrayList<>(restoredResults.values());
        Map<String, NodeRunResultBO> resultMap = new LinkedHashMap<>(restoredResults);
        if (checkpointManager.hasSuspendedResult(resultMap)) {
            log.info("工作流仍处于挂起状态，等待人工唤醒: runtimeId={}", runtimeId);
            return allResults;
        }

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
                NodeRunResultBO result = executeNodeWithPruning(pending.get(0), nodeMap, flowEdges, context, prunedNodes);
                allResults.add(result);
                resultMap.put(pending.get(0), result);

                if (isError(result)) {
                    checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, groupIndex, resultMap,
                            context != null ? context.getVariables() : Collections.emptyMap());
                    log.error("节点执行失败，检查点已保存，终止工作流: {}", result.getNodeName());
                    completedSuccessfully = false;
                    break;
                }
                if (isSuspended(result)) {
                    checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, groupIndex, resultMap,
                            context != null ? context.getVariables() : Collections.emptyMap());
                    log.info("节点挂起，检查点已保存: runtimeId={}, node={}", runtimeId, result.getNodeName());
                    completedSuccessfully = false;
                    break;
                }
            } else {
                List<CompletableFuture<NodeRunResultBO>> futures = new ArrayList<>();
                for (String nodeUuid : pending) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> executeNodeWithPruning(nodeUuid, nodeMap, flowEdges, context, prunedNodes),
                            executorService));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                boolean hasError = false;
                boolean hasSuspended = false;
                for (CompletableFuture<NodeRunResultBO> future : futures) {
                    try {
                        NodeRunResultBO result = future.get();
                        allResults.add(result);
                        resultMap.put(result.getNodeUuid(), result);
                        if (isError(result)) {
                            hasError = true;
                        } else if (isSuspended(result)) {
                            hasSuspended = true;
                        }
                    } catch (Exception e) {
                        log.error("并行执行异常", e);
                        hasError = true;
                    }
                }

                if (hasError) {
                    checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, groupIndex, resultMap,
                            context != null ? context.getVariables() : Collections.emptyMap());
                    log.error("并行执行组中存在失败节点，检查点已保存，终止工作流");
                    completedSuccessfully = false;
                    break;
                }
                if (hasSuspended) {
                    checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, groupIndex, resultMap,
                            context != null ? context.getVariables() : Collections.emptyMap());
                    log.info("并行执行组中存在挂起节点，检查点已保存");
                    completedSuccessfully = false;
                    break;
                }
            }

            // 每组执行完后保存检查点（包含变量环境）
            checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, groupIndex + 1, resultMap,
                    context != null ? context.getVariables() : Collections.emptyMap());
        }

        // 只在全部成功时删除检查点
        if (completedSuccessfully) {
            checkpointManager.deleteCheckpoint(runtimeId);
        }
        return allResults;
    }

    public boolean wakeSuspended(String runtimeId, Map<String, Object> humanInput) {
        return checkpointManager.wakeSuspended(runtimeId, humanInput);
    }

    /**
     * 带动态递归剪枝检测的节点执行
     */
    private NodeRunResultBO executeNodeWithPruning(String nodeUuid, Map<String, KbFlowNodeDO> nodeMap,
                                                  List<KbFlowEdgeDO> flowEdges, RuntimeContextBO context,
                                                  Set<String> prunedNodes) {
        // 1. 若当前节点已被前面的条件分支剪除，则不提交物理执行，直接标记为 SKIPPED
        if (prunedNodes.contains(nodeUuid)) {
            KbFlowNodeDO def = nodeMap.get(nodeUuid);
            String name = def != null ? def.getName() : "未知节点";
            NodeRunResultBO skipped = new NodeRunResultBO();
            skipped.setNodeUuid(nodeUuid);
            skipped.setNodeName(name);
            skipped.setStatus(RuntimeStatusEnums.SKIPPED.getCode());
            skipped.setOutput(Map.of("status", "SKIPPED", "reason", "pruned_by_condition"));
            if (context != null && context.getVariables() != null) {
                context.getVariables().put(nodeUuid + ".status", RuntimeStatusEnums.SKIPPED.getCode());
                context.getVariables().put(nodeUuid + ".skipped", true);
            }
            log.info("节点处于未命中条件分支，跳过物理执行并标记为 SKIPPED: nodeUuid={}, nodeName={}",
                    nodeUuid, name);
            return skipped;
        }

        // 2. 正常物理执行
        NodeRunResultBO result = executeNode(nodeUuid, nodeMap, flowEdges, context);

        // 3. 若为条件网关节点（产出了选中的后继分支），递归计算未选中的后继子图并加入剪枝集合
        if (result.getNextNodeIds() != null) {
            Set<String> newlyPruned = DagUtils.computePrunedNodes(nodeUuid, result.getNextNodeIds(), flowEdges);
            if (!newlyPruned.isEmpty()) {
                prunedNodes.addAll(newlyPruned);
                for (String p : newlyPruned) {
                    if (context != null && context.getVariables() != null) {
                        context.getVariables().put(p + ".status", RuntimeStatusEnums.SKIPPED.getCode());
                        context.getVariables().put(p + ".skipped", true);
                    }
                }
                log.info("条件分支决策激活: nodeUuid={}, 选中={}, 动态剪枝下游节点={}",
                        nodeUuid, result.getNextNodeIds(), newlyPruned);
            }
        }

        return result;
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

    private boolean isError(NodeRunResultBO result) {
        return RuntimeStatusEnums.ERROR.getCode().equals(result.getStatus());
    }

    private boolean isSuspended(NodeRunResultBO result) {
        return RuntimeStatusEnums.SUSPENDED.getCode().equals(result.getStatus());
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
