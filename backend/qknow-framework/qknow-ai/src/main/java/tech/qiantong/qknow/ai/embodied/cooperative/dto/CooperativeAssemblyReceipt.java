package tech.qiantong.qknow.ai.embodied.cooperative.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Phase 68: 不可变协同装配密码学存证凭单 (Java 21 Record 格式)
 * 内置会话 ID、工件 ID、参与智能体列表、内力残差、接触力均值、装配位置误差与 SHA-256 签名自验
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record CooperativeAssemblyReceipt(
        String receiptId,
        String sessionId,
        String workpieceId,
        List<String> participatingAgents,
        double internalForceResidual,    // 内力残差 (N)，目标 <= 30.0N
        double meanContactForce,         // 接触力均值 (N)
        double assemblyPositionError,    // 装配几何对准误差 (m)，目标 <= 0.001m
        String fsmState,                 // 终态 FSM 状态，如 "LOCKED" 或 "DEGRADED_SOFT_LANDING"
        long timestampNs,
        String signatureSha256
) {
    public static CooperativeAssemblyReceipt generate(
            String sessionId,
            String workpieceId,
            List<String> participatingAgents,
            double internalForceResidual,
            double meanContactForce,
            double assemblyPositionError,
            String fsmState
    ) {
        String receiptId = "RCP-COOP-" + System.nanoTime() + "-" + (int) (Math.random() * 10000);
        long now = System.nanoTime();
        String payload = buildPayload(receiptId, sessionId, workpieceId, participatingAgents,
                internalForceResidual, meanContactForce, assemblyPositionError, fsmState, now);
        String sig = calculateSha256(payload);
        return new CooperativeAssemblyReceipt(
                receiptId, sessionId, workpieceId,
                participatingAgents != null ? List.copyOf(participatingAgents) : List.of(),
                internalForceResidual, meanContactForce, assemblyPositionError,
                fsmState, now, sig
        );
    }

    public boolean verifyIntegrity() {
        if (signatureSha256 == null || signatureSha256.isBlank()) {
            return false;
        }
        String payload = buildPayload(receiptId, sessionId, workpieceId, participatingAgents,
                internalForceResidual, meanContactForce, assemblyPositionError, fsmState, timestampNs);
        String expected = calculateSha256(payload);
        return expected.equalsIgnoreCase(signatureSha256);
    }

    private static String buildPayload(
            String receiptId, String sessionId, String workpieceId, List<String> agents,
            double internalResidual, double meanContact, double posError, String state, long ts
    ) {
        return String.format(Locale.US, "%s|%s|%s|%s|%.6f|%.6f|%.6f|%s|%d",
                receiptId != null ? receiptId : "",
                sessionId != null ? sessionId : "",
                workpieceId != null ? workpieceId : "",
                agents != null ? String.join(",", agents) : "",
                internalResidual, meanContact, posError,
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
