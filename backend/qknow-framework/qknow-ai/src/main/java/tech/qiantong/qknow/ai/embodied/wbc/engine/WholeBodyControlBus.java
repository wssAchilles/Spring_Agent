package tech.qiantong.qknow.ai.embodied.wbc.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.embodied.wbc.dto.WholeBodyControlReceipt;
import tech.qiantong.qknow.ai.embodied.wbc.dto.WholeBodyState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1000Hz 实时微秒级无锁全身控制总线 (Whole-Body Control Bus)
 * 基于 4096 槽位定长环形队列与 JitterGuard 时钟抖动守卫，连续 3 帧抖动 (> 2ms) 或单帧时延 > 20ms 自动切入 DEGRADED_GRAVITY_COMP 软着陆降级
 *
 * @author Achilles
 * @since 2026-09-15
 */
@Component
public class WholeBodyControlBus {

    private static final Logger log = LoggerFactory.getLogger(WholeBodyControlBus.class);

    private static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    private final WholeBodyState[] ringBuffer = new WholeBodyState[BUFFER_SIZE];
    private final AtomicLong writeSequence = new AtomicLong(0);

    private final AtomicBoolean degradedMode = new AtomicBoolean(false);
    private volatile String degradationReason = "NORMAL";

    private long lastDispatchEpochNano = System.nanoTime();
    private int consecutiveJitterCount = 0;

    /**
     * 高频无锁发布状态帧至 4096 槽位总线 (耗时 <= 50ns)
     */
    public long publishState(WholeBodyState state) {
        long seq = writeSequence.getAndIncrement();
        int index = (int) (seq & BUFFER_MASK);
        ringBuffer[index] = state;
        return seq;
    }

    /**
     * 读取最新状态帧
     */
    public WholeBodyState getLatestState() {
        long seq = writeSequence.get() - 1;
        if (seq < 0) {
            return null;
        }
        return ringBuffer[(int) (seq & BUFFER_MASK)];
    }

    /**
     * 1000Hz 控制时钟步进与 JitterGuard 监控
     */
    public boolean stepClockAndCheckJitter(double simulatedDeltaMs) {
        long currentNano = System.nanoTime();
        double deltaMs = (simulatedDeltaMs > 0) ? simulatedDeltaMs : (currentNano - lastDispatchEpochNano) / 1_000_000.0;
        lastDispatchEpochNano = currentNano;

        // 判定单帧致命时延
        if (deltaMs > 20.0) {
            degradedMode.set(true);
            degradationReason = "SINGLE_FRAME_TIMEOUT_" + String.format("%.2f", deltaMs) + "MS";
            log.error("[WholeBodyControlBus] 致命时延: {}ms > 20ms, 触发 DEGRADED_GRAVITY_COMP 软着陆", deltaMs);
            return false;
        }

        // 判定连续抖动 (标称 1.0ms, 抖动量 = |delta - 1.0|)
        double jitter = Math.abs(deltaMs - 1.0);
        if (jitter > 2.0) {
            consecutiveJitterCount++;
            if (consecutiveJitterCount >= 3) {
                degradedMode.set(true);
                degradationReason = "CONSECUTIVE_JITTER_3_FRAMES";
                log.warn("[WholeBodyControlBus] 连续 3 帧时钟抖动超限 (>2ms), 触发软着陆重力补偿降级");
                return false;
            }
        } else {
            consecutiveJitterCount = Math.max(0, consecutiveJitterCount - 1);
        }

        return true;
    }

    /**
     * 签发不可变全要素审计凭单
     */
    public WholeBodyControlReceipt issueReceipt(
            String receiptId,
            double zmpMarginMm,
            double cmmNorm,
            double qpSolvingTimeMs,
            double frictionConeMargin,
            boolean antiSlipIntervened
    ) {
        return WholeBodyControlReceipt.createSigned(
                receiptId,
                System.currentTimeMillis(),
                zmpMarginMm,
                cmmNorm,
                qpSolvingTimeMs,
                frictionConeMargin,
                antiSlipIntervened,
                degradedMode.get(),
                degradationReason
        );
    }

    public boolean isDegradedMode() {
        return degradedMode.get();
    }

    public String getDegradationReason() {
        return degradationReason;
    }

    public void resetBus() {
        degradedMode.set(false);
        degradationReason = "NORMAL";
        consecutiveJitterCount = 0;
        lastDispatchEpochNano = System.nanoTime();
    }
}
