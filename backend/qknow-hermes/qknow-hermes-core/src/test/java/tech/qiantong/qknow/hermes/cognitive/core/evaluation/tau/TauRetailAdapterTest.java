package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TauRetailAdapterTest {
    @Test
    void tauRetailSmokeCoversTenTasksDatabaseToolsStateAndErrors() throws Exception {
        TauRetailTaskLoader loader = new TauRetailTaskLoader();
        TauRetailTaskLoader.Dataset dataset = loader.load();
        List<TauRetailTaskLoader.TaskCase> smoke = loader.smokeTasks(dataset);

        assertEquals(10, smoke.size());
        assertEquals(5, smoke.subList(0, 5).stream()
                .filter(task -> task.referenceActions().size() <= 4).count());
        assertEquals(5, smoke.subList(5, 10).stream()
                .filter(task -> task.referenceActions().size() >= 5).count());
        assertTrue(smoke.stream().allMatch(task -> task.scenario().split() == TauRetailTaskLoader.Split.BOOTSTRAP));

        JSONObject initialDatabase = TauRetailEnvironment.fromResource().snapshot();
        assertEquals(50, initialDatabase.getJSONObject("products").size());
        assertEquals(500, initialDatabase.getJSONObject("users").size());
        assertEquals(1000, initialDatabase.getJSONObject("orders").size());

        TauRetailStateEvaluator evaluator = new TauRetailStateEvaluator();
        for (TauRetailTaskLoader.TaskCase task : smoke) {
            TauRetailEnvironment environment = new TauRetailEnvironment(initialDatabase);
            TauRetailTools tools = new TauRetailTools(environment);
            for (TauRetailTaskLoader.ToolCall action : task.referenceActions()) {
                assertTrue(tools.call(action).success(), task.scenario().taskId() + ":" + action.name());
            }
            TauRetailStateEvaluator.Evaluation result = evaluator.evaluate(task, initialDatabase, environment);
            assertEquals(TauRetailStateEvaluator.Status.PASS, result.status(), task.scenario().taskId());
            assertEquals(64, result.expectedDigest().length());
            assertEquals(64, result.observedDigest().length());
            assertEquals(64, result.evidenceDigest().length());
            assertEquals(result.expectedDigest(), evaluator.evaluate(task, initialDatabase, environment).expectedDigest());
            assertEquals(result.observedDigest(), evaluator.evaluate(task, initialDatabase, environment).observedDigest());
            assertEquals(result.evidenceDigest(), evaluator.evaluate(task, initialDatabase, environment).evidenceDigest());
        }

        TauRetailEnvironment environment = new TauRetailEnvironment(initialDatabase);
        TauRetailTools tools = new TauRetailTools(environment);
        TauRetailTools.ToolResult missing = tools.call(new TauRetailTaskLoader.ToolCall(
                "get_order_details", new JSONObject(java.util.Map.of("order_id", "#W0000000"))));
        assertFalse(missing.success());
        assertEquals("ORDER_NOT_FOUND", missing.errorCode());
        assertEquals(1, environment.errors().size());
        assertEquals("ORDER_NOT_FOUND", environment.errors().getFirst().errorCode());
    }

    @Test
    void referenceLookupErrorsAreReproducibleWhileUnsupportedToolsStayUnknown() throws Exception {
        TauRetailTaskLoader loader = new TauRetailTaskLoader();
        JSONObject initial = TauRetailEnvironment.fromResource().snapshot();
        TauRetailStateEvaluator evaluator = new TauRetailStateEvaluator();
        for (String taskId : List.of("38", "68")) {
            TauRetailTaskLoader.TaskCase task = loader.load().holdout().stream()
                    .filter(candidate -> candidate.scenario().taskId().equals(taskId)).findFirst().orElseThrow();
            TauRetailEnvironment observed = new TauRetailEnvironment(initial);
            TauRetailTools tools = new TauRetailTools(observed);
            task.referenceActions().forEach(tools::call);

            assertFalse(observed.errors().isEmpty());
            assertEquals(TauRetailStateEvaluator.Status.PASS,
                    evaluator.evaluate(task, initial, observed).status(), taskId);

            TauRetailTools withUnexpectedError = new TauRetailTools(observed);
            withUnexpectedError.call(new TauRetailTaskLoader.ToolCall("get_order_details",
                    new JSONObject(java.util.Map.of("order_id", "#W0000000"))));
            TauRetailStateEvaluator.Evaluation mismatch = evaluator.evaluate(task, initial, observed);
            assertEquals(TauRetailStateEvaluator.Status.FAIL, mismatch.status(), taskId);
            assertEquals("TOOL_ERROR_MISMATCH", mismatch.reasonCode(), taskId);
        }

        TauRetailTaskLoader.TaskCase unsupported = new TauRetailTaskLoader.TaskCase(
                new TauRetailTaskLoader.Scenario("unknown-tool", TauRetailTaskLoader.Split.HOLDOUT,
                        "test", "test", "", ""),
                List.of(new TauRetailTaskLoader.ToolCall("unregistered_tool", new JSONObject())));
        TauRetailEnvironment observed = new TauRetailEnvironment(initial);
        new TauRetailTools(observed).call(unsupported.referenceActions().getFirst());
        TauRetailStateEvaluator.Evaluation result = evaluator.evaluate(unsupported, initial, observed);
        assertEquals(TauRetailStateEvaluator.Status.UNKNOWN, result.status());
        assertEquals("REFERENCE_ACTION_UNSUPPORTED", result.reasonCode());
    }

    @Test
    void deliveredReturnRecordsRequestWithoutRemovingItems() throws Exception {
        TauRetailTaskLoader.TaskCase task = new TauRetailTaskLoader().load().bootstrap().stream()
                .filter(candidate -> candidate.scenario().taskId().equals("73")).findFirst().orElseThrow();
        JSONObject initial = TauRetailEnvironment.fromResource().snapshot();
        TauRetailEnvironment environment = new TauRetailEnvironment(initial);
        TauRetailTools tools = new TauRetailTools(environment);
        int itemCount = initial.getJSONObject("orders").getJSONObject("#W5272531").getJSONArray("items").size();

        assertTrue(tools.call(task.referenceActions().getFirst()).success());
        JSONObject order = environment.snapshot().getJSONObject("orders").getJSONObject("#W5272531");
        assertEquals("return requested", order.getString("status"));
        assertEquals(itemCount, order.getJSONArray("items").size());
        assertEquals(task.referenceActions().getFirst().arguments().getJSONArray("item_ids").stream()
                        .map(String::valueOf).sorted().toList(),
                order.getJSONArray("return_items").stream().map(String::valueOf).toList());
    }
}
