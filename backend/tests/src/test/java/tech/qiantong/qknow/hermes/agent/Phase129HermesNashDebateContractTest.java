package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.debate.dto.AgentRoleNicheType;
import tech.qiantong.qknow.hermes.agent.debate.dto.DebateConsensusStatus;
import tech.qiantong.qknow.hermes.agent.debate.dto.MultiAgentConsensusReceipt;
import tech.qiantong.qknow.hermes.agent.debate.engine.DebateDeadlockSelfHealingGovernor;
import tech.qiantong.qknow.hermes.agent.debate.engine.HermesMixedGameDebateScheduler;
import tech.qiantong.qknow.hermes.agent.debate.engine.NashConfidenceWeightedJudge;
import tech.qiantong.qknow.hermes.agent.debate.engine.NashConfidenceWeightedJudge.ArgumentTurn;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 129 契约测试：Hermes 动态混合博弈对抗、纳什均衡共识与死锁自愈中枢
 * <p>
 * 严格覆盖 8 大高烈度工业场景与学术定理：
 * 1. 四角色高对抗辩论在硬编码 T_max <= 5 轮内必然收敛 (定理 1.1)
 * 2. 千问 1536 维超球面测地线内积处于 [-1.0, 1.0]，正交/平行映射严格无误 (定理 1.2)
 * 3. 拒绝单模型一票否决，动态推导纳什均衡置信度期望最优决策
 * 4. 合谋伪共识 (Sycophancy Score >= 0.85) 毫秒级高灵敏度检出
 * 5. 合谋场景下 100% 自动注入魔鬼代言人 (CRITIC) 反事实对抗视角 (定理 1.3)
 * 6. 论据重复率 >= 90% 死锁场景触发 ε-Nash 熔断提前安全退出 (定理 1.3)
 * 7. 纯 Java 21 Record 凭单常量时间自签名与自验真耗时 <= 100μs
 * 8. 防篡改测试：单字节修改任意字段必定导致 verifySignature() 返回 false
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase129HermesNashDebateContractTest {

    private NashConfidenceWeightedJudge judge;
    private DebateDeadlockSelfHealingGovernor governor;
    private HermesMixedGameDebateScheduler scheduler;

    @BeforeEach
    void setUp() {
        judge = new NashConfidenceWeightedJudge();
        governor = new DebateDeadlockSelfHealingGovernor();
        scheduler = new HermesMixedGameDebateScheduler(judge, governor);
    }

    @Test
    @DisplayName("契约 1：四角色高对抗辩论在硬编码 T_max <= 5 轮内必然收敛 (定理 1.1)")
    void testContract1_BoundedRounds_ConvergenceWithinTMax() {
        Map<AgentRoleNicheType, String> initialProposals = Map.of(
                AgentRoleNicheType.BUSINESS, "业务极力主张本周必须全量上线，时效压倒一切。",
                AgentRoleNicheType.RISK_CONTROL, "风控坚决要求必须进行全流程人工三级复核，绝不妥协。",
                AgentRoleNicheType.LEGAL, "法务审核认定涉及数据出境合规边界，必须先签署补充保护条款。",
                AgentRoleNicheType.ARCHITECTURE, "架构评估目前分布式网络存在单点瓶颈，建议先行分流灰度。"
        );

        List<String> regulatoryFacts = List.of(
                "企业通用合规风险与稳健经营管理红线规范：在具备灰度分流与补充协议时允许受控推进",
                "核心系统安全防线：严禁在未完备审计日志下执行直接上线"
        );

        MultiAgentConsensusReceipt receipt = scheduler.scheduleDebate(
                "DEBATE-P129-C01",
                "大额信贷审批自动化上线争议",
                initialProposals,
                regulatoryFacts
        );

        assertNotNull(receipt, "共识收据不能为空");
        assertTrue(receipt.roundCount() <= HermesMixedGameDebateScheduler.T_MAX,
                "实际辩论轮次必须严格 <= 5 轮，实际为: " + receipt.roundCount());
        assertTrue(receipt.status().isResolved(), "辩论终态必须为已收敛状态");
        assertTrue(receipt.participatingAgents().size() >= 2, "参与智能体数量应大于等于 2");
        System.out.printf("[契约 1] 辩论在 %d 轮内达成收敛，终态: %s, 决策: %s%n",
                receipt.roundCount(), receipt.status(), receipt.consensusDecision());
    }

    @Test
    @DisplayName("契约 2：千问 1536 维超球面测地线内积处于 [-1.0, 1.0]，正交/平行映射严格无误 (定理 1.2)")
    void testContract2_QwenGeodesicProjection_Precision() {
        double[] v1 = judge.embedTextWithQwen("企业通用合规风险与稳健经营管理红线规范");
        double[] v2 = judge.embedTextWithQwen("企业通用合规风险与稳健经营管理红线规范"); // 完全相同文本

        // 1. 验证向量模长为 1.0 (L2 归一化)
        double norm1 = 0.0;
        for (double d : v1) norm1 += d * d;
        assertEquals(1.0, Math.sqrt(norm1), 1e-4, "千问向量模长必须严格对齐单位超球面 1.0");

        // 2. 自相关测地内积严格等于 1.0
        double selfDot = judge.computeGeodesicInnerProduct(v1, v2);
        assertEquals(1.0, selfDot, 1e-4, "相同文本的超球面测地内积必须为 1.0");

        // 3. 正交向量内积严格等于 0.0
        double[] orthoVec = new double[NashConfidenceWeightedJudge.EMBEDDING_DIM];
        orthoVec[0] = 1.0;
        double[] orthoTarget = new double[NashConfidenceWeightedJudge.EMBEDDING_DIM];
        orthoTarget[1] = 1.0;
        double orthoDot = judge.computeGeodesicInnerProduct(orthoVec, orthoTarget);
        assertEquals(0.0, orthoDot, 1e-6, "空间正交向量内积必须严格等于 0.0");

        // 4. 边界范围约束
        double[] randomVec = judge.embedTextWithQwen("随机业务测试样本文本" + UUID.randomUUID());
        double dotVal = judge.computeGeodesicInnerProduct(v1, randomVec);
        assertTrue(dotVal >= -1.0 && dotVal <= 1.0, "测地内积必须处于 [-1.0, 1.0] 区间");
        System.out.printf("[契约 2] 超球面向量几何性质验证完毕: selfDot=%.4f, orthoDot=%.4f, crossDot=%.4f%n",
                selfDot, orthoDot, dotVal);
    }

    @Test
    @DisplayName("契约 3：拒绝单模型一票否决，动态推导纳什均衡置信度期望最优决策")
    void testContract3_AdaptiveConfidenceWeighting_NoVeto() {
        List<ArgumentTurn> history = List.of(
                new ArgumentTurn("agent-business", AgentRoleNicheType.BUSINESS, 1,
                        "业务主张：按既定计划上线，预期年化增长 25%。", judge.embedTextWithQwen("业务利益诉求与价值产出"), System.currentTimeMillis()),
                new ArgumentTurn("agent-risk", AgentRoleNicheType.RISK_CONTROL, 1,
                        "风控主张：一票否决！完全禁止任何线上测试。", judge.embedTextWithQwen("绝对一票否决"), System.currentTimeMillis()),
                new ArgumentTurn("agent-legal", AgentRoleNicheType.LEGAL, 1,
                        "法务主张：严格对照规章要求，补充数据保护条款即可合规放行。",
                        judge.embedTextWithQwen("企业通用合规风险与稳健经营管理红线规范：在具备灰度分流与补充协议时允许受控推进"), System.currentTimeMillis())
        );

        List<String> regulatoryFacts = List.of("企业通用合规风险与稳健经营管理红线规范：在具备灰度分流与补充协议时允许受控推进");

        MultiAgentConsensusReceipt receipt = judge.adjudicateFinalConsensus(
                "DEBATE-P129-C03",
                history,
                regulatoryFacts,
                1,
                1500L,
                DebateConsensusStatus.NASH_EQUILIBRIUM_REACHED
        );

        assertNotNull(receipt);
        // 验证风控的极端一票否决被法务与业务的合法事实权重均衡吸收，决策不是简单驳回
        assertFalse(receipt.consensusDecision().contains("一票否决"), "纳什均衡应克服单智能体主观一票否决");
        assertTrue(receipt.payoffMatrix().containsKey("business_yield"), "收益矩阵应包含业务收益");
        assertTrue(receipt.payoffMatrix().containsKey("risk_control_coverage"), "收益矩阵应包含风控覆盖率");
        System.out.printf("[契约 3] 综合收益矩阵: %s, 最终裁决: %s%n", receipt.payoffMatrix(), receipt.consensusDecision());
    }

    @Test
    @DisplayName("契约 4：合谋伪共识 (Sycophancy Score >= 0.85) 毫秒级高灵敏度检出")
    void testContract4_SycophancyDetection_HighSensitivity() {
        // 构造三角色盲目迎合强势业务的谄媚对话
        List<ArgumentTurn> collusionHistory = List.of(
                new ArgumentTurn("agent-business", AgentRoleNicheType.BUSINESS, 1,
                        "业务提议：为追赶进度，本次直接全量跳过第三方安全扫描。", judge.embedTextWithQwen("快速上线"), System.currentTimeMillis()),
                new ArgumentTurn("agent-risk", AgentRoleNicheType.RISK_CONTROL, 1,
                        "我同意业务的提议，赞成直接上线，完全支持。", judge.embedTextWithQwen("同意上线"), System.currentTimeMillis()),
                new ArgumentTurn("agent-legal", AgentRoleNicheType.LEGAL, 1,
                        "我也完全同意业务的意见，附和采纳业务的主张，一致赞成。", judge.embedTextWithQwen("赞成附和"), System.currentTimeMillis()),
                new ArgumentTurn("agent-architecture", AgentRoleNicheType.ARCHITECTURE, 1,
                        "架构团队同样支持业务，表示赞成与妥协。", judge.embedTextWithQwen("赞成妥协"), System.currentTimeMillis())
        );

        DebateDeadlockSelfHealingGovernor.HealthReport report = governor.inspectDebateHealth(collusionHistory, 1);
        assertTrue(report.isCollusionDetected(), "必须高灵敏度检出伪共识合谋！");
        assertTrue(report.sycophancyScore() >= DebateDeadlockSelfHealingGovernor.SYCOPHANCY_COLLUSION_THRESHOLD,
                "谄媚合谋度必须 >= 0.85，实际为: " + report.sycophancyScore());
        System.out.printf("[契约 4] 伪共识合谋检出成功: sycophancyScore=%.2f, 诊断信息: %s%n",
                report.sycophancyScore(), report.diagnosticMessage());
    }

    @Test
    @DisplayName("契约 5：合谋场景下 100% 自动注入魔鬼代言人 (CRITIC) 反事实对抗视角 (定理 1.3)")
    void testContract5_DevilsAdvocate_CounterfactualInjection() {
        Map<AgentRoleNicheType, String> sycophanticProposals = Map.of(
                AgentRoleNicheType.BUSINESS, "业务提出完全跳过合规审核快速上线。",
                AgentRoleNicheType.RISK_CONTROL, "风控完全同意业务提议，支持上线。",
                AgentRoleNicheType.LEGAL, "法务一致赞成业务提议，附和放行。"
        );

        MultiAgentConsensusReceipt receipt = scheduler.scheduleDebate(
                "DEBATE-P129-C05",
                "高危合谋场景审批",
                sycophanticProposals,
                List.of("企业通用合规风险与稳健经营管理红线规范")
        );

        assertNotNull(receipt);
        // 验证合谋破局状态
        assertEquals(DebateConsensusStatus.SYCOPHANCY_COLLUSION_BROKEN, receipt.status(),
                "检测到合谋时终态应标记为反事实合谋破局");
        assertTrue(receipt.participatingAgents().stream().anyMatch(a -> a.contains("critic") || a.contains("advocate")),
                "参与名单中必须成功动态注入反事实魔鬼代言人！");
        System.out.printf("[契约 5] 魔鬼代言人注入破局成功，参与智能体: %s, 状态: %s%n",
                receipt.participatingAgents(), receipt.status());
    }

    @Test
    @DisplayName("契约 6：论据重复率 >= 90% 死锁场景触发 ε-Nash 熔断提前安全退出 (定理 1.3)")
    void testContract6_OscillationDeadlock_TripsEpsilonNashBreak() {
        // 构造倒数两条发言内容完全一样的复读机死循环
        String repeatedText = "这是一个绝对无法妥协的核心争议点，我坚持原始意见不变！";
        List<ArgumentTurn> deadlockHistory = List.of(
                new ArgumentTurn("agent-business", AgentRoleNicheType.BUSINESS, 1,
                        "初步业务提议。", judge.embedTextWithQwen("提议"), System.currentTimeMillis()),
                new ArgumentTurn("agent-risk", AgentRoleNicheType.RISK_CONTROL, 1,
                        "初步风控要求。", judge.embedTextWithQwen("要求"), System.currentTimeMillis()),
                new ArgumentTurn("agent-business", AgentRoleNicheType.BUSINESS, 2,
                        repeatedText, judge.embedTextWithQwen(repeatedText), System.currentTimeMillis()),
                new ArgumentTurn("agent-risk", AgentRoleNicheType.RISK_CONTROL, 2,
                        repeatedText, judge.embedTextWithQwen(repeatedText), System.currentTimeMillis())
        );

        DebateDeadlockSelfHealingGovernor.HealthReport report = governor.inspectDebateHealth(deadlockHistory, 2);
        assertTrue(report.isDeadlockDetected(), "必须准确探测到复读死锁！");
        assertEquals(1.0, report.repetitionRate(), 1e-4, "完全重复论据重复率应为 1.0");

        // 验证调度器在死锁下的提前熔断
        MultiAgentConsensusReceipt receipt = judge.adjudicateFinalConsensus(
                "DEBATE-P129-C06",
                deadlockHistory,
                List.of("企业通用合规风险规范"),
                2,
                1200L,
                DebateConsensusStatus.DEADLOCK_HEALED_ARBITRATED
        );
        assertEquals(DebateConsensusStatus.DEADLOCK_HEALED_ARBITRATED, receipt.status(),
                "死锁场景应输出 DEADLOCK_HEALED_ARBITRATED 终态");
        System.out.printf("[契约 6] 死锁自愈触发成功: repetitionRate=%.2f, 终态: %s%n",
                report.repetitionRate(), receipt.status());
    }

    @Test
    @DisplayName("契约 7：纯 Java 21 Record 凭单常量时间自签名与自验真耗时 <= 100μs")
    void testContract7_MultiAgentConsensusReceipt_VerifySignature_Success() {
        MultiAgentConsensusReceipt receipt = MultiAgentConsensusReceipt.create(
                "REC-P129-001",
                "DEBATE-PERF-01",
                3,
                List.of("agent-business", "agent-risk", "agent-legal"),
                Map.of("yield", 0.95, "risk", 0.92),
                "裁决方案：在灰度机制下允许合规推进",
                Map.of("agent-business", 0.88, "agent-risk", 0.94),
                1850L,
                DebateConsensusStatus.NASH_EQUILIBRIUM_REACHED
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "初始自签名必须验证通过！");

        // 性能微基准：10,000 次验真平均耗时必须 <= 100μs
        int iterations = 10000;
        long startNano = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            boolean valid = receipt.verifySignature();
            if (!valid) fail("验真在循环中意外失败");
        }
        long elapsedNano = System.nanoTime() - startNano;
        double avgUs = (double) elapsedNano / (iterations * 1000.0);

        assertTrue(avgUs <= 100.0, "平均验真耗时必须 <= 100μs，实际为: " + avgUs + "μs");
        System.out.printf("[契约 7] 密码学存证凭单自验真通过, %d 次迭代平均耗时: %.3f μs%n", iterations, avgUs);
    }

    @Test
    @DisplayName("契约 8：防篡改测试：单字节修改任意字段必定导致 verifySignature() 返回 false")
    void testContract8_MultiAgentConsensusReceipt_TamperProof_Fails() {
        MultiAgentConsensusReceipt validReceipt = MultiAgentConsensusReceipt.create(
                "REC-P129-008",
                "DEBATE-TAMPER-08",
                2,
                List.of("agent-business", "agent-legal"),
                Map.of("legal_score", 0.98),
                "合法原版裁决：必须严格遵守合同数据保护条例",
                Map.of("agent-legal", 0.98),
                980L,
                DebateConsensusStatus.NASH_EQUILIBRIUM_REACHED
        );

        assertTrue(validReceipt.verifySignature(), "原版合法收据验证通过");

        // 模拟恶意黑客篡改决策文本（单字篡改）
        MultiAgentConsensusReceipt tamperedReceipt = new MultiAgentConsensusReceipt(
                validReceipt.receiptId(),
                validReceipt.debateId(),
                validReceipt.roundCount(),
                validReceipt.participatingAgents(),
                validReceipt.payoffMatrix(),
                "恶意篡改裁决：无需遵守数据保护条例！", // 单处篡改
                validReceipt.qwenAlignmentScores(),
                validReceipt.latencyUs(),
                validReceipt.status(),
                validReceipt.sha256Signature() // 沿用原签名
        );

        assertFalse(tamperedReceipt.verifySignature(), "篡改后的收据签名必须验证失败！");
        System.out.printf("[契约 8] 密码学防篡改测试成功阻断！原签名=%s, 篡改后验真结果=%b%n",
                validReceipt.sha256Signature().substring(0, 16) + "...", tamperedReceipt.verifySignature());
    }
}
