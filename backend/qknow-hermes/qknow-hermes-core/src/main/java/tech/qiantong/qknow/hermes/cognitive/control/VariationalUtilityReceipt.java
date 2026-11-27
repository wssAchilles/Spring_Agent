package tech.qiantong.qknow.hermes.cognitive.control;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 变分真实效用后验不可变凭单（Java 21 Record）。
 * <p>
 * 替代旁路观测期硬编码 {@code 0.92 / 0.35} 常数，真实记录 {@code U_t} 各项分解分量与 HMAC-SHA256 签名。
 * </p>
 */
public record VariationalUtilityReceipt(
        String requestId,
        double consistencyScore,
        double hoarePassRate,
        long uncachedTokens,
        long maxBudgetTokens,
        long latencyMs,
        long maxBudgetLatencyMs,
        double realizedUtility,
        long timestampMs,
        String receiptMac
) {

    public static VariationalUtilityReceipt createSigned(
            String requestId,
            double consistencyScore,
            double hoarePassRate,
            long uncachedTokens,
            long maxBudgetTokens,
            long latencyMs,
            long maxBudgetLatencyMs,
            double realizedUtility,
            long timestampMs,
            String secretKey
    ) {
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-utility-default-secret" : secretKey;
        String mac = computeMac(requestId, consistencyScore, hoarePassRate, uncachedTokens,
                maxBudgetTokens, latencyMs, maxBudgetLatencyMs, realizedUtility, timestampMs, safeKey);
        return new VariationalUtilityReceipt(
                requestId, consistencyScore, hoarePassRate, uncachedTokens,
                maxBudgetTokens, latencyMs, maxBudgetLatencyMs, realizedUtility, timestampMs, mac
        );
    }

    public boolean verifyMac(String secretKey) {
        if (receiptMac == null || receiptMac.isBlank()) {
            return false;
        }
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-utility-default-secret" : secretKey;
        String expected = computeMac(requestId, consistencyScore, hoarePassRate, uncachedTokens,
                maxBudgetTokens, latencyMs, maxBudgetLatencyMs, realizedUtility, timestampMs, safeKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receiptMac.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeMac(
            String requestId,
            double consistencyScore,
            double hoarePassRate,
            long uncachedTokens,
            long maxBudgetTokens,
            long latencyMs,
            long maxBudgetLatencyMs,
            double realizedUtility,
            long timestampMs,
            String secretKey
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = String.format(
                    Locale.ROOT,
                    "%s|%.6f|%.6f|%d|%d|%d|%d|%.6f|%d",
                    requestId, consistencyScore, hoarePassRate, uncachedTokens,
                    maxBudgetTokens, latencyMs, maxBudgetLatencyMs, realizedUtility, timestampMs
            );
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VariationalUtilityReceipt HMAC 计算失败", e);
        }
    }
}
