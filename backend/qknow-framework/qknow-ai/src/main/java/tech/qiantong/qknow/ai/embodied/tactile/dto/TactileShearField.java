package tech.qiantong.qknow.ai.embodied.tactile.dto;

import java.util.Objects;

/**
 * 触觉微剪切应变场数据传输对象 (Java 21 Record)
 * <p>
 * 封装法向接触压力 F_n、切向合成力 F_t、微剪切应变张量分量 (gamma_xx, gamma_yy, gamma_xy)、
 * 阿里千问 1536 维超球面单位特征向量与时间戳。
 */
public record TactileShearField(
        String sensorId,
        double normalForceN,
        double tangentialForceN,
        double shearStrainXx,
        double shearStrainYy,
        double shearStrainXy,
        double[] qwenEmbedding1536,
        long timestampNs
) {
    public TactileShearField {
        Objects.requireNonNull(sensorId, "sensorId 不能为 null");
        Objects.requireNonNull(qwenEmbedding1536, "qwenEmbedding1536 不能为 null");
        if (qwenEmbedding1536.length != 1536) {
            throw new IllegalArgumentException("qwenEmbedding1536 维度必须严格为 1536");
        }
    }

    /**
     * 计算微剪切应变张量等效冯·米塞斯 (von Mises) 标量值
     */
    public double equivalentShearStrain() {
        return Math.sqrt(shearStrainXx * shearStrainXx + shearStrainYy * shearStrainYy + 3.0 * shearStrainXy * shearStrainXy);
    }

    /**
     * 校验阿里千问 1536 维超球面单位归一化约束 (||v||_2 = 1.0 +- 1e-5)
     */
    public boolean isHypersphericalUnitNormalized() {
        double sumSq = 0.0;
        for (double val : qwenEmbedding1536) {
            sumSq += val * val;
        }
        return Math.abs(Math.sqrt(sumSq) - 1.0) <= 1e-5;
    }
}
