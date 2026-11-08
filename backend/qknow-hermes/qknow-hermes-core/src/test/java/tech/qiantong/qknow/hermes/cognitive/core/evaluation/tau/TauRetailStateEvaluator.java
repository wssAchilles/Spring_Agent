package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TauRetailStateEvaluator {
    private static final Set<String> MUTATIONS = Set.of(
            "cancel_pending_order", "exchange_delivered_order_items", "modify_pending_order_address",
            "modify_pending_order_items", "modify_pending_order_payment", "modify_user_address",
            "return_delivered_order_items", "transfer_to_human_agents");
    public Evaluation evaluate(TauRetailTaskLoader.TaskCase task, JSONObject initialDatabase,
                               TauRetailEnvironment observed) {
        if (task.referenceActions().isEmpty()) {
            return new Evaluation(Status.UNKNOWN, "MISSING_EXPECTED_ACTIONS", "", "", "");
        }
        TauRetailEnvironment expected = new TauRetailEnvironment(initialDatabase);
        TauRetailTools expectedTools = new TauRetailTools(expected);
        for (TauRetailTaskLoader.ToolCall call : task.referenceActions()) {
            expectedTools.call(call);
        }
        if (expected.calls().stream().anyMatch(record -> !record.success()
                && "UNKNOWN_TOOL".equals(record.errorCode()))) {
            return new Evaluation(Status.UNKNOWN, "REFERENCE_ACTION_UNSUPPORTED", "", "", "");
        }
        List<String> expectedActions = task.referenceActions().stream()
                .map(TauRetailStateEvaluator::actionSignature).toList();
        List<String> observedActions = observed.calls().stream()
                .map(record -> actionSignature(record.call())).toList();
        boolean actionsMatch = isSubsequence(expectedActions, observedActions);
        List<String> expectedMutations = mutations(expected.calls().stream()
                .filter(TauRetailEnvironment.CallRecord::success).map(TauRetailEnvironment.CallRecord::call).toList());
        List<String> observedMutations = mutations(observed.calls().stream()
                .filter(TauRetailEnvironment.CallRecord::success).map(TauRetailEnvironment.CallRecord::call).toList());
        boolean errorsMatch = failedCalls(expected.calls()).equals(failedCalls(observed.calls()));
        String expectedProjection = expectedProjection(task, expected.snapshot());
        String observedProjection = expectedProjection(task, observed.snapshot());
        boolean pass = actionsMatch && errorsMatch && expectedMutations.equals(observedMutations)
                && expectedProjection.equals(observedProjection);
        return new Evaluation(pass ? Status.PASS : Status.FAIL,
                pass ? "STATE_MATCH" : errorsMatch ? "STATE_OR_MUTATION_MISMATCH" : "TOOL_ERROR_MISMATCH",
                digest(expectedProjection), digest(observedProjection), evidenceDigest(observed.calls()));
    }

    private String expectedProjection(TauRetailTaskLoader.TaskCase task, JSONObject database) {
        Map<String, JSONObject> orders = new LinkedHashMap<>();
        Map<String, JSONObject> users = new LinkedHashMap<>();
        for (TauRetailTaskLoader.ToolCall action : task.referenceActions()) {
            String orderId = action.arguments().getString("order_id");
            String userId = action.arguments().getString("user_id");
            if (orderId != null && database.getJSONObject("orders").containsKey(orderId)) {
                JSONObject order = database.getJSONObject("orders").getJSONObject(orderId);
                orders.put(orderId, order);
                userId = order.getString("user_id");
            }
            if (userId != null && database.getJSONObject("users").containsKey(userId)) {
                users.put(userId, database.getJSONObject("users").getJSONObject(userId));
            }
        }
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("orders", orders);
        projection.put("users", users);
        return JSON.toJSONString(projection);
    }

    private List<String> mutations(List<TauRetailTaskLoader.ToolCall> calls) {
        return calls.stream().filter(call -> MUTATIONS.contains(call.name()))
                .map(call -> call.name() + "|" + call.arguments().toJSONString()).toList();
    }

    private List<String> failedCalls(List<TauRetailEnvironment.CallRecord> calls) {
        return calls.stream().filter(record -> !record.success())
                .map(record -> actionSignature(record.call()) + "|" + record.errorCode()).toList();
    }

    private static String actionSignature(TauRetailTaskLoader.ToolCall call) {
        return call.name() + "|" + call.arguments().toJSONString();
    }

    private boolean isSubsequence(List<String> expected, List<String> observed) {
        int next = 0;
        for (String action : observed) {
            if (next < expected.size() && expected.get(next).equals(action)) {
                next++;
            }
        }
        return next == expected.size();
    }

    private String evidenceDigest(List<TauRetailEnvironment.CallRecord> calls) {
        String canonical = calls.stream().map(record -> record.call().name() + "|"
                        + record.call().arguments().toJSONString() + "|" + record.success() + "|"
                        + record.errorCode() + "|" + record.output().toJSONString())
                .collect(java.util.stream.Collectors.joining("\n"));
        return digest(canonical);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public enum Status {
        PASS,
        FAIL,
        UNKNOWN
    }

    public record Evaluation(Status status, String reasonCode, String expectedDigest,
                             String observedDigest, String evidenceDigest) { }
}
