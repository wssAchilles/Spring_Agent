package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.benchmark.dataset.AcademicMetricCalculator;
import tech.qiantong.qknow.hermes.benchmark.dataset.RealBenchmarkSample;
import tech.qiantong.qknow.hermes.benchmark.dataset.RealPublicBenchmarkLoader;
import tech.qiantong.qknow.hermes.benchmark.evaluation.AcademicBenchmarkTask;
import tech.qiantong.qknow.hermes.cognitive.context.CausalEpisodeNode;
import tech.qiantong.qknow.hermes.cognitive.context.ContextFoldingReceipt;
import tech.qiantong.qknow.hermes.cognitive.context.HystereticDualZoneContextFolder;
import tech.qiantong.qknow.hermes.cognitive.control.BudgetVector6D;
import tech.qiantong.qknow.hermes.cognitive.control.EpistemicStoppingReceipt;
import tech.qiantong.qknow.hermes.cognitive.control.HypersphericalEpistemicStoppingGovernor;
import tech.qiantong.qknow.hermes.cognitive.control.ReasoningEffortInjectingChatModel;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.speculative.LatticeGatedSpeculativePrefetcher;
import tech.qiantong.qknow.hermes.cognitive.speculative.SpeculativeToolPattern;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.memory.bitemporal.ColdStandbyTier;
import tech.qiantong.qknow.hermes.transaction.frontier.ToolSideEffectTier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7 H-NSCDS v2 三大原创架构机制（HDZ-PCF / HEIG-BAS / LG-STP）、百炼协议自适应与真实公开基准加载器契约测试。
 */
class Phase7ContextFoldingAndSpeculativeEngineContractTest {

    @Test
    void dashScopeEndpointAdaptsThinkingAndMaxCompletionTokens() {
        DeepSeekCompatibleChatModel bailianModel = new DeepSeekCompatibleChatModel(
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "sk-bailian", "deepseek-v4.1-flash", 0.3, 1024, null
        );
        Prompt p = new Prompt(List.of(new UserMessage("验证百炼思考参数自适应")), ToolCallingChatOptions.builder().build());

        Map<String, Object> bodyHigh = DeepSeekCompatibleChatModel.runWithEffortOverride(
                new DeepSeekCompatibleChatModel.ReasoningEffortOverride(true, DeepSeekReasoningEffort.HIGH.wireValue()),
                () -> bailianModel.buildRequestBody(p, false)
        );
        assertEquals(Boolean.TRUE, bodyHigh.get("enable_thinking"), "百炼兼容接口必须输出 enable_thinking=true");
        assertEquals("high", bodyHigh.get("reasoning_effort"));
        assertFalse(bodyHigh.containsKey("thinking"), "百炼接口需剥离原生 thinking 对象");
        assertEquals(1024, bodyHigh.get("max_completion_tokens"));
        assertFalse(bodyHigh.containsKey("max_tokens"));

        // 非百炼官方直连继续保持原生 thinking 对象格式
        DeepSeekCompatibleChatModel officialModel = new DeepSeekCompatibleChatModel(
                "https://api.deepseek.com", "sk-ds", "deepseek-flash", 0.3, 1024, null
        );
        Map<String, Object> bodyOfficial = DeepSeekCompatibleChatModel.runWithEffortOverride(
                new DeepSeekCompatibleChatModel.ReasoningEffortOverride(true, "medium"),
                () -> officialModel.buildRequestBody(p, false)
        );
        assertEquals(Map.of("type", "enabled"), bodyOfficial.get("thinking"));
        assertEquals(1024, bodyOfficial.get("max_tokens"));
        assertNotNull(ReasoningEffortInjectingChatModel.class);
    }

    @Test
    void hystereticDualZoneFolderPreservesPrefixAndSupportsColdStandbyRestore() {
        HystereticDualZoneContextFolder folder = new HystereticDualZoneContextFolder(300, 600, "fold-key");
        String reqId = "req-hdz-1";
        folder.lockZone0Prefix(reqId, "StaticSystemPrompt+4MetaTools");

        float[] vec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("ep", 1024);
        // 追加 3 个共 450 tokens，位于滞回带 [300, 600] 内，HDZ-PCF 不触发折叠（0 次前缀重构）
        for (int i = 1; i <= 3; i++) {
            var r = folder.appendAndMaybeFold(reqId, new CausalEpisodeNode(
                    "ep" + i,
                    i == 2 ? CausalEpisodeNode.EpisodeType.ACTION : CausalEpisodeNode.EpisodeType.EXPLORATORY,
                    "",
                    "探索与动作证据段落 #" + i + " 包含关键实体事实。",
                    150,
                    i == 3 ? List.of("ep1") : List.of(),
                    vec,
                    false,
                    ColdStandbyTier.ACTIVE_HOT,
                    System.currentTimeMillis()
            ), false);
            assertTrue(r.zone0PrefixPreserved());
            assertFalse(r.hysteresisTriggered(), "滞回高水位 600 未突破前不得触发抖动折叠");
        }

        // 第 4 个节点推高至 650 > 600，一次性批量折叠并压回 <= 300，同时验签与冷备还原 100% 成立
        var triggerReceipt = folder.appendAndMaybeFold(reqId, new CausalEpisodeNode(
                "ep4", CausalEpisodeNode.EpisodeType.EXPLORATORY, "", "最新推理尾部证据", 200,
                List.of(), vec, false, ColdStandbyTier.ACTIVE_HOT, System.currentTimeMillis()
        ), false);
        assertTrue(triggerReceipt.hysteresisTriggered(), "突破 H_hi=600 必须触发批量滞回折叠");
        assertTrue(triggerReceipt.tokensAfterFold() <= 350);
        assertTrue(triggerReceipt.coldStandbyArchivedCount() >= 1);
        assertTrue(triggerReceipt.verifyMac("fold-key"));
        assertTrue(triggerReceipt.zone0PrefixPreserved());

        // 优先折叠无未折叠后继的 ep2 (ACTION) 而保护 ep3 依赖链上的 ep1
        String firstFoldedId = triggerReceipt.foldedEpisodeIds().get(0);
        assertEquals("ep2", firstFoldedId, "ACTION 节点且无后继依赖必须最先被安全折叠");
        assertTrue(folder.restoreFoldedEpisode(reqId, firstFoldedId), "三级冷备归档情景必须支持 100% 反事实还原");

        // 对照 CWL 原地驱逐模式：每突破低水位即原地修改导致前缀多次失效
        String cwlReq = "req-cwl-compare";
        for (int i = 1; i <= 4; i++) {
            folder.appendAndMaybeFold(cwlReq, new CausalEpisodeNode(
                    "cwl" + i, CausalEpisodeNode.EpisodeType.ACTION, "", "CWL动作轨迹 #" + i,
                    200, List.of(), vec, false, ColdStandbyTier.ACTIVE_HOT, System.currentTimeMillis()
            ), true);
        }
        assertTrue(folder.getPrefixInvalidationCount(cwlReq) >= 2, "CWL 原地驱逐必然导致高频前缀失效");
        assertEquals(0, folder.getPrefixInvalidationCount(reqId), "HDZ-PCF Zone-0 保持零失效");
    }

    @Test
    void epistemicStoppingGovernorStopsOnLowInnovationAndHighBudgetPressure() {
        HypersphericalEpistemicStoppingGovernor governor = new HypersphericalEpistemicStoppingGovernor(
                new UnitSphereEmbeddingResolver(), 0.08
        );
        HnscdsAblationConfig v2Cfg = HnscdsAblationConfig.g6HnscdsV2Full(42L);
        BudgetVector6D budget = new BudgetVector6D(8192L, 6144L, 6, 30_000L, 0.15, 4);

        var r1 = governor.evaluateStep("req-stop", 1, "全新领域证据：甲公司于2025年收购乙公司", 300L, budget, v2Cfg);
        assertEquals(EpistemicStoppingReceipt.StoppingDecision.CONTINUE_DEEPEN, r1.decision());
        assertTrue(r1.tailBudgetTag().contains("<budget_state"));

        // 连续两步返回语义高度重复/零新息的文本 -> 触发 STOP_AND_SYNTHESIZE 最优停机
        var r2 = governor.evaluateStep("req-stop", 2, "全新领域证据：甲公司于2025年收购乙公司", 300L, budget, v2Cfg);
        assertEquals(EpistemicStoppingReceipt.StoppingDecision.PIVOT_EXPLORE, r2.decision());

        var r3 = governor.evaluateStep("req-stop", 3, "全新领域证据：甲公司于2025年收购乙公司", 300L, budget, v2Cfg);
        assertEquals(EpistemicStoppingReceipt.StoppingDecision.STOP_AND_SYNTHESIZE, r3.decision(),
                "连续两步测地新息枯竭必须触发确定性最优停机");
    }

    @Test
    void latticeGatedSpeculativePrefetcherEnforcesBufferableSafetyAndAcceleratesReads() {
        LatticeGatedSpeculativePrefetcher prefetcher = new LatticeGatedSpeculativePrefetcher();
        AtomicInteger riskyExecutions = new AtomicInteger();

        // 1. 绝不允许对 IRREVERSIBLE 或 EXTERNALIZED_REVERSIBLE 工具触发投机
        boolean allowedIrrev = prefetcher.scheduleSpeculativePrefetch(
                "req-spec",
                new SpeculativeToolPattern("p-risk", "after_audit", "transfer_funds", 0.99, ToolSideEffectTier.IRREVERSIBLE),
                "{\"amount\":1000}",
                () -> {
                    riskyExecutions.incrementAndGet();
                    return "DANGEROUS_EXECUTED";
                }
        );
        assertFalse(allowedIrrev, "非 BUFFERABLE 工具绝禁止投机预取");
        assertEquals(0, riskyExecutions.get());
        assertEquals(1L, prefetcher.getTotalSafetyLatticeBlocks());

        // 2. BUFFERABLE 只读工具通过 Java 21 虚拟线程投机预取并在消费时命中加速
        boolean allowedRead = prefetcher.scheduleSpeculativePrefetch(
                "req-spec",
                new SpeculativeToolPattern("p-read", "after_q", "search_hippocampal_graph", 0.92, ToolSideEffectTier.BUFFERABLE),
                "Scott Derrickson nationality",
                () -> {
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ignored) {
                    }
                    return "Scott Derrickson is an American director.";
                }
        );
        assertTrue(allowedRead);
        String hitVal = prefetcher.consumeIfReady(
                "req-spec", "search_hippocampal_graph", "Scott Derrickson nationality", ToolSideEffectTier.BUFFERABLE, 1000L
        );
        assertEquals("Scott Derrickson is an American director.", hitVal);
        assertEquals(1L, prefetcher.getTotalCacheHits());
    }

    @Test
    void realPublicBenchmarkLoaderAndAcademicMetricCalculatorWorkAcrossAllFourDatasets() {
        RealPublicBenchmarkLoader loader = new RealPublicBenchmarkLoader();
        List<RealBenchmarkSample> suite = loader.loadStratifiedSuite(3, 42L);
        assertEquals(12, suite.size(), "本地 4 大真实公开数据集各抽 3 条应共返回 12 条样本");
        assertTrue(suite.stream().anyMatch(s -> s.dataset() == AcademicBenchmarkTask.BenchmarkDataset.HOTPOT_QA));
        assertTrue(suite.stream().anyMatch(s -> s.dataset() == AcademicBenchmarkTask.BenchmarkDataset.MUSIQUE));
        assertTrue(suite.stream().anyMatch(s -> s.dataset() == AcademicBenchmarkTask.BenchmarkDataset.LONG_MEM_EVAL));
        assertTrue(suite.stream().anyMatch(s -> s.dataset() == AcademicBenchmarkTask.BenchmarkDataset.TABLE_BENCH));
        assertTrue(suite.stream().allMatch(s -> !s.question().isBlank() && !s.goldAnswers().isEmpty()));

        // 验证学术 EM / Token-F1 与浮点容差计算
        var exactScore = AcademicMetricCalculator.scoreAnswer("最终答案：yes", List.of("yes"));
        assertEquals(1.0, exactScore.exactMatch(), 1e-6);
        assertEquals(1.0, exactScore.tokenF1(), 1e-6);

        var numScore = AcademicMetricCalculator.scoreAnswer("Average is 10.605 cyclones.", List.of("10.6"));
        assertEquals(1.0, numScore.exactMatch(), 1e-6, "TableBench 1% 浮点容差数值匹配应判定 EM=1.0");
        assertEquals(1.0, numScore.tokenF1(), 1e-6, "数值容差判定通过时 Token-F1 同步给满分");

        var wordNumScore = AcademicMetricCalculator.scoreAnswer("Final Answer: 5", List.of("Five"));
        assertEquals(1.0, wordNumScore.exactMatch(), 1e-6, "英文基数词 Five 与数字 5 必须等价");
        assertEquals(1.0, wordNumScore.tokenF1(), 1e-6);

        var commaDecScore = AcademicMetricCalculator.scoreAnswer("Final Answer: 2,755,233.4", List.of("2755233.40"));
        assertEquals(1.0, commaDecScore.exactMatch(), 1e-6, "千分位逗号与尾零小数必须规范化一致");
        assertEquals(1.0, commaDecScore.tokenF1(), 1e-6);

        // 验证 LG-STP 对键序/空格差异 JSON 参数的标准化消费命中
        LatticeGatedSpeculativePrefetcher jsonPrefetcher = new LatticeGatedSpeculativePrefetcher();
        assertTrue(jsonPrefetcher.scheduleSpeculativePrefetch(
                "req-json-norm",
                new SpeculativeToolPattern("p-json", "search->read", "read_passage_chunk", 0.90, ToolSideEffectTier.BUFFERABLE),
                "{\"title\":\"Doc A\",\"k\":1}",
                () -> "Full Doc A Content"
        ));
        String jsonHit = jsonPrefetcher.consumeIfReady(
                "req-json-norm", "read_passage_chunk", "{ \"k\": 1, \"title\": \"Doc A\" }",
                ToolSideEffectTier.BUFFERABLE, 1000L
        );
        assertEquals("Full Doc A Content", jsonHit, "等价 JSON 参数不同空白与键序必须 100% 命中投机槽位");
    }
}
