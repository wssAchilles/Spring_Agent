package tech.qiantong.qknow.hermes.agent.harness.experience;

public enum StrategyTaskType {
    GENERAL,
    STATE_MUTATION,
    UNCLASSIFIED;

    public boolean appliesTo(StrategyTaskType taskType) {
        return this == taskType || taskType == GENERAL && this == UNCLASSIFIED;
    }

    public boolean canSeedCrossTaskVariant() {
        return this == GENERAL || this == UNCLASSIFIED;
    }
}
