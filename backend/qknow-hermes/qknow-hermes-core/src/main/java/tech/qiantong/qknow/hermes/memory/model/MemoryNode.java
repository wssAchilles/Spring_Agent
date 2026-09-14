package tech.qiantong.qknow.hermes.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 四元组记忆流节点：<t, c, e, I>
 * 包含：创建时间戳、文本内容、阿里千问 1536 维超球面嵌入向量、重要性评分，以及动态衰减衰减因子。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记忆唯一标识 ID
     */
    private String id;

    /**
     * 所属用户 ID
     */
    private String userId;

    /**
     * 记忆文本内容 c
     */
    private String content;

    /**
     * 阿里千问 1536 维单位超球面嵌入向量 e
     */
    private float[] embedding;

    /**
     * 核心重要性评分 I \in [0.0, 1.0]
     */
    private double importance;

    /**
     * 记忆创建时间戳 t (毫秒)
     */
    private long timestamp;

    /**
     * 记忆动态衰减率 \lambda_0 (默认 0.05)
     */
    @Builder.Default
    private double decayRate = 0.05;

    /**
     * 历史访问/激活强化次数 k
     */
    @Builder.Default
    private int accessCount = 0;

    /**
     * 最近一次被访问激活的时间戳
     */
    private long lastAccessedAt;

    /**
     * 扩展属性元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 判断是否属于关键重要记忆 (引理 1.1 激活保护)
     */
    public boolean isCriticallyImportant(double critThreshold) {
        return this.importance >= critThreshold;
    }

    /**
     * 激活/访问记忆并记录
     */
    public void recordAccess(long accessTime) {
        this.accessCount++;
        this.lastAccessedAt = accessTime;
    }
}
