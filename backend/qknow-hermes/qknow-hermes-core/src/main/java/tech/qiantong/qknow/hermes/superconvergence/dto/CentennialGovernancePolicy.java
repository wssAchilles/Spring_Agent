package tech.qiantong.qknow.hermes.superconvergence.dto;

/**
 * 百阶段综合治理策略 Record
 */
public record CentennialGovernancePolicy(
    String policyId,
    double maxSovereignRiskScore,
    double cbfBarrierMargin,
    double qpMaxDeflectionAngleRad,
    boolean allowClosedFormRepair,
    int slidingNonceWindowSize,
    long timestamp
) {
    public CentennialGovernancePolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId 不能为空");
        }
        if (maxSovereignRiskScore <= 0.0 || maxSovereignRiskScore > 1.0) {
            throw new IllegalArgumentException("maxSovereignRiskScore 必须在 (0.0, 1.0] 范围内");
        }
    }

    /**
     * 创建百阶段默认生产治理基线策略
     */
    public static CentennialGovernancePolicy defaultPolicy() {
        return new CentennialGovernancePolicy(
            "POL-CENTENNIAL-DEFAULT",
            0.75,
            0.15,
            0.5236, // 约 30 度弧度角
            true,
            1024,
            System.currentTimeMillis()
        );
    }
}
