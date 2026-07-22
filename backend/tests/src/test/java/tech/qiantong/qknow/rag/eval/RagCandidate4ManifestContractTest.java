package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagCandidate4ManifestContractTest {

    @TempDir
    Path temp;

    @Test
    void manifestRequiresFrozenGeneratorVersionSeedAndCanonicalDatasetHash() throws Exception {
        assertManifestRejected("generator", json ->
                json.put("generator", "candidate4-static-fixture-v2"));
        assertManifestRejected("version", json -> json.put("version", 2));
        assertManifestRejected("seed", json -> json.put("seed", 20260717L));
        assertManifestRejected("dataset-hash", json ->
                json.put("datasetHash", "0".repeat(64)));
    }

    @Test
    void manifestRequiresFixedLogicalResourceNamesAndLowercaseSha256() throws Exception {
        assertManifestRejected("absolute-resource", json -> json.getJSONObject("resources")
                .getJSONObject("corpus").put("file", "/tmp/corpus.jsonl"));
        assertManifestRejected("traversal-resource", json -> json.getJSONObject("resources")
                .getJSONObject("queries").put("file", "../queries.jsonl"));
        assertManifestRejected("wrong-resource", json -> json.getJSONObject("resources")
                .getJSONObject("baseDistractor").put("file", "base-corpus.jsonl"));
        assertManifestRejected("short-resource-hash", json -> json.getJSONObject("resources")
                .getJSONObject("qrels").put("sha256", "abc"));
        assertManifestRejected("uppercase-resource-hash", json -> json.getJSONObject("resources")
                .getJSONObject("qrels").put("sha256", "A".repeat(64)));
        assertManifestRejected("uppercase-dataset-hash", json ->
                json.put("datasetHash", "A".repeat(64)));
    }

    @Test
    void completionRehashesBothCurrentManifestsBeforeWritingArtifact() throws Exception {
        RunFixture fixture = newRun("manifest-drift-before-completion");
        rewriteManifestWithValidCountChange(fixture.paths().selectionManifest());

        assertThrows(IllegalStateException.class, () ->
                RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(
                        fixture.paths(), fixture.handle(), validArtifact(fixture.manifests())));
    }

    @Test
    void completedRunRevalidatesManifestAndArtifactContracts() throws Exception {
        RunFixture fixture = newRun("completed-run-revalidation");
        RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(
                fixture.paths(), fixture.handle(), validArtifact(fixture.manifests()));
        rewriteManifestWithValidCountChange(fixture.paths().holdoutManifest());

        assertThrows(IllegalStateException.class,
                () -> RagCandidate4DiagnosticSupport.verifyCompletedRun(fixture.paths()));

        RunFixture artifactFixture = newRun("completed-artifact-revalidation");
        RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(
                artifactFixture.paths(), artifactFixture.handle(),
                validArtifact(artifactFixture.manifests()));
        JSONObject diagnostic = JSON.parseObject(Files.readString(
                artifactFixture.paths().diagnostic(), StandardCharsets.UTF_8));
        diagnostic.getJSONObject("config").put("topK", 11);
        Files.writeString(artifactFixture.paths().diagnostic(), JSON.toJSONString(diagnostic),
                StandardCharsets.UTF_8);
        JSONObject ledger = JSON.parseObject(Files.readString(
                artifactFixture.paths().ledger(), StandardCharsets.UTF_8));
        ledger.put("artifactSha256",
                ShadowContractSupport.sha256(artifactFixture.paths().diagnostic()));
        Files.writeString(artifactFixture.paths().ledger(), JSON.toJSONString(ledger),
                StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class,
                () -> RagCandidate4DiagnosticSupport.verifyCompletedRun(artifactFixture.paths()));
    }

    @Test
    void diagnosticRequiresSelfConsistentHashesStatusPayloadAndDecision() throws Exception {
        assertArtifactRejected("config-hash", artifact ->
                artifact.put("configHash", "0".repeat(64)));
        assertArtifactRejected("dataset-hash", artifact ->
                artifact.put("datasetHash", "0".repeat(64)));
        assertArtifactRejected("selection-manifest-hash", artifact ->
                artifact.put("selectionManifestHash", "0".repeat(64)));
        assertArtifactRejected("holdout-manifest-hash", artifact ->
                artifact.put("holdoutManifestHash", "0".repeat(64)));
        assertArtifactRejected("holdout-status", artifact ->
                artifact.put("holdoutFreezeStatus", "FROZEN"));
        assertArtifactRejected("unknown-decision", artifact ->
                artifact.put("decision", "PROCEED_TO_PRODUCTION"));
        assertArtifactRejected("valid-with-error", artifact ->
                artifact.put("errorCode", "UNEXPECTED"));
        assertArtifactRejected("valid-without-summary", artifact -> artifact.remove("summary"));
        assertArtifactRejected("valid-without-cases", artifact -> artifact.remove("cases"));
        assertArtifactRejected("invalid-with-payload", artifact -> {
            artifact.put("status", "INVALID");
            artifact.put("errorCode", "FIXTURE_INVALID");
        });
        assertArtifactRejected("invalid-without-error", artifact -> {
            artifact.put("status", "INVALID");
            artifact.remove("summary");
            artifact.remove("cases");
        });

        RunFixture invalidFixture = newRun("valid-invalid-artifact");
        Map<String, Object> invalid = validArtifact(invalidFixture.manifests());
        invalid.put("status", "INVALID");
        invalid.put("errorCode", "FIXTURE_INVALID");
        invalid.put("exceptionClass", "IllegalStateException");
        invalid.remove("summary");
        invalid.remove("cases");
        assertDoesNotThrow(() -> RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(
                invalidFixture.paths(), invalidFixture.handle(), invalid));
    }

    @Test
    void decisionStopsWhenImprovementDoesNotRestoreExactIdentifierEvidence() {
        List<RagCandidate4DiagnosticSupport.CaseEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            evidence.add(targetEvidenceWithoutExactRecovery("target-" + index));
        }
        for (int index = 0; index < 4; index++) {
            evidence.add(controlEvidence("control-" + index));
        }

        assertEquals("STOP_IDENTIFIER_ANCHOR_UNSUPPORTED",
                RagCandidate4DiagnosticSupport.decide(evidence));
    }

    private void assertManifestRejected(String name, Consumer<JSONObject> mutation)
            throws Exception {
        RagCandidate4DiagnosticSupport.RuntimePaths paths = paths(name);
        JSONObject selection = JSON.parseObject(Files.readString(
                paths.selectionManifest(), StandardCharsets.UTF_8));
        mutation.accept(selection);
        Files.writeString(paths.selectionManifest(), JSON.toJSONString(selection),
                StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class,
                () -> RagCandidate4DiagnosticSupport.loadFrozenManifests(paths), name);
    }

    private void assertArtifactRejected(String name, Consumer<Map<String, Object>> mutation)
            throws Exception {
        RunFixture fixture = newRun("artifact-" + name);
        Map<String, Object> artifact = validArtifact(fixture.manifests());
        mutation.accept(artifact);
        assertThrows(IllegalStateException.class, () ->
                RagCandidate4DiagnosticSupport.writeDiagnosticAndComplete(
                        fixture.paths(), fixture.handle(), artifact), name);
    }

    private RunFixture newRun(String name) throws Exception {
        RagCandidate4DiagnosticSupport.RuntimePaths paths = paths(name);
        RagCandidate4DiagnosticSupport.FrozenManifests manifests =
                RagCandidate4DiagnosticSupport.loadFrozenManifests(paths);
        RagCandidate4DiagnosticSupport.RunHandle handle =
                RagCandidate4DiagnosticSupport.beginSelectionRun(paths, manifests);
        return new RunFixture(paths, manifests, handle);
    }

    private RagCandidate4DiagnosticSupport.RuntimePaths paths(String name) throws Exception {
        RagCandidate4DiagnosticSupport.RuntimePaths paths =
                RagCandidate4DiagnosticSupport.paths(temp.resolve(name));
        Files.createDirectories(paths.freezeDirectory());
        writeManifest(paths.selectionManifest(), "candidate4-selection", "FROZEN");
        writeManifest(paths.holdoutManifest(), "candidate4-holdout", "FROZEN_NOT_BLIND");
        return paths;
    }

    private static void writeManifest(Path path, String dataset, String freezeStatus)
            throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestVersion", 1);
        manifest.put("dataset", dataset);
        manifest.put("freezeStatus", freezeStatus);
        manifest.put("generator", "candidate4-static-fixture-v1");
        manifest.put("version", 1);
        manifest.put("seed", 20260716L);
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("corpus", resource("corpus.jsonl", "a".repeat(64)));
        resources.put("queries", resource("queries.jsonl", "b".repeat(64)));
        resources.put("qrels", resource("qrels.tsv", "c".repeat(64)));
        resources.put("baseDistractor", resource("corpus.jsonl", "d".repeat(64)));
        manifest.put("resources", resources);
        manifest.put("counts", new LinkedHashMap<>(Map.of(
                "familyCount", 8,
                "queryCount", 16,
                "documentCount", 51,
                "segmentCount", 114,
                "qrelCount", 38)));
        manifest.put("datasetHash", manifestDatasetHash(manifest));
        Files.writeString(path, JSON.toJSONString(manifest), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> resource(String file, String sha256) {
        return new LinkedHashMap<>(Map.of("file", file, "sha256", sha256));
    }

    private static String manifestDatasetHash(Map<String, ?> manifest) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("generator", manifest.get("generator"));
        evidence.put("version", manifest.get("version"));
        evidence.put("seed", manifest.get("seed"));
        evidence.put("resources", manifest.get("resources"));
        evidence.put("counts", manifest.get("counts"));
        return ShadowContractSupport.configHash(evidence);
    }

    private static void rewriteManifestWithValidCountChange(Path path) throws Exception {
        JSONObject manifest = JSON.parseObject(Files.readString(path, StandardCharsets.UTF_8));
        JSONObject counts = manifest.getJSONObject("counts");
        counts.put("segmentCount", counts.getIntValue("segmentCount") + 1);
        manifest.put("datasetHash", manifestDatasetHash(manifest));
        Files.writeString(path, JSON.toJSONString(manifest), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> validArtifact(
            RagCandidate4DiagnosticSupport.FrozenManifests manifests) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("identifierAware", false);
        config.put("identifierConsistencyEnabled", true);
        config.put("topK", 10);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("status", "VALID");
        artifact.put("decision", "STOP_IDENTIFIER_ANCHOR_UNSUPPORTED");
        artifact.put("errorCode", null);
        artifact.put("datasetHash", manifests.selection().datasetHash());
        artifact.put("selectionManifestHash", manifests.selectionSha256());
        artifact.put("holdoutManifestHash", manifests.holdoutSha256());
        artifact.put("holdoutFreezeStatus", "FROZEN_NOT_BLIND");
        artifact.put("config", config);
        artifact.put("configHash", ShadowContractSupport.configHash(config));
        artifact.put("summary", Map.of());
        artifact.put("cases", List.of());
        return artifact;
    }

    private static RagCandidate4DiagnosticSupport.CaseEvidence
    targetEvidenceWithoutExactRecovery(String queryId) {
        Map<String, Integer> qrels = Map.of("exact", 3, "other-relevant", 2);
        return RagCandidate4DiagnosticSupport.classify(
                new RagCandidate4DiagnosticSupport.CaseInput(
                        queryId,
                        "family-" + queryId,
                        "selection",
                        true,
                        Map.of("keyword", ranked("exact")),
                        ranked("exact"),
                        ranked("exact"),
                        ranked("irrelevant"),
                        ranked("irrelevant", "exact"),
                        ranked("irrelevant"),
                        ranked("irrelevant"),
                        ranked("irrelevant"),
                        ranked("other-relevant", "irrelevant"),
                        qrels,
                        Set.of("exact"),
                        "actual-context",
                        "counterfactual-context",
                        false,
                        false));
    }

    private static RagCandidate4DiagnosticSupport.CaseEvidence controlEvidence(String queryId) {
        List<RagCandidate4DiagnosticSupport.RankedSegment> ranking = ranked("control");
        return RagCandidate4DiagnosticSupport.classify(
                new RagCandidate4DiagnosticSupport.CaseInput(
                        queryId,
                        "family-" + queryId,
                        "selection",
                        false,
                        Map.of("keyword", ranking),
                        ranking,
                        ranking,
                        ranking,
                        ranking,
                        ranking,
                        ranking,
                        ranking,
                        ranking,
                        Map.of(),
                        Set.of(),
                        "same-context",
                        "same-context",
                        false,
                        false));
    }

    private static List<RagCandidate4DiagnosticSupport.RankedSegment> ranked(String... ids) {
        List<RagCandidate4DiagnosticSupport.RankedSegment> ranking = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            ranking.add(new RagCandidate4DiagnosticSupport.RankedSegment(
                    ids[index], index + 1, 1.0D / (index + 1)));
        }
        return List.copyOf(ranking);
    }

    private record RunFixture(
            RagCandidate4DiagnosticSupport.RuntimePaths paths,
            RagCandidate4DiagnosticSupport.FrozenManifests manifests,
            RagCandidate4DiagnosticSupport.RunHandle handle) {
    }
}
