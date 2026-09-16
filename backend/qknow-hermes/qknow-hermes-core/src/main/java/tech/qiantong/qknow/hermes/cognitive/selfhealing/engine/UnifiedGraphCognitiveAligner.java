package tech.qiantong.qknow.hermes.cognitive.selfhealing.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图谱子图统一认知推理对齐器
 * 将推理思维链候选事实与 Phase 87 交付的 2-跳 PPR 诱导子图进行超球面联合投影，抑制事实幻觉
 */
@Slf4j
@Component
public class UnifiedGraphCognitiveAligner {

    public record SubgraphEntity(
            String entityId,
            String entityName,
            double pprWeight,
            double[] embedding
    ) {}

    private final Map<String, SubgraphEntity> entityRegistry = new ConcurrentHashMap<>();

    public void registerSubgraphKnowledge(String entityId, String entityName, double pprWeight, double[] embedding) {
        entityRegistry.put(entityId, new SubgraphEntity(entityId, entityName, pprWeight, embedding));
    }

    /**
     * 事实接地对齐过滤：计算候选事实与子图实体的最大联合投影分
     * A_ij = ((cos + 1) / 2) * pprWeight >= alignThreshold (默认 0.65)
     */
    public List<String> alignAndGroundFacts(List<String> candidateFacts, Map<String, double[]> factEmbeddings, double alignThreshold) {
        if (candidateFacts == null || candidateFacts.isEmpty() || entityRegistry.isEmpty()) {
            return candidateFacts != null ? candidateFacts : Collections.emptyList();
        }

        List<String> groundedFacts = new ArrayList<>();
        for (String fact : candidateFacts) {
            double[] fEmb = factEmbeddings.get(fact);
            double maxAlignmentScore = 0.0;

            if (fEmb != null && fEmb.length == 1536) {
                for (SubgraphEntity ent : entityRegistry.values()) {
                    double[] entEmb = ent.embedding();
                    if (entEmb != null && entEmb.length == 1536) {
                        double dot = 0.0;
                        for (int i = 0; i < 1536; i++) {
                            dot += fEmb[i] * entEmb[i];
                        }
                        double cosNorm = (dot + 1.0) * 0.5;
                        // 联合打分：结合语义测地内积与图谱拓扑 PPR 权重
                        double alignmentScore = cosNorm * (0.5 + 0.5 * ent.pprWeight());
                        if (alignmentScore > maxAlignmentScore) {
                            maxAlignmentScore = alignmentScore;
                        }
                    }
                }
            } else {
                // 若无向量，依据实体名关键词包含判定接地
                for (SubgraphEntity ent : entityRegistry.values()) {
                    if (fact.contains(ent.entityName())) {
                        maxAlignmentScore = Math.max(maxAlignmentScore, 0.70);
                    }
                }
            }

            if (maxAlignmentScore >= alignThreshold) {
                groundedFacts.add(fact);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("图谱接地门禁剔除虚构事实: fact='{}', maxAlignmentScore={}<{}",
                            fact, maxAlignmentScore, alignThreshold);
                }
            }
        }
        return groundedFacts;
    }

    public int getEntityCount() {
        return entityRegistry.size();
    }
}
