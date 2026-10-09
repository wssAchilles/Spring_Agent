package tech.qiantong.qknow.hermes.flow.hitl.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Phase 154 企业级 Agent 基础设施与不可变审计凭单
 * <p>
 * 纯 Java 21 Record 格式，具备常量时间 SHA-256 密码学自验真能力，杜绝时序侧信道泄漏。
 * </p>
 */
public record Phase154MasterAuditReceipt(
        String receiptId,
        String runtimeId,
        String hitlTicketId,
        String mcpServerId,
        int contextualChunksProcessed,
        double cosineSimilarityIntegrity,
        long executionLatencyMicros,
        String sha256Digest
) {
    public Phase154MasterAuditReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(runtimeId, "runtimeId 不能为空");
        Objects.requireNonNull(hitlTicketId, "hitlTicketId 不能为空");
        Objects.requireNonNull(mcpServerId, "mcpServerId 不能为空");
        Objects.requireNonNull(sha256Digest, "sha256Digest 不能为空");
    }

    /**
     * 工厂构建方法，自动计算并填充 SHA-256 摘要
     */
    public static Phase154MasterAuditReceipt create(
            String receiptId,
            String runtimeId,
            String hitlTicketId,
            String mcpServerId,
            int contextualChunksProcessed,
            double cosineSimilarityIntegrity,
            long executionLatencyMicros
    ) {
        String payload = receiptId + ":" + runtimeId + ":" + hitlTicketId + ":" + mcpServerId + ":" + contextualChunksProcessed;
        String digest = computeSha256(payload);
        return new Phase154MasterAuditReceipt(
                receiptId,
                runtimeId,
                hitlTicketId,
                mcpServerId,
                contextualChunksProcessed,
                cosineSimilarityIntegrity,
                executionLatencyMicros,
                digest
        );
    }

    /**
     * 基于 MessageDigest.isEqual 常量时间比对防时序侧信道
     */
    public boolean verifyDigest() {
        String payload = receiptId + ":" + runtimeId + ":" + hitlTicketId + ":" + mcpServerId + ":" + contextualChunksProcessed;
        String expectedDigest = computeSha256(payload);
        byte[] expected = HexFormat.of().parseHex(expectedDigest);
        byte[] actual = HexFormat.of().parseHex(sha256Digest);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
