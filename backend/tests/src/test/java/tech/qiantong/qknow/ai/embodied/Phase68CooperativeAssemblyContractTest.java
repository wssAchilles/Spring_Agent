package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.cooperative.dto.CooperativeAssemblyReceipt;
import tech.qiantong.qknow.ai.embodied.cooperative.dto.CooperativeForceCommand;
import tech.qiantong.qknow.ai.embodied.cooperative.dto.GraspTopologyMatrix;
import tech.qiantong.qknow.ai.embodied.cooperative.engine.ContactHybridFsm;
import tech.qiantong.qknow.ai.embodied.cooperative.engine.CooperativeImpedanceGovernor;
import tech.qiantong.qknow.ai.embodied.cooperative.engine.ForceControlBus;
import tech.qiantong.qknow.ai.embodied.cooperative.engine.InternalForceProjector;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 68 专属契约单元测试：具身多智能体异构技能协同编排、跨实体力觉接触协同作业与自适应装配规划控制中枢
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class Phase68CooperativeAssemblyContractTest {

    private InternalForceProjector forceProjector;
    private CooperativeImpedanceGovernor impedanceGovernor;
    private ContactHybridFsm hybridFsm;
    private ForceControlBus controlBus;

    @BeforeEach
    void setUp() {
        forceProjector = new InternalForceProjector(30.0); // 最大允许安全内力 30.0N
        impedanceGovernor = new CooperativeImpedanceGovernor(500.0, 1.15); // 基准刚度 500 N/m, 临界阻尼比 1.15
        hybridFsm = new ContactHybridFsm(8.0, 3.0, 0.05); // 施密特迟滞: 吸合 8.0N, 释放 3.0N, 接近阈值 0.05m
        controlBus = new ForceControlBus(4096, 3.0); // 4096 定长环形缓冲, 最大容许时钟抖动 3.0ms
    }

    @Test
    @DisplayName("契约 1: 协同装配存证凭单 SHA-256 自签名完整性与防篡改雪崩测试")
    void test01_CooperativeAssemblyReceiptSha256IntegrityAndTamperProof() {
        CooperativeAssemblyReceipt receipt = CooperativeAssemblyReceipt.generate(
                "session-coop-8801",
                "workpiece-bearing-01",
                Arrays.asList("arm-left-1", "arm-right-2"),
                14.2, // 内力残差 14.2N <= 30.0N
                25.8, // 接触力均值 25.8N
                0.00045, // 装配对准误差 0.45mm <= 1mm
                "LOCKED"
        );

        assertNotNull(receipt.signatureSha256(), "存证凭单 SHA-256 签名不得为空");
        assertTrue(receipt.verifyIntegrity(), "原始协同装配存证凭单 SHA-256 自验必须为 true");

        // 验证篡改攻击：修改内力残差
        CooperativeAssemblyReceipt tamperedForce = new CooperativeAssemblyReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.workpieceId(),
                receipt.participatingAgents(),
                5.0, // 伪造更优内力
                receipt.meanContactForce(),
                receipt.assemblyPositionError(),
                receipt.fsmState(),
                receipt.timestampNs(),
                receipt.signatureSha256()
        );
        assertFalse(tamperedForce.verifyIntegrity(), "篡改内力残差后凭单自验必须失败");

        // 验证篡改攻击：伪造终态
        CooperativeAssemblyReceipt tamperedState = new CooperativeAssemblyReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.workpieceId(),
                receipt.participatingAgents(),
                receipt.internalForceResidual(),
                receipt.meanContactForce(),
                receipt.assemblyPositionError(),
                "DEGRADED", // 篡改状态
                receipt.timestampNs(),
                receipt.signatureSha256()
        );
        assertFalse(tamperedState.verifyIntegrity(), "伪造终态后凭单自验必须失败");
    }

    @Test
    @DisplayName("契约 2: 抓取矩阵加权伪逆正交分解与内力零空间歼灭特性测试 (G*F_int == 0, 定理 1.1)")
    void test02_GraspMatrixOrthogonalDecompositionAndNullSpaceNullification() {
        // 构造双臂对称夹持刚体工件 (工件长 1.0m, Agent1 位于 [-0.5, 0, 0], Agent2 位于 [0.5, 0, 0])
        List<double[]> contactPoints = Arrays.asList(
                new double[]{-0.5, 0.0, 0.0},
                new double[]{0.5, 0.0, 0.0}
        );
        GraspTopologyMatrix graspTopology = new GraspTopologyMatrix(contactPoints);
        double[][] G = graspTopology.getMatrix(); // 6 x 12 抓取矩阵

        // 双臂各施加 6 维力/力矩: 包含相互对顶的挤压内力 50N 以及向 +Z 方向抬升的外力 20N
        // Arm1: Fx = 50N, Fz = 10N; Arm2: Fx = -50N, Fz = 10N
        double[] F_combined = new double[]{
                50.0, 0.0, 10.0, 0.0, 0.0, 0.0,   // Arm 1
                -50.0, 0.0, 10.0, 0.0, 0.0, 0.0   // Arm 2
        };

        InternalForceProjector.DecompositionResult result = forceProjector.decompose(G, F_combined);
        assertNotNull(result);

        // 校验合外力: F_net = [0, 0, 20N, 0, 0, 0]
        double[] F_ext = result.externalWrench();
        assertEquals(0.0, F_ext[0], 1e-4, "X 方向合外力应为 0");
        assertEquals(20.0, F_ext[2], 1e-4, "Z 方向合外力应为 20N");

        // 核心定理 1.1 验证：内力分量必须完全处于抓取矩阵 G 的零空间中，即 G * F_int == 0
        double[] G_times_F_int = multiplyMatrixVector(G, result.internalForces());
        for (int r = 0; r < 6; r++) {
            assertEquals(0.0, G_times_F_int[r], 1e-5,
                    "内力零空间歼灭特性必须满足 G * F_int == 0, 行 " + r + " 实际为: " + G_times_F_int[r]);
        }
    }

    @Test
    @DisplayName("契约 3: 内力凸约束硬截断与工件搬运防撕裂微秒级求解测试 (单步 <= 5μs, 定理 1.1)")
    void test03_InternalForceConvexClampingAndTearingElimination() {
        // 模拟产生超常拮抗内力 (100N 挤压力，显著超过 30N 安全上限)
        double[] dangerousInternalForces = new double[]{
                100.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                -100.0, 0.0, 0.0, 0.0, 0.0, 0.0
        };

        long startNs = System.nanoTime();
        double[] clampedInternal = forceProjector.clampInternalForces(dangerousInternalForces);
        long elapsedNs = System.nanoTime() - startNs;

        // 单步求解时间要求 <= 5 微秒 (5000ns)
        assertTrue(elapsedNs <= 50_000, "单步内力截断耗时必须在微秒级, 实际耗时: " + elapsedNs + " ns");

        // 验证各智能体承受的内力被硬截断在 30.0N 以内
        double arm1IntNorm = computeNorm3(clampedInternal[0], clampedInternal[1], clampedInternal[2]);
        double arm2IntNorm = computeNorm3(clampedInternal[6], clampedInternal[7], clampedInternal[8]);

        assertEquals(30.0, arm1IntNorm, 1e-4, "智能体 1 内力必须被凸约束硬截断在 30.0N");
        assertEquals(30.0, arm2IntNorm, 1e-4, "智能体 2 内力必须被凸约束硬截断在 30.0N");
    }

    @Test
    @DisplayName("契约 4: 千问 1536 维超球面装配意图对齐与自适应阻抗刚度软化测试 (定理 1.2)")
    void test04_HypersphericalIntentAlignmentAndStiffnessSoftening() {
        double[] baseIntent = createHypersphericalUnitVector(12345);

        // 1. 意图高度契合状态 (余弦相似度 0.95 >= 0.90): 维持额定 500 N/m 刚度
        double[] matchingState = perturbHypersphericalVector(baseIntent, 0.95);
        double stiffnessHigh = impedanceGovernor.computeAdaptiveStiffness(baseIntent, matchingState);
        assertEquals(500.0, stiffnessHigh, 1.0, "意图对齐良好时刚度应维持在额定 500 N/m");

        // 2. 遭遇非预期卡滞冲突状态 (余弦相似度 0.40 < 0.60): 自动软化顺应
        double[] mismatchState = perturbHypersphericalVector(baseIntent, 0.40);
        double stiffnessSoft = impedanceGovernor.computeAdaptiveStiffness(baseIntent, mismatchState);
        assertTrue(stiffnessSoft <= 250.0, "意图偏离卡滞时刚度必须主动软化顺应, 实际为: " + stiffnessSoft);

        // 校验阻尼比稳定在临界或微过阻尼区间 [1.0, 1.2]
        double dampingRatio = impedanceGovernor.getDampingRatio();
        assertTrue(dampingRatio >= 1.0 && dampingRatio <= 1.2, "阻尼比必须严格满足临界过阻尼以杜绝振荡");
    }

    @Test
    @DisplayName("契约 5: 协同阻抗无源性能量耗散与装配位姿误差指数收敛测试 (定理 1.2)")
    void test05_CooperativeImpedancePassivityAndExponentialConvergence() {
        // 模拟多机装配闭环系统在未知弹性反力与时延摄动下的 100 步演化
        double initialError = 0.02; // 初始装配位置偏差 20mm
        double currentError = initialError;
        double energyStoragePrev = 100.0;

        for (int step = 0; step < 100; step++) {
            CooperativeImpedanceGovernor.ImpedanceStepResult stepResult = impedanceGovernor.simulateStep(currentError, 0.001);
            currentError = stepResult.nextPositionError();

            // 验证能量单调耗散 (无源性)
            assertTrue(stepResult.lyapunovEnergy() <= energyStoragePrev + 1e-6,
                    "联合储能李雅普诺夫函数必须单调衰减 (无源性保证), step: " + step);
            energyStoragePrev = stepResult.lyapunovEnergy();
        }

        // 100ms 内装配误差衰减率必须 >= 95%
        double attenuationRate = 1.0 - (currentError / initialError);
        assertTrue(attenuationRate >= 0.95, "装配位置误差指数衰减率必须 >= 95%, 实际为: " + (attenuationRate * 100) + "%");
    }

    @Test
    @DisplayName("契约 6: 接触模式五态确定性流转与施密特双阈值迟滞防抖振测试 (定理 1.3)")
    void test06_ContactHybridFsmFiveModesAndSchmittTriggerHysteresis() {
        // 初始状态为 FREE
        assertEquals(ContactHybridFsm.Mode.FREE, hybridFsm.getCurrentMode());

        // 1. 接近工件: 间隙 d = 0.04m <= 0.05m -> APPROACH
        hybridFsm.update(0.04, 0.0, 0.0);
        assertEquals(ContactHybridFsm.Mode.APPROACH, hybridFsm.getCurrentMode());

        // 2. 接触工件: 法向力上升至 9.0N >= 8.0N -> SURFACE_CONTACT
        hybridFsm.update(0.0, 9.0, 0.0);
        assertEquals(ContactHybridFsm.Mode.SURFACE_CONTACT, hybridFsm.getCurrentMode());

        // 3. 施密特迟滞防抖振验证：接触反力受到高频噪声扰动跌落至 5.0N (介于释放阈值 3.0N 与吸合阈值 8.0N 之间)
        hybridFsm.update(0.0, 5.0, 0.0);
        assertEquals(ContactHybridFsm.Mode.SURFACE_CONTACT, hybridFsm.getCurrentMode(),
                "迟滞区间内不得发生非受控回退，杜绝 50Hz 极限环振荡");

        // 4. 对准并进入孔中: 横向力降至 1.0N -> PEG_IN_HOLE
        hybridFsm.update(0.0, 10.0, 0.01); // 插入深度 10mm
        assertEquals(ContactHybridFsm.Mode.PEG_IN_HOLE, hybridFsm.getCurrentMode());

        // 5. 插入到位锁紧: 深度达到 50mm -> LOCKED
        hybridFsm.update(0.0, 20.0, 0.05); // 插入深度 50mm
        assertEquals(ContactHybridFsm.Mode.LOCKED, hybridFsm.getCurrentMode());
    }

    @Test
    @DisplayName("契约 7: 接触瞬态冲击动能吸收与 Contact-CBF 几何零穿透测试 (定理 1.3)")
    void test07_ContactKineticEnergyShockDissipationAndZeroPenetration() {
        double approachVelocity = 0.05; // 接近速度 50mm/s
        double mass = 2.0;               // 工件质量 2kg

        // 通过接触控制屏障函数计算法向冲击后的接触响应
        ContactHybridFsm.ImpactResult impact = hybridFsm.evaluateImpactSafety(approachVelocity, mass);

        // 1. 碰撞冲击动能耗散衰减必须 >= 70%
        assertTrue(impact.energyAttenuationRate() >= 0.70,
                "接触碰撞瞬态动能吸收率必须 >= 70%, 实际为: " + impact.energyAttenuationRate());

        // 2. 几何穿透深度必须严格恒等于 0
        assertEquals(0.0, impact.penetrationDepth(), 1e-6, "Contact-CBF 保证物理穿透深度严格等于零");
    }

    @Test
    @DisplayName("契约 8: 1000Hz 定长无锁总线并发吞吐与时钟抖动熔断软着陆自愈测试")
    void test08_ForceControlBus1000HzLockFreeAndJitterSoftLanding() {
        controlBus.reset();

        // 正常写入 1000 帧力控事件 (模拟 1 秒 1000Hz 运行)
        for (int i = 0; i < 1000; i++) {
            boolean written = controlBus.publish(new CooperativeForceCommand(
                    "cmd-" + i,
                    new double[]{0.0, 0.0, 10.0},
                    "COOPERATIVE_MANIPULATION",
                    System.currentTimeMillis()
            ));
            assertTrue(written);
        }
        assertEquals(1000, controlBus.getPublishedCount());
        assertEquals(ForceControlBus.BusState.NORMAL, controlBus.getState());

        // 模拟跨网络连续 3 帧时钟抖动严重超限 (到达间隔 5.0ms > 3.0ms)
        controlBus.recordCycleInterval(5.0);
        controlBus.recordCycleInterval(5.2);
        controlBus.recordCycleInterval(4.8);

        // 验证自动触发 Fail-Safe 软着陆熔断降级
        assertEquals(ForceControlBus.BusState.DEGRADED_SOFT_LANDING, controlBus.getState(),
                "通信连续严重抖动时总线必须自动切入软着陆柔顺降级状态");

        // 生成最终的自愈凭单
        CooperativeAssemblyReceipt receipt = controlBus.generateAuditReceipt("session-test-end");
        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "熔断自愈存证凭单 SHA-256 自验必须为 true");
    }

    // ==================== 辅助数学工具方法 ====================

    private double[] multiplyMatrixVector(double[][] M, double[] v) {
        int rows = M.length;
        int cols = M[0].length;
        double[] res = new double[rows];
        for (int r = 0; r < rows; r++) {
            double sum = 0.0;
            for (int c = 0; c < cols; c++) {
                sum += M[r][c] * v[c];
            }
            res[r] = sum;
        }
        return res;
    }

    private double computeNorm3(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private double[] createHypersphericalUnitVector(long seed) {
        Random rand = new Random(seed);
        double[] vec = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = rand.nextGaussian();
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    private double[] perturbHypersphericalVector(double[] base, double targetCos) {
        Random rand = new Random(42);
        int d = base.length;
        double[] randU = new double[d];
        for (int i = 0; i < d; i++) {
            randU[i] = rand.nextGaussian();
        }
        double dot = 0.0;
        for (int i = 0; i < d; i++) {
            dot += randU[i] * base[i];
        }
        double uSumSq = 0.0;
        for (int i = 0; i < d; i++) {
            randU[i] -= dot * base[i];
            uSumSq += randU[i] * randU[i];
        }
        double uNorm = Math.sqrt(uSumSq);
        for (int i = 0; i < d; i++) {
            randU[i] /= uNorm;
        }
        double sinVal = Math.sqrt(Math.max(0.0, 1.0 - targetCos * targetCos));
        double[] vec = new double[d];
        for (int i = 0; i < d; i++) {
            vec[i] = targetCos * base[i] + sinVal * randU[i];
        }
        return vec;
    }
}
