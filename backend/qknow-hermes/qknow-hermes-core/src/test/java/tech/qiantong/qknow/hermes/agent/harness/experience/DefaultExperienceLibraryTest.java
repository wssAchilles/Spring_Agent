package tech.qiantong.qknow.hermes.agent.harness.experience;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultExperienceLibraryTest {
    @TempDir Path temp;

    @Test
    void probationIsRecallableOnlyByCognitiveSelectionAndRemainsDomainScoped() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(71, List.of("kb-alpha"));
        ExperienceDomain otherDomain = ExperienceDomain.cognitiveScope(72, List.of("kb-alpha"));
        ExperienceDomain otherKnowledgeBase = ExperienceDomain.cognitiveScope(71, List.of("kb-beta"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));

        ExperienceEntry probation = library.createProbation(candidate("probation-1", domain));

        assertEquals(StrategyLifecycleStatus.PROBATION, probation.lifecycleStatus());
        assertEquals(ExperienceDisposition.OBSERVED, probation.disposition());
        assertTrue(library.recallForProposer(domain, "cognitive-evolution", 10).matchedEntries().isEmpty());
        assertEquals(List.of("probation-1"), library.recallForStrategySelection(
                domain, "cognitive-evolution", 10).matchedEntries().stream().map(ExperienceEntry::entryId).toList());
        assertTrue(library.recallForStrategySelection(otherDomain, "cognitive-evolution", 10)
                .matchedEntries().isEmpty());
        assertTrue(library.recallForStrategySelection(otherKnowledgeBase, "cognitive-evolution", 10)
                .matchedEntries().isEmpty());
    }

    @Test
    void configuredProbationThresholdPromotesAfterMinimumUsesAndSuccesses() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(73, List.of("kb-promotion"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp), 3, 2);
        library.createProbation(candidate("promote-after-feedback", domain));

        ExperienceEntry first = feedback(library, domain, "promote-after-feedback", true, 1);
        ExperienceEntry second = feedback(library, domain, "promote-after-feedback", false, 2);
        ExperienceEntry promoted = feedback(library, domain, "promote-after-feedback", true, 3);

        assertEquals(StrategyLifecycleStatus.PROBATION, first.lifecycleStatus());
        assertEquals(StrategyLifecycleStatus.PROBATION, second.lifecycleStatus());
        assertEquals(3, promoted.probationUses());
        assertEquals(2, promoted.probationSuccesses());
        assertEquals(StrategyLifecycleStatus.ADOPTED, promoted.lifecycleStatus());
        assertEquals(ExperienceDisposition.ADOPTED, promoted.disposition());
        assertNotNull(promoted.promotedAt());
        assertEquals(0, promoted.consecutiveFailures());
    }

    @Test
    void threeConsecutiveProbationFailuresRetireAndRestoreOnlyToProbation() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(74, List.of("kb-restore"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        library.createProbation(candidate("restore-after-retirement", domain));

        feedback(library, domain, "restore-after-retirement", false, 1);
        feedback(library, domain, "restore-after-retirement", false, 2);
        ExperienceEntry retired = feedback(library, domain, "restore-after-retirement", false, 3);

        assertEquals(StrategyLifecycleStatus.RETIRED, retired.lifecycleStatus());
        assertEquals(3, retired.consecutiveFailures());
        assertTrue(library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .matchedEntries().isEmpty());

        ExperienceEntry restored = library.restoreToProbation(domain, retired.entryId(), 1);

        assertEquals(StrategyLifecycleStatus.PROBATION, restored.lifecycleStatus());
        assertFalse(restored.retired());
        assertEquals(0, restored.probationUses());
        assertEquals(0, restored.probationSuccesses());
        assertEquals(0, restored.consecutiveFailures());
        assertTrue(library.recallForStrategySelection(domain, "cognitive-evolution", 10)
                .hasRelevantExperience());
    }

    @Test
    void legacyExperienceJsonDefaultsLifecycleWithoutLosingAdoptedEligibility() throws Exception {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(75, List.of("kb-legacy"));
        FileSystemExperienceStore store = new FileSystemExperienceStore(temp);
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(store);
        ExperienceEntry adopted = new ExperienceEntry("legacy-entry", domain, "cognitive-evolution",
                "A reusable strategy for validating task progress before choosing actions.", "runtime evidence",
                evidence("legacy-entry"), 0.8, "legacy-run", "f".repeat(64),
                ExperienceDisposition.ADOPTED, false, ExperienceEntry.AttributionStatus.UNRESOLVED,
                1, false, null, Instant.parse("2026-10-31T00:00:00Z"));
        library.recordExperience(adopted);

        Path entryJson = temp.resolve(domain.toSlug()).resolve("entries/legacy-entry/ENTRY.json");
        JSONObject legacyJson = JSON.parseObject(Files.readString(entryJson));
        legacyJson.remove("lifecycleStatus");
        legacyJson.remove("probationUses");
        legacyJson.remove("probationSuccesses");
        legacyJson.remove("lastUsedAt");
        legacyJson.remove("promotedAt");
        legacyJson.remove("retiredAt");
        Files.writeString(entryJson, legacyJson.toJSONString());

        ExperienceEntry reloaded = store.find(domain, "legacy-entry").orElseThrow();

        assertEquals(StrategyLifecycleStatus.ADOPTED, reloaded.lifecycleStatus());
        assertEquals(0, reloaded.probationUses());
        assertTrue(library.recallForProposer(domain, "cognitive-evolution", 10).hasRelevantExperience());
    }

    @Test
    void activeStrategyPoolIsBoundedWithoutDeletingRetiredHistory() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(76, List.of("kb-capacity"));
        FileSystemExperienceStore store = new FileSystemExperienceStore(temp);
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(store);
        for (int index = 0; index < 31; index++) {
            library.createProbation(candidate("bounded-" + index, domain));
        }
        library.recordExperience(candidate("bounded-recorded", domain));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> library.recordExperience(candidate("bounded-overflow", domain)));

        assertEquals(32, store.list(domain).size());
    }

    private ExperienceEntry feedback(DefaultExperienceLibrary library, ExperienceDomain domain,
                                     String entryId, boolean success, int run) {
        return library.recordProbationFeedback(domain, entryId, success, evidence(entryId + "-" + run),
                "probation-run-" + run, Integer.toHexString(run).repeat(64));
    }

    private ExperienceEntry candidate(String id, ExperienceDomain domain) {
        return new ExperienceEntry(id, domain, "cognitive-evolution",
                "A reusable strategy for validating task progress before choosing actions.", "runtime evidence",
                evidence(id), 0.5, "candidate-run", "a".repeat(64), ExperienceDisposition.OBSERVED,
                false, ExperienceEntry.AttributionStatus.UNRESOLVED, 1, false, null, Instant.now());
    }

    private EvidenceReference evidence(String id) {
        return new EvidenceReference("evidence-" + id, "request-" + id, "b".repeat(64), "COGNITIVE");
    }
}
