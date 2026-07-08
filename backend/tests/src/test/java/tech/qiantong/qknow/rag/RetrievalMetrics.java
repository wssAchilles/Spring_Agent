package tech.qiantong.qknow.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    static Scores evaluate(List<String> expectedSources, List<RetrievedContext> retrieved) {
        Set<String> expected = normalizeExpected(expectedSources);
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("expectedSources must not be empty");
        }
        List<RetrievedContext> results = retrieved == null ? List.of() : retrieved;
        return new Scores(
                recallAt(expected, results, 5),
                recallAt(expected, results, 10),
                reciprocalRank(expected, results),
                ndcgAt(expected, results, 10),
                contextPrecisionAt(expected, results, 5),
                contextPrecisionAt(expected, results, 10)
        );
    }

    static GateResult gate(Scores scores, Thresholds thresholds) {
        List<String> failures = new ArrayList<>();
        if (scores.recallAt5() < thresholds.recallAt5()) {
            failures.add("recallAt5");
        }
        if (scores.recallAt10() < thresholds.recallAt10()) {
            failures.add("recallAt10");
        }
        if (scores.mrr() < thresholds.mrr()) {
            failures.add("mrr");
        }
        if (scores.ndcgAt10() < thresholds.ndcgAt10()) {
            failures.add("ndcgAt10");
        }
        if (scores.contextPrecisionAt5() < thresholds.contextPrecisionAt5()) {
            failures.add("contextPrecisionAt5");
        }
        return new GateResult(failures.isEmpty(), failures);
    }

    private static Set<String> normalizeExpected(List<String> expectedSources) {
        Set<String> expected = new LinkedHashSet<>();
        if (expectedSources == null) {
            return expected;
        }
        for (String source : expectedSources) {
            String normalized = normalize(source);
            if (!normalized.isBlank()) {
                expected.add(normalized);
            }
        }
        return expected;
    }

    private static double recallAt(Set<String> expected, List<RetrievedContext> retrieved, int k) {
        Set<String> found = new LinkedHashSet<>();
        for (RetrievedContext context : top(retrieved, k)) {
            for (String source : expected) {
                if (matchesSource(context, source)) {
                    found.add(source);
                }
            }
        }
        return (double) found.size() / expected.size();
    }

    private static double reciprocalRank(Set<String> expected, List<RetrievedContext> retrieved) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (isRelevantSource(retrieved.get(i), expected)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private static double ndcgAt(Set<String> expected, List<RetrievedContext> retrieved, int k) {
        double dcg = 0.0;
        Set<String> seenSources = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(k, retrieved.size()); i++) {
            String matched = firstUnseenMatchedSource(retrieved.get(i), expected, seenSources);
            if (matched != null) {
                seenSources.add(matched);
                dcg += 1.0 / log2(i + 2);
            }
        }

        double idcg = 0.0;
        int idealHits = Math.min(k, expected.size());
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    private static double contextPrecisionAt(Set<String> expected, List<RetrievedContext> retrieved, int k) {
        List<RetrievedContext> top = top(retrieved, k);
        if (top.isEmpty()) {
            return 0.0;
        }
        long hits = top.stream()
                .filter(context -> isRelevantSource(context, expected))
                .count();
        return (double) hits / top.size();
    }

    private static boolean isRelevantSource(RetrievedContext context, Set<String> expected) {
        for (String source : expected) {
            if (matchesSource(context, source)) {
                return true;
            }
        }
        return false;
    }

    private static String firstUnseenMatchedSource(RetrievedContext context, Set<String> expected, Set<String> seenSources) {
        for (String source : expected) {
            if (!seenSources.contains(source) && matchesSource(context, source)) {
                return source;
            }
        }
        return null;
    }

    private static boolean matchesSource(RetrievedContext context, String expectedSource) {
        return normalize(context.source()).contains(expectedSource)
                || normalize(context.documentName()).contains(expectedSource);
    }

    private static List<RetrievedContext> top(List<RetrievedContext> retrieved, int k) {
        return retrieved.subList(0, Math.min(k, retrieved.size()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    record RetrievedContext(String source, String documentName, String content) {
    }

    record Scores(
            double recallAt5,
            double recallAt10,
            double mrr,
            double ndcgAt10,
            double contextPrecisionAt5,
            double contextPrecisionAt10
    ) {
    }

    record Thresholds(
            double recallAt5,
            double recallAt10,
            double mrr,
            double ndcgAt10,
            double contextPrecisionAt5
    ) {
    }

    record GateResult(boolean passed, List<String> failures) {
    }
}
