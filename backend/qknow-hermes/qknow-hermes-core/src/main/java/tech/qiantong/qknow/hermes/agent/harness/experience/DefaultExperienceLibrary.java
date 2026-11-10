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
    private static final int MAX_VARIANTS_PER_TEMPLATE = 2;
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
    public ExperienceEntry createVariant(ExperienceEntry candidate) {
        Objects.requireNonNull(candidate, "candidate");
        StrategyVariant variant = candidate.strategyVariant();
        StrategyTemplate template = candidate.strategyTemplate();
        if (variant == null || template == null
                || candidate.lifecycleStatus() != StrategyLifecycleStatus.CANDIDATE
                || !candidate.entryId().equals(variant.variantId())
                || !candidate.entryId().equals(template.templateId())
                || !variant.parentTemplateId().equals(template.parentTemplateId())) {
            throw new IllegalArgumentException("candidate must contain a matching variant and child template");
        }
        ReentrantLock lock = lock(candidate.domain());
        lock.lock();
        try {
            ExperienceEntry parent = findByTemplate(candidate.domain(), variant.parentTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("parent template is not in this domain"));
            if (parent.lifecycleStatus() == StrategyLifecycleStatus.RETIRED) {
                throw new IllegalStateException("cannot mutate a retired parent template");
            }
            if (parent.lifecycleStatus() != StrategyLifecycleStatus.ADOPTED
                    && parent.lifecycleStatus() != StrategyLifecycleStatus.PROBATION) {
                throw new IllegalStateException("parent template is not eligible for mutation");
            }
            if (template.version() != parent.strategyTemplate().version() + 1) {
                throw new IllegalArgumentException("variant template version must follow its parent");
            }
            long variants = store.list(candidate.domain()).stream()
                    .filter(entry -> entry.strategyVariant() != null)
                    .filter(entry -> variant.parentTemplateId().equals(
                            entry.strategyVariant().parentTemplateId()))
                    .count();
            if (variants >= MAX_VARIANTS_PER_TEMPLATE) {
                throw new IllegalStateException("variant limit reached for parent " + variant.parentTemplateId());
            }
            if (store.find(candidate.domain(), candidate.entryId()).isPresent()) {
                throw new IllegalStateException("variant already exists " + candidate.entryId());
            }
            ensureActiveCapacity(candidate.domain());
            ExperienceEntry probation = candidate.probationary(1);
            ExperienceLeakageGuard.assertSafeEntry(probation);
            return store.save(probation);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public java.util.Optional<StrategyLineage> lineage(ExperienceDomain domain, String templateId) {
        Objects.requireNonNull(domain, "domain");
        if (templateId == null || templateId.isBlank() || domain.isUnscopedReplay()) {
            return java.util.Optional.empty();
        }
        List<ExperienceEntry> currentEntries = store.list(domain);
        ExperienceEntry target = currentEntries.stream()
                .filter(entry -> entry.strategyTemplate() != null)
                .filter(entry -> entry.strategyTemplate().templateId().equals(templateId))
                .findFirst().orElse(null);
        if (target == null) {
            return java.util.Optional.empty();
        }
        java.util.Map<String, ExperienceEntry> byTemplate = new java.util.HashMap<>();
        currentEntries.stream().filter(entry -> entry.strategyTemplate() != null)
                .forEach(entry -> byTemplate.put(entry.strategyTemplate().templateId(), entry));
        String rootId = templateId;
        java.util.Set<String> visited = new java.util.HashSet<>();
        String parentId = target.strategyTemplate().parentTemplateId();
        while (parentId != null && visited.add(rootId)) {
            ExperienceEntry parent = byTemplate.get(parentId);
            if (parent == null || parent.strategyTemplate().parentTemplateId() == null) {
                rootId = parent == null ? parentId : parent.strategyTemplate().templateId();
                break;
            }
            rootId = parent.strategyTemplate().templateId();
            parentId = parent.strategyTemplate().parentTemplateId();
        }
        String immediateParent = target.strategyTemplate().parentTemplateId();
        List<String> children = currentEntries.stream()
                .filter(entry -> entry.strategyTemplate() != null)
                .filter(entry -> templateId.equals(entry.strategyTemplate().parentTemplateId()))
                .map(entry -> entry.strategyTemplate().templateId()).sorted().toList();
        List<StrategyLineage.LifecycleEvent> history = lifecycleHistory(domain, currentEntries);
        List<StrategyLineage.LifecycleEvent> adopted = history.stream()
                .filter(event -> event.templateId().equals(templateId))
                .filter(event -> event.status() == StrategyLifecycleStatus.ADOPTED).toList();
        List<StrategyLineage.LifecycleEvent> retired = history.stream()
                .filter(event -> event.templateId().equals(templateId))
                .filter(event -> event.status() == StrategyLifecycleStatus.RETIRED).toList();
        String rollbackPointer = target.lifecycleStatus() == StrategyLifecycleStatus.RETIRED
                ? immediateParent : null;
        return java.util.Optional.of(new StrategyLineage(rootId, immediateParent, children,
                adopted, retired, rollbackPointer));
    }

    @Override
    public java.util.Optional<ExperienceEntry> rollbackToParent(ExperienceDomain domain, String variantId) {
        Objects.requireNonNull(domain, "domain");
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry variant = store.find(domain, variantId).orElse(null);
            if (variant == null || variant.strategyVariant() == null
                    || variant.strategyTemplate() == null) {
                return java.util.Optional.empty();
            }
            if (!variant.retired()) {
                ExperienceEntry retiredVariant = variant.retired("variant_rollback_to_parent");
                ExperienceLeakageGuard.assertSafeEntry(retiredVariant);
                store.save(retiredVariant);
            }
            return findByTemplate(domain, variant.strategyVariant().parentTemplateId())
                    .filter(parent -> parent.lifecycleStatus() != StrategyLifecycleStatus.RETIRED);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExperienceEntry recordFeedback(ExperienceDomain domain, String entryId, boolean success,
                                          EvidenceReference evidence, String runId,
                                          String harnessFingerprint) {
        return recordFeedback(domain, entryId, success, evidence, runId, harnessFingerprint,
                FeedbackProvenance.empirical(success));
    }

    @Override
    public ExperienceEntry recordFeedback(ExperienceDomain domain, String entryId, boolean success,
                                          EvidenceReference evidence, String runId,
                                          String harnessFingerprint, FeedbackProvenance provenance) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(provenance, "provenance");
        provenance.validate(success);
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry current = store.find(domain, entryId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience " + entryId));
            if (current.retired()) {
                throw new IllegalStateException("cannot update retired experience " + entryId);
            }
            if (isDuplicateFeedback(current, evidence, runId)) {
                return current;
            }
            ExperienceEntry updated = current.lifecycleStatus() == StrategyLifecycleStatus.PROBATION
                    ? current.withProbationFeedback(success, evidence, runId, harnessFingerprint,
                    probationMinUses, probationMinSuccesses, provenance)
                    : current.withFeedback(success, evidence, runId, harnessFingerprint, provenance);
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
        return recordProbationFeedback(domain, entryId, success, evidence, runId, harnessFingerprint,
                FeedbackProvenance.empirical(success));
    }

    @Override
    public ExperienceEntry recordProbationFeedback(ExperienceDomain domain, String entryId, boolean success,
                                                   EvidenceReference evidence, String runId,
                                                   String harnessFingerprint, FeedbackProvenance provenance) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(provenance, "provenance");
        provenance.validate(success);
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            ExperienceEntry current = store.find(domain, entryId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown experience " + entryId));
            if (current.lifecycleStatus() != StrategyLifecycleStatus.PROBATION) {
                throw new IllegalStateException("strategy is not in probation " + entryId);
            }
            if (isDuplicateFeedback(current, evidence, runId)) {
                return current;
            }
            ExperienceEntry updated = current.withProbationFeedback(success, evidence, runId,
                    harnessFingerprint, probationMinUses, probationMinSuccesses, provenance);
            ExperienceLeakageGuard.assertSafeEntry(updated);
            return store.save(updated);
        } finally {
            lock.unlock();
        }
    }

    private boolean isDuplicateFeedback(ExperienceEntry current, EvidenceReference evidence, String runId) {
        // 仅当条目已实际记录过至少一次反馈后，才对相同运行与证据的重复反馈进行幂等拦截
        return current.feedbackProvenance() != FeedbackProvenance.NONE
                && runId != null && runId.equals(current.sourceRunId())
                && evidence.evidenceId().equals(current.evidenceRef().evidenceId());
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
            ensureActiveCapacity(domain);
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
        List<ExperienceEntry> activeStrategies = store.list(domain).stream()
                .filter(this::isActiveStrategy).toList();
        if (activeStrategies.size() < MAX_ACTIVE_STRATEGIES_PER_DOMAIN) {
            return;
        }
        Comparator<ExperienceEntry> evictionOrder = Comparator
                .comparingInt((ExperienceEntry entry) ->
                        entry.lifecycleStatus() == StrategyLifecycleStatus.PROBATION ? 0 : 1)
                .thenComparingDouble(ExperienceEntry::confidence)
                .thenComparing(entry -> entry.lastUsedAt() == null ? Instant.MIN : entry.lastUsedAt())
                .thenComparing(ExperienceEntry::entryId);
        ExperienceEntry victim = activeStrategies.stream().min(evictionOrder)
                .orElseThrow(() -> new IllegalStateException("active strategy capacity reached for domain "
                        + domain.toSlug()));
        ExperienceEntry retired = victim.retired("active_strategy_capacity_eviction");
        ExperienceLeakageGuard.assertSafeEntry(retired);
        store.save(retired);
    }

    private boolean isActiveStrategy(ExperienceEntry entry) {
        return !entry.retired() && entry.lifecycleStatus() != StrategyLifecycleStatus.RETIRED
                && entry.disposition() != ExperienceDisposition.REJECTED
                && entry.disposition() != ExperienceDisposition.INVALID_FIXTURE;
    }

    private java.util.Optional<ExperienceEntry> findByTemplate(ExperienceDomain domain, String templateId) {
        return store.list(domain).stream()
                .filter(entry -> entry.strategyTemplate() != null)
                .filter(entry -> templateId.equals(entry.strategyTemplate().templateId()))
                .findFirst();
    }

    private List<StrategyLineage.LifecycleEvent> lifecycleHistory(ExperienceDomain domain,
                                                                  List<ExperienceEntry> entries) {
        java.util.ArrayList<StrategyLineage.LifecycleEvent> history = new java.util.ArrayList<>();
        for (ExperienceEntry current : entries) {
            if (current.strategyTemplate() == null) {
                continue;
            }
            for (int version = 1; version < current.version(); version++) {
                store.findVersion(domain, current.entryId(), version).ifPresent(archived ->
                        addLifecycleEvent(history, archived));
            }
            addLifecycleEvent(history, current);
        }
        return history.stream().sorted(java.util.Comparator
                .comparing(StrategyLineage.LifecycleEvent::occurredAt)
                .thenComparing(StrategyLineage.LifecycleEvent::templateId)
                .thenComparingInt(StrategyLineage.LifecycleEvent::experienceVersion)).toList();
    }

    private void addLifecycleEvent(List<StrategyLineage.LifecycleEvent> history, ExperienceEntry entry) {
        StrategyLifecycleStatus status = entry.lifecycleStatus();
        if (status == StrategyLifecycleStatus.ADOPTED || status == StrategyLifecycleStatus.RETIRED) {
            Instant occurredAt = status == StrategyLifecycleStatus.RETIRED ? entry.retiredAt()
                    : entry.promotedAt();
            history.add(new StrategyLineage.LifecycleEvent(entry.strategyTemplate().templateId(),
                    entry.version(), status, occurredAt == null ? entry.createdAt() : occurredAt));
        }
    }
}
