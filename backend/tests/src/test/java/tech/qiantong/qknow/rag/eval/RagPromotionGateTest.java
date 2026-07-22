package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPromotionGateTest {

    @Test
    void pairedDeltaUsesFamiliesAndRejectsMismatchedCases() {
        RagPromotionGate.PrimaryEvidence delta = RagPromotionGate.primary(
                "Recall@10",
                Map.of("f1", List.of(0.4, 0.5), "f2", List.of(0.6)),
                Map.of("f1", List.of(0.6, 0.7), "f2", List.of(0.8)));
        assertTrue(delta.delta().ciLow() > 0.0D);
        assertTrue(delta.pValue() <= 0.05D);
        assertTrue(delta.delta().resamples() == FamilyClusterBootstrap.RESAMPLES);
        assertThrows(IllegalArgumentException.class, () -> RagPromotionGate.primary(
                "Recall@10", Map.of("f1", List.of(0.4)), Map.of("f2", List.of(0.6))));
        assertThrows(IllegalArgumentException.class, () -> RagPromotionGate.primary(
                "Recall@10", Map.of("f1", List.of(0.4)), Map.of("f1", List.of(0.6, 0.7))));
    }

    @Test
    void primaryRequiresPositiveCiAndHolmAdjustedSignificance() {
        RagPromotionGate.PrimaryEvidence positive = RagPromotionGate.primary(
                "Recall@10", values(0.4, 0.5), values(0.6, 0.7));
        RagPromotionGate.Decision passed = RagPromotionGate.evaluate(
                List.of(positive),
                List.of(), new RagPromotionGate.Budget(100, 10, 100, 1),
                new RagPromotionGate.ObservedBudget(10, 1, 10, OptionalDouble.of(0.1)), true);
        assertTrue(passed.passed());

        RagPromotionGate.PrimaryEvidence regressed = RagPromotionGate.primary(
                "Recall@10", values(0.6, 0.7), values(0.4, 0.5));
        RagPromotionGate.Decision failed = RagPromotionGate.evaluate(
                List.of(regressed),
                List.of(), new RagPromotionGate.Budget(100, 10, 100, 1),
                new RagPromotionGate.ObservedBudget(10, 1, 10, OptionalDouble.of(0.1)), true);
        assertFalse(failed.passed());
        assertTrue(failed.failures().stream().anyMatch(value -> value.contains("HOLM")));

        RagPromotionGate.Decision missingCost = RagPromotionGate.evaluate(
                List.of(positive),
                List.of(), new RagPromotionGate.Budget(100, 10, 100, 1),
                RagPromotionGate.ObservedBudget.withoutCost(10, 1, 10), true);
        assertTrue(missingCost.failures().contains("COST_UNAVAILABLE"));
    }

    @Test
    void secondaryMarginCoverageAndBudgetsAreGates() {
        RagPromotionGate.PrimaryEvidence primary = RagPromotionGate.primary(
                "MRR", values(0.4, 0.5), values(0.6, 0.7));
        RagPromotionGate.SecondaryEvidence secondary = RagPromotionGate.secondary(
                "nDCG", values(0.6, 0.6), values(0.55, 0.55), 0.01);
        RagPromotionGate.Decision failed = RagPromotionGate.evaluate(
                List.of(primary), List.of(secondary),
                new RagPromotionGate.Budget(100, 2, 100, 1),
                new RagPromotionGate.ObservedBudget(101, 3, 101,
                        OptionalDouble.of(1.1)), false);
        assertFalse(failed.passed());
        assertTrue(failed.failures().contains("INCOMPLETE_JUDGE_COVERAGE"));
        assertTrue(failed.failures().stream().anyMatch(value -> value.contains("NON_INFERIORITY_MARGIN_BREACHED")));
        assertTrue(failed.failures().contains("LATENCY_BUDGET_EXCEEDED"));
        assertTrue(failed.failures().contains("CALL_BUDGET_EXCEEDED"));
        assertTrue(failed.failures().contains("TOKEN_BUDGET_EXCEEDED"));
        assertTrue(failed.failures().contains("COST_BUDGET_EXCEEDED"));
    }

    private static Map<String, List<Double>> values(double first, double second) {
        return Map.of("f1", List.of(first, first), "f2", List.of(second, second));
    }
}
