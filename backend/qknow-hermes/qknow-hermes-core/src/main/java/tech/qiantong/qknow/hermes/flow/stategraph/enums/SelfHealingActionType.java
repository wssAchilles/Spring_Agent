package tech.qiantong.qknow.hermes.flow.stategraph.enums;

/**
 * 节点自愈动作类型枚举
 */
public enum SelfHealingActionType {
    /**
     * 携带抖动指数退避，在局部重试预算内就地重试
     */
    RETRY_WITH_JITTER,

    /**
     * 重试耗尽或致命异常，切换至 Fallback 旁路节点降级
     */
    ROUTE_TO_FALLBACK,

    /**
     * 不可恢复异常且无 Fallback 旁路，快速阻断失败
     */
    FAIL_FAST
}
