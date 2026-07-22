package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
import tech.qiantong.qknow.ai.service.IEmbeddingService;
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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Algorithm-neutral, qrel-after-freeze Candidate 10.1 lifecycle checkpoint. */
class RagCandidate101IdentifierExactLifecycleCheckpointTest {

    private static final String ENABLE_PROPERTY =
            "rag.eval.candidate10.identifier-exact-lifecycle-checkpoint";
    private static final String MARKER =
            "CANDIDATE101_IDENTIFIER_EXACT_LIFECYCLE";
    private static final String SCHEMA =
            "candidate101-identifier-exact-lifecycle-checkpoint-v1";
    private static final String SCOPE =
            "post-routing-single-retrieve-once-single-variant-"
                    + "counterfactual-rerank-orchestration-vecsim-disabled-"
                    + "colbert90-admission90-selection-v1";
    private static final String ALGORITHM_CONCLUSION = "NOT_REACHED";

    private static final String SOURCE_LOCK_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_SOURCE_LOCK_INVALID";
    private static final String COMMAND_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_COMMAND_INVALID";
    private static final String FROZEN_INPUT_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_FROZEN_INPUT_INVALID";
    private static final String DATABASE_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_DATABASE_INVALID";
    private static final String ADAPTER_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_INFRASTRUCTURE_ADAPTER_INVALID";
    private static final String EXECUTOR_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_EXECUTOR_INVALID";
    private static final String ORCHESTRATION_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_COUNTERFACTUAL_ORCHESTRATION_INVALID";
    private static final String FALLBACK_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_FALLBACK_INVALID";
    private static final String MAPPING_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_CHECKPOINT_MAPPING_INVALID";
    private static final String FUSION_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_FUSION_CONTRACT_INVALID";
    private static final String CONTEXT_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_CONTEXT_CHECKPOINT_INVALID";
    private static final String BUDGET_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_BUDGET_INVALID";
    private static final String QREL_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_QREL_ACCESS_INVALID";
    private static final String SAFETY_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_SAFETY_INVALID";
    private static final String RUNTIME_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_RUNTIME_INVALID";
    private static final String HARNESS_ERROR =
            "CANDIDATE101_IDENTIFIER_LIFECYCLE_HARNESS_INVALID";
    private static final Set<String> FIXED_ERRORS = Set.of(
            SOURCE_LOCK_ERROR, COMMAND_ERROR, FROZEN_INPUT_ERROR,
            DATABASE_ERROR, ADAPTER_ERROR, EXECUTOR_ERROR,
            ORCHESTRATION_ERROR, FALLBACK_ERROR, MAPPING_ERROR,
            FUSION_ERROR, CONTEXT_ERROR, BUDGET_ERROR, QREL_ERROR,
            SAFETY_ERROR, RUNTIME_ERROR, HARNESS_ERROR);

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
    private static final String DEPTH_SOURCE =
            "src/test/java/tech/qiantong/qknow/rag/eval/"
                    + "RagCandidate101CandidateDepthResearchTest.java";
    private static final String DEPTH_REPORTS = "target/surefire-reports/";

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

    private static final Map<String, String> PREDECESSOR_SHAS = Map.of(
            "candidateDepthResearchTestSha256",
            "5a00ab058fb43617c4e5d6a31169620cccbddee798bfaf6752adb2a3385a485d",
            "contractsXmlSha256",
            "dbcc4b2cc5ce38bce0705fa341a66e41616153d913e435f8a0f3fca2a71ea30b",
            "contractsTxtSha256",
            "fb43a20f4403c90fa05e91bcfd369bc4949cf01f46dd9ef7e0c60d983f9f48a7",
            "selectionXmlSha256",
            "da577d1c3fcad57f7feccb948f87c9fd18522a54959a3620c7045f0c89035f19",
            "selectionTxtSha256",
            "59b5d7b918e28caf3608212610b0f6dc960a49fa852be2a4123bd5ab63b0891a",
            "candidateDepthFullRankingSha256",
            "91d13f90ede2703f3c56eb499111ac06cd223d242fb4f26a2ca4a9a4ed2b1c67");

    private static final Map<String, String> SUPPLEMENTAL_SHAS = Map.ofEntries(
            Map.entry("qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/constant/WeaviateConstant.java", "4ecb433a6c11001a3a4301e9538ae3910a41ec755308c2890c2c5a7c50796a24"),
            Map.entry("qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/IVectorStoreService.java", "3d8db6d6568eca9c108e97e2129e213a2b4100340ebe0ec205b2aaf5623671aa"),
            Map.entry("qknow-module-ai/qknow-module-ai-api/src/main/java/tech/qiantong/qknow/module/ai/api/modelMarket/IAiModelApiService.java", "aa64d9fa9787540c58e6e91a26bc261a74addbd01b1617724379e75fb80f0379"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/dal/dataobject/knowledgeBase/KmcKnowledgeBaseDO.java", "bac39b7cbacd0cf598118809aeea403f5a9a785f21ca553195a43b13de791cb7"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/dal/mapper/knowledgeBase/KmcKnowledgeBaseMapper.java", "0d80c31fbd71321f3a457f769eab5fb32593394e80af31c7a1ba0fc585847fa3"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/CandidateFusionService.java", "f5b7f7433bc4714794f95e73fc776c31704be4f75a5529e76d101b8e80b54661"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/GraphRagProperties.java", "f3c2c6018c32e5a4443ee92312929ad315eb9b6a00b046c4d0a15328a2f53b20"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/GraphRagRetriever.java", "0599ece2c7554e29f2994d4e19770f4e12277cf1a02b33a5ffcdb5df27c1af18"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/MetadataRetriever.java", "c664d340221508f2a428d691126c44fc2b0073c2641652b45a045c156cb18588"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryEntityExtractionService.java", "23f9fb094b71a23ee2e55d3f5c651a203a6d7cf4bf27a0b71aa30d8cb23cb987"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryIntentAnalyzer.java", "b6525ced610cc251eae62643fa786446f28d8c84447a707ad745bafdd6f4119b"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryRouter.java", "b68405b7d8edfc8406282445a76b636ac3b9ef844133782426000b776fabb424"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java", "5404023a96346de22e14395193a99c29436b9b84b35b3065648db5574e1e36a2"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/VectorRetriever.java", "c0ab095a8ee1574e984ca13ef8eb0a8c1cf18a850779a95f79b5e42b109a2968"),
            Map.entry("qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/nlp/JiebaNative.java", "d889c1bb8c3b472eac2c97c68f9ec1367128f6a233f2b0292fd57a307447fba3"),
            Map.entry("tests/src/test/java/tech/qiantong/qknow/rag/eval/FeatureHashEmbeddingModel.java", "f91c61cae23ec318ccc0df4848af5443528ee93c830854a2013c9c1b083a2ecc"),
            Map.entry("tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate101CandidateDepthResearchTest.java", "5a00ab058fb43617c4e5d6a31169620cccbddee798bfaf6752adb2a3385a485d"),
            Map.entry("tests/src/test/java/tech/qiantong/qknow/rag/eval/RagCandidate10DiagnosticRecoveryV2Test.java", "606de704a7323a5a92be06111d9925002ef9bc2762d839ed99ef2168834fa4f4"));

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "contracts")
    void checkpointContracts() throws Exception {
        LockState lock = openLocks("contracts");
        try {
            runContracts();
        } finally {
            closeLocks(lock);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = ENABLE_PROPERTY, matches = "selection")
    void locatesEarliestIdentifierExactBoundary() {
        AttemptState state = new AttemptState();
        String marker;
        try {
            marker = validMarker(runSelection(state));
        } catch (Throwable failure) {
            marker = invalidMarker(state, classify(failure));
        }
        requireMarkerSchema(marker);
        System.out.println(marker);
    }

    private static void runContracts() throws Exception {
        require(FIXED_ERRORS.size() == 16, HARNESS_ERROR);
        require(CHECKPOINT_NAMES.size() == 13 && BOUNDARY_NAMES.size() == 7,
                HARNESS_ERROR);
        require(PREDECESSOR_SHAS.size() == 6 && SUPPLEMENTAL_SHAS.size() == 18,
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
                        && orchestration.superRerankCalls.get() == 0
                        && orchestration.filterCalls.get() == 1
                        && orchestration.colbertCalls.get() == 1
                        && orchestration.admissionCalls.get() == 1
                        && orchestration.candidate3Calls.get() == 1
                        && result.size() == BUSINESS_LIMIT
                        && Arrays.equals(before, resultProjection(candidates)),
                ORCHESTRATION_ERROR);

        requireRoutingContracts();
        requireLifecycleContracts();
        requireContextContracts();
        requireFallbackContracts();
        requireExecutorAndJdbcContracts();
        requireVectorContracts();
        requireCommandPropertyContracts();
        requirePreDelegateCaptureContracts();
        requireCapacityContracts();
        requireCanonicalContracts();
    }

    private static void requireCommandPropertyContracts() {
        Map<String, String> expected = expectedLifecycleCommandProperties(
                "contracts");
        requireLifecycleCommandProperties("contracts", expected);
        Map<String, String> invalid = new LinkedHashMap<>(expected);
        invalid.put("qknow.rag.dynamic-top-k.enabled", "true");
        expectCode(COMMAND_ERROR, () ->
                requireLifecycleCommandProperties("contracts", invalid));
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
        Map<String, Set<String>> stages = new LinkedHashMap<>();
        CHECKPOINT_NAMES.forEach(name -> stages.put(name, Set.of()));
        stages.put("keywordRaw", java.util.stream.IntStream.rangeClosed(1, 91)
                .mapToObj(value -> "seg:" + value)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        QueryTrace oversized = new QueryTrace(
                "synthetic", 1, Set.of(), Map.copyOf(stages), Set.of(),
                Set.of(), "NONE", 0, 0);
        RunAudit audit = new RunAudit();
        audit.colbertInputDocumentCount.add(0);
        audit.colbertOutputDocumentCount.add(0);
        expectCode(BUDGET_ERROR, () ->
                requirePerQueryCapacity(List.of(oversized), audit));
    }

    private static void requirePerQueryCapacity(
            List<QueryTrace> traces, RunAudit audit) {
        require(audit.colbertInputDocumentCount.size() == traces.size()
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
                            && stages.get("colbertInput").size()
                            <= CANDIDATE_TOP_K * 4
                            && audit.colbertInputDocumentCount.get(index)
                            == stages.get("colbertInput").size()
                            && audit.colbertInputDocumentCount.get(index)
                            <= CANDIDATE_TOP_K * 4
                            && audit.colbertOutputDocumentCount.get(index)
                            == stages.get("colbertTop90").size()
                            && audit.colbertOutputDocumentCount.get(index)
                            <= CANDIDATE_TOP_K
                            && stages.get("admissionOutput30").size()
                            == BUSINESS_LIMIT
                            && stages.get("candidate3Sources").size()
                            <= BUSINESS_LIMIT,
                    BUDGET_ERROR);
        }
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

    private static void requireRoutingContracts() {
        Map<String, Integer> lexical = zeroBoundaries();
        lexical.put("NOT_RETRIEVED", 16);
        require(route(lexical, 0).equals(new RouteDecision(
                        "LEXICAL_INCLUSION",
                        "PROCEED_TO_IDENTIFIER_AWARE_LEXICAL_INCLUSION_RESEARCH",
                        null)), MAPPING_ERROR);
        Map<String, Integer> colbert = zeroBoundaries();
        colbert.put("COLBERT_FRONTIER_NOT_PRESERVED", 16);
        require(route(colbert, 0).equals(new RouteDecision(
                        "COLBERT_FRONTIER_PRESERVATION",
                        "PROCEED_TO_IDENTIFIER_EXACT_COLBERT_FRONTIER_"
                                + "PRESERVATION_RESEARCH", null)),
                MAPPING_ERROR);
        Map<String, Integer> mixed = zeroBoundaries();
        mixed.put("NOT_RETRIEVED", 8);
        mixed.put("COLBERT_FRONTIER_NOT_PRESERVED", 8);
        require(route(mixed, 0).equals(new RouteDecision(null,
                        "STOP_IDENTIFIER_EXACT_LIFECYCLE_BOUNDARY_NOT_UNIQUE",
                        null)), MAPPING_ERROR);
        Map<String, Integer> unsupported = zeroBoundaries();
        unsupported.put("NO_RELEVANT_EXACT", 1);
        unsupported.put("NOT_RETRIEVED", 15);
        require(route(unsupported, 0).equals(new RouteDecision(null,
                        "STOP_IDENTIFIER_EXACT_LIFECYCLE_UNSUPPORTED", null)),
                MAPPING_ERROR);
        Map<String, Integer> allVisible = zeroBoundaries();
        allVisible.put("COLBERT90_VISIBLE", 16);
        require(route(allVisible, 0).equals(new RouteDecision(null,
                        "STOP_IDENTIFIER_EXACT_LIFECYCLE_UNSUPPORTED", null)),
                MAPPING_ERROR);
        Map<String, Integer> fusion = zeroBoundaries();
        fusion.put("FUSION_NOT_PRESERVED", 1);
        fusion.put("NOT_RETRIEVED", 15);
        require(FUSION_ERROR.equals(route(fusion, 0).errorCode),
                MAPPING_ERROR);
        require("STOP_IDENTIFIER_EXACT_LIFECYCLE_UNSUPPORTED".equals(
                        route(lexical, 1).decision), MAPPING_ERROR);
    }

    private static Map<String, Integer> zeroBoundaries() {
        Map<String, Integer> result = new LinkedHashMap<>();
        BOUNDARY_NAMES.forEach(name -> result.put(name, 0));
        return result;
    }

    private static RouteDecision route(
            Map<String, Integer> counts, int invalidIdentifierCardinality) {
        require(counts != null && counts.keySet().equals(
                        new LinkedHashSet<>(BOUNDARY_NAMES))
                        && counts.values().stream().allMatch(value -> value >= 0)
                        && counts.values().stream().mapToInt(Integer::intValue).sum()
                        == 16,
                MAPPING_ERROR);
        if (counts.get("WEAK_PATH_EXCLUDED") > 0
                || counts.get("FUSION_NOT_PRESERVED") > 0) {
            return new RouteDecision(null, null, FUSION_ERROR);
        }
        int unresolved = BOUNDARY_NAMES.stream()
                .filter(name -> !"COLBERT90_VISIBLE".equals(name))
                .mapToInt(counts::get).sum();
        if (unresolved == 0
                || counts.get("NO_RELEVANT_EXACT") > 0
                || counts.get("FILTER_NOT_PRESERVED") > 0
                || invalidIdentifierCardinality > 0) {
            return new RouteDecision(null,
                    "STOP_IDENTIFIER_EXACT_LIFECYCLE_UNSUPPORTED", null);
        }
        int supportedTypes = 0;
        if (counts.get("NOT_RETRIEVED") > 0) {
            supportedTypes++;
        }
        if (counts.get("COLBERT_FRONTIER_NOT_PRESERVED") > 0) {
            supportedTypes++;
        }
        if (supportedTypes > 1) {
            return new RouteDecision(null,
                    "STOP_IDENTIFIER_EXACT_LIFECYCLE_BOUNDARY_NOT_UNIQUE",
                    null);
        }
        if (counts.get("NOT_RETRIEVED") == unresolved) {
            return new RouteDecision("LEXICAL_INCLUSION",
                    "PROCEED_TO_IDENTIFIER_AWARE_LEXICAL_INCLUSION_RESEARCH",
                    null);
        }
        if (counts.get("COLBERT_FRONTIER_NOT_PRESERVED") == unresolved) {
            return new RouteDecision("COLBERT_FRONTIER_PRESERVATION",
                    "PROCEED_TO_IDENTIFIER_EXACT_COLBERT_FRONTIER_"
                            + "PRESERVATION_RESEARCH", null);
        }
        return new RouteDecision(null, null, MAPPING_ERROR);
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

    private static void requireCanonicalContracts() {
        AttemptState state = new AttemptState();
        String marker = invalidMarker(state, HARNESS_ERROR);
        requireMarkerSchema(marker);
        String json = marker.substring(MARKER.length() + 1);
        JSONObject parsed = JSON.parseObject(json);
        require(canonicalString(parsed).equals(json), SAFETY_ERROR);
        require(parsed.keySet().equals(Set.of(
                        "schemaVersion", "status", "causalScope",
                        "algorithmConclusion", "queryCount",
                        "targetQueryCount", "selectedBoundary",
                        "infrastructure", "access", "decision", "errorCode")),
                SAFETY_ERROR);
        requireNoForbiddenMarkerFields(parsed, "");
        for (Map<String, Integer> boundaries : List.of(
                boundaryFixture("NOT_RETRIEVED", 16),
                boundaryFixture("COLBERT_FRONTIER_NOT_PRESERVED", 16),
                mixedBoundaryFixture(),
                unsupportedBoundaryFixture())) {
            RouteDecision route = route(boundaries, 0);
            String valid = validMarker(syntheticSelectionRun(
                    boundaries, route));
            requireMarkerSchema(valid);

            JSONObject wrongTopLevelType = JSON.parseObject(
                    valid.substring(MARKER.length() + 1));
            wrongTopLevelType.put("queryCount", "40");
            expectCode(SAFETY_ERROR, () -> requireMarkerSchema(
                    MARKER + " " + canonicalString(wrongTopLevelType)));

            JSONObject wrongNestedType = JSON.parseObject(
                    valid.substring(MARKER.length() + 1));
            wrongNestedType.getJSONObject("mapping").put(
                    "missingQrelRelevantQuerySegmentPairCount", null);
            expectCode(SAFETY_ERROR, () -> requireMarkerSchema(
                    MARKER + " " + canonicalString(wrongNestedType)));
        }

        String colbertMarker = validMarker(syntheticSelectionRun(
                boundaryFixture("COLBERT_FRONTIER_NOT_PRESERVED", 16),
                route(boundaryFixture(
                        "COLBERT_FRONTIER_NOT_PRESERVED", 16), 0)));
        JSONObject incompatibleRoute = JSON.parseObject(
                colbertMarker.substring(MARKER.length() + 1));
        incompatibleRoute.put("selectedBoundary", "LEXICAL_INCLUSION");
        incompatibleRoute.put("decision",
                "PROCEED_TO_IDENTIFIER_AWARE_LEXICAL_INCLUSION_RESEARCH");
        expectCode(SAFETY_ERROR, () -> requireMarkerSchema(
                MARKER + " " + canonicalString(incompatibleRoute)));

        JSONObject negativeMapping = JSON.parseObject(
                colbertMarker.substring(MARKER.length() + 1));
        JSONObject mapping = negativeMapping.getJSONObject("mapping");
        mapping.put("qrelRelevantQuerySegmentPairCount", -1);
        mapping.put("mappedQrelRelevantQuerySegmentPairCount", -1);
        mapping.put("relevantExactQuerySegmentPairCount", -1);
        expectCode(SAFETY_ERROR, () -> requireMarkerSchema(
                MARKER + " " + canonicalString(negativeMapping)));

        JSONObject contextBudgetMismatch = JSON.parseObject(
                colbertMarker.substring(MARKER.length() + 1));
        JSONObject database = contextBudgetMismatch.getJSONObject("budgets")
                .getJSONObject("databaseQueries");
        database.put("contextAdjacent", 1);
        database.put("attempted", 81);
        database.put("succeeded", 81);
        database.put("context", 1);
        database.put("total", 81);
        expectCode(SAFETY_ERROR, () -> requireMarkerSchema(
                MARKER + " " + canonicalString(contextBudgetMismatch)));
    }

    private static Map<String, Integer> boundaryFixture(
            String name, int count) {
        Map<String, Integer> value = zeroBoundaries();
        value.put(name, count);
        return Map.copyOf(value);
    }

    private static Map<String, Integer> mixedBoundaryFixture() {
        Map<String, Integer> value = zeroBoundaries();
        value.put("NOT_RETRIEVED", 8);
        value.put("COLBERT_FRONTIER_NOT_PRESERVED", 8);
        return Map.copyOf(value);
    }

    private static Map<String, Integer> unsupportedBoundaryFixture() {
        Map<String, Integer> value = zeroBoundaries();
        value.put("NO_RELEVANT_EXACT", 1);
        value.put("NOT_RETRIEVED", 15);
        return Map.copyOf(value);
    }

    private static SelectionRun syntheticSelectionRun(
            Map<String, Integer> boundaries, RouteDecision route) {
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
                boundaries, Map.copyOf(hashes), route, 0);
        return new SelectionRun(evaluation, syntheticBudgets(),
                syntheticInfrastructure(), Map.of(
                "selectionNonQrelResourceAccessCount", 3,
                "qrelResourceAccessBeforeRanking", 0,
                "qrelResourceAccessCount", 1,
                "holdoutResourceAccessCount", 0), PREDECESSOR_SHAS);
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

    private static void expectCode(String code, Runnable action) {
        try {
            action.run();
        } catch (GateFailure failure) {
            require(code.equals(failure.code), HARNESS_ERROR);
            return;
        }
        throw invalid(HARNESS_ERROR);
    }

    private static Fixture validateInput(Object input)
            throws ReflectiveOperationException {
        RagEvaluationDataset dataset =
                (RagEvaluationDataset) component(input, "dataset");
        @SuppressWarnings("unchecked")
        Map<String, RagCandidate10FixtureGenerator.FamilySpec> families =
                (Map<String, RagCandidate10FixtureGenerator.FamilySpec>)
                        component(input, "families");
        @SuppressWarnings("unchecked")
        List<RetrievalResult> pool =
                (List<RetrievalResult>) component(input, "pool");
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
            require(capture.rerank.colbertTop90.size() >= BUSINESS_LIMIT
                            && capture.rerank.admissionOutput30.size()
                            == BUSINESS_LIMIT,
                    ORCHESTRATION_ERROR);
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
                .map(RagCandidate101IdentifierExactLifecycleCheckpointTest
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

    private static Evaluation evaluateLifecycle(
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

        RouteDecision route = route(boundaries,
                invalidIdentifierCardinality);
        if (route.errorCode != null) {
            throw invalid(route.errorCode);
        }
        Map<String, String> pathHashes = new LinkedHashMap<>();
        CHECKPOINT_NAMES.forEach(name -> pathHashes.put(name,
                RagCandidate10FreezeSupport.sha256(
                        RagCandidate10FreezeSupport.canonicalJsonBytes(
                                perPathHashes.get(name)))));
        require(pathHashes.get("postFilter").equals(
                        pathHashes.get("colbertInput")), MAPPING_ERROR);
        return new Evaluation(qrelPairs, mappedPairs, relevantExactPairs,
                nonExactPairs, Map.copyOf(presence), context.freeze(),
                Map.copyOf(boundaries), Map.copyOf(pathHashes), route,
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

    private static Map<String, Object> accessView(Object access)
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
        return Map.copyOf(value);
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
                        "holdoutResourceAccessCount"), 0), QREL_ERROR);
    }

    private static Object invokeInstance(
            Object target, String name, Object... arguments)
            throws ReflectiveOperationException {
        Method selected = null;
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == arguments.length) {
                require(selected == null, HARNESS_ERROR);
                selected = method;
            }
        }
        require(selected != null, HARNESS_ERROR);
        selected.setAccessible(true);
        return invoke(selected, target, arguments);
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
                        && audit.superRerankCalls.get() == 0
                        && audit.colbertRequestedTopK.size() == QUERY_COUNT
                        && audit.colbertRequestedTopK.stream()
                        .allMatch(value -> value == CANDIDATE_TOP_K)
                        && audit.colbertOutputDocumentCount.stream()
                        .allMatch(value -> value <= CANDIDATE_TOP_K)
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
        value.put("colbertInputMax", 360);
        value.put("colbertOutputMax", 90);
        value.put("admissionOutputCountPerQuery", 30);
        value.put("candidate3OutputMax", 30);
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

    private static LockState openLocks(String expectedMode)
            throws ReflectiveOperationException {
        RagCandidate10FreezeSupport.RuntimePaths paths;
        RagCandidate10FreezeSupport.FrozenEvidence frozen;
        Map<String, String> predecessor;
        Map<String, String> supplemental;
        try {
            paths = publishedRuntimePaths();
            frozen = RagCandidate10FreezeSupport.openDiagnosticEvidence(paths);
            RagCandidate10FreezeSupport.requireSourceLockUnchanged(frozen);
            invokeStage("requireFormalCommand");
            invokeStage("requireNativeDisabled");
            require(Set.of("contracts", "selection").contains(expectedMode)
                            && expectedMode.equals(
                            System.getProperty(ENABLE_PROPERTY)),
                    COMMAND_ERROR);
            requireLifecycleCommandProperties(expectedMode);
            predecessor = predecessorHashes(paths);
            require(PREDECESSOR_SHAS.equals(predecessor), SOURCE_LOCK_ERROR);
            requireDepthPredecessor(paths);
            supplemental = supplementalHashes(paths);
            require(SUPPLEMENTAL_SHAS.equals(supplemental), SOURCE_LOCK_ERROR);
        } catch (GateFailure failure) {
            throw failure;
        } catch (RuntimeException | AssertionError failure) {
            String message = failure.getMessage();
            throw invalid("CANDIDATE10_CONFIG_INVALID".equals(message)
                    ? COMMAND_ERROR : SOURCE_LOCK_ERROR, failure);
        }
        return new LockState(paths, frozen, predecessor, supplemental,
                expectedMode);
    }

    private static void closeLocks(LockState state)
            throws ReflectiveOperationException {
        if (state == null) {
            return;
        }
        try {
            RagCandidate10FreezeSupport.requireSourceLockUnchanged(state.frozen);
            invokeStage("requireFormalCommand");
            invokeStage("requireNativeDisabled");
            requireLifecycleCommandProperties(state.mode);
            require(state.paths.equals(publishedRuntimePaths()), SOURCE_LOCK_ERROR);
            require(state.predecessor.equals(predecessorHashes(state.paths)),
                    SOURCE_LOCK_ERROR);
            require(state.supplemental.equals(supplementalHashes(state.paths)),
                    SOURCE_LOCK_ERROR);
            requireDepthPredecessor(state.paths);
        } catch (GateFailure failure) {
            throw failure;
        } catch (RuntimeException | AssertionError failure) {
            throw invalid(SOURCE_LOCK_ERROR, failure);
        }
    }

    private static void requireLifecycleCommandProperties(String mode) {
        Map<String, String> actual = new LinkedHashMap<>();
        expectedLifecycleCommandProperties(mode).keySet().forEach(key ->
                actual.put(key, System.getProperty(key)));
        requireLifecycleCommandProperties(mode, actual);
    }

    private static void requireLifecycleCommandProperties(
            String mode, Map<String, String> actual) {
        require(expectedLifecycleCommandProperties(mode).equals(actual),
                COMMAND_ERROR);
    }

    private static Map<String, String> expectedLifecycleCommandProperties(
            String mode) {
        require(Set.of("contracts", "selection").contains(mode),
                COMMAND_ERROR);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put(ENABLE_PROPERTY, mode);
        expected.put("qknow.rag.dynamic-top-k.enabled", "false");
        expected.put("qknow.rag.query-entity.enabled", "false");
        expected.put("qknow.rag.rrf.k", "60");
        expected.put("qknow.rag.rrf.weak-path-threshold", "0");
        expected.put("qknow.rag.graph.enabled", "false");
        expected.put("qknow.rag.vector.vecsim-rescore-enabled", "false");
        return Map.copyOf(expected);
    }

    private static RagCandidate10FreezeSupport.RuntimePaths
    publishedRuntimePaths() throws ReflectiveOperationException {
        Method method = RagCandidate10DiagnosticRecoveryV2Test.class
                .getDeclaredMethod("publishedRuntimePaths");
        method.setAccessible(true);
        return (RagCandidate10FreezeSupport.RuntimePaths) invoke(method, null);
    }

    private static Map<String, String> predecessorHashes(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        Path tests = paths.testsDirectory();
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("candidateDepthResearchTestSha256", tests.resolve(DEPTH_SOURCE));
        files.put("contractsXmlSha256", tests.resolve(DEPTH_REPORTS
                + "TEST-tech.qiantong.qknow.rag.eval."
                + "RagCandidate101CandidateDepthResearchTest-"
                + "candidate101-candidate-depth-contracts-attempt-001.xml"));
        files.put("contractsTxtSha256", tests.resolve(DEPTH_REPORTS
                + "tech.qiantong.qknow.rag.eval."
                + "RagCandidate101CandidateDepthResearchTest-"
                + "candidate101-candidate-depth-contracts-attempt-001.txt"));
        files.put("selectionXmlSha256", tests.resolve(DEPTH_REPORTS
                + "TEST-tech.qiantong.qknow.rag.eval."
                + "RagCandidate101CandidateDepthResearchTest-"
                + "candidate101-candidate-depth-selection-attempt-001.xml"));
        files.put("selectionTxtSha256", tests.resolve(DEPTH_REPORTS
                + "tech.qiantong.qknow.rag.eval."
                + "RagCandidate101CandidateDepthResearchTest-"
                + "candidate101-candidate-depth-selection-attempt-001.txt"));
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((name, path) -> result.put(name, hashRegular(path)));
        result.put("candidateDepthFullRankingSha256",
                PREDECESSOR_SHAS.get("candidateDepthFullRankingSha256"));
        return Map.copyOf(result);
    }

    private static Map<String, String> supplementalHashes(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        Path backend = Objects.requireNonNull(paths.testsDirectory().getParent());
        Map<String, String> result = new LinkedHashMap<>();
        SUPPLEMENTAL_SHAS.keySet().stream().sorted().forEach(relative ->
                result.put(relative, hashRegular(backend.resolve(relative))));
        return Map.copyOf(result);
    }

    private static String hashRegular(Path path) {
        require(path != null && !Files.isSymbolicLink(path)
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                SOURCE_LOCK_ERROR);
        return RagCandidate10FreezeSupport.sha256(path);
    }

    private static void requireDepthPredecessor(
            RagCandidate10FreezeSupport.RuntimePaths paths) {
        Path xml = paths.testsDirectory().resolve(DEPTH_REPORTS
                + "TEST-tech.qiantong.qknow.rag.eval."
                + "RagCandidate101CandidateDepthResearchTest-"
                + "candidate101-candidate-depth-selection-attempt-001.xml");
        String content;
        try {
            content = Files.readString(xml, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw invalid(SOURCE_LOCK_ERROR, failure);
        }
        String prefix = "CANDIDATE101_CANDIDATE_DEPTH ";
        int start = content.indexOf(prefix);
        require(start >= 0 && content.indexOf(prefix, start + prefix.length()) < 0,
                SOURCE_LOCK_ERROR);
        int end = content.indexOf('\n', start);
        if (end < 0) {
            end = content.length();
        }
        JSONObject marker;
        try {
            marker = JSON.parseObject(content.substring(
                    start + prefix.length(), end).trim());
        } catch (RuntimeException failure) {
            throw invalid(SOURCE_LOCK_ERROR, failure);
        }
        require("VALID".equals(marker.getString("status"))
                        && "STOP_CANDIDATE_DEPTH_90_HYPOTHESIS_REJECTED".equals(
                        marker.getString("decision"))
                        && marker.getIntValue("targetMechanismTrueCount") == 4
                        && marker.getIntValue("targetQueryCount") == 16
                        && marker.getIntValue("queryCount") == 40
                        && PREDECESSOR_SHAS.get("candidateDepthFullRankingSha256")
                        .equals(marker.getString("baselineFullRankingSha256"))
                        && PREDECESSOR_SHAS.get("candidateDepthFullRankingSha256")
                        .equals(marker.getString("candidateFullRankingSha256")),
                SOURCE_LOCK_ERROR);
        JSONObject families = marker.getJSONObject("familyMechanismCounts");
        JSONObject metrics = marker.getJSONObject("metrics");
        JSONObject quality = marker.getJSONObject("quality");
        require(families != null && families.getIntValue("NONE_TRUE") == 6
                        && families.getIntValue("ONE_TRUE") == 0
                        && families.getIntValue("BOTH_TRUE") == 2
                        && metrics != null
                        && Double.compare(metrics.getDoubleValue("baselineApAt10"), 0.4D) == 0
                        && Double.compare(metrics.getDoubleValue("candidateApAt10"), 0.5D) == 0
                        && Double.compare(metrics.getDoubleValue("baselineNdcgAt10"),
                        0.44525887710618334D) == 0
                        && Double.compare(metrics.getDoubleValue("candidateNdcgAt10"),
                        0.5339441578296376D) == 0
                        && quality != null
                        && quality.getIntValue("targetFamilyStrictImprovementCount") == 2
                        && quality.getIntValue("familyRegressionCount") == 0
                        && quality.getIntValue("unsafeOutputCount") == 0,
                SOURCE_LOCK_ERROR);
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

    private static Object component(Object target, String name)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return invoke(method, target);
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

    private static Object newNestedStage(String simpleName)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(RagCandidate10DiagnosticStageSupport.class
                .getName() + "$" + simpleName);
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            return constructor.newInstance();
        } catch (InvocationTargetException failure) {
            throw invalid(HARNESS_ERROR, failure.getCause());
        }
    }

    private static SelectionRun runSelection(AttemptState state) throws Exception {
        LockState lock = openLocks("selection");
        PostgreSQLContainer<?> container = null;
        CountingExecutor executor = null;
        PendingRun pending = null;
        Throwable failure = null;
        DockerBudget docker = new DockerBudget();
        try {
            Object access = newNestedStage("AccessCounter");
            Object input;
            try {
                input = invokeStage("loadRankingInput",
                        lock.paths, lock.frozen, access);
            } catch (RuntimeException inputFailure) {
                throw invalid(FROZEN_INPUT_ERROR, inputFailure);
            }
            state.access = accessView(access);
            requireAccess(access, false, 0);
            Fixture fixture = validateInput(input);
            state.queryCount = fixture.dataset.queries().size();
            state.targetQueryCount = fixture.targetQueryCount;

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
            executor.setThreadNamePrefix("candidate101-lifecycle-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(30);
            executor.initialize();
            RuntimeAssembly assembly = assembleRuntime(
                    seeded, executor, fixture);
            List<QueryTrace> traces = runQueries(
                    fixture, assembly, executor);
            requireExecutorBeforeFreeze(executor);
            requireAccess(access, false, 0);
            invokeInstance(access, "freezeRanking");
            requireAccess(access, true, 0);
            RagEvaluationDataset labeled;
            try {
                labeled = (RagEvaluationDataset) invokeStage(
                        "loadQrelsAfterRanking",
                        lock.paths, lock.frozen, access);
            } catch (RuntimeException qrelFailure) {
                throw invalid(QREL_ERROR, qrelFailure);
            }
            requireAccess(access, true, 1);
            state.access = accessView(access);
            Evaluation evaluation = evaluateLifecycle(
                    fixture, labeled, traces);
            pending = new PendingRun(
                    fixture, seeded, assembly, traces, evaluation,
                    state.access, lock.predecessor);
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
                try {
                    closeLocks(lock);
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

    private static String validMarker(SelectionRun run) {
        RouteDecision expectedRoute = route(
                run.evaluation.boundaries,
                run.evaluation.invalidIdentifierCardinality);
        require(expectedRoute.equals(run.evaluation.route)
                        && expectedRoute.errorCode == null,
                MAPPING_ERROR);
        JSONObject value = new JSONObject();
        value.put("schemaVersion", SCHEMA);
        value.put("status", "VALID");
        value.put("causalScope", SCOPE);
        value.put("algorithmConclusion", ALGORITHM_CONCLUSION);
        value.put("queryCount", QUERY_COUNT);
        value.put("targetQueryCount", 16);
        int unresolved = BOUNDARY_NAMES.stream()
                .filter(name -> !"COLBERT90_VISIBLE".equals(name))
                .mapToInt(name -> run.evaluation.boundaries.get(name)).sum();
        value.put("unresolvedTargetQueryCount", unresolved);
        value.put("mapping", run.evaluation.mappingView());
        value.put("checkpointPresence",
                run.evaluation.presenceView());
        value.put("contextLineage", run.evaluation.context.view());
        value.put("earliestBoundaryQueryCounts",
                run.evaluation.boundaries);
        value.put("selectedBoundary",
                run.evaluation.route.selectedBoundary);
        value.put("budgets", run.budgets);
        value.put("infrastructure", run.infrastructure);
        value.put("access", run.access);
        value.put("pathSnapshotSha256",
                run.evaluation.pathHashes);
        value.put("predecessorEvidence", run.predecessor);
        value.put("decision", run.evaluation.route.decision);
        value.put("errorCode", null);
        String marker = MARKER + " " + canonicalString(value);
        requireMarkerSchema(marker);
        return marker;
    }

    private static String invalidMarker(AttemptState state, String errorCode) {
        JSONObject value = new JSONObject();
        value.put("schemaVersion", SCHEMA);
        value.put("status", "INVALID");
        value.put("causalScope", SCOPE);
        value.put("algorithmConclusion", ALGORITHM_CONCLUSION);
        value.put("queryCount", state.queryCount);
        value.put("targetQueryCount", state.targetQueryCount);
        value.put("selectedBoundary", null);
        value.put("infrastructure", state.infrastructure);
        value.put("access", state.access);
        value.put("decision", null);
        value.put("errorCode", errorCode);
        return MARKER + " " + canonicalString(value);
    }

    private static void requireMarkerSchema(String marker) {
        require(marker != null && marker.startsWith(MARKER + " "), SAFETY_ERROR);
        String raw = marker.substring(MARKER.length() + 1);
        JSONObject parsed;
        try {
            parsed = JSON.parseObject(raw);
        } catch (RuntimeException failure) {
            throw invalid(SAFETY_ERROR, failure);
        }
        require(canonicalString(parsed).equals(raw), SAFETY_ERROR);
        boolean valid = "VALID".equals(parsed.getString("status"));
        Set<String> expected = valid ? Set.of(
                "schemaVersion", "status", "causalScope",
                "algorithmConclusion", "queryCount", "targetQueryCount",
                "unresolvedTargetQueryCount", "mapping",
                "checkpointPresence", "contextLineage",
                "earliestBoundaryQueryCounts", "selectedBoundary", "budgets",
                "infrastructure", "access", "pathSnapshotSha256",
                "predecessorEvidence", "decision", "errorCode") : Set.of(
                "schemaVersion", "status", "causalScope",
                "algorithmConclusion", "queryCount", "targetQueryCount",
                "selectedBoundary", "infrastructure", "access", "decision",
                "errorCode");
        require(parsed.keySet().equals(expected)
                        && SCHEMA.equals(parsed.getString("schemaVersion"))
                        && SCOPE.equals(parsed.getString("causalScope"))
                        && ALGORITHM_CONCLUSION.equals(
                        parsed.getString("algorithmConclusion")),
                SAFETY_ERROR);
        requireMarkerTypes(parsed, valid);
        if (valid) {
            require(parsed.getIntValue("queryCount") == QUERY_COUNT
                            && parsed.getIntValue("targetQueryCount") == 16
                            && parsed.get("errorCode") == null,
                    SAFETY_ERROR);
            String selected = parsed.getString("selectedBoundary");
            String decision = parsed.getString("decision");
            boolean tuple = ("LEXICAL_INCLUSION".equals(selected)
                    && "PROCEED_TO_IDENTIFIER_AWARE_LEXICAL_INCLUSION_RESEARCH"
                    .equals(decision))
                    || ("COLBERT_FRONTIER_PRESERVATION".equals(selected)
                    && "PROCEED_TO_IDENTIFIER_EXACT_COLBERT_FRONTIER_"
                    .concat("PRESERVATION_RESEARCH").equals(decision))
                    || (selected == null
                    && Set.of(
                            "STOP_IDENTIFIER_EXACT_LIFECYCLE_BOUNDARY_NOT_UNIQUE",
                            "STOP_IDENTIFIER_EXACT_LIFECYCLE_UNSUPPORTED")
                    .contains(decision));
            require(tuple, SAFETY_ERROR);
            requireValidNestedSchema(parsed);
        } else {
            require("INVALID".equals(parsed.getString("status"))
                            && parsed.get("selectedBoundary") == null
                            && parsed.get("decision") == null
                            && FIXED_ERRORS.contains(
                            parsed.getString("errorCode")), SAFETY_ERROR);
        }
        requireFiniteJson(parsed);
        requireNoForbiddenMarkerFields(parsed, "");
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

    private static void requireMarkerTypes(
            JSONObject value, boolean valid) {
        requireStringFields(value, Set.of(
                "schemaVersion", "status", "causalScope",
                "algorithmConclusion"));
        requireIntegerFields(value, Set.of("queryCount", "targetQueryCount"));
        requireObjectFields(value, Set.of("infrastructure", "access"));
        if (!valid) {
            require(value.get("selectedBoundary") == null
                            && value.get("decision") == null
                            && value.get("errorCode") instanceof String,
                    SAFETY_ERROR);
            return;
        }

        requireIntegerFields(value, Set.of("unresolvedTargetQueryCount"));
        requireObjectFields(value, Set.of(
                "mapping", "checkpointPresence", "contextLineage",
                "earliestBoundaryQueryCounts", "budgets",
                "pathSnapshotSha256", "predecessorEvidence"));
        require(value.get("selectedBoundary") == null
                        || value.get("selectedBoundary") instanceof String,
                SAFETY_ERROR);
        require(value.get("decision") instanceof String
                        && value.get("errorCode") == null,
                SAFETY_ERROR);

        JSONObject mapping = value.getJSONObject("mapping");
        requireIntegerFields(mapping, mapping.keySet());
        JSONObject presence = value.getJSONObject("checkpointPresence");
        for (String checkpoint : CHECKPOINT_NAMES) {
            Object raw = presence.get(checkpoint);
            require(raw instanceof JSONObject, SAFETY_ERROR);
            JSONObject item = (JSONObject) raw;
            requireIntegerFields(item, item.keySet());
        }

        JSONObject context = value.getJSONObject("contextLineage");
        requireIntegerFields(context, Set.of(
                "sourceSegmentCount", "sourceExactCount",
                "contextSegmentCount", "contextSourceVisibleCount",
                "contextSourceOmittedCount", "contextIntroducedCount",
                "contextSourceExactVisibleCount",
                "contextSourceExactOmittedCount",
                "contextIntroducedExactCount", "contextExactCount"));
        requireObjectFields(context, Set.of(
                "expansionModeCounts", "contextJdbcQueryCounts"));
        requireIntegerFields(context.getJSONObject("expansionModeCounts"),
                context.getJSONObject("expansionModeCounts").keySet());
        requireIntegerFields(context.getJSONObject("contextJdbcQueryCounts"),
                context.getJSONObject("contextJdbcQueryCounts").keySet());

        requireIntegerFields(
                value.getJSONObject("earliestBoundaryQueryCounts"),
                value.getJSONObject("earliestBoundaryQueryCounts").keySet());
        requireStringFields(value.getJSONObject("pathSnapshotSha256"),
                value.getJSONObject("pathSnapshotSha256").keySet());
        requireStringFields(value.getJSONObject("predecessorEvidence"),
                value.getJSONObject("predecessorEvidence").keySet());

        JSONObject budgets = value.getJSONObject("budgets");
        requireObjectFields(budgets, Set.of(
                "limits", "configuration", "calls", "adapters",
                "databaseQueries", "fallback", "externalCalls"));
        requireIntegerFields(budgets.getJSONObject("limits"),
                budgets.getJSONObject("limits").keySet());
        requireConfigurationTypes(budgets.getJSONObject("configuration"));
        for (String key : List.of(
                "calls", "adapters", "databaseQueries", "fallback",
                "externalCalls")) {
            JSONObject nested = budgets.getJSONObject(key);
            requireIntegerFields(nested, nested.keySet());
        }

        JSONObject infrastructure = value.getJSONObject("infrastructure");
        requireObjectFields(infrastructure, Set.of("docker", "executor", "seed"));
        requireIntegerFields(infrastructure.getJSONObject("docker"),
                infrastructure.getJSONObject("docker").keySet());
        JSONObject executor = infrastructure.getJSONObject("executor");
        requireIntegerFields(executor, executor.keySet().stream()
                .filter(key -> !"terminatedAfterCleanup".equals(key))
                .collect(java.util.stream.Collectors.toSet()));
        requireBooleanFields(executor, Set.of("terminatedAfterCleanup"));
        JSONObject seed = infrastructure.getJSONObject("seed");
        requireIntegerFields(seed, Set.of(
                "loadRankingInputCalls", "queryCount", "familyCount",
                "corpusCount", "postgresDocumentCount",
                "postgresSegmentCount", "postgresEntityMetadataCount",
                "vectorDocumentCount", "vectorSnapshotKeyCount",
                "seedEmbeddingInputCount"));
        requireStringFields(seed, Set.of(
                "corpusProjectionSha256", "postgresProjectionSha256",
                "vectorProjectionSha256"));
        requireBooleanFields(seed, Set.of(
                "vectorSnapshotKeySetExact", "seedEmbeddingInputOrderExact",
                "projectionBytesEqual", "countersResetBeforeSelection"));
        requireIntegerFields(value.getJSONObject("access"),
                value.getJSONObject("access").keySet());
    }

    private static void requireConfigurationTypes(JSONObject configuration) {
        requireBooleanFields(configuration, Set.of(
                "dynamicTopKEnabled", "queryEntityEnabled",
                "identifierConsistencyEnabled", "identifierAware",
                "vecSimRescoreEnabled", "graphEnabled", "graphPprEnabled",
                "colbertEnabled", "colbertEmbeddingServicePresent"));
        requireIntegerFields(configuration, Set.of(
                "rrfK", "contextMaxBytes", "contextMaxTokens",
                "colbertDimensions", "colbertMaxTokensPerDoc",
                "colbertNgramSize", "vectorEmbeddingDimensions",
                "vectorEmbeddingSeed"));
        Object threshold = configuration.get("weakPathThreshold");
        require(threshold instanceof Number number
                        && Double.isFinite(number.doubleValue()), SAFETY_ERROR);
        requireStringFields(configuration, Set.of(
                "colbertEmbeddingPlatform", "colbertEmbeddingBaseUrl",
                "colbertEmbeddingApiKey", "colbertEmbeddingModel",
                "vectorEmbeddingVersion"));
        requireObjectFields(configuration, Set.of("eligibility"));
        JSONObject eligibility = configuration.getJSONObject("eligibility");
        requireBooleanFields(eligibility, eligibility.keySet());
    }

    private static void requireIntegerFields(
            JSONObject value, Collection<String> keys) {
        require(value != null, SAFETY_ERROR);
        for (String key : keys) {
            Object raw = value.get(key);
            require(raw instanceof Byte || raw instanceof Short
                            || raw instanceof Integer || raw instanceof Long
                            || raw instanceof java.math.BigInteger,
                    SAFETY_ERROR);
        }
    }

    private static void requireBooleanFields(
            JSONObject value, Collection<String> keys) {
        require(value != null, SAFETY_ERROR);
        for (String key : keys) {
            require(value.get(key) instanceof Boolean, SAFETY_ERROR);
        }
    }

    private static void requireStringFields(
            JSONObject value, Collection<String> keys) {
        require(value != null, SAFETY_ERROR);
        for (String key : keys) {
            require(value.get(key) instanceof String, SAFETY_ERROR);
        }
    }

    private static void requireObjectFields(
            JSONObject value, Collection<String> keys) {
        require(value != null, SAFETY_ERROR);
        for (String key : keys) {
            require(value.get(key) instanceof JSONObject, SAFETY_ERROR);
        }
    }

    private static void requireValidNestedSchema(JSONObject value) {
        JSONObject mapping = value.getJSONObject("mapping");
        requireKeys(mapping, Set.of(
                "qrelRelevantQuerySegmentPairCount",
                "mappedQrelRelevantQuerySegmentPairCount",
                "relevantExactQuerySegmentPairCount",
                "nonExactRelevantQuerySegmentPairCount",
                "missingQrelRelevantQuerySegmentPairCount",
                "duplicateCorpusKeyCount", "duplicateCheckpointKeyCount",
                "nonMonotonicLifecyclePairCount"));
        int qrel = mapping.getIntValue(
                "qrelRelevantQuerySegmentPairCount");
        require(mapping.values().stream().allMatch(item ->
                        ((Number) item).longValue() >= 0)
                        && mapping.getIntValue(
                        "mappedQrelRelevantQuerySegmentPairCount") == qrel
                        && mapping.getIntValue(
                        "relevantExactQuerySegmentPairCount")
                        + mapping.getIntValue(
                        "nonExactRelevantQuerySegmentPairCount") == qrel
                        && mapping.getIntValue(
                        "missingQrelRelevantQuerySegmentPairCount") == 0
                        && mapping.getIntValue("duplicateCorpusKeyCount") == 0
                        && mapping.getIntValue("duplicateCheckpointKeyCount") == 0
                        && mapping.getIntValue(
                        "nonMonotonicLifecyclePairCount") == 0,
                SAFETY_ERROR);

        JSONObject presence = value.getJSONObject("checkpointPresence");
        requireKeys(presence, new LinkedHashSet<>(CHECKPOINT_NAMES));
        for (String checkpoint : CHECKPOINT_NAMES) {
            JSONObject item = presence.getJSONObject(checkpoint);
            requireKeys(item, Set.of(
                    "relevantExactQuerySegmentPairCount",
                    "targetQueryCountWithAnyRelevantExact"));
            require(item.getIntValue(
                            "relevantExactQuerySegmentPairCount") >= 0
                            && item.getIntValue(
                            "targetQueryCountWithAnyRelevantExact") >= 0
                            && item.getIntValue(
                            "targetQueryCountWithAnyRelevantExact") <= 16,
                    SAFETY_ERROR);
        }

        JSONObject context = value.getJSONObject("contextLineage");
        requireKeys(context, Set.of(
                "sourceSegmentCount", "sourceExactCount",
                "contextSegmentCount", "contextSourceVisibleCount",
                "contextSourceOmittedCount", "contextIntroducedCount",
                "contextSourceExactVisibleCount",
                "contextSourceExactOmittedCount",
                "contextIntroducedExactCount", "contextExactCount",
                "expansionModeCounts", "contextJdbcQueryCounts"));
        requireContextIdentities(new ContextCounts(
                context.getLongValue("sourceSegmentCount"),
                context.getLongValue("sourceExactCount"),
                context.getLongValue("contextSegmentCount"),
                context.getLongValue("contextSourceVisibleCount"),
                context.getLongValue("contextSourceOmittedCount"),
                context.getLongValue("contextIntroducedCount"),
                context.getLongValue("contextSourceExactVisibleCount"),
                context.getLongValue("contextSourceExactOmittedCount"),
                context.getLongValue("contextIntroducedExactCount"),
                context.getLongValue("contextExactCount")));
        JSONObject modes = context.getJSONObject("expansionModeCounts");
        requireKeys(modes, Set.of(
                "NONE", "PARENT", "ADJACENT",
                "PARENT_FALLBACK_ADJACENT"));
        JSONObject jdbcContext = context.getJSONObject(
                "contextJdbcQueryCounts");
        requireKeys(jdbcContext, Set.of("parent", "adjacent", "total"));
        require(modes.values().stream().allMatch(item ->
                        ((Number) item).longValue() >= 0)
                        && jdbcContext.values().stream().allMatch(item ->
                        ((Number) item).longValue() >= 0)
                        && modes.values().stream().mapToLong(
                        item -> ((Number) item).longValue()).sum() == QUERY_COUNT
                        && jdbcContext.getLongValue("parent")
                        == modes.getLongValue("PARENT")
                        + modes.getLongValue("PARENT_FALLBACK_ADJACENT")
                        && jdbcContext.getLongValue("adjacent")
                        == modes.getLongValue("ADJACENT")
                        + modes.getLongValue("PARENT_FALLBACK_ADJACENT")
                        && jdbcContext.getLongValue("total")
                        == jdbcContext.getLongValue("parent")
                        + jdbcContext.getLongValue("adjacent")
                        && jdbcContext.getLongValue("total") <= 80,
                SAFETY_ERROR);

        JSONObject boundaries = value.getJSONObject(
                "earliestBoundaryQueryCounts");
        requireKeys(boundaries, new LinkedHashSet<>(BOUNDARY_NAMES));
        int boundaryTotal = boundaries.values().stream()
                .mapToInt(item -> ((Number) item).intValue()).sum();
        int unresolved = BOUNDARY_NAMES.stream()
                .filter(name -> !"COLBERT90_VISIBLE".equals(name))
                .mapToInt(boundaries::getIntValue).sum();
        require(boundaries.values().stream().allMatch(item ->
                        ((Number) item).longValue() >= 0)
                        && boundaryTotal == 16
                        && unresolved == value.getIntValue(
                        "unresolvedTargetQueryCount")
                        && boundaries.getIntValue("WEAK_PATH_EXCLUDED") == 0
                        && boundaries.getIntValue("FUSION_NOT_PRESERVED") == 0,
                SAFETY_ERROR);
        requireBoundaryDecision(boundaries, unresolved,
                value.getString("selectedBoundary"),
                value.getString("decision"));

        JSONObject hashes = value.getJSONObject("pathSnapshotSha256");
        requireKeys(hashes, new LinkedHashSet<>(CHECKPOINT_NAMES));
        hashes.values().forEach(hash -> require(hash instanceof String text
                        && text.matches("[0-9a-f]{64}"), SAFETY_ERROR));
        require(Objects.equals(hashes.get("postFilter"),
                hashes.get("colbertInput")), SAFETY_ERROR);
        JSONObject predecessor = value.getJSONObject("predecessorEvidence");
        requireKeys(predecessor, PREDECESSOR_SHAS.keySet());
        require(predecessor.equals(new JSONObject(PREDECESSOR_SHAS)),
                SAFETY_ERROR);

        JSONObject budgets = value.getJSONObject("budgets");
        requireKeys(budgets, Set.of(
                "limits", "configuration", "calls", "adapters",
                "databaseQueries", "fallback", "externalCalls"));
        requireLimits(budgets.getJSONObject("limits"));
        require(canonicalString(budgets.getJSONObject("configuration"))
                        .equals(canonicalString(expectedConfiguration())),
                SAFETY_ERROR);
        requireCalls(budgets.getJSONObject("calls"));
        requireAdapters(budgets.getJSONObject("adapters"));
        JSONObject database = budgets.getJSONObject("databaseQueries");
        requireDatabaseQueries(database);
        require(jdbcContext.getLongValue("parent")
                        == database.getLongValue("contextParent")
                        && jdbcContext.getLongValue("adjacent")
                        == database.getLongValue("contextAdjacent")
                        && jdbcContext.getLongValue("total")
                        == database.getLongValue("context"),
                SAFETY_ERROR);
        JSONObject fallback = budgets.getJSONObject("fallback");
        requireKeys(fallback, Set.of(
                "scopeOpenCount", "scopeVerificationCount",
                "expectedJavaTokenizationEventCount",
                "unexpectedScopedEventCount", "sqlFailureCount"));
        require(fallback.getIntValue("scopeOpenCount") == QUERY_COUNT
                        && fallback.getIntValue("scopeVerificationCount")
                        == QUERY_COUNT
                        && fallback.getIntValue(
                        "expectedJavaTokenizationEventCount") == QUERY_COUNT
                        && fallback.getIntValue(
                        "unexpectedScopedEventCount") == 0
                        && fallback.getIntValue("sqlFailureCount") == 0,
                SAFETY_ERROR);
        JSONObject external = budgets.getJSONObject("externalCalls");
        requireKeys(external, Set.of(
                "externalEmbedding", "vectorDatabase", "graphExternal",
                "remoteReranker", "localReranker", "network", "llm"));
        require(external.values().stream().allMatch(item ->
                item instanceof Number number && number.longValue() == 0),
                SAFETY_ERROR);

        JSONObject infrastructure = value.getJSONObject("infrastructure");
        requireKeys(infrastructure, Set.of("docker", "executor", "seed"));
        requireDocker(infrastructure.getJSONObject("docker"));
        requireExecutorSchema(infrastructure.getJSONObject("executor"));
        requireSeedSchema(infrastructure.getJSONObject("seed"));
        JSONObject access = value.getJSONObject("access");
        requireKeys(access, Set.of(
                "selectionNonQrelResourceAccessCount",
                "qrelResourceAccessBeforeRanking", "qrelResourceAccessCount",
                "holdoutResourceAccessCount"));
        require(access.getIntValue(
                        "selectionNonQrelResourceAccessCount") == 3
                        && access.getIntValue(
                        "qrelResourceAccessBeforeRanking") == 0
                        && access.getIntValue("qrelResourceAccessCount") == 1
                        && access.getIntValue("holdoutResourceAccessCount") == 0,
                SAFETY_ERROR);
    }

    private static void requireBoundaryDecision(
            JSONObject boundaries, int unresolved,
            String selectedBoundary, String decision) {
        int notRetrieved = boundaries.getIntValue("NOT_RETRIEVED");
        int noRelevantExact = boundaries.getIntValue("NO_RELEVANT_EXACT");
        int filterNotPreserved = boundaries.getIntValue(
                "FILTER_NOT_PRESERVED");
        int colbertNotPreserved = boundaries.getIntValue(
                "COLBERT_FRONTIER_NOT_PRESERVED");
        boolean noUnsupportedBoundary = noRelevantExact == 0
                && filterNotPreserved == 0;
        boolean consistent;
        if ("PROCEED_TO_IDENTIFIER_AWARE_LEXICAL_INCLUSION_RESEARCH"
                .equals(decision)) {
            consistent = "LEXICAL_INCLUSION".equals(selectedBoundary)
                    && unresolved > 0 && noUnsupportedBoundary
                    && notRetrieved == unresolved;
        } else if ("PROCEED_TO_IDENTIFIER_EXACT_COLBERT_FRONTIER_"
                .concat("PRESERVATION_RESEARCH").equals(decision)) {
            consistent = "COLBERT_FRONTIER_PRESERVATION".equals(
                    selectedBoundary)
                    && unresolved > 0 && noUnsupportedBoundary
                    && colbertNotPreserved == unresolved;
        } else if ("STOP_IDENTIFIER_EXACT_LIFECYCLE_BOUNDARY_NOT_UNIQUE"
                .equals(decision)) {
            consistent = selectedBoundary == null && unresolved > 0
                    && noUnsupportedBoundary && notRetrieved > 0
                    && colbertNotPreserved > 0
                    && notRetrieved + colbertNotPreserved == unresolved;
        } else if ("STOP_IDENTIFIER_EXACT_LIFECYCLE_UNSUPPORTED"
                .equals(decision)) {
            // A NOT_RETRIEVED target with unsupported identifier cardinality
            // is intentionally not exposed in the sanitized marker.
            consistent = selectedBoundary == null
                    && (unresolved == 0 || noRelevantExact > 0
                    || filterNotPreserved > 0 || notRetrieved > 0);
        } else {
            consistent = false;
        }
        require(consistent, SAFETY_ERROR);
    }

    private static void requireKeys(JSONObject value, Set<String> keys) {
        require(value != null && value.keySet().equals(keys), SAFETY_ERROR);
    }

    private static void requireFiniteJson(Object value) {
        if (value instanceof Number number) {
            require(Double.isFinite(number.doubleValue()), SAFETY_ERROR);
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                require(key instanceof String, SAFETY_ERROR);
                requireFiniteJson(nested);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(
                    RagCandidate101IdentifierExactLifecycleCheckpointTest
                            ::requireFiniteJson);
        }
    }

    private static void requireLimits(JSONObject limits) {
        requireKeys(limits, Set.of(
                "businessOutputLimit", "candidateTopK",
                "colbertRequestedTopK", "admissionScanMaxRank",
                "candidate3RequestedTopK", "perPathTopK",
                "retrieverPathCount", "fusedCandidateMax",
                "colbertInputMax", "colbertOutputMax",
                "admissionOutputCountPerQuery", "candidate3OutputMax",
                "queryVariantsPerQuery", "contextMaxBytes",
                "contextMaxTokens"));
        require(limits.equals(new JSONObject(limitsView())), SAFETY_ERROR);
    }

    private static void requireCalls(JSONObject calls) {
        requireKeys(calls, Set.of(
                "queryIntentAnalyze", "retrieveOnce", "queryEntityExtract",
                "keywordRetriever", "vectorRetriever", "metadataRetriever",
                "graphRetriever", "fusion", "counterfactualRerank",
                "filter", "colbert", "admission", "candidate3", "context"));
        require(calls.values().stream().allMatch(item ->
                item instanceof Number number
                        && number.longValue() == QUERY_COUNT), SAFETY_ERROR);
    }

    private static void requireAdapters(JSONObject adapters) {
        requireKeys(adapters, Set.of(
                "kbMapperLookup", "embeddingModelResolve",
                "vectorStoreResolve", "similaritySearch",
                "documentEmbeddingAfterSeed", "queryEmbeddingAfterSeed",
                "totalEmbeddingInputsAfterSeed", "vectorAdd", "vectorDelete",
                "chatModelResolve", "chatModelCall"));
        require(adapters.getLongValue("kbMapperLookup") == 80
                        && adapters.getLongValue("embeddingModelResolve") == 40
                        && adapters.getLongValue("vectorStoreResolve") == 40
                        && adapters.getLongValue("similaritySearch") == 40
                        && adapters.getLongValue(
                        "documentEmbeddingAfterSeed") == 0
                        && adapters.getLongValue("queryEmbeddingAfterSeed") == 40
                        && adapters.getLongValue(
                        "totalEmbeddingInputsAfterSeed") == 40
                        && adapters.getLongValue("vectorAdd") == 0
                        && adapters.getLongValue("vectorDelete") == 0
                        && adapters.getLongValue("chatModelResolve") == 0
                        && adapters.getLongValue("chatModelCall") == 0,
                SAFETY_ERROR);
    }

    private static void requireDatabaseQueries(JSONObject database) {
        requireKeys(database, Set.of(
                "keyword", "metadata", "vector", "contextParent",
                "contextAdjacent", "contextOther", "other", "attempted",
                "succeeded", "failed", "context", "total"));
        long context = database.getLongValue("contextParent")
                + database.getLongValue("contextAdjacent");
        long total = database.getLongValue("keyword")
                + database.getLongValue("metadata") + context;
        require(database.getLongValue("keyword") == QUERY_COUNT
                        && database.getLongValue("metadata") >= 0
                        && database.getLongValue("metadata") <= QUERY_COUNT
                        && database.getLongValue("vector") == 0
                        && database.getLongValue("contextParent") >= 0
                        && database.getLongValue("contextParent") <= QUERY_COUNT
                        && database.getLongValue("contextAdjacent") >= 0
                        && database.getLongValue("contextAdjacent") <= QUERY_COUNT
                        && database.getLongValue("contextOther") == 0
                        && database.getLongValue("other") == 0
                        && database.getLongValue("attempted") == total
                        && database.getLongValue("succeeded") == total
                        && database.getLongValue("failed") == 0
                        && database.getLongValue("context") == context
                        && database.getLongValue("total") == total
                        && context <= 80, SAFETY_ERROR);
    }

    private static void requireDocker(JSONObject docker) {
        requireKeys(docker, Set.of(
                "imageInspect", "containerConstruct", "containerStart",
                "imagePull", "containerStop"));
        require(docker.getIntValue("imageInspect") == 1
                        && docker.getIntValue("containerConstruct") == 1
                        && docker.getIntValue("containerStart") == 1
                        && docker.getIntValue("imagePull") == 0
                        && docker.getIntValue("containerStop") == 1,
                SAFETY_ERROR);
    }

    private static void requireExecutorSchema(JSONObject executor) {
        requireKeys(executor, Set.of(
                "corePoolSize", "maxPoolSize", "queueCapacity",
                "submitAttemptCount", "acceptedSubmitCount",
                "callableSubmitCount", "runnableSubmitCount", "started",
                "succeeded", "completed", "failed", "rejected",
                "doneFutureCount", "cancelledFutureCount", "taskCount",
                "completedTaskCount", "activeBeforeQrelFreeze",
                "queuedBeforeQrelFreeze",
                "queueRemainingCapacityBeforeQrelFreeze",
                "terminatedAfterCleanup"));
        require(executor.getIntValue("corePoolSize") == 4
                        && executor.getIntValue("maxPoolSize") == 4
                        && executor.getIntValue("queueCapacity") == 32
                        && executor.getLongValue("submitAttemptCount") == 200
                        && executor.getLongValue("acceptedSubmitCount") == 200
                        && executor.getLongValue("callableSubmitCount") == 200
                        && executor.getLongValue("runnableSubmitCount") == 0
                        && executor.getLongValue("started") == 200
                        && executor.getLongValue("succeeded") == 200
                        && executor.getLongValue("completed") == 200
                        && executor.getLongValue("failed") == 0
                        && executor.getLongValue("rejected") == 0
                        && executor.getLongValue("doneFutureCount") == 200
                        && executor.getLongValue("cancelledFutureCount") == 0
                        && executor.getLongValue("taskCount") == 200
                        && executor.getLongValue("completedTaskCount") == 200
                        && executor.getIntValue("activeBeforeQrelFreeze") == 0
                        && executor.getIntValue("queuedBeforeQrelFreeze") == 0
                        && executor.getIntValue(
                        "queueRemainingCapacityBeforeQrelFreeze") == 32
                        && executor.getBooleanValue("terminatedAfterCleanup"),
                SAFETY_ERROR);
    }

    private static void requireSeedSchema(JSONObject seed) {
        requireKeys(seed, Set.of(
                "loadRankingInputCalls", "queryCount", "familyCount",
                "corpusCount", "postgresDocumentCount",
                "postgresSegmentCount", "postgresEntityMetadataCount",
                "vectorDocumentCount", "vectorSnapshotKeyCount",
                "seedEmbeddingInputCount", "corpusProjectionSha256",
                "postgresProjectionSha256", "vectorProjectionSha256",
                "vectorSnapshotKeySetExact", "seedEmbeddingInputOrderExact",
                "projectionBytesEqual", "countersResetBeforeSelection"));
        String corpus = seed.getString("corpusProjectionSha256");
        require(seed.getIntValue("loadRankingInputCalls") == 1
                        && seed.getIntValue("queryCount") == QUERY_COUNT
                        && seed.getIntValue("familyCount") == FAMILY_COUNT
                        && seed.getIntValue("corpusCount") == CORPUS_COUNT
                        && seed.getIntValue("postgresDocumentCount")
                        == CORPUS_COUNT
                        && seed.getIntValue("postgresSegmentCount")
                        == CORPUS_COUNT
                        && seed.getIntValue("postgresEntityMetadataCount") == 0
                        && seed.getIntValue("vectorDocumentCount")
                        == CORPUS_COUNT
                        && seed.getIntValue("vectorSnapshotKeyCount")
                        == CORPUS_COUNT
                        && seed.getIntValue("seedEmbeddingInputCount")
                        == CORPUS_COUNT
                        && corpus != null && corpus.matches("[0-9a-f]{64}")
                        && corpus.equals(seed.getString(
                        "postgresProjectionSha256"))
                        && corpus.equals(seed.getString(
                        "vectorProjectionSha256"))
                        && seed.getBooleanValue("vectorSnapshotKeySetExact")
                        && seed.getBooleanValue("seedEmbeddingInputOrderExact")
                        && seed.getBooleanValue("projectionBytesEqual")
                        && seed.getBooleanValue(
                        "countersResetBeforeSelection"), SAFETY_ERROR);
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
            audit.fused = freezeResults(candidates, "fused");
            List<RetrievalResult> filtered = (List<RetrievalResult>)
                    invokeRerankStage(this, "filterIrrelevant",
                            query, copyResults(candidates), queryIntent);
            audit.postFilter = freezeResults(filtered, "postFilter");
            audit.colbertInput = freezeResults(filtered, "colbertInput");

            audit.trace.add("colbert(90)");
            audit.colbertCalls.incrementAndGet();
            List<RetrievalResult> colbert = (List<RetrievalResult>)
                    invokeRerankStage(this, "colbertCoarseRerank",
                            query, copyResults(filtered), CANDIDATE_TOP_K);
            require(colbert.size() <= CANDIDATE_TOP_K,
                    ORCHESTRATION_ERROR);
            audit.colbertTop90 = freezeResults(colbert, "colbertTop90");

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
            audit.admissionOutput30 = freezeResults(
                    admitted, "admissionOutput30");

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
            audit.candidate3Sources = freezeResults(
                    result, "candidate3Sources");
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
            List<RetrievalResult> values, String stage) {
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
        private final AtomicInteger superRerankCalls = new AtomicInteger();
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
                            rerank.candidate3Sources)
                            && contextSources.equals(candidate3Sources),
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

    private static String canonicalString(Object value) {
        return new String(RagCandidate10FreezeSupport.canonicalJsonBytes(value),
                StandardCharsets.UTF_8).stripTrailing();
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
        private Object access = Map.of(
                "selectionNonQrelResourceAccessCount", 0,
                "qrelResourceAccessBeforeRanking", 0,
                "qrelResourceAccessCount", 0,
                "holdoutResourceAccessCount", 0);
        private Object infrastructure = Map.of();
    }

    private record SelectionRun(
            Evaluation evaluation,
            Map<String, Object> budgets,
            Map<String, Object> infrastructure,
            Object access,
            Map<String, String> predecessor) {
    }

    private record PendingRun(
            Fixture fixture,
            SeededInfrastructure seeded,
            RuntimeAssembly assembly,
            List<QueryTrace> traces,
            Evaluation evaluation,
            Object access,
            Map<String, String> predecessor) {

        private SelectionRun finish(
                DockerBudget docker, CountingExecutor executor) {
            Map<String, Object> budgets = budgetView(
                    assembly, executor, traces);
            Map<String, Object> infrastructure = infrastructureView(
                    docker, executor, seeded);
            return new SelectionRun(evaluation, budgets, infrastructure,
                    access, predecessor);
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

    private record RouteDecision(
            String selectedBoundary, String decision, String errorCode) {
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
            RouteDecision route,
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

    private record LockState(
            RagCandidate10FreezeSupport.RuntimePaths paths,
            RagCandidate10FreezeSupport.FrozenEvidence frozen,
            Map<String, String> predecessor,
            Map<String, String> supplemental,
            String mode) {
    }
}
