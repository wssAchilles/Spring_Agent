package tech.qiantong.qknow.hermes.cognitive.treesearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 失败分支的反事实因果反思补丁（Java 21 不可变 Record，对应定理 8.3）。
 * <p>
 * 强制以 Zone-1 尾部增量约束（Append-Only Tail Delta）注入下一兄弟分支，
 * 严禁修改 Zone-0 静态冻结系统前缀，确保 DeepSeek KV Prefix Cache 字节级命中率。
 * </p>
 */
public record CounterfactualBranchPatch(
        String patchId,
        String sourceFailedBranchId,
        FailureRootCause rootCause,
        String causalFailureDiagnosis,
        String correctiveConstraintPrompt,
        Map<String, Object> stateOverrides,
        List<String> avoidToolNames
) {
    /**
     * 失败分支的因果根因四分法枚举。
     */
    public enum FailureRootCause {
        TOOL_CONTRACT_VIOLATION,
        CODEACT_ASSERTION_FAILED,
        TEMPORAL_CONTRADICTION,
        CONSISTENCY_DRIFT
    }

    public CounterfactualBranchPatch {
        Objects.requireNonNull(patchId, "patchId 不能为空");
        Objects.requireNonNull(sourceFailedBranchId, "sourceFailedBranchId 不能为空");
        Objects.requireNonNull(rootCause, "rootCause 不能为空");
        causalFailureDiagnosis = causalFailureDiagnosis != null ? causalFailureDiagnosis : "";
        correctiveConstraintPrompt = correctiveConstraintPrompt != null ? correctiveConstraintPrompt : "";

        // 过滤 null 键与 null 值，防止 Map.copyOf 运行期抛出 NullPointerException
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (stateOverrides != null) {
            for (Map.Entry<String, Object> entry : stateOverrides.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    sanitized.put(entry.getKey(), entry.getValue());
                }
            }
        }
        stateOverrides = Collections.unmodifiableMap(sanitized);

        List<String> safeTools = new ArrayList<>();
        if (avoidToolNames != null) {
            for (String tool : avoidToolNames) {
                if (tool != null && !tool.isBlank()) {
                    safeTools.add(tool.trim());
                }
            }
        }
        avoidToolNames = List.copyOf(safeTools);
    }

    /**
     * 生成可安全追加到 Zone-1 尾部的结构化反事实约束，绝不触碰 Zone-0 前缀。
     */
    public String toAppendOnlyTailDelta() {
        return "[CAUSAL_REFLECTION_PATCH id=" + patchId
                + " sourceBranch=" + sourceFailedBranchId
                + " rootCause=" + rootCause.name()
                + "] " + correctiveConstraintPrompt;
    }
}
