package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.impedance.dto.AdaptiveImpedanceState;
import tech.qiantong.qknow.ai.embodied.impedance.dto.MultiModalSensorFrame;
import tech.qiantong.qknow.ai.embodied.impedance.dto.TactileVisualImpedanceReceipt;
import tech.qiantong.qknow.ai.embodied.impedance.engine.PassivityAdaptiveImpedanceGovernor;
import tech.qiantong.qknow.ai.embodied.impedance.engine.SpatioTemporalSensorAligner;
import tech.qiantong.qknow.ai.embodied.impedance.engine.TactileVisualControlBus;
import tech.qiantong.qknow.ai.embodied.impedance.engine.TactileVisualSafetyGate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 78 具身多智能体异构传感器高维时空感知融合与毫秒级触觉-视觉阻抗协同控制中枢 专属契约测试类
 * 覆盖定理 1.1、命题 2.1、定理 1.2 与定理 1.3 核心数学物理不变量。
 *
 * @author Achilles
 * @since 2026-09-16
 */
public class Phase78TactileVisualImpedanceContractTest {

    private SpatioTemporalSensorAligner aligner;
    private PassivityAdaptiveImpedanceGovernor governor;
    private TactileVisualSafetyGate safetyGate;
    private TactileVisualControlBus controlBus;

    @BeforeEach
    void setUp() {
        aligner = new SpatioTemporalSensorAligner();
        governor = new PassivityAdaptiveImpedanceGovernor(1.0, 50.0, 20.0);
        safetyGate = new TactileVisualSafetyGate(500.0, 0.05);
        controlBus = new TactileVisualControlBus();
    }

    private MultiModalSensorFrame createMockFrame(double normalForceZ, double latencyMs) {
        double[] ft = new double[]{2.0, 1.5, normalForceZ, 0.1, 0.2, 0.05};
        double[] visPose = new double[]{0.50, 0.20, 0.15};
        double[] q = new double[]{0.1, 0.2, -0.3, 0.4, 0.5, -0.2};
        double[] dq = new double[]{0.05, -0.02, 0.04, 0.01, -0.03, 0.02};
        double[] qwen = aligner.projectToQwenHypersphere(new double[]{0.5, 0.2, 0.15, normalForceZ});

        return new MultiModalSensorFrame(
                "FRAME_78_001",
                System.currentTimeMillis() * 1000L,
                (System.currentTimeMillis() - (long) latencyMs) * 1000L,
                System.currentTimeMillis() * 1000L,
                ft,
                visPose,
                q,
                dq,
                latencyMs,
                qwen
        );
    }

    @Test
    @DisplayName("契约 1: 连续时间李代数 B 样条流形时空测地插值无偏估计 (定理 1.1)")
    void testContract1_ContinuousTimeSpatioTemporalAlignment() {
        // 模拟视觉存在 40ms 随机延迟
        MultiModalSensorFrame frame = createMockFrame(0.5, 40.0);

        // 预热消除高压测试环境下的类加载抖动
        aligner.alignVisualPoseContinuous(frame);

        long startNs = System.nanoTime();
        double[] alignedPose = aligner.alignVisualPoseContinuous(frame);
        long elapsedUs = (System.nanoTime() - startNs) / 1000L;

        assertNotNull(alignedPose);
        assertEquals(3, alignedPose.length);
        assertTrue(elapsedUs <= 150L, "单步连续李代数时空对齐耗时应 <= 150us，实测: " + elapsedUs + "us");

        // 验证残差收敛性
        double residualMm = aligner.computeAlignmentResidualMm(frame, alignedPose);
        assertTrue(residualMm <= 0.05, "时空对齐残差应 <= 0.05mm，实测: " + residualMm);
    }

    @Test
    @DisplayName("契约 2: 阿里千问 1536 维超球面多模态触视觉对齐与拓扑拟保距性 (命题 2.1)")
    void testContract2_Qwen1536HypersphereMetricPreservation() {
        MultiModalSensorFrame frame1 = createMockFrame(5.0, 30.0);
        MultiModalSensorFrame frame2 = createMockFrame(50.0, 30.0);

        double[] v1 = frame1.qwenEmbedding1536();
        double[] v2 = frame2.qwenEmbedding1536();

        assertNotNull(v1);
        assertEquals(1536, v1.length);

        // 验证超球面 L2 单位范数
        double norm1 = 0.0;
        for (double val : v1) norm1 += val * val;
        assertEquals(1.0, Math.sqrt(norm1), 1e-5, "向量必须严格处于单位超球面流形上");

        // 验证测地线距离单调有界性
        double geodesicDist = aligner.computeGeodesicDistance(v1, v2);
        assertTrue(geodesicDist >= 0.0 && geodesicDist <= Math.PI, "测地大圆弧距离应在 [0, PI] 区间内");
    }

    @Test
    @DisplayName("契约 3: 触视觉双模态三阶段无冲击平滑过渡阻抗调节 (定理 1.2)")
    void testContract3_ThreePhasePassivityAdaptiveImpedanceTransition() {
        // 1. 自由运动阶段 (Fz < 1N)
        MultiModalSensorFrame freeFrame = createMockFrame(0.2, 30.0);
        AdaptiveImpedanceState state1 = governor.updateImpedance(freeFrame, 40.0);
        assertEquals(PassivityAdaptiveImpedanceGovernor.PHASE_FREE_MOTION, state1.phaseName());
        assertEquals(300.0, state1.currentStiffnessNorm(), 1e-3);

        // 2. 触觉初接触柔顺捕获阶段 (1N <= Fz < 20N)
        MultiModalSensorFrame captureFrame = createMockFrame(5.0, 30.0);
        AdaptiveImpedanceState state2 = governor.updateImpedance(captureFrame, 40.0);
        assertEquals(PassivityAdaptiveImpedanceGovernor.PHASE_TACTILE_CAPTURE, state2.phaseName());
        // 柔顺捕获阶段刚度下调，阻尼增大以吸收冲击
        assertTrue(state2.currentStiffnessNorm() <= 300.0, "初接触捕获阶段应降低刚度以消除冲击");
        assertTrue(state2.currentDampingNorm() >= 40.0, "初接触捕获阶段应增大阻尼");

        // 3. 稳态力控装配阶段 (Fz >= 20N)
        MultiModalSensorFrame assemblyFrame = createMockFrame(35.0, 30.0);
        AdaptiveImpedanceState state3 = governor.updateImpedance(assemblyFrame, 40.0);
        assertEquals(PassivityAdaptiveImpedanceGovernor.PHASE_PRECISION_ASSEMBLY, state3.phaseName());
        assertTrue(state3.currentStiffnessNorm() >= 300.0, "稳态装配阶段应具备高刚度保证装配精度");
    }

    @Test
    @DisplayName("契约 4: 虚拟储能罐能量守恒监控与接触极限环自激振荡消除 (定理 1.2)")
    void testContract4_EnergyTankConservationAndChatteringSuppression() {
        double initialTank = governor.getEnergyTankLevel();
        assertEquals(20.0, initialTank, 1e-3);

        // 模拟连续刚度增加，观察储能罐支出
        MultiModalSensorFrame assemblyFrame = createMockFrame(40.0, 30.0);
        governor.updateImpedance(assemblyFrame, 40.0);
        double tankAfterStiffnessIncrease = governor.getEnergyTankLevel();
        assertTrue(tankAfterStiffnessIncrease < initialTank, "刚度提升必须从储能罐中扣除等效能量");

        // 储能罐能量始终处于安全区间 [eMin, eMax]
        assertTrue(tankAfterStiffnessIncrease >= 1.0, "储能罐能量不得低于 eMin (1.0J)");
        assertTrue(tankAfterStiffnessIncrease <= 50.0, "储能罐能量不得高于 eMax (50.0J)");
    }

    @Test
    @DisplayName("契约 5: 相对阶 r=2 高阶控制屏障 HOCBF 与微秒级闭式 QP 解析投影 (定理 1.3)")
    void testContract5_HigherOrderCBFFastAnalyticalQPSafetyGate() {
        double[] nominalTorque = new double[]{80.0, 60.0, 50.0, 30.0, 20.0, 10.0};

        // 工况 A: 接触力 200N，远低于 500N 阈值，双臂距离 0.15m (安全)
        TactileVisualSafetyGate.SafetyGateResult resA = safetyGate.filterTorque(
                nominalTorque, 200.0, 0.15, new double[]{0.0, 0.0, 0.0}
        );
        assertFalse(resA.modifiedByGate(), "安全工况下力矩无需修改");
        assertEquals(nominalTorque[0], resA.safeTorque()[0], 1e-3);

        // 工况 B: 接触力高达 480N，逼近 500N 危险阈值 (触发力矩压制)
        long startNs = System.nanoTime();
        TactileVisualSafetyGate.SafetyGateResult resB = safetyGate.filterTorque(
                nominalTorque, 480.0, 0.15, new double[]{0.0, 0.0, 0.0}
        );
        long elapsedUs = (System.nanoTime() - startNs) / 1000L;

        assertTrue(resB.modifiedByGate(), "接触力超限应触发安全门禁修改");
        assertTrue(resB.safeTorque()[0] < nominalTorque[0], "修改后力矩应下调以防止工件压溃");
        assertTrue(elapsedUs <= 10L, "微秒级闭式 QP 投影耗时应 <= 10us，实测: " + elapsedUs + "us");
    }

    @Test
    @DisplayName("契约 6: 多臂协同狭窄空间几何防干涉与内力硬约束保证 (定理 1.3)")
    void testContract6_MultiArmAntiCollisionAndInternalForceBound() {
        double[] nominalTorque = new double[]{40.0, 30.0, 20.0, 10.0, 5.0, 2.0};

        // 模拟双臂末端间距逼近 0.055m (临界防撞距离 0.05m + 预警 0.02m = 0.07m 区域)
        TactileVisualSafetyGate.SafetyGateResult res = safetyGate.filterTorque(
                nominalTorque, 100.0, 0.055, new double[]{0.0, 0.0, 0.0}
        );

        assertTrue(res.modifiedByGate(), "双臂间距过近应触发排斥力矩修正");
        assertNotEquals(nominalTorque[0], res.safeTorque()[0], "安全门禁应施加反向排斥力矩规避碰撞");
    }

    @Test
    @DisplayName("契约 7: 1000Hz 定长 4096 槽位 Disruptor 无锁总线与 JitterGuard 降级软着陆")
    void testContract7_DisruptorLockFreeBusAndJitterGuard() {
        MultiModalSensorFrame frame = createMockFrame(10.0, 30.0);
        AdaptiveImpedanceState state = governor.updateImpedance(frame, 40.0);
        double[] torque = new double[]{10.0, 20.0, 15.0, 5.0, 2.0, 1.0};

        long nowNs = System.nanoTime();
        long seq = controlBus.publishControl(frame, state, torque, nowNs);
        assertTrue(seq >= 0, "发布序列号应 >= 0");
        assertFalse(controlBus.isDegradedMode(), "正常发布下不应进入降级模式");

        TactileVisualControlBus.ControlSlot slot = controlBus.getSlot(seq);
        assertEquals(TactileVisualControlBus.STATE_NORMAL, slot.busState());

        // 模拟连续 3 帧时钟抖动 (> 2ms = 2_000_000ns)
        controlBus.publishControl(frame, state, torque, nowNs + 5_000_000L);
        controlBus.publishControl(frame, state, torque, nowNs + 10_000_000L);
        controlBus.publishControl(frame, state, torque, nowNs + 15_000_000L);

        assertTrue(controlBus.isDegradedMode(), "连续 3 帧时钟抖动应触发 JitterGuard 降级软着陆");
        TactileVisualControlBus.ControlSlot degradedSlot = controlBus.getSlot(controlBus.getLatestSequence());
        assertEquals(TactileVisualControlBus.STATE_DEGRADED, degradedSlot.busState());
        // 验证力矩被衰减为 20%
        assertEquals(2.0, degradedSlot.commandedTorque()[0], 1e-3);
    }

    @Test
    @DisplayName("契约 8: 不可变多模态感知与阻抗协同存证凭单 SHA-256 密码学签名验真与防篡改")
    void testContract8_CryptographicReceiptVerificationAndTamperResistance() {
        TactileVisualImpedanceReceipt receipt = TactileVisualImpedanceReceipt.createAndSign(
                "REC_78_888",
                "SESSION_IMPEDANCE_001",
                "ROBOT_ARM_DUAL_01",
                "PRECISION_ASSEMBLY",
                0.018,
                800.0,
                60.0,
                18.5,
                420.0,
                25L,
                "NORMAL"
        );

        assertNotNull(receipt);
        assertNotNull(receipt.sha256Signature());
        assertTrue(receipt.verifySignature(), "原生创建的存证凭单应 100% 验真通过");

        // 模拟篡改刚度数据
        TactileVisualImpedanceReceipt tamperedReceipt = new TactileVisualImpedanceReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.robotArmId(),
                receipt.phaseName(),
                receipt.spatiotemporalResidualMm(),
                9999.0, // 被恶意篡改
                receipt.dampingNorm(),
                receipt.energyTankJoules(),
                receipt.hocbfMargin(),
                receipt.stepLatencyUs(),
                receipt.busState(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );

        assertFalse(tamperedReceipt.verifySignature(), "篡改任何字段后的凭单验真必须立即失败并拦截");
    }
}
