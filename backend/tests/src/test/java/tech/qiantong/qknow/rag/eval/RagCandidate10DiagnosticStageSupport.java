package tech.qiantong.qknow.rag.eval;

import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.ai.service.IEmbeddingService;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.service.rag.RagContextBuilder;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertNative;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class RagCandidate10DiagnosticStageSupport {

    static final String CAUSAL_SCOPE =
            "post-fusion-content-only-colbert-snapshot";
    static final String PROCEED_DECISION =
            "PROCEED_TO_CANDIDATE10_PRODUCTION_RED";
    static final String STOP_DECISION =
            "STOP_BOUNDED_EXACT_WINDOW_TAIL_ADMISSION_UNSUPPORTED";

    static final String SOURCE_LOCK_ERROR = "CANDIDATE10_SOURCE_LOCK_INVALID";
    static final String CONFIG_ERROR = "CANDIDATE10_CONFIG_INVALID";
    static final String FIXTURE_ERROR = "CANDIDATE10_FIXTURE_INVALID";
    static final String RANKING_ERROR = "CANDIDATE10_RANKING_INVALID";
    static final String QREL_ACCESS_ERROR = "CANDIDATE10_QREL_ACCESS_INVALID";
    static final String RUNTIME_ERROR = "CANDIDATE10_RUNTIME_INVALID";

    private static final String SCHEMA_VERSION = "candidate10-diagnostic-v1";
    private static final int EXPECTED_QUERIES = 40;
    private static final int EXPECTED_SEGMENTS = 1120;
    private static final int BUSINESS_TOP_K = 10;
    private static final int FULL_PREFIX = 30;
    private static final int WINDOW_END = 60;
    private static final Pattern CONTEXT_SEGMENT =
            Pattern.compile("segmentId=(\\d+)");
    private static final Set<String> TARGET_SHAPES = Set.of(
            "numeric-token", "doc-prefix", "zero-padded", "han-punctuation");
    private static final Set<String> QREL_BEARING_ROLES = Set.of(
            "target", "baseline-present", "no-ID", "multi-ID",
            "relevant-nonexact", "long-token", "non-corroborated-exact");
    private static final Set<String> FIXED_ERRORS = Set.of(
            SOURCE_LOCK_ERROR, CONFIG_ERROR, FIXTURE_ERROR, RANKING_ERROR,
            QREL_ACCESS_ERROR, RUNTIME_ERROR);
    private static final Set<String> FORBIDDEN_ARTIFACT_KEYS = Set.of(
            "case", "cases", "query", "queryId", "family", "familyId",
            "identifier", "identifiers", "segmentId", "documentId",
            "documentName", "content", "qrel", "qrels", "qrelGrade",
            "grade", "sql", "sqlParameters", "exception", "message",
            "stackTrace");
    private static final Map<String, String> FORMAL_PROPERTIES = Map.ofEntries(
            Map.entry("rag.eval.candidate10.freeze", "false"),
            Map.entry("rag.eval.candidate10.diagnostic", "true"),
            Map.entry("rag.eval.candidate10.diagnostic-arm", "true"),
            Map.entry("rag.eval.candidate10.environment-qualification", "false"),
            Map.entry("rag.eval.shadow", "false"),
            Map.entry("rag.eval.shadow.compare-stable", "false"),
            Map.entry("rag.eval.identifier.diagnostic", "false"),
            Map.entry("rag.eval.candidate2.diagnostic", "false"),
            Map.entry("rag.eval.candidate3.diagnostic", "false"),
            Map.entry("rag.eval.candidate4.diagnostic", "false"),
            Map.entry("rag.eval.candidate5.diagnostic", "false"),
            Map.entry("rag.eval.candidate6.diagnostic", "false"),
            Map.entry("rag.eval.candidate8.diagnostic", "false"),
            Map.entry("rag.eval.candidate9.diagnostic", "false"),
            Map.entry("rag.eval.candidate9.recovery", "false"),
            Map.entry("rag.eval.promotion", "false"),
            Map.entry("rag.eval.live", "false"),
            Map.entry("qknow.rag.keyword.identifier-aware", "false"),
            Map.entry("qknow.rag.rerank.identifier-consistency-enabled", "true"),
            Map.entry("qknow.rag.local-reranker.enabled", "false"),
            Map.entry("qknow.rag.onnx-reranker.enabled", "false"),
            Map.entry("hermes.rag.colbert.enabled", "true"),
            Map.entry("hermes.rag.colbert.ngram-size", "3"),
            Map.entry("hermes.rag.colbert.dimensions", "64"),
            Map.entry("hermes.rag.colbert.max-tokens-per-doc", "128"),
            Map.entry("hermes.rag.colbert.embedding-platform", ""),
            Map.entry("hermes.rag.colbert.embedding-base-url", ""),
            Map.entry("hermes.rag.colbert.embedding-api-key", ""),
            Map.entry("hermes.rag.colbert.embedding-model", ""),
            Map.entry("hermes.rag.context.max-bytes", "20000"),
            Map.entry("hermes.rag.context.max-tokens", "0"));
    private static final String QUALIFIED_JAVA_HOME =
            "/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home";

    private RagCandidate10DiagnosticStageSupport() {
    }

    static DiagnosticResult runFormalSelectionDiagnostic() {
        return runSelectionDiagnostic(
                RagCandidate10FreezeSupport.formalPaths(), true);
    }

    static DiagnosticResult runSelectionDiagnostic(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        return runSelectionDiagnostic(paths, false);
    }

    static DiagnosticResult runVersionedSelectionDiagnostic(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        return runSelectionDiagnostic(paths, true);
    }

    private static DiagnosticResult runSelectionDiagnostic(
            RagCandidate10FreezeSupport.RuntimePaths paths,
            boolean formal) {
        Objects.requireNonNull(paths, "paths");
        requireFreshRun(paths);
        AccessCounter access = new AccessCounter();
        RunState run = begin(paths);
        RagCandidate10FreezeSupport.FrozenEvidence frozen = null;
        try {
            if (formal) {
                requireFormalCommand();
            }
            frozen = openFrozenEvidence(paths);
            requireNativeDisabled();
            RankingInput input = loadRankingInput(paths, frozen, access);
            List<QueryRanking> rankings = rankSelection(input, access);
            requireSourceLock(frozen);

            String rankingSha256 = sha256(canonicalBytes(
                    rankings.stream().map(QueryRanking::hashView).toList()));
            access.freezeRanking();
            run.advance(Phase.RANKING_FROZEN, Map.of(
                    "datasetHash", frozen.selectionDatasetHash(),
                    "configHash", frozen.configHash(),
                    "sourceLockSha256", frozen.sourceLockSha256(),
                    "selectionManifestSha256", frozen.selectionManifestSha256(),
                    "rankingSha256", rankingSha256,
                    "qrelResourceAccessBeforeRanking",
                    access.qrelResourceAccessBeforeRanking,
                    "holdoutResourceAccessCount",
                    access.holdoutResourceAccessCount));

            RagEvaluationDataset labeled = loadQrelsAfterRanking(
                    paths, frozen, access);
            run.advance(Phase.QRELS_LOADED, Map.of(
                    "qrelResourceAccessBeforeRanking",
                    access.qrelResourceAccessBeforeRanking,
                    "qrelResourceAccessCount", access.qrelResourceAccessCount,
                    "holdoutResourceAccessCount",
                    access.holdoutResourceAccessCount));

            EvaluationSummary evaluation = evaluate(
                    input, labeled, rankings);
            String decision = decide(evaluation.decisionEvidence());
            requireSourceLock(frozen);
            Map<String, Object> artifact = validArtifact(
                    frozen, rankingSha256, access, evaluation, decision);
            return publishAndComplete(run, artifact);
        } catch (RuntimeException | LinkageError failure) {
            if (Files.exists(paths.diagnostic(), LinkOption.NOFOLLOW_LINKS)
                    || run.phase == Phase.COMPLETED) {
                throw failure;
            }
            String errorCode = classify(failure);
            Map<String, Object> artifact = invalidArtifact(
                    frozen, run.phase, access, errorCode);
            return publishAndComplete(run, artifact);
        }
    }

    private static RagCandidate10FreezeSupport.FrozenEvidence openFrozenEvidence(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        try {
            return RagCandidate10FreezeSupport.openDiagnosticEvidence(paths);
        } catch (RuntimeException failure) {
            throw fail(SOURCE_LOCK_ERROR, failure);
        }
    }

    private static void requireSourceLock(
            RagCandidate10FreezeSupport.FrozenEvidence frozen) {
        try {
            RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
        } catch (RuntimeException failure) {
            throw fail(SOURCE_LOCK_ERROR, failure);
        }
    }

    private static RankingInput loadRankingInput(
            RagCandidate10FreezeSupport.RuntimePaths paths,
            RagCandidate10FreezeSupport.FrozenEvidence frozen,
            AccessCounter access) {
        RagCandidate10FixtureGenerator.RankingFixture generated =
                RagCandidate10FixtureGenerator.rankingView(
                        RagCandidate10FixtureGenerator.Split.SELECTION);
        for (String logical : List.of("corpus", "queries", "pressure")) {
            RagCandidate10FixtureGenerator.Resource expected =
                    generated.resource(logical);
            RagCandidate10FreezeSupport.ResourceBinding binding =
                    frozen.selectionResources().get(logical);
            if (binding == null
                    || !expected.fileName().equals(binding.file())
                    || !expected.sha256().equals(binding.sha256())) {
                throw fail(FIXTURE_ERROR);
            }
            byte[] actual = readSelection(
                    paths.selectionDirectory().resolve(binding.file()),
                    false, access);
            if (!Arrays.equals(expected.bytes(), actual)
                    || !expected.sha256().equals(sha256(actual))) {
                throw fail(FIXTURE_ERROR);
            }
        }

        RagEvaluationDataset dataset = generated.dataset();
        if (dataset.queries().size() != EXPECTED_QUERIES
                || dataset.corpusById().size() != EXPECTED_SEGMENTS
                || !dataset.qrels().isEmpty()
                || access.selectionNonQrelResourceAccessCount != 3
                || access.qrelResourceAccessCount != 0
                || access.holdoutResourceAccessCount != 0) {
            throw fail(FIXTURE_ERROR);
        }
        Map<String, RagCandidate10FixtureGenerator.FamilySpec> families =
                generated.families().stream().collect(Collectors.toMap(
                        RagCandidate10FixtureGenerator.FamilySpec::familyId,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (families.size() != 20) {
            throw fail(FIXTURE_ERROR);
        }
        List<RetrievalResult> pool = dataset.corpusById().values().stream()
                .sorted(Comparator.comparingLong(segment ->
                        parseLong(segment.segmentId(), FIXTURE_ERROR)))
                .map(RagCandidate10DiagnosticStageSupport::toOriginal)
                .toList();
        return new RankingInput(dataset, Map.copyOf(families), pool);
    }

    private static RagEvaluationDataset loadQrelsAfterRanking(
            RagCandidate10FreezeSupport.RuntimePaths paths,
            RagCandidate10FreezeSupport.FrozenEvidence frozen,
            AccessCounter access) {
        if (!access.rankingFrozen
                || access.qrelResourceAccessBeforeRanking != 0
                || access.qrelResourceAccessCount != 0
                || access.holdoutResourceAccessCount != 0) {
            throw fail(QREL_ACCESS_ERROR);
        }
        RagCandidate10FixtureGenerator.GeneratedSplit generated =
                RagCandidate10FixtureGenerator.selection();
        RagCandidate10FixtureGenerator.Resource expected =
                generated.resource("qrels");
        RagCandidate10FreezeSupport.ResourceBinding binding =
                frozen.selectionResources().get("qrels");
        if (binding == null
                || !expected.fileName().equals(binding.file())
                || !expected.sha256().equals(binding.sha256())) {
            throw fail(FIXTURE_ERROR);
        }
        byte[] actual = readSelection(
                paths.selectionDirectory().resolve(binding.file()), true, access);
        if (!Arrays.equals(expected.bytes(), actual)
                || !expected.sha256().equals(sha256(actual))
                || !generated.datasetHash().equals(frozen.selectionDatasetHash())
                || access.qrelResourceAccessCount != 1
                || access.holdoutResourceAccessCount != 0) {
            throw fail(QREL_ACCESS_ERROR);
        }
        RagEvaluationDataset dataset = generated.dataset();
        RagEvaluationDatasetLoader.validate(dataset);
        return dataset;
    }

    private static byte[] readSelection(
            Path path, boolean qrel, AccessCounter access) {
        requireRegular(path, qrel ? QREL_ACCESS_ERROR : FIXTURE_ERROR);
        access.selection(qrel);
        try {
            return Files.readAllBytes(path);
        } catch (IOException failure) {
            throw fail(qrel ? QREL_ACCESS_ERROR : FIXTURE_ERROR, failure);
        }
    }

    private static List<QueryRanking> rankSelection(
            RankingInput input, AccessCounter access) {
        if (access.qrelResourceAccessCount != 0
                || access.holdoutResourceAccessCount != 0) {
            throw fail(QREL_ACCESS_ERROR);
        }
        List<QueryRanking> rankings = new ArrayList<>(EXPECTED_QUERIES);
        for (RagEvaluationDataset.QueryCase query : input.dataset.queries()) {
            RagCandidate10FixtureGenerator.FamilySpec family =
                    input.families.get(query.familyId());
            if (family == null) {
                throw fail(FIXTURE_ERROR);
            }
            ArmEvidence baseline = runArm(
                    query.retrievalQuery(), input.pool, false);
            ArmEvidence candidate = runArm(
                    query.retrievalQuery(), input.pool, true);
            if (!baseline.fullRankingSha256.equals(
                    candidate.fullRankingSha256)
                    || baseline.scorer.queryTokenCount
                    != candidate.scorer.queryTokenCount
                    || baseline.scorer.documentTokenCount
                    != candidate.scorer.documentTokenCount) {
                throw fail(RANKING_ERROR);
            }
            rankings.add(new QueryRanking(
                    query, family.role().wireName(), family.shapeName(),
                    baseline, candidate));
        }
        if (rankings.size() != EXPECTED_QUERIES) {
            throw fail(FIXTURE_ERROR);
        }
        return List.copyOf(rankings);
    }

    private static ArmEvidence runArm(
            String query,
            List<RetrievalResult> canonicalPool,
            boolean candidateArm) {
        List<RetrievalResult> originals = copyResults(canonicalPool);
        byte[] originalsBefore = canonicalBytes(
                originals.stream().map(
                        RagCandidate10DiagnosticStageSupport::resultView).toList());

        AtomicInteger externalEmbeddingCalls = new AtomicInteger();
        CapturingColbertScorer scorer = scorer(externalEmbeddingCalls);
        CountingDeterministicReranker deterministic =
                new CountingDeterministicReranker();
        RagRerankService service = new RagRerankService();
        ReflectionTestUtils.setField(service, "colbertScorer", scorer);
        ReflectionTestUtils.setField(service,
                "deterministicRerankerProvider", deterministic);
        ReflectionTestUtils.setField(service,
                "identifierConsistencyEnabled", true);

        if (RagFallbackMonitor.currentScope() != null) {
            throw fail(RANKING_ERROR);
        }
        List<RetrievalResult> production;
        try (RagFallbackMonitor.Scope ignored = RagFallbackMonitor.openScope()) {
            production = ReflectionTestUtils.invokeMethod(
                    service, "colbertCoarseRerank",
                    query, originals, originals.size());
            requireNoScopedFallback(
                    RagFallbackMonitor.currentScopeSnapshot());
        }
        if (RagFallbackMonitor.currentScope() != null || production == null) {
            throw fail(RANKING_ERROR);
        }
        ScorerAudit scorerAudit = scorer.audit(originals.size());
        if (externalEmbeddingCalls.get() != 0) {
            throw fail(CONFIG_ERROR);
        }

        List<RetrievalResult> oracle = oracleResults(
                service, originals, scorer.rerankedDocuments);
        requireResultsEqual(oracle, production);
        RagCandidate10DiagnosticSupport.FullRankingSnapshot snapshot =
                RagCandidate10DiagnosticSupport.snapshotFullRanking(
                        originals, scorer.rerankedDocuments);
        requireSnapshotEqualsOracle(snapshot.rows(), oracle);
        String fullRankingSha256 = sha256(canonicalBytes(
                snapshot.rows().stream().map(
                        RagCandidate10DiagnosticStageSupport::snapshotView).toList()));

        RagCandidate10DiagnosticSupport.AdmissionResult admission =
                RagCandidate10DiagnosticSupport.admit(
                        snapshot,
                        query,
                        new RagCandidate10DiagnosticSupport.Eligibility(
                                candidateArm, true, true));
        List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> selected =
                candidateArm ? admission.candidate() : admission.baseline();
        List<RetrievalResult> beforeCandidate3 = selected.stream()
                .map(row -> oracle.get(row.fullRank() - 1))
                .map(RagCandidate10DiagnosticStageSupport::copyResult)
                .toList();
        List<RetrievalResult> sources = ReflectionTestUtils.invokeMethod(
                service,
                "identifierConsistencyRerank",
                RerankRequestContext.builder().query(query).build(),
                copyResults(beforeCandidate3),
                QueryIntent.builder().build(),
                BUSINESS_TOP_K);
        if (sources == null || sources.size() > BUSINESS_TOP_K
                || deterministic.calls != 1) {
            throw fail(RANKING_ERROR);
        }

        CountingJdbcTemplate jdbc = new CountingJdbcTemplate();
        RagContextBuilder contextBuilder = new RagContextBuilder();
        ReflectionTestUtils.setField(contextBuilder, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(contextBuilder, "maxContextBytes", 20_000);
        ReflectionTestUtils.setField(contextBuilder, "maxContextTokens", 0);
        String context = contextBuilder.buildContext(copyResults(sources), true);
        if (jdbc.calls != 1) {
            throw fail(RUNTIME_ERROR);
        }
        RagResult result = RagResult.builder()
                .sources(copyResults(sources))
                .context(Objects.requireNonNullElse(context, ""))
                .build();
        List<Long> sourceIds = result.getSources().stream()
                .map(RetrievalResult::getSegmentId).toList();
        List<Long> contextIds = contextSegmentIds(result.getContext());
        if (!Arrays.equals(originalsBefore, canonicalBytes(
                originals.stream().map(
                        RagCandidate10DiagnosticStageSupport::resultView).toList()))) {
            throw fail(RANKING_ERROR);
        }
        List<Long> fullRankingIds = snapshot.rows().stream()
                .map(RagCandidate10DiagnosticSupport.RetrievalSnapshot::segmentId)
                .toList();
        return new ArmEvidence(
                fullRankingSha256,
                fullRankingIds,
                rankMap(fullRankingIds),
                admission.admittedFullRank(),
                sourceIds,
                contextIds,
                scorerAudit,
                1,
                1,
                jdbc.calls,
                externalEmbeddingCalls.get(),
                0, 0, 0, 0, 0, 0);
    }

    private static CapturingColbertScorer scorer(
            AtomicInteger externalEmbeddingCalls) {
        ColbertScorer.ColbertConfig config = new ColbertScorer.ColbertConfig();
        config.setEnabled(true);
        config.setNgramSize(3);
        config.setDimensions(64);
        config.setMaxTokensPerDoc(128);
        config.setEmbeddingPlatform("");
        config.setEmbeddingBaseUrl("");
        config.setEmbeddingApiKey("");
        config.setEmbeddingModel("");
        InvocationHandler forbidden = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "candidate10-forbidden-embedding";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            externalEmbeddingCalls.incrementAndGet();
            throw fail(CONFIG_ERROR);
        };
        IEmbeddingService embedding = (IEmbeddingService) Proxy.newProxyInstance(
                IEmbeddingService.class.getClassLoader(),
                new Class<?>[]{IEmbeddingService.class}, forbidden);
        return new CapturingColbertScorer(config, embedding);
    }

    private static List<RetrievalResult> oracleResults(
            RagRerankService service,
            List<RetrievalResult> originals,
            List<Document> documents) {
        Map<Long, RetrievalResult> originalsById = new LinkedHashMap<>();
        for (RetrievalResult original : originals) {
            if (original == null || original.getSegmentId() == null
                    || originalsById.putIfAbsent(
                    original.getSegmentId(), original) != null) {
                throw fail(RANKING_ERROR);
            }
        }
        List<RetrievalResult> results = new ArrayList<>(documents.size());
        for (Document document : documents) {
            RetrievalResult result = ReflectionTestUtils.invokeMethod(
                    service, "toRetrievalResult", document, originalsById);
            if (result == null) {
                throw fail(RANKING_ERROR);
            }
            results.add(result);
        }
        return List.copyOf(results);
    }

    private static void requireResultsEqual(
            List<RetrievalResult> expected,
            List<RetrievalResult> actual) {
        if (expected.size() != actual.size()) {
            throw fail(RANKING_ERROR);
        }
        for (int index = 0; index < expected.size(); index++) {
            RetrievalResult left = expected.get(index);
            RetrievalResult right = actual.get(index);
            if (!sameResult(left, right)) {
                throw fail(RANKING_ERROR);
            }
        }
    }

    private static void requireSnapshotEqualsOracle(
            List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> snapshots,
            List<RetrievalResult> oracle) {
        if (snapshots.size() != oracle.size()) {
            throw fail(RANKING_ERROR);
        }
        for (int index = 0; index < snapshots.size(); index++) {
            RagCandidate10DiagnosticSupport.RetrievalSnapshot row =
                    snapshots.get(index);
            RetrievalResult result = oracle.get(index);
            if (row.fullRank() != index + 1
                    || !Objects.equals(row.segmentId(), result.getSegmentId())
                    || !Objects.equals(row.qmSegmentId(), result.getQmSegmentId())
                    || !Objects.equals(row.parentSegmentId(),
                    result.getParentSegmentId())
                    || !Objects.equals(row.documentId(), result.getDocumentId())
                    || !Objects.equals(row.documentName(), result.getDocumentName())
                    || !Objects.equals(row.content(), result.getContent())
                    || !Objects.equals(row.answer(), result.getAnswer())
                    || Double.compare(row.score(), result.getScore()) != 0
                    || !Objects.equals(row.source(), result.getSource())
                    || !sameMetadata(row.metadata(), result.getMetadata())) {
                throw fail(RANKING_ERROR);
            }
        }
    }

    private static EvaluationSummary evaluate(
            RankingInput input,
            RagEvaluationDataset labeled,
            List<QueryRanking> rankings) {
        List<QueryEvaluation> cases = new ArrayList<>(rankings.size());
        for (QueryRanking ranking : rankings) {
            Map<String, Integer> qrels = labeled.qrelsFor(
                    ranking.query.id());
            boolean qrelBearing = QREL_BEARING_ROLES.contains(ranking.role);
            if (qrels.isEmpty() == qrelBearing) {
                throw fail(FIXTURE_ERROR);
            }
            Metrics baseline = qrels.isEmpty()
                    ? new Metrics(0.0D, 0.0D)
                    : metrics(RagMetrics.evaluate(
                    qrels, strings(ranking.baseline.sourceIds)));
            Metrics candidate = qrels.isEmpty()
                    ? new Metrics(0.0D, 0.0D)
                    : metrics(RagMetrics.evaluate(
                    qrels, strings(ranking.candidate.sourceIds)));
            boolean mechanism = targetMechanism(
                    input.dataset, ranking, qrels);
            boolean unsafe = unsafeOutput(
                    input.dataset, ranking, qrels);
            cases.add(new QueryEvaluation(
                    ranking.query.familyId(), ranking.query.language(),
                    ranking.role, ranking.shape, mechanism, unsafe,
                    baseline.ap(), baseline.ndcg(),
                    candidate.ap(), candidate.ndcg()));
        }

        Map<String, List<QueryEvaluation>> byFamily = cases.stream()
                .collect(Collectors.groupingBy(
                        QueryEvaluation::familyId,
                        LinkedHashMap::new, Collectors.toList()));
        int qualifyingFamilies = 0;
        Set<String> coveredShapes = new LinkedHashSet<>();
        boolean qualifyingStrict = true;
        boolean noFamilyRegression = true;
        for (List<QueryEvaluation> family : byFamily.values()) {
            Metrics baseline = average(family, false);
            Metrics candidate = average(family, true);
            noFamilyRegression &= candidate.ap >= baseline.ap
                    && candidate.ndcg >= baseline.ndcg;
            boolean qualifying = family.size() == 2
                    && family.stream().allMatch(QueryEvaluation::mechanism)
                    && family.stream().map(QueryEvaluation::language)
                    .collect(Collectors.toSet()).equals(Set.of("en", "zh"));
            if (qualifying) {
                qualifyingFamilies++;
                coveredShapes.add(family.get(0).shape);
                qualifyingStrict &= candidate.ap > baseline.ap
                        && candidate.ndcg > baseline.ndcg;
            }
        }
        Metrics aggregateBaseline = average(cases, false);
        Metrics aggregateCandidate = average(cases, true);
        boolean aggregateStrict = aggregateCandidate.ap > aggregateBaseline.ap
                && aggregateCandidate.ndcg > aggregateBaseline.ndcg;
        boolean noUnsafe = cases.stream().noneMatch(QueryEvaluation::unsafe);
        boolean budgetsValid = rankings.stream().allMatch(QueryRanking::budgetValid);
        DecisionEvidence decision = new DecisionEvidence(
                coveredShapes.equals(TARGET_SHAPES),
                qualifyingFamilies > 0,
                qualifyingStrict,
                aggregateStrict,
                noFamilyRegression,
                noUnsafe,
                budgetsValid);
        return new EvaluationSummary(
                List.copyOf(cases), qualifyingFamilies,
                aggregateBaseline, aggregateCandidate, decision);
    }

    private static boolean targetMechanism(
            RagEvaluationDataset dataset,
            QueryRanking ranking,
            Map<String, Integer> qrels) {
        if (!"target".equals(ranking.role)
                || !TARGET_SHAPES.contains(ranking.shape)) {
            return false;
        }
        List<String> identifiers =
                RagCandidate10DiagnosticSupport.identifierTerms(
                        ranking.query.retrievalQuery());
        if (identifiers.isEmpty() || identifiers.size() > 2
                || ranking.candidate.admittedFullRank == null) {
            return false;
        }
        Long rank30 = ranking.baseline.fullRankingIds.get(FULL_PREFIX - 1);
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
            Long id = parseLong(relevant, FIXTURE_ERROR);
            Integer rank = ranking.candidate.rankBySegmentId.get(id);
            if (rank != null && rank >= FULL_PREFIX + 1 && rank <= WINDOW_END
                    && rank.equals(ranking.candidate.admittedFullRank)
                    && ranking.candidate.sourceIds.contains(id)
                    && ranking.candidate.contextIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean unsafeOutput(
            RagEvaluationDataset dataset,
            QueryRanking ranking,
            Map<String, Integer> qrels) {
        List<String> identifiers =
                RagCandidate10DiagnosticSupport.identifierTerms(
                        ranking.query.retrievalQuery());
        Set<Long> outputs = new LinkedHashSet<>();
        outputs.addAll(ranking.baseline.sourceIds);
        outputs.addAll(ranking.baseline.contextIds);
        outputs.addAll(ranking.candidate.sourceIds);
        outputs.addAll(ranking.candidate.contextIds);
        for (Long id : outputs) {
            RagEvaluationDataset.CorpusSegment segment =
                    dataset.corpusById().get(String.valueOf(id));
            if (segment == null) {
                return true;
            }
            String name = documentName(segment);
            String role = Objects.toString(
                    segment.metadata().get("candidate10Role"), "");
            boolean relevant = qrels.containsKey(String.valueOf(id));
            boolean any = RagCandidate10DiagnosticSupport
                    .matchesAnyIdentifier(name, identifiers);
            boolean all = RagCandidate10DiagnosticSupport
                    .matchesAllIdentifiers(name, identifiers);
            if ((!relevant && any)
                    || "semantic-near-lure-core".equals(role)
                    || "boundary-core".equals(role)
                    || "zero-padding-core".equals(role)
                    || ("multi-ID-core".equals(role) && any && !all)) {
                return true;
            }
        }
        return false;
    }

    static String decide(DecisionEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return evidence.allShapesCovered
                && evidence.hasQualifyingFamilies
                && evidence.qualifyingFamiliesStrictlyImprove
                && evidence.aggregateStrictlyImproves
                && evidence.noFamilyRegression
                && evidence.noUnsafeOutput
                && evidence.budgetsValid
                ? PROCEED_DECISION : STOP_DECISION;
    }

    static void requireNoScopedFallback(Map<String, ?> scopedFallbacks) {
        if (scopedFallbacks == null || !scopedFallbacks.isEmpty()) {
            throw fail(RANKING_ERROR);
        }
    }

    static boolean validTransition(Phase current, Phase next) {
        if (current == null || next == null || current == Phase.COMPLETED) {
            return false;
        }
        if (next == Phase.COMPLETED) {
            return true;
        }
        return current == Phase.RUNNING && next == Phase.RANKING_FROZEN
                || current == Phase.RANKING_FROZEN
                && next == Phase.QRELS_LOADED;
    }

    private static Map<String, Object> validArtifact(
            RagCandidate10FreezeSupport.FrozenEvidence frozen,
            String rankingSha256,
            AccessCounter access,
            EvaluationSummary evaluation,
            String decision) {
        Map<String, Object> artifact = baseArtifact(frozen, access);
        artifact.put("validity", "VALID");
        artifact.put("rankingSha256", rankingSha256);
        artifact.put("counts", Map.of(
                "queryCount", evaluation.cases.size(),
                "armCount", evaluation.cases.size() * 2,
                "qualifyingFamilyCount", evaluation.qualifyingFamilies));
        artifact.put("budgets", budgetSummary(evaluation.cases.size()));
        artifact.put("roleSummary", aggregateSummary(
                evaluation.cases, QueryEvaluation::role));
        artifact.put("shapeSummary", aggregateSummary(
                evaluation.cases.stream()
                        .filter(value -> "target".equals(value.role)).toList(),
                QueryEvaluation::shape));
        artifact.put("metrics", Map.of(
                "baselineApAt10", evaluation.aggregateBaseline.ap,
                "baselineNdcgAt10", evaluation.aggregateBaseline.ndcg,
                "candidateApAt10", evaluation.aggregateCandidate.ap,
                "candidateNdcgAt10", evaluation.aggregateCandidate.ndcg));
        artifact.put("decision", decision);
        artifact.put("errorCode", null);
        validateSanitizedArtifact(artifact);
        return artifact;
    }

    private static Map<String, Object> invalidArtifact(
            RagCandidate10FreezeSupport.FrozenEvidence frozen,
            Phase failedPhase,
            AccessCounter access,
            String errorCode) {
        Map<String, Object> artifact = baseArtifact(frozen, access);
        artifact.put("validity", "INVALID");
        artifact.put("failedPhase", failedPhase.name());
        artifact.put("decision", null);
        artifact.put("errorCode", requireErrorCode(errorCode));
        validateSanitizedArtifact(artifact);
        return artifact;
    }

    private static Map<String, Object> baseArtifact(
            RagCandidate10FreezeSupport.FrozenEvidence frozen,
            AccessCounter access) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("schemaVersion", SCHEMA_VERSION);
        artifact.put("causalScope", CAUSAL_SCOPE);
        artifact.put("datasetHash", frozen == null
                ? "UNAVAILABLE" : frozen.selectionDatasetHash());
        artifact.put("configHash", frozen == null
                ? "UNAVAILABLE" : frozen.configHash());
        artifact.put("sourceLockSha256", frozen == null
                ? "UNAVAILABLE" : frozen.sourceLockSha256());
        artifact.put("selectionManifestSha256", frozen == null
                ? "UNAVAILABLE" : frozen.selectionManifestSha256());
        artifact.put("access", Map.of(
                "qrelResourceAccessBeforeRanking",
                access.qrelResourceAccessBeforeRanking,
                "qrelResourceAccessCount", access.qrelResourceAccessCount,
                "holdoutResourceAccessCount",
                access.holdoutResourceAccessCount));
        return artifact;
    }

    static void validateSanitizedArtifact(Map<String, ?> artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String validity = Objects.toString(artifact.get("validity"), "");
        if (!Set.of("VALID", "INVALID").contains(validity)
                || !SCHEMA_VERSION.equals(artifact.get("schemaVersion"))
                || !CAUSAL_SCOPE.equals(artifact.get("causalScope"))
                || !artifact.containsKey("decision")
                || !artifact.containsKey("errorCode")) {
            throw fail(RUNTIME_ERROR);
        }
        rejectForbiddenFields(artifact);
        Object decision = artifact.get("decision");
        Object errorCode = artifact.get("errorCode");
        if ("INVALID".equals(validity)) {
            if (artifact.containsKey("counts")
                    || artifact.containsKey("budgets")
                    || artifact.containsKey("metrics")
                    || artifact.containsKey("roleSummary")
                    || artifact.containsKey("shapeSummary")
                    || decision != null) {
                throw fail(RUNTIME_ERROR);
            }
            if (!(errorCode instanceof String invalidErrorCode)
                    || invalidErrorCode.isEmpty()
                    || !FIXED_ERRORS.contains(invalidErrorCode)) {
                throw fail(RUNTIME_ERROR);
            }
        } else if (!artifact.containsKey("metrics")
                || !(PROCEED_DECISION.equals(decision)
                        || STOP_DECISION.equals(decision))
                || errorCode != null) {
            throw fail(RUNTIME_ERROR);
        }
    }

    private static void rejectForbiddenFields(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = Objects.toString(entry.getKey(), "");
                if (FORBIDDEN_ARTIFACT_KEYS.contains(key)) {
                    throw fail(RUNTIME_ERROR);
                }
                rejectForbiddenFields(entry.getValue());
            }
        } else if (value instanceof Iterable<?> values) {
            values.forEach(
                    RagCandidate10DiagnosticStageSupport::rejectForbiddenFields);
        }
    }

    private static List<Map<String, Object>> aggregateSummary(
            List<QueryEvaluation> cases,
            java.util.function.Function<QueryEvaluation, String> classifier) {
        Map<String, List<QueryEvaluation>> grouped = cases.stream()
                .collect(Collectors.groupingBy(
                        classifier, TreeMap::new, Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<QueryEvaluation>> entry : grouped.entrySet()) {
            Metrics baseline = average(entry.getValue(), false);
            Metrics candidate = average(entry.getValue(), true);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("group", entry.getKey());
            row.put("queryCount", entry.getValue().size());
            row.put("baselineApAt10", baseline.ap);
            row.put("baselineNdcgAt10", baseline.ndcg);
            row.put("candidateApAt10", candidate.ap);
            row.put("candidateNdcgAt10", candidate.ndcg);
            row.put("nonDegrading",
                    candidate.ap >= baseline.ap
                            && candidate.ndcg >= baseline.ndcg);
            result.add(Map.copyOf(row));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> budgetSummary(int queryCount) {
        int arms = queryCount * 2;
        return Map.ofEntries(
                Map.entry("scorerCalls", arms),
                Map.entry("candidate3Calls", arms),
                Map.entry("contextCalls", arms),
                Map.entry("internalJdbcCalls", arms),
                Map.entry("encodedTokenDelta", 0),
                Map.entry("externalDbCalls", 0),
                Map.entry("externalVectorCalls", 0),
                Map.entry("externalMetadataCalls", 0),
                Map.entry("externalGraphCalls", 0),
                Map.entry("externalNetworkCalls", 0),
                Map.entry("externalLlmCalls", 0));
    }

    private static DiagnosticResult publishAndComplete(
            RunState run,
            Map<String, Object> artifact) {
        validateSanitizedArtifact(artifact);
        boolean valid = "VALID".equals(artifact.get("validity"));
        String decision = (String) artifact.get("decision");
        String errorCode = (String) artifact.get("errorCode");
        byte[] artifactBytes = canonicalBytes(artifact);
        atomicCreate(run.paths.diagnostic(), artifactBytes,
                RUNTIME_ERROR);
        requireBytes(run.paths.diagnostic(), artifactBytes, RUNTIME_ERROR);
        String artifactSha256 = sha256(artifactBytes);
        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("diagnosticSha256", artifactSha256);
        completed.put("validity", artifact.get("validity"));
        completed.put("decision", decision);
        completed.put("errorCode", errorCode);
        run.advance(Phase.COMPLETED, completed);
        return new DiagnosticResult(
                valid,
                decision,
                errorCode,
                run.paths.diagnostic(),
                artifactSha256,
                run.paths.ledger(),
                sha256(run.lastLedgerBytes),
                artifact);
    }

    private static RunState begin(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        requireDirectory(paths.freezeDirectory(), SOURCE_LOCK_ERROR);
        requireRegular(paths.sourceLock(), SOURCE_LOCK_ERROR);
        requireAbsent(paths.ledger(), RUNTIME_ERROR);
        requireAbsent(paths.diagnostic(), RUNTIME_ERROR);
        RunState run = new RunState(paths);
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("schemaVersion", SCHEMA_VERSION);
        ledger.put("causalScope", CAUSAL_SCOPE);
        ledger.put("status", Phase.RUNNING.name());
        byte[] bytes = canonicalBytes(ledger);
        atomicCreate(paths.ledger(), bytes, RUNTIME_ERROR);
        requireBytes(paths.ledger(), bytes, RUNTIME_ERROR);
        run.lastLedgerBytes = bytes;
        return run;
    }

    private static void requireFreshRun(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        requireAbsent(paths.ledger(), RUNTIME_ERROR);
        requireAbsent(paths.diagnostic(), RUNTIME_ERROR);
        requireAbsent(paths.diagnostic().resolveSibling(
                "." + paths.diagnostic().getFileName() + ".tmp"),
                RUNTIME_ERROR);
        requireAbsent(paths.ledger().resolveSibling(
                "." + paths.ledger().getFileName() + ".tmp"),
                RUNTIME_ERROR);
    }

    private static void requireFormalCommand() {
        for (Map.Entry<String, String> expected : FORMAL_PROPERTIES.entrySet()) {
            if (!expected.getValue().equals(
                    System.getProperty(expected.getKey()))) {
                throw fail(CONFIG_ERROR);
            }
        }
        if (!QUALIFIED_JAVA_HOME.equals(System.getProperty("java.home"))
                || !"17.0.19".equals(System.getProperty("java.version"))
                || !"17.0.19+10".equals(
                System.getProperty("java.runtime.version"))
                || !"Eclipse Adoptium".equals(
                System.getProperty("java.vendor"))
                || !"aarch64".equals(System.getProperty("os.arch"))
                || !"UTF-8".equalsIgnoreCase(
                System.getProperty("file.encoding"))
                || !"UTC".equals(System.getProperty("user.timezone"))
                || !"en".equals(System.getProperty("user.language"))
                || !"US".equals(System.getProperty("user.country"))
                || !"".equals(System.getProperty("user.script", ""))
                || !"".equals(System.getProperty("user.variant", ""))
                || !"".equals(System.getProperty("qknow.native.lib.dir", ""))
                || !System.getProperty("java.library.path", "").endsWith(
                "/backend/tests/target/rag-eval/no-native")) {
            throw fail(CONFIG_ERROR);
        }
        Locale expected = Locale.forLanguageTag("en-US");
        if (!expected.equals(Locale.getDefault())) {
            throw fail(CONFIG_ERROR);
        }
        boolean candidate10ProductionPropertyPresent = System.getProperties()
                .stringPropertyNames().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.startsWith("qknow.")
                        && value.contains("candidate10"));
        if (candidate10ProductionPropertyPresent) {
            throw fail(CONFIG_ERROR);
        }
    }

    private static void requireNativeDisabled() {
        if (RagFallbackMonitor.currentScope() != null
                || ColbertNative.isAvailable()) {
            throw fail(CONFIG_ERROR);
        }
    }

    private static RetrievalResult toOriginal(
            RagEvaluationDataset.CorpusSegment segment) {
        Object score = segment.metadata().get("score");
        Object source = segment.metadata().get("source");
        Object name = segment.metadata().get("documentName");
        if (!(score instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || !(source instanceof String sourceValue)
                || !(name instanceof String documentName)) {
            throw fail(FIXTURE_ERROR);
        }
        return RetrievalResult.builder()
                .segmentId(parseLong(segment.segmentId(), FIXTURE_ERROR))
                .qmSegmentId(null)
                .parentSegmentId(segment.parentSegmentId())
                .documentId(parseLong(segment.documentId(), FIXTURE_ERROR))
                .documentName(documentName)
                .content(segment.content())
                .answer(null)
                .score(number.doubleValue())
                .source(sourceValue)
                .metadata(new LinkedHashMap<>(segment.metadata()))
                .build();
    }

    private static List<Long> contextSegmentIds(String context) {
        List<Long> ids = new ArrayList<>();
        Matcher matcher = CONTEXT_SEGMENT.matcher(
                Objects.requireNonNullElse(context, ""));
        while (matcher.find()) {
            ids.add(parseLong(matcher.group(1), RUNTIME_ERROR));
        }
        return List.copyOf(ids);
    }

    private static Map<Long, Integer> rankMap(List<Long> ids) {
        Map<Long, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            if (ids.get(index) == null
                    || ranks.putIfAbsent(ids.get(index), index + 1) != null) {
                throw fail(RANKING_ERROR);
            }
        }
        return Map.copyOf(ranks);
    }

    private static String documentName(
            RagEvaluationDataset.CorpusSegment segment) {
        Object value = segment.metadata().get("documentName");
        if (!(value instanceof String name)) {
            throw fail(FIXTURE_ERROR);
        }
        return name;
    }

    private static Metrics average(
            List<QueryEvaluation> values, boolean candidate) {
        if (values.isEmpty()) {
            return new Metrics(0.0D, 0.0D);
        }
        return new Metrics(
                values.stream().mapToDouble(value -> candidate
                        ? value.candidateAp : value.baselineAp).average()
                        .orElseThrow(),
                values.stream().mapToDouble(value -> candidate
                        ? value.candidateNdcg : value.baselineNdcg).average()
                        .orElseThrow());
    }

    private static Metrics metrics(RagMetrics.Scores scores) {
        return new Metrics(scores.retrievalApAt10(), scores.ndcgAt10());
    }

    private static List<String> strings(List<Long> values) {
        return values.stream().map(String::valueOf).toList();
    }

    private static Map<String, Object> resultView(RetrievalResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("segmentId", result.getSegmentId());
        value.put("qmSegmentId", result.getQmSegmentId());
        value.put("parentSegmentId", result.getParentSegmentId());
        value.put("documentId", result.getDocumentId());
        value.put("documentName", result.getDocumentName());
        value.put("content", result.getContent());
        value.put("answer", result.getAnswer());
        value.put("score", result.getScore());
        value.put("source", result.getSource());
        value.put("metadata", result.getMetadata() == null
                ? null : new LinkedHashMap<>(result.getMetadata()));
        return value;
    }

    private static Map<String, Object> snapshotView(
            RagCandidate10DiagnosticSupport.RetrievalSnapshot row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("rank", row.fullRank());
        value.put("segmentId", row.segmentId());
        value.put("qmSegmentId", row.qmSegmentId());
        value.put("parentSegmentId", row.parentSegmentId());
        value.put("documentId", row.documentId());
        value.put("documentName", row.documentName());
        value.put("content", row.content());
        value.put("answer", row.answer());
        value.put("score", row.score());
        value.put("source", row.source());
        value.put("metadata", new LinkedHashMap<>(row.metadata()));
        return value;
    }

    private static boolean sameResult(
            RetrievalResult left, RetrievalResult right) {
        return Objects.equals(left.getSegmentId(), right.getSegmentId())
                && Objects.equals(left.getQmSegmentId(), right.getQmSegmentId())
                && Objects.equals(left.getParentSegmentId(),
                right.getParentSegmentId())
                && Objects.equals(left.getDocumentId(), right.getDocumentId())
                && Objects.equals(left.getDocumentName(), right.getDocumentName())
                && Objects.equals(left.getContent(), right.getContent())
                && Objects.equals(left.getAnswer(), right.getAnswer())
                && Double.compare(left.getScore(), right.getScore()) == 0
                && Objects.equals(left.getSource(), right.getSource())
                && sameMetadata(left.getMetadata(), right.getMetadata());
    }

    private static boolean sameMetadata(
            Map<String, Object> left, Map<String, Object> right) {
        if (!Objects.equals(left, right)) {
            return false;
        }
        return left == null || new ArrayList<>(left.keySet()).equals(
                new ArrayList<>(right.keySet()));
    }

    private static List<RetrievalResult> copyResults(
            List<RetrievalResult> values) {
        return values.stream().map(
                RagCandidate10DiagnosticStageSupport::copyResult).toList();
    }

    private static RetrievalResult copyResult(RetrievalResult value) {
        return RetrievalResult.builder()
                .segmentId(value.getSegmentId())
                .qmSegmentId(value.getQmSegmentId())
                .parentSegmentId(value.getParentSegmentId())
                .documentId(value.getDocumentId())
                .documentName(value.getDocumentName())
                .content(value.getContent())
                .answer(value.getAnswer())
                .score(value.getScore())
                .source(value.getSource())
                .metadata(value.getMetadata() == null ? null
                        : new LinkedHashMap<>(value.getMetadata()))
                .build();
    }

    private static String classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof DiagnosticFailure diagnostic) {
                return requireErrorCode(diagnostic.errorCode);
            }
            if (RagCandidate10DiagnosticSupport.RANKING_ERROR.equals(
                    current.getMessage())) {
                return RANKING_ERROR;
            }
            current = current.getCause();
        }
        return RUNTIME_ERROR;
    }

    private static String requireErrorCode(String value) {
        if (!FIXED_ERRORS.contains(value)) {
            throw fail(RUNTIME_ERROR);
        }
        return value;
    }

    private static DiagnosticFailure fail(String errorCode) {
        return new DiagnosticFailure(requireKnownError(errorCode), null);
    }

    private static DiagnosticFailure fail(
            String errorCode, Throwable cause) {
        return new DiagnosticFailure(requireKnownError(errorCode), cause);
    }

    private static String requireKnownError(String errorCode) {
        if (!FIXED_ERRORS.contains(errorCode)) {
            throw new IllegalArgumentException("unknown Candidate 10 error");
        }
        return errorCode;
    }

    private static long parseLong(String value, String errorCode) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException failure) {
            throw fail(errorCode, failure);
        }
    }

    private static byte[] canonicalBytes(Object value) {
        return RagCandidate10FreezeSupport.canonicalJsonBytes(value);
    }

    private static String sha256(byte[] value) {
        return RagCandidate10FreezeSupport.sha256(value);
    }

    private static void atomicCreate(
            Path target, byte[] bytes, String errorCode) {
        atomicWrite(target, bytes, false, errorCode);
    }

    private static void atomicReplace(
            Path target, byte[] bytes, String errorCode) {
        atomicWrite(target, bytes, true, errorCode);
    }

    private static void atomicWrite(
            Path target, byte[] bytes, boolean replace, String errorCode) {
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        requireDirectory(parent, errorCode);
        if (!replace) {
            requireAbsent(normalized, errorCode);
        }
        Path staging = normalized.resolveSibling(
                "." + normalized.getFileName() + ".tmp");
        requireAbsent(staging, errorCode);
        try {
            OpenOption[] options = {
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
            try (FileChannel channel = FileChannel.open(staging, options)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            requireBytes(staging, bytes, errorCode);
            if (replace) {
                Files.move(staging, normalized,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(staging, normalized,
                        StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException failure) {
            throw fail(errorCode, failure);
        }
        requireBytes(normalized, bytes, errorCode);
    }

    private static void requireBytes(
            Path path, byte[] expected, String errorCode) {
        requireRegular(path, errorCode);
        try {
            if (!Arrays.equals(expected, Files.readAllBytes(path))) {
                throw fail(errorCode);
            }
        } catch (IOException failure) {
            throw fail(errorCode, failure);
        }
    }

    private static void requireDirectory(Path path, String errorCode) {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw fail(errorCode);
        }
    }

    private static void requireRegular(Path path, String errorCode) {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw fail(errorCode);
        }
    }

    private static void requireAbsent(Path path, String errorCode) {
        if (path == null || Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw fail(errorCode);
        }
    }

    enum Phase {
        RUNNING,
        RANKING_FROZEN,
        QRELS_LOADED,
        COMPLETED
    }

    record DiagnosticResult(
            boolean valid,
            String decision,
            String errorCode,
            Path artifact,
            String artifactSha256,
            Path ledger,
            String ledgerSha256,
            Map<String, Object> artifactData) {

        DiagnosticResult {
            String validity = valid ? "VALID" : "INVALID";
            if (artifactData == null
                    || !artifactData.containsKey("decision")
                    || !artifactData.containsKey("errorCode")
                    || !validity.equals(artifactData.get("validity"))
                    || !Objects.equals(decision, artifactData.get("decision"))
                    || !Objects.equals(errorCode,
                            artifactData.get("errorCode"))) {
                throw fail(RUNTIME_ERROR);
            }
            artifactData = Collections.unmodifiableMap(
                    new LinkedHashMap<>(artifactData));
        }
    }

    record DecisionEvidence(
            boolean allShapesCovered,
            boolean hasQualifyingFamilies,
            boolean qualifyingFamiliesStrictlyImprove,
            boolean aggregateStrictlyImproves,
            boolean noFamilyRegression,
            boolean noUnsafeOutput,
            boolean budgetsValid) {
    }

    private record RankingInput(
            RagEvaluationDataset dataset,
            Map<String, RagCandidate10FixtureGenerator.FamilySpec> families,
            List<RetrievalResult> pool) {
    }

    private record ScorerAudit(
            int calls,
            int requestedTopK,
            int inputCount,
            int outputCount,
            long queryTokenCount,
            long documentTokenCount) {
    }

    private record ArmEvidence(
            String fullRankingSha256,
            List<Long> fullRankingIds,
            Map<Long, Integer> rankBySegmentId,
            Integer admittedFullRank,
            List<Long> sourceIds,
            List<Long> contextIds,
            ScorerAudit scorer,
            int candidate3Calls,
            int contextCalls,
            int internalJdbcCalls,
            int externalEmbeddingCalls,
            int externalDbCalls,
            int externalVectorCalls,
            int externalMetadataCalls,
            int externalGraphCalls,
            int externalNetworkCalls,
            int externalLlmCalls) {

        private Map<String, Object> hashView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("fullRankingSha256", fullRankingSha256);
            value.put("fullRankingIds", fullRankingIds);
            value.put("admittedFullRank", admittedFullRank);
            value.put("sourceIds", sourceIds);
            value.put("contextIds", contextIds);
            value.put("scorerCalls", scorer.calls);
            value.put("requestedTopK", scorer.requestedTopK);
            value.put("inputCount", scorer.inputCount);
            value.put("outputCount", scorer.outputCount);
            value.put("queryTokenCount", scorer.queryTokenCount);
            value.put("documentTokenCount", scorer.documentTokenCount);
            value.put("candidate3Calls", candidate3Calls);
            value.put("contextCalls", contextCalls);
            value.put("internalJdbcCalls", internalJdbcCalls);
            value.put("externalEmbeddingCalls", externalEmbeddingCalls);
            value.put("externalDbCalls", externalDbCalls);
            value.put("externalVectorCalls", externalVectorCalls);
            value.put("externalMetadataCalls", externalMetadataCalls);
            value.put("externalGraphCalls", externalGraphCalls);
            value.put("externalNetworkCalls", externalNetworkCalls);
            value.put("externalLlmCalls", externalLlmCalls);
            return value;
        }

        private boolean budgetValid() {
            return scorer.calls == 1
                    && scorer.requestedTopK == EXPECTED_SEGMENTS
                    && scorer.inputCount == EXPECTED_SEGMENTS
                    && scorer.outputCount == EXPECTED_SEGMENTS
                    && candidate3Calls == 1
                    && contextCalls == 1
                    && internalJdbcCalls == 1
                    && externalEmbeddingCalls == 0
                    && externalDbCalls == 0
                    && externalVectorCalls == 0
                    && externalMetadataCalls == 0
                    && externalGraphCalls == 0
                    && externalNetworkCalls == 0
                    && externalLlmCalls == 0;
        }
    }

    private record QueryRanking(
            RagEvaluationDataset.QueryCase query,
            String role,
            String shape,
            ArmEvidence baseline,
            ArmEvidence candidate) {

        private Map<String, Object> hashView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("queryId", query.id());
            value.put("familyId", query.familyId());
            value.put("language", query.language());
            value.put("role", role);
            value.put("shape", shape);
            value.put("baseline", baseline.hashView());
            value.put("candidate", candidate.hashView());
            return value;
        }

        private boolean budgetValid() {
            return baseline.budgetValid()
                    && candidate.budgetValid()
                    && baseline.scorer.queryTokenCount
                    == candidate.scorer.queryTokenCount
                    && baseline.scorer.documentTokenCount
                    == candidate.scorer.documentTokenCount;
        }
    }

    private record QueryEvaluation(
            String familyId,
            String language,
            String role,
            String shape,
            boolean mechanism,
            boolean unsafe,
            double baselineAp,
            double baselineNdcg,
            double candidateAp,
            double candidateNdcg) {
    }

    private record Metrics(double ap, double ndcg) {
    }

    private record EvaluationSummary(
            List<QueryEvaluation> cases,
            int qualifyingFamilies,
            Metrics aggregateBaseline,
            Metrics aggregateCandidate,
            DecisionEvidence decisionEvidence) {
    }

    private static final class AccessCounter {
        private boolean rankingFrozen;
        private int selectionNonQrelResourceAccessCount;
        private int qrelResourceAccessBeforeRanking;
        private int qrelResourceAccessCount;
        private int holdoutResourceAccessCount;

        private void selection(boolean qrel) {
            if (qrel) {
                qrelResourceAccessCount++;
                if (!rankingFrozen) {
                    qrelResourceAccessBeforeRanking++;
                }
            } else {
                selectionNonQrelResourceAccessCount++;
            }
        }

        private void freezeRanking() {
            if (qrelResourceAccessCount != 0
                    || holdoutResourceAccessCount != 0) {
                throw fail(QREL_ACCESS_ERROR);
            }
            rankingFrozen = true;
        }
    }

    private static final class RunState {
        private final RagCandidate10FreezeSupport.RuntimePaths paths;
        private Phase phase = Phase.RUNNING;
        private byte[] lastLedgerBytes;

        private RunState(RagCandidate10FreezeSupport.RuntimePaths paths) {
            this.paths = paths;
        }

        private void advance(Phase next, Map<String, ?> fields) {
            if (!RagCandidate10DiagnosticStageSupport.validTransition(
                    phase, next)) {
                throw fail(RUNTIME_ERROR);
            }
            requireBytes(paths.ledger(), lastLedgerBytes, RUNTIME_ERROR);
            Map<String, Object> ledger = new LinkedHashMap<>();
            ledger.put("schemaVersion", SCHEMA_VERSION);
            ledger.put("causalScope", CAUSAL_SCOPE);
            ledger.put("status", next.name());
            ledger.put("previousStatus", phase.name());
            fields.forEach(ledger::put);
            byte[] bytes = canonicalBytes(ledger);
            atomicReplace(paths.ledger(), bytes, RUNTIME_ERROR);
            lastLedgerBytes = bytes;
            phase = next;
        }

    }

    private static final class CapturingColbertScorer extends ColbertScorer {
        private final int maxTokensPerDocument;
        private int calls;
        private int requestedTopK;
        private int inputCount;
        private long queryTokenCount;
        private long documentTokenCount;
        private List<Document> rerankedDocuments = List.of();

        private CapturingColbertScorer(
                ColbertConfig config, IEmbeddingService embeddingService) {
            super(config, embeddingService);
            maxTokensPerDocument = config.getMaxTokensPerDoc();
        }

        @Override
        public List<Document> rerank(
                String query, List<Document> documents, int topK) {
            calls++;
            requestedTopK = topK;
            inputCount = documents == null ? -1 : documents.size();
            queryTokenCount = tokenCount(query);
            documentTokenCount = documents == null ? -1L : documents.stream()
                    .map(Document::getText)
                    .mapToLong(this::tokenCount)
                    .map(count -> maxTokensPerDocument > 0
                            ? Math.min(count, maxTokensPerDocument) : count)
                    .sum();
            List<Document> result = super.rerank(query, documents, topK);
            rerankedDocuments = result == null ? List.of() : List.copyOf(result);
            return result;
        }

        private ScorerAudit audit(int expectedSize) {
            if (calls != 1 || requestedTopK != expectedSize
                    || inputCount != expectedSize
                    || rerankedDocuments.size() != expectedSize) {
                throw fail(RANKING_ERROR);
            }
            Set<Long> ids = new LinkedHashSet<>();
            for (Document document : rerankedDocuments) {
                Object id = document.getMetadata().get("segmentId");
                Object score = document.getMetadata().get("colbert_score");
                if (!(id instanceof Long segmentId) || !ids.add(segmentId)
                        || !(score instanceof Number number)
                        || !Double.isFinite(number.doubleValue())) {
                    throw fail(RANKING_ERROR);
                }
            }
            return new ScorerAudit(
                    calls, requestedTopK, inputCount,
                    rerankedDocuments.size(), queryTokenCount,
                    documentTokenCount);
        }

        @SuppressWarnings("unchecked")
        private int tokenCount(String text) {
            try {
                Method method = ColbertScorer.class.getDeclaredMethod(
                        "tokenize", String.class);
                method.setAccessible(true);
                Object value = method.invoke(this, text);
                if (!(value instanceof List<?> tokens)) {
                    throw fail(RANKING_ERROR);
                }
                return tokens.size();
            } catch (ReflectiveOperationException | SecurityException failure) {
                throw fail(RANKING_ERROR, failure);
            }
        }

    }

    private static final class CountingDeterministicReranker
            extends DeterministicRerankerProvider {
        private int calls;

        @Override
        public List<RetrievalResult> rerank(
                RerankRequestContext context,
                List<RetrievalResult> candidates,
                QueryIntent queryIntent,
                int topK) {
            calls++;
            return super.rerank(context, candidates, queryIntent, topK);
        }
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int calls;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            calls++;
            String normalized = Objects.requireNonNullElse(sql, "")
                    .toLowerCase(Locale.ROOT);
            if (!normalized.contains("s2.position - 1")
                    || !normalized.contains("s2.position + 1")) {
                throw fail(RUNTIME_ERROR);
            }
            return List.of();
        }
    }

    private static final class DiagnosticFailure extends IllegalStateException {
        private final String errorCode;

        private DiagnosticFailure(String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
        }
    }
}
