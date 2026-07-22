package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Development-only attribution of the frozen Candidate 10 mechanism.
 * This class deliberately uses the existing private ranking implementation;
 * it never creates or updates formal evidence.
 */
class RagCandidate10MechanismFactorAttributionTest {

    private static final String ENABLE_PROPERTY =
            "rag.eval.candidate10.mechanism-factor-attribution";
    private static final String STAGE_CLASS =
            RagCandidate10DiagnosticStageSupport.class.getName();
    private static final String RECOVERY_CLASS =
            RagCandidate10DiagnosticRecoveryV2Test.class.getName();
    private static final String MARKER =
            "CANDIDATE10_MECHANISM_FACTOR_ATTRIBUTION";
    private static final String HARNESS_ERROR =
            "CANDIDATE10_MECHANISM_FACTOR_ATTRIBUTION_HARNESS_INVALID";
    private static final String FROZEN_ERROR =
            "FROZEN_INPUT_CONTRACT_INVALID";
    private static final String RANKING_SHA =
            "8b3e99be63b059a2522481934e317b3af18c3bd3e77b865ea6ce71e5e8ab2bf6";
    private static final String STAGE_SOURCE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticStageSupport.java";
    private static final String SUPPORT_SOURCE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticSupport.java";
    private static final String RECOVERY_SOURCE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticRecoveryV2Test.java";
    private static final String RANKING_ATTRIBUTION_SOURCE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10RankingFailureAttributionTest.java";
    private static final String STAGE_SHA =
            "9949793dc2a8ee418e9ab8dbc1e7e6541458c6ea0639919f0b9f4c7190e6a6c6";
    private static final String SUPPORT_SHA =
            "802df58abd94b00b32c9c056bc119888eff72353070c3a7ff09168049805fa6e";
    private static final String RECOVERY_SOURCE_SHA =
            "606de704a7323a5a92be06111d9925002ef9bc2762d839ed99ef2168834fa4f4";
    private static final String RANKING_ATTRIBUTION_SOURCE_SHA =
            "890411497b17f23234a7e5b0ac62bc8b458dca48fb13138dd1e0eee6e551f671";
    private static final String ORIGINAL_DIAGNOSTIC_SHA =
            "844ba8b24cc28527d5b80ad7964549797db61e02e69ec1100fc0260e94ed4533";
    private static final String ORIGINAL_LEDGER_SHA =
            "f789dcea0a1b2609824ac2b1614b29f82546631eb678e4ec056c0690ab3d0431";
    private static final String ORIGINAL_SOURCE_LOCK_SHA =
            "2faa4a7847c9c9adb54212d8158b1ebe36d3d1ccf1a86ae516e0dc2ac3440d66";
    private static final String RECOVERY_DIAGNOSTIC_SHA =
            "5d2ce5fe3aedbb392911950d53a907cd8df93827767733918158e9bb6c0933d7";
    private static final String RECOVERY_LEDGER_SHA =
            "7feac42f1a196dceea0dbf62d665f0495e104dce038d7633673c6204408509b0";
    private static final String RECOVERY_SOURCE_LOCK_SHA =
            "3f1cfcaddc18b8c59df192fbb7b3a609daa266b9e9c905b3bab2d1e1d49f6b0f";
    private static final String RECOVERY_ENVELOPE_SHA =
            "93f3c243f9a8300689f3197f37212944f1b818415820682e932fcb3839803f1f";
    private static final String SELECTION_MANIFEST_SHA =
            "2dcd028732aac132c98497e95958926fbd788d1c993033d25249bccbd181d32e";
    private static final String HOLDOUT_MANIFEST_SHA =
            "798e81b6845f2e6034d78affeb85f436b721feb745b5e6f4e8721a8c6ee4b334";
    private static final String SELECTION_CORPUS_SHA =
            "68388a618a963b1de58f0dd617ae5230e61366f7f3abbf62cc6d7de32087749b";
    private static final String SELECTION_QUERIES_SHA =
            "7778e4447fa0338348e2baf9bdb34e800439a9cdb5f06a00574046fbf9d8b6d1";
    private static final String SELECTION_PRESSURE_SHA =
            "110c56ab06cc45015f648256770b60b87a58b56d814d849f8147e217514a5354";
    private static final Set<String> TARGET_SHAPES = Set.of(
            "numeric-token", "doc-prefix", "zero-padded", "han-punctuation");
    private static final Set<String> TARGET_LANGUAGES = Set.of("en", "zh");
    private static final int FULL_SIZE = 1_120;
    private static final int PREFIX_END = 30;
    private static final int WINDOW_END = 60;

    private static final Path BACKEND = Path.of(
            "/Users/achilles/Documents/许子祺/Agent/backend");
    private static final Path TESTS = BACKEND.resolve("tests");
    private static final Path RUNTIME = TESTS.resolve("target/rag-eval");
    private static final Path ORIGINAL_FREEZE = RUNTIME.resolve(
            "candidate10-freeze");
    private static final Path ORIGINAL_DIAGNOSTIC = RUNTIME.resolve(
            "candidate10-calibration-diagnostic.json");
    private static final Path ORIGINAL_LEDGER = ORIGINAL_FREEZE.resolve(
            "selection-ledger.json");
    private static final Path ORIGINAL_SOURCE_LOCK = ORIGINAL_FREEZE.resolve(
            "candidate10-source-lock.json");
    private static final Path REPORTS = TESTS.resolve(
            "target/surefire-reports");

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "true")
    void locatesMechanismFactors() {
        try {
            runAttribution();
        } catch (AssertionError failure) {
            throw failure;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw harnessInvalid();
        }
    }

    private static void runAttribution()
            throws ReflectiveOperationException {
        RagCandidate10FreezeSupport.RuntimePaths paths =
                publishedRuntimePaths();
        Map<String, String> before = allowedHashes(paths);
        requireExpectedHashes(before);

        RagCandidate10FreezeSupport.FrozenEvidence frozen =
                RagCandidate10FreezeSupport.openDiagnosticEvidence(paths);
        requireLockedSources(frozen);
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        invokeStageNoArgs("requireFormalCommand");
        invokeStageNoArgs("requireNativeDisabled");

        Class<?> accessType = Class.forName(STAGE_CLASS + "$AccessCounter");
        Object access = newInstance(accessType);
        Method loadInput = stageMethod(
                "loadRankingInput",
                RagCandidate10FreezeSupport.RuntimePaths.class,
                RagCandidate10FreezeSupport.FrozenEvidence.class,
                accessType);
        Object input = invokeStatic(loadInput, paths, frozen, access);
        requireAccess(access, false, 0);
        RagEvaluationDataset dataset =
                (RagEvaluationDataset) component(input, "dataset");
        @SuppressWarnings("unchecked")
        List<?> pool = (List<?>) component(input, "pool");
        require(dataset.queries().size() == 40 && pool.size() == FULL_SIZE);

        Method rankSelection = stageMethod(
                "rankSelection", input.getClass(), accessType);
        @SuppressWarnings("unchecked")
        List<?> rankings = (List<?>) invokeStatic(rankSelection, input, access);
        requireAccess(access, false, 0);
        require(rankings.size() == 40);
        requireRankingContract(rankings);
        String rankingSha = rankingSha(rankings);
        require(RANKING_SHA.equals(rankingSha));

        Method freezeRanking = accessType.getDeclaredMethod("freezeRanking");
        freezeRanking.setAccessible(true);
        invokeInstance(freezeRanking, access);
        requireAccess(access, true, 0);

        Method loadQrels = stageMethod(
                "loadQrelsAfterRanking",
                RagCandidate10FreezeSupport.RuntimePaths.class,
                RagCandidate10FreezeSupport.FrozenEvidence.class,
                accessType);
        RagEvaluationDataset labeled =
                (RagEvaluationDataset) invokeStatic(loadQrels, paths, frozen, access);
        requireAccess(access, true, 1);

        AttributionAggregate aggregate = classify(
                dataset, labeled, rankings);
        require(RANKING_SHA.equals(rankingSha));
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        require(Objects.equals(before, allowedHashes(paths)));
        String marker = aggregate.marker(access);
        requireMarkerSchema(marker);
        System.out.println(marker);
    }

    private static void requireMarkerSchema(String marker) {
        String prefix = MARKER + " ";
        require(marker != null && marker.startsWith(prefix));
        String json = marker.substring(prefix.length());
        JSONObject value;
        try {
            value = JSON.parseObject(json);
            require(value != null);
            require(Arrays.equals(
                    (json + "\n").getBytes(StandardCharsets.UTF_8),
                    RagCandidate10FreezeSupport.canonicalJsonBytes(value)));
        } catch (RuntimeException failure) {
            throw harnessInvalid();
        }
        Object status = value.get("status");
        if ("FROZEN_INPUT_CONTRACT_INVALID".equals(status)) {
            requireFrozenMarker(value);
        } else if ("VALID".equals(status)) {
            requireValidMarker(value);
        } else {
            throw harnessInvalid();
        }
    }

    private static void requireFrozenMarker(JSONObject value) {
        requireKeys(value, Set.of(
                "schemaVersion", "status", "targetQueryCount",
                "targetFamilyCount", "rankingSha256", "access",
                "errorCode"));
        requireString(value, "schemaVersion",
                "candidate10-mechanism-factor-attribution-v1");
        requireString(value, "status", "FROZEN_INPUT_CONTRACT_INVALID");
        requireInt(value, "targetQueryCount", 16);
        requireInt(value, "targetFamilyCount", 8);
        requireString(value, "rankingSha256", RANKING_SHA);
        requireString(value, "errorCode", FROZEN_ERROR);
        requireAccess(value);
    }

    private static void requireValidMarker(JSONObject value) {
        requireKeys(value, Set.of(
                "schemaVersion", "status", "targetQueryCount",
                "targetFamilyCount", "earliestStageCounts",
                "dominantEarliestStage", "attributionDecision",
                "combinationCounts", "dominantStageCombinationCounts",
                "dominantStageRegionCounts", "dominantStageShapeCounts",
                "dominantStageLanguageCounts", "familyMechanismCounts",
                "targetMechanismTrueCount", "rankingSha256", "access",
                "errorCode"));
        requireString(value, "schemaVersion",
                "candidate10-mechanism-factor-attribution-v1");
        requireString(value, "status", "VALID");
        requireInt(value, "targetQueryCount", 16);
        requireInt(value, "targetFamilyCount", 8);
        requireString(value, "rankingSha256", RANKING_SHA);
        require(value.get("errorCode") == null);
        requireAccess(value);

        JSONObject stages = requireObject(value, "earliestStageCounts");
        requireKeys(stages, Arrays.stream(Stage.values())
                .map(Enum::name).collect(Collectors.toSet()));
        requireMapCounts(stages, false, 16);

        JSONObject combinations = requireObject(value, "combinationCounts");
        requireMapCounts(combinations, true, 16);

        JSONObject families = requireObject(value, "familyMechanismCounts");
        requireKeys(families, Set.of("NONE_TRUE", "ONE_TRUE", "BOTH_TRUE"));
        requireMapCounts(families, false, 8);
        requireInt(families, "BOTH_TRUE", 0);
        int oneTrue = requireInt(families, "ONE_TRUE");
        int bothTrue = requireInt(families, "BOTH_TRUE");
        int mechanismTrue = requireInt(value, "targetMechanismTrueCount");
        require(mechanismTrue == oneTrue + (2 * bothTrue));

        Object decision = value.get("attributionDecision");
        require("RESEARCH_GATE_BLOCKED".equals(decision)
                || "ADMISSION_CONTRACT_MISMATCH_BLOCKED".equals(decision));
        Object dominant = value.get("dominantEarliestStage");
        JSONObject dominantCombination = requireObject(value,
                "dominantStageCombinationCounts");
        JSONObject dominantRegion = requireObject(value,
                "dominantStageRegionCounts");
        JSONObject dominantShape = requireObject(value,
                "dominantStageShapeCounts");
        JSONObject dominantLanguage = requireObject(value,
                "dominantStageLanguageCounts");
        if (dominant == null) {
            require(dominantCombination.isEmpty());
            require(dominantRegion.isEmpty());
            require(dominantShape.isEmpty());
            require(dominantLanguage.isEmpty());
        } else {
            require(dominant instanceof String
                    && Arrays.stream(Stage.values()).map(Enum::name)
                    .anyMatch(dominant::equals));
            int dominantCount = requireInt(stages, (String) dominant);
            require(dominantCount >= 9);
            requireMapCounts(dominantCombination, true, dominantCount);
            requireMapCounts(dominantRegion, true, dominantCount);
            requireMapCounts(dominantShape, true, dominantCount);
            requireMapCounts(dominantLanguage, true, dominantCount);
        }
    }

    private static void requireAccess(JSONObject value) {
        JSONObject access = requireObject(value, "access");
        requireKeys(access, Set.of(
                "selectionNonQrelResourceAccessCount",
                "qrelResourceAccessBeforeRanking",
                "qrelResourceAccessCount", "holdoutResourceAccessCount"));
        requireInt(access, "selectionNonQrelResourceAccessCount", 3);
        requireInt(access, "qrelResourceAccessBeforeRanking", 0);
        requireInt(access, "qrelResourceAccessCount", 1);
        requireInt(access, "holdoutResourceAccessCount", 0);
    }

    private static void requireKeys(JSONObject value, Set<String> expected) {
        require(value != null && value.keySet().equals(expected));
    }

    private static JSONObject requireObject(JSONObject parent, String key) {
        Object value = parent == null ? null : parent.get(key);
        require(value instanceof JSONObject);
        return (JSONObject) value;
    }

    private static void requireMapCounts(
            JSONObject value, boolean requirePositive, int expectedSum) {
        int sum = 0;
        for (Object entryValue : value.values()) {
            require(entryValue instanceof Number);
            Number number = (Number) entryValue;
            long integral = number.longValue();
            require(number.doubleValue() == integral);
            require(integral >= (requirePositive ? 1 : 0)
                    && integral <= Integer.MAX_VALUE);
            sum += (int) integral;
        }
        require(sum == expectedSum);
    }

    private static void requireString(
            JSONObject value, String key, String expected) {
        require(Objects.equals(value.get(key), expected));
    }

    private static int requireInt(JSONObject value, String key) {
        Object actual = value.get(key);
        require(actual instanceof Number);
        Number number = (Number) actual;
        long integral = number.longValue();
        require(number.doubleValue() == integral
                && integral >= Integer.MIN_VALUE
                && integral <= Integer.MAX_VALUE);
        return (int) integral;
    }

    private static void requireInt(
            JSONObject value, String key, int expected) {
        require(requireInt(value, key) == expected);
    }

    private static RagCandidate10FreezeSupport.RuntimePaths
    publishedRuntimePaths() throws ReflectiveOperationException {
        Class<?> recovery = Class.forName(RECOVERY_CLASS);
        Method method = recovery.getDeclaredMethod("publishedRuntimePaths");
        method.setAccessible(true);
        return (RagCandidate10FreezeSupport.RuntimePaths)
                invokeStatic(method);
    }

    private static AttributionAggregate classify(
            RagEvaluationDataset dataset,
            RagEvaluationDataset labeled,
            List<?> rankings) throws ReflectiveOperationException {
        List<Factor> factors = new ArrayList<>();
        Method targetMechanism = null;
        if (!rankings.isEmpty()) {
            targetMechanism = stageMethod(
                    "targetMechanism", RagEvaluationDataset.class,
                    rankings.get(0).getClass(), Map.class);
        }
        try {
            requireAllRelevantQrelsPresent(dataset, labeled, rankings);
            for (Object ranking : rankings) {
                String role = (String) component(ranking, "role");
                String shape = (String) component(ranking, "shape");
                Object queryValue = component(ranking, "query");
                RagEvaluationDataset.QueryCase query =
                        (RagEvaluationDataset.QueryCase) queryValue;
                if (!"target".equals(role) || !TARGET_SHAPES.contains(shape)) {
                    continue;
                }
                Map<String, Integer> qrels = labeled.qrelsFor(query.id());
                boolean oracle = (Boolean) invokeStatic(targetMechanism,
                        dataset, ranking, qrels);
                factors.add(classifyTarget(
                        dataset, query, qrels, ranking, oracle));
            }
        } catch (FrozenInputException failure) {
            return AttributionAggregate.frozen(factors.size());
        }
        require(factors.size() == 16);
        validateFamilies(factors);
        return AttributionAggregate.valid(factors);
    }

    private static Factor classifyTarget(
            RagEvaluationDataset dataset,
            RagEvaluationDataset.QueryCase query,
            Map<String, Integer> qrels,
            Object ranking,
            boolean oracle) throws ReflectiveOperationException {
        Object baseline = component(ranking, "baseline");
        Object candidate = component(ranking, "candidate");
        @SuppressWarnings("unchecked")
        List<Long> fullIds = (List<Long>) component(candidate, "fullRankingIds");
        @SuppressWarnings("unchecked")
        List<Long> baselineIds =
                (List<Long>) component(baseline, "fullRankingIds");
        @SuppressWarnings("unchecked")
        Map<Long, Integer> rankMap =
                (Map<Long, Integer>) component(candidate, "rankBySegmentId");
        requireFullRanking(fullIds, baselineIds, rankMap);

        List<String> identifiers =
                RagCandidate10DiagnosticSupport.identifierTerms(
                        query.retrievalQuery());
        Set<Long> exactRelevant = new LinkedHashSet<>();
        for (String relevant : qrels.keySet()) {
            if (!isRelevant(qrels, relevant)) {
                continue;
            }
            long id = parseId(relevant);
            Integer rank = rankMap.get(id);
            RagEvaluationDataset.CorpusSegment segment =
                    dataset.corpusById().get(relevant);
            if (segment == null || rank == null) {
                throw new FrozenInputException();
            }
            String name = documentName(segment);
            if (RagCandidate10DiagnosticSupport.matchesAllIdentifiers(
                    name, identifiers)) {
                exactRelevant.add(id);
            }
        }
        String region = regionSignature(exactRelevant, rankMap);
        String guard = guardOutcome(dataset, fullIds, identifiers);

        Integer admitted = (Integer) component(candidate, "admittedFullRank");
        List<Integer> windowExactRanks = exactRanks(
                dataset, fullIds, identifiers, PREFIX_END + 1, WINDOW_END);
        String admission;
        if (!"NONE".equals(guard)) {
            admission = admitted == null
                    ? Admission.NOT_EVALUATED.name()
                    : Admission.ADMISSION_CONTRACT_MISMATCH.name();
        } else if (admitted == null) {
            admission = windowExactRanks.isEmpty()
                    ? Admission.NO_AND_EXACT_IN_WINDOW.name()
                    : Admission.ADMISSION_CONTRACT_MISMATCH.name();
        } else if (admitted < PREFIX_END + 1 || admitted > WINDOW_END
                || windowExactRanks.isEmpty()
                || admitted.intValue() != windowExactRanks.get(0)) {
            admission = Admission.ADMISSION_CONTRACT_MISMATCH.name();
        } else {
            long admittedId = fullIds.get(admitted - 1);
            admission = isRelevant(qrels, String.valueOf(admittedId))
                    ? Admission.ADMITTED_RELEVANT.name()
                    : Admission.ADMITTED_NONRELEVANT.name();
        }

        String downstream = Downstream.NOT_APPLICABLE.name();
        if (Admission.ADMITTED_RELEVANT.name().equals(admission)
                || Admission.ADMITTED_NONRELEVANT.name().equals(admission)) {
            long admittedId = fullIds.get(admitted - 1);
            @SuppressWarnings("unchecked")
            List<Long> sources = (List<Long>) component(candidate, "sourceIds");
            @SuppressWarnings("unchecked")
            List<Long> context = (List<Long>) component(candidate, "contextIds");
            if (!uniqueKnownIds(sources, rankMap)
                    || !uniqueKnownIds(context, rankMap)) {
                admission = Admission.ADMISSION_CONTRACT_MISMATCH.name();
            } else if (!sources.contains(admittedId)) {
                downstream = Downstream.DROPPED_BY_CANDIDATE3.name();
            } else if (!context.contains(admittedId)) {
                downstream = Downstream.DROPPED_BY_CONTEXT.name();
            } else {
                downstream = Downstream.VISIBLE_IN_SOURCE_AND_CONTEXT.name();
            }
        } else if (!Downstream.NOT_APPLICABLE.name().equals(downstream)) {
            throw new FrozenInputException();
        }

        String stage = earliestStage(
                region, guard, admission, downstream, exactRelevant,
                fullIds, rankMap, admitted, oracle);
        return new Factor(
                query.familyId(), query.language(),
                (String) component(ranking, "shape"), region, guard,
                admission, downstream, stage, oracle);
    }

    private static String earliestStage(
            String region,
            String guard,
            String admission,
            String downstream,
            Set<Long> exactRelevant,
            List<Long> fullIds,
            Map<Long, Integer> rankMap,
            Integer admitted,
            boolean oracle) {
        if (Admission.ADMISSION_CONTRACT_MISMATCH.name().equals(admission)) {
            return Stage.ADMISSION_CONTRACT_MISMATCH.name();
        }
        if (oracle && !Admission.ADMITTED_RELEVANT.name().equals(admission)) {
            return Stage.ADMISSION_CONTRACT_MISMATCH.name();
        }
        if (oracle && !Downstream.VISIBLE_IN_SOURCE_AND_CONTEXT.name()
                .equals(downstream)) {
            return Stage.ADMISSION_CONTRACT_MISMATCH.name();
        }
        if (!Guard.NONE.name().equals(guard)) {
            return Stage.GUARD_BLOCKED.name();
        }
        if (Admission.ADMITTED_NONRELEVANT.name().equals(admission)) {
            return Stage.ADMITTED_NONRELEVANT.name();
        }
        if (Downstream.DROPPED_BY_CANDIDATE3.name().equals(downstream)) {
            return Stage.ADMITTED_RELEVANT_DROPPED_BY_CANDIDATE3.name();
        }
        if (Downstream.DROPPED_BY_CONTEXT.name().equals(downstream)) {
            return Stage.ADMITTED_RELEVANT_DROPPED_BY_CONTEXT.name();
        }
        if (Admission.ADMITTED_RELEVANT.name().equals(admission)) {
            require(admitted != null && admitted >= 1
                    && admitted <= fullIds.size());
            long admittedId = fullIds.get(admitted - 1);
            if (!exactRelevant.contains(admittedId)) {
                if (oracle) {
                    return Stage.ADMISSION_CONTRACT_MISMATCH.name();
                }
                return Stage.ADMITTED_RELEVANT_NONEXACT.name();
            }
            if (!oracle) {
                return Stage.ADMISSION_CONTRACT_MISMATCH.name();
            }
            return Stage.MECHANISM_REACHED.name();
        }
        if (Admission.NO_AND_EXACT_IN_WINDOW.name().equals(admission)) {
            if (Region.TAIL.name().equals(region)) {
                return Stage.NO_AND_EXACT_IN_WINDOW_TAIL.name();
            }
            if (Region.NONE.name().equals(region)) {
                return Stage.RELEVANT_EXACT_REGION_NONE.name();
            }
            return Stage.ADMISSION_CONTRACT_MISMATCH.name();
        }
        if (Region.NONE.name().equals(region)) {
            return Stage.RELEVANT_EXACT_REGION_NONE.name();
        }
        return Stage.UNCLASSIFIED_MECHANISM_FAILURE.name();
    }

    private static String guardOutcome(
            RagEvaluationDataset dataset,
            List<Long> fullIds,
            List<String> identifiers) throws ReflectiveOperationException {
        if (identifiers.isEmpty() || identifiers.size() > 2) {
            return Guard.IDENTIFIER_CARDINALITY_INVALID.name();
        }
        for (int index = 0; index < PREFIX_END; index++) {
            if (matchesAll(dataset, fullIds.get(index), identifiers)) {
                return Guard.PREFIX_AND_EXACT_PRESENT.name();
            }
        }
        if (matchesAny(dataset, fullIds.get(PREFIX_END - 1), identifiers)) {
            return Guard.RANK30_ANY_IDENTIFIER.name();
        }
        return Guard.NONE.name();
    }

    private static List<Integer> exactRanks(
            RagEvaluationDataset dataset,
            List<Long> fullIds,
            List<String> identifiers,
            int start,
            int end) throws ReflectiveOperationException {
        List<Integer> ranks = new ArrayList<>();
        for (int rank = start; rank <= end; rank++) {
            if (matchesAll(dataset, fullIds.get(rank - 1), identifiers)) {
                ranks.add(rank);
            }
        }
        return ranks;
    }

    private static String regionSignature(
            Set<Long> exactRelevant,
            Map<Long, Integer> rankMap) {
        Set<Region> regions = new LinkedHashSet<>();
        for (Long id : exactRelevant) {
            Integer rank = rankMap.get(id);
            if (rank == null) {
                throw new FrozenInputException();
            }
            if (rank <= PREFIX_END) {
                regions.add(Region.PREFIX);
            } else if (rank <= WINDOW_END) {
                regions.add(Region.WINDOW);
            } else {
                regions.add(Region.TAIL);
            }
        }
        if (regions.isEmpty()) {
            return Region.NONE.name();
        }
        return List.of(Region.PREFIX, Region.WINDOW, Region.TAIL).stream()
                .filter(regions::contains)
                .map(Enum::name)
                .collect(Collectors.joining("_"));
    }

    private static boolean isRelevant(
            Map<String, Integer> qrels, String segmentId) {
        Integer grade = qrels.get(segmentId);
        return grade != null && grade > 0;
    }

    private static void requireAllRelevantQrelsPresent(
            RagEvaluationDataset dataset,
            RagEvaluationDataset labeled,
            List<?> rankings) throws ReflectiveOperationException {
        for (Object ranking : rankings) {
            RagEvaluationDataset.QueryCase query =
                    (RagEvaluationDataset.QueryCase) component(ranking, "query");
            @SuppressWarnings("unchecked")
            Map<Long, Integer> rankMap = (Map<Long, Integer>) component(
                    component(ranking, "candidate"), "rankBySegmentId");
            for (String relevant : labeled.qrelsFor(query.id()).keySet()) {
                if (isRelevant(labeled.qrelsFor(query.id()), relevant)
                        && !rankMap.containsKey(parseId(relevant))) {
                    throw new FrozenInputException();
                }
            }
        }
    }

    private static boolean matchesAll(
            RagEvaluationDataset dataset,
            long id,
            List<String> identifiers) throws ReflectiveOperationException {
        RagEvaluationDataset.CorpusSegment segment =
                dataset.corpusById().get(String.valueOf(id));
        if (segment == null) {
            throw new FrozenInputException();
        }
        return RagCandidate10DiagnosticSupport.matchesAllIdentifiers(
                documentName(segment), identifiers);
    }

    private static boolean matchesAny(
            RagEvaluationDataset dataset,
            long id,
            List<String> identifiers) throws ReflectiveOperationException {
        RagEvaluationDataset.CorpusSegment segment =
                dataset.corpusById().get(String.valueOf(id));
        if (segment == null) {
            throw new FrozenInputException();
        }
        return RagCandidate10DiagnosticSupport.matchesAnyIdentifier(
                documentName(segment), identifiers);
    }

    private static String documentName(
            RagEvaluationDataset.CorpusSegment segment)
            throws ReflectiveOperationException {
        Method method = RagCandidate10DiagnosticStageSupport.class
                .getDeclaredMethod("documentName",
                        RagEvaluationDataset.CorpusSegment.class);
        method.setAccessible(true);
        return (String) invokeStatic(method, segment);
    }

    private static void requireRankingContract(List<?> rankings)
            throws ReflectiveOperationException {
        Set<String> shapes = new LinkedHashSet<>();
        Map<String, List<String>> familyLanguages = new LinkedHashMap<>();
        for (Object ranking : rankings) {
            Object baseline = component(ranking, "baseline");
            Object candidate = component(ranking, "candidate");
            require(Objects.equals(component(baseline, "fullRankingSha256"),
                    component(candidate, "fullRankingSha256")));
            require(Objects.equals(component(baseline, "fullRankingIds"),
                    component(candidate, "fullRankingIds")));
            Object baselineScorer = component(baseline, "scorer");
            Object candidateScorer = component(candidate, "scorer");
            require(Objects.equals(component(baselineScorer, "queryTokenCount"),
                    component(candidateScorer, "queryTokenCount")));
            require(Objects.equals(component(baselineScorer, "documentTokenCount"),
                    component(candidateScorer, "documentTokenCount")));
            requireBudget(baseline);
            requireBudget(candidate);
            Object queryValue = component(ranking, "query");
            RagEvaluationDataset.QueryCase query =
                    (RagEvaluationDataset.QueryCase) queryValue;
            String role = (String) component(ranking, "role");
            String shape = (String) component(ranking, "shape");
            if ("target".equals(role) && TARGET_SHAPES.contains(shape)) {
                shapes.add(shape);
                familyLanguages.computeIfAbsent(query.familyId(),
                        ignored -> new ArrayList<>()).add(query.language());
            }
        }
        require(shapes.equals(TARGET_SHAPES));
        require(familyLanguages.size() == 8);
        familyLanguages.values().forEach(languages -> {
            require(languages.size() == 2);
            require(new LinkedHashSet<>(languages).equals(TARGET_LANGUAGES));
        });
    }

    private static void requireBudget(Object arm)
            throws ReflectiveOperationException {
        Object scorer = component(arm, "scorer");
        require(Objects.equals(component(scorer, "calls"), 1));
        require(Objects.equals(component(scorer, "requestedTopK"), FULL_SIZE));
        require(Objects.equals(component(scorer, "inputCount"), FULL_SIZE));
        require(Objects.equals(component(scorer, "outputCount"), FULL_SIZE));
        require(Objects.equals(component(arm, "candidate3Calls"), 1));
        require(Objects.equals(component(arm, "contextCalls"), 1));
        require(Objects.equals(component(arm, "internalJdbcCalls"), 1));
        for (String field : List.of(
                "externalEmbeddingCalls", "externalDbCalls",
                "externalVectorCalls", "externalMetadataCalls",
                "externalGraphCalls", "externalNetworkCalls",
                "externalLlmCalls")) {
            require(Objects.equals(component(arm, field), 0));
        }
    }

    private static void validateFamilies(List<Factor> factors) {
        Map<String, List<Factor>> families = factors.stream().collect(
                Collectors.groupingBy(Factor::familyId,
                        LinkedHashMap::new, Collectors.toList()));
        require(families.size() == 8);
        families.values().forEach(values -> {
            require(values.size() == 2);
            require(values.stream().map(Factor::language)
                    .collect(Collectors.toSet()).equals(TARGET_LANGUAGES));
        });
    }

    private static void requireFullRanking(
            List<Long> candidateIds,
            List<Long> baselineIds,
            Map<Long, Integer> rankMap) {
        if (candidateIds.size() != FULL_SIZE
                || !candidateIds.equals(baselineIds)
                || new LinkedHashSet<>(candidateIds).size() != FULL_SIZE
                || rankMap.size() != FULL_SIZE) {
            throw new FrozenInputException();
        }
        for (int index = 0; index < candidateIds.size(); index++) {
            if (!Objects.equals(rankMap.get(candidateIds.get(index)), index + 1)) {
                throw new FrozenInputException();
            }
        }
    }

    private static boolean uniqueKnownIds(
            List<Long> ids, Map<Long, Integer> rankMap) {
        if (ids == null || rankMap == null
                || new LinkedHashSet<>(ids).size() != ids.size()) {
            return false;
        }
        return ids.stream().allMatch(id -> id != null && rankMap.containsKey(id));
    }

    private static long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException failure) {
            throw new FrozenInputException();
        }
    }

    private static String rankingSha(List<?> rankings)
            throws ReflectiveOperationException {
        List<Object> views = new ArrayList<>();
        for (Object ranking : rankings) {
            views.add(component(ranking, "hashView"));
        }
        return RagCandidate10FreezeSupport.sha256(
                RagCandidate10FreezeSupport.canonicalJsonBytes(views));
    }

    private static Object component(Object target, String name)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return invokeInstance(method, target);
    }

    private static Method stageMethod(String name, Class<?>... types)
            throws NoSuchMethodException {
        Method method = RagCandidate10DiagnosticStageSupport.class
                .getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static void invokeStageNoArgs(String name)
            throws ReflectiveOperationException {
        invokeStatic(stageMethod(name));
    }

    private static Object invokeStatic(Method method, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(HARNESS_ERROR);
        }
    }

    private static Object invokeInstance(Method method, Object target,
                                         Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(HARNESS_ERROR);
        }
    }

    private static Object newInstance(Class<?> type)
            throws ReflectiveOperationException {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            return constructor.newInstance();
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(HARNESS_ERROR);
        }
    }

    private static void requireAccess(
            Object access, boolean frozen, int qrelCount)
            throws ReflectiveOperationException {
        require(Objects.equals(fieldValue(access, "rankingFrozen"), frozen));
        require(Objects.equals(
                fieldValue(access, "selectionNonQrelResourceAccessCount"), 3));
        require(Objects.equals(
                fieldValue(access, "qrelResourceAccessBeforeRanking"), 0));
        require(Objects.equals(
                fieldValue(access, "qrelResourceAccessCount"), qrelCount));
        require(Objects.equals(
                fieldValue(access, "holdoutResourceAccessCount"), 0));
    }

    private static Object fieldValue(Object target, String name)
            throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Map<String, String> allowedHashes(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("originalDiagnostic", ORIGINAL_DIAGNOSTIC);
        files.put("originalLedger", ORIGINAL_LEDGER);
        files.put("originalSourceLock", ORIGINAL_SOURCE_LOCK);
        files.put("recoveryDiagnostic", paths.diagnostic());
        files.put("recoveryLedger", paths.ledger());
        files.put("recoverySourceLock", paths.sourceLock());
        files.put("recoveryEnvelope",
                paths.freezeDirectory().resolve("recovery-manifest.json"));
        files.put("selectionManifest", paths.selectionManifest());
        files.put("holdoutManifest", paths.holdoutManifest());
        files.put("selectionCorpus",
                paths.selectionDirectory().resolve("corpus.jsonl"));
        files.put("selectionQueries",
                paths.selectionDirectory().resolve("queries.jsonl"));
        files.put("selectionPressure",
                paths.selectionDirectory().resolve("pressure.json"));
        files.put("stageSource", BACKEND.resolve(STAGE_SOURCE));
        files.put("supportSource", BACKEND.resolve(SUPPORT_SOURCE));
        files.put("recoverySource", BACKEND.resolve(RECOVERY_SOURCE));
        files.put("rankingAttributionSource",
                BACKEND.resolve(RANKING_ATTRIBUTION_SOURCE));
        Map<String, String> hashes = new LinkedHashMap<>();
        files.forEach((name, path) -> {
            require(path != null
                    && !Files.isSymbolicLink(path)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
            hashes.put(name, RagCandidate10FreezeSupport.sha256(path));
        });
        snapshotReports(hashes);
        return Map.copyOf(hashes);
    }

    private static void snapshotReports(Map<String, String> hashes) {
        try (var stream = Files.list(REPORTS)) {
            stream.filter(path -> Files.isRegularFile(path,
                            LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(path))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("TEST-tech.qiantong.qknow.rag.eval."
                                + "RagCandidate10DiagnosticRecoveryV2Test-")
                                || name.startsWith("tech.qiantong.qknow.rag.eval."
                                + "RagCandidate10DiagnosticRecoveryV2Test-")
                                || name.startsWith("TEST-tech.qiantong.qknow.rag.eval."
                                + "RagCandidate10FormalEvidenceTest-")
                                || name.startsWith("tech.qiantong.qknow.rag.eval."
                                + "RagCandidate10FormalEvidenceTest-")
                                || name.startsWith("TEST-tech.qiantong.qknow.rag.eval."
                                + "RagCandidate10RankingFailureAttributionTest-")
                                || name.startsWith("tech.qiantong.qknow.rag.eval."
                                + "RagCandidate10RankingFailureAttributionTest-");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> hashes.put(
                            "report:" + path.getFileName(),
                            RagCandidate10FreezeSupport.sha256(path)));
        } catch (Exception failure) {
            throw harnessInvalid();
        }
    }

    private static void requireExpectedHashes(Map<String, String> hashes) {
        requireEquals(hashes.get("originalDiagnostic"), ORIGINAL_DIAGNOSTIC_SHA);
        requireEquals(hashes.get("originalLedger"), ORIGINAL_LEDGER_SHA);
        requireEquals(hashes.get("originalSourceLock"), ORIGINAL_SOURCE_LOCK_SHA);
        requireEquals(hashes.get("recoveryDiagnostic"), RECOVERY_DIAGNOSTIC_SHA);
        requireEquals(hashes.get("recoveryLedger"), RECOVERY_LEDGER_SHA);
        requireEquals(hashes.get("recoverySourceLock"), RECOVERY_SOURCE_LOCK_SHA);
        requireEquals(hashes.get("recoveryEnvelope"), RECOVERY_ENVELOPE_SHA);
        requireEquals(hashes.get("selectionManifest"), SELECTION_MANIFEST_SHA);
        requireEquals(hashes.get("holdoutManifest"), HOLDOUT_MANIFEST_SHA);
        requireEquals(hashes.get("selectionCorpus"), SELECTION_CORPUS_SHA);
        requireEquals(hashes.get("selectionQueries"), SELECTION_QUERIES_SHA);
        requireEquals(hashes.get("selectionPressure"), SELECTION_PRESSURE_SHA);
        requireEquals(hashes.get("stageSource"), STAGE_SHA);
        requireEquals(hashes.get("supportSource"), SUPPORT_SHA);
        requireEquals(hashes.get("recoverySource"), RECOVERY_SOURCE_SHA);
        requireEquals(hashes.get("rankingAttributionSource"),
                RANKING_ATTRIBUTION_SOURCE_SHA);
    }

    private static void requireLockedSources(
            RagCandidate10FreezeSupport.FrozenEvidence frozen) {
        requireEquals(frozen.lockedFiles().get(STAGE_SOURCE), STAGE_SHA);
        requireEquals(frozen.lockedFiles().get(SUPPORT_SOURCE), SUPPORT_SHA);
    }

    private static void requireEquals(Object actual, Object expected) {
        require(Objects.equals(actual, expected));
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw harnessInvalid();
        }
    }

    private static AssertionError harnessInvalid() {
        return new AssertionError(HARNESS_ERROR);
    }

    private enum Region {
        NONE, PREFIX, WINDOW, TAIL
    }

    private enum Guard {
        NONE, IDENTIFIER_CARDINALITY_INVALID,
        PREFIX_AND_EXACT_PRESENT, RANK30_ANY_IDENTIFIER
    }

    private enum Admission {
        NOT_EVALUATED, NO_AND_EXACT_IN_WINDOW,
        ADMISSION_CONTRACT_MISMATCH, ADMITTED_RELEVANT,
        ADMITTED_NONRELEVANT
    }

    private enum Downstream {
        NOT_APPLICABLE, DROPPED_BY_CANDIDATE3,
        DROPPED_BY_CONTEXT, VISIBLE_IN_SOURCE_AND_CONTEXT
    }

    private enum Stage {
        FROZEN_INPUT_CONTRACT_INVALID,
        ADMISSION_CONTRACT_MISMATCH,
        GUARD_BLOCKED,
        ADMITTED_NONRELEVANT,
        ADMITTED_RELEVANT_DROPPED_BY_CANDIDATE3,
        ADMITTED_RELEVANT_DROPPED_BY_CONTEXT,
        ADMITTED_RELEVANT_NONEXACT,
        NO_AND_EXACT_IN_WINDOW_TAIL,
        RELEVANT_EXACT_REGION_NONE,
        MECHANISM_REACHED,
        UNCLASSIFIED_MECHANISM_FAILURE
    }

    private record Factor(
            String familyId,
            String language,
            String shape,
            String region,
            String guard,
            String admission,
            String downstream,
            String stage,
            boolean mechanism) {

        private String combination() {
            return region + "|" + guard + "|" + admission + "|"
                    + downstream;
        }
    }

    private static final class AttributionAggregate {
        private final List<Factor> factors;
        private final boolean frozen;

        private AttributionAggregate(List<Factor> factors, boolean frozen) {
            this.factors = List.copyOf(factors);
            this.frozen = frozen;
        }

        static AttributionAggregate valid(List<Factor> factors) {
            return new AttributionAggregate(factors, false);
        }

        static AttributionAggregate frozen(int ignored) {
            return new AttributionAggregate(List.of(), true);
        }

        private String marker(Object access)
                throws ReflectiveOperationException {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("schemaVersion",
                    "candidate10-mechanism-factor-attribution-v1");
            marker.put("status", frozen ? FROZEN_ERROR : "VALID");
            marker.put("targetQueryCount", 16);
            marker.put("targetFamilyCount", 8);
            if (frozen) {
                marker.put("rankingSha256", RANKING_SHA);
                marker.put("access", accessMap(access));
                marker.put("errorCode", FROZEN_ERROR);
            } else {
                Map<String, Integer> stages = new LinkedHashMap<>();
                for (Stage stage : Stage.values()) {
                    stages.put(stage.name(), count(f ->
                            stage.name().equals(f.stage())));
                }
                String dominant = dominant(stages);
                boolean mismatch = stages.get(Stage.ADMISSION_CONTRACT_MISMATCH.name()) > 0;
                boolean unclassified = stages.get(
                        Stage.UNCLASSIFIED_MECHANISM_FAILURE.name()) > 0;
                String effectiveDominant = mismatch || unclassified
                        ? null : dominant;
                Map<String, Object> combinationCounts = counts(
                        factors, Factor::combination);
                Map<String, Object> familyCounts = familyMechanismCounts();
                marker.put("earliestStageCounts", stages);
                marker.put("dominantEarliestStage",
                        effectiveDominant);
                marker.put("attributionDecision",
                        mismatch ? "ADMISSION_CONTRACT_MISMATCH_BLOCKED"
                                : "RESEARCH_GATE_BLOCKED");
                marker.put("combinationCounts", combinationCounts);
                marker.put("dominantStageCombinationCounts",
                        effectiveDominant == null ? Map.of() : counts(
                                factors, f -> effectiveDominant.equals(f.stage())
                                        ? f.combination() : null));
                marker.put("dominantStageRegionCounts",
                        effectiveDominant == null ? Map.of() : counts(
                                factors, f -> effectiveDominant.equals(f.stage())
                                        ? f.region() : null));
                marker.put("dominantStageShapeCounts",
                        effectiveDominant == null ? Map.of() : counts(
                                factors, f -> effectiveDominant.equals(f.stage())
                                        ? f.shape() : null));
                marker.put("dominantStageLanguageCounts",
                        effectiveDominant == null ? Map.of() : counts(
                                factors, f -> effectiveDominant.equals(f.stage())
                                        ? f.language() : null));
                marker.put("familyMechanismCounts", familyCounts);
                marker.put("targetMechanismTrueCount",
                        factors.stream().filter(Factor::mechanism).count());
                marker.put("rankingSha256", RANKING_SHA);
                marker.put("access", accessMap(access));
                marker.put("errorCode", null);
            }
            String json = new String(
                    RagCandidate10FreezeSupport.canonicalJsonBytes(marker),
                    StandardCharsets.UTF_8).trim();
            return MARKER + " " + json;
        }

        private Map<String, Object> accessMap(Object access)
                throws ReflectiveOperationException {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("selectionNonQrelResourceAccessCount",
                    fieldValue(access, "selectionNonQrelResourceAccessCount"));
            value.put("qrelResourceAccessBeforeRanking",
                    fieldValue(access, "qrelResourceAccessBeforeRanking"));
            value.put("qrelResourceAccessCount",
                    fieldValue(access, "qrelResourceAccessCount"));
            value.put("holdoutResourceAccessCount",
                    fieldValue(access, "holdoutResourceAccessCount"));
            return value;
        }

        private int count(Function<Factor, Boolean> predicate) {
            int result = 0;
            for (Factor factor : factors) {
                if (predicate.apply(factor)) {
                    result++;
                }
            }
            return result;
        }

        private Map<String, Object> counts(
                List<Factor> values,
                Function<Factor, String> keyFunction) {
            Map<String, Object> result = new TreeMap<>();
            values.stream().map(keyFunction).filter(Objects::nonNull)
                    .forEach(key -> result.merge(key, 1, (left, right) ->
                            ((Integer) left) + ((Integer) right)));
            return result;
        }

        private String dominant(Map<String, Integer> stages) {
            int max = stages.values().stream().max(Integer::compareTo)
                    .orElse(0);
            if (max < 9 || stages.values().stream()
                    .filter(value -> value == max).count() != 1) {
                return null;
            }
            return stages.entrySet().stream()
                    .filter(entry -> entry.getValue() == max)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }

        private Map<String, Object> familyMechanismCounts() {
            Map<String, List<Factor>> families = factors.stream().collect(
                    Collectors.groupingBy(Factor::familyId,
                            LinkedHashMap::new, Collectors.toList()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("NONE_TRUE", 0);
            result.put("ONE_TRUE", 0);
            result.put("BOTH_TRUE", 0);
            for (List<Factor> values : families.values()) {
                long trueCount = values.stream().filter(Factor::mechanism).count();
                String key = trueCount == 0 ? "NONE_TRUE"
                        : trueCount == 1 ? "ONE_TRUE" : "BOTH_TRUE";
                result.put(key, ((Integer) result.get(key)) + 1);
            }
            require(((Integer) result.get("BOTH_TRUE")) == 0);
            return result;
        }
    }

    private static final class FrozenInputException extends RuntimeException {
        private FrozenInputException() {
            super(FROZEN_ERROR);
        }
    }
}
