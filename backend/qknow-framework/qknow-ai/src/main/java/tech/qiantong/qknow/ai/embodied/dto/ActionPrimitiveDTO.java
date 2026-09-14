package tech.qiantong.qknow.ai.embodied.dto;

import java.io.Serializable;

/**
 * 具身动作执行控制原语实体 (Theorem 3.1)
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class ActionPrimitiveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 动作原语类型
     */
    public enum ActionType {
        MOVE_TO,       // 移动至目标位姿
        GRASP,         // 夹爪抓取物体
        RELEASE,       // 释放物体
        ROTATE,        // 旋转角度
        APPLY_FORCE,   // 施加指定力矩
        EMERGENCY_STOP // 紧急制动
    }

    /**
     * 可逆性安全等级
     */
    public enum ReversibilityLevel {
        REVERSIBLE,               // 纯可逆动作（如空载移动、微调）
        CONDITIONALLY_REVERSIBLE, // 条件可逆动作（如抓取、轻微搬运）
        IRREVERSIBLE              // 物理不可逆动作（如高压排气、强力冲压、破坏性断电）
    }

    /**
     * 执行流转状态
     */
    public enum ExecutionStatus {
        PENDING,        // 待规划
        SIMULATED_PASS, // 数字孪生仿真通过
        SIMULATED_FAIL, // 仿真失败（碰撞或越界）
        EXECUTED,       // 物理执行成功并完成态势对账
        BLOCKED,        // 门禁拦截
        FAILED          // 执行后对账失败（如抓取滑脱）
    }

    private String actionId;
    private ActionType actionType;
    private String targetEntityId;
    private SpatialPose3D targetPose;
    private double expectedForceNewton; // 预期执行力矩 (N)
    private ReversibilityLevel reversibility;
    private String safetyTicket;        // 人机协同二次授权凭证
    private ExecutionStatus status;

    public ActionPrimitiveDTO() {
        this.reversibility = ReversibilityLevel.REVERSIBLE;
        this.status = ExecutionStatus.PENDING;
        this.expectedForceNewton = 10.0;
    }

    public ActionPrimitiveDTO(String actionId, ActionType actionType, String targetEntityId,
                              SpatialPose3D targetPose, ReversibilityLevel reversibility) {
        this.actionId = actionId;
        this.actionType = actionType;
        this.targetEntityId = targetEntityId;
        this.targetPose = targetPose;
        this.reversibility = reversibility;
        this.status = ExecutionStatus.PENDING;
        this.expectedForceNewton = 10.0;
    }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }
    public String getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(String targetEntityId) { this.targetEntityId = targetEntityId; }
    public SpatialPose3D getTargetPose() { return targetPose; }
    public void setTargetPose(SpatialPose3D targetPose) { this.targetPose = targetPose; }
    public double getExpectedForceNewton() { return expectedForceNewton; }
    public void setExpectedForceNewton(double expectedForceNewton) { this.expectedForceNewton = expectedForceNewton; }
    public ReversibilityLevel getReversibility() { return reversibility; }
    public void setReversibility(ReversibilityLevel reversibility) { this.reversibility = reversibility; }
    public String getSafetyTicket() { return safetyTicket; }
    public void setSafetyTicket(String safetyTicket) { this.safetyTicket = safetyTicket; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
}
