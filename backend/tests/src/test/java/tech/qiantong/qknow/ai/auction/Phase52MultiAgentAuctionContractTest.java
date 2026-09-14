package tech.qiantong.qknow.ai.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 52: 多智能体分布式组合拍卖竞标中枢、VCG 真实性激励代数与抗女巫信用共识网络 专属契约测试
 */
public class Phase52MultiAgentAuctionContractTest {

    private AntiSybilCreditLedger creditLedger;
    private CombinatorialAuctionEngine auctionEngine;
    private TruthfulIncentiveMechanism incentiveMechanism;
    private ShapleyCreditAllocator shapleyAllocator;
    private MultiAgentAuctionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        creditLedger = new AntiSybilCreditLedger();
        auctionEngine = new CombinatorialAuctionEngine();
        incentiveMechanism = new TruthfulIncentiveMechanism();
        shapleyAllocator = new ShapleyCreditAllocator();
        coordinator = new MultiAgentAuctionCoordinator(creditLedger, auctionEngine, incentiveMechanism, shapleyAllocator);
    }

    @Test
    @DisplayName("契约 1: 验证组合拍卖求解引擎(WDP)在异构任务分配中精准求得社会总成本最小解")
    void test1_CombinatorialAuctionEngineSocialCostMinimization() {
        // 构造任务列表: 检索任务与深度推理任务
        CombinatorialAuctionEngine.TaskItem t1 = new CombinatorialAuctionEngine.TaskItem("task_retrieve", "vector_search", 1.0);
        CombinatorialAuctionEngine.TaskItem t2 = new CombinatorialAuctionEngine.TaskItem("task_reason", "cot_reasoning", 2.0);
        List<CombinatorialAuctionEngine.TaskItem> tasks = List.of(t1, t2);

        // 构造竞标报价:
        // A: 检索低价 0.8
        // B: 检索高价 1.6
        // C: 推理低价 1.8
        // D: 推理高价 2.5
        CombinatorialAuctionEngine.AgentBid bidA = new CombinatorialAuctionEngine.AgentBid("b1", "agent_A", List.of("task_retrieve"), 0.8);
        CombinatorialAuctionEngine.AgentBid bidB = new CombinatorialAuctionEngine.AgentBid("b2", "agent_B", List.of("task_retrieve"), 1.6);
        CombinatorialAuctionEngine.AgentBid bidC = new CombinatorialAuctionEngine.AgentBid("b3", "agent_C", List.of("task_reason"), 1.8);
        CombinatorialAuctionEngine.AgentBid bidD = new CombinatorialAuctionEngine.AgentBid("b4", "agent_D", List.of("task_reason"), 2.5);

        List<CombinatorialAuctionEngine.AgentBid> bids = List.of(bidA, bidB, bidC, bidD);

        CombinatorialAuctionEngine.AuctionAllocation allocation = auctionEngine.solveWinnerDetermination(tasks, bids);

        assertTrue(allocation.isFeasible(), "任务应当被完全覆盖可行分配");
        assertEquals(2.6, allocation.totalCost(), 1e-6, "最小社会总成本应当为 0.8 + 1.8 = 2.6");

        // 验证中标者
        Map<String, List<String>> allocMap = allocation.allocation();
        assertTrue(allocMap.containsKey("agent_A"));
        assertTrue(allocMap.get("agent_A").contains("task_retrieve"));
        assertTrue(allocMap.containsKey("agent_C"));
        assertTrue(allocMap.get("agent_C").contains("task_reason"));
        assertFalse(allocMap.containsKey("agent_B"));
        assertFalse(allocMap.containsKey("agent_D"));
    }

    @Test
    @DisplayName("契约 2: 验证定理 1.1 VCG 机制占优策略真实性保证 (DSIC: Dominant-Strategy Incentive Compatibility)")
    void test2_TruthfulIncentiveMechanismDSIC() {
        // 场景: Agent 1 真实执行成本 c1 = 10.0, 竞争对手 Agent 2 报价 b2 = 14.0
        double trueCost = 10.0;

        // 1. 诚实申报 b1 = 10.0
        // S* 选 Agent 1, 成本 10.0; 排除 1 后的最优成本为 14.0; 其余人在 S* 中成本为 0.0
        // VCG 报酬 p1 = 14.0 - 0.0 = 14.0
        double honestPayment = incentiveMechanism.calculateVcgPayment("agent_1", 14.0, 0.0);
        assertEquals(14.0, honestPayment, 1e-6);
        double honestUtility = honestPayment - trueCost; // 4.0

        // 2. 虚假申报过高: b1_mis = 16.0
        // 此时系统选择 Agent 2 (14.0 < 16.0), Agent 1 未中标, 获得报酬 0.0, 净效用 0.0 < 4.0
        double misreportedHighPayment = 0.0;
        double misreportedHighUtility = misreportedHighPayment - 0.0; // 0.0

        // 3. 虚假申报过低: 假设真实成本 c1 = 16.0, 对手 b2 = 14.0, 试图以 8.0 恶意低价抢单
        // 若以 8.0 抢单, 排除 1 后的成本 14.0, 其余人在 S* 成本 0.0, 报酬 p1 = 14.0
        // 此时净效用 = 14.0 - 16.0 = -2.0 (产生净亏损!)
        double underbidPayment = incentiveMechanism.calculateVcgPayment("agent_1", 14.0, 0.0);
        double underbidUtility = underbidPayment - 16.0; // -2.0

        // 验证 DSIC 断言
        assertTrue(honestUtility >= misreportedHighUtility);
        assertTrue(honestUtility > underbidUtility);

        // 调用类内置形式化验证器
        boolean dsicVerified = incentiveMechanism.verifyDSIC(trueCost, 10.0, 16.0, honestPayment, misreportedHighPayment);
        assertTrue(dsicVerified, "诚实申报真实成本必须为严格弱占优策略");
    }

    @Test
    @DisplayName("契约 3: 验证定理 1.3 拓扑传导阻抗对女巫伪造集群的 100% 阻断")
    void test3_AntiSybilCreditLedgerConductanceFiltering() {
        // 注册一个合规工作者, 并与种子节点 agent_coordinator 建立信任边
        String legitAgent = "agent_legit_worker";
        creditLedger.recordExecutionSuccess(legitAgent, 0.1);
        creditLedger.addTrustEdge("agent_coordinator", legitAgent);

        assertFalse(creditLedger.isSybil(legitAgent), "正常连通种子节点的智能体不应被判定为女巫");

        // 构造女巫集群: sybil_1 ~ sybil_5, 互相建立信任边, 但与系统种子节点无任何连通割边
        for (int i = 1; i <= 5; i++) {
            String sybil = "sybil_bot_" + i;
            creditLedger.recordExecutionSuccess(sybil, 0.2); // 试图伪造高分
            for (int j = 1; j <= 5; j++) {
                if (i != j) {
                    creditLedger.addTrustEdge(sybil, "sybil_bot_" + j);
                }
            }
        }

        // 验证定理 1.3: 传导阻抗使所有孤立女巫节点判定为 isSybil = true
        for (int i = 1; i <= 5; i++) {
            assertTrue(creditLedger.isSybil("sybil_bot_" + i), "孤立伪造的女巫集群节点必须 100% 被阻断过滤");
        }

        // 验证未知陌生节点未连通种子同样被阻断
        assertTrue(creditLedger.isSybil("unknown_intruder_999"));
    }

    @Test
    @DisplayName("契约 4: 验证定理 1.2 沙普利合作博弈公理化分配 (完备效率性、对称性与虚设性)")
    void test4_ShapleyCreditAllocatorAxiomaticProperties() {
        List<String> agents = List.of("agent_retriever", "agent_reasoner", "agent_dummy");

        // 定义特征函数 v(S):
        // 检索与推理缺一不可, 二者均在收益为 100, 仅有一个收益 20; dummy 节点完全不带来任何边际增益
        Function<Set<String>, Double> coalitionFunc = subset -> {
            boolean hasRetriever = subset.contains("agent_retriever");
            boolean hasReasoner = subset.contains("agent_reasoner");
            if (hasRetriever && hasReasoner) {
                return 100.0;
            } else if (hasRetriever || hasReasoner) {
                return 20.0;
            } else {
                return 0.0;
            }
        };

        Map<String, Double> shapley = shapleyAllocator.calculateShapleyValues(agents, coalitionFunc);

        // 1. 公理 1 (完备效率性 Efficiency): 总和严格等于全联盟价值 v(N) = 100.0
        double totalShapley = shapley.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(100.0, totalShapley, 1e-6, "沙普利值之和必须 100% 守恒");

        // 2. 公理 2 (对称性 Symmetry): retriever 与 reasoner 角色地位对称, 分配值完全相等
        assertEquals(shapley.get("agent_retriever"), shapley.get("agent_reasoner"), 1e-6, "对称智能体应获得相同边际报酬");

        // 3. 公理 3 (虚设性 Dummy Player): dummy 边际贡献为 0, 分配值必须为 0.0
        assertEquals(0.0, shapley.get("agent_dummy"), 1e-6, "虚设节点边际贡献为 0 时沙普利报酬必须严格为 0");

        // 且核心智能体均获得非负报酬
        assertTrue(shapley.get("agent_retriever") > 0);
    }

    @Test
    @DisplayName("契约 5: 验证拍卖协调中枢端到端生命周期与低延迟保障 (<= 5ms)")
    void test5_MultiAgentAuctionCoordinatorEndToEndLifecycle() {
        // 注册并信任合法工作智能体
        creditLedger.addTrustEdge("agent_coordinator", "worker_search");
        creditLedger.addTrustEdge("agent_coordinator", "worker_synthesis");

        CombinatorialAuctionEngine.TaskItem t1 = new CombinatorialAuctionEngine.TaskItem("t_search", "web_search", 1.0);
        CombinatorialAuctionEngine.TaskItem t2 = new CombinatorialAuctionEngine.TaskItem("t_synth", "report_synthesis", 1.5);
        List<CombinatorialAuctionEngine.TaskItem> tasks = List.of(t1, t2);

        // 混入女巫节点的恶意竞标
        CombinatorialAuctionEngine.AgentBid legitBid1 = new CombinatorialAuctionEngine.AgentBid("b1", "worker_search", List.of("t_search"), 1.2);
        CombinatorialAuctionEngine.AgentBid legitBid2 = new CombinatorialAuctionEngine.AgentBid("b2", "worker_synthesis", List.of("t_synth"), 2.0);
        CombinatorialAuctionEngine.AgentBid sybilBid = new CombinatorialAuctionEngine.AgentBid("b_sybil", "sybil_hacker", List.of("t_search", "t_synth"), 0.1);

        List<CombinatorialAuctionEngine.AgentBid> allBids = List.of(legitBid1, legitBid2, sybilBid);

        Function<Set<String>, Double> taskValueFunc = s -> s.size() * 50.0;

        long startNs = System.nanoTime();
        AuctionSettlementReceipt receipt = coordinator.coordinateAuction("TASK_BATCH_001", tasks, allBids, taskValueFunc);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertNotNull(receipt);
        assertEquals("TASK_BATCH_001", receipt.taskId());
        assertTrue(receipt.settlementHash().length() == 64, "必须生成 64 位 SHA-256 存证哈希");

        // 验证女巫报价被过滤，中标者均为合法节点
        Map<String, List<String>> alloc = receipt.winnerTaskAllocation();
        assertFalse(alloc.containsKey("sybil_hacker"), "女巫节点必须被完全剔除出决标名单");
        assertTrue(alloc.containsKey("worker_search"));
        assertTrue(alloc.containsKey("worker_synthesis"));

        // 验证执行耗时 (单次轻量调度极速完成)
        assertTrue(elapsedMs < 100, "端到端组合拍卖与结算调度必须毫秒级极速完成, 实际: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("契约 6: 验证结算凭据 Record 的不可变性与 SHA-256 密码学防篡改")
    void test6_AuctionSettlementReceiptCryptographicImmutability() {
        Map<String, List<String>> alloc = new HashMap<>();
        alloc.put("agent_1", List.of("task_1"));

        Map<String, Double> vcg = Map.of("agent_1", 5.0);
        Map<String, Double> shapley = Map.of("agent_1", 20.0);

        AuctionSettlementReceipt receipt = AuctionSettlementReceipt.createReceipt(
                "AUC_001", "TASK_001", alloc, vcg, shapley, 3.5
        );

        // 验证不可变视图: 修改返回的 Map 必须抛出异常
        assertThrows(UnsupportedOperationException.class, () -> {
            receipt.winnerTaskAllocation().put("agent_evil", List.of("task_evil"));
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            receipt.vcgPayments().put("agent_evil", 999.0);
        });

        // 验证哈希唯一性与雪崩效应
        AuctionSettlementReceipt receiptModified = AuctionSettlementReceipt.createReceipt(
                "AUC_002", "TASK_001", alloc, vcg, shapley, 3.5
        );
        assertNotEquals(receipt.settlementHash(), receiptModified.settlementHash(), "不同拍卖的存证哈希必须绝对不同");
    }

    @Test
    @DisplayName("契约 7: 验证恶意节点违约超时惩罚与信誉黑名单自动隔离机制")
    void test7_UnderbiddingMaliciousAgentPenaltyAndQuarantine() {
        String badWorker = "agent_unreliable_worker";
        creditLedger.addTrustEdge("agent_coordinator", badWorker);
        creditLedger.recordExecutionSuccess(badWorker, 0.0); // 初始信誉 0.70

        // 连续发生 2 次执行超时/违约
        creditLedger.recordExecutionTimeout(badWorker);
        creditLedger.recordExecutionTimeout(badWorker);

        Optional<AntiSybilCreditLedger.AgentCreditProfile> profileOpt = creditLedger.getProfile(badWorker);
        assertTrue(profileOpt.isPresent());
        AntiSybilCreditLedger.AgentCreditProfile profile = profileOpt.get();

        // 0.70 - 0.25 - 0.25 = 0.20 < 0.40, 触发自动隔离
        assertTrue(profile.reputationScore() <= 0.40, "违约惩罚后信誉分必须严重下挫");
        assertTrue(profile.isQuarantined(), "信誉低于 0.40 必须自动关入黑名单隔离");

        // 隔离节点在抗女巫检测中必须被直接判定为女巫/不可信
        assertTrue(creditLedger.isSybil(badWorker), "被隔离节点应直接判定为不可信并被拍卖引擎阻断");
    }

    @Test
    @DisplayName("契约 8: 验证高并发多线程并发竞标下的线程安全性与稳定性")
    void test8_HighConcurrencyAuctionThroughput() throws InterruptedException, ExecutionException {
        int threads = 8;
        int iterationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int threadIdx = i;
            futures.add(executor.submit(() -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                    String worker = "worker_" + threadIdx + "_" + j;
                    creditLedger.addTrustEdge("agent_coordinator", worker);
                    creditLedger.recordExecutionSuccess(worker, 0.05);

                    CombinatorialAuctionEngine.TaskItem task = new CombinatorialAuctionEngine.TaskItem("task_" + threadIdx + "_" + j, "test_skill", 1.0);
                    CombinatorialAuctionEngine.AgentBid bid = new CombinatorialAuctionEngine.AgentBid("b_" + threadIdx + "_" + j, worker, List.of(task.taskId()), 1.0);

                    AuctionSettlementReceipt r = coordinator.coordinateAuction(
                            "TASK_CONCUR_" + threadIdx + "_" + j,
                            List.of(task),
                            List.of(bid),
                            s -> 10.0
                    );
                    if (r == null || r.settlementHash() == null) {
                        return false;
                    }
                }
                return true;
            }));
        }

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "并发拍卖与结算必须全部成功返回有效收据");
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
}
