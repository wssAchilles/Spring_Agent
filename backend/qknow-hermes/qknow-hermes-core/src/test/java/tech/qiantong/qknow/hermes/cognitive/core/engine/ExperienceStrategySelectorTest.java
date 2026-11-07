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
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTemplate;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyVariant;

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
        ExperienceEntry adopted = entry("adopted", domain, ExperienceDisposition.ADOPTED, 10, 10)
                .withStrategy(template("template-adopted", 1, null), null);
        library.recordExperience(adopted);
        ExperienceEntry probation = library.createProbation(
                entry("probation", domain, ExperienceDisposition.OBSERVED, 0, 0));
        ExperienceEntry candidate = entry("candidate", domain, ExperienceDisposition.OBSERVED, 0, 0);
        StrategyTemplate variantTemplate = template("variant-one", 2, "template-adopted");
        ExperienceEntry variant = library.createVariant(entry("variant-one", domain,
                ExperienceDisposition.OBSERVED, 0, 0).withStrategy(variantTemplate,
                new StrategyVariant("variant-one", "template-adopted", "reflection mutation",
                        List.of("reflectionRules"), "Improve task completion after an observed omission.")));

        ExperienceStrategySelector.Selection selection = new ExperienceStrategySelector()
                .select(List.of(adopted, probation, variant, candidate));

        assertEquals(3, selection.scores().size());
        assertEquals("probation", selection.selected().entryId());
        assertEquals(StrategyLifecycleStatus.PROBATION, selection.scores().getFirst().lifecycleStatus());
        assertTrue(selection.scores().stream().anyMatch(score -> score.entryId().equals("variant-one")
                && score.lifecycleStatus() == StrategyLifecycleStatus.PROBATION));
        assertTrue(selection.scores().stream().anyMatch(score -> score.lifecycleStatus()
                == StrategyLifecycleStatus.ADOPTED));
    }

    private StrategyTemplate template(String id, int version, String parentId) {
        return StrategyTemplate.create(id, version, parentId,
                List.of("request has required evidence"), List.of("inspect", "act"),
                List.of("use declared tools only"), List.of("check preconditions"),
                List.of("independent verifier pass"));
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
