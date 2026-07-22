package tech.qiantong.qknow.rag.eval;

import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RagShadowReportSupport {

    private RagShadowReportSupport() {
    }

    static RagBenchmarkReport buildReport(RagEvaluationDataset dataset,
                                           Map<String, RagResult> results,
                                           Map<String, ?> config) {
        List<RagEvaluationDataset.QueryCase> testCases = dataset.queries().stream()
                .filter(query -> "test".equals(query.split()))
                .toList();
        Map<String, List<Double>> recall5 = new LinkedHashMap<>();
        Map<String, List<Double>> recall10 = new LinkedHashMap<>();
        Map<String, List<Double>> mrr = new LinkedHashMap<>();
        Map<String, List<Double>> ndcg10 = new LinkedHashMap<>();
        Map<String, List<Double>> ap10 = new LinkedHashMap<>();
        Map<String, List<Double>> empty = new LinkedHashMap<>();
        List<RagBenchmarkReport.FailureSample> failures = new ArrayList<>();

        for (RagEvaluationDataset.QueryCase query : testCases) {
            RagResult result = results.get(query.id());
            if (result == null) {
                throw new IllegalArgumentException("Missing shadow result: " + query.id());
            }
            List<String> ranking = rankedSegmentIds(result);
            if (!query.answerable()) {
                add(empty, query.familyId(), ranking.isEmpty() ? 1.0 : 0.0);
                continue;
            }
            RagMetrics.Scores scores = RagMetrics.evaluate(dataset.qrelsFor(query.id()), ranking);
            add(recall5, query.familyId(), scores.recallAt5());
            add(recall10, query.familyId(), scores.recallAt10());
            add(mrr, query.familyId(), scores.mrr());
            add(ndcg10, query.familyId(), scores.ndcgAt10());
            add(ap10, query.familyId(), scores.retrievalApAt10());
            if (ranking.isEmpty()) {
                failures.add(new RagBenchmarkReport.FailureSample(
                        query.id(), query.familyId(), "EMPTY_RETRIEVAL", "answerable query returned no sources"));
            }
        }

        Map<String, RagBenchmarkReport.MetricEstimate> metrics = new LinkedHashMap<>();
        put(metrics, "Recall@5", recall5);
        put(metrics, "Recall@10", recall10);
        put(metrics, "MRR", mrr);
        put(metrics, "nDCG@10", ndcg10);
        put(metrics, "RetrievalAP@10", ap10);
        put(metrics, "EmptyRetrievalRate", empty);

        Map<String, Map<String, RagBenchmarkReport.MetricEstimate>> strata = new LinkedHashMap<>();
        for (String stratum : requiredStrata(dataset)) {
            List<RagEvaluationDataset.QueryCase> stratumCases = testCases.stream()
                    .filter(query -> query.strata().contains(stratum))
                    .toList();
            Map<String, List<Double>> stratumRecall10 = new LinkedHashMap<>();
            for (RagEvaluationDataset.QueryCase query : stratumCases) {
                if (!query.answerable()) {
                    continue;
                }
                RagMetrics.Scores scores = RagMetrics.evaluate(
                        dataset.qrelsFor(query.id()), rankedSegmentIds(results.get(query.id())));
                add(stratumRecall10, query.familyId(), scores.recallAt10());
            }
            if (!stratumRecall10.isEmpty()) {
                strata.put(stratum, Map.of("Recall@10", estimate(stratumRecall10)));
            }
        }

        return new RagBenchmarkReport(
                ShadowContractSupport.datasetHash(),
                ShadowContractSupport.configHash(config),
                RagBenchmarkReport.DatasetEvidenceLevel.ENGINEERING_BASELINE,
                metrics,
                strata,
                failures);
    }

    static Map<String, Map<String, List<Double>>> familyScores(
            RagEvaluationDataset dataset, Map<String, RagResult> results) {
        List<RagEvaluationDataset.QueryCase> testCases = dataset.queries().stream()
                .filter(query -> "test".equals(query.split()))
                .toList();
        Map<String, List<Double>> ndcg10 = new LinkedHashMap<>();
        Map<String, List<Double>> ap10 = new LinkedHashMap<>();
        Map<String, List<Double>> empty = new LinkedHashMap<>();
        for (RagEvaluationDataset.QueryCase query : testCases) {
            RagResult result = results.get(query.id());
            if (result == null) {
                throw new IllegalArgumentException("Missing shadow result: " + query.id());
            }
            List<String> ranking = rankedSegmentIds(result);
            if (!query.answerable()) {
                add(empty, query.familyId(), ranking.isEmpty() ? 1.0 : 0.0);
                continue;
            }
            RagMetrics.Scores scores = RagMetrics.evaluate(dataset.qrelsFor(query.id()), ranking);
            add(ndcg10, query.familyId(), scores.ndcgAt10());
            add(ap10, query.familyId(), scores.retrievalApAt10());
        }
        Map<String, Map<String, List<Double>>> familyScores = new LinkedHashMap<>();
        familyScores.put("RetrievalAP@10", ap10);
        familyScores.put("nDCG@10", ndcg10);
        familyScores.put("EmptyRetrievalRate", empty);
        return familyScores;
    }

    static List<String> rankedSegmentIds(RagResult result) {
        if (result == null || result.getSources() == null) {
            return List.of();
        }
        List<String> ranking = new ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (RetrievalResult source : result.getSources()) {
            if (source != null && source.getSegmentId() != null
                    && seen.add(String.valueOf(source.getSegmentId()))) {
                ranking.add(String.valueOf(source.getSegmentId()));
            }
        }
        return ranking;
    }

    private static List<String> requiredStrata(RagEvaluationDataset dataset) {
        return List.of("short", "follow_up", "exact_number", "negation", "unanswerable", "multi_hop", "temporal");
    }

    private static void put(Map<String, RagBenchmarkReport.MetricEstimate> target,
                            String name, Map<String, List<Double>> values) {
        if (!values.isEmpty()) {
            target.put(name, estimate(values));
        }
    }

    private static RagBenchmarkReport.MetricEstimate estimate(Map<String, List<Double>> values) {
        FamilyClusterBootstrap.ConfidenceInterval interval = new FamilyClusterBootstrap().mean(values);
        return RagBenchmarkReport.MetricEstimate.valid(
                interval.estimate(), interval.ciLow(), interval.ciHigh(), interval.clusters());
    }

    private static void add(Map<String, List<Double>> values, String family, double value) {
        values.computeIfAbsent(family, ignored -> new ArrayList<>()).add(value);
    }
}
