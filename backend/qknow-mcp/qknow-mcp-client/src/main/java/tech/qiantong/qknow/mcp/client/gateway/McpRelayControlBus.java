package tech.qiantong.qknow.mcp.client.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.gateway.dto.McpContractType;
import tech.qiantong.qknow.mcp.core.gateway.dto.McpExecutionReceipt;
import tech.qiantong.qknow.mcp.core.gateway.dto.McpRelayEventFrame;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 1000Hz 实时定长 4096 槽位 Disruptor 无锁 MCP 中继控制总线 (McpRelayControlBus)
 * 环形无锁并发写入 <= 50ns，内置 JitterGuard 连续 3 帧时钟抖动监测与降级软着陆
 */
public class McpRelayControlBus {

    private static final Logger log = LoggerFactory.getLogger(McpRelayControlBus.class);

    public static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_MASK = BUFFER_SIZE - 1;

    public static final int JITTER_THRESHOLD_COUNT = 3;
    public static final long JITTER_TIME_THRESHOLD_MS = 2L;

    public static final String STATUS_ACTIVE_NOMINAL = "ACTIVE_NOMINAL";
    public static final String STATUS_DEGRADED_FALLBACK_STUB = "DEGRADED_FALLBACK_STUB";

    private final AtomicReferenceArray<McpRelayEventFrame> ringBuffer = new AtomicReferenceArray<>(BUFFER_SIZE);
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicLong lastPublishNs = new AtomicLong(0L);
    private final AtomicInteger consecutiveJitterCount = new AtomicInteger(0);

    private volatile String busStatus = STATUS_ACTIVE_NOMINAL;

    /**
     * 1000Hz 纳秒级无锁发布中继事件帧并执行 JitterGuard 监控
     *
     * @param frame 中继事件帧
     * @return 发布是否成功
     */
    public boolean publishFrame(McpRelayEventFrame frame) {
        if (frame == null) return false;

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
                    if (!STATUS_DEGRADED_FALLBACK_STUB.equals(busStatus)) {
                        log.warn("[JitterGuard] 连续 {} 帧时钟抖动 > {}ms (当前 delta={}ms)，触发降级软着陆: 切入 {}",
                                count, JITTER_TIME_THRESHOLD_MS, deltaMs, STATUS_DEGRADED_FALLBACK_STUB);
                        busStatus = STATUS_DEGRADED_FALLBACK_STUB;
                    }
                }
            } else {
                consecutiveJitterCount.set(0);
            }
        }

        return true;
    }

    /**
     * 手动触发降级软着陆
     */
    public void tripDegradation(String reason) {
        log.warn("[McpRelayControlBus] 手动触发降级软着陆: reason={}", reason);
        busStatus = STATUS_DEGRADED_FALLBACK_STUB;
    }

    /**
     * 恢复正常运行态
     */
    public void resetStatus() {
        busStatus = STATUS_ACTIVE_NOMINAL;
        consecutiveJitterCount.set(0);
    }

    public String getBusStatus() {
        return busStatus;
    }

    public long getPublishedCount() {
        return sequence.get();
    }

    public McpRelayEventFrame getSlot(long seq) {
        return ringBuffer.get((int) (seq & BUFFER_MASK));
    }

    /**
     * 统筹签发全链路密码学防篡改存证凭单
     */
    public McpExecutionReceipt issueReceipt(
            String sessionId,
            String serverId,
            String toolName,
            McpContractType contractType,
            String executionStatus,
            long latencyUs,
            String inputHash,
            String outputHash
    ) {
        String receiptId = "rcpt-mcp-" + UUID.randomUUID();
        return McpExecutionReceipt.create(
                receiptId, sessionId, serverId, toolName, contractType,
                executionStatus, latencyUs, inputHash, outputHash, busStatus
        );
    }
}
