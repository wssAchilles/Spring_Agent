package tech.qiantong.qknow.ai.gateway.model;

import lombok.Getter;

/**
 * Phase 28: 路由业务场景与多目标权重偏好
 */
@Getter
public enum RoutingScenario {
    /**
     * 实时交互式对话：极度敏感于首字打字机延迟 (TTFT P99)
     */
    INTERACTIVE_CHAT(0.75, 0.05, 0.20),

    /**
     * 智能体复杂推理 (Hermes 深度推演)：吞吐与网络连接稳定性优先
     */
    AGENT_REASONING(0.50, 0.20, 0.30),

    /**
     * 后台离线批处理 (知识库分块、千问向量化、评测集扫描)：单位 Token 成本与批量吞吐优先
     */
    OFFLINE_EMBEDDING(0.10, 0.70, 0.20);

    private final double latencyWeight;
    private final double costWeight;
    private final double errorPenaltyWeight;

    RoutingScenario(double latencyWeight, double costWeight, double errorPenaltyWeight) {
        this.latencyWeight = latencyWeight;
        this.costWeight = costWeight;
        this.errorPenaltyWeight = errorPenaltyWeight;
    }
}
