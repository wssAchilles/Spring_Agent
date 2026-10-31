package tech.qiantong.qknow.hermes.agent.harness.ppc;

import java.util.List;
import java.util.Map;

public record PpcRun(
        String runId,
        PpcMode mode,
        Budget budget,
        int totalProbesExecuted,
        int totalModelCallsExecuted,
        long totalTokensConsumed,
        double totalCostCnyConsumed,
        int promptOnlyDowngradeCount,
        List<PpcCandidate> candidates,
        List<PpcProbeResult> probes,
        List<PpcComposer.PositionSwapAudit> positionSwapAudits,
        String selectedCandidateId,
        String finalVerdict,
        Map<String, String> checksumsSha256
) {
    public record Budget(int maxProbes, int maxModelCalls, long maxTotalTokens,
                         int maxOutputTokensPerCall, double maxCostCny) {
        public Budget {
            if (maxProbes < 1 || maxProbes > 20 || maxModelCalls < 1 || maxModelCalls > 60
                    || maxTotalTokens < 1 || maxTotalTokens > 200_000L
                    || maxOutputTokensPerCall < 1 || maxOutputTokensPerCall > 2_000
                    || !Double.isFinite(maxCostCny) || maxCostCny <= 0.0D || maxCostCny > 10.0D) {
                throw new IllegalArgumentException("Stage 9 budget exceeds hard caps");
            }
        }
    }

    public PpcRun {
        candidates = List.copyOf(candidates);
        probes = List.copyOf(probes);
        positionSwapAudits = List.copyOf(positionSwapAudits);
        checksumsSha256 = Map.copyOf(checksumsSha256);
    }
}
