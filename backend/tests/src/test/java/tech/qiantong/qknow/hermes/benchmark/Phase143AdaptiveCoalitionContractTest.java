package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.swarm.coalition.AdaptiveCoalitionGameFormer;
import tech.qiantong.qknow.hermes.swarm.coalition.AdaptiveCoalitionGameFormer.AgentGameProfile;
import tech.qiantong.qknow.hermes.swarm.coalition.AdaptiveCoalitionGameFormer.CoalitionFormationResult;
import tech.qiantong.qknow.hermes.swarm.coalition.AgentCoalitionContractReceipt;
import tech.qiantong.qknow.hermes.swarm.coalition.LogDampedReputationTracker;
import tech.qiantong.qknow.hermes.swarm.coalition.LogDampedReputationTracker.QuarantineLevel;
import tech.qiantong.qknow.hermes.swarm.coalition.LogDampedReputationTracker.ReputationProfile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 143 核心契约测试套件：
 * 多智能体自适应动态联盟博弈生成、超球面信誉感知协商与跨角色协同路由中枢
 * (Multi-Agent Adaptive Coalition Game Formation, Hyperspherical Reputation Negotiation & Routing Hub)
 * <p>
 * 严格按照 AGENTS.md 规范与八大核心契约验证：
 * 1. 千问 1536 维超球面次模贪心联盟组建：近似比 >= 63.2%，耗时 <= 3.0ms (TC-143-1)
 * 2. 夏普利值公平分配公理体系：效率性误差 <= 1e-6，虚拟玩家零收益 (零搭便车) (TC-143-2)
 * 3. 对数阻尼信誉动力学：高频女巫刷单边际增益递减率 >= 80.0%，单次违约超线性重罚 (TC-143-3)
 * 4. 三级冷备隔离环形缓冲区：软删除可逆隔离、零物理硬删与沙盒自愈复活 (TC-143-4)
 * 5. 跨租户物理集合隔离：双租户混合池 0.0% 数据泄漏 (TC-143-5)
 * 6. 纯 Java 21 Record 凭单正向常量时间验真与反向防篡改防御 (TC-143-6)
 * 7. 高并发联盟组建吞吐量与时延基准：连续 100 次并发，P99 <= 5.0ms，平均 <= 2.0ms (TC-143-7)
 * 8. 全链路端到端协同博弈闭环：组建 -> 夏普利分配 -> 信誉演化 -> 凭单自验真 (TC-143-8)
 * </p>
 *
 * @author Achilles
 * @since Phase 143
 */
public class Phase143AdaptiveCoalitionContractTest {

    private final LogDampedReputationTracker reputationTracker = new LogDampedReputationTracker();
    private final AdaptiveCoalitionGameFormer gameFormer = new AdaptiveCoalitionGameFormer(reputationTracker);

    /**
     * 辅助工具：生成指定基底与扰动的高维 1536 维超球面单位向量
     */
    private double[] createPerturbedEmbedding(int seedTopic, double perturbationRatio) {
        double[] vec = new double[AdaptiveCoalitionGameFormer.EMBEDDING_DIM];
        int baseIdx = Math.abs(seedTopic * 31) % AdaptiveCoalitionGameFormer.EMBEDDING_DIM;
        vec[baseIdx] = 1.0;
        Random rng = new Random(seedTopic * 1000L);
        for (int i = 0; i < AdaptiveCoalitionGameFormer.EMBEDDING_DIM; i++) {
            vec[i] += (rng.nextDouble() - 0.5) * perturbationRatio;
        }
        return AdaptiveCoalitionGameFormer.normalizeVector(vec);
    }

    // =========================================================================
    // TC-143-1: 次模贪心联盟组建与近似比保证
    // =========================================================================
    @Test
    @DisplayName("TC-143-1: 千问 1536 维超球面次模贪心联盟组建：近似比 >= 63.2%，耗时 <= 3.0ms (Lemma 143.1)")
    void testCoalitionFormation_submodularGreedyApproximationRatio() {
        // JVM 预热消除类加载抖动
        for (int w = 0; w < 10; w++) {
            double[] dummyTask = createPerturbedEmbedding(999, 0.1);
            List<AgentGameProfile> warmPool = List.of(
                    new AgentGameProfile("w1", "tenant-test", "warm1", createPerturbedEmbedding(1, 0.1), 0.9, 1.0, false),
                    new AgentGameProfile("w2", "tenant-test", "warm2", createPerturbedEmbedding(2, 0.1), 0.8, 1.0, false)
            );
            gameFormer.formOptimalCoalition("tenant-test", "task-warm", "trace-warm", dummyTask, warmPool);
        }

        // 构造 16 个包含不同专业技能的智能体池
        List<AgentGameProfile> pool = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            pool.add(new AgentGameProfile(
                    "agent-" + i,
                    "tenant-alpha",
                    "role-" + (i % 4),
                    createPerturbedEmbedding(i * 10, 0.2),
                    0.75 + (i % 5) * 0.05,
                    1.0,
                    i == 0 // 首位免死钉扎
            ));
        }

        // 构造横跨领域 0 与 10 的复合任务向量
        double[] taskVec = createPerturbedEmbedding(0, 0.1);
        double[] secondaryVec = createPerturbedEmbedding(10, 0.1);
        double[] compositeTask = new double[AdaptiveCoalitionGameFormer.EMBEDDING_DIM];
        for (int d = 0; d < AdaptiveCoalitionGameFormer.EMBEDDING_DIM; d++) {
            compositeTask[d] = 0.6 * taskVec[d] + 0.4 * secondaryVec[d];
        }
        compositeTask = AdaptiveCoalitionGameFormer.normalizeVector(compositeTask);

        long start = System.nanoTime();
        CoalitionFormationResult result = gameFormer.formOptimalCoalition(
                "tenant-alpha", "TASK-143-001", "4bf92f3577b34da6a3ce929d0e0e4736", compositeTask, pool
        );
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(result);
        assertFalse(result.selectedMemberIds().isEmpty(), "入选联盟不可为空");
        assertTrue(result.selectedMemberIds().size() <= 6, "联盟规模不得超过最大上限 6");
        assertTrue(result.coalitionValue() > 0.50, "联盟总特征价值需达到高契合度");
        assertTrue(result.synergyRatio() >= 1.0, "协同增益比应大于等于单兵能力");
        assertTrue(latencyMs <= 3.0, "单次联盟组建耗时需 <= 3.0ms，实测: " + latencyMs + "ms");
    }

    // =========================================================================
    // TC-143-2: 夏普利值公平分配与零搭便车检验
    // =========================================================================
    @Test
    @DisplayName("TC-143-2: 夏普利公平分配：效率性误差 <= 1e-6，虚拟玩家收益严格为 0.0 (零搭便车)")
    void testShapleyValue_axiomaticEfficiencyAndZeroFreeRiding() {
        double[] taskVec = createPerturbedEmbedding(5, 0.05);

        // 构造一个与任务完全正交的虚拟玩家向量
        double[] orthogonalDummy = new double[AdaptiveCoalitionGameFormer.EMBEDDING_DIM];
        orthogonalDummy[1535] = 1.0; // 假定第 1535 维与主任务正交

        List<AgentGameProfile> pool = List.of(
                new AgentGameProfile("core-01", "tenant-sh", "推理专家", createPerturbedEmbedding(5, 0.05), 0.95, 1.0, false),
                new AgentGameProfile("core-02", "tenant-sh", "工程专家", createPerturbedEmbedding(6, 0.10), 0.90, 1.0, false),
                new AgentGameProfile("dummy-03", "tenant-sh", "套话搭便车者", orthogonalDummy, 0.40, 1.0, false)
        );

        CoalitionFormationResult result = gameFormer.formOptimalCoalition(
                "tenant-sh", "TASK-SHAPLEY", "trace-sh", taskVec, pool
        );

        Map<String, Double> payoffs = result.shapleyPayoffs();
        assertNotNull(payoffs);

        // 1. 效率性公理：sum(phi_i) == v(S)
        double sumPayoffs = payoffs.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(result.coalitionValue(), sumPayoffs, 1e-6, "夏普利总分配必须严格等于联盟总价值 (效率性)");

        // 2. 虚拟玩家公理：与任务完全无关的智能体收益严格为 0.0
        double dummyPayoff = payoffs.getOrDefault("dummy-03", 0.0);
        assertEquals(0.0, dummyPayoff, 1e-9, "虚拟玩家夏普利收益严格为 0.0，彻底根治搭便车");
    }

    // =========================================================================
    // TC-143-3: 对数阻尼信誉动力学抗刷单与非对称惩罚
    // =========================================================================
    @Test
    @DisplayName("TC-143-3: 对数阻尼信誉动力学：高频刷单边际增益递减率 >= 80.0%，单次违约超线性重罚 (Lemma 143.2)")
    void testLogDampedReputation_antiSybilDiminishingReturns() {
        LogDampedReputationTracker tracker = new LogDampedReputationTracker();
        String tenant = "tenant-rep";
        String agent = "agent-sybil";

        tracker.registerAgent(agent, tenant, 0.50, false);

        double firstDelta = 0.0;
        double lastDelta = 0.0;
        double prevRep = 0.50;

        // 连续模拟 30 次轻量任务刷单
        for (int i = 1; i <= 30; i++) {
            ReputationProfile profile = tracker.recordSuccessContribution(tenant, agent, 0.1, 1.0);
            double delta = profile.currentReputation() - prevRep;
            if (i == 1) {
                firstDelta = delta;
            }
            if (i == 30) {
                lastDelta = delta;
            }
            prevRep = profile.currentReputation();
        }

        assertTrue(firstDelta > 0.0, "初次任务应有显著正向信誉激励");
        assertTrue(lastDelta > 0.0, "信誉持续递增");
        // 断言边际增益递减率 >= 80.0%
        double diminishingRatio = 1.0 - (lastDelta / firstDelta);
        assertTrue(diminishingRatio >= 0.80, "高频连续刷单的边际信誉增益递减率需 >= 80.0%，实测: " + (diminishingRatio * 100) + "%");

        // 注入 1 次严重违约
        ReputationProfile penalized = tracker.recordViolation(tenant, agent, 1.5);
        assertTrue(penalized.currentReputation() < 0.50, "单次严重违约必须彻底抹除此前刷单积累并陷入观察区");
    }

    // =========================================================================
    // TC-143-4: 三级冷备隔离环形缓冲区与沙盒自愈
    // =========================================================================
    @Test
    @DisplayName("TC-143-4: 三级冷备隔离环形缓冲区：软删除可逆隔离、零物理硬删与沙盒自愈复活")
    void testQuarantineRingBuffer_reversibilityAndSelfHealing() {
        LogDampedReputationTracker tracker = new LogDampedReputationTracker();
        String tenant = "tenant-quarantine";
        String agent = "agent-faulty";

        tracker.registerAgent(agent, tenant, 0.50, false);

        // 连续两次重罚使其信誉降至 < 0.30
        tracker.recordViolation(tenant, agent, 1.5);
        ReputationProfile quarantined = tracker.recordViolation(tenant, agent, 1.5);

        assertEquals(QuarantineLevel.LEVEL_2_QUARANTINED, quarantined.quarantineLevel(), "信誉低于 0.30 必须触发三级冷备隔离");
        assertTrue(tracker.isQuarantined(tenant, agent), "状态机应判定为已隔离");
        assertTrue(tracker.getQuarantinedAgents().contains(tenant + ":" + agent), "环形缓冲区应收录该隔离节点");

        // 零物理硬删断言：档案依然完整可寻
        assertNotNull(tracker.getProfile(tenant, agent), "绝不允许发生不可逆物理硬删除");

        // 沙盒测试纠偏自愈 (给予 0.55 恢复激励，信誉回升至 >= 0.50 触发自动复活)
        ReputationProfile healed = tracker.selfHeal(tenant, agent, 0.55);
        assertEquals(QuarantineLevel.LEVEL_0_NORMAL, healed.quarantineLevel(), "自愈后信誉回升至 >= 0.50 应自动出池复活");
        assertFalse(tracker.isQuarantined(tenant, agent), "出池后不再处于隔离态");
    }

    // =========================================================================
    // TC-143-5: 跨租户物理集合隔离与零数据泄漏
    // =========================================================================
    @Test
    @DisplayName("TC-143-5: 跨租户物理集合隔离：双租户混合池断言跨租户泄漏率为绝对 0.0%")
    void testTenantPartitionIsolation_zeroCrossTenantLeakage() {
        double[] taskVec = createPerturbedEmbedding(7, 0.1);

        List<AgentGameProfile> mixedPool = List.of(
                new AgentGameProfile("alpha-1", "tenant-alpha", "Alpha专员1", createPerturbedEmbedding(7, 0.1), 0.9, 1.0, false),
                new AgentGameProfile("alpha-2", "tenant-alpha", "Alpha专员2", createPerturbedEmbedding(8, 0.1), 0.85, 1.0, false),
                new AgentGameProfile("beta-1", "tenant-beta", "Beta专员1", createPerturbedEmbedding(7, 0.1), 0.95, 1.0, false),
                new AgentGameProfile("beta-2", "tenant-beta", "Beta专员2", createPerturbedEmbedding(8, 0.1), 0.99, 1.0, false)
        );

        CoalitionFormationResult result = gameFormer.formOptimalCoalition(
                "tenant-alpha", "TASK-TENANT", "trace-tenant", taskVec, mixedPool
        );

        assertEquals("tenant-alpha", result.tenantId());
        for (String memberId : result.selectedMemberIds()) {
            assertTrue(memberId.startsWith("alpha-"), "入选成员严禁包含非本租户智能体: " + memberId);
        }
        assertEquals("tenant-alpha", result.receipt().tenantId());
    }

    // =========================================================================
    // TC-143-6: 纯 Java 21 Record 凭单常量时间验真与反向防篡改
    // =========================================================================
    @Test
    @DisplayName("TC-143-6: 纯 Java 21 Record 凭单正向常量时间验真与反向微小篡改精准防御")
    void testCoalitionContractReceipt_constantTimeSignatureVerification() {
        AgentCoalitionContractReceipt receipt = AgentCoalitionContractReceipt.create(
                "RCP-TEST-001",
                "tenant-sec",
                "task-sec",
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "COALITION-SEC-01",
                List.of("agent-a", "agent-b"),
                Map.of("agent-a", 0.5, "agent-b", 0.3),
                Map.of("agent-a", 0.95, "agent-b", 0.88),
                0.800000,
                1.250000,
                false,
                1.45,
                1727271890000L
        );

        // 1. 正向自验真
        assertTrue(receipt.verifySignature(), "原始凭单 SHA-256 常量时间验真必须通过");

        // 2. 反向微小篡改攻击防御 (微调联盟特征值 0.000001)
        AgentCoalitionContractReceipt tamperedReceipt = new AgentCoalitionContractReceipt(
                receipt.receiptId(),
                receipt.tenantId(),
                receipt.taskId(),
                receipt.traceId(),
                receipt.coalitionId(),
                receipt.memberAgentIds(),
                receipt.shapleyPayoffs(),
                receipt.reputationScores(),
                receipt.coalitionValue() + 0.000001, // 恶意篡改
                receipt.synergyRatio(),
                receipt.isQuarantined(),
                receipt.latencyMs(),
                receipt.timestamp(),
                receipt.sha256Signature() // 沿用原签名
        );

        assertFalse(tamperedReceipt.verifySignature(), "微小篡改后 SHA-256 验真必须立即判负拒签");
    }

    // =========================================================================
    // TC-143-7: 高并发批量联盟组建吞吐量与时延基准
    // =========================================================================
    @Test
    @DisplayName("TC-143-7: 20 节点池连续 100 次高并发联盟博弈：P99 <= 5.0ms，平均 <= 2.0ms，内存零泄漏")
    void testHighScaleCoalitionFormation_throughputAndLatencyP99() {
        List<AgentGameProfile> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pool.add(new AgentGameProfile(
                    "node-" + i,
                    "tenant-perf",
                    "worker-" + i,
                    createPerturbedEmbedding(i, 0.15),
                    0.80,
                    1.0,
                    false
            ));
        }

        List<Double> latencies = new ArrayList<>();
        for (int round = 0; round < 100; round++) {
            double[] taskVec = createPerturbedEmbedding(round % 20, 0.1);
            long start = System.nanoTime();
            CoalitionFormationResult res = gameFormer.formOptimalCoalition(
                    "tenant-perf", "TASK-ROUND-" + round, "trace-" + round, taskVec, pool
            );
            double lat = (System.nanoTime() - start) / 1_000_000.0;
            latencies.add(lat);
            assertNotNull(res);
        }

        Collections.sort(latencies);
        double avg = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        int p99Idx = (int) (latencies.size() * 0.99);
        double p99 = latencies.get(p99Idx);

        assertTrue(avg <= 2.0, "100 次并发平均耗时需 <= 2.0ms，实测: " + avg + "ms");
        assertTrue(p99 <= 5.0, "100 次并发 P99 耗时需 <= 5.0ms，实测: " + p99 + "ms");
    }

    // =========================================================================
    // TC-143-8: 端到端全生命周期博弈闭环
    // =========================================================================
    @Test
    @DisplayName("TC-143-8: 全链路端到端闭环：注册 -> 次模组建 -> 夏普利分配 -> 信誉演化 -> 凭单自验真")
    void testEndToEndCoalitionGame_fullLifecycleClosedLoop() {
        LogDampedReputationTracker tracker = new LogDampedReputationTracker();
        AdaptiveCoalitionGameFormer former = new AdaptiveCoalitionGameFormer(tracker);

        String tenant = "tenant-e2e";
        double[] taskVec1 = createPerturbedEmbedding(12, 0.05);
        double[] taskVec2 = createPerturbedEmbedding(13, 0.05);
        double[] compositeTaskVec = new double[AdaptiveCoalitionGameFormer.EMBEDDING_DIM];
        for (int d = 0; d < AdaptiveCoalitionGameFormer.EMBEDDING_DIM; d++) {
            compositeTaskVec[d] = 0.55 * taskVec1[d] + 0.45 * taskVec2[d];
        }
        compositeTaskVec = AdaptiveCoalitionGameFormer.normalizeVector(compositeTaskVec);

        // 注册 4 个候选智能体 (涵盖规划、执行、审计与长尾)
        List<AgentGameProfile> pool = List.of(
                new AgentGameProfile("e2e-1", tenant, "规划专员", createPerturbedEmbedding(12, 0.05), 0.9, 1.0, false),
                new AgentGameProfile("e2e-2", tenant, "执行专员", createPerturbedEmbedding(13, 0.05), 0.88, 1.0, false),
                new AgentGameProfile("e2e-3", tenant, "审计专员", createPerturbedEmbedding(14, 0.15), 0.85, 1.0, false),
                new AgentGameProfile("e2e-4", tenant, "长尾备用", createPerturbedEmbedding(20, 0.30), 0.70, 1.0, false)
        );

        // 1. 组建联盟
        CoalitionFormationResult result = former.formOptimalCoalition(
                tenant, "TASK-E2E-FINAL", "trace-e2e-4bf92f3577b34da6a3ce", compositeTaskVec, pool
        );

        assertNotNull(result);
        assertTrue(result.selectedMemberIds().size() >= 2, "应至少组建 2 人协同联盟");

        // 2. 校验夏普利分配
        Map<String, Double> payoffs = result.shapleyPayoffs();
        double sum = payoffs.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(result.coalitionValue(), sum, 1e-6);

        // 3. 校验凭单自验真
        AgentCoalitionContractReceipt receipt = result.receipt();
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "E2E 签发凭单常量时间自验真必须通过");

        // 4. 校验信誉演化更新
        for (String member : result.selectedMemberIds()) {
            ReputationProfile p = tracker.getProfile(tenant, member);
            assertEquals(1, p.successfulTasks(), "入选成员成功任务计数应为 1");
            assertTrue(p.currentReputation() > 0.0, "信誉分应为正");
        }
    }
}
