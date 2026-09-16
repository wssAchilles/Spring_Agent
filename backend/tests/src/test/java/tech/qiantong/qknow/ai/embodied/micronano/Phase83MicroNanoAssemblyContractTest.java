package tech.qiantong.qknow.ai.embodied.micronano;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.micronano.dto.MicroNanoAssemblyReceipt;
import tech.qiantong.qknow.ai.embodied.micronano.dto.MicroNanoStateFrame;
import tech.qiantong.qknow.ai.embodied.micronano.engine.DefocusRobustVisualAlignmentOperator;
import tech.qiantong.qknow.ai.embodied.micronano.engine.MicroAdhesionReleaseOperator;
import tech.qiantong.qknow.ai.embodied.micronano.engine.MicroComplianceImpedanceGovernor;
import tech.qiantong.qknow.ai.embodied.micronano.engine.MicroNanoCoordinationControlBus;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 83 具身智能体微纳尺度视触力感知流形、高动态微装配与微夹持操纵动力学中枢 8 项专属契约测试
 *
 * @author Achilles
 * @since 2026-09-16
 */
public class Phase83MicroNanoAssemblyContractTest {

    private MicroAdhesionReleaseOperator adhesionOperator;
    private MicroComplianceImpedanceGovernor impedanceGovernor;
    private DefocusRobustVisualAlignmentOperator visualAligner;
    private MicroNanoCoordinationControlBus controlBus;
    private Random random;

    @BeforeEach
    void setUp() {
        adhesionOperator = new MicroAdhesionReleaseOperator();
        impedanceGovernor = new MicroComplianceImpedanceGovernor();
        visualAligner = new DefocusRobustVisualAlignmentOperator();
        controlBus = new MicroNanoCoordinationControlBus(adhesionOperator, impedanceGovernor, visualAligner);
        random = new Random(42);
    }

    /**
     * 辅助构造阿里千问 1536 维超球面归一化单位向量 (||v||_2 = 1.0 +- 1e-4)
     */
    private double[] createNormalizedQwenVector(Random rng) {
        double[] vec = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = rng.nextGaussian();
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("契约 1: 阿里千问 1536 维超球面微纳视触力全状态流形同胚映射与维度/模长强校验 (命题 2.1)")
    void testQwen1536HypersphereMetricManifoldContract() {
        double[] validVec = createNormalizedQwenVector(random);

        // 正确构造 1536 维超球面时序帧
        MicroNanoStateFrame frame = new MicroNanoStateFrame(
                "FRAME-001", System.nanoTime(),
                12.5, -4.2, 50.0,
                5.0, 5.0, 12.0,
                15.0, 25000.0, 0.2,
                validVec
        );
        assertNotNull(frame);
        assertEquals(1536, frame.qwenEmbedding1536().length);

        // 验证非法维度拒识 (如 768 维)
        double[] invalidDimVec = new double[768];
        assertThrows(IllegalArgumentException.class, () -> new MicroNanoStateFrame(
                "FRAME-BAD-DIM", System.nanoTime(),
                12.5, -4.2, 50.0,
                5.0, 5.0, 12.0,
                15.0, 25000.0, 0.2,
                invalidDimVec
        ));

        // 验证未归一化非单位向量拒识 (模长 != 1.0)
        double[] unnormalizedVec = Arrays.copyOf(validVec, 1536);
        for (int i = 0; i < 1536; i++) {
            unnormalizedVec[i] *= 2.5; // 模长变为 2.5
        }
        assertThrows(IllegalArgumentException.class, () -> new MicroNanoStateFrame(
                "FRAME-BAD-NORM", System.nanoTime(),
                12.5, -4.2, 50.0,
                5.0, 5.0, 12.0,
                15.0, 25000.0, 0.2,
                unnormalizedVec
        ));
    }

    @Test
    @DisplayName("契约 2: 表面范德华力-毛细弯月面黏附力学解耦与高频微剪切主动脱粘动力学算子 (定理 1.1)")
    void testMicroAdhesionReleaseActiveShearDetachmentContract() {
        double[] vec = createNormalizedQwenVector(random);

        // 场景 A: 施加压电高频超声剪切振动 (f_s = 25kHz >= 20kHz)，触发生效的主动脱粘
        MicroNanoStateFrame activeShearFrame = new MicroNanoStateFrame(
                "FRAME-SHEAR-ACTIVE", System.nanoTime(),
                10.0, 10.0, 0.02,
                5.0, 5.0, 10.0,
                25.0, 25000.0, 0.1,
                vec
        );
        var resultActive = adhesionOperator.evaluateAndRelease(activeShearFrame, 15.0, 0.02);

        assertTrue(resultActive.detachmentSuccess(), "高频超声微剪切应确保 100% 成功主动脱附");
        assertFalse(resultActive.inAdhesionLock(), "不应陷入表面黏附锁死");
        assertTrue(resultActive.splashResidualMicron() <= 1.0,
                "主动剪切脱粘伴生飞溅微位移残差必须 <= 1.0 um, 实测: " + resultActive.splashResidualMicron());
        assertTrue(resultActive.solveLatencyMicros() <= MicroAdhesionReleaseOperator.MAX_SOLVE_LATENCY_MICROS,
                "单步计算耗时必须 <= 100 us, 实测: " + resultActive.solveLatencyMicros());

        // 场景 B: 反事实对照 (未施加高频剪切微振动，f_s = 0Hz)
        MicroNanoStateFrame passiveFrame = new MicroNanoStateFrame(
                "FRAME-PASSIVE-PULL", System.nanoTime(),
                10.0, 10.0, 0.02,
                5.0, 5.0, 10.0,
                25.0, 0.0, 0.1, // 无剪切振动
                vec
        );
        var resultPassive = adhesionOperator.evaluateAndRelease(passiveFrame, 15.0, 0.02);

        assertTrue(resultPassive.inAdhesionLock(), "无剪切振动且强表面黏附力下微工件必然陷入黏附死锁");
        assertFalse(resultPassive.detachmentSuccess(), "脱粘释放应判定失败");
        assertTrue(resultPassive.splashResidualMicron() > 10.0,
                "强行拉拽飞溅残差应严重发散 (> 10 um), 实测: " + resultPassive.splashResidualMicron());
    }

    @Test
    @DisplayName("契约 3: 微牛级相对阶 r=2 Micro-HOCBF 极速闭式二次规划 (QP) 正交超平面解析投影 (定理 1.2)")
    void testMicroHocbfAntiCrushingClosedFormQpContract() {
        double[] vec = createNormalizedQwenVector(random);

        // 极限临界接触状态: 接触力达到 48 uN，逼近破裂上限 50 uN
        MicroNanoStateFrame criticalFrame = new MicroNanoStateFrame(
                "FRAME-CRITICAL-FORCE", System.nanoTime(),
                5.0, 5.0, 0.5,
                10.0, 10.0, 30.0,
                48.0, 20000.0, 0.1,
                vec
        );

        // 输入激进的电压指令 (100V)，若不截断必然导致微器件粉碎性压溃
        double aggressiveVoltage = 100.0;
        var qpResult = impedanceGovernor.computeCompliantForce(
                criticalFrame, 20.0, aggressiveVoltage,
                MicroComplianceImpedanceGovernor.DEFAULT_YIELD_LIMIT_MICRO_N
        );

        assertTrue(qpResult.cbfActive(), "Micro-HOCBF 必须实时拦截激进超调指令");
        assertTrue(qpResult.safeControlVoltage() < aggressiveVoltage,
                "安全电压必须被正交超平面解析投影硬截断");
        assertEquals(0.0, qpResult.crushRate(), 1e-9, "定理 1.2 保证微器件微压溃率恒等于 0.0%");
        assertTrue(qpResult.solveLatencyMicros() <= MicroComplianceImpedanceGovernor.MAX_QP_SOLVE_LATENCY_MICROS,
                "闭式二次规划求解耗时必须 <= 15 us, 实测: " + qpResult.solveLatencyMicros());
    }

    @Test
    @DisplayName("契约 4: 微牛级高频柔顺力控阻抗稳态微力跟踪残差收敛 (定理 1.2)")
    void testMicroNewtonImpedanceTrackingContract() {
        double[] vec = createNormalizedQwenVector(random);

        // 目标接触微力 20.0 uN，当前传感器测得 25.0 uN (偏差 5 uN)
        MicroNanoStateFrame trackingFrame = new MicroNanoStateFrame(
                "FRAME-FORCE-TRACKING", System.nanoTime(),
                5.0, 5.0, 0.2,
                5.0, 5.0, 15.0,
                25.0, 20000.0, 0.1,
                vec
        );

        var qpResult = impedanceGovernor.computeCompliantForce(
                trackingFrame, 20.0, 15.0,
                MicroComplianceImpedanceGovernor.DEFAULT_YIELD_LIMIT_MICRO_N
        );

        assertTrue(Math.abs(qpResult.forceTrackingErrorMicroN()) <= 0.5,
                "高频柔顺阻抗稳态微力跟踪残差必须 <= 0.5 uN, 实测: " + qpResult.forceTrackingErrorMicroN());
    }

    @Test
    @DisplayName("契约 5: 狭窄焦深 (DOF <= 10um) 显微视觉 80% 严重虚焦模糊下亚微米几何流形对齐 (定理 1.3)")
    void testDefocusRobustVisualAlignmentSubMicronContract() {
        double[] targetVec = createNormalizedQwenVector(random);
        // 生成具有微小测地线旋转与逼近的当前帧
        double[] currentVec = targetVec.clone();
        currentVec[0] += 0.01;
        // 重新归一化
        double s = 0.0;
        for (double v : currentVec) s += v * v;
        double n = Math.sqrt(s);
        for (int i = 0; i < 1536; i++) currentVec[i] /= n;

        // 目标参考特征帧 (焦平面位置: 0, 0, 0)
        MicroNanoStateFrame targetFrame = new MicroNanoStateFrame(
                "FRAME-TARGET-REF", System.nanoTime(),
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                targetVec
        );

        // 当前显微观测帧: 存在 80% (0.80) 严重虚焦散焦模糊，微工件几何位置在 0.35 um 处
        MicroNanoStateFrame currentFrame = new MicroNanoStateFrame(
                "FRAME-CURRENT-BLURRED", System.nanoTime(),
                0.25, 0.20, 0.15,
                1.0, 1.0, 2.0,
                5.0, 20000.0, 0.80, // 80% 严重虚焦散焦
                currentVec
        );

        var alignResult = visualAligner.computeAlignment(currentFrame, targetFrame);

        assertTrue(alignResult.alignmentSuccess(), "即使在 80% 严重散焦虚焦下系统仍应精密对齐成功");
        assertTrue(alignResult.alignmentResidualMicron() <= DefocusRobustVisualAlignmentOperator.TARGET_ALIGNMENT_TOLERANCE_MICRON,
                "装配对接空间对齐残差必须 <= 0.5 um, 实测: " + alignResult.alignmentResidualMicron());
        assertTrue(alignResult.solveLatencyMicros() <= DefocusRobustVisualAlignmentOperator.MAX_ALIGN_LATENCY_MICROS,
                "单步计算耗时必须 <= 120 us, 实测: " + alignResult.solveLatencyMicros());
    }

    @Test
    @DisplayName("契约 6: 1000Hz 定长 4096 槽位 Disruptor 无锁微纳控制总线高频吞吐与全流程闭环")
    void testDisruptorControlBusHighFrequencyNonBlockingContract() {
        double[] vec = createNormalizedQwenVector(random);

        MicroNanoStateFrame targetFrame = new MicroNanoStateFrame(
                "TARGET-001", System.nanoTime(),
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                vec
        );

        // 连续非阻塞发布 5000 帧 (超过 4096 槽位容量，验证环形覆盖回绕与无锁吞吐)
        long startNs = System.nanoTime();
        for (int i = 0; i < 5000; i++) {
            MicroNanoStateFrame f = new MicroNanoStateFrame(
                    "F-" + i, System.nanoTime(),
                    0.1, 0.1, 0.05,
                    2.0, 2.0, 5.0,
                    15.0, 25000.0, 0.15,
                    vec
            );
            boolean pub = controlBus.publishFrame(f);
            assertTrue(pub);
        }
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        assertTrue(durationMs < 500, "5000 帧无锁环形写入应在 500ms 内极速完成, 实测: " + durationMs + "ms");
        assertEquals(5000L, controlBus.getCurrentSequence());

        // 执行单周期全流程闭环
        MicroNanoStateFrame cycleFrame = new MicroNanoStateFrame(
                "F-CYCLE", System.nanoTime(),
                0.1, 0.1, 0.05,
                2.0, 2.0, 5.0,
                15.0, 25000.0, 0.15,
                vec
        );
        MicroNanoAssemblyReceipt receipt = controlBus.processCycle(
                "SESSION-101", "WORKPIECE-MEMS-01",
                cycleFrame, targetFrame, 15.0, 5.0
        );

        assertNotNull(receipt);
        assertTrue(receipt.detachmentSuccess());
        assertEquals(0.0, receipt.crushRate(), 1e-9);
        assertTrue(receipt.releaseSplashMicron() <= 1.0);
        assertTrue(receipt.alignmentErrorMicron() <= 0.5);
    }

    @Test
    @DisplayName("契约 7: JitterGuard 时钟抖动守卫滑动监控连续 3 帧超时瞬切 DEGRADED_COMPLIANT_MICRO_RETRACT 柔顺微回退模式")
    void testJitterGuardSafetyDegradedMicroRetractContract() {
        double[] vec = createNormalizedQwenVector(random);
        controlBus.resetBusStatus();
        assertEquals(MicroNanoCoordinationControlBus.STATUS_ACTIVE_NOMINAL, controlBus.getBusStatus());

        // 发布初始帧
        controlBus.publishFrame(new MicroNanoStateFrame(
                "F-0", System.nanoTime(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10.0, 20000.0, 0.1, vec
        ));

        // 模拟连续 3 帧时钟抖动延迟 (> 2ms)
        for (int i = 1; i <= 3; i++) {
            try {
                Thread.sleep(4); // 停顿 4ms 模拟通信阻塞抖动
            } catch (InterruptedException ignored) {}
            controlBus.publishFrame(new MicroNanoStateFrame(
                    "F-" + i, System.nanoTime(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10.0, 20000.0, 0.1, vec
            ));
        }

        // 验证 JitterGuard 瞬时触发安全降级微回退
        assertEquals(MicroNanoCoordinationControlBus.STATUS_DEGRADED_RETRACT, controlBus.getBusStatus(),
                "连续 3 帧时钟抖动必须瞬时切入柔顺微回退安全保护模式");
    }

    @Test
    @DisplayName("契约 8: 不可变微纳装配操作存证凭单 MicroNanoAssemblyReceipt SHA-256 密码学自签名与防篡改自验 100% 通过")
    void testMicroNanoAssemblyReceiptTamperProofContract() {
        double[] vec = createNormalizedQwenVector(random);

        MicroNanoStateFrame target = new MicroNanoStateFrame(
                "TGT-001", System.nanoTime(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, vec
        );
        MicroNanoStateFrame current = new MicroNanoStateFrame(
                "CUR-001", System.nanoTime(), 0.1, 0.1, 0.02, 2.0, 2.0, 5.0, 18.0, 22000.0, 0.2, vec
        );

        MicroNanoAssemblyReceipt receipt = controlBus.processCycle(
                "SESS-2026", "DIE-CHIP-42", current, target, 18.0, 5.0
        );

        // 原始凭单自验防伪
        assertTrue(receipt.verifySignature(), "合法生成的凭单 SHA-256 签名必须严格通过校验");

        // 模拟黑客篡改飞溅残差数据 (将 0.04 um 改为 5.0 um)
        MicroNanoAssemblyReceipt tamperedReceipt = new MicroNanoAssemblyReceipt(
                receipt.receiptId(),
                receipt.assemblySessionId(),
                receipt.workpieceId(),
                5.0, // 恶意篡改飞溅位移
                receipt.detachmentSuccess(),
                receipt.maxContactForceMicroN(),
                receipt.crushRate(),
                receipt.alignmentErrorMicron(),
                receipt.hocbfSafetyMargin(),
                receipt.qwenGeodesicDistance(),
                receipt.busStatus(),
                receipt.computeLatencyMicros(),
                receipt.signature() // 保持原始签名不变
        );

        assertFalse(tamperedReceipt.verifySignature(), "被篡改数据的凭单必须被密码学验真方法 100% 拒识拦截");
    }
}
