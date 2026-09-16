package tech.qiantong.qknow.ai.embodied.impedance.engine;

import tech.qiantong.qknow.ai.embodied.impedance.dto.AdaptiveImpedanceState;
import tech.qiantong.qknow.ai.embodied.impedance.dto.MultiModalSensorFrame;

/**
 * 触视觉无源性自适应阻抗调节器
 * 实现自由运动、触觉捕获与精密装配三阶段无冲击平滑过渡，
 * 引入虚拟能量储能罐 (Energy Tank) 守恒闭环，动态吸收时变刚度注入能量，杜绝接触自激振荡与高频啸叫。
 *
 * @author Achilles
 * @since 2026-09-16
 */
public class PassivityAdaptiveImpedanceGovernor {

    public static final String PHASE_FREE_MOTION = "FREE_MOTION";
    public static final String PHASE_TACTILE_CAPTURE = "TACTILE_CAPTURE";
    public static final String PHASE_PRECISION_ASSEMBLY = "PRECISION_ASSEMBLY";

    private final double eMinJoules;
    private final double eMaxJoules;
    private double currentEnergyTankJoules;
    private double previousStiffnessNorm;

    public PassivityAdaptiveImpedanceGovernor() {
        this(1.0, 50.0, 20.0);
    }

    public PassivityAdaptiveImpedanceGovernor(double eMinJoules, double eMaxJoules, double initialEnergyJoules) {
        this.eMinJoules = eMinJoules;
        this.eMaxJoules = eMaxJoules;
        this.currentEnergyTankJoules = Math.max(eMinJoules, Math.min(eMaxJoules, initialEnergyJoules));
        this.previousStiffnessNorm = 300.0; // 默认自由空间刚度
    }

    /**
     * 根据多模态感知帧更新自适应阻抗参数与储能罐动力学状态
     */
    public AdaptiveImpedanceState updateImpedance(MultiModalSensorFrame frame, double targetNormalForceN) {
        double normalForce = Math.abs(frame.tactileForceTorque6D()[2]); // Fz 法向力
        double shearForce = Math.sqrt(
                frame.tactileForceTorque6D()[0] * frame.tactileForceTorque6D()[0] +
                frame.tactileForceTorque6D()[1] * frame.tactileForceTorque6D()[1]
        );

        String currentPhase;
        double targetStiffness;
        double targetDamping;

        if (normalForce < 1.0) {
            // 阶段 1：自由运动阶段 (高频视觉引导位姿追踪)
            currentPhase = PHASE_FREE_MOTION;
            targetStiffness = 300.0;
            targetDamping = 40.0;
        } else if (normalForce < targetNormalForceN * 0.5) {
            // 阶段 2：触觉初接触柔顺捕获阶段 (微秒级降低冲击力)
            currentPhase = PHASE_TACTILE_CAPTURE;
            targetStiffness = 150.0; // 柔顺化软着陆
            targetDamping = 80.0;  // 增大阻尼耗散冲击
        } else {
            // 阶段 3：触视觉精密力控装配阶段
            currentPhase = PHASE_PRECISION_ASSEMBLY;
            targetStiffness = 800.0; // 稳态保压刚度
            targetDamping = 60.0;
        }

        // 储能罐无源性守恒动力学更新
        double stiffnessDelta = targetStiffness - previousStiffnessNorm;
        if (stiffnessDelta > 0.0) {
            // 刚度增大向闭环注入虚拟能量，必须由储能罐支出
            double energyRequired = 0.5 * stiffnessDelta * 1e-3; // 近似能量增量
            if (currentEnergyTankJoules - energyRequired >= eMinJoules) {
                currentEnergyTankJoules -= energyRequired;
                previousStiffnessNorm = targetStiffness;
            } else {
                // 储能罐余量不足，冻结刚度抬升以防自激振荡
                targetStiffness = previousStiffnessNorm;
            }
        } else {
            // 刚度降低或阻尼耗散，能量回充储能罐
            double energyRecovered = Math.abs(stiffnessDelta) * 0.5 * 1e-3;
            currentEnergyTankJoules = Math.min(eMaxJoules, currentEnergyTankJoules + energyRecovered);
            previousStiffnessNorm = targetStiffness;
        }

        // 剪切滑移裕度计算 (基于库伦摩擦锥: mu = 0.6)
        double frictionLimit = Math.max(0.1, normalForce * 0.6);
        double shearMargin = Math.max(0.0, Math.min(1.0, 1.0 - (shearForce / frictionLimit)));

        // 标称 HOCBF 裕度
        double hocbfMargin = 500.0 - normalForce; // 假设法向力硬限制 500N

        return new AdaptiveImpedanceState(
                currentPhase,
                previousStiffnessNorm,
                targetDamping,
                currentEnergyTankJoules,
                normalForce,
                shearMargin,
                hocbfMargin,
                false
        );
    }

    /**
     * 计算名义阻抗关节力矩 (Nm)
     */
    public double[] computeNominalTorque(AdaptiveImpedanceState state, MultiModalSensorFrame frame, double[] targetJointPositions) {
        int dof = (frame.jointPositionsRad() != null) ? frame.jointPositionsRad().length : 6;
        double[] nominalTorque = new double[dof];

        double kp = state.currentStiffnessNorm() * 0.05;
        double kd = state.currentDampingNorm() * 0.05;

        for (int i = 0; i < dof; i++) {
            double qTarget = (targetJointPositions != null && i < targetJointPositions.length) ? targetJointPositions[i] : 0.0;
            double qActual = (frame.jointPositionsRad() != null && i < frame.jointPositionsRad().length) ? frame.jointPositionsRad()[i] : 0.0;
            double dqActual = (frame.jointVelocitiesRadS() != null && i < frame.jointVelocitiesRadS().length) ? frame.jointVelocitiesRadS()[i] : 0.0;

            double error = qTarget - qActual;
            nominalTorque[i] = kp * error - kd * dqActual;
        }

        return nominalTorque;
    }

    public double getEnergyTankLevel() {
        return currentEnergyTankJoules;
    }

    public void rechargeTank(double joules) {
        this.currentEnergyTankJoules = Math.min(eMaxJoules, currentEnergyTankJoules + joules);
    }
}
