package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.BudgetVector6D;
import tech.qiantong.qknow.hermes.cognitive.control.HypersphericalEpistemicStoppingGovernor;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalFailureReflector;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalReflexiveTreeSearchGovernor;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CounterfactualBranchPatch;
import tech.qiantong.qknow.hermes.cognitive.treesearch.MonotonicBranchAcceptanceArbiter;
import tech.qiantong.qknow.hermes.cognitive.treesearch.NeuroSymbolicBranchEvaluator;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10.2 (MA-BCBT) 离线契约单测：
 * 验证无客观硬缺陷时反 sycophancy 原假设锁定、新增客观见证 + 严格增益门槛仲裁、
 * 以及 CausalReflexiveTreeSearchGovernor 对 preserveAsNullBaseline 分支的防误剪枝保护。
 */
class Phase10_2MonotonicTournamentBcbtContractTest {

    @Test
    void arbiterRejectsSycophanticFlipWithoutNewWitnessEvenWithSlightlyHigherScore() {
        MonotonicBranchAcceptanceArbiter arbiter = new MonotonicBranchAcceptanceArbiter();
        var primary = new NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome(
                0.76, 0.55, 0.85, 0.15, 0.05, false, null, ""
        );
        var candidate = new NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome(
                0.88, 0.55, 0.90, 0.25, 0.05, false, null, ""
        );

        var receipt = arbiter.arbitrate(
                "exec-no-witness", primary, false, false, candidate, false, 0, false, 0.08
        );
        assertFalse(receipt.candidateAccepted(), "无新增客观工具/CodeAct 见证时严禁翻转无硬缺陷初答");
        assertEquals("REJECTED_SYCOPHANTIC_NO_NEW_OBJECTIVE_WITNESS", receipt.decisionRationale());

        // 即使新增了 1 个工具见证但增益不足 margin=0.08，仍锁定初答
        var lowMarginCandidate = new NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome(
                0.80, 0.55, 0.86, 0.18, 0.05, false, null, ""
        );
        var tieReceipt = arbiter.arbitrate(
                "exec-low-margin", primary, false, false, lowMarginCandidate, false, 1, false, 0.08
        );
        assertFalse(tieReceipt.candidateAccepted(), "增益 < margin 时应锁定保留初答");
        assertEquals("REJECTED_INSUFFICIENT_VALUE_MARGIN", tieReceipt.decisionRationale());
    }

    @Test
    void arbiterAcceptsDominantNewWitnessOrObjectiveDefectRepair() {
        MonotonicBranchAcceptanceArbiter arbiter = new MonotonicBranchAcceptanceArbiter();
        var cleanPrimary = new NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome(
                0.70, 0.55, 0.78, 0.10, 0.05, false, null, ""
        );
        var dominantCandidate = new NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome(
                0.84, 1.00, 0.88, 0.20, 0.05, false, null, ""
        );
        var dominantReceipt = arbiter.arbitrate(
                "exec-dom", cleanPrimary, false, false, dominantCandidate, false, 1, true, 0.08
        );
        assertTrue(dominantReceipt.candidateAccepted());
        assertEquals("ACCEPTED_DOMINANT_NEW_WITNESS_WITH_MARGIN", dominantReceipt.decisionRationale());

        // 初答存在 FRSV 违约或缺少必调工具时，无硬缺陷的干净候选直接修复采用
        var defectivePrimary = new NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome(
                0.42, 0.55, 0.35, 0.10, 0.05, true,
                CounterfactualBranchPatch.FailureRootCause.CONSISTENCY_DRIFT, "FRSV unit mismatch"
        );
        var repairReceipt = arbiter.arbitrate(
                "exec-repair", defectivePrimary, true, true, dominantCandidate, false, 1, false, 0.08
        );
        assertTrue(repairReceipt.candidateAccepted());
        assertEquals("REPAIRED_PRIMARY_OBJECTIVE_DEFECT", repairReceipt.decisionRationale());
        assertEquals(2L, ((Number) arbiter.getTelemetryStats().get("acceptedRefinements")).longValue());
        assertEquals(0L, ((Number) arbiter.getTelemetryStats().get("rejectedFlipProtections")).longValue());
    }

    @Test
    void treeSearchGovernorPreservesNullBaselineBranchAndZone0Prefix() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        PgStepJournalStateCheckpointer journal = new PgStepJournalStateCheckpointer();
        ReasoningChainConsistencyAuditor auditor = new ReasoningChainConsistencyAuditor(resolver);
        NeuroSymbolicBranchEvaluator evaluator = new NeuroSymbolicBranchEvaluator(auditor, resolver);
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("bcbt-v5-test");
        CausalFailureReflector reflector = new CausalFailureReflector(hippo, resolver);
        HypersphericalEpistemicStoppingGovernor stopping = new HypersphericalEpistemicStoppingGovernor(resolver, 0.02);

        CausalReflexiveTreeSearchGovernor governor = new CausalReflexiveTreeSearchGovernor(
                journal, evaluator, reflector, stopping, resolver, "bcbt-v5-secret", 2, 2, 2
        );

        String zone0 = "STATIC_SYSTEM_PROMPT_V5_ANCHOR";
        HnscdsAblationConfig cfg = HnscdsAblationConfig.g16V5MonotonicBcbt(42L);
        AtomicInteger callIdx = new AtomicInteger(0);
        var outcome = governor.executeBoundedSearch(
                "bcbt-null-exec",
                "main",
                0,
                "Who was the director of The Red Balloon?",
                zone0,
                BudgetVector6D.defaults(),
                cfg,
                ctx -> {
                    int idx = callIdx.incrementAndGet();
                    if (idx == 1) {
                        // 无硬缺陷且标注 preserveAsNullBaseline=true，严禁伪造剪枝丢失基线输出
                        return new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                                "Primary baseline candidate",
                                "Albert Lamorisse directed The Red Balloon according to passage.",
                                "Final Answer: Albert Lamorisse",
                                "Albert Lamorisse directed The Red Balloon in 1956.",
                                null, List.of(), Map.of("branch", "null"),
                                List.of("search_hippocampal_graph"), 120L,
                                false, zone0, true
                        );
                    }
                    return new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                            "Secondary exploratory branch",
                            "Checking alternative entry.",
                            "Final Answer: Pascal Lamorisse",
                            "Pascal Lamorisse starred in The Red Balloon.",
                            null, List.of(), Map.of("branch", "alt"),
                            List.of("read_passage_chunk"), 120L,
                            false, zone0, false
                    );
                }
        );

        assertTrue(outcome.receipt().zone0PrefixPreserved(), "Zone-0 前缀必须保持 100% 字节守恒");
        assertNotNull(outcome.winningOutput());
        assertFalse(outcome.winningOutput().isBlank());
    }
}
