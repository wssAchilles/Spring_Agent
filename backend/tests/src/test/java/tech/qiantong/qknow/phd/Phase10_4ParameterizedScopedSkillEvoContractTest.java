package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.hermes.agent.skill.EnterpriseSkillRecord;
import tech.qiantong.qknow.hermes.agent.skill.LifelongSkillBankEngine;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.evolution.CounterfactualSkillRegressionGate;
import tech.qiantong.qknow.hermes.cognitive.evolution.ProgrammaticSkillSynthesizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.RhoSelfPreferenceOptimizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.SleepTimeEvolutionCoordinator;
import tech.qiantong.qknow.hermes.cognitive.evolution.SynthesizedSkillCandidate;
import tech.qiantong.qknow.hermes.cognitive.evolution.VerifiedSkillHarvester;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.cognitive.verification.EvidenceGrade;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpecCompiler;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.SleepTimeSpectralDistiller;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10.4 (P-VPS-Evo) 离线契约单测（驱动真实 Python3 安全格沙盒）：
 * 验证自合成参数化槽位+内联快照+非空自包含单元测试、条件作用域 Seeger PAC-Bayes 零回归晋升、
 * 越界/回归候选软隔离进冷备仓以及 VERIFIED 技能前向 Zone-1 复用召回。
 */
class Phase10_4ParameterizedScopedSkillEvoContractTest {

    @Test
    void synthesizerAndScopedSleepCoordinatorPromoteSafeSkillAndReuseOnline() throws Exception {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        AstSecurityInspector astInspector = new AstSecurityInspector();
        ProgrammaticSkillSynthesizer synthesizer = new ProgrammaticSkillSynthesizer(astInspector, resolver);
        LifelongSkillBankEngine skillBank = new LifelongSkillBankEngine("pvps-test-secret", 0.65);
        VerifiedSkillHarvester harvester = new VerifiedSkillHarvester(skillBank, resolver);
        CounterfactualSkillRegressionGate gate = new CounterfactualSkillRegressionGate();
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("pvps-hippo");
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("pvps-router", 42L);
        SleepTimeEvolutionCoordinator coordinator = new SleepTimeEvolutionCoordinator(
                new SleepTimeSpectralDistiller(hippo),
                skillBank,
                gate,
                new RhoSelfPreferenceOptimizer(router, resolver, hippo),
                astInspector,
                null
        );

        TemporalAnchorQueryParser parser = new TemporalAnchorQueryParser();
        ReadOnlyDatasetView tableView = new ReadOnlyDatasetView(
                "tv-1",
                parser.parse("total spending", System.currentTimeMillis()),
                List.of("merchant", "spending"),
                List.of(List.of("Best Buy", "350"), List.of("Trader Joe", "65")),
                2, 0, "sha-tv-1"
        );

        String rawPython = """
                idx = DATASET_COLUMNS.index("spending")
                total = sum(float(r[idx]) for r in DATASET_ROWS)
                print(round(total, 2))
                """;
        String question = "What is the total amount of spending?";
        QuestionRequirementSpec spec = new QuestionRequirementSpecCompiler().compile(question);

        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            VerifiedCodeActReceipt rcpt = runtime.executePython(
                    "synth-1", rawPython, tableView,
                    EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
            );
            assertTrue(rcpt.isSuccess(), "Python 沙盒首次执行应通过: " + rcpt.stderr());

            ReasoningChainConsistencyAuditor auditor = new ReasoningChainConsistencyAuditor(resolver);
            var specAudit = auditor.auditWithSpec(
                    "audit-pvps", question,
                    "Executed deterministic summation over DATASET_ROWS spending column yielding 415.0.",
                    "Final Answer: 415.0",
                    List.of(AttestedToolWitness.createSigned(
                            "w-py", "execute_verified_codeact", rawPython, rcpt.stdout(),
                            EvidenceGrade.TOOL_WITNESS, 1000L, "sec"
                    )),
                    rcpt.stdout(), rcpt, spec, false, HnscdsAblationConfig.g18HnscdsV5Ultimate(42L)
            );
            assertTrue(specAudit.passed());

            // 1. 自动合成带 ${target_col} 参数槽位、<=25 行内联快照和自包含 assert 断言的候选技能
            var candOpt = synthesizer.synthesizeFromExecution(
                    "skill-spend-sum", question, spec, tableView, rawPython, rcpt, specAudit
            );
            assertTrue(candOpt.isPresent(), "双见证通过脚本必须成功合成候选");
            SynthesizedSkillCandidate cand = candOpt.get();
            assertTrue(cand.parameterSlots().containsKey("target_col"));
            assertTrue(cand.codeActPythonTemplate().contains("${target_col}"));
            assertFalse(cand.unitTestAssertions().isEmpty(), "必须告别缺失单测断言死锁");

            // 禁止 try: / import os 的危险脚本必须被拒绝
            assertTrue(synthesizer.synthesizeFromExecution(
                    "bad-skill", question, spec, tableView, "try:\n    print(1)\nexcept:\n    pass", rcpt, specAudit
            ).isEmpty());

            // 2. 验证作用域评估与 Seeger PAC-Bayes 上界 < 1.0
            var scopedReceipt = gate.evaluateScopedAdmission(
                    cand, cand.scopedAnchors(), 0.72, 0.05, astInspector, runtime
            );
            assertTrue(scopedReceipt.admitted(), "作用域准入失败: " + scopedReceipt.rejectionReason());
            assertTrue(scopedReceipt.seegerPacUpperBound() > 0.0 && scopedReceipt.seegerPacUpperBound() < 1.0,
                    "Seeger PAC-Bayes 上界必须严格 < 1.0: " + scopedReceipt.seegerPacUpperBound());

            // 3. 入库并跑通作用域睡眠期周期（借助真实沙盒执行晋升）
            SleepTimeEvolutionCoordinator liveCoordinator = new SleepTimeEvolutionCoordinator(
                    new SleepTimeSpectralDistiller(hippo),
                    skillBank,
                    gate,
                    new RhoSelfPreferenceOptimizer(router, resolver, hippo),
                    astInspector,
                    runtime
            );
            assertTrue(harvester.harvestSynthesizedCandidate(cand, resolver.resolveUnit1024(question)).isPresent());
            var sleepReceipt = liveCoordinator.runScopedSleepCycle(
                    harvester.drainPendingScopedCandidates(),
                    cand.scopedAnchors(), 0.72, 0.05, 0.20,
                    HnscdsAblationConfig.g18HnscdsV5Ultimate(42L)
            );
            assertTrue(sleepReceipt.executed());
            assertEquals(1, sleepReceipt.promotedSkillsCount(), "P-VPS-Evo 应真实晋升 1 个 VERIFIED 技能");
            assertEquals(0, sleepReceipt.quarantinedSkillsCount());
            assertEquals(EnterpriseSkillRecord.SkillLifecycleState.VERIFIED,
                    skillBank.getActiveSkillsSnapshot().get("skill-spend-sum").lifecycleState());

            // 4. 在线推理前向检索 Hint 必须通过常量时间 SHA-256 验签并命中
            var hintOpt = skillBank.retrieveVerifiedSkillHint(
                    "Calculate the total amount of spending from table",
                    resolver.resolveUnit1024(question),
                    0.75
            );
            assertTrue(hintOpt.isPresent(), "已晋升 VERIFIED 技能必须在 Zone-1 前向复用查询中被召回");
            assertEquals("skill-spend-sum", hintOpt.get().skillId());
        }
        assertNotNull(coordinator.getTelemetryStats());
    }

    @Test
    void regressionInScopedAnchorQuarantinesCandidateToColdBuffer() throws Exception {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        AstSecurityInspector astInspector = new AstSecurityInspector();
        CounterfactualSkillRegressionGate gate = new CounterfactualSkillRegressionGate();
        LifelongSkillBankEngine skillBank = new LifelongSkillBankEngine("pvps-reg-secret", 0.65);
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("pvps-reg-hippo");
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("pvps-reg-router", 42L);
        TemporalAnchorQueryParser parser = new TemporalAnchorQueryParser();

        ReadOnlyDatasetView tv = new ReadOnlyDatasetView(
                "tv-reg", parser.parse("sum", System.currentTimeMillis()),
                List.of("val"), List.of(List.of("10"), List.of("20")), 2, 0, "sha-reg"
        );
        float[] qVec = resolver.resolveUnit1024("What is total val?");
        var brokenAnchor = new CounterfactualSkillRegressionGate.ScopedAnchorTestCase(
                "anc-fail", "What is total val?", Map.of("target_col", "val"), tv,
                "999999", true, QuestionRequirementSpec.MetricType.RAW_VALUE, qVec
        );
        SynthesizedSkillCandidate brokenCand = new SynthesizedSkillCandidate(
                "broken-skill", "broken_name", "broken skill",
                QuestionRequirementSpec.MetricType.RAW_VALUE,
                Map.of("target_col", "val"),
                "print(30)",
                List.of("assert isinstance(DATASET_ROWS, list)"),
                List.of(brokenAnchor),
                "trace"
        );
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            SleepTimeEvolutionCoordinator coordinator = new SleepTimeEvolutionCoordinator(
                    new SleepTimeSpectralDistiller(hippo),
                    skillBank,
                    gate,
                    new RhoSelfPreferenceOptimizer(router, resolver, hippo),
                    astInspector,
                    runtime
            );
            var rec = coordinator.runScopedSleepCycle(
                    List.of(brokenCand), List.of(brokenAnchor), 0.72, 0.05, 0.20,
                    HnscdsAblationConfig.g18HnscdsV5Ultimate(42L)
            );
            assertEquals(0, rec.promotedSkillsCount());
            assertEquals(1, rec.quarantinedSkillsCount(), "基线原本通过而候选输出错位必须严格触发回归软隔离");
        }
    }
}
