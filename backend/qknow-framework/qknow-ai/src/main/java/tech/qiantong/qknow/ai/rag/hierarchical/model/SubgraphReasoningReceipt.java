package tech.qiantong.qknow.ai.rag.hierarchical.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 图谱子图推理与因果骨架不可变存证凭单 (Java 21 Record)
 * 记录多跳剪枝拓扑实体、因果命题链条、Token 预算消耗与执行耗时，
 * 采用 SHA-256 密码学自签名保证防篡改与审计可追溯。
 */
public record SubgraphReasoningReceipt(
        String receiptId,
        String sessionId,
        String query,
        List<String> seedEntities,
        List<PrunedGraphNode> prunedNodes,
        List<CausalProposition> causalChains,
        int tokenBudgetConsumed,
        long executionTimeMs,
        Instant generatedAt,
        String receiptSignature
) {
    /**
     * 剪枝保留的图节点 Record
     */
    public record PrunedGraphNode(
            String entityId,
            String entityName,
            String entityType,
            double geodesicDistanceScore,
            int hop
    ) {}

    /**
     * 因果命题三元组 Record
     */
    public record CausalProposition(
            String sourceEntity,
            String relation,
            String targetEntity,
            double confidenceWeight,
            String naturalLanguageStatement
    ) {}

    public SubgraphReasoningReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(query, "query 不能为空");
        seedEntities = seedEntities == null ? List.of() : List.copyOf(seedEntities);
        prunedNodes = prunedNodes == null ? List.of() : List.copyOf(prunedNodes);
        causalChains = causalChains == null ? List.of() : List.copyOf(causalChains);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;

        if (receiptSignature == null) {
            receiptSignature = calculateSignature(receiptId, query, prunedNodes, causalChains, tokenBudgetConsumed, executionTimeMs);
        }
    }

    public static SubgraphReasoningReceipt create(
            String receiptId,
            String sessionId,
            String query,
            List<String> seedEntities,
            List<PrunedGraphNode> prunedNodes,
            List<CausalProposition> causalChains,
            int tokenBudgetConsumed,
            long executionTimeMs,
            Instant generatedAt
    ) {
        String safeReceiptId = receiptId != null ? receiptId : "RCP-GRAG-" + System.currentTimeMillis();
        String safeQuery = query != null ? query : "";
        List<PrunedGraphNode> safeNodes = prunedNodes != null ? List.copyOf(prunedNodes) : List.of();
        List<CausalProposition> safeChains = causalChains != null ? List.copyOf(causalChains) : List.of();
        String sig = calculateSignature(safeReceiptId, safeQuery, safeNodes, safeChains, tokenBudgetConsumed, executionTimeMs);

        return new SubgraphReasoningReceipt(
                safeReceiptId,
                sessionId,
                safeQuery,
                seedEntities,
                safeNodes,
                safeChains,
                tokenBudgetConsumed,
                executionTimeMs,
                generatedAt != null ? generatedAt : Instant.now(),
                sig
        );
    }

    private static String calculateSignature(
            String receiptId,
            String query,
            List<PrunedGraphNode> nodes,
            List<CausalProposition> chains,
            int tokenBudget,
            long execTime
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(receiptId.getBytes(StandardCharsets.UTF_8));
            digest.update(query.getBytes(StandardCharsets.UTF_8));
            digest.update(String.valueOf(tokenBudget).getBytes(StandardCharsets.UTF_8));
            digest.update(String.valueOf(execTime).getBytes(StandardCharsets.UTF_8));
            for (PrunedGraphNode node : nodes) {
                digest.update(node.entityId().getBytes(StandardCharsets.UTF_8));
            }
            for (CausalProposition chain : chains) {
                digest.update(chain.naturalLanguageStatement().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    public boolean verifySignature() {
        return receiptSignature.equals(calculateSignature(
                receiptId, query, prunedNodes, causalChains, tokenBudgetConsumed, executionTimeMs
        ));
    }
}
