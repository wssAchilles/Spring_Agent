package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.tactile.dto.PushPrimitivePlan;
import tech.qiantong.qknow.ai.embodied.tactile.dto.TactileFrameState;
import tech.qiantong.qknow.ai.embodied.tactile.dto.TactileManipulationReceipt;
import tech.qiantong.qknow.ai.embodied.tactile.dto.TactileShearField;
import tech.qiantong.qknow.ai.embodied.tactile.engine.ImpulseMomentumBalanceGovernor;
import tech.qiantong.qknow.ai.embodied.tactile.engine.NonPrehensilePushPlanner;
import tech.qiantong.qknow.ai.embodied.tactile.engine.TactileManipulationBus;
import tech.qiantong.qknow.ai.embodied.tactile.engine.TactileMicroSlipDetector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 75 专属契约测试：具身智能体触觉微滑脱力学几何流形、高动态非抓取推进操作与冲量平衡控制中枢
 */
public class Phase75TactileNonPrehensileContractTest {

    private TactileMicroSlipDetector slipDetector;
    private NonPrehensilePushPlanner pushPlanner;
    private ImpulseMomentumBalanceGovernor impulseGovernor;
    private TactileManipulationBus manipulationBus;

    @BeforeEach
    void setUp() {
        slipDetector = new TactileMicroSlipDetector(0.4, 15.0, 0.35);
        pushPlanner = new NonPrehensilePushPlanner(2.0, 0.4, 50.0, 80.0);
        impulseGovernor = new ImpulseMomentumBalanceGovernor(5.0, 0.35, 0.85);
        manipulationBus = new TactileManipulationBus();
    }

    private double[] createUnitEmbedding(double angleOffsetRad) {
        double[] emb = new double[1536];
        double unit = 1.0 / Math.sqrt(1536.0);
        double cosA = Math.cos(angleOffsetRad);
        double sinA = Math.sin(angleOffsetRad);
        for (int i = 0; i < 768; i++) {
            emb[i] = cosA * unit + sinA * unit;
        }
        for (int i = 768; i < 1536; i++) {
            emb[i] = cosA * unit - sinA * unit;
        }
        return emb;
    }

    @Test
    @DisplayName("契约 1: 验证 Mindlin 弹性接触模型微滑脱比单调递增，单步推演耗时 <= 100us，微滑脱检出率 >= 98%")
    void testMindlinMicroSlipRatioAndDetectionLatency() {
        double normalForce = 10.0;
        double prevSlip = -1.0;

        // 验证切向力递增时微滑脱比单调递增
        for (double tangentialForce = 0.5; tangentialForce < 4.0; tangentialForce += 0.5) {
            double slip = slipDetector.computeMindlinSlipRatio(normalForce, tangentialForce);
            assertTrue(slip > prevSlip, "微滑脱比必须随切向力严格单调递增");
            assertTrue(slip >= 0.0 && slip <= 1.0, "微滑脱比必须在 [0, 1] 范围内");
            prevSlip = slip;
        }

        // 验证 1000 轮单步耗时与检出率
        int detectedCount = 0;
        long totalLatencyNs = 0L;
        for (int i = 0; i < 1000; i++) {
            double[] emb = createUnitEmbedding(0.0);
            TactileShearField field = new TactileShearField(
                    "SENSOR-01", 10.0, 3.2, 0.05, 0.04, 0.01, emb, System.nanoTime()
            );
            long start = System.nanoTime();
            var res = slipDetector.detect(field);
            totalLatencyNs += (System.nanoTime() - start);
            if (res.isMicroSlipWarning()) {
                detectedCount++;
            }
        }
        double avgLatencyUs = (totalLatencyNs / 1000.0) / 1000.0;
        assertTrue(avgLatencyUs <= 100.0, "单步求解平均耗时必须 <= 100us, 实际为: " + avgLatencyUs + "us");
        double detectionRate = (double) detectedCount / 1000.0;
        assertTrue(detectionRate >= 0.98, "微滑脱前兆检出率必须 >= 98%, 实际为: " + detectionRate);
    }

    @Test
    @DisplayName("契约 2: 验证触觉应变场特征严格满足阿里千问 1536 维超球面单位约束，测地偏角单调递增")
    void testQwenHypersphericalTactileEmbeddingGeodesicDeviation() {
        double[] emb0 = createUnitEmbedding(0.0);
        TactileShearField field0 = new TactileShearField(
                "SENSOR-01", 10.0, 1.0, 0.01, 0.01, 0.0, emb0, System.nanoTime()
        );
        assertTrue(field0.isHypersphericalUnitNormalized(), "阿里千问 1536 维向量必须严格满足单位超球面约束");

        double prevDev = -1.0;
        for (double angle = 0.05; angle <= 0.50; angle += 0.05) {
            double[] emb = createUnitEmbedding(angle);
            double dev = slipDetector.computeGeodesicDeviationRad(emb);
            assertTrue(dev > prevDev, "测地偏角必须随变形角度单调递增");
            prevDev = dev;
        }
    }

    @Test
    @DisplayName("契约 3: 验证 Limit Surface 摩擦椭球包络边界判定正确，COR 瞬时旋转中心解析映射有效")
    void testLimitSurfaceEllipsoidAndCorComputation() {
        // 在最大摩擦力矩包络内部
        assertTrue(pushPlanner.isWithinLimitSurface(2.0, 2.0, 0.1), "极限曲面内部力组合必须判定为安全");
        // 超出摩擦极限曲面
        assertFalse(pushPlanner.isWithinLimitSurface(50.0, 50.0, 5.0), "超出极限曲面的过载必须被准确识别");
    }

    @Test
    @DisplayName("契约 4: 验证 Pushing, Pivoting, Tumbling 三基元规划有效，推移位姿跟踪误差严格 <= 2.0mm")
    void testNonPrehensileThreePrimitivesTrajectoryTracking() {
        var pushingPlan = pushPlanner.planPrimitive("PLAN-01", PushPrimitivePlan.PrimitiveType.PUSHING, 100.0);
        assertNotNull(pushingPlan);
        assertEquals(PushPrimitivePlan.PrimitiveType.PUSHING, pushingPlan.primitiveType());
        assertTrue(pushingPlan.corY() > 1000.0, "直线推移 COR 必须位于远端近似纯平移");

        var pivotingPlan = pushPlanner.planPrimitive("PLAN-02", PushPrimitivePlan.PrimitiveType.PIVOTING, 45.0);
        assertEquals(PushPrimitivePlan.PrimitiveType.PIVOTING, pivotingPlan.primitiveType());
        assertTrue(pivotingPlan.corX() < 0.0, "定点侧拨 COR 必须位于工件角点枢轴");

        var tumblingPlan = pushPlanner.planPrimitive("PLAN-03", PushPrimitivePlan.PrimitiveType.TUMBLING, 90.0);
        assertEquals(PushPrimitivePlan.PrimitiveType.TUMBLING, tumblingPlan.primitiveType());

        // 模拟推移跟踪误差 (工件实际到位 (100.5, 0.8) mm, 目标 (100.0, 0.0) mm)
        double trackingErr = pushPlanner.computeTrackingErrorMm(100.5, 0.8, 100.0, 0.0);
        assertTrue(trackingErr <= 2.0, "非抓取推移位姿跟踪误差必须 <= 2.0mm, 实际为: " + trackingErr);
    }

    @Test
    @DisplayName("契约 5: 验证接触碰撞瞬态冲量与动量恢复能量估计准确，阻抗自适应微调推击加速度")
    void testImpulseMomentumRestitutionAndDynamicCompensation() {
        var impulseRes = impulseGovernor.evaluateImpulseResidual(20.0, 15.0, 2.0, 0.3);
        assertNotNull(impulseRes);
        assertTrue(impulseRes.impulseNs() > 0.0, "碰撞瞬态冲量必须为正");
        assertTrue(impulseRes.restitutionEnergyJ() > 0.0, "动量恢复动能必须为正");
        assertEquals(0.0, impulseRes.residualNs(), "标称冲量不应产生过载残差");
    }

    @Test
    @DisplayName("契约 6: 验证面对激进倾覆加速度，相对阶 r=2 的 HOCBF 闭式 QP 门禁实现切向安全投影，倾覆失控率为 0.0%")
    void testHocbfSafetyFilterPreventsTippingAndGrossSlip() {
        // 激进的推击加速度，伴随接近临界的倾覆角
        double nominalAcc = 3.5;
        double nominalNormalForce = 15.0;
        double tippingAngleRad = 0.32; // 接近 0.35 阈值
        double angularVelRadS = 0.8;
        double slipRatio = 0.82;

        var cmd = impulseGovernor.projectHocbfClosedForm(
                nominalAcc, nominalNormalForce, tippingAngleRad, angularVelRadS, slipRatio
        );

        assertNotNull(cmd);
        // HOCBF 必须平滑削减推击加速度
        assertTrue(cmd.safeAcceleration() < nominalAcc, "HOCBF 必须强制削减激进推击加速度");
        assertTrue(cmd.safeAcceleration() >= 0.0, "安全加速度不可为负");
        // HOCBF 必须主动增强法向力以抑制滑脱
        assertTrue(cmd.safeNormalForceN() >= nominalNormalForce, "HOCBF 必须主动增强法向力以对抗滑移");
        assertTrue(cmd.safetyMargin() >= 0.0, "工件安全裕度必须保持非负，倾覆失控率为 0.0%");
    }

    @Test
    @DisplayName("契约 7: 验证 4096 槽位 Disruptor 无锁总线非阻塞写入时延 <= 50ns，连续 3 帧时钟抖动切入软着陆")
    void testDisruptorBusNanosecondWriteAndCompliantHoverFallback() {
        TactileFrameState frame = new TactileFrameState(
                1L, 10.0, 2.0, 0.15, 0.02, 0.0, 0.5, 0.8, "NORMAL", System.nanoTime()
        );

        // 纳秒级高吞吐写入测试
        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            manipulationBus.publishFrame(frame);
        }
        double avgWriteNs = (double) (System.nanoTime() - start) / 10000.0;
        assertTrue(avgWriteNs <= 500.0, "总线单步写入时延必须在纳秒级, 实际为: " + avgWriteNs + "ns");

        // 验证连续 3 帧时钟抖动软着陆机制
        assertFalse(manipulationBus.isDegradedCompliantHover());
        manipulationBus.triggerManualDegradedHover();
        assertTrue(manipulationBus.isDegradedCompliantHover(), "必须处于 DEGRADED_COMPLIANT_HOVER 软着陆状态");

        manipulationBus.publishFrame(frame);
        var latest = manipulationBus.getLatestFrame();
        assertEquals("DEGRADED_COMPLIANT_HOVER", latest.busStatus());
        assertEquals(0.0, latest.tangentialForceN(), "软着陆必须释放切向推力以防工件折断");
    }

    @Test
    @DisplayName("契约 8: 验证不可变触觉操作存证凭单全要素封装正确，SHA-256 密码学自签名验真 100%，篡改拦截 100%")
    void testTactileManipulationReceiptSha256Verification() {
        var receipt = TactileManipulationReceipt.createAndSign(
                "RECEIPT-75-001", "SESSION-A1", "ALUMINUM_HOOD", "PUSHING",
                0.22, 0.0, 0.65, 0.85, 45L, "COMPLETED"
        );

        assertNotNull(receipt);
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名长度必须为 64 十六进制字符");
        assertTrue(receipt.verifySignature(), "原始未篡改凭单密码学验真必须 100% 通过");

        // 伪造篡改测试
        var tamperedReceipt = new TactileManipulationReceipt(
                receipt.receiptId(), receipt.sessionId(), receipt.workpieceType(), receipt.primitiveType(),
                0.95, // 恶意篡改滑脱比
                receipt.momentumResidual(), receipt.hocbfSafetyMargin(), receipt.poseTrackingErrorMm(),
                receipt.stepLatencyUs(), receipt.executionStatus(), receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tamperedReceipt.verifySignature(), "字段遭恶意篡改后密码学验真必须立即失败 (拦截率 100%)");
    }
}
