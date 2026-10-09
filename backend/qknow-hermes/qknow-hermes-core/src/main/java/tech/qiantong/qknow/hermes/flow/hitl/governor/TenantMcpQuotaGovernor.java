package tech.qiantong.qknow.hermes.flow.hitl.governor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 企业 MCP 工具细粒度租户配额与自适应熔断网关 (Tenant MCP Quota Governor)
 * <p>
 * 落实高并发生产级安全治理：
 * 1. 租户级无锁原子并发租约控制 (In-Flight Concurrency Limiter)；
 * 2. 租户-工具级三态自愈熔断器 (CLOSED -> OPEN -> HALF_OPEN)；
 * 3. 故障率超标瞬时短路拦截，防范智能体失控并发刷穿外部依赖。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Component
public class TenantMcpQuotaGovernor {

    public static final int DEFAULT_MAX_CONCURRENCY = 16;
    public static final int CIRCUIT_FAILURE_THRESHOLD = 5;
    public static final long CIRCUIT_RESET_TIMEOUT_MS = 2000L;

    public static final String ERR_TENANT_QUOTA_EXHAUSTED = "ERR_TENANT_QUOTA_EXHAUSTED";
    public static final String ERR_MCP_CIRCUIT_OPEN = "ERR_MCP_CIRCUIT_OPEN";

    public enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    /**
     * 租户工具配额账本内部状态
     */
    public static class TenantToolCircuit {
        public final AtomicInteger inFlight = new AtomicInteger(0);
        public final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        public final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
        public final AtomicLong lastStateChangeTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * 租约申请结果 Record
     */
    public record QuotaLeaseResult(
            boolean acquired,
            String errorCode,
            String errorMessage,
            int currentInFlight,
            CircuitState circuitState
    ) {
        public static QuotaLeaseResult granted(int inFlight, CircuitState state) {
            return new QuotaLeaseResult(true, null, "配额租约获取成功", inFlight, state);
        }

        public static QuotaLeaseResult rejected(String code, String message, int inFlight, CircuitState state) {
            return new QuotaLeaseResult(false, code, message, inFlight, state);
        }
    }

    private final Map<String, TenantToolCircuit> circuitRegistry = new ConcurrentHashMap<>();

    private String getCircuitKey(String tenantId, String toolCode) {
        return (tenantId != null ? tenantId : "default_tenant") + ":" + (toolCode != null ? toolCode : "default_tool");
    }

    /**
     * 获取或申请工具调用租约
     */
    public QuotaLeaseResult acquireLease(String tenantId, String toolCode, int maxConcurrency) {
        String key = getCircuitKey(tenantId, toolCode);
        TenantToolCircuit circuit = circuitRegistry.computeIfAbsent(key, k -> new TenantToolCircuit());

        long now = System.currentTimeMillis();
        CircuitState currentState = circuit.state.get();

        // 1. 检查熔断器状态
        if (currentState == CircuitState.OPEN) {
            if (now - circuit.lastStateChangeTimestamp.get() > CIRCUIT_RESET_TIMEOUT_MS) {
                // 冷却时间已过，进入半开状态尝试放行探针
                if (circuit.state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                    circuit.lastStateChangeTimestamp.set(now);
                    log.info("[MCP Governor] 熔断器进入半开试探态: key={}", key);
                }
            } else {
                return QuotaLeaseResult.rejected(
                        ERR_MCP_CIRCUIT_OPEN,
                        String.format("目标工具 %s 连续故障超标，当前处于熔断保护状态 (OPEN)，快速短路拦截", toolCode),
                        circuit.inFlight.get(),
                        CircuitState.OPEN
                );
            }
        }

        // 2. 检查租户并发水位
        int limit = maxConcurrency > 0 ? maxConcurrency : DEFAULT_MAX_CONCURRENCY;
        int current = circuit.inFlight.get();
        while (current < limit) {
            if (circuit.inFlight.compareAndSet(current, current + 1)) {
                return QuotaLeaseResult.granted(current + 1, circuit.state.get());
            }
            current = circuit.inFlight.get();
        }

        // 超出并发上限
        return QuotaLeaseResult.rejected(
                ERR_TENANT_QUOTA_EXHAUSTED,
                String.format("租户 %s 对工具 %s 的在途并发数超限 (上限 %d)，触发流量削峰拦截", tenantId, toolCode, limit),
                circuit.inFlight.get(),
                circuit.state.get()
        );
    }

    /**
     * 释放租约并更新熔断状态机
     */
    public void releaseLease(String tenantId, String toolCode, boolean success) {
        String key = getCircuitKey(tenantId, toolCode);
        TenantToolCircuit circuit = circuitRegistry.get(key);
        if (circuit == null) return;

        circuit.inFlight.decrementAndGet();
        long now = System.currentTimeMillis();

        if (success) {
            circuit.consecutiveFailures.set(0);
            if (circuit.state.get() == CircuitState.HALF_OPEN) {
                circuit.state.set(CircuitState.CLOSED);
                circuit.lastStateChangeTimestamp.set(now);
                log.info("[MCP Governor] 探针成功，熔断器自愈恢复闭合 (CLOSED): key={}", key);
            }
        } else {
            int failures = circuit.consecutiveFailures.incrementAndGet();
            if (circuit.state.get() == CircuitState.HALF_OPEN || failures >= CIRCUIT_FAILURE_THRESHOLD) {
                circuit.state.set(CircuitState.OPEN);
                circuit.lastStateChangeTimestamp.set(now);
                log.warn("[MCP Governor] 触发熔断打开 (OPEN): key={}, consecutiveFailures={}", key, failures);
            }
        }
    }

    public TenantToolCircuit getCircuit(String tenantId, String toolCode) {
        return circuitRegistry.get(getCircuitKey(tenantId, toolCode));
    }
}
