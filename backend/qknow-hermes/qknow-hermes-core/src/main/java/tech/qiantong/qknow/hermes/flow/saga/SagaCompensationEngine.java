package tech.qiantong.qknow.hermes.flow.saga;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.BaseNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowEdgeDO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;

import java.util.*;

/**
 * SAGA 事务状态补偿自愈引擎
 * 基于已执行节点子图的转置图 G^R 执行逆拓扑序 (LIFO) 补偿回滚
 */
@Slf4j
@Component
public class SagaCompensationEngine {

    private static final int MAX_RETRY_TIMES = 3;
    private static final long INITIAL_BACKOFF_MS = 50L;

    /**
     * 在人工审批硬性驳回时触发 SAGA 补偿
     */
    public boolean compensateOnRejection(List<KbFlowNodeDO> nodes,
                                        List<KbFlowEdgeDO> edges,
                                        Map<String, NodeRunResultBO> completedResults,
                                        Map<String, BaseNodeBO> nodeInstanceMap,
                                        RuntimeContextBO context,
                                        String rejectionReason) {
        log.warn("人工审批硬性驳回，启动 SAGA 逆拓扑补偿自愈体系，驳回原因: {}", rejectionReason);
        return compensateExecutedNodes(nodes, edges, completedResults, nodeInstanceMap, context,
                "APPROVAL_REJECTED: " + rejectionReason);
    }

    /**
     * 在下游节点执行异常时触发 SAGA 补偿
     */
    public boolean compensateOnError(List<KbFlowNodeDO> nodes,
                                     List<KbFlowEdgeDO> edges,
                                     Map<String, NodeRunResultBO> completedResults,
                                     Map<String, BaseNodeBO> nodeInstanceMap,
                                     RuntimeContextBO context,
                                     String failedNodeUuid,
                                     String errorMessage) {
        log.error("工作流执行发生异常，触发 SAGA 逆拓扑补偿回滚: failedNode={}, error={}",
                failedNodeUuid, errorMessage);
        return compensateExecutedNodes(nodes, edges, completedResults, nodeInstanceMap, context,
                "EXECUTION_ERROR at " + failedNodeUuid + ": " + errorMessage);
    }

    /**
     * 核心逆拓扑补偿调度
     */
    public boolean compensateExecutedNodes(List<KbFlowNodeDO> nodes,
                                          List<KbFlowEdgeDO> edges,
                                          Map<String, NodeRunResultBO> completedResults,
                                          Map<String, BaseNodeBO> nodeInstanceMap,
                                          RuntimeContextBO context,
                                          String reason) {
        if (completedResults == null || completedResults.isEmpty()) {
            log.info("无已执行完成的节点，跳过 SAGA 补偿");
            return true;
        }

        // 1. 过滤出成功执行的节点 UUID
        Set<String> successfulNodeUuids = new LinkedHashSet<>();
        for (Map.Entry<String, NodeRunResultBO> entry : completedResults.entrySet()) {
            if (entry.getValue() != null &&
                RuntimeStatusEnums.SUCCESS.getCode().equals(entry.getValue().getStatus())) {
                successfulNodeUuids.add(entry.getKey());
            }
        }

        if (successfulNodeUuids.isEmpty()) {
            log.info("未发现执行成功的节点，无需补偿");
            return true;
        }

        // 2. 构建转置依赖图 G^R 并进行逆拓扑排序 (LIFO)
        List<String> reverseOrder = computeReverseTopologicalOrder(successfulNodeUuids, edges);
        log.info("SAGA 逆拓扑补偿序列确立: {}", reverseOrder);

        List<Map<String, Object>> auditLogs = new ArrayList<>();
        boolean allSuccess = true;

        // 3. 严格按照逆拓扑序执行各个节点的 compensate 方法
        for (String nodeUuid : reverseOrder) {
            NodeRunResultBO originalResult = completedResults.get(nodeUuid);
            BaseNodeBO nodeInstance = nodeInstanceMap != null ? nodeInstanceMap.get(nodeUuid) : null;

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("nodeUuid", nodeUuid);
            logEntry.put("timestamp", System.currentTimeMillis());

            if (nodeInstance instanceof CompensableNode compensable) {
                log.info("执行节点 SAGA 补偿: nodeUuid={}, handler={}", nodeUuid, compensable.getCompensationIdentifier());
                boolean nodeCompensated = executeWithRetry(compensable, context, originalResult);
                logEntry.put("status", nodeCompensated ? "COMPENSATED" : "FAILED");
                logEntry.put("handler", compensable.getCompensationIdentifier());

                if (!nodeCompensated) {
                    allSuccess = false;
                    log.error("节点 SAGA 补偿重试耗尽仍然失败: nodeUuid={}", nodeUuid);
                }
            } else {
                log.debug("节点未实现 CompensableNode，优雅跳过逆向补偿: nodeUuid={}", nodeUuid);
                logEntry.put("status", "SKIPPED_NOT_COMPENSABLE");
            }
            auditLogs.add(logEntry);
        }

        log.info("SAGA 逆拓扑补偿执行完毕，总体状态: {}, 审计日志: {}",
                allSuccess ? "ALL_COMPENSATED" : "PARTIAL_FAILED", JSON.toJSONString(auditLogs));
        return allSuccess;
    }

    /**
     * 带指数退避的补偿重试机制
     */
    private boolean executeWithRetry(CompensableNode compensable,
                                     RuntimeContextBO context,
                                     NodeRunResultBO originalResult) {
        int attempts = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempts < MAX_RETRY_TIMES) {
            attempts++;
            try {
                boolean ok = compensable.compensate(context, originalResult);
                if (ok) {
                    return true;
                }
                log.warn("节点补偿逻辑返回 false，准备第 {} 次重试", attempts);
            } catch (Exception e) {
                log.warn("节点补偿抛出异常 (第 {} 次尝试): {}", attempts, e.getMessage());
            }

            if (attempts < MAX_RETRY_TIMES) {
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoff *= 2;
            }
        }
        return false;
    }

    /**
     * 构建转置图并进行拓扑排序（LIFO）
     */
    private List<String> computeReverseTopologicalOrder(Set<String> activeNodes, List<KbFlowEdgeDO> edges) {
        // 构建转置邻接表与入度表
        Map<String, Set<String>> reverseAdj = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (String node : activeNodes) {
            reverseAdj.put(node, new LinkedHashSet<>());
            inDegree.put(node, 0);
        }

        // 原图边: u -> v，转置边: v -> u
        if (edges != null) {
            for (KbFlowEdgeDO edge : edges) {
                String u = edge.getSourceNodeUuid();
                String v = edge.getTargetNodeUuid();
                if (activeNodes.contains(u) && activeNodes.contains(v)) {
                    // 转置图: v -> u
                    if (reverseAdj.get(v).add(u)) {
                        inDegree.put(u, inDegree.get(u) + 1);
                    }
                }
            }
        }

        // Kahn 算法拓扑排序转置图
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            order.add(node);

            for (String neighbor : reverseAdj.get(node)) {
                int deg = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, deg);
                if (deg == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // 兜底：若存在孤立节点或局部环，补齐剩余活跃节点
        for (String node : activeNodes) {
            if (!order.contains(node)) {
                order.add(node);
            }
        }

        return order;
    }
}
