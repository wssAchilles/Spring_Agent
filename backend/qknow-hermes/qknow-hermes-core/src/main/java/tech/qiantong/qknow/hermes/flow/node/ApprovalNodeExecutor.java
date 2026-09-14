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

        // 记录待办项并注册 CompletableFuture（便于进程内事件通知）
        CompletableFuture<Void> approvalFuture = new CompletableFuture<>();
        PENDING_APPROVALS.put(approvalKey, approvalFuture);

        // 提取配置中的审批原因与元数据
        String reason = "等待人工审批";
        if (node.getConfig() != null && !node.getConfig().isBlank()) {
            try {
                com.alibaba.fastjson2.JSONObject config = com.alibaba.fastjson2.JSONObject.parseObject(node.getConfig());
                if (config.containsKey("reason") && !config.getString("reason").isBlank()) {
                    reason = config.getString("reason");
                }
            } catch (Exception e) {
                log.debug("解析审批配置异常: {}", e.getMessage());
            }
        }

        // 非阻塞 Delimited Continuation：立即返回 SUSPENDED 挂起状态，物理工作线程零阻塞归还线程池
        Map<String, Object> output = new java.util.LinkedHashMap<>();
        output.put("status", "SUSPENDED");
        output.put("approvalKey", approvalKey);
        output.put("flowId", flowId);
        output.put("requestId", requestId);
        output.put("nodeId", nodeId);
        output.put("reason", reason);
        output.put("suspendedAt", System.currentTimeMillis());

        log.info("审批节点非阻塞挂起成功: approvalKey={}, reason={}", approvalKey, reason);
        return NodeRunResultBO.suspended(nodeId, node.getName(), output);
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
