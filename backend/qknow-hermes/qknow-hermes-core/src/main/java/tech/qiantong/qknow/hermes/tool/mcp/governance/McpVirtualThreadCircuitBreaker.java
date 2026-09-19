package tech.qiantong.qknow.hermes.tool.mcp.governance;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java 21 虚拟线程三态断路器与熔断自愈控制器 (定理 1.2)
 * 采用 CLOSED / OPEN / HALF_OPEN 三态马尔可夫状态机，结合 Java 21 虚拟线程 Unmount 特性，
 * 实现外部 MCP 工具故障时的毫秒级快速失败（Fail-Open 软着陆）与主容器工作线程池 0 阻塞
 */
@Component
public class McpVirtualThreadCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(McpVirtualThreadCircuitBreaker.class);

    /**
     * 断路器三态枚举
     */
    public enum State {
        CLOSED,    // 正常闭合态：所有请求放行
        OPEN,      // 熔断开启态：请求快速失败，触发软着陆
        HALF_OPEN  // 半开探测态：仅放行 1 个试探请求
    }

    /**
     * 默认失败阈值 (连续失败 5 次触发熔断)
     */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /**
     * 默认熔断冷却重置时间 (10 秒)
     */
    public static final long DEFAULT_RESET_TIMEOUT_MILLIS = 10_000L;

    /**
     * 默认工具执行硬超时时间 (5 秒)
     */
    public static final long DEFAULT_TOOL_TIMEOUT_MILLIS = 5_000L;

    // 工具断路器状态容器: toolCode -> ToolCircuit
    private final Map<String, ToolCircuit> circuitMap = new ConcurrentHashMap<>();

    // 共享轻量级 Java 21 虚拟线程执行器 (通过反射初始化，免疫低版本 IDE 编译污染)
    private final ExecutorService virtualThreadExecutor = createVirtualThreadExecutor();

    private static ExecutorService createVirtualThreadExecutor() {
        try {
            java.lang.reflect.Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            return (ExecutorService) method.invoke(null);
        } catch (Exception e) {
            log.warn("[CircuitBreaker] 无法初始化虚拟线程执行器，回退至平台线程池: {}", e.getMessage());
            return Executors.newCachedThreadPool();
        }
    }

    /**
     * 内部单工具断路器状态上下文
     */
    public static class ToolCircuit {
        private final String toolCode;
        private final int failureThreshold;
        private final long resetTimeoutMillis;

        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicLong lastStateTransitionTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);

        public ToolCircuit(String toolCode, int failureThreshold, long resetTimeoutMillis) {
            this.toolCode = toolCode;
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMillis = resetTimeoutMillis;
        }

        public State getState() {
            return state.get();
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures.get();
        }

        public void reset() {
            state.set(State.CLOSED);
            consecutiveFailures.set(0);
            halfOpenProbeInFlight.set(false);
            lastStateTransitionTime.set(System.currentTimeMillis());
        }

        public void forceOpen() {
            state.set(State.OPEN);
            lastStateTransitionTime.set(System.currentTimeMillis());
            halfOpenProbeInFlight.set(false);
        }
    }

    /**
     * 获取或创建指定工具的断路器
     */
    public ToolCircuit getOrCreateCircuit(String toolCode) {
        return circuitMap.computeIfAbsent(toolCode, k ->
                new ToolCircuit(k, DEFAULT_FAILURE_THRESHOLD, DEFAULT_RESET_TIMEOUT_MILLIS));
    }

    /**
     * 获取指定工具当前断路器状态
     */
    public State getState(String toolCode) {
        ToolCircuit circuit = circuitMap.get(toolCode);
        return circuit != null ? circuit.getState() : State.CLOSED;
    }

    /**
     * 获取所有受控工具的断路器状态快照
     */
    public Map<String, String> getAllStates() {
        Map<String, String> states = new ConcurrentHashMap<>();
        for (Map.Entry<String, ToolCircuit> entry : circuitMap.entrySet()) {
            states.put(entry.getKey(), entry.getValue().getState().name());
        }
        return states;
    }

    /**
     * 重置指定工具断路器为 CLOSED
     */
    public void reset(String toolCode) {
        ToolCircuit circuit = circuitMap.get(toolCode);
        if (circuit != null) {
            circuit.reset();
            log.info("[CircuitBreaker] 工具 {} 断路器已重置为 CLOSED", toolCode);
        }
    }

    /**
     * 强制开启指定工具断路器 (OPEN)
     */
    public void forceOpen(String toolCode) {
        getOrCreateCircuit(toolCode).forceOpen();
        log.warn("[CircuitBreaker] 工具 {} 断路器已被强制切换为 OPEN", toolCode);
    }

    /**
     * 在 Java 21 虚拟线程中隔离执行工具调用，内嵌三态状态机与硬超时控制
     *
     * @param toolCode       工具唯一标识
     * @param callable       工具实际执行函数
     * @param timeoutMillis  执行硬超时时间 (ms)
     * @return 工具执行结果 JSON 字符串或软着陆降级信封
     */
    public String executeWithIsolation(String toolCode, Callable<String> callable, long timeoutMillis) {
        ToolCircuit circuit = getOrCreateCircuit(toolCode);
        long effectiveTimeout = timeoutMillis > 0 ? timeoutMillis : DEFAULT_TOOL_TIMEOUT_MILLIS;

        // 1. 检查断路器当前状态
        State currentState = circuit.state.get();
        if (currentState == State.OPEN) {
            long elapsed = System.currentTimeMillis() - circuit.lastStateTransitionTime.get();
            if (elapsed >= circuit.resetTimeoutMillis) {
                // 冷却时间已过，跃迁至 HALF_OPEN
                if (circuit.state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    circuit.lastStateTransitionTime.set(System.currentTimeMillis());
                    circuit.halfOpenProbeInFlight.set(false);
                    log.info("[CircuitBreaker] 工具 {} 冷却期已过 ({}ms)，状态跃迁至 HALF_OPEN", toolCode, elapsed);
                }
            } else {
                // 处于 OPEN 态且在冷却期内，快速失败 (Fail-Open 软着陆)
                return buildFallbackResponse(toolCode, "CIRCUIT_BREAKER_OPEN",
                        "下游服务故障，工具当前处于熔断保护状态，快速失败以避免阻塞工作流。");
            }
        }

        // 2. HALF_OPEN 状态下的并发控制 (仅放行单次试探)
        if (circuit.state.get() == State.HALF_OPEN) {
            if (!circuit.halfOpenProbeInFlight.compareAndSet(false, true)) {
                // 已有探测请求在运行，其他请求快速失败
                return buildFallbackResponse(toolCode, "CIRCUIT_BREAKER_HALF_OPEN_BUSY",
                        "工具当前处于半开探测状态，正有试探请求在执行，其余请求快速跳过。");
            }
        }

        // 3. 使用 Java 21 虚拟线程执行器隔离执行调用
        Future<String> future = virtualThreadExecutor.submit(callable);
        try {
            String result = future.get(effectiveTimeout, TimeUnit.MILLISECONDS);

            // 调用成功处理
            handleSuccess(circuit);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[CircuitBreaker] 工具 {} 调用超时 (超过 {}ms)", toolCode, effectiveTimeout);
            handleFailure(circuit, "TIMEOUT");
            return buildFallbackResponse(toolCode, "TIMEOUT",
                    "工具调用超过硬超时阈值 (" + effectiveTimeout + "ms)，已强制中断执行。");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("[CircuitBreaker] 工具 {} 执行被中断", toolCode);
            handleFailure(circuit, "INTERRUPTED");
            return buildFallbackResponse(toolCode, "INTERRUPTED", "工具调用执行被中断。");
        } catch (ExecutionException e) {
            future.cancel(true);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("[CircuitBreaker] 工具 {} 执行抛出异常: {}", toolCode, cause.getMessage());
            handleFailure(circuit, "EXECUTION_ERROR: " + cause.getMessage());
            return buildFallbackResponse(toolCode, "EXECUTION_ERROR",
                    "工具执行异常: " + cause.getMessage());
        } catch (Exception e) {
            future.cancel(true);
            log.error("[CircuitBreaker] 工具 {} 执行发生非预期错误: {}", toolCode, e.getMessage());
            handleFailure(circuit, "UNKNOWN_ERROR: " + e.getMessage());
            return buildFallbackResponse(toolCode, "UNKNOWN_ERROR", "未知执行错误: " + e.getMessage());
        }
    }

    /**
     * 处理调用成功
     */
    private void handleSuccess(ToolCircuit circuit) {
        State current = circuit.state.get();
        if (current == State.HALF_OPEN) {
            // 半开试探成功，自愈恢复为 CLOSED
            circuit.state.set(State.CLOSED);
            circuit.consecutiveFailures.set(0);
            circuit.halfOpenProbeInFlight.set(false);
            circuit.lastStateTransitionTime.set(System.currentTimeMillis());
            log.info("[CircuitBreaker] 工具 {} 半开探测成功，断路器自愈恢复为 CLOSED", circuit.toolCode);
        } else if (current == State.CLOSED) {
            // 闭合态成功，连续失败计数归零
            circuit.consecutiveFailures.set(0);
        }
    }

    /**
     * 处理调用失败
     */
    private void handleFailure(ToolCircuit circuit, String reason) {
        State current = circuit.state.get();
        circuit.halfOpenProbeInFlight.set(false);

        if (current == State.HALF_OPEN) {
            // 半开试探失败，立即重新熔断切回 OPEN
            circuit.state.set(State.OPEN);
            circuit.lastStateTransitionTime.set(System.currentTimeMillis());
            log.warn("[CircuitBreaker] 工具 {} 半开探测失败 ({})，重新熔断切换为 OPEN", circuit.toolCode, reason);
        } else if (current == State.CLOSED) {
            int failures = circuit.consecutiveFailures.incrementAndGet();
            if (failures >= circuit.failureThreshold) {
                // 达到失败阈值，原子切换至 OPEN
                if (circuit.state.compareAndSet(State.CLOSED, State.OPEN)) {
                    circuit.lastStateTransitionTime.set(System.currentTimeMillis());
                    log.error("[CircuitBreaker] 工具 {} 连续失败达到阈值 ({})，触发熔断切换至 OPEN",
                            circuit.toolCode, failures);
                }
            }
        }
    }

    /**
     * 构建标准 Fail-Open 软着陆信封响应
     */
    private String buildFallbackResponse(String toolCode, String status, String message) {
        JSONObject response = new JSONObject();
        response.put("status", status);
        response.put("tool", toolCode);
        response.put("fallback", true);
        response.put("message", message);
        response.put("error", message); // 兼容既有错误处理与调用契约
        response.put("timestamp", System.currentTimeMillis());
        return response.toJSONString();
    }
}
