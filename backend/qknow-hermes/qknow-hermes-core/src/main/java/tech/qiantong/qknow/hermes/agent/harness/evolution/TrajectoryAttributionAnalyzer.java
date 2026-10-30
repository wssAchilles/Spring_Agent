package tech.qiantong.qknow.hermes.agent.harness.evolution;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;

import java.util.List;

/** Computes orthogonal degradation signals from explicit execution counters. */
public final class TrajectoryAttributionAnalyzer {
    public record TrajectoryEvidence(long reasoningTokens, long answerTokens,
                                     int ragProvided, int ragUsed,
                                     List<HarnessEvidenceRecord.ToolExecutionEntry> toolExecutions) {
        public TrajectoryEvidence {
            if (reasoningTokens < 0 || answerTokens < 0 || ragProvided < 0
                    || ragUsed < 0 || ragUsed > ragProvided) {
                throw new IllegalArgumentException("Trajectory counters are inconsistent");
            }
            toolExecutions = toolExecutions == null ? List.of() : List.copyOf(toolExecutions);
        }
    }

    public record AttributionVector(double reasoningExpansion, double toolErrorRate,
                                    double ragUnderutilization) {}

    public AttributionVector analyze(TrajectoryEvidence evidence) {
        if (evidence == null) {
            return new AttributionVector(0.0, 0.0, 0.0);
        }
        double reasoningExpansion = evidence.answerTokens() == 0
                ? (evidence.reasoningTokens() == 0 ? 0.0 : evidence.reasoningTokens())
                : (double) evidence.reasoningTokens() / evidence.answerTokens();
        long errors = evidence.toolExecutions().stream().filter(entry -> !entry.success()).count();
        double toolErrorRate = evidence.toolExecutions().isEmpty() ? 0.0
                : (double) errors / evidence.toolExecutions().size();
        double ragUnderutilization = evidence.ragProvided() == 0 ? 0.0
                : 1.0 - (double) evidence.ragUsed() / evidence.ragProvided();
        return new AttributionVector(reasoningExpansion, toolErrorRate, ragUnderutilization);
    }
}
