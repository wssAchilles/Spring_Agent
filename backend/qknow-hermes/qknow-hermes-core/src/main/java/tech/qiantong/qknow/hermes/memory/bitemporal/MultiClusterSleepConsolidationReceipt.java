package tech.qiantong.qknow.hermes.memory.bitemporal;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * S^1023 多簇球面睡眠整合与 Wiki / Error-Book 编译不可变凭单（定理 4.1(1)-(2)）。
 */
public record MultiClusterSleepConsolidationReceipt(
        String consolidationId,
        String sessionId,
        List<SphericalClusterSummary> clusters,
        double totalMultiClusterChordDistortion,
        double singleCentroidCollapseDistortion,
        List<String> compiledWikiPages,
        List<String> compiledErrorBookNotes,
        long elapsedMicros
) {
    public MultiClusterSleepConsolidationReceipt {
        Objects.requireNonNull(consolidationId, "consolidationId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        clusters = List.copyOf(clusters != null ? clusters : List.of());
        compiledWikiPages = List.copyOf(compiledWikiPages != null ? compiledWikiPages : List.of());
        compiledErrorBookNotes = List.copyOf(compiledErrorBookNotes != null ? compiledErrorBookNotes : List.of());
    }

    /**
     * 单个球冠簇的精确几何失真统计摘要
     *
     * @param clusterIndex              簇索引
     * @param memberCount               簇内样本数
     * @param centroid1024              外蕴归一化质心 mu_k^* in S^1023
     * @param meanResultantLength       平均合成长度 R_k = ||sum omega_i v_i||_2 in (0, 1]
     * @param maxCapRadiusRad           簇最大测地角半径 theta_k in [0, pi)
     * @param squaredChordDistortion    精确期望平方弦重构失真 2*(1 - R_k)
     * @param intrinsicGeodesicDistortion 实测本征测地平方失真 sum omega_i * arccos(<v_i, mu_k^*>)^2
     * @param geodesicUpperBound        定理 4.1(2) 球冠紧上界 ((theta_k/2) / sin(theta_k/2))^2 * 2*(1 - R_k)
     */
    public record SphericalClusterSummary(
            int clusterIndex,
            int memberCount,
            float[] centroid1024,
            double meanResultantLength,
            double maxCapRadiusRad,
            double squaredChordDistortion,
            double intrinsicGeodesicDistortion,
            double geodesicUpperBound
    ) {
        public SphericalClusterSummary {
            centroid1024 = centroid1024 != null ? Arrays.copyOf(centroid1024, centroid1024.length) : new float[0];
        }

        @Override
        public float[] centroid1024() {
            return Arrays.copyOf(centroid1024, centroid1024.length);
        }
    }
}
