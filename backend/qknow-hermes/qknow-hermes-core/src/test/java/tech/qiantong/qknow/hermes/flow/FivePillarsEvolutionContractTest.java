package tech.qiantong.qknow.hermes.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.debate.ConsensusArbitrationReceipt;
import tech.qiantong.qknow.hermes.agent.debate.impl.DefaultConsensusArbiterEngine;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.LeaseAcquisitionReceipt;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.StateCheckpointRecord;
import tech.qiantong.qknow.hermes.flow.checkpoint.spi.impl.InMemoryRingBufferCheckpointStorage;
import tech.qiantong.qknow.hermes.flow.debug.DebugBreakpointRecord;
import tech.qiantong.qknow.hermes.flow.debug.TimeTravelStepRecord;
import tech.qiantong.qknow.hermes.flow.debug.impl.DefaultWorkflowTimeTravelDebugger;
import tech.qiantong.qknow.hermes.flow.mesh.governance.AIMDConcurrencyGovernor;
import tech.qiantong.qknow.hermes.flow.mesh.governance.MeshBackpressureTelemetry;
import tech.qiantong.qknow.hermes.flow.mesh.governance.MeshSchedulingTicket;
import tech.qiantong.qknow.hermes.flow.mesh.governance.impl.DefaultAdaptiveBackpressureMeshCoordinator;
import tech.qiantong.qknow.hermes.tool.hnsw.HypersphericalCosineMetric;
import tech.qiantong.qknow.hermes.tool.hnsw.HypersphericalHnswToolIndex;
import tech.qiantong.qknow.hermes.tool.hnsw.impl.DefaultHypersphericalHnswToolIndex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 五大架构演进支柱核心生产契约回归测试套件
 * 严格运行于 Java 21 隔离环境
 */
public class FivePillarsEvolutionContractTest {

    @Test
    @DisplayName("支柱一契约测试: 自适应背压网格调度器与 AIMD 并发调谐")
    void testAdaptiveBackpressureMeshCoordinatorSlotAndGovernor() throws Exception {
        byte[] secret = "test_backpressure_secret_key_32b!".getBytes(StandardCharsets.UTF_8);
        DefaultAdaptiveBackpressureMeshCoordinator coordinator =
                new DefaultAdaptiveBackpressureMeshCoordinator(4, 2, 8, 1000L, secret);

        // 1. 申请槽位并验证凭单
        CompletableFuture<Optional<MeshSchedulingTicket>> ticketFuture =
                coordinator.acquireSlotAsync("run_1001", "node_fetch", 2000L);
        Optional<MeshSchedulingTicket> ticketOpt = ticketFuture.get();
        assertTrue(ticketOpt.isPresent(), "应当成功分配网格执行槽位");
        MeshSchedulingTicket ticket = ticketOpt.get();
        assertEquals("run_1001", ticket.runtimeId());
        assertEquals("node_fetch", ticket.nodeId());
        assertTrue(ticket.verifySignature(secret), "凭单 HMAC 签名必须通过常量时间验签");

        // 2. 释放槽位并回传遥测
        coordinator.releaseSlot(ticket, 250L, true, false);

        // 3. 验证 AIMD 调谐逻辑
        AIMDConcurrencyGovernor governor = coordinator.getGovernor();
        assertEquals(4, governor.getCurrentLimit());

        // 模拟正常低延迟流量 -> 触发加性增
        MeshBackpressureTelemetry goodTelemetry = new MeshBackpressureTelemetry(
                System.currentTimeMillis(), 2, 0, 800.0, 0.0, 0, 4
        );
        Thread.sleep(1050); // 跨越 1000ms 调谐窗口
        int limitAfterGood = governor.adjustOnFeedback(goodTelemetry);
        assertEquals(5, limitAfterGood, "低延迟良好流量应触发加性增 +1");

        // 模拟遭遇 429 限流 -> 触发乘性减
        MeshBackpressureTelemetry throttleTelemetry = new MeshBackpressureTelemetry(
                System.currentTimeMillis(), 4, 3, 2500.0, 0.1, 2, 5
        );
        int limitAfterThrottle = governor.adjustOnFeedback(throttleTelemetry);
        assertTrue(limitAfterThrottle <= 3, "遭遇 429 限流必须触发乘性减至 <= 3");
    }

    @Test
    @DisplayName("支柱二契约测试: 可插拔检查点存储 SPI、Fencing Token 写屏障与冷备环形缓冲区")
    void testStateCheckpointStorageSpiFencingAndColdRingBuffer() {
        InMemoryRingBufferCheckpointStorage storage = new InMemoryRingBufferCheckpointStorage(16);

        byte[] hmac = "checkpoint_hmac_sha256_mock".getBytes(StandardCharsets.UTF_8);
        StateCheckpointRecord recordV1 = new StateCheckpointRecord(
                "run_2001", "flow_finance", 1, "{\"step1\":\"ok\"}", "{\"balance\":100}",
                "RUNNING", "[]", null, 0L,
                100L, 1, System.currentTimeMillis(), System.currentTimeMillis(), hmac
        );

        // 1. 成功写入检查点
        boolean savedV1 = storage.saveCheckpointWithFencing(recordV1, 100L);
        assertTrue(savedV1, "初始检查点应当保存成功");

        // 2. 拦截陈旧 Fencing Token 写入 (试图以 token=99 覆写已有的 token=100)
        StateCheckpointRecord staleRecord = new StateCheckpointRecord(
                "run_2001", "flow_finance", 1, "{\"step1\":\"stale\"}", "{\"balance\":90}",
                "RUNNING", "[]", "worker_stale", System.currentTimeMillis() + 60000L,
                99L, 2, System.currentTimeMillis(), System.currentTimeMillis(), hmac
        );
        boolean savedStale = storage.saveCheckpointWithFencing(staleRecord, 99L);
        assertFalse(savedStale, "单调递增写屏障必须拦截陈旧 Token 写入");

        // 3. 租约抢占与 CAS 验证 (worker_2 抢占未被占用的租约)
        LeaseAcquisitionReceipt leaseReceipt = storage.tryAcquireLease("run_2001", "worker_2", 30000L, 100L);
        assertTrue(leaseReceipt.acquired(), "持匹配 Fencing Token 应当成功抢占租约");
        assertEquals("worker_2", leaseReceipt.currentOwnerId());
        assertEquals(101L, leaseReceipt.fencingToken(), "成功抢占后 Fencing Token 应当原子自增");

        // 4. 验证在有效期内第三方 (worker_3) 无法抢占
        LeaseAcquisitionReceipt conflictReceipt = storage.tryAcquireLease("run_2001", "worker_3", 30000L, 101L);
        assertFalse(conflictReceipt.acquired(), "租约在有效期内应被保护，拦截第三方并发抢占");

        // 5. 软删除隔离转入冷备缓冲区 (防物理硬删除)
        boolean archived = storage.archiveCheckpointToColdBuffer("run_2001");
        assertTrue(archived, "应当成功软删除隔离并转入冷备缓冲区");
        Optional<StateCheckpointRecord> loaded = storage.loadCheckpoint("run_2001");
        assertTrue(loaded.isEmpty(), "主线读取应已隔离，防未定义污染");
    }

    @Test
    @DisplayName("支柱三契约测试: 千问 1536 维超球面单位向量、SIMD 角距离与 HNSW 量化图索引")
    void testHypersphericalHnswToolIndexAndCosineMetric() {
        int dim = HypersphericalCosineMetric.VECTOR_DIMENSION;
        assertEquals(1536, dim, "千问向量维度必须锁定为严格 1536 维");

        // 1. 构建两个单位超球面向量
        float[] vecA = new float[dim];
        float[] vecB = new float[dim];
        for (int i = 0; i < dim; i++) {
            vecA[i] = (float) Math.sin(i);
            vecB[i] = (float) Math.sin(i);
        }
        normalize(vecA);
        normalize(vecB);

        // 同一向量角距离应严格接近 0
        float distSame = HypersphericalCosineMetric.angularDistance(vecA, vecB);
        assertTrue(distSame < 1e-6f, "相同超球面单位向量角距离应为 0");

        // 2. SQ8 标量量化压缩与保真度验证
        byte[] sq8A = HypersphericalCosineMetric.quantizeSq8(vecA);
        assertEquals(1536, sq8A.length, "SQ8 量化字节大小应为 1536 (相比 float 节省 75% 内存)");
        float[] deqA = HypersphericalCosineMetric.dequantizeSq8(sq8A);
        float distDequant = HypersphericalCosineMetric.angularDistance(vecA, deqA);
        assertTrue(distDequant < 0.05f, "SQ8 解量化后的角距离扰动应严格 < 0.05");

        // 3. 超球面 HNSW 索引插入与 Top-K 近邻检索
        DefaultHypersphericalHnswToolIndex hnswIndex = new DefaultHypersphericalHnswToolIndex();
        hnswIndex.insert("tool_database_query", vecA, true);

        float[] vecC = new float[dim];
        for (int i = 0; i < dim; i++) vecC[i] = (float) Math.cos(i);
        normalize(vecC);
        hnswIndex.insert("tool_image_generate", vecC, true);

        assertEquals(2, hnswIndex.size(), "索引中应注册 2 个工具");

        List<HypersphericalHnswToolIndex.ScoredTool> results = hnswIndex.searchKnn(vecA, 1, 32);
        assertFalse(results.isEmpty(), "KNN 搜索应返回候选结果");
        assertEquals("tool_database_query", results.get(0).toolCode(), "与 vecA 最相似的工具应为 tool_database_query");
        assertTrue(results.get(0).cosineSimilarity() > 0.95f, "余弦相似度应当接近 1.0");
    }

    @Test
    @DisplayName("支柱四契约测试: 多智能体 2+1 轻量辩论、贝叶斯共识收敛与 DeepSeek 思考链仲裁")
    void testConsensusArbiterEngineBoundedDebateAndDeepSeekReasoning() throws Exception {
        DefaultConsensusArbiterEngine arbiter = new DefaultConsensusArbiterEngine();

        Map<String, String> conflictingOutputs = new HashMap<>();
        conflictingOutputs.put("worker_analyst", "根据季度财务审计数据计算，营业收入环比增长 14.8%，现金流充沛。");
        conflictingOutputs.put("worker_skeptic", "市场环境存在不确定性，收入可能平稳，需要进一步核验。");

        CompletableFuture<ConsensusArbitrationReceipt> future =
                arbiter.arbitrateDebateAsync("分析 Q3 营收走势", conflictingOutputs, 2);

        ConsensusArbitrationReceipt receipt = future.get();
        assertNotNull(receipt, "仲裁凭单不得为空");
        assertTrue(receipt.totalRounds() <= 2, "辩论轮次必须严格有界受限于 <= 2 轮");
        assertTrue(receipt.consensusScore() >= 0.70, "收敛共识度应 >= 0.70");
        assertEquals("worker_analyst", receipt.winningProposition().contains("14.8%") ? "worker_analyst" : "other");
        assertNotNull(receipt.deepSeekReasoningContent(), "必须包含 DeepSeek 思考链思维推理过程");
        assertNotNull(receipt.cryptographicSeal(), "必须附加 SHA-256 不可变存证封印");
    }

    @Test
    @DisplayName("支柱五契约测试: 前端交互式断点调试、maxAge=30 滑动窗口与时间旅行分叉重放")
    void testWorkflowTimeTravelDebuggerBreakpointsAndReplay() {
        DefaultWorkflowTimeTravelDebugger debugger = new DefaultWorkflowTimeTravelDebugger();

        // 1. 注册断点
        DebugBreakpointRecord bp = new DebugBreakpointRecord(
                "bp_1", "flow_payment", "node_risk_check", null, 2, true, System.currentTimeMillis()
        );
        debugger.registerBreakpoint(bp);

        // 验证断点前两次命中，第三次超出 limit
        assertTrue(debugger.shouldPauseAtNode("flow_payment", "node_risk_check", Map.of()), "第 1 次应触发断点");
        assertTrue(debugger.shouldPauseAtNode("flow_payment", "node_risk_check", Map.of()), "第 2 次应触发断点");
        assertFalse(debugger.shouldPauseAtNode("flow_payment", "node_risk_check", Map.of()), "第 3 次超出上限不应触发断点");

        // 2. 模拟记录 35 步执行快照 (验证 maxAge=30 滑动窗口内存防护)
        for (long s = 1; s <= 35; s++) {
            debugger.recordStepSnapshot(new TimeTravelStepRecord(
                    s, "run_3001", "node_" + s, "Node " + s, "COMPLETED",
                    Map.of("in", s), Map.of("out", s * 2), Map.of("ctx", s),
                    10L, System.currentTimeMillis()
            ));
        }

        List<TimeTravelStepRecord> history = debugger.getTimeTravelHistory("run_3001");
        assertEquals(30, history.size(), "滑动窗口必须严格保持最大 30 步，杜绝前端与后端内存爆炸");
        assertEquals(6L, history.get(0).stepSequence(), "最老存留步序应当为第 6 步 (1-5 步已滑出)");

        // 3. 时间旅行分叉重放 (从第 20 步分叉并注入热修改变量)
        Map<String, Object> hotInjectedVariables = Map.of("hot_fix_flag", true, "risk_override", "LOW");
        String forkedRuntimeId = debugger.forkAndReplay("run_3001", 20L, hotInjectedVariables);

        assertNotNull(forkedRuntimeId);
        assertTrue(forkedRuntimeId.contains("run_3001_fork_"), "新分叉运行时 ID 应包含原运行时和 fork 标识");

        List<TimeTravelStepRecord> forkedHistory = debugger.getTimeTravelHistory(forkedRuntimeId);
        assertFalse(forkedHistory.isEmpty(), "分叉分支应当具备继承的历史版本链");
        TimeTravelStepRecord lastForkedStep = forkedHistory.get(forkedHistory.size() - 1);
        assertEquals(20L, lastForkedStep.stepSequence(), "分叉末端步序必须为指定的第 20 步");
        assertEquals(true, lastForkedStep.fullContextSnapshot().get("hot_fix_flag"), "分叉末端必须包含热注入的修正变量");
    }

    private void normalize(float[] v) {
        float norm = 0.0f;
        for (float val : v) {
            norm += val * val;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-12f) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= norm;
            }
        }
    }
}
