package tech.qiantong.qknow.rag.eval;

import org.springframework.ai.document.Document;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class RagCandidate10DiagnosticSupport {

    static final String ADMISSION_POLICY =
            "bounded-exact-window-tail-admission-v1";
    static final int BUSINESS_LIMIT = 30;
    static final int TAIL_MAX_RANK = 60;
    static final String RANKING_ERROR = "CANDIDATE10_RANKING_INVALID";

    private RagCandidate10DiagnosticSupport() {
    }

    static FullRankingSnapshot snapshotFullRanking(
            List<RetrievalResult> originals,
            List<Document> fullRanking) {
        if (originals == null || fullRanking == null
                || originals.size() != fullRanking.size()) {
            throw rankingInvalid();
        }

        Map<Long, RetrievalResult> originalsBySegmentId = new LinkedHashMap<>();
        for (RetrievalResult original : originals) {
            if (original == null || original.getSegmentId() == null
                    || originalsBySegmentId.putIfAbsent(
                    original.getSegmentId(), original) != null) {
                throw rankingInvalid();
            }
        }

        Set<Long> rankedSegmentIds = new LinkedHashSet<>();
        List<RetrievalSnapshot> rows = new ArrayList<>(fullRanking.size());
        for (int index = 0; index < fullRanking.size(); index++) {
            Document document = fullRanking.get(index);
            if (document == null || document.getMetadata() == null) {
                throw rankingInvalid();
            }
            Map<String, Object> documentMetadata =
                    freezeMetadata(document.getMetadata());
            Object segmentIdValue = documentMetadata.get("segmentId");
            if (!(segmentIdValue instanceof Long segmentId)
                    || !rankedSegmentIds.add(segmentId)) {
                throw rankingInvalid();
            }
            RetrievalResult original = originalsBySegmentId.get(segmentId);
            if (original == null
                    || !Objects.equals(original.getContent(), document.getText())) {
                throw rankingInvalid();
            }
            Object colbertValue = documentMetadata.get("colbert_score");
            if (!(colbertValue instanceof Number colbertNumber)
                    || !Double.isFinite(colbertNumber.doubleValue())) {
                throw rankingInvalid();
            }
            double score = documentMetadata.get("score") instanceof Number number
                    ? number.doubleValue() : original.getScore();
            String source = documentMetadata.get("source") instanceof String value
                    ? value : original.getSource();
            rows.add(new RetrievalSnapshot(
                    index + 1,
                    original.getSegmentId(),
                    original.getQmSegmentId(),
                    original.getParentSegmentId(),
                    original.getDocumentId(),
                    original.getDocumentName(),
                    original.getContent(),
                    original.getAnswer(),
                    score,
                    source,
                    documentMetadata));
        }
        if (!rankedSegmentIds.equals(originalsBySegmentId.keySet())) {
            throw rankingInvalid();
        }
        return new FullRankingSnapshot(rows);
    }

    static AdmissionResult admit(
            FullRankingSnapshot ranking,
            String query,
            Eligibility eligibility) {
        Objects.requireNonNull(ranking, "ranking");
        Objects.requireNonNull(eligibility, "eligibility");
        List<RetrievalSnapshot> rows = ranking.rows();
        int prefixSize = Math.min(BUSINESS_LIMIT, rows.size());
        List<RetrievalSnapshot> baseline = rows.subList(0, prefixSize);
        if (!eligibility.requestActive() || rows.size() <= BUSINESS_LIMIT) {
            return AdmissionResult.unchanged(baseline);
        }

        List<String> identifiers = identifierTerms(query);
        if (identifiers.isEmpty() || identifiers.size() > 2) {
            return AdmissionResult.unchanged(baseline);
        }
        List<Pattern> patterns = identifierPatterns(identifiers);
        if (baseline.stream().anyMatch(row -> matchesAll(row, patterns))
                || matchesAny(rows.get(BUSINESS_LIMIT - 1), patterns)) {
            return AdmissionResult.unchanged(baseline);
        }

        int windowEnd = Math.min(TAIL_MAX_RANK, rows.size());
        for (int index = BUSINESS_LIMIT; index < windowEnd; index++) {
            RetrievalSnapshot selected = rows.get(index);
            if (matchesAll(selected, patterns)) {
                List<RetrievalSnapshot> candidate = new ArrayList<>(
                        rows.subList(0, BUSINESS_LIMIT - 1));
                candidate.add(selected);
                return new AdmissionResult(
                        baseline, candidate, selected.fullRank());
            }
        }
        return AdmissionResult.unchanged(baseline);
    }

    static List<String> identifierTerms(String query) {
        Method method;
        try {
            method = KeywordRetriever.class.getDeclaredMethod(
                    "extractIdentifierTerms", String.class);
            method.setAccessible(true);
        } catch (NoSuchMethodException | SecurityException
                 | InaccessibleObjectException failure) {
            throw new IllegalStateException(
                    "CANDIDATE10_IDENTIFIER_EXTRACTOR_UNAVAILABLE", failure);
        }
        try {
            Object extracted = method.invoke(null, query);
            if (!(extracted instanceof List<?> values)
                    || values.stream().anyMatch(value -> !(value instanceof String))) {
                throw new IllegalStateException(
                        "CANDIDATE10_IDENTIFIER_EXTRACTOR_FAILED");
            }
            return values.stream().map(String.class::cast).toList();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(
                    "CANDIDATE10_IDENTIFIER_EXTRACTOR_UNAVAILABLE", failure);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(
                    "CANDIDATE10_IDENTIFIER_EXTRACTOR_FAILED",
                    failure.getCause());
        }
    }

    static boolean matchesAllIdentifiers(
            String documentName, List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return false;
        }
        return matchesAll(documentName, identifierPatterns(identifiers));
    }

    static boolean matchesAnyIdentifier(
            String documentName, List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return false;
        }
        return matchesAny(documentName, identifierPatterns(identifiers));
    }

    private static List<Pattern> identifierPatterns(List<String> identifiers) {
        return identifiers.stream()
                .map(identifier -> Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(identifier)
                                + "(?![\\p{L}\\p{N}])"))
                .toList();
    }

    private static boolean matchesAll(
            RetrievalSnapshot row, List<Pattern> patterns) {
        return matchesAll(row.documentName(), patterns);
    }

    private static boolean matchesAll(
            String documentName, List<Pattern> patterns) {
        return documentName != null && patterns.stream().allMatch(
                pattern -> pattern.matcher(documentName).find());
    }

    private static boolean matchesAny(
            RetrievalSnapshot row, List<Pattern> patterns) {
        return matchesAny(row.documentName(), patterns);
    }

    private static boolean matchesAny(
            String documentName, List<Pattern> patterns) {
        return documentName != null && patterns.stream().anyMatch(
                pattern -> pattern.matcher(documentName).find());
    }

    private static Map<String, Object> freezeMetadata(
            Map<String, Object> metadata) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Object value = entry.getValue();
            if (entry.getKey() == null || !isImmutableScalar(value)) {
                throw rankingInvalid();
            }
            if ((value instanceof Double doubleValue
                    && !Double.isFinite(doubleValue))
                    || (value instanceof Float floatValue
                    && !Float.isFinite(floatValue))) {
                throw rankingInvalid();
            }
            copy.put(entry.getKey(), value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static boolean isImmutableScalar(Object value) {
        return value == null || value instanceof String
                || value instanceof Boolean || value instanceof Character
                || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double
                || value instanceof BigInteger || value instanceof BigDecimal
                || value instanceof Enum<?>;
    }

    private static IllegalStateException rankingInvalid() {
        return new IllegalStateException(RANKING_ERROR);
    }

    private static List<RetrievalResult> materialize(
            List<RetrievalSnapshot> snapshots) {
        List<RetrievalResult> results = new ArrayList<>(snapshots.size());
        for (RetrievalSnapshot snapshot : snapshots) {
            results.add(snapshot.materialize());
        }
        return results;
    }

    record Eligibility(
            boolean candidate10DiagnosticArm,
            boolean candidate3Enabled,
            boolean deterministicPath) {

        boolean requestActive() {
            return candidate10DiagnosticArm
                    && candidate3Enabled
                    && deterministicPath;
        }
    }

    record FullRankingSnapshot(List<RetrievalSnapshot> rows) {

        FullRankingSnapshot {
            rows = List.copyOf(rows);
        }
    }

    record AdmissionResult(
            List<RetrievalSnapshot> baseline,
            List<RetrievalSnapshot> candidate,
            Integer admittedFullRank) {

        AdmissionResult {
            baseline = List.copyOf(baseline);
            candidate = List.copyOf(candidate);
        }

        static AdmissionResult unchanged(List<RetrievalSnapshot> baseline) {
            return new AdmissionResult(baseline, baseline, null);
        }

        boolean admitted() {
            return admittedFullRank != null;
        }

        List<RetrievalResult> materializeBaseline() {
            return materialize(baseline);
        }

        List<RetrievalResult> materializeCandidate() {
            return materialize(candidate);
        }
    }

    record RetrievalSnapshot(
            int fullRank,
            Long segmentId,
            String qmSegmentId,
            String parentSegmentId,
            Long documentId,
            String documentName,
            String content,
            String answer,
            double score,
            String source,
            Map<String, Object> metadata) {

        RetrievalSnapshot {
            metadata = freezeMetadata(metadata);
        }

        RetrievalResult materialize() {
            return RetrievalResult.builder()
                    .segmentId(segmentId)
                    .qmSegmentId(qmSegmentId)
                    .parentSegmentId(parentSegmentId)
                    .documentId(documentId)
                    .documentName(documentName)
                    .content(content)
                    .answer(answer)
                    .score(score)
                    .source(source)
                    .metadata(new LinkedHashMap<>(metadata))
                    .build();
        }
    }
}
