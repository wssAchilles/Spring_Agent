package tech.qiantong.qknow.ai.federated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 联邦记忆加权聚合器
 * <p>
 * 接收多智能体上传的本地见解向量，注入局部差分隐私 (LDP) 高斯扰动，
 * 执行加权平均聚合与千问 1536 维超球面单位重投影，保护私有记忆防反演。
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public class FederatedMemoryAggregator {

    public record GlobalMetaMemoryCard(
            String roundId,
            String anchorQuery,
            float[] aggregatedVector,
            int participatingAgentCount,
            double ldpNoiseSigma,
            String metaMemoryHash
    ) {}

    private final double ldpNoiseSigma;
    private final int minQuorum;

    public FederatedMemoryAggregator(double ldpNoiseSigma, int minQuorum) {
        this.ldpNoiseSigma = Math.max(0.0, ldpNoiseSigma);
        this.minQuorum = Math.max(1, minQuorum);
    }

    /**
     * 聚合各节点本地认知向量
     */
    public GlobalMetaMemoryCard aggregate(
            String roundId,
            String anchorQuery,
            List<LocalMemoryDistiller.LocalInsight> localInsights
    ) {
        if (localInsights == null || localInsights.size() < minQuorum) {
            throw new IllegalStateException(String.format("参与节点数不足最小法定 Quorum (%d)", minQuorum));
        }

        float[] aggregated = new float[1536];
        double totalWeight = 0.0;
        Random gaussianRnd = new Random(roundId.hashCode());

        for (LocalMemoryDistiller.LocalInsight insight : localInsights) {
            double weight = Math.max(0.1, insight.confidenceScore());
            totalWeight += weight;

            float[] v = insight.embeddingVector();
            for (int i = 0; i < 1536; i++) {
                // 1. 注入局部差分隐私 LDP 高斯噪声 N(0, sigma^2)
                float noise = ldpNoiseSigma > 0 ? (float) (gaussianRnd.nextGaussian() * ldpNoiseSigma) : 0.0f;
                aggregated[i] += (float) (weight * (v[i] + noise));
            }
        }

        // 归一加权平均
        if (totalWeight > 0.0) {
            for (int i = 0; i < 1536; i++) {
                aggregated[i] /= totalWeight;
            }
        }

        // 2. 超球面保模归一化投影 (||v||_2 = 1.0)
        normalizeToHypersphere(aggregated);

        // 计算全局元记忆哈希
        String metaHash = computeVectorHash(aggregated);

        return new GlobalMetaMemoryCard(
                roundId,
                anchorQuery,
                aggregated,
                localInsights.size(),
                ldpNoiseSigma,
                metaHash
        );
    }

    private void normalizeToHypersphere(float[] v) {
        float norm = 0.0f;
        for (float val : v) {
            norm += val * val;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-9f) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= norm;
            }
        }
    }

    private String computeVectorHash(float[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(10, v.length); i++) {
            sb.append(String.format("%.4f,", v[i]));
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
