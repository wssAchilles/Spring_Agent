package tech.qiantong.qknow.ai.geosync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Phase 61: 跨数据中心分布式智能体状态同步不可变存证凭单 (Record)
 * <p>
 * 具备密码学抗篡改特性，内置 SHA-256 签名计算与自校验能力。
 */
public record GeoStateAuditReceipt(
        String receiptId,
        long syncRoundId,
        String originRegion,
        String targetRegion,
        String vectorClockSnapshot,
        String convergedStateHash,
        boolean conflictResolved,
        boolean degraded,
        String decisionSummary,
        long timestamp,
        String sha256Signature
) {

    /**
     * 工厂方法：计算并创建带有 SHA-256 签名的跨域存证凭据
     */
    public static GeoStateAuditReceipt create(
            String receiptId,
            long syncRoundId,
            String originRegion,
            String targetRegion,
            String vectorClockSnapshot,
            String convergedStateHash,
            boolean conflictResolved,
            boolean degraded,
            String decisionSummary,
            long timestamp
    ) {
        String signature = computeHash(
                receiptId, syncRoundId, originRegion, targetRegion,
                vectorClockSnapshot, convergedStateHash, conflictResolved,
                degraded, decisionSummary, timestamp
        );
        return new GeoStateAuditReceipt(
                receiptId, syncRoundId, originRegion, targetRegion,
                vectorClockSnapshot, convergedStateHash, conflictResolved,
                degraded, decisionSummary, timestamp, signature
        );
    }

    /**
     * 自验签名是否与账本内容严格匹配
     */
    public boolean verifySignature() {
        String expectedHash = computeHash(
                receiptId, syncRoundId, originRegion, targetRegion,
                vectorClockSnapshot, convergedStateHash, conflictResolved,
                degraded, decisionSummary, timestamp
        );
        return Objects.equals(this.sha256Signature, expectedHash);
    }

    private static String computeHash(
            String receiptId,
            long syncRoundId,
            String originRegion,
            String targetRegion,
            String vectorClockSnapshot,
            String convergedStateHash,
            boolean conflictResolved,
            boolean degraded,
            String decisionSummary,
            long timestamp
    ) {
        String raw = String.format("%s|%d|%s|%s|%s|%s|%b|%b|%s|%d",
                receiptId, syncRoundId, originRegion, targetRegion,
                vectorClockSnapshot, convergedStateHash, conflictResolved,
                degraded, decisionSummary, timestamp);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("缺失 SHA-256 摘要算法实现", e);
        }
    }
}
