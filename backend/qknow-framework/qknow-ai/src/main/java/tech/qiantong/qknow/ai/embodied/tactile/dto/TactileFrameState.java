package tech.qiantong.qknow.ai.embodied.tactile.dto;

import java.util.Objects;

/**
 * 触觉与操作 1000Hz 实时高频控制帧 (Java 21 Record)
 * <p>
 * 封装周期序列号、法向力、切向力、微滑脱比 eta_slip、测地偏角、冲量平衡残差、
 * 当前推移位姿误差 (mm)、HOCBF 安全裕度、总线状态与时间戳。
 */
public record TactileFrameState(
        long sequenceId,
        double normalForceN,
        double tangentialForceN,
        double slipRatio,
        double geodesicDeviationRad,
        double momentumResidual,
        double poseTrackingErrorMm,
        double hocbfSafetyMargin,
        String busStatus,
        long timestampNs
) {
    public TactileFrameState {
        Objects.requireNonNull(busStatus, "busStatus 不能为 null");
    }

    public TactileFrameState withDegradedStatus(String degradedStatus) {
        return new TactileFrameState(
                this.sequenceId,
                this.normalForceN,
                0.0, // 柔顺悬停释放切向推力
                this.slipRatio,
                this.geodesicDeviationRad,
                this.momentumResidual,
                this.poseTrackingErrorMm,
                this.hocbfSafetyMargin,
                degradedStatus,
                System.nanoTime()
        );
    }
}
