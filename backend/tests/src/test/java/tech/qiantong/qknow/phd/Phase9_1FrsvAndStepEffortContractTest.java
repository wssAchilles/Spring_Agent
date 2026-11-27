package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.CognitiveStepPhase;
import tech.qiantong.qknow.hermes.cognitive.control.ReasoningEffortInjectingChatModel;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputeRoutingReceipt;
import tech.qiantong.qknow.hermes.cognitive.control.StepEvidenceDriftTracker;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CounterfactualBranchPatch;
import tech.qiantong.qknow.hermes.cognitive.treesearch.NeuroSymbolicBranchEvaluator;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.cognitive.verification.EvidenceGrade;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpecCompiler;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.cognitive.verification.RequirementSpecAuditResult;
import tech.qiantong.qknow.hermes.tool.codeact.CodeActExecutionPlan;
import tech.qiantong.qknow.hermes.tool.codeact.SandboxIsolationAttestation;
import tech.qiantong.qknow.hermes.tool.codeact.SecurityLatticeLevel;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.1 契约单测：FRSV 六维形式化需求规格校验 + ZCEA-SCR 步级异构控算 + DeepSeek 跨模式协议防雷。
 */
public class Phase9_1FrsvAndStepEffortContractTest {

    @Test
    void testFrsvCompilesAndPenalizesSpecViolationsRigidly() {
        QuestionRequirementSpecCompiler compiler = new QuestionRequirementSpecCompiler();
        ReasoningChainConsistencyAuditor auditor = new ReasoningChainConsistencyAuditor(new UnitSphereEmbeddingResolver());
        HnscdsAblationConfig g11 = HnscdsAblationConfig.g11V4FrsvShscr(42L);
        List<AttestedToolWitness> witnesses = List.of(
                AttestedToolWitness.createSigned("w-1", "dataset_probe", "raw", "Revenue 2023=100, 2024=125",
                        EvidenceGrade.DOUBLE_ATTESTED, System.currentTimeMillis(), "k")
        );

        // 1. 百分比错报裸数值 + 分母口径缺失 -> 乘性硬门控必须压分 <= 0.45
        String qPercent = "What was the percentage increase of total revenue from 2023 to 2024?";
        QuestionRequirementSpec specPct = compiler.compile(qPercent);
        assertEquals(QuestionRequirementSpec.MetricType.PERCENTAGE_CHANGE, specPct.metricType());
        assertEquals("%", specPct.requiredUnit());

        RequirementSpecAuditResult pctBad = auditor.auditWithSpec(
                "req-pct-bad", qPercent,
                "In 2024 it reached 125 while 2023 was 100, difference is 25",
                "25",
                witnesses, "2023 revenue=100, 2024 revenue=125",
                null, specPct, false, g11
        );
        assertTrue(pctBad.hasHardViolations());
        assertTrue(pctBad.consistencyReceipt().overallConsistencyScore() <= 0.45);
        assertFalse(pctBad.passed());

        // 2. 实体粒度过窄（询问上层网络，却回答具体子频道）-> 硬违约压分
        String qNet = "Which television network broadcasts BBC Radio 4?";
        QuestionRequirementSpec specNet = compiler.compile(qNet);
        assertEquals(QuestionRequirementSpec.EntityGranularity.OVERARCHING_ORG_OR_NETWORK, specNet.entityGranularity());
        RequirementSpecAuditResult netBad = auditor.auditWithSpec(
                "req-net-bad", qNet,
                "BBC Radio 4 is part of the BBC network.",
                "BBC Radio 4",
                witnesses, "BBC Radio 4 is a station owned by BBC network",
                null, specNet, false, g11
        );
        assertTrue(netBad.hasHardViolations());
        assertTrue(netBad.violations().stream().anyMatch(v -> "ENTITY_GRANULARITY_TOO_NARROW".equals(v.code())));
        assertTrue(netBad.consistencyReceipt().overallConsistencyScore() <= 0.45);

        // 3. 差值题直接抄单端点值 -> 硬违约压分
        String qDiff = "How much more did Alice spend in May than April?";
        RequirementSpecAuditResult diffBad = auditor.auditWithSpec(
                "req-diff-bad", qDiff,
                "Alice spent 350 dollars in May and 250 dollars in April.",
                "350 dollars",
                witnesses, "April spending 250, May spending 350",
                null, null, false, g11
        );
        assertTrue(diffBad.hasHardViolations());
        assertTrue(diffBad.violations().stream().anyMatch(v -> "DELTA_RAW_ENDPOINT_COPIED".equals(v.code())));
        assertTrue(diffBad.consistencyReceipt().overallConsistencyScore() <= 0.45);

        // 4. CodeAct 算对但终答抄错数值 -> 硬违约压分；对齐则高分通过
        SandboxIsolationAttestation att = new SandboxIsolationAttestation(
                "POSIX_ULIMIT_SUBPROCESS", SecurityLatticeLevel.SANDBOX_MEM,
                false, false, true, true, "Mac OS X"
        );
        VerifiedCodeActReceipt codeReceipt = VerifiedCodeActReceipt.createSigned(
                "rcpt-1", "plan-1", CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                "python", "deadbeef", 0, "25.00%", "", false, false, 1200L, att,
                System.currentTimeMillis(), "hermes-codeact-sandbox-secret"
        );
        RequirementSpecAuditResult codeMismatch = auditor.auditWithSpec(
                "req-code-bad", qPercent,
                "Calculated total revenue percentage increase via Python as 25.00%",
                "14.20%",
                witnesses, "2023 total revenue=100, 2024 total revenue=125",
                codeReceipt, specPct, false, g11
        );
        assertFalse(codeMismatch.codeActWitnessAligned());
        assertTrue(codeMismatch.consistencyReceipt().overallConsistencyScore() <= 0.45);

        RequirementSpecAuditResult codeGood = auditor.auditWithSpec(
                "req-code-ok", qPercent,
                "The percentage increase of total revenue is (125 - 100) / 100 = 25.00%.",
                "25.00%",
                witnesses, "2023 total revenue=100, 2024 total revenue=125",
                codeReceipt, specPct, false, g11
        );
        assertTrue(codeGood.codeActWitnessAligned());
        assertFalse(codeGood.hasHardViolations());
        assertTrue(codeGood.passed());
        assertTrue(codeGood.consistencyReceipt().overallConsistencyScore() >= 0.65);
    }

    @Test
    void testNeuroSymbolicBranchEvaluatorConsumesFrsvDiagnostic() {
        NeuroSymbolicBranchEvaluator evaluator = new NeuroSymbolicBranchEvaluator();
        float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("total percentage revenue", 1024);
        float[] bVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("revenue diff", 1024);

        var inputG11 = new NeuroSymbolicBranchEvaluator.BranchEvaluationInput(
                "req-eval-g11",
                "What was the percentage increase of total revenue from 2023 to 2024?",
                "2023 total revenue was 100 and 2024 was 125.",
                "25",
                "total revenue 2023=100 2024=125",
                null,
                List.of(),
                qVec, bVec, null, 200L, 4000L, false,
                HnscdsAblationConfig.g11V4FrsvShscr(42L)
        );
        var outcomeG11 = evaluator.evaluate(inputG11);
        assertTrue(outcomeG11.hardFailureDetected());
        assertEquals(CounterfactualBranchPatch.FailureRootCause.CONSISTENCY_DRIFT, outcomeG11.detectedFailureCause());
        assertTrue(outcomeG11.failureDiagnostic().contains("UNIT_PERCENTAGE_MISSING"));
        assertTrue(outcomeG11.compositeValue() <= 0.25);
    }

    @Test
    void testSphericalVarianceIdentityAndDualTrackStepEffortRouting() {
        float[] v1 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("Alice salary in January", 1024);
        float[] v2 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("Alice salary in February", 1024);
        float[] v3 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("Unrelated quantum physics formula", 1024);
        List<float[]> vectors = List.of(v1, v2, v3);

        double rBar = StepComputePolicy.computeMeanResultantLength(vectors);
        double rBarSqFromNorm = rBar * rBar;
        double rBarSqFromChord = StepComputePolicy.computeMeanResultantLengthSquaredByPairwiseChord(vectors);
        assertEquals(rBarSqFromNorm, rBarSqFromChord, 1e-5, "S^1023 球面合向量模长平方必须满足双元弦恒等式");
        double dS = StepComputePolicy.computeSphericalVariance(vectors);
        assertEquals(1.0 - rBar, dS, 1e-6);

        StepComputePolicy policy = new StepComputePolicy();
        // 1. 隔离上下文 + 低难度探索阶段允许分配 NONE / LOW，显著压低思考能耗
        StepComputeRoutingReceipt isoExplore = policy.decideStepEffort(
                "req-zcea", 1, CognitiveStepPhase.EXPLORE_RETRIEVE, true, false,
                v1, List.of(v1), 0.0, 0.0, 0, 1.0
        );
        assertTrue(isoExplore.chosenEffort() == DeepSeekReasoningEffort.NONE
                || isoExplore.chosenEffort() == DeepSeekReasoningEffort.LOW);

        // 2. 线性会话若已激活思考态，必须锁定至少 LOW（禁止回落 NONE 引发历史 tool_calls 协议冲突）
        StepComputeRoutingReceipt linearExplore = policy.decideStepEffort(
                "req-zcea", 2, CognitiveStepPhase.EXPLORE_RETRIEVE, false, true,
                v1, List.of(v1), 0.0, 0.0, 0, 1.0
        );
        assertNotEquals(DeepSeekReasoningEffort.NONE, linearExplore.chosenEffort());
        assertTrue(linearExplore.chosenEffort().thinkingEnabled());

        // 3. 遇到拒签历史或反事实修复必须跃迁至 HIGH
        StepComputeRoutingReceipt repair = policy.decideStepEffort(
                "req-zcea", 3, CognitiveStepPhase.COUNTERFACTUAL_REPAIR, true, true,
                v1, vectors, 0.55, 0.70, 1, 2.5
        );
        assertEquals(DeepSeekReasoningEffort.HIGH, repair.chosenEffort());
    }

    @Test
    void testConformalUcbThresholdCalibrationMonotonicity() {
        List<StepComputePolicy.CalibrationTrace> traces = new ArrayList<>();
        // 构造 80 个样本：低难度 NONE/LOW 全部成功且最省 token；高难度需要 MEDIUM/HIGH
        for (int i = 0; i < 40; i++) {
            traces.add(new StepComputePolicy.CalibrationTrace(
                    0.12 + (i % 5) * 0.02, true,
                    true, true, true, true,
                    0.0, 120.0, 350.0, 900.0
            ));
        }
        for (int i = 0; i < 40; i++) {
            traces.add(new StepComputePolicy.CalibrationTrace(
                    0.82 + (i % 5) * 0.03, true,
                    true, false, false, true,
                    0.0, 140.0, 400.0, 950.0
            ));
        }

        StepComputePolicy strictPolicy = new StepComputePolicy();
        var strictCal = strictPolicy.calibrateConformalThresholds(traces, 0.25, 0.10);
        assertTrue(strictCal.constraintSatisfied());
        assertTrue(strictCal.ucbRiskUpperBound() <= 0.25 + 1e-9);
        assertTrue(strictCal.expectedThinkTokens() < 925.0, "Conformal 最优阈值应显著节省全 HIGH 平均思考开销");
        assertTrue(strictCal.thresholds()[0] >= 0.15, "低难度组无风险时下阈值应放行 NONE");
    }

    @Test
    void testDynamicReasoningEffortSwitchingAcrossCallsAndTrackerSync() {
        DeepSeekCompatibleChatModel dsModel = new DeepSeekCompatibleChatModel(
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "dummy-key",
                "deepseek-v4.1-flash",
                0.2
        );
        AtomicReference<Map<String, Object>> capturedBody = new AtomicReference<>();
        ChatModel spyDelegate = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                capturedBody.set(dsModel.buildRequestBody(prompt, false));
                return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return null;
            }
        };

        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("k", 42L);
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        StepEvidenceDriftTracker tracker = new StepEvidenceDriftTracker(router, resolver);
        StepComputePolicy policy = new StepComputePolicy();
        HnscdsAblationConfig g11 = HnscdsAblationConfig.g11V4FrsvShscr(42L);

        ReasoningEffortInjectingChatModel wrapper = new ReasoningEffortInjectingChatModel(
                spyDelegate,
                () -> tracker.getLatestRecommendedEffort("req-dyn").orElse(DeepSeekReasoningEffort.LOW),
                null
        );

        // 首轮默认 LOW -> 真实穿透至 DeepSeekCompatibleChatModel 请求体
        wrapper.call(new Prompt("step 1"));
        assertEquals("low", capturedBody.get().get("reasoning_effort"));
        assertEquals(Boolean.TRUE, capturedBody.get().get("enable_thinking"));

        // 触发反事实修复高风险步 -> tracker 更新为 HIGH，下一次 call 自动切 HIGH
        StepComputeRoutingReceipt stepReceipt = tracker.observeStepWithPolicy(
                "req-dyn", "complex multi-hop calculation", "codeact", "assertion mismatch",
                List.of(resolver.resolveUnit1024("mismatch")),
                CognitiveStepPhase.COUNTERFACTUAL_REPAIR, 0.30, 0.85, 1, true, policy, g11
        );
        assertEquals(DeepSeekReasoningEffort.HIGH, stepReceipt.chosenEffort());

        wrapper.call(new Prompt("step 2"));
        assertEquals("high", capturedBody.get().get("reasoning_effort"));
        assertEquals(Boolean.TRUE, capturedBody.get().get("enable_thinking"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDeepSeekBuildRequestBodyPadsMissingReasoningContentForToolCallsInThinkingMode() {
        DeepSeekCompatibleChatModel model = new DeepSeekCompatibleChatModel(
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "dummy-key",
                "deepseek-v4.1-flash",
                0.2
        );
        AssistantMessage historyAssistantWithoutReasoning = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("tc_1", "function", "search_kb", "{\"q\":\"x\"}")))
                .build();
        List<Message> msgs = List.of(
                new UserMessage("question"),
                historyAssistantWithoutReasoning
        );

        Map<String, Object> thinkingBody = DeepSeekCompatibleChatModel.runWithEffortOverride(
                new DeepSeekCompatibleChatModel.ReasoningEffortOverride(true, "high"),
                () -> model.buildRequestBody(new Prompt(msgs), false)
        );
        List<Map<String, Object>> payloadMsgs = (List<Map<String, Object>>) thinkingBody.get("messages");
        Map<String, Object> assistantPayload = payloadMsgs.get(1);
        assertTrue(assistantPayload.containsKey("tool_calls"));
        assertTrue(assistantPayload.containsKey("reasoning_content"),
                "思考模式下含 tool_calls 的历史助手消息必须补入 reasoning_content 防 400 拒绝");
        assertEquals("", assistantPayload.get("reasoning_content"));

        // 非思考模式下不强行注入 reasoning_content
        AtomicInteger nonThinkingHasField = new AtomicInteger(0);
        Map<String, Object> nonThinkingBody = DeepSeekCompatibleChatModel.runWithEffortOverride(
                new DeepSeekCompatibleChatModel.ReasoningEffortOverride(false, "none"),
                () -> model.buildRequestBody(new Prompt(msgs), false)
        );
        List<Map<String, Object>> nonThinkingMsgs = (List<Map<String, Object>>) nonThinkingBody.get("messages");
        if (nonThinkingMsgs.get(1).containsKey("reasoning_content")) {
            nonThinkingHasField.incrementAndGet();
        }
        assertEquals(0, nonThinkingHasField.get());
    }
}
