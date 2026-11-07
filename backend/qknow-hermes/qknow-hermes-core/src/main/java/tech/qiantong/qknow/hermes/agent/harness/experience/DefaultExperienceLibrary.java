package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class DefaultExperienceLibrary implements ExperienceLibrary {
    private static final double RETIRE_THRESHOLD = 0.20;
    private static final int MAX_ACTIVE_STRATEGIES_PER_DOMAIN = 32;
    private static final int DEFAULT_PROBATION_MIN_USES = 3;
    private static final int DEFAULT_PROBATION_MIN_SUCCESSES = 2;
    private final ExperienceStore store;
    private final int probationMinUses;
    private final int probationMinSuccesses;
    private final ConcurrentHashMap<ExperienceDomain, ReentrantLock> mutationLocks = new ConcurrentHashMap<>();

    public DefaultExperienceLibrary(ExperienceStore store) {
        this(store, DEFAULT_PROBATION_MIN_USES, DEFAULT_PROBATION_MIN_SUCCESSES);
    }

    public DefaultExperienceLibrary(ExperienceStore store, int probationMinUses, int probationMinSuccesses) {
        this.store = Objects.requireNonNull(store, "store");
        if (probationMinUses < 1 || probationMinSuccesses < 1 || probationMinSuccesses > probationMinUses) {
            throw new IllegalArgumentException("invalid probation promotion thresholds");
        }
        this.probationMinUses = probationMinUses;
        this.probationMinSuccesses = probationMinSuccesses;
    }

    @Override
    public ExperienceRecall recallForProposer(ExperienceDomain domain, String queryCue, int maxResults) {
        return recall(domain, queryCue, maxResults, false);
    }

    @Override
    public ExperienceRecall recallForStrategySelection(ExperienceDomain domain, String queryCue, int maxResults) {
        return recall(domain, queryCue, maxResults, true);
    }

    private ExperienceRecall recall(ExperienceDomain domain, String queryCue, int maxResults,
                                   boolean includeProbation) {
        Objects.requireNonNull(domain, "domain");
        if (queryCue == null || queryCue.isBlank() || maxResults < 1) {
            throw new IllegalArgumentException("queryCue and positive maxResults are required");
        }
        if (domain.isUnscopedReplay()) {
            return new ExperienceRecall(domain, queryCue, List.of(), 0, 0);
        }
        List<ExperienceEntry> entries = store.list(domain);
        String needle = queryCue.toLowerCase(Locale.ROOT);
        java.util.function.Predicate<ExperienceEntry> eligible = includeProbation
                ? entry -> entry.isEligibleForStrategySelection(RETIRE_THRESHOLD)
                : entry -> entry.isEligibleForPositiveProposer(RETIRE_THRESHOLD)
                && entry.lifecycleStatus() != StrategyLifecycleStatus.PROBATION;
        List<ExperienceEntry> matches = entries.stream()
                .filter(eligible)
                .filter(entry -> entry.cue().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingDouble(ExperienceEntry::confidence).reversed()
                        .thenComparing(ExperienceEntry::entryId))
                .limit(maxResults)
                .toList();
        int filtered = (int) entries.stream()
                .filter(eligible.negate()).count();
        return new ExperienceRecall(domain, queryCue, matches, entries.size(), filtered);
    }

    @Override
    public java.util.Optional<ExperienceEntry> find(ExperienceDomain domain, String entryId) {
        return store.find(Objects.requireNonNull(domain, "domain"), entryId);
    }

    @Override
    public ExperienceEntry recordExperience(ExperienceEntry entry) {
        Objects.requireNonNull(entry, "entry");
        ReentrantLock lock = lock(entry.domain());
        lock.lock();
        try {
            if (store.find(entry.domain(), entry.entryId()).isEmpty() && isActiveStrategy(entry)) {
                ensureActiveCapacity(entry.domain());
            }
            ExperienceLeakageGuard.assertSafeEntry(entry);
            return store.save(entry);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExperienceEntry adoptRuntimeStrategy(ExperienceEntry candidate) {
        Objects.requireNonNull(candidate, "candidate");
        ReentrantLock lock = lock(candidate.domain());
        lock.lock();
        try {
            ExperienceEntry current = store.find(candidate.domain(), candidate.entryId()).orElse(null);
            if (current == null) {
                ensureActiveCapacity(candidate.domain());
                ExperienceLeakageGuard.assertSafeEntry(candidate);
                return store.save(candidate.promoted(candidate.version(), Instant.now()));
            }
            if (current.retired()) {
                throw new IllegalStateException("cannot auto-adopt a retired strategy");
            }
            ExperienceEntry confirmed = current.lifecycleStatus() == StrategyLifecycleStatus.ADOPTED
                    ? current.withFeedback(true, candidate.evidenceRef(),
                    candidate.sourceRunId(), candidate.harnessFingerprint())
                    : current.promoted(current.version() + 1, candidate.evidenceRef(),
                    candidate.sourceRunId(), candidate.harnessFingerprint(), Instant.now());
            ExperienceLeakageGuard.assertSafeEntry(confirmed);
            return store.save(confirmed);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExperienceEntry createProbation(ExperienceEntry candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.failureObserved() || candidate.retired()
                || candidate.lifecycleStatus() != StrategyLifecycleStatus.CANDIDATE
                || candidate.disposition() == ExperienceDisposition.REJECTED
                || candidate.disposition() == ExperienceDisposition.INVALID_FIXTURE) {
            throw new IllegalArgumentException("failed or rejected strategy cannot enter probation");
        }
        ReentrantLock lock = lock(candidate.domain());
        lock.lock();
        try {
            ExperienceEntry current = store.find(candidate.domain(), candidate.entryId()).orElse(null);
            if (current == null) {
                ensureActiveCapacity(candidate.domain());
                ExperienceEntry probation = candidate.probationary(1);
                ExperienceLeakageGuard.assertSafeEntry(probation);
                return store.save(probation);
            }
            if (current.lifecycleStatus() == StrategyLifecycleStatus.RETIRED) {
                throw new IllegalStateException("retired strategy requires explicit restore before probation");
            }
            if (current.lifecycleStatus() == StrategyLifecycleStatus.ADOPTED
                    || current.lifecycleStatus() == StrategyLifecycleStatus.PROBATION) {
                return current;
            }
            ExperienceEntry probation = current.probationary(current.version() + 1);
            ExperienceLeakageGuard.assertSafeEntry(probation);
            return store.save(probation);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExperienceEntry recordFeedback(ExperienceDomain domain, String entryId, boolean success,
                                          EvidenceReference evidence, String runId,
                                          String harnessFingerprint) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(evidence, "evidence");
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry current = store.find(domain, entryId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience " + entryId));
            if (current.retired()) {
                throw new IllegalStateException("cannot update retired experience " + entryId);
            }
            ExperienceEntry updated = current.lifecycleStatus() == StrategyLifecycleStatus.PROBATION
                    ? current.withProbationFeedback(success, evidence, runId, harnessFingerprint,
                    probationMinUses, probationMinSuccesses)
                    : current.withFeedback(success, evidence, runId, harnessFingerprint);
            ExperienceLeakageGuard.assertSafeEntry(updated);
            return store.save(updated);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExperienceEntry recordProbationFeedback(ExperienceDomain domain, String entryId, boolean success,
                                                   EvidenceReference evidence, String runId,
                                                   String harnessFingerprint) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(evidence, "evidence");
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry current = store.find(domain, entryId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience " + entryId));
            if (current.lifecycleStatus() != StrategyLifecycleStatus.PROBATION) {
                throw new IllegalStateException("strategy is not in probation " + entryId);
            }
            ExperienceEntry updated = current.withProbationFeedback(success, evidence, runId,
                    harnessFingerprint, probationMinUses, probationMinSuccesses);
            ExperienceLeakageGuard.assertSafeEntry(updated);
            return store.save(updated);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExperienceEntry promote(ExperienceDomain domain, String entryId) {
        Objects.requireNonNull(domain, "domain");
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry current = store.find(domain, entryId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience " + entryId));
            if (current.lifecycleStatus() == StrategyLifecycleStatus.RETIRED) {
                throw new IllegalStateException("cannot promote a retired strategy");
            }
            if (current.lifecycleStatus() == StrategyLifecycleStatus.ADOPTED) {
                return current;
            }
            ExperienceEntry promoted = current.promoted(current.version() + 1, Instant.now());
            ExperienceLeakageGuard.assertSafeEntry(promoted);
            return store.save(promoted);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExperienceEntry restore(ExperienceDomain domain, String entryId, int version) {
        Objects.requireNonNull(domain, "domain");
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry current = store.find(domain, entryId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience " + entryId));
            ExperienceEntry archived = store.findVersion(domain, entryId, version)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience version " + version));
            ExperienceEntry restored = archived.restoredAsVersion(current.version() + 1);
            ExperienceLeakageGuard.assertSafeEntry(restored);
            return store.save(restored);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExperienceEntry restoreToProbation(ExperienceDomain domain, String entryId, int version) {
        Objects.requireNonNull(domain, "domain");
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry current = store.find(domain, entryId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience " + entryId));
            if (current.lifecycleStatus() != StrategyLifecycleStatus.RETIRED) {
                throw new IllegalStateException("only retired strategies can be restored to probation");
            }
            ExperienceEntry archived = store.findVersion(domain, entryId, version)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience version " + version));
            ExperienceEntry restored = archived.restoredAsProbation(current.version() + 1);
            ExperienceLeakageGuard.assertSafeEntry(restored);
            return store.save(restored);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean retire(ExperienceDomain domain, String entryId, String retirementReason) {
        Objects.requireNonNull(domain, "domain");
        if (retirementReason == null || retirementReason.isBlank()) {
            throw new IllegalArgumentException("retirementReason is required");
        }
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry current = store.find(domain, entryId).orElse(null);
            if (current == null || current.retired()) {
                return false;
            }
            ExperienceEntry updated = current.retired(retirementReason);
            ExperienceLeakageGuard.assertSafeEntry(updated);
            store.save(updated);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lock(ExperienceDomain domain) {
        return mutationLocks.computeIfAbsent(domain, ignored -> new ReentrantLock());
    }

    private void ensureActiveCapacity(ExperienceDomain domain) {
        long activeStrategies = store.list(domain).stream()
                .filter(this::isActiveStrategy)
                .count();
        if (activeStrategies >= MAX_ACTIVE_STRATEGIES_PER_DOMAIN) {
            throw new IllegalStateException("active strategy capacity reached for domain " + domain.toSlug());
        }
    }

    private boolean isActiveStrategy(ExperienceEntry entry) {
        return !entry.retired() && entry.lifecycleStatus() != StrategyLifecycleStatus.RETIRED
                && entry.disposition() != ExperienceDisposition.REJECTED
                && entry.disposition() != ExperienceDisposition.INVALID_FIXTURE;
    }
}
