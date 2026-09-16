package tech.qiantong.qknow.ai.embodied.hybrid.dto;

import java.util.Objects;

/**
 * 轮腿混合构型状态帧。
 * <p>
 * 封装轮腿各关节角度与轮速、机身六维位姿旋量、各足端接触力矢、局部地形高程特征及阿里千问 1536 维超球面单位向量。
 */
public record LegWheelStateFrame(
        String frameId,
        String sessionId,
        String robotId,
        String topologyMode,
        double[] jointAngles,
        double[] jointVelocities,
        double[] wheelVelocities,
        double[] basePose6D,
        double[] baseTwist6D,
        double[][] footContactForces,
        double[] terrainElevationMap,
        double[] qwenEmbedding1536,
        long timestampMicros
) {
    public LegWheelStateFrame {
        Objects.requireNonNull(frameId, "frameId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(robotId, "robotId 不能为空");
        Objects.requireNonNull(topologyMode, "topologyMode 不能为空");
        Objects.requireNonNull(jointAngles, "jointAngles 不能为空");
        Objects.requireNonNull(jointVelocities, "jointVelocities 不能为空");
        Objects.requireNonNull(wheelVelocities, "wheelVelocities 不能为空");
        Objects.requireNonNull(basePose6D, "basePose6D 不能为空");
        Objects.requireNonNull(baseTwist6D, "baseTwist6D 不能为空");
        Objects.requireNonNull(footContactForces, "footContactForces 不能为空");
        Objects.requireNonNull(terrainElevationMap, "terrainElevationMap 不能为空");
        Objects.requireNonNull(qwenEmbedding1536, "qwenEmbedding1536 不能为空");

        if (qwenEmbedding1536.length != 1536) {
            throw new IllegalArgumentException("阿里千问特征向量维度必须严格为 1536，当前为: " + qwenEmbedding1536.length);
        }

        // 校验阿里千问 1536 维向量的超球面单位模长约束: ||v||_2 = 1.0 ± 1e-5
        double normSq = 0.0;
        for (double val : qwenEmbedding1536) {
            normSq += val * val;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("阿里千问特征向量未投影在单位超球面上，L2 范数为: " + norm);
        }

        // 防御性拷贝
        jointAngles = jointAngles.clone();
        jointVelocities = jointVelocities.clone();
        wheelVelocities = wheelVelocities.clone();
        basePose6D = basePose6D.clone();
        baseTwist6D = baseTwist6D.clone();
        terrainElevationMap = terrainElevationMap.clone();
        qwenEmbedding1536 = qwenEmbedding1536.clone();

        double[][] copyFootForces = new double[footContactForces.length][];
        for (int i = 0; i < footContactForces.length; i++) {
            copyFootForces[i] = (footContactForces[i] != null) ? footContactForces[i].clone() : new double[3];
        }
        footContactForces = copyFootForces;
    }
}
