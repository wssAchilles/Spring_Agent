package tech.qiantong.qknow.ai.embodied.deformable.dto;

/**
 * 可形变物体内部弹性势能分布指标
 * <p>
 * 封装拉伸势能、剪切势能、弯曲势能、四面体保体积反穿透惩罚势能与总能量泛函。
 *
 * @param stretchEnergy 拉伸势能 (J)
 * @param shearEnergy   剪切势能 (J)
 * @param bendingEnergy 弯曲势能 (J)
 * @param volumePenalty 四面体单元保体积反穿透惩罚势能 (J)
 * @param totalEnergy   系统总弹性势能 (J)
 */
public record DeformationEnergyMetric(
        double stretchEnergy,
        double shearEnergy,
        double bendingEnergy,
        double volumePenalty,
        double totalEnergy
) {
    public DeformationEnergyMetric {
        if (stretchEnergy < 0 || shearEnergy < 0 || bendingEnergy < 0 || volumePenalty < 0) {
            throw new IllegalArgumentException("弹性势能分量必须非负");
        }
    }

    /**
     * 工厂方法：基于各能量分量自动汇总总能量
     */
    public static DeformationEnergyMetric of(double stretch, double shear, double bend, double volPenalty) {
        double total = stretch + shear + bend + volPenalty;
        return new DeformationEnergyMetric(stretch, shear, bend, volPenalty, total);
    }
}
