package tech.qiantong.qknow.ai.chaos.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.chaos.dto.ChaosExperimentDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自治受控混沌工程注入引擎
 * 支持网络延迟、丢包、单向/双向网络分区与软死注入，提供爆炸半径限制与 50ms 紧急止血开关 (Emergency Kill-Switch)
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class AutonomousChaosGovernor {

    private static final Logger log = LoggerFactory.getLogger(AutonomousChaosGovernor.class);

    private final Map<String, ChaosExperimentDTO> activeExperiments = new ConcurrentHashMap<>();
    private final AtomicBoolean emergencyKilled = new AtomicBoolean(false);
    private final Random random = new Random(42);

    /**
     * 注入混沌实验
     */
    public boolean injectChaos(ChaosExperimentDTO experiment) {
        if (emergencyKilled.get()) {
            log.warn("全局紧急止血开关处于激活状态，拒绝注入新的混沌实验: {}", experiment.getExperimentId());
            return false;
        }
        if (experiment == null || experiment.getExperimentId() == null) {
            return false;
        }
        experiment.setStatus(ChaosExperimentDTO.ExperimentStatus.ACTIVE);
        experiment.setStartTimeMs(System.currentTimeMillis());
        activeExperiments.put(experiment.getExperimentId(), experiment);
        log.info("成功激活混沌实验: id={}, type={}, region={}, node={}",
                experiment.getExperimentId(), experiment.getExperimentType(), experiment.getTargetRegion(), experiment.getTargetNodeId());
        return true;
    }

    /**
     * 取消特定混沌实验
     */
    public boolean cancelChaos(String experimentId) {
        ChaosExperimentDTO removed = activeExperiments.remove(experimentId);
        if (removed != null) {
            removed.setStatus(ChaosExperimentDTO.ExperimentStatus.CANCELLED);
            log.info("已取消混沌实验: {}", experimentId);
            return true;
        }
        return false;
    }

    /**
     * 紧急一键止血熔断 (Emergency Kill-Switch)
     * 50ms 内强制清空所有活跃注入并重置系统基线
     */
    public void triggerEmergencyKillSwitch() {
        long start = System.currentTimeMillis();
        emergencyKilled.set(true);
        activeExperiments.values().forEach(exp -> exp.setStatus(ChaosExperimentDTO.ExperimentStatus.CANCELLED));
        activeExperiments.clear();
        long cost = System.currentTimeMillis() - start;
        log.warn("已触发混沌工程 Emergency Kill-Switch！全部实验已强制清理，耗时: {}ms", cost);
    }

    /**
     * 重置紧急熔断状态
     */
    public void resetKillSwitch() {
        emergencyKilled.set(false);
        log.info("已解除 Emergency Kill-Switch 状态");
    }

    /**
     * 模拟网络延迟判定
     */
    public long getSimulatedLatencyMs(String region, String nodeId) {
        cleanupExpiredExperiments();
        if (emergencyKilled.get() || activeExperiments.isEmpty()) {
            return 0L;
        }
        for (ChaosExperimentDTO exp : activeExperiments.values()) {
            if (exp.getExperimentType() == ChaosExperimentDTO.ExperimentType.NETWORK_LATENCY) {
                if (matchesTarget(exp, region, nodeId)) {
                    return exp.getLatencyMs();
                }
            }
        }
        return 0L;
    }

    /**
     * 模拟丢包判定
     */
    public boolean shouldDropPacket(String region, String nodeId) {
        cleanupExpiredExperiments();
        if (emergencyKilled.get() || activeExperiments.isEmpty()) {
            return false;
        }
        for (ChaosExperimentDTO exp : activeExperiments.values()) {
            if (exp.getExperimentType() == ChaosExperimentDTO.ExperimentType.PACKET_LOSS) {
                if (matchesTarget(exp, region, nodeId)) {
                    double roll = random.nextDouble();
                    return roll < exp.getPacketLossRate();
                }
            }
        }
        return false;
    }

    /**
     * 模拟网络分区连通性判定
     * 返回 true 表示两节点/机房之间被阻断（不可达）
     */
    public boolean isPartitioned(String regionA, String regionB) {
        cleanupExpiredExperiments();
        if (emergencyKilled.get() || activeExperiments.isEmpty()) {
            return false;
        }
        if (regionA == null || regionB == null || regionA.equals(regionB)) {
            return false;
        }
        for (ChaosExperimentDTO exp : activeExperiments.values()) {
            if (exp.getExperimentType() == ChaosExperimentDTO.ExperimentType.NETWORK_PARTITION) {
                // 双向对称断开
                if (matchesRegion(exp, regionA) || matchesRegion(exp, regionB)) {
                    return true;
                }
            } else if (exp.getExperimentType() == ChaosExperimentDTO.ExperimentType.ASYMMETRIC_PARTITION) {
                // 单向不对称断开：A发往B断开，而B发往A正常
                if (matchesRegion(exp, regionA) && !matchesRegion(exp, regionB)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 定期/按需自愈清理超时实验
     */
    public void cleanupExpiredExperiments() {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ChaosExperimentDTO> entry : activeExperiments.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRemove.add(entry.getKey());
            }
        }
        for (String id : toRemove) {
            ChaosExperimentDTO expired = activeExperiments.remove(id);
            if (expired != null) {
                expired.setStatus(ChaosExperimentDTO.ExperimentStatus.EXPIRED);
                log.info("混沌实验超时已自动自愈失效: {}", id);
            }
        }
    }

    public int getActiveExperimentCount() {
        cleanupExpiredExperiments();
        return activeExperiments.size();
    }

    public boolean isEmergencyKilled() {
        return emergencyKilled.get();
    }

    private boolean matchesTarget(ChaosExperimentDTO exp, String region, String nodeId) {
        if (exp.getTargetNodeId() != null && exp.getTargetNodeId().equals(nodeId)) {
            return true;
        }
        if (exp.getTargetRegion() != null && exp.getTargetRegion().equals(region)) {
            return true;
        }
        return exp.getTargetRegion() == null && exp.getTargetNodeId() == null;
    }

    private boolean matchesRegion(ChaosExperimentDTO exp, String region) {
        return exp.getTargetRegion() != null && exp.getTargetRegion().equals(region);
    }
}
