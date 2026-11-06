package tech.qiantong.qknow.hermes.cognitive.core.model;

public record ReflectionResult(boolean accepted, RepairProposal repairProposal, String reusableStrategy) {
    public ReflectionResult {
        reusableStrategy = reusableStrategy == null ? "" : reusableStrategy.trim();
    }
}
