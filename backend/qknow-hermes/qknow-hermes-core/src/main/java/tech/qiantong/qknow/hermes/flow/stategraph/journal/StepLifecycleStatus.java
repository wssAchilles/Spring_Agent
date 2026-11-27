package tech.qiantong.qknow.hermes.flow.stategraph.journal;

/**
 * 嵌入式预写步骤日志生命周期状态枚举。
 */
public enum StepLifecycleStatus {
    /** 步骤已登记预写但尚未提交 */
    PENDING,
    /** 步骤已完成确定性提交（重放时直接旁路返回） */
    COMMITTED,
    /** 步骤在人机协同屏障处挂起等待审批 */
    INTERRUPTED_HITL,
    /** 分支中止或超时后软隔离归档至三级冷备环 */
    QUARANTINED_COLD
}
