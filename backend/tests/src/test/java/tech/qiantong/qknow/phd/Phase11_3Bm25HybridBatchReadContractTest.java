package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.cognitive.tool.BatchParallelReadCoordinator;
import tech.qiantong.qknow.hermes.memory.hybrid.Bm25HypersphericalHybridIndex;
import tech.qiantong.qknow.hermes.memory.hybrid.QueryFocusedMultiWindowExtractor;
import tech.qiantong.qknow.hermes.transaction.frontier.ToolSideEffectTier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11.3 (BPR-LM) 批量并发只读检索与 BM25-超球面混合分片索引契约单测。
 * 重点复现 LONGMEM-37d43f65 (16GB@2385 中段黑洞) 与 LONGMEM-gpt4_2ba83207 (Thrive Market@11685 / $150) 夹具。
 */
class Phase11_3Bm25HybridBatchReadContractTest {

    @Test
    void queryFocusedExtractorRecoversMiddle16GbThatClampLongPassageDrops() {
        // 构造 15,547 字符同构长会话：头部只含 "laptop RAM upgrade"，关键 "16GB" 精确置于偏移 2385（正落入 [2000, len-2000) 黑洞）
        String headIntro = "user: I recently upgraded my laptop's RAM because browsing felt sluggish on my old notebook.\n"
                + "assistant: That's great! What kind of work do you usually do on your computer? "
                + "Everyday office software benefits a lot from additional memory.\n";
        String filler = "We discussed browser tab management, SSD cleanup, screen brightness settings, and cooling pads. ".repeat(22);
        StringBuilder doc = new StringBuilder(16000);
        doc.append(headIntro).append(filler);
        while (doc.length() < 2385) {
            doc.append("Extra hardware troubleshooting tips and battery saver suggestions. ");
        }
        doc.setLength(2385);
        doc.append("My laptop is a Dell Inspiron 15 5000 series. Before the RAM upgrade to 16GB, battery life was 6-7 hours.");
        while (doc.length() < 15547) {
            doc.append(" More general software discussion about operating system updates and keyboard shortcuts.");
        }
        doc.setLength(15547);

        String fullSession = doc.toString();
        assertTrue(fullSession.indexOf("16GB") >= 2380 && fullSession.indexOf("16GB") < 13547,
                "关键答案必须精确落入旧 clampLongPassage [2000, len-2000) 截断黑洞区间");

        String extracted = QueryFocusedMultiWindowExtractor.extractMultiWindowEvidence(
                "session_0", fullSession, "What did I upgrade my laptop RAM capacity to?"
        );
        assertTrue(extracted.contains("16GB"),
                "QueryFocusedMultiWindowExtractor 必须穿透中段黑洞精准抽取偏移 2385 的 16GB 证据");
        assertTrue(extracted.length() <= 3900);
    }

    @Test
    void hybridSliceIndexRecallsThriveMarket150DollarsAtTailOfMultiSessionFixture() {
        Function<String, float[]> encoder = txt -> LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(txt, 1024);
        Bm25HypersphericalHybridIndex index = new Bm25HypersphericalHybridIndex();

        String s0 = "May 21: Trader Joe's receipt total $64 and grocery checklist. "
                + "General conversation about meal prepping and kitchen organization. ".repeat(145)
                + "Picked up specialty sauces at Walmart ($45) and browsed Thrive Market pantry staples.";
        String s1 = "May 23: Walmart weekly run for household paper goods and detergents ($70). "
                + "Talked about weekend gardening plans and weather forecasts. ".repeat(150);
        String s2 = "May 25: Placed a large organic pantry order at Thrive Market and spent around $150 total. "
                + "Also discussed recipes, spices, and storage jars in great detail. ".repeat(140)
                + "Briefly stopped by Publix for fresh fruit ($32).";
        String s3 = "May 26: Stopped by Publix ($38) and picked up snacks at Walmart ($25). "
                + "Long discussion about summer travel packing tips and luggage sizes. ".repeat(130);

        index.indexDocument("session_0", s0, "2023/05/21", encoder);
        index.indexDocument("session_1", s1, "2023/05/23", encoder);
        index.indexDocument("session_2", s2, "2023/05/25", encoder);
        index.indexDocument("session_3", s3, "2023/05/26", encoder);

        String query = "Thrive Market $150 grocery store past month";
        var outcome = index.search("req-longmem", query, encoder.apply(query), Map.of("session_2", 1.0), 4);

        assertFalse(outcome.hits().isEmpty());
        boolean hitThrive150Slice = outcome.hits().stream().anyMatch(h ->
                "session_2".equals(h.entry().docTitle())
                        && h.entry().content().contains("Thrive Market")
                        && h.entry().content().contains("$150"));
        assertTrue(hitThrive150Slice, "BM25-超球面混合分片检索必须直接定位并返回含 Thrive Market 与 $150 的精确分片");
        assertTrue(outcome.hits().stream().allMatch(h -> h.bm25Score() >= 0.0), "Lucene IDF 必须严格非负");
        assertTrue(outcome.receipt().topBm25Score() > 0.0);
    }

    @Test
    void batchParallelReadCoordinatorDeduplicatesAcceleratesPreservesOrderAndIsolatesIrreversible() {
        BatchParallelReadCoordinator coordinator = new BatchParallelReadCoordinator();
        AtomicInteger dupExecCount = new AtomicInteger(0);

        List<BatchParallelReadCoordinator.ToolInvocationTask> tasks = List.of(
                new BatchParallelReadCoordinator.ToolInvocationTask(
                        "c1", "read_passage_chunk", "{\"title\":\"s0\"}", ToolSideEffectTier.BUFFERABLE,
                        () -> { sleepQuietly(60); dupExecCount.incrementAndGet(); return "R0"; }),
                new BatchParallelReadCoordinator.ToolInvocationTask(
                        "c2", "read_passage_chunk", "{\"title\":\"s1\"}", ToolSideEffectTier.BUFFERABLE,
                        () -> { sleepQuietly(60); return "R1"; }),
                // 与 c1 参数一致，应触发批内去重复用
                new BatchParallelReadCoordinator.ToolInvocationTask(
                        "c3", "read_passage_chunk", "{ \"title\":\"s0\" }", ToolSideEffectTier.BUFFERABLE,
                        () -> { sleepQuietly(60); dupExecCount.incrementAndGet(); return "R0"; }),
                new BatchParallelReadCoordinator.ToolInvocationTask(
                        "c4", "read_passage_chunk", "{\"title\":\"s2\"}", ToolSideEffectTier.BUFFERABLE,
                        () -> { sleepQuietly(60); return "R2"; }),
                new BatchParallelReadCoordinator.ToolInvocationTask(
                        "c5", "commit_high_risk_action", "{\"action\":\"wire\"}", ToolSideEffectTier.IRREVERSIBLE,
                        () -> { sleepQuietly(20); return "SERIAL_GUARDED"; })
        );

        var outcome = coordinator.executeOrdered("req-bpr", tasks, Duration.ofSeconds(5));
        assertEquals(List.of("R0", "R1", "R0", "R2", "SERIAL_GUARDED"), outcome.orderedResults(),
                "虚拟线程并发归并结果必须 100% 保持原始任务下标顺序");
        assertEquals(4, outcome.receipt().parallelReadTasks());
        assertEquals(1, outcome.receipt().serialGuardedTasks(), "IRREVERSIBLE 任务必须被强制串行隔离");
        assertEquals(1, outcome.receipt().deduplicatedTasks(), "规范化等价的重复只读调用必须触发批内去重");
        assertTrue(outcome.receipt().wallClockMs() < outcome.receipt().sumTaskMs() + 25L);
        assertEquals(1, dupExecCount.get(), "规范化一致键仅应实际执行 1 次");
    }

    @Test
    void perDocDiversityPassPreventsHighFrequencySessionFromCrowdingOutFourthDevice() {
        Function<String, float[]> encoder = txt -> LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(txt, 1024);
        Bm25HypersphericalHybridIndex index = new Bm25HypersphericalHybridIndex();

        // 模拟 gpt4_31ff4165：session_2 重复大量 "hearing aids device"（~30次），session_3 开头有 "Accu-Chek blood glucose"
        String s0 = "Fitbit guided breathing health device daily usage. " + "general wellness tips. ".repeat(80);
        String s1 = "Fitbit Versa 3 smartwatch health tracker step count. " + "walking suggestions. ".repeat(80);
        String s2 = ("hearing aids BTE health device battery replacement. ".repeat(15) + "\n").repeat(3);
        String s3 = "Accu-Chek Aviva Nano blood glucose health device three times a day. " + "breakfast recipes. ".repeat(85);
        String s4 = "Nebulizer machine inhalation health device twice a day. " + "steam care tips. ".repeat(80);

        index.indexDocument("session_0", s0, "2023/05/21", encoder);
        index.indexDocument("session_1", s1, "2023/05/22", encoder);
        index.indexDocument("session_2", s2, "2023/05/22", encoder);
        index.indexDocument("session_3", s3, "2023/05/23", encoder);
        index.indexDocument("session_4", s4, "2023/05/24", encoder);

        String query = "How many health-related devices do I use in a day Fitbit Accu-Chek nebulizer hearing";
        var outcome = index.search("req-div", query, encoder.apply(query), Map.of(), 4);
        long distinctTitles = outcome.hits().stream().map(h -> h.entry().docTitle()).distinct().count();
        assertEquals(4L, distinctTitles, "Top-4 检索结果必须覆盖 4 个独立会话，禁止单个高频会话重复霸占名额");
        assertTrue(outcome.hits().stream().anyMatch(h -> "session_3".equals(h.entry().docTitle())),
                "Per-Doc 多样性必须确保含唯一实体 Accu-Chek 的 session_3 被稳定召回");
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
