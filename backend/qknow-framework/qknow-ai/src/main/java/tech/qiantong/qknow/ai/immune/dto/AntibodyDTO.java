package tech.qiantong.qknow.ai.immune.dto;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 防御抗体数据传输对象
 *
 * @author Achilles
 * @since Phase 45
 */
public class AntibodyDTO {

    /**
     * 抗体唯一标识
     */
    private String antibodyId;

    /**
     * 靶向变异攻击模式描述
     */
    private String targetMutationPattern;

    /**
     * 抗体特征中心向量（阿里千问 1536 维超球面）
     */
    private float[] featureVector;

    /**
     * 词法掩码特征
     */
    private long featureMask;

    /**
     * 亲和度激活阻断阈值 theta [0.0, 1.0]
     */
    private double affinityThreshold;

    /**
     * 激活与阻断计数
     */
    private AtomicLong activationCount = new AtomicLong(0);

    /**
     * 适应度评分 [0.0, 1.0]
     */
    private double fitnessScore;

    /**
     * 创建时间戳
     */
    private long createdAt;

    public AntibodyDTO() {
        this.createdAt = System.currentTimeMillis();
    }

    public AntibodyDTO(String antibodyId, String targetMutationPattern, float[] featureVector,
                       long featureMask, double affinityThreshold, double fitnessScore) {
        this.antibodyId = antibodyId;
        this.targetMutationPattern = targetMutationPattern;
        this.featureVector = featureVector;
        this.featureMask = featureMask;
        this.affinityThreshold = affinityThreshold;
        this.fitnessScore = fitnessScore;
        this.activationCount = new AtomicLong(0);
        this.createdAt = System.currentTimeMillis();
    }

    public String getAntibodyId() {
        return antibodyId;
    }

    public void setAntibodyId(String antibodyId) {
        this.antibodyId = antibodyId;
    }

    public String getTargetMutationPattern() {
        return targetMutationPattern;
    }

    public void setTargetMutationPattern(String targetMutationPattern) {
        this.targetMutationPattern = targetMutationPattern;
    }

    public float[] getFeatureVector() {
        return featureVector;
    }

    public void setFeatureVector(float[] featureVector) {
        this.featureVector = featureVector;
    }

    public long getFeatureMask() {
        return featureMask;
    }

    public void setFeatureMask(long featureMask) {
        this.featureMask = featureMask;
    }

    public double getAffinityThreshold() {
        return affinityThreshold;
    }

    public void setAffinityThreshold(double affinityThreshold) {
        this.affinityThreshold = affinityThreshold;
    }

    public AtomicLong getActivationCount() {
        return activationCount;
    }

    public void setActivationCount(AtomicLong activationCount) {
        this.activationCount = activationCount;
    }

    public double getFitnessScore() {
        return fitnessScore;
    }

    public void setFitnessScore(double fitnessScore) {
        this.fitnessScore = fitnessScore;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
