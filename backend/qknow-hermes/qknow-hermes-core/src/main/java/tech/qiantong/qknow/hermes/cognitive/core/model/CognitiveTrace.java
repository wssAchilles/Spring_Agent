package tech.qiantong.qknow.hermes.cognitive.core.model;

import tech.qiantong.qknow.hermes.cognitive.core.verification.OutcomeSignal;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;

import java.util.List;

public record CognitiveTrace(
        List<String> states,
        List<StrategyScore> strategyScores,
        List<ReflectionEvent> reflections,
        List<RepairEvent> repairs,
        List<ExperienceEvent> experienceEvents,
        String terminationCode,
        OutcomeSignal outcomeSignal
) {
    public CognitiveTrace(List<String> states, List<StrategyScore> strategyScores,
                          List<ReflectionEvent> reflections, List<RepairEvent> repairs,
                          List<ExperienceEvent> experienceEvents, String terminationCode) {
        this(states, strategyScores, reflections, repairs, experienceEvents, terminationCode, null);
    }

    public CognitiveTrace {
        states = List.copyOf(states);
        strategyScores = List.copyOf(strategyScores);
        reflections = List.copyOf(reflections);
        repairs = List.copyOf(repairs);
        experienceEvents = List.copyOf(experienceEvents);
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

    public record ExperienceEvent(String entryId, int version, String action,
                                  int attempts, int successes, boolean retired,
                                  StrategyLifecycleStatus lifecycleStatus, int probationUses,
                                  int probationSuccesses, int consecutiveFailures,
                                  OutcomeSignal.Status outcomeStatus) {
        public ExperienceEvent(String entryId, int version, String action,
                               int attempts, int successes, boolean retired) {
            this(entryId, version, action, attempts, successes, retired,
                    retired ? StrategyLifecycleStatus.RETIRED : null, 0, 0, 0,
                    OutcomeSignal.Status.UNKNOWN);
        }
    }
}
