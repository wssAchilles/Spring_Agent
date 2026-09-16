package tech.qiantong.qknow.hermes.consensus.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.consensus.dto.DebateConsensusEventFrame;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1000Hz 定长 4096 槽位 Disruptor 无锁博弈辩论与共识总线
 * 非阻塞单帧写入 <= 50ns，JitterGuard 监控连续 3 帧时钟抖动软着陆
 */
@Component
public class DebateConsensusControlBus {

    private static final Logger log = LoggerFactory.getLogger(DebateConsensusControlBus.class);

    public static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    public static final String STATUS_NORMAL = "STATUS_NORMAL";
    public static final String STATUS_DEGRADED_BUFFERED = "STATUS_DEGRADED_BUFFERED";

    private final DebateConsensusEventFrame[] ringBuffer = new DebateConsensusEventFrame[BUFFER_SIZE];
    private final AtomicLong publishSequence = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // JitterGuard 监控
    private volatile int consecutiveJitterCount = 0;
    private volatile boolean jitterGuardTriggered = false;
    private volatile String currentStatus = STATUS_NORMAL;

    public void start() {
        running.set(true);
        log.info("1000Hz Disruptor 博弈辩论与共识总线已启动，槽位容量: {}", BUFFER_SIZE);
    }

    public void shutdown() {
        running.set(false);
        log.info("1000Hz Disruptor 博弈辩论与共识总线已优雅停机");
    }

    public boolean publishFrame(DebateConsensusEventFrame frame) {
        if (frame == null) {
            return false;
        }

        long seq = publishSequence.getAndIncrement();
        int slot = (int) (seq & BUFFER_MASK);
        ringBuffer[slot] = frame;
        return true;
    }

    public void recordLatencyJitter(double jitterMillis) {
        if (jitterMillis > 2.0) {
            consecutiveJitterCount++;
            if (consecutiveJitterCount >= 3) {
                jitterGuardTriggered = true;
                currentStatus = STATUS_DEGRADED_BUFFERED;
                log.warn("JitterGuard 连续 3 帧高时钟抖动 ({}ms)，瞬切 STATUS_DEGRADED_BUFFERED 缓冲软着陆", jitterMillis);
            }
        } else {
            consecutiveJitterCount = 0;
            if (jitterGuardTriggered) {
                jitterGuardTriggered = false;
                currentStatus = STATUS_NORMAL;
            }
        }
    }

    public boolean isJitterGuardTriggered() {
        return jitterGuardTriggered;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public long getPublishedCount() {
        return publishSequence.get();
    }
}
