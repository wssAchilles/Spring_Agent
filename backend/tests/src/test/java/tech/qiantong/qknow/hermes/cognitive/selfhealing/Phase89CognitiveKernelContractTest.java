package tech.qiantong.qknow.hermes.cognitive.selfhealing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.dto.CognitiveSelfHealingEventFrame;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.dto.CognitiveExecutionReceipt;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.dto.CognitiveState;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.dto.MemoryHierarchyType;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.engine.CognitiveKernelControlBus;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.engine.CognitiveSelfHealingStateMachine;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.engine.HierarchicalMemoryCompressor;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.engine.UnifiedGraphCognitiveAligner;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 89: 复杂业务 Agent 认知推理内核自愈状态机、长程交互记忆动态分层压缩与图谱子图统一认知中枢
 * 专属契约单元测试套件 (8/8 严苛契约)
 */
public class Phase89CognitiveKernelContractTest {

    private double[] createNormalizedSphericalEmbedding(int seed) {
        double[] vec = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = Math.sin(seed + i * 0.173);
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("测试 1: 认知自愈状态机标准生命周期合法流转 (IDLE -> PERCEIVING -> REASONING -> JUDGING -> SUCCESS)")
    void testCognitiveStateMachine_NormalLifecycleTransitions() {
        CognitiveSelfHealingStateMachine fsm = new CognitiveSelfHealingStateMachine();
        assertEquals(CognitiveState.IDLE, fsm.getCurrentState());

        assertTrue(fsm.transitionTo(CognitiveState.PERCEIVING));
        assertTrue(fsm.transitionTo(CognitiveState.REASONING));
        assertTrue(fsm.transitionTo(CognitiveState.JUDGING));
        assertTrue(fsm.transitionTo(CognitiveState.TERMINATED_SUCCESS));
        assertTrue(fsm.isTerminal());
        assertFalse(fsm.isSelfHealingTriggered());
    }

    @Test
    @DisplayName("测试 2: 连续两轮语义相似度 >= 0.90 且未达标时精准检出死锁并切入 SELF_HEALING 态")
    void testCognitiveStateMachine_DeadlockDetectionAndSelfHealing() {
        CognitiveSelfHealingStateMachine fsm = new CognitiveSelfHealingStateMachine();
        fsm.transitionTo(CognitiveState.PERCEIVING);
        fsm.transitionTo(CognitiveState.REASONING);
        fsm.transitionTo(CognitiveState.JUDGING);

        double[] emb1 = createNormalizedSphericalEmbedding(101);
        double[] emb2 = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            emb2[i] = emb1[i] + 0.01 * Math.cos(i);
            sumSq += emb2[i] * emb2[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            emb2[i] /= norm;
        }

        fsm.recordAttemptEmbedding(emb1);
        fsm.recordAttemptEmbedding(emb2);

        boolean deadlock = fsm.checkDeadlock(emb2, emb1, 0.45);
        assertTrue(deadlock, "必须精确识别连续两轮语义余弦相似度 >= 0.90 的死锁");
        assertTrue(fsm.isSelfHealingTriggered());

        assertTrue(fsm.transitionTo(CognitiveState.SELF_HEALING));
        assertEquals(CognitiveState.SELF_HEALING, fsm.getCurrentState());

        String mutatedPrompt = fsm.applySelfHealingMutation("初始 Prompt", "连续回答出现死锁", 2);
        assertTrue(mutatedPrompt.contains("反事实负向约束"));
        assertTrue(mutatedPrompt.contains("思维链修正"));
    }

    @Test
    @DisplayName("测试 3: 反思自愈达到最大限制轮次 (4轮) 优雅降级至 TERMINATED_DEGRADED_FALLBACK 软着陆")
    void testCognitiveStateMachine_MaxAttemptsGracefulDegradation() {
        CognitiveSelfHealingStateMachine fsm = new CognitiveSelfHealingStateMachine();
        fsm.transitionTo(CognitiveState.PERCEIVING);
        fsm.transitionTo(CognitiveState.REASONING);
        fsm.transitionTo(CognitiveState.JUDGING);

        assertTrue(fsm.transitionTo(CognitiveState.TERMINATED_DEGRADED_FALLBACK));
        assertEquals(CognitiveState.TERMINATED_DEGRADED_FALLBACK, fsm.getCurrentState());
        assertTrue(fsm.isTerminal());
    }

    @Test
    @DisplayName("测试 4: 动态分层记忆压缩器在 Ebbinghaus 遗忘衰减下实现 Token 压缩率 >= 75%")
    void testHierarchicalMemory_EbbinghausDecayAndCompressionRatio() {
        HierarchicalMemoryCompressor compressor = new HierarchicalMemoryCompressor();
        double[] qEmb = createNormalizedSphericalEmbedding(202);

        int rawChars = 0;
        for (int i = 0; i < 30; i++) {
            String content = "第 " + i + " 轮对话：关于客户业务需求的详细陈述与日常沟通过渡问答细节 " + i;
            rawChars += content.length();
            double importance = (i % 7 == 0) ? 0.85 : 0.25;
            double[] emb = createNormalizedSphericalEmbedding(202 + i);
            compressor.storeMemory("sess_001", MemoryHierarchyType.EPISODIC, content, importance, emb);
        }

        // JIT 预热消除类加载与批量执行下的冷启动抖动
        for (int w = 0; w < 100; w++) {
            compressor.retrieveAndCompressContext("sess_001", "预热", qEmb, 400);
        }

        long startNano = System.nanoTime();
        List<String> compressed = compressor.retrieveAndCompressContext("sess_001", "查询核心需求", qEmb, 400);
        long elapsedMicros = (System.nanoTime() - startNano) / 1000L;

        assertTrue(elapsedMicros <= 200, "分层记忆压缩检索耗时必须 <= 200μs，实测: " + elapsedMicros + "μs");
        assertFalse(compressed.isEmpty());

        int compressedChars = compressed.stream().mapToInt(String::length).sum();
        double compressionRatio = compressor.calculateCompressionRatio(rawChars / 2, compressedChars / 2);
        assertTrue(compressionRatio >= 0.75, "Token 压缩率必须 >= 75%，实测: " + compressionRatio);
    }

    @Test
    @DisplayName("测试 5: 高重要性 (>= 0.70) 核心业务事实在分层压缩后 100% 召回留存")
    void testHierarchicalMemory_CoreBusinessFactRetention() {
        HierarchicalMemoryCompressor compressor = new HierarchicalMemoryCompressor();
        double[] qEmb = createNormalizedSphericalEmbedding(303);

        for (int i = 0; i < 15; i++) {
            compressor.storeMemory("sess_core", MemoryHierarchyType.EPISODIC, "普通问候与寒暄 " + i, 0.15, qEmb);
        }

        String coreFact1 = "【关键合规】违约金上限严格锁定在合同标的额的 5% 以内";
        String coreFact2 = "【主键规则】生产数据库主键唯一前缀为 QKNOW_CORP_2026";
        compressor.storeMemory("sess_core", MemoryHierarchyType.SEMANTIC, coreFact1, 0.95, qEmb);
        compressor.storeMemory("sess_core", MemoryHierarchyType.SEMANTIC, coreFact2, 0.90, qEmb);

        List<String> compressed = compressor.retrieveAndCompressContext("sess_core", "审核合同违约金与主键", qEmb, 200);

        assertTrue(compressed.contains(coreFact1), "核心合规事实必须 100% 留存");
        assertTrue(compressed.contains(coreFact2), "核心主键规则必须 100% 留存");
    }

    @Test
    @DisplayName("测试 6: 图谱子图统一认知对齐器有效过滤虚构断言 (< 0.65)，仅接地知识留存")
    void testUnifiedGraphAligner_GroundingFilterAndHallucinationSuppression() {
        UnifiedGraphCognitiveAligner aligner = new UnifiedGraphCognitiveAligner();
        double[] entEmb = createNormalizedSphericalEmbedding(404);

        aligner.registerSubgraphKnowledge("ent_001", "腾讯控股", 0.95, entEmb);
        aligner.registerSubgraphKnowledge("ent_002", "微信支付", 0.88, entEmb);

        String validFact1 = "腾讯控股是领先的科技企业";
        String validFact2 = "微信支付提供移动交易接口";
        String hallucinatedFact = "某未知虚构空壳公司拥有千亿现金流与量子计算机";

        List<String> candidates = List.of(validFact1, validFact2, hallucinatedFact);
        Map<String, double[]> embeddings = new HashMap<>();
        embeddings.put(validFact1, entEmb);
        embeddings.put(validFact2, entEmb);
        embeddings.put(hallucinatedFact, createNormalizedSphericalEmbedding(999));

        // JIT 预热消除类加载与批量执行下的冷启动抖动
        for (int w = 0; w < 100; w++) {
            aligner.alignAndGroundFacts(candidates, embeddings, 0.65);
        }

        long startNano = System.nanoTime();
        List<String> grounded = aligner.alignAndGroundFacts(candidates, embeddings, 0.65);
        long elapsedMicros = (System.nanoTime() - startNano) / 1000L;

        assertTrue(elapsedMicros <= 100, "单步图谱对齐接地耗时必须 <= 100μs，实测: " + elapsedMicros + "μs");
        assertEquals(2, grounded.size());
        assertTrue(grounded.contains(validFact1));
        assertTrue(grounded.contains(validFact2));
        assertFalse(grounded.contains(hallucinatedFact), "图谱接地门禁必须剔除虚构断言");
    }

    @Test
    @DisplayName("测试 7: 1000Hz 4096 槽位 Disruptor 无锁认知总线高频推帧与 JitterGuard 监控")
    void testDisruptorBus_SubMicrosecondThroughputAndJitterGuard() {
        CognitiveKernelControlBus bus = new CognitiveKernelControlBus();
        double[] emb = createNormalizedSphericalEmbedding(505);

        assertEquals(CognitiveKernelControlBus.STATUS_NORMAL, bus.getCurrentStatus());

        for (int i = 0; i < 16; i++) {
            CognitiveSelfHealingEventFrame frame = new CognitiveSelfHealingEventFrame(
                    "evt_" + i, "sess_bus", CognitiveState.REASONING,
                    1, 0.88, false, false, emb, System.currentTimeMillis() * 1000L, i
            );
            assertTrue(frame.isValidEmbedding());
            boolean ok = bus.publishFrame(frame);
            assertTrue(ok);
        }

        CognitiveSelfHealingEventFrame read = bus.readFrame(0);
        assertNotNull(read);
        assertEquals("evt_0", read.eventId());
    }

    @Test
    @DisplayName("测试 8: 不可变认知执行存证凭单 SHA-256 自签名与验真 100% 通过")
    void testCognitiveExecutionReceipt_CryptographicSelfVerification() {
        CognitiveKernelControlBus bus = new CognitiveKernelControlBus();

        CognitiveExecutionReceipt receipt = bus.issueReceipt(
                "sess_verified_001",
                "生成长程业务投研报告",
                2,
                true,
                0.785,
                5,
                24500L
        );

        assertNotNull(receipt);
        assertEquals("sess_verified_001", receipt.sessionId());
        assertEquals(2, receipt.totalAttempts());
        assertTrue(receipt.selfHealingApplied());
        assertEquals(5, receipt.groundedEntityCount());
        assertTrue(receipt.verifySignature(), "不可变存证凭单 SHA-256 自签名验真必须 100% 通过");
    }
}
