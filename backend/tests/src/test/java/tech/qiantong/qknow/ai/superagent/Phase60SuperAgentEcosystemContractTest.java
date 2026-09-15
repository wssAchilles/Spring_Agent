package tech.qiantong.qknow.ai.superagent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 60 专属契约测试：企业级自主可进化超级智能体生态总线、自省认知元框架与全局全生命周期主权自治控制台
 */
public class Phase60SuperAgentEcosystemContractTest {

    private MetacognitiveIntrospector introspector;
    private AutonomicEvolutionBus evolutionBus;
    private SovereignGovernanceController governanceController;
    private EvolutionaryFitnessEvaluator fitnessEvaluator;
    private SuperAgentEcosystemCoordinator coordinator;

    @BeforeEach
    void setUp() {
        introspector = new MetacognitiveIntrospector();
        evolutionBus = new AutonomicEvolutionBus();
        governanceController = new SovereignGovernanceController();
        fitnessEvaluator = new EvolutionaryFitnessEvaluator();
        coordinator = new SuperAgentEcosystemCoordinator(
                introspector, evolutionBus, governanceController, fitnessEvaluator
        );
    }

    @Test
    @DisplayName("契约 1: 超级智能体全局主权不可变存证账本 SHA-256 自签名与防篡改测试")
    void testLedgerIntegrityAndSha256Verification() {
        SuperAgentAuditLedger ledger = SuperAgentAuditLedger.create(
                "LEDGER-001", 100L, 0.4567, 10, 1, 0.8850,
                true, false, "治理评估正常，策略通过多目标帕累托门禁获批晋级",
                System.currentTimeMillis()
        );

        assertNotNull(ledger);
        assertTrue(ledger.verifySignature(), "原始不可变存证账本自签名校验必须通过");

        // 篡改账本中关键字段（如将未通过熔断状态篡改为已熔断）
        SuperAgentAuditLedger tampered = new SuperAgentAuditLedger(
                ledger.ledgerId(), ledger.governanceEpoch(), ledger.clusterCognitiveEntropy(),
                ledger.activeAgentCount(), ledger.isolatedAgentCount(),
                0.9999, // 篡改适应度得分
                ledger.promotionApproved(), ledger.emergencyHalted(), ledger.sovereignDecision(),
                ledger.timestamp(), ledger.sha256Signature()
        );
        assertFalse(tampered.verifySignature(), "字段被篡改后的存证账本 SHA-256 验真必须严格失败");
    }

    @Test
    @DisplayName("契约 2: 自省认知元框架集群认知熵量化、临界警戒与自激振荡环路检测测试 (定理 1.1)")
    void testMetacognitiveIntrospectorEntropyCalculationAndOscillationDetection() {
        // 1. 验证空状态熵为 0
        assertEquals(0.0, introspector.calculateClusterCognitiveEntropy(), 1e-6);
        assertFalse(introspector.isEntropyCritical());

        // 2. 注入多智能体认知信念分布
        Map<String, Double> belief1 = Map.of("STATE_EXPLORE", 0.7, "STATE_EXPLOIT", 0.3);
        Map<String, Double> belief2 = Map.of("STATE_EXPLORE", 0.2, "STATE_EXPLOIT", 0.8);
        introspector.recordAgentBelief("AGENT_ALPHA", belief1);
        introspector.recordAgentBelief("AGENT_BETA", belief2);

        double entropy = introspector.calculateClusterCognitiveEntropy();
        assertTrue(entropy > 0.0, "存在多样化信念状态时集群认知熵必须大于 0");
        assertTrue(entropy <= Math.log(2) + 0.05, "双状态香农熵不应超过理论极大值");

        // 3. 模拟并检测跨智能体环形振荡调用 A -> B -> C -> A
        assertFalse(introspector.detectOscillationLoop(), "初始无环调用拓扑不应误报振荡");

        introspector.recordCall("AGENT_A", "AGENT_B");
        introspector.recordCall("AGENT_B", "AGENT_C");
        introspector.recordCall("AGENT_C", "AGENT_A");

        assertTrue(introspector.detectOscillationLoop(), "环形递归调用 (A->B->C->A) 必须被确定性捕获检出");
    }

    @Test
    @DisplayName("契约 3: 四维帕累托多目标进化适应度评估与合规一票否决红线门禁测试 (定理 1.2)")
    void testEvolutionaryFitnessEvaluatorParetoDominance() {
        // 场景 A: 卓越表现但安全合规未达 1.0 -> 触发一票否决
        var resultSafetyFail = fitnessEvaluator.evaluate(
                0.98, // 成功率高
                0.95, // 经济性高
                0.90, // 延迟好
                0.99  // 合规欠缺 0.01
        );
        assertFalse(resultSafetyFail.promotionApproved(), "安全合规 < 1.0 必须触发一票否决禁止晋级");
        assertTrue(resultSafetyFail.overallFitnessScore() <= 0.49, "被一票否决的策略得分必须被截断惩罚");
        assertTrue(resultSafetyFail.decisionSummary().contains("一票否决"));

        // 场景 B: 合规全达标且四维加权优异 -> 获批晋级
        var resultSuccess = fitnessEvaluator.evaluate(
                0.90, // 0.90 * 0.35 = 0.315
                0.85, // 0.85 * 0.25 = 0.2125
                0.80, // 0.80 * 0.20 = 0.160
                1.00  // 1.00 * 0.20 = 0.200 -> Sum = 0.8875 >= 0.80
        );
        assertTrue(resultSuccess.promotionApproved(), "四维帕累托达标且合规满分必须获批晋级");
        assertEquals(0.8875, resultSuccess.overallFitnessScore(), 1e-4);

        // 场景 C: 合规达标但加权得分不足 0.80 -> 拒绝晋级
        var resultScoreLow = fitnessEvaluator.evaluate(
                0.60, 0.60, 0.60, 1.00
        );
        assertFalse(resultScoreLow.promotionApproved(), "综合得分未达门槛 (0.80) 必须拒绝晋级");
    }

    @Test
    @DisplayName("契约 4: 自主进化生态总线有界队列流控、主题订阅与紧急排空测试")
    void testAutonomicEvolutionBusBoundedQueueAndBackpressure() {
        AtomicInteger eventCounter = new AtomicInteger(0);
        evolutionBus.subscribe("POLICY_EVOLVED", event -> eventCounter.incrementAndGet());

        // 发布事件
        boolean sent = evolutionBus.publishEvent("AGENT_LEADER", "POLICY_EVOLVED", Map.of("weights", "v2"));
        assertTrue(sent, "正常容量下事件必须成功发布");
        assertEquals(1, eventCounter.get(), "订阅者必须收到广播事件");
        assertEquals(1, evolutionBus.getQueueSize());

        // 紧急排空与注销
        int drainedCount = evolutionBus.purgeAndDrain();
        assertEquals(1, drainedCount, "必须成功排空积压队列中所有事件");
        assertEquals(0, evolutionBus.getQueueSize());

        // 排空后总线拒绝新事件注入
        boolean rejected = evolutionBus.publishEvent("AGENT_LEADER", "POLICY_EVOLVED", "test");
        assertFalse(rejected, "已排空总线必须拒绝新事件入队");
    }

    @Test
    @DisplayName("契约 5: 全局主权自治控制器违规计数与入狱隔离机制测试 (定理 1.3)")
    void testSovereignGovernanceControllerJailIsolation() {
        governanceController.registerAgent("AGENT_ROGUE");
        assertTrue(governanceController.isAgentAllowed("AGENT_ROGUE"), "正常注册智能体默认处于活跃受信任状态");
        assertEquals(1, governanceController.getActiveAgentCount());
        assertEquals(0, governanceController.getIsolatedAgentCount());

        // 违规两次尚未入狱
        governanceController.reportViolation("AGENT_ROGUE", "轻微超时");
        governanceController.reportViolation("AGENT_ROGUE", "调用格式错误");
        assertTrue(governanceController.isAgentAllowed("AGENT_ROGUE"));

        // 第三次违规达到隔离阈值
        governanceController.reportViolation("AGENT_ROGUE", "严重越权");
        assertFalse(governanceController.isAgentAllowed("AGENT_ROGUE"), "达到违规阈值智能体必须自动被入狱隔离并撤销准入");
        assertEquals(SovereignGovernanceController.AgentState.JAIL_ISOLATED, governanceController.getAgentState("AGENT_ROGUE"));
        assertEquals(0, governanceController.getActiveAgentCount());
        assertEquals(1, governanceController.getIsolatedAgentCount());

        // 解冻恢复
        governanceController.releaseAgent("AGENT_ROGUE");
        assertTrue(governanceController.isAgentAllowed("AGENT_ROGUE"), "解冻后智能体恢复活跃状态并清空违规计数");
        assertEquals(1, governanceController.getActiveAgentCount());
        assertEquals(0, governanceController.getIsolatedAgentCount());
    }

    @Test
    @DisplayName("契约 6: 全局主权自治控制器紧急物理硬断路器 (Kill-Switch) <= 50ms 止血测试")
    void testSovereignGovernanceControllerEmergencyKillSwitch() {
        governanceController.registerAgent("AGENT_1");
        governanceController.registerAgent("AGENT_2");
        assertTrue(governanceController.isAgentAllowed("AGENT_1"));
        assertTrue(governanceController.isAgentAllowed("AGENT_2"));

        long startTime = System.nanoTime();
        boolean triggered = governanceController.triggerEmergencyKillSwitch("检测到不可逆外部入侵");
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(triggered);
        assertTrue(governanceController.isEmergencyHalted());
        assertTrue(durationMs <= 50, "物理硬断路器响应时间必须 <= 50ms (实际: " + durationMs + "ms)");

        // 熔断后所有节点一律不可通行
        assertFalse(governanceController.isAgentAllowed("AGENT_1"));
        assertFalse(governanceController.isAgentAllowed("AGENT_2"));

        // 复位断路器
        governanceController.resetEmergencyKillSwitch();
        assertFalse(governanceController.isEmergencyHalted());
        assertTrue(governanceController.isAgentAllowed("AGENT_1"));
    }

    @Test
    @DisplayName("契约 7: 超级智能体生态端到端治理闭环调度与账本不可变存证签发测试")
    void testEndToEndSuperAgentCoordinationSuccess() {
        List<String> clusterAgents = List.of("AGENT_PLANNER", "AGENT_CODER", "AGENT_TESTER");

        long startNs = System.nanoTime();
        SuperAgentAuditLedger ledger = coordinator.coordinateCycle(
                1L,
                clusterAgents,
                0.92, // 成功率
                0.88, // 经济性
                0.85, // 延迟裕度
                1.00  // 合规满分
        );
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertNotNull(ledger);
        assertTrue(ledger.verifySignature(), "生态协调周期签发的存证账本自签名必须 100% 校验通过");
        assertTrue(ledger.promotionApproved(), "四维达标生态周期必须获批晋级");
        assertFalse(ledger.emergencyHalted());
        assertEquals(3, ledger.activeAgentCount());
        assertEquals(0, ledger.isolatedAgentCount());
        assertTrue(ledger.sovereignDecision().contains("获批晋级"));
        assertTrue(elapsedMs <= 20, "单次治理调度耗时必须在极速毫秒级内完成 (实际: " + elapsedMs + "ms)");
    }

    @Test
    @DisplayName("契约 8: 调度器边界防御与非法参数拒绝测试")
    void testCoordinatorRejectsInvalidInputs() {
        // 非法代际号 <= 0
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.coordinateCycle(0L, List.of("AGENT_A"), 0.9, 0.9, 0.9, 1.0)
        );

        // 活跃智能体列表为 null
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.coordinateCycle(1L, null, 0.9, 0.9, 0.9, 1.0)
        );
    }
}
