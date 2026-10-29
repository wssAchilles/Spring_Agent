package tech.qiantong.qknow.hermes.agent.harness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hermes Harness 聚合根凭单。
 *
 * <p>8 大组件均为不可变 Record；凭单指纹只依赖 botId/version/组件内容，
 * 不依赖 createdAt，因此相同配置可稳定复用缓存前缀。</p>
 */
public record HermesHarnessVoucher(
        String voucherId,
        Long botId,
        String version,
        Instant createdAt,
        InvariantSystemPromptRecord invariantSystemPrompt,
        GuardrailsSpecificationRecord guardrails,
        MemoryContextStrategyRecord memoryContext,
        ToolGuidancePolicyRecord toolGuidance,
        SubagentsTopologyRecord subagents,
        EnterpriseSkillBankRecord skillBank,
        SandboxedExecutionPlanRecord sandboxedExecutionPlan,
        McpMiddlewarePluginsRecord mcpMiddlewarePlugins
) {
    public HermesHarnessVoucher {
        voucherId = requireText(voucherId, "voucherId");
        botId = botId == null ? 0L : botId;
        version = requireText(version, "version");
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        invariantSystemPrompt = Objects.requireNonNull(invariantSystemPrompt, "invariantSystemPrompt");
        guardrails = Objects.requireNonNull(guardrails, "guardrails");
        memoryContext = Objects.requireNonNull(memoryContext, "memoryContext");
        toolGuidance = Objects.requireNonNull(toolGuidance, "toolGuidance");
        subagents = Objects.requireNonNull(subagents, "subagents");
        skillBank = Objects.requireNonNull(skillBank, "skillBank");
        sandboxedExecutionPlan = Objects.requireNonNull(sandboxedExecutionPlan, "sandboxedExecutionPlan");
        mcpMiddlewarePlugins = Objects.requireNonNull(mcpMiddlewarePlugins, "mcpMiddlewarePlugins");
    }

    /**
     * 对所有组件内容做确定性 SHA-256 指纹。
     */
    public String computeFingerprint() {
        String canonical = canonical();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 常量时间指纹比对，避免侧信道泄漏。
     */
    public boolean matchesFingerprint(String expected) {
        if (expected == null) {
            return false;
        }
        byte[] actualBytes = computeFingerprint().getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actualBytes, expectedBytes);
    }

    public String canonical() {
        return String.join("\u001e",
                String.valueOf(botId),
                version,
                invariantSystemPrompt.canonical(),
                guardrails.canonical(),
                memoryContext.canonical(),
                toolGuidance.canonical(),
                subagents.canonical(),
                skillBank.canonical(),
                sandboxedExecutionPlan.canonical(),
                mcpMiddlewarePlugins.canonical());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
