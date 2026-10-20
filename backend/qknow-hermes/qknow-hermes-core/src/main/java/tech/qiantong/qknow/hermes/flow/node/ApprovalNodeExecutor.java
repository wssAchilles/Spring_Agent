package tech.qiantong.qknow.hermes.flow.node;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.common.utils.spring.SpringUtils;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.redis.service.IRedisService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人工审批节点 — Human-in-the-loop
 * 参考：LangGraph Human-in-the-loop（35.8k⭐）
 *
 * 工作流执行到此节点时暂停，等待人工审批后继续。
 * 审批状态持久化在 Redis 中，消除跨 JVM 部署时的状态失联。
 */
@Slf4j
public class ApprovalNodeExecutor {

    public static final String REDIS_PENDING_PREFIX = "hermes:approval:pending:";
    public static final String REDIS_DECISION_PREFIX = "hermes:approval:decision:";
    public static final long DEFAULT_TIMEOUT_SECONDS = 24 * 3600; // 待审批项 TTL 24 小时
    public static final long DECISION_RETENTION_SECONDS = 2 * 3600; // 审批终态保留 2 小时

    private static final Map<String, CompletableFuture<Void>> PENDING_APPROVALS = new ConcurrentHashMap<>();

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
                JSONObject config = JSON.parseObject(node.getConfig());
                if (config.containsKey("reason") && !config.getString("reason").isBlank()) {
                    reason = config.getString("reason");
                }
            } catch (Exception e) {
                log.debug("解析审批配置异常: {}", e.getMessage());
            }
        }

        // 构建审批待办明细数据
        Map<String, Object> pendingData = new LinkedHashMap<>();
        pendingData.put("approvalKey", approvalKey);
        pendingData.put("flowId", flowId);
        pendingData.put("requestId", requestId);
        pendingData.put("nodeId", nodeId);
        pendingData.put("nodeName", node.getName());
        pendingData.put("reason", reason);
        pendingData.put("status", "PENDING");
        pendingData.put("suspendedAt", System.currentTimeMillis());

        // 持久化至 Redis，打破跨 JVM 隔离壁垒
        try {
            IRedisService redisService = getRedisService();
            if (redisService != null) {
                redisService.set(REDIS_PENDING_PREFIX + approvalKey, JSON.toJSONString(pendingData), DEFAULT_TIMEOUT_SECONDS);
                log.info("审批待办成功持久化至 Redis: key={}", REDIS_PENDING_PREFIX + approvalKey);
            }
        } catch (Exception e) {
            log.warn("写入 Redis 审批待办失败 (降级为进程内 Map): {}", e.getMessage());
        }

        // 非阻塞 Delimited Continuation：立即返回 SUSPENDED 挂起状态，物理工作线程零阻塞归还线程池
        Map<String, Object> output = new LinkedHashMap<>(pendingData);
        output.put("status", "SUSPENDED");

        log.info("审批节点非阻塞挂起成功: approvalKey={}, reason={}", approvalKey, reason);
        return NodeRunResultBO.suspended(nodeId, node.getName(), output);
    }

    /**
     * 审批通过（由前端 API 或代理 Controller 调用）
     */
    public static boolean approve(String flowId, String requestId, String nodeId) {
        String key = flowId + ":" + requestId + ":" + nodeId;
        boolean memSuccess = false;
        CompletableFuture<Void> future = PENDING_APPROVALS.remove(key);
        if (future != null) {
            future.complete(null);
            memSuccess = true;
        }

        // 更新 Redis 终态
        boolean redisSuccess = false;
        try {
            IRedisService redisService = getRedisService();
            if (redisService != null) {
                String pendingJson = redisService.get(REDIS_PENDING_PREFIX + key);
                if (pendingJson != null) {
                    redisService.delete(REDIS_PENDING_PREFIX + key);
                    Map<String, Object> decision = new HashMap<>();
                    decision.put("approvalKey", key);
                    decision.put("decision", "APPROVED");
                    decision.put("decidedAt", System.currentTimeMillis());
                    redisService.set(REDIS_DECISION_PREFIX + key, JSON.toJSONString(decision), DECISION_RETENTION_SECONDS);
                    redisSuccess = true;
                    log.info("Redis 审批状态已更新为 APPROVED: key={}", key);
                }
            }
        } catch (Exception e) {
            log.warn("更新 Redis 审批通过状态异常: {}", e.getMessage());
        }

        return memSuccess || redisSuccess;
    }

    /**
     * 审批拒绝（由前端 API 或代理 Controller 调用）
     */
    public static boolean reject(String flowId, String requestId, String nodeId, String reason) {
        String key = flowId + ":" + requestId + ":" + nodeId;
        boolean memSuccess = false;
        CompletableFuture<Void> future = PENDING_APPROVALS.remove(key);
        if (future != null) {
            future.completeExceptionally(new RuntimeException("审批被拒绝: " + reason));
            memSuccess = true;
        }

        // 更新 Redis 终态
        boolean redisSuccess = false;
        try {
            IRedisService redisService = getRedisService();
            if (redisService != null) {
                String pendingJson = redisService.get(REDIS_PENDING_PREFIX + key);
                if (pendingJson != null) {
                    redisService.delete(REDIS_PENDING_PREFIX + key);
                    Map<String, Object> decision = new HashMap<>();
                    decision.put("approvalKey", key);
                    decision.put("decision", "REJECTED");
                    decision.put("reason", reason);
                    decision.put("decidedAt", System.currentTimeMillis());
                    redisService.set(REDIS_DECISION_PREFIX + key, JSON.toJSONString(decision), DECISION_RETENTION_SECONDS);
                    redisSuccess = true;
                    log.info("Redis 审批状态已更新为 REJECTED: key={}", key);
                }
            }
        } catch (Exception e) {
            log.warn("更新 Redis 审批拒绝状态异常: {}", e.getMessage());
        }

        return memSuccess || redisSuccess;
    }

    /**
     * 查询待审批列表（进程内视图）
     */
    public static Map<String, CompletableFuture<Void>> getPendingApprovals() {
        return new ConcurrentHashMap<>(PENDING_APPROVALS);
    }

    /**
     * 安全获取 Redis 服务 Bean（容错无 Spring 上下文的纯单测）
     */
    private static IRedisService getRedisService() {
        try {
            return SpringUtils.getBean(IRedisService.class);
        } catch (Exception e) {
            return null;
        }
    }
}
