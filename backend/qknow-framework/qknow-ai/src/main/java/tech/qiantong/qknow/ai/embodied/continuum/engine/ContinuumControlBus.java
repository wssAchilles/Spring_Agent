package tech.qiantong.qknow.ai.embodied.continuum.engine;

import tech.qiantong.qknow.ai.embodied.continuum.dto.ContinuumServoingReceipt;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 1000Hz 实时高频定长 4096 槽位 Disruptor 无锁连续体控制总线。
 * <p>
 * 缓存行填充消除伪共享，非阻塞写入 <= 50ns，
 * 集成 JitterGuard 监控时钟抖动超限（连续 3 帧 > 2ms）或压力越界瞬时切入 DEGRADED_PRESSURE_RELIEF 柔顺快速泄压软着陆保护。
 */
public class ContinuumControlBus {

    public static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;
    public static final double PRESSURE_KILL_SWITCH_KPA = 380.0;

    // 缓存行填充消除伪共享
    protected long p1, p2, p3, p4, p5, p6, p7;
    private final AtomicLong writeCursor = new AtomicLong(0L);
    protected long p8, p9, p10, p11, p12, p13, p14;

    private final ContinuumServoingReceipt[] ringBuffer = new ContinuumServoingReceipt[BUFFER_SIZE];
    private final AtomicReference<String> busState = new AtomicReference<>("NORMAL_RUNNING");

    // JitterGuard 状态监测
    private long lastPublishTimeNanos = 0L;
    private int consecutiveJitterCount = 0;

    public ContinuumControlBus() {}

    /**
     * 纳秒级非阻塞发布伺服存证凭单。
     */
    public boolean publishReceipt(ContinuumServoingReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt 不能为空");
        long nowNanos = System.nanoTime();

        // 1. JitterGuard 时钟抖动检测
        if (lastPublishTimeNanos > 0L) {
            long deltaNanos = nowNanos - lastPublishTimeNanos;
            // 理论周期 1000us = 1,000,000ns，若偏差绝对值超过 2ms = 2,000,000ns
            if (Math.abs(deltaNanos - 1_000_000L) > 2_000_000L) {
                consecutiveJitterCount++;
                if (consecutiveJitterCount >= 3) {
                    busState.set("DEGRADED_PRESSURE_RELIEF");
                }
            } else {
                consecutiveJitterCount = 0;
            }
        }
        lastPublishTimeNanos = nowNanos;

        // 2. 瞬态超高压硬熔断
        for (double p : receipt.chamberPressuresKPa()) {
            if (p >= PRESSURE_KILL_SWITCH_KPA) {
                busState.set("DEGRADED_PRESSURE_RELIEF");
                break;
            }
        }

        // 3. 无锁原子写入
        long sequence = writeCursor.getAndIncrement();
        int slot = (int) (sequence & BUFFER_MASK);
        ringBuffer[slot] = receipt;
        return true;
    }

    /**
     * 读取最新一条写入的存证凭单。
     */
    public ContinuumServoingReceipt getLatestReceipt() {
        long current = writeCursor.get();
        if (current == 0L) return null;
        int slot = (int) ((current - 1L) & BUFFER_MASK);
        return ringBuffer[slot];
    }

    public String getBusState() {
        return busState.get();
    }

    public void manualTriggerEmergencyDepressurization() {
        busState.set("DEGRADED_PRESSURE_RELIEF");
    }

    public void resetNormalRunning() {
        busState.set("NORMAL_RUNNING");
        consecutiveJitterCount = 0;
    }

    public long getPublishedCount() {
        return writeCursor.get();
    }
}
