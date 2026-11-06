package tech.qiantong.qknow.hermes.cognitive.core.verification;

import java.util.Objects;

/** Redacted, reproducible result signal. It stores digests, never the verified payload. */
public record OutcomeSignal(
        String taskId,
        String familyId,
        Status status,
        String reasonCode,
        String expectedDigest,
        String observedDigest,
        String evidenceDigest) {

    public enum Status { PASS, FAIL, UNKNOWN }

    public OutcomeSignal {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        if (familyId == null || familyId.isBlank()) {
            throw new IllegalArgumentException("familyId is required");
        }
        Objects.requireNonNull(status, "status");
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        expectedDigest = normalizeDigest(expectedDigest);
        observedDigest = normalizeDigest(observedDigest);
        evidenceDigest = normalizeDigest(evidenceDigest);
        if (status == Status.PASS
                && (expectedDigest.isBlank() || observedDigest.isBlank() || evidenceDigest.isBlank())) {
            throw new IllegalArgumentException("PASS requires expected, observed and evidence digests");
        }
    }

    public boolean isPass() {
        return status == Status.PASS;
    }

    private static String normalizeDigest(String digest) {
        if (digest == null || digest.isBlank()) {
            return "";
        }
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a SHA-256 hexadecimal value");
        }
        return digest;
    }
}
