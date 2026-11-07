package tech.qiantong.qknow.hermes.cognitive.core.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.qiantong.qknow.hermes.agent.harness.experience.DefaultExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.EvidenceReference;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry.AttributionStatus;
import tech.qiantong.qknow.hermes.agent.harness.experience.FileSystemExperienceStore;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceStrategySelectorTest {
    @TempDir Path temp;

    @Test
    void ucbIncludesProbationAlongsideAdoptedAndExcludesCandidates() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(81, List.of("kb-selector"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        ExperienceEntry adopted = entry("adopted", domain, ExperienceDisposition.ADOPTED, 10, 10);
        ExperienceEntry probation = library.createProbation(
                entry("probation", domain, ExperienceDisposition.OBSERVED, 0, 0));
        ExperienceEntry candidate = entry("candidate", domain, ExperienceDisposition.OBSERVED, 0, 0);

        ExperienceStrategySelector.Selection selection = new ExperienceStrategySelector()
                .select(List.of(adopted, probation, candidate));

        assertEquals(2, selection.scores().size());
        assertEquals("probation", selection.selected().entryId());
        assertEquals(StrategyLifecycleStatus.PROBATION, selection.scores().getFirst().lifecycleStatus());
        assertTrue(selection.scores().stream().anyMatch(score -> score.lifecycleStatus()
                == StrategyLifecycleStatus.ADOPTED));
    }

    private ExperienceEntry entry(String id, ExperienceDomain domain, ExperienceDisposition disposition,
                                  int attempts, int successes) {
        return new ExperienceEntry(id, domain, "cognitive-evolution",
                "A reusable strategy for validating task progress before choosing actions.", "runtime evidence",
                new EvidenceReference("evidence-" + id, "request-" + id, "c".repeat(64), "COGNITIVE"),
                disposition == ExperienceDisposition.ADOPTED ? 0.9 : 0.5, "run-" + id, "d".repeat(64),
                disposition, false, AttributionStatus.UNRESOLVED, 1, false, null,
                Instant.parse("2026-11-01T00:00:00Z"), attempts, successes, 0);
    }
}
