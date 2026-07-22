package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate10NonDockerContractTest {

    private static final RagCandidate10DiagnosticSupport.Eligibility ACTIVE =
            new RagCandidate10DiagnosticSupport.Eligibility(true, true, true);
    private static final String ONE_ID_QUERY = "document 034";
    private static final String TWO_ID_QUERY = "document 034 document 056";

    @Test
    void allContracts() {
        assertEquals("bounded-exact-window-tail-admission-v1",
                RagCandidate10DiagnosticSupport.ADMISSION_POLICY);
        assertEquals(List.of("034", "056"),
                RagCandidate10DiagnosticSupport.identifierTerms(
                        "document 034 document 056 document 034"));
        assertEquals(List.of("07621"),
                RagCandidate10DiagnosticSupport.identifierTerms(
                        "policy 07621"));
        assertTrue(RagCandidate10DiagnosticSupport.matchesAllIdentifiers(
                "policy-034-056.txt", List.of("034", "056")));
        assertTrue(RagCandidate10DiagnosticSupport.matchesAnyIdentifier(
                "policy-034.txt", List.of("034", "056")));
        assertFalse(RagCandidate10DiagnosticSupport.matchesAllIdentifiers(
                "policy-034.txt", List.of("034", "056")));
        assertFalse(RagCandidate10DiagnosticSupport.matchesAnyIdentifier(
                "policy-1034.txt", List.of("034")));
        assertFalse(RagCandidate10DiagnosticSupport.matchesAnyIdentifier(
                "文034档.txt", List.of("034")));

        assertUnchanged(29, ONE_ID_QUERY, Map.of(29, "policy-034.txt"), ACTIVE);
        assertUnchanged(30, ONE_ID_QUERY, Map.of(30, "policy-034.txt"), ACTIVE);
        assertAdmitted(31, ONE_ID_QUERY, Map.of(31, "policy-034.txt"), 31);
        assertAdmitted(60, TWO_ID_QUERY,
                Map.of(60, "policy-034-056.txt"), 60);
        assertUnchanged(61, ONE_ID_QUERY, Map.of(61, "policy-034.txt"), ACTIVE);
        assertAdmitted(75, ONE_ID_QUERY,
                Map.of(60, "policy-034.txt", 61, "policy-034.txt"), 60);
        assertUnchanged(61, ONE_ID_QUERY,
                Map.of(5, "policy-034.txt", 31, "policy-034.txt"), ACTIVE);
        assertUnchanged(61, TWO_ID_QUERY,
                Map.of(30, "policy-034.txt", 31, "policy-034-056.txt"), ACTIVE);
        assertUnchanged(61, TWO_ID_QUERY,
                Map.of(31, "policy-034.txt"), ACTIVE);
        assertUnchanged(61, "policy 07621",
                Map.of(31, "policy-7621.txt"), ACTIVE);
        assertUnchanged(61, ONE_ID_QUERY,
                Map.of(31, "policy-1034.txt"), ACTIVE);
        assertUnchanged(61, ONE_ID_QUERY,
                Map.of(31, "文034档.txt"), ACTIVE);
        assertUnchanged(61, "knowledge graph",
                Map.of(31, "policy-034.txt"), ACTIVE);
        assertUnchanged(61,
                "document 034 document 056 document 078",
                Map.of(31, "policy-034-056-078.txt"), ACTIVE);

        assertUnchanged(61, ONE_ID_QUERY, Map.of(31, "policy-034.txt"),
                new RagCandidate10DiagnosticSupport.Eligibility(false, true, true));
        assertUnchanged(61, ONE_ID_QUERY, Map.of(31, "policy-034.txt"),
                new RagCandidate10DiagnosticSupport.Eligibility(true, false, true));
        assertUnchanged(61, ONE_ID_QUERY, Map.of(31, "policy-034.txt"),
                new RagCandidate10DiagnosticSupport.Eligibility(true, true, false));

        RagRerankService rerankService = new RagRerankService();
        Map<String, Object> originalMetadata = new LinkedHashMap<>();
        Map<String, Object> nestedBusinessMetadata = new LinkedHashMap<>();
        nestedBusinessMetadata.put(
                "nested", new ArrayList<>(List.of("must-not-leak")));
        originalMetadata.put("business-only", nestedBusinessMetadata);
        RetrievalResult oracleOriginal = RetrievalResult.builder()
                .segmentId(10_290_001L)
                .qmSegmentId("qm-oracle")
                .parentSegmentId("parent-oracle")
                .documentId(10_295_001L)
                .documentName("oracle-document.txt")
                .content("oracle content")
                .answer("oracle answer")
                .score(12.25D)
                .source("vector-original")
                .metadata(originalMetadata)
                .build();

        Document overrideDocument = oracleDocument(oracleOriginal, 91.5D);
        overrideDocument.getMetadata().put("document-only", "preserved");
        overrideDocument.getMetadata().put("score", 87.5D);
        overrideDocument.getMetadata().put("source", "colbert-document");
        RetrievalResult overrideResult = assertConversionMatchesOracle(
                rerankService, oracleOriginal, overrideDocument);
        assertEquals(87.5D, overrideResult.getScore());
        assertEquals("colbert-document", overrideResult.getSource());
        assertFalse(overrideResult.getMetadata().containsKey("business-only"));

        Document missingFallbackDocument = oracleDocument(
                oracleOriginal, 90.5D);
        RetrievalResult missingFallback = assertConversionMatchesOracle(
                rerankService, oracleOriginal, missingFallbackDocument);
        assertEquals(oracleOriginal.getScore(), missingFallback.getScore());
        assertEquals(oracleOriginal.getSource(), missingFallback.getSource());
        assertFalse(missingFallback.getMetadata().containsKey("score"));
        assertFalse(missingFallback.getMetadata().containsKey("source"));

        Document nullFallbackDocument = oracleDocument(
                oracleOriginal, 89.5D);
        nullFallbackDocument.getMetadata().put("score", null);
        nullFallbackDocument.getMetadata().put("source", null);
        RetrievalResult nullFallback = assertConversionMatchesOracle(
                rerankService, oracleOriginal, nullFallbackDocument);
        assertEquals(oracleOriginal.getScore(), nullFallback.getScore());
        assertEquals(oracleOriginal.getSource(), nullFallback.getSource());
        assertTrue(nullFallback.getMetadata().containsKey("score"));
        assertTrue(nullFallback.getMetadata().containsKey("source"));
        assertNull(nullFallback.getMetadata().get("score"));
        assertNull(nullFallback.getMetadata().get("source"));

        Document wrongTypeFallbackDocument = oracleDocument(
                oracleOriginal, 88.5D);
        wrongTypeFallbackDocument.getMetadata().put(
                "score", "wrong-score-type");
        wrongTypeFallbackDocument.getMetadata().put("source", 17L);
        RetrievalResult wrongTypeFallback = assertConversionMatchesOracle(
                rerankService, oracleOriginal, wrongTypeFallbackDocument);
        assertEquals(oracleOriginal.getScore(), wrongTypeFallback.getScore());
        assertEquals(oracleOriginal.getSource(), wrongTypeFallback.getSource());
        assertEquals("wrong-score-type",
                wrongTypeFallback.getMetadata().get("score"));
        assertEquals(17L, wrongTypeFallback.getMetadata().get("source"));

        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.originals().get(0).setSegmentId(null));
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.originals().set(0, null));
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.fullRanking().set(0, null));
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.originals().get(1).setSegmentId(
                        value.originals().get(0).getSegmentId()));
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.fullRanking().get(1).getMetadata().put(
                        "segmentId",
                        value.fullRanking().get(0).getMetadata().get("segmentId")));
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.fullRanking().get(0).getMetadata().put("segmentId", 999_999L));
        for (Object invalidSegmentId : List.<Object>of(
                (byte) 1,
                (short) 1,
                1,
                1.0F,
                1.0D,
                BigInteger.ONE,
                BigDecimal.ONE,
                "1")) {
            assertRankingInvalid(fixture(3, Map.of()), value ->
                    value.fullRanking().get(0).getMetadata().put(
                            "segmentId", invalidSegmentId));
        }
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.fullRanking().remove(0));
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.fullRanking().add(value.fullRanking().get(0)));
        for (Object invalid : List.of(Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertRankingInvalid(fixture(3, Map.of()), value ->
                    value.fullRanking().get(0).getMetadata().put(
                            "colbert_score", invalid));
        }
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.fullRanking().get(0).getMetadata().remove("colbert_score"));
        assertRankingInvalid(fixture(3, Map.of()), value ->
                value.fullRanking().get(0).getMetadata().put(
                        "mutable", new ArrayList<>()));

        Fixture isolated = fixture(31, Map.of(31, "policy-034.txt"));
        List<Map<String, Object>> documentMetadataBefore = isolated.fullRanking()
                .stream().<Map<String, Object>>map(document -> new LinkedHashMap<>(
                        document.getMetadata())).toList();
        RagCandidate10DiagnosticSupport.AdmissionResult admission =
                RagCandidate10DiagnosticSupport.admit(
                        RagCandidate10DiagnosticSupport.snapshotFullRanking(
                                isolated.originals(), isolated.fullRanking()),
                        ONE_ID_QUERY, ACTIVE);
        List<RetrievalResult> baselineOne = admission.materializeBaseline();
        List<RetrievalResult> baselineTwo = admission.materializeBaseline();
        List<RetrievalResult> candidateOne = admission.materializeCandidate();
        List<RetrievalResult> candidateTwo = admission.materializeCandidate();
        assertNotSame(baselineOne.get(0), baselineTwo.get(0));
        assertNotSame(baselineOne.get(0).getMetadata(),
                baselineTwo.get(0).getMetadata());
        assertNotSame(baselineOne.get(0).getMetadata(),
                candidateOne.get(0).getMetadata());
        baselineOne.get(0).setContent("mutated");
        baselineOne.get(0).getMetadata().put("mutated", true);
        candidateOne.get(29).setScore(-1.0D);
        assertEquals("shared content evidence", baselineTwo.get(0).getContent());
        assertFalse(baselineTwo.get(0).getMetadata().containsKey("mutated"));
        assertFalse(baselineTwo.get(0).getMetadata().containsKey("business"));
        assertEquals(10_000.0D - 31.0D, candidateTwo.get(29).getScore());
        assertEquals("shared content evidence",
                isolated.originals().get(0).getContent());
        assertEquals(documentMetadataBefore,
                isolated.fullRanking().stream()
                        .map(Document::getMetadata).toList());
        assertThrows(UnsupportedOperationException.class, () ->
                admission.candidate().get(0).metadata().put("mutated", true));
    }

    private static Document oracleDocument(
            RetrievalResult original, double colbertScore) {
        Document document = Document.builder()
                .id("segment-" + original.getSegmentId())
                .text(original.getContent())
                .metadata(new LinkedHashMap<>())
                .build();
        document.getMetadata().put("segmentId", original.getSegmentId());
        document.getMetadata().put("colbert_score", colbertScore);
        return document;
    }

    private static RetrievalResult assertConversionMatchesOracle(
            RagRerankService service,
            RetrievalResult original,
            Document document) {
        Map<String, Object> metadataBefore =
                new LinkedHashMap<>(document.getMetadata());
        List<String> metadataOrderBefore =
                new ArrayList<>(document.getMetadata().keySet());
        RetrievalResult oracle = ReflectionTestUtils.invokeMethod(
                service,
                "toRetrievalResult",
                document,
                Map.of(original.getSegmentId(), original));
        assertNotNull(oracle);

        RetrievalResult actual = RagCandidate10DiagnosticSupport
                .snapshotFullRanking(List.of(original), List.of(document))
                .rows().get(0).materialize();
        assertRetrievalResultEquals(oracle, actual);
        assertNotSame(document.getMetadata(), oracle.getMetadata());
        assertNotSame(document.getMetadata(), actual.getMetadata());
        assertNotSame(oracle.getMetadata(), actual.getMetadata());
        assertEquals(metadataBefore, document.getMetadata());
        assertEquals(metadataOrderBefore,
                new ArrayList<>(document.getMetadata().keySet()));
        assertEquals(metadataBefore, actual.getMetadata());
        assertEquals(metadataOrderBefore,
                new ArrayList<>(actual.getMetadata().keySet()));
        return actual;
    }

    private static void assertRetrievalResultEquals(
            RetrievalResult expected, RetrievalResult actual) {
        assertEquals(expected.getSegmentId(), actual.getSegmentId());
        assertEquals(expected.getQmSegmentId(), actual.getQmSegmentId());
        assertEquals(expected.getParentSegmentId(), actual.getParentSegmentId());
        assertEquals(expected.getDocumentId(), actual.getDocumentId());
        assertEquals(expected.getDocumentName(), actual.getDocumentName());
        assertEquals(expected.getContent(), actual.getContent());
        assertEquals(expected.getAnswer(), actual.getAnswer());
        assertEquals(expected.getScore(), actual.getScore());
        assertEquals(expected.getSource(), actual.getSource());
        assertEquals(expected.getMetadata(), actual.getMetadata());
        assertEquals(new ArrayList<>(expected.getMetadata().keySet()),
                new ArrayList<>(actual.getMetadata().keySet()));
    }

    private static void assertAdmitted(
            int size, String query, Map<Integer, String> names, int selectedRank) {
        Fixture value = fixture(size, names);
        RagCandidate10DiagnosticSupport.AdmissionResult result =
                RagCandidate10DiagnosticSupport.admit(
                        RagCandidate10DiagnosticSupport.snapshotFullRanking(
                                value.originals(), value.fullRanking()),
                        query, ACTIVE);

        assertTrue(result.admitted());
        assertEquals(selectedRank, result.admittedFullRank());
        assertEquals(30, result.baseline().size());
        assertEquals(30, result.candidate().size());
        assertEquals(result.baseline().subList(0, 29),
                result.candidate().subList(0, 29));
        assertEquals(selectedRank,
                result.candidate().get(29).fullRank());
        RetrievalResult selected = result.materializeCandidate().get(29);
        assertEquals(value.originals().get(selectedRank - 1).getScore(),
                selected.getScore());
        assertEquals(value.originals().get(selectedRank - 1).getContent(),
                selected.getContent());
        assertEquals(value.originals().get(selectedRank - 1).getDocumentName(),
                selected.getDocumentName());
    }

    private static void assertUnchanged(
            int size,
            String query,
            Map<Integer, String> names,
            RagCandidate10DiagnosticSupport.Eligibility eligibility) {
        Fixture value = fixture(size, names);
        RagCandidate10DiagnosticSupport.AdmissionResult result =
                RagCandidate10DiagnosticSupport.admit(
                        RagCandidate10DiagnosticSupport.snapshotFullRanking(
                                value.originals(), value.fullRanking()),
                        query, eligibility);

        assertFalse(result.admitted());
        assertNull(result.admittedFullRank());
        assertEquals(Math.min(30, size), result.candidate().size());
        assertEquals(result.baseline(), result.candidate());
        assertEquals(result.materializeBaseline(), result.materializeCandidate());
    }

    private static void assertRankingInvalid(
            Fixture value, java.util.function.Consumer<Fixture> corruption) {
        corruption.accept(value);
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> RagCandidate10DiagnosticSupport.snapshotFullRanking(
                        value.originals(), value.fullRanking()));
        assertEquals(RagCandidate10DiagnosticSupport.RANKING_ERROR,
                failure.getMessage());
    }

    private static Fixture fixture(
            int size, Map<Integer, String> namesByRank) {
        List<RetrievalResult> originals = new ArrayList<>(size);
        List<Document> fullRanking = new ArrayList<>(size);
        for (int rank = 1; rank <= size; rank++) {
            long segmentId = 10_200_000L + rank;
            double score = 10_000.0D - rank;
            String content = "shared content evidence";
            String source = "vector";
            RetrievalResult original = RetrievalResult.builder()
                    .segmentId(segmentId)
                    .qmSegmentId("qm-" + segmentId)
                    .parentSegmentId("parent-" + segmentId)
                    .documentId(10_205_000L + rank)
                    .documentName(namesByRank.getOrDefault(
                            rank, "ordinary-" + rank + ".txt"))
                    .content(content)
                    .answer("answer-" + rank)
                    .score(score)
                    .source(source)
                    .metadata(new LinkedHashMap<>(Map.of(
                            "business", "original-" + rank)))
                    .build();
            Document document = Document.builder()
                    .id("segment-" + segmentId)
                    .text(content)
                    .metadata(new LinkedHashMap<>())
                    .build();
            document.getMetadata().put("segmentId", segmentId);
            document.getMetadata().put("score", score);
            document.getMetadata().put("source", source);
            document.getMetadata().put("colbert_score", 100.0D - rank);
            originals.add(original);
            fullRanking.add(document);
        }
        return new Fixture(originals, fullRanking);
    }

    private record Fixture(
            List<RetrievalResult> originals,
            List<Document> fullRanking) {
    }
}
