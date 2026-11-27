package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.skill.LifelongSkillBankEngine;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.benchmark.dataset.RealBenchmarkSample;
import tech.qiantong.qknow.hermes.benchmark.evaluation.AcademicBenchmarkTask;
import tech.qiantong.qknow.hermes.benchmark.evaluation.RealBailianMultiTurnExperimentDriver;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.evolution.CounterfactualSkillRegressionGate;
import tech.qiantong.qknow.hermes.cognitive.evolution.RhoSelfPreferenceOptimizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.SleepTimeEvolutionCoordinator;
import tech.qiantong.qknow.hermes.cognitive.evolution.VerifiedSkillHarvester;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.cognitive.verification.EvidenceGrade;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpecCompiler;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.BridgeContractGate;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;
import tech.qiantong.qknow.hermes.memory.bitemporal.SleepTimeSpectralDistiller;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ActiveSlotLedgerIndex;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicDatasetViewMaterializer;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicRelationalStateExtractor;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.5 离线端到端集成契约单测：
 * 验证 FRSV 防假阳规则校准、BTER-MV 口语消费抽取直连 CodeAct、AgentOrchestrator Phase 9 主干接入、
 * PA-TSG 三节点确定性并发流水线以及 G14 配置兼容性。
 */
class Phase9_5OrchestratorAndDriverSynergyContractTest {

    private static UnitSphereEmbeddingResolver hashResolver() {
        return new UnitSphereEmbeddingResolver();
    }

    @Test
    void frsvCompilerAvoidsFalsePositivesOnEntityPercentageAndPieChartsWhileBlockingRealViolations() {
        QuestionRequirementSpecCompiler compiler = new QuestionRequirementSpecCompiler();
        ReasoningChainConsistencyAuditor auditor = new ReasoningChainConsistencyAuditor(hashResolver());
        HnscdsAblationConfig v4Cfg = HnscdsAblationConfig.g14HnscdsV4Ultimate(42L);
        List<AttestedToolWitness> witnesses = List.of(AttestedToolWitness.createSigned(
                "r1", "execute_verified_codeact", "{}", "george enescu international airport | 68.06",
                EvidenceGrade.TOOL_WITNESS, System.currentTimeMillis(), "s"
        ));

        // 1. 实体选择题（含 percentage change 定义但仅问哪个机场）不得要求答案携带 %
        String airportQ = "Airport Traffic Growth Rate is defined as the percentage change in the number of passengers handled by an airport from one year to another. Based on this definition, which airport has the largest Airport Traffic Growth Rate from 2008 to 2009?";
        QuestionRequirementSpec airportSpec = compiler.compile(airportQ);
        assertEquals("", airportSpec.requiredUnit(), "实体选择问句不得强加 % 单位要求");
        assertTrue(airportSpec.requiresCodeActWitness());
        var airportAudit = auditor.auditWithSpec(
                "r-airport", airportQ,
                "Computed percentage change across airports from 2008 to 2009; George Enescu International Airport is largest (68.06%).",
                "george enescu international airport",
                witnesses,
                "george enescu international airport growth 68.06%",
                null, airportSpec, false, v4Cfg
        );
        assertFalse(airportAudit.hasHardViolations(), "合法实体回答不应被误杀: " + airportAudit.correctiveSpecHint());

        // 2. pie chart 绘图题必须归为 SERIES_LIST 而非 RATIO_SHARE
        String pieQ = "Please help me draw a pie chart that shows the proportions of all species in Costa Rica.";
        QuestionRequirementSpec pieSpec = compiler.compile(pieQ);
        assertEquals(QuestionRequirementSpec.MetricType.SERIES_LIST, pieSpec.metricType());
        assertEquals("y_references", pieSpec.outputFormatPattern());
        var pieAudit = auditor.auditWithSpec(
                "r-pie", pieQ,
                "Extracted exact species counts into y_references series.",
                "y_references = [[183, 838, 232, 258, 12119]]",
                witnesses, "183 838 232 258 12119", null, pieSpec, false, v4Cfg
        );
        assertFalse(pieAudit.hasHardViolations(), "合法 pie chart 序列不应被误杀: " + pieAudit.correctiveSpecHint());

        // 3. 礼貌描述祈使句 ("Can you describe...") 不得判为 BOOLEAN_YES_NO
        String descQ = "Can you describe the structure of the table, highlight the main columns, and provide key insights?";
        QuestionRequirementSpec descSpec = compiler.compile(descQ);
        assertNotEquals(QuestionRequirementSpec.MetricType.BOOLEAN_YES_NO, descSpec.metricType());

        // 4. 显式索要百分比与分母时，若缺失 % 与分母口径仍必须刚性拦截
        String pctQ = "Which state or region has the highest proportion of Military MPs to total MPs, and what is the percentage?";
        QuestionRequirementSpec pctSpec = compiler.compile(pctQ);
        assertEquals("%", pctSpec.requiredUnit());
        var pctViolation = auditor.auditWithSpec(
                "r-pct", pctQ,
                "Kayin State Hluttaw seems high.",
                "Kayin State Hluttaw, 0.26",
                witnesses, "Military MPs data", null, pctSpec, false, v4Cfg
        );
        assertTrue(pctViolation.hasHardViolations(), "缺失 % 且缺少 total mps 分母推导时必须刚性拦截");
    }

    @Test
    void bterMvExtractsSpokenSpendingAndWeightFactsIntoCodeActDatasetView() {
        UnitSphereEmbeddingResolver resolver = hashResolver();
        EpisodicDatasetViewMaterializer materializer = new EpisodicDatasetViewMaterializer(
                new TemporalAnchorQueryParser(),
                new ActiveSlotLedgerIndex(),
                new EpisodicRelationalStateExtractor(resolver),
                null
        );

        long day1 = 1_700_000_000_000L;
        long day2 = 1_700_100_000_000L;
        int c1 = materializer.ingestDialogueTurn("s1", "sess_1", "user",
                "Yesterday I spent $120 for groceries at Thrive Market and my weight was 74 kg.", day1);
        int c2 = materializer.ingestDialogueTurn("s1", "sess_2", "user",
                "Today I paid $85 for groceries at Walmart, ordered $60 from Publix, and my weight is 72 kg.", day2);
        assertTrue(c1 >= 2 && c2 >= 3, "英文消费金额与数值状态更新应被稳定抽入台账");

        var viewReceipt = materializer.materializeViewForQuery(
                "s1", "Which grocery store did I spend the most money at?", day2 + 1000L, true
        );
        assertTrue(viewReceipt.totalProjectedRows() >= 4);

        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            String py = """
                    totals = {}
                    for r in DATASET_ROWS:
                        if r[2] == 'spending' and r[4]:
                            totals[r[1]] = totals.get(r[1], 0.0) + float(r[4])
                    top_store = max(totals.items(), key=lambda kv: kv[1])[0]
                    print(top_store)
                    """;
            VerifiedCodeActReceipt rec = runtime.executePython(
                    "p95-e2r-test", py, viewReceipt.datasetView(),
                    new EnterpriseCodeSandboxRuntime.SandboxResourceQuota(4000L, 128L * 1024 * 1024, 3_000_000L, 32 * 1024, true, true)
            );
            assertTrue(rec.isSuccess(), "CodeAct 执行失败: " + rec.stderr());
            assertTrue(rec.stdout().contains("Thrive Market"), "期望输出 Thrive Market，实际: " + rec.stdout());
        }
    }

    @Test
    void agentOrchestratorExposesAndAcceptsAllPhase9MainlineEngines() throws Exception {
        UnitSphereEmbeddingResolver resolver = hashResolver();
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("p95-orch");
        LifelongSkillBankEngine bank = new LifelongSkillBankEngine("p95-secret");
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);

        orchestrator.setQuestionRequirementSpecCompiler(new QuestionRequirementSpecCompiler());
        orchestrator.setStepComputePolicy(new StepComputePolicy());
        orchestrator.setDurableStateGraphEngine(new DurableStateGraphEngine.DefaultDurableStateGraphEngine(
                new StateCheckpointRepository.DefaultStateCheckpointRepository()
        ));
        orchestrator.setEpisodicDatasetViewMaterializer(new EpisodicDatasetViewMaterializer(
                new TemporalAnchorQueryParser(), new ActiveSlotLedgerIndex(),
                new EpisodicRelationalStateExtractor(resolver), hippo
        ));
        orchestrator.setVerifiedSkillHarvester(new VerifiedSkillHarvester(bank, resolver));
        orchestrator.setSleepTimeEvolutionCoordinator(new SleepTimeEvolutionCoordinator(
                new SleepTimeSpectralDistiller(hippo),
                bank,
                new CounterfactualSkillRegressionGate(),
                new RhoSelfPreferenceOptimizer(new HypersphericalSupernetRouter(), resolver, hippo),
                new AstSecurityInspector(),
                null
        ));

        assertNull(orchestrator.getLatestSpecAuditResult("non-existent-req"));
        assertTrue(orchestrator.getLatestStepComputeReceipts("non-existent-req").isEmpty());
        Method recordPost = AgentOrchestrator.class.getDeclaredMethod(
                "recordPostAgentPhdEngines",
                tech.qiantong.qknow.hermes.proto.ChatRequest.class,
                String.class, long.class, long.class, long.class, long.class, boolean.class
        );
        assertNotNull(recordPost);
    }

    @Test
    void paTsgDriverPipelineExecutesDeterministicallyWithZone0Preserved() {
        UnitSphereEmbeddingResolver resolver = hashResolver();
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("p95-hippo");
        long now = System.currentTimeMillis();
        RealBenchmarkSample sample = new RealBenchmarkSample(
                "MUSIQUE-SYN-1",
                AcademicBenchmarkTask.BenchmarkDataset.MUSIQUE,
                "Which network owns the station where Alpha Show first aired?",
                List.of("British Broadcasting Corporation"),
                List.of(
                        new RealBenchmarkSample.ContextChunk("Alpha Show", "Alpha Show first aired on Radio Alpha in 1995.", ""),
                        new RealBenchmarkSample.ContextChunk("Radio Alpha", "Radio Alpha is a radio station operated by British Broadcasting Corporation.", ""),
                        new RealBenchmarkSample.ContextChunk("Distractor One", "This distractor talks about unrelated astronomy conferences.", ""),
                        new RealBenchmarkSample.ContextChunk("Distractor Two", "This distractor covers European railway schedules.", ""),
                        new RealBenchmarkSample.ContextChunk("Distractor Three", "Another unrelated history paragraph.", ""),
                        new RealBenchmarkSample.ContextChunk("Distractor Four", "Final unrelated biography note.", "")
                ),
                List.of("Alpha Show", "Radio Alpha"),
                "2hop",
                "",
                false
        );

        for (int i = 0; i < sample.contextChunks().size(); i++) {
            var c = sample.contextChunks().get(i);
            String tid = "ent_" + i;
            String pid = "chunk_" + i;
            float[] tv = resolver.resolveUnit1024(c.title());
            float[] pv = resolver.resolveUnit1024(c.content());
            hippo.upsertVertex(new HippocampalVertex(tid, c.title(), HippocampalVertexTier.PHRASE_ENTITY, tv));
            hippo.upsertVertex(new HippocampalVertex(pid, "[" + c.title() + "] " + c.content(), HippocampalVertexTier.PASSAGE_EPISODE, pv));
            hippo.ingestFactEdge(tid, pid, "CONTAINS_EVIDENCE", false, pv, now, now, sample.sampleId());
        }

        BridgeContractGate gate = new BridgeContractGate();
        String anchorPrompt = "STATIC_ZONE0_SYSTEM_ANCHOR_V4_FOR_PA_TSG_TEST";
        var res1 = RealBailianMultiTurnExperimentDriver.runTypedParallelSubgraphBrief(
                "exec-p95-1", sample, anchorPrompt, hippo, gate, resolver, now
        );
        var res2 = RealBailianMultiTurnExperimentDriver.runTypedParallelSubgraphBrief(
                "exec-p95-2", sample, anchorPrompt, hippo, gate, resolver, now
        );

        assertTrue(res1.executed(), "PA-TSG 子图流水线应当成功跑至终态");
        assertTrue(res1.zone0Intact(), "Zone-0 前缀 SHA-256 锚点必须全程守恒");
        assertTrue(res1.fanoutCount() >= 2, "应当并行扇出多个工作节点实例: " + res1.fanoutCount());
        assertTrue(res1.briefText().contains("<pa_tsg_verified_brief>"));
        assertTrue(res1.briefText().contains("Radio Alpha"));
        assertEquals(res1.stateDigest(), res2.stateDigest(), "同输入多次并行运行最终状态哈希必须 100% 一致");
    }

    @Test
    void g14ConfigEnablesAllFiveV4MechanismsWhilePreservingLegacyAnchors() {
        HnscdsAblationConfig g14 = HnscdsAblationConfig.fromProfileId("G14_HNSCDS_V4_ULTIMATE", 42L);
        assertTrue(g14.requirementSpecVerificationEnabled());
        assertTrue(g14.stepHeterogeneousEffortEnabled());
        assertTrue(g14.typedParallelSubgraphEnabled());
        assertTrue(g14.episodicRelationalViewEnabled());
        assertTrue(g14.verifiedProgrammaticSkillEvolutionEnabled());

        HnscdsAblationConfig g10 = HnscdsAblationConfig.fromProfileId("G10_HNSCDS_V3_ULTIMATE", 42L);
        assertFalse(g10.requirementSpecVerificationEnabled());
        assertFalse(g10.typedParallelSubgraphEnabled());
        assertTrue(g10.causalReflexiveTreeSearchEnabled());
    }

    @Test
    void frsvExtractsFinalAnswerSegmentAndDoesNotMisclassifyWorldSeriesOrProseNumbers() {
        QuestionRequirementSpecCompiler compiler = new QuestionRequirementSpecCompiler();
        ReasoningChainConsistencyAuditor auditor = new ReasoningChainConsistencyAuditor(hashResolver());
        HnscdsAblationConfig v4Cfg = HnscdsAblationConfig.g14HnscdsV4Ultimate(42L);
        List<AttestedToolWitness> witnesses = List.of(AttestedToolWitness.createSigned(
                "r95b", "execute_verified_codeact", "{}", "353.2",
                EvidenceGrade.TOOL_WITNESS, System.currentTimeMillis(), "s"
        ));

        // 1. "World Series" 不得被误判为图表 SERIES_LIST
        QuestionRequirementSpec wsSpec = compiler.compile("When was the 2016 World Series played?");
        assertNotEquals(QuestionRequirementSpec.MetricType.SERIES_LIST, wsSpec.metricType());
        assertNotEquals("y_references", wsSpec.outputFormatPattern());

        // 2. Final Answer 段中的 353.2 与 CodeAct stdout="353.2" 匹配时，不得被正文开头前提数字 "10" 误杀
        String tableQ = "What is the average total points of all couples who have performed more than 10 dances?";
        VerifiedCodeActReceipt okReceipt = VerifiedCodeActReceipt.createSigned(
                "rcpt-avg", "plan-avg",
                tech.qiantong.qknow.hermes.tool.codeact.CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                "python", "deadbeef", 0, "353.2\n", "", false, false, 100L,
                new tech.qiantong.qknow.hermes.tool.codeact.SandboxIsolationAttestation(
                        "HOST_PYTHON",
                        tech.qiantong.qknow.hermes.tool.codeact.SecurityLatticeLevel.RO_DATA_READ,
                        true, true, true, true, "macOS"
                ),
                System.currentTimeMillis(), "s"
        );
        var avgAudit = auditor.auditWithSpec(
                "r-avg", tableQ,
                "Filtered couples with > 10 dances and computed average = 353.2.",
                "Couples who performed more than 10 dances averaged 353.2.\n\nFinal Answer: 353.2",
                witnesses, "couples dances total points 353.2", okReceipt, null, false, v4Cfg
        );
        assertTrue(avgAudit.codeActWitnessAligned(), "正文出现前提阈值 10 时不得误报 CODEACT_STDOUT_ANSWER_MISMATCH");
        assertFalse(avgAudit.hasHardViolations(), "应无硬违约: " + avgAudit.correctiveSpecHint());

        // 3. 饼图题若仅正文提及 y_references 但 Final Answer 行输出文本百分比，必须触发 Y_REFERENCES_FORMAT_MISSING
        String pieQ = "Please help me draw a pie chart that shows the proportions of all species in Costa Rica.";
        var pieBad = auditor.auditWithSpec(
                "r-pie-bad", pieQ,
                "Calculated proportions for chart.",
                "Prepared y_references proportions: Mammals 1.70%, Reptiles 1.89%.\n\nFinal Answer: Mammals 1.70%, Reptiles 1.89%",
                witnesses, "species counts Costa Rica", null, null, false, v4Cfg
        );
        assertTrue(pieBad.violations().stream().anyMatch(v -> "Y_REFERENCES_FORMAT_MISSING".equals(v.code())),
                "Final Answer 未采用 y_references = [[...]] 格式时必须被硬拦截");
    }
}
