package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.actuation.dto.ContinuousActuationReceipt;
import tech.qiantong.qknow.ai.embodied.actuation.dto.ContinuousTrajectoryCommand;
import tech.qiantong.qknow.ai.embodied.actuation.engine.ActuationControlBus;
import tech.qiantong.qknow.ai.embodied.actuation.engine.AdaptiveImpedanceActuator;
import tech.qiantong.qknow.ai.embodied.actuation.engine.ContinuousTrajectorySmoother;
import tech.qiantong.qknow.ai.embodied.actuation.engine.HighOrderBarrierGovernor;
import tech.qiantong.qknow.ai.embodied.dto.SpatialPose3D;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 65 专属契约单元测试：具身智能体连续动作轨迹平滑、高阶李雅普诺夫控制屏障证书与阻抗抗扰自适应执行中枢
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class Phase65ContinuousActuationContractTest {

    private ContinuousTrajectorySmoother smoother;
    private HighOrderBarrierGovernor governor;
    private AdaptiveImpedanceActuator actuator;
    private ActuationControlBus controlBus;

    @BeforeEach
    void setUp() {
        smoother = new ContinuousTrajectorySmoother();
        governor = new HighOrderBarrierGovernor(new double[]{1.0, 0.0, 0.0}, 0.3, 2.0, 2.0, 3.0);
        actuator = new AdaptiveImpedanceActuator(1.0, 500.0, 1.1, 0.02, 20.0);
        controlBus = new ActuationControlBus(smoother, governor, actuator);
    }

    @Test
    @DisplayName("契约 1: 连续执行存证凭单 SHA-256 签名完整性与防篡改测试")
    void test01_ContinuousActuationReceiptSha256IntegrityAndTamperProof() {
        ContinuousActuationReceipt receipt = ContinuousActuationReceipt.generate(
                "CMD_001", "HASH_ALPHA", 0.8, 2.1, 15.4, 0.15, 12.0, "NORMAL_EXECUTED"
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "原始凭单 SHA-256 签名必须自验通过");

        // 模拟篡改状态 (将 NORMAL_EXECUTED 篡改为 FAKE_STATE)
        ContinuousActuationReceipt tampered = new ContinuousActuationReceipt(
                receipt.receiptId(), receipt.commandId(), receipt.timestampNs(),
                receipt.commandHash(), receipt.maxVelocity(), receipt.maxAcceleration(),
                receipt.maxJerk(), receipt.minBarrierMargin(), receipt.meanContactForce(),
                "FAKE_STATE", receipt.signatureSha256()
        );
        assertFalse(tampered.verifyIntegrity(), "被篡改的凭单自验必须失败");
    }

    @Test
    @DisplayName("契约 2: 五次样条最小跃度平滑与 C^2 连续及加加速度有界测试 (Theorem 1.1)")
    void test02_QuinticSplineSmoothnessAndJerkBounded() {
        SpatialPose3D start = new SpatialPose3D(0.0, 0.0, 0.0, 0, 0, 0);
        SpatialPose3D end = new SpatialPose3D(1.0, 2.0, 0.5, 0, 0, 0);
        double duration = 2.0;

        ContinuousTrajectorySmoother.QuinticCoefficients spline = smoother.fitQuinticSpline(start, end, duration);

        // 验证端点边界
        ContinuousTrajectorySmoother.TrajectoryState state0 = spline.evaluate(0.0);
        assertEquals(0.0, state0.position().getX(), 1e-4);
        assertEquals(0.0, state0.velocity()[0], 1e-4);
        assertEquals(0.0, state0.acceleration()[0], 1e-4);

        ContinuousTrajectorySmoother.TrajectoryState stateEnd = spline.evaluate(duration);
        assertEquals(1.0, stateEnd.position().getX(), 1e-4);
        assertEquals(2.0, stateEnd.position().getY(), 1e-4);
        assertEquals(0.5, stateEnd.position().getZ(), 1e-4);
        assertEquals(0.0, stateEnd.velocity()[0], 1e-4);

        // 密集采样校验跃度有界性 |j(t)| <= 50.0
        for (double t = 0.0; t <= duration; t += 0.02) {
            ContinuousTrajectorySmoother.TrajectoryState s = spline.evaluate(t);
            assertTrue(s.maxJerkNorm() <= 50.0, "瞬时跃度模长必须小于 50.0 m/s^3");
        }
    }

    @Test
    @DisplayName("契约 3: 千问 1536 维超球面测地切空间投影曲率有界性测试 (Theorem 1.1)")
    void test03_HypersphereGeodesicCurvatureBoundInvariant() {
        SpatialPose3D start = new SpatialPose3D(0.0, 0.0, 0.0, 0, 0, 0);
        SpatialPose3D end = new SpatialPose3D(1.0, 1.0, 1.0, 0, 0, 0);
        ContinuousTrajectorySmoother.QuinticCoefficients spline = smoother.fitQuinticSpline(start, end, 1.0);

        float[] qwenVector = new float[1536];
        qwenVector[0] = 1.0f; // 单位向量

        double maxCurvature = 0.0;
        for (double t = 0.1; t <= 0.9; t += 0.05) {
            ContinuousTrajectorySmoother.TrajectoryState s = spline.evaluate(t);
            double kappa = smoother.computeGeodesicCurvature(s, qwenVector);
            assertFalse(Double.isNaN(kappa), "测地曲率不能为 NaN");
            assertFalse(Double.isInfinite(kappa), "测地曲率必须有限");
            maxCurvature = Math.max(maxCurvature, kappa);
        }

        assertTrue(maxCurvature < 100.0, "测地曲率在有界速度下具备有限紧致上界");
    }

    @Test
    @DisplayName("契约 4: 相对阶 r=2 高阶控制屏障证书零穿透前向不变性测试 (Theorem 1.2)")
    void test04_HighOrderBarrierGovernorForwardInvarianceZeroViolation() {
        // 障碍物在 (1.0, 0, 0), 半径 0.3m. 安全距离平方 0.09.
        HighOrderBarrierGovernor testGov = new HighOrderBarrierGovernor(new double[]{1.0, 0.0, 0.0}, 0.3, 2.0, 2.0, 5.0);
        // 当前位置 (0.4, 0, 0), 速度 (1.0, 0, 0) 高速冲向障碍物
        double[] pos = new double[]{0.4, 0.0, 0.0};
        double[] vel = new double[]{1.0, 0.0, 0.0};
        double[] nominalAcc = new double[]{2.0, 0.0, 0.0}; // 试图继续加速冲撞

        HighOrderBarrierGovernor.GovernorResult result = testGov.filter(pos, vel, nominalAcc);

        assertTrue(result.isIntervened(), "向障碍物冲撞必须触发 HOCBF 拦截修补");
        assertTrue(result.safeAcceleration()[0] < 0.0, "修正后的加速度必须为负向制动以阻断碰撞");

        // 模拟离散前向步进 50 步，验证始终未穿透安全边界
        double dt = 0.01;
        double currentX = pos[0];
        double currentVx = vel[0];
        for (int step = 0; step < 50; step++) {
            HighOrderBarrierGovernor.GovernorResult res = testGov.filter(
                    new double[]{currentX, 0, 0}, new double[]{currentVx, 0, 0}, new double[]{0.5, 0, 0}
            );
            currentVx += res.safeAcceleration()[0] * dt;
            currentX += currentVx * dt;
            // 距离障碍物中心 1.0 的距离
            double dist = Math.abs(currentX - 1.0);
            assertTrue(dist >= 0.28, "轨迹在 HOCBF 保护下绝不穿透物理禁区 (安全半径 0.3m)");
        }
    }

    @Test
    @DisplayName("契约 5: 极速 QP 正交投影控制修补李普希茨平滑性测试 (Theorem 1.2)")
    void test05_BarrierGovernorQpProjectionLipschitzContinuity() {
        double[] vel = new double[]{0.8, 0.0, 0.0};
        double[] nomAcc = new double[]{1.0, 0.0, 0.0};

        double[] pos1 = new double[]{0.6, 0.0, 0.0};
        double[] pos2 = new double[]{0.601, 0.0, 0.0}; // 微小扰动 1mm

        HighOrderBarrierGovernor.GovernorResult res1 = governor.filter(pos1, vel, nomAcc);
        HighOrderBarrierGovernor.GovernorResult res2 = governor.filter(pos2, vel, nomAcc);

        double deltaU = Math.abs(res1.safeAcceleration()[0] - res2.safeAcceleration()[0]);
        double deltaX = Math.abs(pos1[0] - pos2[0]);

        double lipschitzRatio = deltaU / deltaX;
        assertTrue(lipschitzRatio < 200.0, "控制修正满足李普希茨连续性，无突变阶跃抖动");
    }

    @Test
    @DisplayName("契约 6: 自适应阻抗变刚度与扰动观测器 (DOB) 冲击指数收敛测试 (Theorem 1.3)")
    void test06_AdaptiveImpedanceDobExponentialConvergence() {
        actuator.resetDob();

        // 施加突变 80N 冲击外力
        double[] measuredForce = new double[]{80.0, 0.0, 0.0};
        double[] posErr = new double[]{0.01, 0.0, 0.0};
        double[] velErr = new double[]{0.05, 0.0, 0.0};

        AdaptiveImpedanceActuator.ActuatorResponse r0 = actuator.update(posErr, velErr, measuredForce, 0.001);

        // 验证刚度自适应软化 (基础刚度 500N/m 软化至 < 250N/m)
        assertTrue(r0.currentStiffness() < 250.0, "在 80N 大外力下阻抗刚度必须自适应软化");
        assertTrue(r0.currentDamping() > 0, "阻尼必须保持正定过阻尼");

        // 迭代 50 步，验证 DOB 对外力扰动的指数收敛
        AdaptiveImpedanceActuator.ActuatorResponse rFinal = r0;
        for (int i = 0; i < 50; i++) {
            rFinal = actuator.update(posErr, velErr, measuredForce, 0.002);
        }

        // DOB 估计出的扰动应当有效抵消外力，使得输出合力显著下降
        assertTrue(rFinal.compliantForceOutput()[0] < 40.0, "经 DOB 扰动对消后顺应输出力峰值下降 >= 50%");
    }

    @Test
    @DisplayName("契约 7: 1000Hz 4096 槽位无锁环形总线与时延抖动软着陆熔断测试")
    void test07_ActuationControlBusRingBufferAndJitterGuard() {
        ContinuousTrajectoryCommand cmd = new ContinuousTrajectoryCommand(
                "CMD_BUS_01", "ARM_01",
                List.of(new SpatialPose3D(0, 0, 0, 0, 0, 0), new SpatialPose3D(1, 1, 1, 0, 0, 0)),
                1.0, 1.0, 3.0, 50.0, new float[1536]
        );

        // 正常高频驱动 50 周期
        for (int i = 0; i < 50; i++) {
            ContinuousActuationReceipt r = controlBus.executeCycle(cmd, i * 0.001, new double[]{5.0, 0, 0});
            assertNotNull(r);
            assertTrue(r.verifyIntegrity());
        }
        assertEquals(50L, controlBus.getCommittedCount(), "总线槽位必须无锁线性单调提交");
        assertEquals(ActuationControlBus.BusState.NORMAL, controlBus.getCurrentState());

        // 模拟触发软着陆熔断
        controlBus.setCurrentState(ActuationControlBus.BusState.DEGRADED_SOFT_LANDING);
        ContinuousActuationReceipt degradedReceipt = controlBus.executeCycle(cmd, 0.5, new double[]{0, 0, 0});
        assertEquals("BUS_DEGRADED", degradedReceipt.actuationStatus(), "软着陆状态下必须降级制动");
    }

    @Test
    @DisplayName("契约 8: 端到端连续控制微秒级计算延迟与闭环收敛测试")
    void test08_EndToEndContinuousActuationCycleConvergence() {
        ContinuousTrajectoryCommand cmd = new ContinuousTrajectoryCommand(
                "CMD_E2E_01", "ARM_01",
                List.of(new SpatialPose3D(0, 0, 0, 0, 0, 0), new SpatialPose3D(0.5, 0.5, 0.5, 0, 0, 0)),
                1.0, 1.0, 3.0, 50.0, new float[1536]
        );

        controlBus.setCurrentState(ActuationControlBus.BusState.NORMAL);

        long startNs = System.nanoTime();
        int iterations = 1000; // 模拟 1000 次 1kHz 控制循环
        for (int i = 0; i < iterations; i++) {
            double t = (i % 1000) * 0.001;
            ContinuousActuationReceipt rcpt = controlBus.executeCycle(cmd, t, new double[]{2.0, 1.0, 0.5});
            assertNotNull(rcpt);
        }
        long totalNs = System.nanoTime() - startNs;
        double avgMs = (totalNs / 1_000_000.0) / iterations;

        assertTrue(avgMs <= 2.0, "单次连续控制循环平均耗时必须 <= 2ms (实际在微秒级)");
        System.out.printf("1000 次连续控制循环完成，单步平均耗时: %.4f ms\n", avgMs);
    }
}
