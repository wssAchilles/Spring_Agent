package tech.qiantong.qknow.hermes.cognitive.core.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Redacted business outcome asserted by a deterministic tool-domain adapter. */
public record CognitiveToolOutcomeEvidence(Status status, String reasonCode, String evidenceDigest) {
    public enum Status { APPLIED, REJECTED, UNKNOWN }

    public CognitiveToolOutcomeEvidence {
        Objects.requireNonNull(status, "status");
        if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("reasonCode must be a stable uppercase code");
        }
        evidenceDigest = evidenceDigest == null ? "" : evidenceDigest;
        if (status == Status.UNKNOWN) {
            if (!evidenceDigest.isEmpty()) {
                throw new IllegalArgumentException("UNKNOWN cannot carry an outcome evidence digest");
            }
        } else if (!evidenceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("known outcomes require a SHA-256 evidence digest");
        }
    }

    public static CognitiveToolOutcomeEvidence unknown(String reasonCode) {
        return new CognitiveToolOutcomeEvidence(Status.UNKNOWN, reasonCode, "");
    }

    public static CognitiveToolOutcomeEvidence fromCanonicalState(Status status, String reasonCode,
                                                                   String canonicalBusinessState) {
        if (status == null || status == Status.UNKNOWN || canonicalBusinessState == null
                || canonicalBusinessState.isBlank()) {
            return unknown("BUSINESS_STATE_UNAVAILABLE");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalBusinessState.getBytes(StandardCharsets.UTF_8));
            return new CognitiveToolOutcomeEvidence(status, reasonCode, java.util.HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
