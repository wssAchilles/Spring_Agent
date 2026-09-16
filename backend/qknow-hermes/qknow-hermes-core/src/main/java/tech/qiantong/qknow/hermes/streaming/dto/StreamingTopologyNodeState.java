package tech.qiantong.qknow.hermes.streaming.dto;

/**
 * 流式推理拓扑节点状态 Java 21 Record
 * 封装节点标识、角色、健康状态、往返时延 RTT 与词元吞吐率
 */
public record StreamingTopologyNodeState(
        String nodeId,
        String nodeRole,
        String status, // HEALTHY, DEGRADED, FAILED
        long rttMillis,
        double throughputTokensPerSec,
        long lastHeartbeatTimestamp
) {
    public static final String STATUS_HEALTHY = "HEALTHY";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String STATUS_FAILED = "FAILED";

    public boolean isHealthy() {
        return STATUS_HEALTHY.equalsIgnoreCase(status) && rttMillis <= 200;
    }

    public StreamingTopologyNodeState withDegraded() {
        return new StreamingTopologyNodeState(nodeId, nodeRole, STATUS_DEGRADED, rttMillis, throughputTokensPerSec, System.currentTimeMillis());
    }

    public StreamingTopologyNodeState withFailed() {
        return new StreamingTopologyNodeState(nodeId, nodeRole, STATUS_FAILED, rttMillis, 0.0, System.currentTimeMillis());
    }

    public StreamingTopologyNodeState withRtt(long newRtt) {
        String newStatus = newRtt > 200 ? STATUS_DEGRADED : STATUS_HEALTHY;
        return new StreamingTopologyNodeState(nodeId, nodeRole, newStatus, newRtt, throughputTokensPerSec, System.currentTimeMillis());
    }
}
