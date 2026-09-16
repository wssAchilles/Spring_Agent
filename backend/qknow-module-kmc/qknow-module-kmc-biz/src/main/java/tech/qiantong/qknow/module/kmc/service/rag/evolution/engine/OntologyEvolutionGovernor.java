package tech.qiantong.qknow.module.kmc.service.rag.evolution.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 神经符号本体演化流形收缩调节器 (定理 1.3)
 * 标准受控本体树超球面覆盖帽投影规约，发散开放式抽取谓词规范化收敛，杜绝 Schema 爆炸
 */
@Component
public class OntologyEvolutionGovernor {

    private static final Logger log = LoggerFactory.getLogger(OntologyEvolutionGovernor.class);

    // 标准受控本体关系谓词 -> 阿里千问 1536 维超球面中心概念向量
    private final Map<String, double[]> standardOntology = new ConcurrentHashMap<>();

    // 未达标长尾新概念隔离待审缓冲区: rawPredicate -> 频次与特征
    private final Map<String, AtomicInteger> conceptBuffer = new ConcurrentHashMap<>();

    // 统计指标
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger contractedQueries = new AtomicInteger(0);

    public void registerStandardPredicate(String predicateName, double[] conceptEmbedding) {
        if (predicateName != null && conceptEmbedding != null && conceptEmbedding.length == 1536) {
            standardOntology.put(predicateName, conceptEmbedding);
        }
    }

    /**
     * 将候选谓词流形收缩规约至标准受控本体 (定理 1.3)
     * 阈值: 0.80
     */
    public String contractPredicateToStandard(String rawPredicate, double[] predicateEmbedding, double threshold) {
        if (rawPredicate == null || rawPredicate.isBlank()) {
            return "unknown";
        }
        totalQueries.incrementAndGet();

        // 1. 若本身属于受控本体，直接恒等通过
        if (standardOntology.containsKey(rawPredicate)) {
            contractedQueries.incrementAndGet();
            return rawPredicate;
        }

        // 2. 在标准受控本体超球面流形上计算测地内积覆盖帽 (8 路展开加速)
        String bestStandard = null;
        double bestCosine = -1.0;

        if (predicateEmbedding != null && predicateEmbedding.length == 1536) {
            for (Map.Entry<String, double[]> entry : standardOntology.entrySet()) {
                double dot = DynamicEntityAlignmentEngine.calculateDotProduct1536(predicateEmbedding, entry.getValue());
                if (dot > bestCosine) {
                    bestCosine = dot;
                    bestStandard = entry.getKey();
                }
            }
        }

        // 3. 判定是否落入覆盖帽 C_r (dot >= threshold)
        if (bestStandard != null && bestCosine >= threshold) {
            contractedQueries.incrementAndGet();
            if (log.isDebugEnabled()) {
                log.debug("本体流形收缩: 发散谓词 '{}' 规约至标准本体 '{}' (相似度={})", rawPredicate, bestStandard, bestCosine);
            }
            return bestStandard;
        }

        // 4. 未落入标准覆盖帽，隔离入待审缓冲区并记录频次
        conceptBuffer.computeIfAbsent(rawPredicate, k -> new AtomicInteger(0)).incrementAndGet();
        if (log.isDebugEnabled()) {
            log.debug("长尾新概念已隔离入待审池: '{}', 当前出现频次={}", rawPredicate, conceptBuffer.get(rawPredicate).get());
        }
        return "NEW_CONCEPT";
    }

    public double getContractionRatio() {
        int total = totalQueries.get();
        if (total == 0) {
            return 1.0;
        }
        return (double) contractedQueries.get() / total;
    }

    public int getBufferedConceptCount() {
        return conceptBuffer.size();
    }

    public int getBufferedConceptFrequency(String predicate) {
        AtomicInteger count = conceptBuffer.get(predicate);
        return count == null ? 0 : count.get();
    }
}
