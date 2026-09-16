package tech.qiantong.qknow.mcp.core.orchestration.dto;

import java.util.List;

/**
 * 微服务拓扑节点状态 Record (Java 21 原生 Record)
 * 封装微服务节点 ID、集群域、承载工具集、实时 RTT、错误率及阿里千问 1536 维超球面单位向量
 */
public record McpTopologyNodeState(
        String serviceId,
        String clusterName,
        List<String> providedTools,
        double rttMillis,
        double errorRate,
        boolean healthy,
        double[] sphericalEmbedding,
        long lastHeartbeatEpochMs
) {
    /**
     * 严格校验阿里千问 1536 维超球面单位向量范数：||v||_2 = 1.0 ± 1e-4
     */
    public boolean isValidEmbedding() {
        if (sphericalEmbedding == null || sphericalEmbedding.length != 1536) {
            return false;
        }
        double normSq = 0.0;
        for (double v : sphericalEmbedding) {
            normSq += v * v;
        }
        return Math.abs(Math.sqrt(normSq) - 1.0) <= 1e-4;
    }
}
