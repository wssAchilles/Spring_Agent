package tech.qiantong.qknow.ai.embodied.micronano.dto;

/**
 * 具身微装配微力与对齐状态评估模型 (Java 21 Record)
 * 封装装配对接位姿残差、微牛接触力偏差、表面黏附断裂裕度与相对阶 r=2 Micro-HOCBF 安全裕度。
 *
 * @param alignmentResidualMicron 装配对准几何空间综合残差 (um)
 * @param microContactForceDeviation 微牛级接触力跟踪偏差 (uN)
 * @param adhesionFractureMargin 表面黏附断裂能量释放率裕度 (标量，正值表示脱附顺畅)
 * @param hocbfSafetyMargin 相对阶 r=2 Micro-HOCBF 微压溃安全裕度 (uN)
 * @param inAdhesionLock 是否陷入表面黏附死锁
 * @param isCrushRisk 是否存在接触力突破极限压溃微器件风险
 * @param currentContactForceMicroN 当前瞬态法向微接触力 (uN)
 * @param yieldLimitMicroN 微工件抗压溃破坏断裂极限微力 (uN)
 * @author Achilles
 * @since 2026-09-16
 */
public record MicroAssemblyWrenchState(
        double alignmentResidualMicron,
        double microContactForceDeviation,
        double adhesionFractureMargin,
        double hocbfSafetyMargin,
        boolean inAdhesionLock,
        boolean isCrushRisk,
        double currentContactForceMicroN,
        double yieldLimitMicroN
) {
    public MicroAssemblyWrenchState {
        if (yieldLimitMicroN <= 0.0) {
            throw new IllegalArgumentException("yieldLimitMicroN must be positive, got: " + yieldLimitMicroN);
        }
    }
}
