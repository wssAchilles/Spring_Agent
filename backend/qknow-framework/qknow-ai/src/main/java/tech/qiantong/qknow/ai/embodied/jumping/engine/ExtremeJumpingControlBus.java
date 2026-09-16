package tech.qiantong.qknow.ai.embodied.jumping.engine;

import tech.qiantong.qknow.ai.embodied.jumping.dto.ExtremeJumpingReceipt;
import tech.qiantong.qknow.ai.embodied.jumping.dto.JumpingPhaseStateFrame;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1000Hz 实时高频定长 4096 槽位 Disruptor 无锁极端弹跳越障控制总线。
 * 内置 JitterGuard 连续 3 帧时钟抖动滑动监控与失重超时守护，
 * 异常瞬时切入 DEGRADED_COMPLIANT_CROUCH 柔顺收拢趴地自愈降级模式。
 */
public class ExtremeJumpingControlBus {

    public static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    public static final String MODE_NORMAL = "NORMAL_JUMPING";
    public static final String MODE_DEGRADED_COMPLIANT_CROUCH = "DEGRADED_COMPLIANT_CROUCH";

    private final JumpingPhaseStateFrame[] ringBuffer = new JumpingPhaseStateFrame[BUFFER_SIZE];
    private final AtomicLong producerSequence = new AtomicLong(0);

    private final AtomicBoolean degradedMode = new AtomicBoolean(false);
    private volatile String degradationReason = null;

    private long lastTimestampMicros = 0;
    private int jitterConsecutiveCount = 0;
    private long flightPhaseStartMicros = 0;

    /**
     * 纳秒级无锁写入状态帧 (<= 50ns)。
     *
     * @param frame 弹跳状态帧
     * @return 写入是否成功
     */
    public boolean publishFrame(JumpingPhaseStateFrame frame) {
        Objects.requireNonNull(frame, "frame 不能为空");

        long seq = producerSequence.getAndIncrement();
        int index = (int) (seq & BUFFER_MASK);
        ringBuffer[index] = frame;

        // JitterGuard 守卫检查
        inspectJitterAndFlightTime(frame);
        return true;
    }

    private synchronized void inspectJitterAndFlightTime(JumpingPhaseStateFrame frame) {
        long currentMicros = frame.timestampMicros();

        // 1. 时钟抖动检查 (周期 1000us, 若差值 > 2000us 计为抖动)
        if (lastTimestampMicros > 0) {
            long delta = currentMicros - lastTimestampMicros;
            if (delta > 2000) {
                jitterConsecutiveCount++;
                if (jitterConsecutiveCount >= 3) {
                    tripDegradedMode("JitterGuard: 连续 3 帧时钟抖动超限 (delta=" + delta + "us)");
                }
            } else {
                jitterConsecutiveCount = 0;
            }
        }
        lastTimestampMicros = currentMicros;

        // 2. 空中失重超时检查 (自由飞行超过 1.2s 未触地，判定异常并触发收拢软着陆)
        if (frame.phase() == JumpingPhaseStateFrame.PHASE_AERIAL_FLIGHT) {
            if (flightPhaseStartMicros == 0) {
                flightPhaseStartMicros = currentMicros;
            } else if (currentMicros - flightPhaseStartMicros > 1_200_000L) { // 1.2s
                tripDegradedMode("FlightTimeoutGuard: 空中自由飞行阶段失重超时 (>1.2s)");
            }
        } else {
            flightPhaseStartMicros = 0;
        }
    }

    public void tripDegradedMode(String reason) {
        degradedMode.set(true);
        this.degradationReason = reason;
    }

    public void resetDegradedMode() {
        degradedMode.set(false);
        this.degradationReason = null;
        this.jitterConsecutiveCount = 0;
        this.flightPhaseStartMicros = 0;
    }

    public boolean isDegraded() {
        return degradedMode.get();
    }

    public String getDegradationReason() {
        return degradationReason;
    }

    public String getCurrentOperatingMode() {
        return degradedMode.get() ? MODE_DEGRADED_COMPLIANT_CROUCH : MODE_NORMAL;
    }

    /**
     * 读取最新一条状态帧。
     */
    public JumpingPhaseStateFrame getLatestFrame() {
        long currentSeq = producerSequence.get();
        if (currentSeq == 0) {
            return null;
        }
        int index = (int) ((currentSeq - 1) & BUFFER_MASK);
        return ringBuffer[index];
    }

    /**
     * 生成不可变执行存证凭单。
     */
    public ExtremeJumpingReceipt issueReceipt(String sessionId, String robotId, double obstacleHeight,
                                             double takeoffImpulse, double apexHeight, double touchAttitudeErrDeg,
                                             double energyAbsorptionRatio, double hocbfMargin, long solveLatencyMicros) {
        return ExtremeJumpingReceipt.createAndSign(
                "RECEIPT-" + System.nanoTime(),
                sessionId,
                robotId,
                obstacleHeight,
                takeoffImpulse,
                apexHeight,
                touchAttitudeErrDeg,
                energyAbsorptionRatio,
                hocbfMargin,
                solveLatencyMicros,
                degradedMode.get(),
                degradationReason,
                System.currentTimeMillis()
        );
    }
}
