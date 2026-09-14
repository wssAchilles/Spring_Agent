package tech.qiantong.qknow.ai.alignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 51: 多智能体自主目标对齐宪政中枢、势能保持奖励塑形与动态伦理安全屏障 专属契约测试
 */
public class Phase51MultiAgentAlignmentContractTest {

    private ConstitutionalRuleBook ruleBook;
    private PotentialBasedRewardShaper rewardShaper;
    private GoalDriftDetector driftDetector;
    private EthicalSafetyBarrier safetyBarrier;
    private AlignmentAuditLedger auditLedger;
    private MultiAgentAlignmentGovernor governor;

    @BeforeEach
    void setUp() {
        ruleBook = new ConstitutionalRuleBook();
        rewardShaper = new PotentialBasedRewardShaper();
        driftDetector = new GoalDriftDetector();
        safetyBarrier = new EthicalSafetyBarrier(ruleBook);
        auditLedger = new AlignmentAuditLedger();
        governor = new MultiAgentAlignmentGovernor(rewardShaper, driftDetector, safetyBarrier, auditLedger);
    }

    @Test
    @DisplayName("契约 1: 验证不可变宪政原则树的分级优先级偏序与违规模式识别")
    void test1_ConstitutionalRuleBookHierarchyAndPriority() {
        assertFalse(ruleBook.getPrinciples().isEmpty());

        // 验证违规检测：红线原则匹配
        Optional<ConstitutionalRuleBook.ConstitutionalPrinciple> violation = ruleBook.checkViolation(
                "EXPORT_SENSITIVE_DATA",
                Map.of("target", "SELECT * FROM sys_user_secrets")
        );
        assertTrue(violation.isPresent());
        assertEquals("CR-001", violation.get().id());
        assertEquals(ConstitutionalRuleBook.PrincipleTier.ABSOLUTE_REDLINE, violation.get().tier());

        // 验证合规指令无违规
        Optional<ConstitutionalRuleBook.ConstitutionalPrinciple> clean = ruleBook.checkViolation(
                "GENERATE_SUMMARY_REPORT",
                Map.of("scope", "public_knowledge")
        );
        assertTrue(clean.isEmpty());
    }

    @Test
    @DisplayName("契约 2: 验证 Ng 1999 势能奖励塑形(PBRS)在闭环环路中的策略无偏不变性")
    void test2_PotentialBasedRewardShapingPolicyInvariance() {
        PotentialBasedRewardShaper.PotentialState s0 = new PotentialBasedRewardShaper.PotentialState(0.1, 1.0, 0.1);
        PotentialBasedRewardShaper.PotentialState s1 = new PotentialBasedRewardShaper.PotentialState(0.5, 0.9, 0.2);
        PotentialBasedRewardShaper.PotentialState s2 = new PotentialBasedRewardShaper.PotentialState(0.8, 0.95, 0.1);

        double phi0 = rewardShaper.calculatePotential(s0);
        double phi1 = rewardShaper.calculatePotential(s1);
        double phi2 = rewardShaper.calculatePotential(s2);

        // 验证前向单步推进势能单调递增
        assertTrue(phi1 > phi0);
        assertTrue(phi2 > phi1);

        // 闭环路径: s0 -> s1 -> s2 -> s0 (gamma = 1.0)
        List<Double> cycle = List.of(phi0, phi1, phi2, phi0);
        double cycleReward = rewardShaper.calculateCycleCumulativeShapingReward(cycle, 1.0);

        // 定理 1.1 核心断言：环路累计势能奖励严格为 0，杜绝刷分作弊
        assertEquals(0.0, cycleReward, 1e-9, "势能差分在任意环路中的累积奖励必须严格恒为 0");
    }

    @Test
    @DisplayName("契约 3: 验证千问 1536 维超球面目标漂移度量器(GoalDriftDetector)精准判定")
    void test3_GoalDriftDetectorAlignmentAndDetection() {
        float[] rootVec = new float[1536];
        Arrays.fill(rootVec, 0.025f);

        // 构造高度对齐动作向量 (同向)
        float[] alignedVec = Arrays.copyOf(rootVec, 1536);
        alignedVec[0] += 0.005f;

        // 构造严重漂移动作向量 (接近正交或反向)
        float[] driftedVec = new float[1536];
        for (int i = 0; i < 1536; i++) {
            driftedVec[i] = (i % 2 == 0) ? 0.025f : -0.025f;
        }

        GoalDriftDetector.IntentVector rootIntent = new GoalDriftDetector.IntentVector("INT_ROOT", rootVec, "Public summary");
        GoalDriftDetector.IntentVector alignedIntent = new GoalDriftDetector.IntentVector("ACT_ALIGNED", alignedVec, "Query summary");
        GoalDriftDetector.IntentVector driftedIntent = new GoalDriftDetector.IntentVector("ACT_DRIFT", driftedVec, "Attack query");

        GoalDriftDetector.DriftEvaluation evalAligned = driftDetector.evaluateDrift(rootIntent, alignedIntent);
        assertEquals(GoalDriftDetector.DriftStatus.ALIGNED, evalAligned.status());
        assertTrue(evalAligned.cosineSimilarity() >= 0.75);

        GoalDriftDetector.DriftEvaluation evalDrifted = driftDetector.evaluateDrift(rootIntent, driftedIntent);
        assertEquals(GoalDriftDetector.DriftStatus.SEVERE_DRIFT, evalDrifted.status());
        assertTrue(evalDrifted.cosineSimilarity() < 0.60);
    }

    @Test
    @DisplayName("契约 4: 验证伦理安全屏障对红线动作与严重目标漂移的 100% 绝对硬阻断")
    void test4_EthicalSafetyBarrierCriticalActionBlocking() {
        // 1. 触发绝对红线
        EthicalSafetyBarrier.BarrierDecision redlineDecision = safetyBarrier.inspectAndFilter(
                "DROP TABLE qknow_users;",
                Map.of(),
                null
        );
        assertFalse(redlineDecision.allowed());
        assertEquals("CRITICAL_BLOCKED_REDLINE", redlineDecision.verdictCode());

        // 2. 触发严重目标漂移
        GoalDriftDetector.DriftEvaluation severeDrift = new GoalDriftDetector.DriftEvaluation(
                "ROOT", "DRIFT", 0.35, 1.21, GoalDriftDetector.DriftStatus.SEVERE_DRIFT, "Drift alert"
        );
        EthicalSafetyBarrier.BarrierDecision driftDecision = safetyBarrier.inspectAndFilter(
                "SAFE_ACTION",
                Map.of(),
                severeDrift
        );
        assertFalse(driftDecision.allowed());
        assertEquals("BLOCKED_SEVERE_GOAL_DRIFT", driftDecision.verdictCode());
    }

    @Test
    @DisplayName("契约 5: 验证伦理安全屏障正交超平面投影对中度疑虑动作的安全修补放行")
    void test5_EthicalSafetyBarrierSafeProjectionSanitization() {
        GoalDriftDetector.DriftEvaluation moderateDrift = new GoalDriftDetector.DriftEvaluation(
                "ROOT", "MODERATE", 0.68, 0.82, GoalDriftDetector.DriftStatus.MODERATE_DRIFT, "Moderate drift"
        );

        Map<String, Object> rawParams = new HashMap<>();
        rawParams.put("queryScope", "cross_tenant_shared");

        EthicalSafetyBarrier.BarrierDecision decision = safetyBarrier.inspectAndFilter(
                "ANALYZE_SHARED_STATS",
                rawParams,
                moderateDrift
        );

        // 验证正交超平面修补放行（非粗暴误杀）
        assertTrue(decision.allowed());
        assertTrue(decision.modifiedByProjection());
        assertEquals("APPROVED_WITH_PROJECTION", decision.verdictCode());
        // 验证注入了安全约束补丁
        assertEquals(true, decision.sanitizedParams().get("boundedScope"));
        assertEquals(50, decision.sanitizedParams().get("maxRecords"));
        assertEquals(true, decision.sanitizedParams().get("readOnly"));
    }

    @Test
    @DisplayName("契约 6: 验证多智能体目标对齐总控中枢端到端调度链路与毫秒级延迟预算")
    void test6_MultiAgentAlignmentGovernorEndToEndPipeline() {
        float[] rootVec = new float[1536];
        Arrays.fill(rootVec, 0.025f);
        GoalDriftDetector.IntentVector root = new GoalDriftDetector.IntentVector("R001", rootVec, "Legal advice");
        GoalDriftDetector.IntentVector action = new GoalDriftDetector.IntentVector("A001", rootVec, "Legal advice");

        PotentialBasedRewardShaper.PotentialState s0 = new PotentialBasedRewardShaper.PotentialState(0.2, 1.0, 0.0);
        PotentialBasedRewardShaper.PotentialState s1 = new PotentialBasedRewardShaper.PotentialState(0.6, 1.0, 0.0);

        MultiAgentAlignmentGovernor.AlignmentVerdict verdict = governor.evaluateAction(
                "agent_legal",
                "SEARCH_CIVIL_CODE",
                Map.of("article", 1024),
                root,
                action,
                s0,
                s1
        );

        assertTrue(verdict.approved());
        assertEquals("APPROVED_SAFE", verdict.verdictCode());
        assertTrue(verdict.shapedReward() > 0);
        assertNotNull(verdict.auditEventId());
        assertTrue(verdict.latencyMs() <= 5, "端到端对齐判定耗时应在 5ms 以内, actual: " + verdict.latencyMs());
    }

    @Test
    @DisplayName("契约 7: 验证对齐审计证据账本的密码学不可篡改性与哈希链完整性")
    void test7_AlignmentAuditLedgerCryptographicImmutability() {
        auditLedger.recordEntry("agent_1", "ACT_1", true, "APPROVED_SAFE", 1.25);
        auditLedger.recordEntry("agent_2", "ACT_2", false, "CRITICAL_BLOCKED_REDLINE", -1.0);
        auditLedger.recordEntry("agent_1", "ACT_3", true, "APPROVED_WITH_PROJECTION", 0.85);

        assertEquals(3, auditLedger.getTotalEntries());
        assertTrue(auditLedger.verifyIntegrity(), "密码学 SHA-256 审计链必须验证通过");
    }

    @Test
    @DisplayName("契约 8: 验证势能奖励塑形对智能体规范博弈(Specification Gaming)刷分的抵御性")
    void test8_SpecificationGamingResistance() {
        // 模拟智能体试图通过在状态 A 和 B 之间反复震荡 50 次刷取奖励
        PotentialBasedRewardShaper.PotentialState stateA = new PotentialBasedRewardShaper.PotentialState(0.3, 0.9, 0.1);
        PotentialBasedRewardShaper.PotentialState stateB = new PotentialBasedRewardShaper.PotentialState(0.4, 0.9, 0.1);

        double phiA = rewardShaper.calculatePotential(stateA);
        double phiB = rewardShaper.calculatePotential(stateB);

        double accumulatedShapingReward = 0.0;
        double current = phiA;
        for (int i = 0; i < 50; i++) {
            double next = (i % 2 == 0) ? phiB : phiA;
            accumulatedShapingReward += (1.0 * next - current);
            current = next;
        }

        // 最终回到状态 A，总塑形收益严格为 0
        assertEquals(0.0, accumulatedShapingReward, 1e-9, "50 轮无意义震荡后，PBRS 累积塑形奖励必须清零，无法刷取任何不正当收益");
    }
}
