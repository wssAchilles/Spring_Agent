package tech.qiantong.qknow.ai.embodied.digitaltwin.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * 不可变因果数字孪生与装配自愈密码学存证凭单 (Java 21 Record)
 * <p>
 * 封装凭单唯一 ID、会话 ID、装配产线 ID、因果图拓扑哈希、根因工位节点 ID、
 * 仿真保真度级别、瞬时残差指标、自愈决策结果摘要、单步推演耗时微秒数、
 * 总线状态、时间戳与 SHA-256 密码学防篡改签名。
 */
public record CausalDigitalTwinReceipt(
        String receiptId,
        String sessionId,
        String assemblyLineId,
        String causalGraphHash,
        int rootCauseStationId,
        String fidelityLevel,
        double residualNorm,
        String selfHealingDecision,
        long executionDurationUs,
        String busState,
        long timestampMs,
        String sha256Signature
) {
    public CausalDigitalTwinReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(assemblyLineId, "assemblyLineId 不能为空");
        Objects.requireNonNull(causalGraphHash, "causalGraphHash 不能为空");
        Objects.requireNonNull(fidelityLevel, "fidelityLevel 不能为空");
        Objects.requireNonNull(selfHealingDecision, "selfHealingDecision 不能为空");
        Objects.requireNonNull(busState, "busState 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 工厂签名方法：格式化全要素字段并签署 SHA-256 防篡改签名
     */
    public static CausalDigitalTwinReceipt sign(
            String receiptId,
            String sessionId,
            String assemblyLineId,
            String causalGraphHash,
            int rootCauseStationId,
            String fidelityLevel,
            double residualNorm,
            String selfHealingDecision,
            long executionDurationUs,
            String busState,
            long timestampMs
    ) {
        String raw = String.format(Locale.US, "%s|%s|%s|%s|%d|%s|%.6f|%s|%d|%s|%d",
                receiptId, sessionId, assemblyLineId, causalGraphHash, rootCauseStationId,
                fidelityLevel, residualNorm, selfHealingDecision,
                executionDurationUs, busState, timestampMs);
        String signature = computeSha256(raw);
        return new CausalDigitalTwinReceipt(
                receiptId, sessionId, assemblyLineId, causalGraphHash, rootCauseStationId,
                fidelityLevel, residualNorm, selfHealingDecision,
                executionDurationUs, busState, timestampMs, signature
        );
    }

    /**
     * 密码学自检验真方法：核验签名是否真实且未经任何字段篡改
     */
    public boolean verifySignature() {
        String raw = String.format(Locale.US, "%s|%s|%s|%s|%d|%s|%.6f|%s|%d|%s|%d",
                receiptId, sessionId, assemblyLineId, causalGraphHash, rootCauseStationId,
                fidelityLevel, residualNorm, selfHealingDecision,
                executionDurationUs, busState, timestampMs);
        String expected = computeSha256(raw);
        return expected.equalsIgnoreCase(this.sha256Signature);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
