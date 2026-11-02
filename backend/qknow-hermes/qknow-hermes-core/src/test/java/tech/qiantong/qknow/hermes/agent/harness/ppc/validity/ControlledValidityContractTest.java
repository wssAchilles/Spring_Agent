package tech.qiantong.qknow.hermes.agent.harness.ppc.validity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControlledValidityContractTest {
    @Test
    void typedReceiptFactDoesNotMistakeQuestionOrNegationForAnAffirmativeClaim() {
        JSONObject task = task("B03");
        var rubric = rubric(task);
        String answer = """
                {"taskId":"B03","facts":{"transactionId":"TX-FIX-B-001","status":"SUCCESS",\
                "accountId":"ACC-FIX-B-01","receiptIssued":false},\
                "sourceQuote":"是否已发凭证 / 未发凭证 / 不代表已发凭证"}
                """;
        var evidence = rubric.assess(answer);
        assertTrue(evidence.passed());
        assertTrue(rubric.independentlyReproduces(evidence));

        String falseClaim = answer.replace("\"receiptIssued\":false", "\"receiptIssued\":true");
        assertEquals(ControlledAnswerRubric.Verdict.FACT_MISMATCH, rubric.assess(falseClaim).verdict());
        assertEquals(ControlledAnswerRubric.Verdict.INVALID_JSON,
                rubric.assess("TX-FIX-B-001 SUCCESS ACC-FIX-B-01 未发凭证").verdict());
    }

    @Test
    void fullRedactedAnswerIsRetainedAndCanBeIndependentlyReplayed(@TempDir Path temp) throws Exception {
        var rubric = rubric(task("B01"));
        String full = "{" + "\"taskId\":\"B01\",\"facts\":{\"inputDate\":\"2026-11-31\","
                + "\"dateValid\":false,\"daysInMonth\":30},\"sourceQuote\":\""
                + "x".repeat(400) + "\"}";
        var evidence = rubric.assess(full);
        assertTrue(evidence.passed());
        assertEquals(full, evidence.fullRedactedAnswer());
        assertTrue(evidence.fullRedactedAnswer().length() > 300);
        assertTrue(rubric.independentlyReproduces(evidence));
        Path saved = temp.resolve("assessment.json");
        Files.writeString(saved, JSON.toJSONString(evidence), StandardCharsets.UTF_8);
        var restored = JSON.parseObject(Files.readString(saved, StandardCharsets.UTF_8),
                ControlledAnswerRubric.Assessment.class);
        assertEquals(evidence, restored);
        assertTrue(rubric.independentlyReproduces(restored));
        assertFalse(rubric.independentlyReproduces(new ControlledAnswerRubric.Assessment(
                full + " ", evidence.answerSha256(), evidence.rubricSha256(), evidence.verdict())));

        String fakeKey = "sk-" + "x".repeat(20);
        var sensitive = rubric.assess(full.replace("x".repeat(400), fakeKey));
        assertEquals(ControlledAnswerRubric.Verdict.REDACTED_OUTPUT, sensitive.verdict());
        assertFalse(sensitive.fullRedactedAnswer().contains(fakeKey));
        assertFalse(sensitive.passed());
    }

    @Test
    void tenVersionedTasksHaveExplicitFailureMechanismsAndTriggeredHistory() {
        JSONArray tasks = tasks().getJSONArray("tasks");
        assertEquals("SYNTHETIC_ALGORITHM_BENCHMARK", tasks().getString("mode"));
        assertEquals(10, tasks.size());
        Set<String> ids = new HashSet<>();
        Set<String> bootstrapCategories = new HashSet<>();
        Set<String> holdoutCategories = new HashSet<>();
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            assertTrue(ids.add(task.getString("id")));
            assertFalse(task.getString("failureMechanism").isBlank());
            assertFalse(task.getString("question").isBlank());
            assertFalse(rubric(task).expectedFacts().isEmpty());
            ("BOOTSTRAP".equals(task.getString("split")) ? bootstrapCategories : holdoutCategories)
                    .add(task.getString("category"));
        }
        Set<String> categories = Set.of("PROMPT_ONLY", "RAG", "TOOL", "HISTORY", "COMPOSITE");
        assertEquals(categories, bootstrapCategories);
        assertEquals(categories, holdoutCategories);
        assertEquals(20, task("B04").getJSONArray("history").size());
        assertEquals(20, task("H04").getJSONArray("history").size());
    }

    @Test
    void fakeModelSeesSemanticCandidateAAndCandidateBActuallyDropsStaleHistory() {
        HermesHarnessVoucher baseline = new HermesHarnessAssembler().createDefaultProductionVoucher(110201L);
        HermesHarnessVoucher candidateA = ControlledValidityCandidates.evidenceBoundPrompt(baseline);
        HermesHarnessVoucher candidateB = ControlledValidityCandidates.triggeredMemoryWindow(baseline);
        JSONObject task = task("B04");

        Prompt basePrompt = firstModelPrompt(baseline, task);
        Prompt aPrompt = firstModelPrompt(candidateA, task);
        Prompt bPrompt = firstModelPrompt(candidateB, task);
        assertNotEquals(baseline.computeFingerprint(), candidateA.computeFingerprint());
        assertNotEquals(baseline.computeFingerprint(), candidateB.computeFingerprint());
        assertEquals(baseline.memoryContext(), candidateA.memoryContext());
        assertEquals(19, candidateB.memoryContext().shortTermWindowSize());
        assertEquals(basePrompt.getSystemMessage().getText(), bPrompt.getSystemMessage().getText());
        assertFalse(basePrompt.getSystemMessage().getText().contains("疑问、否定和引用"));
        assertTrue(aPrompt.getSystemMessage().getText().contains("疑问、否定和引用"));

        String baseMessages = messageText(basePrompt);
        String aMessages = messageText(aPrompt);
        String bMessages = messageText(bPrompt);
        assertTrue(baseMessages.contains("旧提案：采用 socket proxy"));
        assertTrue(aMessages.contains("旧提案：采用 socket proxy"));
        assertFalse(bMessages.contains("旧提案：采用 socket proxy"));
        assertTrue(bMessages.contains("最终决议：采用 eBPF"));
        assertNotEquals(baseMessages, bMessages);
    }

    @Test
    void deterministicFirstMentionOracleMakesTheStableImprovementGateReachable() {
        HermesHarnessVoucher baseline = new HermesHarnessAssembler().createDefaultProductionVoucher(110201L);
        HermesHarnessVoucher candidateB = ControlledValidityCandidates.triggeredMemoryWindow(baseline);
        JSONObject task = task("B04");
        var rubric = rubric(task);
        for (int repeat = 1; repeat <= 2; repeat++) {
            assertFalse(rubric.assess(firstMentionOracle(firstModelPrompt(baseline, task))).passed());
            assertTrue(rubric.assess(firstMentionOracle(firstModelPrompt(candidateB, task))).passed());
        }
    }

    @Test
    void frozenStage13BooleansReproduceTheOldMatrixButCannotQualifyStableImprovement() {
        JSONObject matrix = resource("/harness15-validity/stage13-quality-matrix.json");
        assertEquals("49ce0c3a903e295d986bfcebf4b694190067c1fca06c086a4a5b1ad1bbd12499",
                matrix.getString("sourceRunSha256"));
        JSONArray rows = matrix.getJSONArray("rows");
        assertEquals(10, rows.size());
        assertEquals(List.of(
                "B01/1:FPP", "B01/2:PPP", "B02/1:PPP", "B02/2:PPP",
                "B03/1:FFP", "B03/2:PFF", "B04/1:PPP", "B04/2:PPP",
                "B05/1:PPP", "B05/2:PPP"), rows.stream()
                .map(value -> (JSONObject) value)
                .map(row -> row.getString("taskId") + "/" + row.getIntValue("repetition") + ":"
                        + (row.getBooleanValue("baseline") ? "P" : "F")
                        + (row.getBooleanValue("A") ? "P" : "F")
                        + (row.getBooleanValue("B") ? "P" : "F"))
                .toList());
        int baselinePasses = 0;
        int aPasses = 0;
        int bPasses = 0;
        Set<String> roundOneFailures = new HashSet<>();
        Set<String> roundTwoFailures = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row.getBooleanValue("baseline")) baselinePasses++;
            else (row.getIntValue("repetition") == 1 ? roundOneFailures : roundTwoFailures)
                    .add(row.getString("taskId"));
            if (row.getBooleanValue("A")) aPasses++;
            if (row.getBooleanValue("B")) bPasses++;
        }
        assertEquals(8, baselinePasses);
        assertEquals(8, aPasses);
        assertEquals(9, bPasses);
        assertEquals(Set.of("B01", "B03"), roundOneFailures);
        assertTrue(roundTwoFailures.isEmpty());
        roundOneFailures.retainAll(roundTwoFailures);
        assertTrue(roundOneFailures.isEmpty());
    }

    private static ControlledAnswerRubric rubric(JSONObject task) {
        return new ControlledAnswerRubric(task.getString("id"), task.getJSONObject("expectedFacts"));
    }

    private static JSONObject task(String id) {
        JSONArray tasks = tasks().getJSONArray("tasks");
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (id.equals(task.getString("id"))) return task;
        }
        throw new IllegalArgumentException("unknown task " + id);
    }

    private static JSONObject tasks() {
        return resource("/harness15-validity/tasks.json");
    }

    private static JSONObject resource(String path) {
        try (InputStream stream = ControlledValidityContractTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Stage 15 task resource is missing");
            return JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException failed) {
            throw new IllegalStateException(failed);
        }
    }

    private static Prompt firstModelPrompt(HermesHarnessVoucher voucher, JSONObject task) {
        ChatModel model = mock(ChatModel.class);
        List<Prompt> prompts = new ArrayList<>();
        when(model.stream(any(Prompt.class))).thenAnswer(call -> {
            prompts.add(call.getArgument(0));
            return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("{}")))));
        });
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            prompts.add(call.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{}"))));
        });
        ChatModelFactory factory = new ChatModelFactory() {
            @Override
            public ChatModel getChatModel(String platform, String baseUrl, String apiKey, String modelName) {
                return model;
            }
        };
        PlanSolveConfig plan = new PlanSolveConfig();
        plan.setEnabled(false);
        plan.setRpReactEnabled(false);
        plan.setMaxReflectionRetries(0);
        AgentOrchestrator agent = new AgentOrchestrator(factory, name -> null,
                null, plan, null, null, null, null, null);
        String requestId = "h15-" + task.getString("id") + "-" + voucher.version();
        agent.registerRequestVoucher(requestId, voucher);
        try {
            ChatRequest.Builder request = ChatRequest.newBuilder().setRequestId(requestId).setBotId(110201L)
                    .setQuestion(task.getString("question") + "\n" + tasks().getString("answerContract"))
                    .setModelConfig(ModelConfig.newBuilder().setPlatform("deepseek")
                            .setModelName("offline-fake").build());
            JSONArray history = task.getJSONArray("history");
            if (history != null) {
                for (int i = 0; i < history.size(); i++) {
                    JSONObject turn = history.getJSONObject(i);
                    request.addHistory(ChatMessage.newBuilder().setRole(turn.getString("role"))
                            .setContent(turn.getString("content")).build());
                }
            }
            agent.chat(request.build()).collectList().block(Duration.ofSeconds(30));
        } finally {
            agent.unregisterRequestVoucher(requestId);
        }
        assertFalse(prompts.isEmpty());
        return prompts.getFirst();
    }

    private static String messageText(Prompt prompt) {
        return prompt.getInstructions().stream().map(message -> message.getText() == null
                ? "" : message.getText()).reduce("", (left, right) -> left + "\n" + right);
    }

    private static String firstMentionOracle(Prompt prompt) {
        String chosen = messageText(prompt).contains("旧提案：采用 socket proxy") ? "socket_proxy" : "eBPF";
        return JSON.toJSONString(Map.of("taskId", "B04", "facts", Map.of(
                "chosenPath", chosen, "latencyCause", "context_switch")));
    }
}
