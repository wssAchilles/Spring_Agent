package tech.qiantong.qknow.hermes.cognitive.verification;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 带密码学溯源签名的工具执行见证凭据（EG-VAR Witness，Java 21 Record）。
 * <p>
 * 强制采用 {@link MessageDigest#isEqual(byte[], byte[])} 常量时间验签，杜绝时序侧信道风险。
 * </p>
 */
public record AttestedToolWitness(
        String requestId,
        String toolName,
        String inputDigestSha256,
        String outputDigestSha256,
        EvidenceGrade grade,
        long timestampMs,
        String witnessMac
) {

    public static AttestedToolWitness createSigned(
            String requestId,
            String toolName,
            String rawInput,
            String rawOutput,
            EvidenceGrade grade,
            long timestampMs,
            String secretKey
    ) {
        String inDigest = sha256Hex(rawInput == null ? "" : rawInput);
        String outDigest = sha256Hex(rawOutput == null ? "" : rawOutput);
        EvidenceGrade safeGrade = grade == null ? EvidenceGrade.TOOL_WITNESS : grade;
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-witness-default-secret" : secretKey;
        String mac = computeMac(requestId, toolName, inDigest, outDigest, safeGrade, timestampMs, safeKey);
        return new AttestedToolWitness(requestId, toolName, inDigest, outDigest, safeGrade, timestampMs, mac);
    }

    public boolean verifyMac(String secretKey) {
        if (witnessMac == null || witnessMac.isBlank()) {
            return false;
        }
        String safeKey = (secretKey == null || secretKey.isBlank()) ? "hermes-witness-default-secret" : secretKey;
        String expected = computeMac(requestId, toolName, inputDigestSha256, outputDigestSha256, grade, timestampMs, safeKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                witnessMac.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeMac(
            String requestId,
            String toolName,
            String inputDigestSha256,
            String outputDigestSha256,
            EvidenceGrade grade,
            long timestampMs,
            String secretKey
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = String.format(
                    Locale.ROOT,
                    "%s|%s|%s|%s|%s|%d",
                    requestId, toolName, inputDigestSha256, outputDigestSha256, grade.name(), timestampMs
            );
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AttestedToolWitness HMAC 计算失败", e);
        }
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
