package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStoreContent;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.ai.service.IChatModelService;
import tech.qiantong.qknow.ai.service.IVectorStoreService;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase.KmcKnowledgeBaseMapper;
import tech.qiantong.qknow.module.kmc.service.rag.CandidateFusionService;
import tech.qiantong.qknow.module.kmc.service.rag.CypherSafetyValidator;
import tech.qiantong.qknow.module.kmc.service.rag.DynamicTopKConfig;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagProperties;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.MetadataRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.QueryEntityExtractionService;
import tech.qiantong.qknow.module.kmc.service.rag.QueryIntentAnalyzer;
import tech.qiantong.qknow.module.kmc.service.rag.QueryRouter;
import tech.qiantong.qknow.module.kmc.service.rag.RagContextBuilder;
import tech.qiantong.qknow.module.kmc.service.rag.RagRetrievalService;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.VectorRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.nlp.JiebaNative;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertNative;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.sim.VecSimNative;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Rebuilds a durable Candidate 10.2A lifecycle baseline without legacy artifacts. */
class RagCandidate102AEvidenceBaselineV2Test {

    private static final String ENABLE_PROPERTY =
            "rag.eval.candidate10.evidence-baseline-v2";
    private static final String MARKER =
            "CANDIDATE102A_EVIDENCE_BASELINE_V2";
    private static final String SCHEMA =
            "candidate102a-evidence-baseline-v2-v1";
    private static final String SOURCE_LOCK_SCHEMA =
            "candidate102a-evidence-baseline-v2-source-lock-v1";
    private static final String SNAPSHOT_SCHEMA =
            "candidate102a-worktree-snapshot-v1";
    private static final String NAMESPACE =
            "candidate102a-evidence-baseline-v2";
    private static final String ATTEMPT = "002";
    private static final String ACTIVE_ATTEMPT_TOKEN = "attempt-" + ATTEMPT;
    private static final String PREDECESSOR_STATUS =
            "SUPERSEDED_UNRECOVERABLE";
    private static final String SCOPE =
            "post-routing-single-retrieve-once-single-variant-"
                    + "counterfactual-rerank-orchestration-vecsim-disabled-"
                    + "colbert90-admission90-selection-v1";
    private static final String ALGORITHM_CONCLUSION = "NOT_REACHED";
    private static final String BASE_COMMIT =
            "5c84bb044352a3fef684a37f70cc08ac80058c7b";
    private static final String WORK_BRANCH =
            "codex/candidate102a-evidence-baseline-v2";
    private static final String EXPECTED_JAVA_HOME =
            "/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home";
    private static final String EXPECTED_JAVA_VERSION = "17.0.19";
    private static final String EXPECTED_JAVA_RUNTIME_VERSION = "17.0.19+10";
    private static final String EXPECTED_JAVA_VENDOR = "Eclipse Adoptium";
    private static final String EXPECTED_OS_ARCH = "aarch64";

    private static final Path REPOSITORY_ROOT = Path.of(
            "/Users/achilles/Documents/许子祺/Agent");
    private static final Path BACKEND_ROOT = REPOSITORY_ROOT.resolve("backend");
    private static final Path TESTS_ROOT = BACKEND_ROOT.resolve("tests");
    private static final Path PLAN_SOURCE = REPOSITORY_ROOT.resolve(
            "plans/2026-07-30-candidate102a-evidence-baseline-v2-"
                    + ACTIVE_ATTEMPT_TOKEN + ".md");
    private static final String PLAN_SOURCE_SHA256 =
            "f8c4e0fc5ae520af0c02cffef4171e16a0202374d7e489742fb2ead56ce2c774";
    private static final Path ATTEMPT_ROOT = TESTS_ROOT.resolve(
            "target/rag-eval/candidate102a-evidence-baseline-v2/attempt-002");
    private static final Path SELECTION_ROOT = ATTEMPT_ROOT.resolve("selection");
    private static final Path WORKTREE_ROOT = ATTEMPT_ROOT.resolve("worktree");
    private static final Path SOURCE_LOCK_FILE =
            ATTEMPT_ROOT.resolve("source-lock.json");
    private static final Path MARKER_FILE =
            ATTEMPT_ROOT.resolve("canonical-marker.json");
    private static final Path COMMANDS_FILE = ATTEMPT_ROOT.resolve("commands.sh");
    private static final Path BEFORE_SNAPSHOT_SOURCE = Path.of(
            "/Users/achilles/.codex/evidence-worktree-snapshots/"
                    + "candidate102a-evidence-baseline-v2-attempt-002-before.json");
    private static final String BEFORE_SNAPSHOT_SHA256 =
            "94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e";
    private static final Path BEFORE_SNAPSHOT_FILE = WORKTREE_ROOT.resolve(
            "candidate102a-evidence-baseline-v2-attempt-002-before.json");
    private static final Path AFTER_SNAPSHOT_FILE = WORKTREE_ROOT.resolve(
            "candidate102a-evidence-baseline-v2-attempt-002-after-selection.json");

    private static final String TEST_SOURCE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate102AEvidenceBaselineV2Test.java";
    private static final String FIXTURE_GENERATOR_SOURCE =
            "tests/src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate10FixtureGenerator.java";
    private static final String FIXTURE_GENERATOR_SHA256 =
            "8262d8bf5ad65330e0052119bea910cdd4af81e45a450b8238bc499cfd307a6e";
    private static final String COLBERT_SCORER_SOURCE =
            "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/"
                    + "tech/qiantong/qknow/module/kmc/service/rag/rerank/"
                    + "ColbertScorer.java";
    private static final String COLBERT_SCORER_SHA256 =
            "bf8d340ef6e591a8471e374d03706fbe82bfb58c4d36cc7e58d15c5580569c6e";
    private static final String DIRTY_EMBEDDING_SOURCE =
            "qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/"
                    + "service/impl/EmbeddingServiceImpl.java";
    private static final String DIRTY_RAG_CHECKER_SOURCE =
            "qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/"
                    + "hermes/eval/RAGChecker.java";
    private static final Map<String, String> DIRTY_REACTOR_SHAS = Map.of(
            DIRTY_EMBEDDING_SOURCE,
            "75746407754cdfcf350960bd9587831bb7e2a76df4ba0ef2d46c1a81ea8a2e7f",
            DIRTY_RAG_CHECKER_SOURCE,
            "898d815973fcaedf9dd7bd0a0f73bd6d18431c8ccd4c4b5b385c08a0ed561ffd");

    private static final String SOURCE_LOCK_ERROR =
            "CANDIDATE102A_BASELINE_V2_SOURCE_LOCK_INVALID";
    private static final String COMMAND_ERROR =
            "CANDIDATE102A_BASELINE_V2_COMMAND_INVALID";
    private static final String FROZEN_INPUT_ERROR =
            "CANDIDATE102A_BASELINE_V2_FROZEN_INPUT_INVALID";
    private static final String DATABASE_ERROR =
            "CANDIDATE102A_BASELINE_V2_DATABASE_INVALID";
    private static final String ADAPTER_ERROR =
            "CANDIDATE102A_BASELINE_V2_INFRASTRUCTURE_ADAPTER_INVALID";
    private static final String EXECUTOR_ERROR =
            "CANDIDATE102A_BASELINE_V2_EXECUTOR_INVALID";
    private static final String ORCHESTRATION_ERROR =
            "CANDIDATE102A_BASELINE_V2_COUNTERFACTUAL_ORCHESTRATION_INVALID";
    private static final String FALLBACK_ERROR =
            "CANDIDATE102A_BASELINE_V2_FALLBACK_INVALID";
    private static final String MAPPING_ERROR =
            "CANDIDATE102A_BASELINE_V2_CHECKPOINT_MAPPING_INVALID";
    private static final String FUSION_ERROR =
            "CANDIDATE102A_BASELINE_V2_FUSION_CONTRACT_INVALID";
    private static final String CONTEXT_ERROR =
            "CANDIDATE102A_BASELINE_V2_CONTEXT_CHECKPOINT_INVALID";
    private static final String BUDGET_ERROR =
            "CANDIDATE102A_BASELINE_V2_BUDGET_INVALID";
    private static final String QREL_ERROR =
            "CANDIDATE102A_BASELINE_V2_QREL_ACCESS_INVALID";
    private static final String SAFETY_ERROR =
            "CANDIDATE102A_BASELINE_V2_SAFETY_INVALID";
    private static final String RUNTIME_ERROR =
            "CANDIDATE102A_BASELINE_V2_RUNTIME_INVALID";
    private static final String HARNESS_ERROR =
            "CANDIDATE102A_BASELINE_V2_HARNESS_INVALID";
    private static final String WORKTREE_ERROR =
            "CANDIDATE102A_BASELINE_V2_WORKTREE_SNAPSHOT_INVALID";
    private static final Set<String> FIXED_ERRORS = Set.of(
            SOURCE_LOCK_ERROR, COMMAND_ERROR, FROZEN_INPUT_ERROR,
            DATABASE_ERROR, ADAPTER_ERROR, EXECUTOR_ERROR,
            ORCHESTRATION_ERROR, FALLBACK_ERROR, MAPPING_ERROR,
            FUSION_ERROR, CONTEXT_ERROR, BUDGET_ERROR, QREL_ERROR,
            SAFETY_ERROR, RUNTIME_ERROR, HARNESS_ERROR, WORKTREE_ERROR);

    private static final int QUERY_COUNT = 40;
    private static final int FAMILY_COUNT = 20;
    private static final int CORPUS_COUNT = 1_120;
    private static final long KNOWLEDGE_BASE_ID = 10_160_000L;
    private static final int BUSINESS_LIMIT = 30;
    private static final int CANDIDATE_TOP_K = 90;
    private static final int ADMISSION_MAX_RANK = 90;
    private static final int VECTOR_DIMENSIONS = 256;
    private static final long VECTOR_SEED = 20_260_715L;
    private static final String VECTOR_VERSION = "feature-hash-v1";
    private static final String IMAGE =
            "pgvector/pgvector:0.8.1-pg16@sha256:"
                    + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";
    private static final List<String> CHECKPOINT_NAMES = List.of(
            "keywordRaw", "vectorRaw", "metadataRaw", "graphRaw",
            "retrieverUnion", "weakPathEligibleUnion", "fused",
            "postFilter", "colbertInput", "colbertTop90",
            "admissionOutput30", "candidate3Sources", "contextRendered");
    private static final List<String> BOUNDARY_NAMES = List.of(
            "NO_RELEVANT_EXACT", "NOT_RETRIEVED", "WEAK_PATH_EXCLUDED",
            "FUSION_NOT_PRESERVED", "FILTER_NOT_PRESERVED",
            "COLBERT_FRONTIER_NOT_PRESERVED", "COLBERT90_VISIBLE");
    private static final Set<String> FORBIDDEN_MARKER_FIELDS = Set.of(
            "query", "ordinal", "familyid", "identifier", "segment",
            "segmentid", "document", "documentid", "metadata", "score",
            "content", "path", "exception", "cause", "stack");

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "contracts")
    void baselineContracts() throws Exception {
        requireCommandProperties("contracts");
        requireRuntimeIdentity();
        runContracts();
    }

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "freeze")
    void freezeCurrentEvidence() throws Exception {
        requireCommandProperties("freeze");
        requireRuntimeIdentity();
        freezeEvidence();
    }

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "selection")
    void establishSelectionBaseline() {
        AttemptState state = new AttemptState();
        JSONObject marker;
        try {
            marker = validMarker(runSelection(state));
        } catch (Throwable failure) {
            marker = invalidMarker(state, classify(failure));
        }
        publishMarker(marker);
    }

    private static void runContracts() throws Exception {
        require(FIXED_ERRORS.size() == 17, HARNESS_ERROR);
        require(CHECKPOINT_NAMES.size() == 13 && BOUNDARY_NAMES.size() == 7,
                HARNESS_ERROR);

        RagCandidate10DiagnosticSupport.Eligibility active =
                new RagCandidate10DiagnosticSupport.Eligibility(true, true, true);
        require(active.candidate10DiagnosticArm()
                        && active.candidate3Enabled()
                        && active.deterministicPath()
                        && active.requestActive(),
                ORCHESTRATION_ERROR);
        String one = "document 101";
        String two = "document 101 and document 202";
        String three = "document 101 document 202 document 303";
        require(RagCandidate10DiagnosticSupport.identifierTerms(one).size() == 1
                        && RagCandidate10DiagnosticSupport.identifierTerms(two).size() == 2
                        && RagCandidate10DiagnosticSupport.identifierTerms(three).size() == 3,
                ORCHESTRATION_ERROR);

        for (int size : List.of(29, 30, 60, 61, 90, 91)) {
            RagCandidate10DiagnosticSupport.FullRankingSnapshot ranking =
                    syntheticRanking(size, Set.of(), false, false);
            require(admit(ranking, two, active, 90).candidate().size()
                            == Math.min(BUSINESS_LIMIT, size),
                    ORCHESTRATION_ERROR);
            require(admit(ranking, two, active, 60).equals(
                            RagCandidate10DiagnosticSupport.admit(
                                    ranking, two, active)),
                    ORCHESTRATION_ERROR);
        }
        requireAdmitted(31, two, 60);
        requireAdmitted(60, two, 60);
        requireUnchanged(syntheticRanking(91, Set.of(61), false, false),
                two, active, 60);
        requireAdmitted(61, two, 90);
        requireAdmitted(61, one, 90);
        requireAdmitted(90, two, 90);
        requireUnchanged(syntheticRanking(91, Set.of(91), false, false),
                two, active, 90);
        RagCandidate10DiagnosticSupport.AdmissionResult first = admit(
                syntheticRanking(91, Set.of(31, 60, 61, 90), false, false),
                two, active, 90);
        require(Objects.equals(first.admittedFullRank(), 31),
                ORCHESTRATION_ERROR);
        requireUnchanged(syntheticRanking(91, Set.of(61), true, false),
                two, active, 90);
        requireUnchanged(syntheticRanking(91, Set.of(61), false, true),
                two, active, 90);
        requireUnchanged(syntheticRanking(91, Set.of(61), false, false),
                "no identifiers", active, 90);
        requireUnchanged(syntheticRanking(91, Set.of(61), false, false),
                three, active, 90);
        requireUnchanged(syntheticRanking(91, Set.of(61), false, false),
                two, new RagCandidate10DiagnosticSupport.Eligibility(
                        false, true, true), 90);
        RagCandidate10DiagnosticSupport.FullRankingSnapshot legacyRanking =
                syntheticRanking(91, Set.of(31, 60, 61), false, false);
        RagCandidate10DiagnosticSupport.AdmissionResult legacy =
                RagCandidate10DiagnosticSupport.admit(
                        legacyRanking, two, active);
        RagCandidate10DiagnosticSupport.AdmissionResult scan60 =
                admit(legacyRanking, two, active, 60);
        require(legacy.equals(scan60)
                        && Arrays.equals(
                        RagCandidate10FreezeSupport.canonicalJsonBytes(
                                legacy.candidate()),
                        RagCandidate10FreezeSupport.canonicalJsonBytes(
                                scan60.candidate()))
                        && Arrays.equals(
                        resultProjection(legacy.materializeCandidate()),
                        resultProjection(scan60.materializeCandidate())),
                ORCHESTRATION_ERROR);

        RunAudit orchestration = new RunAudit();
        CounterfactualRerankService service = counterfactualService(orchestration);
        List<RetrievalResult> candidates = syntheticCandidates(91);
        byte[] before = resultProjection(candidates);
        List<RetrievalResult> result = service.rerank(
                "alpha document 101 and document 202", candidates,
                QueryIntent.builder().keywords(List.of("alpha")).build(),
                BUSINESS_LIMIT, null, null);
        require(orchestration.trace.equals(List.of(
                        "filter", "colbert(90)", "admission(90)",
                        "candidate3(30)"))
                        && orchestration.filterCalls.get() == 1
                        && orchestration.colbertCalls.get() == 1
                        && orchestration.admissionCalls.get() == 1
                        && orchestration.candidate3Calls.get() == 1
                        && result.size() == BUSINESS_LIMIT
                        && Arrays.equals(before, resultProjection(candidates)),
                ORCHESTRATION_ERROR);

        requireLifecycleContracts();
        requireContextContracts();
        requireFallbackContracts();
        requireExecutorAndJdbcContracts();
        requireVectorContracts();
        requireCommandPropertyContracts();
        requirePreDelegateCaptureContracts();
        requireCapacityContracts();
        requireBaselineMarkerContracts();
        requirePersistenceContracts();
    }

    private static void requireCommandPropertyContracts() {
        Map<String, String> expected = expectedCommandProperties("contracts");
        requireCommandProperties("contracts", expected);
        Map<String, String> invalid = new LinkedHashMap<>(expected);
        invalid.put("qknow.rag.dynamic-top-k.enabled", "true");
        expectCode(COMMAND_ERROR, () ->
                requireCommandProperties("contracts", invalid));
    }

    private static void requirePreDelegateCaptureContracts() {
        List<RetrievalResult> source = new ArrayList<>(
                syntheticCandidates(2));
        Set<String> frozenSource = freezeKeys(source);
        List<List<RetrievalResult>> paths = new ArrayList<>();
        paths.add(source);
        paths.add(new ArrayList<>());
        List<Set<String>> frozenPaths = freezePathKeys(paths);
        source.clear();
        require(frozenSource.equals(Set.of("seg:1", "seg:2"))
                        && frozenPaths.get(0).equals(frozenSource)
                        && frozenPaths.get(1).isEmpty(),
                MAPPING_ERROR);
    }

    private static void requireCapacityContracts() {
        QueryTrace input90 = syntheticCapacityTrace(90, 90, 30, 30, true);
        QueryTrace input360 = syntheticCapacityTrace(360, 90, 30, 30, true);
        requirePerQueryCapacity(List.of(input90, input360), capacityAudit(
                List.of(90, 360), List.of(90, 90), List.of(90, 90)));

        expectCapacityCode(BUDGET_ERROR,
                List.of(withStageSize(input90, "keywordRaw", 91)),
                capacityAudit(List.of(90), List.of(90), List.of(90)));
        expectCapacityCode(BUDGET_ERROR,
                List.of(syntheticCapacityTrace(89, 90, 30, 30, true)),
                capacityAudit(List.of(89), List.of(90), List.of(90)));
        expectCapacityCode(BUDGET_ERROR,
                List.of(syntheticCapacityTrace(361, 90, 30, 30, true)),
                capacityAudit(List.of(361), List.of(90), List.of(90)));
        expectCapacityCode(BUDGET_ERROR, List.of(input90),
                capacityAudit(List.of(90), List.of(89), List.of(90)));
        expectCapacityCode(BUDGET_ERROR, List.of(input90),
                capacityAudit(List.of(90), List.of(), List.of(90)));
        expectCapacityCode(BUDGET_ERROR,
                List.of(syntheticCapacityTrace(90, 89, 30, 30, true)),
                capacityAudit(List.of(90), List.of(90), List.of(89)));
        expectCapacityCode(BUDGET_ERROR,
                List.of(syntheticCapacityTrace(90, 91, 30, 30, true)),
                capacityAudit(List.of(90), List.of(90), List.of(91)));

        QueryTrace orchestrationFailure =
                syntheticCapacityTrace(90, 90, 29, 30, true);
        QueryTrace budgetFailure =
                syntheticCapacityTrace(89, 90, 30, 30, true);
        expectCapacityCode(ORCHESTRATION_ERROR,
                List.of(orchestrationFailure),
                capacityAudit(List.of(90), List.of(90), List.of(90)));
        expectCapacityCode(ORCHESTRATION_ERROR,
                List.of(syntheticCapacityTrace(90, 90, 30, 29, true)),
                capacityAudit(List.of(90), List.of(90), List.of(90)));
        expectCapacityCode(ORCHESTRATION_ERROR,
                List.of(syntheticCapacityTrace(90, 90, 30, 30, false)),
                capacityAudit(List.of(90), List.of(90), List.of(90)));
        expectCapacityCode(BUDGET_ERROR,
                List.of(orchestrationFailure, budgetFailure),
                capacityAudit(List.of(90, 89), List.of(90, 90),
                        List.of(90, 90)));
        expectCapacityCode(BUDGET_ERROR,
                List.of(budgetFailure, orchestrationFailure),
                capacityAudit(List.of(89, 90), List.of(90, 90),
                        List.of(90, 90)));
    }

    private static void requirePerQueryCapacity(
            List<QueryTrace> traces, RunAudit audit) {
        require(audit.colbertRequestedTopK.size() == traces.size()
                        && audit.colbertInputDocumentCount.size() == traces.size()
                        && audit.colbertOutputDocumentCount.size()
                        == traces.size(),
                BUDGET_ERROR);
        for (int index = 0; index < traces.size(); index++) {
            Map<String, Set<String>> stages = traces.get(index).stages();
            require(stages.get("keywordRaw").size() <= CANDIDATE_TOP_K
                            && stages.get("vectorRaw").size()
                            <= CANDIDATE_TOP_K
                            && stages.get("metadataRaw").size()
                            <= CANDIDATE_TOP_K
                            && stages.get("graphRaw").size()
                            <= CANDIDATE_TOP_K
                            && stages.get("fused").size()
                            <= CANDIDATE_TOP_K * 4
                            && stages.get("postFilter").size()
                            <= CANDIDATE_TOP_K * 4
                            && audit.colbertInputDocumentCount.get(index)
                            == stages.get("colbertInput").size()
                            && audit.colbertInputDocumentCount.get(index)
                            >= CANDIDATE_TOP_K
                            && audit.colbertInputDocumentCount.get(index)
                            <= CANDIDATE_TOP_K * 4
                            && audit.colbertRequestedTopK.get(index)
                            == CANDIDATE_TOP_K
                            && audit.colbertOutputDocumentCount.get(index)
                            == stages.get("colbertTop90").size()
                            && audit.colbertOutputDocumentCount.get(index)
                            == CANDIDATE_TOP_K,
                    BUDGET_ERROR);
        }
        for (QueryTrace trace : traces) {
            Map<String, Set<String>> stages = trace.stages();
            require(stages.get("admissionOutput30").size()
                            == BUSINESS_LIMIT
                            && stages.get("candidate3Sources").size()
                            == BUSINESS_LIMIT
                            && trace.contextSources().equals(
                            stages.get("candidate3Sources")),
                    ORCHESTRATION_ERROR);
        }
    }

    private static QueryTrace syntheticCapacityTrace(
            int input, int output, int admission, int candidate3,
            boolean contextMatches) {
        Map<String, Set<String>> stages = new LinkedHashMap<>();
        CHECKPOINT_NAMES.forEach(name -> stages.put(name, Set.of()));
        stages.put("colbertInput", syntheticKeys(input));
        stages.put("colbertTop90", syntheticKeys(output));
        stages.put("admissionOutput30", syntheticKeys(admission));
        stages.put("candidate3Sources", syntheticKeys(candidate3));
        Set<String> contextSources = contextMatches
                ? stages.get("candidate3Sources")
                : Set.of("seg:context-mismatch");
        return new QueryTrace(
                "synthetic", 1, Set.of(), Map.copyOf(stages),
                contextSources, Set.of(), "NONE", 0, 0);
    }

    private static QueryTrace withStageSize(
            QueryTrace trace, String stage, int size) {
        Map<String, Set<String>> stages = new LinkedHashMap<>(trace.stages());
        stages.put(stage, syntheticKeys(size));
        return new QueryTrace(
                trace.queryId(), trace.identifierCardinality(),
                trace.exactKeys(), Map.copyOf(stages), trace.contextSources(),
                trace.contextRendered(), trace.expansionMode(),
                trace.contextParentQueries(), trace.contextAdjacentQueries());
    }

    private static Set<String> syntheticKeys(int size) {
        return java.util.stream.IntStream.rangeClosed(1, size)
                .mapToObj(value -> "seg:" + value)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static RunAudit capacityAudit(
            List<Integer> inputs, List<Integer> requestedTopK,
            List<Integer> outputs) {
        RunAudit audit = new RunAudit();
        audit.colbertInputDocumentCount.addAll(inputs);
        audit.colbertRequestedTopK.addAll(requestedTopK);
        audit.colbertOutputDocumentCount.addAll(outputs);
        return audit;
    }

    private static void expectCapacityCode(
            String errorCode, List<QueryTrace> traces, RunAudit audit) {
        expectCode(errorCode, () -> requirePerQueryCapacity(traces, audit));
    }

    private static CounterfactualRerankService counterfactualService(
            RunAudit audit) {
        ColbertScorer.ColbertConfig config = new ColbertScorer.ColbertConfig();
        config.setEnabled(true);
        config.setDimensions(64);
        config.setMaxTokensPerDoc(128);
        config.setNgramSize(3);
        config.setEmbeddingPlatform("");
        config.setEmbeddingBaseUrl("");
        config.setEmbeddingApiKey("");
        config.setEmbeddingModel("");
        DeterministicRerankerProvider deterministic =
                new DeterministicRerankerProvider();
        CounterfactualRerankService service =
                new CounterfactualRerankService(audit);
        ReflectionTestUtils.setField(service, "rerankerProviders",
                List.of(deterministic));
        ReflectionTestUtils.setField(service, "deterministicRerankerProvider",
                deterministic);
        CapturingColbertScorer scorer =
                new CapturingColbertScorer(config, audit);
        audit.colbertConfig = config;
        audit.colbertScorer = scorer;
        ReflectionTestUtils.setField(service, "colbertScorer", scorer);
        ReflectionTestUtils.setField(service,
                "identifierConsistencyEnabled", true);
        return service;
    }

    private static List<RetrievalResult> syntheticCandidates(int size) {
        List<RetrievalResult> values = new ArrayList<>(size);
        for (int rank = 1; rank <= size; rank++) {
            values.add(RetrievalResult.builder()
                    .segmentId((long) rank)
                    .documentId(10_000L + rank)
                    .documentName("synthetic-document-" + rank)
                    .content("alpha synthetic content " + rank)
                    .score((size - rank + 1) / (double) size)
                    .source("synthetic")
                    .metadata(Map.of("synthetic", true))
                    .build());
        }
        return values;
    }

    private static RagCandidate10DiagnosticSupport.FullRankingSnapshot
    syntheticRanking(
            int size, Set<Integer> exactRanks, boolean prefixExact,
            boolean rank30Any) {
        List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> rows =
                new ArrayList<>(size);
        for (int rank = 1; rank <= size; rank++) {
            String documentName = exactRanks.contains(rank)
                    ? "document 101 and document 202"
                    : "document unmatched-" + rank;
            if (prefixExact && rank == 1) {
                documentName = "document 101 and document 202";
            }
            if (rank30Any && rank == 30) {
                documentName = "document 101 only";
            }
            rows.add(new RagCandidate10DiagnosticSupport.RetrievalSnapshot(
                    rank, (long) rank, null, null, 10_000L + rank,
                    documentName, "content " + rank, null,
                    size - rank, "synthetic",
                    Map.of("colbert_score", (double) (size - rank))));
        }
        return new RagCandidate10DiagnosticSupport.FullRankingSnapshot(rows);
    }

    private static void requireAdmitted(int rank, String query, int maxRank)
            throws ReflectiveOperationException {
        RagCandidate10DiagnosticSupport.AdmissionResult result = admit(
                syntheticRanking(91, Set.of(rank), false, false), query,
                new RagCandidate10DiagnosticSupport.Eligibility(true, true, true),
                maxRank);
        require(Objects.equals(result.admittedFullRank(), rank)
                        && result.candidate().size() == BUSINESS_LIMIT
                        && result.candidate().get(BUSINESS_LIMIT - 1).fullRank()
                        == rank,
                ORCHESTRATION_ERROR);
    }

    private static void requireUnchanged(
            RagCandidate10DiagnosticSupport.FullRankingSnapshot ranking,
            String query,
            RagCandidate10DiagnosticSupport.Eligibility eligibility,
            int maxRank) throws ReflectiveOperationException {
        RagCandidate10DiagnosticSupport.AdmissionResult result =
                admit(ranking, query, eligibility, maxRank);
        require(result.admittedFullRank() == null
                        && result.baseline().equals(result.candidate())
                        && result.candidate().size()
                        == Math.min(BUSINESS_LIMIT, ranking.rows().size()),
                ORCHESTRATION_ERROR);
    }

    private static RagCandidate10DiagnosticSupport.AdmissionResult admit(
            RagCandidate10DiagnosticSupport.FullRankingSnapshot ranking,
            String query,
            RagCandidate10DiagnosticSupport.Eligibility eligibility,
            int maxRank) throws ReflectiveOperationException {
        Method method = RagCandidate101CandidateDepthResearchTest.class
                .getDeclaredMethod("admitAtMaxRank",
                        RagCandidate10DiagnosticSupport.FullRankingSnapshot.class,
                        String.class,
                        RagCandidate10DiagnosticSupport.Eligibility.class,
                        int.class);
        method.setAccessible(true);
        return (RagCandidate10DiagnosticSupport.AdmissionResult)
                invoke(method, null, ranking, query, eligibility, maxRank);
    }

    private static RagCandidate10DiagnosticSupport.FullRankingSnapshot
    snapshot(List<RetrievalResult> ranking) {
        List<RagCandidate10DiagnosticSupport.RetrievalSnapshot> rows =
                new ArrayList<>(ranking.size());
        Set<Long> ids = new LinkedHashSet<>();
        for (int index = 0; index < ranking.size(); index++) {
            RetrievalResult result = ranking.get(index);
            require(result != null && result.getSegmentId() != null
                            && ids.add(result.getSegmentId()),
                    ORCHESTRATION_ERROR);
            rows.add(new RagCandidate10DiagnosticSupport.RetrievalSnapshot(
                    index + 1, result.getSegmentId(), result.getQmSegmentId(),
                    result.getParentSegmentId(), result.getDocumentId(),
                    result.getDocumentName(), result.getContent(),
                    result.getAnswer(), result.getScore(), result.getSource(),
                    result.getMetadata() == null
                            ? Map.of() : result.getMetadata()));
        }
        return new RagCandidate10DiagnosticSupport.FullRankingSnapshot(rows);
    }

    private static byte[] resultProjection(List<RetrievalResult> values) {
        List<Map<String, Object>> rows = new ArrayList<>(values.size());
        for (RetrievalResult value : values) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segmentId", value.getSegmentId());
            row.put("documentId", value.getDocumentId());
            row.put("documentName", value.getDocumentName());
            row.put("content", value.getContent());
            row.put("score", value.getScore());
            row.put("source", value.getSource());
            row.put("metadata", value.getMetadata());
            rows.add(row);
        }
        return RagCandidate10FreezeSupport.canonicalJsonBytes(rows);
    }

    private static List<RetrievalResult> copyResults(
            List<RetrievalResult> values) {
        List<RetrievalResult> copy = new ArrayList<>(values.size());
        for (RetrievalResult value : values) {
            copy.add(RetrievalResult.builder()
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
                    .build());
        }
        return copy;
    }

    private static Object invokeRerankStage(
            RagRerankService target, String name, Object... arguments) {
        Class<?> type = RagRerankService.class;
        Method selected = null;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == arguments.length) {
                require(selected == null, HARNESS_ERROR);
                selected = method;
            }
        }
        require(selected != null, HARNESS_ERROR);
        selected.setAccessible(true);
        try {
            return invoke(selected, target, arguments);
        } catch (ReflectiveOperationException failure) {
            throw invalid(HARNESS_ERROR, failure);
        }
    }

    private static Map<String, Integer> zeroBoundaries() {
        Map<String, Integer> result = new LinkedHashMap<>();
        BOUNDARY_NAMES.forEach(name -> result.put(name, 0));
        return result;
    }
    private static void requireLifecycleContracts() {
        Set<String> corpus = Set.of("seg:1", "seg:2");
        Map<String, Set<String>> valid = new LinkedHashMap<>();
        valid.put("retrieverUnion", Set.of("seg:1", "seg:2"));
        valid.put("weakPathEligibleUnion", Set.of("seg:1", "seg:2"));
        valid.put("fused", Set.of("seg:1", "seg:2"));
        valid.put("postFilter", Set.of("seg:1", "seg:2"));
        valid.put("colbertInput", Set.of("seg:1", "seg:2"));
        valid.put("colbertTop90", Set.of("seg:1"));
        valid.put("admissionOutput30", Set.of("seg:1"));
        valid.put("candidate3Sources", Set.of("seg:1"));
        require(validateLifecycle(corpus, valid) == 0, MAPPING_ERROR);

        Map<String, Set<String>> reappears = new LinkedHashMap<>(valid);
        reappears.put("postFilter", Set.of("seg:1"));
        reappears.put("colbertInput", Set.of("seg:1"));
        reappears.put("colbertTop90", Set.of("seg:1", "seg:2"));
        require(validateLifecycle(corpus, reappears) == 1, MAPPING_ERROR);
        Map<String, Set<String>> unknown = new LinkedHashMap<>(valid);
        unknown.put("fused", Set.of("seg:1", "seg:2", "seg:3"));
        expectCode(MAPPING_ERROR, () -> validateLifecycle(corpus, unknown));

        RetrievalResult one = RetrievalResult.builder().segmentId(1L)
                .content("one").score(1.0).source("synthetic").build();
        expectCode(MAPPING_ERROR, () -> freezeKeys(List.of(one, one)));
        CaptureState capture = new CaptureState();
        QueryCapture query = capture.begin(
                "synthetic", 2, Set.of("seg:1"));
        query.setRaw("keyword", List.of(one));
        query.setRaw("vector", List.of(copyResults(List.of(one)).get(0)));
        query.setRaw("metadata", List.of());
        query.setRaw("graph", List.of());
        CapturingFusionService fusion = new CapturingFusionService(
                capture, new QueryCounters());
        ReflectionTestUtils.setField(fusion, "rrfK", 60);
        ReflectionTestUtils.setField(fusion, "weakPathThreshold", 0.0D);
        fusion.fuseWithDiagnostics(
                List.of(List.of(one), List.of(copyResults(List.of(one)).get(0))),
                List.of("keyword", "vector"));
        query.validateFusion();
        capture.clear(query);

        Set<String> introduced = new LinkedHashSet<>(Set.of("seg:2"));
        introduced.removeAll(Set.of("seg:1"));
        require(introduced.equals(Set.of("seg:2"))
                        && validateLifecycle(corpus, valid) == 0,
                CONTEXT_ERROR);
    }

    private static int validateLifecycle(
            Set<String> corpus, Map<String, Set<String>> stages) {
        List<String> chain = List.of(
                "retrieverUnion", "weakPathEligibleUnion", "fused",
                "postFilter", "colbertInput", "colbertTop90",
                "admissionOutput30", "candidate3Sources");
        require(stages.keySet().containsAll(chain), MAPPING_ERROR);
        stages.values().forEach(keys -> require(corpus.containsAll(keys),
                MAPPING_ERROR));
        int nonMonotonic = 0;
        for (String key : corpus) {
            boolean disappeared = false;
            for (String stage : chain) {
                boolean present = stages.get(stage).contains(key);
                if (present && disappeared) {
                    nonMonotonic++;
                    break;
                }
                if (!present) {
                    disappeared = true;
                }
            }
        }
        return nonMonotonic;
    }

    private static void requireContextContracts() {
        ContextCounts counts = new ContextCounts(
                10, 4, 11, 8, 2, 3, 3, 1, 2, 5);
        requireContextIdentities(counts);
        expectCode(CONTEXT_ERROR, () -> requireContextIdentities(
                new ContextCounts(10, 4, 11, 8, 1, 3, 3, 1, 2, 5)));
    }

    private static void requireContextIdentities(ContextCounts value) {
        require(List.of(
                        value.sourceSegmentCount,
                        value.sourceExactCount,
                        value.contextSegmentCount,
                        value.contextSourceVisibleCount,
                        value.contextSourceOmittedCount,
                        value.contextIntroducedCount,
                        value.contextSourceExactVisibleCount,
                        value.contextSourceExactOmittedCount,
                        value.contextIntroducedExactCount,
                        value.contextExactCount).stream()
                        .allMatch(count -> count >= 0)
                        && value.contextSourceVisibleCount
                        + value.contextSourceOmittedCount
                        == value.sourceSegmentCount
                        && value.contextSourceExactVisibleCount
                        + value.contextSourceExactOmittedCount
                        == value.sourceExactCount
                        && value.contextSourceVisibleCount
                        + value.contextIntroducedCount
                        == value.contextSegmentCount
                        && value.contextExactCount
                        == value.contextSourceExactVisibleCount
                        + value.contextIntroducedExactCount
                        && value.sourceExactCount <= value.sourceSegmentCount
                        && value.contextExactCount <= value.contextSegmentCount
                        && value.contextSourceExactVisibleCount
                        <= value.contextSourceVisibleCount
                        && value.contextIntroducedExactCount
                        <= value.contextIntroducedCount,
                CONTEXT_ERROR);
    }

    private static SelectionRun syntheticSelectionRun(
            Map<String, Integer> boundaries) {
        Map<String, PresenceAccumulator> presence = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        CHECKPOINT_NAMES.forEach(name -> {
            presence.put(name, new PresenceAccumulator());
            hashes.put(name, "0".repeat(64));
        });
        ContextEvidence context = new ContextEvidence(
                new ContextCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                Map.of("NONE", 40L, "PARENT", 0L, "ADJACENT", 0L,
                        "PARENT_FALLBACK_ADJACENT", 0L),
                0, 0, 0);
        Evaluation evaluation = new Evaluation(
                0, 0, 0, 0, Map.copyOf(presence), context,
                boundaries, Map.copyOf(hashes), 0);
        return new SelectionRun(evaluation, syntheticBudgets(),
                syntheticInfrastructure(), Map.of(
                "selectionNonQrelResourceAccessCount", 3,
                "qrelResourceAccessBeforeRanking", 0,
                "qrelResourceAccessCount", 1,
                "holdoutPathOperationCount", 0), "0".repeat(64));
    }

    private static Map<String, Object> syntheticBudgets() {
        Map<String, Object> calls = new LinkedHashMap<>();
        List.of("queryIntentAnalyze", "retrieveOnce", "queryEntityExtract",
                        "keywordRetriever", "vectorRetriever",
                        "metadataRetriever", "graphRetriever", "fusion",
                        "counterfactualRerank", "filter", "colbert",
                        "admission", "candidate3", "context")
                .forEach(name -> calls.put(name, 40));
        Map<String, Object> adapters = new LinkedHashMap<>();
        adapters.put("kbMapperLookup", 80);
        adapters.put("embeddingModelResolve", 40);
        adapters.put("vectorStoreResolve", 40);
        adapters.put("similaritySearch", 40);
        adapters.put("documentEmbeddingAfterSeed", 0);
        adapters.put("queryEmbeddingAfterSeed", 40);
        adapters.put("totalEmbeddingInputsAfterSeed", 40);
        adapters.put("vectorAdd", 0);
        adapters.put("vectorDelete", 0);
        adapters.put("chatModelResolve", 0);
        adapters.put("chatModelCall", 0);
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("keyword", 40);
        database.put("metadata", 40);
        database.put("vector", 0);
        database.put("contextParent", 0);
        database.put("contextAdjacent", 0);
        database.put("contextOther", 0);
        database.put("other", 0);
        database.put("attempted", 80);
        database.put("succeeded", 80);
        database.put("failed", 0);
        database.put("context", 0);
        database.put("total", 80);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("limits", limitsView());
        value.put("configuration", expectedConfiguration());
        value.put("calls", calls);
        value.put("adapters", adapters);
        value.put("databaseQueries", database);
        value.put("fallback", Map.of(
                "scopeOpenCount", 40,
                "scopeVerificationCount", 40,
                "expectedJavaTokenizationEventCount", 40,
                "unexpectedScopedEventCount", 0,
                "sqlFailureCount", 0));
        value.put("externalCalls", Map.of(
                "externalEmbedding", 0, "vectorDatabase", 0,
                "graphExternal", 0, "remoteReranker", 0,
                "localReranker", 0, "network", 0, "llm", 0));
        return Map.copyOf(value);
    }

    private static Map<String, Object> syntheticInfrastructure() {
        Map<String, Object> executor = new LinkedHashMap<>();
        executor.put("corePoolSize", 4);
        executor.put("maxPoolSize", 4);
        executor.put("queueCapacity", 32);
        executor.put("submitAttemptCount", 200);
        executor.put("acceptedSubmitCount", 200);
        executor.put("callableSubmitCount", 200);
        executor.put("runnableSubmitCount", 0);
        executor.put("started", 200);
        executor.put("succeeded", 200);
        executor.put("completed", 200);
        executor.put("failed", 0);
        executor.put("rejected", 0);
        executor.put("doneFutureCount", 200);
        executor.put("cancelledFutureCount", 0);
        executor.put("taskCount", 200);
        executor.put("completedTaskCount", 200);
        executor.put("activeBeforeQrelFreeze", 0);
        executor.put("queuedBeforeQrelFreeze", 0);
        executor.put("queueRemainingCapacityBeforeQrelFreeze", 32);
        executor.put("terminatedAfterCleanup", true);
        String sha = "0".repeat(64);
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("loadRankingInputCalls", 1);
        seed.put("queryCount", 40);
        seed.put("familyCount", 20);
        seed.put("corpusCount", 1120);
        seed.put("postgresDocumentCount", 1120);
        seed.put("postgresSegmentCount", 1120);
        seed.put("postgresEntityMetadataCount", 0);
        seed.put("vectorDocumentCount", 1120);
        seed.put("vectorSnapshotKeyCount", 1120);
        seed.put("seedEmbeddingInputCount", 1120);
        seed.put("corpusProjectionSha256", sha);
        seed.put("postgresProjectionSha256", sha);
        seed.put("vectorProjectionSha256", sha);
        seed.put("vectorSnapshotKeySetExact", true);
        seed.put("seedEmbeddingInputOrderExact", true);
        seed.put("projectionBytesEqual", true);
        seed.put("countersResetBeforeSelection", true);
        return Map.of(
                "docker", Map.of(
                        "imageInspect", 1, "containerConstruct", 1,
                        "containerStart", 1, "imagePull", 0,
                        "containerStop", 1),
                "executor", executor, "seed", seed);
    }

    private static void requireFallbackContracts() throws Exception {
        require(RagFallbackMonitor.currentScope() == null
                        && !JiebaNative.isAvailable(), FALLBACK_ERROR);
        try (RagFallbackMonitor.Scope ignored = RagFallbackMonitor.openScope()) {
            JiebaNative.safeCut("contract 中文 tokenizer");
            requireExpectedJiebaFallback();
        }
        require(RagFallbackMonitor.currentScope() == null, FALLBACK_ERROR);
    }

    private static void requireExecutorAndJdbcContracts() throws Exception {
        CountingExecutor executor = new CountingExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        Future<Object> failed = executor.submit(() -> {
            throw new IllegalStateException("synthetic");
        });
        try {
            failed.get(5, TimeUnit.SECONDS);
            throw invalid(HARNESS_ERROR);
        } catch (java.util.concurrent.ExecutionException expected) {
            require(expected.getCause() instanceof IllegalStateException,
                    EXECUTOR_ERROR);
        }
        CountDownLatch started = new CountDownLatch(1);
        Future<Object> cancelled = executor.submit(() -> {
            started.countDown();
            new CountDownLatch(1).await();
            return null;
        });
        require(started.await(5, TimeUnit.SECONDS)
                        && cancelled.cancel(true), EXECUTOR_ERROR);
        executor.awaitIdle();
        executor.initiateShutdown();
        try {
            executor.submit(() -> "rejected");
            throw invalid(HARNESS_ERROR);
        } catch (RuntimeException expected) {
            require(executor.rejected.get() == 1, EXECUTOR_ERROR);
        }
        executor.shutdownAndAwait();
        require(executor.failed.get() == 2
                        && cancelled.isDone() && cancelled.isCancelled()
                        && executor.terminatedAfterCleanup, EXECUTOR_ERROR);

        DataSource failing = mock(DataSource.class);
        when(failing.getConnection()).thenThrow(
                new SQLException("synthetic jdbc failure"));
        SqlCounters sql = new SqlCounters();
        CountingJdbcTemplate jdbc = new CountingJdbcTemplate(
                failing, sql, "keyword");
        try {
            jdbc.query("SELECT 1", (rs, row) -> 1);
            throw invalid(HARNESS_ERROR);
        } catch (RuntimeException expected) {
            require(sql.attempted == 1 && sql.failed == 1
                            && sql.succeeded == 0, FALLBACK_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireVectorContracts() {
        SeedCounters counters = new SeedCounters();
        CountingEmbeddingModel model = new CountingEmbeddingModel(
                new FeatureHashEmbeddingModel(8, 7L, "synthetic-v1"),
                counters);
        SimpleVectorStore store = SimpleVectorStore.builder(model).build();
        List<Document> documents = List.of(
                Document.builder().id("k2").text("two")
                        .metadata(Map.of("order", 2)).build(),
                Document.builder().id("k1").text("one")
                        .metadata(Map.of("order", 1)).build());
        store.add(documents);
        Object raw = ReflectionTestUtils.getField(store, "store");
        require(raw instanceof Map<?, ?>, ADAPTER_ERROR);
        Map<String, SimpleVectorStoreContent> snapshot =
                new LinkedHashMap<>((Map<String, SimpleVectorStoreContent>) raw);
        require(snapshot.keySet().equals(Set.of("k1", "k2")), ADAPTER_ERROR);
        List<String> lockedOrder = List.of("k2", "k1");
        List<String> actual = lockedOrder.stream()
                .map(key -> Objects.requireNonNull(snapshot.get(key)).getId())
                .toList();
        require(actual.equals(lockedOrder)
                        && counters.seedInputs.equals(List.of(
                        new SeedEmbeddingInput("k2", "two"),
                        new SeedEmbeddingInput("k1", "one"))),
                ADAPTER_ERROR);
    }

    private static void freezeEvidence() {
        require(!Files.exists(ATTEMPT_ROOT, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(ATTEMPT_ROOT),
                FROZEN_INPUT_ERROR);
        require(BEFORE_SNAPSHOT_SHA256.equals(sha256(readRegularBytes(
                        BEFORE_SNAPSHOT_SOURCE, WORKTREE_ERROR))),
                WORKTREE_ERROR);
        JSONObject externalBefore = readCanonicalObject(
                BEFORE_SNAPSHOT_SOURCE, WORKTREE_ERROR);
        requireSnapshotSchema(externalBefore, "BEFORE");
        JSONObject currentBefore = captureWorktreeSnapshot("BEFORE");
        requireSameWorktree(externalBefore, currentBefore);

        RagCandidate10FixtureGenerator.RankingFixture ranking =
                RagCandidate10FixtureGenerator.rankingView(
                        RagCandidate10FixtureGenerator.Split.SELECTION);
        requireRankingFixture(ranking);
        createDirectoriesNoSymlink(SELECTION_ROOT, FROZEN_INPUT_ERROR);
        createDirectoriesNoSymlink(WORKTREE_ROOT, FROZEN_INPUT_ERROR);
        for (String logical : List.of("corpus", "queries", "pressure")) {
            RagCandidate10FixtureGenerator.Resource resource =
                    ranking.resource(logical);
            createNew(SELECTION_ROOT.resolve(resource.fileName()),
                    resource.bytes(), FROZEN_INPUT_ERROR);
        }
        createNew(COMMANDS_FILE, commandsBytes(), COMMAND_ERROR);
        try {
            Files.setPosixFilePermissions(COMMANDS_FILE, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE));
        } catch (IOException | UnsupportedOperationException failure) {
            throw invalid(COMMAND_ERROR, failure);
        }
        createNew(BEFORE_SNAPSHOT_FILE,
                readRegularBytes(BEFORE_SNAPSHOT_SOURCE, WORKTREE_ERROR),
                WORKTREE_ERROR);
        createNew(SOURCE_LOCK_FILE,
                canonicalBytes(sourceLockDocument()), SOURCE_LOCK_ERROR);
        verifySourceLock();
    }

    private static RankingInput loadRankingInput(AccessCounter access) {
        require(access != null && access.allZero(), FROZEN_INPUT_ERROR);
        verifySourceLock();
        RagCandidate10FixtureGenerator.RankingFixture generated =
                RagCandidate10FixtureGenerator.rankingView(
                        RagCandidate10FixtureGenerator.Split.SELECTION);
        requireRankingFixture(generated);
        for (String logical : List.of("corpus", "queries", "pressure")) {
            RagCandidate10FixtureGenerator.Resource expected =
                    generated.resource(logical);
            access.selectionNonQrelResourceAccessCount++;
            byte[] actual = readRegularBytes(
                    SELECTION_ROOT.resolve(expected.fileName()),
                    FROZEN_INPUT_ERROR);
            require(Arrays.equals(expected.bytes(), actual)
                            && expected.sha256().equals(sha256(actual)),
                    FROZEN_INPUT_ERROR);
        }
        Map<String, RagCandidate10FixtureGenerator.FamilySpec> families =
                generated.families().stream().collect(
                        java.util.stream.Collectors.toMap(
                                RagCandidate10FixtureGenerator.FamilySpec::familyId,
                                value -> value,
                                (left, right) -> left,
                                LinkedHashMap::new));
        List<RetrievalResult> pool = generated.dataset().corpusById().values()
                .stream()
                .sorted(Comparator.comparingLong(segment ->
                        parseLong(segment.segmentId(), FROZEN_INPUT_ERROR)))
                .map(RagCandidate102AEvidenceBaselineV2Test::toOriginal)
                .toList();
        require(access.selectionNonQrelResourceAccessCount == 3
                        && access.qrelResourceAccessBeforeRanking == 0
                        && access.qrelResourceAccessCount == 0
                        && access.holdoutPathOperationCount == 0,
                QREL_ERROR);
        return new RankingInput(generated.dataset(), Map.copyOf(families), pool);
    }

    private static RagEvaluationDataset loadQrelsAfterRanking(
            Fixture fixture, AccessCounter access) {
        require(access != null && access.rankingFrozen
                        && access.qrelResourceAccessBeforeRanking == 0
                        && access.qrelResourceAccessCount == 0
                        && access.holdoutPathOperationCount == 0,
                QREL_ERROR);
        access.qrelResourceAccessCount++;
        RagCandidate10FixtureGenerator.QrelFixture qrels =
                RagCandidate10FixtureGenerator.qrels(
                        RagCandidate10FixtureGenerator.Split.SELECTION);
        RagEvaluationDataset labeled = new RagEvaluationDataset(
                fixture.dataset.corpusById(), fixture.dataset.queries(),
                qrels.qrels());
        RagEvaluationDatasetLoader.validate(labeled);
        require(qrels.pairCount() > 0
                        && access.qrelResourceAccessCount == 1
                        && access.holdoutPathOperationCount == 0,
                QREL_ERROR);
        return labeled;
    }

    private static RetrievalResult toOriginal(
            RagEvaluationDataset.CorpusSegment segment) {
        Object score = segment.metadata().get("score");
        Object source = segment.metadata().get("source");
        Object name = segment.metadata().get("documentName");
        require(score instanceof Number number
                        && Double.isFinite(number.doubleValue())
                        && source instanceof String
                        && name instanceof String,
                FROZEN_INPUT_ERROR);
        return RetrievalResult.builder()
                .segmentId(parseLong(segment.segmentId(), FROZEN_INPUT_ERROR))
                .qmSegmentId(null)
                .parentSegmentId(segment.parentSegmentId())
                .documentId(parseLong(
                        segment.documentId(), FROZEN_INPUT_ERROR))
                .documentName((String) name)
                .content(segment.content())
                .answer(null)
                .score(((Number) score).doubleValue())
                .source((String) source)
                .metadata(new LinkedHashMap<>(segment.metadata()))
                .build();
    }

    private static void requireRankingFixture(
            RagCandidate10FixtureGenerator.RankingFixture ranking) {
        require(ranking != null
                        && ranking.split()
                        == RagCandidate10FixtureGenerator.Split.SELECTION
                        && ranking.dataset().queries().size() == QUERY_COUNT
                        && ranking.dataset().corpusById().size() == CORPUS_COUNT
                        && ranking.dataset().qrels().isEmpty()
                        && ranking.families().size() == FAMILY_COUNT
                        && ranking.resources().keySet().equals(
                        Set.of("corpus", "queries", "pressure")),
                FROZEN_INPUT_ERROR);
    }

    private static void verifySourceLock() {
        JSONObject actual = readCanonicalObject(
                SOURCE_LOCK_FILE, SOURCE_LOCK_ERROR);
        byte[] expected = canonicalBytes(sourceLockDocument());
        require(Arrays.equals(expected, canonicalBytes(actual)),
                SOURCE_LOCK_ERROR);
    }

    private static Map<String, Object> sourceLockDocument() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", SOURCE_LOCK_SCHEMA);
        value.put("namespace", NAMESPACE);
        value.put("attempt", ATTEMPT);
        value.put("predecessorStatus", PREDECESSOR_STATUS);
        value.put("repository", repositoryView());
        value.put("jdk", runtimeIdentity());
        value.put("sourceFiles", List.of(
                fileBinding(TEST_SOURCE),
                fileBinding(FIXTURE_GENERATOR_SOURCE),
                fileBinding(COLBERT_SCORER_SOURCE),
                fileBinding(DIRTY_EMBEDDING_SOURCE),
                fileBinding(DIRTY_RAG_CHECKER_SOURCE)).stream()
                .sorted(Comparator.comparing(binding ->
                        (String) binding.get("path"))).toList());
        value.put("commands", artifactBinding(
                ATTEMPT_ROOT.relativize(COMMANDS_FILE).toString(),
                COMMANDS_FILE));
        List<Map<String, Object>> resources = new ArrayList<>();
        for (String file : List.of(
                "corpus.jsonl", "pressure.json", "queries.jsonl")) {
            resources.add(artifactBinding("selection/" + file,
                    SELECTION_ROOT.resolve(file)));
        }
        value.put("selectionNonQrelInputs", Map.of(
                "resourceAccessCount", 3,
                "files", List.copyOf(resources),
                "setSha256", sha256(canonicalBytes(resources))));
        value.put("worktreeBefore", Map.of(
                "path", "worktree/" + BEFORE_SNAPSHOT_FILE.getFileName(),
                "sha256", hashRegular(BEFORE_SNAPSHOT_FILE),
                "entryCount", 6));
        value.put("runtimeContract", Map.of(
                "queryCount", QUERY_COUNT,
                "familyCount", FAMILY_COUNT,
                "corpusCount", CORPUS_COUNT,
                "checkpointCount", CHECKPOINT_NAMES.size(),
                "futureCount", 200,
                "qrelAfterRankingFreeze", true,
                "qrelAccessCount", 1,
                "holdoutPathOperationCount", 0));
        return Map.copyOf(value);
    }

    private static Map<String, Object> repositoryView() {
        require(REPOSITORY_ROOT.equals(Path.of(
                        runCommand(REPOSITORY_ROOT, SOURCE_LOCK_ERROR,
                                "git", "rev-parse", "--show-toplevel").trim()))
                        && BASE_COMMIT.equals(runCommand(
                        REPOSITORY_ROOT, SOURCE_LOCK_ERROR,
                        "git", "rev-parse", "HEAD").trim())
                        && WORK_BRANCH.equals(runCommand(
                        REPOSITORY_ROOT, SOURCE_LOCK_ERROR,
                        "git", "symbolic-ref", "--short", "HEAD").trim())
                        && "sha1".equals(runCommand(
                        REPOSITORY_ROOT, SOURCE_LOCK_ERROR,
                        "git", "rev-parse", "--show-object-format").trim()),
                SOURCE_LOCK_ERROR);
        return Map.of(
                "repositoryRoot", REPOSITORY_ROOT.toString(),
                "backendRoot", BACKEND_ROOT.toString(),
                "head", BASE_COMMIT,
                "branch", WORK_BRANCH,
                "objectFormat", "sha1");
    }

    private static Map<String, Object> runtimeIdentity() {
        return Map.of(
                "javaHome", System.getProperty("java.home"),
                "javaVersion", System.getProperty("java.version"),
                "javaRuntimeVersion", System.getProperty(
                        "java.runtime.version"),
                "javaVendor", System.getProperty("java.vendor"),
                "osArch", System.getProperty("os.arch"));
    }

    private static void requireRuntimeIdentity() {
        require(runtimeIdentity().equals(Map.of(
                        "javaHome", EXPECTED_JAVA_HOME,
                        "javaVersion", EXPECTED_JAVA_VERSION,
                        "javaRuntimeVersion", EXPECTED_JAVA_RUNTIME_VERSION,
                        "javaVendor", EXPECTED_JAVA_VENDOR,
                        "osArch", EXPECTED_OS_ARCH))
                        && "UTF-8".equals(System.getProperty("file.encoding"))
                        && "UTC".equals(System.getProperty("user.timezone"))
                        && "en".equals(System.getProperty("user.language"))
                        && "US".equals(System.getProperty("user.country")),
                COMMAND_ERROR);
    }

    private static Map<String, Object> fileBinding(String relative) {
        Path path = BACKEND_ROOT.resolve(relative);
        String sha = hashRegular(path);
        if (DIRTY_REACTOR_SHAS.containsKey(relative)) {
            require(DIRTY_REACTOR_SHAS.get(relative).equals(sha),
                    SOURCE_LOCK_ERROR);
        } else if (COLBERT_SCORER_SOURCE.equals(relative)) {
            require(COLBERT_SCORER_SHA256.equals(sha), SOURCE_LOCK_ERROR);
        } else if (FIXTURE_GENERATOR_SOURCE.equals(relative)) {
            require(FIXTURE_GENERATOR_SHA256.equals(sha), SOURCE_LOCK_ERROR);
        }
        return artifactBinding(relative, path);
    }

    private static Map<String, Object> artifactBinding(
            String relative, Path path) {
        byte[] bytes = readRegularBytes(path, SOURCE_LOCK_ERROR);
        return Map.of(
                "path", relative.replace('\\', '/'),
                "sizeBytes", bytes.length,
                "sha256", sha256(bytes));
    }

    private static JSONObject captureWorktreeSnapshot(String phase) {
        require(Set.of("BEFORE", "AFTER_SELECTION").contains(phase),
                WORKTREE_ERROR);
        byte[] raw = runCommandBytes(REPOSITORY_ROOT, WORKTREE_ERROR,
                "git", "status", "--porcelain=v1", "-z",
                "--untracked-files=all", "--no-renames", "--", ".",
                ":(exclude)backend/tests/target/**",
                ":(exclude)backend/tests/src/test/java/tech/qiantong/qknow/"
                        + "rag/eval/RagCandidate102AEvidenceBaselineV2Test.java",
                ":(exclude)backend/tests/evidence/"
                        + "candidate102a-evidence-baseline-v2/**",
                ":(exclude)plans/2026-07-30-candidate102a-evidence-"
                        + "baseline-v2-" + ACTIVE_ATTEMPT_TOKEN + ".md");
        List<Map<String, Object>> entries = new ArrayList<>();
        for (byte[] record : splitNul(raw)) {
            require(record.length >= 4 && record[2] == ' ', WORKTREE_ERROR);
            String xy = new String(record, 0, 2, StandardCharsets.US_ASCII);
            String path = new String(record, 3, record.length - 3,
                    StandardCharsets.UTF_8);
            entries.add(worktreeEntry(path, xy));
        }
        entries.sort(Comparator.comparing(entry ->
                (String) entry.get("path")));
        JSONObject snapshot = new JSONObject();
        snapshot.put("schemaVersion", SNAPSHOT_SCHEMA);
        snapshot.put("phase", phase);
        snapshot.put("headCommit", BASE_COMMIT);
        snapshot.put("entries", entries);
        return snapshot;
    }

    private static Map<String, Object> worktreeEntry(String relative, String xy) {
        Path path = REPOSITORY_ROOT.resolve(relative);
        String type;
        String sha = null;
        if (Files.isSymbolicLink(path)) {
            type = "SYMLINK";
            try {
                sha = sha256(Files.readSymbolicLink(path).toString()
                        .getBytes(StandardCharsets.UTF_8));
            } catch (IOException failure) {
                throw invalid(WORKTREE_ERROR, failure);
            }
        } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            type = "REGULAR";
            sha = hashRegular(path);
        } else if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            type = "MISSING";
        } else {
            throw invalid(WORKTREE_ERROR);
        }
        byte[] rawIndex = runCommandBytes(REPOSITORY_ROOT, WORKTREE_ERROR,
                "git", "ls-files", "-s", "-z", "--", relative);
        String indexMode = null;
        String indexObjectId = null;
        List<byte[]> indexEntries = splitNul(rawIndex);
        if (!indexEntries.isEmpty()) {
            require(indexEntries.size() == 1, WORKTREE_ERROR);
            String value = new String(indexEntries.get(0), StandardCharsets.UTF_8);
            int tab = value.indexOf('\t');
            require(tab > 0, WORKTREE_ERROR);
            String[] fields = value.substring(0, tab).split(" ");
            require(fields.length == 3 && "0".equals(fields[2]),
                    WORKTREE_ERROR);
            indexMode = fields[0];
            indexObjectId = fields[1];
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", relative);
        entry.put("porcelainXY", xy);
        entry.put("worktreeType", type);
        entry.put("worktreeSha256", sha);
        entry.put("indexMode", indexMode);
        entry.put("indexObjectId", indexObjectId);
        return Collections.unmodifiableMap(new LinkedHashMap<>(entry));
    }

    private static List<byte[]> splitNul(byte[] value) {
        List<byte[]> records = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < value.length; index++) {
            if (value[index] == 0) {
                if (index > start) {
                    records.add(Arrays.copyOfRange(value, start, index));
                }
                start = index + 1;
            }
        }
        require(start == value.length, WORKTREE_ERROR);
        return List.copyOf(records);
    }

    private static void requireSnapshotSchema(JSONObject snapshot, String phase) {
        require(snapshot != null && snapshot.keySet().equals(Set.of(
                        "schemaVersion", "phase", "headCommit", "entries"))
                        && SNAPSHOT_SCHEMA.equals(
                        snapshot.getString("schemaVersion"))
                        && phase.equals(snapshot.getString("phase"))
                        && BASE_COMMIT.equals(snapshot.getString("headCommit"))
                        && snapshot.getJSONArray("entries") != null,
                WORKTREE_ERROR);
    }

    private static void requireSameWorktree(
            JSONObject before, JSONObject after) {
        requireSnapshotSchema(before, "BEFORE");
        require(Set.of("BEFORE", "AFTER_SELECTION").contains(
                        after.getString("phase")), WORKTREE_ERROR);
        requireSnapshotSchema(after, after.getString("phase"));
        require(before.getJSONArray("entries").equals(
                        after.getJSONArray("entries")), WORKTREE_ERROR);
    }

    private static JSONObject readCanonicalObject(Path path, String errorCode) {
        byte[] bytes = readRegularBytes(path, errorCode);
        try {
            JSONObject value = JSON.parseObject(bytes);
            require(Arrays.equals(bytes, canonicalBytes(value)), errorCode);
            return value;
        } catch (RuntimeException failure) {
            throw invalid(errorCode, failure);
        }
    }

    private static byte[] readRegularBytes(Path path, String errorCode) {
        require(path != null && !Files.isSymbolicLink(path)
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                errorCode);
        try {
            return Files.readAllBytes(path);
        } catch (IOException failure) {
            throw invalid(errorCode, failure);
        }
    }

    private static void createNew(Path path, byte[] bytes, String errorCode) {
        require(path != null && bytes != null, errorCode);
        Path normalized = path.toAbsolutePath().normalize();
        Path root = TESTS_ROOT.toAbsolutePath().normalize();
        require(normalized.startsWith(root)
                        && !Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(normalized), errorCode);
        createDirectoriesNoSymlink(normalized.getParent(), errorCode);
        try {
            Files.write(normalized, bytes, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw invalid(errorCode, failure);
        }
        requireNoSymlink(root, normalized, errorCode);
        require(Arrays.equals(bytes, readRegularBytes(path, errorCode)),
                errorCode);
    }

    private static void createDirectoriesNoSymlink(
            Path directory, String errorCode) {
        Path root = TESTS_ROOT.toAbsolutePath().normalize();
        Path target = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath().normalize();
        require(target.startsWith(root)
                        && !Files.isSymbolicLink(root)
                        && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS),
                errorCode);
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(current)) {
                require(!Files.isSymbolicLink(current)
                                && Files.isDirectory(
                                current, LinkOption.NOFOLLOW_LINKS),
                        errorCode);
                continue;
            }
            try {
                Files.createDirectory(current);
            } catch (IOException failure) {
                throw invalid(errorCode, failure);
            }
        }
        requireNoSymlink(root, target, errorCode);
    }

    private static void requireNoSymlink(
            Path base, Path target, String errorCode) {
        Path normalizedBase = Objects.requireNonNull(base, "base")
                .toAbsolutePath().normalize();
        Path normalizedTarget = Objects.requireNonNull(target, "target")
                .toAbsolutePath().normalize();
        require(normalizedTarget.startsWith(normalizedBase), errorCode);
        Path current = normalizedBase;
        require(!Files.isSymbolicLink(current), errorCode);
        for (Path part : normalizedBase.relativize(normalizedTarget)) {
            current = current.resolve(part);
            require(!Files.isSymbolicLink(current), errorCode);
        }
    }

    private static byte[] canonicalBytes(Object value) {
        return RagCandidate10FreezeSupport.canonicalJsonBytes(value);
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException failure) {
            throw invalid(HARNESS_ERROR, failure);
        }
    }

    private static String runCommand(
            Path directory, String errorCode, String... command) {
        return new String(runCommandBytes(directory, errorCode, command),
                StandardCharsets.UTF_8);
    }

    private static byte[] runCommandBytes(
            Path directory, String errorCode, String... command) {
        Process process;
        try {
            process = new ProcessBuilder(command).directory(
                    directory.toFile()).redirectErrorStream(true).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                input.transferTo(output);
            }
            require(process.waitFor() == 0, errorCode);
            return output.toByteArray();
        } catch (IOException failure) {
            throw invalid(errorCode, failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw invalid(errorCode, failure);
        }
    }

    private static void expectCode(String code, Runnable action) {
        try {
            action.run();
        } catch (GateFailure failure) {
            require(code.equals(failure.code), HARNESS_ERROR);
            return;
        }
        throw invalid(HARNESS_ERROR);
    }

    private static Fixture validateInput(RankingInput input) {
        RagEvaluationDataset dataset = input.dataset;
        Map<String, RagCandidate10FixtureGenerator.FamilySpec> families =
                input.families;
        List<RetrievalResult> pool = input.pool;
        require(dataset != null && dataset.queries().size() == QUERY_COUNT
                        && dataset.corpusById().size() == CORPUS_COUNT
                        && dataset.qrels().isEmpty()
                        && families.size() == FAMILY_COUNT
                        && pool.size() == CORPUS_COUNT,
                FROZEN_INPUT_ERROR);

        Set<String> queryIds = new LinkedHashSet<>();
        Map<String, List<RagEvaluationDataset.QueryCase>> byFamily =
                new LinkedHashMap<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            require(query != null && query.id() != null
                            && queryIds.add(query.id())
                            && families.containsKey(query.familyId()),
                    FROZEN_INPUT_ERROR);
            byFamily.computeIfAbsent(query.familyId(), ignored ->
                    new ArrayList<>()).add(query);
        }
        require(byFamily.size() == FAMILY_COUNT, FROZEN_INPUT_ERROR);
        byFamily.values().forEach(queries -> require(queries.size() == 2
                        && queries.stream().map(
                        RagEvaluationDataset.QueryCase::language)
                        .collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of("en", "zh")),
                FROZEN_INPUT_ERROR));

        Map<Long, RetrievalResult> poolBySegment = new LinkedHashMap<>();
        for (RetrievalResult result : pool) {
            require(result != null && result.getSegmentId() != null
                            && poolBySegment.putIfAbsent(
                            result.getSegmentId(), result) == null,
                    FROZEN_INPUT_ERROR);
        }
        List<CorpusRow> rows = new ArrayList<>(CORPUS_COUNT);
        Set<String> datasetKeys = new LinkedHashSet<>();
        Set<Long> numericIds = new LinkedHashSet<>();
        Set<Long> documentIds = new LinkedHashSet<>();
        Map<String, Integer> corpusFamilyCounts = new LinkedHashMap<>();
        dataset.corpusById().entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> parseLong(
                        entry.getValue().segmentId(), FROZEN_INPUT_ERROR)))
                .forEach(entry -> {
                    RagEvaluationDataset.CorpusSegment segment = entry.getValue();
                    long segmentId = parseLong(
                            segment.segmentId(), FROZEN_INPUT_ERROR);
                    long documentId = parseLong(
                            segment.documentId(), FROZEN_INPUT_ERROR);
                    RetrievalResult original = poolBySegment.get(segmentId);
                    require(entry.getKey() != null
                                    && datasetKeys.add(entry.getKey())
                                    && numericIds.add(segmentId)
                                    && documentIds.add(documentId)
                                    && original != null
                                    && segment.parentSegmentId() == null
                                    && original.getParentSegmentId() == null
                                    && segmentId == 10_160_001L + rows.size()
                                    && documentId == 10_165_000L + rows.size(),
                            FROZEN_INPUT_ERROR);
                    validateMetadata(segment.metadata(), rows.size() + 1);
                    corpusFamilyCounts.merge(
                            (String) segment.metadata().get("familyId"),
                            1, Integer::sum);
                    String documentName = (String)
                            segment.metadata().get("documentName");
                    require(Objects.equals(original.getDocumentName(), documentName)
                                    && Objects.equals(original.getContent(),
                                    segment.content())
                                    && original.getDocumentId() != null
                                    && original.getDocumentId() == documentId,
                            FROZEN_INPUT_ERROR);
                    rows.add(new CorpusRow(
                            entry.getKey(), segmentId, documentId,
                            documentName, segment.content(),
                            segment.parentSegmentId(), segment.metadata(),
                            original));
                });
        require(rows.size() == CORPUS_COUNT
                        && numericIds.size() == CORPUS_COUNT
                        && documentIds.size() == CORPUS_COUNT
                        && corpusFamilyCounts.keySet().equals(families.keySet())
                        && corpusFamilyCounts.values().stream()
                        .allMatch(count -> count == 56),
                FROZEN_INPUT_ERROR);
        int targetQueryCount = Math.toIntExact(dataset.queries().stream()
                .filter(query -> "target".equals(
                        families.get(query.familyId()).role().wireName()))
                .count());
        require(targetQueryCount == 16, FROZEN_INPUT_ERROR);
        return new Fixture(dataset, families, List.copyOf(rows),
                Map.copyOf(poolBySegment), targetQueryCount,
                corpusProjection(rows));
    }

    private static void validateMetadata(
            Map<String, Object> metadata, int ordinal) {
        require(metadata != null && metadata.keySet().equals(Set.of(
                        "candidate10Role", "documentName", "familyId",
                        "identifierShape", "kbId", "ordinal", "score",
                        "source"))
                        && metadata.get("candidate10Role") instanceof String
                        && metadata.get("documentName") instanceof String name
                        && !name.isBlank()
                        && metadata.get("familyId") instanceof String
                        && metadata.get("identifierShape") instanceof String
                        && metadata.get("kbId") instanceof Long kb
                        && kb == KNOWLEDGE_BASE_ID
                        && metadata.get("ordinal") instanceof Integer actual
                        && actual == ordinal
                        && metadata.get("score") instanceof Number score
                        && Double.compare(score.doubleValue(), 0.0D) == 0
                        && "candidate10-static-fixture-v1".equals(
                        metadata.get("source")),
                FROZEN_INPUT_ERROR);
    }

    private static byte[] corpusProjection(List<CorpusRow> rows) {
        return RagCandidate10FreezeSupport.canonicalJsonBytes(rows.stream()
                .map(row -> projection(row.datasetKey, row.segmentId,
                        row.documentId, KNOWLEDGE_BASE_ID,
                        row.documentName, row.content,
                        row.parentSegmentId))
                .toList());
    }

    private static Map<String, Object> projection(
            String datasetKey, long segmentId, long documentId,
            long knowledgeBaseId, String documentName, String content,
            String parentSegmentId) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("datasetKey", datasetKey);
        value.put("segmentId", segmentId);
        value.put("documentId", documentId);
        value.put("knowledgeBaseId", knowledgeBaseId);
        value.put("documentName", documentName);
        value.put("content", content);
        value.put("parentSegmentId", parentSegmentId);
        return value;
    }

    private static long parseLong(String value, String error) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException failure) {
            throw invalid(error, failure);
        }
    }

    private static void inspectImage(DockerBudget budget) {
        require(budget.imageInspect == 0 && budget.containerConstruct == 0,
                DATABASE_ERROR);
        try {
            var response = DockerClientFactory.instance().client()
                    .inspectImageCmd(IMAGE).exec();
            budget.imageInspect++;
            List<String> digests = response.getRepoDigests();
            require(digests != null && digests.stream().anyMatch(value ->
                            value != null
                                    && value.startsWith("pgvector/pgvector@sha256:")
                                    && value.endsWith(
                                    "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337")),
                    DATABASE_ERROR);
        } catch (NotFoundException missing) {
            throw invalid(DATABASE_ERROR, missing);
        } catch (GateFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalid(DATABASE_ERROR, failure);
        }
    }

    private static SeededInfrastructure seedInfrastructure(
            PostgreSQLContainer<?> container, Fixture fixture) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(),
                container.getPassword());
        JdbcTemplate bootstrap = new JdbcTemplate(dataSource);
        try {
            createSchema(bootstrap);
            List<Object[]> documents = fixture.rows.stream()
                    .map(row -> new Object[]{row.documentId,
                            KNOWLEDGE_BASE_ID, row.documentName})
                    .toList();
            List<Object[]> segments = fixture.rows.stream()
                    .map(row -> new Object[]{row.segmentId, row.documentId,
                            row.content, row.documentName, null,
                            row.ordinal(), null, row.parentSegmentId})
                    .toList();
            bootstrap.batchUpdate("INSERT INTO kmc_document "
                    + "(id,knowledge_base_id,name,del_flag) VALUES (?,?,?,0)",
                    documents);
            bootstrap.batchUpdate("INSERT INTO kmc_document_segment "
                    + "(id,document_id,content,document_name,answer,position,"
                    + "qm_segment_id,parent_id,del_flag) "
                    + "VALUES (?,?,?,?,?,?,?,?,0)", segments);
        } catch (RuntimeException failure) {
            throw invalid(DATABASE_ERROR, failure);
        }
        byte[] postgres = postgresProjection(bootstrap, fixture);
        require(Arrays.equals(fixture.corpusProjection, postgres),
                DATABASE_ERROR);

        SeedCounters counters = new SeedCounters();
        CountingEmbeddingModel embedding = new CountingEmbeddingModel(
                new FeatureHashEmbeddingModel(
                        VECTOR_DIMENSIONS, VECTOR_SEED, VECTOR_VERSION),
                counters);
        SimpleVectorStore store = SimpleVectorStore.builder(embedding).build();
        List<Document> documents = fixture.rows.stream()
                .map(row -> {
                    Map<String, Object> metadata =
                            new LinkedHashMap<>(row.metadata);
                    metadata.put(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID,
                            KNOWLEDGE_BASE_ID);
                    metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID,
                            row.documentId);
                    metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME,
                            row.documentName);
                    metadata.put(WeaviateConstant.METADATA_FIELD_SEGMENT_ID,
                            row.segmentId);
                    return Document.builder().id(row.datasetKey)
                            .text(row.content).metadata(metadata).build();
                }).toList();
        store.add(documents);
        counters.vectorSeedAddCount++;
        require(counters.seedInputs.size() == CORPUS_COUNT,
                ADAPTER_ERROR);
        for (int index = 0; index < CORPUS_COUNT; index++) {
            CorpusRow row = fixture.rows.get(index);
            SeedEmbeddingInput actual = counters.seedInputs.get(index);
            require(row.datasetKey.equals(actual.id)
                            && row.content.equals(actual.text), ADAPTER_ERROR);
        }
        byte[] vector = vectorProjection(store, fixture);
        require(Arrays.equals(fixture.corpusProjection, vector)
                        && Arrays.equals(postgres, vector), ADAPTER_ERROR);
        String projectionSha = RagCandidate10FreezeSupport.sha256(
                fixture.corpusProjection);
        SeedEvidence evidence = new SeedEvidence(
                projectionSha,
                RagCandidate10FreezeSupport.sha256(postgres),
                RagCandidate10FreezeSupport.sha256(vector));
        return new SeededInfrastructure(
                dataSource, bootstrap, store, embedding, counters, evidence);
    }

    private static void createSchema(JdbcTemplate jdbc) {
        for (String sql : List.of(
                "CREATE EXTENSION IF NOT EXISTS pg_trgm",
                "CREATE TABLE kmc_document (id BIGINT PRIMARY KEY, "
                        + "knowledge_base_id BIGINT NOT NULL, name TEXT NOT NULL, "
                        + "del_flag SMALLINT NOT NULL DEFAULT 0)",
                "CREATE TABLE kmc_document_segment (id BIGINT PRIMARY KEY, "
                        + "document_id BIGINT NOT NULL, content TEXT NOT NULL, "
                        + "document_name TEXT, answer TEXT, position INTEGER, "
                        + "qm_segment_id TEXT, parent_id TEXT, "
                        + "del_flag SMALLINT NOT NULL DEFAULT 0, "
                        + "content_tsv tsvector GENERATED ALWAYS AS "
                        + "(to_tsvector('simple', coalesce(content, ''))) STORED)",
                "CREATE TABLE kmc_segment_entity_metadata (id BIGSERIAL PRIMARY KEY, "
                        + "segment_id BIGINT NOT NULL, entities JSONB NOT NULL)",
                "CREATE INDEX kmc_segment_content_tsv_idx ON "
                        + "kmc_document_segment USING GIN(content_tsv)",
                "CREATE INDEX kmc_segment_content_trgm_idx ON "
                        + "kmc_document_segment USING GIN(content gin_trgm_ops)",
                "CREATE INDEX kmc_segment_document_position_idx ON "
                        + "kmc_document_segment(document_id,position,id)",
                "CREATE INDEX kmc_segment_entity_idx ON "
                        + "kmc_segment_entity_metadata USING GIN(entities)",
                "CREATE INDEX kmc_document_kb_active_idx ON "
                        + "kmc_document(knowledge_base_id,id) WHERE del_flag=0",
                "CREATE INDEX kmc_document_name_trgm_active_idx ON "
                        + "kmc_document USING GIN(name gin_trgm_ops) "
                        + "WHERE del_flag=0")) {
            jdbc.execute(sql);
        }
    }

    private static byte[] postgresProjection(
            JdbcTemplate jdbc, Fixture fixture) {
        List<Map<String, Object>> rows = jdbc.query("SELECT s.id AS segment_id, "
                        + "s.document_id, d.knowledge_base_id, d.name AS document_name, "
                        + "s.content, s.parent_id FROM kmc_document_segment s "
                        + "JOIN kmc_document d ON d.id=s.document_id "
                        + "ORDER BY s.id ASC",
                (rs, index) -> {
                    long segmentId = rs.getLong("segment_id");
                    CorpusRow expected = fixture.rowBySegmentId.get(segmentId);
                    require(expected != null, DATABASE_ERROR);
                    return projection(expected.datasetKey, segmentId,
                            rs.getLong("document_id"),
                            rs.getLong("knowledge_base_id"),
                            rs.getString("document_name"),
                            rs.getString("content"),
                            rs.getString("parent_id"));
                });
        require(rows.size() == CORPUS_COUNT
                        && jdbc.queryForObject(
                        "SELECT count(*) FROM kmc_document", Integer.class)
                        == CORPUS_COUNT
                        && jdbc.queryForObject(
                        "SELECT count(*) FROM kmc_document_segment", Integer.class)
                        == CORPUS_COUNT
                        && jdbc.queryForObject(
                        "SELECT count(*) FROM kmc_segment_entity_metadata",
                        Integer.class) == 0,
                DATABASE_ERROR);
        return RagCandidate10FreezeSupport.canonicalJsonBytes(rows);
    }

    @SuppressWarnings("unchecked")
    private static byte[] vectorProjection(
            SimpleVectorStore store, Fixture fixture) {
        Object raw = ReflectionTestUtils.getField(store, "store");
        require(raw instanceof Map<?, ?>, ADAPTER_ERROR);
        Map<String, SimpleVectorStoreContent> snapshot =
                new LinkedHashMap<>((Map<String, SimpleVectorStoreContent>) raw);
        Set<String> expectedKeys = fixture.rows.stream()
                .map(CorpusRow::datasetKey)
                .collect(java.util.stream.Collectors.toSet());
        require(snapshot.size() == CORPUS_COUNT
                        && snapshot.keySet().equals(expectedKeys), ADAPTER_ERROR);
        List<Map<String, Object>> projection = new ArrayList<>(CORPUS_COUNT);
        for (CorpusRow row : fixture.rows) {
            SimpleVectorStoreContent actual = snapshot.get(row.datasetKey);
            require(actual != null && row.datasetKey.equals(actual.getId())
                            && row.content.equals(actual.getText())
                            && actual.getMetadata().size() == 12
                            && actual.getEmbedding().length == VECTOR_DIMENSIONS
                            && allFinite(actual.getEmbedding()), ADAPTER_ERROR);
            Map<String, Object> metadata = actual.getMetadata();
            validateMetadata(metadata.entrySet().stream()
                    .filter(entry -> !entry.getKey().startsWith("kmc_"))
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey, Map.Entry::getValue,
                            (left, right) -> left, LinkedHashMap::new)),
                    row.ordinal());
            require(Objects.equals(metadata.get(
                                    WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID),
                            KNOWLEDGE_BASE_ID)
                            && Objects.equals(metadata.get(
                                    WeaviateConstant.METADATA_FIELD_DOCUMENT_ID),
                            row.documentId)
                            && Objects.equals(metadata.get(
                                    WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME),
                            row.documentName)
                            && Objects.equals(metadata.get(
                                    WeaviateConstant.METADATA_FIELD_SEGMENT_ID),
                            row.segmentId), ADAPTER_ERROR);
            projection.add(projection(actual.getId(), row.segmentId,
                    ((Number) metadata.get(
                            WeaviateConstant.METADATA_FIELD_DOCUMENT_ID)).longValue(),
                    ((Number) metadata.get(
                            WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID)).longValue(),
                    (String) metadata.get(
                            WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME),
                    actual.getText(), null));
        }
        return RagCandidate10FreezeSupport.canonicalJsonBytes(projection);
    }

    private static boolean allFinite(float[] values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static RuntimeAssembly assembleRuntime(
            SeededInfrastructure seeded, CountingExecutor executor,
            Fixture fixture) {
        SeedCounters counters = seeded.counters;
        CaptureState capture = new CaptureState();
        QueryCounters calls = new QueryCounters();
        SqlCounters sql = new SqlCounters();

        JdbcTemplate keywordJdbc = new CountingJdbcTemplate(
                seeded.dataSource, sql, "keyword");
        JdbcTemplate metadataJdbc = new CountingJdbcTemplate(
                seeded.dataSource, sql, "metadata");
        JdbcTemplate contextJdbc = new CountingJdbcTemplate(
                seeded.dataSource, sql, "context");
        JdbcTemplate otherJdbc = new CountingJdbcTemplate(
                seeded.dataSource, sql, "other");

        VectorStore guardedVectorStore = mock(VectorStore.class, invocation -> {
            return switch (invocation.getMethod().getName()) {
                case "similaritySearch" -> {
                    counters.similaritySearch++;
                    yield seeded.store.similaritySearch(
                            (org.springframework.ai.vectorstore.SearchRequest)
                                    invocation.getArgument(0));
                }
                case "add" -> {
                    counters.vectorAdd++;
                    throw invalid(ADAPTER_ERROR);
                }
                case "delete" -> {
                    counters.vectorDelete++;
                    throw invalid(ADAPTER_ERROR);
                }
                case "toString" -> "candidate101-guarded-vector-store";
                default -> throw invalid(ADAPTER_ERROR);
            };
        });
        IAiModelApiService aiModelService = mock(
                IAiModelApiService.class, invocation -> {
                    if ("getEmbeddingModel".equals(
                            invocation.getMethod().getName())) {
                        counters.embeddingModelResolve++;
                        require(Objects.equals(invocation.getArgument(0), 1L)
                                        && "feature-hash-v1".equals(
                                        invocation.getArgument(1)),
                                ADAPTER_ERROR);
                        return seeded.embedding;
                    }
                    throw invalid(ADAPTER_ERROR);
                });
        IVectorStoreService vectorStoreService = mock(
                IVectorStoreService.class, invocation -> {
                    if ("getVectorStore".equals(
                            invocation.getMethod().getName())) {
                        counters.vectorStoreResolve++;
                        require(invocation.getArgument(0) == seeded.embedding,
                                ADAPTER_ERROR);
                        return guardedVectorStore;
                    }
                    throw invalid(ADAPTER_ERROR);
                });
        KmcKnowledgeBaseDO knowledgeBase = KmcKnowledgeBaseDO.builder()
                .id(KNOWLEDGE_BASE_ID)
                .embeddingModelProvider("1")
                .embeddingModel("feature-hash-v1")
                .rerankingEnable(false)
                .build();
        KmcKnowledgeBaseMapper mapper = mock(
                KmcKnowledgeBaseMapper.class, invocation -> {
                    if ("selectById".equals(invocation.getMethod().getName())) {
                        counters.kbMapperLookup++;
                        require(Objects.equals(invocation.getArgument(0),
                                KNOWLEDGE_BASE_ID), ADAPTER_ERROR);
                        return knowledgeBase;
                    }
                    throw invalid(ADAPTER_ERROR);
                });

        CapturingVectorRetriever vector = new CapturingVectorRetriever(
                capture, calls);
        ReflectionTestUtils.setField(vector, "aiModelService", aiModelService);
        ReflectionTestUtils.setField(vector, "vectorStoreService",
                vectorStoreService);
        ReflectionTestUtils.setField(vector, "kmcKnowledgeBaseMapper", mapper);
        ReflectionTestUtils.setField(vector, "jdbcTemplate",
                new CountingJdbcTemplate(seeded.dataSource, sql, "vector"));
        ReflectionTestUtils.setField(vector, "vecSimRescoreEnabled", false);

        CapturingKeywordRetriever keyword = new CapturingKeywordRetriever(
                capture, calls);
        ReflectionTestUtils.setField(keyword, "jdbcTemplate", keywordJdbc);
        ReflectionTestUtils.setField(keyword, "identifierAware", false);
        CapturingMetadataRetriever metadata = new CapturingMetadataRetriever(
                capture, calls);
        ReflectionTestUtils.setField(metadata, "jdbcTemplate", metadataJdbc);

        GraphRagProperties graphProperties = new GraphRagProperties();
        graphProperties.setEnabled(false);
        graphProperties.setPprEnabled(false);
        CapturingGraphRetriever graph = new CapturingGraphRetriever(
                capture, calls);
        ReflectionTestUtils.setField(graph, "jdbcTemplate", otherJdbc);
        ReflectionTestUtils.setField(graph, "properties", graphProperties);
        ReflectionTestUtils.setField(graph, "cypherSafetyValidator",
                new CypherSafetyValidator());

        IChatModelService chatModels = mock(
                IChatModelService.class, invocation -> {
                    counters.chatModelResolve++;
                    throw invalid(ADAPTER_ERROR);
                });
        QueryEntityExtractionService.QueryEntityConfig entityConfig =
                new QueryEntityExtractionService.QueryEntityConfig();
        entityConfig.setEnabled(false);
        CapturingEntityExtraction entity = new CapturingEntityExtraction(
                chatModels, entityConfig, calls);

        CapturingFusionService fusion = new CapturingFusionService(
                capture, calls);
        ReflectionTestUtils.setField(fusion, "rrfK", 60);
        ReflectionTestUtils.setField(fusion, "weakPathThreshold", 0.0D);

        RunAudit rerankAudit = new RunAudit();
        CounterfactualRerankService rerank =
                counterfactualService(rerankAudit);
        CapturingContextBuilder context = new CapturingContextBuilder(
                capture, calls, sql);
        ReflectionTestUtils.setField(context, "jdbcTemplate", contextJdbc);
        ReflectionTestUtils.setField(context, "maxContextBytes", 20_000);
        ReflectionTestUtils.setField(context, "maxContextTokens", 0);

        RagRetrievalService service = new RagRetrievalService();
        ReflectionTestUtils.setField(service, "vectorRetriever", vector);
        ReflectionTestUtils.setField(service, "keywordRetriever", keyword);
        ReflectionTestUtils.setField(service, "metadataRetriever", metadata);
        ReflectionTestUtils.setField(service, "graphRagRetriever", graph);
        ReflectionTestUtils.setField(service, "candidateFusionService", fusion);
        ReflectionTestUtils.setField(service, "ragRerankService", rerank);
        ReflectionTestUtils.setField(service, "ragContextBuilder", context);
        ReflectionTestUtils.setField(service, "kmcKnowledgeBaseMapper", mapper);
        ReflectionTestUtils.setField(service,
                "queryEntityExtractionService", entity);
        ReflectionTestUtils.setField(service, "retrievalExecutor", executor);

        DynamicTopKConfig dynamicTopK = new DynamicTopKConfig();
        dynamicTopK.setEnabled(false);

        require(!((Boolean) ReflectionTestUtils.getField(
                        vector, "vecSimRescoreEnabled"))
                        && !((Boolean) ReflectionTestUtils.getField(
                        keyword, "identifierAware"))
                        && (Boolean) ReflectionTestUtils.getField(
                        rerank, "identifierConsistencyEnabled")
                        && ((Integer) ReflectionTestUtils.getField(
                        fusion, "rrfK")) == 60
                        && Double.compare((Double) ReflectionTestUtils.getField(
                        fusion, "weakPathThreshold"), 0.0D) == 0
                        && context.getMaxContextBytes() == 20_000,
                COMMAND_ERROR);
        Map<String, Object> configuration = configurationReadback(
                dynamicTopK, entityConfig, vector, keyword, rerank, fusion,
                context, graphProperties, rerankAudit);
        return new RuntimeAssembly(service, new QueryIntentAnalyzer(), capture,
                calls, sql, counters, rerankAudit, context, graphProperties,
                entityConfig, dynamicTopK, configuration);
    }

    private static List<QueryTrace> runQueries(
            Fixture fixture, RuntimeAssembly assembly,
            CountingExecutor executor) throws Exception {
        require(RagFallbackMonitor.currentScope() == null
                        && !JiebaNative.isAvailable()
                        && !ColbertNative.isAvailable()
                        && !VecSimNative.isAvailable(), COMMAND_ERROR);
        List<QueryTrace> traces = new ArrayList<>(QUERY_COUNT);
        for (RagEvaluationDataset.QueryCase query : fixture.dataset.queries()) {
            List<String> identifiers =
                    RagCandidate10DiagnosticSupport.identifierTerms(
                            query.retrievalQuery());
            Set<String> exactKeys = fixture.rows.stream()
                    .filter(row ->
                            RagCandidate10DiagnosticSupport.matchesAllIdentifiers(
                                    row.documentName, identifiers))
                    .map(row -> "seg:" + row.segmentId)
                    .collect(java.util.stream.Collectors.toCollection(
                            LinkedHashSet::new));
            require(fixture.allSegmentKeys.containsAll(exactKeys),
                    MAPPING_ERROR);
            QueryCapture capture = assembly.capture.begin(
                    query.id(), identifiers.size(), Set.copyOf(exactKeys));
            assembly.rerankAudit.beginQuery();
            int futureOffset = executor.futures.size();
            long failedOffset = executor.failed.get();
            long rejectedOffset = executor.rejected.get();
            long sqlFailedOffset = assembly.sql.failed;
            require(RagFallbackMonitor.currentScope() == null,
                    FALLBACK_ERROR);
            RagResult result;
            assembly.calls.queryIntentAnalyze++;
            QueryIntent intent = assembly.intentAnalyzer.analyze(
                    query.retrievalQuery());
            Object enhancement = queryEnhancement(query.retrievalQuery());
            try (RagFallbackMonitor.Scope ignored =
                         RagFallbackMonitor.openScope()) {
                assembly.calls.retrieveOnce++;
                result = invokeRetrieveOnce(
                        assembly.service, intent, enhancement);
                executor.awaitIdle();
                List<Future<?>> queryFutures = executor.futures.subList(
                        futureOffset, executor.futures.size());
                require(queryFutures.size() == 5
                                && queryFutures.stream().allMatch(Future::isDone)
                                && queryFutures.stream().noneMatch(
                                Future::isCancelled)
                                && executor.failed.get() == failedOffset
                                && executor.rejected.get() == rejectedOffset,
                        EXECUTOR_ERROR);
                require(assembly.sql.failed == sqlFailedOffset,
                        FALLBACK_ERROR);
                requireExpectedJiebaFallback();
            }
            require(RagFallbackMonitor.currentScope() == null,
                    FALLBACK_ERROR);
            require(result != null && result.getSources() != null,
                    RUNTIME_ERROR);
            capture.contextRendered = freezeContext(
                    result.getContext(), fixture);
            capture.contextText = result.getContext();
            capture.candidate3Sources = freezeKeys(result.getSources());
            capture.rerank = assembly.rerankAudit.querySnapshot();
            capture.validateFusion();
            traces.add(capture.freeze());
            assembly.capture.clear(capture);
        }
        require(assembly.capture.current == null, MAPPING_ERROR);
        require(executor.submitAttempt.get() == 200
                        && executor.accepted.get() == 200
                        && executor.callableSubmit.get() == 200
                        && executor.runnableSubmit.get() == 0
                        && executor.started.get() == 200
                        && executor.succeeded.get() == 200
                        && executor.completed.get() == 200
                        && executor.failed.get() == 0
                        && executor.rejected.get() == 0
                        && executor.futures.size() == 200
                        && executor.futures.stream().allMatch(Future::isDone)
                        && executor.futures.stream().noneMatch(
                        Future::isCancelled), EXECUTOR_ERROR);
        return List.copyOf(traces);
    }

    private static Object queryEnhancement(String query) throws Exception {
        Class<?> type = Class.forName(RagRetrievalService.class.getName()
                + "$QueryEnhancement");
        Constructor<?> constructor = type.getDeclaredConstructor(
                String.class, String.class, List.class, List.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                query, "single", List.of(query), List.of(query), false);
    }

    private static RagResult invokeRetrieveOnce(
            RagRetrievalService service, QueryIntent intent,
            Object enhancement) throws Exception {
        Method method = Arrays.stream(
                        RagRetrievalService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("retrieveOnce")
                        && candidate.getParameterCount() == 8)
                .findFirst().orElseThrow();
        method.setAccessible(true);
        return (RagResult) invoke(method, service,
                KNOWLEDGE_BASE_ID, intent, enhancement, BUSINESS_LIMIT,
                false, new LinkedHashMap<String, Object>(), "single",
                QueryRouter.QueryRoute.MEDIUM);
    }

    private static void requireExpectedJiebaFallback()
            throws ReflectiveOperationException {
        Map<String, Object> snapshot = new LinkedHashMap<>(
                RagFallbackMonitor.currentScopeSnapshot());
        Object raw = snapshot.remove("jni");
        require(raw instanceof Map<?, ?> event
                        && event.keySet().equals(Set.of(
                        "count", "lastFallback", "lastReason", "lastAt"))
                        && Objects.equals(event.get("count"), 1L)
                        && "java_tokenization".equals(event.get("lastFallback"))
                        && "jieba native unavailable".equals(
                        event.get("lastReason"))
                        && event.get("lastAt") instanceof String lastAt
                        && !lastAt.isBlank(), FALLBACK_ERROR);
        invokeStage("requireNoScopedFallback", snapshot);
    }

    private static Set<String> freezeContext(
            String context, Fixture fixture) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) invokeStage(
                "contextSegmentIds", context);
        Set<String> result = new LinkedHashSet<>();
        for (Long id : ids) {
            require(id != null && fixture.rowBySegmentId.containsKey(id)
                            && result.add("seg:" + id), CONTEXT_ERROR);
        }
        return Set.copyOf(result);
    }

    private static Set<String> freezeKeys(List<RetrievalResult> values) {
        Set<String> keys = new LinkedHashSet<>();
        for (RetrievalResult value : values) {
            require(value != null && value.getSegmentId() != null
                            && keys.add(segmentKey(value)), MAPPING_ERROR);
        }
        return Set.copyOf(keys);
    }

    private static List<String> orderedKeys(List<RetrievalResult> values) {
        require(values != null, MAPPING_ERROR);
        List<String> keys = values.stream()
                .map(RagCandidate102AEvidenceBaselineV2Test
                        ::segmentKey)
                .toList();
        require(new LinkedHashSet<>(keys).size() == keys.size(),
                MAPPING_ERROR);
        return keys;
    }

    private static List<Set<String>> freezePathKeys(
            List<List<RetrievalResult>> paths) {
        require(paths != null, MAPPING_ERROR);
        List<Set<String>> frozen = new ArrayList<>(paths.size());
        for (List<RetrievalResult> path : paths) {
            require(path != null, MAPPING_ERROR);
            frozen.add(freezeKeys(path));
        }
        return List.copyOf(frozen);
    }

    private static void requireExecutorBeforeFreeze(CountingExecutor executor) {
        executor.awaitIdle();
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        require(executor.getActiveCount() == 0
                        && executor.getQueueSize() == 0
                        && pool.getQueue().remainingCapacity() == 32,
                EXECUTOR_ERROR);
        executor.activeBeforeFreeze = executor.getActiveCount();
        executor.queuedBeforeFreeze = executor.getQueueSize();
        executor.remainingBeforeFreeze =
                pool.getQueue().remainingCapacity();
    }

    private static Evaluation evaluateBaseline(
            Fixture fixture, RagEvaluationDataset labeled,
            List<QueryTrace> traces) {
        require(labeled.queries().equals(fixture.dataset.queries())
                        && labeled.corpusById().equals(
                        fixture.dataset.corpusById())
                        && traces.size() == QUERY_COUNT,
                QREL_ERROR);
        Map<String, QueryTrace> traceByQuery = traces.stream()
                .collect(java.util.stream.Collectors.toMap(
                        QueryTrace::queryId, value -> value,
                        (left, right) -> left, LinkedHashMap::new));
        Map<String, Map<String, Integer>> qrels = labeled.qrels();
        int qrelPairs = 0;
        int mappedPairs = 0;
        int relevantExactPairs = 0;
        int nonExactPairs = 0;
        int invalidIdentifierCardinality = 0;
        int nonMonotonic = 0;
        Map<String, Integer> boundaries = zeroBoundaries();
        Map<String, PresenceAccumulator> presence = new LinkedHashMap<>();
        CHECKPOINT_NAMES.forEach(name -> presence.put(
                name, new PresenceAccumulator()));
        ContextAccumulator context = new ContextAccumulator();

        Map<String, List<String>> perPathHashes = new LinkedHashMap<>();
        CHECKPOINT_NAMES.forEach(name -> perPathHashes.put(
                name, new ArrayList<>()));
        for (RagEvaluationDataset.QueryCase query : labeled.queries()) {
            QueryTrace trace = traceByQuery.get(query.id());
            require(trace != null, MAPPING_ERROR);
            Set<String> exactKeys = trace.exactKeys;
            require(fixture.allSegmentKeys.containsAll(exactKeys),
                    MAPPING_ERROR);
            Set<String> relevantKeys = new LinkedHashSet<>();
            Map<String, Integer> grades = qrels.getOrDefault(
                    query.id(), Map.of());
            for (Map.Entry<String, Integer> grade : grades.entrySet()) {
                if (grade.getValue() == null || grade.getValue() <= 0) {
                    continue;
                }
                qrelPairs++;
                CorpusRow row = fixture.rowByDatasetKey.get(grade.getKey());
                require(row != null, MAPPING_ERROR);
                mappedPairs++;
                String key = "seg:" + row.segmentId;
                relevantKeys.add(key);
                if (exactKeys.contains(key)) {
                    relevantExactPairs++;
                } else {
                    nonExactPairs++;
                }
            }
            Set<String> relevantExact = intersection(relevantKeys, exactKeys);
            boolean target = "target".equals(
                    fixture.families.get(query.familyId()).role().wireName());
            for (String checkpoint : CHECKPOINT_NAMES) {
                Set<String> visible = intersection(
                        relevantExact, trace.stages.get(checkpoint));
                PresenceAccumulator accumulator = presence.get(checkpoint);
                accumulator.pairs += visible.size();
                if (target && !visible.isEmpty()) {
                    accumulator.targetQueries++;
                }
                perPathHashes.get(checkpoint).add(
                        RagCandidate10FreezeSupport.sha256(
                                RagCandidate10FreezeSupport.canonicalJsonBytes(
                                        trace.stages.get(checkpoint).stream()
                                                .sorted().toList())));
            }
            nonMonotonic += validateLifecycle(
                    fixture.allSegmentKeys, trace.monotonicStages());
            accumulateContext(context, trace, exactKeys);
            if (target) {
                String boundary = earliestBoundary(relevantExact, trace);
                boundaries.compute(boundary, (ignored, value) -> value + 1);
                if ("NOT_RETRIEVED".equals(boundary)
                        && (trace.identifierCardinality < 1
                        || trace.identifierCardinality > 2)) {
                    invalidIdentifierCardinality++;
                }
            }
        }
        require(qrelPairs == mappedPairs
                        && qrelPairs == relevantExactPairs + nonExactPairs
                        && nonMonotonic == 0
                        && boundaries.values().stream()
                        .mapToInt(Integer::intValue).sum() == 16,
                MAPPING_ERROR);
        requireContextIdentities(context.counts());
        require(context.modeCounts.values().stream()
                        .mapToLong(Long::longValue).sum() == QUERY_COUNT
                        && context.parentQueries
                        == context.modeCounts.get("PARENT")
                        + context.modeCounts.get("PARENT_FALLBACK_ADJACENT")
                        && context.adjacentQueries
                        == context.modeCounts.get("ADJACENT")
                        + context.modeCounts.get("PARENT_FALLBACK_ADJACENT")
                        && context.totalQueries == context.parentQueries
                        + context.adjacentQueries
                        && context.totalQueries <= 80,
                CONTEXT_ERROR);

        Map<String, String> pathHashes = new LinkedHashMap<>();
        CHECKPOINT_NAMES.forEach(name -> pathHashes.put(name,
                RagCandidate10FreezeSupport.sha256(
                        RagCandidate10FreezeSupport.canonicalJsonBytes(
                                perPathHashes.get(name)))));
        require(pathHashes.get("postFilter").equals(
                        pathHashes.get("colbertInput")), MAPPING_ERROR);
        return new Evaluation(qrelPairs, mappedPairs, relevantExactPairs,
                nonExactPairs, Map.copyOf(presence), context.freeze(),
                Map.copyOf(boundaries), Map.copyOf(pathHashes),
                invalidIdentifierCardinality);
    }

    private static Set<String> intersection(
            Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return Set.copyOf(result);
    }

    private static String earliestBoundary(
            Set<String> relevantExact, QueryTrace trace) {
        if (relevantExact.isEmpty()) {
            return "NO_RELEVANT_EXACT";
        }
        if (intersection(relevantExact,
                trace.stages.get("retrieverUnion")).isEmpty()) {
            return "NOT_RETRIEVED";
        }
        if (intersection(relevantExact,
                trace.stages.get("weakPathEligibleUnion")).isEmpty()) {
            return "WEAK_PATH_EXCLUDED";
        }
        if (intersection(relevantExact,
                trace.stages.get("fused")).isEmpty()) {
            return "FUSION_NOT_PRESERVED";
        }
        if (intersection(relevantExact,
                trace.stages.get("postFilter")).isEmpty()) {
            return "FILTER_NOT_PRESERVED";
        }
        if (intersection(relevantExact,
                trace.stages.get("colbertTop90")).isEmpty()) {
            return "COLBERT_FRONTIER_NOT_PRESERVED";
        }
        return "COLBERT90_VISIBLE";
    }

    private static void accumulateContext(
            ContextAccumulator accumulator, QueryTrace trace,
            Set<String> exact) {
        Set<String> source = trace.contextSources;
        Set<String> rendered = trace.contextRendered;
        Set<String> visible = intersection(source, rendered);
        Set<String> omitted = new LinkedHashSet<>(source);
        omitted.removeAll(rendered);
        Set<String> introduced = new LinkedHashSet<>(rendered);
        introduced.removeAll(source);
        accumulator.sourceSegmentCount += source.size();
        accumulator.sourceExactCount += intersection(source, exact).size();
        accumulator.contextSegmentCount += rendered.size();
        accumulator.contextSourceVisibleCount += visible.size();
        accumulator.contextSourceOmittedCount += omitted.size();
        accumulator.contextIntroducedCount += introduced.size();
        accumulator.contextSourceExactVisibleCount +=
                intersection(visible, exact).size();
        accumulator.contextSourceExactOmittedCount +=
                intersection(omitted, exact).size();
        accumulator.contextIntroducedExactCount +=
                intersection(introduced, exact).size();
        accumulator.contextExactCount += intersection(rendered, exact).size();
        accumulator.modeCounts.compute(trace.expansionMode,
                (ignored, count) -> count + 1L);
        accumulator.parentQueries += trace.contextParentQueries;
        accumulator.adjacentQueries += trace.contextAdjacentQueries;
        accumulator.totalQueries += trace.contextParentQueries
                + trace.contextAdjacentQueries;
    }

    private static Map<String, Object> accessView(AccessCounter access) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("selectionNonQrelResourceAccessCount",
                access.selectionNonQrelResourceAccessCount);
        value.put("qrelResourceAccessBeforeRanking",
                access.qrelResourceAccessBeforeRanking);
        value.put("qrelResourceAccessCount",
                access.qrelResourceAccessCount);
        value.put("holdoutPathOperationCount",
                access.holdoutPathOperationCount);
        return Map.copyOf(value);
    }

    private static void requireAccess(
            AccessCounter access, boolean frozen, int qrelCount) {
        require(access.rankingFrozen == frozen
                        && access.selectionNonQrelResourceAccessCount == 3
                        && access.qrelResourceAccessBeforeRanking == 0
                        && access.qrelResourceAccessCount == qrelCount
                        && access.holdoutPathOperationCount == 0,
                QREL_ERROR);
    }

    private static Map<String, Object> infrastructureView(
            DockerBudget docker, CountingExecutor executor,
            SeededInfrastructure seeded) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("docker", docker.view());
        value.put("executor", executor != null
                ? executor.view() : Map.of());
        value.put("seed", seeded != null
                ? seeded.seedView() : Map.of());
        return Map.copyOf(value);
    }

    private static Map<String, Object> emptyInfrastructure() {
        return Map.of(
                "docker", new DockerBudget().view(),
                "executor", Map.of("terminatedAfterCleanup", false),
                "seed", Map.of("available", false));
    }

    private static Map<String, Object> budgetView(
            RuntimeAssembly assembly, CountingExecutor executor,
            List<QueryTrace> traces) {
        RunAudit audit = assembly.rerankAudit;
        QueryCounters calls = assembly.calls;
        SeedCounters adapters = assembly.counters;
        requirePerQueryCapacity(traces, audit);
        require(traces.size() == QUERY_COUNT
                        && calls.queryIntentAnalyze == QUERY_COUNT
                        && calls.retrieveOnce == QUERY_COUNT
                        && calls.queryEntityExtract == QUERY_COUNT
                        && calls.keywordRetriever == QUERY_COUNT
                        && calls.vectorRetriever == QUERY_COUNT
                        && calls.metadataRetriever == QUERY_COUNT
                        && calls.graphRetriever == QUERY_COUNT
                        && calls.fusion == QUERY_COUNT
                        && audit.counterfactualRerankCalls.get() == QUERY_COUNT
                        && audit.filterCalls.get() == QUERY_COUNT
                        && audit.colbertCalls.get() == QUERY_COUNT
                        && audit.admissionCalls.get() == QUERY_COUNT
                        && audit.candidate3Calls.get() == QUERY_COUNT
                        && calls.context == QUERY_COUNT
                        && audit.colbertRequestedTopK.size() == QUERY_COUNT
                        && audit.colbertRequestedTopK.stream()
                        .allMatch(value -> value == CANDIDATE_TOP_K)
                        && audit.colbertOutputDocumentCount.stream()
                        .allMatch(value -> value == CANDIDATE_TOP_K)
                        && adapters.kbMapperLookup == 80
                        && adapters.embeddingModelResolve == QUERY_COUNT
                        && adapters.vectorStoreResolve == QUERY_COUNT
                        && adapters.similaritySearch == QUERY_COUNT
                        && adapters.documentEmbeddingAfterSeed == 0
                        && adapters.queryEmbeddingAfterSeed == QUERY_COUNT
                        && adapters.vectorAdd == 0 && adapters.vectorDelete == 0
                        && adapters.chatModelResolve == 0
                        && adapters.chatModelCall == 0,
                BUDGET_ERROR);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("limits", limitsView());
        value.put("configuration", configurationView(assembly));
        value.put("calls", callsView(calls, audit));
        value.put("adapters", adapters.adapterView());
        value.put("databaseQueries", assembly.sql.view());
        value.put("fallback", Map.of(
                "scopeOpenCount", QUERY_COUNT,
                "scopeVerificationCount", QUERY_COUNT,
                "expectedJavaTokenizationEventCount", QUERY_COUNT,
                "unexpectedScopedEventCount", 0,
                "sqlFailureCount", 0));
        value.put("externalCalls", Map.of(
                "externalEmbedding", 0,
                "vectorDatabase", 0,
                "graphExternal", 0,
                "remoteReranker", 0,
                "localReranker", 0,
                "network", 0,
                "llm", 0));
        return Map.copyOf(value);
    }

    private static Map<String, Object> limitsView() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("businessOutputLimit", 30);
        value.put("candidateTopK", 90);
        value.put("colbertRequestedTopK", 90);
        value.put("admissionScanMaxRank", 90);
        value.put("candidate3RequestedTopK", 30);
        value.put("perPathTopK", 90);
        value.put("retrieverPathCount", 4);
        value.put("fusedCandidateMax", 360);
        value.put("colbertInputMin", 90);
        value.put("colbertInputMax", 360);
        value.put("colbertOutputCountPerQuery", 90);
        value.put("admissionOutputCountPerQuery", 30);
        value.put("candidate3OutputCountPerQuery", 30);
        value.put("queryVariantsPerQuery", 1);
        value.put("contextMaxBytes", 20_000);
        value.put("contextMaxTokens", 0);
        return Map.copyOf(value);
    }

    private static Map<String, Object> configurationView(
            RuntimeAssembly assembly) {
        require(assembly.configuration.size() == 24,
                COMMAND_ERROR);
        return assembly.configuration;
    }

    private static Map<String, Object> configurationReadback(
            DynamicTopKConfig dynamicTopK,
            QueryEntityExtractionService.QueryEntityConfig entityConfig,
            VectorRetriever vector, KeywordRetriever keyword,
            RagRerankService rerank, CandidateFusionService fusion,
            RagContextBuilder context, GraphRagProperties graph,
            RunAudit audit) {
        RagCandidate10DiagnosticSupport.Eligibility eligibility =
                new RagCandidate10DiagnosticSupport.Eligibility(
                        true, true, true);
        require(eligibility.requestActive(), COMMAND_ERROR);
        ColbertScorer.ColbertConfig colbert = audit.colbertConfig;
        require(colbert != null && audit.colbertScorer != null,
                COMMAND_ERROR);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("dynamicTopKEnabled", dynamicTopK.isEnabled());
        value.put("queryEntityEnabled", entityConfig.isEnabled());
        value.put("identifierConsistencyEnabled",
                ReflectionTestUtils.getField(
                        rerank, "identifierConsistencyEnabled"));
        value.put("rrfK", ReflectionTestUtils.getField(fusion, "rrfK"));
        value.put("weakPathThreshold",
                ReflectionTestUtils.getField(fusion, "weakPathThreshold"));
        value.put("identifierAware",
                ReflectionTestUtils.getField(keyword, "identifierAware"));
        value.put("vecSimRescoreEnabled",
                ReflectionTestUtils.getField(vector, "vecSimRescoreEnabled"));
        value.put("graphEnabled", graph.isEnabled());
        value.put("graphPprEnabled", graph.isPprEnabled());
        value.put("contextMaxBytes", context.getMaxContextBytes());
        value.put("contextMaxTokens",
                ReflectionTestUtils.getField(context, "maxContextTokens"));
        value.put("colbertEnabled", colbert.isEnabled());
        value.put("colbertDimensions", colbert.getDimensions());
        value.put("colbertMaxTokensPerDoc", colbert.getMaxTokensPerDoc());
        value.put("colbertNgramSize", colbert.getNgramSize());
        value.put("colbertEmbeddingPlatform", colbert.getEmbeddingPlatform());
        value.put("colbertEmbeddingBaseUrl", colbert.getEmbeddingBaseUrl());
        value.put("colbertEmbeddingApiKey", colbert.getEmbeddingApiKey());
        value.put("colbertEmbeddingModel", colbert.getEmbeddingModel());
        value.put("colbertEmbeddingServicePresent",
                ReflectionTestUtils.getField(
                        audit.colbertScorer, "embeddingService") != null);
        value.put("vectorEmbeddingDimensions", VECTOR_DIMENSIONS);
        value.put("vectorEmbeddingSeed", (int) VECTOR_SEED);
        value.put("vectorEmbeddingVersion", VECTOR_VERSION);
        value.put("eligibility", Map.of(
                "candidate10DiagnosticArm",
                eligibility.candidate10DiagnosticArm(),
                "candidate3Enabled", eligibility.candidate3Enabled(),
                "deterministicPath", eligibility.deterministicPath()));
        require(value.size() == 24 && value.values().stream()
                        .noneMatch(Objects::isNull)
                        && value.equals(expectedConfiguration()),
                COMMAND_ERROR);
        return Map.copyOf(value);
    }

    private static Map<String, Object> expectedConfiguration() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("dynamicTopKEnabled", false);
        value.put("queryEntityEnabled", false);
        value.put("identifierConsistencyEnabled", true);
        value.put("rrfK", 60);
        value.put("weakPathThreshold", 0.0D);
        value.put("identifierAware", false);
        value.put("vecSimRescoreEnabled", false);
        value.put("graphEnabled", false);
        value.put("graphPprEnabled", false);
        value.put("contextMaxBytes", 20_000);
        value.put("contextMaxTokens", 0);
        value.put("colbertEnabled", true);
        value.put("colbertDimensions", 64);
        value.put("colbertMaxTokensPerDoc", 128);
        value.put("colbertNgramSize", 3);
        value.put("colbertEmbeddingPlatform", "");
        value.put("colbertEmbeddingBaseUrl", "");
        value.put("colbertEmbeddingApiKey", "");
        value.put("colbertEmbeddingModel", "");
        value.put("colbertEmbeddingServicePresent", false);
        value.put("vectorEmbeddingDimensions", VECTOR_DIMENSIONS);
        value.put("vectorEmbeddingSeed", (int) VECTOR_SEED);
        value.put("vectorEmbeddingVersion", VECTOR_VERSION);
        value.put("eligibility", Map.of(
                "candidate10DiagnosticArm", true,
                "candidate3Enabled", true,
                "deterministicPath", true));
        return Map.copyOf(value);
    }

    private static Map<String, Object> callsView(
            QueryCounters calls, RunAudit audit) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("queryIntentAnalyze", calls.queryIntentAnalyze);
        value.put("retrieveOnce", calls.retrieveOnce);
        value.put("queryEntityExtract", calls.queryEntityExtract);
        value.put("keywordRetriever", calls.keywordRetriever);
        value.put("vectorRetriever", calls.vectorRetriever);
        value.put("metadataRetriever", calls.metadataRetriever);
        value.put("graphRetriever", calls.graphRetriever);
        value.put("fusion", calls.fusion);
        value.put("counterfactualRerank",
                audit.counterfactualRerankCalls.get());
        value.put("filter", audit.filterCalls.get());
        value.put("colbert", audit.colbertCalls.get());
        value.put("admission", audit.admissionCalls.get());
        value.put("candidate3", audit.candidate3Calls.get());
        value.put("context", calls.context);
        return Map.copyOf(value);
    }

    private static void requireCommandProperties(String mode) {
        Map<String, String> actual = new LinkedHashMap<>();
        expectedCommandProperties(mode).keySet().forEach(key ->
                actual.put(key, System.getProperty(key)));
        requireCommandProperties(mode, actual);
    }

    private static void requireCommandProperties(
            String mode, Map<String, String> actual) {
        require(expectedCommandProperties(mode).equals(actual),
                COMMAND_ERROR);
    }

    private static Map<String, String> expectedCommandProperties(
            String mode) {
        require(Set.of("contracts", "freeze", "selection").contains(mode),
                COMMAND_ERROR);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put(ENABLE_PROPERTY, mode);
        expected.put("rag.eval.candidate10.freeze", "false");
        expected.put("rag.eval.candidate10.diagnostic", "true");
        expected.put("rag.eval.candidate10.diagnostic-arm", "true");
        expected.put("rag.eval.candidate10.environment-qualification", "false");
        expected.put("rag.eval.shadow", "false");
        expected.put("rag.eval.shadow.compare-stable", "false");
        expected.put("rag.eval.identifier.diagnostic", "false");
        expected.put("rag.eval.candidate2.diagnostic", "false");
        expected.put("rag.eval.candidate3.diagnostic", "false");
        expected.put("rag.eval.candidate4.diagnostic", "false");
        expected.put("rag.eval.candidate5.diagnostic", "false");
        expected.put("rag.eval.candidate6.diagnostic", "false");
        expected.put("rag.eval.candidate8.diagnostic", "false");
        expected.put("rag.eval.candidate9.diagnostic", "false");
        expected.put("rag.eval.candidate9.recovery", "false");
        expected.put("rag.eval.promotion", "false");
        expected.put("rag.eval.live", "false");
        expected.put("qknow.rag.dynamic-top-k.enabled", "false");
        expected.put("qknow.rag.query-entity.enabled", "false");
        expected.put("qknow.rag.rrf.k", "60");
        expected.put("qknow.rag.rrf.weak-path-threshold", "0");
        expected.put("qknow.rag.graph.enabled", "false");
        expected.put("qknow.rag.vector.vecsim-rescore-enabled", "false");
        expected.put("qknow.rag.keyword.identifier-aware", "false");
        expected.put("qknow.rag.rerank.identifier-consistency-enabled", "true");
        expected.put("qknow.rag.local-reranker.enabled", "false");
        expected.put("qknow.rag.onnx-reranker.enabled", "false");
        expected.put("hermes.rag.colbert.enabled", "true");
        expected.put("hermes.rag.colbert.ngram-size", "3");
        expected.put("hermes.rag.colbert.dimensions", "64");
        expected.put("hermes.rag.colbert.max-tokens-per-doc", "128");
        expected.put("hermes.rag.colbert.embedding-platform", "");
        expected.put("hermes.rag.colbert.embedding-base-url", "");
        expected.put("hermes.rag.colbert.embedding-api-key", "");
        expected.put("hermes.rag.colbert.embedding-model", "");
        expected.put("hermes.rag.context.max-bytes", "20000");
        expected.put("hermes.rag.context.max-tokens", "0");
        return Map.copyOf(expected);
    }

    private static byte[] commandsBytes() {
        String script = """
                #!/bin/sh
                set -eu
                repo='/Users/achilles/Documents/许子祺/Agent'
                backend="$repo/backend"
                tests="$backend/tests"
                java_home='/Users/achilles/.jdks/candidate10-temurin-17.0.19+10/Contents/Home'
                java_path="$java_home/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin"
                test_class='tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test'
                attempt="$tests/target/rag-eval/candidate102a-evidence-baseline-v2/attempt-002"
                reports="$tests/target/surefire-reports"
                archive_parent="$tests/evidence/candidate102a-evidence-baseline-v2"
                archive="$archive_parent/attempt-002"
                contracts_lock="$archive_parent/.attempt-002.contracts-started"
                freeze_lock="$archive_parent/.attempt-002.freeze-started"
                selection_lock="$archive_parent/.attempt-002.selection-started"
                consumed_authorizations="$tests/target/rag-eval/candidate102a-evidence-baseline-v2/.attempt-002-selection-authorizations-consumed"
                plan="$repo/plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md"
                plan_sha='f8c4e0fc5ae520af0c02cffef4171e16a0202374d7e489742fb2ead56ce2c774'
                failed_name='attempt-'
                failed_name="${failed_name}001-failed-contracts"
                failed="$archive_parent/$failed_name"

                ensure_relative_parent() {
                  root=$1
                  relative=$2
                  test -d "$root"; test ! -L "$root"
                  test -n "$relative"
                  old_ifs=$IFS
                  IFS='/'
                  set -- $relative
                  IFS=$old_ifs
                  current=$root
                  while test "$#" -gt 0; do
                    component=$1
                    shift
                    case "$component" in
                      ''|'.'|'..'|*/*) exit 64 ;;
                    esac
                    current="$current/$component"
                    ensure_directory "$current"
                  done
                }

                copy_create() {
                  source=$1
                  destination=$2
                  test -f "$source"; test ! -L "$source"
                  test ! -e "$destination"; test ! -L "$destination"
                  case "$destination" in
                    "$archive"/*) ;;
                    *) exit 64 ;;
                  esac
                  relative=${destination#"$archive"/}
                  parent=${relative%/*}
                  if test "$parent" != "$relative"; then
                    ensure_relative_parent "$archive" "$parent"
                  fi
                  (set -C; /bin/cat "$source" > "$destination")
                  test -f "$destination"; test ! -L "$destination"
                  /usr/bin/cmp -s "$source" "$destination"
                }

                require_sha() {
                  path=$1
                  expected=$2
                  test -f "$path"; test ! -L "$path"
                  digest=$(/usr/bin/shasum -a 256 "$path")
                  actual=${digest%% *}
                  test "$actual" = "$expected"
                }

                ensure_directory() {
                  directory=$1
                  if test -e "$directory" || test -L "$directory"; then
                    test -d "$directory"; test ! -L "$directory"
                  else
                    /bin/mkdir "$directory"
                    test -d "$directory"; test ! -L "$directory"
                  fi
                }

                verify_report() (
                  report_root=$1
                  method=$2
                  report_suffix=$3
                  expected_marker_count=$4
                  expected_marker_file=${5-}
                  xml="$report_root/TEST-$test_class-$report_suffix.xml"
                  txt="$report_root/$test_class-$report_suffix.txt"
                  test -f "$xml"; test ! -L "$xml"
                  test -f "$txt"; test ! -L "$txt"
                  tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-report.XXXXXX)
                  cleanup_report() {
                    cleanup_status=$?
                    trap - HUP INT TERM EXIT
                    /bin/rm -f "$tmp/system-out" "$tmp/marker-lines" \
                      "$tmp/expected-marker"
                    /bin/rmdir "$tmp"
                    return "$cleanup_status"
                  }
                  trap cleanup_report EXIT
                  trap 'exit 129' HUP
                  trap 'exit 130' INT
                  trap 'exit 143' TERM
                  suite_nodes='(/*[local-name()="testsuite"] | /*[local-name()="testsuites"]/*[local-name()="testsuite"])'
                  suite_count=$(/usr/bin/xmllint --xpath \
                    "count($suite_nodes)" "$xml")
                  test "$suite_count" = '1'
                  suite="($suite_nodes)[1]"
                  test "$(/usr/bin/xmllint --xpath "string($suite/@tests)" "$xml")" = '1'
                  test "$(/usr/bin/xmllint --xpath "string($suite/@failures)" "$xml")" = '0'
                  test "$(/usr/bin/xmllint --xpath "string($suite/@errors)" "$xml")" = '0'
                  test "$(/usr/bin/xmllint --xpath "string($suite/@skipped)" "$xml")" = '0'
                  testcase_count=$(/usr/bin/xmllint --xpath \
                    "count($suite/*[local-name()='testcase' and @name='$method' and @classname='$test_class($report_suffix)'])" "$xml")
                  test "$testcase_count" = '1'
                  test "$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase'])" "$xml")" = '1'
                  test "$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase']//*[local-name()='failure' or local-name()='error'])" "$xml")" = '0'
                  test "$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase']/*[local-name()='system-out'])" "$xml")" = '1'
                  test "$(/usr/bin/xmllint --xpath "count($suite/*[local-name()='testcase']/*[local-name()='system-err'])" "$xml")" = '0'
                  test "$(/usr/bin/grep -Fxc 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' "$txt")" = '1'
                  credential_xpath='count(//*[local-name()="property" and (contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api-key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"api_key") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"apikey") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"secret") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"password") or contains(translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz"),"credential"))])'
                  allowed_xpath='count(//*[local-name()="property" and @name="hermes.rag.colbert.embedding-api-key" and @value=""])'
                  test "$(/usr/bin/xmllint --xpath "$credential_xpath" "$xml")" = '1'
                  test "$(/usr/bin/xmllint --xpath "$allowed_xpath" "$xml")" = '1'
                  if LC_ALL=C /usr/bin/grep -E -- '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|Bearer[[:space:]]+[A-Za-z0-9._~+/=-]{8,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}' "$xml" >/dev/null; then
                    return 1
                  else
                    scan_status=$?
                  fi
                  test "$scan_status" -eq 1
                  /usr/bin/xmllint --xpath \
                    "string($suite/*[local-name()='testcase']/*[local-name()='system-out'])" \
                    "$xml" > "$tmp/system-out"
                  /usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{print}' \
                    "$tmp/system-out" > "$tmp/marker-lines"
                  marker_count=$(/usr/bin/awk 'END{print NR+0}' \
                    "$tmp/marker-lines")
                  test "$marker_count" = "$expected_marker_count"
                  case "$expected_marker_count" in
                    0) test -z "$expected_marker_file" ;;
                    1)
                      test -f "$expected_marker_file"; test ! -L "$expected_marker_file"
                      { printf '%s ' 'CANDIDATE102A_EVIDENCE_BASELINE_V2'; \
                        /bin/cat "$expected_marker_file"; } \
                        > "$tmp/expected-marker"
                      /usr/bin/cmp -s "$tmp/marker-lines" \
                        "$tmp/expected-marker"
                      ;;
                    *) return 64 ;;
                  esac
                )

                verify_phase_lock() {
                  phase_lock=$1
                  method=$2
                  report_suffix=$3
                  test -d "$phase_lock"; test ! -L "$phase_lock"
                  test "$(/usr/bin/stat -f '%Lp' "$phase_lock")" = '500'
                  test -d "$phase_lock/reports"; test ! -L "$phase_lock/reports"
                  test "$(/usr/bin/stat -f '%Lp' "$phase_lock/reports")" = '500'
                  test -f "$phase_lock/reports.sha256"; test ! -L "$phase_lock/reports.sha256"
                  test "$(/usr/bin/stat -f '%Lp' "$phase_lock/reports.sha256")" = '400'
                  test "$(/usr/bin/awk 'END{print NR+0}' "$phase_lock/reports.sha256")" = '2'
                  (cd "$phase_lock"; /usr/bin/shasum -a 256 -c reports.sha256)
                  verify_report "$phase_lock/reports" "$method" \
                    "$report_suffix" 0
                }

                verify_source_bindings() (
                  source_lock="$attempt/source-lock.json"
                  commands="$attempt/commands.sh"
                  test -f "$source_lock"; test ! -L "$source_lock"
                  test -f "$commands"; test ! -L "$commands"; test -x "$commands"
                  test -f "$plan"; test ! -L "$plan"
                  require_sha "$plan" "$plan_sha"
                  tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-source.XXXXXX)
                  cleanup_source() {
                    cleanup_status=$?
                    trap - HUP INT TERM EXIT
                    /bin/rm -f "$tmp/canonical" "$tmp/files" "$tmp/resources"
                    /bin/rmdir "$tmp"
                    return "$cleanup_status"
                  }
                  trap cleanup_source EXIT
                  trap 'exit 129' HUP
                  trap 'exit 130' INT
                  trap 'exit 143' TERM
                  /usr/bin/jq -cS . "$source_lock" > "$tmp/canonical"
                  /usr/bin/cmp -s "$source_lock" "$tmp/canonical"
                  /usr/bin/jq -e '
                    .schemaVersion == "candidate102a-evidence-baseline-v2-source-lock-v1" and
                    .namespace == "candidate102a-evidence-baseline-v2" and
                    .attempt == "002" and
                    .predecessorStatus == "SUPERSEDED_UNRECOVERABLE" and
                    .repository.headCommit == "5c84bb044352a3fef684a37f70cc08ac80058c7b" and
                    .repository.branch == "codex/candidate102a-evidence-baseline-v2" and
                    .repository.objectFormat == "sha1" and
                    .runtimeContract.queryCount == 40 and
                    .runtimeContract.familyCount == 20 and
                    .runtimeContract.corpusCount == 1120 and
                    .runtimeContract.checkpointCount == 13 and
                    .runtimeContract.futureCount == 200 and
                    .runtimeContract.qrelAfterRankingFreeze == true and
                    .runtimeContract.qrelAccessCount == 1 and
                    .runtimeContract.holdoutPathOperationCount == 0 and
                    (.sourceFiles | length) == 5' "$source_lock" >/dev/null
                  /usr/bin/jq -r '.sourceFiles[] | [.path,.sha256] | @tsv' \
                    "$source_lock" > "$tmp/files"
                  test "$(/usr/bin/awk 'END{print NR+0}' "$tmp/files")" = '5'
                  while IFS='\t' read -r relative expected; do
                    case "$relative" in
                      /*|*'..'*|*'\\'*) exit 64 ;;
                    esac
                    require_sha "$backend/$relative" "$expected"
                  done < "$tmp/files"
                  require_sha "$backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java" \
                    'bf8d340ef6e591a8471e374d03706fbe82bfb58c4d36cc7e58d15c5580569c6e'
                  require_sha "$backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate10FixtureGenerator.java" \
                    '8262d8bf5ad65330e0052119bea910cdd4af81e45a450b8238bc499cfd307a6e'
                  command_path=$(/usr/bin/jq -er '.commands.path' "$source_lock")
                  command_size=$(/usr/bin/jq -er '.commands.sizeBytes' "$source_lock")
                  command_sha=$(/usr/bin/jq -er '.commands.sha256' "$source_lock")
                  test "$command_path" = 'commands.sh'
                  test "$(/usr/bin/stat -f '%z' "$commands")" = "$command_size"
                  require_sha "$commands" "$command_sha"
                  test "$(/usr/bin/stat -f '%Lp' "$commands")" = '755'
                  /usr/bin/jq -r '.selectionNonQrelInputs.files[] |
                    [.path,.sha256] | @tsv' "$source_lock" > "$tmp/resources"
                  test "$(/usr/bin/awk 'END{print NR+0}' "$tmp/resources")" = '3'
                  while IFS='\t' read -r relative expected; do
                    case "$relative" in selection/*) ;; *) exit 64 ;; esac
                    require_sha "$attempt/$relative" "$expected"
                  done < "$tmp/resources"
                  require_sha "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json" \
                    '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
                )

                verify_jdk() (
                  tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-jdk.XXXXXX)
                  cleanup_jdk() {
                    cleanup_status=$?
                    trap - HUP INT TERM EXIT
                    /bin/rm -f "$tmp/stdout" "$tmp/stderr"
                    /bin/rmdir "$tmp"
                    return "$cleanup_status"
                  }
                  trap cleanup_jdk EXIT
                  trap 'exit 129' HUP
                  trap 'exit 130' INT
                  trap 'exit 143' TERM
                  "$java_home/bin/java" -XshowSettings:properties -version \
                    > "$tmp/stdout" 2> "$tmp/stderr"
                  test ! -s "$tmp/stdout"
                  property() {
                    key=$1
                    value=$(/usr/bin/awk -v key="$key" '
                      index($0, "    " key " = ") == 1 {
                        sub("^    " key " = ", ""); print; count++
                      }
                      END { if (count != 1) exit 1 }
                    ' "$tmp/stderr")
                    printf '%s' "$value"
                  }
                  test "$(property java.home)" = "$java_home"
                  test "$(property java.version)" = '17.0.19'
                  test "$(property java.runtime.version)" = '17.0.19+10'
                  test "$(property java.vendor)" = 'Eclipse Adoptium'
                  test "$(property os.arch)" = 'aarch64'
                )

                verify_worktree() (
                  external='/Users/achilles/.codex/evidence-worktree-snapshots/candidate102a-evidence-baseline-v2-attempt-002-before.json'
                  runtime="$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json"
                  require_sha "$external" '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
                  require_sha "$runtime" '94f56c9e44ec3b257208258532fb8eda88eda08ff60f420371cb019fe52d693e'
                  /usr/bin/cmp -s "$external" "$runtime"
                  /usr/bin/jq -e '
                    .schemaVersion == "candidate102a-worktree-snapshot-v1" and
                    .phase == "BEFORE" and
                    .headCommit == "5c84bb044352a3fef684a37f70cc08ac80058c7b" and
                    (.entries | length) == 6' "$runtime" >/dev/null
                  tmp=$(/usr/bin/mktemp -d /tmp/candidate102a-worktree.XXXXXX)
                  cleanup_worktree() {
                    cleanup_status=$?
                    trap - HUP INT TERM EXIT
                    /bin/rm -f "$tmp/actual" "$tmp/expected" \
                      "$tmp/actual-sorted" "$tmp/expected-sorted" "$tmp/index"
                    /bin/rmdir "$tmp"
                    return "$cleanup_status"
                  }
                  trap cleanup_worktree EXIT
                  trap 'exit 129' HUP
                  trap 'exit 130' INT
                  trap 'exit 143' TERM
                  /usr/bin/git -C "$repo" status --porcelain=v1 -z \
                    --untracked-files=all --no-renames -- . \
                    ':(exclude)backend/tests/target/**' \
                    ':(exclude)backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java' \
                    ':(exclude)backend/tests/evidence/candidate102a-evidence-baseline-v2/**' \
                    ':(exclude)plans/2026-07-30-candidate102a-evidence-baseline-v2-attempt-002.md' \
                    > "$tmp/actual"
                  printf ' D %s\0' '23030327许子祺/.officecli/config.json' > "$tmp/expected"
                  for path in \
                    '23030327许子祺/1-第2周报.docx' \
                    'backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java' \
                    'backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java' \
                    'frontend/package-lock.json' \
                    'scripts/start.sh'
                  do
                    printf ' M %s\0' "$path" >> "$tmp/expected"
                  done
                  LC_ALL=C /usr/bin/sort -z "$tmp/actual" > "$tmp/actual-sorted"
                  LC_ALL=C /usr/bin/sort -z "$tmp/expected" > "$tmp/expected-sorted"
                  /usr/bin/cmp -s "$tmp/actual-sorted" "$tmp/expected-sorted"
                  require_sha "$repo/23030327许子祺/1-第2周报.docx" \
                    '3c1a18239854fcbb1cbc54a7b70eb42e6b4afdc633afb6e8b38b13b64fdb03f8'
                  require_sha "$backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java" \
                    '75746407754cdfcf350960bd9587831bb7e2a76df4ba0ef2d46c1a81ea8a2e7f'
                  require_sha "$backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java" \
                    '898d815973fcaedf9dd7bd0a0f73bd6d18431c8ccd4c4b5b385c08a0ed561ffd'
                  require_sha "$repo/frontend/package-lock.json" \
                    'fd6650108cdadbcf0e23a9f0471eac4aab987a230f2091b40a7a439e2c5a509a'
                  require_sha "$repo/scripts/start.sh" \
                    '26436484ad4c1306cfd108611addfd09fe8025d067e93b39be5d01e5f3de0f59'
                  test ! -e "$repo/23030327许子祺/.officecli/config.json"
                )

                verify_receipt() (
                  receipt=$1
                  test -f "$receipt"; test ! -L "$receipt"
                  test "$(/usr/bin/stat -f '%Lp' "$receipt")" = '400'
                  test "$(/usr/bin/dirname "$receipt")" = "$consumed_authorizations"
                  nonce=$(/usr/bin/jq -er '.authorizationNonce' "$receipt")
                  test "$(/usr/bin/basename "$receipt")" = "$nonce.json"
                  require_sha "$plan" "$plan_sha"
                  source_lock_sha=$(/usr/bin/shasum -a 256 "$attempt/source-lock.json")
                  source_lock_sha=${source_lock_sha%% *}
                  test_sha=$(/usr/bin/jq -er '
                    [.sourceFiles[] | select(.path == "tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java")] as $v |
                    if ($v|length)==1 then $v[0].sha256 else error("test binding") end' \
                    "$attempt/source-lock.json")
                  /usr/bin/jq -e --arg plan "$plan_sha" --arg lock "$source_lock_sha" \
                    --arg test "$test_sha" '
                    (keys == ["allowSelectionOnce","allowSelectionQrelAfterFreezeOnce",
                              "attempt","authorizationNonce","namespace","planSha256",
                              "sourceLockSha256","testSourceSha256"]) and
                    .allowSelectionOnce == true and
                    .allowSelectionQrelAfterFreezeOnce == true and
                    .attempt == "002" and
                    .namespace == "candidate102a-evidence-baseline-v2" and
                    (.authorizationNonce | test("^[0-9a-f]{64}$")) and
                    .planSha256 == $plan and .sourceLockSha256 == $lock and
                    .testSourceSha256 == $test' "$receipt" >/dev/null
                )

                preflight_archive_paths() {
                  evidence="$tests/evidence"
                  archive_parent="$evidence/candidate102a-evidence-baseline-v2"
                  archive="$archive_parent/attempt-002"
                  staging="$archive_parent/.attempt-002.staging"
                  archive_rel='backend/tests/evidence/candidate102a-evidence-baseline-v2/attempt-002'
                  staging_rel='backend/tests/evidence/candidate102a-evidence-baseline-v2/.attempt-002.staging'

                  test -d "$repo"; test ! -L "$repo"
                  test -d "$backend"; test ! -L "$backend"
                  test -d "$tests"; test ! -L "$tests"
                  test "$(/usr/bin/git -C "$repo" rev-parse --show-toplevel)" = "$repo"
                  ensure_directory "$evidence"
                  ensure_directory "$archive_parent"
                  test -d "$archive_parent"; test ! -L "$archive_parent"
                  test -w "$archive_parent"; test -x "$archive_parent"
                  test ! -e "$archive"; test ! -L "$archive"
                  test ! -e "$staging"; test ! -L "$staging"

                  if tracked=$(/usr/bin/git -C "$repo" ls-files -- \
                       'backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java' \
                       "$archive_rel" "$staging_rel"); then
                    ls_files_status=0
                  else
                    ls_files_status=$?
                  fi
                  test "$ls_files_status" -eq 0
                  test -z "$tracked"

                  set -- \
                    'backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate102AEvidenceBaselineV2Test.java' \
                    "$archive_rel/SHA256SUMS" \
                    "$archive_rel/canonical-marker.json" \
                    "$archive_rel/commands.sh" \
                    "$archive_rel/reactor-inputs/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java" \
                    "$archive_rel/reactor-inputs/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java" \
                    "$archive_rel/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml" \
                    "$archive_rel/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml" \
                    "$archive_rel/reports/TEST-tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.xml" \
                    "$archive_rel/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt" \
                    "$archive_rel/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt" \
                    "$archive_rel/reports/tech.qiantong.qknow.rag.eval.RagCandidate102AEvidenceBaselineV2Test-candidate102a-evidence-baseline-v2-selection-attempt-002.txt" \
                    "$archive_rel/selection/corpus.jsonl" \
                    "$archive_rel/selection/pressure.json" \
                    "$archive_rel/selection/queries.jsonl" \
                    "$archive_rel/source-lock.json" \
                    "$archive_rel/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json" \
                    "$archive_rel/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json"

                  if /usr/bin/git -C "$repo" check-ignore --no-index -- "$@" \
                       >/dev/null; then
                    check_ignore_status=0
                  else
                    check_ignore_status=$?
                  fi
                  test "$check_ignore_status" -eq 1
                }

                archive_once() {
                  preflight_archive_paths
                  evidence="$tests/evidence"
                  archive_parent="$evidence/candidate102a-evidence-baseline-v2"
                  archive="$archive_parent/attempt-002"
                  staging="$archive_parent/.attempt-002.staging"
                  /bin/mkdir "$staging"
                  test -d "$staging"; test ! -L "$staging"
                  copy_create "$attempt/source-lock.json" "$staging/source-lock.json"
                  copy_create "$attempt/canonical-marker.json" "$staging/canonical-marker.json"
                  copy_create "$attempt/commands.sh" "$staging/commands.sh"
                  copy_create "$reports/TEST-$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml" "$staging/reports/TEST-$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-002.xml"
                  copy_create "$reports/TEST-$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml" "$staging/reports/TEST-$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.xml"
                  copy_create "$reports/TEST-$test_class-candidate102a-evidence-baseline-v2-selection-attempt-002.xml" "$staging/reports/TEST-$test_class-candidate102a-evidence-baseline-v2-selection-attempt-002.xml"
                  copy_create "$reports/$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt" "$staging/reports/$test_class-candidate102a-evidence-baseline-v2-contracts-attempt-002.txt"
                  copy_create "$reports/$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt" "$staging/reports/$test_class-candidate102a-evidence-baseline-v2-freeze-attempt-002.txt"
                  copy_create "$reports/$test_class-candidate102a-evidence-baseline-v2-selection-attempt-002.txt" "$staging/reports/$test_class-candidate102a-evidence-baseline-v2-selection-attempt-002.txt"
                  copy_create "$attempt/selection/corpus.jsonl" "$staging/selection/corpus.jsonl"
                  copy_create "$attempt/selection/queries.jsonl" "$staging/selection/queries.jsonl"
                  copy_create "$attempt/selection/pressure.json" "$staging/selection/pressure.json"
                  copy_create "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json" "$staging/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json"
                  copy_create "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json" "$staging/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"
                  embedding_source="$backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java"
                  embedding_copy="$staging/reactor-inputs/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java"
                  rag_checker_source="$backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java"
                  rag_checker_copy="$staging/reactor-inputs/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RAGChecker.java"
                  require_sha "$embedding_source" '75746407754cdfcf350960bd9587831bb7e2a76df4ba0ef2d46c1a81ea8a2e7f'
                  copy_create "$embedding_source" "$embedding_copy"
                  require_sha "$embedding_copy" '75746407754cdfcf350960bd9587831bb7e2a76df4ba0ef2d46c1a81ea8a2e7f'
                  require_sha "$rag_checker_source" '898d815973fcaedf9dd7bd0a0f73bd6d18431c8ccd4c4b5b385c08a0ed561ffd'
                  copy_create "$rag_checker_source" "$rag_checker_copy"
                  require_sha "$rag_checker_copy" '898d815973fcaedf9dd7bd0a0f73bd6d18431c8ccd4c4b5b385c08a0ed561ffd'
                  (cd "$staging"; test -z "$(/usr/bin/find . -type l -print -quit)"; set -C; /usr/bin/find . -type f ! -name SHA256SUMS -print | LC_ALL=C /usr/bin/sort | while IFS= read -r path; do /usr/bin/shasum -a 256 "$path"; done > SHA256SUMS; set +C; test "$(/usr/bin/wc -l < SHA256SUMS | /usr/bin/tr -d ' ')" = '16'; /usr/bin/shasum -a 256 -c SHA256SUMS; test "$(/usr/bin/find . -type f | /usr/bin/wc -l | /usr/bin/tr -d ' ')" = '17')
                  test ! -e "$archive"; test ! -L "$archive"
                  /bin/mv "$staging" "$archive"
                  test ! -e "$staging"; test ! -L "$staging"
                  test -d "$archive"; test ! -L "$archive"
                  (cd "$archive"; test -z "$(/usr/bin/find . -type l -print -quit)"; /usr/bin/shasum -a 256 -c SHA256SUMS; test "$(/usr/bin/find . -type f | /usr/bin/wc -l | /usr/bin/tr -d ' ')" = '17')
                }

                command=''
                if test "$#" -gt 0; then
                  command=$1
                fi
                case "$command" in
                  compile)
                    env JAVA_HOME="$java_home" PATH="$java_path" /opt/homebrew/bin/rtk mvn -f "$backend/pom.xml" -pl tests -am -DskipTests -Dmaven.resources.skip=true test-compile
                    ;;
                  contracts)
                    suffix='candidate102a-evidence-baseline-v2-contracts-attempt-002'
                    xml="$reports/TEST-$test_class-$suffix.xml"
                    txt="$reports/$test_class-$suffix.txt"
                    test ! -e "$xml"; test ! -L "$xml"
                    test ! -e "$txt"; test ! -L "$txt"

                    env JAVA_HOME="$java_home" PATH="$java_path" /opt/homebrew/bin/rtk mvn -f "$backend/pom.xml" -pl tests -am "-Dtest=$test_class#baselineContracts" -Dsurefire.failIfNoSpecifiedTests=false "-Dsurefire.reportNameSuffix=$suffix" -Dmaven.resources.skip=true -Drag.eval.candidate10.evidence-baseline-v2=contracts -Drag.eval.candidate10.freeze=false -Drag.eval.candidate10.diagnostic=true -Drag.eval.candidate10.diagnostic-arm=true -Drag.eval.candidate10.environment-qualification=false -Drag.eval.shadow=false -Drag.eval.shadow.compare-stable=false -Drag.eval.identifier.diagnostic=false -Drag.eval.candidate2.diagnostic=false -Drag.eval.candidate3.diagnostic=false -Drag.eval.candidate4.diagnostic=false -Drag.eval.candidate5.diagnostic=false -Drag.eval.candidate6.diagnostic=false -Drag.eval.candidate8.diagnostic=false -Drag.eval.candidate9.diagnostic=false -Drag.eval.candidate9.recovery=false -Drag.eval.promotion=false -Drag.eval.live=false -Dqknow.rag.dynamic-top-k.enabled=false -Dqknow.rag.query-entity.enabled=false -Dqknow.rag.rrf.k=60 -Dqknow.rag.rrf.weak-path-threshold=0 -Dqknow.rag.graph.enabled=false -Dqknow.rag.vector.vecsim-rescore-enabled=false -Dqknow.rag.keyword.identifier-aware=false -Dqknow.rag.rerank.identifier-consistency-enabled=true -Dqknow.rag.local-reranker.enabled=false -Dqknow.rag.onnx-reranker.enabled=false -Dhermes.rag.colbert.enabled=true -Dhermes.rag.colbert.ngram-size=3 -Dhermes.rag.colbert.dimensions=64 -Dhermes.rag.colbert.max-tokens-per-doc=128 -Dhermes.rag.colbert.embedding-platform= -Dhermes.rag.colbert.embedding-base-url= -Dhermes.rag.colbert.embedding-api-key= -Dhermes.rag.colbert.embedding-model= -Dhermes.rag.context.max-bytes=20000 -Dhermes.rag.context.max-tokens=0 -DforkCount=1 -DreuseForks=false '-DargLine=-Dfile.encoding=UTF-8 -Duser.timezone=UTC -Duser.language=en -Duser.country=US -Duser.script= -Duser.variant= -Dqknow.native.lib.dir= -Djava.library.path=/Users/achilles/Documents/许子祺/Agent/backend/tests/target/rag-eval/no-native' test

                    test -f "$xml"; test ! -L "$xml"
                    test -f "$txt"; test ! -L "$txt"
                    suite='(/*[local-name()="testsuite"] | /*[local-name()="testsuites"]/*[local-name()="testsuite"])[1]'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@tests)" "$xml")" = '1'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@failures)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@errors)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@skipped)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "count(//*[local-name()='testcase' and @name='baselineContracts' and @classname='$test_class'])" "$xml")" = '1'
                    test "$(/usr/bin/grep -Fc 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' "$txt")" = '1'
                    system_out=$(/usr/bin/xmllint --xpath 'string((//*[local-name()="system-out"])[1])' "$xml")
                    marker_count=$(printf '%s\\n' "$system_out" | /usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{n++}END{print n+0}')
                    test "$marker_count" = '0'
                    ;;
                  freeze)
                    suffix='candidate102a-evidence-baseline-v2-freeze-attempt-002'
                    xml="$reports/TEST-$test_class-$suffix.xml"
                    txt="$reports/$test_class-$suffix.txt"
                    test ! -e "$attempt"; test ! -L "$attempt"
                    test ! -e "$xml"; test ! -L "$xml"
                    test ! -e "$txt"; test ! -L "$txt"

                    env JAVA_HOME="$java_home" PATH="$java_path" /opt/homebrew/bin/rtk mvn -f "$backend/pom.xml" -pl tests -am "-Dtest=$test_class#freezeCurrentEvidence" -Dsurefire.failIfNoSpecifiedTests=false "-Dsurefire.reportNameSuffix=$suffix" -Dmaven.resources.skip=true -Drag.eval.candidate10.evidence-baseline-v2=freeze -Drag.eval.candidate10.freeze=false -Drag.eval.candidate10.diagnostic=true -Drag.eval.candidate10.diagnostic-arm=true -Drag.eval.candidate10.environment-qualification=false -Drag.eval.shadow=false -Drag.eval.shadow.compare-stable=false -Drag.eval.identifier.diagnostic=false -Drag.eval.candidate2.diagnostic=false -Drag.eval.candidate3.diagnostic=false -Drag.eval.candidate4.diagnostic=false -Drag.eval.candidate5.diagnostic=false -Drag.eval.candidate6.diagnostic=false -Drag.eval.candidate8.diagnostic=false -Drag.eval.candidate9.diagnostic=false -Drag.eval.candidate9.recovery=false -Drag.eval.promotion=false -Drag.eval.live=false -Dqknow.rag.dynamic-top-k.enabled=false -Dqknow.rag.query-entity.enabled=false -Dqknow.rag.rrf.k=60 -Dqknow.rag.rrf.weak-path-threshold=0 -Dqknow.rag.graph.enabled=false -Dqknow.rag.vector.vecsim-rescore-enabled=false -Dqknow.rag.keyword.identifier-aware=false -Dqknow.rag.rerank.identifier-consistency-enabled=true -Dqknow.rag.local-reranker.enabled=false -Dqknow.rag.onnx-reranker.enabled=false -Dhermes.rag.colbert.enabled=true -Dhermes.rag.colbert.ngram-size=3 -Dhermes.rag.colbert.dimensions=64 -Dhermes.rag.colbert.max-tokens-per-doc=128 -Dhermes.rag.colbert.embedding-platform= -Dhermes.rag.colbert.embedding-base-url= -Dhermes.rag.colbert.embedding-api-key= -Dhermes.rag.colbert.embedding-model= -Dhermes.rag.context.max-bytes=20000 -Dhermes.rag.context.max-tokens=0 -DforkCount=1 -DreuseForks=false '-DargLine=-Dfile.encoding=UTF-8 -Duser.timezone=UTC -Duser.language=en -Duser.country=US -Duser.script= -Duser.variant= -Dqknow.native.lib.dir= -Djava.library.path=/Users/achilles/Documents/许子祺/Agent/backend/tests/target/rag-eval/no-native' test

                    test -f "$xml"; test ! -L "$xml"
                    test -f "$txt"; test ! -L "$txt"
                    suite='(/*[local-name()="testsuite"] | /*[local-name()="testsuites"]/*[local-name()="testsuite"])[1]'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@tests)" "$xml")" = '1'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@failures)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@errors)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@skipped)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "count(//*[local-name()='testcase' and @name='freezeCurrentEvidence' and @classname='$test_class'])" "$xml")" = '1'
                    test "$(/usr/bin/grep -Fc 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' "$txt")" = '1'
                    system_out=$(/usr/bin/xmllint --xpath 'string((//*[local-name()="system-out"])[1])' "$xml")
                    marker_count=$(printf '%s\\n' "$system_out" | /usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{n++}END{print n+0}')
                    test "$marker_count" = '0'

                    for path in \
                      "$attempt/source-lock.json" \
                      "$attempt/commands.sh" \
                      "$attempt/selection/corpus.jsonl" \
                      "$attempt/selection/queries.jsonl" \
                      "$attempt/selection/pressure.json" \
                      "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-before.json"
                    do
                      test -f "$path"; test ! -L "$path"
                    done
                    test ! -e "$attempt/canonical-marker.json"; test ! -L "$attempt/canonical-marker.json"
                    test ! -e "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"
                    test ! -L "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"
                    ;;
                  selection)
                    suffix='candidate102a-evidence-baseline-v2-selection-attempt-002'
                    xml="$reports/TEST-$test_class-$suffix.xml"
                    txt="$reports/$test_class-$suffix.txt"
                    test -f "$attempt/source-lock.json"; test ! -L "$attempt/source-lock.json"
                    test ! -e "$attempt/canonical-marker.json"; test ! -L "$attempt/canonical-marker.json"
                    test ! -e "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"
                    test ! -L "$attempt/worktree/candidate102a-evidence-baseline-v2-attempt-002-after-selection.json"
                    preflight_archive_paths
                    test ! -e "$xml"; test ! -L "$xml"
                    test ! -e "$txt"; test ! -L "$txt"

                    env JAVA_HOME="$java_home" PATH="$java_path" /opt/homebrew/bin/rtk mvn -f "$backend/pom.xml" -pl tests -am "-Dtest=$test_class#establishSelectionBaseline" -Dsurefire.failIfNoSpecifiedTests=false "-Dsurefire.reportNameSuffix=$suffix" -Dmaven.resources.skip=true -Drag.eval.candidate10.evidence-baseline-v2=selection -Drag.eval.candidate10.freeze=false -Drag.eval.candidate10.diagnostic=true -Drag.eval.candidate10.diagnostic-arm=true -Drag.eval.candidate10.environment-qualification=false -Drag.eval.shadow=false -Drag.eval.shadow.compare-stable=false -Drag.eval.identifier.diagnostic=false -Drag.eval.candidate2.diagnostic=false -Drag.eval.candidate3.diagnostic=false -Drag.eval.candidate4.diagnostic=false -Drag.eval.candidate5.diagnostic=false -Drag.eval.candidate6.diagnostic=false -Drag.eval.candidate8.diagnostic=false -Drag.eval.candidate9.diagnostic=false -Drag.eval.candidate9.recovery=false -Drag.eval.promotion=false -Drag.eval.live=false -Dqknow.rag.dynamic-top-k.enabled=false -Dqknow.rag.query-entity.enabled=false -Dqknow.rag.rrf.k=60 -Dqknow.rag.rrf.weak-path-threshold=0 -Dqknow.rag.graph.enabled=false -Dqknow.rag.vector.vecsim-rescore-enabled=false -Dqknow.rag.keyword.identifier-aware=false -Dqknow.rag.rerank.identifier-consistency-enabled=true -Dqknow.rag.local-reranker.enabled=false -Dqknow.rag.onnx-reranker.enabled=false -Dhermes.rag.colbert.enabled=true -Dhermes.rag.colbert.ngram-size=3 -Dhermes.rag.colbert.dimensions=64 -Dhermes.rag.colbert.max-tokens-per-doc=128 -Dhermes.rag.colbert.embedding-platform= -Dhermes.rag.colbert.embedding-base-url= -Dhermes.rag.colbert.embedding-api-key= -Dhermes.rag.colbert.embedding-model= -Dhermes.rag.context.max-bytes=20000 -Dhermes.rag.context.max-tokens=0 -DforkCount=1 -DreuseForks=false '-DargLine=-Dfile.encoding=UTF-8 -Duser.timezone=UTC -Duser.language=en -Duser.country=US -Duser.script= -Duser.variant= -Dqknow.native.lib.dir= -Djava.library.path=/Users/achilles/Documents/许子祺/Agent/backend/tests/target/rag-eval/no-native' test

                    test -f "$xml"; test ! -L "$xml"
                    test -f "$txt"; test ! -L "$txt"
                    suite='(/*[local-name()="testsuite"] | /*[local-name()="testsuites"]/*[local-name()="testsuite"])[1]'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@tests)" "$xml")" = '1'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@failures)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@errors)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "string($suite/@skipped)" "$xml")" = '0'
                    test "$(/usr/bin/xmllint --xpath "count(//*[local-name()='testcase' and @name='establishSelectionBaseline' and @classname='$test_class'])" "$xml")" = '1'
                    test "$(/usr/bin/grep -Fc 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' "$txt")" = '1'
                    system_out=$(/usr/bin/xmllint --xpath 'string((//*[local-name()="system-out"])[1])' "$xml")
                    marker_count=$(printf '%s\\n' "$system_out" | /usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{n++}END{print n+0}')
                    test "$marker_count" = '1'
                    marker_line=$(printf '%s\\n' "$system_out" | /usr/bin/awk 'index($0,"CANDIDATE102A_EVIDENCE_BASELINE_V2 ")==1{print}')
                    marker_json=$(printf '%s\\n' "$marker_line" | /usr/bin/awk '{sub(/^CANDIDATE102A_EVIDENCE_BASELINE_V2 /,""); print}')
                    test -f "$attempt/canonical-marker.json"; test ! -L "$attempt/canonical-marker.json"
                    actual_marker_sha=$(printf '%s\\n' "$marker_json" | /usr/bin/shasum -a 256 | /usr/bin/awk '{print $1}')
                    expected_marker_sha=$(/usr/bin/shasum -a 256 "$attempt/canonical-marker.json" | /usr/bin/awk '{print $1}')
                    test "$actual_marker_sha" = "$expected_marker_sha"
                    archive_once
                    ;;
                  *)
                    echo 'usage: commands.sh compile|contracts|freeze|selection' >&2
                    exit 64
                    ;;
                esac
                """;
        return script.getBytes(StandardCharsets.UTF_8);
    }

    private static String hashRegular(Path path) {
        require(path != null && !Files.isSymbolicLink(path)
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                SOURCE_LOCK_ERROR);
        return RagCandidate10FreezeSupport.sha256(path);
    }

    private static void requirePersistenceContracts() {
        byte[] commands = commandsBytes();
        String text = new String(commands, StandardCharsets.UTF_8);
        int archiveDefinition = text.indexOf("archive_once() {\n");
        int archivePreflight = text.indexOf(
                "archive_once() {\n  preflight_archive_paths\n");
        int archiveMkdir = text.indexOf(
                "  /bin/mkdir \"$staging\"\n", archivePreflight);
        int selectionCase = text.indexOf("  selection)\n");
        int selectionPreflight = text.indexOf(
                "    preflight_archive_paths\n", selectionCase);
        int selectionReportPreflight = text.indexOf(
                "    test ! -e \"$xml\"; test ! -L \"$xml\"\n",
                selectionPreflight);
        int selectionMaven = text.indexOf(
                "\"-Dtest=$test_class#establishSelectionBaseline\"",
                selectionReportPreflight);
        int checkIgnoreStart = text.indexOf("  set -- ");
        int checkIgnoreEnd = text.indexOf(
                "  if /usr/bin/git -C \"$repo\" check-ignore",
                checkIgnoreStart);
        require(text.startsWith("#!/bin/sh\nset -eu\n")
                        && text.endsWith("\n")
                        && !text.endsWith("\n\n")
                        && text.indexOf('\r') < 0 && text.indexOf('\0') < 0
                        && occurrences(text, "baselineContracts") == 2
                        && occurrences(text, "freezeCurrentEvidence") == 2
                        && occurrences(text,
                        "establishSelectionBaseline") == 2
                        && occurrences(text,
                        "preflight_archive_paths() {") == 1
                        && occurrences(text,
                        "preflight_archive_paths\n") == 2
                        && occurrences(text, "archive_once") == 2
                        && occurrences(text,
                        "system_out=$(/usr/bin/xmllint --xpath") == 3
                        && occurrences(text, "marker_count=$(printf") == 3
                        && occurrences(text,
                        "test \"$marker_count\" = '0'") == 2
                        && occurrences(text,
                        "test \"$marker_count\" = '1'") == 1
                        && occurrences(text,
                        "test ! -e \"$xml\"; test ! -L \"$xml\"") == 3
                        && occurrences(text,
                        "test -f \"$xml\"; test ! -L \"$xml\"") == 3
                        && occurrences(text,
                        "suite='(/*[local-name()=\"testsuite\"]") == 3
                        && text.contains(
                        "-Drag.eval.candidate10.evidence-baseline-v2=contracts")
                        && text.contains(
                        "-Drag.eval.candidate10.evidence-baseline-v2=freeze")
                        && text.contains(
                        "-Drag.eval.candidate10.evidence-baseline-v2=selection")
                        && text.contains("suffix='candidate102a-evidence-"
                        + "baseline-v2-contracts-attempt-002'")
                        && text.contains("suffix='candidate102a-evidence-"
                        + "baseline-v2-freeze-attempt-002'")
                        && text.contains("suffix='candidate102a-evidence-"
                        + "baseline-v2-selection-attempt-002'")
                        && text.contains("test \"$ls_files_status\" -eq 0")
                        && text.contains("test -z \"$tracked\"")
                        && text.contains(
                        "check-ignore --no-index -- \"$@\"")
                        && text.contains(">/dev/null; then")
                        && text.contains(
                        "test \"$check_ignore_status\" -eq 1")
                        && archiveDefinition >= 0
                        && archivePreflight == archiveDefinition
                        && archiveMkdir > archivePreflight
                        && selectionCase >= 0
                        && selectionPreflight > selectionCase
                        && selectionReportPreflight > selectionPreflight
                        && selectionMaven > selectionReportPreflight
                        && checkIgnoreStart >= 0
                        && checkIgnoreEnd > checkIgnoreStart
                        && !text.contains("run_selector")
                        && !text.contains("verify_report")
                        && !text.contains("preflight_report")
                        && !text.contains("check-ignore -q")
                        && !text.contains("\"$xml\" | /usr/bin/awk")
                        && !text.contains("git commit")
                        && !text.contains("git push")
                        && !text.contains("git clone")
                        && !text.contains("refs/tags"), COMMAND_ERROR);
        String checkIgnoreBlock =
                text.substring(checkIgnoreStart, checkIgnoreEnd);
        List<String> archivePaths = List.of(
                "SHA256SUMS",
                "canonical-marker.json",
                "commands.sh",
                "reactor-inputs/qknow-framework/qknow-ai/src/main/java/"
                        + "tech/qiantong/qknow/ai/service/impl/"
                        + "EmbeddingServiceImpl.java",
                "reactor-inputs/qknow-hermes/qknow-hermes-core/src/main/java/"
                        + "tech/qiantong/qknow/hermes/eval/RAGChecker.java",
                "reports/TEST-tech.qiantong.qknow.rag.eval."
                        + "RagCandidate102AEvidenceBaselineV2Test-"
                        + "candidate102a-evidence-baseline-v2-contracts-"
                        + "attempt-002.xml",
                "reports/TEST-tech.qiantong.qknow.rag.eval."
                        + "RagCandidate102AEvidenceBaselineV2Test-"
                        + "candidate102a-evidence-baseline-v2-freeze-"
                        + "attempt-002.xml",
                "reports/TEST-tech.qiantong.qknow.rag.eval."
                        + "RagCandidate102AEvidenceBaselineV2Test-"
                        + "candidate102a-evidence-baseline-v2-selection-"
                        + "attempt-002.xml",
                "reports/tech.qiantong.qknow.rag.eval."
                        + "RagCandidate102AEvidenceBaselineV2Test-"
                        + "candidate102a-evidence-baseline-v2-contracts-"
                        + "attempt-002.txt",
                "reports/tech.qiantong.qknow.rag.eval."
                        + "RagCandidate102AEvidenceBaselineV2Test-"
                        + "candidate102a-evidence-baseline-v2-freeze-"
                        + "attempt-002.txt",
                "reports/tech.qiantong.qknow.rag.eval."
                        + "RagCandidate102AEvidenceBaselineV2Test-"
                        + "candidate102a-evidence-baseline-v2-selection-"
                        + "attempt-002.txt",
                "selection/corpus.jsonl",
                "selection/pressure.json",
                "selection/queries.jsonl",
                "source-lock.json",
                "worktree/candidate102a-evidence-baseline-v2-attempt-002-"
                        + "after-selection.json",
                "worktree/candidate102a-evidence-baseline-v2-attempt-002-"
                        + "before.json");
        require(archivePaths.size() == 17
                        && occurrences(checkIgnoreBlock,
                        "\"$archive_rel/") == 17
                        && checkIgnoreBlock.contains(
                        "'backend/tests/src/test/java/tech/qiantong/qknow/"
                                + "rag/eval/"
                                + "RagCandidate102AEvidenceBaselineV2Test.java'"),
                COMMAND_ERROR);
        archivePaths.forEach(path -> require(checkIgnoreBlock.contains(
                "\"$archive_rel/" + path + "\""), COMMAND_ERROR));
        JSONObject before = readCanonicalObject(
                BEFORE_SNAPSHOT_SOURCE, WORKTREE_ERROR);
        require(BEFORE_SNAPSHOT_SHA256.equals(
                        hashRegular(BEFORE_SNAPSHOT_SOURCE)),
                WORKTREE_ERROR);
        requireSnapshotSchema(before, "BEFORE");
        require(before.getJSONArray("entries").size() == 6,
                WORKTREE_ERROR);
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
        require(selected != null, HARNESS_ERROR);
        selected.setAccessible(true);
        return invoke(selected, null, arguments);
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof GateFailure gateFailure) {
                throw gateFailure;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw invalid(HARNESS_ERROR, cause);
        }
    }

    private static SelectionRun runSelection(AttemptState state) throws Exception {
        PostgreSQLContainer<?> container = null;
        CountingExecutor executor = null;
        PendingRun pending = null;
        AccessCounter access = null;
        Throwable failure = null;
        DockerBudget docker = new DockerBudget();
        try {
            requireCommandProperties("selection");
            requireRuntimeIdentity();
            verifySourceLock();
            state.sourceLockSha256 = hashRegular(SOURCE_LOCK_FILE);
            access = new AccessCounter();
            state.liveAccess = access;
            RankingInput input = loadRankingInput(access);
            state.access = accessView(access);
            requireAccess(access, false, 0);
            Fixture fixture = validateInput(input);
            state.queryCount = fixture.dataset.queries().size();

            inspectImage(docker);
            DockerImageName image = DockerImageName.parse(IMAGE)
                    .asCompatibleSubstituteFor("postgres");
            container = new PostgreSQLContainer<>(image)
                    .withDatabaseName("shadow")
                    .withUsername("shadow")
                    .withPassword("shadow")
                    .withEnv("TZ", "UTC")
                    .withEnv("LANG", "C")
                    .withEnv("LC_ALL", "C")
                    .withEnv("POSTGRES_INITDB_ARGS",
                            "--encoding=UTF8 --locale=C")
                    .withImagePullPolicy(ignored -> false)
                    .withReuse(false);
            docker.containerConstruct++;
            container.start();
            docker.containerStart++;

            SeededInfrastructure seeded = seedInfrastructure(
                    container, fixture);
            seeded.counters.resetForSelection();
            require(seeded.counters.allZero(), BUDGET_ERROR);

            executor = new CountingExecutor();
            executor.setCorePoolSize(4);
            executor.setMaxPoolSize(4);
            executor.setQueueCapacity(32);
            executor.setThreadNamePrefix("candidate102a-baseline-v2-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(30);
            executor.initialize();
            RuntimeAssembly assembly = assembleRuntime(
                    seeded, executor, fixture);
            List<QueryTrace> traces = runQueries(
                    fixture, assembly, executor);
            requirePerQueryCapacity(traces, assembly.rerankAudit);
            requireExecutorBeforeFreeze(executor);
            requireAccess(access, false, 0);
            access.freezeRanking(traces);
            requireAccess(access, true, 0);
            RagEvaluationDataset labeled;
            try {
                labeled = loadQrelsAfterRanking(fixture, access);
            } finally {
                state.access = accessView(access);
            }
            requireAccess(access, true, 1);
            Evaluation evaluation = evaluateBaseline(
                    fixture, labeled, traces);
            state.targetQueryCount = fixture.targetQueryCount;
            pending = new PendingRun(
                    fixture, seeded, assembly, traces, evaluation,
                    state.access);
        } catch (Throwable current) {
            failure = current;
        } finally {
            try {
                if (executor != null) {
                    try {
                        executor.shutdownAndAwait();
                    } catch (Throwable cleanupFailure) {
                        if (failure == null) {
                            failure = invalid(EXECUTOR_ERROR, cleanupFailure);
                        }
                    }
                }
                try {
                    if (container != null) {
                        container.stop();
                        docker.containerStop++;
                    }
                } catch (Throwable cleanupFailure) {
                    if (failure == null) {
                        failure = invalid(
                                DATABASE_ERROR, cleanupFailure);
                    }
                }
                try {
                    state.infrastructure = infrastructureView(
                            docker, executor,
                            pending != null ? pending.seeded : null);
                } catch (Throwable infrastructureFailure) {
                    if (failure == null) {
                        failure = infrastructureFailure;
                    }
                }
            } finally {
                if (state.liveAccess != null) {
                    state.access = accessView(state.liveAccess);
                }
                try {
                    JSONObject before = readCanonicalObject(
                            BEFORE_SNAPSHOT_FILE, WORKTREE_ERROR);
                    JSONObject after = captureWorktreeSnapshot(
                            "AFTER_SELECTION");
                    createNew(AFTER_SNAPSHOT_FILE,
                            canonicalBytes(after), WORKTREE_ERROR);
                    requireSameWorktree(before, after);
                } catch (Throwable worktreeFailure) {
                    failure = invalid(WORKTREE_ERROR, worktreeFailure);
                }
                try {
                    requireCommandProperties("selection");
                    requireRuntimeIdentity();
                    verifySourceLock();
                } catch (Throwable sourceFailure) {
                    failure = invalid(SOURCE_LOCK_ERROR, sourceFailure);
                }
            }
        }
        if (failure != null) {
            if (failure instanceof Exception exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw invalid(RUNTIME_ERROR, failure);
        }
        require(pending != null && executor != null
                        && executor.terminatedAfterCleanup,
                EXECUTOR_ERROR);
        return pending.finish(docker, executor);
    }

    private static JSONObject validMarker(SelectionRun run) {
        Map<String, Integer> boundaries = run.evaluation.boundaries;
        int visible = boundaries.get("COLBERT90_VISIBLE");
        int frontierMissing = boundaries.get(
                "COLBERT_FRONTIER_NOT_PRESERVED");
        int unresolved = BOUNDARY_NAMES.stream()
                .filter(name -> !"COLBERT90_VISIBLE".equals(name))
                .mapToInt(name -> boundaries.get(name)).sum();
        boolean matched = visible == 12 && frontierMissing == 4
                && unresolved == 4
                && run.evaluation.invalidIdentifierCardinality == 0
                && BOUNDARY_NAMES.stream()
                .filter(name -> !Set.of(
                        "COLBERT90_VISIBLE",
                        "COLBERT_FRONTIER_NOT_PRESERVED").contains(name))
                .allMatch(name -> boundaries.get(name) == 0);
        JSONObject value = markerBase("VALID");
        value.put("queryCount", QUERY_COUNT);
        value.put("targetQueryCount", 16);
        value.put("unresolvedTargetQueryCount", unresolved);
        value.put("observation", matched
                ? "MATCHED_12_VISIBLE_4_FRONTIER_MISSING"
                : "DIVERGED_FROM_12_4");
        value.put("mapping", run.evaluation.mappingView());
        value.put("checkpointPresence", run.evaluation.presenceView());
        value.put("contextLineage", run.evaluation.context.view());
        value.put("earliestBoundaryQueryCounts", boundaries);
        value.put("selectedBoundary",
                matched ? "COLBERT_FRONTIER_PRESERVATION" : null);
        value.put("budgets", run.budgets);
        value.put("infrastructure", run.infrastructure);
        value.put("access", run.access);
        value.put("pathSnapshotSha256", run.evaluation.pathHashes);
        value.put("sourceLockSha256", run.sourceLockSha256);
        value.put("decision", matched
                ? "PROCEED_TO_CANDIDATE102A_SEAM_PLAN_REVIEW"
                : "STOP_CANDIDATE102A_SEAM_BASELINE_DIVERGED");
        value.put("errorCode", null);
        value.put("productionChange", false);
        value.put("algorithmChange", false);
        requireMarkerSchema(value);
        return value;
    }

    private static JSONObject invalidMarker(
            AttemptState state, String errorCode) {
        JSONObject value = markerBase("INVALID");
        value.put("queryCount", state.queryCount);
        value.put("targetQueryCount", state.targetQueryCount);
        value.put("observation", null);
        value.put("selectedBoundary", null);
        value.put("infrastructure", state.infrastructure);
        value.put("access", state.access);
        value.put("sourceLockSha256", state.sourceLockSha256);
        value.put("decision", null);
        value.put("errorCode", errorCode);
        value.put("productionChange", false);
        value.put("algorithmChange", false);
        requireMarkerSchema(value);
        return value;
    }

    private static JSONObject markerBase(String status) {
        JSONObject value = new JSONObject();
        value.put("schemaVersion", SCHEMA);
        value.put("status", status);
        value.put("namespace", NAMESPACE);
        value.put("attempt", ATTEMPT);
        value.put("predecessorStatus", PREDECESSOR_STATUS);
        value.put("causalScope", SCOPE);
        value.put("algorithmConclusion", ALGORITHM_CONCLUSION);
        return value;
    }

    private static void publishMarker(JSONObject marker) {
        requireMarkerSchema(marker);
        byte[] bytes = canonicalBytes(marker);
        createNew(MARKER_FILE, bytes, SAFETY_ERROR);
        require(Arrays.equals(bytes,
                        readRegularBytes(MARKER_FILE, SAFETY_ERROR)),
                SAFETY_ERROR);
        String json = new String(bytes, StandardCharsets.UTF_8);
        require(json.endsWith("\n") && !json.endsWith("\n\n"),
                SAFETY_ERROR);
        System.out.println(MARKER + " "
                + json.substring(0, json.length() - 1));
    }

    private static void requireMarkerSchema(JSONObject marker) {
        require(marker != null
                        && SCHEMA.equals(marker.getString("schemaVersion"))
                        && NAMESPACE.equals(marker.getString("namespace"))
                        && ATTEMPT.equals(marker.getString("attempt"))
                        && PREDECESSOR_STATUS.equals(
                        marker.getString("predecessorStatus"))
                        && SCOPE.equals(marker.getString("causalScope"))
                        && ALGORITHM_CONCLUSION.equals(
                        marker.getString("algorithmConclusion")),
                SAFETY_ERROR);
        boolean valid = "VALID".equals(marker.getString("status"));
        Set<String> expected = valid ? Set.of(
                "schemaVersion", "status", "namespace", "attempt",
                "predecessorStatus", "causalScope", "algorithmConclusion",
                "queryCount", "targetQueryCount",
                "unresolvedTargetQueryCount", "observation", "mapping",
                "checkpointPresence", "contextLineage",
                "earliestBoundaryQueryCounts", "selectedBoundary",
                "budgets", "infrastructure", "access",
                "pathSnapshotSha256", "sourceLockSha256", "decision",
                "errorCode", "productionChange", "algorithmChange") : Set.of(
                "schemaVersion", "status", "namespace", "attempt",
                "predecessorStatus", "causalScope", "algorithmConclusion",
                "queryCount", "targetQueryCount", "observation",
                "selectedBoundary", "infrastructure", "access",
                "sourceLockSha256", "decision", "errorCode",
                "productionChange", "algorithmChange");
        require(marker.keySet().equals(expected)
                        && marker.get("queryCount") instanceof Integer
                        && marker.get("targetQueryCount") instanceof Integer
                        && Boolean.FALSE.equals(marker.get("productionChange"))
                        && Boolean.FALSE.equals(marker.get("algorithmChange"))
                        && marker.getJSONObject("infrastructure") != null
                        && marker.getJSONObject("access") != null,
                SAFETY_ERROR);
        requireAccessSchema(marker.getJSONObject("access"));
        if (valid) {
            require(marker.getIntValue("queryCount") == QUERY_COUNT
                            && marker.getIntValue("targetQueryCount") == 16
                            && marker.get("unresolvedTargetQueryCount")
                            instanceof Integer
                            && marker.getJSONObject("mapping") != null
                            && marker.getJSONObject("checkpointPresence") != null
                            && marker.getJSONObject("contextLineage") != null
                            && marker.getJSONObject(
                            "earliestBoundaryQueryCounts") != null
                            && marker.getJSONObject("budgets") != null
                            && marker.getJSONObject(
                            "pathSnapshotSha256") != null
                            && marker.get("errorCode") == null
                            && marker.getString("sourceLockSha256")
                            .matches("[0-9a-f]{64}"),
                    SAFETY_ERROR);
            String observation = marker.getString("observation");
            String selected = marker.getString("selectedBoundary");
            String decision = marker.getString("decision");
            boolean matched =
                    "MATCHED_12_VISIBLE_4_FRONTIER_MISSING".equals(observation)
                            && "COLBERT_FRONTIER_PRESERVATION".equals(selected)
                            && "PROCEED_TO_CANDIDATE102A_SEAM_PLAN_REVIEW"
                            .equals(decision);
            boolean diverged = "DIVERGED_FROM_12_4".equals(observation)
                    && selected == null
                    && "STOP_CANDIDATE102A_SEAM_BASELINE_DIVERGED"
                    .equals(decision);
            require(matched || diverged, SAFETY_ERROR);
        } else {
            require("INVALID".equals(marker.getString("status"))
                            && marker.get("observation") == null
                            && marker.get("selectedBoundary") == null
                            && marker.get("decision") == null
                            && FIXED_ERRORS.contains(
                            marker.getString("errorCode"))
                            && (marker.get("sourceLockSha256") == null
                            || marker.getString("sourceLockSha256")
                            .matches("[0-9a-f]{64}")),
                    SAFETY_ERROR);
        }
        requireFiniteJson(marker);
        requireNoForbiddenMarkerFields(marker, "");
        byte[] canonical = canonicalBytes(marker);
        require(Arrays.equals(canonical,
                        canonicalBytes(JSON.parseObject(canonical))),
                SAFETY_ERROR);
    }

    private static void requireAccessSchema(JSONObject access) {
        require(access.keySet().equals(Set.of(
                        "selectionNonQrelResourceAccessCount",
                        "qrelResourceAccessBeforeRanking",
                        "qrelResourceAccessCount",
                        "holdoutPathOperationCount"))
                        && access.values().stream().allMatch(
                        value -> value instanceof Integer
                                && ((Integer) value) >= 0),
                SAFETY_ERROR);
    }

    private static void requireBaselineMarkerContracts() {
        AttemptState state = new AttemptState();
        requireMarkerSchema(invalidMarker(state, HARNESS_ERROR));

        Map<String, Integer> matched = zeroBoundaries();
        matched.put("COLBERT90_VISIBLE", 12);
        matched.put("COLBERT_FRONTIER_NOT_PRESERVED", 4);
        JSONObject valid = validMarker(syntheticSelectionRun(matched));
        require("MATCHED_12_VISIBLE_4_FRONTIER_MISSING".equals(
                        valid.getString("observation"))
                        && "PROCEED_TO_CANDIDATE102A_SEAM_PLAN_REVIEW".equals(
                        valid.getString("decision")), SAFETY_ERROR);

        Map<String, Integer> diverged = zeroBoundaries();
        diverged.put("COLBERT90_VISIBLE", 11);
        diverged.put("COLBERT_FRONTIER_NOT_PRESERVED", 5);
        JSONObject stopped = validMarker(syntheticSelectionRun(diverged));
        require("DIVERGED_FROM_12_4".equals(
                        stopped.getString("observation"))
                        && stopped.get("selectedBoundary") == null
                        && "STOP_CANDIDATE102A_SEAM_BASELINE_DIVERGED".equals(
                        stopped.getString("decision")), SAFETY_ERROR);
    }

    private static void requireNoForbiddenMarkerFields(
            Object value, String parentPath) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                require(key instanceof String, SAFETY_ERROR);
                String name = ((String) key).toLowerCase(Locale.ROOT);
                String path = parentPath.isEmpty()
                        ? name : parentPath + "." + name;
                boolean sanitizedMetadataCount =
                        "budgets.databasequeries.metadata".equals(path);
                require(!FORBIDDEN_MARKER_FIELDS.contains(name)
                                || sanitizedMetadataCount,
                        SAFETY_ERROR);
                requireNoForbiddenMarkerFields(nested, path);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(nested ->
                    requireNoForbiddenMarkerFields(nested, parentPath));
        }
    }

    private static void requireFiniteJson(Object value) {
        if (value instanceof Double number) {
            require(Double.isFinite(number), SAFETY_ERROR);
        } else if (value instanceof Float number) {
            require(Float.isFinite(number), SAFETY_ERROR);
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(
                    RagCandidate102AEvidenceBaselineV2Test::requireFiniteJson);
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(
                    RagCandidate102AEvidenceBaselineV2Test::requireFiniteJson);
        }
    }
    private static String classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof GateFailure gateFailure
                    && FIXED_ERRORS.contains(gateFailure.code)) {
                return gateFailure.code;
            }
            current = current.getCause();
        }
        return RUNTIME_ERROR;
    }

    private static final class CounterfactualRerankService
            extends RagRerankService {
        private final RunAudit audit;

        private CounterfactualRerankService(RunAudit audit) {
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<RetrievalResult> rerank(
                String query, List<RetrievalResult> candidates,
                QueryIntent queryIntent, int topK,
                Long rerankingProviderName, String rerankingModelName) {
            audit.counterfactualRerankCalls.incrementAndGet();
            require(topK == BUSINESS_LIMIT, ORCHESTRATION_ERROR);
            if (candidates == null || candidates.isEmpty()) {
                return List.of();
            }
            audit.trace.add("filter");
            audit.filterCalls.incrementAndGet();
            audit.fused = freezeResults(candidates);
            List<RetrievalResult> filtered = (List<RetrievalResult>)
                    invokeRerankStage(this, "filterIrrelevant",
                            query, copyResults(candidates), queryIntent);
            audit.postFilter = freezeResults(filtered);
            audit.colbertInput = freezeResults(filtered);

            audit.trace.add("colbert(90)");
            audit.colbertCalls.incrementAndGet();
            List<RetrievalResult> colbert = (List<RetrievalResult>)
                    invokeRerankStage(this, "colbertCoarseRerank",
                            query, copyResults(filtered), CANDIDATE_TOP_K);
            audit.colbertTop90 = freezeResults(colbert);

            audit.trace.add("admission(90)");
            audit.admissionCalls.incrementAndGet();
            RagCandidate10DiagnosticSupport.AdmissionResult admission;
            try {
                admission = admit(snapshot(colbert), query,
                        new RagCandidate10DiagnosticSupport.Eligibility(
                                true, true, true), ADMISSION_MAX_RANK);
            } catch (ReflectiveOperationException failure) {
                throw invalid(HARNESS_ERROR, failure);
            }
            List<RetrievalResult> admitted = admission.materializeCandidate();
            audit.admissionOutput30 = freezeResults(admitted);

            audit.trace.add("candidate3(30)");
            audit.candidate3Calls.incrementAndGet();
            RerankRequestContext context = RerankRequestContext.builder()
                    .query(query)
                    .providerName(rerankingProviderName)
                    .modelName(rerankingModelName)
                    .build();
            List<RetrievalResult> result = (List<RetrievalResult>)
                    invokeRerankStage(this, "identifierConsistencyRerank",
                            context, copyResults(admitted), queryIntent,
                            BUSINESS_LIMIT);
            audit.candidate3Sources = freezeResults(result);
            return result;
        }
    }

    private static final class CapturingColbertScorer extends ColbertScorer {
        private final RunAudit audit;

        private CapturingColbertScorer(
                ColbertConfig config, RunAudit audit) {
            super(config, null);
            this.audit = audit;
        }

        @Override
        public List<Document> rerank(
                String query, List<Document> documents, int topK) {
            audit.colbertRequestedTopK.add(topK);
            audit.colbertInputDocumentCount.add(documents.size());
            List<Document> result = super.rerank(query, documents, topK);
            audit.colbertOutputDocumentCount.add(result.size());
            return result;
        }
    }

    private static List<RetrievalResult> freezeResults(
            List<RetrievalResult> values) {
        require(values != null, ORCHESTRATION_ERROR);
        List<RetrievalResult> copy = copyResults(values);
        Set<String> keys = new LinkedHashSet<>();
        for (RetrievalResult value : copy) {
            require(value != null && value.getSegmentId() != null
                            && keys.add(segmentKey(value)),
                    MAPPING_ERROR);
        }
        return List.copyOf(copy);
    }

    private static String segmentKey(RetrievalResult value) {
        return "seg:" + Objects.requireNonNull(value.getSegmentId());
    }

    private static final class RunAudit {
        private final List<String> trace = new ArrayList<>();
        private final AtomicInteger counterfactualRerankCalls =
                new AtomicInteger();
        private final AtomicInteger filterCalls = new AtomicInteger();
        private final AtomicInteger colbertCalls = new AtomicInteger();
        private final AtomicInteger admissionCalls = new AtomicInteger();
        private final AtomicInteger candidate3Calls = new AtomicInteger();
        private final List<Integer> colbertRequestedTopK = new ArrayList<>();
        private final List<Integer> colbertInputDocumentCount = new ArrayList<>();
        private final List<Integer> colbertOutputDocumentCount = new ArrayList<>();
        private List<RetrievalResult> fused = List.of();
        private List<RetrievalResult> postFilter = List.of();
        private List<RetrievalResult> colbertInput = List.of();
        private List<RetrievalResult> colbertTop90 = List.of();
        private List<RetrievalResult> admissionOutput30 = List.of();
        private List<RetrievalResult> candidate3Sources = List.of();
        private ColbertScorer.ColbertConfig colbertConfig;
        private CapturingColbertScorer colbertScorer;

        private void beginQuery() {
            trace.clear();
            fused = List.of();
            postFilter = List.of();
            colbertInput = List.of();
            colbertTop90 = List.of();
            admissionOutput30 = List.of();
            candidate3Sources = List.of();
        }

        private RerankSnapshot querySnapshot() {
            require(trace.equals(List.of(
                            "filter", "colbert(90)", "admission(90)",
                            "candidate3(30)"))
                            && orderedKeys(postFilter).equals(
                            orderedKeys(colbertInput)),
                    ORCHESTRATION_ERROR);
            return new RerankSnapshot(
                    freezeKeys(fused), freezeKeys(postFilter),
                    freezeKeys(colbertInput), freezeKeys(colbertTop90),
                    freezeKeys(admissionOutput30),
                    freezeKeys(candidate3Sources));
        }
    }

    private static final class CountingEmbeddingModel implements EmbeddingModel {
        private final FeatureHashEmbeddingModel delegate;
        private final SeedCounters counters;

        private CountingEmbeddingModel(
                FeatureHashEmbeddingModel delegate, SeedCounters counters) {
            this.delegate = delegate;
            this.counters = counters;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            counters.queryEmbeddingAfterSeed +=
                    request.getInstructions().size();
            return delegate.call(request);
        }

        @Override
        public float[] embed(Document document) {
            require(!counters.selectionPhase, ADAPTER_ERROR);
            counters.seedInputs.add(new SeedEmbeddingInput(
                    document.getId(), document.getText()));
            return delegate.embed(document);
        }

        @Override
        public float[] embed(String text) {
            require(counters.selectionPhase, ADAPTER_ERROR);
            counters.queryEmbeddingAfterSeed++;
            return delegate.embed(text);
        }

        @Override
        public int dimensions() {
            return delegate.dimensions();
        }
    }

    private static final class CountingExecutor extends ThreadPoolTaskExecutor {
        private final AtomicLong submitAttempt = new AtomicLong();
        private final AtomicLong accepted = new AtomicLong();
        private final AtomicLong callableSubmit = new AtomicLong();
        private final AtomicLong runnableSubmit = new AtomicLong();
        private final AtomicLong started = new AtomicLong();
        private final AtomicLong succeeded = new AtomicLong();
        private final AtomicLong completed = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();
        private final List<Future<?>> futures =
                Collections.synchronizedList(new ArrayList<>());
        private int activeBeforeFreeze;
        private int queuedBeforeFreeze;
        private int remainingBeforeFreeze;
        private boolean terminatedAfterCleanup;

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            submitAttempt.incrementAndGet();
            callableSubmit.incrementAndGet();
            try {
                Future<T> future = super.submit(() -> {
                    started.incrementAndGet();
                    try {
                        T value = task.call();
                        succeeded.incrementAndGet();
                        return value;
                    } catch (Exception failure) {
                        failed.incrementAndGet();
                        throw failure;
                    } catch (Error failure) {
                        failed.incrementAndGet();
                        throw failure;
                    } finally {
                        completed.incrementAndGet();
                    }
                });
                accepted.incrementAndGet();
                futures.add(future);
                return future;
            } catch (RuntimeException failure) {
                rejected.incrementAndGet();
                throw failure;
            }
        }

        @Override
        public Future<?> submit(Runnable task) {
            submitAttempt.incrementAndGet();
            runnableSubmit.incrementAndGet();
            try {
                Future<?> future = super.submit(task);
                accepted.incrementAndGet();
                futures.add(future);
                return future;
            } catch (RuntimeException failure) {
                rejected.incrementAndGet();
                throw failure;
            }
        }

        private void awaitIdle() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                if (getActiveCount() == 0 && getQueueSize() == 0) {
                    return;
                }
                Thread.onSpinWait();
            }
            throw invalid(EXECUTOR_ERROR);
        }

        private void shutdownAndAwait() {
            initiateShutdown();
            ThreadPoolExecutor pool = getThreadPoolExecutor();
            try {
                terminatedAfterCleanup = pool.awaitTermination(
                        30, TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw invalid(EXECUTOR_ERROR, failure);
            }
            if (!terminatedAfterCleanup) {
                pool.shutdownNow();
                throw invalid(EXECUTOR_ERROR);
            }
        }

        private Map<String, Object> view() {
            long done = futures.stream().filter(Future::isDone).count();
            long cancelled = futures.stream().filter(
                    Future::isCancelled).count();
            ThreadPoolExecutor pool = getThreadPoolExecutor();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("corePoolSize", getCorePoolSize());
            value.put("maxPoolSize", getMaxPoolSize());
            value.put("queueCapacity", 32);
            value.put("submitAttemptCount", submitAttempt.get());
            value.put("acceptedSubmitCount", accepted.get());
            value.put("callableSubmitCount", callableSubmit.get());
            value.put("runnableSubmitCount", runnableSubmit.get());
            value.put("started", started.get());
            value.put("succeeded", succeeded.get());
            value.put("completed", completed.get());
            value.put("failed", failed.get());
            value.put("rejected", rejected.get());
            value.put("doneFutureCount", done);
            value.put("cancelledFutureCount", cancelled);
            value.put("taskCount", pool.getTaskCount());
            value.put("completedTaskCount", pool.getCompletedTaskCount());
            value.put("activeBeforeQrelFreeze", activeBeforeFreeze);
            value.put("queuedBeforeQrelFreeze", queuedBeforeFreeze);
            value.put("queueRemainingCapacityBeforeQrelFreeze",
                    remainingBeforeFreeze);
            value.put("terminatedAfterCleanup", terminatedAfterCleanup);
            return Map.copyOf(value);
        }
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private final SqlCounters counters;
        private final String category;

        private CountingJdbcTemplate(
                DataSource dataSource, SqlCounters counters,
                String category) {
            super(dataSource);
            this.counters = counters;
            this.category = category;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            counters.attempt(category, sql);
            try {
                List<T> result = super.query(sql, rowMapper);
                counters.success(category);
                return result;
            } catch (RuntimeException failure) {
                counters.failure();
                throw failure;
            }
        }

        @Override
        public <T> List<T> query(
                String sql, RowMapper<T> rowMapper, Object... args) {
            counters.attempt(category, sql);
            try {
                List<T> result = super.query(sql, rowMapper, args);
                counters.success(category);
                return result;
            } catch (RuntimeException failure) {
                counters.failure();
                throw failure;
            }
        }
    }

    private static final class CapturingKeywordRetriever
            extends KeywordRetriever {
        private final CaptureState capture;
        private final QueryCounters calls;

        private CapturingKeywordRetriever(
                CaptureState capture, QueryCounters calls) {
            this.capture = capture;
            this.calls = calls;
        }

        @Override
        public List<RetrievalResult> retrieve(
                Long knowledgeBaseId, String query, int topK) {
            calls.keywordRetriever++;
            require(topK == CANDIDATE_TOP_K, BUDGET_ERROR);
            List<RetrievalResult> result =
                    super.retrieve(knowledgeBaseId, query, topK);
            capture.current().setRaw("keyword", result);
            return result;
        }
    }

    private static final class CapturingVectorRetriever
            extends VectorRetriever {
        private final CaptureState capture;
        private final QueryCounters calls;

        private CapturingVectorRetriever(
                CaptureState capture, QueryCounters calls) {
            this.capture = capture;
            this.calls = calls;
        }

        @Override
        public List<RetrievalResult> retrieve(
                Long knowledgeBaseId, String query, int topK, Integer dayNo) {
            calls.vectorRetriever++;
            require(topK == CANDIDATE_TOP_K, BUDGET_ERROR);
            List<RetrievalResult> result = super.retrieve(
                    knowledgeBaseId, query, topK, dayNo);
            capture.current().setRaw("vector", result);
            return result;
        }
    }

    private static final class CapturingMetadataRetriever
            extends MetadataRetriever {
        private final CaptureState capture;
        private final QueryCounters calls;

        private CapturingMetadataRetriever(
                CaptureState capture, QueryCounters calls) {
            this.capture = capture;
            this.calls = calls;
        }

        @Override
        public List<RetrievalResult> retrieve(
                Long knowledgeBaseId, QueryIntent intent, int topK) {
            calls.metadataRetriever++;
            require(topK == CANDIDATE_TOP_K, BUDGET_ERROR);
            List<RetrievalResult> result = super.retrieve(
                    knowledgeBaseId, intent, topK);
            capture.current().setRaw("metadata", result);
            return result;
        }
    }

    private static final class CapturingGraphRetriever
            extends GraphRagRetriever {
        private final CaptureState capture;
        private final QueryCounters calls;

        private CapturingGraphRetriever(
                CaptureState capture, QueryCounters calls) {
            this.capture = capture;
            this.calls = calls;
        }

        @Override
        public List<RetrievalResult> retrieve(
                Long knowledgeBaseId, QueryIntent intent, String query,
                QueryRouter.QueryRoute route, int topK) {
            calls.graphRetriever++;
            require(topK == CANDIDATE_TOP_K, BUDGET_ERROR);
            List<RetrievalResult> result = super.retrieve(
                    knowledgeBaseId, intent, query, route, topK);
            capture.current().setRaw("graph", result);
            return result;
        }
    }

    private static final class CapturingEntityExtraction
            extends QueryEntityExtractionService {
        private final QueryCounters calls;

        private CapturingEntityExtraction(
                IChatModelService service, QueryEntityConfig config,
                QueryCounters calls) {
            super(service, config);
            this.calls = calls;
        }

        @Override
        public List<String> extract(
                String query, List<String> fallbackKeywords) {
            calls.queryEntityExtract++;
            return super.extract(query, fallbackKeywords);
        }
    }

    private static final class CapturingFusionService
            extends CandidateFusionService {
        private final CaptureState capture;
        private final QueryCounters calls;

        private CapturingFusionService(
                CaptureState capture, QueryCounters calls) {
            this.capture = capture;
            this.calls = calls;
        }

        @Override
        public FusionResult fuseWithDiagnostics(
                List<List<RetrievalResult>> resultLists,
                List<String> pathNames) {
            calls.fusion++;
            List<Set<String>> inputKeys = freezePathKeys(resultLists);
            List<String> inputPathNames = List.copyOf(pathNames);
            FusionResult result = super.fuseWithDiagnostics(
                    resultLists, pathNames);
            capture.current().setFusion(
                    inputKeys, inputPathNames, result);
            return result;
        }
    }

    private static final class CapturingContextBuilder
            extends RagContextBuilder {
        private final CaptureState capture;
        private final QueryCounters calls;
        private final SqlCounters sql;

        private CapturingContextBuilder(
                CaptureState capture, QueryCounters calls, SqlCounters sql) {
            this.capture = capture;
            this.calls = calls;
            this.sql = sql;
        }

        @Override
        public String buildContext(
                List<RetrievalResult> results, boolean expandAdjacent) {
            calls.context++;
            Set<String> sourceKeys = freezeKeys(results);
            long parentBefore = sql.contextParent;
            long adjacentBefore = sql.contextAdjacent;
            String context = super.buildContext(results, expandAdjacent);
            capture.current().setContextSource(sourceKeys,
                    sql.contextParent - parentBefore,
                    sql.contextAdjacent - adjacentBefore);
            return context;
        }
    }

    private static final class CaptureState {
        private volatile QueryCapture current;

        private synchronized QueryCapture begin(
                String queryId, int identifierCardinality,
                Set<String> exactKeys) {
            require(current == null, MAPPING_ERROR);
            current = new QueryCapture(
                    queryId, identifierCardinality, exactKeys);
            return current;
        }

        private QueryCapture current() {
            QueryCapture value = current;
            require(value != null, MAPPING_ERROR);
            return value;
        }

        private synchronized void clear(QueryCapture expected) {
            require(current == expected, MAPPING_ERROR);
            current = null;
        }
    }

    private static final class QueryCapture {
        private final String queryId;
        private final int identifierCardinality;
        private final Set<String> exactKeys;
        private final Map<String, Set<String>> raw = new LinkedHashMap<>();
        private Set<String> retrieverUnion = Set.of();
        private Set<String> weakPathEligibleUnion = Set.of();
        private Set<String> fused = Set.of();
        private Set<String> candidate3Sources = Set.of();
        private Set<String> contextRendered = Set.of();
        private Set<String> contextSources = Set.of();
        private String contextText = "";
        private String expansionMode = "NONE";
        private long contextParentQueries;
        private long contextAdjacentQueries;
        private RerankSnapshot rerank;

        private QueryCapture(
                String queryId, int identifierCardinality,
                Set<String> exactKeys) {
            this.queryId = queryId;
            this.identifierCardinality = identifierCardinality;
            this.exactKeys = Set.copyOf(exactKeys);
        }

        private synchronized void setRaw(
                String path, List<RetrievalResult> values) {
            require(!raw.containsKey(path), MAPPING_ERROR);
            raw.put(path, freezeKeys(values));
        }

        private synchronized void setFusion(
                List<Set<String>> pathKeys, List<String> pathNames,
                CandidateFusionService.FusionResult result) {
            require(pathKeys.size() == pathNames.size(), FUSION_ERROR);
            Set<String> union = new LinkedHashSet<>();
            Set<String> eligible = new LinkedHashSet<>();
            Set<String> excluded = Set.copyOf(result.getExcludedPaths());
            for (int index = 0; index < pathKeys.size(); index++) {
                Set<String> keys = pathKeys.get(index);
                union.addAll(keys);
                if (!excluded.contains(pathNames.get(index))) {
                    eligible.addAll(keys);
                }
            }
            retrieverUnion = Set.copyOf(union);
            weakPathEligibleUnion = Set.copyOf(eligible);
            fused = freezeKeys(result.getResults());
        }

        private synchronized void setContextSource(
                Set<String> sourceKeys, long parent, long adjacent) {
            contextSources = Set.copyOf(sourceKeys);
            contextParentQueries = parent;
            contextAdjacentQueries = adjacent;
            expansionMode = parent > 0 && adjacent > 0
                    ? "PARENT_FALLBACK_ADJACENT"
                    : parent > 0 ? "PARENT"
                    : adjacent > 0 ? "ADJACENT" : "NONE";
        }

        private void validateFusion() {
            require(raw.keySet().equals(Set.of(
                            "keyword", "vector", "metadata", "graph")),
                    MAPPING_ERROR);
            Set<String> rawUnion = new LinkedHashSet<>();
            raw.values().forEach(rawUnion::addAll);
            require(rawUnion.equals(retrieverUnion)
                            && retrieverUnion.equals(weakPathEligibleUnion)
                            && weakPathEligibleUnion.equals(fused),
                    FUSION_ERROR);
        }

        private QueryTrace freeze() {
            require(rerank != null
                            && fused.equals(rerank.fused)
                            && rerank.postFilter.equals(rerank.colbertInput)
                            && candidate3Sources.equals(
                            rerank.candidate3Sources),
                    MAPPING_ERROR);
            Map<String, Set<String>> stages = new LinkedHashMap<>();
            stages.put("keywordRaw", raw.get("keyword"));
            stages.put("vectorRaw", raw.get("vector"));
            stages.put("metadataRaw", raw.get("metadata"));
            stages.put("graphRaw", raw.get("graph"));
            stages.put("retrieverUnion", retrieverUnion);
            stages.put("weakPathEligibleUnion", weakPathEligibleUnion);
            stages.put("fused", fused);
            stages.put("postFilter", rerank.postFilter);
            stages.put("colbertInput", rerank.colbertInput);
            stages.put("colbertTop90", rerank.colbertTop90);
            stages.put("admissionOutput30", rerank.admissionOutput30);
            stages.put("candidate3Sources", candidate3Sources);
            stages.put("contextRendered", contextRendered);
            require(stages.keySet().equals(new LinkedHashSet<>(CHECKPOINT_NAMES)),
                    MAPPING_ERROR);
            return new QueryTrace(queryId, identifierCardinality, exactKeys,
                    Map.copyOf(stages),
                    contextSources, contextRendered, expansionMode,
                    contextParentQueries, contextAdjacentQueries);
        }
    }

    private static GateFailure invalid(String code) {
        return new GateFailure(code, null);
    }

    private static GateFailure invalid(String code, Throwable cause) {
        return new GateFailure(code, cause);
    }

    private static void require(boolean condition, String errorCode) {
        if (!condition) {
            throw invalid(errorCode);
        }
    }

    private static final class GateFailure extends RuntimeException {
        private final String code;

        private GateFailure(String code, Throwable cause) {
            super(code, cause, false, false);
            this.code = code;
        }
    }

    private static final class AttemptState {
        private int queryCount;
        private int targetQueryCount;
        private Map<String, Object> access = Map.of(
                "selectionNonQrelResourceAccessCount", 0,
                "qrelResourceAccessBeforeRanking", 0,
                "qrelResourceAccessCount", 0,
                "holdoutPathOperationCount", 0);
        private Map<String, Object> infrastructure = emptyInfrastructure();
        private String sourceLockSha256;
        private AccessCounter liveAccess;
    }

    private record SelectionRun(
            Evaluation evaluation,
            Map<String, Object> budgets,
            Map<String, Object> infrastructure,
            Map<String, Object> access,
            String sourceLockSha256) {
    }

    private record PendingRun(
            Fixture fixture,
            SeededInfrastructure seeded,
            RuntimeAssembly assembly,
            List<QueryTrace> traces,
            Evaluation evaluation,
            Map<String, Object> access) {

        private SelectionRun finish(
                DockerBudget docker, CountingExecutor executor) {
            Map<String, Object> budgets = budgetView(
                    assembly, executor, traces);
            Map<String, Object> infrastructure = infrastructureView(
                    docker, executor, seeded);
            return new SelectionRun(evaluation, budgets, infrastructure,
                    access, hashRegular(SOURCE_LOCK_FILE));
        }
    }

    private record RankingInput(
            RagEvaluationDataset dataset,
            Map<String, RagCandidate10FixtureGenerator.FamilySpec> families,
            List<RetrievalResult> pool) {

        private RankingInput {
            Objects.requireNonNull(dataset, "dataset");
            families = Map.copyOf(families);
            pool = List.copyOf(pool);
        }
    }

    private static final class AccessCounter {
        private int selectionNonQrelResourceAccessCount;
        private int qrelResourceAccessBeforeRanking;
        private int qrelResourceAccessCount;
        private int holdoutPathOperationCount;
        private boolean rankingFrozen;

        private boolean allZero() {
            return selectionNonQrelResourceAccessCount == 0
                    && qrelResourceAccessBeforeRanking == 0
                    && qrelResourceAccessCount == 0
                    && holdoutPathOperationCount == 0 && !rankingFrozen;
        }

        private void freezeRanking(List<QueryTrace> traces) {
            require(!rankingFrozen && traces.size() == QUERY_COUNT
                            && traces.stream().allMatch(trace ->
                            trace.stages.keySet().equals(
                                    new LinkedHashSet<>(CHECKPOINT_NAMES))),
                    QREL_ERROR);
            qrelResourceAccessBeforeRanking = qrelResourceAccessCount;
            rankingFrozen = true;
        }
    }

    private record RuntimeAssembly(
            RagRetrievalService service,
            QueryIntentAnalyzer intentAnalyzer,
            CaptureState capture,
            QueryCounters calls,
            SqlCounters sql,
            SeedCounters counters,
            RunAudit rerankAudit,
            CapturingContextBuilder context,
            GraphRagProperties graphProperties,
            QueryEntityExtractionService.QueryEntityConfig entityConfig,
            DynamicTopKConfig dynamicTopK,
            Map<String, Object> configuration) {
    }

    private static final class Fixture {
        private final RagEvaluationDataset dataset;
        private final Map<String, RagCandidate10FixtureGenerator.FamilySpec>
                families;
        private final List<CorpusRow> rows;
        private final Map<Long, RetrievalResult> poolBySegment;
        private final int targetQueryCount;
        private final byte[] corpusProjection;
        private final Map<Long, CorpusRow> rowBySegmentId;
        private final Map<String, CorpusRow> rowByDatasetKey;
        private final Set<String> allSegmentKeys;

        private Fixture(
                RagEvaluationDataset dataset,
                Map<String, RagCandidate10FixtureGenerator.FamilySpec> families,
                List<CorpusRow> rows,
                Map<Long, RetrievalResult> poolBySegment,
                int targetQueryCount, byte[] corpusProjection) {
            this.dataset = dataset;
            this.families = families;
            this.rows = rows;
            this.poolBySegment = poolBySegment;
            this.targetQueryCount = targetQueryCount;
            this.corpusProjection = corpusProjection.clone();
            this.rowBySegmentId = rows.stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            CorpusRow::segmentId, value -> value));
            this.rowByDatasetKey = rows.stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            CorpusRow::datasetKey, value -> value));
            this.allSegmentKeys = rows.stream()
                    .map(row -> "seg:" + row.segmentId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private record CorpusRow(
            String datasetKey,
            long segmentId,
            long documentId,
            String documentName,
            String content,
            String parentSegmentId,
            Map<String, Object> metadata,
            RetrievalResult original) {

        private int ordinal() {
            return (Integer) metadata.get("ordinal");
        }
    }

    private record SeedEmbeddingInput(String id, String text) {
    }

    private record SeedEvidence(
            String corpusProjectionSha256,
            String postgresProjectionSha256,
            String vectorProjectionSha256) {
    }

    private static final class SeedCounters {
        private final List<SeedEmbeddingInput> seedInputs = new ArrayList<>();
        private boolean selectionPhase;
        private long vectorSeedAddCount;
        private long kbMapperLookup;
        private long embeddingModelResolve;
        private long vectorStoreResolve;
        private long similaritySearch;
        private long queryEmbeddingAfterSeed;
        private long documentEmbeddingAfterSeed;
        private long vectorAdd;
        private long vectorDelete;
        private long chatModelResolve;
        private long chatModelCall;

        private void resetForSelection() {
            selectionPhase = true;
            kbMapperLookup = 0;
            embeddingModelResolve = 0;
            vectorStoreResolve = 0;
            similaritySearch = 0;
            queryEmbeddingAfterSeed = 0;
            documentEmbeddingAfterSeed = 0;
            vectorAdd = 0;
            vectorDelete = 0;
            chatModelResolve = 0;
            chatModelCall = 0;
        }

        private boolean allZero() {
            return selectionPhase && kbMapperLookup == 0
                    && embeddingModelResolve == 0 && vectorStoreResolve == 0
                    && similaritySearch == 0 && queryEmbeddingAfterSeed == 0
                    && documentEmbeddingAfterSeed == 0 && vectorAdd == 0
                    && vectorDelete == 0 && chatModelResolve == 0
                    && chatModelCall == 0;
        }

        private Map<String, Object> adapterView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("kbMapperLookup", kbMapperLookup);
            value.put("embeddingModelResolve", embeddingModelResolve);
            value.put("vectorStoreResolve", vectorStoreResolve);
            value.put("similaritySearch", similaritySearch);
            value.put("documentEmbeddingAfterSeed", documentEmbeddingAfterSeed);
            value.put("queryEmbeddingAfterSeed", queryEmbeddingAfterSeed);
            value.put("totalEmbeddingInputsAfterSeed",
                    documentEmbeddingAfterSeed + queryEmbeddingAfterSeed);
            value.put("vectorAdd", vectorAdd);
            value.put("vectorDelete", vectorDelete);
            value.put("chatModelResolve", chatModelResolve);
            value.put("chatModelCall", chatModelCall);
            return Map.copyOf(value);
        }
    }

    private record SeededInfrastructure(
            DriverManagerDataSource dataSource,
            JdbcTemplate bootstrap,
            SimpleVectorStore store,
            CountingEmbeddingModel embedding,
            SeedCounters counters,
            SeedEvidence evidence) {

        private Map<String, Object> seedView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("loadRankingInputCalls", 1);
            value.put("queryCount", QUERY_COUNT);
            value.put("familyCount", FAMILY_COUNT);
            value.put("corpusCount", CORPUS_COUNT);
            value.put("postgresDocumentCount", CORPUS_COUNT);
            value.put("postgresSegmentCount", CORPUS_COUNT);
            value.put("postgresEntityMetadataCount", 0);
            value.put("vectorDocumentCount", CORPUS_COUNT);
            value.put("vectorSnapshotKeyCount", CORPUS_COUNT);
            value.put("seedEmbeddingInputCount", counters.seedInputs.size());
            value.put("corpusProjectionSha256",
                    evidence.corpusProjectionSha256);
            value.put("postgresProjectionSha256",
                    evidence.postgresProjectionSha256);
            value.put("vectorProjectionSha256",
                    evidence.vectorProjectionSha256);
            value.put("vectorSnapshotKeySetExact", true);
            value.put("seedEmbeddingInputOrderExact", true);
            value.put("projectionBytesEqual", true);
            value.put("countersResetBeforeSelection", true);
            return Map.copyOf(value);
        }
    }

    private static final class QueryCounters {
        private long queryIntentAnalyze;
        private long retrieveOnce;
        private long queryEntityExtract;
        private long keywordRetriever;
        private long vectorRetriever;
        private long metadataRetriever;
        private long graphRetriever;
        private long fusion;
        private long context;
    }

    private static final class SqlCounters {
        private long keyword;
        private long metadata;
        private long vector;
        private long contextParent;
        private long contextAdjacent;
        private long contextOther;
        private long other;
        private long attempted;
        private long succeeded;
        private long failed;

        private synchronized void attempt(String category, String sql) {
            attempted++;
            switch (category) {
                case "keyword" -> keyword++;
                case "metadata" -> metadata++;
                case "vector" -> vector++;
                case "context" -> {
                    String normalized = sql.toLowerCase(Locale.ROOT);
                    if (normalized.contains("qm_segment_id in")) {
                        contextParent++;
                    } else if (normalized.contains("position - 1")
                            && normalized.contains("position + 1")) {
                        contextAdjacent++;
                    } else {
                        contextOther++;
                    }
                }
                default -> other++;
            }
        }

        private synchronized void success(String ignored) {
            succeeded++;
        }

        private synchronized void failure() {
            failed++;
        }

        private Map<String, Object> view() {
            long context = contextParent + contextAdjacent;
            long total = keyword + metadata + context;
            require(keyword == QUERY_COUNT && metadata >= 0
                            && metadata <= QUERY_COUNT && vector == 0
                            && contextParent >= 0
                            && contextParent <= QUERY_COUNT
                            && contextAdjacent >= 0
                            && contextAdjacent <= QUERY_COUNT
                            && contextOther == 0 && other == 0
                            && attempted == total && succeeded == total
                            && failed == 0 && context <= 80,
                    BUDGET_ERROR);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("keyword", keyword);
            value.put("metadata", metadata);
            value.put("vector", vector);
            value.put("contextParent", contextParent);
            value.put("contextAdjacent", contextAdjacent);
            value.put("contextOther", contextOther);
            value.put("other", other);
            value.put("attempted", attempted);
            value.put("succeeded", succeeded);
            value.put("failed", failed);
            value.put("context", context);
            value.put("total", total);
            return Map.copyOf(value);
        }
    }

    private record RerankSnapshot(
            Set<String> fused,
            Set<String> postFilter,
            Set<String> colbertInput,
            Set<String> colbertTop90,
            Set<String> admissionOutput30,
            Set<String> candidate3Sources) {
    }

    private record QueryTrace(
            String queryId,
            int identifierCardinality,
            Set<String> exactKeys,
            Map<String, Set<String>> stages,
            Set<String> contextSources,
            Set<String> contextRendered,
            String expansionMode,
            long contextParentQueries,
            long contextAdjacentQueries) {

        private Map<String, Set<String>> monotonicStages() {
            Map<String, Set<String>> value = new LinkedHashMap<>();
            for (String name : List.of(
                    "retrieverUnion", "weakPathEligibleUnion", "fused",
                    "postFilter", "colbertInput", "colbertTop90",
                    "admissionOutput30", "candidate3Sources")) {
                value.put(name, stages.get(name));
            }
            return value;
        }
    }

    private static final class PresenceAccumulator {
        private int pairs;
        private int targetQueries;

        private Map<String, Object> view() {
            return Map.of(
                    "relevantExactQuerySegmentPairCount", pairs,
                    "targetQueryCountWithAnyRelevantExact", targetQueries);
        }
    }

    private record ContextCounts(
            long sourceSegmentCount,
            long sourceExactCount,
            long contextSegmentCount,
            long contextSourceVisibleCount,
            long contextSourceOmittedCount,
            long contextIntroducedCount,
            long contextSourceExactVisibleCount,
            long contextSourceExactOmittedCount,
            long contextIntroducedExactCount,
            long contextExactCount) {
    }

    private static final class ContextAccumulator {
        private long sourceSegmentCount;
        private long sourceExactCount;
        private long contextSegmentCount;
        private long contextSourceVisibleCount;
        private long contextSourceOmittedCount;
        private long contextIntroducedCount;
        private long contextSourceExactVisibleCount;
        private long contextSourceExactOmittedCount;
        private long contextIntroducedExactCount;
        private long contextExactCount;
        private final Map<String, Long> modeCounts = new LinkedHashMap<>(Map.of(
                "NONE", 0L, "PARENT", 0L, "ADJACENT", 0L,
                "PARENT_FALLBACK_ADJACENT", 0L));
        private long parentQueries;
        private long adjacentQueries;
        private long totalQueries;

        private ContextCounts counts() {
            return new ContextCounts(sourceSegmentCount, sourceExactCount,
                    contextSegmentCount, contextSourceVisibleCount,
                    contextSourceOmittedCount, contextIntroducedCount,
                    contextSourceExactVisibleCount,
                    contextSourceExactOmittedCount,
                    contextIntroducedExactCount, contextExactCount);
        }

        private ContextEvidence freeze() {
            return new ContextEvidence(counts(), Map.copyOf(modeCounts),
                    parentQueries, adjacentQueries, totalQueries);
        }
    }

    private record ContextEvidence(
            ContextCounts counts,
            Map<String, Long> modeCounts,
            long parentQueries,
            long adjacentQueries,
            long totalQueries) {

        private Map<String, Object> view() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("sourceSegmentCount", counts.sourceSegmentCount);
            value.put("sourceExactCount", counts.sourceExactCount);
            value.put("contextSegmentCount", counts.contextSegmentCount);
            value.put("contextSourceVisibleCount",
                    counts.contextSourceVisibleCount);
            value.put("contextSourceOmittedCount",
                    counts.contextSourceOmittedCount);
            value.put("contextIntroducedCount", counts.contextIntroducedCount);
            value.put("contextSourceExactVisibleCount",
                    counts.contextSourceExactVisibleCount);
            value.put("contextSourceExactOmittedCount",
                    counts.contextSourceExactOmittedCount);
            value.put("contextIntroducedExactCount",
                    counts.contextIntroducedExactCount);
            value.put("contextExactCount", counts.contextExactCount);
            value.put("expansionModeCounts", modeCounts);
            value.put("contextJdbcQueryCounts", Map.of(
                    "parent", parentQueries,
                    "adjacent", adjacentQueries,
                    "total", totalQueries));
            return Map.copyOf(value);
        }
    }

    private record Evaluation(
            int qrelPairs,
            int mappedPairs,
            int relevantExactPairs,
            int nonExactPairs,
            Map<String, PresenceAccumulator> presence,
            ContextEvidence context,
            Map<String, Integer> boundaries,
            Map<String, String> pathHashes,
            int invalidIdentifierCardinality) {

        private Map<String, Object> mappingView() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("qrelRelevantQuerySegmentPairCount", qrelPairs);
            value.put("mappedQrelRelevantQuerySegmentPairCount", mappedPairs);
            value.put("relevantExactQuerySegmentPairCount", relevantExactPairs);
            value.put("nonExactRelevantQuerySegmentPairCount", nonExactPairs);
            value.put("missingQrelRelevantQuerySegmentPairCount", 0);
            value.put("duplicateCorpusKeyCount", 0);
            value.put("duplicateCheckpointKeyCount", 0);
            value.put("nonMonotonicLifecyclePairCount", 0);
            return Map.copyOf(value);
        }

        private Map<String, Object> presenceView() {
            Map<String, Object> value = new LinkedHashMap<>();
            CHECKPOINT_NAMES.forEach(name -> value.put(
                    name, presence.get(name).view()));
            return Map.copyOf(value);
        }
    }

    private static final class DockerBudget {
        private int imageInspect;
        private int containerConstruct;
        private int containerStart;
        private int imagePull;
        private int containerStop;

        private Map<String, Object> view() {
            return Map.of(
                    "imageInspect", imageInspect,
                    "containerConstruct", containerConstruct,
                    "containerStart", containerStart,
                    "imagePull", imagePull,
                    "containerStop", containerStop);
        }
    }

}
