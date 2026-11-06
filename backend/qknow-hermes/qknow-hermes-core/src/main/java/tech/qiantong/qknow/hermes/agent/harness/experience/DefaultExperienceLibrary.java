package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class DefaultExperienceLibrary implements ExperienceLibrary {
    private static final double RETIRE_THRESHOLD = 0.20;
    private final ExperienceStore store;
    private final ConcurrentHashMap<ExperienceDomain, ReentrantLock> mutationLocks = new ConcurrentHashMap<>();

    public DefaultExperienceLibrary(ExperienceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public ExperienceRecall recallForProposer(ExperienceDomain domain, String queryCue, int maxResults) {
        Objects.requireNonNull(domain, "domain");
        if (queryCue == null || queryCue.isBlank() || maxResults < 1) {
            throw new IllegalArgumentException("queryCue and positive maxResults are required");
        }
        if (domain.isUnscopedReplay()) {
            return new ExperienceRecall(domain, queryCue, List.of(), 0, 0);
        }
        List<ExperienceEntry> entries = store.list(domain);
        String needle = queryCue.toLowerCase(Locale.ROOT);
        List<ExperienceEntry> matches = entries.stream()
                .filter(entry -> entry.isEligibleForPositiveProposer(RETIRE_THRESHOLD))
                .filter(entry -> entry.cue().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingDouble(ExperienceEntry::confidence).reversed()
                        .thenComparing(ExperienceEntry::entryId))
                .limit(maxResults)
                .toList();
        int filtered = (int) entries.stream()
                .filter(entry -> !entry.isEligibleForPositiveProposer(RETIRE_THRESHOLD)).count();
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
                ExperienceLeakageGuard.assertSafeEntry(candidate);
                return store.save(candidate);
            }
            if (current.retired()) {
                throw new IllegalStateException("cannot auto-adopt a retired strategy");
            }
            ExperienceEntry confirmed = current.withFeedback(true, candidate.evidenceRef(),
                    candidate.sourceRunId(), candidate.harnessFingerprint());
            ExperienceLeakageGuard.assertSafeEntry(confirmed);
            return store.save(confirmed);
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
            ExperienceEntry updated = current.withFeedback(success, evidence, runId, harnessFingerprint);
            ExperienceLeakageGuard.assertSafeEntry(updated);
            return store.save(updated);
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
}
