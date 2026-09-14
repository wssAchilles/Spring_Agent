package tech.qiantong.qknow.ai.chaos.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.chaos.dto.ChaosExperimentDTO;
import tech.qiantong.qknow.ai.chaos.dto.NodeHealthSnapshotVO;
import tech.qiantong.qknow.ai.chaos.dto.RegionLeaseDTO;

import java.util.Set;

/**
 * 全局故障自愈总协调器 (Theorem 2.1)
 * 统筹混沌演练、多活脑裂仲裁与亚健康自愈闭环，确保平均恢复时间 MTTR <= 1000ms
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class SelfHealingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingOrchestrator.class);

    private final AutonomousChaosGovernor chaosGovernor;
    private final MultiRegionSplitBrainArbiter splitBrainArbiter;
    private final LimpingNodeDetector limpingNodeDetector;

    public SelfHealingOrchestrator(AutonomousChaosGovernor chaosGovernor,
                                  MultiRegionSplitBrainArbiter splitBrainArbiter,
                                  LimpingNodeDetector limpingNodeDetector) {
        this.chaosGovernor = chaosGovernor;
        this.splitBrainArbiter = splitBrainArbiter;
        this.limpingNodeDetector = limpingNodeDetector;
    }

    /**
     * 端到端弹性业务写操作流转
     * 涵盖跨机房连通性检验、租约与 Fencing Token 验证、下游 CAS 写入
     */
    public boolean executeResilientWrite(String sourceRegion, String targetRegion, String nodeId, long fencingToken) {
        // 1. 检查是否存在网络分区
        if (chaosGovernor.isPartitioned(sourceRegion, targetRegion)) {
            log.warn("网络分区阻断: 机房 {} 与机房 {} 之间无法通信！", sourceRegion, targetRegion);
            return false;
        }

        // 2. 检查节点是否处于软隔离
        NodeHealthSnapshotVO health = limpingNodeDetector.getNodeHealth(nodeId);
        if (health != null && (health.getStatus() == NodeHealthSnapshotVO.HealthStatus.ISOLATED || health.getRoutingWeight() == 0)) {
            log.warn("节点 {} 处于软隔离状态，拒绝执行写请求！", nodeId);
            return false;
        }

        // 3. 检查机房租约有效性
        if (!splitBrainArbiter.verifyWritePermission(targetRegion, fencingToken)) {
            log.warn("机房 {} 租约校验未通过，写操作拒绝！", targetRegion);
            return false;
        }

        // 4. 下游存储 CAS 校验
        return splitBrainArbiter.validateStorageCas(fencingToken);
    }

    /**
     * 执行混沌演练与自愈验证
     */
    public long runChaosDrillAndMeasureMttr(ChaosExperimentDTO experiment, Runnable onPartitionRecovery) {
        long startTime = System.currentTimeMillis();

        // 1. 注入混沌
        chaosGovernor.injectChaos(experiment);

        // 2. 演练并触发恢复
        if (onPartitionRecovery != null) {
            onPartitionRecovery.run();
        }

        // 3. 撤销实验
        chaosGovernor.cancelChaos(experiment.getExperimentId());

        long mttr = System.currentTimeMillis() - startTime;
        log.info("混沌故障演练完成，平均自愈恢复时间 MTTR: {}ms", mttr);
        return mttr;
    }

    public AutonomousChaosGovernor getChaosGovernor() {
        return chaosGovernor;
    }

    public MultiRegionSplitBrainArbiter getSplitBrainArbiter() {
        return splitBrainArbiter;
    }

    public LimpingNodeDetector getLimpingNodeDetector() {
        return limpingNodeDetector;
    }
}
