package tech.qiantong.qknow.hermes.agent.harness.offline;

import tech.qiantong.qknow.hermes.agent.harness.*;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 离线受控变异提议器契约与实现。
 *
 * <p>严格限定在低风险的 3 个组件（SystemPrompt、ToolGuidance、Memory）进行单变量离线变异，
 * 绝不在生产环境引入动态代码或破坏 DeepSeek 静态前缀缓存。</p>
 */
public interface OfflineHarnessProposer {

    /**
     * 变异目标维度枚举。
     */
    enum MutationDimension {
        SYSTEM_PROMPT_CONCISE,    // 角色提示词精简与无幻觉边界强化（要求负或零 Token 增量）
        TOOL_GUIDANCE_ENHANCED,   // 工具选择指引与前置参数验证策略强化
        MEMORY_WINDOW_OPTIMIZED   // 短期记忆滑动窗口紧凑化调整（优化 Token 占用与实体召回平衡）
    }

    /**
     * 基于基线凭单生成单变量受控变异候选凭单。
     */
    HermesHarnessVoucher proposeMutation(
            HermesHarnessVoucher baselineVoucher,
            MutationDimension dimension,
            OfflineBenchmarkTestCase testCase
    );

    /**
     * 变异安全性与缓存友好硬门禁校验器。
     */
    class MutationSafetyValidator {
        private static final Pattern DYNAMIC_PLACEHOLDER = Pattern.compile(
                "\\{\\{[^{}]*}}|\\$\\{[^{}]*}|\\{[A-Za-z_][A-Za-z0-9_.-]*}");

        public static void validate(HermesHarnessVoucher baseline, HermesHarnessVoucher candidate) {
            Objects.requireNonNull(baseline, "baseline 不能为 null");
            Objects.requireNonNull(candidate, "candidate 不能为 null");

            // 1. 静态前缀缓存保护断言：严禁包含任何动态占位符
            String roleDef = candidate.invariantSystemPrompt().roleDefinition();
            String principles = candidate.invariantSystemPrompt().corePrinciples();
            String safety = candidate.invariantSystemPrompt().safetyInstructions();

            assertNoPlaceholder(roleDef);
            assertNoPlaceholder(principles);
            assertNoPlaceholder(safety);

            // 2. 提示词字符数非正增长断言（严格防止膨胀）
            int basePromptChars = baseline.invariantSystemPrompt().canonical().length();
            int candPromptChars = candidate.invariantSystemPrompt().canonical().length();
            if (candPromptChars > basePromptChars) {
                throw new IllegalStateException("MUTATION_REJECTED: 候选 SystemPrompt 字符数超标膨胀 (Base: "
                        + basePromptChars + ", Cand: " + candPromptChars + ")");
            }

            // 3. 记忆窗口范围约束 [5, 30]
            int windowSize = candidate.memoryContext().shortTermWindowSize();
            if (windowSize < 5 || windowSize > 30) {
                throw new IllegalStateException("MUTATION_REJECTED: Memory 窗口尺寸超界 [5, 30]: " + windowSize);
            }

            // 4. 越界组件不可变性断言（其余 5 大组件必须 100% 保持与基线一致）
            if (!baseline.guardrails().canonical().equals(candidate.guardrails().canonical())) {
                throw new IllegalStateException("MUTATION_REJECTED: 严禁篡改 GuardrailsSpecification 契约");
            }
            if (!baseline.subagents().canonical().equals(candidate.subagents().canonical())) {
                throw new IllegalStateException("MUTATION_REJECTED: 严禁篡改 SubagentsTopology 契约");
            }
            if (!baseline.skillBank().canonical().equals(candidate.skillBank().canonical())) {
                throw new IllegalStateException("MUTATION_REJECTED: 严禁篡改 EnterpriseSkillBank 契约");
            }
            if (!baseline.sandboxedExecutionPlan().canonical().equals(candidate.sandboxedExecutionPlan().canonical())) {
                throw new IllegalStateException("MUTATION_REJECTED: 严禁篡改 SandboxedExecutionPlan 契约");
            }
            if (!baseline.mcpMiddlewarePlugins().canonical().equals(candidate.mcpMiddlewarePlugins().canonical())) {
                throw new IllegalStateException("MUTATION_REJECTED: 严禁篡改 McpMiddlewarePlugins 契约");
            }
        }

        private static void assertNoPlaceholder(String text) {
            if (text != null && DYNAMIC_PLACEHOLDER.matcher(text).find()) {
                throw new IllegalStateException("MUTATION_REJECTED: 检测到动态占位符，破坏 DeepSeek 前缀缓存");
            }
        }
    }

    /**
     * 默认离线受控变异提议器实现。
     */
    class DefaultOfflineHarnessProposer implements OfflineHarnessProposer {
        @Override
        public HermesHarnessVoucher proposeMutation(
                HermesHarnessVoucher baselineVoucher,
                MutationDimension dimension,
                OfflineBenchmarkTestCase testCase) {

            Objects.requireNonNull(baselineVoucher, "baselineVoucher");
            Objects.requireNonNull(dimension, "dimension");

            HermesHarnessVoucher candidate;

            switch (dimension) {
                case SYSTEM_PROMPT_CONCISE -> {
                    // 精简角色描述，强化客观提取原则，去除套话（字符数减少，提升 Cache 紧凑度）
                    InvariantSystemPromptRecord basePrompt = baselineVoucher.invariantSystemPrompt();
                    String concisePrinciples = (basePrompt.corePrinciples() == null || basePrompt.corePrinciples().isBlank())
                            ? "回答需客观准确，严禁臆测。"
                            : basePrompt.corePrinciples().trim();
                    if (concisePrinciples.length() > 50) {
                        concisePrinciples = concisePrinciples.substring(0, 48) + "。";
                    }
                    InvariantSystemPromptRecord candidatePrompt = new InvariantSystemPromptRecord(
                            "企业知识与工具中枢智能体",
                            concisePrinciples,
                            "禁止未经授权的写操作。"
                    );
                    candidate = new HermesHarnessVoucher(
                            baselineVoucher.voucherId() + "-cand-prompt",
                            baselineVoucher.botId(),
                            baselineVoucher.version() + "-mut-prompt",
                            Instant.now(),
                            candidatePrompt,
                            baselineVoucher.guardrails(),
                            baselineVoucher.memoryContext(),
                            baselineVoucher.toolGuidance(),
                            baselineVoucher.subagents(),
                            baselineVoucher.skillBank(),
                            baselineVoucher.sandboxedExecutionPlan(),
                            baselineVoucher.mcpMiddlewarePlugins()
                    );
                }
                case TOOL_GUIDANCE_ENHANCED -> {
                    // 强化工具策略：开启前置霍尔逻辑验证，提升工具调用精准率
                    ToolGuidancePolicyRecord enhancedToolPolicy = new ToolGuidancePolicyRecord(
                            true,
                            true,
                            baselineVoucher.toolGuidance().twoPhaseApprovalEnabled()
                    );
                    candidate = new HermesHarnessVoucher(
                            baselineVoucher.voucherId() + "-cand-tool",
                            baselineVoucher.botId(),
                            baselineVoucher.version() + "-mut-tool",
                            Instant.now(),
                            baselineVoucher.invariantSystemPrompt(),
                            baselineVoucher.guardrails(),
                            baselineVoucher.memoryContext(),
                            enhancedToolPolicy,
                            baselineVoucher.subagents(),
                            baselineVoucher.skillBank(),
                            baselineVoucher.sandboxedExecutionPlan(),
                            baselineVoucher.mcpMiddlewarePlugins()
                    );
                }
                case MEMORY_WINDOW_OPTIMIZED -> {
                    // 优化滑动窗口尺寸：从 20 精简到 10，保持前序关键实体同时降低 Token
                    MemoryContextStrategyRecord optimizedMemory = new MemoryContextStrategyRecord(
                            10,
                            baselineVoucher.memoryContext().longTermRecallTopK(),
                            baselineVoucher.memoryContext().reflexionDepth()
                    );
                    candidate = new HermesHarnessVoucher(
                            baselineVoucher.voucherId() + "-cand-mem",
                            baselineVoucher.botId(),
                            baselineVoucher.version() + "-mut-mem",
                            Instant.now(),
                            baselineVoucher.invariantSystemPrompt(),
                            baselineVoucher.guardrails(),
                            optimizedMemory,
                            baselineVoucher.toolGuidance(),
                            baselineVoucher.subagents(),
                            baselineVoucher.skillBank(),
                            baselineVoucher.sandboxedExecutionPlan(),
                            baselineVoucher.mcpMiddlewarePlugins()
                    );
                }
                default -> throw new IllegalArgumentException("未知的变异维度: " + dimension);
            }

            // 变异后自动执行安全硬门禁校验
            MutationSafetyValidator.validate(baselineVoucher, candidate);
            return candidate;
        }
    }
}
