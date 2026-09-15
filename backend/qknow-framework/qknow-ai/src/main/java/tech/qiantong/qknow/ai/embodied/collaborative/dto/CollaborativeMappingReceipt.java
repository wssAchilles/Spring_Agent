package tech.qiantong.qknow.ai.embodied.collaborative.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * 不可变协同建图存证凭单 (Java 21 Record)
 * 内置会话 ID、参与智能体列表、对齐残差 RMSE、次模增益分配比、孪生同步保真度与 SHA-256 防篡改签名
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record CollaborativeMappingReceipt(
        String sessionId,
        List<String> participatingAgents,
        double alignmentRmse,
        double submodularGainRatio,
        double twinFidelity,
        double latencyMs,
        long timestamp,
        String signature
) {

    public CollaborativeMappingReceipt(
            String sessionId,
            List<String> participatingAgents,
            double alignmentRmse,
            double submodularGainRatio,
            double twinFidelity,
            double latencyMs,
            long timestamp
    ) {
        this(
                sessionId,
                participatingAgents != null ? List.copyOf(participatingAgents) : List.of(),
                alignmentRmse,
                submodularGainRatio,
                twinFidelity,
                latencyMs,
                timestamp,
                calculateSha256(sessionId, participatingAgents, alignmentRmse, submodularGainRatio, twinFidelity, latencyMs, timestamp)
        );
    }

    /**
     * 零信任凭单完整性自验证
     *
     * @return 签名匹配返回 true，被篡改返回 false
     */
    public boolean verifyIntegrity() {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = calculateSha256(
                sessionId,
                participatingAgents,
                alignmentRmse,
                submodularGainRatio,
                twinFidelity,
                latencyMs,
                timestamp
        );
        return expected.equals(signature);
    }

    private static String calculateSha256(
            String sessionId,
            List<String> agents,
            double alignmentRmse,
            double submodularGainRatio,
            double twinFidelity,
            double latencyMs,
            long timestamp
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = String.format(Locale.US,
                    "%s|%s|%.6f|%.6f|%.6f|%.2f|%d",
                    sessionId != null ? sessionId : "",
                    agents != null ? String.join(",", agents) : "",
                    alignmentRmse,
                    submodularGainRatio,
                    twinFidelity,
                    latencyMs,
                    timestamp
            );
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
