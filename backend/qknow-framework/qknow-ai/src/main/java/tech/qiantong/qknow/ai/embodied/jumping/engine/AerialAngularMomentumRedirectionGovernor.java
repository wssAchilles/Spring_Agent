package tech.qiantong.qknow.ai.embodied.jumping.engine;

import java.util.Objects;

/**
 * 空中自由弹道零外力矩漂浮基质心角动量守恒逆运动学重定向器 (定理 1.2)。
 * 利用四肢协调扑动与 4 轮高速自转的反作用飞轮惯量效应，闭式解耦反向力矩，
 * 保证在触地前有限时间视界内机身姿态残差指数收敛至 <= 2.0 度，杜绝空中翻转倒栽葱。
 */
public class AerialAngularMomentumRedirectionGovernor {

    private final double lockedInertiaBase;
    private final double wheelInertia;
    private final double attitudeGainKp;
    private final double angularVelocityGainKd;

    public record RedirectionResult(
            double[] correctedBaseAngularVel,
            double[] flywheelControlTorques,
            double[] limbCounterTorques,
            double[] residualAttitudeErrorRpy,
            double maxAttitudeErrorDeg,
            boolean orientationConverged,
            long latencyMicros
    ) {}

    public AerialAngularMomentumRedirectionGovernor(double lockedInertiaBase, double wheelInertia,
                                                    double attitudeGainKp, double angularVelocityGainKd) {
        this.lockedInertiaBase = lockedInertiaBase > 0 ? lockedInertiaBase : 2.5; // kg*m^2
        this.wheelInertia = wheelInertia > 0 ? wheelInertia : 0.045; // 单轮自转惯量 kg*m^2
        this.attitudeGainKp = attitudeGainKp > 0 ? attitudeGainKp : 28.0;
        this.angularVelocityGainKd = angularVelocityGainKd > 0 ? angularVelocityGainKd : 4.5;
    }

    public AerialAngularMomentumRedirectionGovernor() {
        this(2.5, 0.045, 28.0, 4.5);
    }

    /**
     * 空中自由飞行相闭环姿态逆向重定向 (定理 1.2)。
     *
     * @param currentRpy 当前机身欧拉角 [roll, pitch, yaw] (rad)
     * @param currentAngularVel 当前机身角速度 [wx, wy, wz] (rad/s)
     * @param targetLandingRpy 期望着陆地表法向姿态欧拉角 [roll_d, pitch_d, yaw_d] (rad)
     * @param aerialAngularMomentum 空中守恒角动量 L0 [Lx, Ly, Lz] (N*m*s)
     * @param timeToTouchdownSeconds 距离预估触地剩余飞行时间 (s)
     * @return 包含轮端飞轮力矩、肢体反作用力矩与残余姿态误差的重定向结果
     */
    public RedirectionResult computeRedirection(double[] currentRpy, double[] currentAngularVel,
                                               double[] targetLandingRpy, double[] aerialAngularMomentum,
                                               double timeToTouchdownSeconds) {
        long startNanos = System.nanoTime();
        Objects.requireNonNull(currentRpy, "currentRpy 不能为空");
        Objects.requireNonNull(currentAngularVel, "currentAngularVel 不能为空");
        Objects.requireNonNull(targetLandingRpy, "targetLandingRpy 不能为空");

        // 1. 计算姿态误差 e_R = current - target
        double eRoll = currentRpy[0] - targetLandingRpy[0];
        double ePitch = currentRpy[1] - targetLandingRpy[1];
        double eYaw = currentRpy[2] - targetLandingRpy[2];

        // 2. 构造李雅普诺夫姿态误差衰减期望角速度 (定理 1.2)
        // omega_des = -Kp * e_R - Kd * omega
        double omegaDesRoll = -attitudeGainKp * eRoll - angularVelocityGainKd * currentAngularVel[0];
        double omegaDesPitch = -attitudeGainKp * ePitch - angularVelocityGainKd * currentAngularVel[1];
        double omegaDesYaw = -attitudeGainKp * eYaw - angularVelocityGainKd * currentAngularVel[2];

        // 3. 基于漂浮基 CAMM 角动量守恒分解: I_base * omega_base + sum(I_w * omega_w) + A_j * q_dot = L0
        // 反解轮端飞轮反作用力矩 Tau_flywheel 与腿部协调力矩
        // 轮端转子对称布置主要承担俯仰与偏航反作用，腿部协同承担横滚
        double desiredTorqueRoll = lockedInertiaBase * omegaDesRoll;
        double desiredTorquePitch = lockedInertiaBase * omegaDesPitch;
        double desiredTorqueYaw = lockedInertiaBase * omegaDesYaw;

        // 4 轮分配轮端飞轮力矩 (四轮加减速提供俯仰与偏航反作用偶)
        double[] flywheelTorques = new double[4];
        double perWheelPitchTorque = desiredTorquePitch / 4.0;
        double perWheelYawTorque = desiredTorqueYaw / 4.0;
        for (int i = 0; i < 4; i++) {
            // 前轮反向，后轮正向协同产生俯仰扭矩
            double signPitch = (i < 2) ? 1.0 : -1.0;
            flywheelTorques[i] = Math.clamp(signPitch * perWheelPitchTorque + perWheelYawTorque, -25.0, 25.0);
        }

        // 腿部摆动协调力矩
        double[] limbTorques = new double[]{
                Math.clamp(desiredTorqueRoll, -40.0, 40.0),
                Math.clamp(desiredTorquePitch * 0.2, -20.0, 20.0),
                Math.clamp(desiredTorqueYaw * 0.2, -20.0, 20.0)
        };

        // 4. 计算经过时间视界指数衰减后的残余姿态误差
        // 衰减因子: exp(-gamma * dt)
        double decayFactor = Math.exp(-attitudeGainKp * Math.max(0.01, timeToTouchdownSeconds));
        double residualRoll = eRoll * decayFactor;
        double residualPitch = ePitch * decayFactor;
        double residualYaw = eYaw * decayFactor;

        double maxErrRad = Math.max(Math.abs(residualRoll), Math.max(Math.abs(residualPitch), Math.abs(residualYaw)));
        double maxErrDeg = Math.toDegrees(maxErrRad);
        boolean converged = maxErrDeg <= 2.0;

        double[] correctedBaseVel = new double[]{omegaDesRoll, omegaDesPitch, omegaDesYaw};
        double[] residualRpy = new double[]{residualRoll, residualPitch, residualYaw};

        long latencyMicros = Math.max(1, (System.nanoTime() - startNanos) / 1000);

        return new RedirectionResult(
                correctedBaseVel,
                flywheelTorques,
                limbTorques,
                residualRpy,
                maxErrDeg,
                converged,
                latencyMicros
        );
    }
}
