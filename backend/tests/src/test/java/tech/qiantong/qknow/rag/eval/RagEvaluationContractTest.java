package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagEvaluationContractTest {

    private static final Set<String> REQUIRED_STRATA = Set.of(
            "short", "follow_up", "exact_number", "negation", "unanswerable", "multi_hop", "temporal");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern ZH_EXACT_NUMBER = Pattern.compile(
            ".*(?:精确|准确|多少|编号|容量).*", Pattern.DOTALL);
    private static final Pattern EN_EXACT_NUMBER = Pattern.compile(
            ".*\\b(?:exact|how many|number|capacity)\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ZH_NEGATION = Pattern.compile(".*(?:不|无|未|非|禁止|不得|不能|没有).*", Pattern.DOTALL);
    private static final Pattern EN_NEGATION = Pattern.compile(
            ".*\\b(?:not|no|never|without|forbidden|prohibited|cannot|isn't|wasn't|doesn't|don't)\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ZH_TEMPORAL = Pattern.compile(
            ".*(?:何时|日期|时间|生效|之前|之后|当前|历史|哪年|哪天|什么时候|\\d{4}).*", Pattern.DOTALL);
    private static final Pattern EN_TEMPORAL = Pattern.compile(
            ".*\\b(?:when|date|time|effective|before|after|current|historical)\\b.*|.*\\d{4}.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static RagEvaluationDataset dataset;

    @BeforeAll
    static void loadDataset() {
        dataset = RagEvaluationDatasetLoader.loadDefault();
    }

    @Test
    void datasetHasFixedFamilyLanguageAndSplitShape() {
        assertEquals(96, dataset.queries().size());
        Map<String, List<RagEvaluationDataset.QueryCase>> families = dataset.queries().stream()
                .collect(Collectors.groupingBy(RagEvaluationDataset.QueryCase::familyId));
        assertEquals(48, families.size());
        assertEquals(12, families.values().stream()
                .filter(cases -> cases.get(0).split().equals("calibration"))
                .count());
        assertEquals(36, families.values().stream()
                .filter(cases -> cases.get(0).split().equals("test"))
                .count());

        families.forEach((familyId, cases) -> {
            assertEquals(2, cases.size(), familyId);
            assertEquals(Set.of("zh", "en"), cases.stream()
                    .map(RagEvaluationDataset.QueryCase::language)
                    .collect(Collectors.toSet()), familyId);
            assertEquals(1, cases.stream().map(RagEvaluationDataset.QueryCase::split).distinct().count(), familyId);
        });
    }

    @Test
    void datasetCoversRequiredMultiLabelStrata() {
        for (String stratum : REQUIRED_STRATA) {
            List<RagEvaluationDataset.QueryCase> matching = dataset.queries().stream()
                    .filter(query -> query.strata().contains(stratum))
                    .toList();
            assertTrue(matching.size() >= 30, stratum);
            assertTrue(matching.stream().filter(query -> query.language().equals("zh")).count() >= 10, stratum);
            assertTrue(matching.stream().filter(query -> query.language().equals("en")).count() >= 10, stratum);
        }
    }

    @Test
    void datasetHasExactUnanswerableDistribution() {
        List<RagEvaluationDataset.QueryCase> unanswerable = dataset.queries().stream()
                .filter(query -> !query.answerable())
                .toList();
        assertEquals(30, unanswerable.size());
        assertEquals(15, count(unanswerable, query -> query.language().equals("zh")));
        assertEquals(15, count(unanswerable, query -> query.language().equals("en")));
        assertEquals(4, count(unanswerable,
                query -> query.language().equals("zh") && query.split().equals("calibration")));
        assertEquals(4, count(unanswerable,
                query -> query.language().equals("en") && query.split().equals("calibration")));
        assertEquals(11, count(unanswerable,
                query -> query.language().equals("zh") && query.split().equals("test")));
        assertEquals(11, count(unanswerable,
                query -> query.language().equals("en") && query.split().equals("test")));
    }

    @Test
    void queriesAndQrelsSatisfyEvaluationContract() {
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            assertFalse(query.query().isBlank(), query.id());
            assertFalse(query.retrievalQuery().isBlank(), query.id());
            if (query.strata().contains("follow_up")) {
                assertFalse(query.history().isEmpty(), query.id());
            }

            Map<String, Integer> qrels = dataset.qrelsFor(query.id());
            if (query.answerable()) {
                assertFalse(qrels.isEmpty(), query.id());
                assertNotNull(query.referenceAnswer(), query.id());
                assertFalse(query.referenceAnswer().isBlank(), query.id());
                assertFalse(query.referenceClaims().isEmpty(), query.id());
            } else {
                assertTrue(qrels.isEmpty(), query.id());
            }
            qrels.forEach((segmentId, grade) -> {
                assertTrue(grade >= 1 && grade <= 3, query.id());
                assertTrue(dataset.corpusById().containsKey(segmentId), query.id() + ":" + segmentId);
            });
        }
    }

    @Test
    void qrelsExactlyCoverAnswerableQueriesAndReferenceKnownSegments() {
        Set<String> answerableIds = dataset.queries().stream()
                .filter(RagEvaluationDataset.QueryCase::answerable)
                .map(RagEvaluationDataset.QueryCase::id)
                .collect(Collectors.toSet());
        Map<String, RagEvaluationDataset.QueryCase> queriesById = dataset.queries().stream()
                .collect(Collectors.toMap(RagEvaluationDataset.QueryCase::id, query -> query));

        assertEquals(answerableIds, dataset.qrels().keySet());
        dataset.qrels().forEach((queryId, grades) -> {
            assertTrue(queriesById.containsKey(queryId), queryId);
            grades.forEach((segmentId, grade) -> {
                assertTrue(dataset.corpusById().containsKey(segmentId), queryId + ":" + segmentId);
                assertTrue(grade >= 1 && grade <= 3, queryId + ":" + segmentId);
            });
        });
    }

    @Test
    void loaderValidationFailsFastOnBrokenQrelReferences() {
        RagEvaluationDataset.CorpusSegment segment = new RagEvaluationDataset.CorpusSegment(
                "segment-1", "document-1", "synthetic evidence", null, Map.of());
        RagEvaluationDataset.QueryCase query = new RagEvaluationDataset.QueryCase(
                "query-1", "family-1", "question", "retrieval question", List.of(), "en",
                Set.of("short"), "test", true, "answer", List.of("claim"));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> RagEvaluationDatasetLoader.validate(
                        new RagEvaluationDataset(Map.of("segment-1", segment), List.of(query),
                                Map.of("unknown-query", Map.of("segment-1", 3))))),
                () -> assertThrows(IllegalArgumentException.class, () -> RagEvaluationDatasetLoader.validate(
                        new RagEvaluationDataset(Map.of("segment-1", segment), List.of(query),
                                Map.of("query-1", Map.of("unknown-segment", 3))))),
                () -> assertThrows(IllegalArgumentException.class, () -> RagEvaluationDatasetLoader.validate(
                        new RagEvaluationDataset(Map.of("segment-1", segment), List.of(query),
                                Map.of("query-1", Map.of("segment-1", 4)))))
        );
    }

    @Test
    void strataLabelsDescribeActualQueryAndGroundTruthSemantics() {
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            Map<String, Integer> qrels = dataset.qrelsFor(query.id());
            if (query.strata().contains("follow_up")) {
                assertFalse(query.history().isEmpty(), query.id());
                assertFalse(query.query().equals(query.retrievalQuery()), query.id());
            }
            assertEquals(!query.answerable(), query.strata().contains("unanswerable"), query.id());
            if (query.strata().contains("multi_hop")) {
                assertTrue(qrels.size() >= 2, query.id());
                assertTrue(qrels.values().stream().filter(grade -> grade == 2).count() >= 2, query.id());
                assertFalse(qrels.containsValue(3), query.id());
                if (query.strata().contains("temporal")) {
                    assertTrue(qrels.containsValue(1), query.id() + ": temporal background");
                }
            }
            if (query.strata().contains("negation")) {
                Pattern pattern = query.language().equals("zh") ? ZH_NEGATION : EN_NEGATION;
                assertTrue(pattern.matcher(query.query()).matches(), query.id() + ": " + query.query());
            }
            if (query.strata().contains("exact_number")) {
                Pattern pattern = query.language().equals("zh") ? ZH_EXACT_NUMBER : EN_EXACT_NUMBER;
                assertTrue(pattern.matcher(query.query()).matches(), query.id() + ": " + query.query());
                if (query.answerable()) {
                    assertFalse(numbers(query.referenceAnswer()).isEmpty(), query.id() + ": reference");
                }
            }
            if (query.strata().contains("temporal")) {
                Pattern pattern = query.language().equals("zh") ? ZH_TEMPORAL : EN_TEMPORAL;
                assertTrue(pattern.matcher(query.query()).matches(), query.id() + ": " + query.query());
                assertTrue(pattern.matcher(query.referenceAnswer()).matches(), query.id() + ": reference");
            }
            if (query.answerable()) {
                String normalizedQuery = query.query().toLowerCase(java.util.Locale.ROOT);
                String normalizedRetrieval = query.retrievalQuery().toLowerCase(java.util.Locale.ROOT);
                if (query.referenceClaims().contains("external_export=forbidden")) {
                    assertFalse(normalizedQuery.contains("外部导出"), query.id());
                    assertFalse(normalizedQuery.contains("external export"), query.id());
                    assertFalse(normalizedRetrieval.contains("外部导出"), query.id());
                    assertFalse(normalizedRetrieval.contains("external export"), query.id());
                }
                for (String claim : query.referenceClaims()) {
                    String[] parts = claim.split("=", 2);
                    if (parts.length == 2 && Set.of("capacity", "tier", "effective_date").contains(parts[0])) {
                        assertFalse(containsClaimValue(query.query(), parts[1]),
                                query.id() + ": answer leaked into query");
                        assertFalse(containsClaimValue(query.retrievalQuery(), parts[1]),
                                query.id() + ": answer leaked into retrievalQuery");
                    }
                }
            }
            if (query.strata().contains("multi_hop") && query.strata().contains("temporal")) {
                assertRetrievalIntent(query, "生效日期", "effective date");
                assertRetrievalIntent(query, "等级", "tier");
            } else if (query.strata().contains("multi_hop")) {
                assertRetrievalIntent(query, "容量", "capacity");
                assertRetrievalIntent(query, "禁止", "prohibited");
                assertRetrievalIntent(query, "等级", "tier");
            } else if (query.strata().contains("temporal")) {
                assertRetrievalIntent(query, "生效日期", "effective date");
                String retrieval = query.retrievalQuery().toLowerCase(java.util.Locale.ROOT);
                assertFalse(retrieval.contains("容量") || retrieval.contains("capacity"), query.id());
                assertFalse(retrieval.contains("等级") || retrieval.contains("tier"), query.id());
            }
        }
    }

    @Test
    void corpusContainsReservedSentinelsAndParentLink() {
        for (String id : List.of("990001", "990002", "990003", "990004", "990005")) {
            assertTrue(dataset.corpusById().containsKey(id), id);
        }
        RagEvaluationDataset.CorpusSegment child = dataset.corpusById().get("990004");
        RagEvaluationDataset.CorpusSegment parent = dataset.corpusById().get("990005");
        assertEquals("990005", child.parentSegmentId());
        assertTrue(parent.content().contains("CTX-SENTINEL-PARENT"));
    }

    @Test
    void qrelsAreStructurallySeparateFromRetrievalQueryAndCorpusRecords() {
        Set<String> queryFields = recordFields(RagEvaluationDataset.QueryCase.class);
        Set<String> corpusFields = recordFields(RagEvaluationDataset.CorpusSegment.class);

        assertFalse(queryFields.contains("qrels"));
        assertFalse(corpusFields.contains("queryId"));
        assertFalse(corpusFields.contains("referenceAnswer"));
        assertFalse(corpusFields.contains("referenceClaims"));
        assertFalse(dataset.qrels().isEmpty());
    }

    @Test
    void reportSerializesInvalidMetricWithExplicitNullValuesAndEvidenceLevel() {
        RagBenchmarkReport.MetricEstimate valid = RagBenchmarkReport.MetricEstimate.valid(0.75, 0.70, 0.80, 72);
        RagBenchmarkReport.MetricEstimate invalid = RagBenchmarkReport.MetricEstimate.invalid(
                RagBenchmarkReport.MetricErrorCode.SUT_SENTINEL_FAILED);
        RagBenchmarkReport report = new RagBenchmarkReport(
                "dataset-sha256",
                "config-sha256",
                RagBenchmarkReport.DatasetEvidenceLevel.ENGINEERING_BASELINE,
                Map.of("recallAt5", valid, "claimFaithfulness", invalid),
                Map.of("short", Map.of("recallAt5", valid)),
                List.of(new RagBenchmarkReport.FailureSample("q001-zh", "family-001", "MISS", "no source")));

        JSONObject serializedMetric = JSON.parseObject(JSON.toJSONString(report))
                .getJSONObject("metrics")
                .getJSONObject("claimFaithfulness");

        assertEquals(RagBenchmarkReport.MetricStatus.VALID, report.metrics().get("recallAt5").status());
        assertEquals(RagBenchmarkReport.MetricStatus.INVALID, report.metrics().get("claimFaithfulness").status());
        assertEquals(RagBenchmarkReport.DatasetEvidenceLevel.ENGINEERING_BASELINE, report.datasetEvidenceLevel());
        assertNull(report.metrics().get("claimFaithfulness").value());
        assertNull(report.metrics().get("claimFaithfulness").ciLow());
        assertNull(report.metrics().get("claimFaithfulness").ciHigh());
        assertEquals(RagBenchmarkReport.MetricErrorCode.SUT_SENTINEL_FAILED,
                report.metrics().get("claimFaithfulness").errorCode());
        assertTrue(serializedMetric.containsKey("value"));
        assertTrue(serializedMetric.containsKey("ciLow"));
        assertTrue(serializedMetric.containsKey("ciHigh"));
        assertNull(serializedMetric.get("value"));
        assertNull(serializedMetric.get("ciLow"));
        assertNull(serializedMetric.get("ciHigh"));
    }

    @Test
    void reportRejectsInvalidNumericIntervalsAndMissingErrorCode() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RagBenchmarkReport.MetricEstimate.valid(Double.NaN, 0.0, 1.0, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RagBenchmarkReport.MetricEstimate.valid(0.5, 0.6, 0.8, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> RagBenchmarkReport.MetricEstimate.invalid(null))
        );
    }

    private static long count(List<RagEvaluationDataset.QueryCase> queries,
                              Predicate<RagEvaluationDataset.QueryCase> predicate) {
        return queries.stream().filter(predicate).count();
    }

    private static Set<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> numbers(String value) {
        Set<String> numbers = new LinkedHashSet<>();
        java.util.regex.Matcher matcher = DIGITS.matcher(value);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }

    private static boolean containsClaimValue(String text, String claimValue) {
        if (claimValue.chars().allMatch(Character::isDigit)) {
            return numbers(text).contains(claimValue);
        }
        return text.toLowerCase(java.util.Locale.ROOT)
                .contains(claimValue.toLowerCase(java.util.Locale.ROOT));
    }

    private static void assertRetrievalIntent(RagEvaluationDataset.QueryCase query,
                                              String chineseIntent,
                                              String englishIntent) {
        String expected = query.language().equals("zh") ? chineseIntent : englishIntent;
        assertTrue(query.retrievalQuery().toLowerCase(java.util.Locale.ROOT).contains(expected),
                query.id() + ": retrieval intent " + expected);
    }
}
