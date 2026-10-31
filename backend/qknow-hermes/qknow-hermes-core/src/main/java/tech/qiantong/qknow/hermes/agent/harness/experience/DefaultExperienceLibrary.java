package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DefaultExperienceLibrary implements ExperienceLibrary {
    private static final double RETIRE_THRESHOLD = 0.20;
    private final ExperienceStore store;

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
    public ExperienceEntry recordExperience(ExperienceEntry entry) {
        Objects.requireNonNull(entry, "entry");
        ExperienceLeakageGuard.assertSafeEntry(entry);
        return store.save(entry);
    }

    @Override
    public boolean retire(ExperienceDomain domain, String entryId, String retirementReason) {
        Objects.requireNonNull(domain, "domain");
        if (retirementReason == null || retirementReason.isBlank()) {
            throw new IllegalArgumentException("retirementReason is required");
        }
        ExperienceEntry current = store.find(domain, entryId).orElse(null);
        if (current == null || current.retired()) {
            return false;
        }
        ExperienceEntry updated = current.retired(retirementReason);
        ExperienceLeakageGuard.assertSafeEntry(updated);
        store.save(updated);
        return true;
    }
}
