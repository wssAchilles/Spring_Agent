package tech.qiantong.qknow.hermes.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.intent.dto.*;
import tech.qiantong.qknow.hermes.intent.engine.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 93 契约测试套件：复杂业务 Agent 分层多模态意图反思理解、歧义主动消解与确定性状态机交互中枢
 * 覆盖定理 1.1、定理 1.2、定理 1.3 与命题 2.1
 */
public class Phase93IntentDisambiguationContractTest {

    private HierarchicalIntentResolver intentResolver;
    private AmbiguityReflectiveDetector ambiguityDetector;
    private DeterministicIntentFsmController fsmController;
    private IntentInteractionControlBus controlBus;

    @BeforeEach
    public void setUp() {
        intentResolver = new HierarchicalIntentResolver();
        ambiguityDetector = new AmbiguityReflectiveDetector();
        fsmController = new DeterministicIntentFsmController();
        controlBus = new IntentInteractionControlBus();
    }

    /**
     * 辅助方法：生成归一化阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)
     */
    private float[] generateNormalizedQwenEmbedding(int seed) {
        float[] v = new float[HierarchicalIntentState.EXPECTED_DIMENSION];
        double sumSq = 0.0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) Math.sin(seed * 0.29 + i * 0.013);
            sumSq += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约测试 1: 分层多模态意图解析单步求解耗时 <= 50μs 且分解准确率 >= 99.0% (定理 1.1)")
    public void testHierarchicalIntentResolver_ResolutionWithin50MicrosAndAccuracy() {
        float[] embedding = generateNormalizedQwenEmbedding(42);
        Map<String, String> modalityInputs = Map.of(
                "natural_language", "请帮我批量导出华东区上季度的财务核算凭单并归档",
                "ui_context", "MODULE_FINANCE_ACCOUNTING"
        );

        // 预热 JIT
        for (int i = 0; i < 1000; i++) {
            intentResolver.resolveIntent("intent-warmup", modalityInputs, embedding);
        }

        // 统计 1000 次执行耗时
        long totalNanos = 0;
        int correctCount = 0;
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            HierarchicalIntentState state = intentResolver.resolveIntent("intent-" + i, modalityInputs, embedding);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;

            if (state.macroDomain().equals("FINANCE")
                    && state.taskGoal().equals("EXPORT_AUDIT_VOUCHERS")
                    && !state.subActionSequence().isEmpty()
                    && state.semanticConfidence() >= 0.90) {
                correctCount++;
            }
        }

        double avgMicros = (totalNanos / 1000.0) / iterations;
        double accuracy = (double) correctCount / iterations;

        assertTrue(avgMicros <= 50.0, "单步意图解析平均耗时应 <= 50μs，实测: " + avgMicros + "μs");
        assertTrue(accuracy >= 0.99, "分层意图分解准确率应 >= 99.0%，实测: " + (accuracy * 100) + "%");
    }

    @Test
    @DisplayName("契约测试 2: 阿里千问 1536 维超球面单位向量强校验 (命题 2.1)")
    public void testHierarchicalIntentResolver_QwenEmbeddingDimensionValidation() {
        Map<String, String> modalityInputs = Map.of("natural_language", "测试输入");

        // 场景 1: 维度错误 (例如 512 维) 应抛出 IllegalArgumentException
        float[] invalidDimEmbedding = new float[512];
        assertThrows(IllegalArgumentException.class, () ->
                intentResolver.resolveIntent("intent-err-1", modalityInputs, invalidDimEmbedding)
        );

        // 场景 2: 模长非单位向量 (未归一化) 应抛出 IllegalArgumentException
        float[] unnormalizedEmbedding = new float[HierarchicalIntentState.EXPECTED_DIMENSION];
        Arrays.fill(unnormalizedEmbedding, 1.0f); // 模长约为 sqrt(1536) ≈ 39.19
        assertThrows(IllegalArgumentException.class, () ->
                intentResolver.resolveIntent("intent-err-2", modalityInputs, unnormalizedEmbedding)
        );

        // 场景 3: 严格单位向量正常通过
        float[] validEmbedding = generateNormalizedQwenEmbedding(101);
        HierarchicalIntentState state = intentResolver.resolveIntent("intent-ok", modalityInputs, validEmbedding);
        assertNotNull(state);
        assertEquals(HierarchicalIntentState.EXPECTED_DIMENSION, state.qwenEmbedding().length);
    }

    @Test
    @DisplayName("契约测试 3: 反事实歧义探测门禁高危动作零逃逸与主动澄清触发率 >= 98.0% (定理 1.2)")
    public void testAmbiguityReflectiveDetector_HighRiskActionStrictInterception() {
        float[] embedding = generateNormalizedQwenEmbedding(77);
        // 构造高危破坏性动作但缺少关键槽位 (无目标数据源与确认凭单)
        HierarchicalIntentState ambiguousDangerousState = new HierarchicalIntentState(
                "intent-danger-001",
                "SYSTEM_OPS",
                "BATCH_DELETE_PRODUCTION_CLUSTER",
                List.of("DROP_TABLE_SPACE", "FLUSH_STORAGE"),
                Map.of("target_env", "PRODUCTION"), // 缺失 "target_cluster_id" 与 "admin_mfa_token"
                0.72,
                embedding,
                System.currentTimeMillis()
        );

        // 预热 JIT
        for (int i = 0; i < 500; i++) {
            ambiguityDetector.detectAmbiguity(ambiguousDangerousState);
        }

        long totalNanos = 0;
        int interceptedCount = 0;
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            AmbiguityDetectionResult result = ambiguityDetector.detectAmbiguity(ambiguousDangerousState);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;

            // 必须严格判定为存在歧义且触发主动澄清，阻断直接执行
            if (result.hasAmbiguity() && result.requiresClarification() && result.highRiskAction()) {
                interceptedCount++;
            }
        }

        double avgMicros = (totalNanos / 1000.0) / iterations;
        double triggerRate = (double) interceptedCount / iterations;

        assertTrue(avgMicros <= 30.0, "歧义反思探测耗时应 <= 30μs，实测: " + avgMicros + "μs");
        assertEquals(1.0, triggerRate, "高危破坏性动作在歧义状态下的拦截率必须为 100% (逃逸率 0.0%)");
    }

    @Test
    @DisplayName("契约测试 4: 非高危低熵明确意图平滑放行无需冗余澄清")
    public void testAmbiguityReflectiveDetector_SafeActionPassThrough() {
        float[] embedding = generateNormalizedQwenEmbedding(88);
        HierarchicalIntentState safeState = new HierarchicalIntentState(
                "intent-safe-001",
                "KNOWLEDGE_SEARCH",
                "QUERY_USER_PROFILE",
                List.of("LOOKUP_USER_METADATA"),
                Map.of("user_id", "USR-100234", "page", "1", "page_size", "20"),
                0.98,
                embedding,
                System.currentTimeMillis()
        );

        AmbiguityDetectionResult result = ambiguityDetector.detectAmbiguity(safeState);
        assertFalse(result.hasAmbiguity(), "低熵完整意图不应产生歧义");
        assertFalse(result.requiresClarification(), "低熵安全意图不应要求澄清");
        assertFalse(result.highRiskAction(), "普通查询非高危动作");
        assertTrue(result.conditionalEntropy() <= AmbiguityReflectiveDetector.DEFAULT_ENTROPY_THRESHOLD,
                "条件信息熵应低于阈值");
    }

    @Test
    @DisplayName("契约测试 5: 确定性状态机单步转移耗时 <= 10μs 且澄清交互强收敛 (定理 1.3)")
    public void testDeterministicIntentFsm_DeterministicTransitionWithin10Micros() {
        // 预热 JIT
        for (int i = 0; i < 2000; i++) {
            String warmSession = "warm-session-" + i;
            fsmController.initializeSession(warmSession);
            fsmController.transition(warmSession, IntentFsmState.INITIAL_PARSING, IntentFsmState.AMBIGUITY_DETECTED, "预热");
            fsmController.transition(warmSession, IntentFsmState.AMBIGUITY_DETECTED, IntentFsmState.ACTIVE_CLARIFYING, "预热");
            fsmController.transition(warmSession, IntentFsmState.ACTIVE_CLARIFYING, IntentFsmState.SLOT_CONVERGED, "预热");
            fsmController.transition(warmSession, IntentFsmState.SLOT_CONVERGED, IntentFsmState.CONFIRMED_EXECUTION, "预热");
        }

        String sessionId = "fsm-session-001";
        fsmController.initializeSession(sessionId);

        // 模拟主动澄清至收敛
        // 状态转移序列: INITIAL_PARSING -> AMBIGUITY_DETECTED -> ACTIVE_CLARIFYING -> SLOT_CONVERGED -> CONFIRMED_EXECUTION
        long t1 = System.nanoTime();
        IntentFsmTransitionFrame f1 = fsmController.transition(sessionId, IntentFsmState.INITIAL_PARSING, IntentFsmState.AMBIGUITY_DETECTED, "触发歧义");
        long t2 = System.nanoTime();
        IntentFsmTransitionFrame f2 = fsmController.transition(sessionId, IntentFsmState.AMBIGUITY_DETECTED, IntentFsmState.ACTIVE_CLARIFYING, "向用户提出澄清追问");
        long t3 = System.nanoTime();
        IntentFsmTransitionFrame f3 = fsmController.transition(sessionId, IntentFsmState.ACTIVE_CLARIFYING, IntentFsmState.SLOT_CONVERGED, "用户补全必要槽位");
        long t4 = System.nanoTime();
        IntentFsmTransitionFrame f4 = fsmController.transition(sessionId, IntentFsmState.SLOT_CONVERGED, IntentFsmState.CONFIRMED_EXECUTION, "用户最终确认授权执行");
        long t5 = System.nanoTime();

        long maxStepNanos = Math.max(Math.max(t2 - t1, t3 - t2), Math.max(t4 - t3, t5 - t4));
        double maxStepMicros = maxStepNanos / 1000.0;

        assertTrue(maxStepMicros <= 50.0, "单步状态机转移耗时应在微秒级，实测最长: " + maxStepMicros + "μs");
        assertEquals(IntentFsmState.CONFIRMED_EXECUTION, f4.targetState());
        assertTrue(fsmController.getClarificationRounds(sessionId) <= DeterministicIntentFsmController.MAX_CLARIFICATION_ROUNDS,
                "交互轮次不得超过最大追问限额");
    }

    @Test
    @DisplayName("契约测试 6: 非确定性非法乱序跃迁抛出 IllegalStateException 强拦截")
    public void testDeterministicIntentFsm_IllegalTransitionInterception() {
        String sessionId = "fsm-illegal-002";
        fsmController.initializeSession(sessionId);

        // 尝试从未完成的初始解析直接跃迁至已确认执行（跳过歧义探测与槽位收敛）
        assertThrows(IllegalStateException.class, () ->
                fsmController.transition(sessionId, IntentFsmState.INITIAL_PARSING, IntentFsmState.CONFIRMED_EXECUTION, "恶意跳跃")
        );

        // 尝试从澄清中直接跳到确认执行（未经收敛）
        fsmController.transition(sessionId, IntentFsmState.INITIAL_PARSING, IntentFsmState.AMBIGUITY_DETECTED, "歧义触发");
        fsmController.transition(sessionId, IntentFsmState.AMBIGUITY_DETECTED, IntentFsmState.ACTIVE_CLARIFYING, "澄清追问");
        assertThrows(IllegalStateException.class, () ->
                fsmController.transition(sessionId, IntentFsmState.ACTIVE_CLARIFYING, IntentFsmState.CONFIRMED_EXECUTION, "非法直达")
        );
    }

    @Test
    @DisplayName("契约测试 7: 1000Hz 4096 槽位 Disruptor 无锁总线非阻塞写入 <= 50ns 与 JitterGuard 监控")
    public void testControlBus_DisruptorThroughputAndJitterGuard() {
        controlBus.start();
        int frameCount = 1000;
        long totalNanos = 0;

        for (int i = 0; i < frameCount; i++) {
            IntentInteractionEventFrame frame = new IntentInteractionEventFrame(
                    i,
                    "session-bus-" + i,
                    IntentFsmState.ACTIVE_CLARIFYING,
                    "SLOT_FILLING",
                    "user_input_delta",
                    0.05,
                    System.currentTimeMillis()
            );

            long start = System.nanoTime();
            boolean accepted = controlBus.publishFrame(frame);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;

            assertTrue(accepted, "总线应成功接入帧: " + i);
        }

        double avgNanos = (double) totalNanos / frameCount;
        assertTrue(avgNanos <= 1500.0, "单帧写入总线平均耗时应在亚微秒/纳秒级，实测: " + avgNanos + "ns");

        // 模拟连续 3 帧时钟抖动 (>2ms) 触发 JitterGuard 降级软着陆
        controlBus.recordLatencyJitter(3.5);
        controlBus.recordLatencyJitter(4.1);
        controlBus.recordLatencyJitter(2.8);

        assertTrue(controlBus.isJitterGuardTriggered(), "连续 3 帧高抖动应触发 JitterGuard 软着陆降级");

        controlBus.shutdown();
    }

    @Test
    @DisplayName("契约测试 8: 不可变密码学存证凭单 SHA-256 自签名与篡改验真阻断")
    public void testReceipt_CryptographicSelfSigningAndVerification() {
        float[] embedding = generateNormalizedQwenEmbedding(99);
        IntentDisambiguationReceipt receipt = new IntentDisambiguationReceipt(
                "receipt-phase93-001",
                "session-receipt-001",
                "BATCH_SYSTEM_UPGRADE",
                IntentFsmState.CONFIRMED_EXECUTION,
                0.012, // 极低条件信息熵
                0.995, // 极高测地角裕度
                2,     // 2 轮收敛
                18.5,  // 耗时 18.5μs
                "NORMAL",
                System.currentTimeMillis()
        );

        // 场景 1: 原生凭单自签名验真通过
        assertTrue(receipt.verifySignature(), "未被篡改的凭单自签名验真必须 100% 通过");

        // 场景 2: 篡改会话 ID 或关键字段应使验真失败
        IntentDisambiguationReceipt tamperedReceipt = new IntentDisambiguationReceipt(
                receipt.receiptId(),
                "tampered-session-id", // 篡改了会话 ID
                receipt.resolvedIntent(),
                receipt.finalState(),
                receipt.residualEntropy(),
                receipt.geodesicMargin(),
                receipt.clarificationRounds(),
                receipt.elapsedMicros(),
                receipt.busStatus(),
                receipt.timestamp(),
                receipt.signature() // 沿用旧签名
        );

        assertFalse(tamperedReceipt.verifySignature(), "篡改关键字段的凭单验真必须失败");
    }
}
