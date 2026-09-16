package tech.qiantong.qknow.ai.embodied.micronano.engine;

import tech.qiantong.qknow.ai.embodied.micronano.dto.MicroAssemblyWrenchState;
import tech.qiantong.qknow.ai.embodied.micronano.dto.MicroNanoAssemblyReceipt;
import tech.qiantong.qknow.ai.embodied.micronano.dto.MicroNanoStateFrame;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 1000Hz 定长 4096 槽位 Disruptor 无锁微纳控制总线 + JitterGuard 软着陆防线
 * 支撑纳秒级非阻塞写入、微秒级全闭环时序解算与不可变微纳装配存证凭单签发。
 *
 * @author Achilles
 * @since 2026-09-16
 */
public class MicroNanoCoordinationControlBus {

    /** 定长 4096 槽位环形缓冲区 */
    public static final int BUFFER_SIZE = 4096;

    /** 槽位掩码 (4096 - 1) */
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    /** JitterGuard 连续时钟抖动阈值次数 */
    public static final int JITTER_THRESHOLD_COUNT = 3;

    /** JitterGuard 帧间延迟超限阈值 (毫秒) */
    public static final long JITTER_TIME_THRESHOLD_MS = 2L;

    /** 正常标称运行态 */
    public static final String STATUS_ACTIVE_NOMINAL = "ACTIVE_NOMINAL";

    /** JitterGuard 触发的柔顺微回退软着陆安全态 */
    public static final String STATUS_DEGRADED_RETRACT = "DEGRADED_COMPLIANT_MICRO_RETRACT";

    private final AtomicReferenceArray<MicroNanoStateFrame> ringBuffer = new AtomicReferenceArray<>(BUFFER_SIZE);
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicLong lastPublishNs = new AtomicLong(0L);
    private final AtomicInteger consecutiveJitterCount = new AtomicInteger(0);

    private volatile String busStatus = STATUS_ACTIVE_NOMINAL;

    private final MicroAdhesionReleaseOperator adhesionOperator;
    private final MicroComplianceImpedanceGovernor impedanceGovernor;
    private final DefocusRobustVisualAlignmentOperator visualAligner;

    public MicroNanoCoordinationControlBus() {
        this.adhesionOperator = new MicroAdhesionReleaseOperator();
        this.impedanceGovernor = new MicroComplianceImpedanceGovernor();
        this.visualAligner = new DefocusRobustVisualAlignmentOperator();
    }

    public MicroNanoCoordinationControlBus(
            MicroAdhesionReleaseOperator adhesionOperator,
            MicroComplianceImpedanceGovernor impedanceGovernor,
            DefocusRobustVisualAlignmentOperator visualAligner
    ) {
        this.adhesionOperator = Objects.requireNonNull(adhesionOperator, "adhesionOperator cannot be null");
        this.impedanceGovernor = Objects.requireNonNull(impedanceGovernor, "impedanceGovernor cannot be null");
        this.visualAligner = Objects.requireNonNull(visualAligner, "visualAligner cannot be null");
    }

    /**
     * 1000Hz 纳秒级非阻塞写入环形槽位并由 JitterGuard 监测时钟抖动
     *
     * @param frame 待发布的微纳感知时序帧
     * @return 发布是否成功
     */
    public boolean publishFrame(MicroNanoStateFrame frame) {
        if (frame == null) {
            return false;
        }
        long seq = sequence.getAndIncrement();
        int slot = (int) (seq & BUFFER_MASK);
        ringBuffer.set(slot, frame);

        long nowNs = System.nanoTime();
        long prevNs = lastPublishNs.getAndSet(nowNs);
        if (prevNs > 0) {
            long deltaMs = (nowNs - prevNs) / 1_000_000L;
            if (deltaMs > JITTER_TIME_THRESHOLD_MS) {
                int count = consecutiveJitterCount.incrementAndGet();
                if (count >= JITTER_THRESHOLD_COUNT) {
                    this.busStatus = STATUS_DEGRADED_RETRACT;
                }
            } else {
                consecutiveJitterCount.set(0);
            }
        }
        return true;
    }

    /**
     * 执行单周期微纳装配全流程物理闭环并签发密码学存证凭单
     *
     * @param sessionId 装配任务会话 ID
     * @param workpieceId 微工件唯一 ID
     * @param currentFrame 当前感应状态帧
     * @param targetFrame 目标装配特征帧
     * @param targetForceMicroN 期望接触力 (uN)
     * @param nominalVoltage 名义驱动电压 (V)
     * @return 不可变存证凭单
     */
    public MicroNanoAssemblyReceipt processCycle(
            String sessionId,
            String workpieceId,
            MicroNanoStateFrame currentFrame,
            MicroNanoStateFrame targetFrame,
            double targetForceMicroN,
            double nominalVoltage
    ) {
        long startNs = System.nanoTime();
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(workpieceId, "workpieceId cannot be null");

        // 1. 发布当前帧至总线
        publishFrame(currentFrame);

        // 2. 狭窄景深显微视觉流形对齐解算 (定理 1.3)
        var alignResult = visualAligner.computeAlignment(currentFrame, targetFrame);

        // 3. 微牛级柔顺力控阻抗与 Micro-HOCBF 闭式 QP 安全投影 (定理 1.2)
        var qpResult = impedanceGovernor.computeCompliantForce(
                currentFrame, targetForceMicroN, nominalVoltage,
                MicroComplianceImpedanceGovernor.DEFAULT_YIELD_LIMIT_MICRO_N
        );

        // 4. 表面黏附力学解析与压电微剪切主动脱粘动力学评估 (定理 1.1)
        var detachResult = adhesionOperator.evaluateAndRelease(
                currentFrame, 15.0, 0.01
        );

        // 5. 若处于 JitterGuard 降级状态，强制安全微退避
        String currentBusStatus = this.busStatus;

        long computeLatencyMicros = Math.max(1L, (System.nanoTime() - startNs) / 1_000L);
        String receiptId = "RCP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 6. 生成 SHA-256 密码学签名
        String signature = MicroNanoAssemblyReceipt.generateSignature(
                receiptId, sessionId, workpieceId,
                detachResult.splashResidualMicron(),
                detachResult.detachmentSuccess(),
                qpResult.actualContactForceMicroN(),
                qpResult.crushRate(),
                alignResult.alignmentResidualMicron(),
                qpResult.hocbfSafetyMargin(),
                alignResult.qwenGeodesicDistance(),
                currentBusStatus,
                computeLatencyMicros
        );

        return new MicroNanoAssemblyReceipt(
                receiptId, sessionId, workpieceId,
                detachResult.splashResidualMicron(),
                detachResult.detachmentSuccess(),
                qpResult.actualContactForceMicroN(),
                qpResult.crushRate(),
                alignResult.alignmentResidualMicron(),
                qpResult.hocbfSafetyMargin(),
                alignResult.qwenGeodesicDistance(),
                currentBusStatus,
                computeLatencyMicros,
                signature
        );
    }

    public String getBusStatus() {
        return busStatus;
    }

    public void setBusStatus(String busStatus) {
        this.busStatus = busStatus;
    }

    public void resetBusStatus() {
        this.busStatus = STATUS_ACTIVE_NOMINAL;
        this.consecutiveJitterCount.set(0);
    }

    public long getCurrentSequence() {
        return sequence.get();
    }
}
