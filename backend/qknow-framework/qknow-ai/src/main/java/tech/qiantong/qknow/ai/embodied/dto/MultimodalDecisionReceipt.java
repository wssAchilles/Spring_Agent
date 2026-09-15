package tech.qiantong.qknow.ai.embodied.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

/**
 * 具身多模态决策防篡改存证凭单 (Java 21 Record, SHA-256 签名自验)
 *
 * @param receiptId              凭单全局唯一业务标识
 * @param sessionId              具身智能体会话标识
 * @param inputMultimodalHash    多源输入时序切片 SHA-256 摘要
 * @param causalMaskValid        因果注意力掩码下三角校验判定
 * @param executedActionId       最终签发执行的动作标识
 * @param executionDurationMs    状态闭环全流程耗时 (ms)
 * @param signatureHash          防篡改自验签名哈希
 * @param timestampMs            签发物理毫秒时间戳
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record MultimodalDecisionReceipt(
        String receiptId,
        String sessionId,
        String inputMultimodalHash,
        boolean causalMaskValid,
        String executedActionId,
        long executionDurationMs,
        String signatureHash,
        long timestampMs
) {

    public static MultimodalDecisionReceipt generate(
            String sessionId,
            String inputMultimodalHash,
            boolean causalMaskValid,
            String executedActionId,
            long executionDurationMs
    ) {
        long now = System.currentTimeMillis();
        String receiptId = "RCPT-EMB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String signature = computeSignature(receiptId, sessionId, inputMultimodalHash, causalMaskValid, executedActionId, executionDurationMs, now);
        return new MultimodalDecisionReceipt(
                receiptId, sessionId, inputMultimodalHash, causalMaskValid, executedActionId, executionDurationMs, signature, now
        );
    }

    /**
     * 自验凭单完整性与真实性，严防事后篡改
     */
    public boolean verifyIntegrity() {
        String expected = computeSignature(receiptId, sessionId, inputMultimodalHash, causalMaskValid, executedActionId, executionDurationMs, timestampMs);
        return expected.equalsIgnoreCase(signatureHash);
    }

    private static String computeSignature(
            String receiptId, String sessionId, String inputHash,
            boolean maskValid, String actionId, long duration, long ts
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = receiptId + "|" + sessionId + "|" + inputHash + "|" + maskValid + "|" + actionId + "|" + duration + "|" + ts;
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "HASH_FALLBACK_" + receiptId + "_" + ts;
        }
    }
}
