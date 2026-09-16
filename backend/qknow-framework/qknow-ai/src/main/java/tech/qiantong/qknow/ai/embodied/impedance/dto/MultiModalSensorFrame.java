package tech.qiantong.qknow.ai.embodied.impedance.dto;

import java.util.Arrays;

/**
 * 具身异构传感器多模态时间戳对齐帧 (Java 21 Record)
 * 封装高频触觉剪切/法向力、中频视觉空间坐标、高频关节状态、视觉传输延迟与阿里千问 1536 维超球面单位向量。
 *
 * @author Achilles
 * @since 2026-09-16
 */
public record MultiModalSensorFrame(
        String frameId,
        long tactileTimestampUs,
        long visualTimestampUs,
        long jointTimestampUs,
        double[] tactileForceTorque6D, // [Fx, Fy, Fz, Tx, Ty, Tz]
        double[] visualPoseTranslation3D, // 视觉空间末端位姿 [x, y, z] (m)
        double[] jointPositionsRad,    // 关节角度 (rad)
        double[] jointVelocitiesRadS,  // 关节角速度 (rad/s)
        double visualLatencyMs,        // 视觉测量传输延迟 (ms)
        double[] qwenEmbedding1536     // 阿里千问 1536 维超球面单位向量
) {
    public MultiModalSensorFrame {
        if (tactileForceTorque6D == null || tactileForceTorque6D.length != 6) {
            throw new IllegalArgumentException("触觉六维力矩数组长度必须严格为 6");
        }
        if (visualPoseTranslation3D == null || visualPoseTranslation3D.length != 3) {
            throw new IllegalArgumentException("视觉三维平移数组长度必须严格为 3");
        }
        if (qwenEmbedding1536 != null && qwenEmbedding1536.length != 1536) {
            throw new IllegalArgumentException("阿里千问特征向量必须严格为 1536 维");
        }
    }
}
