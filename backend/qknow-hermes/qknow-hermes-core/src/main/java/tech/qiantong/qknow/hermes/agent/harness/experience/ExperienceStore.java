package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.List;
import java.util.Optional;

public interface ExperienceStore {
    ExperienceEntry save(ExperienceEntry entry);

    Optional<ExperienceEntry> find(ExperienceDomain domain, String entryId);

    List<ExperienceEntry> list(ExperienceDomain domain);
}
