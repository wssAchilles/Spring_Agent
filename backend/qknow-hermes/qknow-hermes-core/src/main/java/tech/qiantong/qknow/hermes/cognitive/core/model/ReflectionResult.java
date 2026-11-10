package tech.qiantong.qknow.hermes.cognitive.core.model;

import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyInsightEdit;

import java.util.List;

public record ReflectionResult(boolean accepted, RepairProposal repairProposal, String reusableStrategy,
                               List<StrategyInsightEdit> insightEdits) {
    public ReflectionResult(boolean accepted, RepairProposal repairProposal, String reusableStrategy) {
        this(accepted, repairProposal, reusableStrategy, List.of());
    }

    public ReflectionResult {
        reusableStrategy = reusableStrategy == null ? "" : reusableStrategy.trim();
        insightEdits = insightEdits == null ? List.of() : List.copyOf(insightEdits);
    }
}
