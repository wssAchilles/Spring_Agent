package tech.qiantong.qknow.ai.embodied.continuum.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * 连续体软体臂离散骨架几何位姿帧。
 * <p>
 * 封装中心线空间采样坐标、截面曲率与扭转旋量、微流控腔室实测气压、
 * 末端六维触觉力矩以及阿里千问 1536 维超球面单位向量。
 *
 * @param frameId 帧唯一标识
 * @param timestampMicros 采集时间戳（微秒）
 * @param centerlinePositions 中心线离散点三维坐标数组 [x0, y0, z0, x1, y1, z1, ...]
 * @param curvatureTwist 截面平均弯曲曲率与扭转率 [kappa_x, kappa_y, tau_z]
 * @param chamberPressures 各微流控腔室实测气压 (kPa)
 * @param tipTactileWrench 末端六维力觉与力矩 [Fx, Fy, Fz, Mx, My, Mz]
 * @param qwenEmbedding 阿里千问 1536 维超球面归一化特征向量
 */
public record ContinuumArmGeometryFrame(
        String frameId,
        long timestampMicros,
        double[] centerlinePositions,
        double[] curvatureTwist,
        double[] chamberPressures,
        double[] tipTactileWrench,
        double[] qwenEmbedding
) {
    public ContinuumArmGeometryFrame {
        Objects.requireNonNull(frameId, "frameId 不能为空");
        Objects.requireNonNull(centerlinePositions, "centerlinePositions 不能为空");
        Objects.requireNonNull(curvatureTwist, "curvatureTwist 不能为空");
        Objects.requireNonNull(chamberPressures, "chamberPressures 不能为空");
        Objects.requireNonNull(tipTactileWrench, "tipTactileWrench 不能为空");
        Objects.requireNonNull(qwenEmbedding, "qwenEmbedding 不能为空");

        if (qwenEmbedding.length != 1536) {
            throw new IllegalArgumentException("阿里千问特征向量必须精确为 1536 维，当前维度: " + qwenEmbedding.length);
        }
        double normSq = 0.0;
        for (double val : qwenEmbedding) {
            normSq += val * val;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("阿里千问特征向量必须为超球面单位向量 (L2 = 1.0)，当前模长: " + norm);
        }
    }
}
