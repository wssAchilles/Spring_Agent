package tech.qiantong.qknow.hermes.agent.harness.offline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.*;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermes Harness 阶段三离线受控 Propose–Probe–Compose 闭环与回放契约测试。
 *
 * <p>精确覆盖 7 大验收契约单测：
 * 1. 基准任务集完整性与不可变性；
 * 2. 变异安全性与前缀缓存友好校验；
 * 3. 探针执行差异精准提取；
 * 4. 预算门禁硬拦截 (ΔTokens <= 0, ΔDuration <= 5ms)；
 * 5. 双向交叉盲审位置偏置免疫；
 * 6. 防数据泄漏静态雷区拦截；
 * 7. 离线凭单确定性常量时间回放验签。</p>
 */
@DisplayName("Hermes Harness 阶段三离线受控 PPC 契约测试套件")
class HermesHarnessStage3OfflineLoopTest {

    private HermesHarnessVoucher createBaselineVoucher() {
        return new HermesHarnessVoucher(
                "v-base-test",
                1001L,
                "1.0.0-BASE",
                Instant.EPOCH,
                new InvariantSystemPromptRecord(
                        "企业级知识中枢核心智能体角色，负责多工具调用与语义检索。",
                        "必须严格遵守知识库事实，严禁虚构数值或编造不存在的接口字段。",
                        "严禁执行未经审核的资金冲正与写操作。"
                ),
                GuardrailsSpecificationRecord.defaults(),
                MemoryContextStrategyRecord.defaults(),
                ToolGuidancePolicyRecord.defaults(),
                SubagentsTopologyRecord.singleAgent(),
                EnterpriseSkillBankRecord.empty(),
                SandboxedExecutionPlanRecord.declarative(),
                McpMiddlewarePluginsRecord.defaults()
        );
    }

    private HarnessEvidenceRecord createSampleEvidence(
            String evidenceId,
            long tokens,
            long durationMs,
            String toolName,
            boolean toolSuccess) {
        return createSampleEvidence(evidenceId, tokens, durationMs, toolName, toolSuccess, UsageStatus.KNOWN);
    }

    private HarnessEvidenceRecord createSampleEvidence(
            String evidenceId,
            long tokens,
            long durationMs,
            String toolName,
            boolean toolSuccess,
            UsageStatus usageStatus) {

        List<HarnessEvidenceRecord.ToolExecutionEntry> tools = toolName == null
                ? List.of()
                : List.of(new HarnessEvidenceRecord.ToolExecutionEntry(
                        toolName,
                        durationMs / 2,
                        toolSuccess,
                        "a1b2c3d4e5f60718",
                        "b2c3d4e5f60718a1"
                ));

        return new HarnessEvidenceRecord(
                evidenceId,
                "req-test-01",
                1001L,
                "voucher-fingerprint-sample",
                "1.0.0-BASE",
                Instant.EPOCH,
                "测试用户请求摘要",
                2,
                0.92,
                tools,
                tokens,
                (tokens * 1.5) / 1_000_000.0,
                durationMs,
                "测试智能体最终答复输出摘要",
                HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE,
                usageStatus,
                "",
                false,
                0,
                RagAttributionSummary.of(2, 0, 0.92)
        );
    }

    @Test
    @DisplayName("契约 1: 验证 5 个离线小样本基准任务用例的不可变性与完整性")
    void testBenchmarkSuiteIntegrity() {
        List<OfflineBenchmarkTestCase> suite = OfflineBenchmarkTestCase.createDefaultSuite();
        assertEquals(5, suite.size(), "必须精确包含 5 个代表性离线业务用例");

        for (OfflineBenchmarkTestCase tc : suite) {
            assertNotNull(tc.taskId(), "taskId 不能为空");
            assertNotNull(tc.category(), "category 不能为空");
            assertNotNull(tc.question(), "question 不能为空");
            assertTrue(tc.minContextTokensThreshold() > 0, "Token 阈值必须为正数");
        }

        // 验证不可变性：修改输入参数 map 必须抛出异常
        OfflineBenchmarkTestCase tc1 = suite.get(0);
        assertThrows(UnsupportedOperationException.class, () ->
                tc1.inputParams().put("illegalKey", "illegalVal"));
    }

    @Test
    @DisplayName("契约 2: 验证 Proposer 变异安全性与前缀缓存友好性（防占位符、防膨胀）")
    void testProposerMutationSafety() {
        HermesHarnessVoucher baseline = createBaselineVoucher();
        OfflineHarnessProposer proposer = new OfflineHarnessProposer.DefaultOfflineHarnessProposer();
        OfflineBenchmarkTestCase testCase = OfflineBenchmarkTestCase.createDefaultSuite().get(0);

        // 1. 正常 System Prompt 精简变异
        HermesHarnessVoucher mutatedPromptVoucher = proposer.proposeMutation(
                baseline,
                OfflineHarnessProposer.MutationDimension.SYSTEM_PROMPT_CONCISE,
                testCase
        );
        assertNotNull(mutatedPromptVoucher);
        assertTrue(mutatedPromptVoucher.invariantSystemPrompt().canonical().length()
                        <= baseline.invariantSystemPrompt().canonical().length(),
                "精简变异后 SystemPrompt 字符数严禁超过基线（确保 ΔTokens <= 0）");

        // 2. 正常 Memory 紧凑变异
        HermesHarnessVoucher mutatedMemVoucher = proposer.proposeMutation(
                baseline,
                OfflineHarnessProposer.MutationDimension.MEMORY_WINDOW_OPTIMIZED,
                testCase
        );
        assertEquals(10, mutatedMemVoucher.memoryContext().shortTermWindowSize());

        // 3. 构造非法变异：注入动态占位符破坏 DeepSeek Context Caching，断言必须抛出异常拦截
        assertThrows(IllegalArgumentException.class, () ->
                new InvariantSystemPromptRecord(
                        "角色定义 {{dynamic_user_name}}",
                        "核心原则",
                        "安全红线"
                ));
    }

    @Test
    @DisplayName("契约 3: 验证探针执行物理差异精准提取算法")
    void testProbeExecutionDiffExtraction() {
        OfflineBenchmarkTestCase testCase = OfflineBenchmarkTestCase.createDefaultSuite().get(1); // tool-route-02
        HarnessEvidenceRecord baseEvidence = createSampleEvidence("ev-base", 1000, 100, "banking_transaction_query", true);
        HarnessEvidenceRecord candEvidence = createSampleEvidence("ev-cand", 950, 103, "banking_transaction_query", true);

        ProbeExecutionDiff diff = EvidenceDiffExtractor.extract(testCase, baseEvidence, candEvidence, true);

        assertNotNull(diff);
        assertEquals("tool-route-02", diff.taskId());
        assertEquals(-50, diff.deltaTokens(), "Token 增量必须精准为 -50");
        assertEquals(3, diff.deltaDurationMs(), "耗时增量必须精准为 3ms");
        assertTrue(diff.usageComparable(), "仅双方真实用量已知时允许比较");
        assertTrue(diff.toolSelectionMatched(), "工具选择匹配");
        assertTrue(diff.satisfiesBudgetGate(), "严格满足 ΔTokens <= 0 且 ΔDuration <= 5ms");
        assertTrue(diff.isEligibleForAdoption(), "具备采纳资质");
    }

    @Test
    @DisplayName("契约 4: 验证预算门禁硬拦截率 100%（超 Token 或超时立即拒绝）")
    void testBudgetGateEnforcement() {
        OfflineBenchmarkTestCase testCase = OfflineBenchmarkTestCase.createDefaultSuite().get(0);
        HarnessEvidenceRecord baseEvidence = createSampleEvidence("ev-base", 1000, 100, null, true);

        // 样本 A: Token 膨胀 (ΔTokens = +10 > 0) -> 必须被拒绝
        HarnessEvidenceRecord tokenInflatedEvidence = createSampleEvidence("ev-cand-1", 1010, 95, null, true);
        ProbeExecutionDiff diffA = EvidenceDiffExtractor.extract(testCase, baseEvidence, tokenInflatedEvidence, true);
        assertFalse(diffA.satisfiesBudgetGate(), "Token 膨胀必须被预算门禁拦截");
        assertFalse(diffA.isEligibleForAdoption());

        // 样本 B: 耗时增加超标 (ΔDuration = +8ms > 5ms) -> 必须被拒绝
        HarnessEvidenceRecord timeoutEvidence = createSampleEvidence("ev-cand-2", 900, 108, null, true);
        ProbeExecutionDiff diffB = EvidenceDiffExtractor.extract(testCase, baseEvidence, timeoutEvidence, true);
        assertFalse(diffB.satisfiesBudgetGate(), "耗时增加超标必须被预算门禁拦截");
        assertFalse(diffB.isEligibleForAdoption());
    }

    @Test
    @DisplayName("未知 Usage 即使差分数值合规，也不得通过预算门禁或进入盲审")
    void testUnknownUsageRejectsAdoption() {
        OfflineBenchmarkTestCase testCase = OfflineBenchmarkTestCase.createDefaultSuite().get(0);
        HarnessEvidenceRecord baseline = createSampleEvidence("ev-base", 1000, 100, null, true);
        HarnessEvidenceRecord candidate = createSampleEvidence(
                "ev-cand", 900, 102, null, true, UsageStatus.UNKNOWN);
        ProbeExecutionDiff diff = EvidenceDiffExtractor.extract(testCase, baseline, candidate, true);

        assertEquals(UsageStatus.KNOWN, diff.baselineUsageStatus());
        assertEquals(UsageStatus.UNKNOWN, diff.candidateUsageStatus());
        assertFalse(diff.usageComparable());
        assertFalse(EvidenceDiffExtractor.extract(testCase, candidate, baseline, true).usageComparable());
        assertFalse(diff.satisfiesBudgetGate());
        assertFalse(diff.isEligibleForAdoption());
        DualBlindSelectionEvaluator.SelectionDecisionRecord decision = new DualBlindSelectionEvaluator().evaluate(
                testCase, diff, "base", "cand", (a, b) -> {
                    throw new AssertionError("未知 Usage 不应调用盲审裁判");
                });
        assertFalse(decision.candidateAdopted());
        assertEquals(DualBlindSelectionEvaluator.LABEL_BASELINE, decision.chosenCandidateId());
    }

    @Test
    @DisplayName("契约 5: 验证双向交叉匿名盲审（Position-Swap Dual Pass）消除大模型位置偏置")
    void testDualBlindSelectionPositionConsistency() {
        OfflineBenchmarkTestCase testCase = OfflineBenchmarkTestCase.createDefaultSuite().get(0);
        DualBlindSelectionEvaluator evaluator = new DualBlindSelectionEvaluator();

        // 构造合规的差分（满足预算门禁）
        ProbeExecutionDiff eligibleDiff = new ProbeExecutionDiff(
                testCase.taskId(),
                "ev-base",
                "ev-cand",
                1000,
                900,
                -100,
                100,
                102,
                2,
                0.0015,
                0.0013,
                true,
                true,
                UsageStatus.KNOWN,
                UsageStatus.KNOWN,
                true
        );

        // 场景 1: 裁判真实客观判定候选者更优（无论候选者放在 A 还是 B，裁判都准确认出候选内容）
        DualBlindSelectionEvaluator.BlindJudgeFunction objectiveJudge = (optA, optB) -> {
            // 假设 optA 包含 "候选优势内容"，则选 A，否则选 B
            return optA.contains("候选优势内容") ? "A" : "B";
        };

        DualBlindSelectionEvaluator.SelectionDecisionRecord decision1 = evaluator.evaluate(
                testCase,
                eligibleDiff,
                "基线答案摘要",
                "候选优势内容：回答完备客观且排版结构化",
                objectiveJudge
        );
        assertTrue(decision1.candidateAdopted(), "客观优选情况下候选者应被采纳");
        assertEquals(DualBlindSelectionEvaluator.LABEL_CANDIDATE, decision1.chosenCandidateId());
        assertTrue(decision1.positionConsistency(), "双向盲审位置一致性必须为 true");

        // 场景 2: 裁判存在严重位置偏见（无论内容是什么，大模型盲目固定选择首项 "A"）
        DualBlindSelectionEvaluator.BlindJudgeFunction biasedJudge = (optA, optB) -> "A";

        DualBlindSelectionEvaluator.SelectionDecisionRecord decision2 = evaluator.evaluate(
                testCase,
                eligibleDiff,
                "基线答案摘要",
                "候选优势内容",
                biasedJudge
        );
        assertFalse(decision2.candidateAdopted(), "存在位置偏见导致对调裁决冲突时，严禁采纳候选者");
        assertEquals(DualBlindSelectionEvaluator.LABEL_BASELINE, decision2.chosenCandidateId());
        assertFalse(decision2.positionConsistency(), "双向一致性核验必须为 false，触发平局安全回退");
    }

    @Test
    @DisplayName("契约 6: 验证三权分立防数据泄漏雷区静态扫描断言")
    void testZeroDataLeakageTripwire() {
        // 1. 正常内容无泄漏
        assertDoesNotThrow(() ->
                SelectionDataLeakageTripwire.assertNoLeak(
                        "用户业务查询请求与候选智能体输出摘要",
                        List.of("src/main/resources/config.json")
                ));

        // 2. 文本中包含非法判词标记（如 passed_count）
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                SelectionDataLeakageTripwire.assertNoLeak(
                        "评测摘要: passed_count: 5, pass_rate: 100%",
                        List.of()
                ));
        assertTrue(ex1.getMessage().contains("CRITICAL_DATA_LEAKAGE"));

        // 3. 文件路径中包含敏感 holdout 路径
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                SelectionDataLeakageTripwire.assertNoLeak(
                        "正常上下文",
                        List.of("docs/holdout/expected_answers.json")
                ));
        assertTrue(ex2.getMessage().contains("CRITICAL_DATA_LEAKAGE"));
    }

    @Test
    @DisplayName("契约 7: 验证离线证据凭单确定性常量时间回放验签（通过率 100%）")
    void testOfflineEvidenceDeterministicReplay() {
        // 构造 5 份离线评测沉淀的真实证据
        List<HarnessEvidenceRecord> evidenceList = List.of(
                createSampleEvidence("ev-01", 850, 95, "tool_a", true),
                createSampleEvidence("ev-02", 920, 110, "tool_b", true),
                createSampleEvidence("ev-03", 780, 88, "tool_c", true),
                createSampleEvidence("ev-04", 1050, 130, null, true),
                createSampleEvidence("ev-05", 890, 102, "tool_d", true)
        );

        OfflineEvidenceReplayer.ReplayVerificationResult result =
                OfflineEvidenceReplayer.replayAndVerify(evidenceList);

        assertTrue(result.allVerified(), "所有离线证据凭单的防篡改签名必须 100% 验证通过");
        assertEquals(5, result.totalRecords());
        assertEquals(5, result.validCount());
        assertTrue(result.failedEvidenceIds().isEmpty(), "失败凭单列表必须为空");
    }
}
