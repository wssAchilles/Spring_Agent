package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.memory.model.MemoryNode;
import tech.qiantong.qknow.hermes.memory.scoring.MemoryScoringServiceImpl;
import tech.qiantong.qknow.hermes.memory.scoring.ParetoMemoryRanker;
import tech.qiantong.qknow.hermes.memory.scoring.ParetoMemoryRanker.MemoryObjectiveVector;
import tech.qiantong.qknow.hermes.memory.scoring.ParetoMemoryRanker.ObjectiveVector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParetoMemoryRankerTest {

    @Test
    void testStrictDominance() {
        MemoryObjectiveVector superior = objective("superior", 0.9, 0.8, 0.7);
        MemoryObjectiveVector inferior = objective("inferior", 0.8, 0.7, 0.6);

        List<List<MemoryObjectiveVector>> fronts = ParetoMemoryRanker.computeParetoFronts(List.of(superior, inferior));

        assertEquals(2, fronts.size());
        assertEquals("superior", fronts.get(0).get(0).node().getId());
        assertEquals(1, fronts.get(0).get(0).paretoRank());
        assertEquals("inferior", fronts.get(1).get(0).node().getId());
        assertEquals(2, fronts.get(1).get(0).paretoRank());
        assertTrue(ParetoMemoryRanker.dominates(superior, inferior));
        assertFalse(ParetoMemoryRanker.dominates(inferior, superior));
        assertFalse(ParetoMemoryRanker.dominates(superior, objective("equal", 0.9, 0.8, 0.7)));
    }

    @Test
    void testTradeOffFrontier() {
        MemoryObjectiveVector oldRelevant = objective("old-relevant", 0.2, 0.95, 0.6);
        MemoryObjectiveVector freshModerate = objective("fresh-moderate", 0.95, 0.2, 0.6);

        List<List<MemoryObjectiveVector>> fronts = ParetoMemoryRanker.computeParetoFronts(List.of(oldRelevant, freshModerate));

        assertEquals(1, fronts.size());
        assertEquals(2, fronts.get(0).size());
        assertEquals(1, fronts.get(0).get(0).paretoRank());
        assertEquals(1, fronts.get(0).get(1).paretoRank());
        assertFalse(ParetoMemoryRanker.dominates(oldRelevant, freshModerate));
        assertFalse(ParetoMemoryRanker.dominates(freshModerate, oldRelevant));
    }

    @Test
    void testCrowdingDistanceDiversity() {
        List<ObjectiveVector<String>> candidates = List.of(
                vector("recency-edge", 1.0, 0.0, 0.5),
                vector("dense-1", 0.75, 0.25, 0.5),
                vector("dense-2", 0.50, 0.50, 0.5),
                vector("dense-3", 0.25, 0.75, 0.5),
                vector("relevance-edge", 0.0, 1.0, 0.5));

        List<String> selected = ParetoMemoryRanker.rankObjectivesTopK(candidates, 2);

        assertEquals(2, selected.size());
        assertTrue(selected.contains("recency-edge"));
        assertTrue(selected.contains("relevance-edge"));
        List<ObjectiveVector<String>> crowding = ParetoMemoryRanker.assignObjectiveCrowdingDistance(candidates);
        assertTrue(crowding.get(0).crowdingDistance() == Double.POSITIVE_INFINITY);
        assertTrue(crowding.get(4).crowdingDistance() == Double.POSITIVE_INFINITY);
    }

    @Test
    void testExtremeCornerCases() {
        MemoryScoringServiceImpl scoring = new MemoryScoringServiceImpl();
        assertTrue(ParetoMemoryRanker.computeParetoFronts(null).isEmpty());
        assertTrue(ParetoMemoryRanker.computeParetoFronts(List.of()).isEmpty());
        assertTrue(ParetoMemoryRanker.assignCrowdingDistance(null).isEmpty());
        assertTrue(ParetoMemoryRanker.assignCrowdingDistance(List.of()).isEmpty());
        assertTrue(ParetoMemoryRanker.rankTopK(null, new float[2], 3, 0L, scoring).isEmpty());
        assertTrue(ParetoMemoryRanker.rankTopK(List.of(node("x", new float[2])), new float[2], 0, 0L, scoring).isEmpty());

        MemoryObjectiveVector zero = objective("zero", 0.0, 0.0, 0.0);
        MemoryObjectiveVector zeroCopy = objective("zero-copy", 0.0, 0.0, 0.0);
        List<List<MemoryObjectiveVector>> zeroFront = ParetoMemoryRanker.computeParetoFronts(List.of(zero, zeroCopy));
        assertEquals(1, zeroFront.size());
        assertEquals(2, zeroFront.get(0).size());
        assertEquals(0.0, ParetoMemoryRanker.assignCrowdingDistance(List.of(zero)).get(0).crowdingDistance());

        MemoryObjectiveVector one = objective("one", 1.0, 1.0, 1.0);
        MemoryObjectiveVector oneCopy = objective("one-copy", 1.0, 1.0, 1.0);
        assertEquals(1, ParetoMemoryRanker.computeParetoFronts(List.of(one, oneCopy)).size());

        assertThrows(IllegalArgumentException.class, () -> ParetoMemoryRanker.dominates(
                vector("two-d", 1.0, 0.0), vector("three-d", 1.0, 0.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> ParetoMemoryRanker.computeObjectiveFronts(List.of(
                vector("two-d", 1.0, 0.0), vector("three-d", 1.0, 0.0, 0.0))));
        assertTrue(ParetoMemoryRanker.rankTopK(
                List.of(node("mismatch", new float[3])), new float[2], 1, 0L, scoring).isEmpty());

        float[] dynamicEmbedding = new float[1024];
        dynamicEmbedding[0] = 1.0f;
        assertEquals(1, scoring.rerankByParetoFrontier(
                List.of(node("1024", dynamicEmbedding)), dynamicEmbedding, 1, 1L).size());
    }

    @Test
    void testFastFirstFrontMatchesQuadraticReferenceWithTies() {
        Random random = new Random(731);
        List<ObjectiveVector<String>> candidates = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            candidates.add(vector("random-" + i, random.nextDouble() * 0.95,
                    random.nextDouble() * 0.95, random.nextDouble() * 0.95));
        }
        candidates.add(vector("edge-a", 0.99, 0.1, 0.5));
        candidates.add(vector("edge-a-copy", 0.99, 0.1, 0.5));
        candidates.add(vector("edge-a-relevance-variant", 0.99, 0.2, 0.5));
        candidates.add(vector("edge-b", 0.1, 0.99, 0.5));

        List<List<ObjectiveVector<String>>> referenceFronts = ParetoMemoryRanker.computeObjectiveFronts(candidates);
        Set<String> expected = new HashSet<>();
        for (ObjectiveVector<String> candidate : referenceFronts.get(0)) {
            expected.add(candidate.item());
        }
        List<String> actual = ParetoMemoryRanker.rankObjectivesTopK(candidates, expected.size());

        assertEquals(expected.size(), actual.size());
        assertEquals(expected, new HashSet<>(actual));
    }

    @Test
    void testGenericTopKFallbackAndBoundaryInputs() {
        assertTrue(ParetoMemoryRanker.computeObjectiveFronts((List<ObjectiveVector<String>>) null).isEmpty());
        assertTrue(ParetoMemoryRanker.computeObjectiveFronts(List.of()).isEmpty());
        assertTrue(ParetoMemoryRanker.assignObjectiveCrowdingDistance(null).isEmpty());
        assertTrue(ParetoMemoryRanker.assignObjectiveCrowdingDistance(List.of()).isEmpty());
        assertEquals(0.0, ParetoMemoryRanker.assignObjectiveCrowdingDistance(
                List.of(vector("singleton", 0.5, 0.5, 0.5))).get(0).crowdingDistance());
        assertTrue(ParetoMemoryRanker.rankObjectivesTopK(null, 2).isEmpty());
        assertTrue(ParetoMemoryRanker.rankObjectivesTopK(List.of(), 2).isEmpty());
        assertTrue(ParetoMemoryRanker.rankObjectivesTopK(List.of(vector("only", 1.0, 1.0, 1.0)), 0).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new ObjectiveVector<>("empty", List.of(), 0.0, 0.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ObjectiveVector<>("null-list", null, 0.0, 0.0, 0));
        ObjectiveVector<String> nullObjective = new ObjectiveVector<>("null-objective",
                java.util.Arrays.asList((Double) null), 0.0, Double.NaN, 0);
        assertEquals(0.0, nullObjective.objectives().get(0));
        assertEquals(0.0, nullObjective.crowdingDistance());
        assertThrows(NullPointerException.class,
                () -> ParetoMemoryRanker.dominates((ObjectiveVector<String>) null, vector("x", 1.0)));
        assertThrows(NullPointerException.class,
                () -> ParetoMemoryRanker.computeObjectiveFronts(java.util.Arrays.asList((ObjectiveVector<String>) null)));
        assertThrows(IllegalArgumentException.class, () -> ParetoMemoryRanker.assignObjectiveCrowdingDistance(
                List.of(vector("two-d", 1.0, 0.0), vector("three-d", 1.0, 0.0, 0.0))));
        assertThrows(IllegalArgumentException.class, () -> ParetoMemoryRanker.rankObjectivesTopK(
                List.of(vector("three-d", 1.0, 0.0, 0.0), vector("four-d", 1.0, 0.0, 0.0, 0.0)), 1));

        List<ObjectiveVector<String>> fourObjectives = List.of(
                vector("dominant", 1.0, 1.0, 1.0, 1.0),
                vector("front-2-a", 0.9, 0.2, 0.4, 0.4),
                vector("front-2-b", 0.3, 0.9, 0.4, 0.4),
                vector("front-2-c", 0.6, 0.6, 0.8, 0.5));
        List<String> selected = ParetoMemoryRanker.rankObjectivesTopK(fourObjectives, 2);
        assertEquals(2, selected.size());
        assertEquals("dominant", selected.get(0));
        assertTrue(Set.of("front-2-a", "front-2-b", "front-2-c").contains(selected.get(1)));
        List<String> fullyAppended = ParetoMemoryRanker.rankObjectivesTopK(fourObjectives, 4);
        assertEquals(4, fullyAppended.size());
        assertEquals("dominant", fullyAppended.get(0));
        assertEquals("strong", ParetoMemoryRanker.rankObjectivesTopK(List.of(
                vector("weak", 0.2, 0.2, 0.2, 0.2),
                vector("strong", 0.9, 0.9, 0.9, 0.9)), 1).get(0));

        MemoryScoringServiceImpl scoring = new MemoryScoringServiceImpl();
        float[] embedding = {1.0f};
        MemoryNode best = node("best", embedding);
        best.setImportance(1.0);
        MemoryNode second = node("second", embedding);
        second.setImportance(0.5);
        MemoryNode third = node("third", embedding);
        third.setImportance(0.25);
        List<MemoryNode> ranked = ParetoMemoryRanker.rankTopK(
                java.util.Arrays.asList(best, null, second, third), embedding, 2, 1L, scoring);
        assertEquals(2, ranked.size());
        assertSame(best, ranked.get(0));
        assertTrue(ranked.contains(second) || ranked.contains(third));
    }

    @Test
    void testRemainingDominanceAndNumericalBranches() {
        MemoryScoringServiceImpl scoring = new MemoryScoringServiceImpl();
        assertTrue(ParetoMemoryRanker.rankTopK(List.of(), new float[]{1.0f}, 1, 1L, scoring).isEmpty());

        assertEquals(1, ParetoMemoryRanker.rankTopK(
                List.of(node("no-embedding", null)), new float[]{1.0f}, 1, 1L, scoring).size());
        assertEquals(1, ParetoMemoryRanker.rankTopK(
                List.of(node("no-query", new float[]{1.0f})), null, 1, 1L, scoring).size());
        assertEquals(1, ParetoMemoryRanker.rankTopK(
                List.of(node("nan-embedding", new float[]{Float.NaN})), new float[]{1.0f}, 1, 1L, scoring).size());

        List<ObjectiveVector<String>> threeObjectiveCases = List.of(
                vector("all-high", 1.0, 1.0, 1.0),
                vector("all-low", 0.0, 0.0, 0.0),
                vector("equal-a", 0.5, 0.5, 0.5),
                vector("equal-b", 0.5, 0.5, 0.5),
                vector("relevance-only-high", 0.5, 1.0, 0.5),
                vector("importance-only-high", 0.5, 0.5, 1.0),
                vector("mixed-a", 1.0, 0.0, 0.5),
                vector("mixed-b", 0.0, 1.0, 0.5));
        assertFalse(ParetoMemoryRanker.computeObjectiveFronts(threeObjectiveCases).isEmpty());

        List<ObjectiveVector<String>> fourObjectiveCases = List.of(
                vector("four-high", 1.0, 1.0, 1.0, 1.0),
                vector("four-low", 0.0, 0.0, 0.0, 0.0),
                vector("four-equal-a", 0.5, 0.5, 0.5, 0.5),
                vector("four-equal-b", 0.5, 0.5, 0.5, 0.5),
                vector("four-mixed-a", 1.0, 0.0, 1.0, 0.0),
                vector("four-mixed-b", 0.0, 1.0, 0.0, 1.0));
        assertFalse(ParetoMemoryRanker.computeObjectiveFronts(fourObjectiveCases).isEmpty());
        assertEquals("third-dimension-dominates", ParetoMemoryRanker.computeObjectiveFronts(List.of(
                vector("lower-third-dimension", 1.0, 1.0, 0.0),
                vector("third-dimension-dominates", 1.0, 1.0, 1.0))).get(0).get(0).item());
        assertEquals("middle-dimension-dominates", ParetoMemoryRanker.computeObjectiveFronts(List.of(
                vector("lower-middle-dimension", 1.0, 0.0, 1.0),
                vector("middle-dimension-dominates", 1.0, 1.0, 1.0))).get(0).get(0).item());

        assertEquals(0.0, new ObjectiveVector<>("negative-crowding", List.of(0.5), 0.5, -1.0, 1)
                .crowdingDistance());

        MemoryNode sameStateA = node("same-state-a", new float[]{1.0f});
        sameStateA.setTimestamp(1L);
        sameStateA.setAccessCount(0);
        MemoryNode sameStateB = node("same-state-b", new float[]{1.0f});
        sameStateB.setTimestamp(1L);
        sameStateB.setAccessCount(0);
        assertEquals(1, ParetoMemoryRanker.rankTopK(
                List.of(sameStateA, sameStateB), new float[]{1.0f}, 1, 1L, scoring).size());
    }

    @Test
    void testExecutionLatency() {
        int n = 1_000;
        int dimensions = 1_024;
        long now = 1_800_000_000_000L;
        Random random = new Random(292);
        List<MemoryNode> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float[] embedding = new float[dimensions];
            double norm = 0.0;
            for (int d = 0; d < dimensions; d++) {
                embedding[d] = random.nextFloat() - 0.5f;
                norm += embedding[d] * embedding[d];
            }
            float scale = (float) (1.0 / Math.sqrt(norm));
            for (int d = 0; d < dimensions; d++) {
                embedding[d] *= scale;
            }
            candidates.add(MemoryNode.builder().id("m-" + i).embedding(embedding)
                    .importance(random.nextDouble()).timestamp(now - random.nextLong(30L * 86400000L))
                    .decayRate(0.05).accessCount(random.nextInt(20)).build());
        }
        float[] query = new float[dimensions];
        query[0] = 1.0f;
        MemoryScoringServiceImpl scoring = new MemoryScoringServiceImpl();

        for (int i = 0; i < 5; i++) {
            ParetoMemoryRanker.rankTopK(candidates, query, 20, now, scoring);
        }
        long[] elapsedNanos = new long[9];
        for (int i = 0; i < elapsedNanos.length; i++) {
            long started = System.nanoTime();
            List<MemoryNode> result = ParetoMemoryRanker.rankTopK(candidates, query, 20, now, scoring);
            elapsedNanos[i] = System.nanoTime() - started;
            assertEquals(20, result.size());
        }
        java.util.Arrays.sort(elapsedNanos);
        double medianMillis = elapsedNanos[elapsedNanos.length / 2] / 1_000_000.0;
        System.out.printf("pareto_n1000_embedding1024_median_ms=%.3f%n", medianMillis);
        System.out.println("pareto_n1000_embedding1024_sorted_samples_ns=" + java.util.Arrays.toString(elapsedNanos));
        assertTrue(medianMillis <= 5.0, "Median ranking latency exceeded 5.0 ms: " + medianMillis);
    }

    private static MemoryObjectiveVector objective(String id, double recency, double relevance, double importance) {
        return new MemoryObjectiveVector(node(id, null), recency, relevance, importance, 0.0, 0);
    }

    private static ObjectiveVector<String> vector(String item, double... objectives) {
        List<Double> values = new ArrayList<>(objectives.length);
        for (double value : objectives) {
            values.add(value);
        }
        return new ObjectiveVector<>(item, values, objectives.length > 1 ? objectives[1] : objectives[0], 0.0, 0);
    }

    private static MemoryNode node(String id, float[] embedding) {
        return MemoryNode.builder().id(id).embedding(embedding).importance(0.5).timestamp(1L).build();
    }
}
