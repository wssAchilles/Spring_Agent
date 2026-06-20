package tech.qiantong.qknow.hermes.flow.dag;

import lombok.extern.slf4j.Slf4j;
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
public class DagExecutor {

    private final NodeFactory nodeFactory;
    private final ExecutorService executorService;

    public DagExecutor(NodeFactory nodeFactory) {
        this.nodeFactory = nodeFactory;
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
