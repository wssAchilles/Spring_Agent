package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class TauRetailTaskLoader {
    private static final String ROOT = "/tau2/retail/";
    private static final List<String> BOOTSTRAP_IDS = List.of(
            "50", "69", "72", "73", "75", "76", "78", "80", "81", "82", "85", "88");
    private static final List<String> HOLDOUT_IDS = List.of(
            "36", "38", "40", "65", "68", "70", "71", "74", "77", "79", "90", "94");

    public Dataset load() throws IOException {
        JSONArray rawTasks = JSON.parseArray(read("tasks.json"));
        JSONObject splitTasks = JSON.parseObject(read("split_tasks.json"));
        Set<String> trainIds = stringSet(splitTasks.getJSONArray("train"));
        Set<String> testIds = stringSet(splitTasks.getJSONArray("test"));
        Map<String, JSONObject> byId = rawTasks.stream().map(JSONObject.class::cast)
                .collect(Collectors.toMap(task -> task.getString("id"), task -> task));
        List<TaskCase> bootstrap = BOOTSTRAP_IDS.stream()
                .map(id -> parse(byId, trainIds, id, Split.BOOTSTRAP)).toList();
        List<TaskCase> holdout = HOLDOUT_IDS.stream()
                .map(id -> parse(byId, testIds, id, Split.HOLDOUT)).toList();
        if (bootstrap.size() != 12 || holdout.size() != 12
                || !java.util.Collections.disjoint(BOOTSTRAP_IDS, HOLDOUT_IDS)) {
            throw new IllegalArgumentException("tau retail evaluation split is invalid");
        }
        return new Dataset(bootstrap, holdout);
    }

    public List<TaskCase> realAgentSmokeTasks() throws IOException {
        return realAgentSmokeTasks(Boolean.parseBoolean(System.getenv("TAU_SMOKE_INCLUDE_COMPLEX")));
    }

    public List<TaskCase> realAgentSmokeTasks(boolean includeComplex) throws IOException {
        List<String> ids = includeComplex ? List.of("76", "113", "67", "43", "0")
                : List.of("76", "113", "67");
        JSONArray rawTasks = JSON.parseArray(read("tasks.json"));
        JSONObject splits = JSON.parseObject(read("split_tasks.json"));
        Set<String> trainIds = stringSet(splits.getJSONArray("train"));
        Map<String, JSONObject> byId = rawTasks.stream().map(JSONObject.class::cast)
                .collect(Collectors.toMap(task -> task.getString("id"), task -> task));
        return ids.stream().map(id -> parse(byId, trainIds, id, Split.BOOTSTRAP)).toList();
    }

    public String policy() throws IOException {
        return read("policy.md");
    }

    public List<TaskCase> smokeTasks(Dataset dataset) {
        List<String> simple = List.of("50", "73", "75", "80", "85");
        List<String> multiStep = List.of("0", "6", "15", "16", "20");
        JSONArray rawTasks;
        try {
            rawTasks = JSON.parseArray(read("tasks.json"));
        } catch (IOException e) {
            throw new IllegalStateException("tau retail task resource is unavailable", e);
        }
        Map<String, JSONObject> rawById = rawTasks.stream().map(JSONObject.class::cast)
                .collect(Collectors.toMap(task -> task.getString("id"), task -> task));
        List<TaskCase> result = new ArrayList<>();
        for (String id : simple) {
            result.add(parseRaw(rawById.get(id), id, Split.BOOTSTRAP));
        }
        for (String id : multiStep) {
            result.add(parseRaw(rawById.get(id), id, Split.BOOTSTRAP));
        }
        return List.copyOf(result);
    }

    private TaskCase parse(Map<String, JSONObject> byId, Set<String> splitIds, String id, Split split) {
        if (!splitIds.contains(id)) {
            throw new IllegalArgumentException("selected tau task does not belong to " + split + ": " + id);
        }
        JSONObject raw = byId.get(id);
        if (raw == null) {
            throw new IllegalArgumentException("selected tau task is missing: " + id);
        }
        return parseRaw(raw, id, split);
    }

    private TaskCase parseRaw(JSONObject raw, String id, Split split) {
        JSONObject scenario = raw.getJSONObject("user_scenario");
        JSONObject instructions = scenario.getJSONObject("instructions");
        JSONObject criteria = raw.getJSONObject("evaluation_criteria");
        List<ToolCall> referenceActions = criteria.getJSONArray("actions").stream()
                .map(JSONObject.class::cast)
                .map(action -> new ToolCall(action.getString("name"), action.getJSONObject("arguments")))
                .toList();
        return new TaskCase(new Scenario(id, split, instructions.getString("task_instructions"),
                instructions.getString("reason_for_call"), instructions.getString("known_info"),
                instructions.getString("unknown_info")), referenceActions);
    }

    private Set<String> stringSet(JSONArray values) {
        return values.stream().map(String::valueOf).collect(Collectors.toCollection(HashSet::new));
    }

    private String read(String name) throws IOException {
        try (InputStream stream = TauRetailTaskLoader.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) {
                throw new IllegalArgumentException("missing tau retail resource " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public enum Split {
        BOOTSTRAP,
        HOLDOUT
    }

    public record Scenario(String taskId, Split split, String taskInstructions,
                           String reasonForCall, String knownInfo, String unknownInfo) {
        public Scenario {
            if (taskId == null || taskId.isBlank() || split == null
                    || reasonForCall == null || reasonForCall.isBlank()) {
                throw new IllegalArgumentException("tau task public scenario is incomplete");
            }
        }

        public String publicText() {
            return taskInstructions + "\n" + reasonForCall;
        }
    }

    public record ToolCall(String name, JSONObject arguments) {
        public ToolCall {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("tool name is required");
            }
            arguments = arguments == null ? new JSONObject() : JSON.parseObject(arguments.toJSONString());
        }
    }

    public record TaskCase(Scenario scenario, List<ToolCall> referenceActions) {
        public TaskCase {
            if (scenario == null) {
                throw new IllegalArgumentException("scenario is required");
            }
            referenceActions = List.copyOf(referenceActions);
        }
    }

    public record Dataset(List<TaskCase> bootstrap, List<TaskCase> holdout) {
        public Dataset {
            bootstrap = List.copyOf(bootstrap);
            holdout = List.copyOf(holdout);
        }
    }
}
