package tech.qiantong.qknow.hermes.agent.harness.experience;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class HarnessExperienceStage10Test {
    private static final String RUN_ID = "c44e7e20-5e33-49c4-bc57-105a8cc3c347";
    private static final String RESOURCE_ROOT = "harness9-replay/" + RUN_ID + "/";

    @TempDir Path temp;

    @Test
    void frozenStage9ReplayStaysUnscopedAndPreservesUnresolvedAttribution() throws Exception {
        Path run = copyFrozenRun();
        byte[] summaryBefore = Files.readAllBytes(run.resolve("ppc_summary.json"));
        byte[] historyBefore = Files.readAllBytes(run.resolve("probes/baseline-ctx-history-04.json"));
        FileSystemExperienceStore store = new FileSystemExperienceStore(temp);
        ExperienceLibrary library = new DefaultExperienceLibrary(store);

        ExperienceRunSummaryRecord result = new Stage9ExperienceImporter(library).importDerivedFixture(run);

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
                () -> new Stage9ExperienceImporter(new DefaultExperienceLibrary(store)).importDerivedFixture(copy));
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
        Path manifest = copy.resolve("test-checksums.sha256");
        String original = Files.readString(manifest);
        String previous = original.lines().filter(line -> line.endsWith("  selected_candidate.json"))
                .findFirst().orElseThrow();
        Files.writeString(manifest, original.replace(previous, checksum + "  selected_candidate.json"));
        FileSystemExperienceStore store = new FileSystemExperienceStore(temp.resolve("library"));

        assertThrows(java.io.UncheckedIOException.class,
                () -> new Stage9ExperienceImporter(new DefaultExperienceLibrary(store)).importDerivedFixture(copy));
        assertTrue(store.list(ExperienceDomain.unscopedReplay()).isEmpty());
    }

    @Test
    void selectedCandidateMustAgreeWithSummaryEvenWhenParsedDataIsAvailable() throws Exception {
        Path copy = copyFrozenRun();
        JSONObject summary = JSON.parseObject(Files.readString(copy.resolve("ppc_summary.json")));
        JSONObject selected = JSON.parseObject(Files.readString(copy.resolve("selected_candidate.json")));
        JSONObject baseline = summary.getJSONArray("candidates").getJSONObject(0);
        selected.put("reason", "UNRELATED_REPLAY_REASON");

        assertThrows(IllegalArgumentException.class,
                () -> Stage9ExperienceImporter.verifySelection(summary, selected,
                        Map.of("baseline", baseline), true));
    }

    @Test
    void derivedFixtureHasIndependentHashChainAndRedactedText() throws Exception {
        Path copy = copyFrozenRun();
        JSONObject mapping;
        try (InputStream input = resource("provenance-map.json")) {
            mapping = JSON.parseObject(input.readAllBytes());
        }
        String manifestHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(copy.resolve("test-checksums.sha256"))));
        assertEquals("harness9-allowlist-v1", mapping.getString("rulesVersion"));
        assertEquals("7cc4f4c71d7e675eac4f99d47f9d722c34a6ffa3c159bddd808ed73951a61288",
                mapping.getString("originalManifestSha256"));
        assertEquals(manifestHash, mapping.getString("derivedManifestSha256"));
        assertEquals(17, mapping.getJSONArray("files").size());
        for (Object value : mapping.getJSONArray("files")) {
            JSONObject file = (JSONObject) value;
            assertEquals(file.getString("originalPath"), file.getString("derivedPath"));
            assertTrue(file.getString("originalSha256").matches("[0-9a-f]{64}"));
            String derivedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(copy.resolve(file.getString("derivedPath")))));
            assertEquals(derivedHash, file.getString("derivedSha256"));
        }

        JSONObject summary = JSON.parseObject(Files.readString(copy.resolve("ppc_summary.json")));
        JSONObject selected = JSON.parseObject(Files.readString(copy.resolve("selected_candidate.json")));
        assertEquals("BASELINE_RETAINED_AFTER_REJECTED_CANDIDATES", selected.getString("reason"));
        assertEquals(summary.getJSONArray("candidates").getJSONObject(0).getJSONObject("voucher"),
                selected.getJSONObject("voucher"));
        for (Object value : mapping.getJSONArray("fingerprints")) {
            JSONObject fingerprint = (JSONObject) value;
            assertTrue(fingerprint.getString("original").matches("[0-9a-f]{64}"));
            JSONObject candidate = summary.getJSONArray("candidates").stream()
                    .map(JSONObject.class::cast)
                    .filter(c -> fingerprint.getString("candidateId").equals(c.getString("candidateId")))
                    .findFirst().orElseThrow();
            assertEquals(fingerprint.getString("derived"), candidate.getString("voucherFingerprint"));
        }
        for (Object value : summary.getJSONArray("probes")) {
            JSONObject evidence = ((JSONObject) value).getJSONObject("evidence");
            assertEquals("[REDACTED USER INPUT]", evidence.getString("userInputSummary"));
            assertEquals("[REDACTED MODEL OUTPUT]", evidence.getString("finalOutputSummary"));
            assertTrue(evidence.getString("requestId").startsWith("derived-request-"));
            for (Object tool : evidence.getJSONArray("toolExecutions")) {
                assertTrue(((JSONObject) tool).getString("toolName").startsWith("derived-tool-"));
            }
        }
        for (Object value : summary.getJSONArray("candidates")) {
            JSONObject prompt = ((JSONObject) value).getJSONObject("voucher")
                    .getJSONObject("invariantSystemPrompt");
            assertEquals("Derived fixture; original prompt redacted.",
                    prompt.getString("corePrinciples"));
        }
    }

    private Path copyFrozenRun() throws Exception {
        Path copy = temp.resolve(RUN_ID);
        try (InputStream manifest = resource("test-checksums.sha256")) {
            Files.createDirectories(copy);
            Files.copy(manifest, copy.resolve("test-checksums.sha256"));
        }
        for (String line : Files.readAllLines(copy.resolve("test-checksums.sha256"))) {
            String file = line.split("  ", 2)[1];
            Path target = copy.resolve(file);
            Files.createDirectories(target.getParent());
            try (InputStream input = resource(file)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return copy;
    }

    private static InputStream resource(String file) {
        InputStream input = HarnessExperienceStage10Test.class.getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + file);
        assertNotNull(input, "derived Stage 9 replay resource is missing: " + file);
        return input;
    }
}
