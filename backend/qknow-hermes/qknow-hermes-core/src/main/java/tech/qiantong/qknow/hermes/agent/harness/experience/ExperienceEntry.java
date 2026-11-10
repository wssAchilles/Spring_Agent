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
        int consecutiveFailures,
        StrategyLifecycleStatus lifecycleStatus,
        int probationUses,
        int probationSuccesses,
        Instant lastUsedAt,
        Instant promotedAt,
        Instant retiredAt,
        StrategyTemplate strategyTemplate,
        StrategyVariant strategyVariant,
        FeedbackProvenance feedbackProvenance
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
        if (probationUses < 0 || probationSuccesses < 0 || probationSuccesses > probationUses) {
            throw new IllegalArgumentException("probation counts must satisfy 0 <= successes <= uses");
        }
        if (feedbackProvenance == null) {
            feedbackProvenance = FeedbackProvenance.NONE;
        }
        if (lifecycleStatus == null) {
            lifecycleStatus = retired ? StrategyLifecycleStatus.RETIRED : initialStatus(disposition);
        }
        if (retired && lifecycleStatus != StrategyLifecycleStatus.RETIRED) {
            lifecycleStatus = StrategyLifecycleStatus.RETIRED;
        }
        if (lifecycleStatus == StrategyLifecycleStatus.RETIRED && !retired) {
            throw new IllegalArgumentException("RETIRED lifecycle requires retired=true");
        }
        if (lifecycleStatus == StrategyLifecycleStatus.ADOPTED
                && disposition != ExperienceDisposition.ADOPTED) {
            throw new IllegalArgumentException("ADOPTED lifecycle requires ADOPTED disposition");
        }
        if (lifecycleStatus == StrategyLifecycleStatus.PROBATION
                && disposition != ExperienceDisposition.OBSERVED) {
            throw new IllegalArgumentException("PROBATION lifecycle requires OBSERVED disposition");
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
        if (lifecycleStatus == StrategyLifecycleStatus.RETIRED && retiredAt == null) {
            retiredAt = createdAt;
        }
        if (lifecycleStatus == StrategyLifecycleStatus.ADOPTED && promotedAt == null) {
            promotedAt = createdAt;
        }
        if (strategyVariant != null && (strategyTemplate == null
                || !strategyVariant.variantId().equals(strategyTemplate.templateId())
                || !strategyVariant.parentTemplateId().equals(strategyTemplate.parentTemplateId()))) {
            throw new IllegalArgumentException("variant and template lineage must agree");
        }
    }

    public ExperienceEntry(String entryId, ExperienceDomain domain, String cue, String lesson,
                           String evidenceSummary, EvidenceReference evidenceRef, double confidence,
                           String sourceRunId, String harnessFingerprint, ExperienceDisposition disposition,
                           boolean failureObserved, AttributionStatus attributionStatus, int version,
                           boolean retired, String retirementReason, Instant createdAt,
                           int feedbackAttempts, int feedbackSuccesses, int consecutiveFailures) {
        this(entryId, domain, cue, lesson, evidenceSummary, evidenceRef, confidence, sourceRunId,
                harnessFingerprint, disposition, failureObserved, attributionStatus, version, retired,
                retirementReason, createdAt, feedbackAttempts, feedbackSuccesses, consecutiveFailures,
                retired ? StrategyLifecycleStatus.RETIRED : initialStatus(disposition), 0, 0, null,
                disposition == ExperienceDisposition.ADOPTED ? createdAt : null,
                retired ? createdAt : null, null, null, FeedbackProvenance.NONE);
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

    private static StrategyLifecycleStatus initialStatus(ExperienceDisposition disposition) {
        return disposition == ExperienceDisposition.ADOPTED
                ? StrategyLifecycleStatus.ADOPTED : StrategyLifecycleStatus.CANDIDATE;
    }

    public boolean isSoftRetired(double retireThreshold) {
        return retired || lifecycleStatus == StrategyLifecycleStatus.RETIRED
                || confidence < retireThreshold || disposition == ExperienceDisposition.INVALID_FIXTURE
                || disposition == ExperienceDisposition.REJECTED;
    }

    public boolean isEligibleForPositiveProposer(double retireThreshold) {
        return !isSoftRetired(retireThreshold) && !failureObserved && !domain.isUnscopedReplay();
    }

    public boolean isEligibleForStrategySelection(double retireThreshold) {
        return !isSoftRetired(retireThreshold) && !failureObserved && !domain.isUnscopedReplay()
                && (lifecycleStatus == StrategyLifecycleStatus.ADOPTED
                || lifecycleStatus == StrategyLifecycleStatus.PROBATION);
    }

    public ExperienceEntry withFeedback(boolean success, EvidenceReference latestEvidence,
                                        String latestRunId, String latestHarnessFingerprint) {
        return withFeedback(success, latestEvidence, latestRunId, latestHarnessFingerprint,
                FeedbackProvenance.empirical(success));
    }

    public ExperienceEntry withFeedback(boolean success, EvidenceReference latestEvidence,
                                        String latestRunId, String latestHarnessFingerprint,
                                        FeedbackProvenance provenance) {
        if (lifecycleStatus != StrategyLifecycleStatus.ADOPTED) {
            throw new IllegalStateException("general feedback requires an adopted strategy");
        }
        Objects.requireNonNull(provenance, "provenance");
        provenance.validate(success);
        return updatedFeedback(success, latestEvidence, latestRunId, latestHarnessFingerprint,
                StrategyLifecycleStatus.ADOPTED, probationUses, probationSuccesses, promotedAt,
                Instant.now(), Integer.MAX_VALUE, Integer.MAX_VALUE, provenance);
    }

    public ExperienceEntry withProbationFeedback(boolean success, EvidenceReference latestEvidence,
                                                 String latestRunId, String latestHarnessFingerprint,
                                                 int minUses, int minSuccesses) {
        return withProbationFeedback(success, latestEvidence, latestRunId, latestHarnessFingerprint,
                minUses, minSuccesses, FeedbackProvenance.empirical(success));
    }

    public ExperienceEntry withProbationFeedback(boolean success, EvidenceReference latestEvidence,
                                                  String latestRunId, String latestHarnessFingerprint,
                                                  int minUses, int minSuccesses,
                                                  FeedbackProvenance provenance) {
        if (lifecycleStatus != StrategyLifecycleStatus.PROBATION) {
            throw new IllegalStateException("probation feedback requires a probation strategy");
        }
        if (minUses < 1 || minSuccesses < 1 || minSuccesses > minUses) {
            throw new IllegalArgumentException("invalid probation promotion thresholds");
        }
        Objects.requireNonNull(provenance, "provenance");
        provenance.validate(success);
        return updatedFeedback(success, latestEvidence, latestRunId, latestHarnessFingerprint,
                StrategyLifecycleStatus.PROBATION, probationUses + 1,
                probationSuccesses + (success && provenance.contributesSuccess() ? 1 : 0),
                promotedAt, Instant.now(), minUses, minSuccesses, provenance);
    }

    private ExperienceEntry updatedFeedback(boolean success, EvidenceReference latestEvidence,
                                             String latestRunId, String latestHarnessFingerprint,
                                             StrategyLifecycleStatus priorStatus, int nextProbationUses,
                                             int nextProbationSuccesses, Instant priorPromotedAt,
                                             Instant usedAt, int minUses, int minSuccesses,
                                             FeedbackProvenance provenance) {
        boolean countsSuccess = success && provenance.contributesSuccess();
        boolean countsFailure = !success && provenance.contributesFailure();
        int attempts = feedbackAttempts + (provenance.isIndependent() ? 1 : 0);
        int successes = feedbackSuccesses + (countsSuccess ? 1 : 0);
        int failuresInARow = countsFailure ? consecutiveFailures + 1
                : countsSuccess ? 0 : consecutiveFailures;
        boolean shouldRetire = failuresInARow >= 3;
        boolean shouldPromote = countsSuccess && !shouldRetire && priorStatus == StrategyLifecycleStatus.PROBATION
                && nextProbationUses >= minUses && nextProbationSuccesses >= minSuccesses;
        double nextConfidence = provenance.isIndependent()
                ? (successes + 1.0) / (attempts + 2.0) : confidence;
        StrategyLifecycleStatus nextStatus = shouldRetire ? StrategyLifecycleStatus.RETIRED
                : shouldPromote ? StrategyLifecycleStatus.ADOPTED : priorStatus;
        ExperienceDisposition nextDisposition = nextStatus == StrategyLifecycleStatus.ADOPTED
                ? ExperienceDisposition.ADOPTED
                : nextStatus == StrategyLifecycleStatus.PROBATION ? ExperienceDisposition.OBSERVED : disposition;
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, latestEvidence,
                nextConfidence, latestRunId, latestHarnessFingerprint,
                nextDisposition, false, attributionStatus, version + 1, shouldRetire,
                shouldRetire ? "runtime_strategy_failed_3_consecutive_times" : null,
                createdAt, attempts, successes, failuresInARow, nextStatus, nextProbationUses,
                nextProbationSuccesses, usedAt, shouldPromote ? usedAt : priorPromotedAt,
                shouldRetire ? usedAt : null, strategyTemplate, strategyVariant, provenance);
    }

    public ExperienceEntry probationary(int nextVersion) {
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, evidenceRef,
                confidence, sourceRunId, harnessFingerprint, ExperienceDisposition.OBSERVED, false,
                attributionStatus, nextVersion, false, null, createdAt, feedbackAttempts,
                feedbackSuccesses, 0, StrategyLifecycleStatus.PROBATION, 0, 0, null, null, null,
                strategyTemplate, strategyVariant, FeedbackProvenance.NONE);
    }

    public ExperienceEntry promoted(int nextVersion, Instant now) {
        return promoted(nextVersion, evidenceRef, sourceRunId, harnessFingerprint, now);
    }

    public ExperienceEntry promoted(int nextVersion, EvidenceReference latestEvidence,
                                    String latestRunId, String latestHarnessFingerprint, Instant now) {
        if (lifecycleStatus == StrategyLifecycleStatus.RETIRED || retired) {
            throw new IllegalStateException("cannot promote a retired strategy");
        }
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, latestEvidence,
                confidence, latestRunId, latestHarnessFingerprint, ExperienceDisposition.ADOPTED,
                false, attributionStatus, nextVersion, false, null, createdAt, feedbackAttempts,
                feedbackSuccesses, 0, StrategyLifecycleStatus.ADOPTED, probationUses,
                probationSuccesses, lastUsedAt, now, null, strategyTemplate, strategyVariant,
                feedbackProvenance);
    }

    public ExperienceEntry retired(String reason) {
        Instant now = Instant.now();
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, evidenceRef,
                confidence, sourceRunId, harnessFingerprint, disposition, failureObserved,
                attributionStatus, version + 1, true, reason, createdAt, feedbackAttempts,
                feedbackSuccesses, consecutiveFailures, StrategyLifecycleStatus.RETIRED,
                probationUses, probationSuccesses, lastUsedAt, promotedAt, now,
                strategyTemplate, strategyVariant, feedbackProvenance);
    }

    public ExperienceEntry restoredAsVersion(int nextVersion) {
        StrategyLifecycleStatus restoredStatus = initialStatus(disposition);
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, evidenceRef,
                confidence, sourceRunId, harnessFingerprint, disposition, failureObserved,
                attributionStatus, nextVersion, false, null, createdAt, feedbackAttempts,
                feedbackSuccesses, consecutiveFailures, restoredStatus, probationUses,
                probationSuccesses, lastUsedAt,
                restoredStatus == StrategyLifecycleStatus.ADOPTED ? promotedAt : null, null,
                strategyTemplate, strategyVariant, feedbackProvenance);
    }

    public ExperienceEntry restoredAsProbation(int nextVersion) {
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, evidenceRef,
                confidence, sourceRunId, harnessFingerprint, ExperienceDisposition.OBSERVED, false,
                attributionStatus, nextVersion, false, null, createdAt, feedbackAttempts,
                feedbackSuccesses, 0, StrategyLifecycleStatus.PROBATION, 0, 0, null, null, null,
                strategyTemplate, strategyVariant, FeedbackProvenance.NONE);
    }

    public ExperienceEntry withStrategy(StrategyTemplate template, StrategyVariant variant) {
        return new ExperienceEntry(entryId, domain, cue, lesson, evidenceSummary, evidenceRef,
                confidence, sourceRunId, harnessFingerprint, disposition, failureObserved,
                attributionStatus, version, retired, retirementReason, createdAt, feedbackAttempts,
                feedbackSuccesses, consecutiveFailures, lifecycleStatus, probationUses,
                probationSuccesses, lastUsedAt, promotedAt, retiredAt, template, variant,
                feedbackProvenance);
    }
}
