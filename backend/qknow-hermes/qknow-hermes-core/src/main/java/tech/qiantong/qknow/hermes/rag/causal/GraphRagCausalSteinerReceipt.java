package tech.qiantong.qknow.hermes.rag.causal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Steiner 树因果骨架提取与 DeepSeek 思考对齐存证凭单
 * <p>
 * 纯 Java 21 Record 格式不可变载荷，内嵌 SHA-256 密码学自签名与常数时间验真，
 * 记录查询文本、种子实体、Steiner 树节点与边数、因果路径拓扑链、事实接地置信度与执行延迟。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record GraphRagCausalSteinerReceipt(
        String receiptId,
        String queryText,
        List<String> seedEntities,
        int steinerNodeCount,
        int steinerEdgeCount,
        List<String> causalPaths,
        double groundingScore,
        long latencyMicros,
        long timestamp,
        String sha256Signature
) {
    public GraphRagCausalSteinerReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(queryText, "queryText 不能为空");
        Objects.requireNonNull(seedEntities, "seedEntities 不能为空");
        Objects.requireNonNull(causalPaths, "causalPaths 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
    }

    /**
     * 静态工厂方法，自动计算 SHA-256 密码学签名
     */
    public static GraphRagCausalSteinerReceipt create(
            String queryText,
            List<String> seedEntities,
            int steinerNodeCount,
            int steinerEdgeCount,
            List<String> causalPaths,
            double groundingScore,
            long latencyMicros
    ) {
        String receiptId = "GRCS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = System.currentTimeMillis();
        String safeQuery = queryText != null ? queryText : "EMPTY_QUERY";
        List<String> safeSeeds = seedEntities != null ? List.copyOf(seedEntities) : List.of();
        List<String> safePaths = causalPaths != null ? List.copyOf(causalPaths) : List.of();

        String canonicalPayload = buildCanonicalPayload(
                receiptId, safeQuery, safeSeeds, steinerNodeCount,
                steinerEdgeCount, safePaths, groundingScore, latencyMicros, timestamp
        );
        String signature = computeSha256(canonicalPayload);

        return new GraphRagCausalSteinerReceipt(
                receiptId,
                safeQuery,
                safeSeeds,
                steinerNodeCount,
                steinerEdgeCount,
                safePaths,
                groundingScore,
                latencyMicros,
                timestamp,
                signature
        );
    }

    /**
     * 验证凭单 SHA-256 自签名，使用常量时间比较杜绝时序攻击
     */
    public boolean verifySignature() {
        String expectedPayload = buildCanonicalPayload(
                receiptId, queryText, seedEntities, steinerNodeCount,
                steinerEdgeCount, causalPaths, groundingScore, latencyMicros, timestamp
        );
        String expectedSignature = computeSha256(expectedPayload);
        return MessageDigest.isEqual(
                sha256Signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildCanonicalPayload(
            String receiptId,
            String query,
            List<String> seeds,
            int nodes,
            int edges,
            List<String> paths,
            double score,
            long latency,
            long ts
    ) {
        return receiptId + "|" + query + "|" + String.join(",", seeds) + "|"
                + nodes + "|" + edges + "|" + String.join(";;", paths) + "|"
                + String.format(Locale.ROOT, "%.4f", score) + "|" + latency + "|" + ts;
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
            throw new IllegalStateException("JVM 环境缺少 SHA-256 算法实现", e);
        }
    }
}
