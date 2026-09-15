package tech.qiantong.qknow.ai.embodied.actuation.engine;

/**
 * 变刚度变阻尼自适应接触阻抗控制器与非线性扰动观测器 (DOB) (Theorem 1.3)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class AdaptiveImpedanceActuator {

    private final double mass;
    private final double baseStiffness;
    private final double dampingRatio;
    private final double forceSofteningFactor;
    private final double dobGain;

    // DOB 内部状态
    private final double[] dobState = new double[3];

    public record ActuatorResponse(
            double currentStiffness,
            double currentDamping,
            double[] estimatedDisturbance,
            double[] compliantForceOutput,
            double forceMagnitude
    ) {}

    public AdaptiveImpedanceActuator(double mass, double baseStiffness, double dampingRatio,
                                     double forceSofteningFactor, double dobGain) {
        this.mass = mass > 0 ? mass : 1.0;
        this.baseStiffness = baseStiffness > 0 ? baseStiffness : 500.0;
        this.dampingRatio = dampingRatio >= 1.0 ? dampingRatio : 1.1; // 临界/过阻尼
        this.forceSofteningFactor = forceSofteningFactor >= 0 ? forceSofteningFactor : 0.02;
        this.dobGain = dobGain > 0 ? dobGain : 20.0;
    }

    /**
     * 单步接触阻抗闭环求解与扰动消除
     */
    public ActuatorResponse update(double[] posError, double[] velError, double[] measuredForce, double dt) {
        if (posError == null) posError = new double[]{0, 0, 0};
        if (velError == null) velError = new double[]{0, 0, 0};
        if (measuredForce == null) measuredForce = new double[]{0, 0, 0};
        double step = dt > 0 ? dt : 0.001;

        double forceNorm = Math.sqrt(measuredForce[0] * measuredForce[0]
                + measuredForce[1] * measuredForce[1]
                + measuredForce[2] * measuredForce[2]);

        // 1. 变刚度阻抗软化: K_d = K_base / (1 + gamma * ||F||)
        double currentStiffness = baseStiffness / (1.0 + forceSofteningFactor * forceNorm);

        // 2. 临界/过阻尼自适应联动: D_d = 2 * zeta * sqrt(M * K)
        double currentDamping = 2.0 * dampingRatio * Math.sqrt(mass * currentStiffness);

        // 3. 经典非线性扰动观测器 (DOB) 状态更新:
        // p_dot = -L/M * (p + L * v_err) + L/M * F_meas
        // d_hat = p + L * v_err
        double[] dHat = new double[3];
        for (int i = 0; i < 3; i++) {
            dHat[i] = dobState[i] + dobGain * velError[i];

            double pDot = -(dobGain / mass) * (dobState[i] + dobGain * velError[i])
                    + (dobGain / mass) * measuredForce[i];

            dobState[i] += pDot * step;
        }

        // 4. 合成前向顺应输出力: 经过 DOB 快速估计和前馈对消后，末端受力被软化吸收
        double[] compliantForce = new double[3];
        for (int i = 0; i < 3; i++) {
            // 顺应力残差: 外力与 DOB 估计值之差，加上自适应弹簧阻尼吸收
            double forceDiff = measuredForce[i] - dHat[i];
            compliantForce[i] = forceDiff * (currentStiffness / baseStiffness);
        }

        return new ActuatorResponse(
                currentStiffness,
                currentDamping,
                dHat,
                compliantForce,
                forceNorm
        );
    }

    public void resetDob() {
        dobState[0] = 0.0;
        dobState[1] = 0.0;
        dobState[2] = 0.0;
    }
}
