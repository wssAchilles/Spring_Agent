package tech.qiantong.qknow.hermes.agent.harness.offline;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;

import java.util.Objects;

/**
 * 物理执行证据差异提取器。
 *
 * <p>吸收 Harness² recursion.py 的差分反馈思想，对 Base 与 Candidate 的执行凭单进行对比，
 * 提取精确定量物理差异（ΔTokens, ΔDuration, 工具匹配状态）。</p>
 */
public final class EvidenceDiffExtractor {

    private EvidenceDiffExtractor() {}

    /**
     * 从基线凭单与候选凭单中提取物理差分指标。
     */
    public static ProbeExecutionDiff extract(
            OfflineBenchmarkTestCase testCase,
            HarnessEvidenceRecord baseEvidence,
            HarnessEvidenceRecord candEvidence,
            boolean nonDegradingQuality) {

        Objects.requireNonNull(testCase, "testCase 不能为 null");
        Objects.requireNonNull(baseEvidence, "baseEvidence 不能为 null");
        Objects.requireNonNull(candEvidence, "candEvidence 不能为 null");

        long deltaTokens = candEvidence.estimatedTokens() - baseEvidence.estimatedTokens();
        long deltaDuration = candEvidence.executionDurationMs() - baseEvidence.executionDurationMs();

        boolean toolMatched = true;
        String expectedTool = testCase.expectedToolName();
        if (expectedTool != null && !expectedTool.isBlank()) {
            toolMatched = candEvidence.toolExecutions() != null && candEvidence.toolExecutions().stream()
                    .anyMatch(entry -> entry.toolName().equalsIgnoreCase(expectedTool) && entry.success());
        }

        return new ProbeExecutionDiff(
                testCase.taskId(),
                baseEvidence.evidenceId(),
                candEvidence.evidenceId(),
                baseEvidence.estimatedTokens(),
                candEvidence.estimatedTokens(),
                deltaTokens,
                baseEvidence.executionDurationMs(),
                candEvidence.executionDurationMs(),
                deltaDuration,
                baseEvidence.estimatedCostCny(),
                candEvidence.estimatedCostCny(),
                toolMatched,
                nonDegradingQuality
        );
    }
}
