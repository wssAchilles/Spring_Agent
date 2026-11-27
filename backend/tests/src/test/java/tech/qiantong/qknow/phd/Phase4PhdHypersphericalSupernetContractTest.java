package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetRoutingReceipt;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;
import tech.qiantong.qknow.hermes.controller.PhdStudioObservabilityController;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 博士论文代际升级 · 阶段 4（论文第 3 章认知超网路由与第 6 章工作台观测控制面）核心契约测试套件
 * 覆盖：
 * 1. 定理 3.1(1)-(2)：S^1023 vMF 注意力 + Normal-Inverse-Gamma 经验贝叶斯 Thompson Sampling + 证据质心平方弦漂移重规划；
 * 2. DeepSeek 思考模式官方参数约束合规（thinkingEnabled / reasoning_effort / temperature=null / top_p 钳制）；
 * 3. AgentOrchestrator 超网路由协同集成；
 * 4. PhdStudioObservabilityController 真实遥测与 Step Journal 时间旅行分叉控制面契约。
 */
class Phase4PhdHypersphericalSupernetContractTest {

    @Test
    @DisplayName("4.1 定理 3.1：非平稳分布漂移下 vMF Thompson Sampling 动态遗憾界与平方弦漂移按需重规划")
    void test01_Theorem3_1_NonStationaryDriftDynamicRegretAndVmfThompsonSampling() {
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("supernet-secret-31", 42L);

        // 两类原型 1024 维查询与证据向量：Phase A（复杂多跳规划）vs Phase B（简单直答）
        float[] complexQuery = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "多跳跨部门供应链违约因果推演与资金流审计对比", 1024);
        float[] simpleQuery = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "查询今日天气与城市温度", 1024);
        float[] evidencePhaseA = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "检测到跨表四跳外键依赖与不可逆写操作前缀", 1024);
        float[] evidencePhaseB = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "单一只读气象接口命中直答缓存", 1024);

        // 先构造与静态采样、随机基线对比的 T = 80 非平稳流（t=41 发生分布跳变）
        double routerTotalRegret = 0.0;
        double staticOneShotRegret = 0.0;
        double uniformRandomRegret = 0.0;

        Random baselineRng = new Random(2026L);
        SupernetTopologyOperator[] allOps = new SupernetTopologyOperator[]{
                SupernetTopologyOperator.DIRECT_FAST,
                SupernetTopologyOperator.SINGLE_REACT,
                SupernetTopologyOperator.PLAN_SOLVE,
                SupernetTopologyOperator.ADVERSARIAL_DEBATE,
                SupernetTopologyOperator.SWARM_PARALLEL
        };
        DeepSeekReasoningEffort[] allEfforts = DeepSeekReasoningEffort.values();

        // 静态一次性采样：在第 0 步后永久锁定在首阶段偏好策略（在 Phase B 切换时彻底失效）
        SupernetRoutingReceipt lockedStatic = null;
        boolean driftTriggeredAt40 = false;
        boolean earlyExitObserved = false;

        float[] prevEvidence = null;
        for (int t = 0; t < 80; t++) {
            boolean isPhaseA = (t < 40);
            float[] queryVec = isPhaseA ? complexQuery : simpleQuery;
            float[] currEvidence = isPhaseA ? evidencePhaseA : evidencePhaseB;
            double confidence = (t == 75) ? 0.99 : 0.72;

            SupernetRoutingReceipt receipt = router.routeAndAdapt(
                    "req-nonstat-" + t, t, queryVec, prevEvidence, currEvidence, confidence
            );
            assertTrue(receipt.verifyMac("supernet-secret-31"), "超网路由凭单 HMAC 签名必须通过常量时间验证");

            if (t == 40) {
                // Phase A -> Phase B 跳变第一步，证据平方弦漂移必须超限并触发重规划
                assertTrue(receipt.chordDriftSquared() > HypersphericalSupernetRouter.DEFAULT_DRIFT_REPLAN_THRESHOLD,
                        "第 41 步切换证据分布时平方弦漂移必须超过阈值，实际=" + receipt.chordDriftSquared());
                assertTrue(receipt.replanTriggered(), "证据平方弦漂移超限时必须触发按需重规划");
                driftTriggeredAt40 = true;
            }
            if (receipt.earlyExitTriggered() || receipt.topologyOperator() == SupernetTopologyOperator.EARLY_EXIT) {
                earlyExitObserved = true;
            }
            if (lockedStatic == null) {
                // 模拟一次性静态固定采样：锁定为一个多算子重型静态选择
                lockedStatic = new SupernetRoutingReceipt(
                        "static", 0, SupernetTopologyOperator.PLAN_SOLVE, DeepSeekReasoningEffort.HIGH,
                        0.0, false, false, 0.9, 0.2, 0L, ""
                );
            }

            double oracleUtility = 0.95;
            double routerUtility = evaluateNonStationaryUtility(
                    isPhaseA, receipt.topologyOperator(), receipt.reasoningEffort());
            routerTotalRegret += Math.max(0.0, oracleUtility - routerUtility);
            router.updatePosterior(receipt.topologyOperator(), receipt.reasoningEffort(), queryVec, routerUtility);

            double staticUtility = evaluateNonStationaryUtility(
                    isPhaseA, lockedStatic.topologyOperator(), lockedStatic.reasoningEffort());
            staticOneShotRegret += Math.max(0.0, oracleUtility - staticUtility);

            SupernetTopologyOperator randOp = allOps[baselineRng.nextInt(allOps.length)];
            DeepSeekReasoningEffort randEff = allEfforts[baselineRng.nextInt(allEfforts.length)];
            double randUtility = evaluateNonStationaryUtility(isPhaseA, randOp, randEff);
            uniformRandomRegret += Math.max(0.0, oracleUtility - randUtility);

            prevEvidence = currEvidence;
        }

        assertTrue(driftTriggeredAt40, "分布突变步必须观测到弦漂移重规划事件");
        assertTrue(earlyExitObserved, "当认知证据置信度饱和 (>= 0.95) 时必须触发 EARLY_EXIT 早退算子");
        assertTrue(routerTotalRegret < staticOneShotRegret * 0.55,
                String.format("定理 3.1 动态遗憾未大幅优于静态一次性采样: router=%.3f vs static=%.3f",
                        routerTotalRegret, staticOneShotRegret));
        assertTrue(routerTotalRegret < uniformRandomRegret * 0.55,
                String.format("定理 3.1 动态遗憾未大幅优于均匀随机基线: router=%.3f vs random=%.3f",
                        routerTotalRegret, uniformRandomRegret));
    }

    private static double evaluateNonStationaryUtility(
            boolean isPhaseA,
            SupernetTopologyOperator op,
            DeepSeekReasoningEffort effort
    ) {
        if (isPhaseA) {
            if ((op == SupernetTopologyOperator.PLAN_SOLVE || op == SupernetTopologyOperator.ADVERSARIAL_DEBATE)
                    && (effort == DeepSeekReasoningEffort.HIGH || effort == DeepSeekReasoningEffort.MEDIUM)) {
                return 0.93;
            }
            if (op == SupernetTopologyOperator.SINGLE_REACT) {
                return 0.55;
            }
            return 0.25;
        } else {
            if ((op == SupernetTopologyOperator.DIRECT_FAST || op == SupernetTopologyOperator.EARLY_EXIT)
                    && (effort == DeepSeekReasoningEffort.NONE || effort == DeepSeekReasoningEffort.LOW)) {
                return 0.94;
            }
            if (op == SupernetTopologyOperator.PLAN_SOLVE && effort == DeepSeekReasoningEffort.HIGH) {
                return 0.30; // 简单查询过度思考与多跳代价惩罚
            }
            return 0.48;
        }
    }

    @Test
    @DisplayName("4.2 DeepSeek 思考模式参数合规验证：NONE/LOW/MEDIUM/HIGH 与 topP/temperature 官方铁律")
    void test02_DeepSeekThinkingOptionsComplianceAndZeroPrefixMutation() {
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("opt-secret", 7L);

        SupernetRoutingReceipt noneReceipt = new SupernetRoutingReceipt(
                "r-none", 0, SupernetTopologyOperator.DIRECT_FAST, DeepSeekReasoningEffort.NONE,
                0.0, false, false, 0.9, 0.1, System.currentTimeMillis(), "dummy"
        );
        DeepSeekChatOptions fastOpts = router.buildDeepSeekChatOptions(noneReceipt, "deepseek-flash");
        assertEquals("deepseek-flash", fastOpts.getModel());
        assertEquals(Boolean.FALSE, fastOpts.getThinkingEnabled());
        assertNull(fastOpts.getReasoningEffort(), "effort=NONE 时不应下发思考力度字段");
        assertNotNull(fastOpts.getTemperature());

        for (DeepSeekReasoningEffort eff : List.of(
                DeepSeekReasoningEffort.LOW, DeepSeekReasoningEffort.MEDIUM, DeepSeekReasoningEffort.HIGH)) {
            SupernetRoutingReceipt thinkReceipt = new SupernetRoutingReceipt(
                    "r-" + eff.name(), 1, SupernetTopologyOperator.PLAN_SOLVE, eff,
                    0.42, true, false, 0.91, 0.3, System.currentTimeMillis(), "dummy"
            );
            DeepSeekChatOptions thinkOpts = router.buildDeepSeekChatOptions(thinkReceipt, "deepseek-flash");
            assertEquals(Boolean.TRUE, thinkOpts.getThinkingEnabled(), "开启思考努力度时必须启用 thinking");
            assertEquals(eff.wireValue(), thinkOpts.getReasoningEffort());
            assertNull(thinkOpts.getTemperature(), "DeepSeek 官方思考模式严禁传递 temperature");
            assertNotNull(thinkOpts.getTopP());
            assertTrue(thinkOpts.getTopP() >= 0.95 && thinkOpts.getTopP() <= 1.0,
                    "思考模式下 topP 必须严格钳制于 [0.95, 1.0]");
        }
    }

    @Test
    @DisplayName("4.3 AgentOrchestrator 协同集成：直接通过超球面超网调度编排模式与 DeepSeek 选项")
    void test03_AgentOrchestratorSupernetRoutingIntegration() {
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("orch-secret", 99L);
        orchestrator.setHypersphericalSupernetRouter(router);

        float[] q = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("并发辩论审计知识图谱跨跳矛盾", 1024);
        SupernetRoutingReceipt receipt = orchestrator.routeWithHypersphericalSupernet(
                "req-orch-1", 0, q, null, q, 0.68
        );

        assertNotNull(receipt);
        assertSame(router, orchestrator.getHypersphericalSupernetRouter());
        assertNotNull(receipt.topologyOperator().toOrchestrationMode());
        assertTrue(router.getTelemetrySnapshot().totalRoutingDecisions() >= 1);
    }

    @Test
    @DisplayName("4.4 PhdStudioObservabilityController：Step Journal 时间旅行分叉与全系统真实遥测控制面契约")
    void test04_PhdStudioObservabilityControllerTimeTravelForkAndTelemetryContract() {
        PgStepJournalStateCheckpointer checkpointer = new PgStepJournalStateCheckpointer(null, "ctl-secret", 2);
        FrontierGatedToolTransactionManager txManager = new FrontierGatedToolTransactionManager("tx-secret");
        BiTemporalHippocampalGraphEngine hippoEngine = new BiTemporalHippocampalGraphEngine("hippo-secret");
        HoareToolContractGateway hoareGateway = new HoareToolContractGateway("hoare-secret");
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("ctl-secret", 11L);

        // 写入两组真实 Step Journal 超步日志
        checkpointer.executeOrReplayIdempotentStep(
                "exec-studio-1", "main", 1, "plan_node", "k1", 1L, Map.of("ch", 1L),
                () -> Map.of("query", "原始风险合同", "draftLimit", 100)
        );
        checkpointer.executeOrReplayIdempotentStep(
                "exec-studio-1", "main", 2, "tool_node", "k2", 2L, Map.of("ch", 2L),
                () -> Map.of("riskFlag", "HIGH", "approved", false)
        );

        PhdStudioObservabilityController controller = new PhdStudioObservabilityController(
                checkpointer, txManager, hippoEngine, hoareGateway, router
        );

        CommonResult<Map<String, Object>> telemetryRes = controller.getTelemetry();
        assertEquals(200, telemetryRes.getCode());
        assertNotNull(telemetryRes.getData());
        assertTrue(telemetryRes.getData().containsKey("supernet"));
        assertTrue(telemetryRes.getData().containsKey("frontierTx"));
        assertTrue(telemetryRes.getData().containsKey("hippocampal"));

        CommonResult<List<Map<String, Object>>> timelineRes = controller.getJournalTimeline("exec-studio-1", "main");
        assertEquals(200, timelineRes.getCode());
        assertEquals(2, timelineRes.getData().size());

        CommonResult<Map<String, Object>> forkRes = controller.forkBranch(
                new PhdStudioObservabilityController.TimeTravelForkRequest(
                        "exec-studio-1", "main", 1, Map.of("draftLimit", 50, "auditNote", "human-repaired")
                )
        );
        assertEquals(200, forkRes.getCode());
        String forkedBranchId = String.valueOf(forkRes.getData().get("forkBranchId"));
        assertTrue(forkedBranchId.startsWith("fork_"));

        @SuppressWarnings("unchecked")
        Map<String, Object> reconstructed = (Map<String, Object>) forkRes.getData().get("reconstructedState");
        assertEquals(50, reconstructed.get("draftLimit"));
        assertEquals("human-repaired", reconstructed.get("auditNote"));
        assertEquals("原始风险合同", reconstructed.get("query"));
    }
}
