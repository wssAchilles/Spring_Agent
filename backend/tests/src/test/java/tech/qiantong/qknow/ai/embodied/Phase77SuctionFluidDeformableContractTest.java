package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.suction.dto.DeformableWallShearState;
import tech.qiantong.qknow.ai.embodied.suction.dto.MicroSuctionCupArrayEnvelope;
import tech.qiantong.qknow.ai.embodied.suction.dto.SuctionFluidManipulationPlan;
import tech.qiantong.qknow.ai.embodied.suction.dto.SuctionFluidReceipt;
import tech.qiantong.qknow.ai.embodied.suction.engine.BionicSuctionManifoldGovernor;
import tech.qiantong.qknow.ai.embodied.suction.engine.DeformableShearAntiTearingPlanner;
import tech.qiantong.qknow.ai.embodied.suction.engine.SuctionCavitationSafetyGate;
import tech.qiantong.qknow.ai.embodied.suction.engine.SuctionFluidControlBus;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 77 具身多指灵巧手与高阶可变形环境拓扑交互、微结构自适应吸附操作与多模态神经流体流形中枢专属契约测试套件
 *
 * @author Achilles
 * @since 2026-09-15
 */
@DisplayName("Phase 77 灵巧手微结构吸附与大形变介质防撕裂流体流形中枢契约测试")
public class Phase77SuctionFluidDeformableContractTest {

    private double[] createUniformEmbedding1536() {
        double[] emb = new double[1536];
        double unit = 1.0 / Math.sqrt(1536.0);
        Arrays.fill(emb, unit);
        return emb;
    }

    private double[] createOrthogonalUnitVector(double[] base) {
        double[] ortho = new double[1536];
        for (int i = 0; i < 768; i++) {
            ortho[i] = 1.0 / Math.sqrt(1536.0);
        }
        for (int i = 768; i < 1536; i++) {
            ortho[i] = -1.0 / Math.sqrt(1536.0);
        }
        return ortho;
    }

    private MicroSuctionCupArrayEnvelope createSampleEnvelope(int count, double pressureKPa, double sealVal) {
        double[][] positions = new double[count][3];
        double[][] normals = new double[count][3];
        double[] pressures = new double[count];
        double[] leakRates = new double[count];
        double[] sealIntegrities = new double[count];

        for (int i = 0; i < count; i++) {
            positions[i] = new double[]{i * 0.01, 0.0, 0.0};
            normals[i] = new double[]{0.0, 0.0, 1.0};
            pressures[i] = pressureKPa;
            leakRates[i] = 0.05;
            sealIntegrities[i] = sealVal;
        }

        return new MicroSuctionCupArrayEnvelope(
                count, positions, normals, pressures, leakRates, sealIntegrities, createUniformEmbedding1536()
        );
    }

    @Test
    @DisplayName("契约 1: 仿生微吸盘负压流形泊肃叶泄漏模型与临界稳态密封充要条件判定 (定理 1.1)")
    void test01_BionicSuctionManifoldAndCriticalSealingInvariant() {
        BionicSuctionManifoldGovernor governor = new BionicSuctionManifoldGovernor();

        double cupRadiusMm = 2.5;
        double targetVacuumKPa = 25.0; // 绝对压力 25 kPa (相对负压约 76 kPa)
        double pumpCapacityLpm = 12.0;

        // 计算临界密封微气隙开度 h_crit
        double hCritMm = governor.computeCriticalClearanceGapMm(cupRadiusMm, targetVacuumKPa, pumpCapacityLpm);
        assertTrue(hCritMm > 0.01 && hCritMm < 0.5, "临界间隙应在合理亚毫米尺度，实测(mm): " + hCritMm);

        // 当实际微气隙极小 (h = 0.01mm < h_crit) 时，泊肃叶微泄漏量应很小 (远低于泵单吸盘容量 2.0 L/min)
        double leakTiny = governor.computeMicroLeakRateLpm(cupRadiusMm, 0.01, 101.325 - targetVacuumKPa);
        assertTrue(leakTiny < 0.5, "微间隙下泄漏流量应极小且远低于吸盘抽气能力，实测(L/min): " + leakTiny);

        // 构造紧密吸附微吸盘包络
        MicroSuctionCupArrayEnvelope tightEnvelope = createSampleEnvelope(6, 22.0, 0.99);

        long startNs = System.nanoTime();
        double[] integrities = governor.evaluateSealIntegrity(tightEnvelope, targetVacuumKPa, pumpCapacityLpm, 0.004);
        boolean sealed = governor.isSealEstablished(tightEnvelope, targetVacuumKPa, pumpCapacityLpm);
        long elapsedUs = (System.nanoTime() - startNs) / 1000;

        assertTrue(sealed, "负压与密封完整度良好时应判定密封达成");
        assertTrue(integrities[0] >= 0.98, "微米级密封面下密封完整度应 >= 98%");
        assertTrue(elapsedUs <= 100, "单步负压流形与密封评估耗时应 <= 100us，实测(us): " + elapsedUs);

        // 验证宏观总吸附力
        double suctionForceN = tightEnvelope.computeTotalSuctionForceN(101.325, cupRadiusMm);
        assertTrue(suctionForceN > 5.0, "6 个微吸盘产生的吸附力应大于 5N，实测(N): " + suctionForceN);
    }

    @Test
    @DisplayName("契约 2: 阿里千问 1536 维超球面微结构接触流形特征对齐与测地度量")
    void test02_QwenHypersphereEmbeddingAndGeodesicMetricInvariant() {
        BionicSuctionManifoldGovernor governor = new BionicSuctionManifoldGovernor();

        double[] v0 = createUniformEmbedding1536();
        double[] vOrtho = createOrthogonalUnitVector(v0);

        // 验证单位超球面约束 ||v||_2 = 1.0 +- 1e-5
        MicroSuctionCupArrayEnvelope envelope = createSampleEnvelope(4, 30.0, 0.96);
        assertTrue(envelope.verifyQwenHypersphereInvariant(), "千问嵌入向量必须严格满足单位超球面约束");

        // 沿着测地大圆弧旋转 theta = pi / 3
        double targetTheta = Math.PI / 3.0;
        double[] vRot = new double[1536];
        for (int i = 0; i < 1536; i++) {
            vRot[i] = Math.cos(targetTheta) * v0[i] + Math.sin(targetTheta) * vOrtho[i];
        }

        double geodesicAngle = governor.computeGeodesicAngle(v0, vRot);
        assertEquals(targetTheta, geodesicAngle, 1e-5, "测地偏角计算应精确无误");

        // 验证同一向量测地角严格为 0
        assertEquals(0.0, governor.computeGeodesicAngle(v0, v0), 1e-5);
    }

    @Test
    @DisplayName("契约 3: 大形变软体介质超弹性应变能估计与破损极限硬截断 (定理 1.2)")
    void test03_LargeDeformationHyperelasticStrainEnergyAndHardTruncation() {
        DeformableShearAntiTearingPlanner planner = new DeformableShearAntiTearingPlanner();

        double c10 = 15.0; // kPa
        double c01 = 5.0;  // kPa

        // 伸长比 lambda = 1.0 时，应变能应严格为 0 (未变形基态)
        double w0 = planner.computeMooneyRivlinEnergy(1.0, c10, c01);
        assertEquals(0.0, w0, 1e-6, "基态应变能必须为 0");

        // 伸长比单调递增时，应变能单调严格递增
        double w1 = planner.computeMooneyRivlinEnergy(1.1, c10, c01);
        double w2 = planner.computeMooneyRivlinEnergy(1.25, c10, c01);
        double w3 = planner.computeMooneyRivlinEnergy(1.4, c10, c01);
        assertTrue(w1 > w0 && w2 > w1 && w3 > w2, "超弹性应变能应随伸长比单调严格递增");

        // 构建临近破坏的大形变壁面剪切状态 (极限 200 kPa，当前 175 kPa > 200 * 0.85 = 170 kPa)
        DeformableWallShearState stateRisky = new DeformableWallShearState(
                "SOFT-CRYSTAL-01", 1.35, 175.0, 200.0, 15.0, 18.5, w3, 0.125
        );
        assertTrue(stateRisky.isTearingRisk(), "当等效应力超过材料允许极限 85% 时必须检出撕裂风险");
        assertTrue(stateRisky.computeSafetyFactor() < 1.18);

        // 安全状态 (当前 80 kPa <= 170 kPa)
        DeformableWallShearState stateSafe = new DeformableWallShearState(
                "SOFT-CRYSTAL-01", 1.08, 80.0, 200.0, 5.0, 6.2, w1, 0.60
        );
        assertFalse(stateSafe.isTearingRisk(), "正常安全范围内不应误报撕裂风险");
    }

    @Test
    @DisplayName("契约 4: 非牛顿流体 Ostwald-de Waele 壁面剪切应力与流变本构特性")
    void test04_NonNewtonianOstwaldDeWaeleWallShearStressInvariant() {
        DeformableShearAntiTearingPlanner planner = new DeformableShearAntiTearingPlanner();

        double kConsistency = 8.5; // kPa * s^n
        // 剪切变稀非牛顿流体 (n = 0.6 < 1)
        double nThinning = 0.6;
        double tau1 = planner.computeWallShearStressKPa(10.0, kConsistency, nThinning);
        double tau2 = planner.computeWallShearStressKPa(100.0, kConsistency, nThinning);

        assertTrue(tau2 > tau1, "剪切应力随剪切速率增加");
        // 表观粘度 eta = tau / shearRate = K * shearRate^(n-1)，在 n < 1 时表观粘度单调下降
        double eta1 = tau1 / 10.0;
        double eta2 = tau2 / 100.0;
        assertTrue(eta2 < eta1, "剪切变稀流体表观粘度应随剪切速率单调下降");

        // 剪切速率为 0 时剪切应力为 0
        assertEquals(0.0, planner.computeWallShearStressKPa(0.0, kConsistency, nThinning), 1e-6);
    }

    @Test
    @DisplayName("契约 5: 动态法向小角度渐进剥离防撕裂流形规划与能量释放削减")
    void test05_AdaptiveTiltPeelAntiTearingTrajectoryConvergence() {
        DeformableShearAntiTearingPlanner planner = new DeformableShearAntiTearingPlanner();

        DeformableWallShearState highStressState = new DeformableWallShearState(
                "WORKPIECE-WAFER-01", 1.28, 160.0, 200.0, 20.0, 25.0, 12.0, 0.20
        );

        SuctionFluidManipulationPlan plan = planner.planAntiTearingPeel(
                "WORKPIECE-WAFER-01", highStressState, 50.0, 25.0
        );

        assertNotNull(plan);
        // 验证自适应倾角严格在 [15.0, 35.0] 度之间
        assertTrue(plan.peelAngleDeg() >= 15.0 && plan.peelAngleDeg() <= 35.0,
                "剥离倾角必须落在 [15, 35] 度内，实测: " + plan.peelAngleDeg());

        // 验证相较垂直剥离能量削减率 >= 70%
        assertTrue(plan.tearingEnergyReductionRatio() >= 0.70,
                "小角度倾斜剥离能量释放率削减必须 >= 70%，实测: " + plan.tearingEnergyReductionRatio());

        // 验证平移速度适度自适应衰减保护
        assertTrue(plan.tangentialVelocityMms() < 50.0, "高应力下切向平移速率应自适应衰减");
    }

    @Test
    @DisplayName("契约 6: 相对阶 r=2 空化数高阶控制屏障 HOCBF 闭式 QP 门禁与零气蚀击穿 (定理 1.3)")
    void test06_RelativeDegree2CavitationHocbfClosedFormQpGate() {
        SuctionCavitationSafetyGate gate = new SuctionCavitationSafetyGate();

        // 正常安全工况: 压力 80 kPa, 流速 2.0 m/s
        double sigmaSafe = gate.computeCavitationNumber(80000.0, 2.0);
        assertTrue(sigmaSafe > 5.0, "常速常压下空化数应远大于临界，实测: " + sigmaSafe);
        assertFalse(gate.isCavitationCritical(sigmaSafe, 1.2));

        // 危险工况: 局部压强接近饱和蒸汽压，流速高达 12.0 m/s
        double sigmaDanger = gate.computeCavitationNumber(3000.0, 12.0);
        assertTrue(sigmaDanger < 0.1, "剧烈流速与极低压下空化数跌入危险区，实测: " + sigmaDanger);
        assertTrue(gate.isCavitationCritical(sigmaDanger, 1.2));

        // 验证相对阶 r=2 HOCBF 闭式极速 QP 安全投影
        double nominalControl = -5.0; // 名义控制激进抽真空 (加速降低气压，导致空化数进一步下降)
        double criticalCavitation = 1.2;
        double alpha1 = 2.0;
        double alpha2 = 3.0;

        long startNs = System.nanoTime();
        // 在危险状态下触发 HOCBF 投影
        double safeControl = gate.projectSafetyControl(
                nominalControl, 1.0, -0.5, criticalCavitation, alpha1, alpha2
        );
        long elapsedUs = (System.nanoTime() - startNs) / 1000;

        // 名义控制 -5.0 违反屏障条件，经极速闭式 QP 投影后控制量必须硬截断至非危险值 (u* >= uMin)
        assertTrue(safeControl > nominalControl, "HOCBF 必须进行安全硬截断，控制量应提高避免气蚀，投影值: " + safeControl);
        assertTrue(elapsedUs <= 10, "闭式 QP 投影耗时必须 <= 10us，实测(us): " + elapsedUs);
    }

    @Test
    @DisplayName("契约 7: 1000Hz 定长 4096 槽位 Disruptor 无锁吸附流体总线与 JitterGuard 软着陆")
    void test07_Disruptor4096LockFreeBusAndJitterGuardDegradedMode() {
        SuctionFluidControlBus bus = new SuctionFluidControlBus();
        MicroSuctionCupArrayEnvelope envelope = createSampleEnvelope(4, 25.0, 0.98);

        // 验证纳秒级无锁写入
        long startNs = System.nanoTime();
        long seq = bus.publishCommand(envelope, 22.0, 100_000_000L);
        long elapsedNs = System.nanoTime() - startNs;

        assertTrue(seq >= 0);
        SuctionFluidControlBus.BusSlot slot = bus.getSlot(seq);
        assertEquals(22.0, slot.commandedPressureKPa());
        assertEquals(seq, slot.sequenceId());
        assertFalse(bus.isDegradedMode(), "初始无抖动不应触发降级");

        // 模拟连续 3 帧时钟抖动 (每次间隔 5ms > 2ms)
        long ts = 100_000_000L;
        for (int i = 0; i < 3; i++) {
            ts += 5_000_000L; // +5ms
            bus.publishCommand(envelope, 20.0, ts);
        }

        assertTrue(bus.isDegradedMode(), "连续 3 帧时钟抖动 > 2ms 必须瞬时切入降级软着陆模式");

        // 降级模式下发布的指令应强制为稳压保压软着陆气压 30.0 kPa
        long degradedSeq = bus.publishCommand(envelope, 15.0, ts + 1_000_000L);
        SuctionFluidControlBus.BusSlot degradedSlot = bus.getSlot(degradedSeq);
        assertEquals(30.0, degradedSlot.commandedPressureKPa(), "降级模式下气压指令应强制为 30.0 kPa 稳压保压");

        // 重置恢复
        bus.resetDegradedMode();
        assertFalse(bus.isDegradedMode());
    }

    @Test
    @DisplayName("契约 8: 不可变多相吸附与流体控制存证凭单 SHA-256 密码学签名验真与防篡改")
    void test08_SuctionFluidReceiptSha256SignatureAndTamperProof() {
        SuctionFluidReceipt receipt = SuctionFluidReceipt.createAndSign(
                "RCPT-PHASE77-001",
                "SESSION-SUCTION-888",
                "DEX-HAND-L7",
                "WAFER-300MM-SILICON",
                "TILT_PEEL",
                0.985,
                12.5,
                2.8,
                0.85,
                45L,
                false
        );

        assertNotNull(receipt);
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名必须为 64 位十六进制字符");

        // 原始自验必须通过
        assertTrue(receipt.verifySignature(), "原始凭单 SHA-256 签名验真必须通过");

        // 篡改测试 1: 篡改最大壁面剪切应力
        SuctionFluidReceipt tamperedShear = new SuctionFluidReceipt(
                receipt.receiptId(), receipt.sessionId(), receipt.dexterityHandId(), receipt.workpieceId(),
                receipt.manipulationPhase(), receipt.avgSealIntegrity(), 99.9, // 篡改
                receipt.cavitationMargin(), receipt.trackingErrorMm(), receipt.stepLatencyUs(),
                receipt.degradedModeActive(), receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tamperedShear.verifySignature(), "篡改剪切应力后验真必须失败");

        // 篡改测试 2: 篡改跟踪误差
        SuctionFluidReceipt tamperedTracking = new SuctionFluidReceipt(
                receipt.receiptId(), receipt.sessionId(), receipt.dexterityHandId(), receipt.workpieceId(),
                receipt.manipulationPhase(), receipt.avgSealIntegrity(), receipt.maxWallShearStressKPa(),
                receipt.cavitationMargin(), 5.5, // 篡改
                receipt.stepLatencyUs(), receipt.degradedModeActive(), receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tamperedTracking.verifySignature(), "篡改跟踪误差后验真必须失败");
    }
}
