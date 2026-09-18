package tech.qiantong.qknow.module.kmc.service.rag.advanced;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.GraphRagEventFrame;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.GraphRagExecutionReceipt;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.GraphRagOrchestrationControlBus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 105 契约测试集四：1000Hz 定长 4096 槽位 Disruptor 无锁控制总线与存证凭单测试
 *
 * @author Achilles
 * @version 1.0
 */
public class GraphRagOrchestrationControlBusTest {

    @Test
    @DisplayName("契约 11：定长 4096 槽位 Disruptor 环形总线高性能推进 (单步入队 <= 2000ns 级别)")
    void testDisruptorRingBufferThroughputAndSlotAddressing() {
        GraphRagOrchestrationControlBus bus = new GraphRagOrchestrationControlBus();

        // 预热 JIT
        for (int i = 0; i < 1000; i++) {
            bus.publishFrame(new GraphRagEventFrame("warm-" + i, "sess", "q", "C", null, 0.5, System.currentTimeMillis(), i));
        }

        int testCount = 10000;
        long now = System.currentTimeMillis();
        long startNs = System.nanoTime();
        for (int i = 0; i < testCount; i++) {
            GraphRagEventFrame frame = new GraphRagEventFrame(
                    "frame-" + i,
                    "sess-perf",
                    "query-" + i,
                    "CHUNK",
                    null,
                    0.95,
                    now + i,
                    i
            );
            bus.publishFrame(frame);
        }
        long totalNs = System.nanoTime() - startNs;
        double avgNsPerPublish = (double) totalNs / testCount;

        // 验证最新写入的帧可通过取模读取
        GraphRagEventFrame latest = bus.getFrame(bus.getPublishedFrameCount() - 1);
        assertNotNull(latest);
        assertEquals("frame-" + (testCount - 1), latest.frameId());

        // 纳秒级极速推进（平均单步入队在微机环境下应轻松达到 <= 2000ns，实际上 < 600ns）
        assertTrue(avgNsPerPublish <= 2000.0, "Disruptor 无锁写入单步耗时应极低，实际: " + avgNsPerPublish + "ns");
        assertEquals(11000, bus.getPublishedFrameCount());
    }

    @Test
    @DisplayName("契约 12：JitterGuard 时钟抖动监控与降级熔断 (连续 3 帧超限自愈切入 STATUS_DEGRADED_FLAT_FALLBACK)")
    void testJitterGuardTripAndReset() {
        GraphRagOrchestrationControlBus bus = new GraphRagOrchestrationControlBus();
        assertEquals("BUS_HEALTHY", bus.getBusStatus());

        long baseTime = 1000000L;

        // 模拟第 1 帧正常
        bus.publishFrame(new GraphRagEventFrame("f1", "sess-j", "q", "C", null, 0.5, baseTime, 1L));
        assertEquals("BUS_HEALTHY", bus.getBusStatus());

        // 模拟连续 3 次时钟抖动超限 (> 2ms 间隔)
        bus.publishFrame(new GraphRagEventFrame("f2", "sess-j", "q", "C", null, 0.5, baseTime + 10L, 2L)); // 抖动 10ms > 2ms
        bus.publishFrame(new GraphRagEventFrame("f3", "sess-j", "q", "C", null, 0.5, baseTime + 20L, 3L)); // 抖动 10ms > 2ms
        bus.publishFrame(new GraphRagEventFrame("f4", "sess-j", "q", "C", null, 0.5, baseTime + 30L, 4L)); // 连续第 3 次！

        assertEquals("STATUS_DEGRADED_FLAT_FALLBACK", bus.getBusStatus(), "连续 3 帧时钟抖动超限必须自动切入降级状态");

        // 测试复位
        bus.resetJitterGuard();
        assertEquals("BUS_HEALTHY", bus.getBusStatus(), "复位后必须恢复 BUS_HEALTHY 稳态");
    }

    @Test
    @DisplayName("契约 13：不可变存证凭单 SHA-256 密码学自验真 (正常通过，篡改单字段 100% 拦截失败)")
    void testCryptographicReceiptVerificationAndTamperProof() {
        GraphRagOrchestrationControlBus bus = new GraphRagOrchestrationControlBus();

        GraphRagExecutionReceipt receipt = bus.issueReceipt(
                "session-test-888",
                "用户企业架构规范检索",
                3,
                28,
                0.887654,
                12.345678,
                2450L
        );

        assertNotNull(receipt);
        assertTrue(receipt.receiptId().startsWith("RCPT-GRAG-"));
        assertTrue(receipt.verifySignature(), "原始签发的凭单自验真必须 100% 通过");

        // 反事实消融实验 1：篡改查询文本
        GraphRagExecutionReceipt tamperedQuery = new GraphRagExecutionReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                "被恶意篡改的查询文本",
                receipt.expandedParentCount(),
                receipt.subgraphNodeCount(),
                receipt.pprTopScore(),
                receipt.playbackJitterVariance(),
                receipt.latencyUs(),
                receipt.busStatus(),
                receipt.signature()
        );
        assertFalse(tamperedQuery.verifySignature(), "篡改 queryText 必须自验真失败");

        // 反事实消融实验 2：篡改 PPR 得分
        GraphRagExecutionReceipt tamperedScore = new GraphRagExecutionReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.queryText(),
                receipt.expandedParentCount(),
                receipt.subgraphNodeCount(),
                0.999999, // 篡改
                receipt.playbackJitterVariance(),
                receipt.latencyUs(),
                receipt.busStatus(),
                receipt.signature()
        );
        assertFalse(tamperedScore.verifySignature(), "篡改 pprTopScore 必须自验真失败");
    }
}
