package tech.qiantong.qknow.hermes.agent.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.BaseAgent;
import tech.qiantong.qknow.hermes.agent.governor.SphericalDyLANRoleGovernor;
import tech.qiantong.qknow.hermes.agent.governor.dto.ShapleyCreditAllocationReceipt;
import tech.qiantong.qknow.hermes.agent.governor.impl.DefaultSphericalDyLANRoleGovernor;
import tech.qiantong.qknow.hermes.agent.governor.impl.MonteCarloShapleyCreditAllocator;
import tech.qiantong.qknow.hermes.agent.planner.impl.DefaultTreeOfThoughtsReActPlanner;
import tech.qiantong.qknow.hermes.agent.planner.mcts.MctsSearchReceipt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第二阶段认知架构进阶核心契约测试套件 (TreeOfThoughtsAndDynamicRoleContractTest)
 * 验证 MCTS 认知树分支前瞻与回溯剪枝、千问 1536 维超球面动态角色涌现与夏普利博弈论公理化价值归因。
 */
public class TreeOfThoughtsAndDynamicRoleContractTest {

    @Test
    @DisplayName("EXP-STAGE2-01: 验证 MCTS 认知树搜索展开、UCB1 评估、前瞻剪枝与防时序侧信道凭单")
    void testMctsTreeSearchLookaheadAndPruning() {
        DefaultTreeOfThoughtsReActPlanner planner = new DefaultTreeOfThoughtsReActPlanner(null, Math.sqrt(2.0), 0.35);

        String goal = "深度排查分布式交易链路中的状态不一致根因并制定修复策略";
        MctsSearchReceipt receipt = planner.planAndExecute(goal, List.of(), Map.of("sessionId", "test-mcts-01"), 6, 3);

        assertNotNull(receipt);
        assertTrue(receipt.totalNodesExpanded() >= 4, "MCTS 树搜索必须展开多分支候选节点");
        assertTrue(receipt.maxDepthReached() >= 2, "树搜索深度必须达到前瞻层级");
        assertTrue(receipt.prunedBranchCount() >= 1, "低价值与死胡同分支必须触发剪枝回溯");
        assertFalse(receipt.optimalActionPath().isEmpty(), "必须成功提取最优动作推演路径");
        assertTrue(receipt.finalAnswerText().contains("MCTS"), "最终答复必须体现认知规划结果");

        // 验证密码学 SHA-256 自签名与常量时间比对
        assertTrue(receipt.verifySignature(), "MCTS 执行凭单必须通过 MessageDigest.isEqual 常量时间验签");
    }

    @Test
    @DisplayName("EXP-STAGE2-02: 验证 MCTS 凭单防篡改安全性")
    void testMctsReceiptTamperProof() {
        MctsSearchReceipt receipt = MctsSearchReceipt.createSigned(
                "rcpt-101",
                "session-101",
                8,
                2,
                3,
                0.88,
                List.of("root", "branch1", "leaf"),
                "最终答复内容",
                120L
        );

        assertTrue(receipt.verifySignature(), "原始凭单签名校验必须通过");

        // 构造被篡改的凭单（修改剪枝数为 99）
        MctsSearchReceipt tampered = new MctsSearchReceipt(
                receipt.receiptId(),
                receipt.traceSessionId(),
                receipt.totalNodesExpanded(),
                99, // 恶意篡改
                receipt.maxDepthReached(),
                receipt.bestTrajectoryValue(),
                receipt.optimalActionPath(),
                receipt.finalAnswerText(),
                receipt.sha256Signature(),
                receipt.executionLatencyMs(),
                receipt.timestampMs()
        );

        assertFalse(tampered.verifySignature(), "篡改凭单校验必须失败");
    }

    @Test
    @DisplayName("EXP-STAGE2-03: 验证千问 1536 维超球面动态角色修剪与自组织涌现")
    void testSphericalDyLANRolePruningAndEmergence() {
        DefaultSphericalDyLANRoleGovernor governor = new DefaultSphericalDyLANRoleGovernor(0.70);

        // 构造千问 1536 维超球面单位向量 (L2 = 1.0)
        float[] taskVec = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            taskVec[i] = (float) Math.sin(i + 1);
            sumSq += taskVec[i] * taskVec[i];
        }
        float inv = (float) (1.0 / Math.sqrt(sumSq));
        for (int i = 0; i < 1536; i++) {
            taskVec[i] *= inv;
        }

        // 构造模拟 Worker 集合
        BaseAgent highMatchWorker = new BaseAgent("MathExpertWorker", "专业复杂的微积分与概率论数学分析智能体") {
            @Override
            public String chat(String question, Map<String, Object> context) { return "ok"; }
        };
        BaseAgent lowMatchWorker = new BaseAgent("WeatherWorker", "仅提供日常天气预报查询的通用边缘助手") {
            @Override
            public String chat(String question, Map<String, Object> context) { return "ok"; }
        };

        Map<String, Double> history = Map.of(
                "MathExpertWorker", 0.90,
                "WeatherWorker", 0.10
        );

        SphericalDyLANRoleGovernor.DynamicRoleDecision decision = governor.governRoles(
                taskVec,
                List.of(highMatchWorker, lowMatchWorker),
                history,
                0.40, // 剪枝阈值
                0.95  // 涌现阈值
        );

        assertNotNull(decision);
        assertTrue(decision.activeAgentIds().contains("MathExpertWorker"), "高匹配高收益智能体必须保持活跃");
        assertTrue(decision.prunedAgentIds().contains("WeatherWorker"), "低匹配边缘智能体必须被动态修剪休眠");
        assertTrue(decision.hasEmergedNewRole(), "超高阈值下必须触发自组织涌现专精角色补充");
        assertFalse(decision.emergedRolePrompt().isBlank());
    }

    @Test
    @DisplayName("EXP-STAGE2-04: 验证蒙特卡洛夏普利边际价值分配器满足有效性与博弈论公理")
    void testMonteCarloShapleyCreditAllocationAxioms() {
        MonteCarloShapleyCreditAllocator allocator = new MonteCarloShapleyCreditAllocator(30);

        List<String> agents = List.of("CoderAgent", "TesterAgent", "ReviewerAgent");

        // 特征函数: Coder 贡献核心 60 分，Tester 增量贡献 25 分，Reviewer 贡献 15 分
        ShapleyCreditAllocationReceipt receipt = allocator.allocateCredits(
                "session-shapley-01",
                agents,
                subset -> {
                    double v = 0.0;
                    if (subset.contains("CoderAgent")) v += 60.0;
                    if (subset.contains("TesterAgent")) v += 25.0;
                    if (subset.contains("ReviewerAgent")) v += 15.0;
                    return v;
                },
                30
        );

        assertNotNull(receipt);
        assertEquals(3, receipt.totalAgentsEvaluated());
        assertEquals(100.0, receipt.grandCoalitionValue(), 0.001);

        Map<String, Double> credits = receipt.normalizedCredits();
        assertNotNull(credits);

        // 验证公理化归一化和为 1.0 (有效性公理)
        double sumCredits = credits.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sumCredits, 0.001, "归一化夏普利权重之和必须严格为 1.0");

        // 验证边际贡献排序: Coder > Tester > Reviewer
        assertTrue(credits.get("CoderAgent") > credits.get("TesterAgent"), "核心贡献者夏普利值必须高于辅助者");
        assertTrue(credits.get("TesterAgent") > credits.get("ReviewerAgent"));

        // 验证自签名凭单
        assertTrue(receipt.verifySignature(), "夏普利分配凭单必须通过常量时间验签");
    }
}
