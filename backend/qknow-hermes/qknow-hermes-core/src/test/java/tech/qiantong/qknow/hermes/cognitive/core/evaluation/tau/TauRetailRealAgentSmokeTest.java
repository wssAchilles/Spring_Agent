package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TauRetailRealAgentSmokeTest {
    @Test
    void classifiesNoToolCallsAndMissingMutation() {
        var task = new TauRetailTaskLoader.TaskCase(
                new TauRetailTaskLoader.Scenario("diagnostic", TauRetailTaskLoader.Split.BOOTSTRAP,
                        "hidden", "Cancel this order.", "", ""),
                List.of(new TauRetailTaskLoader.ToolCall("cancel_pending_order", new com.alibaba.fastjson2.JSONObject())));
        var unknown = new TauRetailStateEvaluator.Evaluation(TauRetailStateEvaluator.Status.FAIL,
                "STATE_OR_MUTATION_MISMATCH", "", "", "");
        var noCalls = new TauRetailAgentPolicy.RunResult(List.of(), 0, 0, 0, false, 0, null,
                List.of(), List.of(), null);
        assertEquals("NO_TOOL_CALLS", diagnosticCode(task, noCalls, unknown));

        var readOnlyCall = new TauRetailToolAdapter.ToolInvocation("get_order_details",
                new com.alibaba.fastjson2.JSONObject(), true, null, new com.alibaba.fastjson2.JSONObject());
        var readOnlyRun = new TauRetailAgentPolicy.RunResult(List.of(), 1, 0, 0,
                false, 0, null, List.of(), List.of(readOnlyCall), null);
        assertEquals("INCOMPLETE_NO_MUTATION", diagnosticCode(task, readOnlyRun, unknown));
        var failedReadOnlyRun = new TauRetailAgentPolicy.RunResult(List.of(), 8, 0, 0,
                false, 0, "AGENT_ERROR_500", List.of(), List.of(readOnlyCall),
                new TauRetailAgentPolicy.FailureDetail("AGENT_CHAT", "req", "IllegalStateException", "500"));
        assertEquals("INCOMPLETE_NO_MUTATION", diagnosticCode(task, failedReadOnlyRun, unknown));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_TAU_REAL_API", matches = "true")
    void runsThreeSimpleRealAgentRetailTasksWhenExplicitlyEnabled() throws IOException {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertNotNull(apiKey);
        assertTrue(!apiKey.isBlank(), "DEEPSEEK_API_KEY must be supplied through the process environment");

        TauRetailTaskLoader loader = new TauRetailTaskLoader();
        List<TauRetailTaskLoader.TaskCase> tasks = loader.realAgentSmokeTasks();
        boolean includeComplex = Boolean.parseBoolean(System.getenv("TAU_SMOKE_INCLUDE_COMPLEX"));
        assertEquals(includeComplex ? 5 : 3, tasks.size());
        String policyText = loader.policy();
        var initialDatabase = TauRetailEnvironment.fromResource().snapshot();
        TauRetailStateEvaluator evaluator = new TauRetailStateEvaluator();
        TauRetailAgentPolicy agent = TauRetailAgentPolicy.fromEnvironment();
        List<TaskSmokeResult> results = new ArrayList<>();
        boolean stoppedForBudget = false;

        for (TauRetailTaskLoader.TaskCase task : tasks) {
            if (stoppedForBudget) {
                results.add(TaskSmokeResult.notRun(task.scenario().taskId()));
                continue;
            }
            TauRetailEnvironment environment = new TauRetailEnvironment(initialDatabase);
            TauRetailTools tools = new TauRetailTools(environment);
            TauRetailUserSimulator.Session user = new TauRetailUserSimulator().start(task.scenario());
            agent.execute(policyText, user.initialMessage(), "", tools, user);
            TauRetailAgentPolicy.RunResult run = agent.lastRun();
            TauRetailStateEvaluator.Evaluation evaluation = evaluator.evaluate(task, initialDatabase, environment);
            results.add(new TaskSmokeResult(task.scenario().taskId(), run.modelCalls(), run.toolCalls(),
                    run.rounds(), evaluation.status().name(),
                    evaluation.reasonCode(), evaluation.observedDigest(), run.totalTokens(),
                    run.tokenUsageKnown(), run.latencyMs(), run.failureCode(),
                    diagnosticCode(task, run, evaluation), run.agentVisibleTools(),
                    run.failureDetail(), run.modelCalls()));
            stoppedForBudget = agent.budgetSnapshot().stoppedReason() != null;
        }

        int realApiCalls = results.stream().mapToInt(TaskSmokeResult::realApiCalls).sum();
        SmokeReport report = new SmokeReport(UUID.randomUUID().toString(), "DeepSeek", agent.modelName(),
                realApiCalls, agent.budgetSnapshot(), results);
        Path reportPath = Path.of("target", "tau-retail-real-agent-smoke.json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, JSON.toJSONString(report), StandardCharsets.UTF_8);
        assertEquals(includeComplex ? 5 : 3, report.tasks().size());
        if (!includeComplex) {
            assertTrue(report.tasks().stream().filter(task -> "PASS".equals(task.evaluatorStatus())).count() >= 2,
                    "the three-task smoke must reach 2/3 PASS before expanding");
        }
    }

    static String diagnosticCode(TauRetailTaskLoader.TaskCase task, TauRetailAgentPolicy.RunResult run,
                                TauRetailStateEvaluator.Evaluation evaluation) {
        if (evaluation.status() == TauRetailStateEvaluator.Status.PASS) {
            return "PASS";
        }
        if (run.toolCalls().isEmpty()) {
            return "NO_TOOL_CALLS";
        }
        Set<String> mutations = Set.of("cancel_pending_order", "exchange_delivered_order_items",
                "modify_pending_order_address", "modify_pending_order_items", "modify_pending_order_payment",
                "modify_user_address", "return_delivered_order_items", "transfer_to_human_agents");
        boolean expectedMutation = task.referenceActions().stream().anyMatch(call -> mutations.contains(call.name()));
        boolean observedMutation = run.toolCalls().stream().anyMatch(call -> mutations.contains(call.name())
                && call.success());
        if (expectedMutation && !observedMutation) {
            return "INCOMPLETE_NO_MUTATION";
        }
        if (run.toolCalls().stream().anyMatch(call -> !call.success())) {
            return "TOOL_ACTION_FAILED";
        }
        if (run.failureCode() != null) {
            return run.failureCode();
        }
        return "EVALUATOR_" + evaluation.reasonCode();
    }

    public record SmokeReport(String runId, String platform, String model, int realApiCalls,
                              TauRetailAgentPolicy.BudgetSnapshot budget,
                              List<TaskSmokeResult> tasks) {
        public SmokeReport {
            tasks = List.copyOf(tasks);
        }
    }

    public record TaskSmokeResult(String taskId, int modelCalls,
                                  List<TauRetailToolAdapter.ToolInvocation> toolCallSequence,
                                  List<TauRetailAgentPolicy.RoundResult> turns, String evaluatorStatus,
                                  String evaluatorReasonCode,
                                  String finalStateDigest, long tokens, boolean tokenUsageKnown,
                                  double latencyMs,
                                  String failureCode, String diagnosticCode,
                                  List<String> agentVisibleTools,
                                  TauRetailAgentPolicy.FailureDetail failureDetail, int realApiCalls) {
        public TaskSmokeResult {
            toolCallSequence = List.copyOf(toolCallSequence);
            turns = List.copyOf(turns);
            agentVisibleTools = List.copyOf(agentVisibleTools);
        }

        private static TaskSmokeResult notRun(String taskId) {
            return new TaskSmokeResult(taskId, 0, List.of(), List.of(), "UNKNOWN",
                    "BUDGET_EXHAUSTED", "", 0, false, 0.0,
                    "NOT_RUN_AFTER_BUDGET_EXHAUSTED", "NOT_RUN_AFTER_BUDGET_EXHAUSTED",
                    List.of(), null, 0);
        }
    }
}
