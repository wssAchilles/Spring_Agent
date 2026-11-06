package tech.qiantong.qknow.hermes.cognitive.core.engine;

import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveTrace;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ExperienceStrategySelector {
    private static final double UCB_EXPLORATION = Math.sqrt(2.0);

    public Selection select(List<ExperienceEntry> candidates) {
        List<ExperienceEntry> adopted = candidates == null ? List.of() : candidates.stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.disposition() == ExperienceDisposition.ADOPTED)
                .filter(entry -> entry.isEligibleForPositiveProposer(0.20))
                .toList();
        int totalAttempts = adopted.stream().mapToInt(ExperienceEntry::feedbackAttempts).sum();
        List<CognitiveTrace.StrategyScore> scores = adopted.stream().map(entry -> {
            int attempts = entry.feedbackAttempts();
            double mean = attempts == 0 ? entry.confidence()
                    : (entry.feedbackSuccesses() + 1.0) / (attempts + 2.0);
            double exploration = UCB_EXPLORATION * Math.sqrt(
                    Math.log(totalAttempts + 2.0) / (attempts + 1.0));
            return new CognitiveTrace.StrategyScore(entry.entryId(), entry.version(), attempts,
                    entry.feedbackSuccesses(), mean, exploration, mean + exploration);
        }).sorted(Comparator.comparingDouble(CognitiveTrace.StrategyScore::score).reversed()
                .thenComparing(CognitiveTrace.StrategyScore::entryId)).toList();
        String selectedId = scores.isEmpty() ? null : scores.getFirst().entryId();
        ExperienceEntry selected = adopted.stream().filter(e -> e.entryId().equals(selectedId)).findFirst().orElse(null);
        return new Selection(selected, scores);
    }

    public record Selection(ExperienceEntry selected, List<CognitiveTrace.StrategyScore> scores) {
        public Selection {
            scores = List.copyOf(scores);
        }
    }
}
