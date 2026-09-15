package tech.qiantong.qknow.ai.embodied.suction.dto;

/**
 * 多相吸附-流体协同序列规划 (Java 21 Record)
 * 封装剥离相位、自适应倾角、切向平移速率与能量释放降低率。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record SuctionFluidManipulationPlan(
        String planId,
        String currentPhase,
        double peelAngleDeg,
        double tangentialVelocityMms,
        double targetVacuumPressureKPa,
        long estimatedDurationMs,
        double tearingEnergyReductionRatio
) {
    public SuctionFluidManipulationPlan {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("Plan ID must not be blank");
        }
        if (currentPhase == null || currentPhase.isBlank()) {
            throw new IllegalArgumentException("Current phase must not be blank");
        }
        if (peelAngleDeg < 0.0 || peelAngleDeg > 90.0) {
            throw new IllegalArgumentException("Peel angle must be within [0, 90] degrees, got: " + peelAngleDeg);
        }
        if (tangentialVelocityMms < 0.0) {
            throw new IllegalArgumentException("Tangential velocity must be non-negative");
        }
    }
}
