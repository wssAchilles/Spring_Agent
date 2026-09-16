package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.hybrid.dto.ContactWrenchState;
import tech.qiantong.qknow.ai.embodied.hybrid.dto.LegWheelLocomotionReceipt;
import tech.qiantong.qknow.ai.embodied.hybrid.dto.LegWheelStateFrame;
import tech.qiantong.qknow.ai.embodied.hybrid.engine.AntiToppleSafetyGate;
import tech.qiantong.qknow.ai.embodied.hybrid.engine.FrictionForceClosureDistributor;
import tech.qiantong.qknow.ai.embodied.hybrid.engine.LegWheelControlBus;
import tech.qiantong.qknow.ai.embodied.hybrid.engine.ReconfigurableKinematicsOperator;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 80 具身智能体多足/轮臂混合构型仿生运动学拓扑重构、微观摩擦接触力封闭流形与毫秒级全地形越障控制中枢专属契约测试。
 */
public class Phase80HybridLegWheelContractTest {

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
    @DisplayName("契约 1: 混合轮腿拓扑可变运动学正逆解与同胚流形平滑切换校验 (定理 1.1)")
    void testContract1_ReconfigurableKinematicsHomotopyAndConditionNumber() {
        ReconfigurableKinematicsOperator operator = new ReconfigurableKinematicsOperator();

        double[] jointAngles = new double[12];
        for (int i = 0; i < 12; i++) {
            jointAngles[i] = (i % 3 == 2) ? -Math.PI / 4.0 : 0.1;
        }
        double[] wheelVelocities = new double[]{10.0, 10.0, 10.0, 10.0};

        // 预热消除初次类加载冷启动抖动
        operator.solveForward(jointAngles, wheelVelocities, ReconfigurableKinematicsOperator.MODE_WHEELED_ROLLING);

        // 1. 正运动学解算与耗时校验
        ReconfigurableKinematicsOperator.KinematicsResult fwdRes =
                operator.solveForward(jointAngles, wheelVelocities, ReconfigurableKinematicsOperator.MODE_WHEELED_ROLLING);

        assertNotNull(fwdRes.footPositions());
        assertEquals(4, fwdRes.footPositions().length);
        assertTrue(fwdRes.latencyMicros() <= 150, "正运动学单步推演耗时必须 <= 150us");

        // 2. 逆运动学同胚流形过渡与奇异值阻尼截断校验
        double[] baseTwist = new double[]{1.2, 0.0, 0.0, 0.0, 0.0, 0.0};
        double[][] footTargets = new double[][]{
                {0.2, 0.15, -0.32},
                {0.2, -0.15, -0.32},
                {-0.2, 0.15, -0.32},
                {-0.2, -0.15, -0.32}
        };

        // 测试从轮式向四足步态过渡 (alpha = 0.5)
        ReconfigurableKinematicsOperator.KinematicsResult invRes =
                operator.solveInverse(baseTwist, footTargets, ReconfigurableKinematicsOperator.MODE_HYBRID_CLIMBING, 0.5);

        assertNotNull(invRes.jointAngles());
        assertEquals(12, invRes.jointAngles().length);
        assertEquals(4, invRes.wheelVelocities().length);
        assertTrue(invRes.conditionNumber() <= 50.0, "雅可比伪逆条件数必须一致有界 kappa <= 50.0 (定理 1.1)");
        assertTrue(invRes.latencyMicros() <= 150, "逆运动学单步推演耗时必须 <= 150us");
    }

    @Test
    @DisplayName("契约 2: 阿里千问 1536 维超球面轮腿构型与全地形特征拟保距嵌入校验")
    void testContract2_QwenEmbeddingSphericalConstraint() {
        double[] validEmb = createNormalizedQwenEmbedding();

        // 正常构建单位超球面向量帧
        LegWheelStateFrame validFrame = new LegWheelStateFrame(
                "FRAME-001", "SES-101", "ROBOT-LW", ReconfigurableKinematicsOperator.MODE_WHEELED_ROLLING,
                new double[12], new double[12], new double[4],
                new double[6], new double[6], new double[4][3],
                new double[100], validEmb, System.currentTimeMillis() * 1000
        );
        assertNotNull(validFrame);
        assertEquals(1536, validFrame.qwenEmbedding1536().length);

        // 非归一化向量强校验拦截
        double[] unnormalizedEmb = new double[1536];
        Arrays.fill(unnormalizedEmb, 2.0); // 模长远大于 1.0

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new LegWheelStateFrame(
                        "FRAME-ERR", "SES-101", "ROBOT-LW", ReconfigurableKinematicsOperator.MODE_WHEELED_ROLLING,
                        new double[12], new double[12], new double[4],
                        new double[6], new double[6], new double[4][3],
                        new double[100], unnormalizedEmb, System.currentTimeMillis() * 1000
                )
        );
        assertTrue(ex.getMessage().contains("阿里千问特征向量未投影在单位超球面上"));
    }

    @Test
    @DisplayName("契约 3: 微观摩擦极限接触力封闭 Wrench Cone 闭式 QP 极速解析分配校验 (定理 1.2)")
    void testContract3_FrictionForceClosureClosedFormQP() {
        FrictionForceClosureDistributor distributor = new FrictionForceClosureDistributor(45.0, 0.1);

        double[] desiredWrench = new double[]{120.0, 0.0, 441.45, 0.0, 15.0, 0.0};
        double[] normalEst = new double[]{110.0, 110.0, 110.0, 110.0};
        double[] muCoeffs = new double[]{0.5, 0.5, 0.5, 0.5}; // 碎石路面摩擦系数
        double[] slipMeasured = new double[]{0.25, 0.25, 0.25, 0.25}; // 开环打滑率 25%

        // 预热消除初次类加载冷启动抖动
        distributor.allocate(desiredWrench, normalEst, muCoeffs, slipMeasured);

        FrictionForceClosureDistributor.AllocationResult result =
                distributor.allocate(desiredWrench, normalEst, muCoeffs, slipMeasured);

        assertNotNull(result);
        assertTrue(result.forceClosureMaintained(), "必须严格维持微观摩擦接触力封闭");
        assertTrue(result.forceClosureMargin() > 0.0, "接触力封闭裕度必须严格正定 (定理 1.2)");
        assertTrue(result.meanMicroSlipRatio() <= 0.02, "闭环微观打滑率必须削减 95% 以上 (<= 0.02)");
        assertTrue(result.latencyMicros() <= 100, "闭式 QP 力分配单步耗时必须 <= 100us");
    }

    @Test
    @DisplayName("契约 4: 局部非凸地形塌陷与轮端悬空空转主动抑制自愈校验")
    void testContract4_WheelSpinAnomalyIsolationAndSelfHealing() {
        FrictionForceClosureDistributor distributor = new FrictionForceClosureDistributor();

        double[] desiredWrench = new double[]{90.0, 0.0, 441.45, 0.0, 0.0, 0.0};
        double[] muCoeffs = new double[]{0.6, 0.6, 0.6, 0.6};

        // 假设右前轮 (index 1) 突遇塌陷完全悬空空转
        FrictionForceClosureDistributor.AllocationResult selfHealRes =
                distributor.handleWheelSpinAnomaly(1, desiredWrench, muCoeffs);

        assertNotNull(selfHealRes);
        assertEquals(0.0, selfHealRes.wheelTorquesNm()[1], 1e-6, "悬空轮驱动力矩必须立即切断清零以杜绝空转飞车");
        assertEquals(0.0, selfHealRes.normalForcesN()[1], 1e-6, "悬空轮法向力必须清零");
        assertTrue(selfHealRes.normalForcesN()[0] > 140.0, "其余三轮必须自适应承接全车重力与牵引力");
        assertTrue(selfHealRes.forceClosureMaintained(), "三点接触力封闭多面体必须重新闭合维持");
    }

    @Test
    @DisplayName("契约 5: 高动态越障瞬态接触冲量阻抗耗散与减速器过载保护校验 (定理 1.3)")
    void testContract5_ObstacleImpulseDampingProtection() {
        AntiToppleSafetyGate gate = new AntiToppleSafetyGate();

        double[] nominalTorques = new double[]{40.0, 40.0, 40.0, 40.0}; // 额定牵引力矩
        // 模拟撞击 20cm 垂直刚性水泥台阶瞬间检测到垂直法向加速度突跃 (70 m/s^2)
        double[] impactAcc = new double[]{10.0, 0.0, 70.0};

        AntiToppleSafetyGate.ImpulseDampingResult dampRes =
                gate.dissipateImpact(nominalTorques, impactAcc, 0.001);

        assertNotNull(dampRes);
        assertTrue(dampRes.peakTorqueReductionRatio() >= 0.60, "瞬态碰撞峰值力矩削减率必须 >= 60% 保护减速机");
        assertTrue(dampRes.dampedTorques()[0] <= 16.0, "阻抗耗散后输出力矩必须平滑限制在安全包络内");
        assertTrue(dampRes.absorbedEnergyJoules() > 0.0, "虚拟阻抗吸收耗散能量必须严格正定");
        assertTrue(dampRes.latencyMicros() <= 50, "冲量耗散单步耗时必须 <= 50us");
    }

    @Test
    @DisplayName("契约 6: 相对阶 r=2 防翻滚 HOCBF 闭式 QP 门禁与零翻覆硬保证校验 (定理 1.3)")
    void testContract6_RelativeDegree2AntiToppleHOCBF() {
        AntiToppleSafetyGate gate = new AntiToppleSafetyGate();

        double[] aggressiveTorques = new double[]{35.0, 35.0, 35.0, 35.0};
        // 模拟机身剧烈侧倾逼近极限 (roll = 32度，接近 35度极限)
        double[] rpyNearTopple = new double[]{Math.toRadians(32.0), Math.toRadians(5.0), 0.0};
        double[] angVelDiverging = new double[]{1.5, 0.2, 0.0}; // 横滚角速度正在恶化加剧倾覆

        // 预热消除初次类加载与 JIT 冷启动调度抖动
        for (int i = 0; i < 20; i++) {
            gate.evaluateAndProject(aggressiveTorques, rpyNearTopple, angVelDiverging, Math.toRadians(35.0));
        }

        AntiToppleSafetyGate.GateResult gateRes =
                gate.evaluateAndProject(aggressiveTorques, rpyNearTopple, angVelDiverging, Math.toRadians(35.0));

        assertNotNull(gateRes);
        assertTrue(gateRes.modifiedByHocbf(), "逼近侧翻边缘时 HOCBF 必须强制介入超平面投影修正");
        assertTrue(gateRes.toppleSafetyMargin() >= 0.0, "防翻滚安全裕度必须保持非负 (定理 1.3)");
        assertTrue(gateRes.latencyMicros() <= 50, "相对阶 r=2 HOCBF 闭式 QP 解析投影单步耗时必须 <= 50us");
    }

    @Test
    @DisplayName("契约 7: 1000Hz 定长 4096 槽位 Disruptor 无锁总线与 JitterGuard 软着陆趴地校验")
    void testContract7_DisruptorBusAndJitterGuardSoftLanding() {
        LegWheelControlBus bus = new LegWheelControlBus();
        double[] validEmb = createNormalizedQwenEmbedding();

        // 1. 高频纳秒级发布状态帧
        LegWheelStateFrame frame = new LegWheelStateFrame(
                "FRAME-TEST", "SES-80", "ROBOT-80", ReconfigurableKinematicsOperator.MODE_HYBRID_CLIMBING,
                new double[12], new double[12], new double[4],
                new double[6], new double[6], new double[4][3],
                new double[100], validEmb, System.currentTimeMillis() * 1000
        );

        long pubStart = System.nanoTime();
        boolean pubOk = bus.publishFrame(frame);
        long pubNanos = System.nanoTime() - pubStart;

        assertTrue(pubOk);
        assertTrue(pubNanos < 50_000L, "无锁环形总线写入耗时必须 <= 50微秒 (实测纳秒级)");

        // 2. 正常单步调度
        LegWheelLocomotionReceipt receipt = bus.dispatchStep(new double[]{1.0, 0.0, 0.0, 0.0, 0.0, 0.0}, "GRAVEL_SLOPE");
        assertNotNull(receipt);
        assertEquals(LegWheelControlBus.BUS_NORMAL, receipt.busState());

        // 3. JitterGuard 时钟抖动连续超限或异常触发软着陆
        bus.triggerEmergencyCrouch();
        assertEquals(LegWheelControlBus.BUS_DEGRADED_STABLE_CROUCH, bus.getCurrentBusState());

        LegWheelLocomotionReceipt emergencyReceipt = bus.dispatchStep(new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, "GRAVEL_SLOPE");
        assertEquals(LegWheelControlBus.BUS_DEGRADED_STABLE_CROUCH, emergencyReceipt.busState(),
                "异常工况下凭单必须存证 DEGRADED_STABLE_CROUCH 柔顺趴地软着陆状态");
    }

    @Test
    @DisplayName("契约 8: 不可变轮腿越障存证凭单 SHA-256 密码学签名验真与防篡改校验")
    void testContract8_ReceiptSha256VerificationAndTamperProof() {
        long now = System.currentTimeMillis();
        LegWheelLocomotionReceipt receipt = LegWheelLocomotionReceipt.createAndSign(
                "RCP-80-001", "SES-PROOF", "ROBOT-LW",
                "RIGID_STAIRS", ReconfigurableKinematicsOperator.MODE_HYBRID_CLIMBING,
                0.245, 1.25, 8.5, 0.82,
                42, 28, LegWheelControlBus.BUS_NORMAL, now
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原生创建的凭单密码学 SHA-256 自签名验真必须 100% 通过");

        // 篡改力封闭裕度数值
        LegWheelLocomotionReceipt tamperedReceipt = new LegWheelLocomotionReceipt(
                receipt.receiptId(), receipt.sessionId(), receipt.robotId(),
                receipt.terrainType(), receipt.topologyMode(),
                0.9999, // 篡改数值
                receipt.meanPitchRollDeviationDeg(), receipt.impulseDissipationWorkJ(),
                receipt.hocbfSafetyMargin(), receipt.kinematicsLatencyMicros(),
                receipt.qpLatencyMicros(), receipt.busState(), receipt.timestamp(),
                receipt.signature()
        );

        assertFalse(tamperedReceipt.verifySignature(), "篡改数值后的存证凭单验真必须立即失败 (防篡改拦截率 100%)");
    }
}
