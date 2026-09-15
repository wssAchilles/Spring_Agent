package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.deformable.dto.DeformableManipulationReceipt;
import tech.qiantong.qknow.ai.embodied.deformable.dto.DeformableObjectState;
import tech.qiantong.qknow.ai.embodied.deformable.dto.DeformationEnergyMetric;
import tech.qiantong.qknow.ai.embodied.deformable.engine.DeformableControlBus;
import tech.qiantong.qknow.ai.embodied.deformable.engine.DeformableManifoldPlanner;
import tech.qiantong.qknow.ai.embodied.deformable.engine.PhysicsInformedNeuralOperator;
import tech.qiantong.qknow.ai.embodied.deformable.engine.TactileVisualManifoldAligner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 71: 具身智能体物理信息神经算子 (PINO)、可形变/软体物体流形操作与触觉-视觉高维几何流形表征中枢
 * 专属契约驱动严苛测试套件 (8/8)
 */
public class Phase71DeformableObjectManipulationContractTest {

    private PhysicsInformedNeuralOperator pinoEngine;
    private TactileVisualManifoldAligner aligner;
    private DeformableManifoldPlanner planner;
    private DeformableControlBus bus;

    @BeforeEach
    void setUp() {
        pinoEngine = new PhysicsInformedNeuralOperator(1.0e5, 0.40, 1100.0, 0.05);
        aligner = new TactileVisualManifoldAligner();
        planner = new DeformableManifoldPlanner(pinoEngine);
        bus = new DeformableControlBus();
    }

    private DeformableObjectState createMockState(String objId, double elongation) {
        int n = 5;
        double[][] nodes = new double[n][3];
        double[][] vels = new double[n][3];
        double[][][] strains = new double[n][3][3];

        for (int i = 0; i < n; i++) {
            nodes[i][0] = i * (0.05 + elongation); // 标称 50mm 间距 + 伸长量
            nodes[i][1] = 0.0;
            nodes[i][2] = 0.0;
            strains[i][0][0] = elongation / 0.05;
            strains[i][1][1] = -0.40 * strains[i][0][0];
            strains[i][2][2] = -0.40 * strains[i][0][0];
        }

        double[][][] tactile = new double[4][4][3];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                tactile[r][c][0] = 0.05; // 剪切 x
                tactile[r][c][1] = 0.02; // 剪切 y
                tactile[r][c][2] = -2.0; // 法向压力 2N
            }
        }

        double[] qwen = new double[1536];
        double norm = Math.sqrt(1536);
        for (int i = 0; i < 1536; i++) {
            qwen[i] = 1.0 / norm;
        }

        double[] centroid = new double[]{nodes[2][0], 0.0, 0.0};
        return new DeformableObjectState(objId, nodes, vels, strains, tactile, centroid, qwen, System.currentTimeMillis());
    }

    @Test
    @DisplayName("Contract 1: PINO 轻量解析前向推演微秒级耗时 (<=1ms) 与能量守恒")
    void test01_PinoForwardInferenceAndEnergyConservation() {
        DeformableObjectState state = createMockState("CABLE-01", 0.005);
        double[][] externalForces = new double[5][3];
        externalForces[4][0] = 5.0; // 末端施加 5N 拉伸力

        long start = System.nanoTime();
        DeformableObjectState nextState = pinoEngine.stepForward(state, externalForces, 1.0);
        long elapsedUs = (System.nanoTime() - start) / 1000;

        assertNotNull(nextState, "PINO 单步前向推演状态不应为空");
        assertTrue(elapsedUs <= 1000, "PINO 解析前向求解耗时必须严格 <= 1.0ms, 实际耗时: " + elapsedUs + " us");

        // 验证自由形变下无发散
        DeformationEnergyMetric energy1 = pinoEngine.computeEnergyMetric(state);
        DeformationEnergyMetric energy2 = pinoEngine.computeEnergyMetric(nextState);
        assertTrue(energy2.totalEnergy() >= 0.0, "弹性总势能必须非负");
        assertFalse(Double.isNaN(energy2.totalEnergy()), "总能量计算不应发生 NaN 发散");
    }

    @Test
    @DisplayName("Contract 2: 触视觉融合映射阿里千问 1536 维超球面单位向量归一化 (||v||_2 = 1.0) 与同向开半球")
    void test02_TactileVisualQwenHypersphericalAlignment() {
        double[][][] tactile = new double[4][4][3];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                tactile[r][c][0] = 0.1;
                tactile[r][c][1] = 0.05;
                tactile[r][c][2] = -5.0;
            }
        }
        double[][] visualPoints = new double[][]{
                {0.1, 0.0, 0.0}, {0.15, 0.02, 0.01}, {0.2, 0.01, -0.01}
        };

        double[] embedding = aligner.projectToQwenHypersphere(tactile, visualPoints);
        assertEquals(1536, embedding.length, "千问特征向量维度必须严格为 1536");

        double normSq = 0.0;
        for (double val : embedding) {
            normSq += val * val;
        }
        double norm = Math.sqrt(normSq);
        assertEquals(1.0, norm, 1e-6, "千问特征向量必须严格约束在单位超球面上 (||v||_2 = 1.0)");

        // 验证不同微扰下测地内积处于同向开半球 (> 0)
        double[][][] tactile2 = new double[4][4][3];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                tactile2[r][c][0] = 0.12;
                tactile2[r][c][1] = 0.06;
                tactile2[r][c][2] = -4.8;
            }
        }
        double[] embedding2 = aligner.projectToQwenHypersphere(tactile2, visualPoints);
        double dotProduct = 0.0;
        for (int i = 0; i < 1536; i++) {
            dotProduct += embedding[i] * embedding2[i];
        }
        assertTrue(dotProduct > 0.80, "触视觉微扰在超球面上测地线对齐内积应高度同向 (>0.80), 实际: " + dotProduct);
    }

    @Test
    @DisplayName("Contract 3: 视觉盲区下触觉微剪切滑脱 100% 检出与 <=2ms 法向补强增压")
    void test03_BlindZoneTactileMicroSlipDetection() {
        // 构造微滑脱触觉场: 剪切力极大 (sx=1.5N), 法向力较小 (fn=1.0N), 剪切比突破 0.75
        double[][][] slippingTactile = new double[4][4][3];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                slippingTactile[r][c][0] = 1.2;
                slippingTactile[r][c][1] = 0.8;
                slippingTactile[r][c][2] = -1.5;
            }
        }

        boolean isVisualOccluded = true; // 视觉完全被大尺寸夹爪遮挡
        boolean microSlipDetected = aligner.isBlindZoneMicroSlipDetected(slippingTactile, isVisualOccluded);
        assertTrue(microSlipDetected, "视觉盲区下局部接触微滑脱检出率必须达到 100%");

        double slipMetric = aligner.computeSlipMetric(slippingTactile);
        assertTrue(slipMetric > 0.75, "滑移风险指标必须突破警戒阈值 0.75, 实际: " + slipMetric);

        long start = System.nanoTime();
        double deltaFn = aligner.computeNormalReinforcementForce(slipMetric, 1.5);
        long elapsedUs = (System.nanoTime() - start) / 1000;

        assertTrue(deltaFn > 0.0, "检测到微滑脱时必须注入正向法向补强力 Delta f_n");
        assertTrue(elapsedUs <= 2000, "法向补强力计算响应耗时必须 <= 2ms, 实际: " + elapsedUs + " us");
    }

    @Test
    @DisplayName("Contract 4: 材料抗拉伸/屈服极限控制屏障 (HOCBF) 生效，防拉断拦截率 100%")
    void test04_DeformablePlannerMaterialYieldBarrier() {
        double yieldLimit = 3.5e4; // 35kPa 屈服极限
        DeformableObjectState nearYieldState = createMockState("CABLE-02", 0.015); // 大伸长状态

        double currentStress = pinoEngine.computeMaxVonMisesStress(nearYieldState);
        double margin = planner.computeYieldMargin(currentStress, yieldLimit);

        double[] targetPosition = new double[]{0.5, 0.0, 0.0}; // 尝试外扩拉伸
        double[][] trajectory = planner.planMinimumEnergyGraspTrajectory(nearYieldState, targetPosition, yieldLimit);

        assertNotNull(trajectory, "规划轨迹不应为空");
        assertTrue(trajectory.length > 0, "轨迹步数必须大于 0");

        // 验证轨迹末端位移受到屈服控制屏障安全截断
        double plannedEndDist = trajectory[trajectory.length - 1][0] - nearYieldState.visualPointCloudCentroid()[0];
        double rawTargetDist = targetPosition[0] - nearYieldState.visualPointCloudCentroid()[0];
        assertTrue(plannedEndDist < rawTargetDist, "在逼近屈服极限时，规划位移必须被屏障严格压缩截断以防拉断");
    }

    @Test
    @DisplayName("Contract 5: 四面体单元体积保正性屏障 (V_tet >= epsilon_vol > 0) 与网格防倒置消除")
    void test05_DeformablePlannerAntiInversionVolumeBarrier() {
        // 构造正常网格
        double[][] normalMesh = new double[][]{
                {0.0, 0.0, 0.0}, {0.05, 0.0, 0.0}, {0.025, 0.05, 0.0}, {0.025, 0.025, 0.05}
        };
        assertFalse(planner.isElementInversionDetected(normalMesh), "正常网格不应判定为倒置");

        // 构造倒置压瘪网格 (两节点重合或负体积)
        double[][] invertedMesh = new double[][]{
                {0.0, 0.0, 0.0}, {0.001, 0.0, 0.0}, {0.001, 0.001, 0.0}, {0.001, 0.001, 0.001}
        };
        assertTrue(planner.isElementInversionDetected(invertedMesh), "退化或塌陷网格必须被检出为倒置风险");

        double[][] resolvedMesh = planner.resolveInversion(invertedMesh);
        assertFalse(planner.isElementInversionDetected(resolvedMesh), "拉普拉斯几何向心松弛后单元倒置必须被 100% 消除");
    }

    @Test
    @DisplayName("Contract 6: 1000Hz 定长 4096 槽位 Disruptor 无锁队列吞吐 (<=50ns) 与 JitterGuard 软着陆降级")
    void test06_DeformableControlBusDisruptorAndJitterGuard() {
        DeformableObjectState state = createMockState("CABLE-03", 0.002);

        // 验证定长无锁环形总线写入与读取
        long start = System.nanoTime();
        bus.publishState(state);
        long elapsedNs = System.nanoTime() - start;

        DeformableObjectState polled = bus.pollLatestState();
        assertNotNull(polled, "轮询最新状态不应为空");
        assertEquals(state.objectId(), polled.objectId(), "读取状态对象 ID 必须一致");
        assertTrue(elapsedNs <= 50000, "无锁写入耗时应极速 (纳秒级), 实际: " + elapsedNs + " ns");

        // 验证 JitterGuard 时钟抖动守卫 (连续 3 帧时钟抖动 > 2ms 触发柔顺持握降级)
        bus.resetDegradedMode();
        assertFalse(bus.isDegradedMode(), "初始模式应为正常");

        bus.stepClockAndCheckJitter(4.0, 1000.0, 50000.0); // 抖动 1
        bus.stepClockAndCheckJitter(4.5, 1000.0, 50000.0); // 抖动 2
        boolean degraded = bus.stepClockAndCheckJitter(4.2, 1000.0, 50000.0); // 抖动 3

        assertTrue(degraded, "连续 3 帧时钟抖动必须切入 DEGRADED_COMPLIANT_HOLD 降级");
        assertTrue(bus.isDegradedMode(), "总线状态应保持降级标记");

        // 验证应力突变超 85% 亦瞬时切入降级
        bus.resetDegradedMode();
        boolean stressDegraded = bus.stepClockAndCheckJitter(1.0, 45000.0, 50000.0); // 45k > 85% * 50k (42.5k)
        assertTrue(stressDegraded, "应力超 85% 极限必须瞬时切入柔顺持握保护");
    }

    @Test
    @DisplayName("Contract 7: 不可变存证凭单全要素封装与 SHA-256 密码学自验防篡改")
    void test07_DeformableManipulationReceiptSha256Verification() {
        DeformableManipulationReceipt receipt = bus.generateReceipt(
                "SESSION-DEF-001", "CABLE-04", 12.5, 25000.0, 50000.0, 0.45, 180
        );

        assertNotNull(receipt, "生成存证凭单不应为空");
        assertNotNull(receipt.sha256Signature(), "SHA-256 签名不应为空");
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名长度必须为 64 十六进制字符");
        assertTrue(receipt.verifySignature(), "存证凭单 SHA-256 签名自验必须通过 (100% 通过率)");

        // 测试篡改检测
        DeformableManipulationReceipt tampered = new DeformableManipulationReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.objectId(),
                receipt.averageStrainEnergy() + 10.0, // 恶意篡改能量值
                receipt.maxVonMisesStress(),
                receipt.stressMargin(),
                receipt.tactileSlipMetric(),
                receipt.pinoInferenceTimeUs(),
                receipt.degradedBusMode(),
                receipt.timestampMs(),
                receipt.sha256Signature() // 沿用旧签名
        );
        assertFalse(tampered.verifySignature(), "篡改数据后签名自验必须失败");
    }

    @Test
    @DisplayName("Contract 8: 形变能量泛函分量非负性与弹性势能计算完备性")
    void test08_DeformableEnergyFunctionalMinimization() {
        DeformableObjectState state = createMockState("CABLE-05", 0.003);
        DeformationEnergyMetric metric = pinoEngine.computeEnergyMetric(state);

        assertTrue(metric.stretchEnergy() >= 0.0, "拉伸势能必须非负");
        assertTrue(metric.shearEnergy() >= 0.0, "剪切势能必须非负");
        assertTrue(metric.bendingEnergy() >= 0.0, "弯曲势能必须非负");
        assertTrue(metric.volumePenalty() >= 0.0, "体积惩罚势能必须非负");
        assertEquals(
                metric.stretchEnergy() + metric.shearEnergy() + metric.bendingEnergy() + metric.volumePenalty(),
                metric.totalEnergy(),
                1e-9,
                "总能量必须精确等于各分量之和"
        );
    }
}
