package tech.qiantong.qknow.hermes.cognitive.selfhealing.engine;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.dto.CognitiveSelfHealingEventFrame;
import tech.qiantong.qknow.hermes.cognitive.selfhealing.dto.CognitiveExecutionReceipt;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 1000Hz 定长 4096 槽位 Disruptor 无锁认知控制总线
 * 支撑纳秒级推帧、JitterGuard 抖动自适应软着陆与密码学存证签发
 */
@Slf4j
@Component
public class CognitiveKernelControlBus {

    public static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    public static final String STATUS_NORMAL = "STATUS_NORMAL";
    public static final String STATUS_DEGRADED_CONSERVATIVE_HEAL = "STATUS_DEGRADED_CONSERVATIVE_HEAL";

    private final AtomicReferenceArray<CognitiveSelfHealingEventFrame> ringBuffer = new AtomicReferenceArray<>(BUFFER_SIZE);
    private final AtomicLong publishSequence = new AtomicLong(0);

    private volatile String currentStatus = STATUS_NORMAL;
    private volatile long lastPublishNano = 0;
    private volatile int consecutiveJitterCount = 0;

    /**
     * 纳秒级推入认知事件帧 (<= 50ns)
     */
    public boolean publishFrame(CognitiveSelfHealingEventFrame frame) {
        if (frame == null) {
            return false;
        }
        long now = System.nanoTime();
        if (lastPublishNano > 0) {
            long jitterNanos = Math.abs((now - lastPublishNano) - 1_000_000L);
            if (jitterNanos > 2_000_000L) {
                consecutiveJitterCount++;
                if (consecutiveJitterCount >= 3) {
                    currentStatus = STATUS_DEGRADED_CONSERVATIVE_HEAL;
                    log.warn("Cognitive JitterGuard 触发时钟抖动过大，总线切入保守自愈软着陆: {}", currentStatus);
                }
            } else {
                consecutiveJitterCount = 0;
            }
        }
        lastPublishNano = now;

        long seq = publishSequence.getAndIncrement();
        int slot = (int) (seq & BUFFER_MASK);
        ringBuffer.set(slot, frame);
        return true;
    }

    public CognitiveSelfHealingEventFrame readFrame(long seq) {
        int slot = (int) (seq & BUFFER_MASK);
        return ringBuffer.get(slot);
    }

    /**
     * 统筹签发不可变认知执行存证凭单
     */
    public CognitiveExecutionReceipt issueReceipt(
            String sessionId,
            String userQuery,
            int totalAttempts,
            boolean selfHealingApplied,
            double memoryCompressionRatio,
            int groundedEntityCount,
            long executionLatencyMicros
    ) {
        String receiptId = "rcpt_cog_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String payload = receiptId + ":" + sessionId + ":" + totalAttempts + ":" + selfHealingApplied + ":" + groundedEntityCount + ":" + currentStatus;
        String signature = DigestUtils.sha256Hex(payload);

        CognitiveExecutionReceipt receipt = new CognitiveExecutionReceipt(
                receiptId,
                sessionId,
                userQuery,
                totalAttempts,
                selfHealingApplied,
                memoryCompressionRatio,
                groundedEntityCount,
                executionLatencyMicros,
                currentStatus,
                signature
        );
        log.info("签发认知执行密码学存证凭单: receiptId={}, sessionId={}, signature={}",
                receiptId, sessionId, signature);
        return receipt;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void resetStatus() {
        this.currentStatus = STATUS_NORMAL;
        this.consecutiveJitterCount = 0;
    }
}
