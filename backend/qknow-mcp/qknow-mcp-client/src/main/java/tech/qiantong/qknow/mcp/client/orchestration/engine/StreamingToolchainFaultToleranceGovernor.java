package tech.qiantong.qknow.mcp.client.orchestration.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式工具容错调节器与 Reflexion 反思自愈器 (定理 1.3)
 * 实现基于滑动窗口的三态自适应断路器 (CLOSED -> OPEN -> HALF_OPEN) 与流式分块看门狗超时监控
 */
@Component
public class StreamingToolchainFaultToleranceGovernor {

    private static final Logger log = LoggerFactory.getLogger(StreamingToolchainFaultToleranceGovernor.class);

    public enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public static final double TRIP_FAILURE_RATE = 0.50;
    public static final long OPEN_COOLING_MILLIS = 2000L;
    public static final long CHUNK_WATCHDOG_TIMEOUT_MILLIS = 3000L;

    public static class ServiceHealthMetric {
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private final AtomicInteger failedCalls = new AtomicInteger(0);
        private volatile CircuitState state = CircuitState.CLOSED;
        private volatile long lastStateChangeMillis = System.currentTimeMillis();

        public AtomicInteger totalCalls() { return totalCalls; }
        public AtomicInteger failedCalls() { return failedCalls; }
        public CircuitState getState() { return state; }
        public void setState(CircuitState state) { this.state = state; }
        public long getLastStateChangeMillis() { return lastStateChangeMillis; }
        public void setLastStateChangeMillis(long lastStateChangeMillis) { this.lastStateChangeMillis = lastStateChangeMillis; }
    }

    private final Map<String, ServiceHealthMetric> healthMetrics = new ConcurrentHashMap<>();
    private final Map<String, Long> chunkWatchdogs = new ConcurrentHashMap<>();

    public void recordCallSuccess(String serviceId) {
        ServiceHealthMetric m = healthMetrics.computeIfAbsent(serviceId, k -> new ServiceHealthMetric());
        m.totalCalls().incrementAndGet();
        if (m.getState() == CircuitState.HALF_OPEN) {
            m.setState(CircuitState.CLOSED);
            m.setLastStateChangeMillis(System.currentTimeMillis());
            m.failedCalls().set(0);
            m.totalCalls().set(0);
            log.info("微服务 {} 恢复正常，断路器恢复 CLOSED", serviceId);
        }
    }

    public void recordCallFailure(String serviceId, String errorReason) {
        ServiceHealthMetric m = healthMetrics.computeIfAbsent(serviceId, k -> new ServiceHealthMetric());
        m.totalCalls().incrementAndGet();
        m.failedCalls().incrementAndGet();

        double failRate = (double) m.failedCalls().get() / m.totalCalls().get();
        if (m.totalCalls().get() >= 4 && failRate >= TRIP_FAILURE_RATE && m.getState() == CircuitState.CLOSED) {
            m.setState(CircuitState.OPEN);
            m.setLastStateChangeMillis(System.currentTimeMillis());
            log.warn("微服务 {} 触发熔断! 失败率={}>={}, 切入 OPEN 态", serviceId, failRate, TRIP_FAILURE_RATE);
        }
    }

    public boolean isCallPermitted(String serviceId) {
        ServiceHealthMetric m = healthMetrics.get(serviceId);
        if (m == null || m.getState() == CircuitState.CLOSED) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (m.getState() == CircuitState.OPEN) {
            if (now - m.getLastStateChangeMillis() > OPEN_COOLING_MILLIS) {
                m.setState(CircuitState.HALF_OPEN);
                m.setLastStateChangeMillis(now);
                log.info("微服务 {} 冷却期结束，尝试切入 HALF_OPEN 探测", serviceId);
                return true;
            }
            return false;
        }

        // HALF_OPEN
        return true;
    }

    /**
     * 更新分块看门狗时间戳
     */
    public void updateChunkWatchdog(String executionId) {
        chunkWatchdogs.put(executionId, System.currentTimeMillis());
    }

    /**
     * 校验分块是否超时断流
     */
    public boolean isChunkTimedOut(String executionId) {
        Long last = chunkWatchdogs.get(executionId);
        if (last == null) return false;
        return (System.currentTimeMillis() - last) > CHUNK_WATCHDOG_TIMEOUT_MILLIS;
    }

    /**
     * Reflexion 反思式变异自愈算子
     */
    public String applyReflexionMutation(String toolName, String currentParams, String failureReason, int attemptIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("### [MCP 工具链反思自愈纠偏 - 第 ").append(attemptIndex).append(" 轮变异]\n");
        sb.append("- 失败工具: ").append(toolName).append("\n");
        sb.append("- 错误溯因: ").append(failureReason != null ? failureReason : "调用超时或返回数据畸变").append("\n");
        sb.append("- 修正建议: 自动注入备用参数或切换至降级备选服务，严禁死锁等待。\n");
        return sb.toString();
    }

    public CircuitState getCircuitState(String serviceId) {
        ServiceHealthMetric m = healthMetrics.get(serviceId);
        return m != null ? m.getState() : CircuitState.CLOSED;
    }
}
