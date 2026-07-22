package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate10CounterfactualContractTest {

    private static final RagCandidate10DiagnosticSupport.Eligibility ACTIVE =
            new RagCandidate10DiagnosticSupport.Eligibility(true, true, true);

    @Test
    void boundedAdmissionReplacesOnlyTail() {
        List<Scenario> scenarios = List.of(
                new Scenario("size-29", 29, "document 034",
                        Map.of(29, "policy-034.txt"), ACTIVE, true, null),
                new Scenario("size-30", 30, "document 034",
                        Map.of(30, "policy-034.txt"), ACTIVE, true, null),
                new Scenario("rank-31", 31,
                        "document 034 document 056",
                        Map.of(5, "policy-034.txt",
                                31, "policy-034-056.txt"),
                        ACTIVE, true, 31),
                new Scenario("rank-60", 60,
                        "document 034 document 056",
                        Map.of(5, "policy-034.txt",
                                60, "policy-034-056.txt"),
                        ACTIVE, true, 60),
                new Scenario("rank-61", 61, "document 034",
                        Map.of(61, "policy-034.txt"), ACTIVE, true, null),
                new Scenario("prefix-and", 61,
                        "document 034 document 056",
                        Map.of(8, "policy-034-056.txt",
                                31, "policy-034-056.txt"),
                        ACTIVE, true, null),
                new Scenario("rank30-or", 61,
                        "document 034 document 056",
                        Map.of(30, "policy-034.txt",
                                31, "policy-034-056.txt"),
                        ACTIVE, true, null),
                new Scenario("partial-multi", 61,
                        "document 034 document 056",
                        Map.of(31, "policy-034.txt"), ACTIVE, true, null),
                new Scenario("zero-padding", 61, "policy 07621",
                        Map.of(31, "policy-7621.txt"), ACTIVE, true, null),
                new Scenario("boundary", 61, "document 034",
                        Map.of(31, "policy-1034.txt"), ACTIVE, true, null),
                new Scenario("no-id", 61, "knowledge graph",
                        Map.of(31, "policy-034.txt"), ACTIVE, true, null),
                new Scenario("three-id", 61,
                        "document 034 document 056 document 078",
                        Map.of(31, "policy-034-056-078.txt"),
                        ACTIVE, true, null),
                new Scenario("candidate3-disabled", 61, "document 034",
                        Map.of(31, "policy-034.txt"),
                        new RagCandidate10DiagnosticSupport.Eligibility(
                                true, false, true), false, null),
                new Scenario("deterministic-inactive", 61, "document 034",
                        Map.of(31, "policy-034.txt"),
                        new RagCandidate10DiagnosticSupport.Eligibility(
                                true, true, false), true, null));

        for (Scenario scenario : scenarios) {
            verifyScenario(scenario);
        }
    }

    private static void verifyScenario(Scenario scenario) {
        ArmInput baselineInput = armInput(scenario);
        ArmInput candidateInput = armInput(scenario);
        assertNoSharedMutableObjects(baselineInput.originals(),
                candidateInput.originals());

        CountingColbertScorer baselineScorer = scorer();
        CountingColbertScorer candidateScorer = scorer();
        int businessTopK = Math.min(
                RagCandidate10DiagnosticSupport.BUSINESS_LIMIT,
                scenario.size());
        List<Document> baselineBusiness = baselineScorer.rerank(
                scenario.query(), baselineInput.documents(), businessTopK);
        List<Document> candidateFull = candidateScorer.rerank(
                scenario.query(), candidateInput.documents(), scenario.size());

        assertEquals(1, baselineScorer.calls());
        assertEquals(1, candidateScorer.calls());
        assertEquals(2, baselineScorer.calls() + candidateScorer.calls());
        assertEquals(baselineScorer.query(), candidateScorer.query());
        assertEquals(baselineScorer.documentTexts(),
                candidateScorer.documentTexts());
        assertEquals(0, candidateScorer.queryTokenCount()
                - baselineScorer.queryTokenCount());
        assertEquals(0, candidateScorer.documentTokenCount()
                - baselineScorer.documentTokenCount());
        assertPostColbertRanking(baselineInput.originals(), baselineBusiness,
                scenario.name() + " baseline");
        assertPostColbertRanking(candidateInput.originals(), candidateFull,
                scenario.name() + " candidate");
        assertEquals(documentSnapshots(baselineBusiness),
                documentSnapshots(candidateFull.subList(0, businessTopK)),
                scenario.name());

        List<RetrievalResult> baselineOracle = toRetrievalResults(
                baselineInput.originals(), baselineBusiness);
        List<RetrievalResult> candidateOracle = toRetrievalResults(
                candidateInput.originals(), candidateFull);
        assertRetrievalListsEqual(baselineOracle,
                candidateOracle.subList(0, businessTopK), scenario.name());
        assertOracleDetachedFromDocuments(baselineBusiness, baselineOracle);
        assertOracleDetachedFromDocuments(candidateFull, candidateOracle);

        List<DocumentSnapshot> baselineDocumentsBeforeSupport =
                documentSnapshots(baselineInput.documents());
        List<DocumentSnapshot> candidateDocumentsBeforeSupport =
                documentSnapshots(candidateInput.documents());
        List<ResultSnapshot> baselineOriginalsBeforeSupport =
                resultSnapshots(baselineInput.originals());
        List<ResultSnapshot> candidateOriginalsBeforeSupport =
                resultSnapshots(candidateInput.originals());

        RagCandidate10DiagnosticSupport.FullRankingSnapshot baselineSnapshot =
                RagCandidate10DiagnosticSupport.snapshotFullRanking(
                        originalsForDocuments(
                                baselineInput.originals(), baselineBusiness),
                        baselineBusiness);
        RagCandidate10DiagnosticSupport.FullRankingSnapshot candidateSnapshot =
                RagCandidate10DiagnosticSupport.snapshotFullRanking(
                        candidateInput.originals(), candidateFull);
        assertEquals(baselineSnapshot.rows(),
                candidateSnapshot.rows().subList(0, businessTopK),
                scenario.name());
        assertRankingMatchesOracle(baselineSnapshot.rows(), baselineOracle,
                scenario.name() + " baseline");
        assertRankingMatchesOracle(candidateSnapshot.rows(), candidateOracle,
                scenario.name() + " candidate");

        RagCandidate10DiagnosticSupport.AdmissionResult baseline =
                RagCandidate10DiagnosticSupport.admit(
                        baselineSnapshot, scenario.query(),
                        new RagCandidate10DiagnosticSupport.Eligibility(
                                false,
                                scenario.eligibility().candidate3Enabled(),
                                scenario.eligibility().deterministicPath()));
        RagCandidate10DiagnosticSupport.AdmissionResult candidate =
                RagCandidate10DiagnosticSupport.admit(
                        candidateSnapshot, scenario.query(),
                        scenario.eligibility());
        int expectedCapacity = Math.min(
                RagCandidate10DiagnosticSupport.BUSINESS_LIMIT,
                scenario.size());
        assertEquals(baselineSnapshot.rows().subList(0, expectedCapacity),
                baseline.baseline(), scenario.name());
        assertEquals(expectedCapacity, candidate.candidate().size(), scenario.name());
        assertEquals(scenario.admittedRank(), candidate.admittedFullRank(),
                scenario.name());

        List<RetrievalResult> baselineBeforeCandidate3 =
                baseline.materializeBaseline();
        List<RetrievalResult> candidateBeforeCandidate3 =
                candidate.materializeCandidate();
        assertMaterializationMatches(baseline.baseline(),
                baselineBeforeCandidate3, scenario.name() + " baseline");
        assertMaterializationMatches(candidate.candidate(),
                candidateBeforeCandidate3, scenario.name() + " candidate");

        List<RetrievalResult> baselineRepeated = baseline.materializeBaseline();
        List<RetrievalResult> candidateRepeated = candidate.materializeCandidate();
        assertRetrievalListsEqual(baselineBeforeCandidate3, baselineRepeated,
                scenario.name() + " repeated baseline");
        assertRetrievalListsEqual(candidateBeforeCandidate3, candidateRepeated,
                scenario.name() + " repeated candidate");

        List<RetrievalResult> mutationProbe = candidate.materializeCandidate();
        List<RetrievalResult> unaffected = candidate.materializeCandidate();
        assertNoSharedMutableObjects(
                baselineInput.originals(), candidateInput.originals(),
                baselineOracle, candidateOracle,
                baselineBeforeCandidate3, candidateBeforeCandidate3,
                baselineRepeated, candidateRepeated, mutationProbe, unaffected);

        List<RetrievalResult> baselineAfterCandidate3 = runCandidate3(
                scenario.query(), baseline.materializeBaseline(),
                scenario.candidate3Enabled());
        List<RetrievalResult> candidateAfterCandidate3 = runCandidate3(
                scenario.query(), candidate.materializeCandidate(),
                scenario.candidate3Enabled());

        if (scenario.admittedRank() == null) {
            assertFalse(candidate.admitted(), scenario.name());
            assertRetrievalListsEqual(baselineBeforeCandidate3,
                    candidateBeforeCandidate3, scenario.name());
            assertRetrievalListsEqual(baselineAfterCandidate3,
                    candidateAfterCandidate3, scenario.name());
        } else {
            assertTrue(candidate.admitted(), scenario.name());
            assertEquals(baseline.baseline().subList(0, 29),
                    candidate.candidate().subList(0, 29), scenario.name());
            assertEquals(scenario.admittedRank(),
                    candidate.candidate().get(29).fullRank(), scenario.name());
            Long admittedId = candidate.candidate().get(29).segmentId();
            assertFalse(baselineAfterCandidate3.stream().anyMatch(
                    item -> admittedId.equals(item.getSegmentId())), scenario.name());
            assertTrue(candidateAfterCandidate3.stream().anyMatch(
                    item -> admittedId.equals(item.getSegmentId())), scenario.name());

            Long commonOrId = candidateSnapshot.rows().get(4).segmentId();
            assertEquals(commonOrId,
                    baselineAfterCandidate3.get(0).getSegmentId(), scenario.name());
            assertEquals(commonOrId,
                    candidateAfterCandidate3.get(0).getSegmentId(), scenario.name());
            assertEquals(baselineAfterCandidate3.get(0).getScore(),
                    candidateAfterCandidate3.get(0).getScore(), scenario.name());
        }

        if (!mutationProbe.isEmpty()) {
            mutationProbe.get(0).setContent("mutated");
            mutationProbe.get(0).getMetadata().put("mutated", true);
            assertFalse(unaffected.get(0).getMetadata().containsKey("mutated"));
            assertRetrievalListsEqual(candidateBeforeCandidate3, unaffected,
                    scenario.name() + " mutation isolation");
        }
        assertEquals(baselineDocumentsBeforeSupport,
                documentSnapshots(baselineInput.documents()), scenario.name());
        assertEquals(candidateDocumentsBeforeSupport,
                documentSnapshots(candidateInput.documents()), scenario.name());
        assertEquals(baselineOriginalsBeforeSupport,
                resultSnapshots(baselineInput.originals()), scenario.name());
        assertEquals(candidateOriginalsBeforeSupport,
                resultSnapshots(candidateInput.originals()), scenario.name());
    }

    private static void assertMaterializationMatches(
            List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> snapshots,
            List<RetrievalResult> materialized,
            String message) {
        assertEquals(snapshots.size(), materialized.size(), message);
        for (int index = 0; index < materialized.size(); index++) {
            RagCandidate10DiagnosticSupport.RetrievalSnapshot snapshot =
                    snapshots.get(index);
            RetrievalResult result = materialized.get(index);
            assertSnapshotMatchesResult(snapshot, result,
                    message + " row " + (index + 1));
        }
    }

    private static void assertRankingMatchesOracle(
            List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> rows,
            List<RetrievalResult> oracle,
            String message) {
        assertEquals(oracle.size(), rows.size(), message);
        for (int index = 0; index < rows.size(); index++) {
            RagCandidate10DiagnosticSupport.RetrievalSnapshot row = rows.get(index);
            assertEquals(index + 1, row.fullRank(), message);
            assertResultMatchesSnapshot(oracle.get(index), row,
                    message + " row " + (index + 1));
        }
    }

    private static void assertRetrievalListsEqual(
            List<RetrievalResult> expected,
            List<RetrievalResult> actual,
            String message) {
        assertEquals(expected.size(), actual.size(), message);
        for (int index = 0; index < expected.size(); index++) {
            assertRetrievalFields(expected.get(index), actual.get(index),
                    message + " row " + (index + 1));
        }
    }

    private static void assertRetrievalFields(
            RetrievalResult expected,
            RetrievalResult actual,
            String message) {
        assertEquals(expected.getSegmentId(), actual.getSegmentId(), message);
        assertEquals(expected.getQmSegmentId(), actual.getQmSegmentId(), message);
        assertEquals(expected.getParentSegmentId(),
                actual.getParentSegmentId(), message);
        assertEquals(expected.getDocumentId(), actual.getDocumentId(), message);
        assertEquals(expected.getDocumentName(), actual.getDocumentName(), message);
        assertEquals(expected.getContent(), actual.getContent(), message);
        assertEquals(expected.getAnswer(), actual.getAnswer(), message);
        assertEquals(expected.getScore(), actual.getScore(), message);
        assertEquals(expected.getSource(), actual.getSource(), message);
        assertMetadataEquals(expected.getMetadata(), actual.getMetadata(), message);
    }

    private static void assertSnapshotMatchesResult(
            RagCandidate10DiagnosticSupport.RetrievalSnapshot expected,
            RetrievalResult actual,
            String message) {
        assertEquals(expected.segmentId(), actual.getSegmentId(), message);
        assertEquals(expected.qmSegmentId(), actual.getQmSegmentId(), message);
        assertEquals(expected.parentSegmentId(),
                actual.getParentSegmentId(), message);
        assertEquals(expected.documentId(), actual.getDocumentId(), message);
        assertEquals(expected.documentName(), actual.getDocumentName(), message);
        assertEquals(expected.content(), actual.getContent(), message);
        assertEquals(expected.answer(), actual.getAnswer(), message);
        assertEquals(expected.score(), actual.getScore(), message);
        assertEquals(expected.source(), actual.getSource(), message);
        assertMetadataEquals(expected.metadata(), actual.getMetadata(), message);
    }

    private static void assertResultMatchesSnapshot(
            RetrievalResult expected,
            RagCandidate10DiagnosticSupport.RetrievalSnapshot actual,
            String message) {
        assertEquals(expected.getSegmentId(), actual.segmentId(), message);
        assertEquals(expected.getQmSegmentId(), actual.qmSegmentId(), message);
        assertEquals(expected.getParentSegmentId(),
                actual.parentSegmentId(), message);
        assertEquals(expected.getDocumentId(), actual.documentId(), message);
        assertEquals(expected.getDocumentName(), actual.documentName(), message);
        assertEquals(expected.getContent(), actual.content(), message);
        assertEquals(expected.getAnswer(), actual.answer(), message);
        assertEquals(expected.getScore(), actual.score(), message);
        assertEquals(expected.getSource(), actual.source(), message);
        assertMetadataEquals(expected.getMetadata(), actual.metadata(), message);
    }

    private static void assertMetadataEquals(
            Map<String, Object> expected,
            Map<String, Object> actual,
            String message) {
        assertEquals(expected, actual, message);
        assertEquals(new ArrayList<>(expected.keySet()),
                new ArrayList<>(actual.keySet()), message + " metadata order");
    }

    @SafeVarargs
    private static void assertNoSharedMutableObjects(
            List<RetrievalResult>... groups) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<RetrievalResult> group : groups) {
            assertTrue(seen.add(group), "shared mutable result list");
            for (RetrievalResult result : group) {
                assertTrue(seen.add(result), "shared RetrievalResult");
                if (result.getMetadata() != null) {
                    assertTrue(seen.add(result.getMetadata()),
                            "shared RetrievalResult metadata");
                }
            }
        }
    }

    private static void assertPostColbertRanking(
            List<RetrievalResult> originals,
            List<Document> ranking,
            String message) {
        Map<Long, RetrievalResult> originalsBySegmentId =
                indexOriginals(originals);
        for (Document document : ranking) {
            Map<String, Object> metadata = document.getMetadata();
            RetrievalResult original = originalsBySegmentId.get(
                    metadata.get("segmentId"));
            assertTrue(original != null, message);
            assertEquals(original.getContent(), document.getText(), message);
            assertEquals(original.getSegmentId(), metadata.get("segmentId"), message);
            assertTrue(metadata.get("score") instanceof Number, message);
            assertEquals(original.getScore(),
                    ((Number) metadata.get("score")).doubleValue(), message);
            assertEquals(original.getSource(), metadata.get("source"), message);
            assertTrue(metadata.get("colbert_score") instanceof Number, message);
            assertTrue(Double.isFinite(
                    ((Number) metadata.get("colbert_score")).doubleValue()), message);
            assertEquals(4, metadata.size(), message);
            assertEquals(Set.of("segmentId", "score", "source", "colbert_score"),
                    metadata.keySet(), message);
        }
    }

    private static List<RetrievalResult> toRetrievalResults(
            List<RetrievalResult> originals,
            List<Document> documents) {
        RagRerankService service = new RagRerankService();
        Map<Long, RetrievalResult> originalsBySegmentId =
                indexOriginals(originals);
        List<RetrievalResult> results = new ArrayList<>(documents.size());
        for (Document document : documents) {
            RetrievalResult result = ReflectionTestUtils.invokeMethod(
                    service, "toRetrievalResult", document, originalsBySegmentId);
            if (result == null) {
                throw new AssertionError("toRetrievalResult returned null");
            }
            results.add(result);
        }
        return results;
    }

    private static void assertOracleDetachedFromDocuments(
            List<Document> documents,
            List<RetrievalResult> oracle) {
        assertEquals(documents.size(), oracle.size());
        for (int index = 0; index < documents.size(); index++) {
            assertNotSame(documents.get(index).getMetadata(),
                    oracle.get(index).getMetadata());
        }
    }

    private static List<RetrievalResult> runCandidate3(
            String query,
            List<RetrievalResult> candidates,
            boolean enabled) {
        DeterministicRerankerProvider deterministic =
                new DeterministicRerankerProvider();
        RagRerankService service = new RagRerankService();
        ReflectionTestUtils.setField(service,
                "deterministicRerankerProvider", deterministic);
        ReflectionTestUtils.setField(service,
                "identifierConsistencyEnabled", enabled);
        List<RetrievalResult> result = ReflectionTestUtils.invokeMethod(
                service,
                "identifierConsistencyRerank",
                RerankRequestContext.builder().query(query).build(),
                candidates,
                QueryIntent.builder().build(),
                10);
        if (result == null) {
            throw new AssertionError("Candidate 3 returned null");
        }
        return result;
    }

    private static List<RetrievalResult> originalsForDocuments(
            List<RetrievalResult> originals,
            List<Document> documents) {
        Map<Long, RetrievalResult> originalsBySegmentId =
                indexOriginals(originals);
        return documents.stream()
                .map(document -> originalsBySegmentId.get(
                        (Long) document.getMetadata().get("segmentId")))
                .toList();
    }

    private static Map<Long, RetrievalResult> indexOriginals(
            List<RetrievalResult> originals) {
        return originals.stream()
                .filter(result -> result.getSegmentId() != null)
                .collect(Collectors.toMap(
                        RetrievalResult::getSegmentId,
                        result -> result,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static List<DocumentSnapshot> documentSnapshots(
            List<Document> documents) {
        return documents.stream()
                .map(document -> new DocumentSnapshot(
                        document.getId(),
                        document.getText(),
                        document.getMetadata(),
                        new ArrayList<>(document.getMetadata().keySet())))
                .toList();
    }

    private static List<ResultSnapshot> resultSnapshots(
            List<RetrievalResult> results) {
        return results.stream()
                .map(ResultSnapshot::from)
                .toList();
    }

    private static CountingColbertScorer scorer() {
        ColbertScorer.ColbertConfig config = new ColbertScorer.ColbertConfig();
        config.setEnabled(true);
        config.setDimensions(64);
        config.setMaxTokensPerDoc(128);
        config.setEmbeddingPlatform("");
        config.setEmbeddingBaseUrl("");
        config.setEmbeddingApiKey("");
        config.setEmbeddingModel("");
        return new CountingColbertScorer(config);
    }

    private static ArmInput armInput(Scenario scenario) {
        List<RetrievalResult> originals = new ArrayList<>(scenario.size());
        List<Document> documents = new ArrayList<>(scenario.size());
        for (int rank = 1; rank <= scenario.size(); rank++) {
            long segmentId = 10_300_000L + rank;
            double score = 10_000.0D - rank;
            String content = "shared content evidence";
            String source = "vector";
            RetrievalResult original = RetrievalResult.builder()
                    .segmentId(segmentId)
                    .qmSegmentId("qm-" + segmentId)
                    .parentSegmentId("parent-" + segmentId)
                    .documentId(10_305_000L + rank)
                    .documentName(scenario.namesByRank().getOrDefault(
                            rank, "ordinary-" + rank + ".txt"))
                    .content(content)
                    .answer("answer-" + rank)
                    .score(score)
                    .source(source)
                    .metadata(new LinkedHashMap<>(Map.of(
                            "business", "original-" + rank)))
                    .build();
            Document document = Document.builder()
                    .id("segment-" + original.getSegmentId())
                    .text(original.getContent())
                    .metadata(new LinkedHashMap<>())
                    .build();
            document.getMetadata().put("segmentId", original.getSegmentId());
            document.getMetadata().put("score", original.getScore());
            document.getMetadata().put("source", original.getSource());
            originals.add(original);
            documents.add(document);
        }
        return new ArmInput(originals, documents);
    }

    private static final class CountingColbertScorer extends ColbertScorer {

        private final int maxTokensPerDoc;
        private int calls;
        private int queryTokenCount;
        private int documentTokenCount;
        private String query;
        private List<String> documentTexts = List.of();

        private CountingColbertScorer(ColbertConfig config) {
            super(config, null);
            this.maxTokensPerDoc = config.getMaxTokensPerDoc();
        }

        @Override
        public List<Document> rerank(
                String query, List<Document> documents, int topK) {
            calls++;
            this.query = query;
            documentTexts = documents.stream().map(Document::getText).toList();
            queryTokenCount = tokenCount(query);
            documentTokenCount = documents.stream()
                    .map(Document::getText)
                    .mapToInt(this::tokenCount)
                    .map(count -> maxTokensPerDoc > 0
                            ? Math.min(count, maxTokensPerDoc) : count)
                    .sum();
            return super.rerank(query, documents, topK);
        }

        @SuppressWarnings("unchecked")
        private int tokenCount(String value) {
            try {
                Method method = ColbertScorer.class.getDeclaredMethod(
                        "tokenize", String.class);
                method.setAccessible(true);
                return ((List<String>) method.invoke(this, value)).size();
            } catch (NoSuchMethodException | IllegalAccessException
                     | InvocationTargetException failure) {
                throw new AssertionError("ColBERT tokenizer unavailable", failure);
            }
        }

        private int calls() {
            return calls;
        }

        private int queryTokenCount() {
            return queryTokenCount;
        }

        private int documentTokenCount() {
            return documentTokenCount;
        }

        private String query() {
            return query;
        }

        private List<String> documentTexts() {
            return documentTexts;
        }
    }

    private record ArmInput(
            List<RetrievalResult> originals,
            List<Document> documents) {
    }

    private record DocumentSnapshot(
            String id,
            String text,
            Map<String, Object> metadata,
            List<String> metadataOrder) {

        private DocumentSnapshot {
            metadata = Collections.unmodifiableMap(
                    new LinkedHashMap<>(metadata));
            metadataOrder = List.copyOf(metadataOrder);
        }
    }

    private record ResultSnapshot(
            Long segmentId,
            String qmSegmentId,
            String parentSegmentId,
            Long documentId,
            String documentName,
            String content,
            String answer,
            double score,
            String source,
            Map<String, Object> metadata,
            List<String> metadataOrder) {

        private ResultSnapshot {
            metadata = Collections.unmodifiableMap(
                    new LinkedHashMap<>(metadata));
            metadataOrder = List.copyOf(metadataOrder);
        }

        private static ResultSnapshot from(RetrievalResult result) {
            return new ResultSnapshot(
                    result.getSegmentId(),
                    result.getQmSegmentId(),
                    result.getParentSegmentId(),
                    result.getDocumentId(),
                    result.getDocumentName(),
                    result.getContent(),
                    result.getAnswer(),
                    result.getScore(),
                    result.getSource(),
                    result.getMetadata(),
                    new ArrayList<>(result.getMetadata().keySet()));
        }
    }

    private record Scenario(
            String name,
            int size,
            String query,
            Map<Integer, String> namesByRank,
            RagCandidate10DiagnosticSupport.Eligibility eligibility,
            boolean candidate3Enabled,
            Integer admittedRank) {
    }
}
