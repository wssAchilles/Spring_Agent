package tech.qiantong.qknow.hermes.cognitive.selfhealing.dto;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * 不可变自愈认知执行存证凭单 Record
 * 记录反思轮次、自愈变异标记、记忆压缩比率、图谱接地实体数与 SHA-256 密码学自签名
 */
public record CognitiveExecutionReceipt(
        String receiptId,
        String sessionId,
        String userQuery,
        int totalAttempts,
        boolean selfHealingApplied,
        double memoryCompressionRatio,
        int groundedEntityCount,
        long executionLatencyMicros,
        String busStatus,
        String signature
) {
    /**
     * 密码学验真方法：校验载荷 SHA-256 哈希防篡改
     */
    public boolean verifySignature() {
        String payload = receiptId + ":" + sessionId + ":" + totalAttempts + ":" + selfHealingApplied + ":" + groundedEntityCount + ":" + busStatus;
        String expected = DigestUtils.sha256Hex(payload);
        return expected.equalsIgnoreCase(signature);
    }
}
