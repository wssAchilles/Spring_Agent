package tech.qiantong.qknow.ai.embodied.dexterous.dto;

import java.util.Arrays;

/**
 * 动态手内重抓取时序规划方案模型 (Java 21 Record)
 * 抽象五阶段相变状态机、选定换指、预紧力前馈重分配与期望位姿。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record RegraspingSequencePlan(
        String planId,
        RegraspPhase currentPhase,
        int totalFingerCount,
        int gaitingFingerIndex,
        double[] targetContactPoint,
        double[] allocatedNormalForcesN,
        double expectedImpulseNs,
        double expectedPoseToleranceMm
) {
    public enum RegraspPhase {
        STABLE_HOLD,        // 全指粘着平衡夹持
        CONTROLLED_SLIDE,   // 指尖受控微滑移调整
        FINGER_LIFT,        // 换指手指卸载抬升
        REPOSITION,         // 自由空间高速重寻位
        SECURE              // 触碰接触恢复与力封闭锁定
    }

    public RegraspingSequencePlan {
        if (totalFingerCount < 3) {
            throw new IllegalArgumentException("Total finger count must be at least 3");
        }
        if (allocatedNormalForcesN == null || allocatedNormalForcesN.length != totalFingerCount) {
            throw new IllegalArgumentException("Allocated normal forces length must match total finger count");
        }
    }

    /**
     * 判断指定手指是否处于支撑指集合中
     */
    public boolean isSupportingFinger(int fingerIdx) {
        if (fingerIdx < 0 || fingerIdx >= totalFingerCount) {
            return false;
        }
        if (currentPhase == RegraspPhase.FINGER_LIFT || currentPhase == RegraspPhase.REPOSITION) {
            return fingerIdx != gaitingFingerIndex;
        }
        return true;
    }
}
