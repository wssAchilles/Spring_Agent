package tech.qiantong.qknow.hermes.agent.harness.experience;

public enum StrategyTaskType {
    GENERAL,
    STATE_MUTATION,
    CANCELLATION,
    EXCHANGE,
    RETURN_REFUND,
    UPDATE,
    DELETION,
    UNCLASSIFIED;

    public boolean appliesTo(StrategyTaskType taskType) {
        return this == taskType || taskType == GENERAL && this == UNCLASSIFIED;
    }

    public boolean canSeedCrossTaskVariant() {
        return this == GENERAL || this == STATE_MUTATION || this == UNCLASSIFIED;
    }

    public boolean isMutationFamily() {
        return switch (this) {
            case STATE_MUTATION, CANCELLATION, EXCHANGE, RETURN_REFUND, UPDATE, DELETION -> true;
            case GENERAL, UNCLASSIFIED -> false;
        };
    }
}
