package tech.qiantong.qknow.hermes.agent.harness.experience;

public record EvidenceReference(
        String evidenceId,
        String requestId,
        String evidenceSha256,
        String executionMode
) {
    public EvidenceReference {
        if (evidenceId == null || evidenceId.isBlank()
                || requestId == null || requestId.isBlank()
                || evidenceSha256 == null || !evidenceSha256.matches("[0-9a-f]{64}")
                || executionMode == null || executionMode.isBlank()) {
            throw new IllegalArgumentException("complete evidence reference is required");
        }
    }
}
