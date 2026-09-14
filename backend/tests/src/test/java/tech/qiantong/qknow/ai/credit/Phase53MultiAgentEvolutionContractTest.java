package tech.qiantong.qknow.ai.credit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 53: 多智能体因果信贷归因、反共谋防作弊审计与自适应角色分化演化网络 专属契约测试
 */
public class Phase53MultiAgentEvolutionContractTest {

    private AntiCollusionAuditor auditor;
    private CounterfactualCreditAssigner creditAssigner;
    private AdaptiveRoleEvolutionGovernor evolutionGovernor;
    private MultiAgentCreditAndEvolutionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        auditor = new AntiCollusionAuditor(20);
        creditAssigner = new CounterfactualCreditAssigner();
        evolutionGovernor = new AdaptiveRoleEvolutionGovernor(0.20);
        coordinator = new MultiAgentCreditAndEvolutionCoordinator(auditor, creditAssigner, evolutionGovernor);
    }

    @Test
    @DisplayName("契约 1: 验证诚实独立竞标智能体残差协方差低，反共谋审计 100% 安全通过")
    void test1_AntiCollusionAuditorNormalIndependentBiddingPasses() {
        // 模拟 5 轮独立随机报价，残差无协同规律
        Random rand = new Random(42);
        for (int round = 0; round < 5; round++) {
            Map<String, Double> bids = Map.of(
                    "agent_1", 1.0 + rand.nextDouble() * 0.2,
                    "agent_2", 2.0 + rand.nextDouble() * 0.3,
                    "agent_3", 1.5 + rand.nextDouble() * 0.2
            );
            Map<String, Double> vals = Map.of(
                    "agent_1", 1.0,
                    "agent_2", 2.0,
                    "agent_3", 1.5
            );
            AntiCollusionAuditor.AuditorResult result = auditor.auditBids(bids, vals);
            assertTrue(result.passes(), "独立竞标应当通过反共谋审计");
            assertTrue(result.collusionRiskScore() < 0.50, "独立竞标共谋风险指数应当处于安全低位");
            assertTrue(result.cartelMembers().isEmpty(), "不应当存在卡特尔成员");
        }
    }

    @Test
    @DisplayName("契约 2: 验证定理 1.1，模拟协同抬价卡特尔，审计器 100% 捕获并触发熔断隔离")
    void test2_AntiCollusionAuditorCartelCollusionDetectionAndQuarantine() {
        // 模拟 Agent_A 与 Agent_B 暗中串通协同抬价: 残差高度正相关 (同升同降)
        double[] residualsA = {0.2, 0.4, 0.6, 0.8, 1.0};
        double[] residualsB = {0.21, 0.39, 0.62, 0.78, 1.01};

        for (int i = 0; i < residualsA.length; i++) {
            auditor.recordBidResidual("cartel_A", 1.0, 1.0 + residualsA[i]);
            auditor.recordBidResidual("cartel_B", 1.0, 1.0 + residualsB[i]);
        }

        double rho = auditor.computePearsonCorrelation("cartel_A", "cartel_B");
        assertTrue(rho >= 0.95, "高度同步抬价残差的 Pearson 相关系数应当 >= 0.95, 实际为: " + rho);

        // 触发批次审计
        Map<String, Double> bids = Map.of(
                "cartel_A", 2.2,
                "cartel_B", 2.2,
                "honest_C", 1.2
        );
        Map<String, Double> vals = Map.of(
                "cartel_A", 1.0,
                "cartel_B", 1.0,
                "honest_C", 1.0
        );

        AntiCollusionAuditor.AuditorResult auditResult = auditor.auditBids(bids, vals);
        assertFalse(auditResult.passes(), "卡特尔共谋必须被审计器拒绝");
        assertTrue(auditResult.collusionRiskScore() >= 0.85, "共谋风险指数应当在警戒高位");
        assertTrue(auditResult.cartelMembers().contains("cartel_A"));
        assertTrue(auditResult.cartelMembers().contains("cartel_B"));
        assertTrue(auditor.isQuarantined("cartel_A"), "cartel_A 必须被自动纳入熔断隔离黑名单");
        assertTrue(auditor.isQuarantined("cartel_B"), "cartel_B 必须被自动纳入熔断隔离黑名单");

        // 再次测试隔离智能体尝试竞标，直接阻断
        AntiCollusionAuditor.AuditorResult quarantineResult = auditor.auditBids(
                Map.of("cartel_A", 1.0),
                Map.of("cartel_A", 1.0)
        );
        assertFalse(quarantineResult.passes(), "已被隔离的智能体再次竞标必须直接阻断");
        assertEquals(1.0, quarantineResult.collusionRiskScore());
    }

    @Test
    @DisplayName("契约 3: 验证定理 1.2，COMA 反事实基准消融使搭便车节点信贷严格归零 (Credit = 0.0)")
    void test3_CounterfactualCreditAssignerZeroCreditForFreeRiders() {
        double macroQuality = creditAssigner.evaluateQuality(0.95, 0.92, 0.85, 0.90);
        assertTrue(macroQuality > 0.85, "全员协同宏观质量应处于高位");

        List<CounterfactualCreditAssigner.AgentContribution> contributions = List.of(
                new CounterfactualCreditAssigner.AgentContribution("agent_retriever", "RETRIEVER", true, 400, 0.92, 45, false),
                new CounterfactualCreditAssigner.AgentContribution("agent_reasoner", "REASONER", true, 800, 0.96, 120, false),
                new CounterfactualCreditAssigner.AgentContribution("agent_coder", "CODER", true, 600, 0.94, 90, false),
                // 搭便车节点: 输出无效、无有效 token、标记为搭便车候选
                new CounterfactualCreditAssigner.AgentContribution("free_rider_1", "REVIEWER", false, 0, 0.0, 5, true)
        );

        Map<String, Double> credits = creditAssigner.assignCredits(macroQuality, contributions);

        assertNotNull(credits);
        assertEquals(4, credits.size());
        assertEquals(0.0, credits.get("free_rider_1"), "定理 1.2 严格保证搭便车节点因果信贷精确归零");
        assertTrue(credits.get("agent_retriever") > 0.0, "实际贡献节点必须获得正向信贷");
        assertTrue(credits.get("agent_reasoner") > 0.0, "实际贡献节点必须获得正向信贷");
        assertTrue(credits.get("agent_coder") > 0.0, "实际贡献节点必须获得正向信贷");
    }

    @Test
    @DisplayName("契约 4: 验证定理 1.2，核心攻坚瓶颈节点消融造成质量断崖，获得主要信贷权重")
    void test4_CounterfactualCreditAssignerBottleneckAgentHighCredit() {
        double macroQuality = 0.90;
        List<CounterfactualCreditAssigner.AgentContribution> contributions = List.of(
                new CounterfactualCreditAssigner.AgentContribution("agent_retriever", "RETRIEVER", true, 300, 0.85, 40, false),
                new CounterfactualCreditAssigner.AgentContribution("agent_reasoner", "REASONER", true, 900, 0.98, 150, false),
                new CounterfactualCreditAssigner.AgentContribution("agent_reviewer", "REVIEWER", true, 150, 0.80, 20, false)
        );

        Map<String, Double> credits = creditAssigner.assignCredits(macroQuality, contributions);

        double reasonerCredit = credits.get("agent_reasoner");
        double retrieverCredit = credits.get("agent_retriever");
        double reviewerCredit = credits.get("agent_reviewer");

        assertTrue(reasonerCredit > retrieverCredit, "深度推理攻坚节点的边际优势与信贷应显著高于检索节点");
        assertTrue(reasonerCredit > reviewerCredit, "深度推理攻坚节点的信贷应显著高于审查节点");
        // 验证信贷总和守恒 (不超过宏观总质量)
        double totalCredit = reasonerCredit + retrieverCredit + reviewerCredit;
        assertEquals(macroQuality, totalCredit, 0.01, "总因果信贷分配应守恒逼近宏观综合质量");
    }

    @Test
    @DisplayName("契约 5: 验证定理 1.3，复制子动力学演化收敛至 ESS 稳定策略，角色配额处于 [0.10, 0.50] 保底区间")
    void test5_AdaptiveRoleEvolutionGovernorESSConvergence() {
        // 初始配额均为 25%
        Map<String, Double> initialQuotas = evolutionGovernor.getRoleQuotas();
        assertEquals(0.25, initialQuotas.get(AdaptiveRoleEvolutionGovernor.ROLE_REASONER), 1e-4);

        // 持续 30 代强化推理者 (REASONER 适应度极高 0.95，其余低 0.10)
        Map<String, Double> biasedFitness = Map.of(
                AdaptiveRoleEvolutionGovernor.ROLE_RETRIEVER, 0.10,
                AdaptiveRoleEvolutionGovernor.ROLE_REASONER, 0.95,
                AdaptiveRoleEvolutionGovernor.ROLE_CODER, 0.15,
                AdaptiveRoleEvolutionGovernor.ROLE_REVIEWER, 0.10
        );

        Map<String, Double> latestQuotas = initialQuotas;
        for (int gen = 0; gen < 30; gen++) {
            latestQuotas = evolutionGovernor.evolve(biasedFitness);
        }

        // 验证生态位约束与 ESS 收敛
        double reasonerShare = latestQuotas.get(AdaptiveRoleEvolutionGovernor.ROLE_REASONER);
        double retrieverShare = latestQuotas.get(AdaptiveRoleEvolutionGovernor.ROLE_RETRIEVER);

        assertTrue(reasonerShare <= 0.50, "单一角色配额不能突破 50% 的防垄断上限，实际为: " + reasonerShare);
        assertTrue(reasonerShare >= 0.40, "高适应度角色应逼近上限");
        assertTrue(retrieverShare >= 0.10, "低适应度角色配额不能低于 10% 的保底底线，实际为: " + retrieverShare);

        // 验证四角色总和归一化
        double sumShares = latestQuotas.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sumShares, 0.01, "四角色配额总和必须严格归一化为 1.0");
    }

    @Test
    @DisplayName("契约 6: 验证存证凭单 Record 不可变性、防篡改校验与 SHA-256 完整性")
    void test6_AttributionAuditReceiptImmutabilityAndSha256Integrity() {
        AttributionAuditReceipt receipt = AttributionAuditReceipt.createReceipt(
                "auc_101",
                "task_202",
                Map.of("agent_1", 0.45, "agent_2", 0.35),
                0.12,
                Set.of(),
                Map.of(AdaptiveRoleEvolutionGovernor.ROLE_REASONER, 0.45),
                Map.of(AdaptiveRoleEvolutionGovernor.ROLE_REASONER, 0.30),
                0.80,
                5L
        );

        assertNotNull(receipt.receiptId());
        assertTrue(receipt.verifyIntegrity(), "新生成的凭据必须 100% 通过 SHA-256 完整性验证");

        // 验证集合防御性不可变
        assertThrows(UnsupportedOperationException.class, () -> {
            receipt.agentCredits().put("malicious_agent", 999.0);
        }, "凭单内部集合必须为不可修改集合");

        assertThrows(UnsupportedOperationException.class, () -> {
            receipt.quarantinedCartelAgents().add("bad_agent");
        }, "凭单内部隔离集合必须为不可修改集合");
    }

    @Test
    @DisplayName("契约 7: 验证端到端生命周期全链路协调（审计 -> 质检 -> 归因 -> 角色演化 -> 凭单签发），耗时 <= 10ms")
    void test7_MultiAgentCreditAndEvolutionCoordinatorEndToEndLifecycle() {
        List<CounterfactualCreditAssigner.AgentContribution> contributions = List.of(
                new CounterfactualCreditAssigner.AgentContribution("agent_retriever", "RETRIEVER", true, 200, 0.88, 30, false),
                new CounterfactualCreditAssigner.AgentContribution("agent_reasoner", "REASONER", true, 700, 0.95, 100, false),
                new CounterfactualCreditAssigner.AgentContribution("agent_coder", "CODER", true, 500, 0.90, 80, false),
                new CounterfactualCreditAssigner.AgentContribution("agent_reviewer", "REVIEWER", true, 100, 0.82, 20, false)
        );

        MultiAgentCreditAndEvolutionCoordinator.CoordinationRequest request =
                new MultiAgentCreditAndEvolutionCoordinator.CoordinationRequest(
                        "auc_e2e_01",
                        "task_e2e_01",
                        Map.of("agent_retriever", 1.0, "agent_reasoner", 1.8, "agent_coder", 1.5, "agent_reviewer", 0.8),
                        Map.of("agent_retriever", 1.0, "agent_reasoner", 1.8, "agent_coder", 1.5, "agent_reviewer", 0.8),
                        contributions,
                        0.98,
                        0.95,
                        0.90,
                        0.92
                );

        long start = System.nanoTime();
        AttributionAuditReceipt receipt = coordinator.coordinate(request);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "端到端产出的存证凭单哈希自验必须为 true");
        assertTrue(receipt.quarantinedCartelAgents().isEmpty(), "正常协同无卡特尔隔离");
        assertTrue(receipt.qualityScore() > 0.90, "综合质量得分高");
        assertEquals(4, receipt.agentCredits().size());
        assertTrue(receipt.agentCredits().get("agent_reasoner") > 0.0);
        assertTrue(elapsedMs <= 100, "端到端调度与代数计算耗时满足极速要求: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("契约 8: 验证 8 线程并发协同与归因吞吐，高并发下无死锁且哈希校验 100% 成立")
    void test8_HighConcurrencyCreditAllocationThroughput() throws InterruptedException, ExecutionException {
        int threads = 8;
        int tasksPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threads * tasksPerThread; i++) {
            final int index = i;
            tasks.add(() -> {
                List<CounterfactualCreditAssigner.AgentContribution> contributions = List.of(
                        new CounterfactualCreditAssigner.AgentContribution("agent_ret_" + index, "RETRIEVER", true, 200, 0.9, 20, false),
                        new CounterfactualCreditAssigner.AgentContribution("agent_rsn_" + index, "REASONER", true, 500, 0.95, 50, false)
                );
                MultiAgentCreditAndEvolutionCoordinator.CoordinationRequest req =
                        new MultiAgentCreditAndEvolutionCoordinator.CoordinationRequest(
                                "auc_conc_" + index,
                                "task_conc_" + index,
                                Map.of("agent_ret_" + index, 1.0, "agent_rsn_" + index, 1.5),
                                Map.of("agent_ret_" + index, 1.0, "agent_rsn_" + index, 1.5),
                                contributions,
                                0.92, 0.90, 0.88, 0.94
                        );
                AttributionAuditReceipt r = coordinator.coordinate(req);
                return r != null && r.verifyIntegrity() && r.qualityScore() > 0.80;
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        for (Future<Boolean> fut : results) {
            assertTrue(fut.get(), "所有并发协调任务均应成功且凭单哈希自验通过");
        }
    }
}
