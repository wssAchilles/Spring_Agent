package tech.qiantong.qknow.ai.embodied.deformable.dto;

import java.util.Objects;

/**
 * 可形变物体瞬时多模态物理状态集
 * <p>
 * 封装物体离散三维网格节点坐标、节点速度、格林-拉格朗日应变张量场、
 * 触觉高分辨率三维微应变与压力场矩阵、全局视觉点云特征以及阿里千问 1536 维超球面单位特征向量。
 *
 * @param objectId                  可形变工件唯一业务标识
 * @param meshNodes                 离散三维网格节点当前空间坐标 [N][3] (m)
 * @param nodeVelocities            节点三维瞬时速度矩阵 [N][3] (m/s)
 * @param strainTensors             格林-拉格朗日非线性应变张量场 [N][3][3]
 * @param tactileField              局部触觉阵列三维微剪切与法向压力场 [H][W][3] (N, m)
 * @param visualPointCloudCentroid  全局视觉点云质心坐标 [3] (m)
 * @param qwenEmbedding             阿里千问 1536 维超球面单位特征向量 (||v||_2 = 1.0)
 * @param timestampMs               采样物理时间戳 (ms)
 */
public record DeformableObjectState(
        String objectId,
        double[][] meshNodes,
        double[][] nodeVelocities,
        double[][][] strainTensors,
        double[][][] tactileField,
        double[] visualPointCloudCentroid,
        double[] qwenEmbedding,
        long timestampMs
) {
    public DeformableObjectState {
        Objects.requireNonNull(objectId, "objectId 不能为空");
        Objects.requireNonNull(meshNodes, "meshNodes 不能为空");
        Objects.requireNonNull(nodeVelocities, "nodeVelocities 不能为空");
        Objects.requireNonNull(qwenEmbedding, "qwenEmbedding 不能为空");
        if (qwenEmbedding.length != 1536) {
            throw new IllegalArgumentException("阿里千问向量维度必须严格为 1536，实际为: " + qwenEmbedding.length);
        }
        if (meshNodes.length != nodeVelocities.length) {
            throw new IllegalArgumentException("网格节点数与速度矩阵维度不一致: " + meshNodes.length + " != " + nodeVelocities.length);
        }
    }
}
