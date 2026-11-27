package tech.qiantong.qknow.hermes.tool.codeact;

import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;

import java.util.List;
import java.util.Objects;

/**
 * 验证型程序化代码执行计划契约（Java 21 不可变 Record）。
 */
public record CodeActExecutionPlan(
        String planId,
        ExecutionTier executionTier,
        String language,
        String scriptOrExpression,
        ReadOnlyDatasetView datasetView,
        List<String> allowedReadOnlyToolIds,
        EnterpriseCodeSandboxRuntime.SandboxResourceQuota quota
) {
    public enum ExecutionTier {
        /** Tier-0：零进程 JVM 内置高精度确定性数学算子 */
        TIER0_IN_PROCESS_MATH,
        /** Tier-1：安全格与静态 AST 审查受控的隔离脚本子进程沙盒 */
        TIER1_SANDBOXED_SCRIPT,
        /** Tier-2：受限无环 AST 工具流水线编排 */
        TIER2_AST_TOOL_PIPELINE
    }

    public CodeActExecutionPlan {
        Objects.requireNonNull(planId, "planId 不能为空");
        Objects.requireNonNull(executionTier, "executionTier 不能为空");
        language = (language == null || language.isBlank()) ? "python" : language;
        scriptOrExpression = scriptOrExpression != null ? scriptOrExpression : "";
        allowedReadOnlyToolIds = List.copyOf(allowedReadOnlyToolIds != null ? allowedReadOnlyToolIds : List.of());
        quota = quota != null ? quota : EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault();
    }
}
