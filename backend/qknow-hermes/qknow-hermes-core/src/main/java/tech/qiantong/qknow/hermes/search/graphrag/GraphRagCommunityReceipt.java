package tech.qiantong.qknow.hermes.search.graphrag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * GraphRAG 社区遍历与认知问答不可变加密凭单
 * 遵循 Java 21 Record 规范，提供常量时间 SHA-256 签名校验
 */
public record GraphRagCommunityReceipt(
        String query,
        String queryIntent,
        String hitCommunityId,
        int hitLevel,
        String retrievedSummary,
        List<String> matchedEntityIds,
        float cosineSimilarity,
        long executionDurationNanos,
        String signature
) {
    public GraphRagCommunityReceipt {
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(queryIntent, "queryIntent 不能为空");
        Objects.requireNonNull(hitCommunityId, "hitCommunityId 不能为空");
        Objects.requireNonNull(retrievedSummary, "retrievedSummary 不能为空");
        Objects.requireNonNull(matchedEntityIds, "matchedEntityIds 不能为空");
        matchedEntityIds = List.copyOf(matchedEntityIds);
        Objects.requireNonNull(signature, "signature 不能为空");
    }

    public static GraphRagCommunityReceipt create(
            String query,
            String queryIntent,
            String hitCommunityId,
            int hitLevel,
            String retrievedSummary,
            List<String> matchedEntityIds,
            float cosineSimilarity,
            long executionDurationNanos
    ) {
        Objects.requireNonNull(matchedEntityIds, "matchedEntityIds 不能为空");
        List<String> immutableEntities = List.copyOf(matchedEntityIds);

        String rawPayload = String.format("%s:%s:%s:%d:%d:%.4f:%d",
                query, queryIntent, hitCommunityId, hitLevel,
                immutableEntities.size(), cosineSimilarity, executionDurationNanos);
        String signature = generateSha256(rawPayload);

        return new GraphRagCommunityReceipt(
                query,
                queryIntent,
                hitCommunityId,
                hitLevel,
                retrievedSummary,
                immutableEntities,
                cosineSimilarity,
                executionDurationNanos,
                signature
        );
    }

    /**
     * 使用 MessageDigest.isEqual 进行常量时间比对验签，防范时序侧信道攻击
     */
    public boolean verifySignature() {
        String rawPayload = String.format("%s:%s:%s:%d:%d:%.4f:%d",
                query, queryIntent, hitCommunityId, hitLevel,
                matchedEntityIds.size(), cosineSimilarity, executionDurationNanos);
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
