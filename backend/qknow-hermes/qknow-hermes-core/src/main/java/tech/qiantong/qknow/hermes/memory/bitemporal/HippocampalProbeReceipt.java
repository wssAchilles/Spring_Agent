package tech.qiantong.qknow.hermes.memory.bitemporal;

import java.util.List;
import java.util.Objects;

/**
 * 思考链内 RI-PPR（识别记忆过滤 + 稠密-稀疏二分图个性化 PageRank）迭代检索凭单。
 */
public record HippocampalProbeReceipt(
        String queryId,
        String subQueryText,
        int rawSeedCount,
        int recognitionFilteredSeedCount,
        int pprIterations,
        double pprL1Residual,
        List<String> recalledPassageIds,
        List<String> recalledPassageContents,
        List<String> recalledErrorBookWarnings,
        long elapsedMicros
) {
    public HippocampalProbeReceipt {
        Objects.requireNonNull(queryId, "queryId 不能为空");
        recalledPassageIds = List.copyOf(recalledPassageIds != null ? recalledPassageIds : List.of());
        recalledPassageContents = List.copyOf(recalledPassageContents != null ? recalledPassageContents : List.of());
        recalledErrorBookWarnings = List.copyOf(recalledErrorBookWarnings != null ? recalledErrorBookWarnings : List.of());
    }
}
