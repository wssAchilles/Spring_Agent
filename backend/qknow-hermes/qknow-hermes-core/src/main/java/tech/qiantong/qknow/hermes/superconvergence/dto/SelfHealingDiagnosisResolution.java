package tech.qiantong.qknow.hermes.superconvergence.dto;

/**
 * 自愈诊断与残差能量裁决 Record
 */
public record SelfHealingDiagnosisResolution(
    String resolutionId,
    String faultSignature,
    double initialLyapunovEnergy,
    double residualLyapunovEnergy,
    double energyDecayRate,
    boolean isConverged,
    boolean isDeadlockFree,
    String compensationAction,
    long diagnosisLatencyUs,
    long timestamp
) {
    public SelfHealingDiagnosisResolution {
        if (resolutionId == null || resolutionId.isBlank()) {
            throw new IllegalArgumentException("resolutionId 不能为空");
        }
        if (faultSignature == null || faultSignature.isBlank()) {
            throw new IllegalArgumentException("faultSignature 不能为空");
        }
    }
}
