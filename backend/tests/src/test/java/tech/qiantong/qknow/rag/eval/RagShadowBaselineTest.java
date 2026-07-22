package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.ai.service.IChatClientService;
import tech.qiantong.qknow.ai.service.IChatModelService;
import tech.qiantong.qknow.ai.service.IEmbeddingService;
import tech.qiantong.qknow.ai.service.IVectorStoreService;
import tech.qiantong.qknow.common.core.domain.model.LoginUser;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.eval.EvaluationDataset;
import tech.qiantong.qknow.hermes.eval.RagasEvalConfig;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase.KmcKnowledgeBaseMapper;
import tech.qiantong.qknow.module.kmc.service.rag.CandidateFusionService;
import tech.qiantong.qknow.module.kmc.service.rag.CragRetrievalEvaluator;
import tech.qiantong.qknow.module.kmc.service.rag.CragWebSearchClient;
import tech.qiantong.qknow.module.kmc.service.rag.CypherSafetyValidator;
import tech.qiantong.qknow.module.kmc.service.rag.DynamicTopKConfig;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagProperties;
import tech.qiantong.qknow.module.kmc.service.rag.GraphRagRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.MetadataRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.PermissionFilter;
import tech.qiantong.qknow.module.kmc.service.rag.RagContextBuilder;
import tech.qiantong.qknow.module.kmc.service.rag.RagRetrievalService;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.QueryEntityExtractionService;
import tech.qiantong.qknow.module.kmc.service.rag.QueryIntentAnalyzer;
import tech.qiantong.qknow.module.kmc.service.rag.VectorRetriever;
import tech.qiantong.qknow.module.kmc.service.rag.QueryRouter;
import tech.qiantong.qknow.module.kmc.service.rag.QueryTransformService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.nlp.JiebaNative;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertNative;
import tech.qiantong.qknow.module.kmc.service.rag.sim.VecSimNative;

import java.time.Duration;
import java.time.ZoneId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "rag.eval.shadow", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagShadowBaselineTest {

    private static final long NORMAL_KB_ID = 990000L;
    private static final long SENTINEL_KB_ID = 9_900_000L;
    private static final long KNOWLEDGE_BASE_ID = NORMAL_KB_ID;
    private static final String WARMUP_QUERY = "shadow warmup retrieval";
    private static final String IDENTIFIER_DIAGNOSTIC_PROPERTY = "rag.eval.identifier.diagnostic";
    private static final String CANDIDATE2_DIAGNOSTIC_PROPERTY = "rag.eval.candidate2.diagnostic";
    private static final String CANDIDATE2_DIAGNOSTIC_REPORT =
            "candidate2-calibration-diagnostic.json";
    private static final String CANDIDATE3_DIAGNOSTIC_PROPERTY = "rag.eval.candidate3.diagnostic";
    private static final String CANDIDATE3_DIAGNOSTIC_REPORT =
            "candidate3-calibration-diagnostic.json";
    private static final Set<String> CANDIDATE3_TARGET_CASES = Set.of(
            "q006-zh", "q011-zh", "q012-zh");
    private static final String CANDIDATE3_ALGORITHM =
            "document-name-exact-identifier-v1";
    private static final String CANDIDATE3_SCORE_POLICY =
            "deterministic-rank-score-preserving-v1";
    private static final int IDENTIFIER_DIAGNOSTIC_ROUNDS = 6;
    private static final int CANDIDATE8_BUSINESS_SQL_LIMIT = 500;
    private static final int CANDIDATE8_BUSINESS_JAVA_LIMIT = 50;
    private static final int CANDIDATE8_BUSINESS_COLBERT_LIMIT = 30;
    private static final Map<String, Long> IDENTIFIER_TARGET_SOURCES = Map.of(
            "q035-zh", 100035L, "q044-zh", 100044L, "q048-zh", 100048L);
    private static final Pattern CONTEXT_SEGMENT_MARKER = Pattern.compile("segmentId=(\\d+)");
    private static final String CONTEXT_SEGMENT_MARKER_PARSE_FAILED =
            "CONTEXT_SEGMENT_MARKER_PARSE_FAILED";
    private static final String CONTEXT_PARENT_SQL_FINGERPRINT = "s.qm_segment_id in (";
    private static final String CONTEXT_ADJACENT_PREVIOUS_SQL_FINGERPRINT = "s2.position - 1";
    private static final String CONTEXT_ADJACENT_NEXT_SQL_FINGERPRINT = "s2.position + 1";
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.1-pg16@sha256:33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337")
            .asCompatibleSubstituteFor("postgres");

    @Test
    @Order(1)
    @DisabledIfSystemProperty(named = IDENTIFIER_DIAGNOSTIC_PROPERTY, matches = "true")
    void isolatedPostgresAndRealSentinelsPassBeforeScoring() throws Exception {
        assumeFalse(candidate2CalibrationDiagnosticEnabled()
                || candidate3CalibrationDiagnosticEnabled()
                || candidate4CalibrationDiagnosticEnabled()
                || candidate5CalibrationDiagnosticEnabled()
                || candidate6CalibrationDiagnosticEnabled()
                || candidate8CalibrationDiagnosticEnabled()
                || candidate9CalibrationDiagnosticEnabled()
                || candidate5ReviewEnabled());
        clearRuntimeArtifacts();
        Locale previousLocale = Locale.getDefault();
        PostgreSQLContainer<?> container = null;
        ExecutorService executor = null;
        AtomicBoolean vectorStoreQueryAttempted = new AtomicBoolean();
        try {
            Locale.setDefault(Locale.ROOT);
            assertEquals("", System.getProperty("qknow.native.lib.dir", ""));
            assertTrue(System.getProperty("java.library.path", "")
                    .contains("backend/tests/target/rag-eval/no-native"));
            assertFalse(JiebaNative.isAvailable());
            assertFalse(VecSimNative.isAvailable());
            assertFalse(ColbertNative.isAvailable());

            container = newContainer();
            executor = Executors.newSingleThreadExecutor();
            container.start();
            JdbcTemplate jdbc = jdbc(container);
            createSchema(jdbc);
            insertSentinels(jdbc);
            assertNull(jdbc.queryForObject("SELECT to_regclass('public.vector_store')", Object.class));
            JdbcTemplate guardedJdbc = spy(jdbc);
            doAnswer(invocation -> {
                String sql = invocation.getArgument(0, String.class);
                if (sql.toLowerCase(Locale.ROOT).contains("vector_store")) {
                    vectorStoreQueryAttempted.set(true);
                    throw new AssertionError("shadow must not query vector_store");
                }
                return invocation.callRealMethod();
            }).when(guardedJdbc).queryForObject(any(String.class), eq(String.class));

            KmcKnowledgeBaseMapper kbMapper = mock(KmcKnowledgeBaseMapper.class);
            KmcKnowledgeBaseDO kb = KmcKnowledgeBaseDO.builder()
                    .id(KNOWLEDGE_BASE_ID)
                    .embeddingModelProvider("1")
                    .embeddingModel("shadow-feature-hash")
                    .rerankingEnable(false)
                    .build();
            when(kbMapper.selectById(KNOWLEDGE_BASE_ID)).thenReturn(kb);
            when(kbMapper.selectById(SENTINEL_KB_ID)).thenReturn(KmcKnowledgeBaseDO.builder()
                    .id(SENTINEL_KB_ID)
                    .embeddingModelProvider("1")
                    .embeddingModel("shadow-feature-hash")
                    .rerankingEnable(false)
                    .build());

            FeatureHashEmbeddingModel embeddingModel =
                    new FeatureHashEmbeddingModel(256, 20260715L, FeatureHashEmbeddingModel.VERSION);
            VectorStore vectorStore = buildVectorStore(embeddingModel);
            IAiModelApiService aiModelService = mock(IAiModelApiService.class);
            IVectorStoreService vectorStoreService = mock(IVectorStoreService.class);
            when(aiModelService.getEmbeddingModel(eq(1L), eq("shadow-feature-hash"))).thenReturn(embeddingModel);
            when(vectorStoreService.getVectorStore(embeddingModel)).thenReturn(vectorStore);

            VectorRetriever vectorRetriever = new VectorRetriever();
            ReflectionTestUtils.setField(vectorRetriever, "aiModelService", aiModelService);
            ReflectionTestUtils.setField(vectorRetriever, "vectorStoreService", vectorStoreService);
            ReflectionTestUtils.setField(vectorRetriever, "kmcKnowledgeBaseMapper", kbMapper);
            ReflectionTestUtils.setField(vectorRetriever, "jdbcTemplate", guardedJdbc);
            ReflectionTestUtils.setField(vectorRetriever, "vecSimRescoreEnabled", false);

            KeywordRetriever keywordRetriever = new KeywordRetriever();
            ReflectionTestUtils.setField(keywordRetriever, "jdbcTemplate", jdbc);
            ReflectionTestUtils.setField(keywordRetriever, "identifierAware", identifierAwareFlag());
            MetadataRetriever metadataRetriever = new MetadataRetriever();
            ReflectionTestUtils.setField(metadataRetriever, "jdbcTemplate", jdbc);
            RagContextBuilder contextBuilder = new RagContextBuilder();
            ReflectionTestUtils.setField(contextBuilder, "jdbcTemplate", jdbc);

            List<String> evidence = new ArrayList<>();
            SentinelPreflight preflight = new SentinelPreflight(executor, Duration.ofSeconds(5));
            SentinelPreflight.Result result = preflight.run(List.of(
                    new SentinelPreflight.NamedCheck("vector", () -> {
                        List<RetrievalResult> hits = vectorRetriever.retrieve(
                                SENTINEL_KB_ID, "VECTOR-SENTINEL-AZURE-QUARTZ-990001", 5);
                        boolean hit = hits.stream().anyMatch(item -> item.getSegmentId() == 990001L);
                        if (hit) evidence.add("vector:990001");
                        return hit;
                    }),
                    new SentinelPreflight.NamedCheck("keyword", () -> {
                        List<RetrievalResult> hits = keywordRetriever.retrieve(
                                SENTINEL_KB_ID, "KEYWORD-SENTINEL-COBALT-LANTERN-990002", 5);
                        boolean hit = hits.stream().anyMatch(item -> item.getSegmentId() == 990002L);
                        if (hit) evidence.add("keyword:990002");
                        return hit;
                    }),
                    new SentinelPreflight.NamedCheck("metadata", () -> {
                        QueryIntent intent = QueryIntent.builder()
                                .entities(List.of("META-SENTINEL-ENTITY"))
                                .build();
                        List<RetrievalResult> hits = metadataRetriever.retrieve(SENTINEL_KB_ID, intent, 5);
                        boolean hit = hits.stream().anyMatch(item -> item.getSegmentId() == 990003L);
                        if (hit) evidence.add("metadata:990003");
                        return hit;
                    }),
                    new SentinelPreflight.NamedCheck("context", () -> {
                        RetrievalResult child = RetrievalResult.builder()
                                .segmentId(990004L).qmSegmentId("990004").parentSegmentId("990005")
                                .documentId(99001L).documentName("sentinel-context")
                                .content("CTX-SENTINEL-CHILD").score(1.0).source("sentinel").build();
                        String context = contextBuilder.buildContext(List.of(child), true);
                        boolean hit = context.contains("segmentId=990005")
                                && context.contains("CTX-SENTINEL-PARENT");
                        if (hit) evidence.add("context:990004->990005:CTX-SENTINEL-PARENT");
                        return hit;
                    })
            ));

            if (!result.valid()) {
                writeInvalidShadowRun(result, evidence);
            }
            assertTrue(result.valid(), () -> "sentinel failed: " + result.failedCheck());
            assertEquals(List.of(
                    "vector:990001", "keyword:990002", "metadata:990003",
                    "context:990004->990005:CTX-SENTINEL-PARENT"), evidence);
            assertFalse((Boolean) ReflectionTestUtils.getField(vectorRetriever, "vecSimRescoreEnabled"));
            assertFalse(vectorStoreQueryAttempted.get(), "shadow must not access vector_store");
            assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM kmc_document_segment", Integer.class));
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            SecurityContextHolder.clearContext();
            if (container != null && container.isRunning()) {
                container.stop();
            }
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    @Order(2)
    void realRetrievalServiceUsesOriginalAndRetrievalQueryWithoutExternalCalls() throws Exception {
        boolean identifierDiagnostic = identifierDiagnosticEnabled();
        boolean candidate2Diagnostic = candidate2CalibrationDiagnosticEnabled();
        boolean candidate3Diagnostic = candidate3CalibrationDiagnosticEnabled();
        boolean candidate4Diagnostic = candidate4CalibrationDiagnosticEnabled();
        boolean candidate5Diagnostic = candidate5CalibrationDiagnosticEnabled();
        boolean candidate6Diagnostic = candidate6CalibrationDiagnosticEnabled();
        boolean candidate8Diagnostic = candidate8CalibrationDiagnosticEnabled();
        boolean candidate9Diagnostic = candidate9CalibrationDiagnosticEnabled();
        boolean candidate9Recovery = Boolean.parseBoolean(System.getProperty(
                RagCandidate91RecoverySupport.RECOVERY_PROPERTY, "false"));
        RagCandidate5ReviewSupport.Arm candidate5ReviewArm =
                RagCandidate5ReviewSupport.configuredArm();
        boolean candidate5Review = candidate5ReviewArm != null;
        int enabledDiagnostics = (identifierDiagnostic ? 1 : 0)
                + (candidate2Diagnostic ? 1 : 0)
                + (candidate3Diagnostic ? 1 : 0)
                + (candidate4Diagnostic ? 1 : 0)
                + (candidate5Diagnostic ? 1 : 0)
                + (candidate6Diagnostic ? 1 : 0)
                + (candidate8Diagnostic ? 1 : 0)
                + (candidate9Diagnostic ? 1 : 0);
        assertTrue(enabledDiagnostics <= 1, "diagnostic modes are mutually exclusive");
        assertFalse(candidate9Recovery && !candidate9Diagnostic,
                "Candidate 9.1 recovery requires Candidate 9 diagnostic");
        assertFalse(candidate5Review && enabledDiagnostics != 0,
                "Candidate 5 review and diagnostic modes are mutually exclusive");
        RagCandidate4DiagnosticSupport.RuntimePaths candidate4Paths = null;
        RagCandidate4DiagnosticSupport.FrozenSelection candidate4Selection = null;
        if (candidate4Diagnostic) {
            candidate4Paths = RagCandidate4DiagnosticSupport.paths(runtimeDirectory());
            RagCandidate4DiagnosticSupport.requireSelectionJobProperties();
            RagCandidate4DiagnosticSupport.requireSelectionRunAvailable(candidate4Paths);
            candidate4Selection = RagCandidate4DiagnosticSupport.loadFormalFrozenSelection(
                    runtimeDirectory());
        }
        RagCandidate5DiagnosticSupport.RuntimePaths candidate5Paths = null;
        RagCandidate5DiagnosticSupport.FrozenDataset candidate5Selection = null;
        if (candidate5Diagnostic) {
            candidate5Paths = RagCandidate5DiagnosticSupport.paths(runtimeDirectory());
            RagCandidate5DiagnosticSupport.requireSelectionJobProperties();
            RagCandidate5DiagnosticSupport.requireSelectionRunAvailable(candidate5Paths);
            candidate5Selection = RagCandidate5DiagnosticSupport.loadFormalFrozenSelection(
                    runtimeDirectory());
        }
        RagCandidate6DiagnosticSupport.RuntimePaths candidate6Paths = null;
        RagCandidate6DiagnosticSupport.FrozenDataset candidate6Selection = null;
        if (candidate6Diagnostic) {
            candidate6Paths = RagCandidate6DiagnosticSupport.paths(runtimeDirectory());
            RagCandidate6DiagnosticSupport.requireSelectionJobProperties();
            RagCandidate6DiagnosticSupport.requireSelectionRunAvailable(candidate6Paths);
            try {
                candidate6Selection =
                        RagCandidate6DiagnosticSupport.loadFormalFrozenSelection(
                                runtimeDirectory());
            } catch (RuntimeException failure) {
                try {
                    RagCandidate6DiagnosticSupport.writePreRunInvalidDiagnostic(
                            candidate6Paths,
                            candidate6Config(),
                            "CANDIDATE6_FROZEN_INPUT_INVALID");
                } catch (RuntimeException writeFailure) {
                    failure.addSuppressed(writeFailure);
                }
                throw failure;
            }
        }
        RagCandidate8DiagnosticSupport.RuntimePaths candidate8Paths = null;
        RagCandidate8DiagnosticSupport.FrozenDataset candidate8Selection = null;
        if (candidate8Diagnostic) {
            candidate8Paths = RagCandidate8DiagnosticSupport.paths(runtimeDirectory());
            RagCandidate8DiagnosticSupport.requireSelectionJobProperties();
            RagCandidate8DiagnosticSupport.requireSelectionRunAvailable(candidate8Paths);
            try {
                candidate8Selection =
                        RagCandidate8DiagnosticSupport.loadFormalFrozenSelection(
                                runtimeDirectory());
            } catch (RuntimeException failure) {
                try {
                    RagCandidate8DiagnosticSupport.writePreRunInvalidDiagnostic(
                            candidate8Paths,
                            candidate8Config(),
                            "CANDIDATE8_FROZEN_INPUT_INVALID");
                } catch (RuntimeException writeFailure) {
                    failure.addSuppressed(writeFailure);
                }
                throw failure;
            }
        }
        RagCandidate9DiagnosticSupport.RuntimePaths candidate9Paths = null;
        RagCandidate9DiagnosticSupport.FrozenDataset candidate9Selection = null;
        RagCandidate91RecoverySupport.RecoveryPaths candidate91Paths = null;
        RagCandidate91RecoverySupport.ArchiveHandle candidate91Archive = null;
        if (candidate9Diagnostic) {
            candidate9Paths = RagCandidate9DiagnosticSupport.paths(runtimeDirectory());
            if (candidate9Recovery) {
                RagCandidate91RecoverySupport.requireRecoveryProperties();
                assertEquals("UTC", ZoneId.systemDefault().getId());
                assertEquals(Locale.forLanguageTag("en-US"), Locale.getDefault());
                assertFalse(JiebaNative.isAvailable());
                assertFalse(VecSimNative.isAvailable());
                assertFalse(ColbertNative.isAvailable());
                RagCandidate91RecoverySupport.requireCurrentConfigHash(
                        ShadowContractSupport.configHash(candidate9Config()));
                candidate91Paths = RagCandidate91RecoverySupport.paths(runtimeDirectory());
                Path workingDirectory = Path.of(System.getProperty("user.dir"))
                        .toAbsolutePath();
                Path backendRoot = workingDirectory.endsWith(Path.of("backend", "tests"))
                        ? workingDirectory.getParent()
                        : Files.isDirectory(workingDirectory.resolve("tests"))
                        ? workingDirectory
                        : workingDirectory.resolve("backend");
                candidate91Archive = RagCandidate91RecoverySupport.publishArchive(
                        candidate91Paths,
                        backendRoot,
                        RagCandidate91RecoverySupport.SOURCE_LOCK_STAGING,
                        RagCandidate91RecoverySupport.lockedLegacyBinding());
                candidate9Selection =
                        RagCandidate9DiagnosticSupport.loadFormalFrozenSelection(
                                runtimeDirectory());
            } else {
                try {
                    RagCandidate9DiagnosticSupport
                            .requireDiagnosticCommandProperties();
                    RagCandidate9DiagnosticSupport.requireSelectionJobProperties();
                } catch (RuntimeException commandFailure) {
                    RagCandidate9DiagnosticSupport
                            .writePreRunCommandInvalidDiagnostic(candidate9Paths);
                    throw commandFailure;
                }
                RagCandidate9DiagnosticSupport.requireSelectionRunAvailable(candidate9Paths);
                try {
                    candidate9Selection =
                            RagCandidate9DiagnosticSupport.loadFormalFrozenSelection(
                                    runtimeDirectory());
                } catch (RuntimeException failure) {
                    try {
                        RagCandidate9DiagnosticSupport.writePreRunInvalidDiagnostic(
                                candidate9Paths,
                                candidate9Config(),
                                "CANDIDATE9_FROZEN_INPUT_INVALID");
                    } catch (RuntimeException writeFailure) {
                        failure.addSuppressed(writeFailure);
                    }
                    throw failure;
                }
            }
        }
        RagCandidate5DiagnosticSupport.FrozenDataset candidate5Holdout = null;
        if (candidate5Review) {
            assertFalse(Boolean.parseBoolean(System.getProperty(
                    "rag.eval.promotion", "false")),
                    "Candidate 5 review must not run as promotion");
            assertFalse(identifierAwareFlag(), "Candidate 5 review requires identifierAware=false");
            assertTrue(identifierConsistencyFlag(),
                    "Candidate 5 review requires Candidate 3 enabled");
            assertEquals(candidate5ReviewArm.enabled(), identifierRecallConsistencyFlag(),
                    "Candidate 5 review arm does not match feature flag");
            assertFalse(Boolean.parseBoolean(System.getProperty(
                    "rag.eval.shadow.compare-stable", "true")),
                    "Candidate 5 review must not compare the stable snapshot");
            candidate5Holdout = RagCandidate5DiagnosticSupport
                    .loadApprovedFormalFrozenHoldout(runtimeDirectory());
            RagCandidate5ReviewSupport.requireArtifactsAbsent(
                    runtimeDirectory(), candidate5ReviewArm);
        }
        Map<String, String> sharedArtifactHashes =
                candidate2Diagnostic || candidate3Diagnostic || candidate4Diagnostic
                        || candidate5Diagnostic || candidate6Diagnostic
                        || candidate8Diagnostic || candidate9Diagnostic
                ? sharedLiveArtifactHashes() : Map.of();
        clearRuntimeArtifacts();
        RagCandidate4DiagnosticSupport.RunHandle candidate4RunHandle = null;
        boolean candidate4RunCompleted = false;
        RagCandidate5DiagnosticSupport.RunHandle candidate5RunHandle = null;
        boolean candidate5RunCompleted = false;
        RagCandidate6DiagnosticSupport.RunHandle candidate6RunHandle = null;
        boolean candidate6RunCompleted = false;
        RagCandidate8DiagnosticSupport.RunHandle candidate8RunHandle = null;
        boolean candidate8RunCompleted = false;
        RagCandidate9DiagnosticSupport.RunHandle candidate9RunHandle = null;
        boolean candidate9RunCompleted = false;
        RagCandidate91RecoverySupport.PreflightHandle candidate91Preflight = null;
        RagCandidate91RecoverySupport.RecoveryRunHandle candidate91RunHandle = null;
        RagCandidate9DiagnosticSupport.RecoveryBinding candidate91Binding = null;
        int candidate91DockerInfrastructureCalls = 0;
        int candidate91DockerImagePullCalls = 0;
        boolean candidate91PostgresImagePresent = true;
        boolean candidate91PreflightStarted = false;
        Locale previousLocale = Locale.getDefault();
        TimeZone previousTimeZone = TimeZone.getDefault();
        PostgreSQLContainer<?> container = null;
        ThreadPoolTaskExecutor retrievalExecutor = null;
        ExecutorService sentinelExecutor = null;
        try {
            if (candidate4Diagnostic) {
                candidate4RunHandle = RagCandidate4DiagnosticSupport.beginSelectionRun(
                        candidate4Paths, candidate4Selection.manifests());
            }
            if (candidate5Diagnostic) {
                candidate5RunHandle = RagCandidate5DiagnosticSupport.beginSelectionRun(
                        candidate5Paths, candidate5Selection.manifests());
            }
            if (candidate6Diagnostic) {
                candidate6RunHandle = RagCandidate6DiagnosticSupport.beginSelectionRun(
                        candidate6Paths, candidate6Selection.manifests());
            }
            if (candidate8Diagnostic) {
                candidate8RunHandle = RagCandidate8DiagnosticSupport.beginSelectionRun(
                        candidate8Paths, candidate8Selection);
            }
            if (candidate9Diagnostic && !candidate9Recovery) {
                candidate9RunHandle = RagCandidate9DiagnosticSupport.beginSelectionRun(
                        candidate9Paths, candidate9Selection);
            }
            Locale.setDefault(candidate9Diagnostic
                    ? Locale.forLanguageTag("en-US") : Locale.ROOT);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals("UTC", TimeZone.getDefault().getID());
            assertEquals("UTC", ZoneId.systemDefault().getId());
            assertEquals("", System.getProperty("qknow.native.lib.dir", ""));
            assertTrue(System.getProperty("java.library.path", "")
                    .contains("backend/tests/target/rag-eval/no-native"));
            assertFalse(JiebaNative.isAvailable());
            assertFalse(VecSimNative.isAvailable());
            assertFalse(ColbertNative.isAvailable());
            RagEvaluationDataset dataset = candidate4Diagnostic
                    ? candidate4Selection.dataset()
                    : candidate5Diagnostic
                    ? candidate5Selection.dataset()
                    : candidate6Diagnostic
                    ? candidate6Selection.dataset()
                    : candidate8Diagnostic
                    ? candidate8Selection.dataset()
                    : candidate9Diagnostic
                    ? candidate9Selection.dataset()
                    : candidate5Review
                    ? candidate5Holdout.dataset()
                    : RagEvaluationDatasetLoader.loadDefault();
            long activeKnowledgeBaseId = candidate4Diagnostic
                    ? RagCandidate4DiagnosticSupport.SELECTION_KB_ID
                    : candidate5Diagnostic
                    ? RagCandidate5DiagnosticSupport.SELECTION_KB_ID
                    : candidate6Diagnostic
                    ? RagCandidate6DiagnosticSupport.SELECTION_KB_ID
                    : candidate8Diagnostic
                    ? RagCandidate8DiagnosticSupport.SELECTION_KB_ID
                    : candidate9Diagnostic
                    ? RagCandidate9DiagnosticSupport.SELECTION_KB_ID
                    : candidate5Review
                    ? RagCandidate5DiagnosticSupport.HOLDOUT_KB_ID
                    : KNOWLEDGE_BASE_ID;
            long firstDocumentId = candidate4Diagnostic
                    ? RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                    : candidate5Diagnostic
                    ? RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                    : candidate6Diagnostic
                    ? RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                    : candidate8Diagnostic
                    ? RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                    : candidate9Diagnostic
                    ? RagCandidate9DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                    : candidate5Review
                    ? RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MIN
                    : 100000L;
            if (candidate9Recovery) {
                candidate91PreflightStarted = true;
                try {
                    candidate91DockerInfrastructureCalls++;
                    DockerClientFactory.instance().client().inspectImageCmd(
                            POSTGRES_IMAGE.asCanonicalNameString()).exec();
                } catch (NotFoundException imageMissing) {
                    candidate91PostgresImagePresent = false;
                }
            }
            container = newContainer();
            if (candidate9Recovery && !candidate91PostgresImagePresent) {
                candidate91DockerImagePullCalls++;
            }
            if (candidate9Recovery) {
                candidate91DockerInfrastructureCalls++;
            }
            container.start();
            JdbcTemplate setupJdbc = jdbc(container);
            AtomicBoolean vectorStoreQueryAttempted = new AtomicBoolean();
            createSchema(setupJdbc);
            insertSentinels(setupJdbc);
            Map<String, Long> documentIds = insertCorpus(
                    setupJdbc, dataset, activeKnowledgeBaseId, firstDocumentId);
            if (candidate9Recovery) {
                try {
                    candidate91Preflight = RagCandidate91RecoverySupport.preflight(
                            candidate91Archive,
                            container,
                            new RagCandidate91RecoverySupport.DockerInfrastructureCounters(
                                    candidate91DockerInfrastructureCalls,
                                    candidate91DockerImagePullCalls));
                } catch (RuntimeException preflightFailure) {
                    try {
                        RagCandidate91RecoverySupport.publishPreflightFailure(
                                candidate91Archive,
                                "CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
                    } catch (RuntimeException publishFailure) {
                        preflightFailure.addSuppressed(publishFailure);
                    }
                    throw preflightFailure;
                }
                RagCandidate91RecoverySupport.requireSameContainer(
                        candidate91Preflight, container);
                candidate91RunHandle = RagCandidate91RecoverySupport.beginRecovery(
                        candidate91Preflight);
                candidate91Binding = RagCandidate91RecoverySupport.bindFrozenSelection(
                        candidate91RunHandle, candidate9Paths, candidate9Selection);
            }
            QueryCounters queryCounters = new QueryCounters();
            IdentifierLatencyDiagnostics latencyDiagnostics = identifierDiagnostic
                    ? new IdentifierLatencyDiagnostics() : null;
            boolean calibrationDiagnostic =
                    candidate2Diagnostic || candidate3Diagnostic || candidate4Diagnostic
                            || candidate5Diagnostic || candidate6Diagnostic
                            || candidate8Diagnostic || candidate9Diagnostic;
            CalibrationStageDiagnostics calibrationDiagnostics = calibrationDiagnostic
                    ? new CalibrationStageDiagnostics() : null;
            JdbcTemplate keywordJdbc = countingJdbc(
                    container, queryCounters, DbCallCategory.KEYWORD, null,
                    latencyDiagnostics,
                    candidate5Diagnostic || candidate6Diagnostic || candidate8Diagnostic
                            || candidate9Diagnostic
                            ? calibrationDiagnostics : null);
            Candidate6JdbcTemplate candidate6KeywordJdbc = null;
            Candidate8JdbcTemplate candidate8KeywordJdbc = null;
            if (candidate5Diagnostic) {
                keywordJdbc = new Candidate5JdbcTemplate(
                        keywordJdbc.getDataSource(), calibrationDiagnostics);
            } else if (candidate6Diagnostic) {
                candidate6KeywordJdbc = new Candidate6JdbcTemplate(
                        keywordJdbc.getDataSource(), calibrationDiagnostics);
                keywordJdbc = candidate6KeywordJdbc;
            } else if (candidate8Diagnostic) {
                candidate8KeywordJdbc = new Candidate8JdbcTemplate(
                        keywordJdbc.getDataSource(), calibrationDiagnostics);
                keywordJdbc = candidate8KeywordJdbc;
            }
            JdbcTemplate metadataJdbc = countingJdbc(
                    container, queryCounters, DbCallCategory.METADATA, null,
                    null, candidate5Diagnostic || candidate6Diagnostic
                            || candidate8Diagnostic || candidate9Diagnostic
                            ? calibrationDiagnostics : null);
            JdbcTemplate vectorJdbc = countingJdbc(
                    container, queryCounters, DbCallCategory.VECTOR, vectorStoreQueryAttempted,
                    null, candidate5Diagnostic || candidate6Diagnostic
                            || candidate8Diagnostic || candidate9Diagnostic
                            ? calibrationDiagnostics : null);
            JdbcTemplate contextJdbc = countingJdbc(
                    container, queryCounters, DbCallCategory.CONTEXT, null,
                    null, candidate5Diagnostic || candidate6Diagnostic
                            || candidate8Diagnostic || candidate9Diagnostic
                            ? calibrationDiagnostics : null);
            JdbcTemplate otherJdbc = countingJdbc(
                    container, queryCounters, DbCallCategory.OTHER, null,
                    null, candidate5Diagnostic || candidate6Diagnostic
                            || candidate8Diagnostic || candidate9Diagnostic
                            ? calibrationDiagnostics : null);

            FeatureHashEmbeddingModel embeddingModel =
                    new FeatureHashEmbeddingModel(256, 20260715L, FeatureHashEmbeddingModel.VERSION);
            VectorStore vectorStore = buildCorpusVectorStore(
                    embeddingModel, dataset, documentIds, activeKnowledgeBaseId);
            IAiModelApiService aiModelService = mock(IAiModelApiService.class);
            IVectorStoreService vectorStoreService = mock(IVectorStoreService.class);
            when(aiModelService.getEmbeddingModel(eq(1L), eq("shadow-feature-hash")))
                    .thenReturn(embeddingModel);
            when(vectorStoreService.getVectorStore(embeddingModel)).thenReturn(vectorStore);

            KmcKnowledgeBaseMapper kbMapper = mock(KmcKnowledgeBaseMapper.class);
            when(kbMapper.selectById(activeKnowledgeBaseId)).thenReturn(KmcKnowledgeBaseDO.builder()
                    .id(activeKnowledgeBaseId)
                    .embeddingModelProvider("1")
                    .embeddingModel("shadow-feature-hash")
                    .rerankingEnable(false)
                    .build());
            when(kbMapper.selectById(SENTINEL_KB_ID)).thenReturn(KmcKnowledgeBaseDO.builder()
                    .id(SENTINEL_KB_ID)
                    .embeddingModelProvider("1")
                    .embeddingModel("shadow-feature-hash")
                    .rerankingEnable(false)
                    .build());

            VectorRetriever vectorRetriever = new VectorRetriever();
            ReflectionTestUtils.setField(vectorRetriever, "aiModelService", aiModelService);
            ReflectionTestUtils.setField(vectorRetriever, "vectorStoreService", vectorStoreService);
            ReflectionTestUtils.setField(vectorRetriever, "kmcKnowledgeBaseMapper", kbMapper);
            ReflectionTestUtils.setField(vectorRetriever, "jdbcTemplate", vectorJdbc);
            ReflectionTestUtils.setField(vectorRetriever, "vecSimRescoreEnabled", false);

            VectorStore sentinelVectorStore = buildVectorStore(embeddingModel);
            IVectorStoreService sentinelVectorStoreService = mock(IVectorStoreService.class);
            when(sentinelVectorStoreService.getVectorStore(embeddingModel)).thenReturn(sentinelVectorStore);
            VectorRetriever sentinelVectorRetriever = new VectorRetriever();
            ReflectionTestUtils.setField(sentinelVectorRetriever, "aiModelService", aiModelService);
            ReflectionTestUtils.setField(sentinelVectorRetriever, "vectorStoreService", sentinelVectorStoreService);
            ReflectionTestUtils.setField(sentinelVectorRetriever, "kmcKnowledgeBaseMapper", kbMapper);
            ReflectionTestUtils.setField(sentinelVectorRetriever, "jdbcTemplate", vectorJdbc);
            ReflectionTestUtils.setField(sentinelVectorRetriever, "vecSimRescoreEnabled", false);

            KeywordRetriever keywordRetriever = identifierDiagnostic
                    ? new DiagnosticKeywordRetriever("A", latencyDiagnostics)
                    : candidate5Diagnostic
                    ? new Candidate5KeywordRetriever(calibrationDiagnostics)
                    : candidate6Diagnostic
                    ? new Candidate6KeywordRetriever(calibrationDiagnostics)
                    : candidate8Diagnostic
                    ? new Candidate8KeywordRetriever(calibrationDiagnostics)
                    : new KeywordRetriever();
            ReflectionTestUtils.setField(keywordRetriever, "jdbcTemplate", keywordJdbc);
            ReflectionTestUtils.setField(keywordRetriever, "identifierAware",
                    identifierDiagnostic || calibrationDiagnostic ? false : identifierAwareFlag());
            KeywordRetriever candidateKeywordRetriever = null;
            if (identifierDiagnostic) {
                candidateKeywordRetriever = new DiagnosticKeywordRetriever("C", latencyDiagnostics);
                ReflectionTestUtils.setField(candidateKeywordRetriever, "jdbcTemplate", keywordJdbc);
                ReflectionTestUtils.setField(candidateKeywordRetriever, "identifierAware", true);
            }
            MetadataRetriever metadataRetriever = new MetadataRetriever();
            ReflectionTestUtils.setField(metadataRetriever, "jdbcTemplate", metadataJdbc);
            RagContextBuilder contextBuilder = identifierDiagnostic
                    ? new DiagnosticContextBuilder(latencyDiagnostics)
                    : new RagContextBuilder();
            ReflectionTestUtils.setField(contextBuilder, "jdbcTemplate", contextJdbc);

            AtomicLong externalChatCalls = new AtomicLong();
            AtomicLong externalEmbeddingBatchCalls = new AtomicLong();
            IChatClientService chatClientService = mock(IChatClientService.class, invocation -> {
                externalChatCalls.incrementAndGet();
                throw new IllegalStateException("shadow external chat disabled: "
                        + invocation.getMethod().getName());
            });
            IChatModelService chatModelService = mock(IChatModelService.class, invocation -> {
                throw new IllegalStateException("shadow external chat model disabled: "
                        + invocation.getMethod().getName());
            });
            EmbeddingModel forbiddenExternalEmbeddingModel = mock(
                    EmbeddingModel.class, invocation -> {
                        if ("call".equals(invocation.getMethod().getName())) {
                            externalEmbeddingBatchCalls.incrementAndGet();
                        }
                        throw new IllegalStateException(
                                "Candidate 9 external embedding disabled");
                    });
            IEmbeddingService forbiddenExternalEmbeddingService = mock(
                    IEmbeddingService.class, invocation ->
                            forbiddenExternalEmbeddingModel);

            QueryRouter.QueryRouterConfig routerConfig = new QueryRouter.QueryRouterConfig();
            routerConfig.setEnabled(!(
                    candidate5Diagnostic || candidate6Diagnostic
                            || candidate8Diagnostic || candidate9Diagnostic
                            || candidate5Review));
            routerConfig.setPlatform("shadow-local");
            routerConfig.setBaseUrl("http://shadow.invalid");
            routerConfig.setApiKey("shadow-placeholder");
            routerConfig.setModelName("shadow-router");
            QueryRouter queryRouter = new QueryRouter(chatClientService, routerConfig);
            QueryEntityExtractionService.QueryEntityConfig entityConfig =
                    new QueryEntityExtractionService.QueryEntityConfig();
            entityConfig.setEnabled(false);
            QueryEntityExtractionService entityExtraction =
                    new QueryEntityExtractionService(chatModelService, entityConfig);
            QueryTransformService.QueryTransformConfig transformConfig =
                    new QueryTransformService.QueryTransformConfig();
            transformConfig.setEnabled(false);
            transformConfig.setStrategy("none");
            QueryTransformService queryTransform = new QueryTransformService(chatClientService, transformConfig);
            CragRetrievalEvaluator.CragConfig cragConfig = new CragRetrievalEvaluator.CragConfig();
            cragConfig.setEnabled(false);
            CragRetrievalEvaluator crag = new CragRetrievalEvaluator(chatModelService, cragConfig);
            CragWebSearchClient.CragWebSearchConfig webConfig = new CragWebSearchClient.CragWebSearchConfig();
            webConfig.setEnabled(false);
            CragWebSearchClient webSearch = new CragWebSearchClient(webConfig);

            GraphRagProperties graphProperties = new GraphRagProperties();
            graphProperties.setEnabled(false);
            GraphRagRetriever graphRetriever = new GraphRagRetriever();
            ReflectionTestUtils.setField(graphRetriever, "jdbcTemplate", otherJdbc);
            ReflectionTestUtils.setField(graphRetriever, "properties", graphProperties);
            ReflectionTestUtils.setField(graphRetriever, "cypherSafetyValidator", new CypherSafetyValidator());

            ColbertScorer.ColbertConfig colbertConfig = new ColbertScorer.ColbertConfig();
            colbertConfig.setEnabled(true);
            colbertConfig.setDimensions(64);
            colbertConfig.setMaxTokensPerDoc(128);
            ColbertScorer colbertScorer = calibrationDiagnostic
                    ? new CalibrationColbertScorer(
                            colbertConfig, calibrationDiagnostics, embeddingModel,
                            candidate4Diagnostic || candidate8Diagnostic
                                    || candidate9Diagnostic,
                            candidate8Diagnostic,
                            candidate9Diagnostic,
                            candidate9Diagnostic
                                    ? forbiddenExternalEmbeddingService : null)
                    : new ColbertScorer(colbertConfig, null);
            assertFalse(ColbertNative.isAvailable());
            List<Document> colbertSmokeDocuments = List.of(
                    Document.builder().id("colbert-a").text("COLBERT-JAVA-FALLBACK-A").build(),
                    Document.builder().id("colbert-b").text("COLBERT-JAVA-FALLBACK-B").build());
            List<Document> colbertSmokeResult = colbertScorer.rerank(
                    "COLBERT-JAVA-FALLBACK", colbertSmokeDocuments, 2);
            assertEquals(2, colbertSmokeResult.size());
            assertTrue(colbertSmokeResult.stream()
                    .allMatch(document -> document.getMetadata().containsKey("colbert_score")));
            DeterministicRerankerProvider deterministic = calibrationDiagnostic
                    ? new CalibrationDeterministicReranker(calibrationDiagnostics)
                    : new DeterministicRerankerProvider();
            RagRerankService rerankService = identifierDiagnostic
                    ? new DiagnosticRerankService(latencyDiagnostics)
                    : calibrationDiagnostic
                    ? new CalibrationRerankService(calibrationDiagnostics)
                    : new RagRerankService();
            ReflectionTestUtils.setField(rerankService, "rerankerProviders", List.of(deterministic));
            ReflectionTestUtils.setField(rerankService, "deterministicRerankerProvider", deterministic);
            ReflectionTestUtils.setField(rerankService, "colbertScorer", colbertScorer);
            ReflectionTestUtils.setField(rerankService, "identifierConsistencyEnabled",
                    candidate4Diagnostic || candidate5Diagnostic || candidate6Diagnostic
                            || candidate8Diagnostic || candidate9Diagnostic
                            || (!calibrationDiagnostic && identifierConsistencyFlag()));

            CandidateFusionService fusionService = identifierDiagnostic
                    ? new DiagnosticFusionService(latencyDiagnostics)
                    : calibrationDiagnostic
                    ? new CalibrationFusionService(calibrationDiagnostics)
                    : new CandidateFusionService();
            ReflectionTestUtils.setField(fusionService, "rrfK", 60);
            ReflectionTestUtils.setField(fusionService, "weakPathThreshold", 0.0D);

            PermissionFilter permissionFilter = mock(PermissionFilter.class);
            when(permissionFilter.getAccessibleKnowledgeBaseIds(990001L))
                    .thenReturn(List.of(activeKnowledgeBaseId));
            assertEquals(List.of(activeKnowledgeBaseId),
                    permissionFilter.getAccessibleKnowledgeBaseIds(990001L));
            assertFalse(permissionFilter.getAccessibleKnowledgeBaseIds(990001L).contains(SENTINEL_KB_ID));
            if (candidate4Diagnostic || candidate5Diagnostic
                    || candidate6Diagnostic || candidate8Diagnostic
                    || candidate9Diagnostic || candidate5Review) {
                assertFalse(permissionFilter.getAccessibleKnowledgeBaseIds(990001L)
                        .contains(NORMAL_KB_ID));
                if (candidate4Diagnostic) {
                    assertSelectionDatabaseIsolation(setupJdbc, documentIds);
                } else if (candidate5Diagnostic) {
                    try {
                        assertCandidate5SelectionDatabaseIsolation(setupJdbc, documentIds);
                    } catch (AssertionError failure) {
                        throw new Candidate5DiagnosticFailure(
                                "CANDIDATE5_KB_ISOLATION_FAILED", failure);
                    }
                } else if (candidate6Diagnostic) {
                    assertCandidate6SelectionDatabaseIsolation(setupJdbc, documentIds);
                } else if (candidate8Diagnostic) {
                    assertCandidate8SelectionDatabaseIsolation(setupJdbc, documentIds);
                } else if (candidate9Diagnostic) {
                    assertCandidate9SelectionDatabaseIsolation(setupJdbc, documentIds);
                } else {
                    assertCandidate5HoldoutDatabaseIsolation(setupJdbc, documentIds);
                }
            }

            DynamicTopKConfig dynamicTopK = new DynamicTopKConfig();
            dynamicTopK.setEnabled(false);
            retrievalExecutor = new ThreadPoolTaskExecutor();
            retrievalExecutor.setCorePoolSize(4);
            retrievalExecutor.setMaxPoolSize(4);
            retrievalExecutor.setQueueCapacity(32);
            retrievalExecutor.setThreadNamePrefix("rag-shadow-");
            retrievalExecutor.initialize();

            QueryIntentAnalyzer queryIntentAnalyzer = spy(new QueryIntentAnalyzer());
            RagRetrievalService service = new RagRetrievalService();
            ReflectionTestUtils.setField(service, "queryIntentAnalyzer", queryIntentAnalyzer);
            ReflectionTestUtils.setField(service, "vectorRetriever", vectorRetriever);
            ReflectionTestUtils.setField(service, "keywordRetriever", keywordRetriever);
            ReflectionTestUtils.setField(service, "metadataRetriever", metadataRetriever);
            ReflectionTestUtils.setField(service, "graphRagRetriever", graphRetriever);
            ReflectionTestUtils.setField(service, "candidateFusionService", fusionService);
            ReflectionTestUtils.setField(service, "ragRerankService", rerankService);
            ReflectionTestUtils.setField(service, "ragContextBuilder", contextBuilder);
            ReflectionTestUtils.setField(service, "kmcKnowledgeBaseMapper", kbMapper);
            ReflectionTestUtils.setField(service, "permissionFilter", permissionFilter);
            ReflectionTestUtils.setField(service, "queryEntityExtractionService", entityExtraction);
            ReflectionTestUtils.setField(service, "cragRetrievalEvaluator", crag);
            ReflectionTestUtils.setField(service, "queryRouter", queryRouter);
            ReflectionTestUtils.setField(service, "queryTransformService", queryTransform);
            ReflectionTestUtils.setField(service, "cragWebSearchClient", webSearch);
            ReflectionTestUtils.setField(service, "dynamicTopKConfig", dynamicTopK);
            ReflectionTestUtils.setField(service, "retrievalExecutor", retrievalExecutor);

            sentinelExecutor = Executors.newSingleThreadExecutor();
            List<String> sentinelEvidence = new ArrayList<>();
            SentinelPreflight.Result sentinelResult = new SentinelPreflight(
                    sentinelExecutor, Duration.ofSeconds(5)).run(List.of(
                    new SentinelPreflight.NamedCheck("vector", () -> {
                        List<RetrievalResult> hits = sentinelVectorRetriever.retrieve(
                                SENTINEL_KB_ID, "VECTOR-SENTINEL-AZURE-QUARTZ-990001", 5);
                        boolean hit = hits.stream().anyMatch(item -> item.getSegmentId() == 990001L);
                        if (hit) {
                            sentinelEvidence.add("vector:990001");
                        }
                        return hit;
                    }),
                    new SentinelPreflight.NamedCheck("keyword", () -> {
                        List<RetrievalResult> hits = keywordRetriever.retrieve(
                                SENTINEL_KB_ID, "KEYWORD-SENTINEL-COBALT-LANTERN-990002", 5);
                        boolean hit = hits.stream().anyMatch(item -> item.getSegmentId() == 990002L);
                        if (hit) {
                            sentinelEvidence.add("keyword:990002");
                        }
                        return hit;
                    }),
                    new SentinelPreflight.NamedCheck("metadata", () -> {
                        QueryIntent intent = QueryIntent.builder()
                                .entities(List.of("META-SENTINEL-ENTITY"))
                                .build();
                        List<RetrievalResult> hits = metadataRetriever.retrieve(SENTINEL_KB_ID, intent, 5);
                        boolean hit = hits.stream().anyMatch(item -> item.getSegmentId() == 990003L);
                        if (hit) {
                            sentinelEvidence.add("metadata:990003");
                        }
                        return hit;
                    }),
                    new SentinelPreflight.NamedCheck("context", () -> {
                        RetrievalResult child = RetrievalResult.builder()
                                .segmentId(990004L).qmSegmentId("990004").parentSegmentId("990005")
                                .documentId(99001L).documentName("sentinel-context")
                                .content("CTX-SENTINEL-CHILD").score(1.0).source("sentinel").build();
                        String context = contextBuilder.buildContext(List.of(child), true);
                        boolean hit = context.contains("segmentId=990005")
                                && context.contains("CTX-SENTINEL-PARENT");
                        if (hit) {
                            sentinelEvidence.add("context:990004->990005:CTX-SENTINEL-PARENT");
                        }
                        return hit;
                    })
            ));
            if (!sentinelResult.valid()) {
                writeInvalidShadowRun(sentinelResult, sentinelEvidence);
            }
            assertTrue(sentinelResult.valid(), () -> "sentinel failed: " + sentinelResult.failedCheck());
            assertEquals(List.of(
                    "vector:990001", "keyword:990002", "metadata:990003",
                    "context:990004->990005:CTX-SENTINEL-PARENT"), sentinelEvidence);
            assertFalse(vectorStoreQueryAttempted.get(), "shadow must not access vector_store");

            if (candidate5Diagnostic || candidate6Diagnostic
                    || candidate8Diagnostic || candidate9Diagnostic
                    || candidate5Review) {
                assertEquals(QueryRouter.QueryRoute.MEDIUM,
                        queryRouter.classify(
                                "ambiguous shadow routing request requiring classification"));
                org.mockito.Mockito.verifyNoInteractions(chatClientService);
            } else {
                // Exercise the real ambiguous-query routing branch. The local mock throws a
                // RuntimeException, which QueryRouter must catch and map to MEDIUM.
                assertEquals(QueryRouter.QueryRoute.MEDIUM,
                        queryRouter.classify(
                                "ambiguous shadow routing request requiring classification"));
                org.mockito.Mockito.verify(chatClientService, org.mockito.Mockito.atLeastOnce())
                        .getChatClient("shadow-local", "http://shadow.invalid",
                                "shadow-placeholder", "shadow-router");
            }
            externalChatCalls.set(0L);

            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(990001L);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
            if (candidate4Diagnostic || candidate5Diagnostic
                    || candidate6Diagnostic || candidate8Diagnostic
                    || candidate9Diagnostic || candidate5Review) {
                org.mockito.Mockito.clearInvocations(kbMapper, permissionFilter);
            }

            if (identifierDiagnostic) {
                runIdentifierLatencyDiagnostic(dataset, service, keywordRetriever,
                        candidateKeywordRetriever, retrievalExecutor, queryCounters,
                        embeddingModel, latencyDiagnostics);
                org.mockito.Mockito.verifyNoInteractions(chatModelService);
                return;
            }
            if (candidate2Diagnostic) {
                runCandidate2CalibrationDiagnostic(
                        dataset, service, retrievalExecutor, calibrationDiagnostics);
                org.mockito.Mockito.verifyNoInteractions(chatModelService);
                return;
            }
            if (candidate3Diagnostic) {
                runCandidate3CalibrationDiagnostic(
                        dataset, service, contextBuilder, retrievalExecutor, calibrationDiagnostics);
                org.mockito.Mockito.verifyNoInteractions(chatModelService);
                return;
            }
            if (candidate4Diagnostic) {
                Map<String, Object> artifact = runCandidate4CalibrationDiagnostic(
                        candidate4Selection,
                        service, contextBuilder, retrievalExecutor, calibrationDiagnostics);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.atLeastOnce())
                        .selectById(RagCandidate4DiagnosticSupport.SELECTION_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(NORMAL_KB_ID);
                org.mockito.Mockito.verify(permissionFilter, org.mockito.Mockito.atLeastOnce())
                        .getAccessibleKnowledgeBaseIds(990001L);
                org.mockito.Mockito.verifyNoInteractions(chatModelService);
                assertEquals(sharedArtifactHashes, sharedLiveArtifactHashes(),
                        "Candidate 4 diagnostic must not mutate shared live artifacts");
                RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(
                        candidate4Paths, candidate4RunHandle, artifact);
                candidate4RunCompleted = true;
                return;
            }
            if (candidate5Diagnostic) {
                Map<String, Object> artifact = runCandidate5CalibrationDiagnostic(
                        candidate5Selection, service, retrievalExecutor,
                        calibrationDiagnostics, queryCounters, embeddingModel, setupJdbc,
                        externalChatCalls);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.atLeastOnce())
                        .selectById(RagCandidate5DiagnosticSupport.SELECTION_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(NORMAL_KB_ID);
                org.mockito.Mockito.verify(permissionFilter, org.mockito.Mockito.atLeastOnce())
                        .getAccessibleKnowledgeBaseIds(990001L);
                org.mockito.Mockito.verifyNoInteractions(chatModelService);
                assertEquals(sharedArtifactHashes, sharedLiveArtifactHashes(),
                        "Candidate 5 diagnostic must not mutate shared live artifacts");
                RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                        candidate5Paths, candidate5RunHandle, artifact);
                candidate5RunCompleted = true;
                return;
            }
            if (candidate6Diagnostic) {
                Map<String, Object> artifact = runCandidate6CalibrationDiagnostic(
                        candidate6Selection, service, fusionService, rerankService,
                        contextBuilder, retrievalExecutor, calibrationDiagnostics,
                        queryCounters, embeddingModel, candidate6KeywordJdbc,
                        externalChatCalls);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.atLeastOnce())
                        .selectById(RagCandidate6DiagnosticSupport.SELECTION_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(NORMAL_KB_ID);
                org.mockito.Mockito.verify(permissionFilter, org.mockito.Mockito.atLeastOnce())
                        .getAccessibleKnowledgeBaseIds(990001L);
                org.mockito.Mockito.verifyNoInteractions(chatClientService, chatModelService);
                assertEquals(sharedArtifactHashes, sharedLiveArtifactHashes(),
                        "Candidate 6 diagnostic must not mutate shared live artifacts");
                RagCandidate6DiagnosticSupport.writeDiagnosticAndComplete(
                        candidate6Paths, candidate6RunHandle, artifact);
                candidate6RunCompleted = true;
                return;
            }
            if (candidate8Diagnostic) {
                Map<String, Object> artifact = runCandidate8CalibrationDiagnostic(
                        candidate8Selection, service, fusionService, rerankService,
                        contextBuilder, retrievalExecutor, calibrationDiagnostics,
                        queryCounters, embeddingModel, candidate8KeywordJdbc,
                        externalChatCalls);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.atLeastOnce())
                        .selectById(RagCandidate8DiagnosticSupport.SELECTION_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(NORMAL_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(RagCandidate8DiagnosticSupport.HOLDOUT_KB_ID);
                org.mockito.Mockito.verify(permissionFilter, org.mockito.Mockito.atLeastOnce())
                        .getAccessibleKnowledgeBaseIds(990001L);
                org.mockito.Mockito.verifyNoInteractions(chatClientService, chatModelService);
                assertEquals(sharedArtifactHashes, sharedLiveArtifactHashes(),
                        "Candidate 8 diagnostic must not mutate shared live artifacts");
                RagCandidate8DiagnosticSupport.writeDiagnosticAndComplete(
                        candidate8Paths, candidate8RunHandle, artifact);
                candidate8RunCompleted = true;
                return;
            }
            if (candidate9Diagnostic) {
                Map<String, Object> artifact = runCandidate9CalibrationDiagnostic(
                        candidate9Paths, candidate9RunHandle, candidate9Selection,
                        candidate91RunHandle, candidate91Binding,
                        service, rerankService, contextBuilder,
                        retrievalExecutor, calibrationDiagnostics, queryCounters,
                        embeddingModel, externalChatCalls,
                        externalEmbeddingBatchCalls);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.atLeastOnce())
                        .selectById(RagCandidate9DiagnosticSupport.SELECTION_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(NORMAL_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(RagCandidate9DiagnosticSupport.HOLDOUT_KB_ID);
                org.mockito.Mockito.verify(permissionFilter, org.mockito.Mockito.atLeastOnce())
                        .getAccessibleKnowledgeBaseIds(990001L);
                org.mockito.Mockito.verifyNoInteractions(chatClientService, chatModelService);
                assertEquals(sharedArtifactHashes, sharedLiveArtifactHashes(),
                        "Candidate 9 diagnostic must not mutate shared live artifacts");
                if (candidate9Recovery) {
                    RagCandidate91RecoverySupport.requireSameContainer(
                            candidate91Preflight, container);
                    RagCandidate91RecoverySupport.publishRecoveryDiagnosticAndComplete(
                            candidate91RunHandle, candidate91Binding,
                            candidate9Selection, artifact);
                } else {
                    RagCandidate9DiagnosticSupport.writeDiagnosticAndComplete(
                            candidate9Paths, candidate9RunHandle,
                            candidate9Selection, artifact);
                }
                candidate9RunCompleted = true;
                return;
            }
            if (candidate5Review) {
                runCandidate5HoldoutArm(
                        candidate5Holdout, candidate5ReviewArm, service, retrievalExecutor,
                        queryCounters,
                        embeddingModel, vectorStoreQueryAttempted, sentinelEvidence,
                        externalChatCalls);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.atLeastOnce())
                        .selectById(RagCandidate5DiagnosticSupport.HOLDOUT_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(NORMAL_KB_ID);
                org.mockito.Mockito.verify(kbMapper, org.mockito.Mockito.never())
                        .selectById(RagCandidate5DiagnosticSupport.SELECTION_KB_ID);
                org.mockito.Mockito.verify(permissionFilter, org.mockito.Mockito.atLeastOnce())
                        .getAccessibleKnowledgeBaseIds(990001L);
                org.mockito.Mockito.verifyNoInteractions(chatClientService, chatModelService);
                return;
            }

            service.retrieve(NORMAL_KB_ID, WARMUP_QUERY, WARMUP_QUERY, 10, false);
            queryCounters.reset();
            int queryInputStart = embeddingModel.recordedInputs().size();

            RagEvaluationDataset.QueryCase answerable = query(dataset, "q005-en");
            RagEvaluationDataset.QueryCase followUp = query(dataset, "q006-en");
            RagEvaluationDataset.QueryCase unanswerable = query(dataset, "q001-en");

            Map<String, RagResult> results = new LinkedHashMap<>();
            List<RetrievalInvocation> retrievalInvocations = new ArrayList<>();
            List<Map<String, Object>> dbCallTrace = new ArrayList<>();
            long retrievalStartNanos = System.nanoTime();
            for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
                retrievalInvocations.add(new RetrievalInvocation(
                        KNOWLEDGE_BASE_ID, queryCase.query(), queryCase.retrievalQuery(), 10, false));
                DbCallSnapshot before = queryCounters.snapshot();
                RagResult result = service.retrieve(KNOWLEDGE_BASE_ID,
                        queryCase.query(), queryCase.retrievalQuery(), 10, false);
                DbCallSnapshot delta = queryCounters.snapshot().minus(before);
                dbCallTrace.add(dbCallTraceRecord(queryCase, result, delta));
                assertEquals(0L, delta.contextOther(),
                        () -> "unclassified context query for " + queryCase.id());
                assertEquals(0L, delta.other(),
                        () -> "unexpected OTHER query for " + queryCase.id());
                results.put(queryCase.id(), result);
            }
            long retrievalLatencyMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - retrievalStartNanos);
            DbCallSnapshot observedDbCalls = queryCounters.snapshot();
            assertEquals(0L, observedDbCalls.contextOther(), "unclassified context queries");
            assertEquals(0L, observedDbCalls.other(), "unexpected OTHER queries");
            assertEquals(96, dbCallTrace.size());
            long tracedDbCalls = dbCallTrace.stream()
                    .mapToLong(trace -> ((Number) trace.get("total")).longValue())
                    .sum();
            assertEquals(observedDbCalls.total(), tracedDbCalls);
            RagResult answer = results.get(answerable.id());
            RagResult followUpResult = results.get(followUp.id());
            RagResult noAnswer = results.get(unanswerable.id());

            assertTrue(answer.getSources().stream().anyMatch(result -> result.getSegmentId() == 100005L));
            assertTrue(followUpResult.getSources().stream().anyMatch(result -> result.getSegmentId() == 100006L));
            assertNotNull(noAnswer.getSources());
            assertNotNull(noAnswer.getContext());
            assertNoSentinelLeakage(results);
            List<String> queryInputs = embeddingModel.recordedInputs().subList(
                    queryInputStart, embeddingModel.recordedInputs().size());
            assertTrue(queryInputs.contains(answerable.retrievalQuery()));
            assertTrue(queryInputs.contains(followUp.retrievalQuery()));
            assertTrue(queryInputs.contains(unanswerable.retrievalQuery()));
            org.mockito.Mockito.verify(queryIntentAnalyzer, org.mockito.Mockito.atLeastOnce())
                    .analyze(answerable.query());
            org.mockito.Mockito.verify(queryIntentAnalyzer, org.mockito.Mockito.atLeastOnce())
                    .analyze(followUp.query());
            org.mockito.Mockito.verify(queryIntentAnalyzer, org.mockito.Mockito.atLeastOnce())
                    .analyze(unanswerable.query());
            List<String> forbiddenInputs = new ArrayList<>();
            for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
                forbiddenInputs.add(queryCase.id());
                if (queryCase.referenceAnswer() != null) {
                    forbiddenInputs.add(queryCase.referenceAnswer());
                }
                forbiddenInputs.addAll(queryCase.referenceClaims());
                forbiddenInputs.addAll(dataset.qrelsFor(queryCase.id()).keySet());
            }
            forbiddenInputs.add("CANARY-REF-20260715");
            for (String forbidden : forbiddenInputs) {
                assertTrue(queryInputs.stream().noneMatch(input -> input.contains(forbidden)),
                        () -> "evaluation-only value leaked into retrieval embedding: " + forbidden);
            }
            assertCanaryBoundary(answerable, answer, retrievalInvocations, queryInputs);
            assertFalse(vectorStoreQueryAttempted.get(), "shadow must not access vector_store");
            Map<String, Object> config = shadowConfig();
            RagBenchmarkReport report = RagShadowReportSupport.buildReport(dataset, results, config);
            assertStableSnapshot(report);
            writeLiveArtifacts(dataset, results);
            Map<String, Object> observedBudget = new LinkedHashMap<>();
            observedBudget.put("latencyMs", retrievalLatencyMs);
            observedBudget.put("dbCalls", observedDbCalls.total());
            observedBudget.put("embeddingCalls", queryInputs.size());
            observedBudget.put("tokens", 0);
            observedBudget.put("costUsd", 0.0D);
            writeRuntimeReport(report, config, sentinelEvidence,
                    RagShadowReportSupport.familyScores(dataset, results), observedBudget, dbCallTrace);
            org.mockito.Mockito.verifyNoInteractions(chatModelService);
        } catch (Exception | Error failure) {
            if (candidate9Recovery && candidate91Archive != null
                    && candidate91Preflight == null
                    && candidate91PreflightStarted
                    && candidate91Paths != null
                    && !Files.exists(candidate91Paths.preflightFailure())) {
                try {
                    RagCandidate91RecoverySupport.publishPreflightFailure(
                            candidate91Archive,
                            "CANDIDATE91_DOCKER_PREFLIGHT_FAILED");
                } catch (RuntimeException completionFailure) {
                    failure.addSuppressed(completionFailure);
                }
            }
            if (candidate4Diagnostic && candidate4RunHandle != null
                    && !candidate4RunCompleted) {
                try {
                    RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(
                            candidate4Paths,
                            candidate4RunHandle,
                            candidate4InvalidDiagnosticRoot(candidate4Selection, failure));
                    candidate4RunCompleted = true;
                } catch (RuntimeException completionFailure) {
                    failure.addSuppressed(completionFailure);
                }
            }
            if (candidate5Diagnostic && candidate5RunHandle != null
                    && !candidate5RunCompleted) {
                try {
                    RagCandidate5DiagnosticSupport.writeDiagnosticAndComplete(
                            candidate5Paths,
                            candidate5RunHandle,
                            candidate5InvalidDiagnosticRoot(candidate5Selection, failure));
                    candidate5RunCompleted = true;
                } catch (RuntimeException completionFailure) {
                    failure.addSuppressed(completionFailure);
                }
            }
            if (candidate6Diagnostic && candidate6RunHandle != null
                    && !candidate6RunCompleted) {
                try {
                    RagCandidate6DiagnosticSupport.writeDiagnosticAndComplete(
                            candidate6Paths,
                            candidate6RunHandle,
                            candidate6InvalidDiagnosticRoot(candidate6Selection, failure));
                    candidate6RunCompleted = true;
                } catch (RuntimeException completionFailure) {
                    failure.addSuppressed(completionFailure);
                }
            }
            if (candidate8Diagnostic && candidate8RunHandle != null
                    && !candidate8RunCompleted) {
                try {
                    RagCandidate8DiagnosticSupport.writeDiagnosticAndComplete(
                            candidate8Paths,
                            candidate8RunHandle,
                            candidate8InvalidDiagnosticRoot(candidate8Selection, failure));
                    candidate8RunCompleted = true;
                } catch (RuntimeException completionFailure) {
                    failure.addSuppressed(completionFailure);
                }
            }
            if (candidate9Recovery && candidate91RunHandle != null
                    && !candidate9RunCompleted) {
                try {
                    RagCandidate91RecoverySupport.publishRuntimeInvalidAndComplete(
                            candidate91RunHandle,
                            candidate9RecoveryErrorCode(failure));
                    candidate9RunCompleted = true;
                } catch (RuntimeException completionFailure) {
                    failure.addSuppressed(completionFailure);
                }
            }
            if (candidate9Diagnostic && !candidate9Recovery
                    && candidate9RunHandle != null
                    && !candidate9RunCompleted) {
                try {
                    RagCandidate9DiagnosticSupport.writeDiagnosticAndComplete(
                            candidate9Paths,
                            candidate9RunHandle,
                            candidate9Selection,
                            candidate9InvalidDiagnosticRoot(candidate9Selection, failure));
                    candidate9RunCompleted = true;
                } catch (RuntimeException completionFailure) {
                    failure.addSuppressed(completionFailure);
                }
            }
            throw failure;
        } finally {
            if (retrievalExecutor != null) {
                retrievalExecutor.shutdown();
            }
            if (sentinelExecutor != null) {
                sentinelExecutor.shutdownNow();
            }
            SecurityContextHolder.clearContext();
            if (container != null && container.isRunning()) {
                container.stop();
            }
            TimeZone.setDefault(previousTimeZone);
            Locale.setDefault(previousLocale);
            if (candidate2Diagnostic || candidate3Diagnostic || candidate4Diagnostic
                    || candidate5Diagnostic || candidate6Diagnostic
                    || candidate8Diagnostic || candidate9Diagnostic) {
                assertEquals(sharedArtifactHashes, sharedLiveArtifactHashes(),
                        "calibration diagnostics must not mutate shared live artifacts");
            }
        }
    }

    private static void runIdentifierLatencyDiagnostic(
            RagEvaluationDataset dataset,
            RagRetrievalService service,
            KeywordRetriever baselineKeywordRetriever,
            KeywordRetriever candidateKeywordRetriever,
            ThreadPoolTaskExecutor executor,
            QueryCounters queryCounters,
            FeatureHashEmbeddingModel embeddingModel,
            IdentifierLatencyDiagnostics diagnostics) throws Exception {
        assertNotNull(candidateKeywordRetriever);
        Map<String, KeywordRetriever> retrievers = Map.of(
                "A", baselineKeywordRetriever, "C", candidateKeywordRetriever);
        List<DiagnosticLoop> loops = new ArrayList<>();

        for (int round = 1; round <= IDENTIFIER_DIAGNOSTIC_ROUNDS; round++) {
            List<String> armOrder = round % 2 == 1 ? List.of("A", "C") : List.of("C", "A");
            for (String arm : armOrder) {
                awaitExecutorIdle(executor);
                ReflectionTestUtils.setField(service, "keywordRetriever", retrievers.get(arm));

                DiagnosticSample warmup = new DiagnosticSample(round, -1, arm, "__warmup__");
                diagnostics.current.set(warmup);
                service.retrieve(NORMAL_KB_ID, WARMUP_QUERY, WARMUP_QUERY, 10, false);
                awaitExecutorIdle(executor);
                diagnostics.current.compareAndSet(warmup, null);
                assertDiagnosticHealthy(warmup);

                queryCounters.reset();
                int loopEmbeddingStart = embeddingModel.recordedInputs().size();
                long loopStart = System.nanoTime();
                int ordinal = 0;
                for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
                    DiagnosticSample sample = new DiagnosticSample(round, ordinal++, arm, queryCase.id());
                    DbCallSnapshot before = queryCounters.snapshot();
                    int embeddingStart = embeddingModel.recordedInputs().size();
                    diagnostics.current.set(sample);
                    long totalStart = System.nanoTime();
                    RagResult result;
                    try {
                        result = service.retrieve(NORMAL_KB_ID,
                                queryCase.query(), queryCase.retrievalQuery(), 10, false);
                    } catch (RuntimeException | Error failure) {
                        sample.retrievalFailureClass.compareAndSet(null,
                                failure.getClass().getSimpleName());
                        writeInvalidIdentifierDiagnostic(
                                "IDENTIFIER_DIAGNOSTIC_RETRIEVAL_FAILURE", sample);
                        throw failure;
                    } finally {
                        sample.totalNs.set(System.nanoTime() - totalStart);
                        awaitExecutorIdle(executor);
                        diagnostics.current.compareAndSet(sample, null);
                    }

                    DbCallSnapshot delta = queryCounters.snapshot().minus(before);
                    sample.dbCalls = delta.total();
                    sample.embeddingCalls = embeddingModel.recordedInputs().size() - embeddingStart;
                    sample.sourceCount = result.getSources() == null ? 0 : result.getSources().size();
                    assertEquals(0L, delta.contextOther(), queryCase.id());
                    assertEquals(0L, delta.other(), queryCase.id());
                    assertEquals(delta.keyword(), sample.keywordJdbcCalls.get(), queryCase.id());
                    assertEquals(sample.keywordJdbcCalls.get(), sample.fullPathVariants.size(), queryCase.id());
                    assertDiagnosticHealthy(sample);
                    if ("C".equals(arm) && IDENTIFIER_TARGET_SOURCES.containsKey(queryCase.id())) {
                        long expected = IDENTIFIER_TARGET_SOURCES.get(queryCase.id());
                        assertTrue(result.getSources().stream()
                                .anyMatch(source -> source.getSegmentId() == expected), queryCase.id());
                    }
                    diagnostics.samples.add(sample);
                }
                long loopTotalNs = System.nanoTime() - loopStart;
                DbCallSnapshot loopCalls = queryCounters.snapshot();
                loops.add(new DiagnosticLoop(round, arm, loopTotalNs, loopCalls.total(),
                        embeddingModel.recordedInputs().size() - loopEmbeddingStart));
            }
        }

        measureDetachedTermExtraction(diagnostics.samples);
        writeIdentifierLatencyDiagnostic(diagnostics.samples, loops);
    }

    private static void runCandidate2CalibrationDiagnostic(
            RagEvaluationDataset dataset,
            RagRetrievalService service,
            ThreadPoolTaskExecutor executor,
            CalibrationStageDiagnostics diagnostics) throws Exception {
        List<RagEvaluationDataset.QueryCase> calibrationCases = dataset.queries().stream()
                .filter(query -> "calibration".equals(query.split()))
                .toList();
        assertEquals(24, calibrationCases.size());
        assertEquals(16, calibrationCases.stream().filter(RagEvaluationDataset.QueryCase::answerable).count());

        service.retrieve(NORMAL_KB_ID, WARMUP_QUERY, WARMUP_QUERY, 10, false);
        awaitExecutorIdle(executor);
        for (RagEvaluationDataset.QueryCase queryCase : calibrationCases) {
            CalibrationTrace trace = new CalibrationTrace(
                    queryCase.id(), queryCase.familyId(), queryCase.split());
            diagnostics.current.set(trace);
            try {
                RagResult result = service.retrieve(NORMAL_KB_ID,
                        queryCase.query(), queryCase.retrievalQuery(), 10, false);
                trace.finalRanking = snapshotResults(result.getSources());
                trace.contextRanking = contextRanking(result.getContext());
                trace.finalContextSha256 = sha256Utf8(result.getContext());
                trace.finalContextEmpty = result.getContext() == null || result.getContext().isEmpty();
                if (!trace.rerankOutput.isEmpty()) {
                    assertEquals(segmentIds(trace.rerankOutput), segmentIds(trace.finalRanking), queryCase.id());
                }
            } catch (RuntimeException | Error failure) {
                writeInvalidCandidate2Diagnostic(
                        "CANDIDATE2_CALIBRATION_RETRIEVAL_FAILURE",
                        queryCase.id(), failure.getClass().getSimpleName());
                throw failure;
            } finally {
                awaitExecutorIdle(executor);
                diagnostics.current.compareAndSet(trace, null);
            }
            diagnostics.traces.add(trace);
        }

        List<CalibrationEvidence> evidence = calibrationCases.stream()
                .filter(RagEvaluationDataset.QueryCase::answerable)
                .map(query -> classifyCalibrationCase(
                        diagnostics.trace(query.id()), dataset.qrelsFor(query.id())))
                .toList();
        writeCandidate2CalibrationDiagnostic(calibrationCases.size(), evidence);
    }

    private static void runCandidate3CalibrationDiagnostic(
            RagEvaluationDataset dataset,
            RagRetrievalService service,
            RagContextBuilder contextBuilder,
            ThreadPoolTaskExecutor executor,
            CalibrationStageDiagnostics diagnostics) throws Exception {
        List<RagEvaluationDataset.QueryCase> calibrationCases = dataset.queries().stream()
                .filter(query -> "calibration".equals(query.split()))
                .toList();
        assertEquals(24, calibrationCases.size());
        assertEquals(16, calibrationCases.stream()
                .filter(RagEvaluationDataset.QueryCase::answerable).count());

        service.retrieve(NORMAL_KB_ID, WARMUP_QUERY, WARMUP_QUERY, 10, false);
        awaitExecutorIdle(executor);
        List<Candidate3Evidence> evidence = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase queryCase : calibrationCases) {
            CalibrationTrace trace = new CalibrationTrace(
                    queryCase.id(), queryCase.familyId(), queryCase.split());
            diagnostics.current.set(trace);
            try {
                RagResult result = service.retrieve(NORMAL_KB_ID,
                        queryCase.query(), queryCase.retrievalQuery(), 10, false);
                trace.finalRanking = snapshotResults(result.getSources());
                trace.contextRanking = contextRanking(result.getContext());
                trace.finalContextSha256 = sha256Utf8(result.getContext());
                trace.finalContextEmpty = result.getContext() == null || result.getContext().isEmpty();
                if (!trace.rerankOutput.isEmpty()) {
                    assertEquals(segmentIds(trace.rerankOutput),
                            segmentIds(trace.finalRanking), queryCase.id());
                }
                evidence.add(candidate3Evidence(
                        queryCase, dataset.qrelsFor(queryCase.id()), trace, contextBuilder));
            } catch (Exception | Error failure) {
                writeInvalidCandidate3Diagnostic(
                        "CANDIDATE3_CALIBRATION_RETRIEVAL_FAILURE",
                        queryCase.id(), failure.getClass().getSimpleName());
                throw failure;
            } finally {
                awaitExecutorIdle(executor);
                diagnostics.current.compareAndSet(trace, null);
            }
            diagnostics.traces.add(trace);
        }
        writeCandidate3CalibrationDiagnostic(dataset, evidence);
    }

    private static Map<String, Object> runCandidate4CalibrationDiagnostic(
            RagCandidate4DiagnosticSupport.FrozenSelection frozenSelection,
            RagRetrievalService service,
            RagContextBuilder contextBuilder,
            ThreadPoolTaskExecutor executor,
            CalibrationStageDiagnostics diagnostics) throws Exception {
        RagEvaluationDataset dataset = frozenSelection.dataset();
        assertEquals(16, dataset.queries().size());
        assertEquals(14, dataset.queries().stream()
                .filter(RagEvaluationDataset.QueryCase::answerable).count());

        service.retrieve(RagCandidate4DiagnosticSupport.SELECTION_KB_ID,
                WARMUP_QUERY, WARMUP_QUERY, 10, false);
        awaitExecutorIdle(executor);
        List<RagCandidate4DiagnosticSupport.CaseEvidence> evidence = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
            CalibrationTrace trace = new CalibrationTrace(
                    queryCase.id(), queryCase.familyId(), queryCase.split());
            diagnostics.current.set(trace);
            try {
                RagResult result = service.retrieve(
                        RagCandidate4DiagnosticSupport.SELECTION_KB_ID,
                        queryCase.query(), queryCase.retrievalQuery(), 10, false);
                trace.finalRanking = snapshotResults(result.getSources());
                trace.contextRanking = contextRanking(result.getContext());
                trace.finalContextSha256 = sha256Utf8(result.getContext());
                trace.finalContextEmpty = result.getContext() == null || result.getContext().isEmpty();
                assertCandidate4ContextIsolation(queryCase.id(), result, trace.contextRanking);
                evidence.add(candidate4Evidence(
                        queryCase, dataset, trace, contextBuilder));
            } finally {
                awaitExecutorIdle(executor);
                diagnostics.current.compareAndSet(trace, null);
            }
            diagnostics.traces.add(trace);
        }
        String decision = RagCandidate4DiagnosticSupport.decide(evidence);
        return candidate4DiagnosticRoot(
                frozenSelection, evidence, "VALID", decision, null);
    }

    private static RagCandidate4DiagnosticSupport.CaseEvidence candidate4Evidence(
            RagEvaluationDataset.QueryCase queryCase,
            RagEvaluationDataset dataset,
            CalibrationTrace trace,
            RagContextBuilder contextBuilder) throws Exception {
        List<String> identifiers = candidate3IdentifierTerms(trace.deterministicQuery);
        List<Pattern> identifierPatterns = identifiers.stream()
                .map(identifier -> Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(identifier)
                                + "(?![\\p{L}\\p{N}])"))
                .toList();
        Map<String, Integer> qrels = dataset.qrelsFor(queryCase.id());
        Set<String> exactRelevantIds = qrels.keySet().stream()
                .filter(segmentId -> {
                    RagEvaluationDataset.CorpusSegment segment =
                            dataset.corpusById().get(segmentId);
                    return segment != null && identifierPatterns.stream().anyMatch(
                            pattern -> pattern.matcher(segment.documentId()).find());
                })
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<String> exactIdentifierCandidateIds = trace.candidatesBySegmentId.entrySet().stream()
                .filter(entry -> candidate3IdentifierMatch(entry.getValue(), identifierPatterns))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<RetrievalResult> counterfactualResults = copyResultsFromRanking(
                trace.finalRanking, trace.candidatesBySegmentId);
        List<RetrievalResult> counterfactualInput = buildCandidate4CounterfactualInput(
                segmentIds(trace.colbertRanking),
                segmentIds(trace.fullColbertRanking),
                trace.candidatesBySegmentId,
                exactIdentifierCandidateIds);
        boolean counterfactualApplied = counterfactualInput.size() > trace.colbertRanking.size();
        if (counterfactualApplied) {
            DeterministicRerankerProvider detachedDeterministic =
                    new DeterministicRerankerProvider();
            RerankRequestContext request = RerankRequestContext.builder()
                    .query(trace.deterministicQuery)
                    .build();
            List<RetrievalResult> deterministicFull = detachedDeterministic.rerank(
                    request, counterfactualInput, trace.queryIntent, counterfactualInput.size());
            List<Double> rankScores = deterministicFull.stream()
                    .map(RetrievalResult::getScore).toList();
            List<RetrievalResult> matches = deterministicFull.stream()
                    .filter(result -> candidate3IdentifierMatch(result, identifierPatterns))
                    .toList();
            List<RetrievalResult> nonMatches = deterministicFull.stream()
                    .filter(result -> !candidate3IdentifierMatch(result, identifierPatterns))
                    .toList();
            List<RetrievalResult> finalOrder = new ArrayList<>(deterministicFull.size());
            finalOrder.addAll(matches);
            finalOrder.addAll(nonMatches);
            for (int index = 0; index < finalOrder.size(); index++) {
                finalOrder.get(index).setScore(rankScores.get(index));
            }
            counterfactualResults = copyResults(
                    finalOrder.subList(0, Math.min(10, finalOrder.size())));
        }
        List<RankedSegment> counterfactualRanking = snapshotResults(counterfactualResults);
        String counterfactualContext = counterfactualApplied
                ? contextBuilder.buildContext(copyResults(counterfactualResults), true)
                : null;
        if (counterfactualApplied) {
            assertCandidate4ContextRankingIsolation(
                    queryCase.id(), contextRanking(counterfactualContext),
                    "counterfactual context");
        }
        String counterfactualContextSha256 = counterfactualApplied
                ? sha256Utf8(counterfactualContext)
                : trace.finalContextSha256;
        boolean counterfactualContextEmpty = counterfactualApplied
                ? counterfactualContext == null || counterfactualContext.isEmpty()
                : trace.finalContextEmpty;

        return RagCandidate4DiagnosticSupport.classify(
                new RagCandidate4DiagnosticSupport.CaseInput(
                        queryCase.id(),
                        queryCase.familyId(),
                        queryCase.split(),
                        queryCase.familyId().startsWith("c4s-t"),
                        candidate4Paths(trace.paths),
                        candidate4Ranking(trace.fusedRanking),
                        candidate4Ranking(trace.filterRanking),
                        candidate4Ranking(trace.colbertRanking),
                        candidate4Ranking(trace.fullColbertRanking),
                        candidate4Ranking(trace.deterministicInputRanking),
                        candidate4Ranking(trace.deterministicActualRanking),
                        candidate4Ranking(trace.finalRanking),
                        candidate4Ranking(counterfactualRanking),
                        qrels,
                        exactRelevantIds,
                        trace.finalContextSha256,
                        counterfactualContextSha256,
                        trace.finalContextEmpty,
                        counterfactualContextEmpty));
    }

    private static Map<String, Object> candidate4DiagnosticRoot(
            RagCandidate4DiagnosticSupport.FrozenSelection frozenSelection,
            List<RagCandidate4DiagnosticSupport.CaseEvidence> evidence,
            String status,
            String decision,
            String errorCode) {
        Map<String, Object> config = candidate4Config();
        Map<String, Long> classifications = evidence.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        item -> item.classification().name(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseCount", evidence.size());
        summary.put("targetCaseCount", evidence.stream().filter(
                RagCandidate4DiagnosticSupport.CaseEvidence::target).count());
        summary.put("controlCaseCount", evidence.stream().filter(
                item -> !item.target()).count());
        summary.put("classifications", classifications);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("status", status);
        artifact.put("decision", decision);
        artifact.put("errorCode", errorCode);
        artifact.put("datasetHash", frozenSelection.datasetHash());
        artifact.put("selectionManifestHash",
                frozenSelection.manifests().selectionSha256());
        artifact.put("holdoutManifestHash",
                frozenSelection.manifests().holdoutSha256());
        artifact.put("holdoutFreezeStatus",
                frozenSelection.manifests().holdout().freezeStatus());
        artifact.put("auditedReads", frozenSelection.auditedReads());
        artifact.put("config", config);
        artifact.put("configHash", ShadowContractSupport.configHash(config));
        artifact.put("summary", summary);
        artifact.put("cases", evidence.stream()
                .map(RagCandidate4DiagnosticSupport.CaseEvidence::toMap).toList());
        return artifact;
    }

    private static Map<String, Object> candidate4InvalidDiagnosticRoot(
            RagCandidate4DiagnosticSupport.FrozenSelection frozenSelection,
            Throwable failure) {
        Map<String, Object> config = candidate4Config();
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("status", "INVALID");
        artifact.put("decision", "STOP_IDENTIFIER_ANCHOR_UNSUPPORTED");
        artifact.put("errorCode", "CANDIDATE4_SELECTION_RUN_FAILED");
        artifact.put("exceptionClass", failure.getClass().getSimpleName());
        artifact.put("datasetHash", frozenSelection.datasetHash());
        artifact.put("selectionManifestHash",
                frozenSelection.manifests().selectionSha256());
        artifact.put("holdoutManifestHash",
                frozenSelection.manifests().holdoutSha256());
        artifact.put("holdoutFreezeStatus",
                frozenSelection.manifests().holdout().freezeStatus());
        artifact.put("auditedReads", frozenSelection.auditedReads());
        artifact.put("config", config);
        artifact.put("configHash", ShadowContractSupport.configHash(config));
        return artifact;
    }

    private static Map<String, Object> candidate4Config() {
        Map<String, Object> config = new LinkedHashMap<>(shadowConfig());
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", true);
        config.put("candidate4EvidenceAlgorithm", "colbert-identifier-suppression-v1");
        return config;
    }

    private static Map<String, List<RagCandidate4DiagnosticSupport.RankedSegment>>
    candidate4Paths(Map<String, List<RankedSegment>> paths) {
        Map<String, List<RagCandidate4DiagnosticSupport.RankedSegment>> converted =
                new LinkedHashMap<>();
        paths.forEach((name, ranking) -> converted.put(name, candidate4Ranking(ranking)));
        return converted;
    }

    private static List<RagCandidate4DiagnosticSupport.RankedSegment> candidate4Ranking(
            List<RankedSegment> ranking) {
        return ranking.stream().map(item ->
                new RagCandidate4DiagnosticSupport.RankedSegment(
                        item.segmentId(), item.rank(), item.score())).toList();
    }

    private static List<RetrievalResult> copyResultsFromRanking(
            List<RankedSegment> ranking,
            Map<String, RetrievalResult> candidatesBySegmentId) {
        List<RetrievalResult> results = new ArrayList<>();
        for (RankedSegment ranked : ranking) {
            RetrievalResult source = candidatesBySegmentId.get(ranked.segmentId());
            if (source == null) {
                continue;
            }
            RetrievalResult copy = copyResults(List.of(source)).get(0);
            copy.setScore(ranked.score());
            results.add(copy);
        }
        return results;
    }

    static List<RetrievalResult> buildCandidate4CounterfactualInput(
            List<String> businessColbertOrder,
            List<String> fullColbertOrder,
            Map<String, RetrievalResult> candidatesBySegmentId,
            Set<String> identifierMatchIds) {
        List<RetrievalResult> results = new ArrayList<>();
        Set<String> included = new LinkedHashSet<>();
        for (String segmentId : businessColbertOrder) {
            RetrievalResult source = candidatesBySegmentId.get(segmentId);
            if (source == null) {
                throw new IllegalStateException(
                        "CANDIDATE4_COLBERT_CANDIDATE_SNAPSHOT_MISSING");
            }
            results.add(copyResults(List.of(source)).get(0));
            included.add(segmentId);
        }
        for (String segmentId : fullColbertOrder) {
            if (!identifierMatchIds.contains(segmentId) || !included.add(segmentId)) {
                continue;
            }
            RetrievalResult source = candidatesBySegmentId.get(segmentId);
            if (source == null) {
                throw new IllegalStateException(
                        "CANDIDATE4_COLBERT_CANDIDATE_SNAPSHOT_MISSING");
            }
            results.add(copyResults(List.of(source)).get(0));
        }
        return results;
    }

    private static boolean containsSegment(
            List<RankedSegment> ranking, Set<String> segmentIds) {
        return ranking.stream().map(RankedSegment::segmentId).anyMatch(segmentIds::contains);
    }

    private static void assertCandidate4ContextIsolation(
            String queryId, RagResult result, List<RankedSegment> contextRanking) {
        for (RetrievalResult source : result.getSources()) {
            assertTrue(source.getSegmentId() != null
                            && source.getSegmentId()
                            >= RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                            && source.getSegmentId()
                            <= RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MAX,
                    () -> queryId + ": source outside Selection KB");
            assertTrue(source.getDocumentId() != null
                            && source.getDocumentId()
                            >= RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                            && source.getDocumentId()
                            <= RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                    () -> queryId + ": source document outside Selection KB");
        }
        assertCandidate4ContextRankingIsolation(queryId, contextRanking, "context");
    }

    private static void assertCandidate4ContextRankingIsolation(
            String queryId, List<RankedSegment> contextRanking, String stage) {
        assertTrue(contextRanking.stream().allMatch(item -> {
            long segmentId = Long.parseLong(item.segmentId());
            return segmentId >= RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                    && segmentId <= RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MAX;
        }), queryId + ": " + stage + " contains non-Selection segment");
    }

    private static void runCandidate5HoldoutArm(
            RagCandidate5DiagnosticSupport.FrozenDataset frozen,
            RagCandidate5ReviewSupport.Arm arm,
            RagRetrievalService service,
            ThreadPoolTaskExecutor retrievalExecutor,
            QueryCounters queryCounters,
            FeatureHashEmbeddingModel embeddingModel,
            AtomicBoolean vectorStoreQueryAttempted,
            List<String> sentinelEvidence,
            AtomicLong externalChatCalls) throws IOException {
        RagEvaluationDataset dataset = frozen.dataset();
        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT, dataset.queries().size());
        assertEquals(frozen.manifests().holdout().datasetHash(), frozen.datasetHash());

        service.retrieve(RagCandidate5DiagnosticSupport.HOLDOUT_KB_ID,
                WARMUP_QUERY, WARMUP_QUERY, 10, false);
        awaitExecutorIdle(retrievalExecutor);
        queryCounters.reset();
        externalChatCalls.set(0L);
        int queryInputStart = embeddingModel.recordedInputs().size();

        Map<String, RagResult> results = new LinkedHashMap<>();
        List<Map<String, Object>> dbCallTrace = new ArrayList<>();
        long retrievalStartNanos = System.nanoTime();
        for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
            DbCallSnapshot before = queryCounters.snapshot();
            RagResult result = service.retrieve(
                    RagCandidate5DiagnosticSupport.HOLDOUT_KB_ID,
                    queryCase.query(), queryCase.retrievalQuery(), 10, false);
            DbCallSnapshot delta = queryCounters.snapshot().minus(before);
            assertEquals(0L, delta.contextOther(),
                    () -> "unclassified Candidate 5 context query for " + queryCase.id());
            assertEquals(0L, delta.other(),
                    () -> "unexpected Candidate 5 OTHER query for " + queryCase.id());
            dbCallTrace.add(candidate5DbCallTraceRecord(queryCase, result, delta, arm));
            assertNull(results.put(queryCase.id(), result),
                    () -> "duplicate Candidate 5 query: " + queryCase.id());
        }
        awaitExecutorIdle(retrievalExecutor);
        long retrievalLatencyMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - retrievalStartNanos);
        DbCallSnapshot observedDbCalls = queryCounters.snapshot();
        assertEquals(0L, observedDbCalls.contextOther(),
                "unclassified Candidate 5 context queries");
        assertEquals(0L, observedDbCalls.other(), "unexpected Candidate 5 OTHER queries");
        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT, dbCallTrace.size());
        assertEquals(observedDbCalls.total(), dbCallTrace.stream()
                .mapToLong(trace -> ((Number) trace.get("total")).longValue())
                .sum());

        List<String> queryInputs = embeddingModel.recordedInputs().subList(
                queryInputStart, embeddingModel.recordedInputs().size());
        assertEquals(RagCandidate5ReviewSupport.CASE_COUNT, queryInputs.size(),
                "Candidate 5 must use one embedding per case");
        for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
            List<String> forbidden = new ArrayList<>();
            forbidden.add(queryCase.id());
            if (queryCase.referenceAnswer() != null) {
                forbidden.add(queryCase.referenceAnswer());
            }
            forbidden.addAll(queryCase.referenceClaims());
            forbidden.addAll(dataset.qrelsFor(queryCase.id()).keySet());
            for (String value : forbidden) {
                assertTrue(queryInputs.stream().noneMatch(input -> input.contains(value)),
                        () -> "Candidate 5 evaluation-only value leaked into retrieval: "
                                + queryCase.id());
            }
        }
        assertNoSentinelLeakage(results);
        assertFalse(vectorStoreQueryAttempted.get(), "Candidate 5 must not access vector_store");
        assertEquals(0L, externalChatCalls.get(), "Candidate 5 must not call external chat");

        Map<String, Object> config = candidate5ReviewConfig(arm);
        RagBenchmarkReport report = RagCandidate5ReviewSupport.buildReport(
                frozen, results, config);
        Map<String, Map<String, List<Double>>> familyScores =
                RagCandidate5ReviewSupport.familyScores(dataset, results);
        Map<String, Object> observedBudget = new LinkedHashMap<>();
        observedBudget.put("latencyMs", retrievalLatencyMs);
        observedBudget.put("dbCalls", observedDbCalls.total());
        observedBudget.put("embeddingCalls", queryInputs.size());
        observedBudget.put("tokens", 0L);
        observedBudget.put("costUsd", 0.0D);
        RagCandidate5ReviewSupport.writeCompleteArm(
                runtimeDirectory(), arm, frozen, results, report, config,
                sentinelEvidence, familyScores, observedBudget, dbCallTrace);
    }

    private static Map<String, Object> candidate5DbCallTraceRecord(
            RagEvaluationDataset.QueryCase query,
            RagResult result,
            DbCallSnapshot delta,
            RagCandidate5ReviewSupport.Arm arm) throws IOException {
        Map<String, Object> trace = dbCallTraceRecord(query, result, delta);
        List<RetrievalResult> sources = result.getSources() == null
                ? List.of() : result.getSources();
        String context = result.getContext() == null ? "" : result.getContext();
        trace.put("identifierRecallConsistencyEnabled", arm.enabled());
        trace.put("sourceSegmentIds", sources.stream()
                .map(RetrievalResult::getSegmentId)
                .map(String::valueOf)
                .toList());
        trace.put("sourceScores", sources.stream()
                .map(RetrievalResult::getScore)
                .toList());
        trace.put("contextSha256", ShadowContractSupport.sha256(
                context.getBytes(StandardCharsets.UTF_8)));
        trace.put("contextEmpty", context.isEmpty());
        return trace;
    }

    private static Map<String, Object> runCandidate5CalibrationDiagnostic(
            RagCandidate5DiagnosticSupport.FrozenDataset frozenSelection,
            RagRetrievalService service,
            ThreadPoolTaskExecutor executor,
            CalibrationStageDiagnostics diagnostics,
            QueryCounters queryCounters,
            FeatureHashEmbeddingModel embeddingModel,
            JdbcTemplate setupJdbc,
            AtomicLong externalChatCalls) throws Exception {
        RagEvaluationDataset dataset = frozenSelection.dataset();
        assertEquals(20, dataset.queries().size());
        externalChatCalls.set(0L);
        service.retrieve(RagCandidate5DiagnosticSupport.SELECTION_KB_ID,
                WARMUP_QUERY, WARMUP_QUERY, 10, false);
        awaitExecutorIdle(executor);
        if (externalChatCalls.get() != 0L) {
            throw new Candidate5DiagnosticFailure(
                    "CANDIDATE5_EXTERNAL_CALL_FAILED");
        }
        queryCounters.reset();

        List<Candidate5CaseArtifact> cases = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
            CalibrationTrace trace = new CalibrationTrace(
                    queryCase.id(), queryCase.familyId(), queryCase.split());
            diagnostics.current.set(trace);
            DbCallSnapshot before = queryCounters.snapshot();
            int embeddingBefore = embeddingModel.recordedInputs().size();
            long externalCallsBefore = externalChatCalls.get();
            try {
                RagResult result = service.retrieve(
                        RagCandidate5DiagnosticSupport.SELECTION_KB_ID,
                        queryCase.query(), queryCase.retrievalQuery(), 10, false);
                awaitExecutorIdle(executor);
                if (trace.sqlFailureClass.get() != null) {
                    throw new Candidate5DiagnosticFailure(
                            "CANDIDATE5_SQL_EXECUTION_FAILED");
                }
                if (trace.selectionKnowledgeBaseMismatch.get()) {
                    throw new Candidate5DiagnosticFailure(
                            "CANDIDATE5_KB_ISOLATION_FAILED");
                }
                DbCallSnapshot delta = queryCounters.snapshot().minus(before);
                int embeddingCalls = embeddingModel.recordedInputs().size() - embeddingBefore;
                long networkCalls = externalChatCalls.get() - externalCallsBefore;
                if (networkCalls != 0L) {
                    throw new Candidate5DiagnosticFailure(
                            "CANDIDATE5_EXTERNAL_CALL_FAILED");
                }
                trace.finalRanking = snapshotResults(result.getSources());
                trace.contextRanking = contextRanking(result.getContext());
                trace.finalContextSha256 = sha256Utf8(result.getContext());
                trace.finalContextEmpty = result.getContext() == null
                        || result.getContext().isEmpty();
                assertCandidate5ContextIsolation(
                        queryCase.id(), result, trace.contextRanking);
                assertCandidate5StageIsolation(queryCase.id(), trace);
                cases.add(candidate5Evidence(
                        queryCase, dataset, trace, delta, embeddingCalls, networkCalls,
                        setupJdbc));
            } finally {
                awaitExecutorIdle(executor);
                diagnostics.current.compareAndSet(trace, null);
                synchronized (trace.fullPathVariants) {
                    trace.fullPathVariants.clear();
                }
            }
        }
        List<RagCandidate5DiagnosticSupport.CaseEvidence> evidence = cases.stream()
                .map(Candidate5CaseArtifact::evidence).toList();
        if (externalChatCalls.get() != 0L) {
            throw new Candidate5DiagnosticFailure(
                    "CANDIDATE5_EXTERNAL_CALL_FAILED");
        }
        String decision = RagCandidate5DiagnosticSupport.decide(evidence);
        return RagCandidate5DiagnosticSupport.freshValidArtifact(
                frozenSelection,
                candidate5Config(),
                decision,
                RagCandidate5DiagnosticSupport.diagnosticSummary(evidence),
                cases.stream().map(Candidate5CaseArtifact::toMap).toList());
    }

    private static Candidate5CaseArtifact candidate5Evidence(
            RagEvaluationDataset.QueryCase queryCase,
            RagEvaluationDataset dataset,
            CalibrationTrace trace,
            DbCallSnapshot calls,
            int embeddingCalls,
            long networkCalls,
            JdbcTemplate jdbc) throws Exception {
        List<String> variants;
        synchronized (trace.fullPathVariants) {
            variants = List.copyOf(trace.fullPathVariants);
        }
        List<String> sqlShapeHashes;
        synchronized (trace.keywordSqlShapeHashes) {
            sqlShapeHashes = List.copyOf(trace.keywordSqlShapeHashes);
        }
        candidate5Require(!variants.isEmpty(), "CANDIDATE5_ARTIFACT_INVALID");
        candidate5Require(queryCase.retrievalQuery().equals(variants.get(0)),
                "CANDIDATE5_ARTIFACT_INVALID");
        candidate5Require(variants.size() == trace.keywordJdbcCalls.get(),
                "CANDIDATE5_ARTIFACT_INVALID");
        candidate5Require(variants.size() == sqlShapeHashes.size(),
                "CANDIDATE5_ARTIFACT_INVALID");

        List<String> frozenIdentifiers = candidate5NumericIdentifiers(queryCase.query());
        List<String> extractedIdentifiers = new ArrayList<>();
        List<String> searchTerms = new ArrayList<>();
        List<Boolean> fullPathPresence = new ArrayList<>();
        for (String variant : variants) {
            extractedIdentifiers.addAll(candidate3IdentifierTerms(variant));
            searchTerms.addAll(candidate5SearchTerms(variant));
            fullPathPresence.add(!frozenIdentifiers.isEmpty()
                    && frozenIdentifiers.stream().allMatch(variant::contains));
        }
        boolean target = queryCase.familyId().startsWith("c5s-t");
        String identifierShape = queryCase.strata().stream()
                .filter(Set.of("numeric-token", "doc-prefix", "han-zero-padded")::contains)
                .findFirst().orElse("none");
        List<RagEvaluationDataset.CorpusSegment> anchors = dataset.corpusById().values().stream()
                .filter(segment -> frozenIdentifiers.stream().anyMatch(identifier ->
                        candidate5BoundaryMatch(candidate5DocumentName(segment), identifier)))
                .toList();
        if (target && anchors.size() != 1) {
            throw new Candidate5DiagnosticFailure("CANDIDATE5_DATASET_INVALID");
        }
        RagEvaluationDataset.CorpusSegment anchor = anchors.size() == 1
                ? anchors.get(0) : null;
        String anchorId = anchor == null ? null : anchor.segmentId();
        boolean keywordReturnedAnchor = anchorId != null
                && containsSegment(trace.paths.getOrDefault("keyword", List.of()), Set.of(anchorId));
        boolean finalReturnedAnchor = anchorId != null
                && containsSegment(trace.finalRanking, Set.of(anchorId));
        boolean documentFieldsConsistent = anchor == null
                || candidate5DocumentFieldsConsistent(jdbc, anchor);
        boolean identifierTermEmitted = searchTerms.stream()
                .anyMatch(frozenIdentifiers::contains);
        List<RagCandidate5DiagnosticSupport.RankedSegment> finalSources =
                candidate5Ranking(trace.finalRanking);
        List<RagCandidate5DiagnosticSupport.RankedSegment> oracleSources =
                candidate5OracleRanking(
                        dataset.qrelsFor(queryCase.id()), trace.finalRanking);
        RagCandidate5DiagnosticSupport.CaseEvidence evidence =
                RagCandidate5DiagnosticSupport.classify(
                        new RagCandidate5DiagnosticSupport.CaseInput(
                                queryCase.id(),
                                queryCase.familyId(),
                                queryCase.language(),
                                queryCase.split(),
                                identifierShape,
                                target,
                                !frozenIdentifiers.isEmpty()
                                        && frozenIdentifiers.stream().allMatch(
                                        queryCase.query()::contains),
                                !frozenIdentifiers.isEmpty()
                                        && frozenIdentifiers.stream().allMatch(
                                        queryCase.retrievalQuery()::contains),
                                fullPathPresence,
                                !frozenIdentifiers.isEmpty()
                                        && extractedIdentifiers.containsAll(frozenIdentifiers),
                                identifierTermEmitted,
                                trace.exactIdentifierPredicate.get(),
                                documentFieldsConsistent,
                                keywordReturnedAnchor,
                                finalReturnedAnchor,
                                candidate5Paths(trace.paths),
                                candidate5Ranking(trace.fusedRanking),
                                candidate5Ranking(trace.filterRanking),
                                finalSources,
                                dataset.qrelsFor(queryCase.id()),
                                oracleSources,
                                trace.finalContextSha256,
                                trace.finalContextEmpty,
                                true));
        Map<String, Long> callCounts = new LinkedHashMap<>();
        callCounts.put("dbCalls", calls.total());
        callCounts.put("embeddingCalls", (long) embeddingCalls);
        callCounts.put("networkCalls", networkCalls);
        return new Candidate5CaseArtifact(
                evidence,
                sha256Utf8(queryCase.query()),
                sha256Utf8(queryCase.retrievalQuery()),
                extractedIdentifiers.size(),
                hashStrings(extractedIdentifiers),
                searchTerms.size(),
                hashStrings(searchTerms),
                hashStrings(sqlShapeHashes),
                Math.toIntExact(trace.identifierPredicateCount.get()),
                Map.copyOf(callCounts));
    }

    private static Map<String, Object> candidate5InvalidDiagnosticRoot(
            RagCandidate5DiagnosticSupport.FrozenDataset frozenSelection,
            Throwable failure) {
        String errorCode = failure instanceof Candidate5DiagnosticFailure diagnosticFailure
                ? diagnosticFailure.errorCode
                : failure instanceof AssertionError
                ? "CANDIDATE5_ARTIFACT_INVALID"
                : "CANDIDATE5_EXTERNAL_CALL_FAILED";
        return RagCandidate5DiagnosticSupport.freshInvalidArtifact(
                frozenSelection,
                candidate5Config(),
                errorCode);
    }

    private static Map<String, Object> runCandidate6CalibrationDiagnostic(
            RagCandidate6DiagnosticSupport.FrozenDataset frozenSelection,
            RagRetrievalService service,
            CandidateFusionService fusionService,
            RagRerankService rerankService,
            RagContextBuilder contextBuilder,
            ThreadPoolTaskExecutor executor,
            CalibrationStageDiagnostics diagnostics,
            QueryCounters queryCounters,
            FeatureHashEmbeddingModel embeddingModel,
            Candidate6JdbcTemplate keywordJdbc,
            AtomicLong externalChatCalls) throws Exception {
        RagEvaluationDataset dataset = frozenSelection.dataset();
        candidate6Require(dataset.queries().size() == 28,
                "CANDIDATE6_DATASET_INVALID");
        candidate6Require(keywordJdbc != null,
                "CANDIDATE6_JDBC_SNAPSHOT_INVALID");
        externalChatCalls.set(0L);
        service.retrieve(RagCandidate6DiagnosticSupport.SELECTION_KB_ID,
                WARMUP_QUERY, WARMUP_QUERY, 10, false);
        awaitExecutorIdle(executor);
        candidate6Require(externalChatCalls.get() == 0L,
                "CANDIDATE6_EXTERNAL_CALL_FAILED");
        queryCounters.reset();

        List<Candidate6CapturedCase> capturedCases = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
            CalibrationTrace trace = new CalibrationTrace(
                    queryCase.id(), queryCase.familyId(), queryCase.split());
            diagnostics.current.set(trace);
            DbCallSnapshot before = queryCounters.snapshot();
            int embeddingBefore = embeddingModel.recordedInputs().size();
            long externalBefore = externalChatCalls.get();
            try {
                RagResult baseline = service.retrieve(
                        RagCandidate6DiagnosticSupport.SELECTION_KB_ID,
                        queryCase.query(), queryCase.retrievalQuery(), 10, false);
                awaitExecutorIdle(executor);
                candidate6Require(trace.sqlFailureClass.get() == null,
                        "CANDIDATE6_SQL_EXECUTION_FAILED");
                candidate6Require(!trace.selectionKnowledgeBaseMismatch.get(),
                        "CANDIDATE6_KB_ISOLATION_FAILED");
                candidate6Require(externalChatCalls.get() == externalBefore,
                        "CANDIDATE6_EXTERNAL_CALL_FAILED");
                DbCallSnapshot sutCalls = queryCounters.snapshot().minus(before);
                int observedEmbeddingCalls = embeddingModel.recordedInputs().size()
                        - embeddingBefore;
                candidate8Require(trace.fullColbertEmbeddingCalls.get()
                                <= observedEmbeddingCalls,
                        "CANDIDATE8_DIAGNOSTIC_EMBEDDING_INVALID");
                int sutEmbeddingCalls = observedEmbeddingCalls
                        - Math.toIntExact(trace.fullColbertEmbeddingCalls.get());
                trace.finalRanking = snapshotResults(baseline.getSources());
                trace.contextRanking = contextRanking(baseline.getContext());
                trace.finalContextSha256 = sha256Utf8(baseline.getContext());
                trace.finalContextEmpty = baseline.getContext() == null
                        || baseline.getContext().isEmpty();
                assertCandidate6StageIsolation(trace);
                List<Candidate6VariantCapture> variants;
                synchronized (trace.candidate6Variants) {
                    variants = List.copyOf(trace.candidate6Variants);
                }
                candidate6Require(!variants.isEmpty()
                                && variants.size() == trace.fullPathVariants.size()
                                && variants.size() == sutCalls.keyword(),
                        "CANDIDATE6_MULTIPLE_JDBC_CALLS_PER_VARIANT");

                List<String> identifiers =
                        RagCandidate6DiagnosticSupport.identifierTerms(
                                queryCase.retrievalQuery());
                Set<Long> exactEvidence = dataset.corpusById().values().stream()
                        .filter(segment ->
                                RagCandidate6DiagnosticSupport.matchesAnyIdentifier(
                                        candidate6DocumentName(segment), identifiers))
                        .map(RagEvaluationDataset.CorpusSegment::segmentId)
                        .map(Long::parseLong)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                List<Candidate6VariantAnalysis> analyses = new ArrayList<>();
                long diagnosticSqlBefore = trace.diagnosticSqlCalls.get();
                for (Candidate6VariantCapture variant : variants) {
                    candidate6Require(variant.identifiers.equals(identifiers),
                            "CANDIDATE6_IDENTIFIER_FIXTURE_INVALID");
                    List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> full =
                            keywordJdbc.executeFullAdmission(
                                    variant, trace, dataset.corpusById().size());
                    List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> priorityRows =
                            keywordJdbc.executeExactFirstBusinessRows(variant, trace);
                    List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> sqlTop50 =
                            RagCandidate6DiagnosticSupport.originalJavaTop50(priorityRows);
                    List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> javaTop50 =
                            RagCandidate6DiagnosticSupport.exactFirstJavaTop50(
                                    variant.businessRows());
                    List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> dualTop50 =
                            RagCandidate6DiagnosticSupport.exactFirstJavaTop50(priorityRows);
                    analyses.add(new Candidate6VariantAnalysis(
                            variant.variantSha256,
                            variant.identifiers.size(),
                            sha256Utf8(normalizeSqlShape(variant.sql())),
                            variant.prioritySqlShapeHash,
                            variant.businessRows(),
                            variant.javaTop50,
                            full,
                            priorityRows,
                            sqlTop50,
                            javaTop50,
                            dualTop50));
                    variant.clearParameters();
                }
                long diagnosticSqlCalls = trace.diagnosticSqlCalls.get()
                        - diagnosticSqlBefore;
                List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> baselineKeyword =
                        RagCandidate6DiagnosticSupport.mergeVariants(
                                analyses.stream().map(Candidate6VariantAnalysis::businessJavaTop50)
                                        .toList());
                List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> sqlKeyword =
                        RagCandidate6DiagnosticSupport.mergeVariants(
                                analyses.stream().map(Candidate6VariantAnalysis::sqlTop50)
                                        .toList());
                List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> javaKeyword =
                        RagCandidate6DiagnosticSupport.mergeVariants(
                                analyses.stream().map(Candidate6VariantAnalysis::javaTop50)
                                        .toList());
                List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> dualKeyword =
                        RagCandidate6DiagnosticSupport.mergeVariants(
                                analyses.stream().map(Candidate6VariantAnalysis::dualTop50)
                                        .toList());
                candidate6Require(candidate6Ids(baselineKeyword).equals(
                                trace.paths.getOrDefault("keyword", List.of()).stream()
                                        .map(item -> Long.parseLong(item.segmentId())).toList()),
                        "CANDIDATE6_VARIANT_MERGE_MISMATCH");
                List<RagCandidate6DiagnosticSupport.VariantStage> stages = analyses.stream()
                        .map(analysis -> new RagCandidate6DiagnosticSupport.VariantStage(
                                candidate6Ids(analysis.fullAdmission()),
                                candidate6Ids(analysis.businessRows()),
                                candidate6Ids(analysis.businessJavaTop50())))
                        .toList();
                RagCandidate6DiagnosticSupport.RootCause classification =
                        RagCandidate6DiagnosticSupport.classifyStages(
                                exactEvidence,
                                stages,
                                candidate6Ids(baselineKeyword),
                                baseline.getSources().stream()
                                        .map(RetrievalResult::getSegmentId).toList());
                capturedCases.add(new Candidate6CapturedCase(
                        queryCase,
                        trace,
                        copyCandidate6Result(baseline),
                        List.copyOf(analyses),
                        Set.copyOf(exactEvidence),
                        classification,
                        baselineKeyword,
                        sqlKeyword,
                        javaKeyword,
                        dualKeyword,
                        sutCalls,
                        diagnosticSqlCalls,
                        sutEmbeddingCalls));
            } finally {
                awaitExecutorIdle(executor);
                diagnostics.current.compareAndSet(trace, null);
                diagnostics.candidate6Variant.remove();
                synchronized (trace.fullPathVariants) {
                    trace.fullPathVariants.clear();
                }
                synchronized (trace.candidate6Variants) {
                    trace.candidate6Variants.clear();
                }
            }
        }

        RagCandidate6DiagnosticSupport.CounterfactualMode selectedMode =
                candidate6SelectedMode(capturedCases);
        List<Candidate6CaseArtifact> artifacts = new ArrayList<>();
        for (Candidate6CapturedCase captured : capturedCases) {
            Candidate6Replay replay = selectedMode
                    == RagCandidate6DiagnosticSupport.CounterfactualMode.NONE
                    ? Candidate6Replay.fromBaseline(captured.baseline())
                    : replayCandidate6(
                            captured,
                            selectedMode,
                            fusionService,
                            rerankService,
                            contextBuilder,
                            diagnostics,
                            queryCounters);
            artifacts.add(candidate6Artifact(
                    captured, replay, selectedMode, dataset));
        }
        List<RagCandidate6DiagnosticSupport.CaseEvidence> evidence = artifacts.stream()
                .map(Candidate6CaseArtifact::evidence).toList();
        candidate6Require(externalChatCalls.get() == 0L,
                "CANDIDATE6_EXTERNAL_CALL_FAILED");
        String decision = RagCandidate6DiagnosticSupport.decide(evidence);
        return RagCandidate6DiagnosticSupport.freshValidArtifact(
                frozenSelection,
                candidate6Config(),
                decision,
                RagCandidate6DiagnosticSupport.diagnosticSummary(evidence),
                artifacts.stream().map(Candidate6CaseArtifact::toMap).toList());
    }

    private static Map<String, Object> runCandidate8CalibrationDiagnostic(
            RagCandidate8DiagnosticSupport.FrozenDataset frozenSelection,
            RagRetrievalService service,
            CandidateFusionService fusionService,
            RagRerankService rerankService,
            RagContextBuilder contextBuilder,
            ThreadPoolTaskExecutor executor,
            CalibrationStageDiagnostics diagnostics,
            QueryCounters queryCounters,
            FeatureHashEmbeddingModel embeddingModel,
            Candidate8JdbcTemplate keywordJdbc,
            AtomicLong externalChatCalls) throws Exception {
        RagEvaluationDataset dataset = frozenSelection.dataset();
        candidate8Require(dataset.queries().size() == 32,
                "CANDIDATE8_DATASET_INVALID");
        candidate8Require(keywordJdbc != null,
                "CANDIDATE8_JDBC_SNAPSHOT_INVALID");
        candidate8Require(frozenSelection.auditedReads()
                        .selectionResourceAccessCount() == 4
                        && frozenSelection.auditedReads()
                        .manifestAccessCount() == 4
                        && frozenSelection.auditedReads()
                        .holdoutResourceAccessCount() == 0,
                "CANDIDATE8_HOLDOUT_ACCESS_FORBIDDEN");
        externalChatCalls.set(0L);
        service.retrieve(RagCandidate8DiagnosticSupport.SELECTION_KB_ID,
                WARMUP_QUERY, WARMUP_QUERY, 10, false);
        awaitExecutorIdle(executor);
        candidate8Require(externalChatCalls.get() == 0L,
                "CANDIDATE8_EXTERNAL_CALL_FAILED");
        queryCounters.reset();

        List<Candidate8CapturedCase> capturedCases = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
            CalibrationTrace trace = new CalibrationTrace(
                    queryCase.id(), queryCase.familyId(), queryCase.split());
            diagnostics.current.set(trace);
            DbCallSnapshot before = queryCounters.snapshot();
            int embeddingBefore = embeddingModel.recordedInputs().size();
            long externalBefore = externalChatCalls.get();
            try {
                RagResult baseline = service.retrieve(
                        RagCandidate8DiagnosticSupport.SELECTION_KB_ID,
                        queryCase.query(), queryCase.retrievalQuery(), 10, false);
                awaitExecutorIdle(executor);
                candidate8Require(trace.sqlFailureClass.get() == null,
                        "CANDIDATE8_SQL_EXECUTION_FAILED");
                candidate8Require(!trace.selectionKnowledgeBaseMismatch.get(),
                        "CANDIDATE8_KB_ISOLATION_FAILED");
                candidate8Require(externalChatCalls.get() == externalBefore,
                        "CANDIDATE8_EXTERNAL_CALL_FAILED");
                DbCallSnapshot sutCalls = queryCounters.snapshot().minus(before);
                int sutEmbeddingCalls = embeddingModel.recordedInputs().size()
                        - embeddingBefore;
                trace.finalRanking = snapshotResults(baseline.getSources());
                trace.contextRanking = contextRanking(baseline.getContext());
                trace.finalContextSha256 = sha256Utf8(baseline.getContext());
                trace.finalContextEmpty = baseline.getContext() == null
                        || baseline.getContext().isEmpty();
                assertCandidate8StageIsolation(trace);

                List<Candidate8VariantCapture> variants;
                synchronized (trace.candidate8Variants) {
                    variants = List.copyOf(trace.candidate8Variants);
                }
                candidate8Require(!variants.isEmpty()
                                && variants.size() == trace.fullPathVariants.size()
                                && variants.size() == sutCalls.keyword(),
                        "CANDIDATE8_MULTIPLE_JDBC_CALLS_PER_VARIANT");
                RagCandidate8DiagnosticSupport.QuerySignals primarySignals =
                        RagCandidate8DiagnosticSupport.querySignals(
                                queryCase.retrievalQuery());
                List<Candidate8VariantAnalysis> analyses = new ArrayList<>();
                long diagnosticSqlBefore = trace.diagnosticSqlCalls.get();
                for (Candidate8VariantCapture variant : variants) {
                    candidate8Require(variant.signals.equals(primarySignals),
                            "CANDIDATE8_SIGNAL_VARIANT_MISMATCH");
                    List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> fullOriginal =
                            keywordJdbc.executeFullOriginal(
                                    variant, trace, dataset.corpusById().size());
                    List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> priorityRows =
                            keywordJdbc.executeCorroboratedBusiness(variant, trace);
                    List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> baselineJava =
                            RagCandidate8DiagnosticSupport.originalJavaTop50(
                                    variant.businessRows());
                    candidate8Require(candidate8SnapshotIds(baselineJava).equals(
                                    candidate8SnapshotIds(variant.javaTop50)),
                            "CANDIDATE8_JAVA_TOP50_MISMATCH");
                    List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> admissionJava =
                            primarySignals.active()
                                    ? RagCandidate8DiagnosticSupport
                                    .stableCorroboratedFirstJavaTop50(priorityRows)
                                    : baselineJava;
                    analyses.add(new Candidate8VariantAnalysis(
                            variant.variantSha256,
                            primarySignals.identifiers().size(),
                            sha256Utf8(JSON.toJSONString(
                                    primarySignals.identifiers())),
                            primarySignals.contentTerms().size(),
                            sha256Utf8(JSON.toJSONString(
                                    primarySignals.contentTerms())),
                            sha256Utf8(normalizeSqlShape(variant.sql())),
                            variant.prioritySqlShapeHash,
                            variant.businessRows(),
                            baselineJava,
                            fullOriginal,
                            priorityRows,
                            admissionJava));
                    variant.clearParameters();
                }
                long diagnosticSqlCalls = trace.diagnosticSqlCalls.get()
                        - diagnosticSqlBefore;
                List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> baselineKeyword =
                        RagCandidate8DiagnosticSupport.mergeVariants(
                                analyses.stream()
                                        .map(Candidate8VariantAnalysis::baselineJavaTop50)
                                        .toList());
                List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> admissionKeyword =
                        RagCandidate8DiagnosticSupport.mergeVariants(
                                analyses.stream()
                                        .map(Candidate8VariantAnalysis::admissionJavaTop50)
                                        .toList());
                candidate8Require(candidate8SnapshotIds(baselineKeyword).equals(
                                trace.paths.getOrDefault("keyword", List.of()).stream()
                                        .map(item -> Long.parseLong(item.segmentId()))
                                        .toList()),
                        "CANDIDATE8_VARIANT_MERGE_MISMATCH");
                Set<Long> exactEvidence = analyses.stream()
                        .flatMap(analysis -> analysis.fullOriginal().stream())
                        .filter(RagCandidate8DiagnosticSupport.RetrievalSnapshot::corroboratedExact)
                        .map(RagCandidate8DiagnosticSupport.RetrievalSnapshot::segmentId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                List<RagCandidate8DiagnosticSupport.VariantStage> stages = analyses.stream()
                        .map(analysis -> new RagCandidate8DiagnosticSupport.VariantStage(
                                candidate8SnapshotIds(analysis.fullOriginal()),
                                candidate8SnapshotIds(analysis.businessRows()),
                                candidate8SnapshotIds(analysis.baselineJavaTop50())))
                        .toList();
                RagCandidate8DiagnosticSupport.RootCause classification =
                        RagCandidate8DiagnosticSupport.classifyStages(
                                exactEvidence,
                                stages,
                                candidate8SnapshotIds(baselineKeyword),
                                baseline.getSources().stream()
                                        .map(RetrievalResult::getSegmentId).toList());
                capturedCases.add(new Candidate8CapturedCase(
                        queryCase,
                        trace,
                        copyCandidate8Result(baseline),
                        primarySignals,
                        List.copyOf(analyses),
                        Set.copyOf(exactEvidence),
                        classification,
                        baselineKeyword,
                        admissionKeyword,
                        sutCalls,
                        diagnosticSqlCalls,
                        sutEmbeddingCalls));
            } finally {
                awaitExecutorIdle(executor);
                diagnostics.current.compareAndSet(trace, null);
                diagnostics.candidate8Variant.remove();
                synchronized (trace.fullPathVariants) {
                    trace.fullPathVariants.clear();
                }
                synchronized (trace.candidate8Variants) {
                    trace.candidate8Variants.clear();
                }
            }
        }

        // Ranking is fully frozen before the evaluation-only qrels are read.
        List<Candidate8RankedCase> rankedCases = new ArrayList<>();
        for (Candidate8CapturedCase captured : capturedCases) {
            Candidate8Replay baseline = Candidate8Replay.fromBaseline(captured);
            Candidate8Replay admissionOnly = replayCandidate8(
                    captured, true, false, fusionService, rerankService,
                    contextBuilder, diagnostics, queryCounters, embeddingModel,
                    externalChatCalls);
            Candidate8Replay survivalOnly = replayCandidate8(
                    captured, false, true, fusionService, rerankService,
                    contextBuilder, diagnostics, queryCounters, embeddingModel,
                    externalChatCalls);
            Candidate8Replay joint = replayCandidate8(
                    captured, true, true, fusionService, rerankService,
                    contextBuilder, diagnostics, queryCounters, embeddingModel,
                    externalChatCalls);
            Map<String, Object> rankingEvidence = candidate8RankingEvidence(
                    captured.queryCase(), baseline, admissionOnly, survivalOnly, joint);
            rankedCases.add(new Candidate8RankedCase(
                    captured,
                    baseline,
                    admissionOnly,
                    survivalOnly,
                    joint,
                    sha256Utf8(JSON.toJSONString(
                            rankingEvidence, JSONWriter.Feature.MapSortField))));
        }
        candidate8Require(rankedCases.size() == 32,
                "CANDIDATE8_RANKING_PHASE_INVALID");
        String rankingPhaseSha256 = sha256Utf8(JSON.toJSONString(
                rankedCases.stream().map(Candidate8RankedCase::rankingHash).toList(),
                JSONWriter.Feature.MapSortField));

        List<Candidate8CaseArtifact> artifacts = new ArrayList<>();
        for (Candidate8RankedCase ranked : rankedCases) {
            artifacts.add(candidate8Artifact(ranked, dataset));
        }
        List<RagCandidate8DiagnosticSupport.CaseEvidence> evidence = artifacts.stream()
                .map(Candidate8CaseArtifact::evidence).toList();
        candidate8Require(externalChatCalls.get() == 0L,
                "CANDIDATE8_EXTERNAL_CALL_FAILED");
        return RagCandidate8DiagnosticSupport.freshValidArtifact(
                frozenSelection,
                candidate8Config(),
                rankingPhaseSha256,
                artifacts.stream().map(Candidate8CaseArtifact::toMap).toList());
    }

    private static Map<String, Object> runCandidate9CalibrationDiagnostic(
            RagCandidate9DiagnosticSupport.RuntimePaths paths,
            RagCandidate9DiagnosticSupport.RunHandle runHandle,
            RagCandidate9DiagnosticSupport.FrozenDataset frozenSelection,
            RagCandidate91RecoverySupport.RecoveryRunHandle recoveryRunHandle,
            RagCandidate9DiagnosticSupport.RecoveryBinding recoveryBinding,
            RagRetrievalService service,
            RagRerankService rerankService,
            RagContextBuilder contextBuilder,
            ThreadPoolTaskExecutor executor,
            CalibrationStageDiagnostics diagnostics,
            QueryCounters queryCounters,
            FeatureHashEmbeddingModel embeddingModel,
            AtomicLong externalChatCalls,
            AtomicLong externalEmbeddingBatchCalls) throws Exception {
        RagEvaluationDataset dataset = frozenSelection.dataset();
        candidate9Require(dataset.queries().size() == 34
                        && dataset.qrels().isEmpty(),
                "CANDIDATE9_DATASET_INVALID");
        RagCandidate9DiagnosticSupport.AccessAudit preRankingAudit =
                frozenSelection.auditedReads();
        candidate9Require(preRankingAudit.selectionResourceAccessCount() == 3
                        && preRankingAudit.manifestAccessCount() == 4
                        && preRankingAudit.holdoutResourceAccessCount() == 0
                        && preRankingAudit.qrelResourceAccessBeforeRanking() == 0
                        && preRankingAudit.qrelResourceAccessCount() == 0,
                "CANDIDATE9_QREL_ACCESS_BEFORE_RANKING");
        externalChatCalls.set(0L);
        service.retrieve(RagCandidate9DiagnosticSupport.SELECTION_KB_ID,
                WARMUP_QUERY, WARMUP_QUERY, 10, false);
        awaitExecutorIdle(executor);
        candidate9Require(externalChatCalls.get() == 0L,
                "CANDIDATE9_EXTERNAL_CALL_FAILED");
        candidate9Require(externalEmbeddingBatchCalls.get() == 0L,
                "CANDIDATE9_EXTERNAL_EMBEDDING_CALL_FAILED");
        queryCounters.reset();

        List<Map<String, Object>> cases = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase queryCase : dataset.queries()) {
            CalibrationTrace trace = new CalibrationTrace(
                    queryCase.id(), queryCase.familyId(), queryCase.split());
            diagnostics.current.set(trace);
            DbCallSnapshot sutBefore = queryCounters.snapshot();
            int embeddingBefore = embeddingModel.recordedInputs().size();
            long externalBefore = externalChatCalls.get();
            long externalEmbeddingBefore = externalEmbeddingBatchCalls.get();
            try {
                RagResult baseline = service.retrieve(
                        RagCandidate9DiagnosticSupport.SELECTION_KB_ID,
                        queryCase.query(), queryCase.retrievalQuery(), 10, false);
                awaitExecutorIdle(executor);
                DbCallSnapshot sutCalls = queryCounters.snapshot().minus(sutBefore);
                int sutEmbeddingCalls = embeddingModel.recordedInputs().size()
                        - embeddingBefore;
                candidate9Require(!trace.selectionKnowledgeBaseMismatch.get(),
                        "CANDIDATE9_KB_ISOLATION_FAILED");
                candidate9Require(trace.sqlFailureClass.get() == null,
                        "CANDIDATE9_SQL_EXECUTION_FAILED");
                candidate9Require(externalChatCalls.get() == externalBefore,
                        "CANDIDATE9_EXTERNAL_CALL_FAILED");
                candidate9Require(trace.candidate9BusinessColbertCalls.get() == 1L
                                && trace.candidate9BaselineFullColbertCalls.get() == 1L
                                && trace.candidate9ProjectionFullColbertCalls.get() == 1L,
                        "CANDIDATE9_COLBERT_CALL_INVALID");
                candidate9Require(
                        trace.candidate9BusinessEmbeddingCalls.get() == 0L
                                && trace.candidate9BaselineFullEmbeddingCalls.get() == 0L
                                && trace.candidate9ProjectionFullEmbeddingCalls.get() == 0L,
                        "CANDIDATE9_EMBEDDING_CALL_INVALID");
                candidate9Require(trace.candidate9FilterDocuments.size()
                                == trace.candidate9BaselineFullColbertDocuments.size()
                                && trace.candidate9FilterDocuments.size()
                                == trace.candidate9ProjectionFullColbertDocuments.size(),
                        "CANDIDATE9_COLBERT_COUNT_INVALID");

                List<Document> baselineBusiness =
                        trace.candidate9BusinessColbertDocuments;
                List<Document> baselineFull =
                        trace.candidate9BaselineFullColbertDocuments;
                List<Document> expectedBaselinePrefix = baselineFull.subList(
                        0, Math.min(RagCandidate9DiagnosticSupport.BUSINESS_COLBERT_LIMIT,
                                baselineFull.size()));
                boolean baselinePrefixMatchesSut = candidate9DocumentRankingsEqual(
                        expectedBaselinePrefix, baselineBusiness);
                candidate9Require(baselinePrefixMatchesSut,
                        "CANDIDATE9_BASELINE_PREFIX_MISMATCH");

                List<Document> projectionFull =
                        trace.candidate9ProjectionFullColbertDocuments;
                List<Document> projectionPrefix = projectionFull.subList(
                        0, Math.min(RagCandidate9DiagnosticSupport.BUSINESS_COLBERT_LIMIT,
                                projectionFull.size()));
                List<RetrievalResult> projectedCandidates =
                        candidate9ResultsFromDocuments(trace, projectionPrefix);
                RerankRequestContext requestContext = RerankRequestContext.builder()
                        .query(Objects.requireNonNullElse(
                                trace.deterministicQuery,
                                queryCase.retrievalQuery()))
                        .build();
                DbCallSnapshot diagnosticBefore = queryCounters.snapshot();
                List<RetrievalResult> projectedSources = ReflectionTestUtils.invokeMethod(
                        rerankService,
                        "identifierConsistencyRerank",
                        requestContext,
                        copyResults(projectedCandidates),
                        trace.queryIntent,
                        10);
                candidate9Require(projectedSources != null,
                        "CANDIDATE9_DETERMINISTIC_REPLAY_FAILED");
                List<RetrievalResult> immutableProjectionSources =
                        copyResults(projectedSources);
                String projectionContext = contextBuilder.buildContext(
                        copyResults(immutableProjectionSources), true);
                DbCallSnapshot diagnosticCalls = queryCounters.snapshot().minus(
                        diagnosticBefore);
                candidate9Require(diagnosticCalls.keyword() == 0L
                                && diagnosticCalls.vector() == 0L
                                && diagnosticCalls.metadata() == 0L
                                && diagnosticCalls.other() == 0L
                                && diagnosticCalls.contextOther() == 0L,
                        "CANDIDATE9_DIAGNOSTIC_DB_INVALID");

                List<RetrievalResult> baselineSources = copyResults(
                        Objects.requireNonNullElse(baseline.getSources(), List.of()));
                String baselineContext = Objects.requireNonNullElse(
                        baseline.getContext(), "");
                projectionContext = Objects.requireNonNullElse(projectionContext, "");
                boolean originalContentRestored =
                        candidate9OriginalContentRestored(trace, baselineSources)
                                && candidate9OriginalContentRestored(
                                trace, immutableProjectionSources);
                candidate9Require(originalContentRestored,
                        "CANDIDATE9_PROJECTION_LEAKED");

                List<String> identifiers =
                        RagCandidate9DiagnosticSupport.visibleIdentifierTerms(
                                queryCase.retrievalQuery());
                boolean tailDisplacementVerified = trace.candidate9Projections.values()
                        .stream().anyMatch(projection -> projection.applied()
                                && projection.originalTokenCount()
                                == RagCandidate9DiagnosticSupport.MAX_TOKENS_PER_DOCUMENT
                                && projection.projectedTokenCount() == 1
                                && projection.retainedContentTokens()
                                == RagCandidate9DiagnosticSupport.MAX_TOKENS_PER_DOCUMENT - 1);
                Map<String, Object> baselineArm = candidate9ArmEvidence(
                        trace, identifiers, baselineFull, baselineBusiness,
                        baselineSources, baselineContext, false);
                Map<String, Object> projectionArm = candidate9ArmEvidence(
                        trace, identifiers, projectionFull, projectionPrefix,
                        immutableProjectionSources, projectionContext,
                        tailDisplacementVerified);
                Map<String, Object> arms = new LinkedHashMap<>();
                arms.put("BASELINE", baselineArm);
                arms.put("FIELD_PROJECTION", projectionArm);

                String rankingSha256 =
                        RagCandidate9DiagnosticSupport.rankingCaseHash(
                                queryCase.id(), queryCase.familyId(),
                                baselineArm, projectionArm);
                long formulaEncodedDocumentTokenDelta =
                        trace.candidate9Projections.values().stream()
                                .filter(RagCandidate9DiagnosticSupport.Projection::applied)
                                .mapToLong(projection -> Math.min(
                                        projection.projectedTokenCount(),
                                        Math.max(
                                                RagCandidate9DiagnosticSupport
                                                        .MAX_TOKENS_PER_DOCUMENT
                                                        - projection.originalTokenCount(),
                                                0)))
                                .sum();
                long baselineEncodedDocumentTokens = baselineFull.stream()
                        .map(Document::getText)
                        .mapToLong(text -> Math.min(
                                RagCandidate9DiagnosticSupport.tokenCount(text),
                                RagCandidate9DiagnosticSupport.MAX_TOKENS_PER_DOCUMENT))
                        .sum();
                long projectionEncodedDocumentTokens = projectionFull.stream()
                        .map(Document::getText)
                        .mapToLong(text -> Math.min(
                                RagCandidate9DiagnosticSupport.tokenCount(text),
                                RagCandidate9DiagnosticSupport.MAX_TOKENS_PER_DOCUMENT))
                        .sum();
                long encodedDocumentTokenDelta =
                        projectionEncodedDocumentTokens - baselineEncodedDocumentTokens;
                candidate9Require(encodedDocumentTokenDelta >= 0L
                                && encodedDocumentTokenDelta
                                == formulaEncodedDocumentTokenDelta,
                        "CANDIDATE9_TOKEN_ACCOUNTING_INVALID");
                Map<String, Object> callCounts = new LinkedHashMap<>();
                callCounts.put("externalEmbeddingBatchCalls",
                        externalEmbeddingBatchCalls.get()
                                - externalEmbeddingBefore);
                callCounts.put("colbertEncodedQueryTokens",
                        RagCandidate9DiagnosticSupport.tokenCount(
                                Objects.requireNonNullElse(
                                        trace.deterministicQuery,
                                        queryCase.retrievalQuery()))
                                - trace.candidate9EncodedQueryTokens);
                callCounts.put("colbertEncodedDocumentTokens",
                        encodedDocumentTokenDelta);
                callCounts.put("llmPromptTokens", 0L);
                callCounts.put("llmCompletionTokens", 0L);
                callCounts.put("cost", 0.0D);
                callCounts.put("sutDbCalls", sutCalls.total());
                callCounts.put("diagnosticContextDbCalls",
                        diagnosticCalls.context());
                callCounts.put("addedVectorCalls", diagnosticCalls.vector());
                callCounts.put("addedMetadataCalls", diagnosticCalls.metadata());
                callCounts.put("addedGraphCalls", diagnosticCalls.other());
                callCounts.put("addedNetworkCalls",
                        externalChatCalls.get() - externalBefore);

                Map<String, Object> value = new LinkedHashMap<>();
                value.put("queryId", queryCase.id());
                value.put("familyId", queryCase.familyId());
                value.put("split", queryCase.split());
                value.put("originalQuerySha256", sha256Utf8(queryCase.query()));
                value.put("retrievalQuerySha256",
                        trace.candidate9EffectiveRerankQuerySha256);
                value.put("extractedIdentifierCount", identifiers.size());
                value.put("extractedIdentifierHash", sha256Utf8(
                        JSON.toJSONString(identifiers)));
                value.put("rankingSha256", rankingSha256);
                value.put("arms", arms);
                value.put("callCounts", callCounts);
                value.put("baselinePrefixMatchesSut", baselinePrefixMatchesSut);
                value.put("originalContentRestored", originalContentRestored);
                value.put("businessColbertCalls",
                        Math.toIntExact(
                                trace.candidate9BusinessColbertCalls.get()));
                value.put("baselineFullColbertCalls",
                        Math.toIntExact(
                                trace.candidate9BaselineFullColbertCalls.get()));
                value.put("projectionFullColbertCalls",
                        Math.toIntExact(
                                trace.candidate9ProjectionFullColbertCalls.get()));
                value.put("sutEmbeddingCalls", sutEmbeddingCalls);
                value.put("businessColbertEmbeddingCalls",
                        Math.toIntExact(
                                trace.candidate9BusinessEmbeddingCalls.get()));
                value.put("baselineFullColbertEmbeddingCalls",
                        Math.toIntExact(
                                trace.candidate9BaselineFullEmbeddingCalls.get()));
                value.put("projectionFullColbertEmbeddingCalls",
                        Math.toIntExact(
                                trace.candidate9ProjectionFullEmbeddingCalls.get()));
                cases.add(Map.copyOf(value));

                candidate9Require(sutEmbeddingCalls == 1
                                && trace.candidate9EncodedQueryTokens
                                == RagCandidate9DiagnosticSupport.tokenCount(
                                Objects.requireNonNullElse(
                                        trace.deterministicQuery,
                                        queryCase.retrievalQuery())),
                        "CANDIDATE9_TOKEN_ACCOUNTING_INVALID");
                candidate9Require(externalEmbeddingBatchCalls.get()
                                == externalEmbeddingBefore,
                        "CANDIDATE9_EXTERNAL_EMBEDDING_CALL_FAILED");
            } finally {
                awaitExecutorIdle(executor);
                diagnostics.current.compareAndSet(trace, null);
            }
        }
        candidate9Require(cases.size() == 34
                        && frozenSelection.auditedReads()
                        .qrelResourceAccessCount() == 0,
                "CANDIDATE9_RANKING_PHASE_INVALID");
        String rankingPhaseSha256 =
                RagCandidate9DiagnosticSupport.rankingPhaseHash(cases);
        RagCandidate9DiagnosticSupport.EvaluationView evaluation;
        if (recoveryRunHandle != null) {
            evaluation = RagCandidate91RecoverySupport
                    .loadQrelsAfterRankingForRecovery(
                            recoveryRunHandle, recoveryBinding,
                            frozenSelection, cases);
        } else {
            evaluation = RagCandidate9DiagnosticSupport.loadQrelsAfterRanking(
                    paths, runHandle, frozenSelection, cases);
        }
        candidate9Require(rankingPhaseSha256.equals(
                        evaluation.rankingPhaseSha256()),
                "CANDIDATE9_RANKING_HASH_INVALID");
        candidate9Require(frozenSelection.auditedReads()
                        .qrelResourceAccessBeforeRanking() == 0
                        && frozenSelection.auditedReads()
                        .qrelResourceAccessCount() == 1
                        && frozenSelection.auditedReads()
                        .holdoutResourceAccessCount() == 0,
                "CANDIDATE9_QREL_ACCESS_INVALID");
        candidate9Require(externalChatCalls.get() == 0L,
                "CANDIDATE9_EXTERNAL_CALL_FAILED");
        candidate9Require(externalEmbeddingBatchCalls.get() == 0L,
                "CANDIDATE9_EXTERNAL_EMBEDDING_CALL_FAILED");
        return recoveryRunHandle == null
                ? RagCandidate9DiagnosticSupport.freshValidArtifact(
                frozenSelection, candidate9Config(), cases)
                : RagCandidate9DiagnosticSupport.freshValidArtifact(
                recoveryBinding, frozenSelection, candidate9Config(), cases);
    }

    private static Map<String, Object> candidate9ArmEvidence(
            CalibrationTrace trace,
            List<String> identifiers,
            List<Document> fullColbert,
            List<Document> businessPrefix,
            List<RetrievalResult> sources,
            String context,
            boolean tailDisplacementVerified) {
        Map<String, Object> arm = new LinkedHashMap<>();
        arm.put("filterOutput", candidate9DocumentStage(
                trace, trace.candidate9FilterDocuments, identifiers));
        arm.put("fullColbert", candidate9DocumentStage(
                trace, fullColbert, identifiers));
        arm.put("businessPrefix", candidate9DocumentStage(
                trace, businessPrefix, identifiers));
        arm.put("finalSources", candidate9ResultStage(
                trace, sources, identifiers));
        arm.put("contextSegments", candidate9ContextStage(
                trace, context, identifiers));
        arm.put("contextSha256", sha256Utf8(context));
        arm.put("contextEmpty", context == null || context.isEmpty());
        arm.put("tailDisplacementVerified", tailDisplacementVerified);
        return Map.copyOf(arm);
    }

    private static Candidate8Replay replayCandidate8(
            Candidate8CapturedCase captured,
            boolean admission,
            boolean survival,
            CandidateFusionService fusionService,
            RagRerankService rerankService,
            RagContextBuilder contextBuilder,
            CalibrationStageDiagnostics diagnostics,
            QueryCounters queryCounters,
            FeatureHashEmbeddingModel embeddingModel,
            AtomicLong externalChatCalls) {
        List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> keyword = admission
                ? captured.admissionKeyword() : captured.baselineKeyword();
        List<List<RetrievalResult>> pathResults = new ArrayList<>();
        List<String> pathNames = new ArrayList<>();
        for (String pathName : List.of("vector", "keyword", "metadata", "graph")) {
            List<RetrievalResult> results = "keyword".equals(pathName)
                    ? RagCandidate8DiagnosticSupport.rebuildResults(keyword)
                    : rebuildCandidate8Path(
                            captured.trace(),
                            captured.trace().paths.getOrDefault(pathName, List.of()));
            if (!results.isEmpty()) {
                pathResults.add(results);
                pathNames.add(pathName);
            }
        }
        CalibrationTrace replayTrace = new CalibrationTrace(
                captured.queryCase().id(),
                captured.queryCase().familyId(),
                captured.queryCase().split());
        CalibrationTrace previous = diagnostics.current.getAndSet(replayTrace);
        DbCallSnapshot before = queryCounters.snapshot();
        int embeddingBefore = embeddingModel.recordedInputs().size();
        long externalBefore = externalChatCalls.get();
        try {
            CandidateFusionService.FusionResult fused =
                    fusionService.fuseWithDiagnostics(pathResults, pathNames);
            String rerankQuery = captured.queryCase().retrievalQuery();
            List<RetrievalResult> sources = rerankService.rerank(
                    rerankQuery,
                    copyResults(fused.getResults()),
                    captured.trace().queryIntent,
                    10,
                    null,
                    null);
            candidate8Require(captured.signals().equals(replayTrace.candidate8Signals),
                    "CANDIDATE8_SIGNAL_REPLAY_MISMATCH");
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> prefix =
                    candidate8DocumentSnapshots(
                            replayTrace,
                            replayTrace.candidate8BusinessColbertDocuments,
                            captured.signals());
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> full =
                    candidate8DocumentSnapshots(
                            replayTrace,
                            replayTrace.candidate8FullColbertDocuments,
                            captured.signals());
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> filterOrder =
                    candidate8DocumentSnapshots(
                            replayTrace,
                            replayTrace.candidate8FilterDocuments,
                            captured.signals());
            candidate8Require(prefix.size()
                            <= CANDIDATE8_BUSINESS_COLBERT_LIMIT,
                    "CANDIDATE8_COLBERT_PREFIX_INVALID");
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> tieOutput = survival
                    ? RagCandidate8DiagnosticSupport.tieReplace(
                    prefix, full, filterOrder, captured.signals())
                    : prefix;
            boolean tieChanged = !tieOutput.equals(prefix);
            boolean cutoffMechanismValid = survival && tieChanged
                    && candidate8CutoffMechanismValid(prefix, tieOutput);
            if (survival && tieChanged) {
                RerankRequestContext requestContext = RerankRequestContext.builder()
                        .query(Objects.requireNonNullElse(
                                replayTrace.deterministicQuery, rerankQuery))
                        .build();
                List<RetrievalResult> replaced =
                        RagCandidate8DiagnosticSupport.rebuildResults(tieOutput);
                List<RetrievalResult> reranked = ReflectionTestUtils.invokeMethod(
                        rerankService,
                        "identifierConsistencyRerank",
                        requestContext,
                        replaced,
                        replayTrace.queryIntent,
                        10);
                candidate8Require(reranked != null,
                        "CANDIDATE8_DETERMINISTIC_REPLAY_FAILED");
                sources = reranked;
                replayTrace.rerankOutput = snapshotResults(sources);
            }
            List<RetrievalResult> immutableSources = copyResults(sources);
            String context = contextBuilder.buildContext(
                    copyResults(immutableSources), true);
            DbCallSnapshot calls = queryCounters.snapshot().minus(before);
            int diagnosticEmbeddingCalls = embeddingModel.recordedInputs().size()
                    - embeddingBefore;
            long diagnosticExternalCalls = externalChatCalls.get() - externalBefore;
            candidate8Require(calls.keyword() == 0L
                            && calls.metadata() == 0L
                            && calls.vector() == 0L
                            && calls.other() == 0L
                            && calls.contextOther() == 0L,
                    "CANDIDATE8_COUNTERFACTUAL_DB_INVALID");
            candidate8Require(diagnosticEmbeddingCalls
                            == replayTrace.businessColbertEmbeddingCalls.get()
                            + replayTrace.fullColbertEmbeddingCalls.get(),
                    "CANDIDATE8_DIAGNOSTIC_EMBEDDING_INVALID");
            candidate8Require(diagnosticExternalCalls == 0L,
                    "CANDIDATE8_EXTERNAL_CALL_FAILED");
            candidate8Require(replayTrace.businessColbertCalls.get() == 1L,
                    "CANDIDATE8_BUSINESS_COLBERT_CALL_INVALID");
            candidate8Require(replayTrace.fullColbertCalls.get()
                            == (captured.signals().active() ? 1L : 0L),
                    "CANDIDATE8_FULL_COLBERT_CALL_INVALID");
            replayTrace.finalRanking = snapshotResults(immutableSources);
            replayTrace.contextRanking = contextRanking(context);
            replayTrace.finalContextSha256 = sha256Utf8(context);
            replayTrace.finalContextEmpty = context == null || context.isEmpty();
            assertCandidate8StageIsolation(replayTrace);
            return new Candidate8Replay(
                    admission,
                    survival,
                    immutableSources,
                    context == null ? "" : context,
                    calls,
                    diagnosticExternalCalls,
                    diagnosticEmbeddingCalls,
                    replayTrace,
                    filterOrder,
                    prefix,
                    full,
                    tieOutput,
                    tieChanged,
                    cutoffMechanismValid);
        } finally {
            diagnostics.current.set(previous);
        }
    }

    private static List<RetrievalResult> rebuildCandidate8Path(
            CalibrationTrace trace,
            List<RankedSegment> ranking) {
        List<RetrievalResult> results = new ArrayList<>();
        for (RankedSegment item : ranking) {
            RetrievalResult captured = trace.candidatesBySegmentId.get(item.segmentId());
            candidate8Require(captured != null,
                    "CANDIDATE8_PATH_SNAPSHOT_INVALID");
            RetrievalResult copy = copyResults(List.of(captured)).get(0);
            copy.setScore(item.score());
            results.add(copy);
        }
        return List.copyOf(results);
    }

    private static List<RagCandidate8DiagnosticSupport.RetrievalSnapshot>
    candidate8DocumentSnapshots(
            CalibrationTrace trace,
            List<Document> documents,
            RagCandidate8DiagnosticSupport.QuerySignals signals) {
        List<RetrievalResult> results = new ArrayList<>();
        for (Document document : documents) {
            Object rawSegmentId = document.getMetadata().get("segmentId");
            candidate8Require(rawSegmentId != null,
                    "CANDIDATE8_COLBERT_SNAPSHOT_INVALID");
            String segmentId = String.valueOf(rawSegmentId);
            RetrievalResult captured = trace.candidatesBySegmentId.get(segmentId);
            candidate8Require(captured != null,
                    "CANDIDATE8_COLBERT_SNAPSHOT_INVALID");
            RetrievalResult copy = copyResults(List.of(captured)).get(0);
            Map<String, Object> metadata = copy.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(copy.getMetadata());
            metadata.remove("colbert_score");
            Object rawScore = document.getMetadata().get("colbert_score");
            if (rawScore instanceof Number number) {
                metadata.put("colbert_score", number.doubleValue());
            }
            copy.setMetadata(metadata);
            results.add(copy);
        }
        return RagCandidate8DiagnosticSupport.snapshotResults(results, signals);
    }

    private static boolean candidate8CutoffMechanismValid(
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> prefix,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> tieOutput) {
        if (prefix.isEmpty() || prefix.size() != tieOutput.size()) {
            return false;
        }
        double cutoff = prefix.get(prefix.size() - 1).colbertScore();
        if (!Double.isFinite(cutoff)) {
            return false;
        }
        boolean changed = false;
        for (int index = 0; index < prefix.size(); index++) {
            RagCandidate8DiagnosticSupport.RetrievalSnapshot before = prefix.get(index);
            RagCandidate8DiagnosticSupport.RetrievalSnapshot after = tieOutput.get(index);
            if (Objects.equals(before.segmentId(), after.segmentId())) {
                continue;
            }
            changed = true;
            if (before.corroboratedExact()
                    || Double.compare(before.colbertScore(), cutoff) != 0
                    || !after.corroboratedExact()
                    || !Double.isFinite(after.colbertScore())
                    || Double.compare(after.colbertScore(), cutoff) != 0) {
                return false;
            }
        }
        return changed;
    }

    private static Map<String, Object> candidate8RankingEvidence(
            RagEvaluationDataset.QueryCase queryCase,
            Candidate8Replay baseline,
            Candidate8Replay admissionOnly,
            Candidate8Replay survivalOnly,
            Candidate8Replay joint) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("queryId", queryCase.id());
        value.put("familyId", queryCase.familyId());
        value.put("BASELINE", candidate8ArmEvidence(baseline));
        value.put("ADMISSION_ONLY", candidate8ArmEvidence(admissionOnly));
        value.put("SURVIVAL_ONLY", candidate8ArmEvidence(survivalOnly));
        value.put("JOINT", candidate8ArmEvidence(joint));
        return value;
    }

    private static Map<String, Object> candidate8ArmEvidence(
            Candidate8Replay replay) {
        Map<String, Object> value = new LinkedHashMap<>();
        double cutoff = replay.businessPrefix().isEmpty()
                ? Double.NaN
                : replay.businessPrefix().get(
                replay.businessPrefix().size() - 1).colbertScore();
        Map<String, Object> paths = new LinkedHashMap<>();
        replay.trace().paths.forEach((name, ranking) -> paths.put(
                name,
                candidate8StageMaps(
                        replay.trace(), ranking,
                        replay.trace().candidate8Signals, cutoff)));
        value.put("paths", paths);
        value.put("fused", candidate8StageMaps(
                replay.trace(), replay.trace().fusedRanking,
                replay.trace().candidate8Signals, cutoff));
        value.put("filterOutput", candidate8SnapshotMaps(
                replay.filterOrder(), cutoff));
        value.put("businessColbert", candidate8SnapshotMaps(
                replay.businessPrefix(), cutoff));
        value.put("fullColbert", candidate8SnapshotMaps(
                replay.fullColbert(), cutoff));
        value.put("tieOutput", candidate8SnapshotMaps(
                replay.tieOutput(), cutoff));
        value.put("deterministic", candidate8StageMaps(
                replay.trace(), replay.trace().deterministicActualRanking,
                replay.trace().candidate8Signals, cutoff));
        value.put("finalSources", candidate8ResultMaps(
                replay.sources(), replay.trace().candidate8Signals, cutoff));
        value.put("contextSegments", candidate8StageMaps(
                replay.trace(), replay.trace().contextRanking,
                replay.trace().candidate8Signals, cutoff));
        value.put("contextSha256", sha256Utf8(replay.context()));
        value.put("contextEmpty", replay.context().isEmpty());
        value.put("tieChanged", replay.tieChanged());
        value.put("cutoffMechanismValid", replay.cutoffMechanismValid());
        return value;
    }

    private static List<Map<String, Object>> candidate8StageMaps(
            CalibrationTrace trace,
            List<RankedSegment> ranking,
            RagCandidate8DiagnosticSupport.QuerySignals signals,
            double cutoff) {
        if (signals == null || ranking.isEmpty()) {
            return List.of();
        }
        Map<String, Double> fullScores = new LinkedHashMap<>();
        for (Document document : trace.candidate8FullColbertDocuments) {
            Object id = document.getMetadata().get("segmentId");
            Object score = document.getMetadata().get("colbert_score");
            if (id != null && score instanceof Number number) {
                fullScores.putIfAbsent(String.valueOf(id), number.doubleValue());
            }
        }
        List<RetrievalResult> values = new ArrayList<>();
        for (RankedSegment item : ranking) {
            RetrievalResult captured = trace.candidatesBySegmentId.get(item.segmentId());
            candidate8Require(captured != null,
                    "CANDIDATE8_STAGE_SNAPSHOT_INVALID");
            RetrievalResult copy = copyResults(List.of(captured)).get(0);
            copy.setScore(item.score());
            Map<String, Object> metadata = copy.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(copy.getMetadata());
            if (fullScores.containsKey(item.segmentId())) {
                metadata.put("colbert_score", fullScores.get(item.segmentId()));
            }
            copy.setMetadata(metadata);
            values.add(copy);
        }
        return candidate8SnapshotMaps(
                RagCandidate8DiagnosticSupport.snapshotResults(values, signals), cutoff);
    }

    private static Candidate8CaseArtifact candidate8Artifact(
            Candidate8RankedCase ranked,
            RagEvaluationDataset dataset) {
        Candidate8CapturedCase captured = ranked.captured();
        RagEvaluationDataset.QueryCase queryCase = captured.queryCase();
        Candidate8Replay baseline = ranked.baseline();
        Candidate8Replay admissionOnly = ranked.admissionOnly();
        Candidate8Replay survivalOnly = ranked.survivalOnly();
        Candidate8Replay joint = ranked.joint();
        RagMetrics.Scores baselineMetrics = candidate8Metrics(
                dataset, queryCase, baseline.sources());
        RagMetrics.Scores jointMetrics = candidate8Metrics(
                dataset, queryCase, joint.sources());
        boolean target = queryCase.strata().contains("candidate8-target");
        String role = candidate8Role(queryCase);
        boolean admissionOnlyUnchanged = candidate8SameBehavior(
                baseline, admissionOnly);
        boolean survivalOnlyUnchanged = candidate8SameBehavior(
                baseline, survivalOnly);
        boolean jointUnchanged = candidate8SameBehavior(baseline, joint);
        boolean diagnosticSideEffectsValid = candidate8ReplaySideEffectsValid(
                admissionOnly, survivalOnly, joint);
        boolean mechanismValid;
        boolean controlUnchanged = true;
        if (target && "en".equals(queryCase.language())) {
            mechanismValid = diagnosticSideEffectsValid
                    && captured.signals().active()
                    && captured.classification().modifiable()
                    && !containsCandidate8Id(
                    captured.baselineKeyword(), captured.exactEvidence())
                    && containsCandidate8Id(
                    captured.admissionKeyword(), captured.exactEvidence())
                    && containsCandidate8Id(
                    admissionOnly.filterOrder(), captured.exactEvidence())
                    && !containsCandidate8Id(
                    admissionOnly.businessPrefix(), captured.exactEvidence())
                    && !containsCandidate8Id(
                    joint.businessPrefix(), captured.exactEvidence())
                    && containsCandidate8Id(
                    joint.tieOutput(), captured.exactEvidence())
                    && joint.cutoffMechanismValid();
        } else if (target) {
            mechanismValid = diagnosticSideEffectsValid
                    && jointUnchanged && !joint.tieChanged();
        } else {
            mechanismValid = diagnosticSideEffectsValid
                    && candidate8ControlMechanismValid(
                    role, captured, baseline, joint);
            controlUnchanged = "multi-id-collision".equals(role)
                    ? candidate8MultiIdContractValid(
                    captured, baseline, admissionOnly, survivalOnly, joint)
                    : admissionOnlyUnchanged && survivalOnlyUnchanged && jointUnchanged;
        }
        RagCandidate8DiagnosticSupport.CaseEvidence evidence =
                new RagCandidate8DiagnosticSupport.CaseEvidence(
                        queryCase.id(),
                        queryCase.familyId(),
                        queryCase.language(),
                        role,
                        target,
                        mechanismValid,
                        baselineMetrics.retrievalApAt10(),
                        baselineMetrics.ndcgAt10(),
                        jointMetrics.retrievalApAt10(),
                        jointMetrics.ndcgAt10(),
                        admissionOnlyUnchanged,
                        survivalOnlyUnchanged,
                        controlUnchanged);
        Map<String, Long> callCounts = new LinkedHashMap<>();
        callCounts.put("sutDbCalls", captured.sutCalls().total());
        callCounts.put("diagnosticSqlCalls", captured.diagnosticSqlCalls());
        callCounts.put("diagnosticContextDbCalls",
                admissionOnly.diagnosticCalls().context()
                        + survivalOnly.diagnosticCalls().context()
                        + joint.diagnosticCalls().context());
        callCounts.put("diagnosticTotalDbCalls",
                callCounts.get("diagnosticSqlCalls")
                        + callCounts.get("diagnosticContextDbCalls"));
        callCounts.put("diagnosticBusinessColbertCalls",
                baseline.trace().businessColbertCalls.get()
                        + admissionOnly.trace().businessColbertCalls.get()
                        + survivalOnly.trace().businessColbertCalls.get()
                        + joint.trace().businessColbertCalls.get());
        callCounts.put("diagnosticFullColbertCalls",
                baseline.trace().fullColbertCalls.get()
                        + admissionOnly.trace().fullColbertCalls.get()
                        + survivalOnly.trace().fullColbertCalls.get()
                        + joint.trace().fullColbertCalls.get());
        callCounts.put("sutEmbeddingCalls", (long) captured.sutEmbeddingCalls());
        long diagnosticBusinessEmbeddingCalls =
                admissionOnly.trace().businessColbertEmbeddingCalls.get()
                        + survivalOnly.trace().businessColbertEmbeddingCalls.get()
                        + joint.trace().businessColbertEmbeddingCalls.get();
        long diagnosticFullEmbeddingCalls =
                baseline.trace().fullColbertEmbeddingCalls.get()
                        + admissionOnly.trace().fullColbertEmbeddingCalls.get()
                        + survivalOnly.trace().fullColbertEmbeddingCalls.get()
                        + joint.trace().fullColbertEmbeddingCalls.get();
        callCounts.put("diagnosticBusinessEmbeddingCalls",
                diagnosticBusinessEmbeddingCalls);
        callCounts.put("diagnosticFullEmbeddingCalls",
                diagnosticFullEmbeddingCalls);
        callCounts.put("diagnosticEmbeddingCalls",
                diagnosticBusinessEmbeddingCalls + diagnosticFullEmbeddingCalls);
        callCounts.put("addedVectorCalls",
                admissionOnly.diagnosticCalls().vector()
                        + survivalOnly.diagnosticCalls().vector()
                        + joint.diagnosticCalls().vector());
        callCounts.put("addedMetadataCalls",
                admissionOnly.diagnosticCalls().metadata()
                        + survivalOnly.diagnosticCalls().metadata()
                        + joint.diagnosticCalls().metadata());
        callCounts.put("addedGraphCalls",
                admissionOnly.diagnosticCalls().other()
                        + survivalOnly.diagnosticCalls().other()
                        + joint.diagnosticCalls().other());
        callCounts.put("addedNetworkCalls",
                admissionOnly.diagnosticExternalCalls()
                        + survivalOnly.diagnosticExternalCalls()
                        + joint.diagnosticExternalCalls());
        return new Candidate8CaseArtifact(
                evidence,
                queryCase.split(),
                sha256Utf8(queryCase.query()),
                sha256Utf8(queryCase.retrievalQuery()),
                captured.signals().identifiers().size(),
                sha256Utf8(JSON.toJSONString(
                        captured.signals().identifiers())),
                captured.signals().contentTerms().size(),
                sha256Utf8(JSON.toJSONString(
                        captured.signals().contentTerms())),
                captured.classification(),
                captured.variants(),
                ranked.rankingHash(),
                baseline,
                admissionOnly,
                survivalOnly,
                joint,
                Map.copyOf(callCounts));
    }

    private static RagMetrics.Scores candidate8Metrics(
            RagEvaluationDataset dataset,
            RagEvaluationDataset.QueryCase queryCase,
            List<RetrievalResult> sources) {
        if (!queryCase.answerable()) {
            return new RagMetrics.Scores(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        return RagMetrics.evaluate(
                dataset.qrelsFor(queryCase.id()),
                sources.stream().map(RetrievalResult::getSegmentId)
                        .map(String::valueOf).toList());
    }

    private static String candidate8Role(
            RagEvaluationDataset.QueryCase queryCase) {
        if (queryCase.strata().contains("candidate8-target")) {
            return "target";
        }
        for (String role : List.of(
                "no-identifier",
                "no-exact-match",
                "exact-only-safety",
                "lexical-lure-below-cutoff",
                "existing-survivor",
                "multi-id-collision")) {
            if (queryCase.strata().contains(role)) {
                return role;
            }
        }
        throw new Candidate8DiagnosticFailure("CANDIDATE8_ROLE_INVALID");
    }

    private static boolean candidate8ControlMechanismValid(
            String role,
            Candidate8CapturedCase captured,
            Candidate8Replay baseline,
            Candidate8Replay joint) {
        return switch (role) {
            case "no-identifier" -> captured.signals().identifiers().isEmpty();
            case "no-exact-match", "exact-only-safety" ->
                    captured.exactEvidence().isEmpty();
            case "lexical-lure-below-cutoff" -> !joint.tieChanged();
            case "existing-survivor" -> baseline.sources().stream()
                    .map(RetrievalResult::getSegmentId)
                    .anyMatch(captured.exactEvidence()::contains);
            case "multi-id-collision" ->
                    captured.signals().identifiers().size() == 2
                            && captured.exactEvidence().size() == 1;
            default -> false;
        };
    }

    private static boolean candidate8MultiIdContractValid(
            Candidate8CapturedCase captured,
            Candidate8Replay baseline,
            Candidate8Replay admissionOnly,
            Candidate8Replay survivalOnly,
            Candidate8Replay joint) {
        if (!candidate8SameBehavior(baseline, admissionOnly)
                || !candidate8SameBehavior(baseline, survivalOnly)) {
            return false;
        }
        Set<Long> baselineIds = baseline.sources().stream()
                .map(RetrievalResult::getSegmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> jointIds = joint.sources().stream()
                .map(RetrievalResult::getSegmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> added = new LinkedHashSet<>(jointIds);
        added.removeAll(baselineIds);
        List<Double> baselineScores = baseline.sources().stream()
                .map(RetrievalResult::getScore).sorted().toList();
        List<Double> jointScores = joint.sources().stream()
                .map(RetrievalResult::getScore).sorted().toList();
        return captured.exactEvidence().containsAll(added)
                && baselineScores.equals(jointScores);
    }

    private static boolean candidate8SameBehavior(
            Candidate8Replay left,
            Candidate8Replay right) {
        return left.sources().stream().map(RetrievalResult::getSegmentId).toList()
                .equals(right.sources().stream()
                        .map(RetrievalResult::getSegmentId).toList())
                && left.sources().stream().map(RetrievalResult::getScore).toList()
                .equals(right.sources().stream()
                        .map(RetrievalResult::getScore).toList())
                && sha256Utf8(left.context()).equals(sha256Utf8(right.context()))
                && left.context().isEmpty() == right.context().isEmpty();
    }

    private static boolean candidate8ReplaySideEffectsValid(
            Candidate8Replay... replays) {
        for (Candidate8Replay replay : replays) {
            if (replay.diagnosticCalls().keyword() != 0L
                    || replay.diagnosticCalls().metadata() != 0L
                    || replay.diagnosticCalls().vector() != 0L
                    || replay.diagnosticCalls().other() != 0L
                    || replay.diagnosticCalls().contextOther() != 0L
                    || replay.diagnosticExternalCalls() != 0L
                    || replay.diagnosticEmbeddingCalls()
                    != replay.trace().businessColbertEmbeddingCalls.get()
                    + replay.trace().fullColbertEmbeddingCalls.get()) {
                return false;
            }
        }
        return true;
    }

    private static List<Long> candidate8SnapshotIds(
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> values) {
        return values.stream()
                .map(RagCandidate8DiagnosticSupport.RetrievalSnapshot::segmentId)
                .toList();
    }

    private static boolean containsCandidate8Id(
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> values,
            Set<Long> targets) {
        return values.stream()
                .map(RagCandidate8DiagnosticSupport.RetrievalSnapshot::segmentId)
                .anyMatch(targets::contains);
    }

    private static RagResult copyCandidate8Result(RagResult source) {
        return RagResult.builder()
                .sources(copyResults(source.getSources()))
                .context(Objects.requireNonNullElse(source.getContext(), ""))
                .debugInfo(Map.of())
                .build();
    }

    private static List<Map<String, Object>> candidate8SnapshotMaps(
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> snapshots) {
        return candidate8SnapshotMaps(snapshots, Double.NaN);
    }

    private static List<Map<String, Object>> candidate8SnapshotMaps(
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> snapshots,
            double cutoff) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < snapshots.size(); index++) {
            RagCandidate8DiagnosticSupport.RetrievalSnapshot snapshot =
                    snapshots.get(index);
            Map<String, Object> value = new LinkedHashMap<>(
                    snapshot.sanitized(index + 1));
            value.put("cutoffEligible",
                    snapshot.corroboratedExact()
                            && Double.isFinite(cutoff)
                            && Double.compare(snapshot.colbertScore(), cutoff) == 0);
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static List<Map<String, Object>> candidate8ResultMaps(
            List<RetrievalResult> results,
            RagCandidate8DiagnosticSupport.QuerySignals signals,
            double cutoff) {
        if (signals == null) {
            return List.of();
        }
        return candidate8SnapshotMaps(
                RagCandidate8DiagnosticSupport.snapshotResults(results, signals),
                cutoff);
    }

    private static List<Map<String, Object>> candidate9DocumentStage(
            CalibrationTrace trace,
            List<Document> documents,
            List<String> identifiers) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            Long segmentId = candidate9DocumentSegmentId(document);
            candidate9Require(segmentId != null,
                    "CANDIDATE9_STAGE_SNAPSHOT_INVALID");
            RetrievalResult original = trace.candidatesBySegmentId.get(
                    String.valueOf(segmentId));
            candidate9Require(original != null,
                    "CANDIDATE9_STAGE_SNAPSHOT_INVALID");
            Object rawScore = document.getMetadata().get("colbert_score");
            if (!(rawScore instanceof Number)) {
                rawScore = document.getMetadata().get("score");
            }
            candidate9Require(rawScore instanceof Number number
                            && Double.isFinite(number.doubleValue()),
                    "CANDIDATE9_STAGE_SCORE_INVALID");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("segmentId", segmentId);
            value.put("rank", index + 1);
            value.put("score", ((Number) rawScore).doubleValue());
            value.put("exactMatch",
                    RagCandidate9DiagnosticSupport.matchesAnyIdentifier(
                            original.getDocumentName(), identifiers));
            values.add(Map.copyOf(value));
        }
        return List.copyOf(values);
    }

    private static List<Map<String, Object>> candidate9ResultStage(
            CalibrationTrace trace,
            List<RetrievalResult> results,
            List<String> identifiers) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < results.size(); index++) {
            RetrievalResult result = results.get(index);
            candidate9Require(result != null && result.getSegmentId() != null
                            && Double.isFinite(result.getScore()),
                    "CANDIDATE9_STAGE_SNAPSHOT_INVALID");
            RetrievalResult original = trace.candidatesBySegmentId.get(
                    String.valueOf(result.getSegmentId()));
            candidate9Require(original != null,
                    "CANDIDATE9_STAGE_SNAPSHOT_INVALID");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("segmentId", result.getSegmentId());
            value.put("rank", index + 1);
            value.put("score", result.getScore());
            value.put("exactMatch",
                    RagCandidate9DiagnosticSupport.matchesAnyIdentifier(
                            original.getDocumentName(), identifiers));
            values.add(Map.copyOf(value));
        }
        return List.copyOf(values);
    }

    private static List<Map<String, Object>> candidate9ContextStage(
            CalibrationTrace trace,
            String context,
            List<String> identifiers) {
        List<Map<String, Object>> values = new ArrayList<>();
        int rank = 1;
        for (RankedSegment segment : contextRanking(context)) {
            RetrievalResult original = trace.candidatesBySegmentId.get(
                    segment.segmentId());
            candidate9Require(original != null,
                    "CANDIDATE9_CONTEXT_SNAPSHOT_INVALID");
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("segmentId", Long.valueOf(segment.segmentId()));
            value.put("rank", rank++);
            value.put("score", 0.0D);
            value.put("exactMatch",
                    RagCandidate9DiagnosticSupport.matchesAnyIdentifier(
                            original.getDocumentName(), identifiers));
            values.add(Map.copyOf(value));
        }
        return List.copyOf(values);
    }

    private static boolean candidate9DocumentRankingsEqual(
            List<Document> expected,
            List<Document> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            Document left = expected.get(index);
            Document right = actual.get(index);
            if (!Objects.equals(candidate9DocumentSegmentId(left),
                    candidate9DocumentSegmentId(right))) {
                return false;
            }
            Object leftScore = left.getMetadata().get("colbert_score");
            Object rightScore = right.getMetadata().get("colbert_score");
            if (!(leftScore instanceof Number leftNumber)
                    || !(rightScore instanceof Number rightNumber)
                    || Double.compare(leftNumber.doubleValue(),
                    rightNumber.doubleValue()) != 0) {
                return false;
            }
        }
        return true;
    }

    private static List<RetrievalResult> candidate9ResultsFromDocuments(
            CalibrationTrace trace, List<Document> documents) {
        List<RetrievalResult> results = new ArrayList<>(documents.size());
        for (Document document : documents) {
            Long segmentId = candidate9DocumentSegmentId(document);
            RetrievalResult original = segmentId == null ? null
                    : trace.candidatesBySegmentId.get(String.valueOf(segmentId));
            candidate9Require(original != null,
                    "CANDIDATE9_RESULT_MAPPING_INVALID");
            RetrievalResult copy = copyResults(List.of(original)).get(0);
            Object score = document.getMetadata().get("score");
            if (score instanceof Number number) {
                copy.setScore(number.doubleValue());
            }
            copy.setMetadata(document.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(document.getMetadata()));
            copy.setContent(original.getContent());
            results.add(copy);
        }
        return List.copyOf(results);
    }

    private static boolean candidate9OriginalContentRestored(
            CalibrationTrace trace, List<RetrievalResult> results) {
        return results.stream().allMatch(result -> {
            RetrievalResult original = result == null || result.getSegmentId() == null
                    ? null : trace.candidatesBySegmentId.get(
                    String.valueOf(result.getSegmentId()));
            return original != null
                    && Objects.equals(result.getContent(), original.getContent());
        });
    }

    private static RagCandidate6DiagnosticSupport.CounterfactualMode
    candidate6SelectedMode(List<Candidate6CapturedCase> cases) {
        List<Candidate6CapturedCase> englishTargets = cases.stream()
                .filter(item -> item.queryCase().familyId().startsWith("c6s-t"))
                .filter(item -> "en".equals(item.queryCase().language()))
                .toList();
        Set<RagCandidate6DiagnosticSupport.RootCause> roots = englishTargets.stream()
                .map(Candidate6CapturedCase::classification)
                .collect(Collectors.toSet());
        if (englishTargets.size() != 8 || roots.size() != 1) {
            return RagCandidate6DiagnosticSupport.CounterfactualMode.NONE;
        }
        RagCandidate6DiagnosticSupport.RootCause root = roots.iterator().next();
        if (root == RagCandidate6DiagnosticSupport.RootCause.JAVA_KEYWORD_RANK_SUPPRESSION) {
            return RagCandidate6DiagnosticSupport.CounterfactualMode.JAVA;
        }
        if (root != RagCandidate6DiagnosticSupport.RootCause.SQL_PRELIMIT_RANK_SUPPRESSION) {
            return RagCandidate6DiagnosticSupport.CounterfactualMode.NONE;
        }
        long sqlRecoverable = englishTargets.stream().filter(item ->
                containsCandidate6Id(item.sqlKeyword(), item.exactEvidence())).count();
        long dualRecoverable = englishTargets.stream().filter(item ->
                containsCandidate6Id(item.dualKeyword(), item.exactEvidence())).count();
        if (sqlRecoverable == englishTargets.size()) {
            return RagCandidate6DiagnosticSupport.CounterfactualMode.SQL;
        }
        if (sqlRecoverable == 0L && dualRecoverable == englishTargets.size()) {
            return RagCandidate6DiagnosticSupport.CounterfactualMode.DUAL;
        }
        return RagCandidate6DiagnosticSupport.CounterfactualMode.NONE;
    }

    private static Candidate6Replay replayCandidate6(
            Candidate6CapturedCase captured,
            RagCandidate6DiagnosticSupport.CounterfactualMode mode,
            CandidateFusionService fusionService,
            RagRerankService rerankService,
            RagContextBuilder contextBuilder,
            CalibrationStageDiagnostics diagnostics,
            QueryCounters queryCounters) {
        List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> keyword = switch (mode) {
            case SQL -> captured.sqlKeyword();
            case JAVA -> captured.javaKeyword();
            case DUAL -> captured.dualKeyword();
            case NONE -> captured.baselineKeyword();
        };
        List<List<RetrievalResult>> pathResults = new ArrayList<>();
        List<String> pathNames = new ArrayList<>();
        for (String pathName : List.of("vector", "keyword", "metadata", "graph")) {
            List<RetrievalResult> results = "keyword".equals(pathName)
                    ? RagCandidate6DiagnosticSupport.rebuildResults(keyword)
                    : rebuildCandidate6Path(
                            captured.trace(),
                            captured.trace().paths.getOrDefault(pathName, List.of()));
            if (!results.isEmpty()) {
                pathResults.add(results);
                pathNames.add(pathName);
            }
        }
        CalibrationTrace previous = diagnostics.current.getAndSet(null);
        DbCallSnapshot before = queryCounters.snapshot();
        try {
            CandidateFusionService.FusionResult fused =
                    fusionService.fuseWithDiagnostics(pathResults, pathNames);
            List<RetrievalResult> sources = rerankService.rerank(
                    Objects.requireNonNullElse(
                            captured.trace().deterministicQuery,
                            captured.queryCase().retrievalQuery()),
                    copyResults(fused.getResults()),
                    captured.trace().queryIntent,
                    10,
                    null,
                    null);
            List<RetrievalResult> immutableSources = copyResults(sources);
            String context = contextBuilder.buildContext(
                    copyResults(immutableSources), true);
            DbCallSnapshot calls = queryCounters.snapshot().minus(before);
            candidate6Require(calls.keyword() == 0L
                            && calls.metadata() == 0L
                            && calls.vector() == 0L
                            && calls.other() == 0L
                            && calls.contextOther() == 0L,
                    "CANDIDATE6_COUNTERFACTUAL_DB_INVALID");
            return new Candidate6Replay(
                    immutableSources,
                    context == null ? "" : context,
                    calls.context(),
                    contextRanking(context));
        } finally {
            diagnostics.current.set(previous);
        }
    }

    private static List<RetrievalResult> rebuildCandidate6Path(
            CalibrationTrace trace,
            List<RankedSegment> ranking) {
        List<RetrievalResult> results = new ArrayList<>();
        for (RankedSegment item : ranking) {
            RetrievalResult captured = trace.candidatesBySegmentId.get(item.segmentId());
            candidate6Require(captured != null,
                    "CANDIDATE6_PATH_SNAPSHOT_INVALID");
            RetrievalResult copy = copyResults(List.of(captured)).get(0);
            copy.setScore(item.score());
            results.add(copy);
        }
        return List.copyOf(results);
    }

    private static Candidate6CaseArtifact candidate6Artifact(
            Candidate6CapturedCase captured,
            Candidate6Replay replay,
            RagCandidate6DiagnosticSupport.CounterfactualMode selectedMode,
            RagEvaluationDataset dataset) {
        RagEvaluationDataset.QueryCase queryCase = captured.queryCase();
        boolean target = queryCase.familyId().startsWith("c6s-t");
        boolean noExactControl = queryCase.familyId().startsWith("c6s-x");
        boolean safetyControl = queryCase.familyId().startsWith("c6s-s");
        String shape = queryCase.strata().stream()
                .filter(Set.of(
                        "numeric-token", "doc-prefix", "zero-padded", "han-adjacent")::contains)
                .findFirst().orElse("none");
        RagMetrics.Scores baselineMetrics = candidate6Metrics(
                dataset, queryCase, captured.baseline().getSources());
        RagMetrics.Scores counterfactualMetrics = candidate6Metrics(
                dataset, queryCase, replay.sources());
        List<Long> baselineIds = captured.baseline().getSources().stream()
                .map(RetrievalResult::getSegmentId).toList();
        List<Long> counterfactualIds = replay.sources().stream()
                .map(RetrievalResult::getSegmentId).toList();
        List<Double> baselineScores = captured.baseline().getSources().stream()
                .map(RetrievalResult::getScore).toList();
        List<Double> counterfactualScores = replay.sources().stream()
                .map(RetrievalResult::getScore).toList();
        String baselineContext = Objects.requireNonNullElse(
                captured.baseline().getContext(), "");
        String counterfactualContext = replay.context();
        boolean behaviorUnchanged = baselineIds.equals(counterfactualIds)
                && baselineScores.equals(counterfactualScores)
                && sha256Utf8(baselineContext).equals(
                sha256Utf8(counterfactualContext))
                && baselineContext.isEmpty() == counterfactualContext.isEmpty();
        List<Double> baselineScoreSet = baselineScores.stream().sorted().toList();
        List<Double> counterfactualScoreSet = counterfactualScores.stream().sorted().toList();
        Set<String> contextIds = replay.contextRanking().stream()
                .map(RankedSegment::segmentId).collect(Collectors.toSet());
        boolean safetyValid = !safetyControl
                || (baselineScoreSet.equals(counterfactualScoreSet)
                && (!baselineContext.isEmpty() || counterfactualContext.isEmpty())
                && captured.exactEvidence().stream()
                .map(String::valueOf).noneMatch(contextIds::contains));
        long diagnosticTotal = captured.diagnosticSqlCalls()
                + replay.contextDbCalls();
        RagCandidate6DiagnosticSupport.CaseEvidence evidence =
                new RagCandidate6DiagnosticSupport.CaseEvidence(
                        queryCase.id(),
                        queryCase.familyId(),
                        queryCase.language(),
                        shape,
                        target,
                        noExactControl,
                        safetyControl,
                        captured.classification(),
                        selectedMode,
                        baselineMetrics.retrievalApAt10(),
                        baselineMetrics.ndcgAt10(),
                        counterfactualMetrics.retrievalApAt10(),
                        counterfactualMetrics.ndcgAt10(),
                        behaviorUnchanged,
                        safetyValid,
                        captured.sutCalls().total(),
                        captured.diagnosticSqlCalls(),
                        replay.contextDbCalls(),
                        diagnosticTotal,
                        0L, 0L, 0L, 0L, 0L);
        return new Candidate6CaseArtifact(
                evidence,
                queryCase.split(),
                sha256Utf8(queryCase.query()),
                sha256Utf8(queryCase.retrievalQuery()),
                captured.exactEvidence().stream().sorted().toList(),
                captured.variants(),
                captured.trace().paths,
                captured.trace().fusedRanking,
                captured.trace().filterRanking,
                captured.trace().colbertRanking,
                captured.trace().deterministicActualRanking,
                captured.baseline().getSources(),
                replay.sources(),
                sha256Utf8(baselineContext),
                sha256Utf8(counterfactualContext),
                baselineContext.isEmpty(),
                counterfactualContext.isEmpty());
    }

    private static RagMetrics.Scores candidate6Metrics(
            RagEvaluationDataset dataset,
            RagEvaluationDataset.QueryCase queryCase,
            List<RetrievalResult> sources) {
        if (!queryCase.answerable()) {
            return new RagMetrics.Scores(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        return RagMetrics.evaluate(
                dataset.qrelsFor(queryCase.id()),
                sources.stream().map(RetrievalResult::getSegmentId)
                        .map(String::valueOf).toList());
    }

    private static List<Long> candidate6Ids(
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> values) {
        return values.stream().map(
                RagCandidate6DiagnosticSupport.RetrievalSnapshot::segmentId).toList();
    }

    private static boolean containsCandidate6Id(
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> values,
            Set<Long> targets) {
        return values.stream().map(
                        RagCandidate6DiagnosticSupport.RetrievalSnapshot::segmentId)
                .anyMatch(targets::contains);
    }

    private static RagResult copyCandidate6Result(RagResult source) {
        return RagResult.builder()
                .sources(copyResults(source.getSources()))
                .context(Objects.requireNonNullElse(source.getContext(), ""))
                .debugInfo(Map.of())
                .build();
    }

    private static Map<String, Object> candidate6InvalidDiagnosticRoot(
            RagCandidate6DiagnosticSupport.FrozenDataset frozenSelection,
            Throwable failure) {
        String errorCode = failure instanceof Candidate6DiagnosticFailure diagnosticFailure
                ? diagnosticFailure.errorCode
                : failure instanceof AssertionError
                ? "CANDIDATE6_ARTIFACT_INVALID"
                : "CANDIDATE6_EXTERNAL_CALL_FAILED";
        return RagCandidate6DiagnosticSupport.freshInvalidArtifact(
                frozenSelection, candidate6Config(), errorCode);
    }

    private static Map<String, Object> candidate6Config() {
        Map<String, Object> config = new LinkedHashMap<>(candidate5Config());
        config.put("candidate6EvidenceAlgorithm",
                "identifier-admission-to-ranking-diagnostic-v1");
        config.put("identifierEvidencePriorityEnabled", false);
        config.put("identifierEvidencePriorityAlgorithm", "unselected");
        config.put("identifierEvidencePrioritySqlPolicy",
                "postgres-posix-c-alnum-v1");
        config.put("keywordCandidateTopK", 50);
        config.put("keywordSqlLimit", 500);
        return config;
    }

    private static Map<String, Object> candidate8InvalidDiagnosticRoot(
            RagCandidate8DiagnosticSupport.FrozenDataset frozenSelection,
            Throwable failure) {
        String errorCode = failure instanceof Candidate8DiagnosticFailure diagnosticFailure
                ? diagnosticFailure.errorCode
                : failure instanceof AssertionError
                ? "CANDIDATE8_ARTIFACT_INVALID"
                : "CANDIDATE8_EXTERNAL_CALL_FAILED";
        return RagCandidate8DiagnosticSupport.freshInvalidArtifact(
                frozenSelection, candidate8Config(), errorCode);
    }

    private static Map<String, Object> candidate8Config() {
        Map<String, Object> config = new LinkedHashMap<>(candidate6Config());
        config.put("candidate3Enabled", true);
        config.put("candidate8Enabled", false);
        config.put("candidate8EvidenceAlgorithm",
                "corroborated-identifier-cutoff-tie-diagnostic-v1");
        config.put("identifierEvidenceEligibilityPolicy",
                "deterministic-config-fail-closed-v2");
        config.put("identifierEvidenceAdmissionPolicy",
                "document-name-content-corroborated-exact-first-v1");
        config.put("identifierEvidenceSurvivalPolicy",
                "colbert-cutoff-tie-replacement-v1");
        config.put("identifierEvidenceContentPolicy",
                "ascii-han-non-identifier-content-v1");
        config.put("identifierEvidenceColbertFailurePolicy",
                "request-fail-closed-v1");
        config.put("identifierEvidenceCorroboratedTieEnabled", false);
        config.put("localRerankerEnabled", false);
        config.put("onnxRerankerEnabled", false);
        config.put("keywordCandidateTopK", 50);
        config.put("keywordSqlLimit", 500);
        config.put("businessColbertLimit", 30);
        config.put("kbRemoteReranking", Map.of(
                "enabled", false,
                "providerName", "",
                "modelName", "",
                "providerConfigured", false,
                "modelConfigured", false));
        String providerPackage =
                "tech.qiantong.qknow.module.kmc.service.rag.rerank.";
        config.put("providerInventory", List.of(
                providerPackage + "DeterministicRerankerProvider"));
        config.put("eligibleProviderClasses", List.of(
                providerPackage + "DashScopeRerankerProvider",
                providerPackage + "DeterministicRerankerProvider",
                providerPackage + "LocalBgeRerankerProvider",
                providerPackage + "LocalRerankerProvider",
                providerPackage + "OnnxRerankerProvider"));
        return config;
    }

    private static Map<String, Object> candidate9InvalidDiagnosticRoot(
            RagCandidate9DiagnosticSupport.FrozenDataset frozenSelection,
            Throwable failure) {
        String errorCode = failure instanceof Candidate9DiagnosticFailure diagnosticFailure
                ? diagnosticFailure.errorCode
                : failure instanceof RagCandidate9DiagnosticSupport
                .FrozenInputChangedException frozenInputChanged
                ? frozenInputChanged.errorCode()
                : failure instanceof AssertionError
                ? "CANDIDATE9_ARTIFACT_INVALID"
                : "CANDIDATE9_EXTERNAL_CALL_FAILED";
        return RagCandidate9DiagnosticSupport.freshInvalidArtifact(
                frozenSelection, candidate9Config(), errorCode);
    }

    private static String candidate9RecoveryErrorCode(Throwable failure) {
        String legacyErrorCode = failure instanceof Candidate9DiagnosticFailure diagnosticFailure
                ? diagnosticFailure.errorCode
                : failure instanceof RagCandidate9DiagnosticSupport
                .FrozenInputChangedException frozenInputChanged
                ? frozenInputChanged.errorCode()
                : failure instanceof AssertionError
                ? "CANDIDATE9_ARTIFACT_INVALID"
                : "CANDIDATE9_RUNTIME_INVALID";
        return legacyErrorCode.replaceFirst(
                "^CANDIDATE9_", "CANDIDATE9_RECOVERY_");
    }

    private static Map<String, Object> candidate9Config() {
        Map<String, String> command =
                RagCandidate9DiagnosticSupport
                        .requireDiagnosticCommandProperties();
        Map<String, Object> config = new LinkedHashMap<>(shadowConfig());
        config.put("candidate3Enabled", Boolean.parseBoolean(command.get(
                "qknow.rag.rerank.identifier-consistency-enabled")));
        config.put("candidate9Enabled", Boolean.parseBoolean(command.get(
                RagCandidate9DiagnosticSupport.PRODUCTION_PROPERTY)));
        config.put("candidate9DiagnosticEnabled", Boolean.parseBoolean(command.get(
                RagCandidate9DiagnosticSupport.DIAGNOSTIC_PROPERTY)));
        config.put("identifierAware", Boolean.parseBoolean(command.get(
                "qknow.rag.keyword.identifier-aware")));
        config.put("colbert", Map.of(
                "enabled", Boolean.parseBoolean(command.get(
                        "hermes.rag.colbert.enabled")),
                "resolvedMaxTokensPerDoc", Integer.parseInt(command.get(
                        "hermes.rag.colbert.max-tokens-per-doc")),
                "embeddingPlatformPresent", !command.get(
                        "hermes.rag.colbert.embedding-platform").isEmpty(),
                "embeddingBaseUrlPresent", !command.get(
                        "hermes.rag.colbert.embedding-base-url").isEmpty(),
                "embeddingApiKeyPresent", !command.get(
                        "hermes.rag.colbert.embedding-api-key").isEmpty(),
                "embeddingModelPresent", !command.get(
                        "hermes.rag.colbert.embedding-model").isEmpty()));
        config.put("localRerankerEnabled", Boolean.parseBoolean(command.get(
                "qknow.rag.local-reranker.enabled")));
        config.put("onnxRerankerEnabled", Boolean.parseBoolean(command.get(
                "qknow.rag.onnx-reranker.enabled")));
        config.put("promotionEnabled", Boolean.parseBoolean(command.get(
                "rag.eval.promotion")));
        config.put("shadowEnabled", Boolean.parseBoolean(command.get(
                "rag.eval.shadow")));
        config.put("compareStable", Boolean.parseBoolean(command.get(
                "rag.eval.shadow.compare-stable")));
        config.put("fileEncoding", command.get("file.encoding"));
        config.put("kbRemoteReranking", Map.of(
                "enabled", false,
                "providerPresent", false,
                "modelPresent", false));
        String providerPackage =
                "tech.qiantong.qknow.module.kmc.service.rag.rerank.";
        config.put("providerInventory", List.of(
                providerPackage + "DeterministicRerankerProvider"));
        config.put("eligibleProviderClasses", List.of(
                providerPackage + "DashScopeRerankerProvider",
                providerPackage + "DeterministicRerankerProvider",
                providerPackage + "LocalBgeRerankerProvider",
                providerPackage + "LocalRerankerProvider",
                providerPackage + "OnnxRerankerProvider"));
        config.put("topK", 10);
        config.put("businessColbertLimit", 30);
        config.put("keywordSqlLimit", 500);
        config.put("keywordCandidateTopK", 50);
        config.put("fork", Map.of(
                "count", Integer.parseInt(command.get("forkCount")),
                "reuse", Boolean.parseBoolean(command.get("reuseForks"))));
        config.put("locale", command.get("user.language")
                + "-" + command.get("user.country"));
        config.put("timezone", command.get("user.timezone"));
        config.put("native", Map.of(
                "available", JiebaNative.isAvailable()
                        || VecSimNative.isAvailable()
                        || ColbertNative.isAvailable(),
                "pathPresent", !command.get("qknow.native.lib.dir").isBlank(),
                "noNativeLibraryPathPresent", true));
        config.put("featureSourceHash", candidate9FeatureSourceHashes());
        config.put("candidate9ProjectionPolicy",
                "matched-visible-identifiers-prefix-v1");
        config.put("candidate9QueryVisibilityPolicy",
                "ascii-space-visible-identifier-v1");
        config.put("candidate9BoundaryPolicy",
                "unicode-letter-number-boundary-v1");
        config.put("candidate9TokenPolicy", "prefix-head-128-v1");
        config.put("candidate9EligibilityPolicy",
                "hash-colbert-deterministic-fail-closed-v1");
        config.put("candidate9FailurePolicy",
                "active-request-propagation-v1");
        config.put("candidate9EvaluationPolicy", "qrel-after-ranking-v1");
        return config;
    }

    private static Map<String, String> candidate9FeatureSourceHashes() {
        Path backend = Path.of(".").toAbsolutePath().normalize();
        if (backend.getFileName() != null
                && "tests".equals(backend.getFileName().toString())) {
            backend = backend.getParent();
        }
        Map<String, String> sources = new TreeMap<>();
        Map<String, Path> files = Map.of(
                "ColbertScorer", backend.resolve(
                        "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java"),
                "DeterministicRerankerProvider", backend.resolve(
                        "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/DeterministicRerankerProvider.java"),
                "KeywordRetriever", backend.resolve(
                        "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/KeywordRetriever.java"),
                "RagContextBuilder", backend.resolve(
                        "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagContextBuilder.java"),
                "RagRerankService", backend.resolve(
                        "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRerankService.java"),
                "RagRetrievalService", backend.resolve(
                        "qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java"));
        files.forEach((name, path) -> {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
                throw new Candidate9DiagnosticFailure(
                        "CANDIDATE9_FEATURE_SOURCE_INVALID");
            }
            sources.put(name, ShadowContractSupport.sha256(path));
        });
        return Map.copyOf(sources);
    }

    private static Map<String, Object> candidate5Config() {
        Map<String, Object> config = new LinkedHashMap<>(shadowConfig());
        config.put("routerEnabled", false);
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", true);
        config.put("identifierRecallConsistencyEnabled", false);
        config.put("candidate5EvidenceAlgorithm",
                "multilingual-identifier-recall-mechanistic-v1");
        config.put("identifierRecallConsistencyAlgorithm",
                "document-name-boundary-predicate-v1");
        config.put("identifierRecallConsistencySqlPolicy",
                "postgres-posix-c-alnum-v1");
        return config;
    }

    private static Map<String, List<RagCandidate5DiagnosticSupport.RankedSegment>>
    candidate5Paths(Map<String, List<RankedSegment>> paths) {
        Map<String, List<RagCandidate5DiagnosticSupport.RankedSegment>> converted =
                new LinkedHashMap<>();
        paths.forEach((name, ranking) -> converted.put(name, candidate5Ranking(ranking)));
        return converted;
    }

    private static List<RagCandidate5DiagnosticSupport.RankedSegment> candidate5Ranking(
            List<RankedSegment> ranking) {
        return ranking.stream().map(item ->
                new RagCandidate5DiagnosticSupport.RankedSegment(
                        item.segmentId(), item.rank(), item.score())).toList();
    }

    private static List<RagCandidate5DiagnosticSupport.RankedSegment>
    candidate5OracleRanking(Map<String, Integer> qrels, List<RankedSegment> actual) {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        qrels.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .forEach(order::add);
        actual.stream().map(RankedSegment::segmentId).forEach(order::add);
        List<RagCandidate5DiagnosticSupport.RankedSegment> ranking = new ArrayList<>();
        int rank = 1;
        for (String segmentId : order) {
            if (rank > 10) {
                break;
            }
            ranking.add(new RagCandidate5DiagnosticSupport.RankedSegment(
                    segmentId, rank++, 0.0D));
        }
        return List.copyOf(ranking);
    }

    @SuppressWarnings("unchecked")
    private static List<String> candidate5SearchTerms(String query) throws Exception {
        java.lang.reflect.Method method = KeywordRetriever.class.getDeclaredMethod(
                "buildSearchTerms", String.class, boolean.class);
        method.setAccessible(true);
        return List.copyOf((List<String>) invokeStatic(method, query, false));
    }

    private static List<String> candidate5NumericIdentifiers(String query) {
        Matcher matcher = Pattern.compile(
                "(?<![\\p{L}\\p{N}])\\d{3,}(?![\\p{L}\\p{N}])").matcher(query);
        List<String> identifiers = new ArrayList<>();
        while (matcher.find()) {
            identifiers.add(matcher.group());
        }
        return List.copyOf(identifiers);
    }

    private static boolean candidate5BoundaryMatch(String text, String identifier) {
        return Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(identifier)
                + "(?![A-Za-z0-9])").matcher(text).find();
    }

    private static boolean candidate5DocumentFieldsConsistent(
            JdbcTemplate jdbc, RagEvaluationDataset.CorpusSegment anchor) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.name AS document_name, s.document_name AS segment_document_name
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id = ? AND s.id = ?
                """, RagCandidate5DiagnosticSupport.SELECTION_KB_ID,
                Long.parseLong(anchor.segmentId()));
        if (rows.size() != 1) {
            return false;
        }
        String expected = candidate5DocumentName(anchor);
        return expected.equals(rows.get(0).get("document_name"))
                && expected.equals(rows.get(0).get("segment_document_name"));
    }

    private static void assertCandidate5ContextIsolation(
            String queryId, RagResult result, List<RankedSegment> contextRanking) {
        for (RetrievalResult source : result.getSources()) {
            candidate5Require(source.getSegmentId() != null
                            && source.getSegmentId()
                            >= RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                            && source.getSegmentId()
                            <= RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MAX,
                    "CANDIDATE5_KB_ISOLATION_FAILED");
            candidate5Require(source.getDocumentId() != null
                            && source.getDocumentId()
                            >= RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                            && source.getDocumentId()
                            <= RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                    "CANDIDATE5_KB_ISOLATION_FAILED");
        }
        candidate5Require(contextRanking.stream().allMatch(item -> {
            long segmentId = Long.parseLong(item.segmentId());
            return segmentId >= RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                    && segmentId <= RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MAX;
        }), "CANDIDATE5_KB_ISOLATION_FAILED");
    }

    private static void assertCandidate5StageIsolation(
            String queryId, CalibrationTrace trace) {
        trace.paths.forEach((pathName, ranking) ->
                assertCandidate5RankingIsolation(
                        queryId, "path:" + pathName, ranking));
        assertCandidate5RankingIsolation(queryId, "fused", trace.fusedRanking);
        assertCandidate5RankingIsolation(queryId, "filter", trace.filterRanking);
        assertCandidate5RankingIsolation(queryId, "final", trace.finalRanking);
    }

    private static void assertCandidate5RankingIsolation(
            String queryId, String stage, List<RankedSegment> ranking) {
        candidate5Require(ranking.stream().allMatch(item -> {
            long segmentId = Long.parseLong(item.segmentId());
            return segmentId >= RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                    && segmentId <= RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MAX;
        }), "CANDIDATE5_KB_ISOLATION_FAILED");
    }

    private static void assertCandidate6StageIsolation(CalibrationTrace trace) {
        trace.paths.forEach((pathName, ranking) ->
                assertCandidate6RankingIsolation(ranking));
        assertCandidate6RankingIsolation(trace.fusedRanking);
        assertCandidate6RankingIsolation(trace.filterRanking);
        assertCandidate6RankingIsolation(trace.colbertRanking);
        assertCandidate6RankingIsolation(trace.finalRanking);
        assertCandidate6RankingIsolation(trace.contextRanking);
    }

    private static void assertCandidate8StageIsolation(CalibrationTrace trace) {
        trace.paths.forEach((pathName, ranking) ->
                assertCandidate8RankingIsolation(ranking));
        assertCandidate8RankingIsolation(trace.fusedRanking);
        assertCandidate8RankingIsolation(trace.filterRanking);
        assertCandidate8RankingIsolation(trace.colbertRanking);
        assertCandidate8RankingIsolation(trace.fullColbertRanking);
        assertCandidate8RankingIsolation(trace.deterministicActualRanking);
        assertCandidate8RankingIsolation(trace.finalRanking);
        assertCandidate8RankingIsolation(trace.contextRanking);
        for (Document document : trace.candidate8FilterDocuments) {
            Object value = document.getMetadata().get("segmentId");
            candidate8Require(value != null,
                    "CANDIDATE8_KB_ISOLATION_FAILED");
            long segmentId = Long.parseLong(String.valueOf(value));
            candidate8Require(segmentId
                            >= RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                            && segmentId
                            <= RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MAX,
                    "CANDIDATE8_KB_ISOLATION_FAILED");
        }
    }

    private static void assertCandidate6RankingIsolation(
            List<RankedSegment> ranking) {
        candidate6Require(ranking.stream().allMatch(item -> {
            long segmentId = Long.parseLong(item.segmentId());
            return segmentId
                    >= RagCandidate6DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                    && segmentId
                    <= RagCandidate6DiagnosticSupport.SELECTION_SEGMENT_ID_MAX;
        }), "CANDIDATE6_KB_ISOLATION_FAILED");
    }

    private static void assertCandidate8RankingIsolation(
            List<RankedSegment> ranking) {
        candidate8Require(ranking.stream().allMatch(item -> {
            long segmentId = Long.parseLong(item.segmentId());
            return segmentId
                    >= RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                    && segmentId
                    <= RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MAX;
        }), "CANDIDATE8_KB_ISOLATION_FAILED");
    }

    private static String hashStrings(List<String> values) {
        return sha256Utf8(String.join("\u0000", values));
    }

    private static void candidate5Require(boolean condition, String errorCode) {
        if (!condition) {
            throw new Candidate5DiagnosticFailure(errorCode);
        }
    }

    private static Candidate3Evidence candidate3Evidence(
            RagEvaluationDataset.QueryCase queryCase,
            Map<String, Integer> qrels,
            CalibrationTrace trace,
            RagContextBuilder contextBuilder) throws Exception {
        List<String> identifiers = candidate3IdentifierTerms(trace.deterministicQuery);
        List<Pattern> identifierPatterns = identifiers.stream()
                .map(identifier -> Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(identifier)
                                + "(?![\\p{L}\\p{N}])"))
                .toList();
        List<RetrievalResult> detachedFull = copyResults(trace.deterministicFullResults);
        List<Double> rankScores = detachedFull.stream()
                .map(RetrievalResult::getScore).toList();
        List<Candidate3RankedCandidate> originalOrder = new ArrayList<>(detachedFull.size());
        for (int index = 0; index < detachedFull.size(); index++) {
            RetrievalResult result = detachedFull.get(index);
            originalOrder.add(new Candidate3RankedCandidate(
                    result, index + 1, candidate3IdentifierMatch(result, identifierPatterns)));
        }
        List<Candidate3RankedCandidate> finalOrder = new ArrayList<>(originalOrder.size());
        originalOrder.stream().filter(Candidate3RankedCandidate::identifierMatch)
                .forEach(finalOrder::add);
        originalOrder.stream().filter(candidate -> !candidate.identifierMatch())
                .forEach(finalOrder::add);
        for (int index = 0; index < finalOrder.size(); index++) {
            finalOrder.get(index).result().setScore(rankScores.get(index));
        }

        int resultSize = Math.min(10, finalOrder.size());
        List<RetrievalResult> counterfactualResults = copyResults(finalOrder.subList(0, resultSize).stream()
                .map(Candidate3RankedCandidate::result).toList());
        List<Double> finalScores = counterfactualResults.stream()
                .map(RetrievalResult::getScore).toList();
        List<Double> expectedScores = rankScores.subList(0, resultSize);
        assertEquals(expectedScores, finalScores, queryCase.id() + ": score policy");

        List<String> baselineIds = segmentIds(trace.finalRanking);
        List<Double> baselineScores = trace.finalRanking.stream()
                .map(RankedSegment::score).toList();
        assertEquals(expectedScores, baselineScores,
                queryCase.id() + ": detached deterministic parity");
        List<RankedSegment> counterfactualRanking = snapshotResults(counterfactualResults);
        List<String> counterfactualIds = segmentIds(counterfactualRanking);
        String counterfactualContext = contextBuilder.buildContext(
                copyResults(counterfactualResults), true);
        String counterfactualContextSha256 = sha256Utf8(counterfactualContext);
        boolean counterfactualContextEmpty = counterfactualContext == null
                || counterfactualContext.isEmpty();
        RagMetrics.Scores zeroMetrics = new RagMetrics.Scores(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        RagMetrics.Scores baselineMetrics = qrels.isEmpty()
                ? zeroMetrics : RagMetrics.evaluate(qrels, baselineIds);
        RagMetrics.Scores counterfactualMetrics = qrels.isEmpty()
                ? zeroMetrics : RagMetrics.evaluate(qrels, counterfactualIds);

        Set<String> relevantIds = qrels.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        List<Candidate3RankedCandidate> matched = originalOrder.stream()
                .filter(Candidate3RankedCandidate::identifierMatch).toList();
        boolean uniqueTrustedMatch = matched.size() == 1
                && relevantIds.contains(String.valueOf(matched.get(0).result().getSegmentId()));
        boolean relevantAtRankOne = !counterfactualIds.isEmpty()
                && relevantIds.contains(counterfactualIds.get(0));
        boolean behaviorChanged = !baselineIds.equals(counterfactualIds)
                || !baselineScores.equals(finalScores)
                || !trace.finalContextSha256.equals(counterfactualContextSha256)
                || trace.finalContextEmpty != counterfactualContextEmpty;

        Map<Integer, Integer> counterfactualRankByOriginalRank = new LinkedHashMap<>();
        for (int index = 0; index < finalOrder.size(); index++) {
            counterfactualRankByOriginalRank.put(finalOrder.get(index).originalRank(), index + 1);
        }
        List<Map<String, Object>> ranking = originalOrder.stream().map(candidate -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("segmentId", String.valueOf(candidate.result().getSegmentId()));
            value.put("originalRank", candidate.originalRank());
            value.put("identifierMatch", candidate.identifierMatch());
            value.put("counterfactualRank",
                    counterfactualRankByOriginalRank.get(candidate.originalRank()));
            return value;
        }).toList();
        return new Candidate3Evidence(
                queryCase.id(), queryCase.familyId(), queryCase.split(), queryCase.answerable(),
                ranking, baselineIds, counterfactualIds, baselineScores, finalScores,
                trace.finalContextSha256, counterfactualContextSha256,
                trace.finalContextEmpty, counterfactualContextEmpty,
                baselineMetrics, counterfactualMetrics,
                matched.size(), uniqueTrustedMatch, relevantAtRankOne,
                behaviorChanged, expectedScores.equals(finalScores));
    }

    @SuppressWarnings("unchecked")
    private static List<String> candidate3IdentifierTerms(String query) throws Exception {
        java.lang.reflect.Method method = KeywordRetriever.class
                .getDeclaredMethod("extractIdentifierTerms", String.class);
        method.setAccessible(true);
        try {
            return List.copyOf((List<String>) method.invoke(null, query));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static boolean candidate3IdentifierMatch(
            RetrievalResult result, List<Pattern> identifierPatterns) {
        if (result == null || result.getDocumentName() == null) {
            return false;
        }
        return identifierPatterns.stream()
                .anyMatch(pattern -> pattern.matcher(result.getDocumentName()).find());
    }

    private static CalibrationEvidence classifyCalibrationCase(
            CalibrationTrace trace, Map<String, Integer> qrels) {
        assertEquals(1, qrels.size(), "calibration fixture must have one relevant segment");
        RagMetrics.Scores finalScores = evaluate(qrels, trace.finalRanking);
        boolean contextComplete = trace.contextRanking.stream()
                .map(RankedSegment::segmentId)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(qrels.keySet());
        if (finalScores.retrievalApAt10() == 1.0D && finalScores.ndcgAt10() == 1.0D) {
            String classification = contextComplete ? "NONE" : "CONTEXT_ONLY";
            return new CalibrationEvidence(trace, classification,
                    finalScores, finalScores, qrels.size(), null);
        }

        Map.Entry<String, List<RankedSegment>> bestPath = trace.paths.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .max((left, right) -> compareScores(
                        evaluate(qrels, left.getValue()), evaluate(qrels, right.getValue())))
                .orElse(null);
        if (bestPath == null || bestPath.getValue().stream()
                .map(RankedSegment::segmentId).noneMatch(qrels::containsKey)) {
            return new CalibrationEvidence(trace, "RETRIEVAL_MISS",
                    finalScores, finalScores, qrels.size(), null);
        }

        RagMetrics.Scores bestPathScores = evaluate(qrels, bestPath.getValue());
        RagMetrics.Scores fusedScores = evaluate(qrels, trace.fusedRanking);
        if (compareScores(bestPathScores, fusedScores) > 0
                && compareScores(bestPathScores, finalScores) > 0) {
            return new CalibrationEvidence(trace, "FUSION_SUPPRESSION",
                    finalScores, bestPathScores, qrels.size(), bestPath.getKey());
        }

        if (!containsAllRelevant(trace.filterRanking, qrels)) {
            return new CalibrationEvidence(trace, "FILTER_DELETION",
                    finalScores, fusedScores, qrels.size(), null);
        }

        RagMetrics.Scores filterScores = evaluate(qrels, trace.filterRanking);
        RagMetrics.Scores colbertScores = evaluate(qrels, trace.colbertRanking);
        if (!containsAllRelevant(trace.colbertRanking, qrels)
                || (compareScores(filterScores, colbertScores) > 0
                && compareScores(filterScores, finalScores) > 0)) {
            return new CalibrationEvidence(trace, "COLBERT_SUPPRESSION",
                    finalScores, filterScores, qrels.size(), null);
        }

        RagMetrics.Scores deterministicScores = evaluate(qrels, trace.deterministicFullRanking);
        if (!containsAllRelevant(trace.deterministicFullRanking, qrels)
                || (compareScores(colbertScores, deterministicScores) > 0
                && compareScores(colbertScores, finalScores) > 0)) {
            return new CalibrationEvidence(trace, "DETERMINISTIC_INVERSION",
                    finalScores, colbertScores, qrels.size(), null);
        }
        return new CalibrationEvidence(trace, "FINAL_RANKING_WEAKNESS",
                finalScores, finalScores, qrels.size(), null);
    }

    private static RagMetrics.Scores evaluate(
            Map<String, Integer> qrels, List<RankedSegment> ranking) {
        return RagMetrics.evaluate(qrels, segmentIds(ranking));
    }

    private static int compareScores(RagMetrics.Scores left, RagMetrics.Scores right) {
        int ap = Double.compare(left.retrievalApAt10(), right.retrievalApAt10());
        return ap != 0 ? ap : Double.compare(left.ndcgAt10(), right.ndcgAt10());
    }

    private static boolean containsAllRelevant(
            List<RankedSegment> ranking, Map<String, Integer> qrels) {
        Set<String> ids = ranking.stream().map(RankedSegment::segmentId)
                .collect(java.util.stream.Collectors.toSet());
        return ids.containsAll(qrels.keySet());
    }

    private static List<String> segmentIds(List<RankedSegment> ranking) {
        return ranking.stream().map(RankedSegment::segmentId).toList();
    }

    private static List<RankedSegment> snapshotResults(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Double> unique = new LinkedHashMap<>();
        for (RetrievalResult result : results) {
            if (result != null && result.getSegmentId() != null) {
                unique.putIfAbsent(String.valueOf(result.getSegmentId()), result.getScore());
            }
        }
        return rankedSegments(unique);
    }

    private static List<RankedSegment> snapshotDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Double> unique = new LinkedHashMap<>();
        for (Document document : documents) {
            Object rawId = document.getMetadata().get("segmentId");
            if (rawId == null) {
                continue;
            }
            Object rawScore = document.getMetadata().get("colbert_score");
            if (!(rawScore instanceof Number)) {
                rawScore = document.getMetadata().get("score");
            }
            double score = rawScore instanceof Number number ? number.doubleValue() : 0.0D;
            unique.putIfAbsent(String.valueOf(rawId), score);
        }
        return rankedSegments(unique);
    }

    private static Long candidate9DocumentSegmentId(Document document) {
        if (document == null || document.getMetadata() == null) {
            return null;
        }
        Object value = document.getMetadata().get("segmentId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<RankedSegment> contextRanking(String context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Double> unique = new LinkedHashMap<>();
        Matcher matcher = CONTEXT_SEGMENT_MARKER.matcher(context);
        while (matcher.find()) {
            unique.putIfAbsent(matcher.group(1), 0.0D);
        }
        return rankedSegments(unique);
    }

    private static List<RankedSegment> rankedSegments(LinkedHashMap<String, Double> unique) {
        List<RankedSegment> ranking = new ArrayList<>(unique.size());
        int rank = 1;
        for (Map.Entry<String, Double> entry : unique.entrySet()) {
            ranking.add(new RankedSegment(entry.getKey(), rank++, entry.getValue()));
        }
        return List.copyOf(ranking);
    }

    private static List<RetrievalResult> copyResults(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }
        return results.stream().map(result -> RetrievalResult.builder()
                .segmentId(result.getSegmentId())
                .qmSegmentId(result.getQmSegmentId())
                .parentSegmentId(result.getParentSegmentId())
                .documentId(result.getDocumentId())
                .documentName(result.getDocumentName())
                .content(result.getContent())
                .answer(result.getAnswer())
                .score(result.getScore())
                .source(result.getSource())
                .metadata(result.getMetadata() == null
                        ? null : new LinkedHashMap<>(result.getMetadata()))
                .build()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static String sha256Utf8(String value) {
        return ShadowContractSupport.sha256(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeCandidate2CalibrationDiagnostic(
            int queryCount, List<CalibrationEvidence> evidence) throws IOException {
        List<CalibrationEvidence> affected = evidence.stream()
                .filter(item -> !"NONE".equals(item.classification()))
                .filter(item -> !"CONTEXT_ONLY".equals(item.classification()))
                .toList();
        Map<String, List<CalibrationEvidence>> byCategory = evidence.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CalibrationEvidence::classification, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        List<Map<String, Object>> categorySummary = new ArrayList<>();
        for (Map.Entry<String, List<CalibrationEvidence>> entry : byCategory.entrySet()) {
            double apUpper = entry.getValue().stream()
                    .mapToDouble(CalibrationEvidence::recoverableAp).sum() / evidence.size();
            double ndcgUpper = entry.getValue().stream()
                    .mapToDouble(CalibrationEvidence::recoverableNdcg).sum() / evidence.size();
            categorySummary.add(Map.of(
                    "classification", entry.getKey(),
                    "caseCount", entry.getValue().size(),
                    "relevantSegmentCount", entry.getValue().stream()
                            .mapToInt(CalibrationEvidence::relevantSegmentCount).sum(),
                    "recoverableApAt10UpperBound", apUpper,
                    "recoverableNdcgAt10UpperBound", ndcgUpper));
        }

        Set<String> affectedCategories = affected.stream()
                .map(CalibrationEvidence::classification)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String decision = affected.size() == 3 && affectedCategories.size() == 1
                ? switch (affectedCategories.iterator().next()) {
                    case "FUSION_SUPPRESSION" -> "WEIGHTED_RRF_ELIGIBLE";
                    case "DETERMINISTIC_INVERSION" -> "RECIPROCAL_BLEND_ELIGIBLE";
                    default -> "STOP_UNSUPPORTED_STAGE";
                }
                : "STOP_NO_COMMON_STAGE";

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "VALID");
        output.put("kind", "CANDIDATE2_CALIBRATION_DIAGNOSTIC");
        output.put("queryCount", queryCount);
        output.put("answerableQueryCount", evidence.size());
        output.put("identifierAware", false);
        output.put("testSplitInspected", false);
        output.put("affectedCaseCount", affected.size());
        output.put("affectedQueryIds", affected.stream()
                .map(item -> item.trace().queryId).toList());
        output.put("categorySummary", categorySummary);
        output.put("decision", decision);
        output.put("cases", evidence.stream().map(CalibrationEvidence::toMap).toList());
        Files.writeString(runtimeDirectory().resolve(CANDIDATE2_DIAGNOSTIC_REPORT),
                JSON.toJSONString(output, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
    }

    private static void writeInvalidCandidate2Diagnostic(
            String errorCode, String queryId, String exceptionClass) throws IOException {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "INVALID");
        output.put("errorCode", errorCode);
        output.put("queryId", queryId);
        output.put("exceptionClass", exceptionClass);
        Files.writeString(runtimeDirectory().resolve(CANDIDATE2_DIAGNOSTIC_REPORT),
                JSON.toJSONString(output, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
    }

    private static void writeCandidate3CalibrationDiagnostic(
            RagEvaluationDataset dataset, List<Candidate3Evidence> evidence) throws IOException {
        List<Candidate3Evidence> answerable = evidence.stream()
                .filter(Candidate3Evidence::answerable).toList();
        double baselineAp = answerable.stream()
                .mapToDouble(item -> item.baselineMetrics().retrievalApAt10())
                .average().orElse(0.0D);
        double counterfactualAp = answerable.stream()
                .mapToDouble(item -> item.counterfactualMetrics().retrievalApAt10())
                .average().orElse(0.0D);
        double baselineNdcg = answerable.stream()
                .mapToDouble(item -> item.baselineMetrics().ndcgAt10())
                .average().orElse(0.0D);
        double counterfactualNdcg = answerable.stream()
                .mapToDouble(item -> item.counterfactualMetrics().ndcgAt10())
                .average().orElse(0.0D);
        Set<String> changedCases = evidence.stream()
                .filter(Candidate3Evidence::behaviorChanged)
                .map(Candidate3Evidence::queryId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean targetCasesPass = CANDIDATE3_TARGET_CASES.stream().allMatch(queryId ->
                evidence.stream().filter(item -> queryId.equals(item.queryId())).findFirst()
                        .map(item -> item.uniqueTrustedMatch()
                                && item.relevantAtRankOne()
                                && item.counterfactualMetrics().retrievalApAt10()
                                > item.baselineMetrics().retrievalApAt10()
                                && item.counterfactualMetrics().ndcgAt10()
                                > item.baselineMetrics().ndcgAt10())
                        .orElse(false));
        boolean nonTargetsUnchanged = evidence.stream()
                .filter(item -> !CANDIDATE3_TARGET_CASES.contains(item.queryId()))
                .allMatch(item -> !item.behaviorChanged());
        boolean noAnswerableRegression = answerable.stream().allMatch(item ->
                item.counterfactualMetrics().retrievalApAt10()
                        >= item.baselineMetrics().retrievalApAt10()
                        && item.counterfactualMetrics().ndcgAt10()
                        >= item.baselineMetrics().ndcgAt10());
        boolean supported = evidence.size() == 24
                && answerable.size() == 16
                && targetCasesPass
                && nonTargetsUnchanged
                && changedCases.equals(CANDIDATE3_TARGET_CASES)
                && noAnswerableRegression
                && counterfactualAp > baselineAp
                && counterfactualNdcg > baselineNdcg
                && evidence.stream().allMatch(Candidate3Evidence::scorePolicyValid);

        Map<String, Object> config = new LinkedHashMap<>(shadowConfig());
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", false);
        config.put("identifierConsistencyAlgorithm", CANDIDATE3_ALGORITHM);
        config.put("identifierConsistencyScorePolicy", CANDIDATE3_SCORE_POLICY);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "VALID");
        output.put("kind", "CANDIDATE3_CALIBRATION_DIAGNOSTIC");
        output.put("decision", supported
                ? "PROCEED_IDENTIFIER_CONSISTENCY" : "STOP");
        output.put("errorCode", supported
                ? null : "STOP_IDENTIFIER_CONSISTENCY_UNSUPPORTED");
        output.put("datasetHash", ShadowContractSupport.datasetHash());
        output.put("config", config);
        output.put("configHash", ShadowContractSupport.configHash(config));
        output.put("identifierAware", false);
        output.put("testSplitInspected", false);
        output.put("queryCount", evidence.size());
        output.put("answerableQueryCount", answerable.size());
        output.put("changedQueryIds", changedCases);
        output.put("metrics", Map.of(
                "baselineRetrievalAP@10", baselineAp,
                "counterfactualRetrievalAP@10", counterfactualAp,
                "baselineNDCG@10", baselineNdcg,
                "counterfactualNDCG@10", counterfactualNdcg));
        output.put("cases", evidence.stream().map(Candidate3Evidence::toMap).toList());
        Files.writeString(runtimeDirectory().resolve(CANDIDATE3_DIAGNOSTIC_REPORT),
                JSON.toJSONString(output, JSONWriter.Feature.PrettyFormat,
                        JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
    }

    private static void writeInvalidCandidate3Diagnostic(
            String errorCode, String queryId, String exceptionClass) throws IOException {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "INVALID");
        output.put("decision", "STOP");
        output.put("errorCode", errorCode);
        output.put("failedQueryId", queryId);
        output.put("exceptionClass", exceptionClass);
        Files.writeString(runtimeDirectory().resolve(CANDIDATE3_DIAGNOSTIC_REPORT),
                JSON.toJSONString(output, JSONWriter.Feature.PrettyFormat,
                        JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
    }

    private static void awaitExecutorIdle(ThreadPoolTaskExecutor executor) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.getActiveCount() != 0
                || !executor.getThreadPoolExecutor().getQueue().isEmpty()) {
            if (System.nanoTime() >= deadline) {
                fail("retrieval executor did not become idle");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    @SuppressWarnings("unchecked")
    private static void measureDetachedTermExtraction(List<DiagnosticSample> samples) throws Exception {
        java.lang.reflect.Method buildSearchTerms = KeywordRetriever.class
                .getDeclaredMethod("buildSearchTerms", String.class, boolean.class);
        java.lang.reflect.Method extractDayTerms = KeywordRetriever.class
                .getDeclaredMethod("extractDayTerms", String.class);
        java.lang.reflect.Method expandWithSynonyms = KeywordRetriever.class
                .getDeclaredMethod("expandWithSynonyms", List.class);
        buildSearchTerms.setAccessible(true);
        extractDayTerms.setAccessible(true);
        expandWithSynonyms.setAccessible(true);
        long checksum = 0L;
        for (DiagnosticSample sample : samples) {
            List<String> variants;
            synchronized (sample.fullPathVariants) {
                variants = new ArrayList<>(sample.fullPathVariants);
                sample.fullPathVariants.clear();
            }
            sample.fullPathVariantCount = variants.size();
            boolean identifierAware = "C".equals(sample.arm);
            for (String variant : variants) {
                long start = System.nanoTime();
                List<String> terms = (List<String>) invokeStatic(
                        buildSearchTerms, variant, identifierAware);
                List<String> dayTerms = (List<String>) invokeStatic(extractDayTerms, variant);
                List<String> expanded = (List<String>) invokeStatic(expandWithSynonyms, terms);
                sample.termExtractionNs.addAndGet(System.nanoTime() - start);
                checksum += terms.size() + dayTerms.size() + expanded.size();
            }
        }
        assertTrue(checksum > 0L, "detached term probe must consume its results");
    }

    private static Object invokeStatic(java.lang.reflect.Method method, Object... args) throws Exception {
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static void assertDiagnosticHealthy(DiagnosticSample sample) throws IOException {
        if (sample.jdbcFailureClass.get() != null) {
            writeInvalidIdentifierDiagnostic("KEYWORD_JDBC_FAILURE", sample);
            fail("identifier latency diagnostic invalid: keyword JDBC failure");
        }
        if (sample.retrievalFailureClass.get() != null) {
            writeInvalidIdentifierDiagnostic("IDENTIFIER_DIAGNOSTIC_RETRIEVAL_FAILURE", sample);
            fail("identifier latency diagnostic invalid: retrieval failure");
        }
    }

    private static void writeInvalidIdentifierDiagnostic(
            String errorCode, DiagnosticSample sample) throws IOException {
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("status", "INVALID");
        invalid.put("errorCode", errorCode);
        invalid.put("round", sample.round);
        invalid.put("arm", sample.arm);
        invalid.put("queryId", sample.queryId);
        String failureClass = sample.jdbcFailureClass.get() != null
                ? sample.jdbcFailureClass.get() : sample.retrievalFailureClass.get();
        invalid.put("exceptionClass", failureClass);
        Files.writeString(runtimeReportPath(),
                JSON.toJSONString(invalid, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
        Files.deleteIfExists(runtimeDbCallTracePath());
        Files.deleteIfExists(runtimeDirectory().resolve("shadow-contexts.jsonl"));
        Files.deleteIfExists(runtimeDirectory().resolve("shadow-labels.jsonl"));
        Files.deleteIfExists(runtimeDirectory().resolve("live-report.json"));
    }

    private static void writeIdentifierLatencyDiagnostic(
            List<DiagnosticSample> samples, List<DiagnosticLoop> loops) throws IOException {
        assertEquals(IDENTIFIER_DIAGNOSTIC_ROUNDS * 2 * 96, samples.size());
        List<Map<String, Object>> topDeltas = pairedQueryDeltas(samples);
        Map<String, Object> reproducibility = reproducibility(loops, samples, topDeltas);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "VALID");
        output.put("kind", "IDENTIFIER_LATENCY_DIAGNOSTIC");
        output.put("rounds", IDENTIFIER_DIAGNOSTIC_ROUNDS);
        output.put("detachedProbe", true);
        output.put("queryCount", 96);
        output.put("armOrder", List.of("A/C", "C/A", "A/C", "C/A", "A/C", "C/A"));
        output.put("config", Map.of(
                "A", Map.of("identifierAware", false),
                "C", Map.of("identifierAware", true),
                "nativeMode", "java-fallback",
                "topK", 10));
        output.put("armSummary", Map.of(
                "A", diagnosticArmSummary(samples, "A"),
                "C", diagnosticArmSummary(samples, "C")));
        output.put("loopRuns", loops.stream().map(DiagnosticLoop::toMap).toList());
        output.put("reproducibility", reproducibility);
        output.put("topPairedQueryDeltas", topDeltas.stream().limit(10).toList());
        output.put("targetQueryAttribution", topDeltas.stream()
                .filter(delta -> IDENTIFIER_TARGET_SOURCES.containsKey(delta.get("queryId")))
                .toList());
        output.put("samples", samples.stream().map(DiagnosticSample::toMap).toList());
        Files.writeString(runtimeReportPath(),
                JSON.toJSONString(output, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
    }

    private static Map<String, Object> diagnosticArmSummary(
            List<DiagnosticSample> samples, String arm) {
        List<DiagnosticSample> armSamples = samples.stream()
                .filter(sample -> arm.equals(sample.arm)).toList();
        Map<String, Object> totals = new LinkedHashMap<>();
        Map<String, Object> p50 = new LinkedHashMap<>();
        Map<String, Object> p95 = new LinkedHashMap<>();
        for (String stage : List.of("termExtractionNs", "keywordJdbcNs", "keywordTotalNs",
                "fusionNs", "rerankNs", "contextBuildNs", "totalNs")) {
            List<Long> values = armSamples.stream().map(sample -> sample.stage(stage)).toList();
            totals.put(stage, values.stream().mapToLong(Long::longValue).sum());
            p50.put(stage, percentile(values, 0.50D));
            p95.put(stage, percentile(values, 0.95D));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totals", totals);
        summary.put("p50", p50);
        summary.put("p95", p95);
        summary.put("dbCalls", armSamples.stream().mapToLong(sample -> sample.dbCalls).sum());
        summary.put("embeddingCalls", armSamples.stream().mapToLong(sample -> sample.embeddingCalls).sum());
        summary.put("keywordCandidateCount", armSamples.stream()
                .mapToLong(sample -> sample.keywordCandidateCount.get()).sum());
        summary.put("fusedCandidateCount", armSamples.stream()
                .mapToLong(sample -> sample.fusedCandidateCount.get()).sum());
        summary.put("rerankedCandidateCount", armSamples.stream()
                .mapToLong(sample -> sample.rerankedCandidateCount.get()).sum());
        return summary;
    }

    private static Map<String, Object> reproducibility(
            List<DiagnosticLoop> loops,
            List<DiagnosticSample> samples,
            List<Map<String, Object>> queryDeltas) {
        List<Double> ratios = new ArrayList<>();
        List<Long> totalDeltas = new ArrayList<>();
        Map<Integer, Map<String, DiagnosticLoop>> byRound = new LinkedHashMap<>();
        for (DiagnosticLoop loop : loops) {
            byRound.computeIfAbsent(loop.round, ignored -> new HashMap<>()).put(loop.arm, loop);
        }
        for (Map<String, DiagnosticLoop> round : byRound.values()) {
            DiagnosticLoop baseline = round.get("A");
            DiagnosticLoop candidate = round.get("C");
            ratios.add((double) candidate.totalNs / Math.max(1L, baseline.totalNs));
            totalDeltas.add(candidate.totalNs - baseline.totalNs);
        }
        double medianRatio = medianDouble(ratios);
        int overBudgetRounds = (int) ratios.stream().filter(ratio -> ratio > 1.10D).count();
        boolean regression = medianRatio > 1.10D && overBudgetRounds >= 4;
        long medianTotalDelta = percentile(totalDeltas, 0.50D);

        List<StageEvidence> evidence = List.of(
                stageEvidence("TERM_EXTRACTION", "termExtractionNs", samples,
                        queryDeltas, medianTotalDelta),
                stageEvidence("KEYWORD_JDBC", "keywordJdbcNs", samples,
                        queryDeltas, medianTotalDelta),
                stageEvidence("DOWNSTREAM_CANDIDATE_PROCESSING", "downstreamNs", samples,
                        queryDeltas, medianTotalDelta));
        String rootCause = "MEASUREMENT_VARIANCE";
        if (regression) {
            rootCause = evidence.stream()
                    .filter(StageEvidence::qualifies)
                    .max(Comparator.comparingLong(StageEvidence::medianDeltaNs))
                    .map(StageEvidence::name)
                    .orElse("UNRESOLVED_CONCURRENCY");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("medianCandidateToBaselineRatio", medianRatio);
        result.put("roundsOverTenPercent", overBudgetRounds);
        result.put("reproducibleRegression", regression);
        result.put("rootCause", rootCause);
        result.put("stageEvidence", evidence.stream().map(StageEvidence::toMap).toList());
        return result;
    }

    private static StageEvidence stageEvidence(
            String name,
            String stage,
            List<DiagnosticSample> samples,
            List<Map<String, Object>> queryDeltas,
            long medianTotalDelta) {
        List<Long> roundDeltas = new ArrayList<>();
        for (int round = 1; round <= IDENTIFIER_DIAGNOSTIC_ROUNDS; round++) {
            int currentRound = round;
            long baseline = samples.stream()
                    .filter(sample -> sample.round == currentRound && "A".equals(sample.arm))
                    .mapToLong(sample -> sample.stage(stage)).sum();
            long candidate = samples.stream()
                    .filter(sample -> sample.round == currentRound && "C".equals(sample.arm))
                    .mapToLong(sample -> sample.stage(stage)).sum();
            roundDeltas.add(candidate - baseline);
        }
        long medianDelta = percentile(roundDeltas, 0.50D);
        int positiveRounds = (int) roundDeltas.stream().filter(delta -> delta > 0L).count();
        int positiveTopQueries = (int) queryDeltas.stream().limit(10)
                .filter(delta -> ((Number) delta.get(stage)).longValue() > 0L).count();
        boolean qualifies = positiveRounds >= 5
                && medianDelta >= Math.max(1L, Math.round(medianTotalDelta * 0.50D))
                && positiveTopQueries >= 7;
        return new StageEvidence(name, medianDelta, positiveRounds, positiveTopQueries, qualifies);
    }

    private static List<Map<String, Object>> pairedQueryDeltas(List<DiagnosticSample> samples) {
        Set<String> queryIds = new LinkedHashSet<>();
        samples.stream().filter(sample -> "A".equals(sample.arm))
                .forEach(sample -> queryIds.add(sample.queryId));
        List<Map<String, Object>> deltas = new ArrayList<>();
        for (String queryId : queryIds) {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("queryId", queryId);
            for (String stage : List.of("termExtractionNs", "keywordJdbcNs", "keywordTotalNs",
                    "fusionNs", "rerankNs", "contextBuildNs", "downstreamNs", "totalNs",
                    "keywordCandidateCount", "fusedCandidateCount", "rerankedCandidateCount",
                    "dbCalls", "embeddingCalls")) {
                List<Long> paired = new ArrayList<>();
                for (int round = 1; round <= IDENTIFIER_DIAGNOSTIC_ROUNDS; round++) {
                    DiagnosticSample baseline = diagnosticSample(samples, round, "A", queryId);
                    DiagnosticSample candidate = diagnosticSample(samples, round, "C", queryId);
                    paired.add(candidate.stage(stage) - baseline.stage(stage));
                }
                delta.put(stage, percentile(paired, 0.50D));
            }
            deltas.add(delta);
        }
        deltas.sort(Comparator.comparingLong(
                delta -> -((Number) delta.get("totalNs")).longValue()));
        return deltas;
    }

    private static DiagnosticSample diagnosticSample(
            List<DiagnosticSample> samples, int round, String arm, String queryId) {
        return samples.stream()
                .filter(sample -> sample.round == round
                        && arm.equals(sample.arm) && queryId.equals(sample.queryId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing diagnostic sample: " + round + ":" + arm + ":" + queryId));
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private static double medianDouble(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0D;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        return size % 2 == 0
                ? (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0D
                : sorted.get(size / 2);
    }

    private static void assertCanaryBoundary(
            RagEvaluationDataset.QueryCase query,
            RagResult result,
            List<RetrievalInvocation> retrievalInvocations,
            List<String> embeddingInputs) {
        String canary = "CANARY-REF-20260715";
        assertEquals(96, retrievalInvocations.size());
        assertTrue(retrievalInvocations.stream().allMatch(invocation ->
                invocation.knowledgeBaseId() == KNOWLEDGE_BASE_ID
                        && invocation.topK() == 10 && !invocation.includeGraph()));
        assertTrue(retrievalInvocations.stream().noneMatch(invocation ->
                invocation.query().contains(canary) || invocation.retrievalQuery().contains(canary)));
        assertTrue(embeddingInputs.stream().noneMatch(input -> input.contains(canary)));

        List<String> prompts = Collections.synchronizedList(new ArrayList<>());
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(factory.getChatModel(eq("shadow-local"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), eq("shadow-judge")))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, Prompt.class).toString();
            prompts.add(prompt);
            String response = prompt.contains("基于以下知识回答问题")
                    ? "shadow generated answer"
                    : "{\"score\":0.9,\"feedback\":\"shadow\"}";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        });

        RagasEvalConfig config = new RagasEvalConfig();
        config.setPlatform("shadow-local");
        config.setModelName("shadow-judge");
        EvaluationDataset evaluation = new EvaluationDataset();
        evaluation.setName("shadow-canary-boundary");
        evaluation.setItems(List.of(new EvaluationDataset.EvalItem(
                query.query(), canary, List.of(result.getContext()))));
        new RagasEvaluator(factory, config).evaluate(evaluation);

        List<String> generationPrompts = prompts.stream()
                .filter(prompt -> prompt.contains("基于以下知识回答问题"))
                .toList();
        assertEquals(1, generationPrompts.size());
        assertTrue(generationPrompts.stream().noneMatch(prompt -> prompt.contains(canary)));
        List<String> canaryPrompts = prompts.stream()
                .filter(prompt -> prompt.contains(canary)).toList();
        assertEquals(3, canaryPrompts.size());
        assertTrue(canaryPrompts.stream().allMatch(prompt -> prompt.contains("精确度")
                || prompt.contains("召回率") || prompt.contains("事实是否")));
    }

    private static void assertNoSentinelLeakage(Map<String, RagResult> results) {
        Set<Long> sentinelIds = Set.of(990001L, 990002L, 990003L, 990004L, 990005L);
        for (RagResult result : results.values()) {
            assertTrue(result.getSources().stream()
                            .noneMatch(source -> sentinelIds.contains(source.getSegmentId())),
                    "normal KB sources must not contain sentinel segments");
            String context = result.getContext() == null ? "" : result.getContext();
            assertTrue(!context.contains("SENTINEL"),
                    "normal KB context must not contain sentinel content");
        }
    }

    private static RagEvaluationDataset.QueryCase query(RagEvaluationDataset dataset, String id) {
        return dataset.queries().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing shadow query: " + id));
    }

    private static Map<String, Object> dbCallTraceRecord(
            RagEvaluationDataset.QueryCase query,
            RagResult result,
            DbCallSnapshot delta) throws IOException {
        Set<String> sourceIds = new LinkedHashSet<>();
        if (result.getSources() != null) {
            for (RetrievalResult source : result.getSources()) {
                if (source != null && source.getSegmentId() != null) {
                    sourceIds.add(String.valueOf(source.getSegmentId()));
                }
            }
        }

        String context = result.getContext() == null ? "" : result.getContext();
        Set<String> contextIds = new LinkedHashSet<>();
        Matcher matcher = CONTEXT_SEGMENT_MARKER.matcher(context);
        while (matcher.find()) {
            contextIds.add(matcher.group(1));
        }
        if (!context.isEmpty() && contextIds.isEmpty()) {
            writeInvalidShadowRun(CONTEXT_SEGMENT_MARKER_PARSE_FAILED, query.id());
            fail("non-empty context has no segmentId marker: " + query.id());
        }

        Set<String> addedContextIds = new LinkedHashSet<>(contextIds);
        addedContextIds.removeAll(sourceIds);
        String adjacentAttribution = delta.contextAdjacent() > 0
                ? (addedContextIds.isEmpty() ? "EMPTY_ADJACENT_PROBE" : "EFFECTIVE_ADJACENT")
                : "NOT_APPLICABLE";

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("queryId", query.id());
        trace.put("identifierAware", identifierAwareFlag());
        trace.put("keyword", delta.keyword());
        trace.put("metadata", delta.metadata());
        trace.put("vector", delta.vector());
        trace.put("contextParent", delta.contextParent());
        trace.put("contextAdjacent", delta.contextAdjacent());
        trace.put("contextOther", delta.contextOther());
        trace.put("other", delta.other());
        trace.put("context", delta.context());
        trace.put("total", delta.total());
        trace.put("sourceCount", sourceIds.size());
        trace.put("contextEmpty", context.isEmpty());
        trace.put("parsedContextSegmentCount", contextIds.size());
        trace.put("contextAddedSegmentCount", addedContextIds.size());
        trace.put("adjacentAttribution", adjacentAttribution);
        return trace;
    }

    private static boolean identifierAwareFlag() {
        return Boolean.parseBoolean(System.getProperty(
                "qknow.rag.keyword.identifier-aware", "false"));
    }

    private static boolean identifierDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                IDENTIFIER_DIAGNOSTIC_PROPERTY, "false"));
    }

    private static boolean candidate2CalibrationDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                CANDIDATE2_DIAGNOSTIC_PROPERTY, "false"));
    }

    private static boolean candidate3CalibrationDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                CANDIDATE3_DIAGNOSTIC_PROPERTY, "false"));
    }

    private static boolean candidate4CalibrationDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                RagCandidate4DiagnosticSupport.DIAGNOSTIC_PROPERTY, "false"));
    }

    private static boolean candidate5CalibrationDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                RagCandidate5DiagnosticSupport.DIAGNOSTIC_PROPERTY, "false"));
    }

    private static boolean candidate6CalibrationDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                RagCandidate6DiagnosticSupport.DIAGNOSTIC_PROPERTY, "false"));
    }

    private static boolean candidate8CalibrationDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                RagCandidate8DiagnosticSupport.DIAGNOSTIC_PROPERTY, "false"));
    }

    private static boolean candidate9CalibrationDiagnosticEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                RagCandidate9DiagnosticSupport.DIAGNOSTIC_PROPERTY, "false"));
    }

    private static boolean candidate5ReviewEnabled() {
        return RagCandidate5ReviewSupport.configuredArm() != null;
    }

    private static boolean identifierConsistencyFlag() {
        return Boolean.parseBoolean(System.getProperty(
                "qknow.rag.rerank.identifier-consistency-enabled", "false"));
    }

    private static boolean identifierRecallConsistencyFlag() {
        return Boolean.parseBoolean(System.getProperty(
                "qknow.rag.keyword.identifier-recall-consistency-enabled", "false"));
    }

    private static Map<String, Object> shadowConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("rrfK", 60);
        config.put("weakPathThreshold", 0.0D);
        config.put("dynamicTopK", Map.of(
                "enabled", false, "defaultTopK", 10, "minTopK", 3, "maxTopK", 80,
                "complexMinTopK", 12, "mediumMultiplier", 1.0D, "complexMultiplier", 1.8D,
                "temporalMultiplier", 1.3D, "keywordMultiplierStep", 0.08D, "maxKeywordBonus", 0.5D));
        config.put("colbert", Map.of("enabled", true, "dimensions", 64, "maxTokens", 128));
        config.put("context", Map.of("maxBytes", 20000, "maxTokens", 0));
        config.put("routerEnabled", true);
        config.put("queryEntityEnabled", false);
        config.put("queryTransformEnabled", false);
        config.put("cragEnabled", false);
        config.put("cragWebEnabled", false);
        config.put("graphEnabled", false);
        config.put("rerankerMode", "deterministic");
        config.put("rerankerProviders", List.of("deterministic"));
        config.put("vecsimRescoreEnabled", false);
        config.put("identifierAware", identifierAwareFlag());
        config.put("identifierConsistencyEnabled", identifierConsistencyFlag());
        config.put("identifierConsistencyAlgorithm", CANDIDATE3_ALGORITHM);
        config.put("identifierConsistencyScorePolicy", CANDIDATE3_SCORE_POLICY);
        config.put("executor", Map.of("core", 4, "max", 4, "queue", 32));
        config.put("topK", 10);
        config.put("nativeMode", "java-fallback");
        config.put("featureHash", Map.of(
                "version", FeatureHashEmbeddingModel.VERSION, "dimensions", 256, "seed", 20260715L));
        config.put("corpusInsertionOrder", "segmentId-ascending");
        return config;
    }

    private static Map<String, Object> candidate5ReviewConfig(
            RagCandidate5ReviewSupport.Arm arm) {
        Map<String, Object> config = shadowConfig();
        config.put("routerEnabled", false);
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", true);
        config.put("identifierRecallConsistencyEnabled", arm.enabled());
        config.put("identifierRecallConsistencyAlgorithm", RagCandidate5ReviewSupport.ALGORITHM);
        config.put("identifierRecallConsistencySqlPolicy", RagCandidate5ReviewSupport.SQL_POLICY);
        return config;
    }

    private static void writeRuntimeReport(RagBenchmarkReport report,
                                           Map<String, Object> config,
                                           List<String> sentinelEvidence,
                                           Map<String, Map<String, List<Double>>> familyScores,
                                           Map<String, Object> observedBudget,
                                           List<Map<String, Object>> dbCallTrace) throws IOException {
        Path directory = runtimeDirectory();
        Files.createDirectories(directory);
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("status", "VALID");
        runtime.put("vecsimRescore", false);
        runtime.put("sentinels", sentinelEvidence);
        runtime.put("config", config);
        runtime.put("report", JSON.parseObject(JSON.toJSONString(report)));
        runtime.put("familyScores", familyScores);
        runtime.put("observedBudget", observedBudget);
        Path contexts = directory.resolve("shadow-contexts.jsonl");
        Path labels = directory.resolve("shadow-labels.jsonl");
        if (!Files.isRegularFile(contexts) || !Files.isRegularFile(labels)) {
            throw new IOException("Shadow live artifacts were not written");
        }
        if (dbCallTrace.size() != 96) {
            throw new IOException("Shadow DB call trace must contain 96 records");
        }
        Path trace = runtimeDbCallTracePath();
        Files.writeString(trace,
                JSON.toJSONString(dbCallTrace, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8);
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("contexts", Map.of("file", contexts.getFileName().toString(),
                "sha256", ShadowContractSupport.sha256(contexts)));
        artifacts.put("labels", Map.of("file", labels.getFileName().toString(),
                "sha256", ShadowContractSupport.sha256(labels)));
        artifacts.put("dbCallTrace", Map.of("file", trace.getFileName().toString(),
                "sha256", ShadowContractSupport.sha256(trace), "count", dbCallTrace.size()));
        artifacts.put("count", 96);
        runtime.put("artifacts", artifacts);
        Files.writeString(runtimeReportPath(),
                JSON.toJSONString(runtime, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
    }

    private static void writeInvalidShadowRun(SentinelPreflight.Result sentinelResult,
                                              List<String> sentinelEvidence) throws IOException {
        Path directory = runtimeDirectory();
        Files.createDirectories(directory);
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("status", "INVALID");
        runtime.put("vecsimRescore", false);
        runtime.put("sentinels", sentinelEvidence);
        runtime.put("failedCheck", sentinelResult.failedCheck());
        runtime.put("errorCode", sentinelResult.errorCode() == null
                ? null : sentinelResult.errorCode().name());
        Files.writeString(runtimeReportPath(),
                JSON.toJSONString(runtime, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
        Files.deleteIfExists(runtimeDbCallTracePath());
        Files.deleteIfExists(directory.resolve("shadow-contexts.jsonl"));
        Files.deleteIfExists(directory.resolve("shadow-labels.jsonl"));
        Files.deleteIfExists(directory.resolve("live-report.json"));
    }

    private static void writeInvalidShadowRun(String errorCode, String failedQueryId) throws IOException {
        Path directory = runtimeDirectory();
        Files.createDirectories(directory);
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("status", "INVALID");
        runtime.put("errorCode", errorCode);
        runtime.put("failedQueryId", failedQueryId);
        Files.writeString(runtimeReportPath(),
                JSON.toJSONString(runtime, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNulls),
                StandardCharsets.UTF_8);
        Files.deleteIfExists(runtimeDbCallTracePath());
        Files.deleteIfExists(directory.resolve("shadow-contexts.jsonl"));
        Files.deleteIfExists(directory.resolve("shadow-labels.jsonl"));
        Files.deleteIfExists(directory.resolve("live-report.json"));
    }

    private static void clearRuntimeArtifacts() throws IOException {
        Path directory = runtimeDirectory();
        if (candidate2CalibrationDiagnosticEnabled()) {
            Files.deleteIfExists(directory.resolve(CANDIDATE2_DIAGNOSTIC_REPORT));
            return;
        }
        if (candidate3CalibrationDiagnosticEnabled()) {
            Files.deleteIfExists(directory.resolve(CANDIDATE3_DIAGNOSTIC_REPORT));
            return;
        }
        if (candidate4CalibrationDiagnosticEnabled()) {
            RagCandidate4DiagnosticSupport.clearDiagnostic(
                    RagCandidate4DiagnosticSupport.paths(directory));
            return;
        }
        if (candidate5CalibrationDiagnosticEnabled()) {
            RagCandidate5DiagnosticSupport.clearDiagnostic(
                    RagCandidate5DiagnosticSupport.paths(directory));
            return;
        }
        if (candidate6CalibrationDiagnosticEnabled()) {
            // Candidate 6 evidence is one-shot. Existing diagnostic or ledger state
            // must remain visible so a crashed run fails closed on the next startup.
            return;
        }
        if (candidate8CalibrationDiagnosticEnabled()) {
            // Candidate 8 evidence is one-shot and must never be auto-cleaned.
            return;
        }
        if (candidate9CalibrationDiagnosticEnabled()) {
            // Candidate 9 evidence is one-shot and must never be auto-cleaned.
            return;
        }
        if (candidate5ReviewEnabled()) {
            return;
        }
        Files.deleteIfExists(runtimeReportPath());
        Files.deleteIfExists(runtimeDbCallTracePath());
        Files.deleteIfExists(directory.resolve("shadow-contexts.jsonl"));
        Files.deleteIfExists(directory.resolve("shadow-labels.jsonl"));
        Files.deleteIfExists(directory.resolve("live-report.json"));
    }

    private static Map<String, String> sharedLiveArtifactHashes() {
        Path directory = runtimeDirectory();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String fileName : List.of("shadow-contexts.jsonl", "shadow-labels.jsonl")) {
            Path path = directory.resolve(fileName);
            hashes.put(fileName, Files.isRegularFile(path)
                    ? ShadowContractSupport.sha256(path) : "ABSENT");
        }
        return Map.copyOf(hashes);
    }

    private static Path runtimeReportPath() {
        String configured = System.getProperty("rag.eval.shadow.report-file", "shadow-report.json");
        Path file = Path.of(configured);
        if (file.isAbsolute() || file.getNameCount() != 1
                || file.getFileName().toString().isBlank()) {
            throw new IllegalArgumentException("rag.eval.shadow.report-file must be a file name");
        }
        return runtimeDirectory().resolve(file).normalize();
    }

    private static Path runtimeDbCallTracePath() {
        Path report = runtimeReportPath();
        String fileName = report.getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        String traceFileName = extension > 0
                ? fileName.substring(0, extension) + "-db-calls" + fileName.substring(extension)
                : fileName + "-db-calls.json";
        return report.resolveSibling(traceFileName);
    }

    private static void writeLiveArtifacts(RagEvaluationDataset dataset,
                                            Map<String, RagResult> results) throws IOException {
        Path directory = runtimeDirectory();
        Files.createDirectories(directory);
        List<String> contexts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            RagResult result = results.get(query.id());
            if (result == null) {
                throw new IllegalArgumentException("Missing live artifact result: " + query.id());
            }
            Map<String, Object> contextRecord = new LinkedHashMap<>();
            contextRecord.put("queryId", query.id());
            contextRecord.put("familyId", query.familyId());
            contextRecord.put("query", query.query());
            contextRecord.put("split", query.split());
            contextRecord.put("answerable", query.answerable());
            contextRecord.put("context", result.getContext() == null ? "" : result.getContext());
            contexts.add(JSON.toJSONString(contextRecord));

            Map<String, Object> labelRecord = new LinkedHashMap<>();
            labelRecord.put("queryId", query.id());
            labelRecord.put("familyId", query.familyId());
            labelRecord.put("split", query.split());
            labelRecord.put("answerable", query.answerable());
            labelRecord.put("referenceAnswer", query.referenceAnswer());
            labelRecord.put("referenceClaims", query.referenceClaims());
            labels.add(JSON.toJSONString(labelRecord));
        }
        Files.writeString(directory.resolve("shadow-contexts.jsonl"),
                String.join("\n", contexts) + "\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("shadow-labels.jsonl"),
                String.join("\n", labels) + "\n", StandardCharsets.UTF_8);
    }

    private static Path runtimeDirectory() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return workingDirectory.endsWith(Path.of("backend", "tests"))
                ? workingDirectory.resolve("target/rag-eval")
                : workingDirectory.resolve("backend/tests/target/rag-eval");
    }

    private static void assertStableSnapshot(RagBenchmarkReport report) throws IOException {
        if (!Boolean.parseBoolean(System.getProperty("rag.eval.shadow.compare-stable", "true"))) {
            return;
        }
        String expected;
        try (InputStream input = RagShadowBaselineTest.class
                .getResourceAsStream("/rag-eval/baseline.json")) {
            if (input == null) {
                throw new AssertionError("Missing stable shadow snapshot; runtime report was written");
            }
            expected = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertEquals(expected, ShadowContractSupport.snapshotJson(report));
    }

    private static Map<String, Long> insertCorpus(
            JdbcTemplate jdbc,
            RagEvaluationDataset dataset,
            long knowledgeBaseId,
            long firstDocumentId) {
        List<RagEvaluationDataset.CorpusSegment> segments = sortedSegments(dataset);
        Map<String, Long> documentIds = new LinkedHashMap<>();
        Map<String, String> documentNames = new LinkedHashMap<>();
        documentIds.put("sentinel-context", 99001L);
        long nextDocumentId = firstDocumentId;
        for (RagEvaluationDataset.CorpusSegment segment : segments) {
            if (isSentinel(segment.segmentId())) {
                continue;
            }
            if (!documentIds.containsKey(segment.documentId())) {
                documentIds.put(segment.documentId(), nextDocumentId++);
            }
            if (knowledgeBaseId == RagCandidate5DiagnosticSupport.SELECTION_KB_ID) {
                documentNames.put(segment.documentId(), candidate5DocumentName(segment));
            } else if (knowledgeBaseId
                    == RagCandidate6DiagnosticSupport.SELECTION_KB_ID) {
                documentNames.put(segment.documentId(), candidate6DocumentName(segment));
            } else if (knowledgeBaseId
                    == RagCandidate8DiagnosticSupport.SELECTION_KB_ID) {
                documentNames.put(segment.documentId(), candidate8DocumentName(segment));
            }
        }
        for (Map.Entry<String, Long> entry : documentIds.entrySet()) {
            if (entry.getValue() == 99001L) {
                continue;
            }
            jdbc.update("INSERT INTO kmc_document(id, knowledge_base_id, name) VALUES (?, ?, ?)",
                    entry.getValue(), knowledgeBaseId,
                    documentNames.getOrDefault(entry.getKey(), entry.getKey()));
        }

        Map<String, Integer> positions = new LinkedHashMap<>();
        for (RagEvaluationDataset.CorpusSegment segment : segments) {
            if (isSentinel(segment.segmentId())) {
                continue;
            }
            long segmentId = Long.parseLong(segment.segmentId());
            long documentId = documentIds.get(segment.documentId());
            int position = positions.merge(segment.documentId(), 1, Integer::sum) - 1;
            String documentName = knowledgeBaseId == RagCandidate5DiagnosticSupport.SELECTION_KB_ID
                    ? candidate5DocumentName(segment)
                    : knowledgeBaseId == RagCandidate6DiagnosticSupport.SELECTION_KB_ID
                    ? candidate6DocumentName(segment)
                    : knowledgeBaseId == RagCandidate8DiagnosticSupport.SELECTION_KB_ID
                    ? candidate8DocumentName(segment) : segment.documentId();
            jdbc.update("INSERT INTO kmc_document_segment(id, document_id, content, document_name, qm_segment_id, parent_id, position) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    segmentId, documentId, segment.content(), documentName, segment.segmentId(),
                    segment.parentSegmentId(), position);
            Object entity = segment.metadata().get("entity");
            if (entity != null) {
                jdbc.update("INSERT INTO kmc_segment_entity_metadata(segment_id, entities) VALUES (?, ?::jsonb)",
                        segmentId, com.alibaba.fastjson2.JSON.toJSONString(List.of(String.valueOf(entity))));
            }
        }
        return documentIds;
    }

    private static VectorStore buildCorpusVectorStore(EmbeddingModel embeddingModel,
                                                       RagEvaluationDataset dataset,
                                                       Map<String, Long> documentIds,
                                                       long knowledgeBaseId) {
        VectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> documents = new ArrayList<>();
        for (RagEvaluationDataset.CorpusSegment segment : sortedSegments(dataset)) {
            if (isSentinel(segment.segmentId())) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, knowledgeBaseId);
            metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, documentIds.get(segment.documentId()));
            metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME,
                    knowledgeBaseId == RagCandidate5DiagnosticSupport.SELECTION_KB_ID
                            ? candidate5DocumentName(segment)
                            : knowledgeBaseId == RagCandidate6DiagnosticSupport.SELECTION_KB_ID
                            ? candidate6DocumentName(segment)
                            : knowledgeBaseId == RagCandidate8DiagnosticSupport.SELECTION_KB_ID
                            ? candidate8DocumentName(segment) : segment.documentId());
            metadata.put(WeaviateConstant.METADATA_FIELD_SEGMENT_ID, Long.parseLong(segment.segmentId()));
            if (segment.parentSegmentId() != null) {
                metadata.put("parent_segment_id", segment.parentSegmentId());
            }
            documents.add(Document.builder()
                    .id(segment.segmentId())
                    .text(segment.content())
                    .metadata(metadata)
                    .build());
        }
        if (knowledgeBaseId == RagCandidate4DiagnosticSupport.SELECTION_KB_ID) {
            for (Document document : documents) {
                long segmentId = ((Number) document.getMetadata().get(
                        WeaviateConstant.METADATA_FIELD_SEGMENT_ID)).longValue();
                long documentId = ((Number) document.getMetadata().get(
                        WeaviateConstant.METADATA_FIELD_DOCUMENT_ID)).longValue();
                assertEquals(RagCandidate4DiagnosticSupport.SELECTION_KB_ID,
                        ((Number) document.getMetadata().get(
                                WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID)).longValue());
                assertTrue(segmentId >= RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                        && segmentId <= RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MAX);
                assertTrue(segmentId < RagCandidate4DiagnosticSupport.HOLDOUT_SEGMENT_ID_MIN
                        || segmentId > RagCandidate4DiagnosticSupport.HOLDOUT_SEGMENT_ID_MAX);
                assertTrue(documentId >= RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                        && documentId <= RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX);
            }
        }
        if (knowledgeBaseId == RagCandidate5DiagnosticSupport.SELECTION_KB_ID) {
            for (Document document : documents) {
                long segmentId = ((Number) document.getMetadata().get(
                        WeaviateConstant.METADATA_FIELD_SEGMENT_ID)).longValue();
                long documentId = ((Number) document.getMetadata().get(
                        WeaviateConstant.METADATA_FIELD_DOCUMENT_ID)).longValue();
                RagEvaluationDataset.CorpusSegment segment = dataset.corpusById().get(
                        String.valueOf(segmentId));
                candidate5Require(segment != null, "CANDIDATE5_KB_ISOLATION_FAILED");
                candidate5Require(RagCandidate5DiagnosticSupport.SELECTION_KB_ID
                                == ((Number) document.getMetadata().get(
                                WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID)).longValue(),
                        "CANDIDATE5_KB_ISOLATION_FAILED");
                candidate5Require(candidate5DocumentName(segment).equals(
                                document.getMetadata().get(
                                        WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME)),
                        "CANDIDATE5_KB_ISOLATION_FAILED");
                candidate5Require(
                        segmentId >= RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                                && segmentId
                                <= RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MAX,
                        "CANDIDATE5_KB_ISOLATION_FAILED");
                candidate5Require(
                        documentId >= RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                                && documentId
                                <= RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                        "CANDIDATE5_KB_ISOLATION_FAILED");
            }
        }
        if (knowledgeBaseId == RagCandidate6DiagnosticSupport.SELECTION_KB_ID) {
            for (Document document : documents) {
                long segmentId = ((Number) document.getMetadata().get(
                        WeaviateConstant.METADATA_FIELD_SEGMENT_ID)).longValue();
                long documentId = ((Number) document.getMetadata().get(
                        WeaviateConstant.METADATA_FIELD_DOCUMENT_ID)).longValue();
                RagEvaluationDataset.CorpusSegment segment = dataset.corpusById().get(
                        String.valueOf(segmentId));
                assertNotNull(segment, "Candidate 6 segment metadata missing");
                assertEquals(RagCandidate6DiagnosticSupport.SELECTION_KB_ID,
                        ((Number) document.getMetadata().get(
                                WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID)).longValue());
                assertEquals(candidate6DocumentName(segment),
                        document.getMetadata().get(
                                WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME));
                assertTrue(segmentId
                        >= RagCandidate6DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                        && segmentId
                        <= RagCandidate6DiagnosticSupport.SELECTION_SEGMENT_ID_MAX);
                assertTrue(documentId
                        >= RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                        && documentId
                        <= RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX);
            }
        }
        if (knowledgeBaseId == RagCandidate8DiagnosticSupport.SELECTION_KB_ID) {
            for (Document document : documents) {
                long segmentId = ((Number) document.getMetadata().get(
                        WeaviateConstant.METADATA_FIELD_SEGMENT_ID)).longValue();
                long documentId = ((Number) document.getMetadata().get(
                        WeaviateConstant.METADATA_FIELD_DOCUMENT_ID)).longValue();
                RagEvaluationDataset.CorpusSegment segment = dataset.corpusById().get(
                        String.valueOf(segmentId));
                candidate8Require(segment != null,
                        "CANDIDATE8_KB_ISOLATION_FAILED");
                candidate8Require(RagCandidate8DiagnosticSupport.SELECTION_KB_ID
                                == ((Number) document.getMetadata().get(
                                WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID)).longValue(),
                        "CANDIDATE8_KB_ISOLATION_FAILED");
                candidate8Require(candidate8DocumentName(segment).equals(
                                document.getMetadata().get(
                                        WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME)),
                        "CANDIDATE8_KB_ISOLATION_FAILED");
                candidate8Require(segmentId
                                >= RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MIN
                                && segmentId
                                <= RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MAX,
                        "CANDIDATE8_KB_ISOLATION_FAILED");
                candidate8Require(documentId
                                >= RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                                && documentId
                                <= RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                        "CANDIDATE8_KB_ISOLATION_FAILED");
            }
        }
        store.add(documents);
        return store;
    }

    private static String candidate5DocumentName(
            RagEvaluationDataset.CorpusSegment segment) {
        Object value = segment.metadata().get("documentName");
        candidate5Require(value instanceof String && !((String) value).isBlank(),
                "CANDIDATE5_DATASET_INVALID");
        return (String) value;
    }

    private static String candidate6DocumentName(
            RagEvaluationDataset.CorpusSegment segment) {
        Object value = segment.metadata().get("documentName");
        if (!(value instanceof String documentName) || documentName.isBlank()) {
            throw new Candidate6DiagnosticFailure("CANDIDATE6_DATASET_INVALID");
        }
        return documentName;
    }

    private static String candidate8DocumentName(
            RagEvaluationDataset.CorpusSegment segment) {
        Object value = segment.metadata().get("documentName");
        if (!(value instanceof String documentName) || documentName.isBlank()) {
            throw new Candidate8DiagnosticFailure("CANDIDATE8_DATASET_INVALID");
        }
        return documentName;
    }

    private static void assertSelectionDatabaseIsolation(
            JdbcTemplate jdbc, Map<String, Long> documentIds) {
        documentIds.entrySet().stream()
                .filter(entry -> !"sentinel-context".equals(entry.getKey()))
                .forEach(entry -> assertTrue(
                        entry.getValue() >= RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                                && entry.getValue()
                                <= RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                        () -> "selection document outside reserved range: " + entry));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id IN (?, ?)
                  AND s.id BETWEEN ? AND ?
                """, Integer.class,
                NORMAL_KB_ID, SENTINEL_KB_ID,
                RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id IN (?, ?)
                  AND id BETWEEN ? AND ?
                """, Integer.class,
                NORMAL_KB_ID, SENTINEL_KB_ID,
                RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id = ?
                  AND (s.id < ? OR s.id > ? OR s.id IN (990001, 990002, 990003, 990004, 990005))
                """, Integer.class,
                RagCandidate4DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate4DiagnosticSupport.SELECTION_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                WHERE s.id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate4DiagnosticSupport.HOLDOUT_SEGMENT_ID_MIN,
                RagCandidate4DiagnosticSupport.HOLDOUT_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id = ?
                  AND (id < ? OR id > ?)
                """, Integer.class,
                RagCandidate4DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate4DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX));
    }

    private static void assertCandidate5SelectionDatabaseIsolation(
            JdbcTemplate jdbc, Map<String, Long> documentIds) {
        documentIds.entrySet().stream()
                .filter(entry -> !"sentinel-context".equals(entry.getKey()))
                .forEach(entry -> {
                    assertEquals(Long.parseLong(entry.getKey()), entry.getValue(),
                            () -> "Candidate 5 document ID remap mismatch: " + entry);
                    assertTrue(
                            entry.getValue()
                                    >= RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                                    && entry.getValue()
                                    <= RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                            () -> "Candidate 5 document outside reserved range: " + entry);
                });
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id IN (?, ?)
                  AND s.id BETWEEN ? AND ?
                """, Integer.class,
                NORMAL_KB_ID, SENTINEL_KB_ID,
                RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id IN (?, ?) AND id BETWEEN ? AND ?
                """, Integer.class,
                NORMAL_KB_ID, SENTINEL_KB_ID,
                RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id = ?
                  AND (s.id < ? OR s.id > ? OR s.id IN (990001, 990002, 990003, 990004, 990005))
                """, Integer.class,
                RagCandidate5DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document_segment WHERE id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate5DiagnosticSupport.HOLDOUT_SEGMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.HOLDOUT_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id = ? AND (id < ? OR id > ?)
                """, Integer.class,
                RagCandidate5DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document WHERE id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MAX));
    }

    private static void assertCandidate5HoldoutDatabaseIsolation(
            JdbcTemplate jdbc, Map<String, Long> documentIds) {
        documentIds.entrySet().stream()
                .filter(entry -> !"sentinel-context".equals(entry.getKey()))
                .forEach(entry -> {
                    assertEquals(Long.parseLong(entry.getKey()), entry.getValue(),
                            () -> "Candidate 5 Holdout document ID remap mismatch: " + entry);
                    assertTrue(
                            entry.getValue() >= RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MIN
                                    && entry.getValue()
                                    <= RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MAX,
                            () -> "Candidate 5 Holdout document outside reserved range: " + entry);
                });
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id IN (?, ?)
                  AND s.id BETWEEN ? AND ?
                """, Integer.class,
                NORMAL_KB_ID, SENTINEL_KB_ID,
                RagCandidate5DiagnosticSupport.HOLDOUT_SEGMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.HOLDOUT_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id IN (?, ?) AND id BETWEEN ? AND ?
                """, Integer.class,
                NORMAL_KB_ID, SENTINEL_KB_ID,
                RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id = ?
                  AND (s.id < ? OR s.id > ? OR s.id IN (990001, 990002, 990003, 990004, 990005))
                """, Integer.class,
                RagCandidate5DiagnosticSupport.HOLDOUT_KB_ID,
                RagCandidate5DiagnosticSupport.HOLDOUT_SEGMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.HOLDOUT_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document_segment WHERE id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.SELECTION_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id = ? AND (id < ? OR id > ?)
                """, Integer.class,
                RagCandidate5DiagnosticSupport.HOLDOUT_KB_ID,
                RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document WHERE id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate5DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX));
    }

    private static void assertCandidate6SelectionDatabaseIsolation(
            JdbcTemplate jdbc, Map<String, Long> documentIds) {
        documentIds.entrySet().stream()
                .filter(entry -> !"sentinel-context".equals(entry.getKey()))
                .forEach(entry -> {
                    assertEquals(Long.parseLong(entry.getKey()), entry.getValue(),
                            () -> "Candidate 6 document ID remap mismatch: " + entry);
                    assertTrue(entry.getValue()
                                    >= RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                                    && entry.getValue()
                                    <= RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                            () -> "Candidate 6 document outside reserved range: " + entry);
                });
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id IN (?, ?)
                  AND s.id BETWEEN ? AND ?
                """, Integer.class,
                NORMAL_KB_ID, SENTINEL_KB_ID,
                RagCandidate6DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate6DiagnosticSupport.SELECTION_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id IN (?, ?) AND id BETWEEN ? AND ?
                """, Integer.class,
                NORMAL_KB_ID, SENTINEL_KB_ID,
                RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id = ?
                  AND (s.id < ? OR s.id > ?
                       OR s.id IN (990001, 990002, 990003, 990004, 990005))
                """, Integer.class,
                RagCandidate6DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate6DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate6DiagnosticSupport.SELECTION_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document_segment
                WHERE id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate6DiagnosticSupport.HOLDOUT_SEGMENT_ID_MIN,
                RagCandidate6DiagnosticSupport.HOLDOUT_SEGMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document
                WHERE id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate6DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MIN,
                RagCandidate6DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MAX));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id = ? AND (id < ? OR id > ?)
                """, Integer.class,
                RagCandidate6DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate6DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX));
    }

    private static void assertCandidate8SelectionDatabaseIsolation(
            JdbcTemplate jdbc, Map<String, Long> documentIds) {
        documentIds.entrySet().stream()
                .filter(entry -> !"sentinel-context".equals(entry.getKey()))
                .forEach(entry -> {
                    candidate8Require(Long.parseLong(entry.getKey()) == entry.getValue(),
                            "CANDIDATE8_KB_ISOLATION_FAILED");
                    candidate8Require(entry.getValue()
                                    >= RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                                    && entry.getValue()
                                    <= RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                            "CANDIDATE8_KB_ISOLATION_FAILED");
                });
        candidate8Require(jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id <> ? AND s.id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate8DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MAX) == 0,
                "CANDIDATE8_KB_ISOLATION_FAILED");
        candidate8Require(jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id <> ? AND id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate8DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX) == 0,
                "CANDIDATE8_KB_ISOLATION_FAILED");
        candidate8Require(jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id = ?
                  AND (s.id < ? OR s.id > ?
                       OR s.id IN (990001, 990002, 990003, 990004, 990005))
                """, Integer.class,
                RagCandidate8DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate8DiagnosticSupport.SELECTION_SEGMENT_ID_MAX) == 0,
                "CANDIDATE8_KB_ISOLATION_FAILED");
        candidate8Require(jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document_segment
                WHERE id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate8DiagnosticSupport.HOLDOUT_SEGMENT_ID_MIN,
                RagCandidate8DiagnosticSupport.HOLDOUT_SEGMENT_ID_MAX) == 0,
                "CANDIDATE8_KB_ISOLATION_FAILED");
        candidate8Require(jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document
                WHERE id BETWEEN ? AND ? OR knowledge_base_id = ?
                """, Integer.class,
                RagCandidate8DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MIN,
                RagCandidate8DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MAX,
                RagCandidate8DiagnosticSupport.HOLDOUT_KB_ID) == 0,
                "CANDIDATE8_KB_ISOLATION_FAILED");
        candidate8Require(jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id = ? AND (id < ? OR id > ?)
                """, Integer.class,
                RagCandidate8DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate8DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX) == 0,
                "CANDIDATE8_KB_ISOLATION_FAILED");
    }

    private static void assertCandidate9SelectionDatabaseIsolation(
            JdbcTemplate jdbc, Map<String, Long> documentIds) {
        documentIds.entrySet().stream()
                .filter(entry -> !"sentinel-context".equals(entry.getKey()))
                .forEach(entry -> {
                    candidate9Require(Long.parseLong(entry.getKey()) == entry.getValue(),
                            "CANDIDATE9_KB_ISOLATION_FAILED");
                    candidate9Require(entry.getValue()
                                    >= RagCandidate9DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN
                                    && entry.getValue()
                                    <= RagCandidate9DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX,
                            "CANDIDATE9_KB_ISOLATION_FAILED");
                });
        candidate9Require(jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id <> ? AND s.id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate9DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate9DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate9DiagnosticSupport.SELECTION_SEGMENT_ID_MAX) == 0,
                "CANDIDATE9_KB_ISOLATION_FAILED");
        candidate9Require(jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id <> ? AND id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate9DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate9DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate9DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX) == 0,
                "CANDIDATE9_KB_ISOLATION_FAILED");
        candidate9Require(jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document_segment s
                JOIN kmc_document d ON d.id = s.document_id
                WHERE d.knowledge_base_id = ?
                  AND (s.id < ? OR s.id > ?
                       OR s.id IN (990001, 990002, 990003, 990004, 990005))
                """, Integer.class,
                RagCandidate9DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate9DiagnosticSupport.SELECTION_SEGMENT_ID_MIN,
                RagCandidate9DiagnosticSupport.SELECTION_SEGMENT_ID_MAX) == 0,
                "CANDIDATE9_KB_ISOLATION_FAILED");
        candidate9Require(jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document_segment
                WHERE id BETWEEN ? AND ?
                """, Integer.class,
                RagCandidate9DiagnosticSupport.HOLDOUT_SEGMENT_ID_MIN,
                RagCandidate9DiagnosticSupport.HOLDOUT_SEGMENT_ID_MAX) == 0,
                "CANDIDATE9_KB_ISOLATION_FAILED");
        candidate9Require(jdbc.queryForObject("""
                SELECT count(*) FROM kmc_document
                WHERE id BETWEEN ? AND ? OR knowledge_base_id = ?
                """, Integer.class,
                RagCandidate9DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MIN,
                RagCandidate9DiagnosticSupport.HOLDOUT_DOCUMENT_ID_MAX,
                RagCandidate9DiagnosticSupport.HOLDOUT_KB_ID) == 0,
                "CANDIDATE9_KB_ISOLATION_FAILED");
        candidate9Require(jdbc.queryForObject("""
                SELECT count(*)
                FROM kmc_document
                WHERE knowledge_base_id = ? AND (id < ? OR id > ?)
                """, Integer.class,
                RagCandidate9DiagnosticSupport.SELECTION_KB_ID,
                RagCandidate9DiagnosticSupport.SELECTION_DOCUMENT_ID_MIN,
                RagCandidate9DiagnosticSupport.SELECTION_DOCUMENT_ID_MAX) == 0,
                "CANDIDATE9_KB_ISOLATION_FAILED");
    }

    private static List<RagEvaluationDataset.CorpusSegment> sortedSegments(RagEvaluationDataset dataset) {
        return dataset.corpusById().values().stream()
                .sorted(java.util.Comparator.comparingLong(segment -> Long.parseLong(segment.segmentId())))
                .toList();
    }

    private static boolean isSentinel(String segmentId) {
        return Set.of("990001", "990002", "990003", "990004", "990005").contains(segmentId);
    }

    private static PostgreSQLContainer<?> newContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("shadow")
                .withUsername("shadow")
                .withPassword("shadow")
                .withEnv("TZ", "UTC")
                .withEnv("LANG", "C")
                .withEnv("LC_ALL", "C")
                .withEnv("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale=C")
                .withReuse(false);
    }

    private static JdbcTemplate jdbc(PostgreSQLContainer<?> container) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private static JdbcTemplate countingJdbc(PostgreSQLContainer<?> container,
                                             QueryCounters counters,
                                             DbCallCategory category,
                                             AtomicBoolean guardVectorStore) {
        return countingJdbc(
                container, counters, category, guardVectorStore, null, null);
    }

    private static JdbcTemplate countingJdbc(PostgreSQLContainer<?> container,
                                             QueryCounters counters,
                                             DbCallCategory category,
                                             AtomicBoolean guardVectorStore,
                                             IdentifierLatencyDiagnostics diagnostics) {
        return countingJdbc(
                container, counters, category, guardVectorStore, diagnostics, null);
    }

    private static JdbcTemplate countingJdbc(PostgreSQLContainer<?> container,
                                             QueryCounters counters,
                                             DbCallCategory category,
                                             AtomicBoolean guardVectorStore,
                                             IdentifierLatencyDiagnostics diagnostics,
                                             CalibrationStageDiagnostics sqlDiagnostics) {
        DriverManagerDataSource delegate = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        AbstractDataSource dataSource = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                try {
                    return countingConnection(
                            delegate.getConnection(), counters, category,
                            guardVectorStore, sqlDiagnostics);
                } catch (SQLException failure) {
                    recordCandidate5SqlFailure(sqlDiagnostics, failure);
                    throw failure;
                }
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                try {
                    return countingConnection(
                            delegate.getConnection(username, password), counters, category,
                            guardVectorStore, sqlDiagnostics);
                } catch (SQLException failure) {
                    recordCandidate5SqlFailure(sqlDiagnostics, failure);
                    throw failure;
                }
            }
        };
        return diagnostics == null
                ? new JdbcTemplate(dataSource)
                : new DiagnosticJdbcTemplate(dataSource, diagnostics);
    }

    private static Connection countingConnection(Connection delegate,
                                                 QueryCounters counters,
                                                 DbCallCategory category,
                                                 AtomicBoolean guardVectorStore,
                                                 CalibrationStageDiagnostics sqlDiagnostics) {
        return (Connection) Proxy.newProxyInstance(
                RagShadowBaselineTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    Object[] invocationArgs = args == null ? new Object[0] : args;
                    String methodName = method.getName();
                    if (("prepareStatement".equals(methodName) || "prepareCall".equals(methodName))
                            && invocationArgs.length > 0 && invocationArgs[0] instanceof String sql) {
                        assertNoVectorStoreSql(sql, guardVectorStore);
                        if (isQuerySql(sql)) {
                            counters.record(category, sql);
                        }
                    }
                    Object result;
                    try {
                        result = invoke(delegate, method, invocationArgs);
                    } catch (Throwable failure) {
                        recordCandidate5SqlFailure(sqlDiagnostics, failure);
                        throw failure;
                    }
                    if (result instanceof java.sql.CallableStatement statement) {
                        return failureRecordingStatement(
                                statement, java.sql.CallableStatement.class, sqlDiagnostics);
                    }
                    if (result instanceof java.sql.PreparedStatement statement) {
                        return failureRecordingStatement(
                                statement, java.sql.PreparedStatement.class, sqlDiagnostics);
                    }
                    if ("createStatement".equals(methodName)
                            && result instanceof java.sql.Statement statement) {
                        return countingStatement(
                                statement, counters, category,
                                guardVectorStore, sqlDiagnostics);
                    }
                    return result;
                });
    }

    private static java.sql.Statement countingStatement(java.sql.Statement delegate,
                                                         QueryCounters counters,
                                                         DbCallCategory category,
                                                         AtomicBoolean guardVectorStore,
                                                         CalibrationStageDiagnostics sqlDiagnostics) {
        return (java.sql.Statement) Proxy.newProxyInstance(
                RagShadowBaselineTest.class.getClassLoader(),
                new Class<?>[]{java.sql.Statement.class},
                (proxy, method, args) -> {
                    Object[] invocationArgs = args == null ? new Object[0] : args;
                    if (invocationArgs.length > 0 && invocationArgs[0] instanceof String sql
                            && (method.getName().startsWith("execute") || "addBatch".equals(method.getName()))) {
                        assertNoVectorStoreSql(sql, guardVectorStore);
                        if (isQuerySql(sql)) {
                            counters.record(category, sql);
                        }
                    }
                    try {
                        Object result = invoke(delegate, method, invocationArgs);
                        return result instanceof java.sql.ResultSet resultSet
                                ? failureRecordingResultSet(resultSet, sqlDiagnostics)
                                : result;
                    } catch (Throwable failure) {
                        recordCandidate5SqlFailure(sqlDiagnostics, failure);
                        throw failure;
                    }
                });
    }

    private static Object failureRecordingStatement(
            java.sql.Statement delegate,
            Class<?> statementInterface,
            CalibrationStageDiagnostics sqlDiagnostics) {
        return Proxy.newProxyInstance(
                RagShadowBaselineTest.class.getClassLoader(),
                new Class<?>[]{statementInterface},
                (proxy, method, args) -> {
                    try {
                        Object result = invoke(
                                delegate, method, args == null ? new Object[0] : args);
                        return result instanceof java.sql.ResultSet resultSet
                                ? failureRecordingResultSet(resultSet, sqlDiagnostics)
                                : result;
                    } catch (Throwable failure) {
                        recordCandidate5SqlFailure(sqlDiagnostics, failure);
                        throw failure;
                    }
                });
    }

    private static java.sql.ResultSet failureRecordingResultSet(
            java.sql.ResultSet delegate,
            CalibrationStageDiagnostics sqlDiagnostics) {
        return (java.sql.ResultSet) Proxy.newProxyInstance(
                RagShadowBaselineTest.class.getClassLoader(),
                new Class<?>[]{java.sql.ResultSet.class},
                (proxy, method, args) -> {
                    try {
                        return invoke(
                                delegate, method, args == null ? new Object[0] : args);
                    } catch (Throwable failure) {
                        recordCandidate5SqlFailure(sqlDiagnostics, failure);
                        throw failure;
                    }
                });
    }

    private static void recordCandidate5SqlFailure(
            CalibrationStageDiagnostics diagnostics, Throwable failure) {
        if (diagnostics == null) {
            return;
        }
        CalibrationTrace trace = diagnostics.current.get();
        if (trace != null) {
            trace.sqlFailureClass.compareAndSet(
                    null, failure.getClass().getSimpleName());
        }
    }

    private static boolean isQuerySql(String sql) {
        String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        return normalized.startsWith("select") || normalized.startsWith("with")
                || normalized.startsWith("show") || normalized.startsWith("values");
    }

    private static String normalizeSqlShape(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static JdbcTemplate guardedJdbc(PostgreSQLContainer<?> container, AtomicBoolean attempted) {
        DriverManagerDataSource delegate = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        AbstractDataSource dataSource = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return guardedConnection(delegate.getConnection(), attempted);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return guardedConnection(delegate.getConnection(username, password), attempted);
            }
        };
        return new JdbcTemplate(dataSource);
    }

    private static Connection guardedConnection(Connection delegate, AtomicBoolean attempted) {
        return (Connection) Proxy.newProxyInstance(
                RagShadowBaselineTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    Object[] invocationArgs = args == null ? new Object[0] : args;
                    String methodName = method.getName();
                    if (("prepareStatement".equals(methodName) || "prepareCall".equals(methodName))
                            && invocationArgs.length > 0 && invocationArgs[0] instanceof String sql) {
                        assertNoVectorStoreSql(sql, attempted);
                    }
                    Object result = invoke(delegate, method, invocationArgs);
                    if ("createStatement".equals(methodName) && result instanceof java.sql.Statement statement) {
                        return guardedStatement(statement, attempted);
                    }
                    return result;
                });
    }

    private static java.sql.Statement guardedStatement(java.sql.Statement delegate, AtomicBoolean attempted) {
        return (java.sql.Statement) Proxy.newProxyInstance(
                RagShadowBaselineTest.class.getClassLoader(),
                new Class<?>[]{java.sql.Statement.class},
                (proxy, method, args) -> {
                    Object[] invocationArgs = args == null ? new Object[0] : args;
                    if (invocationArgs.length > 0 && invocationArgs[0] instanceof String sql
                            && (method.getName().startsWith("execute") || "addBatch".equals(method.getName()))) {
                        assertNoVectorStoreSql(sql, attempted);
                    }
                    return invoke(delegate, method, invocationArgs);
                });
    }

    private static void assertNoVectorStoreSql(String sql, AtomicBoolean attempted) throws SQLException {
        if (attempted == null) {
            return;
        }
        if (sql.toLowerCase(Locale.ROOT).contains("vector_store")) {
            attempted.set(true);
            throw new SQLException("shadow must not access vector_store");
        }
    }

    private static final class DiagnosticJdbcTemplate extends JdbcTemplate {
        private final IdentifierLatencyDiagnostics diagnostics;

        private DiagnosticJdbcTemplate(
                javax.sql.DataSource dataSource, IdentifierLatencyDiagnostics diagnostics) {
            super(dataSource);
            this.diagnostics = diagnostics;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            DiagnosticSample sample = diagnostics.current.get();
            long start = System.nanoTime();
            try {
                return super.query(sql, rowMapper, args);
            } catch (RuntimeException failure) {
                if (sample != null) {
                    sample.jdbcFailureClass.compareAndSet(
                            null, failure.getClass().getSimpleName());
                }
                throw failure;
            } finally {
                if (sample != null) {
                    sample.keywordJdbcNs.addAndGet(System.nanoTime() - start);
                    sample.keywordJdbcCalls.incrementAndGet();
                }
            }
        }
    }

    private static final class Candidate5JdbcTemplate extends JdbcTemplate {
        private final CalibrationStageDiagnostics diagnostics;

        private Candidate5JdbcTemplate(
                javax.sql.DataSource dataSource,
                CalibrationStageDiagnostics diagnostics) {
            super(dataSource);
            this.diagnostics = diagnostics;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            CalibrationTrace trace = diagnostics.current.get();
            if (trace != null) {
                String normalized = normalizeSqlShape(sql);
                trace.keywordSqlShapeHashes.add(sha256Utf8(normalized));
                trace.identifierPredicateCount.addAndGet(
                        countOccurrences(normalized, "d.name ~"));
                if (normalized.contains("(^|[^[:alnum:]])")
                        && normalized.contains("([^[:alnum:]]|$)")) {
                    trace.exactIdentifierPredicate.set(true);
                }
                trace.keywordJdbcCalls.incrementAndGet();
            }
            try {
                return super.query(sql, rowMapper, args);
            } catch (RuntimeException failure) {
                if (trace != null) {
                    trace.sqlFailureClass.compareAndSet(
                            null, failure.getClass().getSimpleName());
                }
                throw failure;
            }
        }
    }

    private static final class Candidate6JdbcTemplate extends JdbcTemplate {
        private final CalibrationStageDiagnostics diagnostics;

        private Candidate6JdbcTemplate(
                javax.sql.DataSource dataSource,
                CalibrationStageDiagnostics diagnostics) {
            super(dataSource);
            this.diagnostics = diagnostics;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            CalibrationTrace trace = diagnostics.current.get();
            Candidate6VariantCapture capture = diagnostics.candidate6Variant.get();
            try {
                List<T> rows = super.query(sql, rowMapper, args);
                if (trace != null && capture != null) {
                    List<RetrievalResult> retrievalRows = castCandidate6Rows(rows);
                    @SuppressWarnings("unchecked")
                    RowMapper<RetrievalResult> retrievalRowMapper =
                            (RowMapper<RetrievalResult>) rowMapper;
                    capture.captureBusinessQuery(
                            sql,
                            retrievalRowMapper,
                            args,
                            RagCandidate6DiagnosticSupport.snapshotResults(
                                    retrievalRows, capture.identifiers));
                }
                return rows;
            } catch (RuntimeException failure) {
                if (trace != null) {
                    trace.sqlFailureClass.compareAndSet(
                            null, failure.getClass().getSimpleName());
                }
                throw failure;
            }
        }

        private List<RagCandidate6DiagnosticSupport.RetrievalSnapshot>
        executeFullAdmission(
                Candidate6VariantCapture capture,
                CalibrationTrace trace,
                int fullLimit) {
            Object[] params = capture.copiedParameters();
            candidate6Require(params.length > 0
                            && params[params.length - 1] instanceof Number
                            && ((Number) params[params.length - 1]).intValue()
                            == RagCandidate6DiagnosticSupport.BUSINESS_SQL_LIMIT,
                    "CANDIDATE6_SQL_SHAPE_INVALID");
            params[params.length - 1] = fullLimit;
            try {
                trace.diagnosticSqlCalls.incrementAndGet();
                List<RetrievalResult> rows = super.query(
                        capture.sql(), capture.rowMapper(), params);
                return RagCandidate6DiagnosticSupport.snapshotResults(
                        rows, capture.identifiers);
            } catch (RuntimeException failure) {
                trace.sqlFailureClass.compareAndSet(
                        null, failure.getClass().getSimpleName());
                throw new Candidate6DiagnosticFailure(
                        "CANDIDATE6_SQL_EXECUTION_FAILED", failure);
            }
        }

        private List<RagCandidate6DiagnosticSupport.RetrievalSnapshot>
        executeExactFirstBusinessRows(
                Candidate6VariantCapture capture,
                CalibrationTrace trace) {
            if (capture.identifiers.isEmpty()) {
                return capture.businessRows();
            }
            RagCandidate6DiagnosticSupport.ExactFirstSql priority =
                    RagCandidate6DiagnosticSupport.exactFirstSql(
                            capture.sql(), capture.copiedParameters(), capture.identifiers);
            capture.prioritySqlShapeHash =
                    sha256Utf8(normalizeSqlShape(priority.sql()));
            try {
                trace.diagnosticSqlCalls.incrementAndGet();
                List<RetrievalResult> rows = super.query(
                        priority.sql(),
                        capture.rowMapper(),
                        priority.parameters().toArray());
                return RagCandidate6DiagnosticSupport.snapshotResults(
                        rows, capture.identifiers);
            } catch (RuntimeException failure) {
                trace.sqlFailureClass.compareAndSet(
                        null, failure.getClass().getSimpleName());
                throw new Candidate6DiagnosticFailure(
                        "CANDIDATE6_SQL_EXECUTION_FAILED", failure);
            }
        }

        private static <T> List<RetrievalResult> castCandidate6Rows(List<T> rows) {
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<RetrievalResult> converted = new ArrayList<>(rows.size());
            for (T row : rows) {
                if (!(row instanceof RetrievalResult retrievalResult)) {
                    throw new Candidate6DiagnosticFailure(
                            "CANDIDATE6_JDBC_SNAPSHOT_INVALID");
                }
                converted.add(retrievalResult);
            }
            return List.copyOf(converted);
        }
    }

    private static final class Candidate8JdbcTemplate extends JdbcTemplate {
        private final CalibrationStageDiagnostics diagnostics;

        private Candidate8JdbcTemplate(
                javax.sql.DataSource dataSource,
                CalibrationStageDiagnostics diagnostics) {
            super(dataSource);
            this.diagnostics = diagnostics;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            CalibrationTrace trace = diagnostics.current.get();
            Candidate8VariantCapture capture = diagnostics.candidate8Variant.get();
            try {
                List<T> rows = super.query(sql, rowMapper, args);
                if (trace != null && capture != null) {
                    List<RetrievalResult> retrievalRows = castCandidate8Rows(rows);
                    @SuppressWarnings("unchecked")
                    RowMapper<RetrievalResult> retrievalRowMapper =
                            (RowMapper<RetrievalResult>) rowMapper;
                    capture.captureBusinessQuery(
                            sql,
                            retrievalRowMapper,
                            args,
                            RagCandidate8DiagnosticSupport.snapshotResults(
                                    retrievalRows, capture.signals));
                }
                return rows;
            } catch (RuntimeException failure) {
                if (trace != null) {
                    trace.sqlFailureClass.compareAndSet(
                            null, failure.getClass().getSimpleName());
                }
                throw failure;
            }
        }

        private List<RagCandidate8DiagnosticSupport.RetrievalSnapshot>
        executeFullOriginal(
                Candidate8VariantCapture capture,
                CalibrationTrace trace,
                int fullLimit) {
            Object[] parameters = candidate8DiagnosticParameters(capture, fullLimit);
            try {
                trace.diagnosticSqlCalls.incrementAndGet();
                List<RetrievalResult> rows = super.query(
                        capture.sql(), capture.rowMapper(), parameters);
                return RagCandidate8DiagnosticSupport.snapshotResults(
                        rows, capture.signals);
            } catch (RuntimeException failure) {
                trace.sqlFailureClass.compareAndSet(
                        null, failure.getClass().getSimpleName());
                throw new Candidate8DiagnosticFailure(
                        "CANDIDATE8_SQL_EXECUTION_FAILED", failure);
            }
        }

        private List<RagCandidate8DiagnosticSupport.RetrievalSnapshot>
        executeCorroboratedBusiness(
                Candidate8VariantCapture capture,
                CalibrationTrace trace) {
            if (capture.signals.identifiers().isEmpty()
                    || capture.signals.contentTerms().isEmpty()) {
                return capture.businessRows();
            }
            RagCandidate8DiagnosticSupport.CorroboratedFirstSql priority =
                    RagCandidate8DiagnosticSupport.corroboratedFirstSql(
                            capture.sql(), capture.copiedParameters(), capture.signals);
            capture.prioritySqlShapeHash =
                    sha256Utf8(normalizeSqlShape(priority.sql()));
            try {
                trace.diagnosticSqlCalls.incrementAndGet();
                List<RetrievalResult> rows = super.query(
                        priority.sql(),
                        capture.rowMapper(),
                        priority.parameters().toArray());
                return RagCandidate8DiagnosticSupport.snapshotResults(
                        rows, capture.signals);
            } catch (RuntimeException failure) {
                trace.sqlFailureClass.compareAndSet(
                        null, failure.getClass().getSimpleName());
                throw new Candidate8DiagnosticFailure(
                        "CANDIDATE8_SQL_EXECUTION_FAILED", failure);
            }
        }

        private static Object[] candidate8DiagnosticParameters(
                Candidate8VariantCapture capture,
                int limit) {
            Object[] parameters = capture.copiedParameters();
            candidate8Require(parameters.length > 0
                            && parameters[parameters.length - 1] instanceof Number
                            && ((Number) parameters[parameters.length - 1]).intValue()
                            == CANDIDATE8_BUSINESS_SQL_LIMIT,
                    "CANDIDATE8_SQL_SHAPE_INVALID");
            parameters[parameters.length - 1] = limit;
            return parameters;
        }

        private static <T> List<RetrievalResult> castCandidate8Rows(List<T> rows) {
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<RetrievalResult> converted = new ArrayList<>(rows.size());
            for (T row : rows) {
                if (!(row instanceof RetrievalResult retrievalResult)) {
                    throw new Candidate8DiagnosticFailure(
                            "CANDIDATE8_JDBC_SNAPSHOT_INVALID");
                }
                converted.add(retrievalResult);
            }
            return List.copyOf(converted);
        }
    }

    private static final class DiagnosticKeywordRetriever extends KeywordRetriever {
        private final String arm;
        private final IdentifierLatencyDiagnostics diagnostics;

        private DiagnosticKeywordRetriever(String arm, IdentifierLatencyDiagnostics diagnostics) {
            this.arm = arm;
            this.diagnostics = diagnostics;
        }

        @Override
        public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query, int topK) {
            DiagnosticSample sample = diagnostics.current.get();
            if (sample == null) {
                return super.retrieve(knowledgeBaseId, query, topK);
            }
            assertEquals(arm, sample.arm);
            sample.fullPathVariants.add(query);
            long start = System.nanoTime();
            try {
                return super.retrieve(knowledgeBaseId, query, topK);
            } finally {
                sample.keywordTotalNs.addAndGet(System.nanoTime() - start);
            }
        }
    }

    private static final class Candidate5KeywordRetriever extends KeywordRetriever {
        private final CalibrationStageDiagnostics diagnostics;

        private Candidate5KeywordRetriever(CalibrationStageDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query, int topK) {
            CalibrationTrace trace = diagnostics.current.get();
            if (trace != null) {
                trace.fullPathVariants.add(query);
                if (!Long.valueOf(RagCandidate5DiagnosticSupport.SELECTION_KB_ID)
                        .equals(knowledgeBaseId)) {
                    trace.selectionKnowledgeBaseMismatch.set(true);
                }
            }
            return super.retrieve(knowledgeBaseId, query, topK);
        }
    }

    private static final class Candidate6KeywordRetriever extends KeywordRetriever {
        private final CalibrationStageDiagnostics diagnostics;

        private Candidate6KeywordRetriever(CalibrationStageDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public List<RetrievalResult> retrieve(
                Long knowledgeBaseId, String query, int topK) {
            CalibrationTrace trace = diagnostics.current.get();
            if (trace == null) {
                return super.retrieve(knowledgeBaseId, query, topK);
            }
            if (!Long.valueOf(RagCandidate6DiagnosticSupport.SELECTION_KB_ID)
                    .equals(knowledgeBaseId)) {
                trace.selectionKnowledgeBaseMismatch.set(true);
            }
            candidate6Require(topK == RagCandidate6DiagnosticSupport.BUSINESS_JAVA_LIMIT,
                    "CANDIDATE6_TOPK_CONTRACT_INVALID");
            List<String> identifiers =
                    RagCandidate6DiagnosticSupport.identifierTerms(query);
            Candidate6VariantCapture capture = new Candidate6VariantCapture(
                    sha256Utf8(query), identifiers);
            trace.fullPathVariants.add(query);
            trace.candidate6Variants.add(capture);
            diagnostics.candidate6Variant.set(capture);
            try {
                List<RetrievalResult> results =
                        super.retrieve(knowledgeBaseId, query, topK);
                capture.javaTop50 = RagCandidate6DiagnosticSupport.snapshotResults(
                        results, identifiers);
                return results;
            } finally {
                diagnostics.candidate6Variant.remove();
            }
        }
    }

    private static final class Candidate8KeywordRetriever extends KeywordRetriever {
        private final CalibrationStageDiagnostics diagnostics;

        private Candidate8KeywordRetriever(CalibrationStageDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public List<RetrievalResult> retrieve(
                Long knowledgeBaseId, String query, int topK) {
            CalibrationTrace trace = diagnostics.current.get();
            if (trace == null) {
                return super.retrieve(knowledgeBaseId, query, topK);
            }
            if (!Long.valueOf(RagCandidate8DiagnosticSupport.SELECTION_KB_ID)
                    .equals(knowledgeBaseId)) {
                trace.selectionKnowledgeBaseMismatch.set(true);
            }
            candidate8Require(topK == CANDIDATE8_BUSINESS_JAVA_LIMIT,
                    "CANDIDATE8_TOPK_CONTRACT_INVALID");
            RagCandidate8DiagnosticSupport.QuerySignals signals =
                    RagCandidate8DiagnosticSupport.querySignals(query);
            Candidate8VariantCapture capture = new Candidate8VariantCapture(
                    sha256Utf8(query), signals);
            trace.fullPathVariants.add(query);
            trace.candidate8Variants.add(capture);
            diagnostics.candidate8Variant.set(capture);
            try {
                List<RetrievalResult> results =
                        super.retrieve(knowledgeBaseId, query, topK);
                capture.javaTop50 =
                        RagCandidate8DiagnosticSupport.snapshotResults(results, signals);
                return results;
            } finally {
                diagnostics.candidate8Variant.remove();
            }
        }
    }

    private static final class DiagnosticFusionService extends CandidateFusionService {
        private final IdentifierLatencyDiagnostics diagnostics;

        private DiagnosticFusionService(IdentifierLatencyDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public FusionResult fuseWithDiagnostics(
                List<List<RetrievalResult>> resultLists, List<String> pathNames) {
            DiagnosticSample sample = diagnostics.current.get();
            long start = System.nanoTime();
            FusionResult result = super.fuseWithDiagnostics(resultLists, pathNames);
            if (sample != null) {
                int keywordIndex = pathNames == null ? -1 : pathNames.indexOf("keyword");
                if (keywordIndex >= 0 && keywordIndex < resultLists.size()) {
                    sample.keywordCandidateCount.addAndGet(resultLists.get(keywordIndex).size());
                }
                sample.fusedCandidateCount.addAndGet(result.getResults().size());
                sample.fusionNs.addAndGet(System.nanoTime() - start);
            }
            return result;
        }
    }

    private static final class CalibrationFusionService extends CandidateFusionService {
        private final CalibrationStageDiagnostics diagnostics;

        private CalibrationFusionService(CalibrationStageDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public FusionResult fuseWithDiagnostics(
                List<List<RetrievalResult>> resultLists, List<String> pathNames) {
            CalibrationTrace trace = diagnostics.current.get();
            if (trace != null) {
                trace.resetPaths();
                for (int index = 0; index < resultLists.size(); index++) {
                    String pathName = pathNames != null && index < pathNames.size()
                            ? pathNames.get(index) : "path-" + index;
                    trace.paths.put(pathName, snapshotResults(resultLists.get(index)));
                }
            }
            FusionResult result = super.fuseWithDiagnostics(resultLists, pathNames);
            if (trace != null) {
                trace.fusedRanking = snapshotResults(result.getResults());
            }
            return result;
        }
    }

    private static final class CalibrationColbertScorer extends ColbertScorer {
        private final CalibrationStageDiagnostics diagnostics;
        private final FeatureHashEmbeddingModel embeddingModel;
        private final boolean captureDetachedFullRanking;
        private final boolean candidate8Capture;
        private final boolean candidate9Capture;

        private CalibrationColbertScorer(
                ColbertConfig config,
                CalibrationStageDiagnostics diagnostics,
                FeatureHashEmbeddingModel embeddingModel,
                boolean captureDetachedFullRanking,
                boolean candidate8Capture,
                boolean candidate9Capture,
                IEmbeddingService externalEmbeddingService) {
            super(config, externalEmbeddingService);
            this.diagnostics = diagnostics;
            this.embeddingModel = embeddingModel;
            this.captureDetachedFullRanking = captureDetachedFullRanking;
            this.candidate8Capture = candidate8Capture;
            this.candidate9Capture = candidate9Capture;
        }

        @Override
        public List<Document> rerank(String query, List<Document> documents, int topK) {
            CalibrationTrace trace = diagnostics.current.get();
            RagCandidate8DiagnosticSupport.QuerySignals signals = candidate8Capture
                    ? RagCandidate8DiagnosticSupport.querySignals(query) : null;
            boolean requestActive = signals != null
                    && !signals.identifiers().isEmpty()
                    && !signals.contentTerms().isEmpty();
            boolean captureFull = trace != null && captureDetachedFullRanking
                    && (!candidate8Capture || requestActive);
            List<Document> detached = captureFull
                    ? RagCandidate4DiagnosticSupport.copyDocuments(documents)
                    : List.of();
            List<Document> projected = trace != null && candidate9Capture
                    ? candidate9ProjectionDocuments(trace, query, documents)
                    : List.of();
            if (trace != null) {
                trace.filterRanking = snapshotDocuments(documents);
                if (candidate8Capture) {
                    trace.candidate8Signals = signals;
                    trace.candidate8FilterDocuments =
                            RagCandidate4DiagnosticSupport.copyDocuments(documents);
                    trace.businessColbertCalls.incrementAndGet();
                }
                if (candidate9Capture) {
                    trace.candidate9EffectiveRerankQuerySha256 = sha256Utf8(query);
                    trace.candidate9FilterDocuments =
                            RagCandidate4DiagnosticSupport.copyDocuments(documents);
                    trace.candidate9BusinessColbertCalls.incrementAndGet();
                    trace.candidate9EncodedQueryTokens =
                            RagCandidate9DiagnosticSupport.tokenCount(query);
                }
            }
            int businessEmbeddingBefore = embeddingModel.recordedInputs().size();
            List<Document> result = super.rerank(query, documents, topK);
            if (trace != null) {
                long businessEmbeddingCalls = embeddingModel.recordedInputs().size()
                        - businessEmbeddingBefore;
                trace.businessColbertEmbeddingCalls.addAndGet(
                        businessEmbeddingCalls);
                if (candidate9Capture) {
                    trace.candidate9BusinessEmbeddingCalls.addAndGet(
                            businessEmbeddingCalls);
                }
                trace.colbertRanking = snapshotDocuments(result);
                if (candidate8Capture) {
                    trace.candidate8BusinessColbertDocuments =
                            RagCandidate4DiagnosticSupport.copyDocuments(result);
                }
                if (candidate9Capture) {
                    trace.candidate9BusinessColbertDocuments =
                            RagCandidate4DiagnosticSupport.copyDocuments(result);
                }
                if (captureFull) {
                    int fullEmbeddingBefore = embeddingModel.recordedInputs().size();
                    List<Document> full =
                            super.rerank(query, detached, detached.size());
                    long baselineFullEmbeddingCalls =
                            embeddingModel.recordedInputs().size()
                                    - fullEmbeddingBefore;
                    trace.fullColbertEmbeddingCalls.addAndGet(
                            baselineFullEmbeddingCalls);
                    trace.fullColbertRanking = snapshotDocuments(full);
                    if (candidate8Capture) {
                        trace.fullColbertCalls.incrementAndGet();
                        trace.candidate8FullColbertDocuments =
                                RagCandidate4DiagnosticSupport.copyDocuments(full);
                    }
                    if (candidate9Capture) {
                        trace.candidate9BaselineFullEmbeddingCalls.addAndGet(
                                baselineFullEmbeddingCalls);
                        trace.candidate9BaselineFullColbertCalls.incrementAndGet();
                        trace.candidate9BaselineFullColbertDocuments =
                                RagCandidate4DiagnosticSupport.copyDocuments(full);
                        int projectionEmbeddingBefore =
                                embeddingModel.recordedInputs().size();
                        List<Document> projectionFull = super.rerank(
                                query, projected, projected.size());
                        trace.candidate9ProjectionFullEmbeddingCalls.addAndGet(
                                embeddingModel.recordedInputs().size()
                                        - projectionEmbeddingBefore);
                        trace.candidate9ProjectionFullColbertCalls.incrementAndGet();
                        trace.candidate9ProjectionFullColbertDocuments =
                                RagCandidate4DiagnosticSupport.copyDocuments(
                                        projectionFull);
                    }
                }
            }
            return result;
        }

        private static List<Document> candidate9ProjectionDocuments(
                CalibrationTrace trace,
                String effectiveRerankQuery,
                List<Document> documents) {
            LinkedHashSet<Long> seen = new LinkedHashSet<>();
            List<Document> projected = new ArrayList<>(documents.size());
            trace.candidate9Projections.clear();
            for (Document document : documents) {
                Long segmentId = candidate9DocumentSegmentId(document);
                candidate9Require(segmentId != null && seen.add(segmentId),
                        "CANDIDATE9_FILTER_SNAPSHOT_INVALID");
                RetrievalResult original = trace.candidatesBySegmentId.get(
                        String.valueOf(segmentId));
                candidate9Require(original != null
                                && Objects.equals(document.getText(), original.getContent()),
                        "CANDIDATE9_FILTER_SNAPSHOT_INVALID");
                RagCandidate9DiagnosticSupport.Projection projection =
                        RagCandidate9DiagnosticSupport.project(
                                effectiveRerankQuery,
                                original.getDocumentName(),
                                original.getContent());
                trace.candidate9Projections.put(segmentId, projection);
                projected.add(Document.builder()
                        .id(document.getId())
                        .text(projection.text())
                        .metadata(document.getMetadata() == null
                                ? new LinkedHashMap<>()
                                : new LinkedHashMap<>(document.getMetadata()))
                        .build());
            }
            return List.copyOf(projected);
        }
    }

    private static final class CalibrationDeterministicReranker
            extends DeterministicRerankerProvider {
        private final CalibrationStageDiagnostics diagnostics;

        private CalibrationDeterministicReranker(CalibrationStageDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public List<RetrievalResult> rerank(
                RerankRequestContext context,
                List<RetrievalResult> candidates,
                QueryIntent queryIntent,
                int topK) {
            CalibrationTrace trace = diagnostics.current.get();
            List<RetrievalResult> detached = trace == null ? List.of() : copyResults(candidates);
            List<RetrievalResult> actual = super.rerank(context, candidates, queryIntent, topK);
            if (trace != null) {
                trace.deterministicQuery = context.getQuery();
                trace.deterministicInputRanking = snapshotResults(detached);
                trace.deterministicActualRanking = snapshotResults(actual);
                List<RetrievalResult> full = super.rerank(
                        context, detached, queryIntent, detached.size());
                trace.deterministicFullRanking = snapshotResults(full);
                trace.deterministicFullResults = List.copyOf(copyResults(full));
            }
            return actual;
        }
    }

    private static final class CalibrationRerankService extends RagRerankService {
        private final CalibrationStageDiagnostics diagnostics;

        private CalibrationRerankService(CalibrationStageDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public List<RetrievalResult> rerank(
                String query,
                List<RetrievalResult> candidates,
                QueryIntent queryIntent,
                int topK,
                Long rerankingProviderName,
                String rerankingModelName) {
            CalibrationTrace trace = diagnostics.current.get();
            if (trace != null) {
                trace.rerankInput = snapshotResults(candidates);
                trace.queryIntent = queryIntent;
                trace.candidatesBySegmentId.clear();
                for (RetrievalResult candidate : copyResults(candidates)) {
                    if (candidate.getSegmentId() != null) {
                        trace.candidatesBySegmentId.put(
                                String.valueOf(candidate.getSegmentId()), candidate);
                    }
                }
            }
            List<RetrievalResult> result = super.rerank(
                    query, candidates, queryIntent, topK,
                    rerankingProviderName, rerankingModelName);
            if (trace != null) {
                trace.rerankOutput = snapshotResults(result);
            }
            return result;
        }
    }

    private static final class DiagnosticRerankService extends RagRerankService {
        private final IdentifierLatencyDiagnostics diagnostics;

        private DiagnosticRerankService(IdentifierLatencyDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public List<RetrievalResult> rerank(
                String query,
                List<RetrievalResult> candidates,
                QueryIntent queryIntent,
                int topK,
                Long rerankingProviderName,
                String rerankingModelName) {
            DiagnosticSample sample = diagnostics.current.get();
            long start = System.nanoTime();
            List<RetrievalResult> results = super.rerank(
                    query, candidates, queryIntent, topK,
                    rerankingProviderName, rerankingModelName);
            if (sample != null) {
                sample.rerankedCandidateCount.addAndGet(results.size());
                sample.rerankNs.addAndGet(System.nanoTime() - start);
            }
            return results;
        }
    }

    private static final class DiagnosticContextBuilder extends RagContextBuilder {
        private final IdentifierLatencyDiagnostics diagnostics;

        private DiagnosticContextBuilder(IdentifierLatencyDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public String buildContext(List<RetrievalResult> results, boolean expandAdjacent) {
            DiagnosticSample sample = diagnostics.current.get();
            long start = System.nanoTime();
            String context = super.buildContext(results, expandAdjacent);
            if (sample != null) {
                sample.contextBuildNs.addAndGet(System.nanoTime() - start);
            }
            return context;
        }
    }

    private static final class IdentifierLatencyDiagnostics {
        private final AtomicReference<DiagnosticSample> current = new AtomicReference<>();
        private final List<DiagnosticSample> samples = new ArrayList<>();
    }

    private static final class CalibrationStageDiagnostics {
        private final AtomicReference<CalibrationTrace> current = new AtomicReference<>();
        private final ThreadLocal<Candidate6VariantCapture> candidate6Variant =
                new ThreadLocal<>();
        private final ThreadLocal<Candidate8VariantCapture> candidate8Variant =
                new ThreadLocal<>();
        private final List<CalibrationTrace> traces = new ArrayList<>();

        private CalibrationTrace trace(String queryId) {
            return traces.stream().filter(trace -> trace.queryId.equals(queryId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "missing calibration trace: " + queryId));
        }
    }

    private static final class CalibrationTrace {
        private final String queryId;
        private final String familyId;
        private final String split;
        private final Map<String, List<RankedSegment>> paths = new LinkedHashMap<>();
        private final List<String> fullPathVariants =
                Collections.synchronizedList(new ArrayList<>());
        private final List<String> keywordSqlShapeHashes =
                Collections.synchronizedList(new ArrayList<>());
        private final AtomicLong keywordJdbcCalls = new AtomicLong();
        private final AtomicLong identifierPredicateCount = new AtomicLong();
        private final AtomicBoolean exactIdentifierPredicate = new AtomicBoolean();
        private final AtomicBoolean selectionKnowledgeBaseMismatch = new AtomicBoolean();
        private final AtomicReference<String> sqlFailureClass = new AtomicReference<>();
        private final List<Candidate6VariantCapture> candidate6Variants =
                Collections.synchronizedList(new ArrayList<>());
        private final List<Candidate8VariantCapture> candidate8Variants =
                Collections.synchronizedList(new ArrayList<>());
        private final AtomicLong diagnosticSqlCalls = new AtomicLong();
        private final AtomicLong businessColbertCalls = new AtomicLong();
        private final AtomicLong fullColbertCalls = new AtomicLong();
        private final AtomicLong businessColbertEmbeddingCalls = new AtomicLong();
        private final AtomicLong fullColbertEmbeddingCalls = new AtomicLong();
        private List<RankedSegment> fusedRanking = List.of();
        private List<RankedSegment> rerankInput = List.of();
        private List<RankedSegment> filterRanking = List.of();
        private List<RankedSegment> colbertRanking = List.of();
        private List<RankedSegment> fullColbertRanking = List.of();
        private List<RankedSegment> deterministicInputRanking = List.of();
        private List<RankedSegment> deterministicActualRanking = List.of();
        private List<RankedSegment> deterministicFullRanking = List.of();
        private List<RankedSegment> rerankOutput = List.of();
        private List<RankedSegment> finalRanking = List.of();
        private List<RankedSegment> contextRanking = List.of();
        private String deterministicQuery;
        private QueryIntent queryIntent;
        private final Map<String, RetrievalResult> candidatesBySegmentId = new LinkedHashMap<>();
        private List<RetrievalResult> deterministicFullResults = List.of();
        private RagCandidate8DiagnosticSupport.QuerySignals candidate8Signals;
        private List<Document> candidate8FilterDocuments = List.of();
        private List<Document> candidate8BusinessColbertDocuments = List.of();
        private List<Document> candidate8FullColbertDocuments = List.of();
        private final AtomicLong candidate9BusinessColbertCalls = new AtomicLong();
        private final AtomicLong candidate9BaselineFullColbertCalls = new AtomicLong();
        private final AtomicLong candidate9ProjectionFullColbertCalls = new AtomicLong();
        private final AtomicLong candidate9BusinessEmbeddingCalls = new AtomicLong();
        private final AtomicLong candidate9BaselineFullEmbeddingCalls = new AtomicLong();
        private final AtomicLong candidate9ProjectionFullEmbeddingCalls = new AtomicLong();
        private final Map<Long, RagCandidate9DiagnosticSupport.Projection>
                candidate9Projections = new LinkedHashMap<>();
        private List<Document> candidate9FilterDocuments = List.of();
        private List<Document> candidate9BusinessColbertDocuments = List.of();
        private List<Document> candidate9BaselineFullColbertDocuments = List.of();
        private List<Document> candidate9ProjectionFullColbertDocuments = List.of();
        private String candidate9EffectiveRerankQuerySha256;
        private int candidate9EncodedQueryTokens;
        private String finalContextSha256;
        private boolean finalContextEmpty;

        private CalibrationTrace(String queryId, String familyId, String split) {
            this.queryId = queryId;
            this.familyId = familyId;
            this.split = split;
            resetPaths();
        }

        private void resetPaths() {
            paths.clear();
            paths.put("vector", List.of());
            paths.put("keyword", List.of());
            paths.put("metadata", List.of());
            paths.put("graph", List.of());
        }

        private Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("queryId", queryId);
            value.put("familyId", familyId);
            value.put("split", split);
            Map<String, Object> pathValues = new LinkedHashMap<>();
            paths.forEach((path, ranking) -> pathValues.put(path, rankingMaps(ranking)));
            value.put("paths", pathValues);
            value.put("fused", rankingMaps(fusedRanking));
            value.put("rerankInput", rankingMaps(rerankInput));
            value.put("filterOutput", rankingMaps(filterRanking));
            value.put("colbertOutput", rankingMaps(colbertRanking));
            value.put("deterministicActual", rankingMaps(deterministicActualRanking));
            value.put("deterministicFull", rankingMaps(deterministicFullRanking));
            value.put("finalSources", rankingMaps(finalRanking));
            value.put("contextSegments", rankingMaps(contextRanking));
            return value;
        }
    }

    private static final class Candidate6VariantCapture {
        private final String variantSha256;
        private final List<String> identifiers;
        private String sql;
        private RowMapper<RetrievalResult> rowMapper;
        private List<Object> parameters = List.of();
        private List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> businessRows =
                List.of();
        private List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> javaTop50 =
                List.of();
        private String prioritySqlShapeHash;

        private Candidate6VariantCapture(
                String variantSha256, List<String> identifiers) {
            this.variantSha256 = variantSha256;
            this.identifiers = List.copyOf(identifiers);
        }

        private void captureBusinessQuery(
                String capturedSql,
                RowMapper<RetrievalResult> capturedRowMapper,
                Object[] capturedParameters,
                List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> rows) {
            candidate6Require(sql == null && rowMapper == null,
                    "CANDIDATE6_MULTIPLE_JDBC_CALLS_PER_VARIANT");
            sql = capturedSql;
            rowMapper = capturedRowMapper;
            parameters = List.copyOf(java.util.Arrays.asList(
                    java.util.Arrays.copyOf(
                            capturedParameters, capturedParameters.length)));
            businessRows = List.copyOf(rows);
        }

        private String sql() {
            candidate6Require(sql != null && rowMapper != null,
                    "CANDIDATE6_JDBC_SNAPSHOT_INVALID");
            return sql;
        }

        private RowMapper<RetrievalResult> rowMapper() {
            candidate6Require(rowMapper != null,
                    "CANDIDATE6_JDBC_SNAPSHOT_INVALID");
            return rowMapper;
        }

        private Object[] copiedParameters() {
            candidate6Require(!parameters.isEmpty(),
                    "CANDIDATE6_JDBC_SNAPSHOT_INVALID");
            return parameters.toArray();
        }

        private List<RagCandidate6DiagnosticSupport.RetrievalSnapshot>
        businessRows() {
            return businessRows;
        }

        private void clearParameters() {
            parameters = List.of();
            rowMapper = null;
            sql = null;
        }
    }

    private static final class Candidate8VariantCapture {
        private final String variantSha256;
        private final RagCandidate8DiagnosticSupport.QuerySignals signals;
        private String sql;
        private RowMapper<RetrievalResult> rowMapper;
        private List<Object> parameters = List.of();
        private List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> businessRows =
                List.of();
        private List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> javaTop50 =
                List.of();
        private String prioritySqlShapeHash;

        private Candidate8VariantCapture(
                String variantSha256,
                RagCandidate8DiagnosticSupport.QuerySignals signals) {
            this.variantSha256 = variantSha256;
            this.signals = signals;
        }

        private void captureBusinessQuery(
                String capturedSql,
                RowMapper<RetrievalResult> capturedRowMapper,
                Object[] capturedParameters,
                List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> rows) {
            candidate8Require(sql == null && rowMapper == null,
                    "CANDIDATE8_MULTIPLE_JDBC_CALLS_PER_VARIANT");
            sql = capturedSql;
            rowMapper = capturedRowMapper;
            parameters = List.copyOf(java.util.Arrays.asList(
                    java.util.Arrays.copyOf(
                            capturedParameters, capturedParameters.length)));
            businessRows = List.copyOf(rows);
        }

        private String sql() {
            candidate8Require(sql != null && rowMapper != null,
                    "CANDIDATE8_JDBC_SNAPSHOT_INVALID");
            return sql;
        }

        private RowMapper<RetrievalResult> rowMapper() {
            candidate8Require(rowMapper != null,
                    "CANDIDATE8_JDBC_SNAPSHOT_INVALID");
            return rowMapper;
        }

        private Object[] copiedParameters() {
            candidate8Require(!parameters.isEmpty(),
                    "CANDIDATE8_JDBC_SNAPSHOT_INVALID");
            return parameters.toArray();
        }

        private List<RagCandidate8DiagnosticSupport.RetrievalSnapshot>
        businessRows() {
            return businessRows;
        }

        private void clearParameters() {
            parameters = List.of();
            rowMapper = null;
            sql = null;
        }
    }

    private record RankedSegment(String segmentId, int rank, double score) {
        private Map<String, Object> toMap() {
            return Map.of("segmentId", segmentId, "rank", rank, "score", score);
        }
    }

    private record Candidate3RankedCandidate(
            RetrievalResult result, int originalRank, boolean identifierMatch) {
    }

    private record Candidate3Evidence(
            String queryId,
            String familyId,
            String split,
            boolean answerable,
            List<Map<String, Object>> ranking,
            List<String> baselineIds,
            List<String> counterfactualIds,
            List<Double> baselineScores,
            List<Double> counterfactualScores,
            String baselineContextSha256,
            String counterfactualContextSha256,
            boolean baselineContextEmpty,
            boolean counterfactualContextEmpty,
            RagMetrics.Scores baselineMetrics,
            RagMetrics.Scores counterfactualMetrics,
            int identifierMatchCount,
            boolean uniqueTrustedMatch,
            boolean relevantAtRankOne,
            boolean behaviorChanged,
            boolean scorePolicyValid) {

        private Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("queryId", queryId);
            value.put("familyId", familyId);
            value.put("split", split);
            value.put("answerable", answerable);
            value.put("ranking", ranking);
            value.put("baselineSourceSegmentIds", baselineIds);
            value.put("counterfactualSourceSegmentIds", counterfactualIds);
            value.put("baselineScores", baselineScores);
            value.put("counterfactualScores", counterfactualScores);
            value.put("baselineContextSha256", baselineContextSha256);
            value.put("counterfactualContextSha256", counterfactualContextSha256);
            value.put("baselineContextEmpty", baselineContextEmpty);
            value.put("counterfactualContextEmpty", counterfactualContextEmpty);
            value.put("baselineRetrievalAP@10", baselineMetrics.retrievalApAt10());
            value.put("counterfactualRetrievalAP@10",
                    counterfactualMetrics.retrievalApAt10());
            value.put("baselineNDCG@10", baselineMetrics.ndcgAt10());
            value.put("counterfactualNDCG@10", counterfactualMetrics.ndcgAt10());
            value.put("identifierMatchCount", identifierMatchCount);
            value.put("uniqueTrustedMatch", uniqueTrustedMatch);
            value.put("relevantAtRankOne", relevantAtRankOne);
            value.put("behaviorChanged", behaviorChanged);
            value.put("scorePolicyValid", scorePolicyValid);
            return value;
        }
    }

    private static List<Map<String, Object>> rankingMaps(List<RankedSegment> ranking) {
        return ranking.stream().map(RankedSegment::toMap).toList();
    }

    private record Candidate5CaseArtifact(
            RagCandidate5DiagnosticSupport.CaseEvidence evidence,
            String originalQuerySha256,
            String retrievalQuerySha256,
            int extractedIdentifierCount,
            String extractedIdentifierHash,
            int searchTermCount,
            String searchTermsHash,
            String sqlShapeHash,
            int identifierPredicateCount,
            Map<String, Long> callCounts) {

        private Map<String, Object> toMap() {
            return evidence.toMap(
                    originalQuerySha256,
                    retrievalQuerySha256,
                    extractedIdentifierCount,
                    extractedIdentifierHash,
                    searchTermCount,
                    searchTermsHash,
                    sqlShapeHash,
                    identifierPredicateCount,
                    callCounts);
        }
    }

    private record Candidate6VariantAnalysis(
            String variantSha256,
            int extractedIdentifierCount,
            String sqlShapeHash,
            String prioritySqlShapeHash,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> businessRows,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> businessJavaTop50,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> fullAdmission,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> priorityBusinessRows,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> sqlTop50,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> javaTop50,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> dualTop50) {

        private Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("variantSha256", variantSha256);
            value.put("extractedIdentifierCount", extractedIdentifierCount);
            value.put("sqlShapeHash", sqlShapeHash);
            value.put("prioritySqlShapeHash", prioritySqlShapeHash);
            value.put("businessRows", candidate6SnapshotMaps(businessRows));
            value.put("businessJavaTop50", candidate6SnapshotMaps(businessJavaTop50));
            value.put("fullAdmission", candidate6SnapshotMaps(fullAdmission));
            value.put("priorityBusinessRows", candidate6SnapshotMaps(priorityBusinessRows));
            value.put("sqlTop50", candidate6SnapshotMaps(sqlTop50));
            value.put("javaTop50", candidate6SnapshotMaps(javaTop50));
            value.put("dualTop50", candidate6SnapshotMaps(dualTop50));
            return value;
        }
    }

    private record Candidate8VariantAnalysis(
            String variantSha256,
            int extractedIdentifierCount,
            String extractedIdentifierHash,
            int extractedContentTermCount,
            String extractedContentTermHash,
            String sqlShapeHash,
            String prioritySqlShapeHash,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> businessRows,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> baselineJavaTop50,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> fullOriginal,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> priorityBusinessRows,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> admissionJavaTop50) {

        private Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("variantSha256", variantSha256);
            value.put("extractedIdentifierCount", extractedIdentifierCount);
            value.put("extractedIdentifierHash", extractedIdentifierHash);
            value.put("extractedContentTermCount", extractedContentTermCount);
            value.put("extractedContentTermHash", extractedContentTermHash);
            value.put("sqlShapeHash", sqlShapeHash);
            value.put("prioritySqlShapeHash", prioritySqlShapeHash);
            value.put("businessRows", candidate8SnapshotMaps(businessRows));
            value.put("baselineJavaTop50",
                    candidate8SnapshotMaps(baselineJavaTop50));
            value.put("fullOriginal", candidate8SnapshotMaps(fullOriginal));
            value.put("priorityBusinessRows",
                    candidate8SnapshotMaps(priorityBusinessRows));
            value.put("admissionJavaTop50",
                    candidate8SnapshotMaps(admissionJavaTop50));
            return value;
        }
    }

    private record Candidate8CapturedCase(
            RagEvaluationDataset.QueryCase queryCase,
            CalibrationTrace trace,
            RagResult baseline,
            RagCandidate8DiagnosticSupport.QuerySignals signals,
            List<Candidate8VariantAnalysis> variants,
            Set<Long> exactEvidence,
            RagCandidate8DiagnosticSupport.RootCause classification,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> baselineKeyword,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> admissionKeyword,
            DbCallSnapshot sutCalls,
            long diagnosticSqlCalls,
            int sutEmbeddingCalls) {
    }

    private record Candidate8Replay(
            boolean admission,
            boolean survival,
            List<RetrievalResult> sources,
            String context,
            DbCallSnapshot diagnosticCalls,
            long diagnosticExternalCalls,
            int diagnosticEmbeddingCalls,
            CalibrationTrace trace,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> filterOrder,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> businessPrefix,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> fullColbert,
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> tieOutput,
            boolean tieChanged,
            boolean cutoffMechanismValid) {

        private static Candidate8Replay fromBaseline(
                Candidate8CapturedCase captured) {
            String context = Objects.requireNonNullElse(
                    captured.baseline().getContext(), "");
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> filter =
                    candidate8DocumentSnapshots(
                            captured.trace(),
                            captured.trace().candidate8FilterDocuments,
                            captured.signals());
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> prefix =
                    candidate8DocumentSnapshots(
                            captured.trace(),
                            captured.trace().candidate8BusinessColbertDocuments,
                            captured.signals());
            List<RagCandidate8DiagnosticSupport.RetrievalSnapshot> full =
                    candidate8DocumentSnapshots(
                            captured.trace(),
                            captured.trace().candidate8FullColbertDocuments,
                            captured.signals());
            return new Candidate8Replay(
                    false,
                    false,
                    copyResults(captured.baseline().getSources()),
                    context,
                    new DbCallSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L),
                    0L,
                    0,
                    captured.trace(),
                    filter,
                    prefix,
                    full,
                    prefix,
                    false,
                    false);
        }
    }

    private record Candidate8RankedCase(
            Candidate8CapturedCase captured,
            Candidate8Replay baseline,
            Candidate8Replay admissionOnly,
            Candidate8Replay survivalOnly,
            Candidate8Replay joint,
            String rankingHash) {
    }

    private record Candidate8CaseArtifact(
            RagCandidate8DiagnosticSupport.CaseEvidence evidence,
            String split,
            String originalQuerySha256,
            String retrievalQuerySha256,
            int extractedIdentifierCount,
            String extractedIdentifierHash,
            int extractedContentTermCount,
            String extractedContentTermHash,
            RagCandidate8DiagnosticSupport.RootCause classification,
            List<Candidate8VariantAnalysis> variants,
            String rankingHash,
            Candidate8Replay baseline,
            Candidate8Replay admissionOnly,
            Candidate8Replay survivalOnly,
            Candidate8Replay joint,
            Map<String, Long> callCounts) {

        private Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("queryId", evidence.queryId());
            value.put("familyId", evidence.familyId());
            value.put("language", evidence.language());
            value.put("split", split);
            value.put("role", evidence.role());
            value.put("target", evidence.target());
            value.put("originalQuerySha256", originalQuerySha256);
            value.put("retrievalQuerySha256", retrievalQuerySha256);
            value.put("extractedIdentifierCount", extractedIdentifierCount);
            value.put("extractedIdentifierHash", extractedIdentifierHash);
            value.put("extractedContentTermCount", extractedContentTermCount);
            value.put("extractedContentTermHash", extractedContentTermHash);
            value.put("classification", classification.name());
            value.put("mechanismValid", evidence.mechanismValid());
            value.put("admissionOnlyUnchanged",
                    evidence.admissionOnlyUnchanged());
            value.put("survivalOnlyUnchanged",
                    evidence.survivalOnlyUnchanged());
            value.put("controlUnchanged", evidence.controlUnchanged());
            value.put("baselineAP@10", evidence.baselineAp());
            value.put("baselineNDCG@10", evidence.baselineNdcg());
            value.put("jointAP@10", evidence.jointAp());
            value.put("jointNDCG@10", evidence.jointNdcg());
            value.put("rankingSha256", rankingHash);
            value.put("variants", variants.stream()
                    .map(Candidate8VariantAnalysis::toMap).toList());
            Map<String, Object> arms = new LinkedHashMap<>();
            arms.put("BASELINE", candidate8ArmEvidence(baseline));
            arms.put("ADMISSION_ONLY", candidate8ArmEvidence(admissionOnly));
            arms.put("SURVIVAL_ONLY", candidate8ArmEvidence(survivalOnly));
            arms.put("JOINT", candidate8ArmEvidence(joint));
            value.put("arms", arms);
            value.put("callCounts", callCounts);
            return value;
        }
    }

    private record Candidate6CapturedCase(
            RagEvaluationDataset.QueryCase queryCase,
            CalibrationTrace trace,
            RagResult baseline,
            List<Candidate6VariantAnalysis> variants,
            Set<Long> exactEvidence,
            RagCandidate6DiagnosticSupport.RootCause classification,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> baselineKeyword,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> sqlKeyword,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> javaKeyword,
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> dualKeyword,
            DbCallSnapshot sutCalls,
            long diagnosticSqlCalls,
            int sutEmbeddingCalls) {
    }

    private record Candidate6Replay(
            List<RetrievalResult> sources,
            String context,
            long contextDbCalls,
            List<RankedSegment> contextRanking) {

        private static Candidate6Replay fromBaseline(RagResult baseline) {
            String context = Objects.requireNonNullElse(baseline.getContext(), "");
            return new Candidate6Replay(
                    copyResults(baseline.getSources()),
                    context,
                    0L,
                    RagShadowBaselineTest.contextRanking(context));
        }
    }

    private record Candidate6CaseArtifact(
            RagCandidate6DiagnosticSupport.CaseEvidence evidence,
            String split,
            String originalQuerySha256,
            String retrievalQuerySha256,
            List<Long> exactEvidenceSegmentIds,
            List<Candidate6VariantAnalysis> variants,
            Map<String, List<RankedSegment>> paths,
            List<RankedSegment> fusedRanking,
            List<RankedSegment> filterRanking,
            List<RankedSegment> colbertRanking,
            List<RankedSegment> deterministicRanking,
            List<RetrievalResult> baselineSources,
            List<RetrievalResult> counterfactualSources,
            String baselineContextSha256,
            String counterfactualContextSha256,
            boolean baselineContextEmpty,
            boolean counterfactualContextEmpty) {

        private Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("queryId", evidence.queryId());
            value.put("familyId", evidence.familyId());
            value.put("language", evidence.language());
            value.put("split", split);
            value.put("identifierShape", evidence.identifierShape());
            value.put("target", evidence.target());
            value.put("noExactMatchControl", evidence.noExactMatchControl());
            value.put("safetyControl", evidence.safetyControl());
            value.put("originalQuerySha256", originalQuerySha256);
            value.put("retrievalQuerySha256", retrievalQuerySha256);
            value.put("exactEvidenceSegmentIds", exactEvidenceSegmentIds);
            value.put("classification", evidence.classification().name());
            value.put("counterfactualMode", evidence.counterfactualMode().name());
            value.put("variants", variants.stream()
                    .map(Candidate6VariantAnalysis::toMap).toList());
            Map<String, Object> pathValues = new LinkedHashMap<>();
            paths.forEach((name, ranking) ->
                    pathValues.put(name, rankingMaps(ranking)));
            value.put("paths", pathValues);
            value.put("fusedRanking", rankingMaps(fusedRanking));
            value.put("filterRanking", rankingMaps(filterRanking));
            value.put("colbertRanking", rankingMaps(colbertRanking));
            value.put("deterministicRanking", rankingMaps(deterministicRanking));
            value.put("baselineFinalSources", candidate6ResultMaps(baselineSources));
            value.put("counterfactualFinalSources",
                    candidate6ResultMaps(counterfactualSources));
            value.put("baselineContextSha256", baselineContextSha256);
            value.put("counterfactualContextSha256", counterfactualContextSha256);
            value.put("baselineContextEmpty", baselineContextEmpty);
            value.put("counterfactualContextEmpty", counterfactualContextEmpty);
            value.put("baselineAP@10", evidence.baselineAp());
            value.put("baselineNDCG@10", evidence.baselineNdcg());
            value.put("counterfactualAP@10", evidence.counterfactualAp());
            value.put("counterfactualNDCG@10", evidence.counterfactualNdcg());
            value.put("behaviorUnchanged", evidence.behaviorUnchanged());
            value.put("safetyValid", evidence.safetyValid());
            value.put("callCounts", Map.of(
                    "sutDbCalls", evidence.sutDbCalls(),
                    "diagnosticSqlCalls", evidence.diagnosticSqlCalls(),
                    "diagnosticContextDbCalls", evidence.diagnosticContextDbCalls(),
                    "diagnosticTotalDbCalls", evidence.diagnosticTotalDbCalls(),
                    "addedEmbeddingCalls", evidence.addedEmbeddingCalls(),
                    "addedVectorCalls", evidence.addedVectorCalls(),
                    "addedMetadataCalls", evidence.addedMetadataCalls(),
                    "addedGraphCalls", evidence.addedGraphCalls(),
                    "addedNetworkCalls", evidence.addedNetworkCalls()));
            return value;
        }
    }

    private static List<Map<String, Object>> candidate6SnapshotMaps(
            List<RagCandidate6DiagnosticSupport.RetrievalSnapshot> snapshots) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < snapshots.size(); index++) {
            values.add(snapshots.get(index).sanitized(index + 1));
        }
        return List.copyOf(values);
    }

    private static List<Map<String, Object>> candidate6ResultMaps(
            List<RetrievalResult> results) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < results.size(); index++) {
            RetrievalResult result = results.get(index);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("segmentId", result.getSegmentId());
            value.put("documentId", result.getDocumentId());
            value.put("rank", index + 1);
            value.put("score", result.getScore());
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static final class Candidate5DiagnosticFailure extends IllegalStateException {
        private final String errorCode;

        private Candidate5DiagnosticFailure(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        private Candidate5DiagnosticFailure(String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
        }
    }

    private static final class Candidate6DiagnosticFailure extends IllegalStateException {
        private final String errorCode;

        private Candidate6DiagnosticFailure(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        private Candidate6DiagnosticFailure(String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
        }
    }

    private static final class Candidate8DiagnosticFailure extends IllegalStateException {
        private final String errorCode;

        private Candidate8DiagnosticFailure(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        private Candidate8DiagnosticFailure(String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
        }
    }

    private static final class Candidate9DiagnosticFailure extends IllegalStateException {
        private final String errorCode;

        private Candidate9DiagnosticFailure(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        private Candidate9DiagnosticFailure(String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
        }
    }

    private static void candidate6Require(boolean condition, String errorCode) {
        if (!condition) {
            throw new Candidate6DiagnosticFailure(errorCode);
        }
    }

    private static void candidate8Require(boolean condition, String errorCode) {
        if (!condition) {
            throw new Candidate8DiagnosticFailure(errorCode);
        }
    }

    private static void candidate9Require(boolean condition, String errorCode) {
        if (!condition) {
            throw new Candidate9DiagnosticFailure(errorCode);
        }
    }

    private record CalibrationEvidence(
            CalibrationTrace trace,
            String classification,
            RagMetrics.Scores finalScores,
            RagMetrics.Scores recoverableStageScores,
            int relevantSegmentCount,
            String bestPath) {
        private double recoverableAp() {
            return Math.max(0.0D,
                    recoverableStageScores.retrievalApAt10() - finalScores.retrievalApAt10());
        }

        private double recoverableNdcg() {
            return Math.max(0.0D,
                    recoverableStageScores.ndcgAt10() - finalScores.ndcgAt10());
        }

        private Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>(trace.toMap());
            value.put("classification", classification);
            value.put("bestPath", bestPath);
            value.put("finalRetrievalApAt10", finalScores.retrievalApAt10());
            value.put("finalNdcgAt10", finalScores.ndcgAt10());
            value.put("recoverableRetrievalApAt10UpperBound", recoverableAp());
            value.put("recoverableNdcgAt10UpperBound", recoverableNdcg());
            return value;
        }
    }

    private static final class DiagnosticSample {
        private final int round;
        private final int queryOrdinal;
        private final String arm;
        private final String queryId;
        private final List<String> fullPathVariants = Collections.synchronizedList(new ArrayList<>());
        private final AtomicLong termExtractionNs = new AtomicLong();
        private final AtomicLong keywordJdbcNs = new AtomicLong();
        private final AtomicLong keywordJdbcCalls = new AtomicLong();
        private final AtomicLong keywordTotalNs = new AtomicLong();
        private final AtomicLong keywordCandidateCount = new AtomicLong();
        private final AtomicLong fusedCandidateCount = new AtomicLong();
        private final AtomicLong rerankedCandidateCount = new AtomicLong();
        private final AtomicLong fusionNs = new AtomicLong();
        private final AtomicLong rerankNs = new AtomicLong();
        private final AtomicLong contextBuildNs = new AtomicLong();
        private final AtomicLong totalNs = new AtomicLong();
        private final AtomicReference<String> jdbcFailureClass = new AtomicReference<>();
        private final AtomicReference<String> retrievalFailureClass = new AtomicReference<>();
        private long dbCalls;
        private long embeddingCalls;
        private long sourceCount;
        private long fullPathVariantCount;

        private DiagnosticSample(int round, int queryOrdinal, String arm, String queryId) {
            this.round = round;
            this.queryOrdinal = queryOrdinal;
            this.arm = arm;
            this.queryId = queryId;
        }

        private long stage(String name) {
            return switch (name) {
                case "termExtractionNs" -> termExtractionNs.get();
                case "keywordJdbcNs" -> keywordJdbcNs.get();
                case "keywordTotalNs" -> keywordTotalNs.get();
                case "keywordCandidateCount" -> keywordCandidateCount.get();
                case "fusedCandidateCount" -> fusedCandidateCount.get();
                case "rerankedCandidateCount" -> rerankedCandidateCount.get();
                case "fusionNs" -> fusionNs.get();
                case "rerankNs" -> rerankNs.get();
                case "contextBuildNs" -> contextBuildNs.get();
                case "downstreamNs" -> fusionNs.get() + rerankNs.get() + contextBuildNs.get();
                case "totalNs" -> totalNs.get();
                case "dbCalls" -> dbCalls;
                case "embeddingCalls" -> embeddingCalls;
                default -> throw new IllegalArgumentException("unknown diagnostic stage: " + name);
            };
        }

        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("round", round);
            values.put("queryOrdinal", queryOrdinal);
            values.put("arm", arm);
            values.put("queryId", queryId);
            values.put("termExtractionNs", termExtractionNs.get());
            values.put("keywordJdbcNs", keywordJdbcNs.get());
            values.put("keywordTotalNs", keywordTotalNs.get());
            values.put("keywordCandidateCount", keywordCandidateCount.get());
            values.put("fusedCandidateCount", fusedCandidateCount.get());
            values.put("rerankedCandidateCount", rerankedCandidateCount.get());
            values.put("fusionNs", fusionNs.get());
            values.put("rerankNs", rerankNs.get());
            values.put("contextBuildNs", contextBuildNs.get());
            values.put("totalNs", totalNs.get());
            values.put("dbCalls", dbCalls);
            values.put("embeddingCalls", embeddingCalls);
            values.put("fullPathVariantCount", fullPathVariantCount);
            values.put("sourceCount", sourceCount);
            return values;
        }
    }

    private record DiagnosticLoop(
            int round, String arm, long totalNs, long dbCalls, long embeddingCalls) {
        private Map<String, Object> toMap() {
            return Map.of(
                    "round", round,
                    "arm", arm,
                    "totalNs", totalNs,
                    "dbCalls", dbCalls,
                    "embeddingCalls", embeddingCalls);
        }
    }

    private record StageEvidence(
            String name,
            long medianDeltaNs,
            int positiveRounds,
            int positiveTopQueries,
            boolean qualifies) {
        private Map<String, Object> toMap() {
            return Map.of(
                    "name", name,
                    "medianDeltaNs", medianDeltaNs,
                    "positiveRounds", positiveRounds,
                    "positiveTopQueries", positiveTopQueries,
                    "qualifies", qualifies);
        }
    }

    private enum DbCallCategory {
        KEYWORD,
        METADATA,
        VECTOR,
        CONTEXT,
        OTHER
    }

    private static final class QueryCounters {
        private final AtomicLong keyword = new AtomicLong();
        private final AtomicLong metadata = new AtomicLong();
        private final AtomicLong vector = new AtomicLong();
        private final AtomicLong contextParent = new AtomicLong();
        private final AtomicLong contextAdjacent = new AtomicLong();
        private final AtomicLong contextOther = new AtomicLong();
        private final AtomicLong other = new AtomicLong();

        private void record(DbCallCategory category, String sql) {
            switch (category) {
                case KEYWORD -> keyword.incrementAndGet();
                case METADATA -> metadata.incrementAndGet();
                case VECTOR -> vector.incrementAndGet();
                case CONTEXT -> recordContext(sql);
                case OTHER -> other.incrementAndGet();
            }
        }

        private void recordContext(String sql) {
            String normalized = sql.toLowerCase(Locale.ROOT);
            if (normalized.contains(CONTEXT_PARENT_SQL_FINGERPRINT)) {
                contextParent.incrementAndGet();
            } else if (normalized.contains(CONTEXT_ADJACENT_PREVIOUS_SQL_FINGERPRINT)
                    && normalized.contains(CONTEXT_ADJACENT_NEXT_SQL_FINGERPRINT)) {
                contextAdjacent.incrementAndGet();
            } else {
                contextOther.incrementAndGet();
            }
        }

        private DbCallSnapshot snapshot() {
            return new DbCallSnapshot(keyword.get(), metadata.get(), vector.get(),
                    contextParent.get(), contextAdjacent.get(), contextOther.get(), other.get());
        }

        private void reset() {
            keyword.set(0L);
            metadata.set(0L);
            vector.set(0L);
            contextParent.set(0L);
            contextAdjacent.set(0L);
            contextOther.set(0L);
            other.set(0L);
        }
    }

    private record DbCallSnapshot(long keyword,
                                  long metadata,
                                  long vector,
                                  long contextParent,
                                  long contextAdjacent,
                                  long contextOther,
                                  long other) {
        private long context() {
            return contextParent + contextAdjacent + contextOther;
        }

        private long total() {
            return keyword + metadata + vector + context() + other;
        }

        private DbCallSnapshot minus(DbCallSnapshot before) {
            return new DbCallSnapshot(
                    keyword - before.keyword,
                    metadata - before.metadata,
                    vector - before.vector,
                    contextParent - before.contextParent,
                    contextAdjacent - before.contextAdjacent,
                    contextOther - before.contextOther,
                    other - before.other);
        }
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        jdbc.execute("CREATE TABLE kmc_document (id BIGINT PRIMARY KEY, knowledge_base_id BIGINT NOT NULL, "
                + "name TEXT NOT NULL, del_flag SMALLINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE kmc_document_segment (id BIGINT PRIMARY KEY, document_id BIGINT NOT NULL, "
                + "content TEXT NOT NULL, document_name TEXT, answer TEXT, position INTEGER, qm_segment_id TEXT, "
                + "parent_id TEXT, del_flag SMALLINT NOT NULL DEFAULT 0, "
                + "content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED)");
        jdbc.execute("CREATE TABLE kmc_segment_entity_metadata (id BIGSERIAL PRIMARY KEY, segment_id BIGINT NOT NULL, "
                + "entities JSONB NOT NULL)");
        jdbc.execute("CREATE INDEX kmc_segment_content_tsv_idx ON kmc_document_segment USING GIN(content_tsv)");
        jdbc.execute("CREATE INDEX kmc_segment_content_trgm_idx ON kmc_document_segment USING GIN(content gin_trgm_ops)");
        jdbc.execute("CREATE INDEX kmc_segment_document_position_idx ON kmc_document_segment(document_id, position, id)");
        jdbc.execute("CREATE INDEX kmc_segment_entity_idx ON kmc_segment_entity_metadata USING GIN(entities)");
        jdbc.execute("CREATE INDEX kmc_document_kb_active_idx ON kmc_document(knowledge_base_id, id) "
                + "WHERE del_flag = 0");
        jdbc.execute("CREATE INDEX kmc_document_name_trgm_active_idx ON kmc_document "
                + "USING GIN(name gin_trgm_ops) WHERE del_flag = 0");
    }

    private static void insertSentinels(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO kmc_document(id, knowledge_base_id, name) VALUES (?, ?, ?)",
                99001L, SENTINEL_KB_ID, "sentinel-context");
        List<Object[]> segments = List.of(
                new Object[]{990001L, "VECTOR-SENTINEL-AZURE-QUARTZ-990001", "990001", null, 0},
                new Object[]{990002L, "KEYWORD-SENTINEL-COBALT-LANTERN-990002", "990002", null, 1},
                new Object[]{990003L, "Metadata sentinel evidence.", "990003", null, 2},
                new Object[]{990004L, "CTX-SENTINEL-CHILD", "990004", "990005", 3},
                new Object[]{990005L, "CTX-SENTINEL-PARENT contains the reserved parent context.", "990005", null, 4});
        for (Object[] segment : segments) {
            jdbc.update("INSERT INTO kmc_document_segment(id, document_id, content, document_name, qm_segment_id, parent_id, position) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", segment[0], 99001L, segment[1], "sentinel-context", segment[2], segment[3], segment[4]);
        }
        jdbc.update("INSERT INTO kmc_segment_entity_metadata(segment_id, entities) VALUES (?, ?::jsonb)",
                990003L, "[\"META-SENTINEL-ENTITY\"]");
    }

    private static VectorStore buildVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> documents = new ArrayList<>();
        Map<String, Object> common = new LinkedHashMap<>();
        common.put(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, SENTINEL_KB_ID);
        common.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, 99001L);
        common.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME, "sentinel-context");
        documents.add(document("990001", "VECTOR-SENTINEL-AZURE-QUARTZ-990001", common, 990001L, null));
        documents.add(document("990002", "KEYWORD-SENTINEL-COBALT-LANTERN-990002", common, 990002L, null));
        documents.add(document("990003", "Metadata sentinel evidence.", common, 990003L, null));
        documents.add(document("990004", "CTX-SENTINEL-CHILD", common, 990004L, "990005"));
        documents.add(document("990005", "CTX-SENTINEL-PARENT contains the reserved parent context.", common, 990005L, null));
        store.add(documents);
        return store;
    }

    private static Document document(String id, String text, Map<String, Object> common,
                                     long segmentId, String parentId) {
        Map<String, Object> metadata = new LinkedHashMap<>(common);
        metadata.put(WeaviateConstant.METADATA_FIELD_SEGMENT_ID, segmentId);
        if (parentId != null) metadata.put("parent_segment_id", parentId);
        return Document.builder().id(id).text(text).metadata(metadata).build();
    }

    private record RetrievalInvocation(long knowledgeBaseId, String query, String retrievalQuery,
                                       int topK, boolean includeGraph) {
    }
}
