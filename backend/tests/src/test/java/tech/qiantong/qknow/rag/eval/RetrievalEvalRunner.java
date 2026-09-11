package tech.qiantong.qknow.rag.eval;

import tech.qiantong.qknow.rag.LiveRetrievalMetrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Phase 01: document-level retrieval eval runner (IR only, zero LLM).
 * Reuses {@link LiveRetrievalMetrics} for Hit/MRR/NDCG.
 */
public final class RetrievalEvalRunner {

    private RetrievalEvalRunner() {
    }

    public record CaseInput(String id, String query, String stratum, boolean answerable,
                            List<String> expectedSources, List<String> retrievedDocs) {
    }

    public record CaseResult(String id, double hitAt5, double hitAt10, double mrrAt10, double ndcgAt10) {
    }

    public record Report(int n, int negativeCount, double negativeFpRate, double macroHitAt10,
                         LiveRetrievalMetrics.Aggregate overall,
                         Map<String, LiveRetrievalMetrics.Aggregate> byStratum) {
    }

    public record PairedCompare(double meanDiff, double ciLow, double ciHigh, double pValueApprox) {
    }

    public static CaseResult score(CaseInput in) {
        LiveRetrievalMetrics.CaseScores s = LiveRetrievalMetrics.score(
                in.expectedSources() == null ? List.of() : in.expectedSources(),
                in.retrievedDocs());
        return new CaseResult(in.id(), s.hitAt5(), s.hitAt10(), s.mrrAt10(), s.ndcgAt10());
    }

    public static Report aggregate(List<CaseInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return new Report(0, 0, 0.0, 0.0,
                    new LiveRetrievalMetrics.Aggregate(0, 0, 0, 0, 0), Map.of());
        }
        Map<String, List<LiveRetrievalMetrics.CaseScores>> byStratum = new TreeMap<>();
        List<LiveRetrievalMetrics.CaseScores> all = new ArrayList<>();
        int negativeCount = 0;
        int negativeNonEmpty = 0;
        for (CaseInput in : inputs) {
            if (!in.answerable()) {
                negativeCount++;
                if (in.retrievedDocs() != null && !in.retrievedDocs().isEmpty()) {
                    negativeNonEmpty++;
                }
                continue; // negatives excluded from Hit/MRR
            }
            LiveRetrievalMetrics.CaseScores s = LiveRetrievalMetrics.score(
                    in.expectedSources() == null ? List.of() : in.expectedSources(),
                    in.retrievedDocs());
            all.add(s);
            byStratum.computeIfAbsent(normalizeStratum(in.stratum()), k -> new ArrayList<>()).add(s);
        }
        Map<String, LiveRetrievalMetrics.Aggregate> byStratumAgg = new LinkedHashMap<>();
        double macroSum = 0;
        int strata = 0;
        for (Map.Entry<String, List<LiveRetrievalMetrics.CaseScores>> e : byStratum.entrySet()) {
            LiveRetrievalMetrics.Aggregate agg = LiveRetrievalMetrics.aggregate(e.getValue());
            byStratumAgg.put(e.getKey(), agg);
            macroSum += agg.hitAt10();
            strata++;
        }
        double macroHitAt10 = strata == 0 ? 0.0 : round(macroSum / strata);
        double fpRate = negativeCount == 0 ? 0.0 : round((double) negativeNonEmpty / negativeCount);
        return new Report(
                inputs.size(),
                negativeCount,
                fpRate,
                macroHitAt10,
                LiveRetrievalMetrics.aggregate(all),
                byStratumAgg);
    }

    /**
     * Paired bootstrap on per-query metric arrays (same length).
     * Returns mean(A-B) and percentile CI; pValueApprox = two-sided bootstrap p.
     */
    public static PairedCompare pairedCompare(double[] a, double[] b, long seed) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return new PairedCompare(0, 0, 0, 1.0);
        }
        int n = a.length;
        double sum = 0;
        double[] diffs = new double[n];
        for (int i = 0; i < n; i++) {
            diffs[i] = a[i] - b[i];
            sum += diffs[i];
        }
        double mean = sum / n;
        int iters = 1000;
        double[] boot = new double[iters];
        java.util.Random rnd = new java.util.Random(seed);
        for (int t = 0; t < iters; t++) {
            double s = 0;
            for (int i = 0; i < n; i++) {
                s += diffs[rnd.nextInt(n)];
            }
            boot[t] = s / n;
        }
        java.util.Arrays.sort(boot);
        double lo = boot[(int) Math.floor(0.025 * (iters - 1))];
        double hi = boot[(int) Math.ceil(0.975 * (iters - 1))];
        int asExtreme = 0;
        for (double v : boot) {
            if (Math.abs(v) >= Math.abs(mean) - 1e-12) {
                asExtreme++;
            }
        }
        double p = (double) asExtreme / iters;
        return new PairedCompare(round(mean), round(lo), round(hi), round(p));
    }

    private static String normalizeStratum(String s) {
        return s == null || s.isBlank() ? "unknown" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
