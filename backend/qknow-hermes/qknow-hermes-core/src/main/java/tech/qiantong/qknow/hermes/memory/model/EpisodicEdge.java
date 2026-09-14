package tech.qiantong.qknow.hermes.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 时态情境图边：EpisodicEdge
 * 包含：源节点 ID、目标节点 ID、边关系类型、有效起始时间 validFrom、废弃时间 validTo、权重与属性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodicEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum RelationType {
        SUPERSEDES,
        CAUSES,
        REFERENCES,
        ASSOCIATED_WITH,
        DERIVED_FROM
    }

    /**
     * 源节点 ID (如新偏好或原因节点)
     */
    private String sourceId;

    /**
     * 目标节点 ID (如被废弃的旧偏好或结果节点)
     */
    private String targetId;

    /**
     * 边关系类型
     */
    private RelationType relationType;

    /**
     * 有效起始时间戳 (毫秒)
     */
    private long validFrom;

    /**
     * 有效终止时间戳 (毫秒，为 null 或 0 表示当前依然有效)
     */
    private Long validTo;

    /**
     * 关联权重 \in [0.0, 1.0]
     */
    @Builder.Default
    private double weight = 1.0;

    /**
     * 边元数据
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    /**
     * 在指定时间快照点是否处于活跃有效状态 (双时态图判定)
     */
    public boolean isActive(long queryTime) {
        if (queryTime < validFrom) {
            return false;
        }
        return validTo == null || validTo == 0L || queryTime < validTo;
    }
}
