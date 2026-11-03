package tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel;

import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.ExperienceGuidedProposer.Suggestion;

public record ExperienceGuidedProposal(Operator operator, String sourceEntryId,
                                       BoundedOperatorPipeline pipeline) {
    public ExperienceGuidedProposal {
        Objects.requireNonNull(operator, "operator");
        sourceEntryId = sourceEntryId == null ? "" : sourceEntryId;
        if (pipeline != null && !pipeline.operators().getFirst().equals(operator)) {
            throw new IllegalArgumentException("pipeline proposal must start with its primary operator");
        }
    }

    public ExperienceGuidedProposal(Operator operator, String sourceEntryId) {
        this(operator, sourceEntryId, null);
    }

    public static ExperienceGuidedProposal bootstrap(Operator operator) {
        return new ExperienceGuidedProposal(operator, "");
    }

    public static ExperienceGuidedProposal fromExperience(Suggestion suggestion) {
        Objects.requireNonNull(suggestion, "suggestion");
        return new ExperienceGuidedProposal(suggestion.operator(), suggestion.entryId(), suggestion.pipeline());
    }

    public static ExperienceGuidedProposal pipeline(BoundedOperatorPipeline pipeline) {
        Objects.requireNonNull(pipeline, "pipeline");
        return new ExperienceGuidedProposal(pipeline.operators().getFirst(), "", pipeline);
    }

    public String candidateKey() {
        return pipeline == null ? operator.name() : pipeline.fingerprint();
    }

    public boolean hasExperienceSource() {
        return !sourceEntryId.isBlank();
    }

    @Override
    public String toString() {
        return pipeline == null
                ? "ExperienceGuidedProposal[operator=" + operator + ", sourceEntryId=" + sourceEntryId + "]"
                : "ExperienceGuidedProposal[pipeline=" + pipeline.fingerprint()
                        + ", sourceEntryId=" + sourceEntryId + "]";
    }
}
