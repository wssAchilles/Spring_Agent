package tech.qiantong.qknow.ai.audit.causal;

/**
 * 全链路因果拓扑溯源图节点实体
 *
 * @param nodeId 节点唯一 UUID
 * @param traceId 任务交互全局 Trace ID
 * @param nodeType 节点在决策链路中的类型
 * @param timestamp 生成时间戳（毫秒）
 * @param inputHash 输入载荷 SHA-256 指纹
 * @param outputHash 输出载荷 SHA-256 指纹
 * @param attributionWeight 对最终输出结果的归一化因果归因贡献权重 [0.0, 1.0]
 * @param securityAuditStatus 绑定的安全审查状态 (PASSED, REDACTED, BLOCKED)
 * @param description 节点简要业务说明
 *
 * @author qknow
 */
public record CausalTraceNode(
        String nodeId,
        String traceId,
        CausalNodeType nodeType,
        long timestamp,
        String inputHash,
        String outputHash,
        double attributionWeight,
        String securityAuditStatus,
        String description
) {}
