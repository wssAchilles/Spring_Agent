package tech.qiantong.qknow.hermes.flow.hitl.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Phase 155 核心不可变审计凭单 (Phase155MasterAuditReceipt)
 * <p>
 * 遵循系统工程总纲 (AGENTS.md) 密码学零时序泄漏铁律：
 * 1. 纯 Java 21 Record 不可变对象构建；
 * 2. 存证字段覆盖多智能体交接跃点数、DeepSeek 64-token 缓存块命中数、拉普拉斯谱剪枝节点数与千问超球面余弦保真度；
 * 3. 散列与验签强制使用 MessageDigest.isEqual() 常量时间比对，阻断时序侧信道反推。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record Phase155MasterAuditReceipt(
        String receiptId,
        String sessionTraceId,
        String tenantId,
        String primaryAgentId,
        int handoffHops,
        int cacheBlockHits64Token,
        int graphRagPrunedNodes,
        double hypersphericalCosineIntegrity,
        long executionLatencyMicros,
        String sha256Digest
) {

    /**
     * 构建不可变审计凭单并生成 SHA-256 密码学签名
     */
    public static Phase155MasterAuditReceipt create(
            String receiptId,
            String sessionTraceId,
            String tenantId,
            String primaryAgentId,
            int handoffHops,
            int cacheBlockHits64Token,
            int graphRagPrunedNodes,
            double hypersphericalCosineIntegrity,
            long executionLatencyMicros
    ) {
        String payload = buildSignPayload(
                receiptId, sessionTraceId, tenantId, primaryAgentId,
                handoffHops, cacheBlockHits64Token, graphRagPrunedNodes,
                hypersphericalCosineIntegrity, executionLatencyMicros
        );
        String digest = computeSha256(payload);
        return new Phase155MasterAuditReceipt(
                receiptId, sessionTraceId, tenantId, primaryAgentId,
                handoffHops, cacheBlockHits64Token, graphRagPrunedNodes,
                hypersphericalCosineIntegrity, executionLatencyMicros, digest
        );
    }

    /**
     * 密码学常量时间自验真 (防时序侧信道攻击)
     */
    public boolean verifyDigest() {
        if (sha256Digest == null || sha256Digest.isBlank()) {
            return false;
        }
        String payload = buildSignPayload(
                receiptId, sessionTraceId, tenantId, primaryAgentId,
                handoffHops, cacheBlockHits64Token, graphRagPrunedNodes,
                hypersphericalCosineIntegrity, executionLatencyMicros
        );
        String expectedDigest = computeSha256(payload);
        byte[] expected = HexFormat.of().parseHex(expectedDigest);
        byte[] actual = HexFormat.of().parseHex(sha256Digest);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String buildSignPayload(
            String receiptId,
            String sessionTraceId,
            String tenantId,
            String primaryAgentId,
            int handoffHops,
            int cacheBlockHits64Token,
            int graphRagPrunedNodes,
            double hypersphericalCosineIntegrity,
            long executionLatencyMicros
    ) {
        return String.format("%s:%s:%s:%s:%d:%d:%d:%.6f:%d",
                receiptId != null ? receiptId : "",
                sessionTraceId != null ? sessionTraceId : "",
                tenantId != null ? tenantId : "",
                primaryAgentId != null ? primaryAgentId : "",
                handoffHops,
                cacheBlockHits64Token,
                graphRagPrunedNodes,
                hypersphericalCosineIntegrity,
                executionLatencyMicros
        );
    }

    private static String computeSha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
