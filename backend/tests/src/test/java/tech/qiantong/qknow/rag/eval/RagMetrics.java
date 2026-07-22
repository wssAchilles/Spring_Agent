package tech.qiantong.qknow.rag.eval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RagMetrics {

    private RagMetrics() {
    }

    public static Scores evaluate(Map<String, Integer> qrels, List<String> rankedSegmentIds) {
        validateQrels(qrels);
        List<String> ranking = deduplicate(rankedSegmentIds);
        return new Scores(
                recallAt(qrels.keySet(), ranking, 5),
                recallAt(qrels.keySet(), ranking, 10),
                reciprocalRank(qrels.keySet(), ranking),
                ndcgAt10(qrels, ranking),
                retrievalApAt10(qrels.keySet(), ranking));
    }

    public static double emptyRetrievalRate(List<? extends List<String>> unanswerableRankings) {
        if (unanswerableRankings == null || unanswerableRankings.isEmpty()) {
            throw new IllegalArgumentException("unanswerableRankings must not be empty");
        }
        long empty = unanswerableRankings.stream()
                .filter(ranking -> ranking == null || ranking.isEmpty())
                .count();
        return (double) empty / unanswerableRankings.size();
    }

    private static void validateQrels(Map<String, Integer> qrels) {
        if (qrels == null || qrels.isEmpty()) {
            throw new IllegalArgumentException("qrels must not be empty");
        }
        qrels.forEach((segmentId, grade) -> {
            if (segmentId == null || segmentId.isBlank() || grade == null || grade < 1 || grade > 3) {
                throw new IllegalArgumentException("qrels grades must be between 1 and 3");
            }
        });
    }

    private static List<String> deduplicate(List<String> rankedSegmentIds) {
        if (rankedSegmentIds == null || rankedSegmentIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String segmentId : rankedSegmentIds) {
            Objects.requireNonNull(segmentId, "ranked segmentId");
            if (segmentId.isBlank()) {
                throw new IllegalArgumentException("ranked segmentId must not be blank");
            }
            unique.add(segmentId);
        }
        return new ArrayList<>(unique);
    }

    private static double recallAt(Set<String> relevant, List<String> ranking, int k) {
        long hits = ranking.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / relevant.size();
    }

    private static double reciprocalRank(Set<String> relevant, List<String> ranking) {
        for (int i = 0; i < ranking.size(); i++) {
            if (relevant.contains(ranking.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private static double ndcgAt10(Map<String, Integer> qrels, List<String> ranking) {
        double dcg = 0.0;
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            int grade = qrels.getOrDefault(ranking.get(i), 0);
            dcg += (Math.pow(2.0, grade) - 1.0) / log2(i + 2.0);
        }
        List<Integer> ideal = qrels.values().stream().sorted(java.util.Comparator.reverseOrder()).limit(10).toList();
        double idcg = 0.0;
        for (int i = 0; i < ideal.size(); i++) {
            idcg += (Math.pow(2.0, ideal.get(i)) - 1.0) / log2(i + 2.0);
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    private static double retrievalApAt10(Set<String> relevant, List<String> ranking) {
        int hits = 0;
        double precisionSum = 0.0;
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            if (relevant.contains(ranking.get(i))) {
                hits++;
                precisionSum += (double) hits / (i + 1);
            }
        }
        return precisionSum / Math.min(relevant.size(), 10);
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    public record Scores(
            double recallAt5,
            double recallAt10,
            double mrr,
            double ndcgAt10,
            double retrievalApAt10
    ) {
    }
}
