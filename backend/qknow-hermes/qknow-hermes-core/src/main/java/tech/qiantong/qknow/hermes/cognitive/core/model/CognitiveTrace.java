package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.util.List;

public record CognitiveTrace(
        List<String> states,
        List<StrategyScore> strategyScores,
        List<ReflectionEvent> reflections,
        List<RepairEvent> repairs,
        List<ExperienceEvent> experienceEvents,
        String terminationCode
) {
    public CognitiveTrace {
        states = List.copyOf(states);
        strategyScores = List.copyOf(strategyScores);
        reflections = List.copyOf(reflections);
        repairs = List.copyOf(repairs);
        experienceEvents = List.copyOf(experienceEvents);
    }

    public record StrategyScore(String entryId, int version, int attempts, int successes,
                                double exploitation, double exploration, double score) {}

    public record ReflectionEvent(String stepId, boolean accepted, String assessmentCode) {}

    public record RepairEvent(String stepId, int attempt, boolean proposalAccepted, String outcomeCode) {}

    public record ExperienceEvent(String entryId, int version, String action,
                                  int attempts, int successes, boolean retired) {}
}
