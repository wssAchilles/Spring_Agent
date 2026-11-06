package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.List;
import java.util.Optional;

public interface ExperienceStore {
    ExperienceEntry save(ExperienceEntry entry);

    Optional<ExperienceEntry> find(ExperienceDomain domain, String entryId);

    Optional<ExperienceEntry> findVersion(ExperienceDomain domain, String entryId, int version);

    List<ExperienceEntry> list(ExperienceDomain domain);
}
