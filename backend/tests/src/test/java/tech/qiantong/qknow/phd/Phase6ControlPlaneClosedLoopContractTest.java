package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.RetrievalEvaluator;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.benchmark.evaluation.AcademicBenchmarkEvaluationHarness;
import tech.qiantong.qknow.hermes.benchmark.evaluation.AcademicBenchmarkTask;
import tech.qiantong.qknow.hermes.benchmark.evaluation.BenchmarkEvaluationReport;
import tech.qiantong.qknow.hermes.cognitive.control.BudgetVector6D;
import tech.qiantong.qknow.hermes.cognitive.control.ReasoningEffortInjectingChatModel;
import tech.qiantong.qknow.hermes.cognitive.control.StepDriftTransitionReceipt;
import tech.qiantong.qknow.hermes.cognitive.control.StepEvidenceDriftTracker;
import tech.qiantong.qknow.hermes.cognitive.control.SupernetControlPlaneDispatcher;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.control.VariationalUtilityEvaluator;
import tech.qiantong.qknow.hermes.cognitive.control.VariationalUtilityReceipt;
import tech.qiantong.qknow.hermes.cognitive.evolution.RhoEvolutionReceipt;
import tech.qiantong.qknow.hermes.cognitive.evolution.RhoSelfPreferenceOptimizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.SelfPreferencePair;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.cognitive.verification.EvidenceGrade;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningConsistencyReceipt;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.memory.MemoryConfiguration;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHyperEdge;
import tech.qiantong.qknow.hermes.memory.bitemporal.ColdStandbyTier;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;
import tech.qiantong.qknow.hermes.memory.bitemporal.SleepTimeSpectralDistiller;
import tech.qiantong.qknow.hermes.memory.bitemporal.SpectralDistillationReceipt;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanSandboxExecutor;
import tech.qiantong.qknow.mcp.router.McpHierarchicalVoronoiRouter;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 H-NSCDS 控制面真闭环与可编程正交消融实验引擎确定性契约测试。
 */
class Phase6ControlPlaneClosedLoopContractTest {

    @Test
    void memoryConfigurationUsesTextEmbeddingV4And1024Dimensions() throws Exception {
        Path memCfgPath = Path.of("..").resolve(
                "qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/MemoryConfiguration.java"
        ).normalize();
        if (!Files.exists(memCfgPath)) {
            memCfgPath = Path.of(
                    "backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/MemoryConfiguration.java"
            );
        }
        String src = Files.readString(memCfgPath);
        assertTrue(src.contains("\"text-embedding-v4\""), "MemoryConfiguration 必须锁定千问 text-embedding-v4");
        assertFalse(src.contains("text-embedding-v3"), "MemoryConfiguration 不得残留 text-embedding-v3");
        assertTrue(src.contains(".dimensions(1024)"), "Embedding 维度必须锁定 1024 维单位超球面");
        assertNotNull(new MemoryConfiguration());
    }

    @Test
    void unitSphereEmbeddingResolverHonorsQwenV4AndOfflineFallback() {
        EmbeddingModel mockQwen = Mockito.mock(EmbeddingModel.class);
        float[] unnormalized = new float[1024];
        unnormalized[0] = 3.0f;
        unnormalized[1] = 4.0f;
        Mockito.when(mockQwen.embed("超球面测试查询")).thenReturn(unnormalized);

        UnitSphereEmbeddingResolver onlineResolver = new UnitSphereEmbeddingResolver(mockQwen, 1000L);
        var res = onlineResolver.resolveWithProvenance("超球面测试查询");
        assertEquals(UnitSphereEmbeddingResolver.ResolutionSource.QWEN_EMBEDDING_V4, res.source());
        assertEquals(1024, res.unitVector1024().length);
        assertEquals(0.6f, res.unitVector1024()[0], 1e-4f);
        assertEquals(0.8f, res.unitVector1024()[1], 1e-4f);

        // 二次请求命中请求级缓存，不重复调用远端模型
        onlineResolver.resolveWithProvenance("超球面测试查询");
        Mockito.verify(mockQwen, Mockito.times(1)).embed("超球面测试查询");

        // 无 EmbeddingModel 或超时异常时安全降级至离线 LSH S^1023 单位向量
        EmbeddingModel failingModel = Mockito.mock(EmbeddingModel.class);
        Mockito.when(failingModel.embed(Mockito.anyString())).thenThrow(new RuntimeException("offline"));
        UnitSphereEmbeddingResolver offlineResolver = new UnitSphereEmbeddingResolver(failingModel, 500L);
        var fallbackRes = offlineResolver.resolveWithProvenance("离线测试");
        assertEquals(UnitSphereEmbeddingResolver.ResolutionSource.OFFLINE_LSH_FALLBACK, fallbackRes.source());
        double normSq = 0.0;
        for (float v : fallbackRes.unitVector1024()) {
            normSq += (double) v * v;
        }
        assertEquals(1.0, Math.sqrt(normSq), 1e-4, "降级输出必须位于 S^1023 单位球面");
    }

    @Test
    void reasoningEffortInjectingChatModelBypassesToolCallingOptionsTrap() {
        DeepSeekCompatibleChatModel dsModel = new DeepSeekCompatibleChatModel(
                "https://api.deepseek.com", "sk-test-dummy", "deepseek-flash", 0.3, 2048, null
        );
        ToolCallback dummyTool = new ToolCallback() {
            private final ToolDefinition def = ToolDefinition.builder()
                    .name("knowledgeQuery")
                    .description("知识库检索")
                    .inputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")
                    .build();

            @Override
            public ToolDefinition getToolDefinition() {
                return def;
            }

            @Override
            public String call(String toolInput) {
                return "ok";
            }
        };

        ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(List.of(dummyTool))
                .topP(0.75)
                .temperature(0.2)
                .build();
        Prompt prompt = new Prompt(List.of(new UserMessage("推演跨跳因果链")), toolOptions);

        // 验证在 ReasoningEffortInjectingChatModel 作用域内部调用 buildRequestBody 同时保留 tools 与注入 thinking/reasoning_effort
        final Map<String, Object>[] capturedBody = new Map[1];
        ChatModel inspectorDelegate = new ChatModel() {
            @Override
            public ChatResponse call(Prompt p) {
                capturedBody[0] = dsModel.buildRequestBody(p, false);
                AssistantMessage am = AssistantMessage.builder()
                        .content("推演结论")
                        .properties(Map.of("reasoning_content", "依据工具事实推演步骤1->2"))
                        .build();
                return new ChatResponse(List.of(new Generation(am)));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt p) {
                return Flux.just(call(p));
            }
        };

        List<String> capturedReasoning = new ArrayList<>();
        ReasoningEffortInjectingChatModel wrapper = new ReasoningEffortInjectingChatModel(
                inspectorDelegate, DeepSeekReasoningEffort.HIGH, capturedReasoning::add
        );
        wrapper.call(prompt);

        assertNotNull(capturedBody[0]);
        assertTrue(capturedBody[0].containsKey("tools"), "ToolCallingChatOptions 中的 tools 必须完整保留");
        assertEquals(Map.of("type", "enabled"), capturedBody[0].get("thinking"), "thinking 必须启用");
        assertEquals("high", capturedBody[0].get("reasoning_effort"), "reasoning_effort 必须精确透传");
        assertFalse(capturedBody[0].containsKey("temperature"), "DeepSeek 思考模式禁用 temperature");
        assertTrue(((Double) capturedBody[0].get("top_p")) >= 0.95, "DeepSeek 思考模式 top_p 必须钳制 >= 0.95");
        assertEquals(List.of("依据工具事实推演步骤1->2"), capturedReasoning, "阻塞路径必须提取 reasoning_content");
    }

    @Test
    void budgetVector6DConservesQuotaAndDowngradesDeterministically() {
        BudgetVector6D tightBudget = new BudgetVector6D(2048L, 1500L, 4, 15_000L, 0.02D, 2);
        var outcome = tightBudget.enforceStaticBudget(
                SupernetTopologyOperator.SWARM_PARALLEL,
                DeepSeekReasoningEffort.HIGH,
                1200L,
                3
        );
        assertTrue(outcome.budgetDowngraded(), "超配额算子必须触发确定性降档");
        assertNotEquals(SupernetTopologyOperator.SWARM_PARALLEL, outcome.resolvedOperator());
        assertNotEquals(DeepSeekReasoningEffort.HIGH, outcome.resolvedEffort());
    }

    @Test
    void variationalUtilityReplacesHardcodedConstantsAndUpdatesSupernet() {
        VariationalUtilityEvaluator evaluator = new VariationalUtilityEvaluator();
        BudgetVector6D budget = BudgetVector6D.defaults();

        VariationalUtilityReceipt highQuality = evaluator.evaluate(
                "req-high", true, 0.96, 1.0, 1000L, 800L, 200L, 500L, budget
        );
        VariationalUtilityReceipt lowQuality = evaluator.evaluate(
                "req-low", false, 0.40, 0.5, 8000L, 0L, 4000L, 25_000L, budget
        );

        assertTrue(highQuality.verifyMac("hermes-utility-default-secret"), "效用凭单 HMAC 常量时间验签必须通过");
        assertTrue(highQuality.realizedUtility() > lowQuality.realizedUtility() + 0.4,
                "真实变分效用必须显著区分高收益缓存命中与失败高延迟场景");
        assertNotEquals(0.92, highQuality.realizedUtility(), 1e-6);
        assertNotEquals(0.35, lowQuality.realizedUtility(), 1e-6);

        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("secret", 77L);
        float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("多跳因果链推理", 1024);
        var beforeReceipt = router.routeAndAdapt("req-before", 0, qVec, null, null, 0.5);
        router.updatePosterior(SupernetTopologyOperator.PLAN_SOLVE, DeepSeekReasoningEffort.MEDIUM, qVec, highQuality.realizedUtility());
        var statsAfter = router.getTelemetrySnapshot();
        assertTrue(statsAfter.totalRoutingDecisions() >= 1);
        assertNotNull(beforeReceipt);
    }

    @Test
    void biTemporalHippocampalGraphPgJdbcPersistenceAndWarmRecovery() throws Exception {
        // 使用轻量 JVM SQL JDBC 存根验证真实 SQL 写入与重启全量热恢复及 MAC 校验闭环
        Map<String, Object[]> storedVertices = new LinkedHashMap<>();
        Map<String, Object[]> storedEdges = new LinkedHashMap<>();
        DataSource fakePgDataSource = createInMemoryHippocampalJdbcDataSource(storedVertices, storedEdges);

        BiTemporalHippocampalGraphEngine writerEngine = new BiTemporalHippocampalGraphEngine(fakePgDataSource, "pg-hippo-key");
        float[] v1 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("实体A", 1024);
        float[] v2 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("实体B", 1024);
        writerEngine.upsertVertex(new HippocampalVertex("v1", "实体A内容", HippocampalVertexTier.PHRASE_ENTITY, v1));
        writerEngine.upsertVertex(new HippocampalVertex("v2", "实体B段落", HippocampalVertexTier.PASSAGE_EPISODE, v2));
        BiTemporalHyperEdge edge = writerEngine.ingestFactEdge(
                "v1", "v2", "RELATES_TO", true, v1, 1000L, 1000L, "doc-1"
        );
        assertFalse(storedVertices.isEmpty(), "顶点必须双写落入 JDBC 表");
        assertFalse(storedEdges.isEmpty(), "双时态超边必须双写落入 JDBC 表");

        // 模拟进程重启后新建引擎从 PG 表热恢复
        BiTemporalHippocampalGraphEngine recoveryEngine = new BiTemporalHippocampalGraphEngine(fakePgDataSource, "pg-hippo-key");
        assertEquals(2, recoveryEngine.getVerticesSnapshot().size(), "重启必须完整热恢复顶点");
        BiTemporalHyperEdge recoveredEdge = recoveryEngine.getLedgerEdgesSnapshot().get(edge.edgeId());
        assertNotNull(recoveredEdge, "重启必须完整热恢复双时态超边");
        assertTrue(recoveredEdge.verifyMac("pg-hippo-key"), "热恢复边 MAC 验签必须完整通过");
    }

    @Test
    void stepEvidenceDriftTrackerTriggersReplanAndEarlyExit() {
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("drift-key", 1024L);
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        StepEvidenceDriftTracker tracker = new StepEvidenceDriftTracker(router, resolver);
        HnscdsAblationConfig cfg = HnscdsAblationConfig.g5FullHnscds(42L);

        StepDriftTransitionReceipt step1 = tracker.observeStep(
                "req-drift", "查找供应链断裂原因", "knowledgeQuery",
                "第一跳观察：供应商 Alpha 正常履约且库存充足。", 0.65, cfg
        );
        assertFalse(step1.earlyExitTriggered());

        // 注入语义剧烈偏移的新证据（平方弦漂移必然 > 0.35）触发非平稳重规划
        StepDriftTransitionReceipt step2 = tracker.observeStep(
                "req-drift", "查找供应链断裂原因", "httpRequest",
                "第二跳突变警告：银行冻结资产导致港口清算系统彻底停摆与违约！", 0.78, cfg
        );
        assertTrue(step2.chordDriftSquared() > 0.35, "跨主题证据跳跃必须触发平方弦漂移 > 0.35");
        assertTrue(step2.replanTriggered(), "超阈值漂移必须触发重规划");

        // 高置信且同一证据延续，触发 EARLY_EXIT 早退
        StepDriftTransitionReceipt step3 = tracker.observeStep(
                "req-drift", "查找供应链断裂原因", "httpRequest",
                "第二跳突变警告：银行冻结资产导致港口清算系统彻底停摆与违约！", 0.97, cfg
        );
        assertTrue(step3.earlyExitTriggered(), "置信度 >= 0.95 且无漂移必须触发 EARLY_EXIT");
        assertEquals(SupernetTopologyOperator.EARLY_EXIT, step3.nextOperator());
    }

    @Test
    void sleepTimeSpectralDistillerReversibleColdStandbyAndRhoEvolution() {
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("sleep-key");
        long now = System.currentTimeMillis();
        // 构建带环稠密连通图（每个节点度>=2）以允许安全谱蒸馏并验证冷备可逆恢复
        String[] nodeIds = {"n1", "n2", "n3", "n4"};
        for (String id : nodeIds) {
            float[] vec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("节点内容-" + id, 1024);
            hippo.upsertVertex(new HippocampalVertex(id, "语义片段 " + id, HippocampalVertexTier.PASSAGE_EPISODE, vec));
        }
        List<BiTemporalHyperEdge> edges = new ArrayList<>();
        edges.add(hippo.ingestFactEdge("n1", "n2", "REL", false,
                LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("e12", 1024), now, now, "d1"));
        edges.add(hippo.ingestFactEdge("n2", "n3", "REL", false,
                LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("e23", 1024), now, now, "d1"));
        edges.add(hippo.ingestFactEdge("n3", "n4", "REL", false,
                LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("e34", 1024), now, now, "d1"));
        edges.add(hippo.ingestFactEdge("n4", "n1", "REL", false,
                LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("e41", 1024), now, now, "d1"));
        edges.add(hippo.ingestFactEdge("n1", "n3", "WEAK_LINK", false,
                LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("噪音弱边", 1024), now, now, "d2"));

        SleepTimeSpectralDistiller distiller = new SleepTimeSpectralDistiller(hippo);
        SpectralDistillationReceipt distReceipt = distiller.distill(0.25, now + 100L);
        assertEquals(4, distReceipt.evaluatedVertexCount());
        assertEquals(5, distReceipt.evaluatedEdgeCount());
        assertTrue(distReceipt.prunedToColdStandbyCount() >= 1, "稠密环图谱蒸馏应可逆归档冗余弱边");

        String archivedEdgeId = distReceipt.archivedEdgeIds().get(0);
        assertEquals(ColdStandbyTier.WARM_QUARANTINE,
                hippo.getLedgerEdgesSnapshot().get(archivedEdgeId).storageTier());
        assertTrue(hippo.restoreEdgeFromColdStandby(archivedEdgeId, now + 200L), "冷备隔离边必须支持 100% 反事实恢复");
        assertEquals(ColdStandbyTier.ACTIVE_HOT,
                hippo.getLedgerEdgesSnapshot().get(archivedEdgeId).storageTier());

        // RHO 回顾性自我偏好演进验证（S^1023 原型更新 + Error-Book 写入）
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("rho-key", 42L);
        float[] beforeProto = router.getPrototypeSnapshot(SupernetTopologyOperator.PLAN_SOLVE, DeepSeekReasoningEffort.HIGH);
        RhoSelfPreferenceOptimizer rho = new RhoSelfPreferenceOptimizer(router, new UnitSphereEmbeddingResolver(), hippo);
        RhoEvolutionReceipt rhoReceipt = rho.evolveFromPreferencePairs(List.of(
                new SelfPreferencePair("r1", "跨文档多跳违约链审计", SupernetTopologyOperator.PLAN_SOLVE,
                        DeepSeekReasoningEffort.HIGH, 0.94, "忽略时间截面直接返回过期事实", 0.22)
        ), 0.25);

        assertEquals(1, rhoReceipt.updatedPrototypeArmsCount());
        assertEquals(1, rhoReceipt.recordedErrorBookCount());
        float[] afterProto = router.getPrototypeSnapshot(SupernetTopologyOperator.PLAN_SOLVE, DeepSeekReasoningEffort.HIGH);
        double cosineShift = 0.0;
        double normSq = 0.0;
        for (int i = 0; i < 1024; i++) {
            cosineShift += (double) beforeProto[i] * afterProto[i];
            normSq += (double) afterProto[i] * afterProto[i];
        }
        assertEquals(1.0, Math.sqrt(normSq), 1e-4, "RHO 更新后原型仍须位于 S^1023 单位球面");
        assertTrue(cosineShift < 0.999999, "RHO 切空间步进必须真实演化原型方向");
        assertTrue(hippo.getVerticesSnapshot().values().stream()
                .anyMatch(v -> v.tier() == HippocampalVertexTier.ERROR_BOOK), "RHO 失败反思必须写入 Error-Book");
    }

    @Test
    void ariadneConsisGuardBidirectionalAuditAndCausalDecouplingBlock() {
        // 1. 只降级不升级格验证
        assertEquals(EvidenceGrade.UNVERIFIED,
                EvidenceGrade.meet(EvidenceGrade.DOUBLE_ATTESTED, EvidenceGrade.UNVERIFIED));
        assertTrue(EvidenceGrade.satisfiesMonotonicNonInflation(
                EvidenceGrade.TOOL_WITNESS, EvidenceGrade.TOOL_WITNESS, EvidenceGrade.DOUBLE_ATTESTED));
        assertFalse(EvidenceGrade.satisfiesMonotonicNonInflation(
                EvidenceGrade.DOUBLE_ATTESTED, EvidenceGrade.TOOL_WITNESS, EvidenceGrade.DOUBLE_ATTESTED));

        AttestedToolWitness witness = AttestedToolWitness.createSigned(
                "req-w", "knowledgeQuery", "{\"query\":\"合同生效日\"}",
                "合同于2026年5月生效并附带违约条款", EvidenceGrade.TOOL_WITNESS,
                System.currentTimeMillis(), "witness-key"
        );
        assertTrue(witness.verifyMac("witness-key"), "工具见证常量时间 HMAC 验签必须通过");

        ReasoningChainConsistencyAuditor auditor = new ReasoningChainConsistencyAuditor(new UnitSphereEmbeddingResolver());
        ReasoningConsistencyReceipt goodReceipt = auditor.audit(
                "req-good",
                "查询合同生效日期",
                "工具返回显示合同于2026年5月生效并附带违约条款，依据该实证可确认日期为2026年5月。",
                "合同生效日期为2026年5月，并附带违约条款。",
                List.of(witness),
                "合同于2026年5月生效并附带违约条款",
                false,
                HnscdsAblationConfig.g5FullHnscds(42L)
        );
        assertTrue(goodReceipt.verifyMac("hermes-consistency-default-secret"));
        assertTrue(goodReceipt.passed(), "忠实一致回答必须通过双向校验");
        assertEquals(EvidenceGrade.TOOL_WITNESS, goodReceipt.resultingGrade());

        // 2. 高危动作但无思考依据（Reasoning Theater / 因果解耦）必须触发强阻断
        ReasoningConsistencyReceipt blockedReceipt = auditor.audit(
                "req-block",
                "执行不可逆转账",
                "",
                "{\"action\":\"transfer\",\"amount\":999999}",
                List.of(),
                "",
                true,
                HnscdsAblationConfig.g5FullHnscds(42L)
        );
        assertTrue(blockedReceipt.causalDecoupledBlock(), "高危动作缺乏思考支撑必须触发反事实因果解耦阻断");
        assertFalse(blockedReceipt.passed());
    }

    @Test
    void ablationMatrixAndBenchmarkHarnessProducesSignificantParetoFront() throws Exception {
        AcademicBenchmarkEvaluationHarness harness = new AcademicBenchmarkEvaluationHarness();
        List<AcademicBenchmarkTask> tasks = harness.buildStratifiedBenchmarkSuite(10); // 4 个基准共 40 题
        assertEquals(40, tasks.size());

        BenchmarkEvaluationReport.MatrixComparisonSummary summary =
                harness.evaluateAblationMatrix(tasks, 42L, null);
        assertEquals(6, summary.reports().size(), "标准消融矩阵必须输出 G0..G5 六组报告");

        BenchmarkEvaluationReport g0 = summary.reports().stream()
                .filter(r -> "G0_VANILLA_REACT".equals(r.profileId())).findFirst().orElseThrow();
        BenchmarkEvaluationReport g5 = summary.reports().stream()
                .filter(r -> "G5_FULL_HNSCDS".equals(r.profileId())).findFirst().orElseThrow();

        String metricsDebug = summary.reports().stream()
                .map(r -> String.format("%s[pass=%.3f,consis=%.3f,tok=%.1f,lat=%.1f]",
                        r.profileId(), r.passAt1Rate(), r.meanReasoningConsistency(),
                        r.meanUncachedTokens(), r.meanLatencyMs()))
                .collect(java.util.stream.Collectors.joining("; "));
        assertTrue(g5.passAt1Rate() > g0.passAt1Rate(), "G5 全量 H-NSCDS Pass@1 必须显著高于 G0 基线: " + metricsDebug);
        assertEquals(1.0, g5.prefixCachePreservationRate(), 1e-6, "开启 Code-Mode 前缀锁定必须维持 100% 前缀不变性");
        assertTrue(g5.paretoHypervolume4D() > g0.paretoHypervolume4D(), "G5 4 维帕累托超体积必须优于 G0: " + metricsDebug);
        assertTrue(summary.paretoOptimalProfiles().contains("G5_FULL_HNSCDS"),
                "G5 必须位于 Pareto 非支配前沿, front=" + summary.paretoOptimalProfiles() + ", metrics=" + metricsDebug);
        assertTrue(summary.mcnemarPValuesVsBaseline().get("G5_FULL_HNSCDS") < 0.05,
                "G5 vs G0 配对 McNemar 检验必须具有统计学显著性 (p < 0.05)");

        Path tempJsonl = Files.createTempFile("hnscds-traj-", ".jsonl");
        harness.writeTrajectoryJsonl(tempJsonl, g5);
        List<String> lines = Files.readAllLines(tempJsonl);
        assertEquals(40, lines.size());
        assertTrue(lines.get(0).contains("variational_utility"));
        assertTrue(lines.get(0).contains("chord_drift_sq"));
        assertTrue(lines.get(0).contains("prefix_cache_preserved"));
        assertTrue(lines.get(0).contains("reasoning_consistency"));
        Files.deleteIfExists(tempJsonl);
    }

    @Test
    void agentOrchestratorEndToEndSupernetTakeoverAndCodeModeLock() {
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("orch-key", 42L);
        UnitSphereEmbeddingResolver embResolver = new UnitSphereEmbeddingResolver();
        SupernetControlPlaneDispatcher dispatcher = new SupernetControlPlaneDispatcher(router, embResolver);
        VariationalUtilityEvaluator utilityEvaluator = new VariationalUtilityEvaluator();
        StepEvidenceDriftTracker driftTracker = new StepEvidenceDriftTracker(router, embResolver);
        ReasoningChainConsistencyAuditor consistencyAuditor = new ReasoningChainConsistencyAuditor(embResolver);
        CodeModePlanSandboxExecutor codeModeExecutor = new CodeModePlanSandboxExecutor(
                new McpHierarchicalVoronoiRouter("test"), (lang, code) -> null
        );
        HoareToolContractGateway hoareGateway = new HoareToolContractGateway("hoare-key");
        FrontierGatedToolTransactionManager frontierTx = new FrontierGatedToolTransactionManager(null);
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("hippo-key");

        ChatModelFactory mockFactory = Mockito.mock(ChatModelFactory.class);
        ToolCallbackResolver mockToolResolver = Mockito.mock(ToolCallbackResolver.class);
        RetrievalEvaluator mockRetrievalEval = Mockito.mock(RetrievalEvaluator.class);

        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel stubChatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                modelCalls.incrementAndGet();
                AssistantMessage am = AssistantMessage.builder()
                        .content("基于控制面验证结论：2026年已完成对齐。")
                        .properties(Map.of("reasoning_content", "已确认任务查询且与结论一致"))
                        .build();
                return new ChatResponse(List.of(new Generation(am)));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(call(prompt));
            }
        };
        Mockito.when(mockFactory.getChatModel(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(stubChatModel);

        AgentOrchestrator orchestrator = new AgentOrchestrator(mockFactory, mockToolResolver, mockRetrievalEval);
        orchestrator.setHypersphericalSupernetRouter(router);
        orchestrator.setUnitSphereEmbeddingResolver(embResolver);
        orchestrator.setSupernetControlPlaneDispatcher(dispatcher);
        orchestrator.setVariationalUtilityEvaluator(utilityEvaluator);
        orchestrator.setStepEvidenceDriftTracker(driftTracker);
        orchestrator.setReasoningChainConsistencyAuditor(consistencyAuditor);
        orchestrator.setCodeModePlanSandboxExecutor(codeModeExecutor);
        orchestrator.setHoareToolContractGateway(hoareGateway);
        orchestrator.setFrontierGatedToolTransactionManager(frontierTx);
        orchestrator.setBiTemporalHippocampalGraphEngine(hippo);
        orchestrator.setActiveAblationConfig(HnscdsAblationConfig.g5FullHnscds(42L));

        ChatRequest req = ChatRequest.newBuilder()
                .setRequestId("req-e2e-phase6")
                .setQuestion("今日状态简单直答")
                .setModelConfig(ModelConfig.newBuilder().setModelName("deepseek-flash").build())
                .build();

        List<ChatEvent> events = orchestrator.chat(req).collectList().block();
        assertNotNull(events);
        assertTrue(modelCalls.get() >= 1, "控制面真接管下必须成功驱动模型推理");
        assertNotNull(orchestrator.getLatestControlDirective("req-e2e-phase6"), "必须签发控制面指令");
        assertNotNull(orchestrator.getLatestVariationalUtilityReceipt("req-e2e-phase6"), "finalizeRequest 必须回写变分效用凭单");
        assertNotNull(orchestrator.getLatestConsistencyReceipt("req-e2e-phase6"), "finalizeRequest 必须签发一致性审计凭单");
        assertTrue(orchestrator.getLatestVariationalUtilityReceipt("req-e2e-phase6").verifyMac("hermes-utility-default-secret"));
    }

    /**
     * 轻量纯 Java Proxy JDBC 数据源，用于在无外部数据库进程的单测中完整验证 PG 双写与启动热恢复协议。
     */
    private static DataSource createInMemoryHippocampalJdbcDataSource(
            Map<String, Object[]> storedVertices,
            Map<String, Object[]> storedEdges
    ) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return createMockConnection(storedVertices, storedEdges);
                    }
                    return null;
                }
        );
    }

    private static Connection createMockConnection(
            Map<String, Object[]> storedVertices,
            Map<String, Object[]> storedEdges
    ) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "createStatement":
                            return Proxy.newProxyInstance(
                                    Statement.class.getClassLoader(),
                                    new Class[]{Statement.class},
                                    (sProxy, sMethod, sArgs) -> "execute".equals(sMethod.getName()) ? Boolean.TRUE : null
                            );
                        case "prepareStatement":
                            return createMockPreparedStatement(String.valueOf(args[0]), storedVertices, storedEdges);
                        case "close":
                            return null;
                        default:
                            return null;
                    }
                }
        );
    }

    private static PreparedStatement createMockPreparedStatement(
            String sql,
            Map<String, Object[]> storedVertices,
            Map<String, Object[]> storedEdges
    ) {
        Map<Integer, Object> params = new LinkedHashMap<>();
        String normalizedSql = sql.trim().toUpperCase(java.util.Locale.ROOT);
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer idx) {
                        params.put(idx, "setNull".equals(name) ? null : args[1]);
                        return null;
                    }
                    if ("executeUpdate".equals(name)) {
                        if (normalizedSql.contains("INTO HERMES_HIPPOCAMPAL_VERTEX")) {
                            storedVertices.put(String.valueOf(params.get(1)), new Object[]{
                                    params.get(1), params.get(2), params.get(3), params.get(4)
                            });
                        } else if (normalizedSql.contains("INTO HERMES_HIPPOCAMPAL_EDGE")) {
                            Object[] row = new Object[14];
                            for (int i = 0; i < 14; i++) {
                                row[i] = params.get(i + 1);
                            }
                            storedEdges.put(String.valueOf(params.get(1)), row);
                        }
                        return 1;
                    }
                    if ("executeQuery".equals(name)) {
                        if (normalizedSql.contains("FROM HERMES_HIPPOCAMPAL_VERTEX")) {
                            return createListResultSet(
                                    List.of("node_id", "tier", "content", "unit_embedding_json"),
                                    new ArrayList<>(storedVertices.values())
                            );
                        }
                        if (normalizedSql.contains("FROM HERMES_HIPPOCAMPAL_EDGE")) {
                            return createListResultSet(
                                    List.of("edge_id", "source_node_id", "target_node_id", "relation_predicate",
                                            "functional_exclusive", "unit_embedding_json", "valid_from_ms",
                                            "valid_to_ms", "tx_from_ms", "tx_to_ms", "source_reference_count",
                                            "cold_standby_tier", "source_docs_json", "hmac_signature"),
                                    new ArrayList<>(storedEdges.values())
                            );
                        }
                    }
                    return null;
                }
        );
    }

    private static ResultSet createListResultSet(List<String> columns, List<Object[]> rows) {
        final int[] cursor = {-1};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("next".equals(name)) {
                        cursor[0]++;
                        return cursor[0] < rows.size();
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    if (args != null && args.length == 1 && args[0] instanceof String col) {
                        int colIdx = columns.indexOf(col);
                        Object val = colIdx >= 0 ? rows.get(cursor[0])[colIdx] : null;
                        return switch (name) {
                            case "getString" -> val != null ? String.valueOf(val) : null;
                            case "getLong" -> val instanceof Number n ? n.longValue() : 0L;
                            case "getInt" -> val instanceof Number n ? n.intValue() : 0;
                            case "getBoolean" -> val instanceof Boolean b && b;
                            case "getObject" -> val;
                            default -> val;
                        };
                    }
                    return null;
                }
        );
    }
}
