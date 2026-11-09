package tech.qiantong.qknow.hermes.agent.harness.experience;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyTemplateLineageTest {
    @TempDir Path temp;

    @Test
    void legacyTemplateWithoutTaskTypeLoadsAsUnclassified() throws Exception {
        List<String> preconditions = List.of("request has required evidence");
        List<String> plan = List.of("inspect", "act");
        List<String> tools = List.of("use declared tools only");
        List<String> reflection = List.of("check preconditions");
        List<String> success = List.of("independent verifier pass");
        String legacyCanonical = "1||" + canonicalList(preconditions) + "|" + canonicalList(plan)
                + "|" + canonicalList(tools) + "|" + canonicalList(reflection) + "|" + canonicalList(success);
        String legacyFingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(legacyCanonical.getBytes(StandardCharsets.UTF_8)));
        JSONObject legacyJson = new JSONObject();
        legacyJson.put("templateId", "legacy-template");
        legacyJson.put("version", 1);
        legacyJson.put("preconditions", preconditions);
        legacyJson.put("planSkeleton", plan);
        legacyJson.put("toolPolicy", tools);
        legacyJson.put("reflectionRules", reflection);
        legacyJson.put("successCriteria", success);
        legacyJson.put("fingerprint", legacyFingerprint);

        StrategyTemplate restored = JSON.parseObject(legacyJson.toJSONString(), StrategyTemplate.class);

        assertEquals(StrategyTaskType.UNCLASSIFIED, restored.taskType());
        assertEquals(legacyFingerprint, restored.fingerprint());
    }

    @Test
    void templateFingerprintAndVersionedParentLineageAreDeterministic() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(111, List.of("kb-lineage"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        ExperienceEntry root = root(domain);
        library.recordExperience(root);
        ExperienceEntry child = variant(domain, root, "child-one", "Add a missing prerequisite check.");
        ExperienceEntry saved = library.createVariant(child);

        StrategyLineage lineage = library.lineage(domain, saved.strategyTemplate().templateId()).orElseThrow();
        StrategyTemplate sameTemplate = StrategyTemplate.create("tpl-root", 1, null,
                List.of("request has required evidence"), List.of("inspect", "act"),
                List.of("use declared tools only"), List.of("check preconditions", "compare output"),
                List.of("independent verifier pass"));
        StrategyTemplate changedTemplate = StrategyTemplate.create("tpl-root", 2, null,
                List.of("request has required evidence"), List.of("inspect", "act"),
                List.of("use declared tools only"), List.of("check preconditions", "compare output"),
                List.of("independent verifier pass", "no missing required field"));

        assertEquals(root.strategyTemplate().fingerprint(), sameTemplate.fingerprint());
        assertNotEquals(sameTemplate.fingerprint(), changedTemplate.fingerprint());
        assertEquals(1, root.strategyTemplate().version());
        assertEquals(2, saved.strategyTemplate().version());
        assertEquals("tpl-root", saved.strategyTemplate().parentTemplateId());
        assertEquals("tpl-root", lineage.rootTemplateId());
        assertEquals("tpl-root", lineage.parentTemplateId());
        assertTrue(lineage.children().isEmpty());
        assertEquals(List.of(saved.strategyTemplate().templateId()),
                library.lineage(domain, "tpl-root").orElseThrow().children());
    }

    @Test
    void variantLimitIsTwoAndOnlyWithinParentDomain() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(112, List.of("kb-parent"));
        ExperienceDomain otherDomain = ExperienceDomain.cognitiveScope(112, List.of("kb-other"));
        ExperienceDomain otherBot = ExperienceDomain.cognitiveScope(999, List.of("kb-parent"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        ExperienceEntry parent = root(domain);
        library.recordExperience(parent);
        library.createVariant(variant(domain, parent, "child-one", "Add a prerequisite check."));
        library.createVariant(variant(domain, parent, "child-two", "Summarize output before completing."));

        assertThrows(IllegalStateException.class,
                () -> library.createVariant(variant(domain, parent, "child-three", "Validate one more condition.")));
        assertThrows(IllegalArgumentException.class,
                () -> library.createVariant(variant(otherDomain, parent, "cross-domain-child",
                        "Do not cross the domain boundary.")));
        assertThrows(IllegalArgumentException.class,
                () -> library.createVariant(variant(otherBot, parent, "cross-bot-child",
                        "Do not cross the bot boundary.")));
        ExperienceEntry wrongVersion = variant(domain, parent, "wrong-version", "Reject a skipped version.");
        StrategyTemplate wrongTemplate = wrongVersion.strategyTemplate();
        ExperienceEntry skippedVersion = wrongVersion.withStrategy(StrategyTemplate.create(
                wrongTemplate.templateId(), 3, wrongTemplate.parentTemplateId(), wrongTemplate.preconditions(),
                wrongTemplate.planSkeleton(), wrongTemplate.toolPolicy(), wrongTemplate.reflectionRules(),
                wrongTemplate.successCriteria()), wrongVersion.strategyVariant());
        assertThrows(IllegalArgumentException.class, () -> library.createVariant(skippedVersion));
        assertEquals(2, library.lineage(domain, "tpl-root").orElseThrow().children().size());
        assertTrue(library.lineage(otherDomain, "tpl-root").isEmpty());
        assertTrue(library.lineage(otherBot, "tpl-root").isEmpty());
    }

    @Test
    void variantFeedbackCanPromoteOrRetireAndRollbackReturnsParent() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(113, List.of("kb-feedback"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp), 3, 2);
        ExperienceEntry parent = root(domain);
        library.recordExperience(parent);
        ExperienceEntry child = library.createVariant(variant(domain, parent,
                "feedback-child", "Check task prerequisites before acting."));

        library.recordProbationFeedback(domain, child.entryId(), true, evidence("success-1"), "run-1", "c".repeat(64));
        library.recordProbationFeedback(domain, child.entryId(), false, evidence("failure-1"), "run-2", "d".repeat(64));
        ExperienceEntry promoted = library.recordProbationFeedback(domain, child.entryId(), true,
                evidence("success-2"), "run-3", "e".repeat(64));
        assertEquals(StrategyLifecycleStatus.ADOPTED, promoted.lifecycleStatus());
          assertEquals(2, promoted.strategyTemplate().version());
          assertEquals(child.strategyTemplate().fingerprint(), promoted.strategyTemplate().fingerprint());
        assertTrue(library.lineage(domain, child.strategyTemplate().templateId()).orElseThrow()
                .adoptedHistory().stream().anyMatch(event -> event.experienceVersion() == promoted.version()));

        assertTrue(library.retire(domain, child.entryId(), "three consecutive variant failures"));
        assertEquals(StrategyLifecycleStatus.RETIRED,
                library.find(domain, child.entryId()).orElseThrow().lifecycleStatus());
        ExperienceEntry restoredParent = library.rollbackToParent(domain, child.entryId()).orElseThrow();
        assertEquals(parent.entryId(), restoredParent.entryId());
        StrategyLineage retiredLineage = library.lineage(domain, child.strategyTemplate().templateId()).orElseThrow();
        assertEquals(parent.strategyTemplate().templateId(), retiredLineage.rollbackPointer());
        assertTrue(retiredLineage.retiredHistory().stream()
                .anyMatch(event -> event.status() == StrategyLifecycleStatus.RETIRED));
    }

    @Test
    void variantIsPersistedAsProbationAndCanBeRestoredAfterSoftRetirement() {
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(114, List.of("kb-restore"));
        DefaultExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        ExperienceEntry parent = root(domain);
        library.recordExperience(parent);
        ExperienceEntry child = library.createVariant(variant(domain, parent,
                "restore-child", "Keep one validation step before completion."));
        assertEquals(StrategyLifecycleStatus.PROBATION, child.lifecycleStatus());

        library.recordProbationFeedback(domain, child.entryId(), false, evidence("fail-1"), "restore-1", "1".repeat(64));
        library.recordProbationFeedback(domain, child.entryId(), false, evidence("fail-2"), "restore-2", "2".repeat(64));
        ExperienceEntry retired = library.recordProbationFeedback(domain, child.entryId(), false,
                evidence("fail-3"), "restore-3", "3".repeat(64));
        assertEquals(StrategyLifecycleStatus.RETIRED, retired.lifecycleStatus());

        ExperienceEntry restored = library.restoreToProbation(domain, child.entryId(), 1);
        assertEquals(StrategyLifecycleStatus.PROBATION, restored.lifecycleStatus());
        assertEquals(child.strategyTemplate().fingerprint(), restored.strategyTemplate().fingerprint());
        assertNotNull(restored.strategyVariant());
        assertFalse(restored.retired());
    }

    private ExperienceEntry root(ExperienceDomain domain) {
        StrategyTemplate template = StrategyTemplate.create("tpl-root", 1, null,
                List.of("request has required evidence"), List.of("inspect", "act"),
                List.of("use declared tools only"), List.of("check preconditions", "compare output"),
                List.of("independent verifier pass"));
        return new ExperienceEntry("tpl-root", domain, "cognitive-evolution",
                "A reusable strategy for validating task progress before choosing actions.", "root template",
                evidence("root"), 0.9, "root-run", "a".repeat(64), ExperienceDisposition.ADOPTED,
                false, ExperienceEntry.AttributionStatus.UNRESOLVED, 1, false, null, Instant.now())
                .withStrategy(template, null);
    }

    private String canonicalList(List<String> values) {
        return values.stream().map(value -> value.length() + ":" + value)
                .collect(java.util.stream.Collectors.joining());
    }

    private ExperienceEntry variant(ExperienceDomain domain, ExperienceEntry parent,
                                    String idSuffix, String mutation) {
        String variantId = "var-" + idSuffix;
        StrategyTemplate parentTemplate = parent.strategyTemplate();
        StrategyTemplate template = StrategyTemplate.create(variantId, parentTemplate.version() + 1,
                parentTemplate.templateId(), parentTemplate.preconditions(), parentTemplate.planSkeleton(),
                parentTemplate.toolPolicy(), List.of(mutation), parentTemplate.successCriteria());
        StrategyVariant variant = new StrategyVariant(variantId, parentTemplate.templateId(), mutation,
                List.of("reflectionRules"), "Address the observed failure with a task-specific strategy variant.");
        return new ExperienceEntry(variantId, domain, "cognitive-evolution", mutation,
                "Reflection-derived strategy variant awaiting execution feedback.", evidence(variantId),
                0.5, "variant-run-" + idSuffix, "b".repeat(64), ExperienceDisposition.OBSERVED,
                false, ExperienceEntry.AttributionStatus.UNRESOLVED, 1, false, null, Instant.now())
                .withStrategy(template, variant);
    }

    private EvidenceReference evidence(String id) {
        return new EvidenceReference("evidence-" + id, "request-" + id, "f".repeat(64), "COGNITIVE_TEMPLATE");
    }
}
