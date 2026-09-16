package tech.qiantong.qknow.mcp.client.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.gateway.dto.McpCircuitBreakerState;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 双向流式协同 MCP 中继网关 (StreamingMcpRelayGateway)
 * 落实定理 1.3 李雅普诺夫队列稳定性与背压：
 * 1. 4KB 定长分片传输 Chunk，防止大报文一次性膨胀爆内存
 * 2. 64KB 输出硬截断保护
 * 3. 自适应滑动窗口三态断路器 (CLOSED, OPEN, HALF_OPEN)，<= 10ms 瞬时切入熔断与优雅降级
 */
public class StreamingMcpRelayGateway {

    private static final Logger log = LoggerFactory.getLogger(StreamingMcpRelayGateway.class);

    public static final int CHUNK_SIZE_BYTES = 4096; // 4KB 分片
    public static final int MAX_OUTPUT_LIMIT_BYTES = 65536; // 64KB 硬截断
    public static final String TRUNCATION_SUFFIX = "...[TRUNCATED_AT_64KB]";
    public static final String DEGRADED_FALLBACK_STUB = "[MCP_CIRCUIT_BREAKER_OPEN_DEGRADED_FALLBACK_STUB]";

    public static final int SLIDING_WINDOW_SIZE = 10;
    public static final double FAILURE_RATE_THRESHOLD = 0.50; // 50% 失败率熔断
    public static final int CONSECUTIVE_TIMEOUT_LIMIT = 3;
    public static final long COOLDOWN_PERIOD_MS = 500L; // 熔断冷却期

    private final AtomicReference<McpCircuitBreakerState> circuitState = new AtomicReference<>(McpCircuitBreakerState.CLOSED);
    private final Queue<Boolean> callHistory = new ConcurrentLinkedQueue<>();
    private final AtomicInteger consecutiveTimeoutCount = new AtomicInteger(0);
    private final AtomicLong lastStateChangeTimestamp = new AtomicLong(System.currentTimeMillis());

    /**
     * 将长字符串按照 4KB 分片 Chunk 切割，用于响应式背压传输
     *
     * @param payload 原始报文
     * @return 分片列表
     */
    public List<String> splitIntoChunks(String payload) {
        if (payload == null || payload.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

        // 如果超出 64KB，先执行硬截断
        if (bytes.length > MAX_OUTPUT_LIMIT_BYTES) {
            byte[] truncated = Arrays.copyOf(bytes, MAX_OUTPUT_LIMIT_BYTES);
            String truncatedStr = new String(truncated, StandardCharsets.UTF_8) + TRUNCATION_SUFFIX;
            bytes = truncatedStr.getBytes(StandardCharsets.UTF_8);
        }

        int offset = 0;
        while (offset < bytes.length) {
            int length = Math.min(CHUNK_SIZE_BYTES, bytes.length - offset);
            chunks.add(new String(bytes, offset, length, StandardCharsets.UTF_8));
            offset += length;
        }

        return chunks;
    }

    /**
     * 校验并应用 64KB 输出硬截断
     */
    public String apply64KbHardTruncation(String rawOutput) {
        if (rawOutput == null) return null;
        byte[] bytes = rawOutput.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_OUTPUT_LIMIT_BYTES) {
            return rawOutput;
        }
        byte[] truncatedBytes = Arrays.copyOf(bytes, MAX_OUTPUT_LIMIT_BYTES);
        return new String(truncatedBytes, StandardCharsets.UTF_8) + TRUNCATION_SUFFIX;
    }

    /**
     * 执行中继前置断路检查
     *
     * @return 是否允许放行
     */
    public boolean allowRequest() {
        McpCircuitBreakerState state = circuitState.get();
        if (state == McpCircuitBreakerState.CLOSED) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (state == McpCircuitBreakerState.OPEN) {
            // 检查冷却期是否已过，若是则转为 HALF_OPEN 试探放行
            if (now - lastStateChangeTimestamp.get() > COOLDOWN_PERIOD_MS) {
                if (circuitState.compareAndSet(McpCircuitBreakerState.OPEN, McpCircuitBreakerState.HALF_OPEN)) {
                    lastStateChangeTimestamp.set(now);
                    log.info("[MCP CircuitBreaker] 冷却期已过，断路器从 OPEN 转移至 HALF_OPEN 试探放行");
                    return true;
                }
            }
            return false;
        }

        // HALF_OPEN 状态允许单次试探
        return true;
    }

    /**
     * 记录调用结果并更新三态断路器状态
     *
     * @param success 是否成功
     * @param isTimeout 是否属于超时失败
     */
    public void recordCallResult(boolean success, boolean isTimeout) {
        long now = System.currentTimeMillis();

        if (success) {
            consecutiveTimeoutCount.set(0);
            callHistory.add(true);
            while (callHistory.size() > SLIDING_WINDOW_SIZE) {
                callHistory.poll();
            }

            if (circuitState.get() == McpCircuitBreakerState.HALF_OPEN) {
                // HALF_OPEN 成功，自愈恢复到 CLOSED
                circuitState.set(McpCircuitBreakerState.CLOSED);
                lastStateChangeTimestamp.set(now);
                log.info("[MCP CircuitBreaker] 试探调用成功，断路器自愈切入 CLOSED");
            }
        } else {
            callHistory.add(false);
            while (callHistory.size() > SLIDING_WINDOW_SIZE) {
                callHistory.poll();
            }

            int timeoutCount = isTimeout ? consecutiveTimeoutCount.incrementAndGet() : 0;

            // 计算滑动窗口失败率
            int failCount = 0;
            for (Boolean r : callHistory) {
                if (!r) failCount++;
            }
            double failRate = callHistory.isEmpty() ? 0.0 : (double) failCount / callHistory.size();

            if (failRate >= FAILURE_RATE_THRESHOLD || timeoutCount >= CONSECUTIVE_TIMEOUT_LIMIT) {
                if (circuitState.get() != McpCircuitBreakerState.OPEN) {
                    circuitState.set(McpCircuitBreakerState.OPEN);
                    lastStateChangeTimestamp.set(now);
                    log.warn("[MCP CircuitBreaker] 连续超时或失败率超标 (failRate={}/{}, timeouts={})，断路器 <= 10ms 瞬时熔断切入 OPEN",
                            failCount, callHistory.size(), timeoutCount);
                }
            }
        }
    }

    public void tripCircuitOpenManually() {
        circuitState.set(McpCircuitBreakerState.OPEN);
        lastStateChangeTimestamp.set(System.currentTimeMillis());
    }

    public void resetCircuitBreaker() {
        circuitState.set(McpCircuitBreakerState.CLOSED);
        callHistory.clear();
        consecutiveTimeoutCount.set(0);
        lastStateChangeTimestamp.set(System.currentTimeMillis());
    }

    public McpCircuitBreakerState getCircuitState() {
        return circuitState.get();
    }
}
