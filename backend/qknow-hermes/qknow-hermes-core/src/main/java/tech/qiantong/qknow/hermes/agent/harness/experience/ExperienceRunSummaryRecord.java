package tech.qiantong.qknow.hermes.agent.harness.experience;

public record ExperienceRunSummaryRecord(
        String runId,
        int totalProbesExecuted,
        int totalModelCallsExecuted,
        long totalTokensConsumed,
        double totalCostCnyConsumed,
        int uniqueCasesEvaluated,
        int qualityPassedCases,
        int unresolvedAttributionCases,
        int evaluatedCandidates,
        int rejectedCandidates,
        int adoptedCandidates,
        String finalVerdict
) {}
