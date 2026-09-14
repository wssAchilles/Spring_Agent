package tech.qiantong.qknow.module.kmc.service.rag.graph;

import java.util.List;
import java.util.Map;

/**
 * 双层神经符号图重排器接口 (GraphRAG 2.0)
 * 整合千问 1536 维超球面种子实体检索、神经符号门控剪枝与 Banach 不动点 PPR 扩散
 */
public interface NeuroSymbolicGraphRanker {

    record RankedGraphContext(
            List<String> activeSeedEntities,
            List<MultiHopCausalPathExtractor.CausalPathFact> validatedCausalChains,
            List<String> macroCommunitySummaries,
            Map<String, Double> entityImportanceScores
    ) {}

    /**
     * 执行双层神经符号检索与重排
     */
    RankedGraphContext rankGraphContext(
            String workspaceId,
            String query,
            float[] queryVector,
            List<String> extractedEntities
    );
}
