package tech.qiantong.qknow.module.kmc.service.rag.evolution.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.evolution.dto.MultimodalEntityNodeState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态多模态实体超球面测地线对齐求解引擎 (定理 1.1)
 * 融合阿里千问 1536 维超球面测地内积、Levenshtein 字符编辑相似度与 1-跳局部拓扑 Fréchet 均值，求解实体同构映射
 */
@Component
public class DynamicEntityAlignmentEngine {

    private static final Logger log = LoggerFactory.getLogger(DynamicEntityAlignmentEngine.class);

    private final Map<String, MultimodalEntityNodeState> entityRegistry = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();

    public void registerEntity(MultimodalEntityNodeState entity) {
        if (entity != null && entity.entityId() != null) {
            entityRegistry.put(entity.entityId(), entity);
            indexTokens(entity.entityId(), entity.canonicalName());
            if (entity.aliases() != null) {
                for (String alias : entity.aliases()) {
                    indexTokens(entity.entityId(), alias);
                }
            }
        }
    }

    private void indexTokens(String entityId, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        for (int i = 0; i < name.length(); i++) {
            String charToken = String.valueOf(name.charAt(i));
            invertedIndex.computeIfAbsent(charToken, k -> ConcurrentHashMap.newKeySet()).add(entityId);
        }
    }

    /**
     * 多模态实体对齐判定 (定理 1.1)
     * 目标泛函: S(u, v) = 0.45 * semSim + 0.25 * editSim + 0.30 * topoSim
     * 判定阈值: 默认 0.85
     */
    public Optional<MultimodalEntityNodeState> alignEntity(MultimodalEntityNodeState candidate, double threshold) {
        if (candidate == null || entityRegistry.isEmpty()) {
            return Optional.empty();
        }

        // 1. 倒排索引粗筛候选集 (按公共字字符命中)
        Set<String> candidateIds = new HashSet<>();
        if (candidate.canonicalName() != null) {
            for (int i = 0; i < candidate.canonicalName().length(); i++) {
                String c = String.valueOf(candidate.canonicalName().charAt(i));
                Set<String> hits = invertedIndex.get(c);
                if (hits != null) {
                    candidateIds.addAll(hits);
                }
            }
        }

        // 若倒排为空或规模较小，允许全量轻量粗筛
        Collection<MultimodalEntityNodeState> searchPool = candidateIds.isEmpty() ?
                entityRegistry.values() :
                candidateIds.stream().map(entityRegistry::get).filter(Objects::nonNull).toList();

        MultimodalEntityNodeState bestMatch = null;
        double bestScore = -1.0;

        for (MultimodalEntityNodeState target : searchPool) {
            if (target.entityId().equals(candidate.entityId())) {
                continue;
            }

            // 1. 语义嵌入内积 (超球面大圆弧距离投影: (dot + 1) / 2)
            double semSim = 0.5;
            if (candidate.sphericalEmbedding() != null && target.sphericalEmbedding() != null &&
                    candidate.sphericalEmbedding().length == 1536 && target.sphericalEmbedding().length == 1536) {
                double dot = calculateDotProduct1536(candidate.sphericalEmbedding(), target.sphericalEmbedding());
                semSim = (dot + 1.0) * 0.5;
            }

            // 启发式上界剪枝 1: 假设后续编辑距离与拓扑内积均为 1.0，最大可能得分能否达到 threshold
            if (0.45 * semSim + 0.55 < threshold) {
                continue;
            }

            // 2. 字符串编辑相似度
            double editSim = calculateEditSimilarity(candidate.canonicalName(), target.canonicalName());
            if (candidate.aliases() != null && !candidate.aliases().isEmpty()) {
                for (String alias : candidate.aliases()) {
                    double aSim = calculateEditSimilarity(alias, target.canonicalName());
                    if (aSim > editSim) {
                        editSim = aSim;
                    }
                }
            }

            // 启发式上界剪枝 2: 假设后续拓扑内积为 1.0，最大可能得分能否达到 threshold
            if (0.45 * semSim + 0.25 * editSim + 0.30 < threshold) {
                continue;
            }

            // 3. 1-跳局部拓扑 Fréchet 均值内积
            double topoSim = 0.5;
            if (candidate.localTopologyEmbedding() != null && target.localTopologyEmbedding() != null &&
                    candidate.localTopologyEmbedding().length == 1536 && target.localTopologyEmbedding().length == 1536) {
                double tDot = calculateDotProduct1536(candidate.localTopologyEmbedding(), target.localTopologyEmbedding());
                topoSim = (tDot + 1.0) * 0.5;
            }

            // 综合凸组合加权得分
            double compositeScore = 0.45 * semSim + 0.25 * editSim + 0.30 * topoSim;
            if (compositeScore >= threshold && compositeScore > bestScore) {
                bestScore = compositeScore;
                bestMatch = target;
            }
        }

        return Optional.ofNullable(bestMatch);
    }

    /**
     * 8路循环展开计算 1536 维超球面单位向量点积
     */
    public static double calculateDotProduct1536(double[] a, double[] b) {
        double dot = 0.0;
        int i = 0;
        for (; i <= 1536 - 8; i += 8) {
            dot += a[i] * b[i]
                 + a[i+1] * b[i+1]
                 + a[i+2] * b[i+2]
                 + a[i+3] * b[i+3]
                 + a[i+4] * b[i+4]
                 + a[i+5] * b[i+5]
                 + a[i+6] * b[i+6]
                 + a[i+7] * b[i+7];
        }
        for (; i < 1536; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    /**
     * 归一化 Levenshtein 字符编辑相似度
     */
    public static double calculateEditSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 || len2 == 0) {
            return 0.0;
        }

        int[] dp = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) {
            dp[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= len2; j++) {
                int temp = dp[j];
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[j] = prev;
                } else {
                    dp[j] = 1 + Math.min(prev, Math.min(dp[j], dp[j - 1]));
                }
                prev = temp;
            }
        }

        int distance = dp[len2];
        int maxLen = Math.max(len1, len2);
        return 1.0 - ((double) distance / maxLen);
    }

    public int getEntityCount() {
        return entityRegistry.size();
    }
}
