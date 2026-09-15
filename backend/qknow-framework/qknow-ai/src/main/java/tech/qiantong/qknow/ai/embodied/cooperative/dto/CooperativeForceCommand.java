package tech.qiantong.qknow.ai.embodied.cooperative.dto;

/**
 * 协同力控派发指令 DTO
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record CooperativeForceCommand(
        String commandId,
        double[] targetWrench,
        String mode,
        long timestamp
) {
}
