package tech.qiantong.qknow.ai.embodied.suction.engine;

import tech.qiantong.qknow.ai.embodied.suction.dto.MicroSuctionCupArrayEnvelope;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 1000Hz 实时高频定长 4096 槽位 Disruptor 无锁吸附流体控制总线
 * 采用定长环形队列与缓存行填充，消除多线程伪共享，
 * 内置 JitterGuard 时钟抖动监控，异常抖动瞬时切入 DEGRADED_SUCTION_HOLD 稳压保压软着陆模式。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class SuctionFluidControlBus {

    public static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    // 缓存行填充预防伪共享
    private long p1, p2, p3, p4, p5, p6, p7;
    private final AtomicLong sequence = new AtomicLong(-1);
    private long p8, p9, p10, p11, p12, p13, p14;

    public record BusSlot(
            long sequenceId,
            MicroSuctionCupArrayEnvelope envelope,
            double commandedPressureKPa,
            long timestampNs
    ) {}

    private final BusSlot[] ringBuffer = new BusSlot[BUFFER_SIZE];

    // JitterGuard 状态
    private volatile boolean degradedMode = false;
    private volatile int consecutiveJitterCount = 0;
    private volatile long lastPublishTimestampNs = 0L;

    public SuctionFluidControlBus() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            ringBuffer[i] = new BusSlot(-1, null, 0.0, 0L);
        }
    }

    /**
     * 纳秒级非阻塞发布控制指令
     */
    public long publishCommand(MicroSuctionCupArrayEnvelope envelope, double commandedPressureKPa, long currentTimestampNs) {
        long seq = sequence.incrementAndGet();
        int index = (int) (seq & BUFFER_MASK);

        // JitterGuard 监控
        if (lastPublishTimestampNs > 0L) {
            long deltaMs = (currentTimestampNs - lastPublishTimestampNs) / 1_000_000L;
            if (deltaMs > 2L) {
                consecutiveJitterCount++;
                if (consecutiveJitterCount >= 3) {
                    degradedMode = true;
                }
            } else {
                consecutiveJitterCount = 0;
            }
        }
        lastPublishTimestampNs = currentTimestampNs;

        // 若处于降级模式，执行稳压保压软着陆指令 (保持 30.0 kPa 安全负压)
        double finalPressure = degradedMode ? 30.0 : commandedPressureKPa;
        ringBuffer[index] = new BusSlot(seq, envelope, finalPressure, currentTimestampNs);
        return seq;
    }

    public BusSlot getSlot(long seq) {
        int index = (int) (seq & BUFFER_MASK);
        return ringBuffer[index];
    }

    public boolean isDegradedMode() {
        return degradedMode;
    }

    public void resetDegradedMode() {
        this.degradedMode = false;
        this.consecutiveJitterCount = 0;
    }

    public long getLatestSequence() {
        return sequence.get();
    }
}
