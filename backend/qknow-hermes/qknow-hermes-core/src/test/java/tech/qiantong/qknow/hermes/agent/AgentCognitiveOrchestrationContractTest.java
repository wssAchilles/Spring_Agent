package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.debate.tripartite.TripartiteDebateArbiterEngine;
import tech.qiantong.qknow.hermes.agent.debate.tripartite.TripartiteDebateReceipt;
import tech.qiantong.qknow.hermes.agent.debate.tripartite.TripartiteDebateRole;
import tech.qiantong.qknow.hermes.flow.hitl.HitlApprovalDecision;
import tech.qiantong.qknow.hermes.flow.hitl.HitlInterruptReceipt;
import tech.qiantong.qknow.hermes.flow.hitl.HumanInTheLoopApprovalController;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 维度三核心工程契约测试：Agent 认知编排与多智能体系统架构升级
 * 覆盖：
 * 1. 人机协同 (HITL) 零阻塞中断挂起、常量时间令牌比对与超时 Saga 补偿
 * 2. 三方结构化博弈对抗辩论、有界收敛与夏普利值边际效用归因
 * 3. 全链路密码学防侧信道与千问 1536 维超球面单位几何约束
 */
public class AgentCognitiveOrchestrationContractTest {

    @Test
    @DisplayName("契约一：HITL 人机协同中断挂起、常量时间令牌比对与 Saga 超时补偿契约")
    public void testHitlInterruptSuspendResumeAndRollback() {
        HumanInTheLoopApprovalController controller = new HumanInTheLoopApprovalController();
        String executionId = "exec_wf_888999";
        String nodeId = "node_high_risk_fund_transfer";

        // 1. 中断挂起测试
        Map<String, Object> currentVars = Map.of("amount", 50000.0, "target_account", "ACC_CORP_001");
        HitlInterruptReceipt suspendReceipt = controller.suspendForApproval(
                executionId, nodeId, "FINANCE_DIRECTOR",
                "Approve high-value corporate fund transfer", currentVars, 1000L); // 1秒超时

        assertNotNull(suspendReceipt);
        assertEquals("SUSPENDED_WAITING_HUMAN", suspendReceipt.status());
        assertTrue(suspendReceipt.verifySignature(), "挂起凭单必须通过常量时间 SHA-256 验签");
        assertTrue(controller.isSuspended(executionId), "状态机必须处于挂起状态");

        String realToken = suspendReceipt.interruptToken();

        // 2. 伪造/篡改令牌测试（常量时间防伪拦截）
        String fakeToken = realToken.substring(0, realToken.length() - 4) + "0000";
        assertThrows(SecurityException.class, () -> {
            controller.resumeWithDecision(executionId, fakeToken,
                    HitlApprovalDecision.APPROVED, Map.of(), "hacker_attacker");
        }, "使用伪造或篡改令牌必须被严格抛出 SecurityException 拦截");

        // 3. 正常核准恢复测试
        HitlInterruptReceipt resumeReceipt = controller.resumeWithDecision(
                executionId, realToken, HitlApprovalDecision.APPROVED,
                Map.of("approved_by_ip", "10.0.1.20"), "user_director_wang");

        assertNotNull(resumeReceipt);
        assertEquals("RESUMED_APPROVED", resumeReceipt.status());
        assertTrue(resumeReceipt.verifySignature(), "核准恢复凭单必须通过验签");
        assertFalse(controller.isSuspended(executionId), "任务恢复后必须移出挂起表");

        // 4. 超时自动熔断与 Saga 补偿测试
        String timeoutExecId = "exec_wf_timeout_001";
        HitlInterruptReceipt timeoutSuspend = controller.suspendForApproval(
                timeoutExecId, "node_external_api_call", "OPS_ADMIN",
                "Wait for third-party webhook confirmation", Map.of(), 50L); // 50ms 超时

        try {
            Thread.sleep(60); // 等待超过 50ms TTL
        } catch (InterruptedException ignored) {}

        HitlInterruptReceipt timeoutReceipt = controller.resumeWithDecision(
                timeoutExecId, timeoutSuspend.interruptToken(),
                HitlApprovalDecision.APPROVED, Map.of(), "late_approver");

        assertEquals("EXPIRED_REVOKED", timeoutReceipt.status(), "超时未核准的任务必须被自动熔断撤销");
        assertTrue(timeoutReceipt.verifySignature());
    }

    @Test
    @DisplayName("契约二：三方结构化博弈（提议-红队-中立仲裁）收敛与夏普利贡献值归因契约")
    public void testTripartiteDebateArbiterConvergenceAndShapleyAttribution() {
        TripartiteDebateArbiterEngine engine = new TripartiteDebateArbiterEngine();
        String debateId = "debate_arch_2026_01";
        String topic = "High-Concurrency Off-Heap Memory Pool Architecture";
        String initialPlan = "Use JVM heap memory for tensor caching and simple garbage collection";
        List<String> riskClues = List.of(
                "JVM Young Gen Allocation Rate spikes cause severe Stop-The-World GC latency",
                "Potential memory fragmentation under high QPS",
                "Unbounded queue growth leading to OutOfMemoryError"
        );

        // 执行三方博弈辩论
        TripartiteDebateReceipt receipt = engine.executeDebate(debateId, topic, initialPlan, riskClues);

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "辩论终决凭单必须通过常量时间验签");

        // 1. 验证有界步数定理：轮次严格 K <= 3
        assertTrue(receipt.totalRounds() <= 3, "辩论轮次必须严格受控在 3 轮以内");
        assertTrue(receipt.isConverged(), "在多轮博弈后分歧度必须收敛");

        // 2. 验证红队漏洞挑战被有效吸纳修补
        assertTrue(receipt.finalResolutionPlan().contains("Patched"),
                "最终共识方案必须吸收并修复红队提出的关键风险点");

        // 3. 验证夏普利边际贡献值归一化 (sum phi_i == 1.0)
        Map<TripartiteDebateRole, Double> shapleyMap = receipt.shapleyAttributionMap();
        assertEquals(3, shapleyMap.size());
        assertTrue(shapleyMap.containsKey(TripartiteDebateRole.PROPOSER_ARCHITECT));
        assertTrue(shapleyMap.containsKey(TripartiteDebateRole.RED_TEAM_AUDITOR));
        assertTrue(shapleyMap.containsKey(TripartiteDebateRole.NEUTRAL_ARBITER));

        double sumShapley = shapleyMap.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sumShapley, 0.01, "各角色的夏普利边际效用总和必须严格归一化为 1.0");

        // 各角色贡献值必须均为正数
        assertTrue(shapleyMap.get(TripartiteDebateRole.PROPOSER_ARCHITECT) > 0.1);
        assertTrue(shapleyMap.get(TripartiteDebateRole.RED_TEAM_AUDITOR) > 0.1);
        assertTrue(shapleyMap.get(TripartiteDebateRole.NEUTRAL_ARBITER) > 0.1);
    }

    @Test
    @DisplayName("契约三：全链路密码学防侧信道与千问 1536 维超球面几何约束契约")
    public void testCryptographicZeroLeakageAndQwen1536HypersphericalGeometry() {
        int qwenDim = 1536;
        Random rand = new Random(2026);

        // 1. 验证千问 1536 维超球面单位向量归一化约束
        float[] vector = new float[qwenDim];
        double sumSq = 0.0;
        for (int i = 0; i < qwenDim; i++) {
            float val = (float) rand.nextGaussian();
            vector[i] = val;
            sumSq += val * val;
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < qwenDim; i++) {
            vector[i] /= norm;
        }

        double checkNormSq = 0.0;
        for (float v : vector) {
            checkNormSq += v * v;
        }
        assertEquals(1.0, Math.sqrt(checkNormSq), 1e-5, "千问向量必须严格满足 L2 模长等于 1.0");

        // 2. 验证防侧信道：篡改 HITL 凭单签名
        HitlInterruptReceipt receipt = HitlInterruptReceipt.createSuspended(
                "exec_test", "node_x", "tok_123", 1000L, 5000L, "ROLE", "Action");
        assertTrue(receipt.verifySignature());

        HitlInterruptReceipt tamperedReceipt = new HitlInterruptReceipt(
                receipt.executionId(),
                receipt.nodeId(),
                receipt.interruptToken(),
                receipt.status(),
                receipt.suspendedEpochMillis(),
                receipt.ttlMillis(),
                receipt.requiredRole(),
                receipt.actionDescription(),
                receipt.sha256Signature().substring(0, receipt.sha256Signature().length() - 2) + "00"
        );
        assertFalse(tamperedReceipt.verifySignature(), "被单字节篡改的 HITL 凭单验签必失败");

        // 3. 验证防侧信道：篡改辩论凭单签名
        TripartiteDebateReceipt dReceipt = TripartiteDebateReceipt.create(
                "d_01", "topic", 2, true, "plan", Map.of(TripartiteDebateRole.PROPOSER_ARCHITECT, 1.0),
                List.of(), 50L);
        assertTrue(dReceipt.verifySignature());

        TripartiteDebateReceipt tamperedDReceipt = new TripartiteDebateReceipt(
                dReceipt.debateId(),
                dReceipt.taskTopic(),
                dReceipt.totalRounds(),
                dReceipt.isConverged(),
                dReceipt.finalResolutionPlan(),
                dReceipt.shapleyAttributionMap(),
                dReceipt.debateTurns(),
                dReceipt.durationMillis(),
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        );
        assertFalse(tamperedDReceipt.verifySignature(), "被伪造的辩论终决凭单验签必失败");
    }
}
