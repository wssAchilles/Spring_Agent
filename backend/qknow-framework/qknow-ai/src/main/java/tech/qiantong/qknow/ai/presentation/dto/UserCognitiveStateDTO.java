package tech.qiantong.qknow.ai.presentation.dto;

import java.io.Serializable;

/**
 * 用户交互行为与即时认知状态快照 DTO
 */
public class UserCognitiveStateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 会话累计上下文 Token 长度 */
    private int contextTokenLength;
    /** 用户操作停顿或阅读间隔毫秒数 */
    private long dwellTimeMs;
    /** 候选分支或方案的信息熵率 (0.0 ~ 1.0) */
    private double informationEntropy;
    /** 业务场景紧急度标量 (0.0 ~ 1.0) */
    private double urgencyFactor;
    /** 连续用户操作纠正频次 (如短时间内撤销/重置次数) */
    private int correctionCount;

    public UserCognitiveStateDTO() {}

    public UserCognitiveStateDTO(int contextTokenLength, long dwellTimeMs, double informationEntropy, double urgencyFactor, int correctionCount) {
        this.contextTokenLength = contextTokenLength;
        this.dwellTimeMs = dwellTimeMs;
        this.informationEntropy = informationEntropy;
        this.urgencyFactor = urgencyFactor;
        this.correctionCount = correctionCount;
    }

    public int getContextTokenLength() {
        return contextTokenLength;
    }

    public void setContextTokenLength(int contextTokenLength) {
        this.contextTokenLength = contextTokenLength;
    }

    public long getDwellTimeMs() {
        return dwellTimeMs;
    }

    public void setDwellTimeMs(long dwellTimeMs) {
        this.dwellTimeMs = dwellTimeMs;
    }

    public double getInformationEntropy() {
        return informationEntropy;
    }

    public void setInformationEntropy(double informationEntropy) {
        this.informationEntropy = informationEntropy;
    }

    public double getUrgencyFactor() {
        return urgencyFactor;
    }

    public void setUrgencyFactor(double urgencyFactor) {
        this.urgencyFactor = urgencyFactor;
    }

    public int getCorrectionCount() {
        return correctionCount;
    }

    public void setCorrectionCount(int correctionCount) {
        this.correctionCount = correctionCount;
    }
}
