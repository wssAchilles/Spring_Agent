package tech.qiantong.qknow.hermes.infra.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 规范化 Prompt 编译不可变凭单
 * 严格基于 Java 21 Record 规范构建，提供密码学常量时间防侧信道安全验签
 */
public record CanonicalPromptReceipt(
        String compiledPrompt,
        int estimatedPrefixTokens,
        int paddingTokensAdded,
        int totalPrefixTokens,
        boolean isAligned64,
        int totalDocumentChunks,
        int totalToolsRegistered,
        long compilationDurationNanos,
        String sha256Signature
) {
    public static CanonicalPromptReceipt create(
            String compiledPrompt,
            int estimatedPrefixTokens,
            int paddingTokensAdded,
            int totalDocumentChunks,
            int totalToolsRegistered,
            long compilationDurationNanos
    ) {
        int totalPrefix = estimatedPrefixTokens + paddingTokensAdded;
        boolean aligned = (totalPrefix % 64 == 0);
        String signature = generateSha256(compiledPrompt, totalPrefix, aligned, totalDocumentChunks, totalToolsRegistered);
        return new CanonicalPromptReceipt(
                compiledPrompt,
                estimatedPrefixTokens,
                paddingTokensAdded,
                totalPrefix,
                aligned,
                totalDocumentChunks,
                totalToolsRegistered,
                compilationDurationNanos,
                signature
        );
    }

    private static String generateSha256(
            String prompt,
            int totalPrefix,
            boolean aligned,
            int chunks,
            int tools
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.format("%d|%b|%d|%d|%d|%s",
                    prompt.length(), aligned, totalPrefix, chunks, tools, prompt);
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
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
            throw new IllegalStateException("JVM 缺少标准 SHA-256 算法实现", e);
        }
    }

    /**
     * 常量时间比对防时序侧信道攻击验签
     */
    public boolean verifySignature() {
        String expected = generateSha256(compiledPrompt, totalPrefixTokens, isAligned64, totalDocumentChunks, totalToolsRegistered);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = sha256Signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
