package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMetricsTest {

    @Test
    void metricsDeduplicateByFirstSegmentOccurrenceAndUseGradedNdcg() {
        Map<String, Integer> qrels = new LinkedHashMap<>();
        qrels.put("A", 3);
        qrels.put("B", 2);
        qrels.put("C", 1);

        RagMetrics.Scores scores = RagMetrics.evaluate(qrels, List.of("X", "A", "A", "C", "B"));

        assertEquals(1.0, scores.recallAt5(), 1e-12);
        assertEquals(1.0, scores.recallAt10(), 1e-12);
        assertEquals(0.5, scores.mrr(), 1e-12);
        assertEquals(0.6388888888888888, scores.retrievalApAt10(), 1e-12);
        assertEquals(expectedNdcg(), scores.ndcgAt10(), 1e-12);
    }

    @Test
    void averagePrecisionDenominatorIsLimitedToTenRelevantSegments() {
        Map<String, Integer> qrels = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            qrels.put("S" + i, 1);
        }
        List<String> topTen = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "S" + i)
                .toList();

        assertEquals(1.0, RagMetrics.evaluate(qrels, topTen).retrievalApAt10(), 1e-12);
    }

    @Test
    void emptyRetrievalRateOnlyCountsEmptyUnanswerableRankings() {
        assertEquals(2.0 / 3.0, RagMetrics.emptyRetrievalRate(
                List.of(List.of(), List.of("noise"), List.of())), 1e-12);
    }

    @Test
    void metricsRejectInvalidGradesAndExposeNoReferenceOrQueryIdInput() {
        assertThrows(IllegalArgumentException.class,
                () -> RagMetrics.evaluate(Map.of("A", 0), List.of("A")));

        List<Method> evaluateMethods = java.util.Arrays.stream(RagMetrics.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("evaluate"))
                .toList();
        assertEquals(1, evaluateMethods.size());
        assertEquals(List.of(Map.class, List.class), List.of(evaluateMethods.get(0).getParameterTypes()));
    }

    @Test
    void familyClusterBootstrapIsDeterministicAndUsesTenThousandResamples() {
        FamilyClusterBootstrap bootstrap = new FamilyClusterBootstrap();
        Map<String, List<Double>> values = Map.of(
                "family-b", List.of(0.0, 0.0),
                "family-a", List.of(1.0, 0.0),
                "family-c", List.of(1.0, 1.0));

        FamilyClusterBootstrap.ConfidenceInterval first = bootstrap.mean(values);
        FamilyClusterBootstrap.ConfidenceInterval second = bootstrap.mean(values);

        assertEquals(first, second);
        assertEquals(20260714L, FamilyClusterBootstrap.DEFAULT_SEED);
        assertEquals(0.5, first.estimate(), 1e-12);
        assertEquals(3, first.clusters());
        assertEquals(10_000, first.resamples());
        assertTrue(first.ciLow() <= first.estimate());
        assertTrue(first.ciHigh() >= first.estimate());
    }

    @Test
    void pairedBootstrapSamplesFamiliesTogetherForContinuousAndBinaryMetrics() {
        FamilyClusterBootstrap bootstrap = new FamilyClusterBootstrap();
        Map<String, List<Double>> baseline = Map.of(
                "family-a", List.of(0.0, 0.0),
                "family-b", List.of(0.5, 0.5));
        Map<String, List<Double>> candidate = Map.of(
                "family-a", List.of(0.25, 0.25),
                "family-b", List.of(0.75, 0.75));

        FamilyClusterBootstrap.ConfidenceInterval delta = bootstrap.pairedDelta(baseline, candidate);
        FamilyClusterBootstrap.ConfidenceInterval binary = bootstrap.mean(Map.of(
                "family-a", List.of(1.0, 0.0),
                "family-b", List.of(1.0, 1.0)));

        assertEquals(0.25, delta.estimate(), 1e-12);
        assertEquals(0.25, delta.ciLow(), 1e-12);
        assertEquals(0.25, delta.ciHigh(), 1e-12);
        assertEquals(0.75, binary.estimate(), 1e-12);
    }

    @Test
    void pairedBootstrapRejectsMismatchedFamilies() {
        FamilyClusterBootstrap bootstrap = new FamilyClusterBootstrap();
        assertThrows(IllegalArgumentException.class, () -> bootstrap.pairedDelta(
                Map.of("family-a", List.of(0.0)),
                Map.of("family-b", List.of(1.0))));
    }

    @Test
    void bootstrapExposesOnlyTheFixedSeedConstructor() {
        assertEquals(1, FamilyClusterBootstrap.class.getConstructors().length);
        assertEquals(0, FamilyClusterBootstrap.class.getConstructors()[0].getParameterCount());
    }

    private static double expectedNdcg() {
        double dcg = 7.0 / log2(3) + 1.0 / log2(4) + 3.0 / log2(5);
        double idcg = 7.0 / log2(2) + 3.0 / log2(3) + 1.0 / log2(4);
        return dcg / idcg;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
