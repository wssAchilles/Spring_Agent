package tech.qiantong.qknow.hermes.cognitive.treesearch;

import java.util.Arrays;
import java.util.Objects;

/**
 * 有界因果反思搜索树节点（Java 21 不可变 Record，锁定千问 1024 维 {@code S^1023} 单位超球面）。
 */
public record CausalSearchBranchNode(
        String nodeId,
        String parentNodeId,
        String journalBranchId,
        int depth,
        String proposedActionSummary,
        float[] unitEmbedding1024,
        int visitCount,
        double meanValue,
        double puctScore,
        String codeActReceiptId,
        double consistencyScore,
        boolean pruned,
        boolean quarantinedToColdRing
) {
    public static final int EMBEDDING_DIMENSION = 1024;

    public CausalSearchBranchNode {
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        Objects.requireNonNull(journalBranchId, "journalBranchId 不能为空");
        Objects.requireNonNull(unitEmbedding1024, "unitEmbedding1024 不能为空");
        if (depth < 0) {
            throw new IllegalArgumentException("搜索树节点深度不能为负数: " + depth);
        }
        if (visitCount < 0) {
            throw new IllegalArgumentException("搜索树节点访问次数不能为负数: " + visitCount);
        }
        if (unitEmbedding1024.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("分支节点嵌入必须为 1024 维，实际: " + unitEmbedding1024.length);
        }
        double normSq = 0.0;
        for (float v : unitEmbedding1024) {
            normSq += (double) v * v;
        }
        double norm = Math.sqrt(normSq);
        if (!Double.isFinite(norm) || Math.abs(norm - 1.0) > 1e-3) {
            throw new IllegalArgumentException("分支节点向量必须位于单位超球面 S^1023 上，实际范数: " + norm);
        }

        unitEmbedding1024 = Arrays.copyOf(unitEmbedding1024, EMBEDDING_DIMENSION);
        proposedActionSummary = proposedActionSummary != null ? proposedActionSummary : "";
        meanValue = clamp01(meanValue);
        consistencyScore = clamp01(consistencyScore);
        puctScore = Double.isFinite(puctScore) ? puctScore : meanValue;
    }

    @Override
    public float[] unitEmbedding1024() {
        return Arrays.copyOf(unitEmbedding1024, EMBEDDING_DIMENSION);
    }

    /**
     * 反向传播统计更新后返回新不可变节点快照。
     */
    public CausalSearchBranchNode withBackpropagatedStats(
            int newVisitCount,
            double newMeanValue,
            double newPuctScore
    ) {
        return new CausalSearchBranchNode(
                nodeId,
                parentNodeId,
                journalBranchId,
                depth,
                proposedActionSummary,
                unitEmbedding1024,
                newVisitCount,
                newMeanValue,
                newPuctScore,
                codeActReceiptId,
                consistencyScore,
                pruned,
                quarantinedToColdRing
        );
    }

    /**
     * 标记节点被剪枝并已软隔离至三级冷备可逆环（严禁物理硬删除）。
     */
    public CausalSearchBranchNode withPrunedAndQuarantined() {
        return new CausalSearchBranchNode(
                nodeId,
                parentNodeId,
                journalBranchId,
                depth,
                proposedActionSummary,
                unitEmbedding1024,
                visitCount,
                meanValue,
                Double.NEGATIVE_INFINITY,
                codeActReceiptId,
                consistencyScore,
                true,
                true
        );
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }
}
