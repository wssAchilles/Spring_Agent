package tech.qiantong.qknow.hermes.agent.harness.ppc.controlled;

import java.util.List;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;

/** Source authentication for one isolated controlled adoption, not a business-domain claim. */
public record ControlledProvenanceRecord(String entryId, String sourceRunId,
                                         String provenanceLevel, String evidenceVerification,
                                         Operator operator, String candidateFingerprint,
                                         String fixtureSha256, String verificationSqlSha256,
                                         List<String> probeDigests, int modelCalls,
                                         long totalTokens, double totalCostCny) {
    public ControlledProvenanceRecord {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(sourceRunId, "sourceRunId");
        if (!"CONTROLLED_FIXTURE".equals(provenanceLevel) || !"VERIFIED".equals(evidenceVerification)) {
            throw new IllegalArgumentException("controlled provenance must be verified");
        }
        Objects.requireNonNull(operator, "operator");
        if (candidateFingerprint == null || !candidateFingerprint.matches("[0-9a-f]{64}")
                || fixtureSha256 == null || !fixtureSha256.matches("[0-9a-f]{64}")
                || verificationSqlSha256 == null || !verificationSqlSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("canonical SHA-256 values are required");
        }
        probeDigests = List.copyOf(probeDigests);
        if (probeDigests.size() != 50 || probeDigests.stream().anyMatch(hash -> !hash.matches("[0-9a-f]{64}"))
                || modelCalls < 1 || modelCalls > 100 || totalTokens < 1 || totalTokens > 200_000
                || !Double.isFinite(totalCostCny) || totalCostCny <= 0 || totalCostCny > 10.0D) {
            throw new IllegalArgumentException("complete bounded probe evidence is required");
        }
    }
}
