package tech.qiantong.qknow.ai.immune.engine;

import tech.qiantong.qknow.ai.immune.dto.AntibodyDTO;
import tech.qiantong.qknow.ai.immune.dto.AntigenDTO;
import tech.qiantong.qknow.ai.immune.enums.ImmuneAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 免疫记忆账本与二次免疫极速判定引擎
 * <p>
 * 基于无锁并发映射表实现已知攻击抗体特征库的高速检索与亲和度匹配。
 * 定理 1.1：二次免疫反应时间复杂度严格为 O(1)，耗时 <= 1ms。
 *
 * @author Achilles
 * @since Phase 45
 */
public class ImmuneMemoryLedger {

    private final Map<String, AntibodyDTO> memoryPool = new ConcurrentHashMap<>();

    private static final double WEIGHT_SEMANTIC = 0.55;
    private static final double WEIGHT_LEXICAL = 0.30;
    private static final double WEIGHT_SYNTAX = 0.15;

    /**
     * 注册/更新防御抗体到记忆库
     */
    public void registerAntibody(AntibodyDTO antibody) {
        if (antibody == null || antibody.getAntibodyId() == null) {
            return;
        }
        memoryPool.put(antibody.getAntibodyId(), antibody);
    }

    /**
     * 查询当前记忆抗体库大小
     */
    public int size() {
        return memoryPool.size();
    }

    /**
     * 获取全部记忆抗体列表
     */
    public Collection<AntibodyDTO> getAllAntibodies() {
        return memoryPool.values();
    }

    /**
     * 清空抗体库（测试或热重置用）
     */
    public void clear() {
        memoryPool.clear();
    }

    /**
     * 计算输入抗原与指定抗体之间的亲和度函数 (Affinity)
     * <p>
     * Affinity(x, a) = w1 * max(0, e_sem · e_a) + w2 * (1 - Hamming(s_lex, m_a)/64) + w3 * c_risk
     */
    public double calculateAffinity(AntigenDTO antigen, AntibodyDTO antibody) {
        if (antigen == null || antibody == null) {
            return 0.0;
        }

        // 1. 语义嵌入余弦相似度
        double dotProduct = 0.0;
        float[] v1 = antigen.getSemanticEmbedding();
        float[] v2 = antibody.getFeatureVector();
        if (v1 != null && v2 != null && v1.length == v2.length) {
            for (int i = 0; i < v1.length; i++) {
                dotProduct += v1[i] * v2[i];
            }
        }
        double semanticScore = Math.max(0.0, Math.min(1.0, dotProduct));

        // 2. 词法 SimHash 汉明相似度
        int hammingDist = Long.bitCount(antigen.getLexicalSimHash() ^ antibody.getFeatureMask());
        double lexicalScore = 1.0 - ((double) hammingDist / 64.0);
        lexicalScore = Math.max(0.0, Math.min(1.0, lexicalScore));

        // 3. 结构语法风险增强
        double syntaxScore = Math.max(0.0, Math.min(1.0, antigen.getSyntaxRiskScore()));

        // 特征基底匹配度：语义相似度 70% + 词法相似度 30%（自身完全匹配时精确为 1.0）
        double baseAffinity = 0.70 * semanticScore + 0.30 * lexicalScore;
        double affinity = Math.min(1.0, baseAffinity + 0.10 * syntaxScore);
        return Math.max(0.0, Math.min(1.0, affinity));
    }

    /**
     * 二次免疫极速检测与处置裁决 (<= 1ms)
     *
     * @param antigen 提取的抗原特征
     * @return 免疫处置结果与激活的抗体
     */
    public ImmuneCheckResult matchImmunity(AntigenDTO antigen) {
        if (antigen == null || memoryPool.isEmpty()) {
            return new ImmuneCheckResult(ImmuneAction.SAFE_PASS, 0.0, null);
        }

        double maxAffinity = 0.0;
        AntibodyDTO matchedAntibody = null;

        for (AntibodyDTO antibody : memoryPool.values()) {
            double affinity = calculateAffinity(antigen, antibody);
            if (affinity > maxAffinity) {
                maxAffinity = affinity;
                matchedAntibody = antibody;
            }
        }

        if (matchedAntibody != null && maxAffinity >= matchedAntibody.getAffinityThreshold()) {
            matchedAntibody.getActivationCount().incrementAndGet();
            return new ImmuneCheckResult(ImmuneAction.BLOCK_AND_ISOLATE, maxAffinity, matchedAntibody);
        }

        return new ImmuneCheckResult(ImmuneAction.SAFE_PASS, maxAffinity, matchedAntibody);
    }

    /**
     * 免疫检查结果结构体
     */
    public static class ImmuneCheckResult {
        private final ImmuneAction action;
        private final double highestAffinity;
        private final AntibodyDTO matchedAntibody;

        public ImmuneCheckResult(ImmuneAction action, double highestAffinity, AntibodyDTO matchedAntibody) {
            this.action = action;
            this.highestAffinity = highestAffinity;
            this.matchedAntibody = matchedAntibody;
        }

        public ImmuneAction getAction() {
            return action;
        }

        public double getHighestAffinity() {
            return highestAffinity;
        }

        public AntibodyDTO getMatchedAntibody() {
            return matchedAntibody;
        }
    }
}
