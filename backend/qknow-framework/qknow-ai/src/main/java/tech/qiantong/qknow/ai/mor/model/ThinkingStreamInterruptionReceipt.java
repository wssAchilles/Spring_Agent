package tech.qiantong.qknow.ai.mor.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 思考流中断与因果自愈密码学存证凭单 (Java 21 Record)
 * 记录 DeepSeek R1 思考死循环截断事件、Token 节省量、信息熵指纹与自愈动作，使用 SHA-256 自签名防篡改
 */
public record ThinkingStreamInterruptionReceipt(
        String sessionId,
        String traceId,
        String interruptionReason,
        long thinkingTokensSpent,
        long tokensSaved,
        double entropyScore,
        double ngramRepetitionScore,
        String healingAction,
        Instant timestamp,
        String signature
) {
    /**
     * 工厂方法：创建不可变存证凭单并生成密码学自签名
     */
    public static ThinkingStreamInterruptionReceipt create(
            String sessionId,
            String traceId,
            String interruptionReason,
            long thinkingTokensSpent,
            long tokensSaved,
            double entropyScore,
            double ngramRepetitionScore,
            String healingAction
    ) {
        Instant now = Instant.now();
        String rawContent = String.join("|",
                sessionId != null ? sessionId : "",
                traceId != null ? traceId : "",
                interruptionReason != null ? interruptionReason : "",
                String.valueOf(thinkingTokensSpent),
                String.valueOf(tokensSaved),
                String.format("%.4f", entropyScore),
                String.format("%.4f", ngramRepetitionScore),
                healingAction != null ? healingAction : "",
                now.toString()
        );
        String sig = sha256(rawContent);

        return new ThinkingStreamInterruptionReceipt(
                sessionId,
                traceId,
                interruptionReason,
                thinkingTokensSpent,
                tokensSaved,
                entropyScore,
                ngramRepetitionScore,
                healingAction,
                now,
                sig
        );
    }

    /**
     * 校验凭单自签名的有效性
     */
    public boolean verifySignature() {
        String rawContent = String.join("|",
                sessionId != null ? sessionId : "",
                traceId != null ? traceId : "",
                interruptionReason != null ? interruptionReason : "",
                String.valueOf(thinkingTokensSpent),
                String.valueOf(tokensSaved),
                String.format("%.4f", entropyScore),
                String.format("%.4f", ngramRepetitionScore),
                healingAction != null ? healingAction : "",
                timestamp.toString()
        );
        return sha256(rawContent).equals(signature);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
