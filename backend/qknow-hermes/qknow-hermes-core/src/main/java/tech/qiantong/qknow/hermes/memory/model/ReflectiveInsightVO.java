package tech.qiantong.qknow.hermes.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 反思提炼高阶见解对象：ReflectiveInsightVO
 * 包含：高阶见解文本、支撑证据记忆 ID 列表、抽象层级、置信度与时间戳
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReflectiveInsightVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 见解唯一 ID
     */
    private String id;

    /**
     * 所属用户 ID
     */
    private String userId;

    /**
     * 高阶见解内容 (如概括性偏好、工作流认知)
     */
    private String insight;

    /**
     * 支撑本洞察的原始离散观测事件/记忆节点 ID 列表
     */
    @Builder.Default
    private List<String> supportingEvidenceIds = new ArrayList<>();

    /**
     * 抽象层级 (1: 聚合事件簇, 2: 宏观行为偏好准则, 3: 全局人设档案)
     */
    @Builder.Default
    private int abstractionLevel = 1;

    /**
     * 推断置信度 \in [0.0, 1.0]
     */
    @Builder.Default
    private double confidence = 0.85;

    /**
     * 生成时间戳 (毫秒)
     */
    private long createdAt;
}
