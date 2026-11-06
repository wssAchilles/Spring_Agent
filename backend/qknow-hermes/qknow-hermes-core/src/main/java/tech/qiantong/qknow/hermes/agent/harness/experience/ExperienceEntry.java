package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.time.Instant;
import java.util.Objects;

public record ExperienceEntry(
        String entryId,
        ExperienceDomain domain,
        String cue,
        String lesson,
        String evidenceSummary,
        EvidenceReference evidenceRef,
        double confidence,
        String sourceRunId,
        String harnessFingerprint,
        ExperienceDisposition disposition,
        boolean failureObserved,
        AttributionStatus attributionStatus,
        int version,
        boolean retired,
        String retirementReason,
        Instant createdAt,
        int feedbackAttempts,
        int feedbackSuccesses,
        int consecutiveFailures
) {
    public enum AttributionStatus {
        NOT_APPLICABLE,
        UNRESOLVED,
        FIXTURE_CONFIRMED,
        AGENT_CONFIRMED
    }

    public ExperienceEntry {
        if (entryId == null || !entryId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                || entryId.equals("..")) {
            throw new IllegalArgumentException("safe entryId is required");
        }
        Objects.requireNonNull(domain, "domain");
        if (cue == null || cue.length() < 5 || lesson == null || lesson.length() < 10) {
            throw new IllegalArgumentException("cue and lesson are required");
        }
        Objects.requireNonNull(evidenceRef, "evidenceRef");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
        if (sourceRunId == null || sourceRunId.isBlank()
                || harnessFingerprint == null || !harnessFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceRunId and SHA-256 fingerprint are required");
        }
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(attributionStatus, "attributionStatus");
        Objects.requireNonNull(createdAt, "createdAt");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (feedbackAttempts < 0 || feedbackSuccesses < 0 || feedbackSuccesses > feedbackAttempts
                || consecutiveFailures < 0 || consecutiveFailures > feedbackAttempts - feedbackSuccesses) {
            throw new IllegalArgumentException("feedback counts must satisfy 0 <= successes <= attempts");
        }
        if (disposition == ExperienceDisposition.INVALID_FIXTURE
                && attributionStatus != AttributionStatus.FIXTURE_CONFIRMED) {
            throw new IllegalArgumentException("INVALID_FIXTURE requires confirmed attribution");
        }
        if (failureObserved && disposition == ExperienceDisposition.ADOPTED) {
            throw new IllegalArgumentException("observed failure cannot be adopted");
        }
        if (failureObserved && attributionStatus == AttributionStatus.NOT_APPLICABLE) {
            throw new IllegalArgumentException("observed failure requires attribution status");
        }
        if (retired && (retirementReason == null || retirementReason.isBlank())) {
            throw new IllegalArgumentException("retirementReason is required");
        }
    }

    public ExperienceEntry(String entryId, ExperienceDomain domain, String cue, String lesson,
                           String evidenceSummary, EvidenceReference evidenceRef, double confidence,
                           String sourceRunId, String harnessFingerprint, ExperienceDisposition disposition,
                           boolean failureObserved, AttributionStatus attributionStatus, int version,
                           boolean retired, String retirementReason, Instant createdAt) {
        this(entryId, domain, cue, lesson, evidenceSummary, evidenceRef, confidence, sourceRunId,
                harnessFingerprint, disposition, failureObserved, attributionStatus, version, retired,
                retirementReason, createdAt, 0, 0, 0);
    }

    public ExperienceEntry(String entryId, ExperienceDomain domain, String cue, String lesson,
                           String evidenceSummary, EvidenceReference evidenceRef, double confidence,
                           String sourceRunId, String harnessFingerprint, ExperienceDisposition disposition,
                           boolean failureObserved, AttributionStatus attributionStatus, int version,
                           boolean retired, String retirementReason, Instant createdAt,
                           int feedbackAttempts, int feedbackSuccesses) {
        this(entryId, domain, cue, lesson, evidenceSummary, evidenceRef, confidence, sourceRunId,
                harnessFingerprint, disposition, failureObserved, attributionStatus, version, retired,
                retirementReason, createdAt, feedbackAttempts, feedbackSuccesses, 0);
    }

    public boolean isSoftRetired(double retireThreshold) {
        return retired || confidence < retireThreshold
                || disposition == ExperienceDisposition.INVALID_FIXTURE
                || disposition == ExperienceDisposition.REJECTED;
    }

    public boolean isEligibleForPositiveProposer(double retireThreshold) {
        return !isSoftRetired(retireThreshold) && !failureObserved && !domain.isUnscopedReplay();
    }

    public ExperienceEntry retired(String reason) {
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, evidenceRef,
                confidence, sourceRunId, harnessFingerprint, disposition, failureObserved,
                attributionStatus, version + 1, true, reason, createdAt, feedbackAttempts, feedbackSuccesses,
                consecutiveFailures);
    }

    public ExperienceEntry withFeedback(boolean success, EvidenceReference latestEvidence,
                                        String latestRunId, String latestHarnessFingerprint) {
        int attempts = feedbackAttempts + 1;
        int successes = feedbackSuccesses + (success ? 1 : 0);
        int failuresInARow = success ? 0 : consecutiveFailures + 1;
        boolean shouldRetire = failuresInARow >= 3;
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, latestEvidence,
                (successes + 1.0) / (attempts + 2.0), latestRunId, latestHarnessFingerprint,
                disposition, false, attributionStatus, version + 1, shouldRetire,
                shouldRetire ? "runtime_strategy_failed_3_consecutive_times" : null,
                createdAt, attempts, successes, failuresInARow);
    }

    public ExperienceEntry restoredAsVersion(int nextVersion) {
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, evidenceRef,
                confidence, sourceRunId, harnessFingerprint, disposition, failureObserved,
                attributionStatus, nextVersion, false, null, createdAt,
                feedbackAttempts, feedbackSuccesses, consecutiveFailures);
    }
}
