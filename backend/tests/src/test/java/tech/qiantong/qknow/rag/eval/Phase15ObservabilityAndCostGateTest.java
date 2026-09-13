package tech.qiantong.qknow.rag.eval;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.cost.DeepSeekCostGovernor;
import tech.qiantong.qknow.module.kmc.service.rag.metrics.RagMetricsService;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15 契约门禁测试：生产级可观测性、Prometheus 核心埋点与 DeepSeek 成本/延迟治理体系
 */
public class Phase15ObservabilityAndCostGateTest {

    @Test
    @DisplayName("契约 1：RagMetricsService 预热初始化且总时序基数严格有界（<= 90）")
    void testRagMetricsServicePrewarmAndBoundedCardinality() {
        MeterRegistry registry = new SimpleMeterRegistry();
        RagMetricsService metricsService = new RagMetricsService(registry);

        // 模拟各阶段埋点记录
        for (RagMetricsService.RagStage stage : RagMetricsService.RagStage.values()) {
            metricsService.recordStage(stage, RagMetricsService.StageOutcome.OK, 10_000_000L); // 10ms
            metricsService.recordStage(stage, RagMetricsService.StageOutcome.BYPASS, 1_000L);
        }

        // 模拟门控计数
        metricsService.recordCacheHitExact();
        metricsService.recordCacheHitSemantic();
        metricsService.recordCacheMiss();
        metricsService.recordCacheBypass();
        metricsService.recordRerankGate(true);
        metricsService.recordRerankGate(false);
        metricsService.recordZeroHit(false);
        metricsService.recordZeroHit(true);

        // 验证 Registry 中生成的 Meters 数量
        long meterCount = registry.getMeters().size();
        assertTrue(meterCount > 0, "预热后应包含预注册的 Meters");
        assertTrue(meterCount <= 90, "总 Meter 数量必须受控在 90 以内，防止高基数膨胀，当前为: " + meterCount);

        // 验证各计时器可正确获取
        assertNotNull(registry.find("rag.stage.duration").tags("stage", "vector", "outcome", "ok").timer());
        assertEquals(1, registry.find("rag.stage.duration").tags("stage", "vector", "outcome", "ok").timer().count());
    }

    @Test
    @DisplayName("契约 2：DeepSeekCostGovernor 纳元定点整数计算精度 100% 吻合官方定价（零浮点截断误差）")
    void testDeepSeekCostGovernorFixedPointPrecision() {
        MeterRegistry registry = new SimpleMeterRegistry();
        DeepSeekCostGovernor governor = new DeepSeekCostGovernor(registry);

        // 场景 A：DeepSeek-V3 计费验证
        // 官方单价：Hit 输入 0.5元/M，Miss 输入 2.0元/M，输出 8.0元/M
        // 测试输入：1,000,000 hit + 1,000,000 miss + 1,000,000 completion
        // 预期精确总额：0.5 + 2.0 + 8.0 = 10.500000000 元整
        governor.recordUsage("deepseek-chat", 1_000_000L, 1_000_000L, 1_000_000L, 0L);

        double costV3 = governor.getCostInYuan("deepseek-chat");
        assertEquals(10.50, costV3, 0.000000001, "DeepSeek-V3 金额必须严格为 10.5 元整，浮点数误差必须为 0");

        // 场景 B：DeepSeek-R1 计费验证
        // 官方单价：Hit 输入 1.0元/M，Miss 输入 4.0元/M，输出 16.0元/M
        // 测试输入：500,000 hit + 500,000 miss + 200,000 completion (含 100,000 reasoning)
        // 预期金额：0.5*1.0 + 0.5*4.0 + 0.2*16.0 = 0.5 + 2.0 + 3.2 = 5.700000000 元整
        governor.recordUsage("deepseek-reasoner", 500_000L, 500_000L, 200_000L, 100_000L);

        double costR1 = governor.getCostInYuan("deepseek-reasoner");
        assertEquals(5.70, costR1, 0.000000001, "DeepSeek-R1 金额必须严格为 5.7 元整，误差必须为 0");

        // 验证 Gauge 监控正确输出
        Gauge costGaugeV3 = registry.find("llm.cost.cny.total").tag("model", "deepseek-chat").gauge();
        assertNotNull(costGaugeV3);
        assertEquals(10.50, costGaugeV3.value(), 0.000000001);
    }

    @Test
    @DisplayName("契约 3：DeepSeekCostGovernor 高并发无锁累加数据完整性（多线程 LongAdder 无丢失）")
    void testDeepSeekCostGovernorHighConcurrencyWithoutLoss() throws InterruptedException {
        MeterRegistry registry = new SimpleMeterRegistry();
        DeepSeekCostGovernor governor = new DeepSeekCostGovernor(registry);

        int threads = 10;
        int iterationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        // 每个线程执行 1000 次，每次输入 10 hit + 20 miss + 30 completion
        // 总调用量：10 * 1000 = 10,000 次
        // 总 hit = 100,000, 总 miss = 200,000, 总 completion = 300,000
        // DeepSeek-V3 费用：(100,000 * 500 + 200,000 * 2000 + 300,000 * 8000) 纳元
        // = (50,000,000 + 400,000,000 + 2,400,000,000) 纳元 = 2,850,000,000 纳元 = 2.85 元
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        governor.recordUsage("deepseek-chat", 10L, 20L, 30L, 0L);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "并发累加应在 5 秒内执行完成");
        executor.shutdown();

        double totalCost = governor.getCostInYuan("deepseek-chat");
        assertEquals(2.85, totalCost, 0.000000001, "高并发累加后总金额必须完全精确无丢失");

        DeepSeekCostGovernor.ModelAccumulator acc = governor.getAccumulator("deepseek-chat");
        assertNotNull(acc);
        assertEquals(100_000L, acc.promptCacheHitTokens.sum());
        assertEquals(200_000L, acc.promptCacheMissTokens.sum());
        assertEquals(300_000L, acc.completionTokens.sum());
    }

    @Test
    @DisplayName("契约 4：活跃 LLM 并发请求计数器 Gauge 步进平衡")
    void testActiveLlmRequestsGaugeBalance() {
        MeterRegistry registry = new SimpleMeterRegistry();
        DeepSeekCostGovernor governor = new DeepSeekCostGovernor(registry);

        governor.markRequestStart();
        governor.markRequestStart();
        Gauge gauge = registry.find("llm.active.requests").gauge();
        assertNotNull(gauge);
        assertEquals(2.0, gauge.value());

        governor.markRequestEnd();
        assertEquals(1.0, gauge.value());

        governor.markRequestEnd();
        assertEquals(0.0, gauge.value());
    }
}
