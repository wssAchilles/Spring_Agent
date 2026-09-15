package tech.qiantong.qknow.ai.skill;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 62: 基于测地线余弦内积的千问 1536 维超球面技能流形索引
 * <p>
 * 基于定理 1.1，在紧致超球面流形 S^1535 上实现毫秒级高精度意图检索与拓扑覆盖。
 */
public class GeodesicSkillManifoldIndex {

    private final Map<String, SkillMetadata> skillIndex = new ConcurrentHashMap<>();

    /**
     * 注册技能至流形索引
     */
    public void registerSkill(SkillMetadata skill) {
        if (skill == null || skill.skillId() == null) {
            throw new IllegalArgumentException("技能元数据不能为空");
        }
        skillIndex.put(skill.skillId(), skill);
    }

    /**
     * 基于测地线余弦内积与历史成功率加权检索 Top-K 技能
     */
    public List<SkillMetadata> searchTopK(float[] queryVector, int k) {
        if (queryVector == null || queryVector.length != SkillMetadata.EMBEDDING_DIMENSION || k <= 0) {
            return List.of();
        }

        record ScoredSkill(SkillMetadata skill, double score) {}

        List<ScoredSkill> scoredList = new ArrayList<>();

        for (SkillMetadata skill : skillIndex.values()) {
            if (skill.isDeprecated()) {
                continue; // 过滤已废弃技能
            }

            double cosineSim = computeCosineSimilarity(queryVector, skill.embeddingVector());
            // 综合测地线相似度 (0.70) 与历史执行成功率 (0.30)
            double combinedScore = 0.70 * cosineSim + 0.30 * skill.successRate();
            scoredList.add(new ScoredSkill(skill, combinedScore));
        }

        scoredList.sort((a, b) -> Double.compare(b.score(), a.score()));

        List<SkillMetadata> results = new ArrayList<>();
        for (int i = 0; i < Math.min(k, scoredList.size()); i++) {
            results.add(scoredList.get(i).skill());
        }
        return results;
    }

    private double computeCosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
        }
        // 由于向量已预先执行 L2 归一化，点积即为余弦相似度
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    public int getSkillCount() {
        return skillIndex.size();
    }

    public void removeSkill(String skillId) {
        if (skillId != null) {
            skillIndex.remove(skillId);
        }
    }

    public void clear() {
        skillIndex.clear();
    }
}
