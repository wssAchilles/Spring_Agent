package tech.qiantong.qknow.hermes.search.graphrag;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * GraphRAG 分层社区结构不可变节点
 * 遵循 Java 21 Record 纯不可变对象规范
 * 挂载阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)
 */
public record HierarchicalCommunityNode(
        String communityId,
        int level,
        List<String> entityIds,
        String summaryReport,
        float[] communityEmbedding,
        List<String> childCommunityIds
) {
    public static final int EMBEDDING_DIM = 1536;

    public HierarchicalCommunityNode {
        Objects.requireNonNull(communityId, "communityId 不能为空");
        Objects.requireNonNull(entityIds, "entityIds 不能为空");
        entityIds = List.copyOf(entityIds);
        Objects.requireNonNull(summaryReport, "summaryReport 不能为空");
        Objects.requireNonNull(communityEmbedding, "communityEmbedding 不能为空");
        if (communityEmbedding.length != EMBEDDING_DIM) {
            throw new IllegalArgumentException("千问社区摘要向量维度必须严格为 " + EMBEDDING_DIM + " 维");
        }
        communityEmbedding = Arrays.copyOf(communityEmbedding, EMBEDDING_DIM);
        Objects.requireNonNull(childCommunityIds, "childCommunityIds 不能为空");
        childCommunityIds = List.copyOf(childCommunityIds);
    }

    public static HierarchicalCommunityNode of(
            String communityId,
            int level,
            List<String> entityIds,
            String summaryReport,
            float[] communityEmbedding,
            List<String> childCommunityIds
    ) {
        return new HierarchicalCommunityNode(
                communityId, level, entityIds, summaryReport, communityEmbedding, childCommunityIds
        );
    }

    /**
     * 计算输入 Query 向量与当前社区摘要向量的超球面余弦相似度
     */
    public float calculateCosine(float[] queryEmbedding) {
        if (queryEmbedding == null || queryEmbedding.length != EMBEDDING_DIM) {
            return 0.0f;
        }
        float dot = 0.0f;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dot += queryEmbedding[i] * communityEmbedding[i];
        }
        return dot;
    }
}
