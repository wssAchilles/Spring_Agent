package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceLibraryContractTest {
    @TempDir Path temp;

    @Test
    void domainsHaveDistinctSafePhysicalSubtrees() throws Exception {
        ExperienceDomain finance = new ExperienceDomain(101, 202, "Finance / Risk");
        ExperienceDomain support = new ExperienceDomain(101, 202, "Support");
        ExperienceDomain replay = ExperienceDomain.unscopedReplay();
        assertEquals("bot_101__kb_202__cat_finance_risk", finance.toSlug());
        assertEquals("unscoped_replay", replay.toSlug());
        assertThrows(IllegalArgumentException.class, () -> new ExperienceDomain(0, 202, "Finance"));
        assertThrows(IllegalArgumentException.class, () -> new ExperienceDomain(101, 0, "Finance"));
        assertThrows(IllegalArgumentException.class, () -> new ExperienceDomain(0, 0, "Finance"));

        FileSystemExperienceStore store = new FileSystemExperienceStore(temp);
        ExperienceLibrary library = new DefaultExperienceLibrary(store);
        library.recordExperience(entry("shared", finance, ExperienceDisposition.ADOPTED, false));
        library.recordExperience(entry("other", support, ExperienceDisposition.ADOPTED, false));
        library.recordExperience(entry("replay", replay, ExperienceDisposition.ADOPTED, false));

        assertTrue(Files.isRegularFile(temp.resolve(finance.toSlug()).resolve("entries/shared/ENTRY.json")));
        assertTrue(Files.isRegularFile(temp.resolve(support.toSlug()).resolve("entries/other/ENTRY.json")));
        assertTrue(Files.isRegularFile(temp.resolve("unscoped_replay/entries/replay/ENTRY.json")));
        ExperienceDomain colliding = new ExperienceDomain(101, 202, "Finance---Risk");
        assertEquals(finance.toSlug(), colliding.toSlug());
        assertThrows(java.io.UncheckedIOException.class,
                () -> library.recordExperience(entry("collision", colliding, ExperienceDisposition.ADOPTED, false)));
        assertFalse(Files.exists(temp.resolve(finance.toSlug()).resolve("entries/collision")));
        assertEquals("shared", library.recallForProposer(finance, "abstract reusable cue", 10)
                .matchedEntries().getFirst().entryId());
        assertEquals("other", library.recallForProposer(support, "abstract reusable cue", 10)
                .matchedEntries().getFirst().entryId());
        assertFalse(library.recallForProposer(replay, "abstract reusable cue", 10).hasRelevantExperience());
        assertEquals(1, store.list(replay).size());
    }

    @Test
    void leakageIsRejectedBeforeAnyFileIsWritten() {
        ExperienceDomain domain = new ExperienceDomain(101, 202, "finance");
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        ExperienceEntry safe = entry("safe", domain, ExperienceDisposition.OBSERVED, false);

        ExperienceEntry grader = new ExperienceEntry("grader", domain, "qualityPass is true", safe.lesson(),
                safe.evidenceSummary(), safe.evidenceRef(), safe.confidence(), safe.sourceRunId(),
                safe.harnessFingerprint(), safe.disposition(), safe.failureObserved(), safe.attributionStatus(),
                safe.version(), safe.retired(), safe.retirementReason(), safe.createdAt());
        ExperienceEntry qrel = new ExperienceEntry("qrel", domain, safe.cue(),
                "A specific TX-20261029-8891 transaction passed", safe.evidenceSummary(), safe.evidenceRef(),
                safe.confidence(), safe.sourceRunId(), safe.harnessFingerprint(), safe.disposition(),
                safe.failureObserved(), safe.attributionStatus(), safe.version(), safe.retired(),
                safe.retirementReason(), safe.createdAt());

        assertThrows(ExperienceLeakageException.class, () -> library.recordExperience(grader));
        assertThrows(ExperienceLeakageException.class, () -> library.recordExperience(qrel));
        assertFalse(Files.exists(temp.resolve(domain.toSlug()).resolve("entries/grader")));
        assertFalse(Files.exists(temp.resolve(domain.toSlug()).resolve("entries/qrel")));
    }

    @Test
    void metadataLeakageAndSymlinkRootAreRejected() throws Exception {
        ExperienceDomain domain = new ExperienceDomain(101, 202, "finance");
        ExperienceLibrary library = new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
        ExperienceEntry safe = entry("safe", domain, ExperienceDisposition.OBSERVED, false);
        ExperienceEntry metadataLeak = new ExperienceEntry("qualityPass", domain, safe.cue(), safe.lesson(),
                safe.evidenceSummary(), safe.evidenceRef(), safe.confidence(), safe.sourceRunId(),
                safe.harnessFingerprint(), safe.disposition(), safe.failureObserved(), safe.attributionStatus(),
                safe.version(), safe.retired(), safe.retirementReason(), safe.createdAt());
        assertThrows(ExperienceLeakageException.class, () -> library.recordExperience(metadataLeak));
        assertFalse(Files.exists(temp.resolve(domain.toSlug())));

        Path link = temp.resolve("linked-root");
        Path target = temp.resolve("actual-root");
        Files.createDirectory(target);
        Files.createSymbolicLink(link, target);
        ExperienceLibrary linked = new DefaultExperienceLibrary(new FileSystemExperienceStore(link));
        assertThrows(java.io.UncheckedIOException.class, () -> linked.recordExperience(safe));
        assertFalse(Files.exists(target.resolve(domain.toSlug())));
    }

    @Test
    void unresolvedFailuresAndRetiredEntriesNeverBecomePositiveSuggestions() throws Exception {
        ExperienceDomain domain = new ExperienceDomain(101, 202, "finance");
        FileSystemExperienceStore store = new FileSystemExperienceStore(temp);
        ExperienceLibrary library = new DefaultExperienceLibrary(store);
        library.recordExperience(entry("positive", domain, ExperienceDisposition.ADOPTED, false));
        library.recordExperience(entry("unresolved", domain, ExperienceDisposition.OBSERVED, true));
        library.recordExperience(entry("rejected", domain, ExperienceDisposition.REJECTED, false));
        assertEquals(1, library.recallForProposer(domain, "abstract reusable cue", 10).matchedEntries().size());
        assertTrue(store.find(domain, "unresolved").orElseThrow().failureObserved());

        Path directory = temp.resolve(domain.toSlug()).resolve("entries/positive");
        String oldJson = Files.readString(directory.resolve("ENTRY.json"));
        String oldMarkdown = Files.readString(directory.resolve("EXPERIENCE.md"));
        assertTrue(library.retire(domain, "positive", "superseded by later evidence"));

        ExperienceEntry retired = store.find(domain, "positive").orElseThrow();
        assertTrue(retired.retired());
        assertEquals(2, retired.version());
        assertEquals("superseded by later evidence", retired.retirementReason());
        assertTrue(Files.readString(directory.resolve("EXPERIENCE.md")).contains("> [!WARNING] RETIRED"));
        assertEquals(oldJson, Files.readString(directory.resolve("versions/1/ENTRY.json")));
        assertEquals(oldMarkdown, Files.readString(directory.resolve("versions/1/EXPERIENCE.md")));
        assertTrue(Files.readString(temp.resolve(domain.toSlug()).resolve("INDEX.md"))
                .contains("## Retired Entries"));
        assertFalse(library.recallForProposer(domain, "abstract reusable cue", 10).hasRelevantExperience());
        assertTrue(Files.isRegularFile(directory.resolve("ENTRY.json")));
    }

    private static ExperienceEntry entry(String id, ExperienceDomain domain,
                                         ExperienceDisposition disposition, boolean failureObserved) {
        return new ExperienceEntry(id, domain, "abstract reusable cue", "An abstract reusable lesson for later review",
                "Evidence is limited to an offline observation", new EvidenceReference("evidence-1", "request-1",
                "a".repeat(64), "OFFLINE"), 0.8, "source-run", "b".repeat(64), disposition,
                failureObserved, failureObserved ? ExperienceEntry.AttributionStatus.UNRESOLVED
                        : ExperienceEntry.AttributionStatus.NOT_APPLICABLE,
                1, false, null, Instant.parse("2026-10-31T00:00:00Z"));
    }
}
