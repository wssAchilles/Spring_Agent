package tech.qiantong.qknow.hermes.flow.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * 企业级自愈认知编排与工具网关不可变审计凭单 (EnterpriseArchitectureAuditReceipt)
 * <p>
 * 遵循高可用与零时序泄漏密码学工程铁律：
 * 1. 采用纯 Java 21 Record 不可变对象构建；
 * 2. 存证超步调度、WAL 状态落盘、确定性规则评估与两阶段工具投影指标；
 * 3. 验签采用 MessageDigest.isEqual 常量时间比对，杜绝时序侧信道漏洞。
 * </p>
 */
public record EnterpriseArchitectureAuditReceipt(
        String executionId,
        String flowOrGraphId,
        int totalSupersteps,
        int completedNodesCount,
        boolean walSnapshotPersisted,
        boolean deterministicRulesPassed,
        double toolTokenCompressionRatio,
        long virtualThreadLatencyMs,
        String sha256Signature,
        long timestampMs,
        Map<String, Object> metadata
) {
    public EnterpriseArchitectureAuditReceipt {
        if (executionId == null || flowOrGraphId == null) {
            throw new IllegalArgumentException("executionId 与 flowOrGraphId 不可为空");
        }
    }

    /**
     * 构建包含自签名的不可变凭单实例
     */
    public static EnterpriseArchitectureAuditReceipt createSigned(
            String executionId,
            String flowOrGraphId,
            int totalSupersteps,
            int completedNodesCount,
            boolean walSnapshotPersisted,
            boolean deterministicRulesPassed,
            double toolTokenCompressionRatio,
            long virtualThreadLatencyMs,
            Map<String, Object> metadata) {

        long now = System.currentTimeMillis();
        String raw = computeRawPayload(
                executionId, flowOrGraphId, totalSupersteps, completedNodesCount,
                walSnapshotPersisted, deterministicRulesPassed, toolTokenCompressionRatio,
                virtualThreadLatencyMs, now
        );
        String signature = computeSha256(raw);

        return new EnterpriseArchitectureAuditReceipt(
                executionId,
                flowOrGraphId,
                totalSupersteps,
                completedNodesCount,
                walSnapshotPersisted,
                deterministicRulesPassed,
                toolTokenCompressionRatio,
                virtualThreadLatencyMs,
                signature,
                now,
                metadata != null ? Map.copyOf(metadata) : Map.of()
        );
    }

    /**
     * 校验自签名完整性 (MessageDigest.isEqual 常量时间比对)
     */
    public boolean verifyIntegrity() {
        if (sha256Signature == null) {
            return false;
        }
        String raw = computeRawPayload(
                executionId, flowOrGraphId, totalSupersteps, completedNodesCount,
                walSnapshotPersisted, deterministicRulesPassed, toolTokenCompressionRatio,
                virtualThreadLatencyMs, timestampMs
        );
        String expectedHash = computeSha256(raw);

        byte[] expectedBytes = expectedHash.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.toLowerCase().getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String computeRawPayload(
            String executionId, String flowOrGraphId, int supersteps, int completedNodes,
            boolean walPersisted, boolean rulesPassed, double compression,
            long latencyMs, long timestampMs) {
        return String.format("%s|%s|%d|%d|%b|%b|%.4f|%d|%d",
                executionId, flowOrGraphId, supersteps, completedNodes,
                walPersisted, rulesPassed, compression, latencyMs, timestampMs);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
