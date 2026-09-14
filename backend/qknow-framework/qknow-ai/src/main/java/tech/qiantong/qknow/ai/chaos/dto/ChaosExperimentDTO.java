package tech.qiantong.qknow.ai.chaos.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * 混沌工程实验定义与载荷实体
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class ChaosExperimentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实验类型枚举
     */
    public enum ExperimentType {
        NETWORK_LATENCY,      // 网络延迟抖动
        PACKET_LOSS,          // 网络丢包
        NETWORK_PARTITION,    // 跨机房双向对称网络分区
        ASYMMETRIC_PARTITION, // 跨机房单向不对称网络分区
        NODE_LIMPING,         // 亚健康软死故障（高延迟/频繁卡顿）
        NODE_CRASH            // 节点硬下线
    }

    /**
     * 实验状态枚举
     */
    public enum ExperimentStatus {
        PENDING,   // 待执行
        ACTIVE,    // 正在生效注入
        CANCELLED, // 主动撤销
        EXPIRED    // 超时自愈失效
    }

    private String experimentId;
    private ExperimentType experimentType;
    private String targetRegion;
    private String targetNodeId;
    private long latencyMs;
    private double packetLossRate; // 0.0 ~ 1.0
    private long maxDurationMs;    // 最大生存时间，超时自动自愈
    private long startTimeMs;
    private ExperimentStatus status;

    public ChaosExperimentDTO() {
        this.status = ExperimentStatus.PENDING;
    }

    public ChaosExperimentDTO(String experimentId, ExperimentType experimentType, String targetRegion,
                              String targetNodeId, long latencyMs, double packetLossRate, long maxDurationMs) {
        this.experimentId = experimentId;
        this.experimentType = experimentType;
        this.targetRegion = targetRegion;
        this.targetNodeId = targetNodeId;
        this.latencyMs = latencyMs;
        this.packetLossRate = packetLossRate;
        this.maxDurationMs = maxDurationMs;
        this.startTimeMs = System.currentTimeMillis();
        this.status = ExperimentStatus.ACTIVE;
    }

    public boolean isExpired() {
        if (maxDurationMs <= 0) {
            return false;
        }
        return System.currentTimeMillis() - startTimeMs >= maxDurationMs;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public ExperimentType getExperimentType() {
        return experimentType;
    }

    public void setExperimentType(ExperimentType experimentType) {
        this.experimentType = experimentType;
    }

    public String getTargetRegion() {
        return targetRegion;
    }

    public void setTargetRegion(String targetRegion) {
        this.targetRegion = targetRegion;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public double getPacketLossRate() {
        return packetLossRate;
    }

    public void setPacketLossRate(double packetLossRate) {
        this.packetLossRate = packetLossRate;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    public ExperimentStatus getStatus() {
        return status;
    }

    public void setStatus(ExperimentStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChaosExperimentDTO that = (ChaosExperimentDTO) o;
        return Objects.equals(experimentId, that.experimentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(experimentId);
    }
}
