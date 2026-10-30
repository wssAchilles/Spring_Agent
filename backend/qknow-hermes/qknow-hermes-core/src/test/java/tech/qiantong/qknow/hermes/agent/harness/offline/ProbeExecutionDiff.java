package tech.qiantong.qknow.hermes.agent.harness.offline;

import tech.qiantong.qknow.hermes.agent.harness.UsageStatus;

import java.util.Objects;

/**
 * 探针执行结果与物理差异比对凭单（不可变 Java 21 Record）。
 *
 * <p>封装探针执行中收集到的真实物理执行指标差分，作为 Selection 匿名盲审与预算门禁的决策输入。</p>
 */
public record ProbeExecutionDiff(
        String taskId,
        String baselineEvidenceId,
        String candidateEvidenceId,
        long baselineTokens,
        long candidateTokens,
        long deltaTokens,             // candidateTokens - baselineTokens（硬门禁要求 <= 0）
        long baselineDurationMs,
        long candidateDurationMs,
        long deltaDurationMs,         // candidateDurationMs - baselineDurationMs（硬门禁要求 <= 5ms）
        double baselineCostCny,
        double candidateCostCny,
        boolean toolSelectionMatched, // 候选工具调用是否精准匹配预期
        boolean nonDegradingQuality,  // 业务应答是否完备且无退化
        UsageStatus baselineUsageStatus,
        UsageStatus candidateUsageStatus,
        boolean usageComparable
) {
    public ProbeExecutionDiff {
        taskId = Objects.requireNonNull(taskId, "taskId 不能为 null");
        baselineEvidenceId = Objects.requireNonNull(baselineEvidenceId, "baselineEvidenceId 不能为 null");
        candidateEvidenceId = Objects.requireNonNull(candidateEvidenceId, "candidateEvidenceId 不能为 null");
        baselineUsageStatus = Objects.requireNonNull(baselineUsageStatus, "baselineUsageStatus 不能为 null");
        candidateUsageStatus = Objects.requireNonNull(candidateUsageStatus, "candidateUsageStatus 不能为 null");
        usageComparable = baselineUsageStatus == UsageStatus.KNOWN && candidateUsageStatus == UsageStatus.KNOWN;
    }

    public ProbeExecutionDiff(
            String taskId, String baselineEvidenceId, String candidateEvidenceId,
            long baselineTokens, long candidateTokens, long deltaTokens,
            long baselineDurationMs, long candidateDurationMs, long deltaDurationMs,
            double baselineCostCny, double candidateCostCny,
            boolean toolSelectionMatched, boolean nonDegradingQuality) {
        this(taskId, baselineEvidenceId, candidateEvidenceId,
                baselineTokens, candidateTokens, deltaTokens,
                baselineDurationMs, candidateDurationMs, deltaDurationMs,
                baselineCostCny, candidateCostCny,
                toolSelectionMatched, nonDegradingQuality,
                UsageStatus.UNKNOWN, UsageStatus.UNKNOWN, false);
    }

    /**
     * 判断是否严格满足阶段三假设锁定的预算门禁：ΔTokens <= 0 且 ΔDuration <= 5ms。
     */
    public boolean satisfiesBudgetGate() {
        return usageComparable && deltaTokens <= 0 && deltaDurationMs <= 5;
    }

    /**
     * 判断候选者是否具备采纳资质（同时满足预算门禁、工具调用精准且质量无退化）。
     */
    public boolean isEligibleForAdoption() {
        return satisfiesBudgetGate() && toolSelectionMatched && nonDegradingQuality;
    }
}
