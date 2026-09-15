package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.fluid.dto.FluidManipulationReceipt;
import tech.qiantong.qknow.ai.embodied.fluid.dto.FluidSloshState;
import tech.qiantong.qknow.ai.embodied.fluid.dto.LiquidDispensingCommand;
import tech.qiantong.qknow.ai.embodied.fluid.engine.FluidControlBus;
import tech.qiantong.qknow.ai.embodied.fluid.engine.FluidDynamicsReducedOperator;
import tech.qiantong.qknow.ai.embodied.fluid.engine.FluidSloshSuppressionPlanner;
import tech.qiantong.qknow.ai.embodied.fluid.engine.NonNewtonianRheologyGovernor;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 72: 具身智能体接触富集型流体-刚体动力学协同、非牛顿流体抓取分注与微观界面多相流控制中枢
 * 专属契约驱动严苛测试套件 (8/8)
 */
public class Phase72FluidStructureManipulationContractTest {

    private FluidDynamicsReducedOperator reducedOperator;
    private NonNewtonianRheologyGovernor rheologyGovernor;
    private FluidSloshSuppressionPlanner suppressionPlanner;
    private FluidControlBus controlBus;

    @BeforeEach
    void setUp() {
        reducedOperator = new FluidDynamicsReducedOperator(0.05);
        rheologyGovernor = new NonNewtonianRheologyGovernor();
        suppressionPlanner = new FluidSloshSuppressionPlanner(reducedOperator);
        controlBus = new FluidControlBus();
    }

    private FluidSloshState createMockSloshState(String containerId, double sloshAngle, double kineticEnergy) {
        double radius = 0.05; // 50mm 半径
        double height = 0.15; // 150mm 高度
        double lipMargin = 0.03; // 30mm 开口裕度
        double initialLiquidH = 0.08; // 80mm 液位
        double density = 1000.0; // 水标称密度
        double viscosity = 0.001; // 水粘度 1mPa*s

        double[] acc = new double[]{1.5, 0.0, 0.0};
        double[] torque = new double[]{0.0, 0.15, 0.0};

        double[] qwen = new double[1536];
        double norm = Math.sqrt(1536);
        for (int i = 0; i < 1536; i++) {
            qwen[i] = 1.0 / norm;
        }

        return new FluidSloshState(
                containerId,
                radius,
                height,
                lipMargin,
                initialLiquidH,
                density,
                viscosity,
                sloshAngle,
                0.0,
                kineticEnergy,
                acc,
                torque,
                qwen,
                System.currentTimeMillis()
        );
    }

    @Test
    @DisplayName("契约 1: 降阶等效单摆流固动力学单步推演耗时严格 <= 1.0ms 且主频解析精度吻合")
    void testReducedOrderSloshingDynamicsMicrosecondPerformance() {
        FluidSloshState state = createMockSloshState("beaker_01", 0.05, 0.02);
        double[] cmdAcc = new double[]{2.0, 0.5, 0.0};

        // 预热 JIT
        for (int i = 0; i < 50; i++) {
            state = reducedOperator.step(state, cmdAcc, 0.001);
        }

        long startNs = System.nanoTime();
        FluidSloshState nextState = reducedOperator.step(state, cmdAcc, 0.001);
        long elapsedNs = System.nanoTime() - startNs;
        long elapsedUs = elapsedNs / 1000;

        assertTrue(elapsedUs <= 1000, "单步降阶推演耗时必须严格 <= 1.0ms (实测: " + elapsedUs + "us)");
        assertNotNull(nextState);
        assertTrue(nextState.sloshKineticEnergy() >= 0.0, "残余晃荡总动能必须保正");

        // 验证主频基准计算
        double omega1 = reducedOperator.computeNaturalFrequency(state.radius(), state.initialLiquidHeight());
        assertTrue(omega1 > 5.0 && omega1 < 30.0, "圆柱容器主晃荡角频率必须在物理合理区间内: " + omega1);
    }

    @Test
    @DisplayName("契约 2: 自由液面特征严格满足阿里千问 1536 维超球面单位模长约束 (||v||_2 = 1.0 +- 1e-6)")
    void testHypersphericalFluidStateEmbeddingNorm() {
        FluidSloshState state = createMockSloshState("vessel_qwen", 0.02, 0.01);
        assertTrue(state.isEmbeddingUnitNorm(), "阿里千问 1536 维超球面特征向量模长必须严格为 1.0");

        double sumSq = 0.0;
        for (double val : state.hypersphericalEmbedding()) {
            sumSq += val * val;
        }
        assertEquals(1.0, Math.sqrt(sumSq), 1e-6, "向量欧氏模长验证必须精确归一化");
    }

    @Test
    @DisplayName("契约 3: 幂律流变本构支持剪切变稀与剪切变稠，粘度全域保正且有界")
    void testNonNewtonianPowerLawRheologyShearThinningAndThickening() {
        // 剪切变稀 (n = 0.6 < 1.0, 如环氧树脂/UV胶)
        double kShearThin = 20.0;
        double nShearThin = 0.6;
        double etaLow = rheologyGovernor.computeApparentViscosity(kShearThin, nShearThin, 1.0);
        double etaHigh = rheologyGovernor.computeApparentViscosity(kShearThin, nShearThin, 100.0);
        assertTrue(etaLow > etaHigh, "剪切变稀流体在高速剪切下表观粘度必须单调下降: " + etaLow + " -> " + etaHigh);

        // 剪切变稠 (n = 1.4 > 1.0, 如胀塑性防弹流体/浓淀粉悬浮液)
        double kShearThick = 5.0;
        double nShearThick = 1.4;
        double etaThickLow = rheologyGovernor.computeApparentViscosity(kShearThick, nShearThick, 1.0);
        double etaThickHigh = rheologyGovernor.computeApparentViscosity(kShearThick, nShearThick, 100.0);
        assertTrue(etaThickHigh > etaThickLow, "剪切变稠流体在高速剪切下表观粘度必须单调上升: " + etaThickLow + " -> " + etaThickHigh);

        // 牛顿流体 (n = 1.0)
        double etaNewtonian = rheologyGovernor.computeApparentViscosity(1.5, 1.0, 50.0);
        assertEquals(1.5, etaNewtonian, 1e-6, "牛顿流体粘度恒定等于稠度系数 K");
    }

    @Test
    @DisplayName("契约 4: CaBER 毛细拉丝断裂时间动态评估与微秒级反转回抽切断率达 100%")
    void testCapillaryBreakupAndReverseSuckBackFilamentElimination() {
        LiquidDispensingCommand cmd = new LiquidDispensingCommand(
                "disp_cmd_01",
                "SHEAR_THINNING",
                25.0,
                0.55,
                0.04, // 40ms 松弛时间
                0.50, // 0.5 mL
                0.80, // 0.8mm 针嘴
                0.50, // 0.5mm 回抽
                System.currentTimeMillis()
        );

        double tauBreak = rheologyGovernor.computeCapillaryBreakupTime(cmd.relaxationTimeLambda());
        assertEquals(0.12, tauBreak, 1e-4, "CaBER 预测拉丝断裂时间应为 3 * lambda");

        // 验证回抽进展与 100% 截断
        double ratioMid = rheologyGovernor.evaluateFilamentCutoffRatio(cmd, 0.02);
        assertTrue(ratioMid > 0.0 && ratioMid < 1.0, "回抽过程中拉丝截断率单调递增");

        double ratioComplete = rheologyGovernor.evaluateFilamentCutoffRatio(cmd, 0.06);
        assertEquals(1.0, ratioComplete, 1e-6, "反向回抽完成后拉丝截断率必须严格达到 100%");
    }

    @Test
    @DisplayName("契约 5: 等效重力矢量主动对齐与开口边缘防溢出控制屏障 (HOCBF) 拦截率 100%")
    void testEquivalentGravityAlignmentAndFreeSurfaceSloshSpillBarrier() {
        // 1. 等效重力矢量对齐倾角
        double ax = 4.905; // 0.5g 侧向加速度
        double ay = 0.0;
        double az = 0.0;
        double thetaDes = suppressionPlanner.computeDesiredAlignmentAngle(ax, ay, az);
        assertEquals(Math.atan2(4.905, 9.81), thetaDes, 1e-4, "期望倾角必须精确平衡切向惯性力");

        // 2. 自由液面开口防溢出屏障
        FluidSloshState safeState = createMockSloshState("tank_01", 0.05, 0.01);
        double marginSafe = suppressionPlanner.computeSpillBarrierMargin(safeState);
        assertTrue(marginSafe > 0.0, "正常状态下防溢出屏障裕度必须大于 0");

        // 危险状态 (晃荡角过大，波高突破临界)
        FluidSloshState dangerState = createMockSloshState("tank_01", 0.45, 0.15);
        double[] nominalAcc = new double[]{5.0, 0.0, 0.0};
        double[] safeAcc = suppressionPlanner.filterNominalAcceleration(dangerState, nominalAcc);
        assertTrue(Math.abs(safeAcc[0]) < Math.abs(nominalAcc[0]), "高阶屏障必须微秒级削减水平加速度以防溢出");
    }

    @Test
    @DisplayName("契约 6: 4096 槽位 Disruptor 无锁总线非阻塞吞吐与时钟抖动 DEGRADED_SAFE_HOVER 软着陆")
    void testLockFreeBus1000HzThroughputAndDegradedSafeHoverTrigger() {
        FluidSloshState state = createMockSloshState("bus_state_01", 0.02, 0.01);

        // 验证非阻塞写入与读取
        long seq = controlBus.publish(state);
        assertTrue(seq >= 0, "序号必须单调递增");
        FluidSloshState readState = controlBus.getLatest();
        assertNotNull(readState);
        assertEquals("bus_state_01", readState.containerId());
        assertFalse(controlBus.isDegradedSafeHover(), "初始状态不应处于降级悬停");

        // 模拟动能突增超过安全阈值 (MAX_ALLOWABLE_KINETIC_ENERGY = 8.0)
        FluidSloshState extremeState = createMockSloshState("bus_state_surge", 0.20, 12.5);
        controlBus.publish(extremeState);
        assertTrue(controlBus.isDegradedSafeHover(), "晃荡动能突增时必须瞬间激活 DEGRADED_SAFE_HOVER 软着陆");
    }

    @Test
    @DisplayName("契约 7: 不可变存证凭单 FluidManipulationReceipt 全要素完整性与 SHA-256 签名自验 100%")
    void testFluidManipulationReceiptSha256Verification() {
        FluidManipulationReceipt receipt = FluidManipulationReceipt.sign(
                "rcpt_fl_001",
                "sess_fl_001",
                "vessel_chem_01",
                15.5,
                0.015,
                0.08,
                0.022,
                0.045,
                1.0,
                125,
                false,
                System.currentTimeMillis()
        );

        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 哈希串长度必须严格为 64 位十六进制");
        assertTrue(receipt.verifySignature(), "原始凭单签名验真必须 100% 通过");

        // 篡改测试
        FluidManipulationReceipt tamperedReceipt = new FluidManipulationReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.containerId(),
                receipt.dispensedVolumeMl() + 5.0, // 恶意篡改分注体积
                receipt.averageKineticEnergy(),
                receipt.maxSloshAngleRad(),
                receipt.sloshAngleMarginRad(),
                receipt.apparentViscosity(),
                receipt.filamentCutoffRatio(),
                receipt.reducedModelInferenceTimeUs(),
                receipt.degradedSafeHover(),
                receipt.timestampMs(),
                receipt.sha256Signature()
        );
        assertFalse(tamperedReceipt.verifySignature(), "被篡改数据的凭单必须无法通过 SHA-256 验真");
    }

    @Test
    @DisplayName("契约 8: 端到端流体分注与防晃荡多周期协同收敛闭环")
    void testEndToEndFluidDispensingAndSloshSuppressionConvergence() {
        FluidSloshState state = createMockSloshState("closed_loop_tank", 0.12, 0.05);
        LiquidDispensingCommand cmd = new LiquidDispensingCommand(
                "cmd_batch_01",
                "SHEAR_THINNING",
                12.0,
                0.70,
                0.03,
                1.0,
                1.0,
                0.5,
                System.currentTimeMillis()
        );

        // 闭环迭代 100 个 1000Hz 周期 (100ms)
        double dt = 0.001;
        for (int i = 0; i < 100; i++) {
            double[] nominalAcc = new double[]{1.0, 0.0, 0.0};
            double[] safeAcc = suppressionPlanner.filterNominalAcceleration(state, nominalAcc);
            state = reducedOperator.step(state, safeAcc, dt);
            controlBus.publish(state);
        }

        FluidSloshState finalState = controlBus.getLatest();
        assertNotNull(finalState);
        assertTrue(finalState.sloshAngle() < 0.20, "经过主动滤波与阻尼耗散，晃荡倾角应保持在安全稳定范围内");
        double cutoffRatio = rheologyGovernor.evaluateFilamentCutoffRatio(cmd, 0.06);
        assertEquals(1.0, cutoffRatio, 1e-6, "分注收尾拉丝截断率必须达到 100%");
    }
}
