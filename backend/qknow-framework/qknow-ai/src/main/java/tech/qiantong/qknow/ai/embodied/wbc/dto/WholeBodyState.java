package tech.qiantong.qknow.ai.embodied.wbc.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * 移动操作全身物理状态集 (不可变 Java 21 Record)
 * 封装基座浮动基 6-DoF 位姿速度、全驱动关节状态、质心动量运动学量、支撑多边形接触点及千问 1536 维超球面意图向量
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record WholeBodyState(
        double[] basePose,           // x, y, z, roll, pitch, yaw (6维)
        double[] baseVelocity,       // vx, vy, vz, wx, wy, wz (6维)
        double[] baseAcceleration,   // ax, ay, az, alphax, alphay, alphaz (6维)
        double[] jointAngles,        // 关节角度 (n维)
        double[] jointVelocities,    // 关节角速度 (n维)
        double[] comPosition,        // 整机质心位置 x, y, z (3维)
        double[] comVelocity,        // 整机质心速度 vx, vy, vz (3维)
        double[] comAcceleration,    // 整机质心加速度 ax, ay, az (3维)
        double[] angularMomentumRate,// 质心角动量变化率 dot_lx, dot_ly, dot_lz (3维)
        double[][] contactPoints,    // 地面支撑接触点坐标 [M][2] (水平投影 x, y)
        double[] semanticEmbedding   // 阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)
) {
    public WholeBodyState {
        Objects.requireNonNull(basePose, "basePose 不能为空");
        Objects.requireNonNull(baseVelocity, "baseVelocity 不能为空");
        Objects.requireNonNull(baseAcceleration, "baseAcceleration 不能为空");
        Objects.requireNonNull(jointAngles, "jointAngles 不能为空");
        Objects.requireNonNull(jointVelocities, "jointVelocities 不能为空");
        Objects.requireNonNull(comPosition, "comPosition 不能为空");
        Objects.requireNonNull(comVelocity, "comVelocity 不能为空");
        Objects.requireNonNull(comAcceleration, "comAcceleration 不能为空");
        Objects.requireNonNull(angularMomentumRate, "angularMomentumRate 不能为空");
        Objects.requireNonNull(contactPoints, "contactPoints 不能为空");
        Objects.requireNonNull(semanticEmbedding, "semanticEmbedding 不能为空");
    }

    /**
     * 校验阿里千问 1536 维向量是否位于单位超球面
     */
    public boolean isHypersphericalNormalized() {
        if (semanticEmbedding.length != 1536) {
            return false;
        }
        double sumSq = 0.0;
        for (double v : semanticEmbedding) {
            sumSq += v * v;
        }
        return Math.abs(Math.sqrt(sumSq) - 1.0) < 1e-4;
    }
}
