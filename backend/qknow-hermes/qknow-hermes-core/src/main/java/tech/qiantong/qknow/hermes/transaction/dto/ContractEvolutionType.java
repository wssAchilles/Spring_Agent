package tech.qiantong.qknow.hermes.transaction.dto;

/**
 * 契约演化类型枚举
 */
public enum ContractEvolutionType {
    /** 完全一致 */
    IDENTICAL,
    /** 向后兼容 (新服务端输出完全包含旧版本客户端所需字段) */
    BACKWARD_COMPATIBLE,
    /** 向前兼容 (新服务端可接受旧客户端请求，新增必填字段已补全默认值) */
    FORWARD_COMPATIBLE,
    /** 双向完全兼容 */
    FULLY_COMPATIBLE,
    /** 不兼容漂移 (存在无默认值的必填新增字段，或超球面语义测地距离 > 0.35 rad) */
    INCOMPATIBLE_DRIFT
}
