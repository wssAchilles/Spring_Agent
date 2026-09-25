package tech.qiantong.qknow.hermes.tool.mcp.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 136 核心资产：基于滑动窗口自适应慢调用三态断路器与降级中枢 (AdaptiveMcpCircuitBreakerGovernor)
 * 落实定理 1.2 基于滑动窗口自适应慢调用熔断的李雅普诺夫稳定性：
 * 1. 环形位滑动窗口结构 (SlidingWindow)，样本量默认为 20
 * 2. 失败率门限 (>= 25%) 与 P99 慢调用门限 (>= 2000ms, >= 30%) 双轨自适应软熔断
 * 3. 严格三态马尔可夫自动机：CLOSED -> OPEN -> HALF_OPEN -> CLOSED 自愈收敛
 * 4. 熔断态自动无缝降级 (Graceful Fallback)，向 Agent 提供标准化兜底，消除级联雪崩
 *
 * @author Achilles
 * @since 2026-09-25
 */
@Component
public class AdaptiveMcpCircuitBreakerGovernor {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveMcpCircuitBreakerGovernor.class);

    public enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public enum CallOutcome {
        SUCCESS_FAST,
        SUCCESS_SLOW,
        FAILURE
    }

    @FunctionalInterface
    public interface FallbackProvider {
        String provideFallback(String toolCode);
    }

    public record CircuitConfig(
            int slidingWindowSize,
            int minimumNumberOfCalls,
            double failureRateThreshold,      // 0.25 (25%)
            double slowCallRateThreshold,     // 0.30 (30%)
            long slowCallDurationThresholdMs, // 2000 ms
            long waitDurationInOpenStateMs,   // 5000 ms
            int permittedHalfOpenCalls        // 2
    ) {
        public static CircuitConfig defaultConfig() {
            return new CircuitConfig(20, 10, 0.25, 0.30, 2000L, 5000L, 2);
        }
    }

    public record CircuitDecision(
            CircuitState state,
            boolean allowExecution,
            String reason,
            String fallbackResponse
    ) {}

    // 工具独立熔断器状态容器
    private final Map<String, ToolCircuitStateHolder> circuitMap = new ConcurrentHashMap<>();
    // 降级兜底提供器表
    private final Map<String, FallbackProvider> fallbackProviders = new ConcurrentHashMap<>();

    private static class ToolCircuitStateHolder {
        final String toolCode;
        final CircuitConfig config;
        volatile CircuitState state = CircuitState.CLOSED;
        volatile long lastStateChangeTimestamp = System.currentTimeMillis();

        // 环形滑动窗口
        final CallOutcome[] ringBuffer;
        int writeIndex = 0;
        int totalRecordedCalls = 0;
        final AtomicInteger halfOpenTrialCalls = new AtomicInteger(0);

        ToolCircuitStateHolder(String toolCode, CircuitConfig config) {
            this.toolCode = toolCode;
            this.config = config;
            this.ringBuffer = new CallOutcome[config.slidingWindowSize()];
        }

        synchronized void record(long durationMs, boolean success) {
            CallOutcome outcome;
            if (!success) {
                outcome = CallOutcome.FAILURE;
            } else if (durationMs >= config.slowCallDurationThresholdMs()) {
                outcome = CallOutcome.SUCCESS_SLOW;
            } else {
                outcome = CallOutcome.SUCCESS_FAST;
            }

            ringBuffer[writeIndex] = outcome;
            writeIndex = (writeIndex + 1) % config.slidingWindowSize();
            if (totalRecordedCalls < config.slidingWindowSize()) {
                totalRecordedCalls++;
            }

            // 状态机演化判定
            if (state == CircuitState.HALF_OPEN) {
                if (!success) {
                    transitionTo(CircuitState.OPEN);
                } else if (halfOpenTrialCalls.get() >= config.permittedHalfOpenCalls()) {
                    transitionTo(CircuitState.CLOSED);
                }
            } else if (state == CircuitState.CLOSED && totalRecordedCalls >= config.minimumNumberOfCalls()) {
                double failureRate = calculateFailureRate();
                double slowCallRate = calculateSlowCallRate();
                if (failureRate >= config.failureRateThreshold() || slowCallRate >= config.slowCallRateThreshold()) {
                    log.warn("[CircuitBreaker] 工具 [{}] 触发自适应熔断跳闸! 失败率: {:.1f}%, 慢调用率: {:.1f}%",
                            toolCode, failureRate * 100, slowCallRate * 100);
                    transitionTo(CircuitState.OPEN);
                }
            }
        }

        synchronized void transitionTo(CircuitState newState) {
            this.state = newState;
            this.lastStateChangeTimestamp = System.currentTimeMillis();
            if (newState == CircuitState.HALF_OPEN) {
                halfOpenTrialCalls.set(0);
            } else if (newState == CircuitState.CLOSED) {
                // 重置滑动窗口
                totalRecordedCalls = 0;
                writeIndex = 0;
            }
            log.info("[CircuitBreaker] 工具 [{}] 断路器状态演化至 -> {}", toolCode, newState);
        }

        synchronized double calculateFailureRate() {
            if (totalRecordedCalls == 0) return 0.0;
            int failureCount = 0;
            for (int i = 0; i < totalRecordedCalls; i++) {
                if (ringBuffer[i] == CallOutcome.FAILURE) {
                    failureCount++;
                }
            }
            return (double) failureCount / totalRecordedCalls;
        }

        synchronized double calculateSlowCallRate() {
            if (totalRecordedCalls == 0) return 0.0;
            int slowCount = 0;
            for (int i = 0; i < totalRecordedCalls; i++) {
                if (ringBuffer[i] == CallOutcome.SUCCESS_SLOW) {
                    slowCount++;
                }
            }
            return (double) slowCount / totalRecordedCalls;
        }
    }

    /**
     * 注册工具降级提供器
     */
    public void registerFallbackProvider(String toolCode, FallbackProvider provider) {
        fallbackProviders.put(toolCode, provider);
    }

    /**
     * 显式注册或初始化工具熔断配置
     */
    public void registerCircuitConfig(String toolCode, CircuitConfig config) {
        circuitMap.put(toolCode, new ToolCircuitStateHolder(toolCode, config));
    }

    /**
     * 评估工具当前是否可执行
     */
    public CircuitDecision evaluateCircuit(String toolCode) {
        return evaluateCircuit(toolCode, CircuitConfig.defaultConfig());
    }

    /**
     * 评估工具当前是否可执行 (指定配置)
     */
    public CircuitDecision evaluateCircuit(String toolCode, CircuitConfig config) {
        ToolCircuitStateHolder holder = circuitMap.computeIfAbsent(toolCode, k -> new ToolCircuitStateHolder(toolCode, config));

        // 检查 OPEN 态冷却超时与自动 HALF_OPEN 自愈试探
        if (holder.state == CircuitState.OPEN) {
            long elapsed = System.currentTimeMillis() - holder.lastStateChangeTimestamp;
            if (elapsed >= holder.config.waitDurationInOpenStateMs()) {
                holder.transitionTo(CircuitState.HALF_OPEN);
            }
        }

        if (holder.state == CircuitState.OPEN) {
            String fallback = getFallbackPayload(toolCode);
            return new CircuitDecision(CircuitState.OPEN, false, "CIRCUIT_BREAKER_OPEN", fallback);
        }

        if (holder.state == CircuitState.HALF_OPEN) {
            if (holder.halfOpenTrialCalls.incrementAndGet() <= holder.config.permittedHalfOpenCalls()) {
                return new CircuitDecision(CircuitState.HALF_OPEN, true, "HALF_OPEN_TRIAL", null);
            } else {
                String fallback = getFallbackPayload(toolCode);
                return new CircuitDecision(CircuitState.HALF_OPEN, false, "HALF_OPEN_TRIAL_LIMIT_REACHED", fallback);
            }
        }

        return new CircuitDecision(CircuitState.CLOSED, true, "CIRCUIT_CLOSED_HEALTHY", null);
    }

    /**
     * 记录单次工具调用结果
     */
    public void recordExecution(String toolCode, long durationMs, boolean success) {
        ToolCircuitStateHolder holder = circuitMap.computeIfAbsent(toolCode, k -> new ToolCircuitStateHolder(toolCode, CircuitConfig.defaultConfig()));
        holder.record(durationMs, success);
    }

    public CircuitState getCircuitState(String toolCode) {
        ToolCircuitStateHolder holder = circuitMap.get(toolCode);
        if (holder == null) {
            return CircuitState.CLOSED;
        }
        if (holder.state == CircuitState.OPEN) {
            long elapsed = System.currentTimeMillis() - holder.lastStateChangeTimestamp;
            if (elapsed >= holder.config.waitDurationInOpenStateMs()) {
                holder.transitionTo(CircuitState.HALF_OPEN);
            }
        }
        return holder.state;
    }

    public double getFailureRate(String toolCode) {
        ToolCircuitStateHolder holder = circuitMap.get(toolCode);
        return holder != null ? holder.calculateFailureRate() : 0.0;
    }

    public double getSlowCallRate(String toolCode) {
        ToolCircuitStateHolder holder = circuitMap.get(toolCode);
        return holder != null ? holder.calculateSlowCallRate() : 0.0;
    }

    private String getFallbackPayload(String toolCode) {
        FallbackProvider provider = fallbackProviders.get(toolCode);
        if (provider != null) {
            return provider.provideFallback(toolCode);
        }
        return "{\"status\":\"DEGRADED\",\"code\":503,\"message\":\"MCP tool degraded via circuit breaker fallback\"}";
    }
}
