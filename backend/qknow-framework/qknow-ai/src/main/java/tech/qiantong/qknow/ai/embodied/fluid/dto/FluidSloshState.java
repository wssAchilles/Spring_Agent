package tech.qiantong.qknow.ai.embodied.fluid.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * 流体晃荡动力学与自由液面状态瞬时表征 (Java 21 Record)
 * <p>
 * 封装容器几何特征、充液静止高度、流体物理属性、自由液面晃荡角与角速度、残余晃荡动能、
 * 六维末端合加速度、流固耦合反作用力矩、阿里千问 1536 维超球面单位特征向量与时间戳。
 *
 * @param containerId              容器唯一标识
 * @param radius                   圆柱形容器内壁半径 R (m)
 * @param height                   容器总高度 H (m)
 * @param lipMargin                开口边缘防溢出裕度 H_lip (m)
 * @param initialLiquidHeight      初始充液静止液位 H_0 (m)
 * @param density                  流体宏观质量密度 rho (kg/m^3)
 * @param dynamicViscosity         流体动力粘度 mu (Pa*s)
 * @param sloshAngle               自由液面一阶基模瞬时晃荡倾角 theta_s (rad)
 * @param sloshAngularVelocity     晃荡角速度 dot{theta}_s (rad/s)
 * @param sloshKineticEnergy       流体残余晃荡总动能 E_k (J)
 * @param endEffectorAcceleration  六维末端三轴加速度 [a_x, a_y, a_z] (m/s^2)
 * @param sloshReactionTorque      流固耦合对末端施加的反作用力矩 [tau_x, tau_y, tau_z] (N*m)
 * @param hypersphericalEmbedding  阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)
 * @param timestampMs              物理测量/推演时间戳 (ms)
 */
public record FluidSloshState(
        String containerId,
        double radius,
        double height,
        double lipMargin,
        double initialLiquidHeight,
        double density,
        double dynamicViscosity,
        double sloshAngle,
        double sloshAngularVelocity,
        double sloshKineticEnergy,
        double[] endEffectorAcceleration,
        double[] sloshReactionTorque,
        double[] hypersphericalEmbedding,
        long timestampMs
) {
    public FluidSloshState {
        Objects.requireNonNull(containerId, "containerId 不能为空");
        Objects.requireNonNull(endEffectorAcceleration, "endEffectorAcceleration 不能为空");
        Objects.requireNonNull(sloshReactionTorque, "sloshReactionTorque 不能为空");
        Objects.requireNonNull(hypersphericalEmbedding, "hypersphericalEmbedding 不能为空");
        if (hypersphericalEmbedding.length != 1536) {
            throw new IllegalArgumentException("hypersphericalEmbedding 维度必须严格为 1536 维，当前为: " + hypersphericalEmbedding.length);
        }
        if (radius <= 0 || height <= 0 || initialLiquidHeight <= 0) {
            throw new IllegalArgumentException("容器几何尺寸与充液高度必须为正数");
        }
    }

    /**
     * 校验阿里千问 1536 维超球面特征向量是否严格满足单位模长归一化约束 (||v||_2 = 1.0 +- 1e-6)
     */
    public boolean isEmbeddingUnitNorm() {
        double sumSq = 0.0;
        for (double v : hypersphericalEmbedding) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= 1e-6;
    }
}
