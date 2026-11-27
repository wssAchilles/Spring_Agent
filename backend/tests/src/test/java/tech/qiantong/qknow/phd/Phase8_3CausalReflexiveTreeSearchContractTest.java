package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.BudgetVector6D;
import tech.qiantong.qknow.hermes.cognitive.control.HypersphericalEpistemicStoppingGovernor;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalFailureReflector;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalReflexiveTreeSearchGovernor;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalSearchBranchNode;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalTreeSearchReceipt;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CounterfactualBranchPatch;
import tech.qiantong.qknow.hermes.cognitive.treesearch.NeuroSymbolicBranchEvaluator;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.cognitive.verification.EvidenceGrade;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalProbeReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8.3 超球面漂移触发有界因果反思树搜索（BCBT）契约测试（验证定理 8.3）。
 */
class Phase8_3CausalReflexiveTreeSearchContractTest {

    private static float[] unitVec(String seed) {
        return LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(seed, 1024);
    }

    @Test
    void persistentForkIsolatesMainBranchAndPrunedBranchEntersColdRingAndErrorBook() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        PgStepJournalStateCheckpointer stepJournal = new PgStepJournalStateCheckpointer();
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine();
        NeuroSymbolicBranchEvaluator evaluator = new NeuroSymbolicBranchEvaluator(
                new ReasoningChainConsistencyAuditor(resolver), resolver
        );
        CausalFailureReflector reflector = new CausalFailureReflector(hippo, resolver);
        HypersphericalEpistemicStoppingGovernor epistemic = new HypersphericalEpistemicStoppingGovernor(resolver);

        CausalReflexiveTreeSearchGovernor governor = new CausalReflexiveTreeSearchGovernor(
                stepJournal, evaluator, reflector, epistemic, resolver
        );

        String execId = "exec-bcbt-fork-1";
        String question = "Calculate the quarterly revenue growth rate cleanly";
        String zone0Prefix = "[SYSTEM_STATIC_ZONE0] Strict deterministic agent contract.";

        // 主干先行提交 superstep=0 基线状态，后续搜索分叉绝不得污染主干
        stepJournal.executeOrReplayIdempotentStep(
                execId, "main", 0, "init", "idem-main-0", 1L, Map.of(),
                () -> Map.of("main_fact", "clean_baseline", "status", "locked")
        );

        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            AtomicInteger callSeq = new AtomicInteger(0);
            CausalReflexiveTreeSearchGovernor.SearchOutcome outcome = governor.executeBoundedSearch(
                    execId,
                    "main",
                    0,
                    question,
                    zone0Prefix,
                    BudgetVector6D.defaults(),
                    HnscdsAblationConfig.g10HnscdsV3Ultimate(42L),
                    ctx -> {
                        int idx = callSeq.incrementAndGet();
                        // 验证分叉从 main superstep=0 完整继承初始状态
                        assertEquals("clean_baseline", ctx.reconstructedBranchState().get("main_fact"));
                        if (idx == 1) {
                            // 分支 1：触发真实 AST 拦截导致硬失败（退出码 -403）
                            VerifiedCodeActReceipt blocked = runtime.executePython(
                                    "plan-fail-1",
                                    "import os\nprint(os.listdir('.'))",
                                    null,
                                    EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
                            );
                            assertFalse(blocked.isSuccess());
                            return new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                                    "Attempt restricted os module inspection",
                                    "Inspect working directory using python",
                                    "Failed execution",
                                    "AST blocked os import",
                                    blocked,
                                    List.of(),
                                    Map.of("branch_attempt", "failed_ast"),
                                    List.of("codeact_python"),
                                    120L,
                                    false,
                                    zone0Prefix
                            );
                        }
                        // 分支 2：验证收到了前驱失败的反事实尾部补丁，并执行合法纯数学计算成功收敛；
                        // 若后续继续进入确认步（idx >= 3），尾部约束列表仍持续保持反事实纠偏历史
                        if (idx == 2) {
                            assertNotNull(ctx.appliedPatch());
                        }
                        assertFalse(ctx.appendOnlyTailConstraints().isEmpty());
                        assertTrue(ctx.appendOnlyTailConstraints().get(0).contains("CAUSAL_REFLECTION_PATCH"));

                        VerifiedCodeActReceipt pass = runtime.executeMath("plan-pass-2", "(120 - 100) / 100");
                        assertTrue(pass.isSuccess());
                        AttestedToolWitness witness = AttestedToolWitness.createSigned(
                                execId, "codeact_math", "(120-100)/100", pass.stdout(),
                                EvidenceGrade.DOUBLE_ATTESTED, System.currentTimeMillis(), "secret"
                        );
                        return new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                                "Compute growth rate cleanly via verified Tier-0 math",
                                "Compute quarterly revenue growth rate (120 - 100) / 100 = 0.2",
                                "The quarterly revenue growth rate is 0.2 (20%).",
                                "Calculate quarterly revenue growth rate result 0.2 from codeact_math",
                                pass,
                                List.of(witness),
                                Map.of("branch_attempt", "converged_math", "result", "0.2"),
                                List.of("codeact_math"),
                                90L,
                                false,
                                zone0Prefix
                        );
                    }
            );

            CausalTreeSearchReceipt receipt = outcome.receipt();
            assertTrue(receipt.verifyMac("hermes-bcbt-tree-search-secret"));
            assertTrue(receipt.zone0PrefixPreserved());
            assertEquals(1, receipt.prunedAndQuarantinedBranches());
            assertTrue(receipt.totalExpandedBranches() >= 2);
            assertEquals(1, receipt.errorBookEntriesWritten());
            assertTrue(outcome.winningOutput().contains("0.2"));
            assertFalse(outcome.generatedPatches().isEmpty());
            assertEquals(CounterfactualBranchPatch.FailureRootCause.TOOL_CONTRACT_VIOLATION,
                    outcome.generatedPatches().get(0).rootCause());

            // 关键断言 1：主干 main 状态零污染（不含有任何子分支覆盖字段）
            Map<String, Object> mainState = stepJournal.reconstructStateAt(execId, "main", 0);
            assertEquals("clean_baseline", mainState.get("main_fact"));
            assertFalse(mainState.containsKey("branch_attempt"));

            // 关键断言 2：被剪枝分支进入三级冷备可逆环而非物理删树
            assertTrue(stepJournal.getColdRingSize() >= 1);

            // 关键断言 3：错题本 ERROR_BOOK 真实落盘且在海马体同主题检索中召回警示
            long nowMs = System.currentTimeMillis() + 100L;
            HippocampalProbeReceipt probe = hippo.probeIterativeSubgraph(
                    "q-err-recall", question, unitVec(question), nowMs, nowMs, 3
            );
            assertFalse(probe.recalledErrorBookWarnings().isEmpty(),
                    "海马体 PPR 必须召回 BCBT 写入的 ERROR_BOOK 防错警示");
            assertTrue(probe.recalledErrorBookWarnings().get(0).contains("[ERROR_BOOK]"));
        }
    }

    @Test
    void zone0PrefixTamperingIsInterceptedAndHmacVerifyRejectsTamperedReceipt() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        CausalReflexiveTreeSearchGovernor governor = new CausalReflexiveTreeSearchGovernor(
                new PgStepJournalStateCheckpointer(),
                new NeuroSymbolicBranchEvaluator(new ReasoningChainConsistencyAuditor(resolver), resolver),
                new CausalFailureReflector(null, resolver),
                new HypersphericalEpistemicStoppingGovernor(resolver),
                resolver
        );

        String execId = "exec-bcbt-zone0-tamper";
        String frozenPrefix = "[ZONE0_IMMUTABLE] DO_NOT_MODIFY";

        CausalReflexiveTreeSearchGovernor.SearchOutcome outcome = governor.executeBoundedSearch(
                execId,
                "main",
                0,
                "Verify immutable Zone-0 contract",
                frozenPrefix,
                BudgetVector6D.defaults(),
                HnscdsAblationConfig.g10HnscdsV3Ultimate(42L),
                ctx -> new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                        "Malicious branch mutating Zone-0",
                        "Try mutating frozen prefix",
                        "Answer after prefix mutation",
                        "Evidence summary",
                        null,
                        List.of(),
                        Map.of(),
                        List.of(),
                        64L,
                        false,
                        "[ZONE0_TAMPERED] corrupted"
                )
        );

        CausalTreeSearchReceipt receipt = outcome.receipt();
        assertFalse(receipt.zone0PrefixPreserved(), "改写 Zone-0 前缀的分支必须被检测并标记 zone0PrefixPreserved=false");
        assertTrue(receipt.prunedAndQuarantinedBranches() >= 1);
        assertTrue(receipt.verifyMac("hermes-bcbt-tree-search-secret"));

        // 破坏凭单内容后，常量时间 HMAC 验签必须返回 false
        CausalTreeSearchReceipt tamperedReceipt = new CausalTreeSearchReceipt(
                receipt.receiptId(),
                receipt.executionId(),
                receipt.zone0PrefixSha256(),
                true, // 篡改保护位
                receipt.totalExpandedBranches(),
                receipt.prunedAndQuarantinedBranches(),
                receipt.maxDepthReached(),
                receipt.epistemicEarlyStopped(),
                receipt.winningBranchId(),
                receipt.winningValue(),
                receipt.errorBookEntriesWritten(),
                receipt.optimalTrajectoryNodeIds(),
                receipt.elapsedMicros(),
                receipt.timestampEpochMs(),
                receipt.hmacSignature()
        );
        assertFalse(tamperedReceipt.verifyMac("hermes-bcbt-tree-search-secret"));
    }

    @Test
    void boundedQuotaAndHeigBasEarlyStoppingGuaranteeFiniteTermination() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        CausalReflexiveTreeSearchGovernor governor = new CausalReflexiveTreeSearchGovernor(
                new PgStepJournalStateCheckpointer(),
                new NeuroSymbolicBranchEvaluator(new ReasoningChainConsistencyAuditor(resolver), resolver),
                new CausalFailureReflector(null, resolver),
                new HypersphericalEpistemicStoppingGovernor(resolver),
                resolver,
                "secret-quota-test",
                2,
                3,
                5
        );

        // 场景 A：连续重复低测地新息观察触发 HEIG-BAS 最优早停（早于 maxTotalNodes=5）
        CausalReflexiveTreeSearchGovernor.SearchOutcome earlyStopped = governor.executeBoundedSearch(
                "exec-heig-stop",
                "main",
                0,
                "Check epistemic stopping convergence",
                "ZONE0",
                BudgetVector6D.defaults(),
                HnscdsAblationConfig.g10HnscdsV3Ultimate(42L),
                ctx -> new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                        "Same repeated observation",
                        "Check epistemic stopping convergence on identical observation",
                        "Exact same repeated evidence passage without new information",
                        "Exact same repeated evidence passage without new information",
                        null,
                        List.of(),
                        Map.of(),
                        List.of(),
                        80L,
                        false,
                        "ZONE0"
                )
        );
        assertTrue(earlyStopped.receipt().epistemicEarlyStopped());
        assertTrue(earlyStopped.receipt().totalExpandedBranches() <= 4);

        // 场景 B：即便所有分支连续失败且新息各异，也严格受限于 maxTotalNodes <= 5 不会死循环
        AtomicInteger seq = new AtomicInteger(0);
        CausalReflexiveTreeSearchGovernor.SearchOutcome quotaBounded = governor.executeBoundedSearch(
                "exec-quota-bound",
                "main",
                0,
                "Test hard node quota bound",
                "ZONE0",
                BudgetVector6D.defaults(),
                HnscdsAblationConfig.g10HnscdsV3Ultimate(42L),
                ctx -> {
                    int n = seq.incrementAndGet();
                    return new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                            "Divergent temporal contradiction step " + n,
                            "Unique branch reasoning " + n,
                            "Unique diverging claim #" + n + " alpha beta gamma",
                            "Different evidence #" + n,
                            null,
                            List.of(),
                            Map.of(),
                            List.of(),
                            64L,
                            true,
                            "ZONE0"
                    );
                }
        );
        assertTrue(quotaBounded.receipt().totalExpandedBranches() <= 5);
        for (CausalSearchBranchNode node : quotaBounded.allNodes()) {
            assertTrue(node.depth() <= 2);
        }
    }

    @Test
    void ablationDisabledDegradesToSingleLinearBranchWithoutForks() {
        PgStepJournalStateCheckpointer stepJournal = new PgStepJournalStateCheckpointer();
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        CausalReflexiveTreeSearchGovernor governor = new CausalReflexiveTreeSearchGovernor(
                stepJournal,
                new NeuroSymbolicBranchEvaluator(new ReasoningChainConsistencyAuditor(resolver), resolver),
                new CausalFailureReflector(null, resolver),
                new HypersphericalEpistemicStoppingGovernor(resolver),
                resolver
        );

        // G6_HNSCDS_V2_FULL 未开启 Phase 8.3 causalReflexiveTreeSearchEnabled
        CausalReflexiveTreeSearchGovernor.SearchOutcome ablated = governor.executeBoundedSearch(
                "exec-ablated-linear",
                "main",
                0,
                "Linear baseline check",
                "ZONE0",
                BudgetVector6D.defaults(),
                HnscdsAblationConfig.g6HnscdsV2Full(42L),
                ctx -> new CausalReflexiveTreeSearchGovernor.BranchExpansionResult(
                        "Single linear step",
                        "Reasoning linear baseline check",
                        "Linear baseline answer",
                        "Linear baseline evidence",
                        null,
                        List.of(),
                        Map.of(),
                        List.of(),
                        64L,
                        false,
                        "ZONE0"
                )
        );

        assertEquals(1, ablated.receipt().totalExpandedBranches());
        assertEquals(0, ablated.receipt().prunedAndQuarantinedBranches());
        assertEquals(0, ablated.receipt().maxDepthReached());
        assertEquals("main", ablated.receipt().winningBranchId());
        assertEquals(0, stepJournal.getColdRingSize());
    }

    @Test
    void triggerPredicateRespondsToCodeActFailureTemporalContradictionAndDrift() {
        NeuroSymbolicBranchEvaluator evaluator = new NeuroSymbolicBranchEvaluator();
        float[] q = unitVec("revenue growth 2024");
        float[] aligned = q;
        float[] drifted = unitVec("unrelated meteorology rainfall statistics in antarctica");

        assertFalse(evaluator.shouldTriggerTreeSearch(q, aligned, null, false, 0.35));
        assertTrue(evaluator.shouldTriggerTreeSearch(q, aligned, null, true, 0.35));
        assertTrue(evaluator.shouldTriggerTreeSearch(q, drifted, null, false, 0.35));
    }
}
