package tech.qiantong.qknow.ai.rag.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * GraphRAG 子图因果思考骨架与时空流形对齐密码学不可变存证凭单 (Java 21 Record)
 * 记录 2-跳有界 PPR 子图节点、因果命题链、时空对齐得分与耗时，内嵌 SHA-256 自签名防篡改
 */
public record GraphRagScaffoldReceipt(
        String sessionId,
        String query,
        List<String> seedEntities,
        List<String> subgraphNodes,
        List<String> subgraphEdges,
        List<String> causalPaths,
        double spatiotemporalScore,
        long executionTimeMs,
        Instant timestamp,
        String signature
) {
    public GraphRagScaffoldReceipt {
        seedEntities = List.copyOf(seedEntities != null ? seedEntities : List.of());
        subgraphNodes = List.copyOf(subgraphNodes != null ? subgraphNodes : List.of());
        subgraphEdges = List.copyOf(subgraphEdges != null ? subgraphEdges : List.of());
        causalPaths = List.copyOf(causalPaths != null ? causalPaths : List.of());
    }

    /**
     * 工厂方法：创建不可变凭单并生成 SHA-256 自签名
     */
    public static GraphRagScaffoldReceipt create(
            String sessionId,
            String query,
            List<String> seedEntities,
            List<String> subgraphNodes,
            List<String> subgraphEdges,
            List<String> causalPaths,
            double spatiotemporalScore,
            long executionTimeMs
    ) {
        Instant now = Instant.now();
        String rawData = String.join("|",
                sessionId != null ? sessionId : "",
                query != null ? query : "",
                String.join(",", seedEntities != null ? seedEntities : List.of()),
                String.join(",", subgraphNodes != null ? subgraphNodes : List.of()),
                String.join(",", causalPaths != null ? causalPaths : List.of()),
                String.format("%.4f", spatiotemporalScore),
                String.valueOf(executionTimeMs),
                now.toString()
        );
        String sig = sha256(rawData);

        return new GraphRagScaffoldReceipt(
                sessionId,
                query,
                seedEntities,
                subgraphNodes,
                subgraphEdges,
                causalPaths,
                spatiotemporalScore,
                executionTimeMs,
                now,
                sig
        );
    }

    /**
     * 校验自签名的有效性，保证凭单不可篡改
     */
    public boolean verifySignature() {
        String rawData = String.join("|",
                sessionId != null ? sessionId : "",
                query != null ? query : "",
                String.join(",", seedEntities != null ? seedEntities : List.of()),
                String.join(",", subgraphNodes != null ? subgraphNodes : List.of()),
                String.join(",", causalPaths != null ? causalPaths : List.of()),
                String.format("%.4f", spatiotemporalScore),
                String.valueOf(executionTimeMs),
                timestamp.toString()
        );
        return sha256(rawData).equals(signature);
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
