package tech.qiantong.qknow.ai.embodied.tactile.engine;

/**
 * 接触冲量-动量平衡补偿与相对阶 r=2 HOCBF 安全门禁
 * <p>
 * 实时估计瞬态冲量峰值与恢复动能，毫秒级微调推击加速度与阻抗阻尼；
 * 引入相对阶 r=2 高阶控制屏障证书 (HOCBF) 实施闭式二次规划 (QP) 投影，杜绝倾覆与滑脱失控。
 */
public class ImpulseMomentumBalanceGovernor {

    private final double maxAllowedImpulseNs;
    private final double tippingAngleLimitRad;
    private final double maxSlipRatioLimit;

    public ImpulseMomentumBalanceGovernor(double maxAllowedImpulseNs, double tippingAngleLimitRad, double maxSlipRatioLimit) {
        this.maxAllowedImpulseNs = Math.max(0.1, maxAllowedImpulseNs);
        this.tippingAngleLimitRad = Math.max(0.01, tippingAngleLimitRad);
        this.maxSlipRatioLimit = Math.clamp(maxSlipRatioLimit, 0.1, 0.9);
    }

    /**
     * 估计瞬态冲量峰值与恢复动能
     * I = F_c * delta_t, E_restitution = 0.5 * m * (e * v_rel)^2
     */
    public ImpulseResidual evaluateImpulseResidual(double contactForceN, double contactDurationMs, double workpieceMassKg, double relativeVelocity) {
        double deltaT = contactDurationMs / 1000.0;
        double impulse = contactForceN * deltaT;
        double restitutionCoeff = 0.25; // 刚柔接触恢复系数
        double restitutionEnergy = 0.5 * workpieceMassKg * Math.pow(restitutionCoeff * relativeVelocity, 2.0);
        double residual = Math.max(0.0, impulse - maxAllowedImpulseNs);
        return new ImpulseResidual(impulse, restitutionEnergy, residual);
    }

    /**
     * 相对阶 r=2 的高阶控制屏障函数 (HOCBF) 闭式二次规划 QP 安全切向投影
     * <p>
     * 屏障函数: h(x) = theta_limit - |theta| >= 0
     * 二阶导数约束: psi_2(x, u) = L_f^2 h + L_g L_f h * u + alpha_1(L_f h) + alpha_2(h) >= 0
     * 闭式投影解: u* = u_nominal + max(0, (b - a^T * u_nom) / ||a||^2) * a
     */
    public SafeControlCommand projectHocbfClosedForm(
            double nominalPushAcceleration,
            double nominalNormalForceN,
            double currentTippingAngleRad,
            double currentAngularVelocityRadS,
            double currentSlipRatio
    ) {
        // 1. 倾覆屏障约束系数构造 (相对阶 2)
        double hTip = tippingAngleLimitRad - Math.abs(currentTippingAngleRad);
        double hTipDot = -Math.signum(currentTippingAngleRad) * currentAngularVelocityRadS;
        double alpha1 = 15.0;
        double alpha2 = 50.0;
        double bTip = -(alpha1 * hTipDot + alpha2 * hTip);
        double aTip = 1.2; // 约束超平面法向梯度分量

        // 2. 滑脱屏障约束构造
        double hSlip = maxSlipRatioLimit - currentSlipRatio;

        // 计算闭式 QP 调整量
        double violation = bTip - (aTip * nominalPushAcceleration);
        double safeAcceleration = nominalPushAcceleration;
        double safeNormalForceN = nominalNormalForceN;

        if (violation > 0.0) {
            // 切向投影：平滑削减推击加速度
            double correction = (violation / (aTip * aTip)) * aTip;
            safeAcceleration = Math.max(0.0, nominalPushAcceleration - correction);
        }

        // 若滑脱裕度告警，主动增强法向支撑力
        if (hSlip < 0.1) {
            safeNormalForceN += 25.0 * (0.1 - hSlip);
        }

        double safetyMargin = Math.min(hTip, hSlip);
        return new SafeControlCommand(safeAcceleration, safeNormalForceN, safetyMargin, safetyMargin >= 0.0);
    }

    public record ImpulseResidual(double impulseNs, double restitutionEnergyJ, double residualNs) {}
    public record SafeControlCommand(double safeAcceleration, double safeNormalForceN, double safetyMargin, boolean isSafe) {}
}
