package tech.qiantong.qknow.hermes.agent.harness.evolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Maximizes fidelity while minimizing token use and latency. */
public final class ParetoFrontierComposer {
    public record Candidate(String id, double fidelity, long tokens, long latencyMs) {
        public Candidate {
            if (id == null || id.isBlank() || !Double.isFinite(fidelity)
                    || fidelity < 0.0 || fidelity > 1.0 || tokens < 0 || latencyMs < 0) {
                throw new IllegalArgumentException("Invalid Pareto candidate");
            }
        }
    }

    public boolean dominates(Candidate left, Candidate right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return left.fidelity() >= right.fidelity()
                && left.tokens() <= right.tokens()
                && left.latencyMs() <= right.latencyMs()
                && (left.fidelity() > right.fidelity()
                    || left.tokens() < right.tokens()
                    || left.latencyMs() < right.latencyMs());
    }

    public List<Candidate> frontier(List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<Candidate> ordered = candidates.stream().map(candidate -> Objects.requireNonNull(candidate, "candidate"))
                .sorted(Comparator.comparing(Candidate::id)
                        .thenComparing(Comparator.comparingDouble(Candidate::fidelity).reversed())
                        .thenComparingLong(Candidate::tokens)
                        .thenComparingLong(Candidate::latencyMs))
                .toList();
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : ordered) {
            if (ordered.stream().noneMatch(other -> dominates(other, candidate))) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }
}
