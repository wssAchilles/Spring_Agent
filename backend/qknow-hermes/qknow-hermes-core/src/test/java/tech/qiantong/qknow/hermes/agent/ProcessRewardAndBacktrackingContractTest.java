package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.planner.prm.StepProcessRewardEvaluator;
import tech.qiantong.qknow.hermes.agent.planner.prm.StepProcessRewardReceipt;
import tech.qiantong.qknow.hermes.agent.planner.prm.TestTimeBacktrackingEngine;
import tech.qiantong.qknow.hermes.agent.speculative.ConfidenceGatedPrefetchFilter;
import tech.qiantong.qknow.hermes.daemon.DaemonProbeDispatcher;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 步骤级过程奖励评估器 (PRM) 与测试时局部 MCTS 回溯验证引擎专项契约测试
 * (ProcessRewardAndBacktrackingContractTest)
 * 验证 4 项严格物理断言：
 * 1. 步骤切分与多源混合过程奖励评分自验真；
 * 2. 低分步骤拦截、回溯触发与冷备仓软隔离；
 * 3. 有界局部 MCTS 回溯与超球面加权 UCB1 自愈；
 * 4. 常驻守护探针内存分发端到端全闭环。
 */
public class ProcessRewardAndBacktrackingContractTest {

    private static final int DIM = 1536;
    private static final String SECRET_KEY = "CONTRACT_TEST_PRM_SECRET_2026";

    private float[] createUnitVector(int seed) {
        float[] v = new float[DIM];
        Random rnd = new Random(seed);
        double sumSq = 0.0;
        for (int i = 0; i < DIM; i++) {
            v[i] = (float) rnd.nextGaussian();
            sumSq += (double) v[i] * v[i];
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < DIM; i++) {
            v[i] /= norm;
        }
        return v;
    }

    @Test
    @DisplayName("断言一：验证步骤切分规整门禁与多源混合过程奖励评估自验真")
    void testStepPrmEvaluationAndSignatureVerification() {
        StepProcessRewardEvaluator evaluator = new StepProcessRewardEvaluator(0.60, SECRET_KEY);

        // 1. 步骤切分与 64-Token/字符规整门禁测试
        String longReasoning = """
                步骤 1: 检查输入参数格式合法性，校验目标账户是否存在于风控白名单中。确认参数无注入风险。
                
                步骤 2: 计算交易手续费与扣减限额，执行前置 Hoare 合约断言检查。确认前置断言全部通过。
                
                短片
                
                步骤 3: 构造不可变支付转账单，调用核心账户微服务执行幂等转账，并等待 Saga 状态就绪。
                """;

        List<String> segments = evaluator.segmentSteps(longReasoning);
        assertNotNull(segments, "切分结果不应为空");
        assertTrue(segments.size() >= 2, "切分后的步骤数量应至少为 2，实际为: " + segments.size());
        for (String seg : segments) {
            assertTrue(seg.length() >= 32, "规整门禁应合并过短片段，实际长度: " + seg.length());
        }

        // 2. 向量构造与多源混合评估
        float[] uTarget = createUnitVector(1001);
        float[] vStepGood = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uTarget, 0.93, 1001L);

        StepProcessRewardReceipt receipt = evaluator.evaluateStep(
                "session_contract_01",
                1,
                segments.get(0),
                uTarget,
                vStepGood,
                true,  // Hoare 契约合规
                true,  // Kahn 因果拓扑就绪
                0.90   // 自省打分
        );

        assertNotNull(receipt, "生成的步骤凭单不应为空");
        assertEquals(StepProcessRewardReceipt.StepEvaluationStatus.PASSED, receipt.status(), "优质步骤应被评为 PASSED");
        assertTrue(receipt.confidenceScore() >= 0.60, "优质步骤得分应 >= 0.60，实际: " + receipt.confidenceScore());
        assertTrue(receipt.verifySignature(SECRET_KEY), "凭单常量时间自验真签名应通过");
        assertFalse(receipt.verifySignature("WRONG_TAMPERED_SECRET"), "错误密钥验签必须失败");

        // 验证数组防御性拷贝
        float[] clonedVec = receipt.hypersphericalVector();
        assertNotNull(clonedVec);
        clonedVec[0] = 999.0f;
        assertNotEquals(999.0f, receipt.hypersphericalVector()[0], "凭单内部超球面向量必须具备浅层与深层不可变性");
    }

    @Test
    @DisplayName("断言二：验证低分劣质步骤拦截、触发回溯与三级冷备仓软隔离")
    void testLowQualityStepTriggerBacktrackAndColdIsolation() {
        StepProcessRewardEvaluator evaluator = new StepProcessRewardEvaluator(0.60, SECRET_KEY);
        TestTimeBacktrackingEngine engine = new TestTimeBacktrackingEngine(evaluator, SECRET_KEY);

        float[] uTarget = createUnitVector(2001);
        float[] vBad = createUnitVector(9999); // 正交向量，相关性极低

        // 模拟异常劣质推导步骤：契约违规 + 因果混乱 + 语义自相矛盾
        StepProcessRewardReceipt badReceipt = evaluator.evaluateStep(
                "session_contract_02",
                1,
                "步骤 1: 发现参数不对，计算错误，无法匹配依赖，尝试非法未授权调用...",
                uTarget,
                vBad,
                false, // 契约违规
                false, // 因果混乱
                0.25   // 低自省分
        );

        assertEquals(StepProcessRewardReceipt.StepEvaluationStatus.TRIGGER_BACKTRACK, badReceipt.status(),
                "低分劣质步骤应触发 TRIGGER_BACKTRACK");
        assertTrue(badReceipt.confidenceScore() < 0.60, "低分劣质步骤得分应 < 0.60，实际: " + badReceipt.confidenceScore());
        assertTrue(badReceipt.verifySignature(SECRET_KEY), "低分凭单签名亦须完整有效");

        // 执行回溯隔离
        TestTimeBacktrackingEngine.BacktrackingDecisionReceipt btReceipt = engine.executeBacktracking(
                "session_contract_02",
                1,
                badReceipt,
                List.of(),
                uTarget
        );

        assertNotNull(btReceipt);
        assertTrue(btReceipt.verifySignature(SECRET_KEY), "回溯决策凭单验签必须通过");
        assertFalse(engine.getColdBufferSnapshot().isEmpty(), "失败剪枝步骤必须转储入三级冷备环形仓");
        assertEquals("session_contract_02", engine.getColdBufferSnapshot().get(0).sessionId());
    }

    @Test
    @DisplayName("断言三：验证有界局部 MCTS 回溯状态机（D_max <= 2, B <= 3）与超球面加权 UCB1 自愈")
    void testBoundedLocalMctsBacktrackingAndUcb1SelfHealing() {
        StepProcessRewardEvaluator evaluator = new StepProcessRewardEvaluator(0.60, SECRET_KEY);
        TestTimeBacktrackingEngine engine = new TestTimeBacktrackingEngine(evaluator, SECRET_KEY);

        String sessionId = "session_contract_03";
        float[] uTarget = createUnitVector(3001);

        // 1. 记录 Step 0 为成功基准检查点
        float[] vStep0 = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uTarget, 0.95, 3001L);
        StepProcessRewardReceipt step0Receipt = evaluator.evaluateStep(
                sessionId, 0, "步骤 0: 系统会话与鉴权初始化",
                uTarget, vStep0, true, true, 0.95
        );
        assertEquals(StepProcessRewardReceipt.StepEvaluationStatus.PASSED, step0Receipt.status());

        // 准备 2 个备选动作分支（Branch Factor B <= 3）
        float[] vAlt1 = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uTarget, 0.94, 3002L);
        float[] vAlt2 = createUnitVector(7777); // 较弱的备选

        TestTimeBacktrackingEngine.CandidateAction altAction1 = new TestTimeBacktrackingEngine.CandidateAction(
                "action:verified_idempotent_query",
                "优质备选分支: 调用只读幂等安全网关进行前置校验",
                vAlt1,
                0.92,
                0,
                0.0
        );

        TestTimeBacktrackingEngine.CandidateAction altAction2 = new TestTimeBacktrackingEngine.CandidateAction(
                "action:unverified_brute_force",
                "弱备选分支: 暴力并发重试",
                vAlt2,
                0.40,
                1,
                0.2
        );

        engine.recordPassedStep(sessionId, 0, step0Receipt.stepContent(), step0Receipt, List.of(altAction1, altAction2));

        // 2. 模拟 Step 1 发生幻觉故障
        float[] vFail = createUnitVector(8888);
        StepProcessRewardReceipt failedReceipt = evaluator.evaluateStep(
                sessionId, 1, "步骤 1: 发生数据竞争与脏写违规",
                uTarget, vFail, false, true, 0.30
        );
        assertEquals(StepProcessRewardReceipt.StepEvaluationStatus.TRIGGER_BACKTRACK, failedReceipt.status());

        // 3. 触发局部回溯
        TestTimeBacktrackingEngine.BacktrackingDecisionReceipt btReceipt = engine.executeBacktracking(
                sessionId,
                1,
                failedReceipt,
                List.of(altAction1, altAction2),
                uTarget
        );

        assertNotNull(btReceipt);
        assertEquals(0, btReceipt.rollbackTargetIndex(), "局部回溯目标步骤应为 Step 0");
        assertTrue(btReceipt.backtrackDepth() <= TestTimeBacktrackingEngine.MAX_BACKTRACK_DEPTH, "回溯深度必须 <= D_max (2)");
        assertEquals("action:verified_idempotent_query", btReceipt.selectedActionKey(), "超球面加权 UCB1 必须精准命中优质分支 altAction1");
        assertTrue(btReceipt.healed(), "通过优质备选分支自愈必须成功");
        assertTrue(btReceipt.revisedScore() >= 0.60, "修正后得分必须达到门禁要求 >= 0.60，实际: " + btReceipt.revisedScore());
        assertTrue(btReceipt.verifySignature(SECRET_KEY), "回溯决策凭单签名必须有效");

        // 4. 验证防死循环熔断机制：连续回溯达到 4 次（超过 K_max = 3）
        engine.executeBacktracking(sessionId, 1, failedReceipt, List.of(), uTarget);
        engine.executeBacktracking(sessionId, 1, failedReceipt, List.of(), uTarget);
        TestTimeBacktrackingEngine.BacktrackingDecisionReceipt fallbackReceipt = engine.executeBacktracking(
                sessionId, 1, failedReceipt, List.of(), uTarget
        );
        assertNotNull(fallbackReceipt);
        assertEquals("FAIL_OPEN_FALLBACK", fallbackReceipt.selectedActionKey(), "超额回溯必须执行 Fail-Open 降级");
        assertFalse(fallbackReceipt.healed());
    }

    @Test
    @DisplayName("断言四：验证常驻守护进程物理探针 STEP_PRM_EVALUATION 与 TEST_TIME_BACKTRACKING 端到端分发")
    void testDaemonProbeDispatcherEndToEndDispatch() {
        // 1. 验证 STEP_PRM_EVALUATION 探针
        String prmPayload = """
                {
                    "probe_type": "STEP_PRM_EVALUATION",
                    "task_id": "probe_task_prm_e2e"
                }
                """;
        String prmResponse = DaemonProbeDispatcher.dispatch(prmPayload);
        assertNotNull(prmResponse);
        assertTrue(prmResponse.contains("\"status\":\"SUCCESS\""), "PRM 探针响应状态必须为 SUCCESS");
        assertTrue(prmResponse.contains("\"require_prm_step_gated_hold\":true"), "PRM 门禁物理断言必须为 true");
        assertTrue(prmResponse.contains("\"high_score_passed\":true"), "优质推理步骤必须 PASSED");
        assertTrue(prmResponse.contains("\"low_score_backtracked\":true"), "劣质推理步骤必须 TRIGGER_BACKTRACK");
        assertTrue(prmResponse.contains("\"seal_verified\":true"), "PRM 签名自验真必须通过");

        // 2. 验证 TEST_TIME_BACKTRACKING 探针
        String btPayload = """
                {
                    "probe_type": "TEST_TIME_BACKTRACKING",
                    "task_id": "probe_task_mcts_e2e"
                }
                """;
        String btResponse = DaemonProbeDispatcher.dispatch(btPayload);
        assertNotNull(btResponse);
        assertTrue(btResponse.contains("\"status\":\"SUCCESS\""), "回溯探针响应状态必须为 SUCCESS");
        assertTrue(btResponse.contains("\"require_bounded_mcts_healed_hold\":true"), "回溯自愈物理断言必须为 true");
        assertTrue(btResponse.contains("\"backtrack_triggered\":true"), "回溯动作必须被触发");
        assertTrue(btResponse.contains("\"healed_success\":true"), "自愈必须成功");
        assertTrue(btResponse.contains("\"seal_verified\":true"), "回溯凭单验签必须通过");
    }
}
