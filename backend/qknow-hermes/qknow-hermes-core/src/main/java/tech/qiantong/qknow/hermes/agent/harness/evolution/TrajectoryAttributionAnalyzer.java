package tech.qiantong.qknow.hermes.agent.harness.evolution;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.RagAttributionStatus;
import tech.qiantong.qknow.hermes.agent.harness.RagAttributionSummary;

import java.util.List;

/** Computes orthogonal degradation signals from explicit execution counters. */
public final class TrajectoryAttributionAnalyzer {
    public record TrajectoryEvidence(long reasoningTokens, long answerTokens,
                                     int ragProvided, int ragUsed, double ragScore,
                                     List<HarnessEvidenceRecord.ToolExecutionEntry> toolExecutions) {
        public TrajectoryEvidence(long reasoningTokens, long answerTokens, int ragProvided, int ragUsed,
                                  List<HarnessEvidenceRecord.ToolExecutionEntry> toolExecutions) {
            this(reasoningTokens, answerTokens, ragProvided, ragUsed, 0.0, toolExecutions);
        }

        public TrajectoryEvidence {
            if (reasoningTokens < 0 || answerTokens < 0 || ragProvided < 0
                    || ragUsed < 0 || ragUsed > ragProvided) {
                throw new IllegalArgumentException("Trajectory counters are inconsistent");
            }
            toolExecutions = toolExecutions == null ? List.of() : List.copyOf(toolExecutions);
            ragScore = Double.isFinite(ragScore) ? Math.max(0.0, ragScore) : 0.0;
        }
    }

    public record AttributionVector(double reasoningExpansion, double toolErrorRate,
                                    double ragUnderutilization, RagAttributionStatus ragStatus,
                                    double ragScore) {
        public AttributionVector(double reasoningExpansion, double toolErrorRate, double ragUnderutilization) {
            this(reasoningExpansion, toolErrorRate, ragUnderutilization, RagAttributionStatus.NO_RAG, 0.0);
        }
    }

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
        RagAttributionSummary rag = RagAttributionSummary.of(
                evidence.ragProvided(), evidence.ragUsed(), evidence.ragScore());
        return new AttributionVector(reasoningExpansion, toolErrorRate, ragUnderutilization,
                rag.status(), rag.ragScore());
    }
}
