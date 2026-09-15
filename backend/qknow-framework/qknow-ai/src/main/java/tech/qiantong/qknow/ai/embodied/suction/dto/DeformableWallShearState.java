package tech.qiantong.qknow.ai.embodied.suction.dto;

/**
 * 大形变软体介质与壁面剪切状态帧 (Java 21 Record)
 * 封装工件超弹性应变能、主伸长比、冯米塞斯应力与非牛顿壁面剪切应力。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record DeformableWallShearState(
        String objectId,
        double maxPrincipalStretchRatio,
        double maxVonMisesStressKPa,
        double allowableStressKPa,
        double shearRateS1,
        double wallShearStressKPa,
        double strainEnergyJ,
        double safetyStrainMarginRatio
) {
    public DeformableWallShearState {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("Object ID must not be blank");
        }
        if (maxPrincipalStretchRatio < 1.0) {
            throw new IllegalArgumentException("Principal stretch ratio must be >= 1.0, got: " + maxPrincipalStretchRatio);
        }
        if (allowableStressKPa <= 0.0) {
            throw new IllegalArgumentException("Allowable stress must be positive");
        }
    }

    /**
     * 判定是否触发材料破损或撕裂风险 (等效应力达到极限的 85%)
     */
    public boolean isTearingRisk() {
        return maxVonMisesStressKPa >= allowableStressKPa * 0.85;
    }

    /**
     * 计算应力安全系数
     */
    public double computeSafetyFactor() {
        return allowableStressKPa / Math.max(maxVonMisesStressKPa, 1e-4);
    }
}
