package tech.qiantong.qknow.mcp.client.orchestration.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpOrchestrationReceipt;
import tech.qiantong.qknow.mcp.core.orchestration.dto.StreamingToolChunkEventFrame;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 1000Hz 定长 4096 槽位 Disruptor 无锁编排中枢总线
 * 纯 Java 21 无锁并发，JitterGuard 滑动抖动守卫与密码学存证凭单统一签发
 */
@Component
public class McpOrchestrationControlBus {

    private static final Logger log = LoggerFactory.getLogger(McpOrchestrationControlBus.class);

    public static final int BUFFER_SIZE = 4096;
    public static final int BUFFER_MASK = BUFFER_SIZE - 1;
    public static final long JITTER_THRESHOLD_MICROS = 2000L;

    public static final String STATUS_NORMAL = "STATUS_NORMAL";
    public static final String STATUS_DEGRADED_TOOL_FALLBACK = "STATUS_DEGRADED_TOOL_FALLBACK";

    private final StreamingToolChunkEventFrame[] ringBuffer = new StreamingToolChunkEventFrame[BUFFER_SIZE];
    private final AtomicLong publishSequence = new AtomicLong(0);

    private volatile String currentStatus = STATUS_NORMAL;
    private volatile long lastPublishMicros = System.currentTimeMillis() * 1000L;
    private int consecutiveJitterCount = 0;

    public boolean publishChunkFrame(StreamingToolChunkEventFrame frame) {
        if (frame == null) return false;

        long nowMicros = System.currentTimeMillis() * 1000L;
        long deltaMicros = nowMicros - lastPublishMicros;
        lastPublishMicros = nowMicros;

        if (deltaMicros > JITTER_THRESHOLD_MICROS) {
            consecutiveJitterCount++;
            if (consecutiveJitterCount >= 3) {
                this.currentStatus = STATUS_DEGRADED_TOOL_FALLBACK;
                log.warn("JitterGuard 监测到工具总线连续时钟抖动 (delta={}μs), 瞬切降级态", deltaMicros);
            }
        } else {
            consecutiveJitterCount = 0;
        }

        long seq = publishSequence.getAndIncrement();
        int slot = (int) (seq & BUFFER_MASK);
        ringBuffer[slot] = frame;
        return true;
    }

    public StreamingToolChunkEventFrame readChunkFrame(long sequence) {
        int slot = (int) (sequence & BUFFER_MASK);
        return ringBuffer[slot];
    }

    public McpOrchestrationReceipt issueReceipt(
            String sessionId,
            String userIntent,
            List<String> plannedToolchain,
            int cycleDetectedCount,
            int selfHealingAttempts,
            long routeLatencyMicros,
            long totalExecutionLatencyMicros
    ) {
        String receiptId = "rcpt_mcp_" + UUID.randomUUID().toString().substring(0, 12);
        McpOrchestrationReceipt receipt = McpOrchestrationReceipt.create(
                receiptId, sessionId, userIntent, plannedToolchain, cycleDetectedCount,
                selfHealingAttempts, routeLatencyMicros, totalExecutionLatencyMicros, currentStatus
        );
        log.info("签发 MCP 编排不可变密码学存证凭单: receiptId={}, sessionId={}, signature={}",
                receipt.receiptId(), receipt.sessionId(), receipt.signature());
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
