package tech.qiantong.qknow.mcp.core.gateway.dto;

/**
 * MCP 中继网关三态滑动窗口断路器状态枚举
 * 对标 Envoy / Resilience4j 生产级熔断状态机
 */
public enum McpCircuitBreakerState {
    /**
     * 闭合状态：调用正常通行，持续统计失败率与延迟
     */
    CLOSED,

    /**
     * 开启状态：熔断拦截，所有请求瞬时快速失败并返回降级存根
     */
    OPEN,

    /**
     * 半开状态：试探性放行有限流量，若连续成功则自愈恢复至 CLOSED
     */
    HALF_OPEN
}
