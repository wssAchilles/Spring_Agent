package tech.qiantong.qknow.hermes.agent.harness.experience;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Stage9ExperienceImporter {
    private static final String SUMMARY_FILE = "ppc_summary.json";
    private static final String SELECTED_FILE = "selected_candidate.json";
    private static final String MANIFEST_FILE = "checksums.sha256";
    private static final String FROZEN_MANIFEST_SHA256 =
            "7cc4f4c71d7e675eac4f99d47f9d722c34a6ffa3c159bddd808ed73951a61288";
    private static final List<String> PASSING_CASES = List.of(
            "kb-qa-01", "tool-route-02", "param-guard-03");
    private static final List<String> UNRESOLVED_CASES = List.of(
            "ctx-history-04", "comp-rag-tool-05");
    private static final List<String> CANDIDATES = List.of(
            "baseline", "candidate-a", "candidate-b");

    private final ExperienceLibrary library;

    public Stage9ExperienceImporter(ExperienceLibrary library) {
        this.library = Objects.requireNonNull(library, "library");
    }

    public ExperienceRunSummaryRecord importRun(Path runDirectory) {
        Objects.requireNonNull(runDirectory, "runDirectory");
        try {
            Path run = runDirectory.toAbsolutePath().normalize();
            Map<String, String> checksums = verifyManifest(run);
            JSONObject summary = JSON.parseObject(Files.readString(run.resolve(SUMMARY_FILE)));
            String runId = summary.getString("runId");
            if (runId == null || !runId.matches("[0-9a-f-]{36}")
                    || !run.getFileName().toString().equals(runId)) {
                throw new IllegalArgumentException("Stage 9 runId does not match its directory");
            }
            JSONArray probes = summary.getJSONArray("probes");
            JSONArray candidates = summary.getJSONArray("candidates");
            if (probes == null || probes.size() != 15
                    || candidates == null || candidates.size() != 3
                    || summary.getIntValue("totalProbesExecuted") != 15
                    || summary.getIntValue("totalModelCallsExecuted") != 26
                    || summary.getLongValue("totalTokensConsumed") != 35_500
                    || Double.compare(summary.getDoubleValue("totalCostCnyConsumed"), 0.06718974) != 0
                    || !"REJECTED_AFTER_REAL_PPC".equals(summary.getString("finalVerdict"))
                    || !"baseline".equals(summary.getString("selectedCandidateId"))) {
                throw new IllegalArgumentException("Stage 9 summary violates frozen replay contract");
            }
            JSONObject jsonChecksums = summary.getJSONObject("checksumsSha256");
            if (jsonChecksums == null || jsonChecksums.size() != 15) {
                throw new IllegalArgumentException("Stage 9 probe checksum map is incomplete");
            }
            for (String path : jsonChecksums.keySet()) {
                if (!sameHash(jsonChecksums.getString(path), checksums.get(path))) {
                    throw new IllegalArgumentException("Stage 9 summary and manifest disagree: " + path);
                }
            }
            Map<String, JSONObject> candidateById = candidates(candidates);
            Map<String, JSONObject> probeByKey = probes(probes);
            for (Map.Entry<String, JSONObject> probe : probeByKey.entrySet()) {
                String file = "probes/" + probe.getKey().replace('/', '-') + ".json";
                JSONObject frozen = JSON.parseObject(Files.readString(run.resolve(file)));
                if (!probe.getValue().equals(frozen)) {
                    throw new IllegalArgumentException("Stage 9 summary and probe disagree: " + file);
                }
            }
            List<ExperienceEntry> entries = new ArrayList<>();
            for (String caseId : PASSING_CASES) {
                entries.add(observed(runId, caseId, probeByKey, checksums, false));
            }
            for (String caseId : UNRESOLVED_CASES) {
                entries.add(observed(runId, caseId, probeByKey, checksums, true));
            }
            entries.add(rejected(runId, "candidate-a", candidateById, checksums,
                    "Prompt compression increased observed token use and cost",
                    "This prompt compression candidate was rejected; causal attribution remains unresolved"));
            entries.add(rejected(runId, "candidate-b", candidateById, checksums,
                    "Tighter loop limit did not pass the quality gate",
                    "This loop-limit candidate reduced cost but still failed the quality gate"));
            if (entries.stream().anyMatch(entry -> entry.disposition() == ExperienceDisposition.ADOPTED
                    || entry.disposition() == ExperienceDisposition.INVALID_FIXTURE
                    || !entry.domain().isUnscopedReplay())) {
                throw new IllegalStateException("Stage 9 replay escaped its isolation contract");
            }
            for (ExperienceEntry entry : entries) {
                library.recordExperience(entry);
            }
            return new ExperienceRunSummaryRecord(runId,
                    summary.getIntValue("totalProbesExecuted"),
                    summary.getIntValue("totalModelCallsExecuted"),
                    summary.getLongValue("totalTokensConsumed"),
                    summary.getDoubleValue("totalCostCnyConsumed"),
                    PASSING_CASES.size() + UNRESOLVED_CASES.size(),
                    PASSING_CASES.size(), UNRESOLVED_CASES.size(),
                    candidateById.size(), 2, 0, summary.getString("finalVerdict"));
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot import frozen Stage 9 replay", error);
        }
    }

    private static Map<String, String> verifyManifest(Path run) throws IOException {
        if (!Files.isDirectory(run) || Files.isSymbolicLink(run)) {
            throw new IOException("Stage 9 replay directory is unavailable");
        }
        Path manifest = run.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest) || Files.isSymbolicLink(manifest)) {
            throw new IOException("Stage 9 checksum manifest is unavailable");
        }
        if (!sameHash(FROZEN_MANIFEST_SHA256, sha256(Files.readAllBytes(manifest)))) {
            throw new IOException("Stage 9 checksum manifest differs from the frozen run");
        }
        Map<String, String> checksums = new HashMap<>();
        for (String line : Files.readAllLines(manifest)) {
            String[] pair = line.split("  ", 2);
            if (pair.length != 2 || !pair[0].matches("[0-9a-f]{64}")
                    || !(pair[1].equals(SUMMARY_FILE) || pair[1].equals(SELECTED_FILE)
                    || pair[1].matches("probes/[A-Za-z0-9._-]+\\.json"))
                    || checksums.putIfAbsent(pair[1], pair[0]) != null) {
                throw new IOException("Invalid Stage 9 checksum manifest");
            }
            Path file = run.resolve(pair[1]);
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)
                    || !sameHash(pair[0], sha256(Files.readAllBytes(file)))) {
                throw new IOException("Stage 9 replay checksum mismatch: " + pair[1]);
            }
        }
        if (checksums.size() != 17
                || !checksums.containsKey(SUMMARY_FILE)
                || !checksums.containsKey(SELECTED_FILE)) {
            throw new IOException("Stage 9 checksum manifest is incomplete");
        }
        return checksums;
    }

    private static Map<String, JSONObject> candidates(JSONArray array) {
        Map<String, JSONObject> result = new HashMap<>();
        for (Object value : array) {
            JSONObject candidate = (JSONObject) value;
            String id = candidate.getString("candidateId");
            if (!CANDIDATES.contains(id) || result.putIfAbsent(id, candidate) != null
                    || !candidate.getString("voucherFingerprint").matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Unexpected Stage 9 candidate");
            }
        }
        return result;
    }

    private static Map<String, JSONObject> probes(JSONArray array) {
        Map<String, JSONObject> result = new HashMap<>();
        Set<String> cases = new HashSet<>();
        cases.addAll(PASSING_CASES);
        cases.addAll(UNRESOLVED_CASES);
        for (Object value : array) {
            JSONObject probe = (JSONObject) value;
            String candidate = probe.getString("candidateId");
            String caseId = probe.getString("caseId");
            if (!CANDIDATES.contains(candidate) || !cases.contains(caseId)
                    || result.putIfAbsent(candidate + "/" + caseId, probe) != null) {
                throw new IllegalArgumentException("Unexpected Stage 9 probe");
            }
            JSONObject evidence = probe.getJSONObject("evidence");
            if (evidence == null || evidence.getLongValue("botId") != 9) {
                throw new IllegalArgumentException("Stage 9 provenance botId mismatch");
            }
        }
        if (result.size() != 15) {
            throw new IllegalArgumentException("Stage 9 probe matrix is incomplete");
        }
        for (String candidate : CANDIDATES) {
            for (String caseId : cases) {
                JSONObject probe = result.get(candidate + "/" + caseId);
                if (probe.getBooleanValue("qualityPass") != PASSING_CASES.contains(caseId)) {
                    throw new IllegalArgumentException("Stage 9 quality observations changed");
                }
            }
        }
        return result;
    }

    private static ExperienceEntry observed(String runId, String caseId,
                                            Map<String, JSONObject> probes,
                                            Map<String, String> checksums, boolean failure) {
        JSONObject probe = probes.get("baseline/" + caseId);
        JSONObject evidence = probe.getJSONObject("evidence");
        String file = "probes/baseline-" + caseId + ".json";
        String cue = switch (caseId) {
            case "kb-qa-01" -> "Grounded knowledge answer";
            case "tool-route-02" -> "Tool routing with transaction evidence";
            case "param-guard-03" -> "Invalid parameter guard";
            case "ctx-history-04" -> "Long context history answer was empty";
            case "comp-rag-tool-05" -> "Composite retrieval lacked calculator call";
            default -> throw new IllegalArgumentException("Unknown Stage 9 case");
        };
        String lesson = switch (caseId) {
            case "kb-qa-01" -> "Baseline answered a grounded knowledge request in this replay";
            case "tool-route-02" -> "Baseline routed the transaction tool in this replay";
            case "param-guard-03" -> "Baseline rejected an invalid parameter in this replay";
            case "ctx-history-04" -> "Answer was empty when completion and reasoning tokens reached the output cap; cause unresolved";
            case "comp-rag-tool-05" -> "Retrieval ran but the requested calculator did not; missing parameter was noted, cause unresolved";
            default -> throw new IllegalArgumentException("Unknown Stage 9 case");
        };
        EvidenceReference reference = new EvidenceReference(evidence.getString("evidenceId"),
                evidence.getString("requestId"), checksums.get(file),
                evidence.getString("executionMode"));
        return new ExperienceEntry("stage9-baseline-" + caseId,
                ExperienceDomain.unscopedReplay(), cue, lesson,
                "Offline Stage 9 probe; botId=9; quality passed=" + !failure, reference,
                0.8, runId, evidence.getString("voucherFingerprint"),
                ExperienceDisposition.OBSERVED, failure,
                failure ? ExperienceEntry.AttributionStatus.UNRESOLVED
                        : ExperienceEntry.AttributionStatus.NOT_APPLICABLE,
                1, false, null, Instant.parse(evidence.getString("timestamp")));
    }

    private static ExperienceEntry rejected(String runId, String candidateId,
                                            Map<String, JSONObject> candidates,
                                            Map<String, String> checksums,
                                            String cue, String lesson) {
        JSONObject candidate = candidates.get(candidateId);
        EvidenceReference reference = new EvidenceReference(runId + "-" + candidateId,
                runId + "-" + candidateId, checksums.get(SUMMARY_FILE), "REAL_PPC");
        return new ExperienceEntry("stage9-" + candidateId,
                ExperienceDomain.unscopedReplay(), cue, lesson,
                "Offline Stage 9 candidate summary; botId=9; quality passed=3/5",
                reference, 0.8, runId, candidate.getString("voucherFingerprint"),
                ExperienceDisposition.REJECTED, false,
                ExperienceEntry.AttributionStatus.NOT_APPLICABLE,
                1, false, null,
                Instant.parse(candidate.getJSONObject("voucher").getString("createdAt")));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean sameHash(String expected, String actual) {
        return expected != null && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
