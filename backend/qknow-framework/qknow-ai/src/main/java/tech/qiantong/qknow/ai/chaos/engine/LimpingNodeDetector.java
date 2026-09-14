package tech.qiantong.qknow.ai.chaos.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.chaos.dto.NodeHealthSnapshotVO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 亚健康节点自适应 EWMA 方差检验与自愈隔离器 (Theorem 2.1)
 * 实时监测微观延迟离群度，对高延迟假死节点实施毫秒级物理软隔离 (Node Fencing)
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class LimpingNodeDetector {

    private static final Logger log = LoggerFactory.getLogger(LimpingNodeDetector.class);

    private static final double ALPHA = 0.2; // EWMA 均值平滑因子
    private static final double BETA = 0.1;  // EWMA 方差平滑因子
    private static final double Z_SCORE_THRESHOLD = 3.0; // 3-Sigma 离群阈值
    private static final int CONSECUTIVE_OUTLIER_LIMIT = 3; // 连续 3 次离群判定为 LIMPING
    private static final int CONSECUTIVE_SUCCESS_LIMIT = 3; // 连续 3 次正常探活恢复 HEALTHY

    private final Map<String, NodeHealthSnapshotVO> nodeHealthMap = new ConcurrentHashMap<>();
    private double clusterBaselineMeanMs = 15.0;
    private double clusterBaselineStdDevMs = 5.0;

    public void registerNode(String nodeId, String region) {
        NodeHealthSnapshotVO vo = new NodeHealthSnapshotVO(nodeId, region);
        vo.setEwmaLatencyMs(clusterBaselineMeanMs);
        vo.setVariance(clusterBaselineStdDevMs * clusterBaselineStdDevMs);
        nodeHealthMap.put(nodeId, vo);
    }

    /**
     * 记录单次请求处理耗时并递推更新 EWMA 与方差
     */
    public synchronized void recordLatency(String nodeId, double latencyMs) {
        NodeHealthSnapshotVO node = nodeHealthMap.computeIfAbsent(nodeId, id -> new NodeHealthSnapshotVO(id, "default"));

        double oldMean = node.getEwmaLatencyMs();
        // 递推更新均值
        double newMean = (1 - ALPHA) * oldMean + ALPHA * latencyMs;
        node.setEwmaLatencyMs(newMean);

        // 递推更新方差
        double delta = latencyMs - oldMean;
        double oldVar = node.getVariance();
        double newVar = (1 - BETA) * oldVar + BETA * (delta * delta);
        node.setVariance(newVar);

        // 计算当前 Z-Score
        double zScore = (newMean - clusterBaselineMeanMs) / Math.max(clusterBaselineStdDevMs, 1.0);
        node.setZScore(zScore);
        node.setLastReportTimestamp(System.currentTimeMillis());

        // 状态机演化
        if (zScore > Z_SCORE_THRESHOLD) {
            node.setConsecutiveFailures(node.getConsecutiveFailures() + 1);
            node.setConsecutiveSuccesses(0);
            if (node.getConsecutiveFailures() >= CONSECUTIVE_OUTLIER_LIMIT) {
                // 标记为亚健康并立即触发软隔离
                node.setStatus(NodeHealthSnapshotVO.HealthStatus.LIMPING);
                isolateNode(nodeId);
            }
        } else {
            node.setConsecutiveSuccesses(node.getConsecutiveSuccesses() + 1);
            node.setConsecutiveFailures(0);
        }
    }

    /**
     * 对亚健康节点实施物理软隔离 (Node Fencing)：权重置 0
     */
    public synchronized void isolateNode(String nodeId) {
        NodeHealthSnapshotVO node = nodeHealthMap.get(nodeId);
        if (node != null) {
            node.setStatus(NodeHealthSnapshotVO.HealthStatus.ISOLATED);
            node.setRoutingWeight(0);
            log.warn("节点 {} 已被软隔离！路由权重置 0，杜绝请求积压与线程池雪崩", nodeId);
        }
    }

    /**
     * 针对处于隔离态节点的探活与慢启动自愈恢复
     */
    public synchronized boolean probeAndRecover(String nodeId, double probeLatencyMs) {
        NodeHealthSnapshotVO node = nodeHealthMap.get(nodeId);
        if (node == null || node.getStatus() != NodeHealthSnapshotVO.HealthStatus.ISOLATED) {
            return false;
        }

        if (probeLatencyMs <= clusterBaselineMeanMs + clusterBaselineStdDevMs) {
            node.setConsecutiveSuccesses(node.getConsecutiveSuccesses() + 1);
            if (node.getConsecutiveSuccesses() >= CONSECUTIVE_SUCCESS_LIMIT) {
                // 解除隔离，慢启动恢复权重
                node.setStatus(NodeHealthSnapshotVO.HealthStatus.HEALTHY);
                node.setEwmaLatencyMs(probeLatencyMs);
                node.setZScore(0.0);
                node.setRoutingWeight(100); // 恢复健康
                log.info("节点 {} 经探活检验已恢复稳定，已自愈解除隔离，恢复路由权重 100", nodeId);
                return true;
            } else {
                // 慢启动放水：10 -> 50
                node.setRoutingWeight(Math.min(100, node.getConsecutiveSuccesses() * 30));
            }
        } else {
            node.setConsecutiveSuccesses(0);
        }
        return false;
    }

    public NodeHealthSnapshotVO getNodeHealth(String nodeId) {
        return nodeHealthMap.get(nodeId);
    }

    public void setClusterBaseline(double meanMs, double stdDevMs) {
        this.clusterBaselineMeanMs = meanMs;
        this.clusterBaselineStdDevMs = stdDevMs;
    }
}
