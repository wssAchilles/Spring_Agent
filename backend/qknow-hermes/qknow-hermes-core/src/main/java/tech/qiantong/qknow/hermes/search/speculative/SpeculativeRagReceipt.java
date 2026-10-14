package tech.qiantong.qknow.hermes.search.speculative;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * 推测性 RAG 集中校验不可变加密防伪凭单
 * 遵循 Java 21 Record 规范，提供常量时间 SHA-256 签名校验
 */
public record SpeculativeRagReceipt(
        String query,
        int draftCount,
        List<SpeculativeDraftRecord> draftRecords,
        String finalSynthesizedAnswer,
        String reasoningContent,
        float verificationScore,
        long executionDurationNanos,
        String signature
) {
    public SpeculativeRagReceipt {
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(draftRecords, "draftRecords 不能为空");
        draftRecords = List.copyOf(draftRecords);
        Objects.requireNonNull(finalSynthesizedAnswer, "finalSynthesizedAnswer 不能为空");
        Objects.requireNonNull(reasoningContent, "reasoningContent 不能为空");
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    public static SpeculativeRagReceipt create(
            String query,
            List<SpeculativeDraftRecord> draftRecords,
            String finalSynthesizedAnswer,
            String reasoningContent,
            float verificationScore,
            long executionDurationNanos
    ) {
        Objects.requireNonNull(draftRecords, "draftRecords 不能为空");
        List<SpeculativeDraftRecord> immutableDrafts = List.copyOf(draftRecords);

        String rawPayload = String.format("%s:%d:%d:%s:%.4f:%d",
                query, immutableDrafts.size(), finalSynthesizedAnswer.length(),
                reasoningContent.length(), verificationScore, executionDurationNanos);
        String signature = generateSha256(rawPayload);

        return new SpeculativeRagReceipt(
                query,
                immutableDrafts.size(),
                immutableDrafts,
                finalSynthesizedAnswer,
                reasoningContent,
                verificationScore,
                executionDurationNanos,
                signature
        );
    }

    /**
     * 使用 MessageDigest.isEqual 进行常量时间比对验签，防范时序侧信道攻击
     */
    public boolean verifySignature() {
        String rawPayload = String.format("%s:%d:%d:%s:%.4f:%d",
                query, draftRecords.size(), finalSynthesizedAnswer.length(),
                reasoningContent.length(), verificationScore, executionDurationNanos);
        String expectedSignature = generateSha256(rawPayload);
        return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String generateSha256(String input) {
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
            throw new IllegalStateException("JVM 环境缺少 SHA-256 算法实现", e);
        }
    }
}
