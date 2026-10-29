package tech.qiantong.qknow.hermes.agent.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermes Harness² 阶段二架构与运行时驱动契约测试。
 *
 * <p>验证任务书 F 节规范：
 * 1. 契约驱动运行时参数（MemoryContextStrategy、ToolGuidancePolicy、Guardrails）；
 * 2. 证据链完整性与 Runmark SHA-256 签名不可篡改性；
 * 3. 证据采集器在多阶段物理留痕的只读旁路安全性；
 * 4. 反事实消融实验（字段篡改差分漂移）。</p>
 */
class HermesHarnessStage2RuntimeTest {

    private final HermesHarnessAssembler assembler = new HermesHarnessAssembler();

    @Test
    @DisplayName("证据凭单生成与确定性 SHA-256 签名有效性验证")
    void testEvidenceRecordDigestVerification() {
        HermesHarnessVoucher voucher = assembler.createDefaultProductionVoucher(1001L);
        String reqId = "req-test-" + UUID.randomUUID();

        HarnessEvidenceCollector collector = new HarnessEvidenceCollector(reqId, 1001L, voucher);
        collector.recordUserInput("查询企业年度财务合规规范");
        collector.recordRagEvidence(3, 0.92);
        collector.recordToolExecution("financeSearchTool", 45L, true, "{\"year\":2025}", "{\"status\":\"ok\"}");
        collector.recordCostAndTokens(1250, 0.0025);
        collector.recordFinalOutput("企业 2025 年度财务合规规范已整理如下...");

        HarnessEvidenceRecord evidence = collector.finalizeEvidence();

        assertNotNull(evidence);
        assertEquals(reqId, evidence.requestId());
        assertEquals(1001L, evidence.botId());
        assertEquals(voucher.computeFingerprint(), evidence.voucherFingerprint());
        assertEquals(voucher.version(), evidence.voucherVersion());
        assertEquals(3, evidence.ragDocumentCount());
        assertEquals(0.92, evidence.ragTopScore(), 1e-6);
        assertEquals(1, evidence.toolExecutions().size());
        assertEquals("financeSearchTool", evidence.toolExecutions().get(0).toolName());
        assertEquals(1250, evidence.estimatedTokens());

        // 验证签名有效性
        String digest = evidence.computeDigest();
        assertNotNull(digest);
        assertEquals(64, digest.length(), "SHA-256 哈希应为 64 位十六进制");
        assertTrue(evidence.verifyDigest(digest), "生成的摘要必须常量时间验证通过");
    }

    @Test
    @DisplayName("反事实消融实验：证据字段遭篡改时签名校验必须严格失败")
    void testCounterfactualTamperDetection() {
        HermesHarnessVoucher voucher = assembler.createDefaultProductionVoucher(2002L);
        String reqId = "req-tamper-" + UUID.randomUUID();

        HarnessEvidenceCollector collector = new HarnessEvidenceCollector(reqId, 2002L, voucher);
        collector.recordUserInput("原始合规问题");
        collector.recordCostAndTokens(500, 0.001);
        collector.recordFinalOutput("原始合法答案");
        HarnessEvidenceRecord genuineEvidence = collector.finalizeEvidence();

        String genuineDigest = genuineEvidence.computeDigest();
        assertTrue(genuineEvidence.verifyDigest(genuineDigest));

        // 反事实 1：恶意篡改 Token 成本
        HarnessEvidenceRecord tamperedTokens = new HarnessEvidenceRecord(
                genuineEvidence.evidenceId(),
                genuineEvidence.requestId(),
                genuineEvidence.botId(),
                genuineEvidence.voucherFingerprint(),
                genuineEvidence.voucherVersion(),
                genuineEvidence.timestamp(),
                genuineEvidence.userInputSummary(),
                genuineEvidence.ragDocumentCount(),
                genuineEvidence.ragTopScore(),
                genuineEvidence.toolExecutions(),
                99999, // 篡改 Token
                genuineEvidence.estimatedCostCny(),
                genuineEvidence.executionDurationMs(),
                genuineEvidence.finalOutputSummary()
        );
        assertFalse(tamperedTokens.verifyDigest(genuineDigest), "篡改 Token 后必须无法通过原签名校验");

        // 反事实 2：恶意篡改最终输出
        HarnessEvidenceRecord tamperedOutput = new HarnessEvidenceRecord(
                genuineEvidence.evidenceId(),
                genuineEvidence.requestId(),
                genuineEvidence.botId(),
                genuineEvidence.voucherFingerprint(),
                genuineEvidence.voucherVersion(),
                genuineEvidence.timestamp(),
                genuineEvidence.userInputSummary(),
                genuineEvidence.ragDocumentCount(),
                genuineEvidence.ragTopScore(),
                genuineEvidence.toolExecutions(),
                genuineEvidence.estimatedTokens(),
                genuineEvidence.estimatedCostCny(),
                genuineEvidence.executionDurationMs(),
                "恶意注入后的虚假输出"
        );
        assertFalse(tamperedOutput.verifyDigest(genuineDigest), "篡改最终输出后必须无法通过原签名校验");
    }

    @Test
    @DisplayName("证据采集器异步监听器回调与纯内存无阻塞测试")
    void testCollectorCompletionListener() {
        HermesHarnessVoucher voucher = assembler.createDefaultProductionVoucher(3003L);
        HarnessEvidenceCollector collector = new HarnessEvidenceCollector("req-listener-1", 3003L, voucher);

        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        collector.addCompletionListener(rec -> {
            listenerCalled.set(true);
            assertEquals("req-listener-1", rec.requestId());
        });

        collector.recordFinalOutput("执行成功");
        HarnessEvidenceRecord record = collector.finalizeEvidence();

        assertTrue(listenerCalled.get(), "finalizeEvidence 必须触发注册的回调监听器");
        assertNotNull(record.computeDigest());
    }

    @Test
    @DisplayName("定制 Voucher 驱动记忆策略与守卫参数契约一致性测试")
    void testCustomVoucherParameterDriving() {
        // 构建深度受限的定制记忆策略
        MemoryContextStrategyRecord customMemory = new MemoryContextStrategyRecord(5, 2, 0);
        GuardrailsSpecificationRecord customGuardrails = new GuardrailsSpecificationRecord(5, 2, 50_000L, 0.02D);
        ToolGuidancePolicyRecord customToolPolicy = new ToolGuidancePolicyRecord(false, true, true);

        HermesHarnessVoucher customVoucher = new HermesHarnessVoucher(
                "voucher-custom-1",
                8888L,
                "2.0.0-CUSTOM",
                Instant.now(),
                new InvariantSystemPromptRecord("定制专家角色", "定制核心准则", "定制安全红线"),
                customGuardrails,
                customMemory,
                customToolPolicy,
                SubagentsTopologyRecord.singleAgent(),
                EnterpriseSkillBankRecord.empty(),
                SandboxedExecutionPlanRecord.declarative(),
                McpMiddlewarePluginsRecord.defaults()
        );

        assertEquals(5, customVoucher.memoryContext().shortTermWindowSize());
        assertEquals(2, customVoucher.memoryContext().longTermRecallTopK());
        assertEquals(0, customVoucher.memoryContext().reflexionDepth());
        assertEquals(5, customVoucher.guardrails().maxCycleSteps());
        assertFalse(customVoucher.toolGuidance().autoToolSelectionEnabled());
        assertTrue(customVoucher.toolGuidance().hoareVerificationEnabled());

        // 验证指纹确定性
        String fp1 = customVoucher.computeFingerprint();
        String fp2 = customVoucher.computeFingerprint();
        assertEquals(fp1, fp2);
        assertTrue(customVoucher.matchesFingerprint(fp1));
    }
}
