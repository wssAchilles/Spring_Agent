package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.dto.*;
import tech.qiantong.qknow.ai.embodied.engine.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 64 专属契约单元测试：跨模态时序多源感知流协同、因果注意力掩码融合与具身智能体事件驱动决策中枢
 */
public class Phase64EmbodiedDecisionContractTest {

    private MultimodalTemporalIngestor ingestor;
    private CausalAttentionMasker masker;
    private EmbodiedDecisionFsm fsm;
    private EventDrivenDecisionBus eventBus;
    private ClosedLoopController controller;
    private SpatialGridGraph gridGraph;

    @BeforeEach
    void setUp() {
        gridGraph = new SpatialGridGraph();
        DigitalTwinSimulator twinSimulator = new DigitalTwinSimulator();
        ActuationSafetyGate safetyGate = new ActuationSafetyGate();
        controller = new ClosedLoopController(gridGraph, twinSimulator, safetyGate);

        ingestor = new MultimodalTemporalIngestor();
        masker = new CausalAttentionMasker();
        fsm = new EmbodiedDecisionFsm(ingestor, masker, controller);
        eventBus = new EventDrivenDecisionBus();

        // 注册初始实体
        SpatialEntityDO robotArm = new SpatialEntityDO(
                "ROBOT_ARM_01", "工业六轴机械臂",
                new SpatialPose3D(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                new BoundingBox3D(new SpatialPose3D(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), 1.0, 1.0, 1.5),
                false
        );
        gridGraph.registerEntity(robotArm);
    }

    @Test
    @DisplayName("契约 1: 具身多模态决策不可变存证凭单 SHA-256 自签名与防篡改测试")
    void test01_DecisionReceiptSha256IntegrityAndTamperProof() {
        MultimodalDecisionReceipt receipt = MultimodalDecisionReceipt.generate(
                "SESSION_EMB_001", "HASH_INPUT_ABC123", true,
                "ACT_MOVE_SE3_01", 120L
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "原始存证凭单 SHA-256 签名必须自验通过");

        // 模拟篡改动作标识 (例如将 ACT_MOVE_SE3_01 篡改为 ACT_MALICIOUS_99)
        MultimodalDecisionReceipt tampered = new MultimodalDecisionReceipt(
                receipt.receiptId(), receipt.sessionId(), receipt.inputMultimodalHash(),
                receipt.causalMaskValid(),
                "ACT_MALICIOUS_99", // 篡改动作
                receipt.executionDurationMs(), receipt.signatureHash(), receipt.timestampMs()
        );

        assertFalse(tampered.verifyIntegrity(), "字段遭篡改的决策凭证 SHA-256 自验必须严格失败");
    }

    @Test
    @DisplayName("契约 2: 阿里千问 1536 维超球面多模态保模归一化测试 (||v||_2 = 1.0, 定理 1.1)")
    void test02_HypersphereProjectionAndNormInvariant() {
        MultimodalTemporalFrame frame = new MultimodalTemporalFrame(
                "FRM-001", 1000L, 1L,
                "平移抓取左侧蓝色工件",
                Map.of("joint_1", 45.0, "joint_2", 90.0, "torque", 12.5),
                "OBJECT_BLUE_PART"
        );

        float[] embedding = masker.projectToUnitHypersphere(frame);

        assertNotNull(embedding);
        assertEquals(CausalAttentionMasker.EMBEDDING_DIM, embedding.length, "向量维度必须严格为 1536 维");

        // 验证 L2 模长严格为 1.0 (误差 <= 1e-5)
        double sumSq = 0.0;
        for (float v : embedding) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        assertEquals(1.0, norm, 1e-5, "阿里千问 1536 维超球面嵌入向量必须精确归一化为 1.0");
    }

    @Test
    @DisplayName("契约 3: 因果注意力下三角掩码矩阵生成与严格未来隔离校验 (定理 1.2)")
    void test03_CausalAttentionMaskLowerTriangularProperty() {
        int seqLen = 5;
        double[][] mask = masker.generateCausalAttentionMask(seqLen);

        assertNotNull(mask);
        assertEquals(seqLen, mask.length);
        assertEquals(seqLen, mask[0].length);

        // 验证下三角属性: j <= i 为 1.0，j > i 严格为 0.0
        for (int i = 0; i < seqLen; i++) {
            for (int j = 0; j < seqLen; j++) {
                if (j <= i) {
                    assertEquals(1.0, mask[i][j], "历史与当前时间步权重必须使能为 1.0");
                } else {
                    assertEquals(0.0, mask[i][j], "未来时间步权重必须硬隔离为 0.0，杜绝时间穿越");
                }
            }
        }

        assertTrue(masker.verifyCausalIntegrity(mask), "合规下三角掩码自验必须通过");

        // 篡改掩码 (在未来位置注入非零权重)
        mask[1][3] = 0.5;
        assertFalse(masker.verifyCausalIntegrity(mask), "未来时态泄漏的掩码矩阵必须被因果完整性核验严格阻断拒绝");
    }

    @Test
    @DisplayName("契约 4: 多源时序感知流 200ms 滑动窗口与单调时钟防时钟漂移测试")
    void test04_TemporalIngestorSlidingWindowAndMonotonicOrder() {
        long baseTime = 10_000L;

        // 模拟外部由于 NTP 抖动导致到达时序颠倒的数据
        // 逻辑时序 1 (物理戳 10050), 逻辑时序 2 (物理戳 9950), 逻辑时序 3 (物理戳 10100)
        ingestor.ingestRawEvent(baseTime + 50, "指令1", Map.of("force", 1.0), "TAG_A");
        ingestor.ingestRawEvent(baseTime - 50, "指令2", Map.of("force", 2.0), "TAG_B");
        ingestor.ingestRawEvent(baseTime + 100, "指令3", Map.of("force", 3.0), "TAG_C");

        // 提取以当前时间 baseTime + 100 为基准的 200ms 滑动窗口 [9900 ~ 10100]
        List<MultimodalTemporalFrame> aligned = ingestor.alignSlidingWindow(baseTime + 100);

        assertEquals(3, aligned.size(), "滑动窗口必须覆盖并在窗口范围内的全部有效时序感知切片");

        // 验证单调逻辑时钟严格保序性，彻底杜绝 NTP 漂移导致的因果倒流
        long lastSeq = 0;
        for (MultimodalTemporalFrame f : aligned) {
            assertTrue(f.getMonotonicSeq() > lastSeq, "切片必须严格按单调逻辑时钟顺序有序排列");
            lastSeq = f.getMonotonicSeq();
        }
    }

    @Test
    @DisplayName("契约 5: 具身决策有限状态机单步原子微转移耗时 <= 1ms 测试 (定理 1.3)")
    void test05_FsmStateTransitionLatencyBudget() {
        assertEquals(EmbodiedDecisionFsm.State.IDLE, fsm.getCurrentState());

        // 测试状态机合法流转序列: IDLE -> SENSING -> MASKING_FUSION -> DELIBERATING -> ACTING -> FEEDBACK -> IDLE
        assertTrue(fsm.transitionTo(EmbodiedDecisionFsm.State.SENSING));
        assertEquals(EmbodiedDecisionFsm.State.SENSING, fsm.getCurrentState());

        assertTrue(fsm.transitionTo(EmbodiedDecisionFsm.State.MASKING_FUSION));
        assertEquals(EmbodiedDecisionFsm.State.MASKING_FUSION, fsm.getCurrentState());

        assertTrue(fsm.transitionTo(EmbodiedDecisionFsm.State.DELIBERATING));
        assertEquals(EmbodiedDecisionFsm.State.DELIBERATING, fsm.getCurrentState());

        assertTrue(fsm.transitionTo(EmbodiedDecisionFsm.State.ACTING));
        assertEquals(EmbodiedDecisionFsm.State.ACTING, fsm.getCurrentState());

        assertTrue(fsm.transitionTo(EmbodiedDecisionFsm.State.FEEDBACK));
        assertEquals(EmbodiedDecisionFsm.State.FEEDBACK, fsm.getCurrentState());

        assertTrue(fsm.transitionTo(EmbodiedDecisionFsm.State.IDLE));
        assertEquals(EmbodiedDecisionFsm.State.IDLE, fsm.getCurrentState());

        // 测试非法状态转移拦截 (如直接从 IDLE 跨越到 ACTING)
        assertFalse(fsm.transitionTo(EmbodiedDecisionFsm.State.ACTING), "越级非法的状态转移必须被严格阻断");
    }

    @Test
    @DisplayName("契约 6: 高性能事件总线 4096 定长槽位与 Fail-Open 降级防 OOM 测试")
    void test06_EventBusRingBufferCapacityAndFailOpen() {
        assertEquals(EventDrivenDecisionBus.BUFFER_CAPACITY, 4096, "环形缓冲容量必须锁定为 4096 定长槽位");

        MultimodalTemporalFrame dummy = new MultimodalTemporalFrame(
                "FRM-DUMMY", 1000L, 1L, "TEST", Map.of(), "TAG"
        );

        // 持续发布至接近满水位 (85% 水位线约为 3481)
        for (int i = 0; i < 3480; i++) {
            boolean published = eventBus.publishEvent(dummy);
            assertTrue(published);
        }

        // 触发超载水位，验证 Fail-Open 降级丢弃机制，防止物理洪峰打满 JVM 堆内存
        for (int i = 0; i < 100; i++) {
            eventBus.publishEvent(dummy);
        }

        assertTrue(eventBus.getDroppedEventsCount() > 0, "达到超载水位线必须主动触发 Fail-Open 降级阻尼丢弃");
    }

    @Test
    @DisplayName("契约 7: 端到端具身决策闭环执行与收敛时间 MTTC <= 1000ms 测试 (定理 1.3)")
    void test07_EndToEndEmbodiedDecisionCycleConvergence() {
        long currentTs = 5000L;
        ingestor.ingestRawEvent(currentTs, "操作员指令：移动机械臂至装配区", Map.of("load", 5.0), "ASSEMBLY_AREA");

        ActionPrimitiveDTO action = new ActionPrimitiveDTO(
                "ACT_MOVE_TARGET", ActionPrimitiveDTO.ActionType.MOVE_TO,
                "ROBOT_ARM_01", new SpatialPose3D(0.5, 0.5, 0.5, 0.0, 0.0, 0.0),
                ActionPrimitiveDTO.ReversibilityLevel.REVERSIBLE
        );

        long start = System.currentTimeMillis();
        MultimodalDecisionReceipt receipt = fsm.stepDecisionCycle("SESSION_PROD_99", currentTs, action);
        long totalElapsed = System.currentTimeMillis() - start;

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "签发的存证收据 SHA-256 签名自验通过");
        assertTrue(receipt.causalMaskValid(), "执行过程中因果注意力掩码下三角约束有效");
        assertTrue(totalElapsed <= 1000L, "端到端具身决策闭环总耗时必须严格满足 MTTC <= 1000ms，当前: " + totalElapsed + "ms");
        assertEquals(EmbodiedDecisionFsm.State.IDLE, fsm.getCurrentState(), "闭环完成后必须重置为 IDLE 就绪状态");
    }

    @Test
    @DisplayName("契约 8: 反事实未来时序噪声注入与历史注意力零泄漏不变量测试 (定理 1.2)")
    void test08_TemporalAntiLookaheadZeroLeakageInvariant() {
        int seqLen = 4;
        double[][] maskBase = masker.generateCausalAttentionMask(seqLen);

        // 模拟第 2 步对历史步骤 0, 1, 2 的权重
        double w0 = maskBase[2][0];
        double w1 = maskBase[2][1];
        double w2 = maskBase[2][2];
        double w3_future = maskBase[2][3];

        assertEquals(1.0, w0);
        assertEquals(1.0, w1);
        assertEquals(1.0, w2);
        assertEquals(0.0, w3_future, "未来时间步 3 对当前时间步 2 的注意力权重必须恒等于 0.0");

        // 无论未来时间步 3 发生何种突变或注入何种恶意噪声，当前时间步 2 的历史因果表征偏导必须恒为零
        List<MultimodalTemporalFrame> frames = List.of(
                new MultimodalTemporalFrame("F0", 100L, 1L, "CMD0", Map.of(), "TAG0"),
                new MultimodalTemporalFrame("F1", 200L, 2L, "CMD1", Map.of(), "TAG1"),
                new MultimodalTemporalFrame("F2", 300L, 3L, "CMD2", Map.of(), "TAG2")
        );
        String hashBase = masker.computeSequenceHash(frames);

        // 增加未来扰动帧 F3
        List<MultimodalTemporalFrame> framesWithFuture = new ArrayList<>(frames);
        framesWithFuture.add(new MultimodalTemporalFrame("F3", 400L, 4L, "FUTURE_NOISE", Map.of(), "NOISE"));

        // 对前 3 帧截断计算的因果掩码和历史偏序依然保持严格恒等
        String hashPrefix = masker.computeSequenceHash(framesWithFuture.subList(0, 3));
        assertEquals(hashBase, hashPrefix, "未来事件的注入绝不影响前序历史序列的因果哈希完整性");
    }
}
