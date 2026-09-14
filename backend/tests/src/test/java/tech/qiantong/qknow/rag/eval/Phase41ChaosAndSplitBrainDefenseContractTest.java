package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.chaos.dto.ChaosExperimentDTO;
import tech.qiantong.qknow.ai.chaos.dto.NodeHealthSnapshotVO;
import tech.qiantong.qknow.ai.chaos.dto.RegionLeaseDTO;
import tech.qiantong.qknow.ai.chaos.engine.AutonomousChaosGovernor;
import tech.qiantong.qknow.ai.chaos.engine.LimpingNodeDetector;
import tech.qiantong.qknow.ai.chaos.engine.MultiRegionSplitBrainArbiter;
import tech.qiantong.qknow.ai.chaos.engine.SelfHealingOrchestrator;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 41 核心契约测试：全局混沌工程自治、故障自愈与多活机房裂脑防御
 * 严格覆盖 10 项严苛契约（定理 1.1, 定理 2.1, 定理 3.1 与 3 大生产级事故场景）
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class Phase41ChaosAndSplitBrainDefenseContractTest {

    private AutonomousChaosGovernor chaosGovernor;
    private MultiRegionSplitBrainArbiter splitBrainArbiter;
    private LimpingNodeDetector limpingDetector;
    private SelfHealingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        chaosGovernor = new AutonomousChaosGovernor();
        // 模拟 5 节点多活集群（Quorum 阈值为 3）
        splitBrainArbiter = new MultiRegionSplitBrainArbiter(5);
        limpingDetector = new LimpingNodeDetector();
        orchestrator = new SelfHealingOrchestrator(chaosGovernor, splitBrainArbiter, limpingDetector);

        // 注册 5 个集群节点
        limpingDetector.registerNode("node-1", "region-east");
        limpingDetector.registerNode("node-2", "region-east");
        limpingDetector.registerNode("node-3", "region-east");
        limpingDetector.registerNode("node-4", "region-south");
        limpingDetector.registerNode("node-5", "region-south");
    }

    @Test
    @DisplayName("契约 01: 多活机房多数派 Quorum 租约裁决与主节点选举契约 (定理 1.1)")
    void contract01_quorumLeaseArbitration_requiresMajorityVotes() {
        assertEquals(3, splitBrainArbiter.calculateQuorumThreshold());

        // 场景 A: 获得 2 票赞成（未达多数派 3 票），当选失败
        Set<String> minorityVotes = Set.of("node-4", "node-5");
        RegionLeaseDTO failedLease = splitBrainArbiter.requestLease("region-south", "node-4", minorityVotes, 3000);
        assertFalse(failedLease.isValid(), "未获法定多数赞同的节点不能持有有效租约");
        assertTrue(failedLease.isReadOnly(), "失败机房必须处于只读模式");

        // 场景 B: 获得 3 票赞成（达到多数派），成功当选并获得递增 Fencing Token
        Set<String> majorityVotes = Set.of("node-1", "node-2", "node-3");
        RegionLeaseDTO successLease = splitBrainArbiter.requestLease("region-east", "node-1", majorityVotes, 3000);
        assertTrue(successLease.isValid(), "达到多数派赞同的节点应成功获得有效租约");
        assertTrue(successLease.getFencingToken() > 100, "Fencing Token 必须单调递增");
        assertEquals("node-1", successLease.getLeaderNodeId());
    }

    @Test
    @DisplayName("契约 02: 跨机房网络完全分区模拟与少数派机房自动降级只读契约 (定理 1.1)")
    void contract02_networkPartition_minorityRegionStepsDownToReadOnly() {
        // 主机房取得租约
        Set<String> majorityVotes = Set.of("node-1", "node-2", "node-3");
        RegionLeaseDTO eastLease = splitBrainArbiter.requestLease("region-east", "node-1", majorityVotes, 3000);
        assertTrue(eastLease.isValid());

        // 模拟网络分区：region-south 失去专线连接并企图自行写数据
        splitBrainArbiter.stepDown("region-south");

        // 校验写权限：south 机房无有效租约，写操作被 100% 阻断
        assertFalse(splitBrainArbiter.verifyWritePermission("region-south", 999L));

        // east 机房持有合法 Lease，写操作正常放行
        assertTrue(splitBrainArbiter.verifyWritePermission("region-east", eastLease.getFencingToken()));
    }

    @Test
    @DisplayName("契约 03: 分布式单调递增 Fencing Token 屏障防双写与过期写入拦截契约")
    void contract03_fencingToken_blocksStaleWritesViaStorageCas() {
        // 初始写入版本 101
        assertTrue(splitBrainArbiter.validateStorageCas(101L));
        assertEquals(101L, splitBrainArbiter.getStorageCommittedToken());

        // 新 Leader 提交版本 102 成功
        assertTrue(splitBrainArbiter.validateStorageCas(102L));
        assertEquals(102L, splitBrainArbiter.getStorageCommittedToken());

        // 脑裂旧 Leader（经历长 GC 苏醒）企图用过期 Token 101 再次写入，CAS 必须绝对拒绝
        boolean staleWriteAllowed = splitBrainArbiter.validateStorageCas(101L);
        assertFalse(staleWriteAllowed, "存储层必须原子拒绝携带旧 Fencing Token 的过期写操作");
        assertEquals(102L, splitBrainArbiter.getStorageCommittedToken(), "存储层提交版本不得倒退");
    }

    @Test
    @DisplayName("契约 04: 跨机房专线单向连通（Asymmetric Partition）偏序仲裁契约")
    void contract04_asymmetricPartition_maintainsSingleGlobalLeader() {
        // 注入单向不对称断开：east 发往 south 断开，south 发往 east 正常
        ChaosExperimentDTO asymmetricExp = new ChaosExperimentDTO(
                "asym-01",
                ChaosExperimentDTO.ExperimentType.ASYMMETRIC_PARTITION,
                "region-east",
                null,
                0,
                0.0,
                5000
        );
        chaosGovernor.injectChaos(asymmetricExp);

        // 连通性测试
        assertTrue(chaosGovernor.isPartitioned("region-east", "region-south"), "单向断开下 east 到 south 判定为不可达");
        assertFalse(chaosGovernor.isPartitioned("region-south", "region-east"), "单向断开下 south 到 east 保持连通");

        // 少数派 south 仍无法凑齐 3 票独立选主
        Set<String> southVotes = Set.of("node-4", "node-5");
        RegionLeaseDTO lease = splitBrainArbiter.requestLease("region-south", "node-4", southVotes, 3000);
        assertFalse(lease.isValid());
    }

    @Test
    @DisplayName("契约 05: 亚健康节点（Limping Node）EWMA 延迟离群与自适应方差检出契约 (定理 2.1)")
    void contract05_limpingNode_detectedByAdaptiveEwmaVariance() {
        limpingDetector.setClusterBaseline(15.0, 5.0);

        // 模拟正常连续请求耗时 (~15ms)
        limpingDetector.recordLatency("node-1", 16.0);
        limpingDetector.recordLatency("node-1", 14.0);
        assertEquals(NodeHealthSnapshotVO.HealthStatus.HEALTHY, limpingDetector.getNodeHealth("node-1").getStatus());

        // 模拟节点 1 突发软死，延迟飙升至 500ms（连续 3 次偏离基线 3-Sigma）
        limpingDetector.recordLatency("node-1", 500.0);
        limpingDetector.recordLatency("node-1", 520.0);
        limpingDetector.recordLatency("node-1", 510.0);

        NodeHealthSnapshotVO health = limpingDetector.getNodeHealth("node-1");
        assertTrue(health.getZScore() > 3.0, "Z-Score 必须显著大于 3.0");
        // 自动判定并流转至 ISOLATED 状态
        assertEquals(NodeHealthSnapshotVO.HealthStatus.ISOLATED, health.getStatus(), "连续严重离群必须被判定并隔离");
        assertEquals(0, health.getRoutingWeight(), "隔离节点路由权重必须为 0");
    }

    @Test
    @DisplayName("契约 06: 亚健康节点毫秒级路由隔离与流量零分发契约")
    void contract06_limpingNode_isolatedWithZeroRoutingWeight() {
        limpingDetector.isolateNode("node-2");

        NodeHealthSnapshotVO node2 = limpingDetector.getNodeHealth("node-2");
        assertEquals(NodeHealthSnapshotVO.HealthStatus.ISOLATED, node2.getStatus());
        assertEquals(0, node2.getRoutingWeight());

        // 尝试通过 orchestrator 向隔离节点派发写请求，被直接阻断
        boolean writeSuccess = orchestrator.executeResilientWrite("region-east", "region-east", "node-2", 100L);
        assertFalse(writeSuccess, "对已隔离节点的写请求应被坚决阻断");
    }

    @Test
    @DisplayName("契约 07: 隔离节点连续探活恢复与慢启动（Slow Start）自愈解除契约")
    void contract07_isolatedNode_slowStartSelfHealingRecovery() {
        limpingDetector.isolateNode("node-3");
        assertEquals(NodeHealthSnapshotVO.HealthStatus.ISOLATED, limpingDetector.getNodeHealth("node-3").getStatus());

        // 探活第 1 次正常 (16ms) -> 权重慢启动放水至 30
        limpingDetector.probeAndRecover("node-3", 16.0);
        assertEquals(NodeHealthSnapshotVO.HealthStatus.ISOLATED, limpingDetector.getNodeHealth("node-3").getStatus());
        assertEquals(30, limpingDetector.getNodeHealth("node-3").getRoutingWeight());

        // 探活第 2 次正常 (15ms) -> 权重提升至 60
        limpingDetector.probeAndRecover("node-3", 15.0);
        assertEquals(60, limpingDetector.getNodeHealth("node-3").getRoutingWeight());

        // 探活第 3 次正常 (14ms) -> 彻底自愈复位至 HEALTHY，权重恢复 100
        boolean recovered = limpingDetector.probeAndRecover("node-3", 14.0);
        assertTrue(recovered, "连续 3 次健康探活后应成功自愈解除隔离");
        assertEquals(NodeHealthSnapshotVO.HealthStatus.HEALTHY, limpingDetector.getNodeHealth("node-3").getStatus());
        assertEquals(100, limpingDetector.getNodeHealth("node-3").getRoutingWeight());
    }

    @Test
    @DisplayName("契约 08: 受控混沌注入器网络延迟与丢包模拟契约")
    void contract08_controlledChaos_latencyAndPacketLossInjection() {
        // 注入 120ms 网络延迟
        ChaosExperimentDTO latencyExp = new ChaosExperimentDTO(
                "lat-01", ChaosExperimentDTO.ExperimentType.NETWORK_LATENCY, "region-east", "node-1", 120, 0.0, 5000
        );
        chaosGovernor.injectChaos(latencyExp);

        assertEquals(120L, chaosGovernor.getSimulatedLatencyMs("region-east", "node-1"));
        assertEquals(0L, chaosGovernor.getSimulatedLatencyMs("region-south", "node-4"), "未受影响节点延迟应为 0");

        // 注入 100% 丢包率
        ChaosExperimentDTO dropExp = new ChaosExperimentDTO(
                "drop-01", ChaosExperimentDTO.ExperimentType.PACKET_LOSS, "region-east", "node-2", 0, 1.0, 5000
        );
        chaosGovernor.injectChaos(dropExp);

        assertTrue(chaosGovernor.shouldDropPacket("region-east", "node-2"));
        assertFalse(chaosGovernor.shouldDropPacket("region-south", "node-5"), "未受影响节点丢包判定应为 false");
    }

    @Test
    @DisplayName("契约 09: 混沌实验全局爆炸半径超限触发 Emergency Kill-Switch 瞬时止血契约")
    void contract09_emergencyKillSwitch_clearsAllChaosWithin50ms() {
        // 注入多个实验
        chaosGovernor.injectChaos(new ChaosExperimentDTO("exp-1", ChaosExperimentDTO.ExperimentType.NETWORK_LATENCY, "region-east", null, 200, 0.0, 10000));
        chaosGovernor.injectChaos(new ChaosExperimentDTO("exp-2", ChaosExperimentDTO.ExperimentType.PACKET_LOSS, "region-south", null, 0, 0.5, 10000));
        assertTrue(chaosGovernor.getActiveExperimentCount() >= 2);

        // 触发紧急止血
        long start = System.currentTimeMillis();
        chaosGovernor.triggerEmergencyKillSwitch();
        long cost = System.currentTimeMillis() - start;

        assertTrue(cost <= 50, "Emergency Kill-Switch 必须在 50ms 内完成瞬时止血");
        assertEquals(0, chaosGovernor.getActiveExperimentCount(), "所有实验必须被强制清空");
        assertTrue(chaosGovernor.isEmergencyKilled());

        // 止血后所有延迟与丢包恢复为 0
        assertEquals(0L, chaosGovernor.getSimulatedLatencyMs("region-east", "node-1"));
        assertFalse(chaosGovernor.shouldDropPacket("region-south", "node-4"));
    }

    @Test
    @DisplayName("契约 10: 端到端多活容灾混沌演练全链路自愈闭环契约 (定理 2.1 MTTR <= 1000ms)")
    void contract10_endToEndMultiRegionChaosResilienceLoop() {
        // 1. 正常状态：主机房 east 获得 Quorum 租约 (3 票)
        Set<String> voters = Set.of("node-1", "node-2", "node-3");
        RegionLeaseDTO lease = splitBrainArbiter.requestLease("region-east", "node-1", voters, 3000);
        long token = lease.getFencingToken();

        // 2. 正常写成功
        assertTrue(orchestrator.executeResilientWrite("region-east", "region-east", "node-1", token));

        // 3. 混沌注入演练：注入全机房跨域网络分区
        ChaosExperimentDTO partitionExp = new ChaosExperimentDTO(
                "drill-partition", ChaosExperimentDTO.ExperimentType.NETWORK_PARTITION, "region-east", null, 0, 0.0, 5000
        );

        long mttr = orchestrator.runChaosDrillAndMeasureMttr(partitionExp, () -> {
            // 分区期间尝试跨机房写入，被成功阻断（零脑裂）
            assertFalse(orchestrator.executeResilientWrite("region-east", "region-south", "node-4", token));
        });

        // 4. 验证自愈恢复时间 MTTR <= 1000ms (定理 2.1)
        assertTrue(mttr <= 1000, "混沌故障注入到自愈完成的时间必须严格满足 MTTR <= 1000ms");

        // 5. 演练结束后分区自动恢复，正常机房写入恢复通行
        long nextToken = token + 1;
        splitBrainArbiter.requestLease("region-east", "node-1", voters, 3000);
        assertTrue(orchestrator.executeResilientWrite("region-east", "region-east", "node-1", nextToken));
    }
}
