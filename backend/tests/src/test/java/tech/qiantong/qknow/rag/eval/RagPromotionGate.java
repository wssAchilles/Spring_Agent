package tech.qiantong.qknow.rag.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Reproducible promotion contract for paired family-cluster experiments. */
final class RagPromotionGate {
    static final double ALPHA = 0.05D;

    private RagPromotionGate() {
    }

    static PrimaryEvidence primary(String name,
                                   Map<String, List<Double>> baseline,
                                   Map<String, List<Double>> candidate) {
        FamilyClusterBootstrap.PairedInference inference =
                new FamilyClusterBootstrap().pairedInference(baseline, candidate);
        return new PrimaryEvidence(name, inference.interval(), inference.pValue());
    }

    static SecondaryEvidence secondary(String name,
                                       Map<String, List<Double>> baseline,
                                       Map<String, List<Double>> candidate,
                                       double nonInferiorityMargin) {
        FamilyClusterBootstrap.PairedInference inference =
                new FamilyClusterBootstrap().pairedInference(baseline, candidate);
        return new SecondaryEvidence(name, inference.interval(), nonInferiorityMargin);
    }

    static Decision evaluate(List<PrimaryEvidence> primary,
                             List<SecondaryEvidence> secondary,
                             Budget budget,
                             ObservedBudget observed,
                             boolean judgeCoverageComplete) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(secondary, "secondary");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(observed, "observed");
        List<String> failures = new ArrayList<>();
        if (!judgeCoverageComplete) {
            failures.add("INCOMPLETE_JUDGE_COVERAGE");
        }
        if (primary.isEmpty()) {
            failures.add("NO_PRIMARY_METRIC");
        }

        List<PrimaryEvidence> ordered = primary.stream()
                .sorted(Comparator.comparingDouble(PrimaryEvidence::pValue))
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            PrimaryEvidence evidence = ordered.get(i);
            double adjustedAlpha = ALPHA / (ordered.size() - i);
            if (!(evidence.delta().ciLow() > 0.0D)) {
                failures.add(evidence.name() + ":PRIMARY_CI_NOT_ABOVE_ZERO");
            }
            if (!(evidence.pValue() <= adjustedAlpha)) {
                failures.add(evidence.name() + ":HOLM_NOT_SIGNIFICANT");
            }
        }

        for (SecondaryEvidence evidence : secondary) {
            if (evidence.delta().ciLow() < -evidence.nonInferiorityMargin()) {
                failures.add(evidence.name() + ":NON_INFERIORITY_MARGIN_BREACHED");
            }
        }

        if (observed.latencyMs() > budget.maxLatencyMs()) {
            failures.add("LATENCY_BUDGET_EXCEEDED");
        }
        if (observed.calls() > budget.maxCalls()) {
            failures.add("CALL_BUDGET_EXCEEDED");
        }
        if (observed.tokens() > budget.maxTokens()) {
            failures.add("TOKEN_BUDGET_EXCEEDED");
        }
        if (observed.costUsd().isEmpty()) {
            failures.add("COST_UNAVAILABLE");
        } else if (observed.costUsd().getAsDouble() > budget.maxCostUsd()) {
            failures.add("COST_BUDGET_EXCEEDED");
        }
        return new Decision(failures.isEmpty(), failures);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("metric name is required");
        }
    }

    private static void validateInference(FamilyClusterBootstrap.ConfidenceInterval interval) {
        Objects.requireNonNull(interval, "interval");
        if (!Double.isFinite(interval.estimate()) || !Double.isFinite(interval.ciLow())
                || !Double.isFinite(interval.ciHigh())
                || interval.ciLow() > interval.estimate() || interval.estimate() > interval.ciHigh()
                || interval.clusters() <= 0
                || interval.resamples() != FamilyClusterBootstrap.RESAMPLES) {
            throw new IllegalArgumentException("invalid paired cluster bootstrap evidence");
        }
    }

    static final class PrimaryEvidence {
        private final String name;
        private final FamilyClusterBootstrap.ConfidenceInterval delta;
        private final double pValue;

        private PrimaryEvidence(String name, FamilyClusterBootstrap.ConfidenceInterval delta,
                                double pValue) {
            validateName(name);
            validateInference(delta);
            if (!Double.isFinite(pValue) || pValue < 0.0D || pValue > 1.0D) {
                throw new IllegalArgumentException("invalid paired bootstrap p-value");
            }
            this.name = name;
            this.delta = delta;
            this.pValue = pValue;
        }

        String name() {
            return name;
        }

        FamilyClusterBootstrap.ConfidenceInterval delta() {
            return delta;
        }

        double pValue() {
            return pValue;
        }
    }

    static final class SecondaryEvidence {
        private final String name;
        private final FamilyClusterBootstrap.ConfidenceInterval delta;
        private final double nonInferiorityMargin;

        private SecondaryEvidence(String name, FamilyClusterBootstrap.ConfidenceInterval delta,
                                  double nonInferiorityMargin) {
            validateName(name);
            validateInference(delta);
            if (!Double.isFinite(nonInferiorityMargin) || nonInferiorityMargin < 0.0D) {
                throw new IllegalArgumentException("non-inferiority margin must be non-negative");
            }
            this.name = name;
            this.delta = delta;
            this.nonInferiorityMargin = nonInferiorityMargin;
        }

        String name() {
            return name;
        }

        FamilyClusterBootstrap.ConfidenceInterval delta() {
            return delta;
        }

        double nonInferiorityMargin() {
            return nonInferiorityMargin;
        }
    }

    record Budget(long maxLatencyMs, long maxCalls, long maxTokens, double maxCostUsd) {
        Budget {
            if (maxLatencyMs < 0 || maxCalls < 0 || maxTokens < 0
                    || !Double.isFinite(maxCostUsd) || maxCostUsd < 0.0D) {
                throw new IllegalArgumentException("budgets must be non-negative");
            }
        }
    }

    record ObservedBudget(long latencyMs, long calls, long tokens, OptionalDouble costUsd) {
        ObservedBudget {
            if (latencyMs < 0 || calls < 0 || tokens < 0 || costUsd == null
                    || (costUsd.isPresent() && (!Double.isFinite(costUsd.getAsDouble())
                    || costUsd.getAsDouble() < 0.0D))) {
                throw new IllegalArgumentException("observed budget values are invalid");
            }
        }

        static ObservedBudget withoutCost(long latencyMs, long calls, long tokens) {
            return new ObservedBudget(latencyMs, calls, tokens, OptionalDouble.empty());
        }
    }

    record Decision(boolean passed, List<String> failures) {
        Decision {
            failures = List.copyOf(failures == null ? List.of() : failures);
        }
    }
}
