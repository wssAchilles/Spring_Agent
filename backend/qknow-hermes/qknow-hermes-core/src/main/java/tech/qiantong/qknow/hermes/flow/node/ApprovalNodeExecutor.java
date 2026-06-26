package tech.qiantong.qknow.hermes.flow.node;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.bo.RuntimeContextBO;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 人工审批节点 — Human-in-the-loop
 * 参考：LangGraph Human-in-the-loop（35.8k⭐）
 *
 * 工作流执行到此节点时暂停，等待人工审批后继续。
 * 审批状态存储在 Redis 中，前端通过 API 查询和更新。
 */
@Slf4j
public class ApprovalNodeExecutor {

    private static final Map<String, CompletableFuture<Void>> PENDING_APPROVALS = new ConcurrentHashMap<>();
    private static final long DEFAULT_TIMEOUT_HOURS = 24;

    /**
     * 执行审批节点（暂停等待审批）
     */
    public static NodeRunResultBO execute(KbFlowNodeDO node, RuntimeContextBO context,
                                           String flowId, String requestId) {
        String nodeId = node.getUuid();
        String approvalKey = flowId + ":" + requestId + ":" + nodeId;

        log.info("审批节点暂停: flowId={}, nodeId={}, 等待人工审批", flowId, nodeId);

        // 创建 CompletableFuture 等待审批
        CompletableFuture<Void> approvalFuture = new CompletableFuture<>();
        PENDING_APPROVALS.put(approvalKey, approvalFuture);

        try {
            // 阻塞等待审批（带超时）
            approvalFuture.get(DEFAULT_TIMEOUT_HOURS, TimeUnit.HOURS);

            log.info("审批节点通过: flowId={}, nodeId={}", flowId, nodeId);
            return NodeRunResultBO.success(nodeId, node.getName(),
                    Map.of("status", "APPROVED", "timestamp", System.currentTimeMillis()));

        } catch (Exception e) {
            log.warn("审批节点超时或失败: flowId={}, nodeId={}", flowId, nodeId);
            PENDING_APPROVALS.remove(approvalKey);
            return NodeRunResultBO.failure(nodeId, node.getName(),
                    "审批超时或被拒绝: " + e.getMessage());
        }
    }

    /**
     * 审批通过（由前端 API 调用）
     */
    public static boolean approve(String flowId, String requestId, String nodeId) {
        String key = flowId + ":" + requestId + ":" + nodeId;
        CompletableFuture<Void> future = PENDING_APPROVALS.remove(key);
        if (future != null) {
            future.complete(null);
            return true;
        }
        return false;
    }

    /**
     * 审批拒绝（由前端 API 调用）
     */
    public static boolean reject(String flowId, String requestId, String nodeId, String reason) {
        String key = flowId + ":" + requestId + ":" + nodeId;
        CompletableFuture<Void> future = PENDING_APPROVALS.remove(key);
        if (future != null) {
            future.completeExceptionally(new RuntimeException("审批被拒绝: " + reason));
            return true;
        }
        return false;
    }

    /**
     * 查询待审批列表
     */
    public static Map<String, CompletableFuture<Void>> getPendingApprovals() {
        return new ConcurrentHashMap<>(PENDING_APPROVALS);
    }
}
