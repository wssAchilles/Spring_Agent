package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class RagCandidate10RankingFailureAttributionTest {

    private static final String ENABLE_PROPERTY =
            "rag.eval.candidate10.ranking-attribution";
    private static final String MARKER = "CANDIDATE10_ATTRIBUTION";
    private static final String RANKING_ERROR =
            "CANDIDATE10_RANKING_INVALID";
    private static final String STAGE_CLASS =
            RagCandidate10DiagnosticStageSupport.class.getName();
    private static final String SUPPORT_CLASS =
            RagCandidate10DiagnosticSupport.class.getName();
    private static final String STAGE_SOURCE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticStageSupport.java";
    private static final String SUPPORT_SOURCE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10DiagnosticSupport.java";
    private static final String STAGE_SHA =
            "b70daf2a7b5d3162a2ad0157aa735a5d2e4b66b61301a9a828c67c5db75e5765";
    private static final String SUPPORT_SHA =
            "802df58abd94b00b32c9c056bc119888eff72353070c3a7ff09168049805fa6e";
    private static final Map<String, String> EXPECTED_HASHES = Map.ofEntries(
            Map.entry("diagnostic",
                    "844ba8b24cc28527d5b80ad7964549797db61e02e69ec1100fc0260e94ed4533"),
            Map.entry("ledger",
                    "f789dcea0a1b2609824ac2b1614b29f82546631eb678e4ec056c0690ab3d0431"),
            Map.entry("sourceLock",
                    "2faa4a7847c9c9adb54212d8158b1ebe36d3d1ccf1a86ae516e0dc2ac3440d66"),
            Map.entry("selectionManifest",
                    "2dcd028732aac132c98497e95958926fbd788d1c993033d25249bccbd181d32e"),
            Map.entry("holdoutManifest",
                    "798e81b6845f2e6034d78affeb85f436b721feb745b5e6f4e8721a8c6ee4b334"),
            Map.entry("selectionCorpus",
                    "68388a618a963b1de58f0dd617ae5230e61366f7f3abbf62cc6d7de32087749b"),
            Map.entry("selectionQueries",
                    "7778e4447fa0338348e2baf9bdb34e800439a9cdb5f06a00574046fbf9d8b6d1"),
            Map.entry("selectionPressure",
                    "110c56ab06cc45015f648256770b60b87a58b56d814d849f8147e217514a5354"));

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "true")
    void locatesFirstRankingFailure() {
        try {
            runAttribution();
        } catch (AssertionError failure) {
            throw failure;
        } catch (RuntimeException | ReflectiveOperationException failure) {
            throw harnessInvalid();
        }
    }

    private static void runAttribution() throws ReflectiveOperationException {
        RagCandidate10FreezeSupport.RuntimePaths paths =
                RagCandidate10FreezeSupport.formalPaths();
        Map<String, String> before = allowedHashes(paths);
        requireEqual(EXPECTED_HASHES, before);

        RagCandidate10FreezeSupport.FrozenEvidence frozen =
                RagCandidate10FreezeSupport.openDiagnosticEvidence(paths);
        requireLockedMappingSources(frozen);
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        invokeNoArgs("requireNativeDisabled");

        Class<?> accessType = nestedClass("AccessCounter");
        Constructor<?> accessConstructor = accessType.getDeclaredConstructor();
        accessConstructor.setAccessible(true);
        Object access = accessConstructor.newInstance();
        Object input = invokeStatic(
                "loadRankingInput",
                new Class<?>[]{
                        RagCandidate10FreezeSupport.RuntimePaths.class,
                        RagCandidate10FreezeSupport.FrozenEvidence.class,
                        accessType},
                paths, frozen, access);
        requireRestrictedAccessCounts(access);

        RagEvaluationDataset dataset = (RagEvaluationDataset) accessor(
                input, "dataset");
        @SuppressWarnings("unchecked")
        List<RetrievalResult> pool = (List<RetrievalResult>) accessor(
                input, "pool");
        require(dataset.queries().size() == 40 && pool.size() == 1_120);

        Method runArm = RagCandidate10DiagnosticStageSupport.class
                .getDeclaredMethod("runArm", String.class, List.class,
                        boolean.class);
        runArm.setAccessible(true);
        Attribution attribution = replay(runArm, dataset.queries(), pool);

        requireRestrictedAccessCounts(access);
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        requireEqual(before, allowedHashes(paths));
        System.out.println(attribution.marker());
    }

    private static Attribution replay(
            Method runArm,
            List<RagEvaluationDataset.QueryCase> queries,
            List<RetrievalResult> pool) throws ReflectiveOperationException {
        for (int index = 0; index < queries.size(); index++) {
            int queryOrdinal = index + 1;
            String query = queries.get(index).retrievalQuery();
            ArmRun baseline = invokeArm(
                    runArm, query, pool, false, queryOrdinal, Arm.BASELINE);
            if (baseline.attribution() != null) {
                return baseline.attribution();
            }
            ArmRun candidate = invokeArm(
                    runArm, query, pool, true, queryOrdinal, Arm.CANDIDATE);
            if (candidate.attribution() != null) {
                return candidate.attribution();
            }
            if (!Objects.equals(
                    accessor(baseline.evidence(), "fullRankingSha256"),
                    accessor(candidate.evidence(), "fullRankingSha256"))) {
                return new Attribution(queryOrdinal, Arm.AB_COMPARISON,
                        FailureStage.AB_FULL_RANKING);
            }
            Object baselineScorer = accessor(baseline.evidence(), "scorer");
            Object candidateScorer = accessor(candidate.evidence(), "scorer");
            if (!Objects.equals(accessor(baselineScorer, "queryTokenCount"),
                    accessor(candidateScorer, "queryTokenCount"))
                    || !Objects.equals(
                    accessor(baselineScorer, "documentTokenCount"),
                    accessor(candidateScorer, "documentTokenCount"))) {
                return new Attribution(queryOrdinal, Arm.AB_COMPARISON,
                        FailureStage.AB_TOKEN_COUNT);
            }
        }
        return new Attribution(queries.size(), Arm.AB_COMPARISON,
                FailureStage.UNCLASSIFIED_RANKING_FAILURE);
    }

    private static ArmRun invokeArm(
            Method runArm,
            String query,
            List<RetrievalResult> pool,
            boolean candidate,
            int queryOrdinal,
            Arm arm) throws ReflectiveOperationException {
        try {
            return new ArmRun(runArm.invoke(null, query, pool, candidate), null);
        } catch (InvocationTargetException failure) {
            if (!hasRankingError(failure)) {
                throw harnessInvalid();
            }
            return new ArmRun(null, new Attribution(
                    queryOrdinal, arm, failureStage(failure)));
        }
    }

    private static FailureStage failureStage(Throwable failure) {
        for (Throwable current = failure;
                current != null; current = current.getCause()) {
            for (StackTraceElement frame : current.getStackTrace()) {
                FailureStage stage = mappedStage(frame);
                if (stage != null) {
                    return stage;
                }
            }
        }
        return FailureStage.UNCLASSIFIED_RANKING_FAILURE;
    }

    private static FailureStage mappedStage(StackTraceElement frame) {
        String owner = frame.getClassName();
        String method = frame.getMethodName();
        int line = frame.getLineNumber();
        if (owner.equals(STAGE_CLASS) || owner.startsWith(STAGE_CLASS + "$")) {
            if (method.equals("fail") || method.equals("requireKnownError")) {
                return null;
            }
            if (method.equals("requireNoScopedFallback") || method.equals("runArm")
                    && line == 376) {
                return FailureStage.COLBERT_FALLBACK;
            }
            if (method.equals("runArm") && line == 365) {
                return FailureStage.PREEXISTING_FALLBACK_SCOPE;
            }
            if (method.equals("audit") || method.equals("tokenCount")) {
                return FailureStage.SCORER_AUDIT;
            }
            if (method.equals("oracleResults")) {
                return FailureStage.PRODUCTION_ORACLE_FIELD;
            }
            if (method.equals("requireResultsEqual")) {
                return line == 514
                        ? FailureStage.PRODUCTION_ORACLE_SIZE
                        : FailureStage.PRODUCTION_ORACLE_FIELD;
            }
            if (method.equals("requireSnapshotEqualsOracle")) {
                return FailureStage.SNAPSHOT_ORACLE;
            }
            if (method.equals("runArm") && line == 415) {
                return FailureStage.CANDIDATE3_RESULT;
            }
            if (method.equals("runArm") && line == 437) {
                return FailureStage.ORIGINALS_MUTATED;
            }
            if (method.equals("rankMap")) {
                return FailureStage.RANK_MAP;
            }
            return null;
        }
        if (owner.equals(SUPPORT_CLASS)) {
            if (method.equals("rankingInvalid")) {
                return null;
            }
            if (method.equals("snapshotFullRanking")
                    || method.equals("freezeMetadata")) {
                return FailureStage.SNAPSHOT_ORACLE;
            }
        }
        return null;
    }

    private static boolean hasRankingError(Throwable failure) {
        for (Throwable current = failure;
                current != null; current = current.getCause()) {
            if (RANKING_ERROR.equals(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> allowedHashes(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("diagnostic", paths.diagnostic());
        files.put("ledger", paths.ledger());
        files.put("sourceLock", paths.sourceLock());
        files.put("selectionManifest", paths.selectionManifest());
        files.put("holdoutManifest", paths.holdoutManifest());
        files.put("selectionCorpus",
                paths.selectionDirectory().resolve("corpus.jsonl"));
        files.put("selectionQueries",
                paths.selectionDirectory().resolve("queries.jsonl"));
        files.put("selectionPressure",
                paths.selectionDirectory().resolve("pressure.json"));
        Map<String, String> hashes = new LinkedHashMap<>();
        files.forEach((name, path) -> {
            require(!Files.isSymbolicLink(path)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
            hashes.put(name, RagCandidate10FreezeSupport.sha256(path));
        });
        return Map.copyOf(hashes);
    }

    private static void requireLockedMappingSources(
            RagCandidate10FreezeSupport.FrozenEvidence frozen) {
        require(STAGE_SHA.equals(frozen.lockedFiles().get(STAGE_SOURCE))
                && SUPPORT_SHA.equals(
                frozen.lockedFiles().get(SUPPORT_SOURCE)));
    }

    private static void requireRestrictedAccessCounts(Object access)
            throws ReflectiveOperationException {
        require(!booleanField(access, "rankingFrozen")
                && intField(access, "selectionNonQrelResourceAccessCount") == 3
                && intField(access, "qrelResourceAccessBeforeRanking") == 0
                && intField(access, "qrelResourceAccessCount") == 0
                && intField(access, "holdoutResourceAccessCount") == 0);
    }

    private static boolean booleanField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static int intField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static Object accessor(Object target, String name)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invokeStatic(
            String name, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        Method method = RagCandidate10DiagnosticStageSupport.class
                .getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException failure) {
            throw harnessInvalid();
        }
    }

    private static void invokeNoArgs(String name)
            throws ReflectiveOperationException {
        invokeStatic(name, new Class<?>[0]);
    }

    private static Class<?> nestedClass(String simpleName)
            throws ClassNotFoundException {
        return Class.forName(STAGE_CLASS + "$" + simpleName);
    }

    private static void requireEqual(Object expected, Object actual) {
        require(Objects.equals(expected, actual));
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw harnessInvalid();
        }
    }

    private static AssertionError harnessInvalid() {
        return new AssertionError("CANDIDATE10_ATTRIBUTION_HARNESS_INVALID");
    }

    private enum Arm {
        BASELINE,
        CANDIDATE,
        AB_COMPARISON
    }

    private enum FailureStage {
        PREEXISTING_FALLBACK_SCOPE,
        COLBERT_FALLBACK,
        SCORER_AUDIT,
        PRODUCTION_ORACLE_SIZE,
        PRODUCTION_ORACLE_FIELD,
        SNAPSHOT_ORACLE,
        CANDIDATE3_RESULT,
        RANK_MAP,
        ORIGINALS_MUTATED,
        AB_FULL_RANKING,
        AB_TOKEN_COUNT,
        UNCLASSIFIED_RANKING_FAILURE
    }

    private record Attribution(
            int queryOrdinal, Arm arm, FailureStage failureStage) {

        private String marker() {
            return MARKER
                    + " queryOrdinal=" + queryOrdinal
                    + " arm=" + arm
                    + " failureStage=" + failureStage
                    + " errorCode=" + RANKING_ERROR;
        }
    }

    private record ArmRun(Object evidence, Attribution attribution) {
    }
}
