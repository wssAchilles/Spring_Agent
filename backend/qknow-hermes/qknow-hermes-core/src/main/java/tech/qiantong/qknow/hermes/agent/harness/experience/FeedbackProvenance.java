package tech.qiantong.qknow.hermes.agent.harness.experience;

/**
 * 标识改变策略反馈计数的信号来源。
 * 模型自评信号设计为中性：保持可观测性，但不能提升置信度、晋升试用策略或触发策略退役。
 */
public enum FeedbackProvenance {
    NONE,
    VERIFIED_PASS,
    VERIFIED_FAIL,
    EMPIRICAL_SUCCESS,
    EMPIRICAL_FAIL,
    MODEL_SELF_REPORT;

    public boolean contributesSuccess() {
        return this == VERIFIED_PASS || this == EMPIRICAL_SUCCESS;
    }

    public boolean contributesFailure() {
        return this == VERIFIED_FAIL || this == EMPIRICAL_FAIL;
    }

    public boolean isIndependent() {
        return contributesSuccess() || contributesFailure();
    }

    public boolean supportsLegacyApi() {
        return this == EMPIRICAL_SUCCESS || this == EMPIRICAL_FAIL;
    }

    public void validate(boolean success) {
        if (this == NONE || contributesSuccess() && !success || contributesFailure() && success) {
            throw new IllegalArgumentException("feedback success does not match provenance " + this);
        }
    }

    public static FeedbackProvenance empirical(boolean success) {
        return success ? EMPIRICAL_SUCCESS : EMPIRICAL_FAIL;
    }
}
