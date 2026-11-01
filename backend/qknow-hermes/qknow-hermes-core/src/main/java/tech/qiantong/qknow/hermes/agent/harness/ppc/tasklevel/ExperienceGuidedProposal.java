package tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel;

import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.ExperienceGuidedProposer.Suggestion;

public record ExperienceGuidedProposal(Operator operator, String sourceEntryId) {
    public ExperienceGuidedProposal {
        Objects.requireNonNull(operator, "operator");
        sourceEntryId = sourceEntryId == null ? "" : sourceEntryId;
    }

    public static ExperienceGuidedProposal bootstrap(Operator operator) {
        return new ExperienceGuidedProposal(operator, "");
    }

    public static ExperienceGuidedProposal fromExperience(Suggestion suggestion) {
        Objects.requireNonNull(suggestion, "suggestion");
        return new ExperienceGuidedProposal(suggestion.operator(), suggestion.entryId());
    }

    public boolean hasExperienceSource() {
        return !sourceEntryId.isBlank();
    }
}
