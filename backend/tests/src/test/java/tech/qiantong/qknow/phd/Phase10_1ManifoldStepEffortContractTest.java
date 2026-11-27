package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.CognitiveStepPhase;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputeRoutingReceipt;
import tech.qiantong.qknow.hermes.cognitive.control.StepEvidenceDriftTracker;
import tech.qiantong.qknow.hermes.cognitive.control.StepManifoldDifficultySignal;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10.1 (MS-SHSCR) 离线契约单测：
 * 验证多候选超球面散度非零性、Bates-Hoeffding 多跳先验校准、MEDIUM 档可达性、
 * 6 步滑动证据窗防坍缩以及 23 维正交消融配置向后兼容性。
 */
class Phase10_1ManifoldStepEffortContractTest {

    @Test
    void multiCandidateManifoldSpreadBreaksZeroCollapseAndReachesMediumOnMultiHop() {
        StepComputePolicy policy = new StepComputePolicy();
        var calRes = policy.calibrateFromDefaultMultiHopPrior();
        assertTrue(calRes.constraintSatisfied());
        assertTrue(Boolean.TRUE.equals(policy.getTelemetryStats().get("conformalCalibrated")));
        double[] th = policy.getCalibratedThresholds();
        assertTrue(th[0] < th[1] && th[1] < th[2], "Conformal 校准阈值必须单调递增");

        float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "Which country was the director of the sequel to Midnight Sonata born in?", 1024);
        float[] cand1 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "Midnight Sonata is a 1998 film directed by Claire Dubois.", 1024);
        float[] cand2 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "The sequel to Midnight Sonata was directed by Marco Bellini in Rome, Italy.", 1024);
        float[] cand3 = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "Claire Dubois was born in Lyon, France and wrote the screenplay.", 1024);

        StepManifoldDifficultySignal signal = new StepManifoldDifficultySignal(
                CognitiveStepPhase.VERIFY_SYNTHESIZE,
                List.of(cand1, cand2, cand3),
                0.28,
                0.30,
                3,
                0,
                true,
                0.08,
                DeepSeekReasoningEffort.MEDIUM,
                false
        );

        StepComputeRoutingReceipt rcpt = policy.decideStepEffortV5(
                "req-ms-1", 2, true, qVec, signal, List.of()
        );
        assertTrue(rcpt.sphericalVariance() > 0.01, "多候选向量在 S^1023 上必须产生非零球面离散度 D_S");
        assertTrue(
                rcpt.chosenEffort() == DeepSeekReasoningEffort.MEDIUM
                        || rcpt.chosenEffort() == DeepSeekReasoningEffort.HIGH,
                "高跳深+分叉歧义必须稳定跃升至 MEDIUM 或 HIGH，绝不坍缩为全 LOW: actual=" + rcpt.chosenEffort()
        );
    }

    @Test
    void slidingEvidenceWindowPreventsSingleVectorCollapseInTracker() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("ms-test-secret", 42L);
        StepEvidenceDriftTracker tracker = new StepEvidenceDriftTracker(router, resolver);
        StepComputePolicy policy = new StepComputePolicy();
        policy.calibrateFromDefaultMultiHopPrior();
        HnscdsAblationConfig cfg = HnscdsAblationConfig.g15V5ManifoldEffort(42L);

        float[] vec1 = resolver.resolveUnit1024("First retrieved document about Alpine Glacier treaty.");
        float[] vec2 = resolver.resolveUnit1024("Second document mentioning totally different Olympic Swimming venue.");

        StepComputeRoutingReceipt r1 = tracker.observeStepWithManifoldSignal(
                "req-win", "Who signed Alpine Glacier treaty?", "search_hippocampal_graph",
                "Alpine Glacier treaty signed in Geneva.",
                new StepManifoldDifficultySignal(
                        CognitiveStepPhase.EXPLORE_RETRIEVE, List.of(vec1), 0.1, 0.15, 1, 0,
                        false, 0.30, DeepSeekReasoningEffort.LOW, false
                ),
                policy, cfg
        );
        StepComputeRoutingReceipt r2 = tracker.observeStepWithManifoldSignal(
                "req-win", "Who signed Alpine Glacier treaty?", "search_hippocampal_graph",
                "Olympic Swimming venue opened in Tokyo.",
                new StepManifoldDifficultySignal(
                        CognitiveStepPhase.CODEACT_PROGRAM, List.of(vec2), 0.48, 0.45, 2, 1,
                        false, 0.12, DeepSeekReasoningEffort.LOW, false
                ),
                policy, cfg
        );
        assertNotNull(r1);
        assertTrue(r2.sphericalVariance() > 0.01, "单步单向量接入滑动证据窗后 D_S 必须 > 0");
        assertTrue(r2.chosenEffort().thinkingEnabled());
        assertEquals(2L, ((Number) policy.getTelemetryStats().get("v5ManifoldDecisions")).longValue());
    }

    @Test
    void isolatedLowDifficultyAllowsNoneWhileLinearThinkingEnforcesFloor() {
        StepComputePolicy policy = new StepComputePolicy();
        policy.calibrateFromDefaultMultiHopPrior();
        float[] sameVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("Direct fact match A.", 1024);

        StepManifoldDifficultySignal isoLow = new StepManifoldDifficultySignal(
                CognitiveStepPhase.EXPLORE_RETRIEVE,
                List.of(sameVec, sameVec),
                0.0, 0.0, 1, 0, false, 0.50,
                DeepSeekReasoningEffort.NONE, true
        );
        StepComputeRoutingReceipt rIso = policy.decideStepEffortV5(
                "req-iso", 0, true, sameVec, isoLow, List.of()
        );
        assertEquals(DeepSeekReasoningEffort.NONE, rIso.chosenEffort(), "隔离上下文超低难度应安全放行 NONE");

        StepManifoldDifficultySignal linearLow = new StepManifoldDifficultySignal(
                CognitiveStepPhase.EXPLORE_RETRIEVE,
                List.of(sameVec, sameVec),
                0.0, 0.0, 1, 0, false, 0.50,
                DeepSeekReasoningEffort.NONE, false
        );
        StepComputeRoutingReceipt rLinear = policy.decideStepEffortV5(
                "req-lin", 1, true, sameVec, linearLow, List.of()
        );
        assertTrue(rLinear.chosenEffort().thinkingEnabled(), "非隔离线性链已启思考时禁止退回 NONE");
    }

    @Test
    void ablationConfigPreservesLegacyConstructorsAndSupportsV5Matrix() {
        HnscdsAblationConfig legacy8 = new HnscdsAblationConfig("G0", false, false, false, false, false, false, 42L);
        assertFalse(legacy8.manifoldSpreadStepEffortEnabled());
        assertFalse(legacy8.parameterizedScopedSkillEvoEnabled());

        HnscdsAblationConfig g18 = HnscdsAblationConfig.fromProfileId("G18_HNSCDS_V5_ULTIMATE", 42L);
        assertTrue(g18.manifoldSpreadStepEffortEnabled());
        assertTrue(g18.monotonicTournamentBcbtEnabled());
        assertTrue(g18.completenessAndPremiseGateEnabled());
        assertTrue(g18.parameterizedScopedSkillEvoEnabled());
        assertTrue(g18.verifiedProgrammaticSkillEvolutionEnabled());
        assertEquals(8, HnscdsAblationConfig.v5ExtendedMatrix(42L).size());
    }
}
