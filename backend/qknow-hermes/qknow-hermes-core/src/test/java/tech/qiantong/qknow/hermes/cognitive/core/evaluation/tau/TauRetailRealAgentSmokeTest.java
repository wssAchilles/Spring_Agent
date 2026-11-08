package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TauRetailRealAgentSmokeTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_TAU_REAL_API", matches = "true")
    void runsFiveRealAgentRetailTasksWhenExplicitlyEnabled() throws IOException {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertNotNull(apiKey);
        assertTrue(!apiKey.isBlank(), "DEEPSEEK_API_KEY must be supplied through the process environment");

        TauRetailTaskLoader loader = new TauRetailTaskLoader();
        List<TauRetailTaskLoader.TaskCase> tasks = loader.realAgentSmokeTasks();
        assertEquals(5, tasks.size());
        String policyText = loader.policy();
        var initialDatabase = TauRetailEnvironment.fromResource().snapshot();
        TauRetailStateEvaluator evaluator = new TauRetailStateEvaluator();
        TauRetailAgentPolicy agent = TauRetailAgentPolicy.fromEnvironment(new ChatModelFactory());
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
                    run.tokenUsageKnown(), run.latencyMs(), run.failureCode(), run.modelCalls()));
            stoppedForBudget = "BUDGET_EXHAUSTED".equals(run.failureCode());
        }

        int realApiCalls = results.stream().mapToInt(TaskSmokeResult::realApiCalls).sum();
        SmokeReport report = new SmokeReport(UUID.randomUUID().toString(), "DeepSeek", agent.modelName(), realApiCalls, results);
        Path reportPath = Path.of("target", "tau-retail-real-agent-smoke.json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, JSON.toJSONString(report), StandardCharsets.UTF_8);
        assertEquals(5, report.tasks().size());
    }

    public record SmokeReport(String runId, String platform, String model, int realApiCalls,
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
                                  String failureCode, int realApiCalls) {
        public TaskSmokeResult {
            toolCallSequence = List.copyOf(toolCallSequence);
            turns = List.copyOf(turns);
        }

        private static TaskSmokeResult notRun(String taskId) {
            return new TaskSmokeResult(taskId, 0, List.of(), List.of(), "UNKNOWN",
                    "BUDGET_EXHAUSTED", "", 0, false, 0.0,
                    "NOT_RUN_AFTER_BUDGET_EXHAUSTED", 0);
        }
    }
}
