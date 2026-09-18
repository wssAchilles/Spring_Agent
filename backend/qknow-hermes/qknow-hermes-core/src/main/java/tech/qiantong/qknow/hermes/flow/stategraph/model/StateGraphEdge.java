package tech.qiantong.qknow.hermes.flow.stategraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.StateGraphEdgeType;

/**
 * 状态图有向边模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateGraphEdge {

    /**
     * 边的唯一标识
     */
    private String edgeId;

    /**
     * 源节点 UUID
     */
    private String sourceNodeUuid;

    /**
     * 目标节点 UUID
     */
    private String targetNodeUuid;

    /**
     * 边的类型：FORWARD / CONDITIONAL / LOOP_BACK
     */
    @Builder.Default
    private StateGraphEdgeType edgeType = StateGraphEdgeType.FORWARD;

    /**
     * 条件表达式（针对 CONDITIONAL 或 LOOP_BACK）
     */
    private String conditionExpression;

    /**
     * 针对 LOOP_BACK 回跳边的最大迭代次数（默认 10 轮）
     */
    @Builder.Default
    private int maxIterations = 10;
}
