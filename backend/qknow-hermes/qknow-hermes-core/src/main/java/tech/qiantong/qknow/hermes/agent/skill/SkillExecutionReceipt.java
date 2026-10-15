package tech.qiantong.qknow.hermes.agent.skill;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * 技能回放执行不可变存证凭单 (SkillExecutionReceipt)
 * <p>
 * 纯 Java 21 Record 结构，内嵌毫秒级回放耗时、加速比、大模型 Token 节约估值以及 SHA-256 不可变防篡改签名。
 * 验签逻辑采用 {@link MessageDigest#isEqual(byte[], byte[])} 进行常量时间比对，
 * 彻底防御时序侧信道攻击风险。
 * </p>
 *
 * @author Achilles
 * @since 2026-10-15
 */
public record SkillExecutionReceipt(
        String receiptId,
        String traceSessionId,
        String matchedSkillId,
        double cosineSimilarity,
        boolean replaySuccess,
        long replayDurationMs,
        double speedupRatio,
        int estimatedTokenSaved,
        String signatureSha256,
        long timestampEpochMs
) implements Serializable {

    public SkillExecutionReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为 null");
        Objects.requireNonNull(traceSessionId, "traceSessionId 不能为 null");
        Objects.requireNonNull(matchedSkillId, "matchedSkillId 不能为 null");
        Objects.requireNonNull(signatureSha256, "signatureSha256 不能为 null");
    }

    /**
     * 工厂方法：计算不可变 SHA-256 签名并签发凭单
     */
    public static SkillExecutionReceipt createSigned(
            String receiptId,
            String traceSessionId,
            String matchedSkillId,
            double cosineSimilarity,
            boolean replaySuccess,
            long replayDurationMs,
            double speedupRatio,
            int estimatedTokenSaved,
            String secretKey
    ) {
        long now = System.currentTimeMillis();
        String effectiveSecret = (secretKey == null || secretKey.isBlank()) ? "HERMES_SKILL_DEFAULT_SECRET_2026" : secretKey;
        String rawPayload = String.format(Locale.ROOT, "%s|%s|%s|%.4f|%s|%d|%.2f|%d|%d|%s",
                receiptId, traceSessionId, matchedSkillId, cosineSimilarity,
                replaySuccess, replayDurationMs, speedupRatio, estimatedTokenSaved, now, effectiveSecret);
        String sig = computeSha256(rawPayload);

        return new SkillExecutionReceipt(
                receiptId, traceSessionId, matchedSkillId, cosineSimilarity,
                replaySuccess, replayDurationMs, speedupRatio, estimatedTokenSaved, sig, now
        );
    }

    /**
     * 常量时间自验真比对，防御时序侧信道攻击
     *
     * @param secretKey 共享验签密钥
     * @return 签名是否真实有效
     */
    public boolean verifySignature(String secretKey) {
        String effectiveSecret = (secretKey == null || secretKey.isBlank()) ? "HERMES_SKILL_DEFAULT_SECRET_2026" : secretKey;
        String rawPayload = String.format(Locale.ROOT, "%s|%s|%s|%.4f|%s|%d|%.2f|%d|%d|%s",
                receiptId, traceSessionId, matchedSkillId, cosineSimilarity,
                replaySuccess, replayDurationMs, speedupRatio, estimatedTokenSaved, timestampEpochMs, effectiveSecret);
        String expectedSig = computeSha256(rawPayload);

        return MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                signatureSha256.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("系统不可用：无法初始化 SHA-256 消息摘要实例", e);
        }
    }
}
