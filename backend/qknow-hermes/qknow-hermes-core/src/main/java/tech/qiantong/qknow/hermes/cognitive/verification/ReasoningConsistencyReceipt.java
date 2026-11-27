package tech.qiantong.qknow.hermes.cognitive.verification;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Ariadne / ConsisGuard 推理链双向一致性不可变审计凭单（Java 21 Record）。
 * <p>
 * 强制采用 {@link MessageDigest#isEqual(byte[], byte[])} 常量时间比对验签，严防时序侧信道泄漏。
 * </p>
 */
public record ReasoningConsistencyReceipt(
        String requestId,
        double forwardFaithfulness,
        double backwardAttribution,
        double counterfactualSensitivityPhi,
        double overallConsistencyScore,
        EvidenceGrade resultingGrade,
        boolean passed,
        boolean healingRequired,
        boolean causalDecoupledBlock,
        long timestampMs,
        String receiptMac
) {

    public static ReasoningConsistencyReceipt createSigned(
            String requestId,
            double forwardFaithfulness,
            double backwardAttribution,
            double counterfactualSensitivityPhi,
            double overallConsistencyScore,
            EvidenceGrade resultingGrade,
            boolean passed,
            boolean healingRequired,
            boolean causalDecoupledBlock,
            long timestampMs,
            String secretKey
    ) {
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-consistency-default-secret" : secretKey;
        String mac = computeMac(
                requestId, forwardFaithfulness, backwardAttribution, counterfactualSensitivityPhi,
                overallConsistencyScore, resultingGrade, passed, healingRequired, causalDecoupledBlock, timestampMs, safeKey
        );
        return new ReasoningConsistencyReceipt(
                requestId, forwardFaithfulness, backwardAttribution, counterfactualSensitivityPhi,
                overallConsistencyScore, resultingGrade, passed, healingRequired, causalDecoupledBlock, timestampMs, mac
        );
    }

    public boolean verifyMac(String secretKey) {
        if (receiptMac == null || receiptMac.isBlank()) {
            return false;
        }
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-consistency-default-secret" : secretKey;
        String expected = computeMac(
                requestId, forwardFaithfulness, backwardAttribution, counterfactualSensitivityPhi,
                overallConsistencyScore, resultingGrade, passed, healingRequired, causalDecoupledBlock, timestampMs, safeKey
        );
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receiptMac.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeMac(
            String requestId,
            double forwardFaithfulness,
            double backwardAttribution,
            double counterfactualSensitivityPhi,
            double overallConsistencyScore,
            EvidenceGrade resultingGrade,
            boolean passed,
            boolean healingRequired,
            boolean causalDecoupledBlock,
            long timestampMs,
            String secretKey
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = String.format(
                    Locale.ROOT,
                    "%s|%.6f|%.6f|%.6f|%.6f|%s|%b|%b|%b|%d",
                    requestId, forwardFaithfulness, backwardAttribution, counterfactualSensitivityPhi,
                    overallConsistencyScore, resultingGrade.name(), passed, healingRequired, causalDecoupledBlock, timestampMs
            );
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("ReasoningConsistencyReceipt HMAC 计算失败", e);
        }
    }
}
