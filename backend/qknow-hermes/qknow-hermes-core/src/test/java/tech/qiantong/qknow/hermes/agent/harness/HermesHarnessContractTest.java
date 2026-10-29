package tech.qiantong.qknow.hermes.agent.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness 8 大不可变组件、指纹与静态缓存前缀的契约守护测试。
 */
class HermesHarnessContractTest {

    private final HermesHarnessAssembler assembler = new HermesHarnessAssembler();

    @Test
    @DisplayName("凭单 8 组件不可变、指纹确定且 64 位十六进制")
    void testVoucherImmutabilityAndFingerprint() {
        HermesHarnessVoucher voucher = assembler.createDefaultProductionVoucher(7L);

        assertEquals(7L, voucher.botId());
        assertEquals(8, new Object[]{
                voucher.invariantSystemPrompt(), voucher.guardrails(), voucher.memoryContext(),
                voucher.toolGuidance(), voucher.subagents(), voucher.skillBank(),
                voucher.sandboxedExecutionPlan(), voucher.mcpMiddlewarePlugins()
        }.length);
        assertEquals("1.0.0", voucher.version());

        String first = voucher.computeFingerprint();
        String second = voucher.computeFingerprint();
        assertEquals(64, first.length());
        assertEquals(first, second);
        assertTrue(voucher.matchesFingerprint(first));
        assertFalse(voucher.matchesFingerprint("00".repeat(32)));
        assertThrows(UnsupportedOperationException.class,
                () -> voucher.skillBank().activeSkillIds().add("mutated"));
    }

    @Test
    @DisplayName("常量时间指纹校验拒绝 null 与错误指纹")
    void testConstantTimeFingerprintVerification() {
        HermesHarnessVoucher voucher = assembler.createDefaultProductionVoucher(7L);
        assertFalse(voucher.matchesFingerprint(null));
        assertFalse(voucher.matchesFingerprint(voucher.computeFingerprint().toUpperCase()));
    }

    @Test
    @DisplayName("SystemPrompt 动态变量必须 Fail-Close")
    void testDynamicVariableRejectionInSystemPrompt() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvariantSystemPromptRecord("你是助手 {{sessionId}}", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new InvariantSystemPromptRecord("你是助手 {time}", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new InvariantSystemPromptRecord("你是助手 ${sessionId}", "", ""));
    }

    @Test
    @DisplayName("装配器保持静态角色/安全规则，且不把动态上下文放进 SystemPrompt")
    void testAssemblerSystemPromptEquivalence() {
        HermesHarnessVoucher voucher = assembler.createRequestVoucher(7L, "你是一个专业企业助手");
        String systemPrompt = assembler.assembleSystemPrompt(voucher, List.of());
        String userMessage = assembler.assembleUserMessageWithContext(
                "用户问题", "<knowledge_base>动态证据</knowledge_base>", voucher);

        assertTrue(systemPrompt.contains("你是一个专业企业助手"));
        assertTrue(systemPrompt.contains("<security_instructions>"));
        assertTrue(systemPrompt.contains("不要编造知识库中不存在的信息"));
        assertFalse(systemPrompt.contains("{{"));
        assertFalse(systemPrompt.contains("动态证据"));
        assertTrue(userMessage.startsWith("<knowledge_base>动态证据"));
        assertTrue(userMessage.endsWith("用户问题\n</user_question>"));
    }

    @Test
    @DisplayName("沙箱计划永久拒绝任意脚本")
    void testSandboxedExecutionPlanRejectsArbitraryCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxedExecutionPlanRecord(List.of("safeSlot"), true));
        assertEquals(false, SandboxedExecutionPlanRecord.declarative().allowArbitraryScripts());
    }

    @Test
    @DisplayName("装配器将只读工具描述放入静态工具指引")
    void testAssemblerEmitsToolGuidance() {
        ToolCallback tool = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("knowledgeQuery")
                        .description("只读知识库查询")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "ok";
            }
        };

        HermesHarnessVoucher voucher = assembler.createDefaultProductionVoucher(7L);
        String prompt = assembler.assembleSystemPrompt(voucher, List.of(tool));

        assertTrue(prompt.contains("<tool_guidance>"));
        assertTrue(prompt.contains("knowledgeQuery: 只读知识库查询"));
        assertTrue(prompt.contains("未列出的工具不得调用"));
    }
}
