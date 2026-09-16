package tech.qiantong.qknow.module.kmc.service.rag.advanced.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * 1000Hz 知识流事件帧 (GraphRAG Event Frame)
 * <p>
 * 采用 Java 21 Record 封装图谱推理与检索事件单帧，内置阿里千问 1536 维超球面单位向量规范校验。
 * </p>
 *
 * @param frameId         事件帧唯一 ID
 * @param sessionId        知识检索会话 ID
 * @param queryText        用户查询或命中文本
 * @param nodeType         节点类型 (如 ENTITY, RELATION, SECTION, CHUNK)
 * @param embeddingVector  阿里千问 1536 维特征向量 (满足 ||v||_2 = 1.0 +- 1e-4)
 * @param pprScore         Personalized PageRank 拓扑激活得分
 * @param timestampMs      事件生成时间戳 (毫秒)
 * @param sequenceNo       总线单调递增序列号
 * @author Achilles
 * @version 1.0
 */
public record GraphRagEventFrame(
        String frameId,
        String sessionId,
        String queryText,
        String nodeType,
        float[] embeddingVector,
        double pprScore,
        long timestampMs,
        long sequenceNo
) {
    /**
     * 阿里千问基准嵌入向量维度 (1536)
     */
    public static final int EXPECTED_EMBEDDING_DIM = 1536;

    /**
     * 超球面单位向量范数容差 (1.0 +- 1e-4)
     */
    public static final float NORM_TOLERANCE = 1e-4f;

    public GraphRagEventFrame {
        Objects.requireNonNull(frameId, "frameId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(queryText, "queryText 不能为空");
        Objects.requireNonNull(nodeType, "nodeType 不能为空");
    }

    /**
     * 校验当前事件帧的向量是否为合法的阿里千问 1536 维超球面单位向量
     *
     * @return true 若向量满足规范，false 否则
     */
    public boolean isValidEmbedding() {
        if (embeddingVector == null) {
            return false;
        }
        if (embeddingVector.length != EXPECTED_EMBEDDING_DIM) {
            return false;
        }
        double sumSq = 0.0;
        for (float val : embeddingVector) {
            sumSq += (double) val * val;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= NORM_TOLERANCE;
    }

    /**
     * 防御性拷贝，保证 Record 的不可变性
     */
    @Override
    public float[] embeddingVector() {
        return embeddingVector == null ? null : Arrays.copyOf(embeddingVector, embeddingVector.length);
    }
}
