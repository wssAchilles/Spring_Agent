package tech.qiantong.qknow.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * H0: shared retrieval metrics for live/keyword eval arms.
 * Hit@K uses expected document names against retrieved document names.
 */
public final class LiveRetrievalMetrics {

    private LiveRetrievalMetrics() {
    }

    public record CaseScores(double hitAt5, double hitAt10, double mrrAt10, double ndcgAt10) {
    }

    public record Aggregate(int n, double hitAt5, double hitAt10, double mrrAt10, double ndcgAt10) {
    }

    public static CaseScores score(List<String> expectedSources, List<String> retrievedDocNames) {
        List<String> expected = normalize(expectedSources);
        List<String> retrieved = retrievedDocNames == null ? List.of() : retrievedDocNames;
        return new CaseScores(
                hitAt(expected, retrieved, 5) ? 1.0 : 0.0,
                hitAt(expected, retrieved, 10) ? 1.0 : 0.0,
                reciprocalRank(expected, retrieved, 10),
                ndcgAt10(expected, retrieved));
    }

    public static Aggregate aggregate(List<CaseScores> scores) {
        if (scores == null || scores.isEmpty()) {
            return new Aggregate(0, 0, 0, 0, 0);
        }
        double h5 = 0, h10 = 0, mrr = 0, ndcg = 0;
        for (CaseScores s : scores) {
            h5 += s.hitAt5();
            h10 += s.hitAt10();
            mrr += s.mrrAt10();
            ndcg += s.ndcgAt10();
        }
        int n = scores.size();
        return new Aggregate(n, r(h5 / n), r(h10 / n), r(mrr / n), r(ndcg / n));
    }

    public static List<String> distinctDocNames(List<String> names) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        if (names == null) {
            return out;
        }
        for (String name : names) {
            if (name != null && seen.add(name)) {
                out.add(name);
            }
        }
        return out;
    }

    /** Match Day01 to Day01 / Day01.md / Day01.pdf / Day01-xxx */
    private static boolean matchesSource(String retrieved, List<String> expected) {
        String name = normalizeOne(retrieved);
        if (name.isBlank()) {
            return false;
        }
        for (String exp : expected) {
            if (name.equals(exp) || name.startsWith(exp + ".") || name.startsWith(exp + "-")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hitAt(List<String> expected, List<String> retrieved, int k) {
        int limit = Math.min(k, retrieved.size());
        for (int i = 0; i < limit; i++) {
            if (matchesSource(retrieved.get(i), expected)) {
                return true;
            }
        }
        return false;
    }

    private static double reciprocalRank(List<String> expected, List<String> retrieved, int k) {
        int limit = Math.min(k, retrieved.size());
        for (int i = 0; i < limit; i++) {
            if (matchesSource(retrieved.get(i), expected)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private static double ndcgAt10(List<String> expected, List<String> retrieved) {
        double dcg = 0;
        Set<String> seen = new LinkedHashSet<>();
        int limit = Math.min(10, retrieved.size());
        for (int i = 0; i < limit; i++) {
            if (matchesSource(retrieved.get(i), expected) && seen.add(normalizeOne(retrieved.get(i)))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        int ideal = Math.min(expected.size(), 10);
        double idcg = 0;
        for (int i = 0; i < ideal; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        return idcg == 0 ? 0 : r(dcg / idcg);
    }

    private static List<String> normalize(List<String> sources) {
        Set<String> out = new LinkedHashSet<>();
        if (sources != null) {
            for (String s : sources) {
                String n = normalizeOne(s);
                if (!n.isBlank()) {
                    out.add(n);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static String normalizeOne(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static double r(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
