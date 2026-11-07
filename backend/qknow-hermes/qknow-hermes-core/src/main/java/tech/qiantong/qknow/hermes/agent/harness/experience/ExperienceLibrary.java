package tech.qiantong.qknow.hermes.agent.harness.experience;

public interface ExperienceLibrary {
    ExperienceRecall recallForProposer(ExperienceDomain domain, String queryCue, int maxResults);

    default ExperienceRecall recallForStrategySelection(ExperienceDomain domain, String queryCue, int maxResults) {
        return recallForProposer(domain, queryCue, maxResults);
    }

    java.util.Optional<ExperienceEntry> find(ExperienceDomain domain, String entryId);

    ExperienceEntry recordExperience(ExperienceEntry entry);

    ExperienceEntry adoptRuntimeStrategy(ExperienceEntry candidate);

    ExperienceEntry createProbation(ExperienceEntry candidate);

    ExperienceEntry recordProbationFeedback(ExperienceDomain domain, String entryId, boolean success,
                                            EvidenceReference evidence, String runId,
                                            String harnessFingerprint);

    ExperienceEntry promote(ExperienceDomain domain, String entryId);

    ExperienceEntry recordFeedback(ExperienceDomain domain, String entryId, boolean success,
                                   EvidenceReference evidence, String runId, String harnessFingerprint);

    ExperienceEntry restore(ExperienceDomain domain, String entryId, int version);

    ExperienceEntry restoreToProbation(ExperienceDomain domain, String entryId, int version);

    boolean retire(ExperienceDomain domain, String entryId, String retirementReason);
}
