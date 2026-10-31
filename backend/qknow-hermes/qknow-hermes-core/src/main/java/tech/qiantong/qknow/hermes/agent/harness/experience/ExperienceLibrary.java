package tech.qiantong.qknow.hermes.agent.harness.experience;

public interface ExperienceLibrary {
    ExperienceRecall recallForProposer(ExperienceDomain domain, String queryCue, int maxResults);

    ExperienceEntry recordExperience(ExperienceEntry entry);

    boolean retire(ExperienceDomain domain, String entryId, String retirementReason);
}
