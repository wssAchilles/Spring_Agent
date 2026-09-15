package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.wbc.dto.LocomanipulationTaskPriority;
import tech.qiantong.qknow.ai.embodied.wbc.dto.WholeBodyControlReceipt;
import tech.qiantong.qknow.ai.embodied.wbc.dto.WholeBodyState;
import tech.qiantong.qknow.ai.embodied.wbc.engine.CentroidalMomentumGovernor;
import tech.qiantong.qknow.ai.embodied.wbc.engine.HierarchicalWbcOptimizer;
import tech.qiantong.qknow.ai.embodied.wbc.engine.NonStationaryContactForceDistributor;
import tech.qiantong.qknow.ai.embodied.wbc.engine.WholeBodyControlBus;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 70 专属契约单元测试：具身智能体多足/轮臂移动操作全身动力学协同 (WBC)、动态质心动量平衡与非平稳接触抓取中枢
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class Phase70WholeBodyLocomanipulationContractTest {

    private CentroidalMomentumGovernor momentumGovernor;
    private HierarchicalWbcOptimizer wbcOptimizer;
    private NonStationaryContactForceDistributor forceDistributor;
    private WholeBodyControlBus controlBus;

    private static final int QWEN_DIM = 1536;

    @BeforeEach
    void setUp() {
        momentumGovernor = new CentroidalMomentumGovernor(45.0, 9.81, 20.0);
        wbcOptimizer = new HierarchicalWbcOptimizer();
        forceDistributor = new NonStationaryContactForceDistributor(3.0, 0.40, 30.0, 250.0);
        controlBus = new WholeBodyControlBus();
    }

    private double[] createNormalizedQwenEmbedding(long seed) {
        Random random = new Random(seed);
        double[] v = new double[QWEN_DIM];
        double sumSq = 0.0;
        for (int i = 0; i < QWEN_DIM; i++) {
            v[i] = random.nextGaussian();
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < QWEN_DIM; i++) {
            v[i] /= norm;
        }
        return v;
    }

    private WholeBodyState createSampleState(double[] baseAcc, double[] comAcc, double[] lDot) {
        double[] basePose = new double[]{0.0, 0.0, 0.45, 0.0, 0.0, 0.0};
        double[] baseVel = new double[]{0.5, 0.0, 0.0, 0.0, 0.0, 0.0};
        double[] jointAngles = new double[]{0.0, 0.5, -1.0, 0.0, 0.5, 0.0};
        double[] jointVels = new double[]{0.1, -0.1, 0.2, 0.0, 0.1, 0.0};
        double[] comPos = new double[]{0.05, 0.0, 0.40};
        double[] comVel = new double[]{0.5, 0.0, 0.0};

        // 四足/轮式四角支撑多边形: [-0.3, -0.2] 到 [0.3, 0.2]
        double[][] contactPoints = new double[][]{
                {0.30, -0.20},
                {0.30, 0.20},
                {-0.30, 0.20},
                {-0.30, -0.20}
        };

        double[] semanticEmbedding = createNormalizedQwenEmbedding(42L);

        return new WholeBodyState(
                basePose, baseVel, baseAcc, jointAngles, jointVels,
                comPos, comVel, comAcc, lDot, contactPoints, semanticEmbedding
        );
    }

    @Test
    @DisplayName("契约测试 1: 质心动量矩阵 CMM 与动态 ZMP 解析坐标计算及稳态/动态偏离度度量")
    void test01_CentroidalMomentumGovernor_CmmAndDynamicZmpCalculation() {
        // 1. 静态稳态工况 (加速度与动量变化率为 0)
        WholeBodyState steadyState = createSampleState(
                new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[]{0.0, 0.0, 0.0},
                new double[]{0.0, 0.0, 0.0}
        );

        double[] steadyZmp = momentumGovernor.calculateDynamicZmp(steadyState);
        assertEquals(steadyState.comPosition()[0], steadyZmp[0], 1e-4, "稳态下动态 ZMP x 坐标应与静态质心对齐");
        assertEquals(steadyState.comPosition()[1], steadyZmp[1], 1e-4, "稳态下动态 ZMP y 坐标应与静态质心对齐");

        // 2. 动态加速工况 (前向加速度 ax=2.5 m/s^2, 绕y轴角动量变化率 lDot_y=20.0 Nm)
        WholeBodyState dynamicState = createSampleState(
                new double[]{2.5, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[]{2.5, 0.0, 0.0},
                new double[]{0.0, 20.0, 0.0}
        );

        double[] dynamicZmp = momentumGovernor.calculateDynamicZmp(dynamicState);
        assertTrue(dynamicZmp[0] < steadyZmp[0], "前向加速时动态 ZMP 应向后偏移");

        double cmmNorm = momentumGovernor.calculateCentroidalMomentumNorm(dynamicState);
        assertTrue(cmmNorm > 0.0, "质心动量综合范数必须严格正定");

        // 验证千问 1536 维超球面归一化
        assertTrue(dynamicState.isHypersphericalNormalized(), "千问特征向量必须满足模长等于 1.0");
    }

    @Test
    @DisplayName("契约测试 2: 质心动量 ZMP 逼近倾翻边缘时 HOCBF 高阶控制屏障软着陆介入")
    void test02_CentroidalMomentumGovernor_HocbfTipOverBoundaryIntervention() {
        // 构造倾翻临界状态：ZMP 靠近前边缘，安全裕度仅 10mm (<= 20mm 阈值)
        WholeBodyState criticalState = createSampleState(
                new double[]{3.5, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[]{3.5, 0.0, 0.0},
                new double[]{0.0, 15.0, 0.0}
        );

        double criticalMarginMm = 10.0; // 模拟距边界 10mm
        CentroidalMomentumGovernor.HocbfInterventionResult result =
                momentumGovernor.evaluateHocbfIntervention(criticalState, criticalMarginMm);

        assertTrue(result.intervened(), "ZMP 距边界 <= 20mm 时必须触发 HOCBF 软着陆干预");
        assertTrue(Math.abs(result.regulatedBaseAcceleration()[0]) < Math.abs(criticalState.baseAcceleration()[0]),
                "底盘加速度必须被自适应削减");
        assertTrue(result.antiTipTorque()[1] < 0, "必须生成反倾翻反向俯仰力矩");
        assertTrue(result.regulatedZmpMarginMm() >= 20.0, "调节后 ZMP 安全裕度必须回弹至安全阈值以上");
    }

    @Test
    @DisplayName("契约测试 3: 分层二次规划 WBC 四级优先级零空间严格正交投影解耦 (J1 * N1 * qddot2 == 0)")
    void test03_HierarchicalWbcOptimizer_FourPriorityDecoupling() {
        int dof = 12; // 6 浮动基 + 6 关节

        // 构造第一优先级雅可比 J1 (3x12)
        double[][] J1 = new double[3][dof];
        for (int i = 0; i < 3; i++) {
            J1[i][i] = 1.0;
            J1[i][i + 3] = 0.5;
        }

        // 计算第一级零空间正交投影算子 N1 (12x12)
        double[][] N1 = wbcOptimizer.computeNullSpaceProjector(J1, dof);

        // 构造第二级任意非零加速度指令
        double[] qddot2 = new double[dof];
        for (int i = 0; i < dof; i++) {
            qddot2[i] = (i + 1) * 2.0;
        }

        // 验证定理 1.1：J1 * N1 * qddot2 的欧氏范数严格等于 0 (< 1e-6)
        double interferenceNorm = wbcOptimizer.computeInterferenceNorm(J1, N1, qddot2, dof);
        assertEquals(0.0, interferenceNorm, 1e-6, "低优先级任务对第一级平衡任务的加速度干涉必须严格为 0 (零空间正交不变量)");
    }

    @Test
    @DisplayName("契约测试 4: 分层 WBC 在机械臂奇异点处的 DLS 阻尼截断与力矩安全边界")
    void test04_HierarchicalWbcOptimizer_DlsSingularityRobustness() {
        int dof = 12;

        // 构造病态/奇异雅可比矩阵 (各行近乎线性相关)
        double[][] J_singular = new double[3][dof];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < dof; j++) {
                J_singular[i][j] = 0.001 * (i + 1);
            }
        }

        double[] a_des = new double[]{10.0, 10.0, 10.0};

        // JIT 预热消除冷启动编译偏差
        for (int w = 0; w < 20; w++) {
            wbcOptimizer.solveHierarchicalWbc(
                    J_singular, a_des,
                    J_singular, a_des,
                    J_singular, a_des,
                    J_singular, a_des,
                    dof
            );
        }

        HierarchicalWbcOptimizer.WbcOptimizationResult result = wbcOptimizer.solveHierarchicalWbc(
                J_singular, a_des,
                J_singular, a_des,
                J_singular, a_des,
                J_singular, a_des,
                dof
        );

        // 1. 验证求解时间 <= 2.0ms (在 CI 共享虚拟核下保证健壮性，实测预热后 < 0.1ms)
        assertTrue(result.solvingTimeMs() <= 2.0, "WBC 单步解析求解耗时必须 <= 2.0ms，实测=" + result.solvingTimeMs() + "ms");

        // 2. 验证关节力矩绝对值硬截断 <= 150.0 Nm
        for (double tau : result.jointTorques()) {
            assertTrue(Math.abs(tau) <= 150.0, "奇异点处关节驱动力矩必须硬截断在 150Nm 以内，实测=" + tau);
        }
    }

    @Test
    @DisplayName("契约测试 5: 底盘加减速冲击下末端工件惯性剪切力前馈补偿与法向力自适应补强")
    void test05_NonStationaryContact_ChassisAccelerationFeedforwardCompensation() {
        // 底盘经历 +/- 2.0 m/s^2 紧急制动加速度，工件质量 3.0kg
        double[] baseAcc = new double[]{2.0, 0.0, 0.0};
        double[] shear = forceDistributor.calculateInertialShearForce(baseAcc);

        assertEquals(-6.0, shear[0], 1e-4, "3kg 工件在 2m/s^2 加速度下产生 6N 惯性剪切力");

        NonStationaryContactForceDistributor.ContactForceAllocationResult alloc =
                forceDistributor.distributeContactForce(baseAcc, 5.0);

        // 验证法向力自适应补强
        assertTrue(alloc.normalClampingForceN() > 30.0, "必须根据惯性剪切力前馈提升法向夹持力");
        assertTrue(alloc.normalClampingForceN() <= 250.0, "法向力不得超过防压损极限 250N");
    }

    @Test
    @DisplayName("契约测试 6: 移动操作接触状态库伦摩擦锥内部保持与滑脱发生率严格零渗透 (定理 1.3)")
    void test06_NonStationaryContact_FrictionConeZeroSlipInvariant() {
        // 模拟多组剧烈底盘加速度冲击
        double[][] testAccels = new double[][]{
                {1.5, 0.0, 0.0},
                {-2.0, 0.5, 0.0},
                {0.0, -1.8, 0.0},
                {2.0, 1.0, 0.0}
        };

        for (double[] acc : testAccels) {
            NonStationaryContactForceDistributor.ContactForceAllocationResult res =
                    forceDistributor.distributeContactForce(acc, 8.0);

            // 摩擦锥裕度 margin = mu * f_n - ||f_t|| > 0
            assertTrue(res.frictionConeMarginN() > 0.0,
                    "接触力必须严格收缩在库伦摩擦锥内部，裕度必须为正: " + res.frictionConeMarginN());
            assertEquals(0.0, res.slipProbability(), 1e-6,
                    "定理 1.3 保证抓取微滑脱发生率严格为 0.0");
        }
    }

    @Test
    @DisplayName("契约测试 7: 1000Hz 定长 4096 槽位无锁总线高频吞吐与 JitterGuard 时钟抖动软着陆熔断")
    void test07_WholeBodyControlBus_HighFrequencyThroughputAndJitterGuard() {
        WholeBodyState state = createSampleState(
                new double[]{0.0, 0.0, 0.0},
                new double[]{0.0, 0.0, 0.0},
                new double[]{0.0, 0.0, 0.0}
        );

        // 1. 测试 4096 槽位无锁总线 10,000 次高频写入与读取
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            controlBus.publishState(state);
        }
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration <= 50, "10,000 次无锁总线写入必须在 50ms 内完成，实测=" + duration + "ms");
        assertNotNull(controlBus.getLatestState(), "最新状态帧必须可立即非阻塞获取");

        // 2. 测试 JitterGuard 连续 3 帧时钟抖动 (> 2ms) 触发软着陆降级
        assertFalse(controlBus.isDegradedMode(), "初始状态不应处于降级模式");

        controlBus.stepClockAndCheckJitter(4.5); // 第 1 帧抖动 3.5ms
        assertFalse(controlBus.isDegradedMode());

        controlBus.stepClockAndCheckJitter(4.8); // 第 2 帧抖动 3.8ms
        assertFalse(controlBus.isDegradedMode());

        controlBus.stepClockAndCheckJitter(5.0); // 第 3 帧抖动 4.0ms -> 连续满 3 帧触发
        assertTrue(controlBus.isDegradedMode(), "连续 3 帧抖动 > 2ms 必须触发 DEGRADED_GRAVITY_COMP 软着陆");
        assertEquals("CONSECUTIVE_JITTER_3_FRAMES", controlBus.getDegradationReason());

        // 3. 测试单帧致命时延 (> 20ms) 触发软着陆
        controlBus.resetBus();
        controlBus.stepClockAndCheckJitter(25.0); // 单帧 25ms 严重卡顿
        assertTrue(controlBus.isDegradedMode(), "单帧时延 > 20ms 必须瞬间触发软着陆降级");
    }

    @Test
    @DisplayName("契约测试 8: 不可变全身执行存证凭单 SHA-256 密码学签名自验与篡改防御")
    void test08_WholeBodyControlReceipt_Sha256TamperProofVerification() {
        WholeBodyControlReceipt receipt = controlBus.issueReceipt(
                "RECEIPT-WBC-20260915-001",
                25.4,   // ZMP 安全裕度 25.4mm
                18.2,   // 质心动量范数
                0.15,   // QP 耗时 0.15ms
                12.8,   // 摩擦锥裕度 12.8N
                true    // 防滑脱触发
        );

        // 1. 签名自验通过
        assertTrue(receipt.verifySignature(), "初始签发的存证凭单 SHA-256 签名自验必须通过");

        // 2. 伪造与篡改防御测试 (修改任一数值字段)
        WholeBodyControlReceipt tamperedReceipt = new WholeBodyControlReceipt(
                receipt.receiptId(),
                receipt.timestampEpochMs(),
                999.9, // 恶意篡改 ZMP 裕度
                receipt.centroidalMomentumNorm(),
                receipt.qpSolvingTimeMs(),
                receipt.frictionConeMargin(),
                receipt.antiSlipIntervened(),
                receipt.degradedMode(),
                receipt.degradationReason(),
                receipt.sha256Signature() // 沿用旧签名
        );

        assertFalse(tamperedReceipt.verifySignature(), "字段篡改后 SHA-256 签名验真必须失败");
    }
}
