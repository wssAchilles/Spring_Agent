package tech.qiantong.qknow.ai.embodied.continuum.dto;

import java.util.Objects;

/**
 * 微流控阵列波纹膨胀腔多腔室瞬态状态快照。
 * <p>
 * 封装多腔气压、时变容积形变速率前馈、Bouc-Wen 迟滞状态、PWM 占空比输出与爆裂安全裕度。
 *
 * @param chamberBatchId 批次唯一标识
 * @param chamberCount 腔室总数量（例如 3 腔或 6 腔）
 * @param actualPressuresKPa 各腔实测气压 (kPa)
 * @param targetPressuresKPa 各腔期望气压 (kPa)
 * @param volumeChangeRates 时变体积形变速率 (m^3/s)
 * @param hysteresisStates Bouc-Wen 内部迟滞状态变量 h_i
 * @param pwmDutyCycles 微阀 PWM 占空比 [-1.0, 1.0]（正充负排）
 * @param hyperelasticStrainEnergyJ 超弹性应变能 (J)
 * @param burstSafetyMarginKPa 腔体防爆裂安全裕度 (kPa)
 */
public record MicrofluidicChamberState(
        String chamberBatchId,
        int chamberCount,
        double[] actualPressuresKPa,
        double[] targetPressuresKPa,
        double[] volumeChangeRates,
        double[] hysteresisStates,
        double[] pwmDutyCycles,
        double hyperelasticStrainEnergyJ,
        double burstSafetyMarginKPa
) {
    public MicrofluidicChamberState {
        Objects.requireNonNull(chamberBatchId, "chamberBatchId 不能为空");
        Objects.requireNonNull(actualPressuresKPa, "actualPressuresKPa 不能为空");
        Objects.requireNonNull(targetPressuresKPa, "targetPressuresKPa 不能为空");
        Objects.requireNonNull(volumeChangeRates, "volumeChangeRates 不能为空");
        Objects.requireNonNull(hysteresisStates, "hysteresisStates 不能为空");
        Objects.requireNonNull(pwmDutyCycles, "pwmDutyCycles 不能为空");

        if (chamberCount <= 0) {
            throw new IllegalArgumentException("腔室数量必须大于 0，当前为: " + chamberCount);
        }
    }
}
