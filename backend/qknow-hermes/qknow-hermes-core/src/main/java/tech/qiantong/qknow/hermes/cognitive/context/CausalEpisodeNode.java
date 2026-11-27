package tech.qiantong.qknow.hermes.cognitive.context;

import tech.qiantong.qknow.hermes.memory.bitemporal.ColdStandbyTier;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 因果情景节点（Java 21 不可变 Record，对应 HDZ-PCF 滞回双区上下文依赖图节点）。
 * <p>
 * 区分探索型（{@link EpisodeType#EXPLORATORY}）与动作型（{@link EpisodeType#ACTION}），
 * 并维护依赖边集合 {@code dependsOnEpisodeIds}、1024 维单位向量与三级冷备可逆层级。
 * </p>
 */
public record CausalEpisodeNode(
        String episodeId,
        EpisodeType episodeType,
        String summaryHandle,
        String rawContent,
        int tokenCount,
        List<String> dependsOnEpisodeIds,
        float[] unitEmbedding1024,
        boolean folded,
        ColdStandbyTier storageTier,
        long createdAtMs
) {

    public enum EpisodeType {
        /** 探索性观察与检索片段（如只读图谱查询结果、文档片段） */
        EXPLORATORY,
        /** 已持久化或已提交的工具动作轨迹（外部副作用已落地或已有句柄） */
        ACTION
    }

    public CausalEpisodeNode {
        Objects.requireNonNull(episodeId, "episodeId 不能为空");
        Objects.requireNonNull(episodeType, "episodeType 不能为空");
        summaryHandle = summaryHandle == null ? "" : summaryHandle;
        rawContent = rawContent == null ? "" : rawContent;
        tokenCount = Math.max(0, tokenCount);
        dependsOnEpisodeIds = dependsOnEpisodeIds == null ? List.of() : List.copyOf(dependsOnEpisodeIds);
        unitEmbedding1024 = unitEmbedding1024 == null ? new float[0] : Arrays.copyOf(unitEmbedding1024, unitEmbedding1024.length);
        storageTier = storageTier == null ? ColdStandbyTier.ACTIVE_HOT : storageTier;
    }

    @Override
    public float[] unitEmbedding1024() {
        return Arrays.copyOf(unitEmbedding1024, unitEmbedding1024.length);
    }

    public CausalEpisodeNode withFolded(ColdStandbyTier targetTier, String compactHandle) {
        return new CausalEpisodeNode(
                episodeId,
                episodeType,
                compactHandle != null ? compactHandle : summaryHandle,
                rawContent,
                tokenCount,
                dependsOnEpisodeIds,
                unitEmbedding1024,
                true,
                targetTier != null ? targetTier : ColdStandbyTier.WARM_QUARANTINE,
                createdAtMs
        );
    }

    public CausalEpisodeNode withRestored() {
        return new CausalEpisodeNode(
                episodeId,
                episodeType,
                summaryHandle,
                rawContent,
                tokenCount,
                dependsOnEpisodeIds,
                unitEmbedding1024,
                false,
                ColdStandbyTier.ACTIVE_HOT,
                createdAtMs
        );
    }
}
