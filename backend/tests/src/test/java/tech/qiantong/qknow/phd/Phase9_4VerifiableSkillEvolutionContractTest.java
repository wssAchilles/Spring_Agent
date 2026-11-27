package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.agent.skill.EnterpriseSkillRecord;
import tech.qiantong.qknow.hermes.agent.skill.LifelongSkillBankEngine;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.evolution.CounterfactualSkillRegressionGate;
import tech.qiantong.qknow.hermes.cognitive.evolution.RhoSelfPreferenceOptimizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.SleepTimeEvolutionCoordinator;
import tech.qiantong.qknow.hermes.cognitive.evolution.VerifiedSkillHarvester;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpecCompiler;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.cognitive.verification.RequirementSpecAuditResult;
import tech.qiantong.qknow.hermes.config.HermesPhdEnginesAutoConfiguration;
import tech.qiantong.qknow.hermes.controller.PhdStudioObservabilityController;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;
import tech.qiantong.qknow.hermes.memory.bitemporal.SleepTimeSpectralDistiller;
import tech.qiantong.qknow.hermes.memory.bitemporal.SpectralDistillationReceipt;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ActiveSlotLedgerIndex;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorSpec;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.tool.codeact.CodeActExecutionPlan;
import tech.qiantong.qknow.hermes.tool.codeact.SandboxIsolationAttestation;
import tech.qiantong.qknow.hermes.tool.codeact.SecurityLatticeLevel;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.4 契约单测：VPS-Evo 双见证技能收割、常量验签、反事实零回归门控、真实 L_sym 谱蒸馏、19 维消融与双端装配。
 */
class Phase9_4VerifiableSkillEvolutionContractTest {

    @Test
    void doubleWitnessHarvesterRejectsBrokenSpecOrFailedCodeAct() {
        LifelongSkillBankEngine engine = new LifelongSkillBankEngine("test-secret", 0.85);
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        VerifiedSkillHarvester harvester = new VerifiedSkillHarvester(engine, resolver);
        ReasoningChainConsistencyAuditor auditor = new ReasoningChainConsistencyAuditor(resolver);
        HnscdsAblationConfig g14 = HnscdsAblationConfig.g14HnscdsV4Ultimate(42L);

        SandboxIsolationAttestation att = new SandboxIsolationAttestation(
                "POSIX_ULIMIT_SUBPROCESS", SecurityLatticeLevel.SANDBOX_MEM,
                false, false, true, true, "Mac OS X"
        );
        VerifiedCodeActReceipt passCode = VerifiedCodeActReceipt.createSigned(
                "rcpt-pass", "p1", CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                "python", "hash1", 0, "25.00%", "", false, false, 900L, att, System.currentTimeMillis(),
                "hermes-codeact-sandbox-secret"
        );
        VerifiedCodeActReceipt failCode = VerifiedCodeActReceipt.createSigned(
                "rcpt-fail", "p2", CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                "python", "hash2", 1, "", "ZeroDivisionError", false, false, 900L, att, System.currentTimeMillis(),
                "hermes-codeact-sandbox-secret"
        );

        String q = "What was the percentage increase of total revenue from 2023 to 2024?";
        RequirementSpecAuditResult badSpec = auditor.auditWithSpec(
                "r-bad", q, "diff=25", "25", List.of(), "2023 revenue=100, 2024 revenue=125",
                null, null, false, g14
        );
        RequirementSpecAuditResult goodSpec = auditor.auditWithSpec(
                "r-good", q, "percentage increase of total revenue is (125-100)/100 = 25.00%", "25.00%",
                List.of(), "2023 total revenue=100, 2024 total revenue=125",
                passCode, null, false, g14
        );

        // 任一客观见证未通过 -> 必拒收割
        assertTrue(harvester.harvestIfVerified("s1", q, Map.of(), "print('25%')", List.of("assert True"), failCode, goodSpec).isEmpty());
        assertTrue(harvester.harvestIfVerified("s2", q, Map.of(), "print('25%')", List.of("assert True"), passCode, badSpec).isEmpty());

        // 双见证全过 -> 以 PROBATION 状态 + 长度前缀 SHA-256 注册入库
        Optional<EnterpriseSkillRecord> harvested = harvester.harvestIfVerified(
                "pct_change_skill", q, Map.of("new_val", "number", "old_val", "number"),
                "val = ((${new_val} - ${old_val}) / ${old_val}) * 100.0\nprint(f'{val:.2f}%')",
                List.of("assert abs((((125 - 100) / 100) * 100.0) - 25.0) < 1e-6"),
                passCode, goodSpec
        );
        assertTrue(harvested.isPresent());
        assertEquals(EnterpriseSkillRecord.SkillLifecycleState.PROBATION, harvested.get().lifecycleState());
        assertTrue(harvested.get().verifyContentSha256ConstantTime());
    }

    @Test
    void constantTimeSignatureVerificationBlocksTamperingAndSandboxExecutesVerifiedSkill() {
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            LifelongSkillBankEngine engine = new LifelongSkillBankEngine("skill-sec", 0.85);
            engine.setSecurityAndSandbox(new AstSecurityInspector(), runtime);

            String task = "calculate percentage change between two values";
            float[] vec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(task, 1024);
            EnterpriseSkillRecord validSkill = EnterpriseSkillRecord.ofCodeActTemplate(
                    "cskill_pct", "pct_template", task,
                    Map.of("old_v", "float", "new_v", "float"),
                    "res = ((${new_v} - ${old_v}) / ${old_v}) * 100.0\nprint(f'{res:.2f}%')",
                    List.of("assert True"),
                    EnterpriseSkillRecord.SkillLifecycleState.VERIFIED,
                    vec
            );
            assertTrue(validSkill.verifyContentSha256ConstantTime());

            // 1. 合法指纹 -> 在独立子进程沙盒成功执行并加速回放
            engine.registerSkill(validSkill);
            var replayOk = engine.evaluateAndReplayCodeActSkill(
                    "sess-ok", task, vec, Map.of("old_v", "100", "new_v", "125"), null
            );
            assertTrue(replayOk.replaySuccess());
            assertEquals("25.00%", replayOk.stepOutputs().get(0).trim());

            // 2. 篡改源码但伪造原签名 -> 常量时间 SHA-256 验签立即阻断
            EnterpriseSkillRecord tampered = new EnterpriseSkillRecord(
                    validSkill.skillId(), validSkill.skillName(), validSkill.description(),
                    validSkill.signatureDsl(), validSkill.parameterSlots(), validSkill.actionSequence(),
                    validSkill.preconditionRule(), validSkill.postconditionRule(),
                    0, 0, 0.9, System.currentTimeMillis(), vec,
                    EnterpriseSkillRecord.SkillKind.CODEACT_PYTHON_TEMPLATE,
                    EnterpriseSkillRecord.SkillLifecycleState.VERIFIED,
                    EnterpriseSkillRecord.SkillDisclosureMode.LAZY_EPHEMERAL,
                    "print('TAMPERED_MALICIOUS')",
                    validSkill.contentSha256(),
                    validSkill.unitTestAssertions()
            );
            assertFalse(tampered.verifyContentSha256ConstantTime());
            engine.registerSkill(tampered);
            var replayTampered = engine.evaluateAndReplayCodeActSkill(
                    "sess-tamper", task, vec, Map.of(), null
            );
            assertFalse(replayTampered.replaySuccess());
            assertTrue(replayTampered.fallbackReason().contains("SKILL_SHA256_TAMPER_DETECTED"));

            // 3. 软隔离与冷备可逆恢复（严禁物理硬删除）
            assertTrue(engine.quarantineSkill("cskill_pct", "test_quarantine"));
            assertFalse(engine.getActiveSkillsSnapshot().containsKey("cskill_pct"));
            assertTrue(engine.restoreFromColdBuffer("cskill_pct"));
            assertEquals(EnterpriseSkillRecord.SkillLifecycleState.PROBATION,
                    engine.getActiveSkillsSnapshot().get("cskill_pct").lifecycleState());
        }
    }

    @Test
    void counterfactualRegressionGateEnforcesZeroRegressionAndPacBound() {
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            CounterfactualSkillRegressionGate gate = new CounterfactualSkillRegressionGate();
            AstSecurityInspector ast = new AstSecurityInspector();
            float[] vec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("sum list rows", 1024);

            List<CounterfactualSkillRegressionGate.AnchorTestCase> anchors = List.of(
                    new CounterfactualSkillRegressionGate.AnchorTestCase(
                            "pass-anchor-1", "q1", Map.of("x", "10", "y", "2"), null, "12", true),
                    new CounterfactualSkillRegressionGate.AnchorTestCase(
                            "pass-anchor-2", "q2", Map.of("x", "20", "y", "5"), null, "25", true),
                    new CounterfactualSkillRegressionGate.AnchorTestCase(
                            "fail-anchor-3", "q3", Map.of("x", "30", "y", "10"), null, "40", false)
            );

            // 候选 1：能过 fail-anchor 但把 pass-anchor-2 搞错了（硬编码回归）-> 必拒
            EnterpriseSkillRecord regressive = EnterpriseSkillRecord.ofCodeActTemplate(
                    "sk-reg", "regressive", "adds x and y", Map.of(),
                    "print(${x} + 10)", List.of("assert 1 + 1 == 2"),
                    EnterpriseSkillRecord.SkillLifecycleState.PROBATION, vec
            );
            var rej = gate.evaluateAdmission(regressive, anchors, 0.05, ast, runtime);
            assertFalse(rej.admitted());
            assertTrue(rej.regressionsCount() >= 1);
            assertTrue(rej.rejectionReason().contains("COUNTERFACTUAL_REGRESSION_DETECTED"));

            // 候选 2：零回归且修复了 fail-anchor-3 -> 准入，且 PAC 上界 == min(1.0, ln(1/0.05) / 2)
            EnterpriseSkillRecord cleanSkill = EnterpriseSkillRecord.ofCodeActTemplate(
                    "sk-clean", "clean", "adds x and y", Map.of(),
                    "print(${x} + ${y})", List.of("assert 10 + 2 == 12"),
                    EnterpriseSkillRecord.SkillLifecycleState.PROBATION, vec
            );
            var adm = gate.evaluateAdmission(cleanSkill, anchors, 0.05, ast, runtime);
            assertTrue(adm.admitted());
            assertEquals(0, adm.regressionsCount());
            assertEquals(1, adm.repairsCount());
            assertEquals(2, adm.baselinePassAnchorCount());
            assertEquals(Math.min(1.0, Math.log(1.0 / 0.05) / 2.0), adm.pacRegressionUpperBound(), 1e-6);
        }
    }

    @Test
    void coveringRadiusMergeBoundsCapacityAndRealLaplacianPowerIterationTracksTopology() {
        // 1. S^1023 覆盖半径有界合并：高相似冗余技能合并单测并软隔离低健康度副本
        LifelongSkillBankEngine engine = new LifelongSkillBankEngine("sec");
        float[] vA = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("revenue growth calculation tool", 1024);
        float[] vB = vA.clone(); // 余弦 == 1.0 >= 0.92，同一覆盖球内
        float[] vOrtho = new float[1024];
        vOrtho[0] = -vA[1];
        vOrtho[1] = vA[0]; // 确保与 vA 近似正交不合并
        double nOrtho = 0;
        for (float f : vOrtho) nOrtho += f * f;
        float inv = (float) (1.0 / Math.sqrt(nOrtho));
        for (int i = 0; i < vOrtho.length; i++) vOrtho[i] *= inv;

        engine.registerSkill(EnterpriseSkillRecord.ofCodeActTemplate(
                "sk-main", "main", "d1", Map.of(), "print(1)", List.of("assert 1 == 1"),
                EnterpriseSkillRecord.SkillLifecycleState.VERIFIED, vA));
        engine.registerSkill(EnterpriseSkillRecord.ofCodeActTemplate(
                "sk-dup", "dup", "d2", Map.of(), "print(1)", List.of("assert 2 == 2"),
                EnterpriseSkillRecord.SkillLifecycleState.VERIFIED, vB).withHealthUpdate(false, 1.0, System.currentTimeMillis()));
        engine.registerSkill(EnterpriseSkillRecord.ofCodeActTemplate(
                "sk-orth", "orth", "d3", Map.of(), "print(3)", List.of("assert 3 == 3"),
                EnterpriseSkillRecord.SkillLifecycleState.VERIFIED, vOrtho));

        int merged = engine.consolidateByCoveringRadius(0.92);
        assertEquals(1, merged);
        assertEquals(2, engine.getActiveSkillsSnapshot().size());
        assertEquals(1, engine.getColdBufferSnapshot().size());
        assertEquals(2, engine.getActiveSkillsSnapshot().get("sk-main").unitTestAssertions().size());

        // 2. 真实 L_sym 正交幂迭代区分真实图拓扑：非连通双团 lambda_2 显著低于全连通完全图
        BiTemporalHippocampalGraphEngine disconnected = new BiTemporalHippocampalGraphEngine();
        float[] ev = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("fact node", 1024);
        for (String id : List.of("a1", "a2", "b1", "b2")) {
            disconnected.upsertVertex(new HippocampalVertex(id, id, HippocampalVertexTier.PHRASE_ENTITY, ev));
        }
        long now = System.currentTimeMillis();
        disconnected.ingestFactEdge("a1", "a2", "REL", false, ev, now, now, "d1");
        disconnected.ingestFactEdge("b1", "b2", "REL", false, ev, now, now, "d2");
        SpectralDistillationReceipt disRcpt = new SleepTimeSpectralDistiller(disconnected).distill(0.2, now);

        BiTemporalHippocampalGraphEngine complete = new BiTemporalHippocampalGraphEngine();
        for (String id : List.of("a1", "a2", "b1", "b2")) {
            complete.upsertVertex(new HippocampalVertex(id, id, HippocampalVertexTier.PHRASE_ENTITY, ev));
        }
        for (String u : List.of("a1", "a2", "b1", "b2")) {
            for (String v : List.of("a1", "a2", "b1", "b2")) {
                if (u.compareTo(v) < 0) {
                    complete.ingestFactEdge(u, v, "REL", false, ev, now, now, "d-" + u + v);
                }
            }
        }
        SpectralDistillationReceipt compRcpt = new SleepTimeSpectralDistiller(complete).distill(0.2, now);

        assertTrue(disRcpt.algebraicConnectivityLambda2() < 0.10,
                "非连通双簇图的真实代数连通度 lambda_2 必须接近 0，实际=" + disRcpt.algebraicConnectivityLambda2());
        assertTrue(compRcpt.algebraicConnectivityLambda2() > 0.70,
                "K4 稠密连通图的真实 lambda_2 必须显著大于 0，实际=" + compRcpt.algebraicConnectivityLambda2());
    }

    @Test
    void sleepCoordinatorEndToEndAndDualSpringMirrorWithObservabilityTelemetry() throws Exception {
        // 1. SleepTimeEvolutionCoordinator 完整慢轨协同测试
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine();
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("sec", 42L);
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        LifelongSkillBankEngine bank = new LifelongSkillBankEngine("sec");
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            bank.setSecurityAndSandbox(new AstSecurityInspector(), runtime);
            SleepTimeEvolutionCoordinator coordinator = new SleepTimeEvolutionCoordinator(
                    new SleepTimeSpectralDistiller(hippo),
                    bank,
                    new CounterfactualSkillRegressionGate(),
                    new RhoSelfPreferenceOptimizer(router, resolver, hippo),
                    new AstSecurityInspector(),
                    runtime
            );
            float[] vec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("add numbers skill", 1024);
            bank.registerSkill(EnterpriseSkillRecord.ofCodeActTemplate(
                    "sk-prob", "prob", "add skill", Map.of(),
                    "print(${a} + ${b})", List.of("assert 1 + 2 == 3"),
                    EnterpriseSkillRecord.SkillLifecycleState.PROBATION, vec
            ));
            var anchors = List.of(
                    new CounterfactualSkillRegressionGate.AnchorTestCase("a1", "q", Map.of("a", "3", "b", "4"), null, "7", false)
            );
            var receipt = coordinator.runSleepCycle(anchors, List.of(), 0.2, HnscdsAblationConfig.g14HnscdsV4Ultimate(42L));
            assertTrue(receipt.executed());
            assertEquals(1, receipt.promotedSkillsCount());
            assertEquals(EnterpriseSkillRecord.SkillLifecycleState.VERIFIED,
                    bank.getActiveSkillsSnapshot().get("sk-prob").lifecycleState());

            // 2. HnscdsAblationConfig G11..G14 矩阵与向后兼容校验
            HnscdsAblationConfig leg14 = new HnscdsAblationConfig(
                    "LEG14", true, true, true, true, true, true, 42L,
                    true, true, true, true, true, true
            );
            assertFalse(leg14.requirementSpecVerificationEnabled());
            assertFalse(leg14.verifiedProgrammaticSkillEvolutionEnabled());
            assertEquals(7, HnscdsAblationConfig.v4ExtendedMatrix(42L).size());
            assertEquals("G14_HNSCDS_V4_ULTIMATE", HnscdsAblationConfig.fromProfileId("G14", 42L).profileId());

            // 3. 双端 Spring 配置镜像校验（反射 HermesPhdEnginesAutoConfiguration + 源码 HermesConfiguration.java）
            Set<String> hermesCoreBeans = new HashSet<>();
            for (Method m : HermesPhdEnginesAutoConfiguration.class.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Bean.class)) {
                    hermesCoreBeans.add(m.getName());
                }
            }
            for (String expected : List.of(
                    "questionRequirementSpecCompiler", "stepComputePolicy", "bridgeContractGate",
                    "activeSlotLedgerIndex", "episodicRelationalStateExtractor",
                    "episodicDatasetViewMaterializer", "lifelongSkillBankEngine",
                    "counterfactualSkillRegressionGate", "verifiedSkillHarvester",
                    "sleepTimeEvolutionCoordinator"
            )) {
                assertTrue(hermesCoreBeans.contains(expected), "HermesPhdEnginesAutoConfiguration 缺少 Bean: " + expected);
            }

            Path serverConfigPath = resolveRepoFile("qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java");
            String serverConfigSource = Files.readString(serverConfigPath);
            Matcher beanMatcher = Pattern.compile("@Bean[\\s\\S]{0,160}?public\\s+[\\w<>]+\\s+(\\w+)\\s*\\(")
                    .matcher(serverConfigSource);
            Set<String> serverBeans = new HashSet<>();
            while (beanMatcher.find()) {
                serverBeans.add(beanMatcher.group(1));
            }
            for (String expected : List.of(
                    "questionRequirementSpecCompiler", "stepComputePolicy", "bridgeContractGate",
                    "activeSlotLedgerIndex", "episodicRelationalStateExtractor",
                    "episodicDatasetViewMaterializer", "lifelongSkillBankEngine",
                    "counterfactualSkillRegressionGate", "verifiedSkillHarvester",
                    "sleepTimeEvolutionCoordinator"
            )) {
                assertTrue(serverBeans.contains(expected), "HermesConfiguration 缺少对等镜像 Bean: " + expected);
            }

            // 4. PhdStudioObservabilityController 新增 5 段 Phase 9 遥测键
            PhdStudioObservabilityController controller = new PhdStudioObservabilityController(
                    new PgStepJournalStateCheckpointer(null, "k", 2),
                    new FrontierGatedToolTransactionManager("k"),
                    hippo,
                    new HoareToolContractGateway("k"),
                    router
            );
            controller.setReasoningChainConsistencyAuditor(new ReasoningChainConsistencyAuditor(resolver));
            controller.setStepComputePolicy(new StepComputePolicy());
            controller.setDurableStateGraphEngine(new DurableStateGraphEngine.DefaultDurableStateGraphEngine(
                    new StateCheckpointRepository.DefaultStateCheckpointRepository()));
            controller.setActiveSlotLedgerIndex(new ActiveSlotLedgerIndex());
            controller.setSleepTimeEvolutionCoordinator(coordinator);

            Map<String, Object> telemetry = controller.getTelemetry().getData();
            assertNotNull(telemetry);
            for (String section : List.of(
                    "frsvSpecAudit", "stepHeterogeneousEffort", "typedSubgraphEngine",
                    "episodicRelationalView", "verifiableSkillEvolution"
            )) {
                assertTrue(telemetry.containsKey(section), "PhdStudio 遥测缺少 Phase 9 分段: " + section);
            }
        }
    }

    private static Path resolveRepoFile(String relativePath) {
        for (Path candidate : List.of(
                Path.of(relativePath),
                Path.of("..").resolve(relativePath).normalize(),
                Path.of("../..").resolve(relativePath).normalize(),
                Path.of("backend").resolve(relativePath).normalize()
        )) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("未定位到源码文件: " + relativePath);
    }
}
