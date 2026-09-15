package tech.qiantong.qknow.ai.embodied.meta.dto;

/**
 * 四类典型高维接触丰富操作技能元枚举
 *
 * @author Achilles
 * @since 2026-09-15
 */
public enum ContactSkillPrimitiveType {
    INSERTION("高精轴孔插拔，主导轴向自适应进给与径向顺应"),
    ALIGNMENT("面-面贴合与轴心找平，极小法向接触力闭环约束"),
    SCREWING("螺纹旋拧装配，轴向预紧力与螺旋导程比例耦合"),
    POLISHING("恒力曲面研磨，法向恒力保持与切向阻抗自适应顺应");

    private final String description;

    ContactSkillPrimitiveType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
