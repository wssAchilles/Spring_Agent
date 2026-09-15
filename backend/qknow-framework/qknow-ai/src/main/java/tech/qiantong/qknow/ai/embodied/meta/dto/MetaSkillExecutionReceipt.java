package tech.qiantong.qknow.ai.embodied.meta.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Phase 69: 不可变自适应技能执行与装配存证凭单 (Java 21 Record)
 * 封装元任务 ID、源/目标本体 ID、技能类型、自适应刚度、残差 MSE、自锁消除状态与 SHA-256 签名自验
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record MetaSkillExecutionReceipt(
        String receiptId,
        String sessionId,
        String workpieceId,
        String sourceEntityId,
        String targetEntityId,
        ContactSkillPrimitiveType primitiveType,
        double[] adaptedStiffness,
        double fiveStepResidualMse,
        boolean wedgingEliminated,
        String fsmState,
        long timestampNs,
        String signatureSha256
) {
    public static MetaSkillExecutionReceipt generate(
            String sessionId,
            String workpieceId,
            String sourceEntityId,
            String targetEntityId,
            ContactSkillPrimitiveType primitiveType,
            double[] adaptedStiffness,
            double fiveStepResidualMse,
            boolean wedgingEliminated,
            String fsmState
    ) {
        String receiptId = "RCP-META-" + System.nanoTime() + "-" + (int) (Math.random() * 10000);
        long now = System.nanoTime();
        String payload = buildPayload(receiptId, sessionId, workpieceId, sourceEntityId, targetEntityId,
                primitiveType, adaptedStiffness, fiveStepResidualMse, wedgingEliminated, fsmState, now);
        String sig = calculateSha256(payload);
        return new MetaSkillExecutionReceipt(
                receiptId, sessionId, workpieceId, sourceEntityId, targetEntityId,
                primitiveType,
                adaptedStiffness != null ? Arrays.copyOf(adaptedStiffness, adaptedStiffness.length) : new double[0],
                fiveStepResidualMse, wedgingEliminated, fsmState, now, sig
        );
    }

    public boolean verifyIntegrity() {
        if (signatureSha256 == null || signatureSha256.isBlank()) {
            return false;
        }
        String payload = buildPayload(receiptId, sessionId, workpieceId, sourceEntityId, targetEntityId,
                primitiveType, adaptedStiffness, fiveStepResidualMse, wedgingEliminated, fsmState, timestampNs);
        String expected = calculateSha256(payload);
        return expected.equalsIgnoreCase(signatureSha256);
    }

    private static String buildPayload(
            String receiptId, String sessionId, String workpieceId, String sourceId, String targetId,
            ContactSkillPrimitiveType type, double[] stiffness, double mse, boolean wedging, String state, long ts
    ) {
        StringBuilder sb = new StringBuilder();
        if (stiffness != null) {
            for (int i = 0; i < stiffness.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(String.format(Locale.US, "%.3f", stiffness[i]));
            }
        }
        return String.format(Locale.US, "%s|%s|%s|%s|%s|%s|%s|%.6f|%b|%s|%d",
                receiptId != null ? receiptId : "",
                sessionId != null ? sessionId : "",
                workpieceId != null ? workpieceId : "",
                sourceId != null ? sourceId : "",
                targetId != null ? targetId : "",
                type != null ? type.name() : "",
                sb.toString(),
                mse,
                wedging,
                state != null ? state : "",
                ts);
    }

    private static String calculateSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
