package tech.qiantong.qknow.ai.chaos.dto;

import java.io.Serializable;

/**
 * 节点多维健康快照与信誉状态实体
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class NodeHealthSnapshotVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY,   // 健康，可承载正常流量
        LIMPING,   // 亚健康软死（高延迟离群），触发告警
        ISOLATED,  // 处于物理软隔离状态，路由权重置为 0
        DEAD       // 硬下线或宕机
    }

    private String nodeId;
    private String region;
    private HealthStatus status;
    private double ewmaLatencyMs;
    private double variance;
    private double zScore;
    private int routingWeight;      // 动态权重 0 ~ 100
    private int consecutiveFailures;
    private int consecutiveSuccesses;
    private long lastReportTimestamp;

    public NodeHealthSnapshotVO() {
        this.status = HealthStatus.HEALTHY;
        this.routingWeight = 100;
        this.lastReportTimestamp = System.currentTimeMillis();
    }

    public NodeHealthSnapshotVO(String nodeId, String region) {
        this();
        this.nodeId = nodeId;
        this.region = region;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public double getEwmaLatencyMs() {
        return ewmaLatencyMs;
    }

    public void setEwmaLatencyMs(double ewmaLatencyMs) {
        this.ewmaLatencyMs = ewmaLatencyMs;
    }

    public double getVariance() {
        return variance;
    }

    public void setVariance(double variance) {
        this.variance = variance;
    }

    public double getZScore() {
        return zScore;
    }

    public void setZScore(double zScore) {
        this.zScore = zScore;
    }

    public int getRoutingWeight() {
        return routingWeight;
    }

    public void setRoutingWeight(int routingWeight) {
        this.routingWeight = routingWeight;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public int getConsecutiveSuccesses() {
        return consecutiveSuccesses;
    }

    public void setConsecutiveSuccesses(int consecutiveSuccesses) {
        this.consecutiveSuccesses = consecutiveSuccesses;
    }

    public long getLastReportTimestamp() {
        return lastReportTimestamp;
    }

    public void setLastReportTimestamp(long lastReportTimestamp) {
        this.lastReportTimestamp = lastReportTimestamp;
    }
}
