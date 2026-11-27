package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.CognitiveStepPhase;
import tech.qiantong.qknow.hermes.cognitive.control.EpistemicDampedComputeReceipt;
import tech.qiantong.qknow.hermes.cognitive.control.EpistemicDampingSignal;
import tech.qiantong.qknow.hermes.cognitive.control.ReasoningEffortInjectingChatModel;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.control.StepEvidenceDriftTracker;
import tech.qiantong.qknow.hermes.cognitive.control.StepManifoldDifficultySignal;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11.1 (ED-SCR) 认知阻尼步级控算与动态思考预算帽契约单测。
 */
class Phase11_1EpistemicDampingComputeContractTest {

    @Test
    void lowInnovationAndHighBudgetPressureBreakMediumSelfLockAndShrinkCap() {
        StepComputePolicy policy = new StepComputePolicy();
        policy.calibrateFromDefaultMultiHopPrior();

        float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("4-hop bridge entity release date", 1024);
        float[] e1 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("passage about film release", 1024);
        float[] e2 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("passage about soundtrack composer", 1024);

        // 模拟此前易自锁为 MEDIUMx10 的第 6 步纯只读检索：机械被标记为 VERIFY_SYNTHESIZE + prior=MEDIUM，
        // 但连续低测地新息 (Delta I = 0.015, streak = 3) 且已消费思考预算 75%
        StepManifoldDifficultySignal lockedSignal = new StepManifoldDifficultySignal(
                CognitiveStepPhase.VERIFY_SYNTHESIZE,
                List.of(e1, e2),
                0.10,
                0.12,
                4,
                0,
                true,
                0.08,
                DeepSeekReasoningEffort.MEDIUM,
                false
        );

        EpistemicDampingSignal dampingSignal = new EpistemicDampingSignal(
                lockedSignal,
                0.015,
                3,
                4500L,
                6000L,
                true
        );

        EpistemicDampedComputeReceipt receipt = policy.decideStepEffortV6Damped(
                "req-4hop-lock", 6, true, qVec, dampingSignal, List.of(e1, e2)
        );

        assertTrue(receipt.dampingFactorGamma() < 0.70, "连续低新息与高预算占比必须产生显著阻尼因子 gamma < 0.70");
        assertTrue(receipt.dampedDifficultyScore() < receipt.rawDifficultyScore(), "阻尼后难度必须严格低于原始难度");
        assertEquals(DeepSeekReasoningEffort.LOW, receipt.baseReceipt().chosenEffort(),
                "纯只读低新息空转步必须打破 MEDIUMx10 自锁并降档为 LOW");
        assertTrue(receipt.recommendedMaxCompletionTokens() < 1024,
                "推荐动态输出预算帽必须明显收紧至 1024 以下，杜绝单步 4096 过度思考");
        assertTrue(receipt.downshiftedByDamping());
        assertTrue(receipt.dampingRationale().contains("pureRetrievalPhaseCorrected"));
    }

    @Test
    void hardErrorOrSpecRejectResetsGammaToOneForFullRepairCapacity() {
        StepComputePolicy policy = new StepComputePolicy();
        policy.calibrateFromDefaultMultiHopPrior();

        float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("table sum check error", 1024);
        float[] e1 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("codeact stderr zero division", 1024);

        StepManifoldDifficultySignal errorSignal = new StepManifoldDifficultySignal(
                CognitiveStepPhase.COUNTERFACTUAL_REPAIR,
                List.of(e1),
                0.45,
                0.68,
                2,
                2,
                false,
                0.20,
                DeepSeekReasoningEffort.HIGH,
                false
        );
        EpistemicDampingSignal dampingSignal = new EpistemicDampingSignal(
                errorSignal, 0.02, 2, 4800L, 6000L, false
        );

        EpistemicDampedComputeReceipt receipt = policy.decideStepEffortV6Damped(
                "req-repair", 4, true, qVec, dampingSignal, List.of(e1)
        );

        assertEquals(1.0, receipt.dampingFactorGamma(), 1e-9, "存在硬错误驳回时必须重置 gamma=1.0 保留完整纠偏算力");
        assertEquals(DeepSeekReasoningEffort.HIGH, receipt.baseReceipt().chosenEffort());
        assertFalse(receipt.downshiftedByDamping());
    }

    @Test
    void directiveSupplierAndTrackerPropagateDynamicCapToDeepSeekRequest() {
        AtomicReference<DeepSeekCompatibleChatModel.ReasoningEffortOverride> captured = new AtomicReference<>();
        ChatModel spyDelegate = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                DeepSeekCompatibleChatModel model = new DeepSeekCompatibleChatModel(
                        "https://dashscope.aliyuncs.com/compatible-mode", "sk-test", "deepseek-v4.1-flash", 0.2, 4096);
                MapHolder.lastBody = model.buildRequestBody(prompt, false);
                return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return null;
            }
        };

        StepComputePolicy policy = new StepComputePolicy();
        policy.calibrateFromDefaultMultiHopPrior();
        StepEvidenceDriftTracker tracker = new StepEvidenceDriftTracker(
                new HypersphericalSupernetRouter(), new UnitSphereEmbeddingResolver());
        HnscdsAblationConfig g19 = HnscdsAblationConfig.g19V6EpistemicDamping(42L);

        StepManifoldDifficultySignal stepSig = new StepManifoldDifficultySignal(
                CognitiveStepPhase.VERIFY_SYNTHESIZE, List.of(), 0.12, 0.10, 4, 0,
                false, 0.25, DeepSeekReasoningEffort.MEDIUM, false
        );
        EpistemicDampingSignal dampingSig = new EpistemicDampingSignal(
                stepSig, 0.01, 3, 4600L, 6000L, true
        );
        var rec = tracker.observeStepWithEpistemicDamping(
                "req-track", "question", "search_hippocampal_graph", "repeated passage content",
                dampingSig, policy, g19
        );

        ReasoningEffortInjectingChatModel wrapper = ReasoningEffortInjectingChatModel.withDirectiveSupplier(
                spyDelegate,
                () -> new ReasoningEffortInjectingChatModel.StepEffortDirective(
                        tracker.getLatestRecommendedEffort("req-track").orElse(DeepSeekReasoningEffort.LOW),
                        tracker.getLatestRecommendedMaxTokens("req-track").orElse(null)
                ),
                null
        );

        wrapper.call(new Prompt("next hop"));
        assertEquals(rec.recommendedMaxCompletionTokens(), MapHolder.lastBody.get("max_completion_tokens"));
        assertEquals("low", MapHolder.lastBody.get("reasoning_effort"));
        assertNull(captured.get());
    }

    @Test
    void earlyMultiHopBridgeWindowPreservesMediumEffortUntilStepFour() {
        StepComputePolicy policy = new StepComputePolicy();
        policy.calibrateFromDefaultMultiHopPrior();

        float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("4-hop county seat bridge", 1024);
        float[] e1 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("county borders Outagamie", 1024);
        float[] e2 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("film origin NBC series", 1024);

        StepManifoldDifficultySignal earlyBridgeSignal = new StepManifoldDifficultySignal(
                CognitiveStepPhase.CODEACT_PROGRAM,
                List.of(e1, e2),
                0.20,
                0.15,
                3,
                0,
                true,
                0.08,
                DeepSeekReasoningEffort.MEDIUM,
                false
        );

        EpistemicDampedComputeReceipt earlyRec = policy.decideStepEffortV6Damped(
                "req-early-hop", 2, true, qVec,
                new EpistemicDampingSignal(earlyBridgeSignal, 0.25, 0, 600L, 6000L, true),
                List.of(e1, e2)
        );
        assertEquals(1.0, earlyRec.dampingFactorGamma(), 1e-9,
                "前 3 步早期多跳保护窗内应维持 gamma=1.0 保留 MEDIUM 桥接推演能力");
        assertEquals(DeepSeekReasoningEffort.MEDIUM, earlyRec.baseReceipt().chosenEffort());
        assertTrue(earlyRec.dampingRationale().contains("earlyHopGuardGamma1.0"));
    }

    private static final class MapHolder {
        static java.util.Map<String, Object> lastBody;
    }
}
