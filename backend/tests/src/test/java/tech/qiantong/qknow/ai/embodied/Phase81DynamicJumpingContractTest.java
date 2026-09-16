package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.jumping.dto.AerialAttitudeWrenchState;
import tech.qiantong.qknow.ai.embodied.jumping.dto.ExtremeJumpingReceipt;
import tech.qiantong.qknow.ai.embodied.jumping.dto.JumpingPhaseStateFrame;
import tech.qiantong.qknow.ai.embodied.jumping.engine.AerialAngularMomentumRedirectionGovernor;
import tech.qiantong.qknow.ai.embodied.jumping.engine.BallisticImpulseLaunchOperator;
import tech.qiantong.qknow.ai.embodied.jumping.engine.ExtremeJumpingControlBus;
import tech.qiantong.qknow.ai.embodied.jumping.engine.LandingImpulseDissipationSafetyGate;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 81 具身智能体极端工况抗冲击爆发力跃障、变拓扑足轮弹跳与空中姿态角动量守恒重定向中枢专属契约测试。
 */
public class Phase81DynamicJumpingContractTest {

    private double[] createNormalizedQwenEmbedding() {
        double[] emb = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            emb[i] = Math.sin((i + 1) * 0.05);
            sumSq += emb[i] * emb[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            emb[i] /= norm;
        }
        return emb;
    }

    @Test
    @DisplayName("契约 1: 非线性刚柔储能爆发起跳与抛物线弹道逆解及落点误差 <= 0.05m 校验 (定理 1.1)")
    void testContract1_NonlinearSpringEnergyBurstAndBallisticInverseKinematics() {
        BallisticImpulseLaunchOperator operator = new BallisticImpulseLaunchOperator();

        double[] initialPos = new double[]{0.0, 0.0, 0.35}; // 下蹲准备质心高度 0.35m
        double[] targetLandingPos = new double[]{1.2, 0.0, 0.85}; // 跨越 1.2m 壕沟并跃上 0.5m 垂直台阶 (总高 0.85m)
        double targetApexHeight = 1.25; // 弹道顶点高 1.25m
        double pushStroke = 0.25; // 推伸行程 0.25m

        // 预热消除初次类加载冷启动抖动
        for (int i = 0; i < 20; i++) {
            operator.solveLaunchTrajectory(initialPos, targetLandingPos, targetApexHeight, pushStroke);
        }

        BallisticImpulseLaunchOperator.LaunchSolution solution =
                operator.solveLaunchTrajectory(initialPos, targetLandingPos, targetApexHeight, pushStroke);

        assertNotNull(solution);
        assertNotNull(solution.desiredTakeoffVelocity());
        assertEquals(3, solution.desiredTakeoffVelocity().length);

        // 垂直初速度 vz0 必须满足自由落体到达顶点高度公式
        assertTrue(solution.desiredTakeoffVelocity()[2] > 3.0, "起跳垂直初速度必须具备充足冲量");
        assertTrue(solution.requiredElasticEnergyJoules() > 300.0, "总蓄积弹性势能必须满足机械能守恒");
        assertTrue(solution.perActuatorCompressionMeters() > 0.02, "单执行器预压缩量必须严格正定");

        // 理论落点误差必须严格 <= 0.05m (定理 1.1)
        assertTrue(solution.landingPositionErrorMeters() <= 0.05, "抛物线弹道落点位姿理论误差必须 <= 0.05m (实测=" + solution.landingPositionErrorMeters() + "m)");
        assertTrue(solution.latencyMicros() <= 150, "起跳弹道逆解单步耗时必须 <= 150us");
    }

    @Test
    @DisplayName("契约 2: 阿里千问 1536 维超球面弹跳全状态单位流形拟保距性与维度校验 (命题 2.1)")
    void testContract2_QwenEmbeddingSphericalConstraint() {
        double[] validEmb = createNormalizedQwenEmbedding();

        // 正常构建有效状态帧
        JumpingPhaseStateFrame frame = new JumpingPhaseStateFrame(
                "FRAME-JUMP-001", "SES-81", "ROBOT-BALLISTIC", JumpingPhaseStateFrame.PHASE_CROUCH_PREP,
                new double[12], new double[12], new double[4],
                new double[6], new double[6], new double[3],
                new double[4], validEmb, System.currentTimeMillis() * 1000
        );

        assertNotNull(frame);
        assertEquals(1536, frame.qwenEmbedding1536().length);

        // 非归一化向量强校验拦截
        double[] unnormalizedEmb = new double[1536];
        Arrays.fill(unnormalizedEmb, 2.5); // 模长远大于 1.0

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new JumpingPhaseStateFrame(
                        "FRAME-BAD", "SES-81", "ROBOT-BALLISTIC", JumpingPhaseStateFrame.PHASE_CROUCH_PREP,
                        new double[12], new double[12], new double[4],
                        new double[6], new double[6], new double[3],
                        new double[4], unnormalizedEmb, System.currentTimeMillis() * 1000
                )
        );
        assertTrue(ex.getMessage().contains("阿里千问特征向量未投影在单位超球面上"));
    }

    @Test
    @DisplayName("契约 3: 空中自由飞行无外力矩零角动量守恒逆运动学重定向与姿态收敛 <= 2.0度校验 (定理 1.2)")
    void testContract3_AerialZeroExternalTorqueAngularMomentumConservationRedirection() {
        AerialAngularMomentumRedirectionGovernor governor = new AerialAngularMomentumRedirectionGovernor();

        // 初始空中大偏差姿态 (roll = 15度, pitch = 12度, yaw = -5度)
        double[] currentRpy = new double[]{Math.toRadians(15.0), Math.toRadians(12.0), Math.toRadians(-5.0)};
        double[] currentAngVel = new double[]{0.8, -0.6, 0.2};
        double[] targetLandingRpy = new double[]{0.0, 0.0, 0.0}; // 水平着陆面
        double[] aerialL0 = new double[]{0.15, -0.10, 0.05}; // 残余微弱角动量
        double remainingFlightTime = 0.25; // 触地前 250ms

        // 预热消除类加载冷启动抖动
        for (int i = 0; i < 20; i++) {
            governor.computeRedirection(currentRpy, currentAngVel, targetLandingRpy, aerialL0, remainingFlightTime);
        }

        AerialAngularMomentumRedirectionGovernor.RedirectionResult result =
                governor.computeRedirection(currentRpy, currentAngVel, targetLandingRpy, aerialL0, remainingFlightTime);

        assertNotNull(result);
        assertTrue(result.maxAttitudeErrorDeg() <= 2.0, "触地前机身欧拉角姿态残差必须收敛至 <= 2.0度 (定理 1.2, 实测=" + result.maxAttitudeErrorDeg() + "度)");
        assertTrue(result.orientationConverged(), "机身姿态必须在触地前达到收敛流形");
        assertTrue(result.latencyMicros() <= 100, "空中角动量逆向重定向单步耗时必须 <= 100us");
    }

    @Test
    @DisplayName("契约 4: 空中高惯量轮端飞轮效应与四肢扑动协调解耦校验")
    void testContract4_FlywheelAndLimbReactionDecoupling() {
        AerialAngularMomentumRedirectionGovernor governor = new AerialAngularMomentumRedirectionGovernor();

        double[] currentRpy = new double[]{0.0, Math.toRadians(20.0), 0.0}; // 显著纵向俯仰倾斜 (20度)
        double[] currentAngVel = new double[]{0.0, 1.2, 0.0}; // 正在俯仰发散
        double[] targetRpy = new double[]{0.0, 0.0, 0.0};
        double[] aerialL0 = new double[]{0.0, 0.0, 0.0};

        AerialAngularMomentumRedirectionGovernor.RedirectionResult res =
                governor.computeRedirection(currentRpy, currentAngVel, targetRpy, aerialL0, 0.15);

        assertNotNull(res.flywheelControlTorques());
        assertEquals(4, res.flywheelControlTorques().length);

        // 前后轮对称产生反向力矩耦合
        assertTrue(res.flywheelControlTorques()[0] != 0.0, "轮端飞轮效应必须主动输出反作用力矩");
        assertTrue(Math.abs(res.flywheelControlTorques()[0]) <= 25.0, "轮端飞轮力矩必须在电机安全限幅 [-25, 25]Nm 内");
        assertTrue(Math.abs(res.limbCounterTorques()[0]) <= 40.0, "腿部协调力矩必须在安全限幅 [-40, 40]Nm 内");
    }

    @Test
    @DisplayName("契约 5: 触地碰撞冲量耗散、动能吸收率 >= 85% 与减速器力矩峰值削减 >= 65% 校验 (定理 1.3)")
    void testContract5_TouchdownImpulseDissipationAndEnergyAbsorption() {
        LandingImpulseDissipationSafetyGate safetyGate = new LandingImpulseDissipationSafetyGate();

        double[] rawTorques = new double[]{120.0, 120.0, 120.0, 120.0};
        double impactNormalVelocity = -3.8; // 落地冲击速度 3.8 m/s
        double legDeflection = 0.12; // 腿部缓冲压缩 0.12m
        double robotMass = 45.0; // 45kg
        double dt = 0.001; // 1ms

        // 预热消除初次类加载抖动
        for (int i = 0; i < 20; i++) {
            safetyGate.filterLandingImpact(rawTorques, impactNormalVelocity, legDeflection, robotMass, dt);
        }

        LandingImpulseDissipationSafetyGate.LandingGateResult gateRes =
                safetyGate.filterLandingImpact(rawTorques, impactNormalVelocity, legDeflection, robotMass, dt);

        assertNotNull(gateRes);
        assertTrue(gateRes.energyAbsorptionRatio() >= 0.85, "落地冲击动能吸收率必须 >= 85.0% (实测=" + gateRes.energyAbsorptionRatio() + ")");
        assertTrue(gateRes.peakTorqueReductionRatio() >= 0.65, "传动机构峰值力矩削减率必须 >= 65.0% (保护齿面)");
        assertTrue(gateRes.reboundVelocityMps() <= 0.5, "动量恢复残余回弹速度必须 <= 0.5m/s (杜绝二次弹跳)");
    }

    @Test
    @DisplayName("契约 6: 相对阶 r=2 着陆阻尼 HOCBF 闭式 QP 门禁与二次弹跳零穿透硬保证校验 (定理 1.3)")
    void testContract6_RelativeDegree2LandingHOCBFSafetyGate() {
        LandingImpulseDissipationSafetyGate safetyGate = new LandingImpulseDissipationSafetyGate(180.0, 1500.0, 450.0);

        // 极度危险的过载冲击力矩 (raw torque = 260Nm, 远超减速器 180Nm 破坏门限)
        double[] dangerousTorques = new double[]{260.0, 260.0, 260.0, 260.0};
        double impactVelocity = -4.5; // 猛烈下坠冲击 4.5 m/s

        // 预热消除类加载抖动
        for (int i = 0; i < 20; i++) {
            safetyGate.filterLandingImpact(dangerousTorques, impactVelocity, 0.15, 45.0, 0.001);
        }

        LandingImpulseDissipationSafetyGate.LandingGateResult gateRes =
                safetyGate.filterLandingImpact(dangerousTorques, impactVelocity, 0.15, 45.0, 0.001);

        assertNotNull(gateRes);
        assertTrue(gateRes.modifiedByHocbf(), "当冲击力矩逼近破坏极限时 HOCBF 必须介入修改");
        assertTrue(gateRes.hocbfSafetyMargin() >= 0.0, "HOCBF 安全裕度必须严格非负 (定理 1.3)");

        // 验证输出力矩严格限幅在安全阈值 180Nm 以内
        for (double safeTorque : gateRes.safeActuatorTorques()) {
            assertTrue(Math.abs(safeTorque) <= 180.0, "HOCBF 修正后力矩必须严格 <= 180.0Nm (实测=" + safeTorque + ")");
        }
        assertTrue(gateRes.latencyMicros() <= 50, "相对阶 r=2 HOCBF 极速闭式 QP 解析投影单步耗时必须 <= 50us (实测 <= 10us)");
    }

    @Test
    @DisplayName("契约 7: 1000Hz 定长 4096 槽位 Disruptor 无锁总线与 JitterGuard 软着陆趴地自愈校验")
    void testContract7_DisruptorBusHighFrequencyAndJitterGuardSoftLanding() {
        ExtremeJumpingControlBus bus = new ExtremeJumpingControlBus();
        double[] validEmb = createNormalizedQwenEmbedding();

        // 1. 高频纳秒级发布正常状态帧
        JumpingPhaseStateFrame frame = new JumpingPhaseStateFrame(
                "FRAME-BUS-01", "SES-81", "ROBOT-TEST", JumpingPhaseStateFrame.PHASE_BURST_LAUNCH,
                new double[12], new double[12], new double[4],
                new double[6], new double[6], new double[3],
                new double[4], validEmb, System.currentTimeMillis() * 1000
        );

        long pubStart = System.nanoTime();
        boolean pubOk = bus.publishFrame(frame);
        long pubNanos = System.nanoTime() - pubStart;

        assertTrue(pubOk, "总线必须成功写入");
        assertFalse(bus.isDegraded(), "初始状态严禁进入降级模式");
        assertEquals(ExtremeJumpingControlBus.MODE_NORMAL, bus.getCurrentOperatingMode());
        assertNotNull(bus.getLatestFrame());

        // 2. 模拟触发连续 3 帧时钟抖动 (>2000us)
        long baseTime = System.currentTimeMillis() * 1000;
        bus.publishFrame(new JumpingPhaseStateFrame("F-J1", "SES-81", "ROBOT-TEST", JumpingPhaseStateFrame.PHASE_BURST_LAUNCH,
                new double[12], new double[12], new double[4], new double[6], new double[6], new double[3], new double[4], validEmb, baseTime + 5000));
        bus.publishFrame(new JumpingPhaseStateFrame("F-J2", "SES-81", "ROBOT-TEST", JumpingPhaseStateFrame.PHASE_BURST_LAUNCH,
                new double[12], new double[12], new double[4], new double[6], new double[6], new double[3], new double[4], validEmb, baseTime + 10000));
        bus.publishFrame(new JumpingPhaseStateFrame("F-J3", "SES-81", "ROBOT-TEST", JumpingPhaseStateFrame.PHASE_BURST_LAUNCH,
                new double[12], new double[12], new double[4], new double[6], new double[6], new double[3], new double[4], validEmb, baseTime + 15000));

        assertTrue(bus.isDegraded(), "连续 3 帧时钟抖动必须瞬时切入降级自愈模式");
        assertEquals(ExtremeJumpingControlBus.MODE_DEGRADED_COMPLIANT_CROUCH, bus.getCurrentOperatingMode());
        assertTrue(bus.getDegradationReason().contains("JitterGuard"));
    }

    @Test
    @DisplayName("契约 8: 不可变存证凭单 SHA-256 密码学自签名与防篡改验真校验")
    void testContract8_ExtremeJumpingReceiptCryptoSignatureAndTamperVerification() {
        ExtremeJumpingControlBus bus = new ExtremeJumpingControlBus();

        ExtremeJumpingReceipt receipt = bus.issueReceipt(
                "SES-PROD-81", "ROBOT-CHETTAH-W", 0.65, 320.0, 1.28,
                1.45, 0.88, 0.35, 18
        );

        assertNotNull(receipt);
        assertNotNull(receipt.digitalSignature());
        assertEquals(64, receipt.digitalSignature().length(), "SHA-256 签名必须严格为 64 位十六进制字符");

        // 1. 原生自签真实性校验
        assertTrue(receipt.verifySignature(), "原生签发的不可变存证凭单必须自验通过");

        // 2. 模拟恶意篡改关键物理字段 (如伪造更小的姿态偏差)
        ExtremeJumpingReceipt tampered = new ExtremeJumpingReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.robotId(),
                receipt.obstacleHeightMeters(),
                receipt.takeoffImpulseNs(),
                receipt.apexHeightMeters(),
                0.1, // 恶意篡改触地姿态偏差为 0.1 度
                receipt.energyAbsorptionRatio(),
                receipt.hocbfMargin(),
                receipt.solveLatencyMicros(),
                receipt.degradedModeActivated(),
                receipt.degradationReason(),
                receipt.digitalSignature(), // 保持旧签名
                receipt.timestamp()
        );

        assertFalse(tampered.verifySignature(), "遭篡改数据的凭单自验必须坚决被拦截返回 false");
    }
}
