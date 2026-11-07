package tech.qiantong.qknow.hermes.cognitive.core.evaluation;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.hermes.cognitive.core.verification.ExpectedOutcome;
import tech.qiantong.qknow.hermes.cognitive.core.verification.TaskOutcomeSpec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EvaluationTaskLoader {
    private static final String RESOURCE_ROOT = "/reasoning_eval/";
    private static final Set<String> FAMILIES = Set.of(
            "STRUCTURED_OUTPUT_ASSERTION", "TOOL_STATE_ASSERTION", "DETERMINISTIC_LOGIC_ASSERTION");

    public Dataset load() throws IOException {
        JSONObject manifest = readObject("task_manifest.json");
        List<EvaluationTask> tasks = readLines("tasks.jsonl").stream()
                .map(this::parseTask).toList();
        Map<String, ExpectedOutcomeFixture> outcomes = new LinkedHashMap<>();
        for (String line : readLines("expected_outcomes.jsonl")) {
            ExpectedOutcomeFixture outcome = parseOutcome(line);
            if (outcomes.putIfAbsent(outcome.taskId(), outcome) != null) {
                throw new IllegalArgumentException("duplicate expected outcome for " + outcome.taskId());
            }
        }
        validate(manifest, tasks, outcomes);
        return new Dataset(tasks, outcomes);
    }

    private EvaluationTask parseTask(String line) {
        JSONObject value = JSON.parseObject(line);
        if (value.containsKey("expectedOutcome") || value.containsKey("expected")) {
            throw new IllegalArgumentException("expected outcome must not be embedded in a task");
        }
        JSONArray rawTags = value.getJSONArray("tags");
        List<String> tags = rawTags == null ? List.of() : rawTags.toJavaList(String.class);
        return new EvaluationTask(value.getString("taskId"), value.getString("familyId"),
                value.getString("domain"), value.getString("question"), tags,
                Split.valueOf(value.getString("split")), value.getString("toolStatusStepId"));
    }

    private ExpectedOutcomeFixture parseOutcome(String line) {
        JSONObject value = JSON.parseObject(line);
        JSONArray rawEvidence = value.getJSONArray("evidenceRefs");
        List<String> evidence = rawEvidence == null ? List.of() : rawEvidence.toJavaList(String.class);
        return new ExpectedOutcomeFixture(value.getString("taskId"), value.getString("familyId"),
                value.getString("value"), evidence, value.getString("toolStatusStepId"));
    }

    private void validate(JSONObject manifest, List<EvaluationTask> tasks,
                          Map<String, ExpectedOutcomeFixture> outcomes) {
        if (manifest.getIntValue("schemaVersion") != 1
                || manifest.getIntValue("taskCount") != 30
                || manifest.getIntValue("expectedOutcomeCount") != 30
                || tasks.size() != 30 || outcomes.size() != 30) {
            throw new IllegalArgumentException("reasoning evaluation manifest count mismatch");
        }
        Set<String> ids = new HashSet<>();
        Map<String, Long> familyCounts = new LinkedHashMap<>();
        Map<Split, Long> splitCounts = new LinkedHashMap<>();
        for (EvaluationTask task : tasks) {
            if (!ids.add(task.taskId())) {
                throw new IllegalArgumentException("duplicate task id " + task.taskId());
            }
            familyCounts.merge(task.familyId(), 1L, Long::sum);
            splitCounts.merge(task.split(), 1L, Long::sum);
            ExpectedOutcomeFixture outcome = outcomes.get(task.taskId());
            if (outcome == null || !task.familyId().equals(outcome.familyId())) {
                throw new IllegalArgumentException("missing or mismatched expected outcome for " + task.taskId());
            }
            if (!Objects.equals(task.toolStatusStepId(), outcome.toolStatusStepId())) {
                throw new IllegalArgumentException("tool step binding mismatch for " + task.taskId());
            }
        }
        if (!ids.equals(outcomes.keySet()) || !familyCounts.keySet().equals(FAMILIES)
                || familyCounts.values().stream().anyMatch(count -> count != 10L)
                || splitCounts.getOrDefault(Split.BOOTSTRAP, 0L) != 18L
                || splitCounts.getOrDefault(Split.HOLDOUT, 0L) != 12L
                || tasks.stream().map(EvaluationTask::domain).distinct().count() != 1) {
            throw new IllegalArgumentException("task families or split counts are invalid");
        }
        JSONObject manifestFamilies = manifest.getJSONObject("familyCounts");
        JSONObject manifestSplits = manifest.getJSONObject("splitCounts");
        if (manifestFamilies == null || manifestSplits == null
                || FAMILIES.stream().anyMatch(family -> manifestFamilies.getIntValue(family) != 10)
                || manifestSplits.getIntValue("BOOTSTRAP") != 18
                || manifestSplits.getIntValue("HOLDOUT") != 12) {
            throw new IllegalArgumentException("task manifest declarations do not match resources");
        }
        for (EvaluationTask task : tasks) {
            if ("TOOL_STATE_ASSERTION".equals(task.familyId())
                    != (task.toolStatusStepId() != null && !task.toolStatusStepId().isBlank())) {
                throw new IllegalArgumentException("tool step binding mismatch for " + task.taskId());
            }
        }
    }

    private JSONObject readObject(String name) throws IOException {
        try (InputStream stream = resource(name)) {
            return JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private List<String> readLines(String name) throws IOException {
        try (InputStream stream = resource(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null; ) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    private InputStream resource(String name) {
        InputStream stream = EvaluationTaskLoader.class.getResourceAsStream(RESOURCE_ROOT + name);
        if (stream == null) {
            throw new IllegalArgumentException("missing reasoning evaluation resource " + name);
        }
        return stream;
    }

    public enum Split {
        BOOTSTRAP,
        HOLDOUT
    }

    public record EvaluationTask(String taskId, String familyId, String domain, String question,
                                 List<String> tags, Split split, String toolStatusStepId) {
        public EvaluationTask {
            if (taskId == null || taskId.isBlank() || !FAMILIES.contains(familyId)
                    || domain == null || domain.isBlank() || question == null || question.isBlank()) {
                throw new IllegalArgumentException("task id, family, domain and question are required");
            }
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
            Objects.requireNonNull(split, "split");
        }
    }

    public record ExpectedOutcomeFixture(String taskId, String familyId, String value,
                                         List<String> evidenceRefs, String toolStatusStepId) {
        public ExpectedOutcomeFixture {
            if (taskId == null || taskId.isBlank() || !FAMILIES.contains(familyId)
                    || value == null || value.isBlank()) {
                throw new IllegalArgumentException("expected outcome fields are required");
            }
            evidenceRefs = List.copyOf(Objects.requireNonNull(evidenceRefs, "evidenceRefs"));
        }

        public TaskOutcomeSpec evaluatorSpec() {
            return new TaskOutcomeSpec(new ExpectedOutcome(familyId, value), evidenceRefs, toolStatusStepId);
        }
    }

    public record Dataset(List<EvaluationTask> tasks, Map<String, ExpectedOutcomeFixture> expectedOutcomes) {
        public Dataset {
            tasks = List.copyOf(tasks);
            expectedOutcomes = Map.copyOf(expectedOutcomes);
        }
    }
}
