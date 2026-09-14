package tech.qiantong.qknow.ai.federated.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 联邦任务聚合结果响应视图
 */
public class FederatedTaskResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    /** 共有知识实体交集 */
    private List<String> sharedEntities;
    /** 同态加权平均置信度 (量化标量值，如 0~1000) */
    private double aggregatedConfidence;
    /** 局部差分隐私合规审计标记 */
    private boolean differentialPrivacyCompliant;
    /** 总执行耗时 (毫秒) */
    private long totalDurationMs;
    /** 参与方列表 */
    private List<String> participantList;

    public FederatedTaskResultVO() {}

    public FederatedTaskResultVO(String taskId, List<String> sharedEntities, double aggregatedConfidence, boolean differentialPrivacyCompliant, long totalDurationMs, List<String> participantList) {
        this.taskId = taskId;
        this.sharedEntities = sharedEntities;
        this.aggregatedConfidence = aggregatedConfidence;
        this.differentialPrivacyCompliant = differentialPrivacyCompliant;
        this.totalDurationMs = totalDurationMs;
        this.participantList = participantList;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<String> getSharedEntities() {
        return sharedEntities;
    }

    public void setSharedEntities(List<String> sharedEntities) {
        this.sharedEntities = sharedEntities;
    }

    public double getAggregatedConfidence() {
        return aggregatedConfidence;
    }

    public void setAggregatedConfidence(double aggregatedConfidence) {
        this.aggregatedConfidence = aggregatedConfidence;
    }

    public boolean isDifferentialPrivacyCompliant() {
        return differentialPrivacyCompliant;
    }

    public void setDifferentialPrivacyCompliant(boolean differentialPrivacyCompliant) {
        this.differentialPrivacyCompliant = differentialPrivacyCompliant;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public List<String> getParticipantList() {
        return participantList;
    }

    public void setParticipantList(List<String> participantList) {
        this.participantList = participantList;
    }
}
