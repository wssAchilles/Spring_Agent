package tech.qiantong.qknow.hermes.cognitive.core.model;

import tech.qiantong.qknow.hermes.agent.harness.experience.FeedbackProvenance;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;
import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeSignal;

import java.util.List;

public record CognitiveTrace(
        List<String> states,
        List<StrategyScore> strategyScores,
        List<ReflectionEvent> reflections,
        List<RepairEvent> repairs,
        List<ExperienceEvent> experienceEvents,
        String terminationCode,
        OutcomeSignal outcomeSignal,
        List<ToolExecutionEvent> toolExecutionEvents
) {
    public CognitiveTrace(List<String> states, List<StrategyScore> strategyScores,
                          List<ReflectionEvent> reflections, List<RepairEvent> repairs,
                          List<ExperienceEvent> experienceEvents, String terminationCode) {
        this(states, strategyScores, reflections, repairs, experienceEvents, terminationCode, null, List.of());
    }

    public CognitiveTrace(List<String> states, List<StrategyScore> strategyScores,
                          List<ReflectionEvent> reflections, List<RepairEvent> repairs,
                          List<ExperienceEvent> experienceEvents, String terminationCode,
                          OutcomeSignal outcomeSignal) {
        this(states, strategyScores, reflections, repairs, experienceEvents, terminationCode,
                outcomeSignal, List.of());
    }

    public CognitiveTrace {
        states = List.copyOf(states);
        strategyScores = List.copyOf(strategyScores);
        reflections = List.copyOf(reflections);
        repairs = List.copyOf(repairs);
        experienceEvents = List.copyOf(experienceEvents);
        toolExecutionEvents = List.copyOf(toolExecutionEvents);
    }

    public OutcomeSignal.Status outcomeStatus() {
        return outcomeSignal == null ? OutcomeSignal.Status.UNKNOWN : outcomeSignal.status();
    }

    public record StrategyScore(String entryId, int version, int attempts, int successes,
                                double exploitation, double exploration, double score,
                                StrategyLifecycleStatus lifecycleStatus) {
        public StrategyScore(String entryId, int version, int attempts, int successes,
                             double exploitation, double exploration, double score) {
            this(entryId, version, attempts, successes, exploitation, exploration, score,
                    StrategyLifecycleStatus.ADOPTED);
        }
    }

    public record ReflectionEvent(String stepId, boolean accepted, String assessmentCode) {}

    public record RepairEvent(String stepId, int attempt, boolean proposalAccepted, String outcomeCode) {}

    public record ToolExecutionEvent(int callIndex, String stepId, String toolName, Status status,
                                     BusinessOutcome businessOutcome, String reasonCode,
                                     String outcomeEvidenceDigest) {
        public ToolExecutionEvent(int callIndex, String stepId, String toolName, Status status, String reasonCode) {
            this(callIndex, stepId, toolName, status, BusinessOutcome.UNKNOWN, reasonCode, "");
        }

        public ToolExecutionEvent {
            businessOutcome = businessOutcome == null ? BusinessOutcome.UNKNOWN : businessOutcome;
            reasonCode = reasonCode == null ? "" : reasonCode;
            outcomeEvidenceDigest = outcomeEvidenceDigest == null ? "" : outcomeEvidenceDigest;
            if (!outcomeEvidenceDigest.isEmpty() && !outcomeEvidenceDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("outcomeEvidenceDigest must be a SHA-256 hexadecimal value");
            }
            if (businessOutcome == BusinessOutcome.UNKNOWN && !outcomeEvidenceDigest.isEmpty()) {
                throw new IllegalArgumentException("UNKNOWN business outcomes cannot carry an evidence digest");
            }
        }

        /** A received response is execution feedback, not proof that the business postcondition holds. */
        public enum Status { RESPONSE_RECEIVED, ERROR }
        public enum BusinessOutcome { APPLIED, REJECTED, UNKNOWN }
    }

    public record ExperienceEvent(String entryId, int version, String action,
                                  int attempts, int successes, boolean retired,
                                  StrategyLifecycleStatus lifecycleStatus, int probationUses,
                                  int probationSuccesses, int consecutiveFailures,
                                  OutcomeSignal.Status outcomeStatus,
                                  FeedbackProvenance feedbackProvenance) {
        public ExperienceEvent(String entryId, int version, String action,
                               int attempts, int successes, boolean retired,
                               StrategyLifecycleStatus lifecycleStatus, int probationUses,
                               int probationSuccesses, int consecutiveFailures,
                               OutcomeSignal.Status outcomeStatus) {
            this(entryId, version, action, attempts, successes, retired, lifecycleStatus,
                    probationUses, probationSuccesses, consecutiveFailures, outcomeStatus,
                    FeedbackProvenance.NONE);
        }

        public ExperienceEvent(String entryId, int version, String action,
                               int attempts, int successes, boolean retired) {
            this(entryId, version, action, attempts, successes, retired,
                    retired ? StrategyLifecycleStatus.RETIRED : null, 0, 0, 0,
                    OutcomeSignal.Status.UNKNOWN, FeedbackProvenance.NONE);
        }
    }
}
