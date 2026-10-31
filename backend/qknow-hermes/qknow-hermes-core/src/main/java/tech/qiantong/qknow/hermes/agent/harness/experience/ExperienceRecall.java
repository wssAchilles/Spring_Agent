package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.List;
import java.util.Objects;

public record ExperienceRecall(
        ExperienceDomain domain,
        String queryCue,
        List<ExperienceEntry> matchedEntries,
        int totalAvailableInDomain,
        int filteredRetiredCount
) {
    public ExperienceRecall {
        Objects.requireNonNull(domain, "domain");
        matchedEntries = List.copyOf(matchedEntries);
    }

    public boolean hasRelevantExperience() {
        return !matchedEntries.isEmpty();
    }
}
