package tech.qiantong.qknow.ai.gateway.model;

/**
 * Phase 28: 大模型提供商通道运行状态枚举
 */
public enum ChannelStatus {
    /**
     * 断路器正常闭合
     */
    CLOSED,

    /**
     * 健康正常，可承接全量流量
     */
    HEALTHY,

    /**
     * 性能降级，仅承接低优先级或探活流量
     */
    DEGRADED,

    /**
     * 熔断跳闸（断路器开启），完全切断流量并快速失败
     */
    OPEN,

    /**
     * 半开状态（断路器探活），仅放行有限探针流量验证自愈
     */
    HALF_OPEN,

    /**
     * 人工标记下线或配置禁用
     */
    OFFLINE
}
