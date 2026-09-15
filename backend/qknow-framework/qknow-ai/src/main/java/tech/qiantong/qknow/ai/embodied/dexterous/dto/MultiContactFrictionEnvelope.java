package tech.qiantong.qknow.ai.embodied.dexterous.dto;

import java.util.Arrays;

/**
 * 多接触点微观摩擦极限包络数据模型 (Java 21 Record)
 * 封装多接触点达布标架、库伦摩擦锥与接触椭圆半轴，并绑定阿里千问 1536 维超球面特征。
 *
 * @author Achilles
 * @since 2026-09-15
 */
public record MultiContactFrictionEnvelope(
        int contactCount,
        double[][] contactPoints,
        double[][] normalVectors,
        double[] frictionCoeffs,
        double[] majorSemiAxesMm,
        double[] minorSemiAxesMm,
        double[] qwenEmbedding1536
) {
    public MultiContactFrictionEnvelope {
        if (contactCount < 3) {
            throw new IllegalArgumentException("Multi-contact friction envelope requires at least 3 contact points, got: " + contactCount);
        }
        if (contactPoints == null || contactPoints.length != contactCount) {
            throw new IllegalArgumentException("Contact points array length must match contact count");
        }
        if (normalVectors == null || normalVectors.length != contactCount) {
            throw new IllegalArgumentException("Normal vectors array length must match contact count");
        }
        if (frictionCoeffs == null || frictionCoeffs.length != contactCount) {
            throw new IllegalArgumentException("Friction coefficients array length must match contact count");
        }
        if (qwenEmbedding1536 == null || qwenEmbedding1536.length != 1536) {
            throw new IllegalArgumentException("Qwen embedding must have exact dimension 1536");
        }
    }

    /**
     * 校验阿里千问 1536 维超球面单位范数约束 ||v||_2 = 1.0 +- 1e-5
     */
    public boolean verifyQwenHypersphereInvariant() {
        double sumSq = 0.0;
        for (double v : qwenEmbedding1536) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= 1e-5;
    }

    /**
     * 计算名义抓取接触中心 (质心近似)
     */
    public double[] computeGraspCenter() {
        double cx = 0.0, cy = 0.0, cz = 0.0;
        for (int i = 0; i < contactCount; i++) {
            cx += contactPoints[i][0];
            cy += contactPoints[i][1];
            cz += contactPoints[i][2];
        }
        return new double[]{cx / contactCount, cy / contactCount, cz / contactCount};
    }
}
