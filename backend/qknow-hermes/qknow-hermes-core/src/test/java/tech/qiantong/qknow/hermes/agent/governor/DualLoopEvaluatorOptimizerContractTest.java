package tech.qiantong.qknow.hermes.agent.governor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.SupervisorAgent;
import tech.qiantong.qknow.hermes.agent.governor.impl.DualLoopEvaluatorOptimizerEngine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 双环 Evaluator-Optimizer 自优化契约测试套件 (DualLoopEvaluatorOptimizerContractTest)
 * 验证内环确定性规则毫秒级自愈、外环李雅普诺夫缺陷势能收敛 (T <= 2 轮)、预算断路器保护与防时序侧信道凭单。
 */
public class DualLoopEvaluatorOptimizerContractTest {

    @Test
    @DisplayName("EXP-DUAL-01: 验证内环 DeterministicRuleHealer 针对未闭合代码、标题格式与占位符的毫秒级自愈")
    void testInnerLoopDeterministicRuleHealer() {
        DeterministicRuleHealer healer = new DeterministicRuleHealer();

        String defectiveText = """
                #测试标题缺少空格
                这里是一个代码片段：
                ```java
                public class Hello {
                    // <待补充> 实现逻辑
                }
                """; // 缺少结尾的 ```

        DeterministicRuleHealer.RuleHealingResult result = healer.heal(defectiveText);

        assertNotNull(result);
        assertTrue(result.fixedCount() >= 2, "必须修复至少 2 项语法与格式缺陷");
        assertTrue(result.appliedRules().contains("RULE_CLOSE_UNTERMINATED_CODE_FENCE"), "必须自动闭合代码块");
        assertTrue(result.appliedRules().contains("RULE_NORMALIZE_MARKDOWN_HEADINGS"), "必须规范化标题空格");

        String healed = result.healedContent();
        assertTrue(healed.contains("# 测试标题缺少空格"), "标题后必须补充空格");
        assertTrue(healed.endsWith("```"), "代码块必须在文本末尾闭合");
        assertFalse(healed.contains("<待补充>"), "占位符必须被消除");
    }

    @Test
    @DisplayName("EXP-DUAL-02: 验证外环 Evaluator-Optimizer 在两轮以内成功收敛并通过阈值 (T <= 2)")
    void testOuterLoopConvergenceWithinTwoRounds() {
        DualLoopEvaluatorOptimizerEngine engine = new DualLoopEvaluatorOptimizerEngine(null, 85.0);

        String task = "请输出具备结构化排版的完整企业级研发总结报告";
        // 初始简陋候选（缺少结构化排版和详细内容）
        String rawCandidate = "研发总结报告：今日完成了基本模块开发。";

        EvaluatorOptimizerGovernor.OptimizationExecutionResult result =
                engine.executeOptimizationLoop("sess-conv-01", task, rawCandidate);

        assertNotNull(result);
        assertNotNull(result.receipt());
        DualLoopQualityReceipt receipt = result.receipt();

        assertTrue(receipt.outerLoopRounds() <= 2, "外环自优化迭代轮次必须严格 <= 2，遵循李雅普诺夫收敛上界");
        assertTrue(receipt.evaluatorScore() >= 85.0, "最终优化得分必须达到或超越 85.0 分");
        assertTrue(receipt.passedThreshold(), "质检结果必须标记为通过");
        assertEquals("OPTIMIZED_PASS", receipt.terminationReason());
        assertTrue(receipt.verifySignature(), "自签名凭单必须通过验证");
    }

    @Test
    @DisplayName("EXP-DUAL-03: 验证极度不可达目标在第 2 轮后触发 CIRCUIT_BREAKER_DEGRADED 断路保护")
    void testCircuitBreakerWhenUnconverged() {
        // 设置极度苛刻达标线 99.0，模拟离线规则启发式无法达到的超高阈值
        DualLoopEvaluatorOptimizerEngine engine = new DualLoopEvaluatorOptimizerEngine(null, 99.0);

        String task = "极其复杂不可达的终极指令";
        String initialCandidate = "基础文本";

        EvaluatorOptimizerGovernor.OptimizationExecutionResult result =
                engine.executeOptimizationLoop("sess-break-01", task, initialCandidate);

        assertNotNull(result);
        DualLoopQualityReceipt receipt = result.receipt();

        // 验证硬编码最大轮次退出，绝对不发生死循环
        assertEquals(2, receipt.outerLoopRounds(), "外环必须在达到最大轮次 2 后断路退出");
        assertFalse(receipt.passedThreshold(), "未达 99.0 分不应标记通过");
        assertEquals("CIRCUIT_BREAKER_DEGRADED", receipt.terminationReason(), "必须触发降级断路器");
        assertTrue(receipt.verifySignature());
    }

    @Test
    @DisplayName("EXP-DUAL-04: 验证 DualLoopQualityReceipt 防时序侧信道签名与篡改阻断")
    void testDualLoopQualityReceiptSignatureAndTamperResistance() {
        DualLoopQualityReceipt receipt = DualLoopQualityReceipt.createSigned(
                "dlqr-9988",
                "sess-9988",
                "hash-init-abc",
                "hash-opt-xyz",
                2,
                1,
                92.5,
                true,
                "OPTIMIZED_PASS",
                List.of("修复了格式", "补充了细节"),
                System.currentTimeMillis()
        );

        assertTrue(receipt.verifySignature(), "正常凭单签名校验必须成功");

        // 篡改评分为 100.0
        DualLoopQualityReceipt tampered = new DualLoopQualityReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.initialCandidateHash(),
                receipt.finalOptimizedHash(),
                receipt.innerLoopFixedCount(),
                receipt.outerLoopRounds(),
                100.0, // 恶意篡改
                receipt.passedThreshold(),
                receipt.terminationReason(),
                receipt.diagnosticFindings(),
                receipt.sha256Signature(),
                receipt.timestamp()
        );

        assertFalse(tampered.verifySignature(), "篡改凭单校验必须失败");
    }

    @Test
    @DisplayName("EXP-DUAL-05: 验证 SupervisorAgent 全流程集成双环质检并存证 DualLoopQualityReceipt")
    void testSupervisorAgentDualLoopIntegration() {
        // 构造 SupervisorAgent（无模型注入，使用离线双环引擎）
        SupervisorAgent supervisor = new SupervisorAgent(
                "TestSupervisor",
                "蜂群主管测试",
                "你是一个专业主管",
                List.of(),
                null,
                List.of()
        );

        String sessionId = "test-sup-dual-loop-" + System.currentTimeMillis();
        String question = "请综合各个模块给出企业级系统架构进展汇报";

        String finalAnswer = supervisor.chat(question, Map.of("sessionId", sessionId));

        assertNotNull(finalAnswer);
        assertFalse(finalAnswer.isBlank());

        // 验证已产生并存证双环质检凭单
        Optional<DualLoopQualityReceipt> receiptOpt = supervisor.getLastQualityReceipt(sessionId);
        assertTrue(receiptOpt.isPresent(), "SupervisorAgent 执行完毕后必须持有该会话的双环质检凭单");

        DualLoopQualityReceipt receipt = receiptOpt.get();
        assertEquals(sessionId, receipt.sessionId());
        assertTrue(receipt.evaluatorScore() >= 80.0, "经过自优化后评分必须显著提升");
        assertTrue(receipt.verifySignature(), "凭单验签必须通过");
    }
}
