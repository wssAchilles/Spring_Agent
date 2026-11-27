package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.BudgetVector6D;
import tech.qiantong.qknow.hermes.cognitive.control.HypersphericalEpistemicStoppingGovernor;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalFailureReflector;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalReflexiveTreeSearchGovernor;
import tech.qiantong.qknow.hermes.cognitive.treesearch.NeuroSymbolicBranchEvaluator;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.config.HermesPhdEnginesAutoConfiguration;
import tech.qiantong.qknow.hermes.controller.PhdStudioObservabilityController;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalEvolutionRetriever;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8.4 H-NSCDS v3 全三箭控制面协同、14 维消融矩阵与双端装配契约测试。
 */
class Phase8_4HnscdsV3ControlPlaneSynergyContractTest {

    @Test
    void ablationConfigPreservesLegacyConstructorsAndDefinesV3Profiles() {
        // 8 参数 Phase 6 兼容构造器
        HnscdsAblationConfig legacy8 = new HnscdsAblationConfig(
                "LEGACY_8", true, true, true, true, true, true, 42L
        );
        assertFalse(legacy8.hystereticContextFoldingEnabled());
        assertFalse(legacy8.temporalCausalEvolutionEnabled());
        assertFalse(legacy8.verifiedCodeActEnabled());
        assertFalse(legacy8.causalReflexiveTreeSearchEnabled());

        // 11 参数 Phase 7 兼容构造器
        HnscdsAblationConfig legacy11 = new HnscdsAblationConfig(
                "LEGACY_11", true, true, true, true, true, true, 42L,
                true, true, true
        );
        assertTrue(legacy11.hystereticContextFoldingEnabled());
        assertFalse(legacy11.temporalCausalEvolutionEnabled());
        assertFalse(legacy11.verifiedCodeActEnabled());
        assertFalse(legacy11.causalReflexiveTreeSearchEnabled());

        // G7..G10 正交递进开关校验
        HnscdsAblationConfig g7 = HnscdsAblationConfig.fromProfileId("G7", 42L);
        assertTrue(g7.temporalCausalEvolutionEnabled());
        assertFalse(g7.verifiedCodeActEnabled());
        assertFalse(g7.causalReflexiveTreeSearchEnabled());

        HnscdsAblationConfig g8 = HnscdsAblationConfig.fromProfileId("G8_V3_CODEACT", 42L);
        assertTrue(g8.temporalCausalEvolutionEnabled());
        assertTrue(g8.verifiedCodeActEnabled());
        assertFalse(g8.causalReflexiveTreeSearchEnabled());

        HnscdsAblationConfig g9 = HnscdsAblationConfig.fromProfileId("G9_V3_CAUSAL_TREE", 42L);
        assertTrue(g9.temporalCausalEvolutionEnabled());
        assertFalse(g9.verifiedCodeActEnabled());
        assertTrue(g9.causalReflexiveTreeSearchEnabled());

        HnscdsAblationConfig g10 = HnscdsAblationConfig.fromProfileId("G10_HNSCDS_V3_ULTIMATE", 42L);
        assertTrue(g10.supernetEnabled());
        assertTrue(g10.hystereticContextFoldingEnabled());
        assertTrue(g10.temporalCausalEvolutionEnabled());
        assertTrue(g10.verifiedCodeActEnabled());
        assertTrue(g10.causalReflexiveTreeSearchEnabled());

        List<HnscdsAblationConfig> matrix = HnscdsAblationConfig.v3ExtendedMatrix(42L);
        assertEquals(8, matrix.size());
        assertEquals("G10_HNSCDS_V3_ULTIMATE", matrix.get(matrix.size() - 1).profileId());
    }

    @Test
    void supernetRouterRegistersV3OperatorsAndBudgetVector6DDowngradesDeterministically() {
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("test-v3", 42L);

        // CODE_ACT_SANDBOX 与 CAUSAL_TREE_SEARCH 已纳入 S^1023 原型库，且 CAUSAL_TREE_SEARCH+NONE 被裁剪
        assertNotNull(router.getPrototypeSnapshot(SupernetTopologyOperator.CODE_ACT_SANDBOX, DeepSeekReasoningEffort.MEDIUM));
        assertNotNull(router.getPrototypeSnapshot(SupernetTopologyOperator.CAUSAL_TREE_SEARCH, DeepSeekReasoningEffort.HIGH));
        assertNull(router.getPrototypeSnapshot(SupernetTopologyOperator.CAUSAL_TREE_SEARCH, DeepSeekReasoningEffort.NONE));

        // 预算深度限制为 3 时，CAUSAL_TREE_SEARCH (depth=4) 确定性降档为 PLAN_SOLVE
        BudgetVector6D depth3Budget = new BudgetVector6D(16_384L, 12_288L, 8, 30_000L, 0.50D, 3);
        var out3 = depth3Budget.enforceStaticBudget(
                SupernetTopologyOperator.CAUSAL_TREE_SEARCH, DeepSeekReasoningEffort.HIGH, 1024L, 2
        );
        assertTrue(out3.budgetDowngraded());
        assertEquals(SupernetTopologyOperator.PLAN_SOLVE, out3.resolvedOperator());

        // 预算深度限制为 2 时，CAUSAL_TREE_SEARCH 确定性降档为 CODE_ACT_SANDBOX
        BudgetVector6D depth2Budget = new BudgetVector6D(16_384L, 12_288L, 8, 30_000L, 0.50D, 2);
        var out2 = depth2Budget.enforceStaticBudget(
                SupernetTopologyOperator.CAUSAL_TREE_SEARCH, DeepSeekReasoningEffort.MEDIUM, 1024L, 2
        );
        assertTrue(out2.budgetDowngraded());
        assertEquals(SupernetTopologyOperator.CODE_ACT_SANDBOX, out2.resolvedOperator());

        // 预算深度限制为 1 时，CODE_ACT_SANDBOX 确定性降档为 DIRECT_FAST
        BudgetVector6D depth1Budget = new BudgetVector6D(16_384L, 12_288L, 8, 30_000L, 0.50D, 1);
        var out1 = depth1Budget.enforceStaticBudget(
                SupernetTopologyOperator.CODE_ACT_SANDBOX, DeepSeekReasoningEffort.LOW, 512L, 1
        );
        assertTrue(out1.budgetDowngraded());
        assertEquals(SupernetTopologyOperator.DIRECT_FAST, out1.resolvedOperator());
    }

    @Test
    void dualConfigurationClassesDeclareMirroredPhase8Beans() throws Exception {
        Set<String> hermesAutoBeanMethods = extractBeanMethodNames(HermesPhdEnginesAutoConfiguration.class);
        Path serverConfigSource = resolveWorkspacePath(
                "backend/qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java"
        );
        if (!Files.exists(serverConfigSource)) {
            serverConfigSource = resolveWorkspacePath(
                    "qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java"
            );
        }
        assertTrue(Files.exists(serverConfigSource), "HermesConfiguration.java 必须存在");
        Set<String> serverBeanMethods = extractBeanMethodNamesFromJavaSource(Files.readString(serverConfigSource));

        List<String> requiredPhase8Beans = List.of(
                "temporalAnchorQueryParser",
                "biTemporalCausalEvolutionRetriever",
                "verifiedCodeActSandboxRuntime",
                "codeActProgrammaticBridge",
                "neuroSymbolicBranchEvaluator",
                "causalFailureReflector",
                "causalReflexiveTreeSearchGovernor"
        );

        for (String beanName : requiredPhase8Beans) {
            assertTrue(serverBeanMethods.contains(beanName),
                    "HermesConfiguration 缺少 Phase 8 Bean: " + beanName);
            assertTrue(hermesAutoBeanMethods.contains(beanName),
                    "HermesPhdEnginesAutoConfiguration 缺少 Phase 8 Bean: " + beanName);
        }
    }

    @Test
    void orchestratorAndObservabilityControllerExposePhase8TelemetryAndPreserveCompatibility() {
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);
        orchestrator.setSupernetControlTakeoverEnabled(true);
        assertNotNull(orchestrator.getCausalReflexiveTreeSearchGovernor());

        // 5 参旧构造器兼容调用
        PgStepJournalStateCheckpointer stepJournal = new PgStepJournalStateCheckpointer();
        FrontierGatedToolTransactionManager txManager = new FrontierGatedToolTransactionManager(null);
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine();
        HoareToolContractGateway hoare = new HoareToolContractGateway();
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter();

        PhdStudioObservabilityController controller = new PhdStudioObservabilityController(
                stepJournal, txManager, hippo, hoare, router
        );

        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        BiTemporalCausalEvolutionRetriever temporalRetriever = new BiTemporalCausalEvolutionRetriever(
                hippo, resolver, new TemporalAnchorQueryParser()
        );
        VerifiedCodeActSandboxRuntime codeActRuntime = new VerifiedCodeActSandboxRuntime();
        CausalReflexiveTreeSearchGovernor treeGovernor = new CausalReflexiveTreeSearchGovernor(
                stepJournal,
                new NeuroSymbolicBranchEvaluator(new ReasoningChainConsistencyAuditor(resolver), resolver),
                new CausalFailureReflector(hippo, resolver),
                new HypersphericalEpistemicStoppingGovernor(resolver),
                resolver
        );

        // 执行一次真实动作以确保遥测计数反映真实增量
        temporalRetriever.retrieve("q-tel-1", "latest flagship status", System.currentTimeMillis(), 2);
        codeActRuntime.executeMath("m-tel-1", "2 + 3 * 4");
        codeActRuntime.close();
        treeGovernor.executeBoundedSearch(
                "exec-tel-1", "main", 0, "Telemetry check", "ZONE0",
                BudgetVector6D.defaults(), HnscdsAblationConfig.g6HnscdsV2Full(42L),
                ctx -> new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                        "ok", "ok", "ok", "ok", null, List.of(), Map.of(), List.of(), 64L, false, "ZONE0"
                )
        );

        controller.setBiTemporalCausalEvolutionRetriever(temporalRetriever);
        controller.setVerifiedCodeActSandboxRuntime(codeActRuntime);
        controller.setCausalReflexiveTreeSearchGovernor(treeGovernor);

        Map<String, Object> telemetry = controller.getTelemetry().getData();
        assertNotNull(telemetry);
        assertTrue(telemetry.containsKey("temporalCausal"));
        assertTrue(telemetry.containsKey("verifiedCodeAct"));
        assertTrue(telemetry.containsKey("causalTreeSearch"));

        @SuppressWarnings("unchecked")
        Map<String, Object> tc = (Map<String, Object>) telemetry.get("temporalCausal");
        assertEquals(1L, ((Number) tc.get("totalRetrievals")).longValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> vc = (Map<String, Object>) telemetry.get("verifiedCodeAct");
        assertEquals(1L, ((Number) vc.get("totalExecutions")).longValue());
        assertEquals(1L, ((Number) vc.get("successfulExecutions")).longValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> ct = (Map<String, Object>) telemetry.get("causalTreeSearch");
        assertEquals(1L, ((Number) ct.get("totalSearches")).longValue());
    }

    private static Set<String> extractBeanMethodNames(Class<?> configClass) {
        Set<String> names = new HashSet<>();
        for (Method m : configClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Bean.class)) {
                names.add(m.getName());
            }
        }
        return names;
    }

    private static Set<String> extractBeanMethodNamesFromJavaSource(String source) {
        Set<String> names = new HashSet<>();
        Pattern p = Pattern.compile("@Bean(?:\\s+@[^\\s(]+(?:\\([^)]*\\))?)*\\s+public\\s+[\\w.<>\\[\\], ?]+\\s+(\\w+)\\s*\\(");
        Matcher m = p.matcher(source);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static Path resolveWorkspacePath(String relative) {
        Path p = Path.of(relative);
        if (Files.exists(p)) {
            return p;
        }
        return Path.of("..").resolve(relative).normalize();
    }
}
