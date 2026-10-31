package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class HarnessExperienceStage10Test {
    private static final String RUN_ID = "c44e7e20-5e33-49c4-bc57-105a8cc3c347";

    @TempDir Path temp;

    @Test
    void frozenStage9ReplayStaysUnscopedAndPreservesUnresolvedAttribution() throws Exception {
        Path run = frozenRun();
        byte[] summaryBefore = Files.readAllBytes(run.resolve("ppc_summary.json"));
        byte[] historyBefore = Files.readAllBytes(run.resolve("probes/baseline-ctx-history-04.json"));
        FileSystemExperienceStore store = new FileSystemExperienceStore(temp);
        ExperienceLibrary library = new DefaultExperienceLibrary(store);

        ExperienceRunSummaryRecord result = new Stage9ExperienceImporter(library).importRun(run);

        assertEquals(RUN_ID, result.runId());
        assertEquals(15, result.totalProbesExecuted());
        assertEquals(26, result.totalModelCallsExecuted());
        assertEquals(35_500L, result.totalTokensConsumed());
        assertEquals(0.06718974, result.totalCostCnyConsumed(), 0.000000001);
        assertEquals(5, result.uniqueCasesEvaluated());
        assertEquals(3, result.qualityPassedCases());
        assertEquals(2, result.unresolvedAttributionCases());
        assertEquals(3, result.evaluatedCandidates());
        assertEquals(2, result.rejectedCandidates());
        assertEquals(0, result.adoptedCandidates());
        assertEquals("REJECTED_AFTER_REAL_PPC", result.finalVerdict());

        ExperienceDomain replay = ExperienceDomain.unscopedReplay();
        List<ExperienceEntry> entries = store.list(replay);
        assertEquals(7, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.domain().equals(replay)));
        assertEquals(5, entries.stream().filter(e -> e.disposition() == ExperienceDisposition.OBSERVED).count());
        assertEquals(2, entries.stream().filter(e -> e.disposition() == ExperienceDisposition.REJECTED).count());
        assertEquals(0, entries.stream().filter(e -> e.disposition() == ExperienceDisposition.ADOPTED).count());
        assertEquals(0, entries.stream().filter(e -> e.disposition() == ExperienceDisposition.INVALID_FIXTURE).count());
        assertEquals(2, entries.stream().filter(ExperienceEntry::failureObserved).count());
        assertTrue(entries.stream().filter(ExperienceEntry::failureObserved)
                .allMatch(e -> e.attributionStatus() == ExperienceEntry.AttributionStatus.UNRESOLVED
                        && e.disposition() == ExperienceDisposition.OBSERVED));
        assertTrue(entries.stream().allMatch(e -> RUN_ID.equals(e.sourceRunId())));
        assertTrue(entries.stream().allMatch(e -> e.evidenceSummary().contains("botId=9")));
        assertTrue(entries.stream().noneMatch(e -> e.isEligibleForPositiveProposer(0.20)));
        assertFalse(library.recallForProposer(replay, "abstract", 20).hasRelevantExperience());
        assertTrue(store.list(new ExperienceDomain(9, 1, "HISTORY")).isEmpty());
        assertArrayEquals(summaryBefore, Files.readAllBytes(run.resolve("ppc_summary.json")));
        assertArrayEquals(historyBefore, Files.readAllBytes(run.resolve("probes/baseline-ctx-history-04.json")));
    }

    @Test
    void tamperedReplayIsRejectedBeforeAnyExperienceIsWritten() throws Exception {
        Path copy = copyFrozenRun();
        Files.writeString(copy.resolve("probes/baseline-ctx-history-04.json"), "corrupted replay");
        Path libraryRoot = temp.resolve("library");
        FileSystemExperienceStore store = new FileSystemExperienceStore(libraryRoot);

        assertThrows(java.io.UncheckedIOException.class,
                () -> new Stage9ExperienceImporter(new DefaultExperienceLibrary(store)).importRun(copy));
        assertTrue(store.list(ExperienceDomain.unscopedReplay()).isEmpty());
        assertFalse(Files.exists(libraryRoot.resolve("unscoped_replay")));
    }

    @Test
    void replayWithRecomputedManifestIsStillRejected() throws Exception {
        Path copy = copyFrozenRun();
        Path selected = copy.resolve("selected_candidate.json");
        Files.writeString(selected, "{}");
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(selected)));
        Path manifest = copy.resolve("checksums.sha256");
        String original = Files.readString(manifest);
        String previous = original.lines().filter(line -> line.endsWith("  selected_candidate.json"))
                .findFirst().orElseThrow();
        Files.writeString(manifest, original.replace(previous, checksum + "  selected_candidate.json"));
        FileSystemExperienceStore store = new FileSystemExperienceStore(temp.resolve("library"));

        assertThrows(java.io.UncheckedIOException.class,
                () -> new Stage9ExperienceImporter(new DefaultExperienceLibrary(store)).importRun(copy));
        assertTrue(store.list(ExperienceDomain.unscopedReplay()).isEmpty());
    }

    private Path copyFrozenRun() throws Exception {
        Path source = frozenRun();
        Path copy = temp.resolve(RUN_ID);
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = copy.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return copy;
    }

    private static Path frozenRun() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("AGENTS.md"))) {
            root = root.getParent();
        }
        assertNotNull(root, "repository root not found");
        Path run = root.resolve("experiment/outputs/harness9-live").resolve(RUN_ID);
        assertTrue(Files.isRegularFile(run.resolve("checksums.sha256")), "Stage 9 frozen replay is missing");
        return run;
    }
}
