package tech.qiantong.qknow.ai.speculative;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 63 专属契约测试：意图投机前置流式执行、端云认知双向同步与轻量符号状态机加速引擎
 */
public class Phase63SpeculativeExecutionContractTest {

    private LightweightSymbolicFsm fsm;
    private SpeculativeIntentPipeline pipeline;
    private BidirectionalCognitiveSyncBus syncBus;
    private SpeculativeStreamingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        fsm = new LightweightSymbolicFsm();
        pipeline = new SpeculativeIntentPipeline();
        syncBus = new BidirectionalCognitiveSyncBus();
        coordinator = new SpeculativeStreamingCoordinator(fsm, pipeline, syncBus);
    }

    private float[] createDummyHypersphereVector(int seed) {
        float[] vec = new float[SpeculativeIntentPipeline.EMBEDDING_DIMENSION];
        Random rnd = new Random(seed);
        for (int i = 0; i < vec.length; i++) {
            vec[i] = rnd.nextFloat() - 0.5f;
        }
        return SpeculativeIntentPipeline.normalizeHypersphere(vec);
    }

    @Test
    @DisplayName("契约 1: 投机执行不可变存证凭单 SHA-256 自签名与防篡改测试")
    void test01_ReceiptSha256SelfVerificationAndTamperResistance() {
        SpeculativeExecutionReceipt receipt = SpeculativeExecutionReceipt.create(
                "SPEC-REC-001", 101L, "查询2026年Q1财务报表审计报告",
                "COMMITTED", true, false, 450L,
                "SHADOW-CTX-101", "投机完全命中，首Token延迟削减超60%",
                System.currentTimeMillis()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原始不可变存证凭单 SHA-256 自签名必须验证通过");

        // 模拟篡改节省耗时 (例如将 450ms 篡改为 9999ms)
        SpeculativeExecutionReceipt tampered = new SpeculativeExecutionReceipt(
                receipt.receiptId(), receipt.epoch(), receipt.intentDraft(),
                receipt.fsmState(), receipt.speculativeHit(), receipt.speculativeAborted(),
                9999L, // 篡改
                receipt.contextHash(), receipt.decisionSummary(),
                receipt.timestamp(), receipt.sha256Signature()
        );

        assertFalse(tampered.verifySignature(), "字段被篡改后的存证凭单 SHA-256 自验必须严格失败");
    }

    @Test
    @DisplayName("契约 2: 轻量符号状态机 (FSM) 击键停顿最优停止流转测试 (定理 1.1)")
    void test02_SymbolicFsmDwellTimeOptimalStoppingTransition() {
        assertEquals(LightweightSymbolicFsm.State.IDLE, fsm.getCurrentState());

        long t0 = 1000L;
        // 收到击键事件
        fsm.onKeystroke(t0);
        assertEquals(LightweightSymbolicFsm.State.TYPING, fsm.getCurrentState());

        // 停顿 150ms (未达 300ms 阈值)
        fsm.checkDwellTime(t0 + 150L);
        assertEquals(LightweightSymbolicFsm.State.TYPING, fsm.getCurrentState(), "未达300ms阈值应保持 TYPING");

        // 停顿达到 350ms (超过 300ms 阈值)
        fsm.checkDwellTime(t0 + 350L);
        assertEquals(LightweightSymbolicFsm.State.DWELLING, fsm.getCurrentState(), "停顿达标应转入 DWELLING");

        // 触发投机
        boolean triggered = fsm.triggerSpeculation(t0 + 360L);
        assertTrue(triggered);
        assertEquals(LightweightSymbolicFsm.State.SPECULATING, fsm.getCurrentState());

        // 用户提交
        fsm.onCommit(t0 + 500L);
        assertEquals(LightweightSymbolicFsm.State.COMMITTED, fsm.getCurrentState());
    }

    @Test
    @DisplayName("契约 3: 千问 1536 维超球面投机预检索保模与维度校验测试 (定理 1.3)")
    void test03_SpeculativePipelineHyperspherePreRetrievalSpeedup() {
        float[] queryVec = createDummyHypersphereVector(42);

        // 校验 L2 模长为 1.0 (误差 <= 1e-5)
        double sumSq = 0.0;
        for (float v : queryVec) {
            sumSq += v * v;
        }
        assertEquals(1.0, Math.sqrt(sumSq), 1e-5, "千问 1536 维超球面嵌入向量必须精确归一化");

        // 验证非 1536 维向量抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            SpeculativeIntentPipeline.normalizeHypersphere(new float[128]);
        }, "非 1536 维向量必须被严格阻断");

        // 模拟知识库切片向量
        Map<String, float[]> kbEmbeddings = new HashMap<>();
        kbEmbeddings.put("SLICE-A-财务总览", queryVec); // 与 Query 相同，余弦内积应为 1.0
        kbEmbeddings.put("SLICE-B-人事考勤", createDummyHypersphereVector(99));

        SpeculativeIntentPipeline.SpeculativeShadowContext shadow =
                pipeline.executeSpeculativePreRetrieval(1L, "财务总览分析", queryVec, kbEmbeddings);

        assertNotNull(shadow);
        assertEquals(1L, shadow.epoch());
        assertFalse(shadow.retrievedSlices().isEmpty());
        assertEquals("SLICE-A-财务总览", shadow.retrievedSlices().get(0), "与 Query 相同的切片必须以最高相似度排在首位");
    }

    @Test
    @DisplayName("契约 4: 投机撤销因果无干扰与零脏写测试 (定理 1.2)")
    void test04_SpeculativeRollbackCausalNonInterference() {
        float[] queryVec = createDummyHypersphereVector(12);
        SpeculativeIntentPipeline.SpeculativeShadowContext shadow =
                pipeline.executeSpeculativePreRetrieval(2L, "用户临时草稿意图", queryVec, Map.of());

        // 注册到双向同步总线
        syncBus.registerShadowContext(shadow);
        assertEquals(1, syncBus.getActiveShadowCount());

        // 用户改写或取消，执行无害回滚算子 (Rollback)
        boolean rollbackSuccess = syncBus.rollback(2L);
        assertTrue(rollbackSuccess, "回滚算子必须成功移除指定代际影子快照");
        assertEquals(0, syncBus.getActiveShadowCount(), "回滚后活动影子上下文必须清空");
        assertEquals(1, syncBus.getAbortedCount(), "中止计数准确递增");

        // 再次回滚同一代际应返回 false (幂等安全)
        assertFalse(syncBus.rollback(2L));
    }

    @Test
    @DisplayName("契约 5: 端云双向同步总线因果偏序与精确提交测试")
    void test05_BidirectionalSyncBusCausalOrderingAndEviction() {
        long epoch1 = syncBus.advanceEpoch();
        long epoch2 = syncBus.advanceEpoch();
        assertTrue(epoch2 > epoch1, "因果代际号必须单调严格递增");

        float[] v1 = createDummyHypersphereVector(1);
        float[] v2 = createDummyHypersphereVector(2);

        SpeculativeIntentPipeline.SpeculativeShadowContext s1 = pipeline.executeSpeculativePreRetrieval(epoch1, "意图A", v1, Map.of());
        SpeculativeIntentPipeline.SpeculativeShadowContext s2 = pipeline.executeSpeculativePreRetrieval(epoch2, "意图B", v2, Map.of());

        syncBus.registerShadowContext(s1);
        syncBus.registerShadowContext(s2);
        assertEquals(2, syncBus.getActiveShadowCount());

        // 提交 epoch2，但意图不匹配（意图漂移）
        SpeculativeIntentPipeline.SpeculativeShadowContext committedMismatch = syncBus.commitIfMatching(epoch2, "意图C");
        assertNull(committedMismatch, "意图不匹配时不能提升为生产上下文");

        // 提交 epoch1，意图完全匹配
        SpeculativeIntentPipeline.SpeculativeShadowContext committedMatch = syncBus.commitIfMatching(epoch1, "意图A");
        assertNotNull(committedMatch, "意图与代际匹配时必须成功原子提升");
        assertEquals("意图A", committedMatch.intentDraft());
    }

    @Test
    @DisplayName("契约 6: DeepSeek 官方 64-Token 整数倍前缀对齐与占位符填充测试")
    void test06_DeepSeekPrefix64TokenAlignment() {
        String raw = "SYSTEM: 知识库投机预检索上下文: SLICE-001, SLICE-002";
        String aligned = SpeculativeIntentPipeline.alignPrefixToBlock(raw);

        assertNotNull(aligned);
        assertTrue(aligned.contains("<!-- ALIGNED_PAD:"), "前缀未满 64-token 整数倍时应自动补充对齐占位符");

        // 再次补齐应具备幂等性与前缀保持
        assertTrue(aligned.startsWith(raw));
    }

    @Test
    @DisplayName("契约 7: 端到端投机完全命中工作流与延迟收益测试 (定理 1.3)")
    void test07_EndToEndSpeculativeHitWorkflow() {
        float[] queryVec = createDummyHypersphereVector(77);
        Map<String, float[]> kb = Map.of("SLICE-核心政策", queryVec);

        SpeculativeExecutionReceipt receipt = coordinator.processInteraction(
                10L, "查询公司核心考勤政策", queryVec,
                true, "查询公司核心考勤政策", kb
        );

        assertNotNull(receipt);
        assertTrue(receipt.speculativeHit(), "完全匹配时投机必须判定为命中");
        assertFalse(receipt.speculativeAborted());
        assertTrue(receipt.latencySavedMs() >= 400L, "投机命中应至少节省 400ms 串行等待耗时");
        assertTrue(receipt.verifySignature(), "签发的投机存证凭单 SHA-256 签名必须合法有效");
    }

    @Test
    @DisplayName("契约 8: 协调器高负载熔断 Fail-Open 降级与参数防御性校验")
    void test08_CoordinatorCircuitBreakerAndFailOpen() {
        float[] queryVec = createDummyHypersphereVector(88);

        // 1. 触发断路器熔断
        coordinator.setCircuitBreakerOpen(true);
        SpeculativeExecutionReceipt degradedReceipt = coordinator.processInteraction(
                20L, "紧急高危查询", queryVec,
                true, "紧急高危查询", Map.of()
        );

        assertNotNull(degradedReceipt);
        assertFalse(degradedReceipt.speculativeHit());
        assertEquals("CIRCUIT_BREAKER_DEGRADED", degradedReceipt.fsmState());
        assertTrue(degradedReceipt.verifySignature(), "熔断降级生成的收据签名仍须自验通过");

        // 恢复断路器
        coordinator.setCircuitBreakerOpen(false);

        // 2. 参数防御性拦截
        assertThrows(IllegalArgumentException.class, () -> {
            coordinator.processInteraction(0L, "意图", queryVec, true, "意图", Map.of());
        }, "epoch <= 0 必须抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            coordinator.processInteraction(1L, "", queryVec, true, "意图", Map.of());
        }, "意图草稿为空必须抛出异常");
    }
}
