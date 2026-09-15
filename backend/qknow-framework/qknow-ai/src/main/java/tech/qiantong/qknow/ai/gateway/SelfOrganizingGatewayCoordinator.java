package tech.qiantong.qknow.ai.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 多智能体自组织动态微服务网关统筹协调总控中枢
 * <p>
 * 闭环调度：
 * 零信任鉴权 -> 意图路由 -> 节点探活自愈 -> 意图自适应 RPC 派发 -> 签发不可变密码学存证凭单
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public class SelfOrganizingGatewayCoordinator {

    public record GatewayRequest(
            String msgId,
            String senderId,
            String intentQuery,
            float[] queryEmbedding,
            String action,
            Map<String, Object> payload,
            long timestamp,
            String nonce,
            String clientSignature,
            Map<String, Object> callerAttributes
    ) {}

    public record GatewayResult(
            boolean success,
            String targetAgentId,
            String routingPath,
            IntentAdaptiveRpcBus.RpcResponse rpcResponse,
            GatewayAuditReceipt receipt,
            String failureReason
    ) {}

    private final AgentGatewayRegistry registry;
    private final IntentAdaptiveRouter router;
    private final IntentAdaptiveRpcBus rpcBus;
    private final ZeroTrustSecurityGate securityGate;

    public SelfOrganizingGatewayCoordinator(
            AgentGatewayRegistry registry,
            IntentAdaptiveRouter router,
            IntentAdaptiveRpcBus rpcBus,
            ZeroTrustSecurityGate securityGate
    ) {
        this.registry = Objects.requireNonNull(registry, "注册中心不能为空");
        this.router = Objects.requireNonNull(router, "路由器不能为空");
        this.rpcBus = Objects.requireNonNull(rpcBus, "RPC 总线不能为空");
        this.securityGate = Objects.requireNonNull(securityGate, "安全门禁不能为空");
    }

    /**
     * 端到端调度处理网关请求
     */
    public GatewayResult dispatch(GatewayRequest request, Map<String, Double> currentLoads) {
        long startTime = System.currentTimeMillis();
        String payloadHash = computePayloadHash(request.payload());

        // 1. 意图自适应路由预选
        AgentGatewayRegistry.AgentMetadata candidate;
        try {
            candidate = router.route(request.intentQuery(), request.queryEmbedding(), currentLoads);
        } catch (Exception e) {
            return new GatewayResult(false, "NONE", "NONE", null, null, "意图路由失败: " + e.getMessage());
        }

        String targetAgentId = candidate.agentId();

        // 2. 零信任安全门禁与抗重放校验
        ZeroTrustSecurityGate.GateResult gateResult = securityGate.verifyAndAuthorize(
                request.msgId(),
                request.senderId(),
                targetAgentId,
                request.timestamp(),
                request.nonce(),
                payloadHash,
                request.clientSignature(),
                request.callerAttributes()
        );

        if (!gateResult.isAllowed()) {
            return new GatewayResult(
                    false, targetAgentId, "GATEWAY->BLOCKED", null, null,
                    "零信任安全拦截: " + gateResult.reason()
            );
        }

        // 3. 动态探活自愈检测（若节点不可用，触发自愈换路）
        Optional<AgentGatewayRegistry.AgentMetadata> targetCheck = registry.getAgent(targetAgentId);
        if (targetCheck.isEmpty() || targetCheck.get().status() == AgentGatewayRegistry.AgentStatus.DEAD) {
            // 触发剔除与重选备用节点
            registry.checkAndEvictStaleNodes(10000L);
            try {
                candidate = router.route(request.intentQuery(), request.queryEmbedding(), currentLoads);
                targetAgentId = candidate.agentId();
            } catch (Exception e) {
                return new GatewayResult(false, targetAgentId, "GATEWAY->EVICTED", null, null, "目标节点已死亡且无可用备用节点");
            }
        }

        // 4. 派发 RPC 请求
        IntentAdaptiveRpcBus.RpcResponse rpcResponse;
        try {
            CompletableFuture<IntentAdaptiveRpcBus.RpcResponse> future = rpcBus.sendAsync(
                    request.senderId(), targetAgentId, request.action(), request.payload()
            );
            rpcResponse = future.get(3000L, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return new GatewayResult(false, targetAgentId, "GATEWAY->RPC_FAILED", null, null, "RPC 调用失败: " + e.getMessage());
        }

        // 5. 签发不可变密码学存证凭单
        long rtt = System.currentTimeMillis() - startTime;
        String routingPath = String.format("%s -> Gateway -> %s", request.senderId(), targetAgentId);
        String receiptId = "RCPT-" + UUID.randomUUID().toString().substring(0, 8);

        GatewayAuditReceipt receipt = GatewayAuditReceipt.create(
                receiptId,
                request.msgId(),
                request.senderId(),
                targetAgentId,
                routingPath,
                rtt,
                request.nonce(),
                request.clientSignature(),
                gateResult.decision().name(),
                System.currentTimeMillis()
        );

        return new GatewayResult(rpcResponse.success(), targetAgentId, routingPath, rpcResponse, receipt, null);
    }

    private String computePayloadHash(Map<String, Object> payload) {
        String str = payload != null ? payload.toString() : "{}";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
