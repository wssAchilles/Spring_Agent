package tech.qiantong.qknow.hermes.memory.bitemporal;

import java.util.List;

/**
 * 睡眠期海马体图谱谱蒸馏与冷备归档不可变凭单（Java 21 Record）。
 */
public record SpectralDistillationReceipt(
        int evaluatedVertexCount,
        int evaluatedEdgeCount,
        int prunedToColdStandbyCount,
        double algebraicConnectivityLambda2,
        double spectralEnergyRetainedRatio,
        List<String> archivedEdgeIds,
        long durationUs,
        long timestampMs
) {
}
