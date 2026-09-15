package tech.qiantong.qknow.ai.embodied.wbc.dto;

/**
 * 移动操作全身控制任务分层优先级枚举
 * 严格遵从四级级联零空间投影顺序：BALANCE_ZMP > CONTACT_FORCE > EE_TRAJECTORY > POSTURE_MIN
 *
 * @author Achilles
 * @since 2026-09-15
 */
public enum LocomanipulationTaskPriority {

    /**
     * 优先级 1 (最高)：质心动量与动态 ZMP 防倾翻平衡 (Level 1)
     */
    BALANCE_ZMP(1, "质心动量与动态ZMP防倾翻平衡"),

    /**
     * 优先级 2：接触力与摩擦锥约束 (Level 2)
     */
    CONTACT_FORCE(2, "接触力跟踪与摩擦锥约束"),

    /**
     * 优先级 3：末端执行器运动轨迹跟踪 (Level 3)
     */
    EE_TRAJECTORY(3, "末端执行器运动轨迹跟踪"),

    /**
     * 优先级 4 (最低)：关节自耗位姿与能耗最小化 (Level 4)
     */
    POSTURE_MIN(4, "关节自耗位姿与能耗最小化");

    private final int level;
    private final String description;

    LocomanipulationTaskPriority(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }
}
