package tech.qiantong.qknow.hermes.transaction.dto;

/**
 * Saga 事务状态枚举
 */
public enum SagaTransactionStatus {
    /** 全部正向子任务提交成功 */
    COMMITTED,
    /** 正在逆拓扑补偿中 */
    COMPENSATING,
    /** 异常后所有已执行子任务均成功逆向补偿 (强最终一致) */
    COMPENSATED,
    /** 部分节点补偿失败，需挂起或人工介入 */
    PARTIALLY_COMPENSATED,
    /** 补偿彻底失败或检测到非法环路 */
    FAILED
}
