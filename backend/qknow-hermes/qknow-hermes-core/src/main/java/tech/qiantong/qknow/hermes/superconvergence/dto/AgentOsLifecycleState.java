package tech.qiantong.qknow.hermes.superconvergence.dto;

import java.util.EnumSet;
import java.util.Set;

/**
 * 操作系统内核 8 态受控生命周期枚举
 */
public enum AgentOsLifecycleState {
    /**
     * 内核引导初始化
     */
    INITIALIZING,

    /**
     * 超球面元认知投影与意图对齐
     */
    COGNITIVE_ALIGNING,

    /**
     * 有限状态代数推理与反射
     */
    AUTONOMIC_REASONING,

    /**
     * 百阶段主权 CBF 安全门禁审计
     */
    BARRIER_AUDITING,

    /**
     * 超融合总线确定性提交
     */
    CONVERGENCE_COMMITTING,

    /**
     * 李雅普诺夫自愈反思补偿
     */
    SELF_HEALING_COMPENSATING,

    /**
     * 抖动缓冲降级软着陆
     */
    DEGRADED_BUFFERED,

    /**
     * 受控安全关机终态
     */
    SHUTDOWN_HALTED;

    /**
     * 判断当前状态是否允许合法转移至目标状态
     *
     * @param next 目标状态
     * @return true 若转移合法，否则 false
     */
    public boolean canTransitionTo(AgentOsLifecycleState next) {
        if (next == null) {
            return false;
        }
        if (this == next) {
            return true; // 保持当前状态合法
        }
        if (this == SHUTDOWN_HALTED) {
            return false; // 终态不可再转移至其他状态
        }

        return switch (this) {
            case INITIALIZING -> next == COGNITIVE_ALIGNING || next == DEGRADED_BUFFERED || next == SHUTDOWN_HALTED;
            case COGNITIVE_ALIGNING -> next == AUTONOMIC_REASONING || next == SELF_HEALING_COMPENSATING || next == DEGRADED_BUFFERED || next == SHUTDOWN_HALTED;
            case AUTONOMIC_REASONING -> next == BARRIER_AUDITING || next == SELF_HEALING_COMPENSATING || next == DEGRADED_BUFFERED || next == SHUTDOWN_HALTED;
            case BARRIER_AUDITING -> next == CONVERGENCE_COMMITTING || next == SELF_HEALING_COMPENSATING || next == DEGRADED_BUFFERED || next == SHUTDOWN_HALTED;
            case CONVERGENCE_COMMITTING -> next == COGNITIVE_ALIGNING || next == AUTONOMIC_REASONING || next == SELF_HEALING_COMPENSATING || next == DEGRADED_BUFFERED || next == SHUTDOWN_HALTED;
            case SELF_HEALING_COMPENSATING -> next == COGNITIVE_ALIGNING || next == AUTONOMIC_REASONING || next == DEGRADED_BUFFERED || next == SHUTDOWN_HALTED;
            case DEGRADED_BUFFERED -> next == COGNITIVE_ALIGNING || next == AUTONOMIC_REASONING || next == SELF_HEALING_COMPENSATING || next == SHUTDOWN_HALTED;
            case SHUTDOWN_HALTED -> false;
        };
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == SHUTDOWN_HALTED;
    }
}
