package tech.qiantong.qknow.ai.embodied.hybrid.dto;

import java.util.Objects;

/**
 * 接触力旋量与微观摩擦状态快照。
 * <p>
 * 封装 4 轮腿末端法向反力、切向摩擦力、微观滑移率、力封闭多面体裕度与翻滚力矩安全裕度。
 */
public record ContactWrenchState(
        String stateId,
        double[] normalForcesN,
        double[] tangentialForcesN,
        double[] frictionCoefficients,
        double[] microSlipRatios,
        double forceClosureMargin,
        double toppleMomentMarginNm,
        boolean isForceClosureMaintained,
        long timestampMicros
) {
    public ContactWrenchState {
        Objects.requireNonNull(stateId, "stateId 不能为空");
        Objects.requireNonNull(normalForcesN, "normalForcesN 不能为空");
        Objects.requireNonNull(tangentialForcesN, "tangentialForcesN 不能为空");
        Objects.requireNonNull(frictionCoefficients, "frictionCoefficients 不能为空");
        Objects.requireNonNull(microSlipRatios, "microSlipRatios 不能为空");

        if (normalForcesN.length != 4 || tangentialForcesN.length != 4 ||
                frictionCoefficients.length != 4 || microSlipRatios.length != 4) {
            throw new IllegalArgumentException("四足轮腿接触点数据维度必须为 4");
        }

        // 防御性拷贝
        normalForcesN = normalForcesN.clone();
        tangentialForcesN = tangentialForcesN.clone();
        frictionCoefficients = frictionCoefficients.clone();
        microSlipRatios = microSlipRatios.clone();
    }
}
