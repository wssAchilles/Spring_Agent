package tech.qiantong.qknow.ai.embodied.jumping.dto;

import java.util.Objects;

/**
 * 空中姿态误差旋量与触地刚柔冲击力控能量状态。
 *
 * @param attitudeErrorRpy 空中机身相对目标着陆地表的外倾欧拉角误差 [delta_roll, delta_pitch, delta_yaw] (rad)
 * @param reactionFlywheelTorques 4 轮端高速自转产生的反作用调姿力矩向量 (Nm)
 * @param limbReactionTorques 腿部摆动扑动反作用调姿力矩 (Nm)
 * @param touchdownKineticEnergyJoules 触地瞬间整机法向冲击机械动能 (J)
 * @param dissipatedEnergyRatio 辛数值阻尼动态吸收耗散率 (>= 85.0%)
 * @param peakTorqueReductionRatio 传动机构峰值力矩削减率 (>= 65.0%)
 * @param hocbfSafetyMargin 相对阶 r=2 防二次弹跳与力矩过载 HOCBF 安全裕度
 * @param timestampMicros 时间戳微秒
 */
public record AerialAttitudeWrenchState(
        double[] attitudeErrorRpy,
        double[] reactionFlywheelTorques,
        double[] limbReactionTorques,
        double touchdownKineticEnergyJoules,
        double dissipatedEnergyRatio,
        double peakTorqueReductionRatio,
        double hocbfSafetyMargin,
        long timestampMicros
) {
    public AerialAttitudeWrenchState {
        Objects.requireNonNull(attitudeErrorRpy, "attitudeErrorRpy 不能为空");
        Objects.requireNonNull(reactionFlywheelTorques, "reactionFlywheelTorques 不能为空");
        Objects.requireNonNull(limbReactionTorques, "limbReactionTorques 不能为空");
    }
}
