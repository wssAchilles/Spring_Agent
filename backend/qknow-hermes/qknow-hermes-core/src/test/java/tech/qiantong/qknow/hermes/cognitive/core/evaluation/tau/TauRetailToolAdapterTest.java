package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TauRetailToolAdapterTest {
    @Test
    void realAgentSmokeCanSelectThreeSimpleTrainingTasks() throws Exception {
        List<TauRetailTaskLoader.TaskCase> tasks = new TauRetailTaskLoader().realAgentSmokeTasks(false);

        assertEquals(List.of("76", "113", "67"),
                tasks.stream().map(task -> task.scenario().taskId()).toList());
        assertTrue(tasks.stream().allMatch(task -> task.scenario().split() == TauRetailTaskLoader.Split.BOOTSTRAP));
        assertTrue(tasks.get(0).referenceActions().stream().anyMatch(call -> call.name().equals("cancel_pending_order")));
        assertTrue(tasks.get(1).referenceActions().stream().anyMatch(call -> call.name().equals("cancel_pending_order")));
        assertTrue(tasks.get(2).referenceActions().stream()
                .anyMatch(call -> call.name().equals("get_order_details")));
        assertEquals(List.of("76", "113", "67", "43", "0"),
                new TauRetailTaskLoader().realAgentSmokeTasks(true).stream()
                        .map(task -> task.scenario().taskId()).toList());
    }

    @Test
    void callbackSchemaAndToolExecutionMatchTauRetailNames() throws Exception {
        TauRetailEnvironment environment = TauRetailEnvironment.fromResource();
        TauRetailToolAdapter adapter = new TauRetailToolAdapter(new TauRetailTools(environment));
        assertTrue(adapter.toolNames().containsAll(List.of("get_order_details", "cancel_pending_order",
                "exchange_delivered_order_items", "modify_user_address")));
        assertEquals(TauRetailToolAdapter.smokeToolNames().size(), adapter.callbacks().size());

        var callback = adapter.resolve("get_order_details");
        assertNotNull(callback);
        assertEquals("get_order_details", callback.getToolDefinition().name());
        JSONObject schema = JSON.parseObject(callback.getToolDefinition().inputSchema());
        assertEquals("object", schema.getString("type"));
        assertTrue(schema.getJSONObject("properties").containsKey("order_id"));

        JSONObject response = JSON.parseObject(callback.call("{\"order_id\":\"#W2378156\"}"));
        assertEquals("success", response.getString("status"));
        assertEquals("#W2378156", response.getJSONObject("result").getString("order_id"));
        assertEquals(1, environment.calls().size());
        assertEquals("get_order_details", adapter.invocations().getFirst().name());
        assertFalse(adapter.invocations().getFirst().arguments().containsKey("expectedOutcome"));
    }
}
