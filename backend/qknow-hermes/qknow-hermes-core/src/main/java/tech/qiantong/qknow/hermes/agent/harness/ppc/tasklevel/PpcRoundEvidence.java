package tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record PpcRoundEvidence(String taskId, int round, String startingFingerprint, Score startingScore,
                               List<CandidateProbe> candidates, ExperienceGuidedProposal selected,
                               String selectedFingerprint) {
    public PpcRoundEvidence {
        if (taskId == null || taskId.isBlank() || round < 1) {
            throw new IllegalArgumentException("taskId and positive round are required");
        }
        Objects.requireNonNull(startingFingerprint, "startingFingerprint");
        Objects.requireNonNull(startingScore, "startingScore");
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(selectedFingerprint, "selectedFingerprint");
    }

    public boolean improved() {
        return selected != null;
    }

    public String computeDigest() {
        StringBuilder canonical = new StringBuilder(taskId).append('\u001f').append(round)
                .append('\u001f').append(startingFingerprint)
                .append('\u001f').append(startingScore);
        for (CandidateProbe candidate : candidates) {
            canonical.append('\u001e').append(candidate);
        }
        canonical.append('\u001f').append(selected).append('\u001f').append(selectedFingerprint);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Score(double quality, boolean safe) {
        public Score {
            if (!Double.isFinite(quality) || quality < 0.0D || quality > 1.0D) {
                throw new IllegalArgumentException("quality must be finite and in [0, 1]");
            }
        }
    }

    public enum ProbeStatus { COMPLETED, FAILED }

    public record CandidateProbe(ExperienceGuidedProposal proposal, String fingerprint,
                                 Score score, ProbeStatus status) {
        public CandidateProbe {
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(status, "status");
            if ((status == ProbeStatus.COMPLETED) != (score != null)) {
                throw new IllegalArgumentException("only completed probes may carry a score");
            }
        }
    }
}
