package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

final class RagCandidate10FixtureGenerator {

    static final String GENERATOR = "candidate10-static-fixture-v1";
    static final int GENERATOR_VERSION = 1;
    static final String PRESSURE_GENERATOR =
            "candidate10-rank-window-pressure-v1";
    static final String CAUSAL_SCOPE =
            "post-fusion-content-only-colbert-snapshot";
    static final String SOURCE = GENERATOR;
    static final double SCORE = 0.0d;

    static final int CORE_PER_FAMILY = 4;
    static final int LEXICAL_PER_FAMILY = 12;
    static final int PRESSURE_PER_FAMILY = 40;
    static final int FULL_PRESSURE_PER_FAMILY = 18;
    static final int SEGMENTS_PER_FAMILY =
            CORE_PER_FAMILY + LEXICAL_PER_FAMILY + PRESSURE_PER_FAMILY;
    static final int LONG_TOKEN_COUNT = 160;

    static final Map<String, String> RESOURCE_FILES = Map.of(
            "corpus", "corpus.jsonl",
            "queries", "queries.jsonl",
            "qrels", "qrels.tsv",
            "pressure", "pressure.json");

    private static final int NOISE_RESIDUE = 28;
    private static final int[] IDENTIFIER_RESIDUES = {0, 1, 2, 4, 5, 6};
    private static final List<String> MARKER_ONE = List.of(
            "c10mt", "c10mu", "c10mv", "c10mw", "c10mx", "c10my", "c10mz");
    private static final List<String> MARKER_TWO = List.of(
            "c10maf", "c10mag", "c10mah", "c10mai", "c10maj", "c10mak", "c10ma");
    private static final List<TargetShape> TARGET_SHAPES = List.of(
            TargetShape.NUMERIC_TOKEN,
            TargetShape.DOC_PREFIX,
            TargetShape.ZERO_PADDED,
            TargetShape.HAN_PUNCTUATION);
    private static final List<RoleAllocation> ROLE_ALLOCATIONS = List.of(
            new RoleAllocation(FamilyRole.TARGET, 8),
            new RoleAllocation(FamilyRole.BASELINE_PRESENT, 2),
            new RoleAllocation(FamilyRole.NO_ID, 1),
            new RoleAllocation(FamilyRole.KEYWORD_LURE, 1),
            new RoleAllocation(FamilyRole.SEMANTIC_NEAR_LURE, 1),
            new RoleAllocation(FamilyRole.BOUNDARY, 2),
            new RoleAllocation(FamilyRole.ZERO_PADDING, 1),
            new RoleAllocation(FamilyRole.MULTI_ID, 1),
            new RoleAllocation(FamilyRole.RELEVANT_NONEXACT, 1),
            new RoleAllocation(FamilyRole.LONG_TOKEN, 1),
            new RoleAllocation(FamilyRole.NON_CORROBORATED_EXACT, 1));

    private RagCandidate10FixtureGenerator() {
    }

    static GeneratedSplit selection() {
        return generate(Split.SELECTION);
    }

    static GeneratedSplit holdout() {
        return generate(Split.HOLDOUT);
    }

    static GeneratedSplit generate(Split split) {
        RankingFixture ranking = rankingView(split);
        QrelFixture qrels = qrels(split);

        Map<String, Map<String, Integer>> qrelMap = qrels.qrels();
        RagEvaluationDataset dataset = new RagEvaluationDataset(
                ranking.dataset().corpusById(), ranking.dataset().queries(), qrelMap);
        RagEvaluationDatasetLoader.validate(dataset);

        Map<String, Resource> resources = new LinkedHashMap<>(ranking.resources());
        resources.put("qrels", qrels.resource());
        resources = orderedResources(resources);

        Map<String, Integer> counts = counts(split, qrels.pairCount());
        Map<String, Object> structure = structure(
                split, ranking.families(), ranking.fixtureSpecHash(), qrels.pairCount());
        byte[] preimage = datasetHashPreimage(split, resources, counts, structure);
        String datasetHash = sha256(preimage);

        validateGenerated(split, dataset, ranking.families(), resources,
                counts, qrels.pairCount(), ranking.fixtureSpecHash());
        return new GeneratedSplit(
                split,
                split.seed,
                dataset,
                ranking.families(),
                resources,
                counts,
                structure,
                ranking.fixtureSpecHash(),
                preimage,
                datasetHash);
    }

    static RankingFixture rankingView(Split split) {
        Objects.requireNonNull(split, "split");
        List<FamilySpec> families = familySpecs(split);
        Map<String, Object> fixtureSpec = fixtureSpec(split);
        String fixtureSpecHash = sha256(canonicalJsonBytes(fixtureSpec));

        List<SegmentRow> segments = segmentRows(split, families);
        List<QueryRow> queries = queryRows(split, families);
        RagEvaluationDataset dataset = new RagEvaluationDataset(
                corpusMap(segments), queryCases(queries), Map.of());

        Map<String, Resource> resources = new LinkedHashMap<>();
        resources.put("corpus", resource("corpus", jsonLines(
                segments.stream().map(SegmentRow::json).toList())));
        resources.put("queries", resource("queries", jsonLines(
                queries.stream().map(QueryRow::json).toList())));
        resources.put("pressure", resource("pressure", canonicalJsonDocumentBytes(
                pressureDocument(split, families, fixtureSpec, fixtureSpecHash))));
        return new RankingFixture(
                split, split.seed, dataset, families,
                Map.copyOf(resources), fixtureSpec, fixtureSpecHash);
    }

    static QrelFixture qrels(Split split) {
        Objects.requireNonNull(split, "split");
        List<FamilySpec> families = familySpecs(split);
        List<QrelRow> rows = qrelRows(split, families);
        StringBuilder value = new StringBuilder("queryId\tsegmentId\tgrade\n");
        for (QrelRow row : rows) {
            value.append(row.queryId()).append('\t')
                    .append(row.segmentId()).append("\t1\n");
        }
        Map<String, Map<String, Integer>> qrels = new LinkedHashMap<>();
        for (QrelRow row : rows) {
            qrels.computeIfAbsent(row.queryId(), ignored -> new LinkedHashMap<>())
                    .put(row.segmentId(), 1);
        }
        Map<String, Map<String, Integer>> immutable = new LinkedHashMap<>();
        qrels.forEach((queryId, grades) ->
                immutable.put(queryId, Map.copyOf(grades)));
        return new QrelFixture(
                split,
                Map.copyOf(immutable),
                resource("qrels", value.toString().getBytes(StandardCharsets.UTF_8)),
                rows.size());
    }

    static byte[] canonicalJsonBytes(Object value) {
        return JSON.toJSONString(
                        canonicalize(value),
                        JSONWriter.Feature.WriteNulls)
                .getBytes(StandardCharsets.UTF_8);
    }

    static String sha256(byte[] bytes) {
        return ShadowContractSupport.sha256(bytes);
    }

    private static List<FamilySpec> familySpecs(Split split) {
        List<Integer> roleOrder = new ArrayList<>();
        for (int ordinal = 1; ordinal <= split.familyCount; ordinal++) {
            roleOrder.add(ordinal);
        }
        roleOrder.sort(Comparator
                .comparing((Integer ordinal) -> roleHash(split, ordinal))
                .thenComparingInt(Integer::intValue));

        Map<Integer, Integer> roleSlots = new LinkedHashMap<>();
        Map<Integer, FamilyRole> roles = new LinkedHashMap<>();
        for (int index = 0; index < roleOrder.size(); index++) {
            int ordinal = roleOrder.get(index);
            int slot = index + 1;
            roleSlots.put(ordinal, slot);
            roles.put(ordinal, roleForSlot(split, slot));
        }

        List<Integer> targetOrder = roleOrder.stream()
                .filter(ordinal -> roles.get(ordinal) == FamilyRole.TARGET)
                .sorted(Comparator
                        .comparing((Integer ordinal) -> shapeHash(split, ordinal))
                        .thenComparingInt(Integer::intValue))
                .toList();
        Map<Integer, Integer> shapeSlots = new LinkedHashMap<>();
        Map<Integer, TargetShape> shapes = new LinkedHashMap<>();
        for (int index = 0; index < targetOrder.size(); index++) {
            int ordinal = targetOrder.get(index);
            shapeSlots.put(ordinal, index + 1);
            shapes.put(ordinal, TARGET_SHAPES.get(index % TARGET_SHAPES.size()));
        }

        List<FamilySpec> families = new ArrayList<>(split.familyCount);
        for (int ordinal = 1; ordinal <= split.familyCount; ordinal++) {
            FamilyRole role = roles.get(ordinal);
            TargetShape shape = shapes.get(ordinal);
            boolean paddedIdentifier = shape == TargetShape.ZERO_PADDED
                    || role == FamilyRole.ZERO_PADDING;
            int group = (ordinal - 1) / MARKER_ONE.size();
            int markerIndex = (ordinal - 1) % MARKER_ONE.size();
            int residue = IDENTIFIER_RESIDUES[group];
            long bucket = split.identifierBase + 10_000L * (ordinal - 1L);
            Identifier identifier1 = identifier(
                    bucket, bucket + 4_999L, residue, paddedIdentifier);
            Identifier identifier2 = identifier(
                    bucket + 5_000L, bucket + 9_999L, residue, false);
            families.add(new FamilySpec(
                    ordinal,
                    familyId(split, ordinal),
                    roleSlots.get(ordinal),
                    role,
                    shapeSlots.get(ordinal),
                    shape,
                    identifier1.raw(),
                    identifier1.visible(),
                    identifier2.raw(),
                    identifier2.visible(),
                    MARKER_ONE.get(markerIndex),
                    MARKER_TWO.get((group + markerIndex) % MARKER_TWO.size())));
        }
        return List.copyOf(families);
    }

    private static String roleHash(Split split, int ordinal) {
        return sha256(("candidate10-role-v1\n" + split.seed + "\n"
                + familyId(split, ordinal)).getBytes(StandardCharsets.UTF_8));
    }

    private static String shapeHash(Split split, int ordinal) {
        return sha256(("candidate10-shape-v1\n" + split.seed + "\n"
                + familyId(split, ordinal)).getBytes(StandardCharsets.UTF_8));
    }

    private static FamilyRole roleForSlot(Split split, int slot) {
        int cursor = 0;
        for (RoleAllocation allocation : ROLE_ALLOCATIONS) {
            cursor += allocation.selectionCount() * split.scale;
            if (slot <= cursor) {
                return allocation.role();
            }
        }
        throw new IllegalArgumentException("CANDIDATE10_ROLE_SLOT_INVALID");
    }

    private static Identifier identifier(
            long start, long end, int residue, boolean padded) {
        for (long value = start; value <= end; value++) {
            String raw = Long.toString(value);
            String visible = padded ? "0" + raw : raw;
            if ((visible.hashCode() & 31) == residue) {
                return new Identifier(raw, visible);
            }
        }
        throw new IllegalStateException("CANDIDATE10_IDENTIFIER_RESIDUE_INVALID");
    }

    private static List<SegmentRow> segmentRows(
            Split split, List<FamilySpec> families) {
        List<SegmentRow> rows = new ArrayList<>(split.segmentCount());
        int coreTotal = split.familyCount * CORE_PER_FAMILY;
        int lexicalTotal = split.familyCount * LEXICAL_PER_FAMILY;
        for (FamilySpec family : families) {
            for (int index = 1; index <= CORE_PER_FAMILY; index++) {
                int ordinal = CORE_PER_FAMILY * (family.ordinal() - 1) + index;
                rows.add(segmentRow(split, family, SegmentKind.CORE, index, ordinal));
            }
            for (int index = 1; index <= LEXICAL_PER_FAMILY; index++) {
                int ordinal = coreTotal
                        + LEXICAL_PER_FAMILY * (family.ordinal() - 1) + index;
                rows.add(segmentRow(split, family, SegmentKind.LEXICAL, index, ordinal));
            }
            for (int index = 1; index <= PRESSURE_PER_FAMILY; index++) {
                int ordinal = coreTotal + lexicalTotal
                        + PRESSURE_PER_FAMILY * (family.ordinal() - 1) + index;
                rows.add(segmentRow(split, family, SegmentKind.PRESSURE, index, ordinal));
            }
        }
        rows.sort(Comparator.comparingInt(SegmentRow::ordinal));
        return List.copyOf(rows);
    }

    private static SegmentRow segmentRow(
            Split split,
            FamilySpec family,
            SegmentKind kind,
            int familyIndex,
            int ordinal) {
        String content = content(split, family, kind, familyIndex, ordinal);
        String documentName = documentName(split, family, kind, familyIndex);
        long segmentId = split.segmentBase + ordinal;
        long documentId = split.documentBase + ordinal - 1L;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("candidate10Role", roleName(family, kind));
        metadata.put("documentName", documentName);
        metadata.put("familyId", family.familyId());
        metadata.put("identifierShape", family.shapeName());
        metadata.put("kbId", split.kbId);
        metadata.put("ordinal", ordinal);
        metadata.put("score", SCORE);
        metadata.put("source", SOURCE);
        RagEvaluationDataset.CorpusSegment segment =
                new RagEvaluationDataset.CorpusSegment(
                        Long.toString(segmentId),
                        Long.toString(documentId),
                        content,
                        null,
                        metadata);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("segmentId", segment.segmentId());
        json.put("documentId", segment.documentId());
        json.put("content", segment.content());
        json.put("parentSegmentId", null);
        json.put("metadata", metadata);
        return new SegmentRow(ordinal, family.familyId(), kind, familyIndex, segment, json);
    }

    private static String roleName(FamilySpec family, SegmentKind kind) {
        return switch (kind) {
            case CORE -> family.role().wireName + "-core";
            case LEXICAL -> "lexical-distractor";
            case PRESSURE -> "pressure-distractor";
        };
    }

    private static String content(
            Split split,
            FamilySpec family,
            SegmentKind kind,
            int familyIndex,
            int ordinal) {
        String noise = noiseToken(split, ordinal, 0);
        if (kind == SegmentKind.LEXICAL) {
            return String.join(" ", fullTokens(family, noise));
        }
        if (kind == SegmentKind.PRESSURE) {
            return familyIndex <= FULL_PRESSURE_PER_FAMILY
                    ? String.join(" ", fullTokens(family, noise))
                    : noise;
        }
        if (familyIndex != 1 && !(family.role() == FamilyRole.TARGET
                && familyIndex == 2)
                && !(family.role() == FamilyRole.MULTI_ID && familyIndex == 2)) {
            return noise;
        }
        return switch (family.role()) {
            case TARGET -> familyIndex == 1
                    ? String.join(" ", mediumTokens(family, noise))
                    : String.join(" ", fullTokens(family, noise));
            case BASELINE_PRESENT, NO_ID, RELEVANT_NONEXACT ->
                    String.join(" ", fullTokens(family, noise));
            case KEYWORD_LURE, NON_CORROBORATED_EXACT -> noise;
            case SEMANTIC_NEAR_LURE ->
                    String.join(" ", nearTokens(family, noise));
            case BOUNDARY, ZERO_PADDING, MULTI_ID -> familyIndex == 1
                    ? String.join(" ", mediumTokens(family, noise))
                    : noise;
            case LONG_TOKEN -> longContent(split, family, ordinal);
        };
    }

    private static List<String> fullTokens(FamilySpec family, String noise) {
        List<String> tokens = new ArrayList<>();
        tokens.add("document");
        if (family.role().identifierCount > 0) {
            tokens.add(family.identifier1());
        }
        if (family.role().identifierCount == 2) {
            tokens.add(family.identifier2());
        }
        tokens.add(family.marker1());
        tokens.add(family.marker2());
        tokens.add("文");
        tokens.add("档");
        tokens.add(noise);
        return stableDistinct(tokens);
    }

    private static List<String> mediumTokens(FamilySpec family, String noise) {
        List<String> tokens = new ArrayList<>(fullTokens(family, noise));
        tokens.remove(family.marker2());
        return List.copyOf(tokens);
    }

    private static List<String> nearTokens(FamilySpec family, String noise) {
        return stableDistinct(List.of(
                "document", family.marker1(), "文", "档", noise));
    }

    private static List<String> stableDistinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static String longContent(
            Split split, FamilySpec family, int ordinal) {
        List<String> tokens = new ArrayList<>(LONG_TOKEN_COUNT);
        for (int position = 1; position <= 123; position++) {
            tokens.add(noiseToken(split, ordinal, position));
        }
        tokens.add("document");
        tokens.add(family.identifier1());
        tokens.add(family.marker1());
        tokens.add("文");
        tokens.add("档");
        tokens.add(family.marker2());
        for (int position = 130; position <= LONG_TOKEN_COUNT; position++) {
            tokens.add(noiseToken(split, ordinal, position));
        }
        if (tokens.size() != LONG_TOKEN_COUNT
                || !family.marker2().equals(tokens.get(128))) {
            throw new IllegalStateException("CANDIDATE10_LONG_TOKEN_INVALID");
        }
        return String.join(" ", tokens);
    }

    private static String noiseToken(Split split, int ordinal, int position) {
        String prefix = "c10n" + split.shortName.toLowerCase(Locale.ROOT)
                + ordinal + (position == 0 ? "" : "p" + position) + "x";
        for (int value = 0; ; value++) {
            String token = prefix + Integer.toString(value, 36);
            if ((token.hashCode() & 31) == NOISE_RESIDUE) {
                return token;
            }
        }
    }

    private static String documentName(
            Split split,
            FamilySpec family,
            SegmentKind kind,
            int familyIndex) {
        if (kind != SegmentKind.CORE) {
            return plainName(split, family, kind, familyIndex);
        }
        if (familyIndex == 1) {
            return switch (family.role()) {
                case TARGET -> targetDocumentName(family);
                case BASELINE_PRESENT, KEYWORD_LURE, SEMANTIC_NEAR_LURE,
                        LONG_TOKEN, NON_CORROBORATED_EXACT ->
                        "DOC-" + family.identifier1() + "-EVIDENCE.txt";
                case BOUNDARY ->
                        "DOC-X" + family.identifier1() + "Y-EVIDENCE.txt";
                case ZERO_PADDING ->
                        "POLICY-" + family.identifier1Raw() + "-EVIDENCE.txt";
                case MULTI_ID -> "DOC-" + family.identifier1() + "-"
                        + family.identifier2() + "-EVIDENCE.txt";
                case NO_ID, RELEVANT_NONEXACT ->
                        plainName(split, family, kind, familyIndex);
            };
        }
        if (family.role() == FamilyRole.MULTI_ID && familyIndex == 2) {
            return "DOC-" + family.identifier1() + "-PARTIAL.txt";
        }
        return plainName(split, family, kind, familyIndex);
    }

    private static String targetDocumentName(FamilySpec family) {
        return switch (Objects.requireNonNull(family.targetShape(), "targetShape")) {
            case NUMERIC_TOKEN -> family.identifier1() + ".txt";
            case DOC_PREFIX -> "DOC-" + family.identifier1() + "-EVIDENCE.txt";
            case ZERO_PADDED ->
                    "POLICY-" + family.identifier1() + "-EVIDENCE.txt";
            case HAN_PUNCTUATION -> "政策（" + family.identifier1() + "）.txt";
        };
    }

    private static String plainName(
            Split split,
            FamilySpec family,
            SegmentKind kind,
            int familyIndex) {
        return String.format(Locale.ROOT, "C10-%s-F%03d-%s%02d.txt",
                split.shortName,
                family.ordinal(),
                kind.code,
                familyIndex);
    }

    private static List<QueryRow> queryRows(
            Split split, List<FamilySpec> families) {
        List<QueryRow> rows = new ArrayList<>(split.queryCount());
        for (FamilySpec family : families) {
            rows.add(queryRow(split, family, "zh"));
            rows.add(queryRow(split, family, "en"));
        }
        return List.copyOf(rows);
    }

    private static QueryRow queryRow(
            Split split, FamilySpec family, String language) {
        String query = query(family, language);
        String id = family.familyId() + "-" + language;
        boolean answerable = !family.role().relevantCoreIndexes.isEmpty();
        List<String> strata = List.of(
                "candidate10",
                family.role().wireName,
                family.shapeName());
        String referenceAnswer = answerable
                ? family.marker1() + " " + family.marker2()
                : null;
        List<String> referenceClaims = answerable
                ? List.of(family.marker1(), family.marker2())
                : List.of();
        RagEvaluationDataset.QueryCase queryCase =
                new RagEvaluationDataset.QueryCase(
                        id,
                        family.familyId(),
                        query,
                        query,
                        List.of(),
                        language,
                        new LinkedHashSet<>(strata),
                        split.externalName,
                        answerable,
                        referenceAnswer,
                        referenceClaims);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("familyId", family.familyId());
        json.put("query", query);
        json.put("retrievalQuery", query);
        json.put("history", List.of());
        json.put("language", language);
        json.put("strata", strata);
        json.put("split", split.externalName);
        json.put("answerable", answerable);
        json.put("referenceAnswer", referenceAnswer);
        json.put("referenceClaims", referenceClaims);
        return new QueryRow(queryCase, json);
    }

    private static String query(FamilySpec family, String language) {
        List<String> values = new ArrayList<>();
        if ("zh".equals(language)) {
            values.add("文档");
            if (family.role().identifierCount > 0) {
                values.add(family.identifier1());
            }
            if (family.role().identifierCount == 2) {
                values.add("文档");
                values.add(family.identifier2());
            }
        } else {
            values.add("document");
            if (family.role().identifierCount > 0) {
                values.add(family.identifier1());
            }
            if (family.role().identifierCount == 2) {
                values.add("document");
                values.add(family.identifier2());
            }
        }
        values.add(family.marker1());
        values.add(family.marker2());
        return String.join(" ", values);
    }

    private static List<QrelRow> qrelRows(
            Split split, List<FamilySpec> families) {
        List<QrelRow> rows = new ArrayList<>(split.qrelPairCount);
        for (FamilySpec family : families) {
            for (String language : List.of("zh", "en")) {
                String queryId = family.familyId() + "-" + language;
                for (int coreIndex : family.role().relevantCoreIndexes.stream()
                        .sorted().toList()) {
                    int ordinal = CORE_PER_FAMILY * (family.ordinal() - 1) + coreIndex;
                    rows.add(new QrelRow(
                            queryId,
                            Long.toString(split.segmentBase + ordinal),
                            ordinal));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static Map<String, RagEvaluationDataset.CorpusSegment> corpusMap(
            List<SegmentRow> rows) {
        Map<String, RagEvaluationDataset.CorpusSegment> corpus = new LinkedHashMap<>();
        for (SegmentRow row : rows) {
            if (corpus.putIfAbsent(row.segment().segmentId(), row.segment()) != null) {
                throw new IllegalStateException("CANDIDATE10_DUPLICATE_SEGMENT");
            }
        }
        return Map.copyOf(corpus);
    }

    private static List<RagEvaluationDataset.QueryCase> queryCases(
            List<QueryRow> rows) {
        return rows.stream().map(QueryRow::query).toList();
    }

    private static Map<String, Object> pressureDocument(
            Split split,
            List<FamilySpec> families,
            Map<String, Object> fixtureSpec,
            String fixtureSpecHash) {
        List<Map<String, Object>> familyPlans = new ArrayList<>(families.size());
        for (FamilySpec family : families) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("familyId", family.familyId());
            value.put("familyOrdinal", family.ordinal());
            value.put("identifier1", family.identifier1());
            value.put("identifier1Raw", family.identifier1Raw());
            value.put("identifier2", family.identifier2());
            value.put("identifier2Raw", family.identifier2Raw());
            value.put("identifierShape", family.shapeName());
            value.put("marker1", family.marker1());
            value.put("marker2", family.marker2());
            value.put("role", family.role().wireName);
            value.put("roleSlot", family.roleSlot());
            value.put("targetShapeSlot", family.targetShapeSlot());
            familyPlans.add(value);
        }
        Map<String, Object> pressure = new LinkedHashMap<>();
        pressure.put("familyPlans", familyPlans);
        pressure.put("fixtureSpec", fixtureSpec);
        pressure.put("fixtureSpecHash", fixtureSpecHash);
        pressure.put("generator", GENERATOR);
        pressure.put("pressureGenerator", PRESSURE_GENERATOR);
        pressure.put("seed", split.seed);
        pressure.put("split", split.externalName);
        pressure.put("version", GENERATOR_VERSION);
        return pressure;
    }

    private static Map<String, Object> fixtureSpec(Split split) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("canonicalization", Map.of(
                "encoding", "UTF-8",
                "jsonKeyOrder", "recursive-lexicographic-v1",
                "lineEnding", "LF",
                "terminalLfCount", 1,
                "timestamps", "FORBIDDEN"));
        value.put("contentTemplates", Map.of(
                "FULL", "stableDistinct(document,I1[,I2],M1,M2,文,档,N)",
                "MEDIUM", "FULL-M2",
                "NEAR", "stableDistinct(document,M1,文,档,N)",
                "LOW", "N",
                "lexical1to12", "FULL",
                "pressure1to18", "FULL",
                "pressure19to40", "LOW"));
        value.put("documentNameTemplates", Map.ofEntries(
                Map.entry("boundaryNegative", "DOC-X<I1>Y-EVIDENCE.txt"),
                Map.entry("docPrefix", "DOC-<I1>-EVIDENCE.txt"),
                Map.entry("exact", "DOC-<I1>-EVIDENCE.txt"),
                Map.entry("hanPunctuation", "政策（<I1>）.txt"),
                Map.entry("multiFull", "DOC-<I1>-<I2>-EVIDENCE.txt"),
                Map.entry("multiPartial", "DOC-<I1>-PARTIAL.txt"),
                Map.entry("numericToken", "<I1>.txt"),
                Map.entry("plain", "C10-<S|H>-F%03d-<C|L|P>%02d.txt"),
                Map.entry("zeroPaddingNegative", "POLICY-<raw-I1>-EVIDENCE.txt"),
                Map.entry("zeroPadded", "POLICY-<0raw-I1>-EVIDENCE.txt")));
        value.put("familyOrder", Map.of(
                "domain", "candidate10-role-v1\\n<seed>\\n<familyId>",
                "sort", "unsigned-sha256-hex-then-family-ordinal"));
        value.put("generator", GENERATOR);
        value.put("idMapping", idMapping(split));
        value.put("identifier", Map.ofEntries(
                Map.entry("bucket", "identifierBase+10000*(familyOrdinal-1)"),
                Map.entry("identifier1Range", "bucket..bucket+4999"),
                Map.entry("identifier2Range", "bucket+5000..bucket+9999"),
                Map.entry("residue", "String.hashCode()&31=A[(familyOrdinal-1)/7]"),
                Map.entry("residueTable", Arrays.stream(IDENTIFIER_RESIDUES).boxed().toList()),
                Map.entry("selectionIdentifierBase", Split.SELECTION.identifierBase),
                Map.entry("holdoutIdentifierBase", Split.HOLDOUT.identifierBase),
                Map.entry("zeroPadding", "one-leading-zero-before-residue-check")));
        value.put("longToken", Map.ofEntries(
                Map.entry("tokenCount", LONG_TOKEN_COUNT),
                Map.entry("noisePositions", List.of("1..123", "130..160")),
                Map.entry("position124", "document"),
                Map.entry("position125", "I1"),
                Map.entry("position126", "M1"),
                Map.entry("position127", "文"),
                Map.entry("position128", "档"),
                Map.entry("position129", "M2")));
        value.put("marker", Map.of(
                "M1", MARKER_ONE,
                "M2", MARKER_TWO,
                "M1Index", "(familyOrdinal-1)%7",
                "M2Index", "((familyOrdinal-1)/7+(familyOrdinal-1)%7)%7"));
        value.put("noise", Map.of(
                "baseTemplate", "c10n<splitShortLower><ordinal>x<base36(k)>",
                "longTemplate", "c10n<splitShortLower><ordinal>p<position>x<base36(k)>",
                "residue", NOISE_RESIDUE,
                "selection", "first-k-from-zero"));
        value.put("ordinalMapping", ordinalMapping(split));
        value.put("pressureGenerator", PRESSURE_GENERATOR);
        value.put("qrelPolicy", Map.of(
                "grade", 1,
                "languageOrder", List.of("zh", "en"),
                "queryOrder", "family-ordinal-then-zh-en",
                "segmentOrder", "numeric-ordinal",
                "roles", qrelRoleCoreIndexes()));
        value.put("queryTemplates", Map.of(
                "en0", "document M1 M2",
                "en1", "document I1 M1 M2",
                "en2", "document I1 document I2 M1 M2",
                "zh0", "文档 M1 M2",
                "zh1", "文档 I1 M1 M2",
                "zh2", "文档 I1 文档 I2 M1 M2"));
        value.put("roleSlots", roleSlots(split));
        value.put("score", SCORE);
        value.put("seed", split.seed);
        value.put("shapeOrder", TARGET_SHAPES.stream()
                .map(shape -> shape.wireName).toList());
        value.put("shapeSort", Map.of(
                "domain", "candidate10-shape-v1\\n<seed>\\n<familyId>",
                "sort", "unsigned-sha256-hex-then-family-ordinal"));
        value.put("source", SOURCE);
        value.put("split", split.externalName);
        value.put("version", GENERATOR_VERSION);
        return immutableMap(value);
    }

    private static Map<String, Object> idMapping(Split split) {
        return Map.ofEntries(
                Map.entry("crossFamilySharing", false),
                Map.entry("documentBase", split.documentBase),
                Map.entry("documentIdFormula", "documentBase+ordinal-1"),
                Map.entry("documentIdMax", split.documentIdMax()),
                Map.entry("documentIdMin", split.documentBase),
                Map.entry("kbId", split.kbId),
                Map.entry("parentSegmentId", "NULL"),
                Map.entry("segmentBase", split.segmentBase),
                Map.entry("segmentIdFormula", "segmentBase+ordinal"),
                Map.entry("segmentIdMax", split.segmentIdMax()),
                Map.entry("segmentIdMin", split.segmentBase + 1L));
    }

    private static Map<String, Object> ordinalMapping(Split split) {
        int coreEnd = split.familyCount * CORE_PER_FAMILY;
        int lexicalEnd = coreEnd + split.familyCount * LEXICAL_PER_FAMILY;
        return Map.of(
                "core", "1.." + coreEnd,
                "coreFormula", "4*(familyOrdinal-1)+coreIndex",
                "lexical", (coreEnd + 1) + ".." + lexicalEnd,
                "lexicalFormula", "coreTotal+12*(familyOrdinal-1)+lexicalIndex",
                "pressure", (lexicalEnd + 1) + ".." + split.segmentCount(),
                "pressureFormula", "coreTotal+lexicalTotal+40*(familyOrdinal-1)+pressureIndex");
    }

    private static Map<String, Object> roleSlots(Split split) {
        Map<String, Object> slots = new LinkedHashMap<>();
        int cursor = 1;
        for (RoleAllocation allocation : ROLE_ALLOCATIONS) {
            int count = allocation.selectionCount() * split.scale;
            slots.put(allocation.role().wireName,
                    count == 1 ? Integer.toString(cursor)
                            : cursor + ".." + (cursor + count - 1));
            cursor += count;
        }
        return immutableMap(slots);
    }

    private static Map<String, Object> qrelRoleCoreIndexes() {
        Map<String, Object> value = new LinkedHashMap<>();
        for (FamilyRole role : FamilyRole.values()) {
            value.put(role.wireName, role.relevantCoreIndexes.stream()
                    .sorted().toList());
        }
        return immutableMap(value);
    }

    private static Map<String, Integer> counts(Split split, int qrelPairCount) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("coreCount", split.familyCount * CORE_PER_FAMILY);
        counts.put("documentCount", split.segmentCount());
        counts.put("familyCount", split.familyCount);
        counts.put("lexicalCount", split.familyCount * LEXICAL_PER_FAMILY);
        counts.put("pressureCount", split.familyCount * PRESSURE_PER_FAMILY);
        counts.put("qrelPairCount", qrelPairCount);
        counts.put("queryCount", split.queryCount());
        counts.put("segmentCount", split.segmentCount());
        return Map.copyOf(counts);
    }

    private static Map<String, Object> structure(
            Split split,
            List<FamilySpec> families,
            String fixtureSpecHash,
            int qrelPairCount) {
        Map<String, Integer> roleCounts = new TreeMap<>();
        Map<String, Integer> shapeCounts = new TreeMap<>();
        for (FamilySpec family : families) {
            roleCounts.merge(family.role().wireName, 1, Integer::sum);
            if (family.targetShape() != null) {
                shapeCounts.merge(family.targetShape().wireName, 1, Integer::sum);
            }
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("causalScope", CAUSAL_SCOPE);
        value.put("crossFamilySharing", false);
        value.put("fixtureSpecHash", fixtureSpecHash);
        value.put("idMapping", idMapping(split));
        value.put("ordinalMapping", ordinalMapping(split));
        value.put("qrelGrade", 1);
        value.put("qrelPairCount", qrelPairCount);
        value.put("queryOrder", "family-ordinal-then-zh-en");
        value.put("roleCounts", roleCounts);
        value.put("roleSlots", roleSlots(split));
        value.put("segmentOwnsDocument", true);
        value.put("shapeCounts", shapeCounts);
        value.put("shapeOrder", TARGET_SHAPES.stream()
                .map(shape -> shape.wireName).toList());
        return immutableMap(value);
    }

    private static byte[] datasetHashPreimage(
            Split split,
            Map<String, Resource> resources,
            Map<String, Integer> counts,
            Map<String, Object> structure) {
        Map<String, Object> resourceEvidence = new TreeMap<>();
        resources.forEach((name, resource) -> resourceEvidence.put(name, Map.of(
                "file", resource.fileName(),
                "sha256", resource.sha256())));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("counts", counts);
        evidence.put("generator", GENERATOR);
        evidence.put("resources", resourceEvidence);
        evidence.put("seed", split.seed);
        evidence.put("structure", structure);
        evidence.put("version", GENERATOR_VERSION);
        return canonicalJsonBytes(evidence);
    }

    private static Resource resource(String logicalName, byte[] bytes) {
        return new Resource(
                Objects.requireNonNull(RESOURCE_FILES.get(logicalName), "resource file"),
                bytes,
                sha256(bytes));
    }

    private static Map<String, Resource> orderedResources(
            Map<String, Resource> resources) {
        Map<String, Resource> ordered = new LinkedHashMap<>();
        for (String name : List.of("corpus", "queries", "qrels", "pressure")) {
            Resource resource = resources.get(name);
            if (resource == null) {
                throw new IllegalStateException("CANDIDATE10_RESOURCE_MISSING");
            }
            ordered.put(name, resource);
        }
        return Map.copyOf(ordered);
    }

    private static byte[] jsonLines(List<Map<String, Object>> rows) {
        StringBuilder value = new StringBuilder();
        for (Map<String, Object> row : rows) {
            value.append(new String(canonicalJsonBytes(row), StandardCharsets.UTF_8))
                    .append('\n');
        }
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] canonicalJsonDocumentBytes(Object value) {
        byte[] json = canonicalJsonBytes(value);
        byte[] result = Arrays.copyOf(json, json.length + 1);
        result[result.length - 1] = '\n';
        return result;
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE10_CANONICAL_JSON_KEY_INVALID");
                }
                sorted.put(stringKey, canonicalize(child));
            });
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> canonical = new ArrayList<>(list.size());
            list.forEach(child -> canonical.add(canonicalize(child)));
            return canonical;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> immutableMap(Map<String, ?> value) {
        return (Map<String, Object>) immutable(value);
    }

    private static Object immutable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException(
                            "CANDIDATE10_CANONICAL_JSON_KEY_INVALID");
                }
                copy.put(stringKey, immutable(child));
            });
            return Map.copyOf(copy);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(RagCandidate10FixtureGenerator::immutable).toList();
        }
        return value;
    }

    private static void validateGenerated(
            Split split,
            RagEvaluationDataset dataset,
            List<FamilySpec> families,
            Map<String, Resource> resources,
            Map<String, Integer> counts,
            int qrelPairCount,
            String fixtureSpecHash) {
        Set<String> documents = new LinkedHashSet<>();
        Set<String> familyIds = new LinkedHashSet<>();
        Set<String> noiseTokens = new LinkedHashSet<>();
        for (FamilySpec family : families) {
            if (!familyIds.add(family.familyId())) {
                throw new IllegalStateException("CANDIDATE10_FAMILY_DUPLICATE");
            }
        }
        for (RagEvaluationDataset.CorpusSegment segment
                : dataset.corpusById().values()) {
            long segmentId = Long.parseLong(segment.segmentId());
            long documentId = Long.parseLong(segment.documentId());
            if (segmentId < split.segmentBase + 1L
                    || segmentId > split.segmentIdMax()
                    || documentId < split.documentBase
                    || documentId > split.documentIdMax()
                    || segment.parentSegmentId() != null
                    || !documents.add(segment.documentId())
                    || !SOURCE.equals(segment.metadata().get("source"))
                    || !(segment.metadata().get("score") instanceof Number score)
                    || Double.compare(score.doubleValue(), SCORE) != 0) {
                throw new IllegalStateException("CANDIDATE10_ID_MAPPING_INVALID");
            }
            for (String token : segment.content().split(" ")) {
                if (token.startsWith("c10n") && !noiseTokens.add(token)) {
                    throw new IllegalStateException("CANDIDATE10_NOISE_DUPLICATE");
                }
            }
        }
        long longRows = dataset.corpusById().values().stream()
                .filter(segment -> Objects.toString(
                        segment.metadata().get("candidate10Role"), "")
                        .equals(FamilyRole.LONG_TOKEN.wireName + "-core"))
                .filter(segment -> segment.content().split(" ").length == LONG_TOKEN_COUNT)
                .count();
        if (dataset.corpusById().size() != split.segmentCount()
                || dataset.queries().size() != split.queryCount()
                || families.size() != split.familyCount
                || qrelPairCount != split.qrelPairCount
                || documents.size() != split.segmentCount()
                || longRows != split.scale
                || !resources.keySet().equals(RESOURCE_FILES.keySet())
                || counts.get("qrelPairCount") != split.qrelPairCount
                || fixtureSpecHash.length() != 64) {
            throw new IllegalStateException("CANDIDATE10_FIXTURE_COUNT_INVALID");
        }
        Map<TargetShape, Long> shapeCounts = families.stream()
                .filter(family -> family.targetShape() != null)
                .collect(() -> new EnumMap<>(TargetShape.class),
                        (map, family) -> map.merge(
                                family.targetShape(), 1L, Long::sum),
                        Map::putAll);
        for (TargetShape shape : TARGET_SHAPES) {
            if (!Objects.equals(shapeCounts.get(shape), 2L * split.scale)) {
                throw new IllegalStateException("CANDIDATE10_SHAPE_COUNT_INVALID");
            }
        }
    }

    private static String familyId(Split split, int ordinal) {
        return String.format(Locale.ROOT, "%s-f%03d", split.familyPrefix, ordinal);
    }

    enum Split {
        SELECTION(
                "selection", "candidate10-selection", "c10s", "S",
                20260725L, 20, 46,
                10_160_000L, 10_160_000L, 10_165_000L, 7_600_000L, 1),
        HOLDOUT(
                "holdout", "candidate10-holdout", "c10h", "H",
                20260726L, 40, 92,
                10_170_000L, 10_170_000L, 10_175_000L, 8_600_000L, 2);

        private final String externalName;
        private final String datasetName;
        private final String familyPrefix;
        private final String shortName;
        private final long seed;
        private final int familyCount;
        private final int qrelPairCount;
        private final long kbId;
        private final long segmentBase;
        private final long documentBase;
        private final long identifierBase;
        private final int scale;

        Split(
                String externalName,
                String datasetName,
                String familyPrefix,
                String shortName,
                long seed,
                int familyCount,
                int qrelPairCount,
                long kbId,
                long segmentBase,
                long documentBase,
                long identifierBase,
                int scale) {
            this.externalName = externalName;
            this.datasetName = datasetName;
            this.familyPrefix = familyPrefix;
            this.shortName = shortName;
            this.seed = seed;
            this.familyCount = familyCount;
            this.qrelPairCount = qrelPairCount;
            this.kbId = kbId;
            this.segmentBase = segmentBase;
            this.documentBase = documentBase;
            this.identifierBase = identifierBase;
            this.scale = scale;
        }

        String externalName() {
            return externalName;
        }

        String datasetName() {
            return datasetName;
        }

        String shortName() {
            return shortName;
        }

        long seed() {
            return seed;
        }

        int familyCount() {
            return familyCount;
        }

        int queryCount() {
            return familyCount * 2;
        }

        int segmentCount() {
            return familyCount * SEGMENTS_PER_FAMILY;
        }

        int qrelPairCount() {
            return qrelPairCount;
        }

        long kbId() {
            return kbId;
        }

        long segmentBase() {
            return segmentBase;
        }

        long documentBase() {
            return documentBase;
        }

        long identifierBase() {
            return identifierBase;
        }

        long segmentIdMax() {
            return segmentBase + segmentCount();
        }

        long documentIdMax() {
            return documentBase + segmentCount() - 1L;
        }
    }

    enum FamilyRole {
        TARGET("target", 1, Set.of(1, 2)),
        BASELINE_PRESENT("baseline-present", 1, Set.of(1)),
        NO_ID("no-ID", 0, Set.of(1)),
        KEYWORD_LURE("keyword-lure", 1, Set.of()),
        SEMANTIC_NEAR_LURE("semantic-near-lure", 1, Set.of()),
        BOUNDARY("boundary", 1, Set.of()),
        ZERO_PADDING("zero-padding", 1, Set.of()),
        MULTI_ID("multi-ID", 2, Set.of(1)),
        RELEVANT_NONEXACT("relevant-nonexact", 1, Set.of(1)),
        LONG_TOKEN("long-token", 1, Set.of(1)),
        NON_CORROBORATED_EXACT("non-corroborated-exact", 1, Set.of(1));

        private final String wireName;
        private final int identifierCount;
        private final Set<Integer> relevantCoreIndexes;

        FamilyRole(
                String wireName,
                int identifierCount,
                Set<Integer> relevantCoreIndexes) {
            this.wireName = wireName;
            this.identifierCount = identifierCount;
            this.relevantCoreIndexes = Set.copyOf(relevantCoreIndexes);
        }

        String wireName() {
            return wireName;
        }

        int identifierCount() {
            return identifierCount;
        }

        Set<Integer> relevantCoreIndexes() {
            return relevantCoreIndexes;
        }
    }

    enum TargetShape {
        NUMERIC_TOKEN("numeric-token"),
        DOC_PREFIX("doc-prefix"),
        ZERO_PADDED("zero-padded"),
        HAN_PUNCTUATION("han-punctuation");

        private final String wireName;

        TargetShape(String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }
    }

    record FamilySpec(
            int ordinal,
            String familyId,
            int roleSlot,
            FamilyRole role,
            Integer targetShapeSlot,
            TargetShape targetShape,
            String identifier1Raw,
            String identifier1,
            String identifier2Raw,
            String identifier2,
            String marker1,
            String marker2) {

        FamilySpec {
            Objects.requireNonNull(familyId, "familyId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(identifier1Raw, "identifier1Raw");
            Objects.requireNonNull(identifier1, "identifier1");
            Objects.requireNonNull(identifier2Raw, "identifier2Raw");
            Objects.requireNonNull(identifier2, "identifier2");
            Objects.requireNonNull(marker1, "marker1");
            Objects.requireNonNull(marker2, "marker2");
        }

        String shapeName() {
            if (targetShape != null) {
                return targetShape.wireName;
            }
            return switch (role) {
                case BOUNDARY -> "boundary-negative";
                case ZERO_PADDING -> "zero-padding-negative";
                case MULTI_ID -> "multi-id";
                default -> "none";
            };
        }
    }

    record Resource(String fileName, byte[] bytes, String sha256) {

        Resource {
            Objects.requireNonNull(fileName, "fileName");
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
            Objects.requireNonNull(sha256, "sha256");
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    record RankingFixture(
            Split split,
            long seed,
            RagEvaluationDataset dataset,
            List<FamilySpec> families,
            Map<String, Resource> resources,
            Map<String, Object> fixtureSpec,
            String fixtureSpecHash) {

        RankingFixture {
            Objects.requireNonNull(split, "split");
            Objects.requireNonNull(dataset, "dataset");
            families = List.copyOf(families);
            resources = Map.copyOf(resources);
            fixtureSpec = immutableMap(fixtureSpec);
            Objects.requireNonNull(fixtureSpecHash, "fixtureSpecHash");
        }

        Resource resource(String logicalName) {
            Resource resource = resources.get(logicalName);
            if (resource == null) {
                throw new IllegalArgumentException("CANDIDATE10_RESOURCE_MISSING");
            }
            return resource;
        }
    }

    record QrelFixture(
            Split split,
            Map<String, Map<String, Integer>> qrels,
            Resource resource,
            int pairCount) {

        QrelFixture {
            Objects.requireNonNull(split, "split");
            Objects.requireNonNull(resource, "resource");
            Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
            qrels.forEach((queryId, grades) ->
                    copy.put(queryId, Map.copyOf(grades)));
            qrels = Map.copyOf(copy);
        }
    }

    record GeneratedSplit(
            Split split,
            long seed,
            RagEvaluationDataset dataset,
            List<FamilySpec> families,
            Map<String, Resource> resources,
            Map<String, Integer> counts,
            Map<String, Object> structure,
            String fixtureSpecHash,
            byte[] datasetHashPreimage,
            String datasetHash) {

        GeneratedSplit {
            Objects.requireNonNull(split, "split");
            Objects.requireNonNull(dataset, "dataset");
            families = List.copyOf(families);
            resources = Map.copyOf(resources);
            counts = Map.copyOf(counts);
            structure = immutableMap(structure);
            Objects.requireNonNull(fixtureSpecHash, "fixtureSpecHash");
            datasetHashPreimage = Objects.requireNonNull(
                    datasetHashPreimage, "datasetHashPreimage").clone();
            Objects.requireNonNull(datasetHash, "datasetHash");
        }

        Resource resource(String logicalName) {
            Resource resource = resources.get(logicalName);
            if (resource == null) {
                throw new IllegalArgumentException("CANDIDATE10_RESOURCE_MISSING");
            }
            return resource;
        }

        @Override
        public byte[] datasetHashPreimage() {
            return datasetHashPreimage.clone();
        }
    }

    private enum SegmentKind {
        CORE("C"),
        LEXICAL("L"),
        PRESSURE("P");

        private final String code;

        SegmentKind(String code) {
            this.code = code;
        }
    }

    private record RoleAllocation(FamilyRole role, int selectionCount) {
    }

    private record Identifier(String raw, String visible) {
    }

    private record SegmentRow(
            int ordinal,
            String familyId,
            SegmentKind kind,
            int familyIndex,
            RagEvaluationDataset.CorpusSegment segment,
            Map<String, Object> json) {
    }

    private record QueryRow(
            RagEvaluationDataset.QueryCase query,
            Map<String, Object> json) {
    }

    private record QrelRow(String queryId, String segmentId, int ordinal) {
    }
}
