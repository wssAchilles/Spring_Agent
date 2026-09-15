package tech.qiantong.qknow.ai.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 56 多智能体自组织网关与零信任协议栈 专属契约单元测试
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
class Phase56GatewayExecutionContractTest {

    private static final String SHARED_SECRET = "secret-key-p56-test-2026";
    private AgentGatewayRegistry registry;
    private IntentAdaptiveRouter router;
    private IntentAdaptiveRpcBus rpcBus;
    private ZeroTrustSecurityGate securityGate;
    private SelfOrganizingGatewayCoordinator coordinator;

    @BeforeEach
    void setUp() {
        registry = new AgentGatewayRegistry();
        router = new IntentAdaptiveRouter(registry);
        rpcBus = new IntentAdaptiveRpcBus();
        securityGate = new ZeroTrustSecurityGate(SHARED_SECRET);
        coordinator = new SelfOrganizingGatewayCoordinator(registry, router, rpcBus, securityGate);
    }

    private static float[] generateNormalizedVector(int seed) {
        float[] v = new float[1536];
        Random rnd = new Random(seed);
        float norm = 0.0f;
        for (int i = 0; i < 1536; i++) {
            v[i] = rnd.nextFloat() - 0.5f;
            norm += v[i] * v[i];
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < 1536; i++) {
            v[i] /= norm;
        }
        return v;
    }

    private static String computePayloadHash(Map<String, Object> payload) {
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

    @Test
    @DisplayName("契约测试 1: 凭单不可变性与 SHA-256 密码学自验一致性")
    void testReceiptIntegrityAndSha256Verification() {
        GatewayAuditReceipt receipt = GatewayAuditReceipt.create(
                "RCPT-001", "MSG-100", "ClientA", "AgentAlpha",
                "ClientA -> Gateway -> AgentAlpha", 12L, "nonce-999",
                "sig-abc", "ALLOWED", System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "凭单 SHA-256 自验必须通过");

        GatewayAuditReceipt tampered = new GatewayAuditReceipt(
                receipt.receiptId(), receipt.msgId(), receipt.senderId(),
                "TamperedAgent", receipt.routingPath(), receipt.rttLatencyMs(),
                receipt.nonce(), receipt.clientSignature(), receipt.abacDecision(),
                receipt.timestamp(), receipt.receiptHash()
        );
        assertFalse(tampered.verifyIntegrity(), "被篡改目标 Agent 的凭单验真必须失败");
    }

    @Test
    @DisplayName("契约测试 2: 智能体自组织注册、心跳刷新与状态流转")
    void testAgentRegistrationAndHeartbeat() {
        float[] vec = generateNormalizedVector(101);
        AgentGatewayRegistry.AgentMetadata meta = new AgentGatewayRegistry.AgentMetadata(
                "Agent-1", "SQL Analyst", "http://10.0.0.1:8080",
                List.of("sql", "query", "database"), vec, 0.9,
                AgentGatewayRegistry.AgentStatus.ONLINE, System.currentTimeMillis()
        );
        registry.register(meta);

        assertEquals(1, registry.totalRegisteredCount());
        assertEquals(1, registry.getHealthyAgents().size());

        // 刷新心跳（降级亚健康）
        boolean ok = registry.heartbeat("Agent-1", 0.4);
        assertTrue(ok);
        assertEquals(AgentGatewayRegistry.AgentStatus.DEGRADED, registry.getAgent("Agent-1").get().status());

        // 刷新心跳（恢复健康）
        registry.heartbeat("Agent-1", 0.95);
        assertEquals(AgentGatewayRegistry.AgentStatus.ONLINE, registry.getAgent("Agent-1").get().status());

        // 注销
        registry.deregister("Agent-1");
        assertEquals(0, registry.totalRegisteredCount());
    }

    @Test
    @DisplayName("契约测试 3: 千问 1536 维超球面意图测地线路由精准匹配")
    void testIntentAdaptiveRoutingAccurateMatch() {
        float[] vecA = generateNormalizedVector(1001);
        float[] vecB = generateNormalizedVector(2002);

        // 注册两个异构智能体
        registry.register(new AgentGatewayRegistry.AgentMetadata(
                "Agent-Vision", "Vision Specialist", "http://10.0.0.1:8081",
                List.of("image", "ocr"), vecA, 0.9,
                AgentGatewayRegistry.AgentStatus.ONLINE, System.currentTimeMillis()
        ));
        registry.register(new AgentGatewayRegistry.AgentMetadata(
                "Agent-NLP", "NLP Specialist", "http://10.0.0.2:8082",
                List.of("nlp", "translation"), vecB, 0.9,
                AgentGatewayRegistry.AgentStatus.ONLINE, System.currentTimeMillis()
        ));

        // 针对 vecA 的近邻意图向量（加入极微小扰动）
        float[] queryNearA = Arrays.copyOf(vecA, vecA.length);
        queryNearA[0] += 0.001f;

        Map<String, Double> loads = Map.of("Agent-Vision", 0.1, "Agent-NLP", 0.1);
        AgentGatewayRegistry.AgentMetadata chosen = router.route("图像识别分析", queryNearA, loads);

        assertNotNull(chosen);
        assertEquals("Agent-Vision", chosen.agentId(), "向量测地线距离最近应精准路由至 Agent-Vision");
    }

    @Test
    @DisplayName("契约测试 4: 相同 Nonce 重放攻击 100% 物理硬拦截")
    void testZeroTrustReplayAttackInterception() {
        long now = System.currentTimeMillis();
        String nonce = "random-nonce-replay-001";
        String msgId = "MSG-REPLAY-1";
        String senderId = "ClientA";
        String targetAgentId = "AgentAlpha";
        String payloadHash = "hash-payload-abc";

        String signature = securityGate.signHmacSha256(msgId, senderId, targetAgentId, now, nonce, payloadHash);

        // 第一次请求放行
        ZeroTrustSecurityGate.GateResult first = securityGate.verifyAndAuthorize(
                msgId, senderId, targetAgentId, now, nonce, payloadHash, signature, Map.of()
        );
        assertTrue(first.isAllowed(), "初次提交合法报文应放行");

        // 相同 Nonce 重放拦截
        ZeroTrustSecurityGate.GateResult replay = securityGate.verifyAndAuthorize(
                msgId, senderId, targetAgentId, now, nonce, payloadHash, signature, Map.of()
        );
        assertFalse(replay.isAllowed(), "重放相同报文必须被拦截");
        assertEquals(ZeroTrustSecurityGate.Decision.REPLAY_ATTACK_DETECTED, replay.decision());
    }

    @Test
    @DisplayName("契约测试 5: 超过 60s 窗口的过期消息 100% 阻断")
    void testZeroTrustExpiredTimestampInterception() {
        long expiredTime = System.currentTimeMillis() - 70000L; // 70秒前
        String nonce = "nonce-expired-001";
        String msgId = "MSG-EXP-1";
        String senderId = "ClientA";
        String targetAgentId = "AgentAlpha";
        String payloadHash = "hash-123";

        String signature = securityGate.signHmacSha256(msgId, senderId, targetAgentId, expiredTime, nonce, payloadHash);

        ZeroTrustSecurityGate.GateResult res = securityGate.verifyAndAuthorize(
                msgId, senderId, targetAgentId, expiredTime, nonce, payloadHash, signature, Map.of()
        );
        assertFalse(res.isAllowed(), "超过 60s 时间倾斜窗的报文必须阻断");
        assertEquals(ZeroTrustSecurityGate.Decision.EXPIRED_TIMESTAMP, res.decision());
    }

    @Test
    @DisplayName("契约测试 6: 报文被篡改或伪造签名 100% 验签失败拦截")
    void testZeroTrustSignatureTamperingInterception() {
        long now = System.currentTimeMillis();
        String nonce = "nonce-tamper-001";
        String msgId = "MSG-TAMPER-1";
        String senderId = "ClientA";
        String targetAgentId = "AgentAlpha";
        String payloadHash = "hash-payload-real";

        // 提供伪造的非法签名
        String badSignature = "fake-signature-ffffffffffffffffffffff";

        ZeroTrustSecurityGate.GateResult res = securityGate.verifyAndAuthorize(
                msgId, senderId, targetAgentId, now, nonce, payloadHash, badSignature, Map.of()
        );
        assertFalse(res.isAllowed(), "伪造或篡改签名必须被拦截");
        assertEquals(ZeroTrustSecurityGate.Decision.INVALID_SIGNATURE, res.decision());
    }

    @Test
    @DisplayName("契约测试 7: 假死/故障节点探活失败毫秒级剔除并自适应重新路由")
    void testNodeFailureDetectionAndDynamicRerouting() {
        // 注册主节点与备节点
        float[] vec = generateNormalizedVector(555);
        registry.register(new AgentGatewayRegistry.AgentMetadata(
                "Agent-Primary", "Primary Node", "http://10.0.0.1",
                List.of("compute"), vec, 0.9,
                AgentGatewayRegistry.AgentStatus.ONLINE, System.currentTimeMillis() - 20000L // 已超时20秒
        ));
        registry.register(new AgentGatewayRegistry.AgentMetadata(
                "Agent-Backup", "Backup Node", "http://10.0.0.2",
                List.of("compute"), vec, 0.9,
                AgentGatewayRegistry.AgentStatus.ONLINE, System.currentTimeMillis() // 活跃心跳
        ));

        // 触发探活与淘汰
        int evicted = registry.checkAndEvictStaleNodes(15000L);
        assertEquals(1, evicted, "应精准剔除 1 个假死超时节点");
        assertEquals(AgentGatewayRegistry.AgentStatus.DEAD, registry.getAgent("Agent-Primary").get().status());

        // 路由应自动避开 DEAD 节点，选拔出活跃的 Backup 节点
        AgentGatewayRegistry.AgentMetadata candidate = router.route("compute", vec, Map.of());
        assertEquals("Agent-Backup", candidate.agentId(), "故障发生后应自动重选至活跃备用节点");
    }

    @Test
    @DisplayName("契约测试 8: 端到端网关调度协同成功，RPC 派发并签发不可变存证凭单")
    void testEndToEndGatewayCoordinationSuccess() {
        float[] vec = generateNormalizedVector(777);
        registry.register(new AgentGatewayRegistry.AgentMetadata(
                "Agent-Core", "Core Worker", "http://10.0.0.10:9090",
                List.of("general", "worker"), vec, 0.95,
                AgentGatewayRegistry.AgentStatus.ONLINE, System.currentTimeMillis()
        ));

        long now = System.currentTimeMillis();
        String nonce = "nonce-e2e-" + UUID.randomUUID();
        String msgId = "MSG-E2E-001";
        String senderId = "Caller-1";
        Map<String, Object> payload = Map.of("task", "evaluate", "value", 100);
        String payloadHash = computePayloadHash(payload);

        String signature = securityGate.signHmacSha256(msgId, senderId, "Agent-Core", now, nonce, payloadHash);

        SelfOrganizingGatewayCoordinator.GatewayRequest req = new SelfOrganizingGatewayCoordinator.GatewayRequest(
                msgId, senderId, "general task", vec, "EVALUATE",
                payload, now, nonce, signature, Map.of("role", "admin")
        );

        SelfOrganizingGatewayCoordinator.GatewayResult result = coordinator.dispatch(req, Map.of());

        assertTrue(result.success(), "端到端网关派发调用必须成功");
        assertEquals("Agent-Core", result.targetAgentId());
        assertNotNull(result.rpcResponse());
        assertTrue(result.rpcResponse().success());
        assertNotNull(result.receipt());
        assertTrue(result.receipt().verifyIntegrity(), "返回的存证凭单自验必须有效");
        assertEquals("ALLOWED", result.receipt().abacDecision());
    }
}
