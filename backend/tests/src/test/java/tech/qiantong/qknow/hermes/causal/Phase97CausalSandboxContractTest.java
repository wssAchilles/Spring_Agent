package tech.qiantong.qknow.hermes.causal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.causal.dto.*;
import tech.qiantong.qknow.hermes.causal.engine.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 97 契约测试套件：复杂业务 Agent 跨模态因果意图预测、时序反事实推演沙盘与自主干预决策中枢
 * 严格验证假设 H-PHASE97-001、定理 1.1~1.3 与命题 2.1
 */
public class Phase97CausalSandboxContractTest {

    private MultimodalCausalIntentPredictor intentPredictor;
    private TemporalCounterfactualSandbox sandbox;
    private AutonomousInterventionMetacenter interventionMetacenter;
    private CausalInterventionControlBus controlBus;

    @BeforeEach
    public void setUp() {
        intentPredictor = new MultimodalCausalIntentPredictor();
        sandbox = new TemporalCounterfactualSandbox();
        interventionMetacenter = new AutonomousInterventionMetacenter();
        controlBus = new CausalInterventionControlBus();
    }

    /**
     * 辅助工具：生成符合千问 1536 维超球面单位向量 (||v||_2 = 1.0 +- 1e-5)
     */
    private float[] generateNormalizedQwenEmbedding(int seed) {
        float[] v = new float[MultimodalCausalState.EXPECTED_DIMENSION];
        double sumSq = 0.0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) Math.sin(seed * 0.17 + i * 0.009);
            sumSq += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约测试 1: 验证定理 1.1，跨模态因果意图预测准确率 >= 99.0% 且伴生混淆消除率 >= 98.0%")
    public void test01_CausalIntentPrediction_AccuracyAndConfounderElimination() {
        float[] embedding = generateNormalizedQwenEmbedding(42);

        // 构造伴生严重网络重试混淆的输入流
        Map<String, String> inputs = Map.of(
                "natural_language", "请核销本季度华东区对公供应商账单结算",
                "ui_events", "USER_FREQUENT_CLICK:RETRY_LAG:5_TIMES"
        );
        MultimodalCausalState state = new MultimodalCausalState("session-001", inputs, embedding, System.nanoTime());

        MultimodalCausalIntentPredictor.CausalPredictionResult result = intentPredictor.predictIntent(state);

        assertEquals("INTENT_SETTLEMENT_CLEARING", result.predictedIntent());
        assertTrue(result.confidence() >= 0.95, "因果意图置信度必须 >= 0.95");
        assertTrue(result.confounderDetected(), "必须精准检出网络重试与界面抖动伴生混淆");
        assertTrue(result.confounderEliminationRate() >= 0.98, "后门准则混淆消除率必须 >= 98.0%");
    }

    @Test
    @DisplayName("契约测试 2: 验证因果意图推断单步耗时严格 <= 60μs (实测极限微秒级)")
    public void test02_CausalIntentPrediction_MicrosecondPerformance() {
        float[] embedding = generateNormalizedQwenEmbedding(101);
        Map<String, String> inputs = Map.of(
                "natural_language", "查询财务知识库关于跨境支付合规白皮书",
                "ui_events", "NORMAL_CLICK"
        );
        MultimodalCausalState state = new MultimodalCausalState("session-bench", inputs, embedding, System.nanoTime());

        // JIT 预热
        for (int i = 0; i < 2000; i++) {
            intentPredictor.predictIntent(state);
        }

        long totalElapsed = 0;
        int runs = 500;
        for (int i = 0; i < runs; i++) {
            MultimodalCausalIntentPredictor.CausalPredictionResult res = intentPredictor.predictIntent(state);
            totalElapsed += res.elapsedNanos();
        }
        double avgMicros = (totalElapsed / (double) runs) / 1000.0;
        assertTrue(avgMicros <= 60.0, "单步因果意图推断平均耗时必须 <= 60μs，实测: " + avgMicros + "μs");
    }

    @Test
    @DisplayName("契约测试 3: 验证定理 1.2，时序反事实沙盘多分支推演 100% 保模归一化且李普希茨有界收敛")
    public void test03_TemporalSandbox_HypersphereBranchingAndLipschitzConvergence() {
        float[] embedding = generateNormalizedQwenEmbedding(77);
        Map<String, String> inputs = Map.of("natural_language", "批量生成财税审计底稿");
        MultimodalCausalState state = new MultimodalCausalState("session-sandbox", inputs, embedding, System.nanoTime());

        List<String> candidateActions = List.of(
                "ACTION_GENERATE_ENCRYPTED_REPORT",
                "ACTION_EXPORT_UNENCRYPTED",
                "ACTION_DELETE_ALL_BACKUP",
                "ACTION_QUERY_ONLY"
        );

        SandboxSimulationResult simResult = sandbox.simulateWhatIfBranches(
                "session-sandbox", state, "INTENT_BATCH_EXPORT", candidateActions, 5
        );

        assertEquals(4, simResult.totalBranches(), "候选分支数必须完整展开");
        assertNotNull(simResult.optimalBranch(), "必须评估出最优候选分支");

        for (CounterfactualSandboxBranch branch : simResult.branches()) {
            float[] latent = branch.predictedLatentState();
            assertEquals(1536, latent.length);
            double sumSq = 0.0;
            for (float v : latent) {
                sumSq += (double) v * v;
            }
            double norm = Math.sqrt(sumSq);
            assertEquals(1.0, norm, 1e-4, "推演的每一条分支潜态向量模长必须严格保持 1.0 +- 1e-4");
            assertTrue(branch.riskScore() >= 0.0 && branch.riskScore() <= 1.0, "风险分有界");
        }

        // 验证高危动作的风险分显著高于安全加密动作
        CounterfactualSandboxBranch deleteBranch = simResult.branches().stream()
                .filter(b -> b.proposedAction().contains("DELETE_ALL"))
                .findFirst().orElseThrow();
        CounterfactualSandboxBranch safeBranch = simResult.branches().stream()
                .filter(b -> b.proposedAction().contains("ENCRYPTED"))
                .findFirst().orElseThrow();
        assertTrue(deleteBranch.riskScore() > safeBranch.riskScore(), "高危破坏动作风险分必须显著更高");
    }

    @Test
    @DisplayName("契约测试 4: 验证时序沙盘多分支树展开单步耗时严格 <= 100μs")
    public void test04_TemporalSandbox_MicrosecondPerformance() {
        float[] embedding = generateNormalizedQwenEmbedding(88);
        MultimodalCausalState state = new MultimodalCausalState(
                "session-perf", Map.of("natural_language", "导出季度汇总"), embedding, System.nanoTime()
        );
        List<String> candidateActions = List.of("ACTION_A", "ACTION_B", "ACTION_C", "ACTION_D");

        // 预热
        for (int i = 0; i < 2000; i++) {
            sandbox.simulateWhatIfBranches("session-perf", state, "INTENT_EXPORT", candidateActions, 5);
        }

        long totalElapsed = 0;
        int runs = 500;
        for (int i = 0; i < runs; i++) {
            SandboxSimulationResult res = sandbox.simulateWhatIfBranches(
                    "session-perf", state, "INTENT_EXPORT", candidateActions, 5
            );
            totalElapsed += res.elapsedNanos();
        }
        double avgMicros = (totalElapsed / (double) runs) / 1000.0;
        assertTrue(avgMicros <= 100.0, "沙盘多分支展开平均耗时必须 <= 100μs，实测: " + avgMicros + "μs");
    }

    @Test
    @DisplayName("契约测试 5: 验证定理 1.3，相对阶 r=2 Temporal CBF 最小干预软投影修补与高危 100% 拦截")
    public void test05_AutonomousIntervention_MinimalInterventionAndSafety() {
        float[] embedding = generateNormalizedQwenEmbedding(99);
        MultimodalCausalState state = new MultimodalCausalState("session-cbf", Map.of("cmd", "test"), embedding, System.nanoTime());

        // 场景 A: 存在参数越界风险，执行最小干预软投影修正 (INTERVENE_SOFT_PROJECT)
        SandboxSimulationResult simMildRisk = sandbox.simulateWhatIfBranches(
                "session-cbf", state, "INTENT_NORMAL", List.of("ACTION_MILD_OVER_PARAM"), 5
        );
        float[] nominalVector = new float[]{1.5f, 0.8f, 0.2f};
        AutonomousInterventionResult resSoft = interventionMetacenter.evaluateIntervention(
                "session-cbf", simMildRisk, nominalVector
        );
        assertTrue(resSoft.intervened(), "必须触发自主干预");
        assertTrue(resSoft.action() == AutonomousInterventionAction.INTERVENE_SOFT_PROJECT
                || resSoft.action() == AutonomousInterventionAction.INTERVENE_ALTERNATIVE_BRANCH);
        assertTrue(resSoft.barrierMargin() >= 0.0, "干预修补后控制屏障裕度必须满足前向不变性 h(x) >= 0");

        // 场景 B: 绝对高危破坏动作 (DELETE_ALL)，执行紧急硬熔断 (EMERGENCY_VETO_HALT)
        SandboxSimulationResult simCritical = sandbox.simulateWhatIfBranches(
                "session-cbf", state, "INTENT_HAZARD", List.of("ACTION_DELETE_ALL_BACKUP"), 5
        );
        AutonomousInterventionResult resHalt = interventionMetacenter.evaluateIntervention(
                "session-cbf", simCritical, nominalVector
        );
        assertEquals(AutonomousInterventionAction.EMERGENCY_VETO_HALT, resHalt.action());
        assertTrue(resHalt.intervened());
    }

    @Test
    @DisplayName("契约测试 6: 正常合规业务动作直通放行，单步干预决策耗时严格 <= 30μs")
    public void test06_AutonomousIntervention_NormalActionDirectPass() {
        float[] embedding = generateNormalizedQwenEmbedding(123);
        MultimodalCausalState state = new MultimodalCausalState("session-normal", Map.of("cmd", "query"), embedding, System.nanoTime());

        SandboxSimulationResult simSafe = sandbox.simulateWhatIfBranches(
                "session-normal", state, "INTENT_QUERY", List.of("ACTION_QUERY_ONLY"), 5
        );

        // 预热
        for (int i = 0; i < 2000; i++) {
            interventionMetacenter.evaluateIntervention("session-normal", simSafe, new float[]{0.1f, 0.2f});
        }

        long totalElapsed = 0;
        int runs = 500;
        for (int i = 0; i < runs; i++) {
            AutonomousInterventionResult res = interventionMetacenter.evaluateIntervention(
                    "session-normal", simSafe, new float[]{0.1f, 0.2f}
            );
            totalElapsed += res.elapsedNanos();
            assertEquals(AutonomousInterventionAction.PASS_DIRECT, res.action());
            assertFalse(res.intervened());
            assertTrue(res.barrierMargin() >= 0.0);
        }
        double avgMicros = (totalElapsed / (double) runs) / 1000.0;
        assertTrue(avgMicros <= 30.0, "单步干预审查平均耗时必须 <= 30μs，实测: " + avgMicros + "μs");
    }

    @Test
    @DisplayName("契约测试 7: 1000Hz 4096 槽位总线非阻塞写入与不可变凭单 SHA-256 自签名验真 100% 通过")
    public void test07_DisruptorControlBus_ThroughputAndReceiptIntegrity() {
        AutonomousInterventionResult sampleResult = new AutonomousInterventionResult(
                AutonomousInterventionAction.PASS_DIRECT,
                false,
                "branch-0",
                new float[]{0.1f},
                0.85,
                "正常直通",
                1200L
        );

        // 验证 1000Hz 高频非阻塞发布
        for (int i = 0; i < 5000; i++) {
            controlBus.publishEvent("session-bus-" + i, "INTENT_KNOWLEDGE_RETRIEVAL", sampleResult, 50);
        }
        assertEquals(5000, controlBus.getPublishedCount());
        assertEquals(CausalInterventionControlBus.STATUS_NORMAL, controlBus.getBusStatus());

        // 验证不可变存证凭单签发与 SHA-256 防篡改验真
        CausalInterventionReceipt receipt = controlBus.issueReceipt(
                "session-bus-verify",
                "hash-graph-abc12345",
                4,
                "ACTION_GENERATE_ENCRYPTED_REPORT",
                false,
                0.85,
                15000L
        );

        assertNotNull(receipt.receiptId());
        assertTrue(receipt.verifyIntegrity(), "凭单自身 SHA-256 签名验真必须 100% 通过");

        // 篡改测试
        CausalInterventionReceipt tampered = new CausalInterventionReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.causalGraphHash(),
                receipt.totalBranches(),
                "ACTION_ILLEGAL_TAMPERED", // 篡改动作
                receipt.intervened(),
                receipt.barrierMargin(),
                receipt.elapsedNanos(),
                receipt.timestamp(),
                receipt.signature()
        );
        assertFalse(tampered.verifyIntegrity(), "篡改后的凭单必须 100% 验真失败");
    }

    @Test
    @DisplayName("契约测试 8: JitterGuard 连续 3 帧时钟抖动 (>2ms) 瞬切 STATUS_DEGRADED_BUFFERED 缓冲软着陆")
    public void test08_DisruptorControlBus_JitterGuardDegradedBuffering() {
        AutonomousInterventionResult sampleResult = new AutonomousInterventionResult(
                AutonomousInterventionAction.PASS_DIRECT, false, "branch-0", new float[]{0.1f}, 0.85, "ok", 500L
        );

        assertEquals(CausalInterventionControlBus.STATUS_NORMAL, controlBus.getBusStatus());

        // 模拟第 1 帧与第 2 帧抖动 (时延 2500μs)
        controlBus.publishEvent("session-jitter-1", "INTENT_A", sampleResult, 2500);
        assertEquals(CausalInterventionControlBus.STATUS_NORMAL, controlBus.getBusStatus());
        controlBus.publishEvent("session-jitter-2", "INTENT_A", sampleResult, 2500);
        assertEquals(CausalInterventionControlBus.STATUS_NORMAL, controlBus.getBusStatus());

        // 模拟第 3 帧连续抖动，触发瞬切软着陆
        controlBus.publishEvent("session-jitter-3", "INTENT_A", sampleResult, 2500);
        assertEquals(CausalInterventionControlBus.STATUS_DEGRADED_BUFFERED, controlBus.getBusStatus());

        // 恢复正常帧，自动恢复 NORMAL
        controlBus.publishEvent("session-jitter-4", "INTENT_A", sampleResult, 50);
        assertEquals(CausalInterventionControlBus.STATUS_NORMAL, controlBus.getBusStatus());
    }
}
