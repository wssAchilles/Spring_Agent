package tech.qiantong.qknow.hermes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.memory.reflexion.ReflexionEpisodicMemoryEngine;
import tech.qiantong.qknow.hermes.memory.reflexion.ReflexionEpisodicMemoryReceipt;
import tech.qiantong.qknow.hermes.memory.reflexion.impl.DefaultReflexionEpisodicMemoryEngine;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseSandboxExecutionReceipt;
import tech.qiantong.qknow.hermes.tool.sandbox.impl.DefaultEnterpriseCodeSandboxRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 终局满分架构契约测试套件 (FinalStageOneHundredScoreContractTest)
 * 验证两大关键代际差距突破：
 * 1. 企业级生产容器代码沙箱运行时与资源配额看门狗
 * 2. 语言强化自反思长程情景记忆引擎与千问 1536 维超球面单位向量流形
 *
 * @author Achilles
 * @since 2026-10-13
 */
public class FinalStageOneHundredScoreContractTest {

    private DefaultEnterpriseCodeSandboxRuntime sandboxRuntime;
    private DefaultReflexionEpisodicMemoryEngine memoryEngine;
    private Path tempSandboxDir;

    @BeforeEach
    void setUp() throws Exception {
        tempSandboxDir = Files.createTempDirectory("test_enterprise_sandbox_");
        sandboxRuntime = new DefaultEnterpriseCodeSandboxRuntime(tempSandboxDir);
        memoryEngine = new DefaultReflexionEpisodicMemoryEngine();
    }

    @AfterEach
    void tearDown() {
        if (sandboxRuntime != null) {
            sandboxRuntime.close();
        }
    }

    @Test
    @DisplayName("契约一：企业级安全沙箱通用脚本执行与资源配额存证验签")
    void testEnterpriseCodeSandboxExecutionAndResourceQuota() throws Exception {
        String script = "echo 'Hello Hermes Enterprise Sandbox' && echo 'Calculation Result: 42'";
        EnterpriseCodeSandboxRuntime.SandboxResourceQuota quota = new EnterpriseCodeSandboxRuntime.SandboxResourceQuota(
                3000L, 64L * 1024 * 1024, 2000_000L, 1024, true, true
        );

        EnterpriseSandboxExecutionReceipt receipt = sandboxRuntime.executeSandboxedScriptAsync(
                script, "sh", quota, Map.of("CUSTOM_FLAG", "HERMES_TEST")
        ).get(5, TimeUnit.SECONDS);

        assertNotNull(receipt, "执行凭单不能为空");
        assertEquals(0, receipt.exitCode(), "脚本应正常退出 (exitCode=0)");
        assertTrue(receipt.stdout().contains("Hello Hermes Enterprise Sandbox"), "输出中应包含执行回显");
        assertTrue(receipt.stdout().contains("Calculation Result: 42"), "输出中应包含计算结果");
        assertFalse(receipt.timedOut(), "正常脚本不应超时");
        assertTrue(receipt.readOnlyProtected(), "应标记只读保护");
        assertTrue(receipt.networkIsolated(), "应标记网络隔离");

        // 验证常量时间 SHA-256 自验真
        assertTrue(receipt.verifySecuritySeal(), "SHA-256 安全封印必须自验真通过 (MessageDigest.isEqual)");
    }

    @Test
    @DisplayName("契约二：沙箱硬看门狗毫秒级超时强杀与资源保护")
    void testEnterpriseCodeSandboxWatchdogHardKillAndTimeout() throws Exception {
        // 无限死循环脚本
        String deadLoopScript = "while true; do sleep 0.1; done";
        EnterpriseCodeSandboxRuntime.SandboxResourceQuota strictQuota = new EnterpriseCodeSandboxRuntime.SandboxResourceQuota(
                500L, 32L * 1024 * 1024, 500_000L, 512, true, true
        );

        long startMs = System.currentTimeMillis();
        EnterpriseSandboxExecutionReceipt receipt = sandboxRuntime.executeSandboxedScriptAsync(
                deadLoopScript, "sh", strictQuota, Map.of()
        ).get(4, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertNotNull(receipt, "执行凭单不能为空");
        assertTrue(receipt.timedOut(), "死循环脚本必须触发看门狗强杀 (timedOut=true)");
        assertEquals(137, receipt.exitCode(), "强杀退出码应为 137 (SIGKILL)");
        assertTrue(elapsedMs >= 450 && elapsedMs <= 2500, "强杀耗时应在看门狗控制窗口内");
        assertTrue(receipt.verifySecuritySeal(), "超时凭单安全封印必须自验真通过");
    }

    @Test
    @DisplayName("契约三：自反思长程情景记忆生成与千问 1536 维超球面单位范数")
    void testReflexionEpisodicMemoryGenerationAndUnitNorm() throws Exception {
        String tenantId = "tenant_alpha";
        String taskDesc = "OrderDataProcessingTask";
        String failTrajectory = "Step 1: Fetch Order; Step 2: Parse Date format 'yyyy-MM-dd'; Error: DateTimeParseException";
        String envFeedback = "java.time.format.DateTimeParseException: Text '2026/10/13' could not be parsed at index 4";

        ReflexionEpisodicMemoryReceipt receipt = memoryEngine.evaluateAndReflectAsync(
                tenantId, "DataWorkerAgent", taskDesc, 1, failTrajectory, envFeedback
        ).get(5, TimeUnit.SECONDS);

        assertNotNull(receipt, "反思凭单不能为空");
        assertEquals(tenantId, receipt.tenantId());
        assertEquals(1, receipt.trialSequence());
        assertTrue(receipt.verbalReflectionLesson().contains("DateTimeParseException")
                || receipt.verbalReflectionLesson().contains("纠偏策略"), "应包含语言反思与纠偏策略");

        // 验证千问 1536 维超球面向量
        float[] vector = receipt.qwenHypersphericalVector();
        assertNotNull(vector, "千问向量不能为空");
        assertEquals(1536, vector.length, "千问向量维度必须严格为 1536 维");

        double normSq = 0.0;
        for (float v : vector) {
            normSq += (double) v * v;
        }
        assertEquals(1.0, Math.sqrt(normSq), 1e-3, "超球面向量必须严格满足单位 L2 范数 ||v||_2 = 1.0");

        // 验证凭单常量时间验签
        assertTrue(receipt.verifyCryptographicSeal(), "反思凭单加密印章必须自验真通过");
    }

    @Test
    @DisplayName("契约四：超球面余弦度量相似反思召回与 Prompt 先验文本注入")
    void testReflexionEpisodicMemoryRecallAndPriorPromptInjection() throws Exception {
        String tenantId = "tenant_beta";
        String task = "SqlDataAnalysisTask";

        // 归档两个试次的反思教训
        memoryEngine.evaluateAndReflectAsync(
                tenantId, "SqlAgent", task, 1, "Trajectory: SELECT * FROM t", "Error: Table not found"
        ).get(5, TimeUnit.SECONDS);

        memoryEngine.evaluateAndReflectAsync(
                tenantId, "SqlAgent", task, 2, "Trajectory: SELECT * FROM db.t", "Error: Permission denied on table db.t"
        ).get(5, TimeUnit.SECONDS);

        // 使用相似任务向量查询
        float[] queryVector = DefaultReflexionEpisodicMemoryEngine.generateDeterministicQwenVector(task + "::Query");

        List<ReflexionEpisodicMemoryReceipt> recalled = memoryEngine.recallRelevantReflections(
                tenantId, queryVector, 3, 0.0
        );

        assertNotNull(recalled, "召回列表不能为空");
        assertFalse(recalled.isEmpty(), "应成功召回历史相关反思");
        assertTrue(recalled.size() <= 2, "召回数量应不超过归档总数");

        // 格式化为 Prompt 先验上下文
        String priorPrompt = memoryEngine.formatReflectionsAsPriorPrompt(recalled);
        assertNotNull(priorPrompt, "先验 Prompt 不能为空");
        assertTrue(priorPrompt.contains("前置长程反思先验规则"), "应包含前置反思先验标头");
        assertTrue(priorPrompt.contains("SqlDataAnalysisTask"), "应包含历史任务标识");
    }
}
