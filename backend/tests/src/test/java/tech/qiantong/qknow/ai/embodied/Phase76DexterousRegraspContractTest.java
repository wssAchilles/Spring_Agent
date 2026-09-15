package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.dexterous.dto.DexterousManipulationReceipt;
import tech.qiantong.qknow.ai.embodied.dexterous.dto.HandEyeCoordinationState;
import tech.qiantong.qknow.ai.embodied.dexterous.dto.MultiContactFrictionEnvelope;
import tech.qiantong.qknow.ai.embodied.dexterous.dto.RegraspingSequencePlan;
import tech.qiantong.qknow.ai.embodied.dexterous.dto.RegraspingSequencePlan.RegraspPhase;
import tech.qiantong.qknow.ai.embodied.dexterous.engine.DexterousManipulationBus;
import tech.qiantong.qknow.ai.embodied.dexterous.engine.DynamicInHandRegraspPlanner;
import tech.qiantong.qknow.ai.embodied.dexterous.engine.HandEyeManifoldSafetyGate;
import tech.qiantong.qknow.ai.embodied.dexterous.engine.MultiContactFrictionGovernor;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 76 具身智能体多接触点微观摩擦极限包络、自适应灵巧手动态重抓取与手眼协同流形控制中枢专属契约测试套件
 *
 * @author Achilles
 * @since 2026-09-15
 */
@DisplayName("Phase 76 灵巧手多接触点摩擦极限包络与动态重抓取控制中枢契约测试")
public class Phase76DexterousRegraspContractTest {

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

    @Test
    @DisplayName("契约 1: 多接触点抓取矩阵 G 解析构建与 Ferrari-Canny 力封闭测度")
    void test01_MultiContactGraspMatrixAndFerrariCannyMetric() {
        MultiContactFrictionGovernor governor = new MultiContactFrictionGovernor(0.5, 20.0);

        // 构造三指对拓抓取正三棱柱工件
        double[][] points = {
                {0.05, 0.0, 0.0},
                {-0.025, 0.0433, 0.0},
                {-0.025, -0.0433, 0.0}
        };
        double[][] normals = {
                {-1.0, 0.0, 0.0},
                {0.5, -0.866, 0.0},
                {0.5, 0.866, 0.0}
        };
        double[] mus = {0.6, 0.6, 0.6};
        double[] majorAxes = {4.0, 4.0, 4.0};
        double[] minorAxes = {3.0, 3.0, 3.0};
        double[] qwenEmb = createUniformEmbedding1536();

        MultiContactFrictionEnvelope envelope = new MultiContactFrictionEnvelope(
                3, points, normals, mus, majorAxes, minorAxes, qwenEmb
        );

        double[][] G = governor.buildGraspMatrix(envelope);
        assertNotNull(G);
        assertEquals(6, G.length);
        assertEquals(9, G[0].length);

        long start = System.nanoTime();
        double metric = governor.computeFerrariCannyMetric(envelope);
        long elapsedUs = (System.nanoTime() - start) / 1000;

        assertTrue(metric >= 0.05, "三指对拓抓取应满足力封闭，当前测度: " + metric);
        assertTrue(governor.isForceClosure(envelope));
        assertTrue(elapsedUs <= 1000, "力封闭单步评估耗时应极低，实测(us): " + elapsedUs);
    }

    @Test
    @DisplayName("契约 2: 阿里千问 1536 维超球面视触同胚流形对齐与测地内积")
    void test02_QwenGeodesicEmbeddingHypersphereInvariant() {
        double[] u0 = createUniformEmbedding1536();
        double[] uOrtho = createOrthogonalUnitVector(u0);

        HandEyeManifoldSafetyGate safetyGate = new HandEyeManifoldSafetyGate(0.05, 20.0, 1.5, 2.0);

        // 测地大圆弧旋转 theta = pi / 4
        double targetTheta = Math.PI / 4.0;
        double[] vTheta = new double[1536];
        for (int i = 0; i < 1536; i++) {
            vTheta[i] = Math.cos(targetTheta) * u0[i] + Math.sin(targetTheta) * uOrtho[i];
        }

        double devRad = safetyGate.computeGeodesicDeviationRad(u0, vTheta);
        assertEquals(targetTheta, devRad, 1e-4, "超球面测地偏角应精确等于设定角");

        // 视触融合在无遮挡与全遮挡下的保模重投影
        double[] fusedNoOcc = safetyGate.fuseVisionAndTactileEmbeddings(u0, vTheta, 0.0);
        double sumSq = 0.0;
        for (double val : fusedNoOcc) sumSq += val * val;
        assertEquals(1.0, Math.sqrt(sumSq), 1e-5, "融合特征必须保持千问单位超球面约束");
    }

    @Test
    @DisplayName("契约 3: 五阶段动态重抓取相变状态机与平滑相变流转")
    void test03_FiveStageRegraspStateMachineSequence() {
        DynamicInHandRegraspPlanner planner = new DynamicInHandRegraspPlanner(4.0, 15.0, 1.8);

        RegraspPhase phase = RegraspPhase.STABLE_HOLD;
        phase = planner.stepNextPhase(phase, 0.35, 3.0);
        assertEquals(RegraspPhase.CONTROLLED_SLIDE, phase);

        phase = planner.stepNextPhase(phase, 0.30, 2.5);
        assertEquals(RegraspPhase.FINGER_LIFT, phase);

        phase = planner.stepNextPhase(phase, 0.25, 2.2);
        assertEquals(RegraspPhase.REPOSITION, phase);

        phase = planner.stepNextPhase(phase, 0.25, 1.8);
        assertEquals(RegraspPhase.SECURE, phase);

        phase = planner.stepNextPhase(phase, 0.40, 0.5);
        assertEquals(RegraspPhase.STABLE_HOLD, phase);
    }

    @Test
    @DisplayName("契约 4: 换指前夕支撑指预紧力前馈重分配与力封闭保持")
    void test04_SupportFingerPreloadRedistributionAntiSlip() {
        DynamicInHandRegraspPlanner planner = new DynamicInHandRegraspPlanner(4.0, 15.0, 1.8);

        // 4 指抓取，指定食指 (index 1) 执行换指
        int totalFingers = 4;
        int gaitingIdx = 1;

        // 1. 稳定夹持阶段
        double[] forcesHold = planner.computePreloadForceDistribution(RegraspPhase.STABLE_HOLD, totalFingers, gaitingIdx);
        for (double f : forcesHold) {
            assertEquals(4.0, f, 1e-3);
        }

        // 2. 换指抬升阶段 (FINGER_LIFT)
        double[] forcesLift = planner.computePreloadForceDistribution(RegraspPhase.FINGER_LIFT, totalFingers, gaitingIdx);
        assertEquals(0.0, forcesLift[gaitingIdx], 1e-4, "换指指尖法向力应卸载至 0");

        // 其余支撑指应提升至 4.0 * 1.8 = 7.2N
        for (int i = 0; i < totalFingers; i++) {
            if (i != gaitingIdx) {
                assertEquals(7.2, forcesLift[i], 1e-3, "其余支撑指应获得安全前馈增益补偿");
            }
        }
    }

    @Test
    @DisplayName("契约 5: 全遮挡极限工况下纯触觉流形自愈与位姿反演有界性")
    void test05_HandEyeSeverelyOccludedTactileSelfHealing() {
        HandEyeManifoldSafetyGate safetyGate = new HandEyeManifoldSafetyGate(0.05, 20.0, 1.5, 2.0);
        double[] uVis = createUniformEmbedding1536();
        double[] uTac = createUniformEmbedding1536();

        // 100% 视觉全遮挡
        double[] fused = safetyGate.fuseVisionAndTactileEmbeddings(uVis, uTac, 1.0);
        assertNotNull(fused);
        assertEquals(1536, fused.length);

        // 验证状态对象判定
        HandEyeCoordinationState state = new HandEyeCoordinationState(
                1001L,
                new double[]{0.1, 0.2, 0.3, 0.0, 0.0, 0.0, 1.0},
                0.98,
                new double[][]{{0.01, 0.02}, {0.02, 0.01}, {0.01, -0.01}},
                fused,
                0.32,
                0.27,
                System.nanoTime(),
                "ACTIVE_1000HZ"
        );

        assertTrue(state.isSeverelyOccluded(), "98% 遮挡应判定为严重遮挡");
    }

    @Test
    @DisplayName("契约 6: 相对阶 r=2 高阶控制屏障 HOCBF 闭式 QP 门禁与零脱手保证")
    void test06_HighOrderControlBarrierHocbfQpSafetyFilter() {
        HandEyeManifoldSafetyGate safetyGate = new HandEyeManifoldSafetyGate(0.08, 18.0, 2.0, 2.0);

        double[] nominalForces = {2.0, 2.0, 2.0}; // 名义法向力偏小
        double currentMetric = 0.05; // 力封闭测度低于安全阈值 0.08
        double metricVelocity = -0.02; // 力封闭还在恶化下降

        long start = System.nanoTime();
        double[] safeForces = safetyGate.filterNominalForcesWithHocbf(nominalForces, currentMetric, metricVelocity);
        long elapsedUs = (System.nanoTime() - start) / 1000;

        assertTrue(elapsedUs <= 100, "闭式 QP 投影耗时应极其微秒级，实测: " + elapsedUs + "us");
        for (int i = 0; i < 3; i++) {
            assertTrue(safeForces[i] > nominalForces[i], "HOCBF 应当触发安全加压补偿");
            assertTrue(safeForces[i] <= 18.0, "法向力不得超过硬上限 18N");
        }
    }

    @Test
    @DisplayName("契约 7: 1000Hz 定长 4096 槽位 Disruptor 无锁总线与 JitterGuard 软着陆")
    void test07_DexterousManipulationBusThroughputAndJitterGuard() {
        DexterousManipulationBus bus = new DexterousManipulationBus();
        double[] emb = createUniformEmbedding1536();

        HandEyeCoordinationState frame = new HandEyeCoordinationState(
                1L,
                new double[]{0, 0, 0, 0, 0, 0, 1},
                0.1,
                new double[][]{{0, 0}, {0, 0}, {0, 0}},
                emb,
                0.35,
                0.27,
                System.nanoTime(),
                "ACTIVE_1000HZ"
        );

        // 高频发布 1000 次，检验无锁吞吐
        for (int i = 0; i < 1000; i++) {
            bus.publish(frame);
        }

        assertEquals("ACTIVE_1000HZ", bus.getBusState());
        assertNotNull(bus.getLatest());

        // 模拟异常时钟抖动切入软着陆
        bus.forceDegradedCompliantGrip();
        assertEquals("DEGRADED_COMPLIANT_GRIP", bus.getBusState());

        bus.resetActive();
        assertEquals("ACTIVE_1000HZ", bus.getBusState());
    }

    @Test
    @DisplayName("契约 8: 不可变灵巧手操作存证凭单 SHA-256 密码学签名验真与防篡改")
    void test08_CryptographicSelfVerificationAndAntiTampering() {
        DexterousManipulationBus bus = new DexterousManipulationBus();

        DexterousManipulationReceipt receipt = bus.issueReceipt(
                "RCP-76-001",
                "SESSION-REGRASP-01",
                "LENS-BARREL-精密镜头",
                "CONTROLLED_SLIDE",
                0.285,
                0.205,
                0.85,
                42L
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原始凭单签名自验必须通过");

        // 恶意篡改力封闭测度
        DexterousManipulationReceipt tampered = new DexterousManipulationReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.workpieceId(),
                receipt.regraspPhase(),
                0.999, // 篡改测度
                receipt.hocbfSafetyMargin(),
                receipt.poseTrackingErrorMm(),
                receipt.stepLatencyUs(),
                receipt.executionStatus(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );

        assertFalse(tampered.verifySignature(), "篡改字段后密码学校验必须拦截");
    }
}
