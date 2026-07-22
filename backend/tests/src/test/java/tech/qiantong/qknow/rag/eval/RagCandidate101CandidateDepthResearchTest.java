package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.service.rag.RagContextBuilder;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Development-only, qrel-invisible Candidate 10.1 depth experiment. */
class RagCandidate101CandidateDepthResearchTest {

    private static final String ENABLE_PROPERTY =
            "rag.eval.candidate10.candidate-depth-research";
    private static final String MARKER = "CANDIDATE101_CANDIDATE_DEPTH";
    private static final String SCHEMA =
            "candidate101-candidate-depth-research-v1";
    private static final String SCOPE =
            "post-fusion-content-only-colbert-snapshot";
    private static final String HYPOTHESIS =
            "qrel-invisible-admission-scan-60-to-90";
    private static final String RECOVERY_RANKING_SHA =
            "8b3e99be63b059a2522481934e317b3af18c3bd3e77b865ea6ce71e5e8ab2bf6";
    private static final String MECHANISM_SOURCE =
            "src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10MechanismFactorAttributionTest.java";
    private static final String MECHANISM_REPORT_PREFIX =
            "target/surefire-reports/";
    private static final Map<String, String> DIRECT_PREDECESSOR_SHAS = Map.of(
            "mechanismAttributionSource",
            "88244b012fbc606d6fb086d688a68f84dc1c7fbf2d9e105dd28452a84060e702",
            "mechanismAttempt020Xml",
            "d216c1c57adca58d424f02c72fe347d450e124bc064c8df586a51415e97a6c76",
            "mechanismAttempt020Txt",
            "d94953350c6410cc3b3d9e082bc8865dcb78a64aa369d0b50b40bfd139a465b8",
            "mechanismAttempt021Xml",
            "d35186e10e0f86e78381fdf23c12886c07f90f38a7deb1c2086f198b3dc4d5e8",
            "mechanismAttempt021Txt",
            "adee1fe0fc8fb5895f401bdeb02503c49a2382e22f101830591f250bb837184c");
    private static final String PROCEED =
            "PROCEED_TO_CANDIDATE_DEPTH_PRODUCTION_RED";
    private static final String STOP_DEPTH =
            "STOP_CANDIDATE_DEPTH_90_HYPOTHESIS_REJECTED";
    private static final String STOP_QUALITY =
            "STOP_CANDIDATE_DEPTH_QUALITY_GATE_REJECTED";

    private static final String SOURCE_LOCK_ERROR =
            "CANDIDATE101_DEPTH_SOURCE_LOCK_INVALID";
    private static final String COMMAND_ERROR =
            "CANDIDATE101_DEPTH_COMMAND_INVALID";
    private static final String FROZEN_ERROR =
            "CANDIDATE101_DEPTH_FROZEN_INPUT_INVALID";
    private static final String BASELINE_ERROR =
            "CANDIDATE101_DEPTH_BASELINE_BINDING_INVALID";
    private static final String RANKING_ERROR =
            "CANDIDATE101_DEPTH_RANKING_INVALID";
    private static final String ADMISSION_ERROR =
            "CANDIDATE101_DEPTH_ADMISSION_CONTRACT_INVALID";
    private static final String BUDGET_ERROR =
            "CANDIDATE101_DEPTH_BUDGET_INVALID";
    private static final String QREL_ERROR =
            "CANDIDATE101_DEPTH_QREL_ACCESS_INVALID";
    private static final String SAFETY_ERROR =
            "CANDIDATE101_DEPTH_SAFETY_INVALID";
    private static final String HARNESS_ERROR =
            "CANDIDATE101_DEPTH_HARNESS_INVALID";
    private static final String RUNTIME_ERROR =
            "CANDIDATE101_DEPTH_RUNTIME_INVALID";
    private static final Set<String> FIXED_ERRORS = Set.of(
            SOURCE_LOCK_ERROR, COMMAND_ERROR, FROZEN_ERROR, BASELINE_ERROR,
            RANKING_ERROR, ADMISSION_ERROR, BUDGET_ERROR, QREL_ERROR,
            SAFETY_ERROR, HARNESS_ERROR, RUNTIME_ERROR);

    private static final int BUSINESS_LIMIT = 30;
    private static final int BASELINE_MAX_RANK = 60;
    private static final int CANDIDATE_MAX_RANK = 90;
    private static final int EXPECTED_QUERIES = 40;
    private static final int EXPECTED_FAMILIES = 20;
    private static final int EXPECTED_SEGMENTS = 1_120;
    private static final int CANDIDATE3_TOP_K = 10;
    private static final Set<String> TARGET_SHAPES = Set.of(
            "numeric-token", "doc-prefix", "zero-padded", "han-punctuation");

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "contracts")
    void candidateDepthContracts() {
        String one = "document 101";
        String two = "document 101 and document 202";
        String three = "document 101 document 202 document 303";
        require(RagCandidate10DiagnosticSupport.identifierTerms(one).size() == 1,
                ADMISSION_ERROR);
        require(RagCandidate10DiagnosticSupport.identifierTerms(two).size() == 2,
                ADMISSION_ERROR);
        require(RagCandidate10DiagnosticSupport.identifierTerms(three).size() == 3,
                ADMISSION_ERROR);

        RagCandidate10DiagnosticSupport.Eligibility active = eligibility(true);
        require(CANDIDATE_MAX_RANK == BUSINESS_LIMIT * 3,
                ADMISSION_ERROR);
        for (int size : List.of(29, 30, 60, 61, 90, 91)) {
            RagCandidate10DiagnosticSupport.FullRankingSnapshot ranking =
                    syntheticRanking(size, Set.of(), false, false);
            requireUnchanged(admitAtMaxRank(
                    ranking, two, active, CANDIDATE_MAX_RANK));
            requireLegacyEquivalent(ranking, two, active);
        }
        requireAdmitted(31, syntheticRanking(91, Set.of(31), false, false),
                two, BASELINE_MAX_RANK);
        requireAdmitted(60, syntheticRanking(91, Set.of(60), false, false),
                two, BASELINE_MAX_RANK);
        requireUnchanged(admitAtMaxRank(
                syntheticRanking(91, Set.of(61), false, false), two,
                active, BASELINE_MAX_RANK));
        requireAdmitted(61, syntheticRanking(91, Set.of(61), false, false),
                two, CANDIDATE_MAX_RANK);
        requireAdmitted(61, syntheticRanking(91, Set.of(61), false, false),
                one, CANDIDATE_MAX_RANK);
        requireAdmitted(90, syntheticRanking(91, Set.of(90), false, false),
                two, CANDIDATE_MAX_RANK);
        requireUnchanged(admitAtMaxRank(
                syntheticRanking(91, Set.of(91), false, false), two,
                active, CANDIDATE_MAX_RANK));
        requireAdmitted(61,
                syntheticRanking(91, Set.of(61, 70), false, false), two,
                CANDIDATE_MAX_RANK);
        requireUnchanged(admitAtMaxRank(
                syntheticRanking(91, Set.of(61), true, false), two,
                active, CANDIDATE_MAX_RANK));
        requireUnchanged(admitAtMaxRank(
                syntheticRanking(91, Set.of(61), false, true), two,
                active, CANDIDATE_MAX_RANK));
        requireUnchanged(admitAtMaxRank(
                syntheticRanking(91, Set.of(61), false, false), "no numbers",
                active, CANDIDATE_MAX_RANK));
        requireUnchanged(admitAtMaxRank(
                syntheticRanking(91, Set.of(61), false, false), three,
                active, CANDIDATE_MAX_RANK));
        requireUnchanged(admitAtMaxRank(
                syntheticRanking(91, Set.of(61), false, false), two,
                eligibility(false), CANDIDATE_MAX_RANK));

        RagCandidate10DiagnosticSupport.FullRankingSnapshot matrix =
                syntheticRanking(91, Set.of(31, 60, 61, 90, 91), false, false);
        RagCandidate10DiagnosticSupport.AdmissionResult legacy =
                RagCandidate10DiagnosticSupport.admit(matrix, two, active);
        RagCandidate10DiagnosticSupport.AdmissionResult versioned =
                admitAtMaxRank(matrix, two, active, BASELINE_MAX_RANK);
        require(legacy.equals(versioned), ADMISSION_ERROR);
        for (LegacyCase value : List.of(
                new LegacyCase(matrix, two, active),
                new LegacyCase(
                        syntheticRanking(91, Set.of(60), false, false),
                        two, active),
                new LegacyCase(
                        syntheticRanking(91, Set.of(61), false, false),
                        two, active),
                new LegacyCase(
                        syntheticRanking(91, Set.of(31), true, false),
                        two, active),
                new LegacyCase(
                        syntheticRanking(91, Set.of(31), false, true),
                        two, active),
                new LegacyCase(
                        syntheticRanking(91, Set.of(31), false, false),
                        one, active),
                new LegacyCase(
                        syntheticRanking(91, Set.of(31), false, false),
                        three, active),
                new LegacyCase(
                        syntheticRanking(91, Set.of(31), false, false),
                        two, eligibility(false)))) {
            requireLegacyEquivalent(
                    value.ranking(), value.query(), value.eligibility());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "selection")
    void evaluatesSelectionCandidateDepth() {
        AttemptState state = new AttemptState();
        String marker;
        try {
            SelectionRun run = runSelection(state);
            marker = validMarker(run);
        } catch (Throwable failure) {
            marker = invalidMarker(state.access, classify(failure));
        }
        requireMarkerSchema(marker);
        System.out.println(marker);
    }

    private static SelectionRun runSelection(AttemptState state)
            throws ReflectiveOperationException {
        RagCandidate10FreezeSupport.RuntimePaths paths = publishedRuntimePaths();
        Map<String, String> before;
        try {
            before = lockedHashes(paths);
            invokeMechanism("requireExpectedHashes",
                    new Class<?>[]{Map.class}, before);
            requireDirectPredecessorHashes(before);
        } catch (RuntimeException | AssertionError failure) {
            throw invalid(SOURCE_LOCK_ERROR, failure);
        }

        RagCandidate10FreezeSupport.FrozenEvidence frozen;
        try {
            frozen = RagCandidate10FreezeSupport.openDiagnosticEvidence(paths);
            RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        } catch (RuntimeException failure) {
            throw invalid(SOURCE_LOCK_ERROR, failure);
        }
        try {
            invokeStage("requireFormalCommand");
            invokeStage("requireNativeDisabled");
        } catch (RuntimeException failure) {
            throw invalid(COMMAND_ERROR, failure);
        }

        Class<?> accessType = nestedStageClass("AccessCounter");
        Object access = newInstance(accessType);
        state.access = access;
        Object input = invokeStage("loadRankingInput", paths, frozen, access);
        requireAccess(access, false, 0);
        RagEvaluationDataset dataset =
                (RagEvaluationDataset) component(input, "dataset");
        @SuppressWarnings("unchecked")
        Map<String, RagCandidate10FixtureGenerator.FamilySpec> families =
                (Map<String, RagCandidate10FixtureGenerator.FamilySpec>)
                        component(input, "families");
        @SuppressWarnings("unchecked")
        List<RetrievalResult> pool =
                (List<RetrievalResult>) component(input, "pool");
        require(dataset.queries().size() == EXPECTED_QUERIES
                        && families.size() == EXPECTED_FAMILIES
                        && pool.size() == EXPECTED_SEGMENTS,
                FROZEN_ERROR);

        List<QueryDepth> rankings = new ArrayList<>(EXPECTED_QUERIES);
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            RagCandidate10FixtureGenerator.FamilySpec family =
                    families.get(query.familyId());
            require(family != null, FROZEN_ERROR);
            ArmEvidence baseline = runArm(
                    query.retrievalQuery(), pool, BASELINE_MAX_RANK);
            ArmEvidence candidate = runArm(
                    query.retrievalQuery(), pool, CANDIDATE_MAX_RANK);
            require(Objects.equals(baseline.fullRankingSha256(),
                            candidate.fullRankingSha256())
                            && baseline.fullRankingIds().equals(
                            candidate.fullRankingIds())
                            && baseline.queryTokenCount()
                            == candidate.queryTokenCount()
                            && baseline.documentTokenCount()
                            == candidate.documentTokenCount(),
                    RANKING_ERROR);
            rankings.add(new QueryDepth(
                    query, family.role().wireName(), family.shapeName(),
                    baseline, candidate));
        }
        requireAccess(access, false, 0);
        invokeInstance(access, "freezeRanking");
        requireAccess(access, true, 0);
        RagEvaluationDataset labeled = (RagEvaluationDataset) invokeStage(
                "loadQrelsAfterRanking", paths, frozen, access);
        requireAccess(access, true, 1);

        SelectionRun run = evaluate(dataset, labeled, rankings, access);
        RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        try {
            Map<String, String> after = lockedHashes(paths);
            require(before.equals(after), SOURCE_LOCK_ERROR);
        } catch (RuntimeException | AssertionError failure) {
            throw invalid(SOURCE_LOCK_ERROR, failure);
        }
        return run;
    }

    private static ArmEvidence runArm(
            String query, List<RetrievalResult> canonicalPool, int maxRank)
            throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<RetrievalResult> originals = (List<RetrievalResult>) invokeStage(
                "copyResults", canonicalPool);
        byte[] originalsBefore = resultBytes(originals);
        AtomicInteger externalEmbeddingCalls = new AtomicInteger();
        ColbertScorer scorer = (ColbertScorer) invokeStage(
                "scorer", externalEmbeddingCalls);
        DeterministicRerankerProvider deterministic =
                (DeterministicRerankerProvider) newInstance(
                        nestedStageClass("CountingDeterministicReranker"));
        RagRerankService service = new RagRerankService();
        ReflectionTestUtils.setField(service, "colbertScorer", scorer);
        ReflectionTestUtils.setField(
                service, "deterministicRerankerProvider", deterministic);
        ReflectionTestUtils.setField(
                service, "identifierConsistencyEnabled", true);

        require(RagFallbackMonitor.currentScope() == null, RANKING_ERROR);
        List<RetrievalResult> production;
        try (RagFallbackMonitor.Scope ignored = RagFallbackMonitor.openScope()) {
            production = ReflectionTestUtils.invokeMethod(
                    service, "colbertCoarseRerank",
                    query, originals, originals.size());
            invokeStage("requireNoScopedFallback",
                    RagFallbackMonitor.currentScopeSnapshot());
        }
        require(RagFallbackMonitor.currentScope() == null
                        && production != null,
                RANKING_ERROR);
        Object scorerAudit = invokeInstance(
                scorer, "audit", originals.size());
        require(externalEmbeddingCalls.get() == 0, BUDGET_ERROR);
        @SuppressWarnings("unchecked")
        List<Document> documents = (List<Document>) fieldValue(
                scorer, "rerankedDocuments");
        @SuppressWarnings("unchecked")
        List<RetrievalResult> oracle = (List<RetrievalResult>) invokeStage(
                "oracleResults", service, originals, documents);
        invokeStage("requireResultsEqual", oracle, production);
        RagCandidate10DiagnosticSupport.FullRankingSnapshot snapshot =
                RagCandidate10DiagnosticSupport.snapshotFullRanking(
                        originals, documents);
        invokeStage("requireSnapshotEqualsOracle", snapshot.rows(), oracle);
        String fullRankingSha = RagCandidate10FreezeSupport.sha256(
                canonical(snapshot.rows().stream().map(row -> {
                    try {
                        return invokeStage("snapshotView", row);
                    } catch (ReflectiveOperationException failure) {
                        throw invalid(HARNESS_ERROR, failure);
                    }
                }).toList()));

        RagCandidate10DiagnosticSupport.AdmissionResult admission =
                admitAtMaxRank(snapshot, query, eligibility(true), maxRank);
        List<RetrievalResult> beforeCandidate3 = new ArrayList<>(BUSINESS_LIMIT);
        for (RagCandidate10DiagnosticSupport.RetrievalSnapshot row
                : admission.candidate()) {
            beforeCandidate3.add((RetrievalResult) invokeStage(
                    "copyResult", oracle.get(row.fullRank() - 1)));
        }
        @SuppressWarnings("unchecked")
        List<RetrievalResult> sources = (List<RetrievalResult>)
                ReflectionTestUtils.invokeMethod(
                        service, "identifierConsistencyRerank",
                        RerankRequestContext.builder().query(query).build(),
                        invokeStage("copyResults", beforeCandidate3),
                        QueryIntent.builder().build(), CANDIDATE3_TOP_K);
        int deterministicCalls = (Integer) fieldValue(deterministic, "calls");
        require(sources != null && sources.size() <= CANDIDATE3_TOP_K
                        && deterministicCalls == 1,
                RANKING_ERROR);

        JdbcTemplate jdbc = (JdbcTemplate) newInstance(
                nestedStageClass("CountingJdbcTemplate"));
        RagContextBuilder contextBuilder = new RagContextBuilder();
        ReflectionTestUtils.setField(contextBuilder, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(contextBuilder, "maxContextBytes", 20_000);
        ReflectionTestUtils.setField(contextBuilder, "maxContextTokens", 0);
        @SuppressWarnings("unchecked")
        List<RetrievalResult> contextSources = (List<RetrievalResult>)
                invokeStage("copyResults", sources);
        String context = contextBuilder.buildContext(contextSources, true);
        int jdbcCalls = (Integer) fieldValue(jdbc, "calls");
        require(jdbcCalls == 1, RUNTIME_ERROR);
        @SuppressWarnings("unchecked")
        List<RetrievalResult> resultSources = (List<RetrievalResult>)
                invokeStage("copyResults", sources);
        RagResult result = RagResult.builder()
                .sources(resultSources)
                .context(Objects.requireNonNullElse(context, ""))
                .build();
        List<Long> sourceIds = result.getSources().stream()
                .map(RetrievalResult::getSegmentId).toList();
        @SuppressWarnings("unchecked")
        List<Long> contextIds = (List<Long>) invokeStage(
                "contextSegmentIds", result.getContext());
        require(Arrays.equals(originalsBefore, resultBytes(originals)),
                RANKING_ERROR);
        List<Long> fullIds = snapshot.rows().stream()
                .map(RagCandidate10DiagnosticSupport.RetrievalSnapshot::segmentId)
                .toList();
        return new ArmEvidence(
                fullRankingSha, fullIds, rankMap(fullIds),
                admission.admittedFullRank(), sourceIds, contextIds,
                intComponent(scorerAudit, "calls"),
                intComponent(scorerAudit, "requestedTopK"),
                intComponent(scorerAudit, "inputCount"),
                intComponent(scorerAudit, "outputCount"),
                longComponent(scorerAudit, "queryTokenCount"),
                longComponent(scorerAudit, "documentTokenCount"),
                1, 1, jdbcCalls, externalEmbeddingCalls.get(),
                0, 0, 0, 0, 0, 0,
                admission.candidate().size(), sources.size());
    }

    private static RagCandidate10DiagnosticSupport.AdmissionResult
    admitAtMaxRank(
            RagCandidate10DiagnosticSupport.FullRankingSnapshot ranking,
            String query,
            RagCandidate10DiagnosticSupport.Eligibility eligibility,
            int maxRank) {
        require(maxRank == BASELINE_MAX_RANK
                        || maxRank == CANDIDATE_MAX_RANK,
                ADMISSION_ERROR);
        RagCandidate10DiagnosticSupport.AdmissionResult legacy =
                RagCandidate10DiagnosticSupport.admit(
                        ranking, query, eligibility);
        if (maxRank == BASELINE_MAX_RANK || legacy.admitted()) {
            return legacy;
        }
        List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> rows =
                ranking.rows();
        List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> baseline =
                rows.subList(0, Math.min(BUSINESS_LIMIT, rows.size()));
        if (!eligibility.requestActive() || rows.size() <= BUSINESS_LIMIT) {
            return legacy;
        }
        List<String> identifiers =
                RagCandidate10DiagnosticSupport.identifierTerms(query);
        if (identifiers.isEmpty() || identifiers.size() > 2
                || baseline.stream().anyMatch(row ->
                RagCandidate10DiagnosticSupport.matchesAllIdentifiers(
                        row.documentName(), identifiers))
                || RagCandidate10DiagnosticSupport.matchesAnyIdentifier(
                rows.get(BUSINESS_LIMIT - 1).documentName(), identifiers)) {
            return legacy;
        }
        for (int index = BASELINE_MAX_RANK;
             index < Math.min(maxRank, rows.size()); index++) {
            RagCandidate10DiagnosticSupport.RetrievalSnapshot selected =
                    rows.get(index);
            if (RagCandidate10DiagnosticSupport.matchesAllIdentifiers(
                    selected.documentName(), identifiers)) {
                List<RagCandidate10DiagnosticSupport.RetrievalSnapshot>
                        candidate = new ArrayList<>(
                        rows.subList(0, BUSINESS_LIMIT - 1));
                candidate.add(selected);
                return new RagCandidate10DiagnosticSupport.AdmissionResult(
                        baseline, candidate, selected.fullRank());
            }
        }
        return legacy;
    }

    private static SelectionRun evaluate(
            RagEvaluationDataset dataset,
            RagEvaluationDataset labeled,
            List<QueryDepth> rankings,
            Object access) throws ReflectiveOperationException {
        List<QueryEvaluation> cases = new ArrayList<>(EXPECTED_QUERIES);
        int unsafeCount = 0;
        long encodedTokenDelta = 0;
        for (QueryDepth ranking : rankings) {
            Map<String, Integer> qrels = labeled.qrelsFor(ranking.query().id());
            Object legacyRanking = stageQueryRanking(
                    ranking, ranking.baseline(), ranking.baseline());
            boolean lockedMechanism = (Boolean) invokeStage(
                    "targetMechanism", dataset, legacyRanking, qrels);
            boolean versionedBaseline = versionedTargetMechanism(
                    dataset, ranking, ranking.baseline(), qrels,
                    BASELINE_MAX_RANK);
            require(lockedMechanism == versionedBaseline, BASELINE_ERROR);
            boolean candidateMechanism = versionedTargetMechanism(
                    dataset, ranking, ranking.candidate(), qrels,
                    CANDIDATE_MAX_RANK);
            Object mixedRanking = stageQueryRanking(
                    ranking, ranking.baseline(), ranking.candidate());
            boolean unsafe = (Boolean) invokeStage(
                    "unsafeOutput", dataset, mixedRanking, qrels);
            unsafeCount += unsafe ? 1 : 0;
            encodedTokenDelta += Math.abs(
                    ranking.baseline().queryTokenCount()
                            - ranking.candidate().queryTokenCount());
            encodedTokenDelta += Math.abs(
                    ranking.baseline().documentTokenCount()
                            - ranking.candidate().documentTokenCount());
            Metrics baseline = metrics(qrels, ranking.baseline().sourceIds());
            Metrics candidate = metrics(qrels, ranking.candidate().sourceIds());
            cases.add(new QueryEvaluation(
                    ranking.query().familyId(), ranking.role(),
                    versionedBaseline, candidateMechanism, unsafe,
                    baseline.ap(), baseline.ndcg(),
                    candidate.ap(), candidate.ndcg()));
        }
        require(unsafeCount == 0, SAFETY_ERROR);

        Metrics baseline = average(cases, false);
        Metrics candidate = average(cases, true);
        require(Double.compare(baseline.ap(), 0.4D) == 0
                        && Double.compare(baseline.ndcg(),
                        0.44525887710618334D) == 0,
                BASELINE_ERROR);
        Map<String, List<QueryEvaluation>> byFamily = cases.stream().collect(
                Collectors.groupingBy(QueryEvaluation::familyId,
                        LinkedHashMap::new, Collectors.toList()));
        require(byFamily.size() == EXPECTED_FAMILIES
                        && byFamily.values().stream().allMatch(v -> v.size() == 2),
                FROZEN_ERROR);
        long targetQueryCount = cases.stream()
                .filter(value -> "target".equals(value.role())).count();
        long targetFamilyCount = byFamily.values().stream()
                .filter(family -> family.stream().allMatch(
                        value -> "target".equals(value.role())))
                .count();
        Set<String> targetShapes = rankings.stream()
                .filter(value -> "target".equals(value.role()))
                .map(QueryDepth::shape)
                .collect(Collectors.toSet());
        require(targetQueryCount == 16 && targetFamilyCount == 8
                        && targetShapes.equals(TARGET_SHAPES),
                FROZEN_ERROR);
        boolean noFamilyRegression = true;
        int familyRegressionCount = 0;
        int targetFamilyStrictImprovementCount = 0;
        Map<String, Integer> mechanismCounts = new LinkedHashMap<>();
        mechanismCounts.put("NONE_TRUE", 0);
        mechanismCounts.put("ONE_TRUE", 0);
        mechanismCounts.put("BOTH_TRUE", 0);
        int baselineTargetMechanismTrueCount = 0;
        int baselineNoneTrueFamilyCount = 0;
        for (List<QueryEvaluation> family : byFamily.values()) {
            Metrics familyBaseline = average(family, false);
            Metrics familyCandidate = average(family, true);
            boolean nonRegressing = familyCandidate.ap() >= familyBaseline.ap()
                    && familyCandidate.ndcg() >= familyBaseline.ndcg();
            noFamilyRegression &= nonRegressing;
            familyRegressionCount += nonRegressing ? 0 : 1;
            if (family.stream().allMatch(value -> "target".equals(value.role()))) {
                long baselineTrueCount = family.stream()
                        .filter(QueryEvaluation::baselineMechanism).count();
                baselineTargetMechanismTrueCount += (int) baselineTrueCount;
                baselineNoneTrueFamilyCount += baselineTrueCount == 0 ? 1 : 0;
                long trueCount = family.stream()
                        .filter(QueryEvaluation::candidateMechanism).count();
                String key = trueCount == 0 ? "NONE_TRUE"
                        : trueCount == 1 ? "ONE_TRUE" : "BOTH_TRUE";
                mechanismCounts.put(key, mechanismCounts.get(key) + 1);
                if (familyCandidate.ap() > familyBaseline.ap()
                        && familyCandidate.ndcg() > familyBaseline.ndcg()) {
                    targetFamilyStrictImprovementCount++;
                }
            }
        }
        int targetMechanismTrueCount = (int) cases.stream()
                .filter(value -> "target".equals(value.role()))
                .filter(QueryEvaluation::candidateMechanism).count();
        require(baselineTargetMechanismTrueCount == 0
                        && baselineNoneTrueFamilyCount == 8,
                BASELINE_ERROR);
        require(mechanismCounts.values().stream().mapToInt(Integer::intValue)
                        .sum() == 8,
                FROZEN_ERROR);
        boolean aggregateStrict = candidate.ap() > baseline.ap()
                && candidate.ndcg() > baseline.ndcg();
        boolean budgetsValid = rankings.stream().allMatch(QueryDepth::budgetValid)
                && encodedTokenDelta == 0;
        require(budgetsValid, BUDGET_ERROR);
        String decision;
        if (targetMechanismTrueCount != 16
                || mechanismCounts.get("BOTH_TRUE") != 8) {
            decision = STOP_DEPTH;
        } else if (!aggregateStrict || !noFamilyRegression
                || targetFamilyStrictImprovementCount != 8) {
            decision = STOP_QUALITY;
        } else {
            decision = PROCEED;
        }
        List<String> baselineShas = rankings.stream()
                .map(value -> value.baseline().fullRankingSha256()).toList();
        List<String> candidateShas = rankings.stream()
                .map(value -> value.candidate().fullRankingSha256()).toList();
        String baselineHash = RagCandidate10FreezeSupport.sha256(
                canonical(baselineShas));
        String candidateHash = RagCandidate10FreezeSupport.sha256(
                canonical(candidateShas));
        require(baselineHash.equals(candidateHash), RANKING_ERROR);
        return new SelectionRun(
                access, List.copyOf(rankings), baseline, candidate,
                Map.copyOf(mechanismCounts), targetMechanismTrueCount,
                aggregateStrict, noFamilyRegression, familyRegressionCount,
                targetFamilyStrictImprovementCount, unsafeCount,
                encodedTokenDelta, baselineHash, candidateHash, decision);
    }

    private static boolean versionedTargetMechanism(
            RagEvaluationDataset dataset,
            QueryDepth ranking,
            ArmEvidence arm,
            Map<String, Integer> qrels,
            int maxRank) throws ReflectiveOperationException {
        if (!"target".equals(ranking.role())
                || !TARGET_SHAPES.contains(ranking.shape())) {
            return false;
        }
        List<String> identifiers =
                RagCandidate10DiagnosticSupport.identifierTerms(
                        ranking.query().retrievalQuery());
        if (identifiers.isEmpty() || identifiers.size() > 2
                || arm.admittedFullRank() == null) {
            return false;
        }
        Long rank30 = arm.fullRankingIds().get(BUSINESS_LIMIT - 1);
        RagEvaluationDataset.CorpusSegment rank30Segment =
                dataset.corpusById().get(String.valueOf(rank30));
        if (rank30Segment == null
                || RagCandidate10DiagnosticSupport.matchesAnyIdentifier(
                documentName(rank30Segment), identifiers)) {
            return false;
        }
        for (String relevant : qrels.keySet()) {
            RagEvaluationDataset.CorpusSegment segment =
                    dataset.corpusById().get(relevant);
            if (segment == null
                    || !RagCandidate10DiagnosticSupport.matchesAllIdentifiers(
                    documentName(segment), identifiers)) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(relevant);
            } catch (RuntimeException failure) {
                throw invalid(FROZEN_ERROR, failure);
            }
            Integer rank = arm.rankBySegmentId().get(id);
            if (rank != null && rank >= BUSINESS_LIMIT + 1 && rank <= maxRank
                    && rank.equals(arm.admittedFullRank())
                    && arm.sourceIds().contains(id)
                    && arm.contextIds().contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static Object stageQueryRanking(
            QueryDepth query, ArmEvidence baseline, ArmEvidence candidate)
            throws ReflectiveOperationException {
        Class<?> scorerType = nestedStageClass("ScorerAudit");
        Class<?> armType = nestedStageClass("ArmEvidence");
        Object baselineArm = stageArm(baseline, scorerType, armType);
        Object candidateArm = stageArm(candidate, scorerType, armType);
        Constructor<?> constructor = nestedStageClass("QueryRanking")
                .getDeclaredConstructor(
                        RagEvaluationDataset.QueryCase.class,
                        String.class, String.class, armType, armType);
        constructor.setAccessible(true);
        return construct(constructor, query.query(), query.role(), query.shape(),
                baselineArm, candidateArm);
    }

    private static Object stageArm(
            ArmEvidence arm, Class<?> scorerType, Class<?> armType)
            throws ReflectiveOperationException {
        Constructor<?> scorer = scorerType.getDeclaredConstructor(
                int.class, int.class, int.class, int.class,
                long.class, long.class);
        scorer.setAccessible(true);
        Object scorerValue = construct(scorer,
                arm.scorerCalls(), arm.requestedTopK(), arm.inputCount(),
                arm.outputCount(), arm.queryTokenCount(),
                arm.documentTokenCount());
        Constructor<?> evidence = armType.getDeclaredConstructor(
                String.class, List.class, Map.class, Integer.class,
                List.class, List.class, scorerType,
                int.class, int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class, int.class);
        evidence.setAccessible(true);
        return construct(evidence,
                arm.fullRankingSha256(), arm.fullRankingIds(),
                arm.rankBySegmentId(), arm.admittedFullRank(),
                arm.sourceIds(), arm.contextIds(), scorerValue,
                arm.candidate3Calls(), arm.contextCalls(),
                arm.internalJdbcCalls(), arm.externalEmbeddingCalls(),
                arm.externalDbCalls(), arm.externalVectorCalls(),
                arm.externalMetadataCalls(), arm.externalGraphCalls(),
                arm.externalNetworkCalls(), arm.externalLlmCalls());
    }

    private static String validMarker(SelectionRun run) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("schemaVersion", SCHEMA);
        marker.put("status", "VALID");
        marker.put("causalScope", SCOPE);
        marker.put("hypothesisId", HYPOTHESIS);
        marker.put("businessLimit", BUSINESS_LIMIT);
        marker.put("effectiveTopK", BUSINESS_LIMIT);
        marker.put("baselineScanMaxRank", BASELINE_MAX_RANK);
        marker.put("candidateScanMaxRank", CANDIDATE_MAX_RANK);
        marker.put("queryCount", EXPECTED_QUERIES);
        marker.put("familyCount", EXPECTED_FAMILIES);
        marker.put("targetQueryCount", 16);
        marker.put("targetFamilyCount", 8);
        marker.put("targetMechanismTrueCount",
                run.targetMechanismTrueCount());
        marker.put("familyMechanismCounts", run.familyMechanismCounts());
        marker.put("metrics", Map.of(
                "baselineApAt10", run.baseline().ap(),
                "baselineNdcgAt10", run.baseline().ndcg(),
                "candidateApAt10", run.candidate().ap(),
                "candidateNdcgAt10", run.candidate().ndcg()));
        marker.put("quality", Map.of(
                "aggregateStrictImprovement", run.aggregateStrictImprovement(),
                "allFamilyNonRegressing", run.allFamilyNonRegressing(),
                "familyRegressionCount", run.familyRegressionCount(),
                "targetFamilyStrictImprovementCount",
                run.targetFamilyStrictImprovementCount(),
                "unsafeOutputCount", run.unsafeOutputCount()));
        marker.put("budgets", budgetMap(run));
        marker.put("access", accessMap(run.access()));
        marker.put("recoveryRankingSha256", RECOVERY_RANKING_SHA);
        marker.put("baselineFullRankingSha256",
                run.baselineFullRankingSha256());
        marker.put("candidateFullRankingSha256",
                run.candidateFullRankingSha256());
        marker.put("decision", run.decision());
        marker.put("errorCode", null);
        return marker(marker);
    }

    private static Map<String, Object> budgetMap(SelectionRun run) {
        int arms = run.rankings().size() * 2;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("armCount", arms);
        value.put("scorerCalls", arms);
        value.put("candidate3Calls", arms);
        value.put("contextCalls", arms);
        value.put("internalJdbcCalls", arms);
        value.put("requestedTopKPerArm", EXPECTED_SEGMENTS);
        value.put("inputCountPerArm", EXPECTED_SEGMENTS);
        value.put("outputCountPerArm", EXPECTED_SEGMENTS);
        value.put("preCandidate3Capacity", BUSINESS_LIMIT);
        value.put("candidate3Capacity", CANDIDATE3_TOP_K);
        value.put("contextMaxBytes", 20_000);
        value.put("contextMaxTokens", 0);
        value.put("encodedTokenDelta", run.encodedTokenDelta());
        value.put("externalEmbeddingCalls", 0);
        value.put("externalDbCalls", 0);
        value.put("externalVectorCalls", 0);
        value.put("externalMetadataCalls", 0);
        value.put("externalGraphCalls", 0);
        value.put("externalNetworkCalls", 0);
        value.put("externalLlmCalls", 0);
        return value;
    }

    private static String invalidMarker(Object access, String errorCode) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("schemaVersion", SCHEMA);
        marker.put("status", "INVALID");
        marker.put("causalScope", SCOPE);
        marker.put("hypothesisId", HYPOTHESIS);
        marker.put("baselineScanMaxRank", BASELINE_MAX_RANK);
        marker.put("candidateScanMaxRank", CANDIDATE_MAX_RANK);
        marker.put("recoveryRankingSha256", RECOVERY_RANKING_SHA);
        marker.put("access", accessMap(access));
        marker.put("decision", null);
        marker.put("errorCode", errorCode);
        return marker(marker);
    }

    private static String marker(Map<String, Object> value) {
        return MARKER + " " + new String(canonical(value),
                StandardCharsets.UTF_8).trim();
    }

    private static void requireMarkerSchema(String marker) {
        String prefix = MARKER + " ";
        require(marker != null && marker.startsWith(prefix), HARNESS_ERROR);
        String json = marker.substring(prefix.length());
        JSONObject parsed;
        try {
            parsed = JSON.parseObject(json);
        } catch (RuntimeException failure) {
            throw invalid(HARNESS_ERROR, failure);
        }
        require(parsed != null && Arrays.equals(
                        (json + "\n").getBytes(StandardCharsets.UTF_8),
                        canonical(parsed)),
                HARNESS_ERROR);
        Set<String> validKeys = Set.of(
                "schemaVersion", "status", "causalScope", "hypothesisId",
                "businessLimit", "effectiveTopK", "baselineScanMaxRank",
                "candidateScanMaxRank", "queryCount", "familyCount",
                "targetQueryCount", "targetFamilyCount",
                "targetMechanismTrueCount", "familyMechanismCounts",
                "metrics", "quality", "budgets", "access",
                "recoveryRankingSha256", "baselineFullRankingSha256",
                "candidateFullRankingSha256", "decision", "errorCode");
        Set<String> invalidKeys = Set.of(
                "schemaVersion", "status", "causalScope", "hypothesisId",
                "baselineScanMaxRank", "candidateScanMaxRank",
                "recoveryRankingSha256", "access", "decision", "errorCode");
        requireString(parsed, "schemaVersion", SCHEMA);
        requireString(parsed, "causalScope", SCOPE);
        requireString(parsed, "hypothesisId", HYPOTHESIS);
        requireString(parsed, "recoveryRankingSha256", RECOVERY_RANKING_SHA);
        requireInteger(parsed, "baselineScanMaxRank", BASELINE_MAX_RANK);
        requireInteger(parsed, "candidateScanMaxRank", CANDIDATE_MAX_RANK);
        String status = stringValue(parsed, "status");
        if ("VALID".equals(status)) {
            require(parsed.keySet().equals(validKeys)
                            && parsed.get("errorCode") == null,
                    HARNESS_ERROR);
            requireValidMarker(parsed);
            return;
        }
        require("INVALID".equals(status)
                        && parsed.keySet().equals(invalidKeys)
                        && parsed.get("decision") == null
                        && FIXED_ERRORS.contains(stringValue(parsed, "errorCode")),
                HARNESS_ERROR);
        JSONObject access = exactObject(parsed, "access", Set.of(
                "selectionNonQrelResourceAccessCount",
                "qrelResourceAccessBeforeRanking",
                "qrelResourceAccessCount",
                "holdoutResourceAccessCount"));
        for (String key : access.keySet()) {
            require(integerValue(access, key) >= 0, HARNESS_ERROR);
        }
    }

    private static void requireValidMarker(JSONObject parsed) {
        requireInteger(parsed, "businessLimit", BUSINESS_LIMIT);
        requireInteger(parsed, "effectiveTopK", BUSINESS_LIMIT);
        requireInteger(parsed, "queryCount", EXPECTED_QUERIES);
        requireInteger(parsed, "familyCount", EXPECTED_FAMILIES);
        requireInteger(parsed, "targetQueryCount", 16);
        requireInteger(parsed, "targetFamilyCount", 8);
        int mechanismTrue = integerValue(
                parsed, "targetMechanismTrueCount");
        require(mechanismTrue >= 0 && mechanismTrue <= 16, HARNESS_ERROR);

        JSONObject familyCounts = exactObject(
                parsed, "familyMechanismCounts",
                Set.of("NONE_TRUE", "ONE_TRUE", "BOTH_TRUE"));
        int noneTrue = integerValue(familyCounts, "NONE_TRUE");
        int oneTrue = integerValue(familyCounts, "ONE_TRUE");
        int bothTrue = integerValue(familyCounts, "BOTH_TRUE");
        require(noneTrue >= 0 && oneTrue >= 0 && bothTrue >= 0
                        && noneTrue + oneTrue + bothTrue == 8
                        && mechanismTrue == oneTrue + 2 * bothTrue,
                HARNESS_ERROR);

        JSONObject metrics = exactObject(parsed, "metrics", Set.of(
                "baselineApAt10", "baselineNdcgAt10",
                "candidateApAt10", "candidateNdcgAt10"));
        double baselineAp = finiteNumber(metrics, "baselineApAt10");
        double baselineNdcg = finiteNumber(metrics, "baselineNdcgAt10");
        double candidateAp = finiteNumber(metrics, "candidateApAt10");
        double candidateNdcg = finiteNumber(metrics, "candidateNdcgAt10");
        require(Double.compare(baselineAp, 0.4D) == 0
                        && Double.compare(
                        baselineNdcg, 0.44525887710618334D) == 0,
                HARNESS_ERROR);

        JSONObject quality = exactObject(parsed, "quality", Set.of(
                "aggregateStrictImprovement", "allFamilyNonRegressing",
                "familyRegressionCount",
                "targetFamilyStrictImprovementCount", "unsafeOutputCount"));
        boolean aggregateStrict = booleanValue(
                quality, "aggregateStrictImprovement");
        boolean allFamilyNonRegressing = booleanValue(
                quality, "allFamilyNonRegressing");
        int familyRegressionCount = integerValue(
                quality, "familyRegressionCount");
        int targetFamilyStrictCount = integerValue(
                quality, "targetFamilyStrictImprovementCount");
        require(familyRegressionCount >= 0
                        && familyRegressionCount <= EXPECTED_FAMILIES
                        && targetFamilyStrictCount >= 0
                        && targetFamilyStrictCount <= 8
                        && allFamilyNonRegressing
                        == (familyRegressionCount == 0)
                        && aggregateStrict
                        == (candidateAp > baselineAp
                        && candidateNdcg > baselineNdcg),
                HARNESS_ERROR);
        requireInteger(quality, "unsafeOutputCount", 0);

        JSONObject budgets = exactObject(parsed, "budgets", Set.of(
                "armCount", "scorerCalls", "candidate3Calls",
                "contextCalls", "internalJdbcCalls", "requestedTopKPerArm",
                "inputCountPerArm", "outputCountPerArm",
                "preCandidate3Capacity", "candidate3Capacity",
                "contextMaxBytes", "contextMaxTokens", "encodedTokenDelta",
                "externalEmbeddingCalls", "externalDbCalls",
                "externalVectorCalls", "externalMetadataCalls",
                "externalGraphCalls", "externalNetworkCalls",
                "externalLlmCalls"));
        for (String key : List.of(
                "armCount", "scorerCalls", "candidate3Calls",
                "contextCalls", "internalJdbcCalls")) {
            requireInteger(budgets, key, 80);
        }
        for (String key : List.of(
                "requestedTopKPerArm", "inputCountPerArm",
                "outputCountPerArm")) {
            requireInteger(budgets, key, EXPECTED_SEGMENTS);
        }
        requireInteger(budgets, "preCandidate3Capacity", BUSINESS_LIMIT);
        requireInteger(budgets, "candidate3Capacity", CANDIDATE3_TOP_K);
        requireInteger(budgets, "contextMaxBytes", 20_000);
        requireInteger(budgets, "contextMaxTokens", 0);
        requireInteger(budgets, "encodedTokenDelta", 0);
        for (String key : List.of(
                "externalEmbeddingCalls", "externalDbCalls",
                "externalVectorCalls", "externalMetadataCalls",
                "externalGraphCalls", "externalNetworkCalls",
                "externalLlmCalls")) {
            requireInteger(budgets, key, 0);
        }

        JSONObject access = exactObject(parsed, "access", Set.of(
                "selectionNonQrelResourceAccessCount",
                "qrelResourceAccessBeforeRanking",
                "qrelResourceAccessCount", "holdoutResourceAccessCount"));
        requireInteger(access, "selectionNonQrelResourceAccessCount", 3);
        requireInteger(access, "qrelResourceAccessBeforeRanking", 0);
        requireInteger(access, "qrelResourceAccessCount", 1);
        requireInteger(access, "holdoutResourceAccessCount", 0);

        String baselineHash = stringValue(
                parsed, "baselineFullRankingSha256");
        String candidateHash = stringValue(
                parsed, "candidateFullRankingSha256");
        require(isSha256(baselineHash)
                        && baselineHash.equals(candidateHash),
                HARNESS_ERROR);
        String decision = stringValue(parsed, "decision");
        String expectedDecision;
        if (mechanismTrue != 16 || bothTrue != 8) {
            expectedDecision = STOP_DEPTH;
        } else if (!aggregateStrict || !allFamilyNonRegressing
                || targetFamilyStrictCount != 8) {
            expectedDecision = STOP_QUALITY;
        } else {
            expectedDecision = PROCEED;
        }
        require(expectedDecision.equals(decision), HARNESS_ERROR);
    }

    private static JSONObject exactObject(
            JSONObject owner, String key, Set<String> keys) {
        Object value = owner.get(key);
        require(value instanceof JSONObject, HARNESS_ERROR);
        JSONObject object = (JSONObject) value;
        require(object.keySet().equals(keys), HARNESS_ERROR);
        return object;
    }

    private static String stringValue(JSONObject object, String key) {
        Object value = object.get(key);
        require(value instanceof String, HARNESS_ERROR);
        return (String) value;
    }

    private static void requireString(
            JSONObject object, String key, String expected) {
        require(expected.equals(stringValue(object, key)), HARNESS_ERROR);
    }

    private static int integerValue(JSONObject object, String key) {
        Object value = object.get(key);
        require(value instanceof Integer, HARNESS_ERROR);
        return (Integer) value;
    }

    private static void requireInteger(
            JSONObject object, String key, int expected) {
        require(integerValue(object, key) == expected, HARNESS_ERROR);
    }

    private static double finiteNumber(JSONObject object, String key) {
        Object value = object.get(key);
        require(value instanceof Number, HARNESS_ERROR);
        double number = ((Number) value).doubleValue();
        require(Double.isFinite(number), HARNESS_ERROR);
        return number;
    }

    private static boolean booleanValue(JSONObject object, String key) {
        Object value = object.get(key);
        require(value instanceof Boolean, HARNESS_ERROR);
        return (Boolean) value;
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Map<String, Object> accessMap(Object access) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("selectionNonQrelResourceAccessCount",
                access == null ? 0 : quietField(access,
                        "selectionNonQrelResourceAccessCount"));
        value.put("qrelResourceAccessBeforeRanking",
                access == null ? 0 : quietField(access,
                        "qrelResourceAccessBeforeRanking"));
        value.put("qrelResourceAccessCount",
                access == null ? 0 : quietField(access,
                        "qrelResourceAccessCount"));
        value.put("holdoutResourceAccessCount",
                access == null ? 0 : quietField(access,
                        "holdoutResourceAccessCount"));
        return value;
    }

    private static Metrics metrics(
            Map<String, Integer> qrels, List<Long> sourceIds) {
        if (qrels.isEmpty()) {
            return new Metrics(0.0D, 0.0D);
        }
        RagMetrics.Scores scores = RagMetrics.evaluate(
                qrels, sourceIds.stream().map(String::valueOf).toList());
        return new Metrics(scores.retrievalApAt10(), scores.ndcgAt10());
    }

    private static Metrics average(
            List<QueryEvaluation> values, boolean candidate) {
        return new Metrics(
                values.stream().mapToDouble(value -> candidate
                        ? value.candidateAp() : value.baselineAp())
                        .average().orElseThrow(),
                values.stream().mapToDouble(value -> candidate
                        ? value.candidateNdcg() : value.baselineNdcg())
                        .average().orElseThrow());
    }

    private static String documentName(
            RagEvaluationDataset.CorpusSegment segment)
            throws ReflectiveOperationException {
        return (String) invokeStage("documentName", segment);
    }

    private static RagCandidate10DiagnosticSupport.FullRankingSnapshot
    syntheticRanking(
            int size, Set<Integer> exactRanks,
            boolean prefixExact, boolean rank30Any) {
        List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> rows =
                new ArrayList<>(size);
        for (int rank = 1; rank <= size; rank++) {
            String name = exactRanks.contains(rank)
                    || (prefixExact && rank == 1)
                    ? "manual 101 202"
                    : rank30Any && rank == BUSINESS_LIMIT
                    ? "manual 101" : "neutral-document";
            rows.add(new RagCandidate10DiagnosticSupport.RetrievalSnapshot(
                    rank, (long) rank, null, null, (long) rank,
                    name, "content", null, rank, "test", Map.of()));
        }
        return new RagCandidate10DiagnosticSupport.FullRankingSnapshot(rows);
    }

    private static void requireAdmitted(
            int expected,
            RagCandidate10DiagnosticSupport.FullRankingSnapshot ranking,
            String query, int maxRank) {
        RagCandidate10DiagnosticSupport.AdmissionResult result =
                admitAtMaxRank(ranking, query, eligibility(true), maxRank);
        require(Objects.equals(result.admittedFullRank(), expected)
                        && result.candidate().size() == BUSINESS_LIMIT
                        && result.candidate().get(BUSINESS_LIMIT - 1).fullRank()
                        == expected,
                ADMISSION_ERROR);
    }

    private static void requireUnchanged(
            RagCandidate10DiagnosticSupport.AdmissionResult result) {
        require(result.admittedFullRank() == null
                        && result.baseline().equals(result.candidate()),
                ADMISSION_ERROR);
    }

    private static void requireLegacyEquivalent(
            RagCandidate10DiagnosticSupport.FullRankingSnapshot ranking,
            String query,
            RagCandidate10DiagnosticSupport.Eligibility eligibility) {
        List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> before =
                List.copyOf(ranking.rows());
        RagCandidate10DiagnosticSupport.AdmissionResult legacy =
                RagCandidate10DiagnosticSupport.admit(
                        ranking, query, eligibility);
        RagCandidate10DiagnosticSupport.AdmissionResult versioned =
                admitAtMaxRank(ranking, query, eligibility, BASELINE_MAX_RANK);
        require(legacy.equals(versioned)
                        && ranking.rows().equals(before),
                ADMISSION_ERROR);
    }

    private static RagCandidate10DiagnosticSupport.Eligibility eligibility(
            boolean active) {
        return new RagCandidate10DiagnosticSupport.Eligibility(
                active, true, true);
    }

    private static Map<Long, Integer> rankMap(List<Long> ids) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            require(ids.get(index) != null
                            && result.putIfAbsent(ids.get(index), index + 1) == null,
                    RANKING_ERROR);
        }
        return Map.copyOf(result);
    }

    private static byte[] resultBytes(List<RetrievalResult> results)
            throws ReflectiveOperationException {
        List<Object> views = new ArrayList<>(results.size());
        for (RetrievalResult result : results) {
            views.add(invokeStage("resultView", result));
        }
        return canonical(views);
    }

    private static byte[] canonical(Object value) {
        return RagCandidate10FreezeSupport.canonicalJsonBytes(value);
    }

    private static RagCandidate10FreezeSupport.RuntimePaths
    publishedRuntimePaths() throws ReflectiveOperationException {
        Method method = RagCandidate10DiagnosticRecoveryV2Test.class
                .getDeclaredMethod("publishedRuntimePaths");
        method.setAccessible(true);
        return (RagCandidate10FreezeSupport.RuntimePaths)
                invoke(method, null);
    }

    private static Map<String, String> lockedHashes(
            RagCandidate10FreezeSupport.RuntimePaths paths)
            throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        Map<String, String> inherited = (Map<String, String>) invokeMechanism(
                "allowedHashes", new Class<?>[]{
                        RagCandidate10FreezeSupport.RuntimePaths.class}, paths);
        Map<String, String> hashes = new LinkedHashMap<>(inherited);
        Map<String, Path> direct = new LinkedHashMap<>();
        direct.put("mechanismAttributionSource",
                paths.testsDirectory().resolve(MECHANISM_SOURCE));
        direct.put("mechanismAttempt020Xml", paths.testsDirectory().resolve(
                MECHANISM_REPORT_PREFIX + "TEST-tech.qiantong.qknow.rag.eval."
                        + "RagCandidate10MechanismFactorAttributionTest-"
                        + "candidate10-mechanism-factor-attribution-"
                        + "attempt-020.xml"));
        direct.put("mechanismAttempt020Txt", paths.testsDirectory().resolve(
                MECHANISM_REPORT_PREFIX + "tech.qiantong.qknow.rag.eval."
                        + "RagCandidate10MechanismFactorAttributionTest-"
                        + "candidate10-mechanism-factor-attribution-"
                        + "attempt-020.txt"));
        direct.put("mechanismAttempt021Xml", paths.testsDirectory().resolve(
                MECHANISM_REPORT_PREFIX + "TEST-tech.qiantong.qknow.rag.eval."
                        + "RagCandidate10MechanismFactorAttributionTest-"
                        + "candidate10-mechanism-factor-attribution-"
                        + "attempt-021.xml"));
        direct.put("mechanismAttempt021Txt", paths.testsDirectory().resolve(
                MECHANISM_REPORT_PREFIX + "tech.qiantong.qknow.rag.eval."
                        + "RagCandidate10MechanismFactorAttributionTest-"
                        + "candidate10-mechanism-factor-attribution-"
                        + "attempt-021.txt"));
        direct.forEach((name, path) -> {
            require(path != null
                            && !Files.isSymbolicLink(path)
                            && Files.isRegularFile(
                            path, LinkOption.NOFOLLOW_LINKS),
                    SOURCE_LOCK_ERROR);
            hashes.put(name, RagCandidate10FreezeSupport.sha256(path));
        });
        snapshotMechanismReports(paths.testsDirectory(), hashes);
        return Map.copyOf(hashes);
    }

    private static void snapshotMechanismReports(
            Path testsDirectory, Map<String, String> hashes) {
        Path reports = testsDirectory.resolve("target/surefire-reports");
        try (var stream = Files.list(reports)) {
            stream.filter(path -> {
                        String name = path.getFileName().toString();
                        return (name.startsWith(
                                "TEST-tech.qiantong.qknow.rag.eval."
                                        + "RagCandidate10MechanismFactor"
                                        + "AttributionTest-")
                                || name.startsWith(
                                "tech.qiantong.qknow.rag.eval."
                                        + "RagCandidate10MechanismFactor"
                                        + "AttributionTest-"))
                                && (name.endsWith(".xml")
                                || name.endsWith(".txt"));
                    })
                    .sorted()
                    .forEach(path -> {
                        require(!Files.isSymbolicLink(path)
                                        && Files.isRegularFile(
                                        path, LinkOption.NOFOLLOW_LINKS),
                                SOURCE_LOCK_ERROR);
                        hashes.put("mechanismReport:" + path.getFileName(),
                                RagCandidate10FreezeSupport.sha256(path));
                    });
        } catch (DepthInvalid failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalid(SOURCE_LOCK_ERROR, failure);
        }
    }

    private static void requireDirectPredecessorHashes(
            Map<String, String> hashes) {
        DIRECT_PREDECESSOR_SHAS.forEach((name, expected) ->
                require(Objects.equals(hashes.get(name), expected),
                        SOURCE_LOCK_ERROR));
    }

    private static Object invokeMechanism(
            String name, Class<?>[] types, Object... arguments)
            throws ReflectiveOperationException {
        Method method = RagCandidate10MechanismFactorAttributionTest.class
                .getDeclaredMethod(name, types);
        method.setAccessible(true);
        return invoke(method, null, arguments);
    }

    private static Object invokeStage(String name, Object... arguments)
            throws ReflectiveOperationException {
        Method selected = null;
        for (Method method
                : RagCandidate10DiagnosticStageSupport.class.getDeclaredMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == arguments.length) {
                if (selected != null) {
                    throw invalid(HARNESS_ERROR);
                }
                selected = method;
            }
        }
        if (selected == null) {
            throw invalid(HARNESS_ERROR);
        }
        selected.setAccessible(true);
        return invoke(selected, null, arguments);
    }

    private static Object invokeInstance(
            Object target, String name, Object... arguments)
            throws ReflectiveOperationException {
        Method selected = null;
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == arguments.length) {
                selected = method;
                break;
            }
        }
        if (selected == null) {
            throw invalid(HARNESS_ERROR);
        }
        selected.setAccessible(true);
        return invoke(selected, target, arguments);
    }

    private static Object invoke(
            Method method, Object target, Object... arguments)
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
            throw invalid(HARNESS_ERROR, cause);
        }
    }

    private static Object component(Object target, String name)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return invoke(method, target);
    }

    private static int intComponent(Object target, String name)
            throws ReflectiveOperationException {
        return (Integer) component(target, name);
    }

    private static long longComponent(Object target, String name)
            throws ReflectiveOperationException {
        return (Long) component(target, name);
    }

    private static Class<?> nestedStageClass(String simpleName)
            throws ClassNotFoundException {
        return Class.forName(RagCandidate10DiagnosticStageSupport.class
                .getName() + "$" + simpleName);
    }

    private static Object newInstance(Class<?> type)
            throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return construct(constructor);
    }

    private static Object construct(
            Constructor<?> constructor, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException failure) {
            throw invalid(HARNESS_ERROR, failure.getCause());
        }
    }

    private static Object fieldValue(Object target, String name)
            throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw invalid(HARNESS_ERROR);
    }

    private static Object quietField(Object target, String name) {
        try {
            return fieldValue(target, name);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return 0;
        }
    }

    private static void requireAccess(
            Object access, boolean frozen, int qrelCount)
            throws ReflectiveOperationException {
        require(Objects.equals(fieldValue(access, "rankingFrozen"), frozen)
                        && Objects.equals(fieldValue(access,
                        "selectionNonQrelResourceAccessCount"), 3)
                        && Objects.equals(fieldValue(access,
                        "qrelResourceAccessBeforeRanking"), 0)
                        && Objects.equals(fieldValue(access,
                        "qrelResourceAccessCount"), qrelCount)
                        && Objects.equals(fieldValue(access,
                        "holdoutResourceAccessCount"), 0),
                QREL_ERROR);
    }

    private static String classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof DepthInvalid invalid) {
                return invalid.errorCode;
            }
            String message = current.getMessage();
            if ("CANDIDATE10_SOURCE_LOCK_INVALID".equals(message)) {
                return SOURCE_LOCK_ERROR;
            }
            if ("CANDIDATE10_CONFIG_INVALID".equals(message)) {
                return COMMAND_ERROR;
            }
            if ("CANDIDATE10_FIXTURE_INVALID".equals(message)) {
                return FROZEN_ERROR;
            }
            if ("CANDIDATE10_RANKING_INVALID".equals(message)) {
                return RANKING_ERROR;
            }
            if ("CANDIDATE10_QREL_ACCESS_INVALID".equals(message)) {
                return QREL_ERROR;
            }
            if ("CANDIDATE10_RUNTIME_INVALID".equals(message)) {
                return RUNTIME_ERROR;
            }
            current = current.getCause();
        }
        return HARNESS_ERROR;
    }

    private static void require(boolean condition, String errorCode) {
        if (!condition) {
            throw invalid(errorCode);
        }
    }

    private static DepthInvalid invalid(String errorCode) {
        return new DepthInvalid(errorCode, null);
    }

    private static DepthInvalid invalid(String errorCode, Throwable cause) {
        return new DepthInvalid(errorCode, cause);
    }

    private record Metrics(double ap, double ndcg) {
    }

    private record QueryEvaluation(
            String familyId,
            String role,
            boolean baselineMechanism,
            boolean candidateMechanism,
            boolean unsafe,
            double baselineAp,
            double baselineNdcg,
            double candidateAp,
            double candidateNdcg) {
    }

    private record ArmEvidence(
            String fullRankingSha256,
            List<Long> fullRankingIds,
            Map<Long, Integer> rankBySegmentId,
            Integer admittedFullRank,
            List<Long> sourceIds,
            List<Long> contextIds,
            int scorerCalls,
            int requestedTopK,
            int inputCount,
            int outputCount,
            long queryTokenCount,
            long documentTokenCount,
            int candidate3Calls,
            int contextCalls,
            int internalJdbcCalls,
            int externalEmbeddingCalls,
            int externalDbCalls,
            int externalVectorCalls,
            int externalMetadataCalls,
            int externalGraphCalls,
            int externalNetworkCalls,
            int externalLlmCalls,
            int preCandidate3Count,
            int candidate3Count) {

        private boolean budgetValid() {
            return scorerCalls == 1
                    && requestedTopK == EXPECTED_SEGMENTS
                    && inputCount == EXPECTED_SEGMENTS
                    && outputCount == EXPECTED_SEGMENTS
                    && candidate3Calls == 1
                    && contextCalls == 1
                    && internalJdbcCalls == 1
                    && externalEmbeddingCalls == 0
                    && externalDbCalls == 0
                    && externalVectorCalls == 0
                    && externalMetadataCalls == 0
                    && externalGraphCalls == 0
                    && externalNetworkCalls == 0
                    && externalLlmCalls == 0
                    && preCandidate3Count == BUSINESS_LIMIT
                    && candidate3Count <= CANDIDATE3_TOP_K;
        }
    }

    private record QueryDepth(
            RagEvaluationDataset.QueryCase query,
            String role,
            String shape,
            ArmEvidence baseline,
            ArmEvidence candidate) {

        private boolean budgetValid() {
            return baseline.budgetValid()
                    && candidate.budgetValid()
                    && baseline.queryTokenCount()
                    == candidate.queryTokenCount()
                    && baseline.documentTokenCount()
                    == candidate.documentTokenCount();
        }
    }

    private record LegacyCase(
            RagCandidate10DiagnosticSupport.FullRankingSnapshot ranking,
            String query,
            RagCandidate10DiagnosticSupport.Eligibility eligibility) {
    }

    private record SelectionRun(
            Object access,
            List<QueryDepth> rankings,
            Metrics baseline,
            Metrics candidate,
            Map<String, Integer> familyMechanismCounts,
            int targetMechanismTrueCount,
            boolean aggregateStrictImprovement,
            boolean allFamilyNonRegressing,
            int familyRegressionCount,
            int targetFamilyStrictImprovementCount,
            int unsafeOutputCount,
            long encodedTokenDelta,
            String baselineFullRankingSha256,
            String candidateFullRankingSha256,
            String decision) {
    }

    private static final class AttemptState {
        private Object access;
    }

    private static final class DepthInvalid extends RuntimeException {
        private final String errorCode;

        private DepthInvalid(String errorCode, Throwable cause) {
            super(errorCode, cause);
            if (!FIXED_ERRORS.contains(errorCode)) {
                throw new IllegalArgumentException("unknown depth error");
            }
            this.errorCode = errorCode;
        }
    }
}
