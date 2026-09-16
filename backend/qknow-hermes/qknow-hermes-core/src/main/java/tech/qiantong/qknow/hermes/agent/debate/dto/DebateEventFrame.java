package tech.qiantong.qknow.hermes.agent.debate.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * 1000Hz 争辩事件帧 (Debate Event Frame)
 * <p>
 * 采用 Java 21 Record 封装黑板争辩过程中的高频单帧事件，内置阿里千问 1536 维超球面单位向量规范校验。
 * </p>
 *
 * @param frameId        事件帧唯一 ID
 * @param debateId       所属争辩会话 ID
 * @param agentId        发言智能体 ID
 * @param roleType       发言智能体生态位角色
 * @param argumentText   观点或论据内容文本
 * @param embeddingVector 阿里千问 1536 维超球面特征向量 (可为 null，若存在必须满足单位范数)
 * @param shannonEntropy 当前争辩轮次计算所得香农争议熵
 * @param roundIndex     当前争辩轮次序号 (从 1 开始)
 * @param timestampMs    事件时间戳 (毫秒)
 * @param sequenceNo     总线单调递增序列号
 * @author Achilles
 * @version 1.0
 */
public record DebateEventFrame(
        String frameId,
        String debateId,
        String agentId,
        AgentRoleNicheType roleType,
        String argumentText,
        float[] embeddingVector,
        double shannonEntropy,
        int roundIndex,
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

    public DebateEventFrame {
        Objects.requireNonNull(frameId, "frameId 不能为空");
        Objects.requireNonNull(debateId, "debateId 不能为空");
        Objects.requireNonNull(agentId, "agentId 不能为空");
        Objects.requireNonNull(roleType, "roleType 不能为空");
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
