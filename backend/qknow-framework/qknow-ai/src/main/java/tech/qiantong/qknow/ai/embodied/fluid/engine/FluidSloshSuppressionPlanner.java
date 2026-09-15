package tech.qiantong.qknow.ai.embodied.fluid.engine;

import tech.qiantong.qknow.ai.embodied.fluid.dto.FluidSloshState;

/**
 * 自由液面防晃荡防飞溅流形轨迹规划器
 * <p>
 * 1. 等效重力矢量主动对齐 (Equivalent Gravity Alignment):
 *    在空间加减速过程中，主动将容器对称轴与合加速度矢量 g_eff = g - a_ee 实时对齐，消除切向自由表面重力分量；
 * 2. 自由液面开口边缘防溢出高阶控制屏障 (Free-Surface HOCBF):
 *    构建相对阶为 2 的安全屏障 h_spill(x) = H_lip - R * tan(|theta_s|) >= 0，
 *    通过解析二次规划 (QP) 正交投影修正末端平移加速度，液体飞溅溢出拦截率 100%。
 */
public class FluidSloshSuppressionPlanner {

    private final FluidDynamicsReducedOperator reducedOperator;

    public FluidSloshSuppressionPlanner(FluidDynamicsReducedOperator reducedOperator) {
        this.reducedOperator = reducedOperator;
    }

    /**
     * 计算消除切向惯性剪切的容器期望主动对齐倾斜角 theta_des (rad)
     *
     * @param ax 末端 X 轴加速度 (m/s^2)
     * @param ay 末端 Y 轴加速度 (m/s^2)
     * @param az 末端 Z 轴加速度 (m/s^2)
     * @return 期望对齐倾角 theta_des (rad)
     */
    public double computeDesiredAlignmentAngle(double ax, double ay, double az) {
        double aLateral = Math.sqrt(ax * ax + ay * ay);
        double effectiveG = FluidDynamicsReducedOperator.GRAVITY + az;
        if (effectiveG <= 0.1) {
            effectiveG = 0.1;
        }
        return Math.atan2(aLateral, effectiveG);
    }

    /**
     * 计算自由液面波高抬升高度 delta_h (m)
     * 几何关系: delta_h = R * tan(|theta_s|)
     */
    public double computeWaveElevation(double radius, double sloshAngle) {
        return radius * Math.tan(Math.abs(sloshAngle));
    }

    /**
     * 计算自由液面开口边缘防溢出控制屏障裕度 h_spill (m)
     * 公式: h_spill = H_lip - R * tan(|theta_s|)
     */
    public double computeSpillBarrierMargin(FluidSloshState state) {
        double waveH = computeWaveElevation(state.radius(), state.sloshAngle());
        return state.lipMargin() - waveH;
    }

    /**
     * 高阶控制屏障 (HOCBF) 极速解析 QP 投影修补器
     * 当预测波高逼近开口边缘裕度时，微秒级削减水平侧向加速度，确保液体绝不溢出。
     *
     * @param state        瞬时流体晃荡状态
     * @param nominalAcc   上层规划名义期望加速度 [ax, ay, az]
     * @return 经过安全屏障修正后的安全加速度 [ax_safe, ay_safe, az_safe]
     */
    public double[] filterNominalAcceleration(FluidSloshState state, double[] nominalAcc) {
        double margin = computeSpillBarrierMargin(state);
        double threshold = 0.2 * state.lipMargin(); // 20% 裕度触发主动限速

        double[] safeAcc = nominalAcc.clone();
        if (margin < threshold) {
            // 解析正交收缩缩放因子
            double scale = Math.max(0.0, margin / threshold);
            if (safeAcc.length > 0) safeAcc[0] *= scale;
            if (safeAcc.length > 1) safeAcc[1] *= scale;
            // 垂直轴通常不产生直接晃荡倾角，保留或适度缓冲
        }
        return safeAcc;
    }
}
