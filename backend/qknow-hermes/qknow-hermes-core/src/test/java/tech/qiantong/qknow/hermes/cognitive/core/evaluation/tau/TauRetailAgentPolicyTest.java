package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TauRetailAgentPolicyTest {
    @Test
    void fakeModelMutationIsVisibleToAgentExecutedAndPassesStateEvaluation() throws Exception {
        TauRetailTaskLoader.TaskCase source = new TauRetailTaskLoader().realAgentSmokeTasks(true).getFirst();
        TauRetailTaskLoader.ToolCall expectedCancel = source.referenceActions().stream()
                .filter(call -> call.name().equals("cancel_pending_order")).findFirst().orElseThrow();
        TauRetailTaskLoader.TaskCase task = new TauRetailTaskLoader.TaskCase(source.scenario(), List.of(expectedCancel));
        TauRetailEnvironment environment = TauRetailEnvironment.fromResource();
        var initial = environment.snapshot();
        TauRetailTools tools = new TauRetailTools(environment);
        TauRetailUserSimulator.Session user = new TauRetailUserSimulator().start(source.scenario());
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0, Prompt.class);
            boolean toolReturned = prompt.getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance);
            if (toolReturned) {
                return Flux.just(response("The cancellation is complete."));
            }
            AssistantMessage call = AssistantMessage.builder().content("")
                    .toolCalls(List.of(new ToolCall("cancel-76", "function", "cancel_pending_order",
                            expectedCancel.arguments().toJSONString()))).build();
            return Flux.just(new ChatResponse(List.of(new Generation(call))));
        });
        TauRetailAgentPolicy policy = new TauRetailAgentPolicy(factory, "test", "offline", "not-a-key", "fake");

        policy.execute("Retail policy", user.initialMessage(), "", tools, user);

        TauRetailAgentPolicy.RunResult run = policy.lastRun();
        assertTrue(run.agentVisibleTools().containsAll(List.of("cancel_pending_order",
                "modify_pending_order_address", "exchange_delivered_order_items")));
        assertEquals("cancel_pending_order", run.toolCalls().getFirst().name());
        assertTrue(run.toolCalls().getFirst().success());
        assertNotNull(environment.snapshot().getJSONObject("orders").getJSONObject(
                expectedCancel.arguments().getString("order_id")));
        assertEquals("cancelled", environment.snapshot().getJSONObject("orders")
                .getJSONObject(expectedCancel.arguments().getString("order_id")).getString("status"));
        assertEquals(TauRetailStateEvaluator.Status.PASS,
                new TauRetailStateEvaluator().evaluate(task, initial, environment).status());
    }

    @Test
    void perTaskCallLimitDoesNotExhaustTheWholeSmokeBudget() {
        var limits = new TauRetailAgentPolicy.BudgetLimits(1, 3, 100, 1_000, 16);
        var budget = new TauRetailAgentPolicy.SharedBudget(limits, false);

        assertTrue(budget.reserveCall(0, 10));
        assertFalse(budget.reserveCall(1, 10));
        assertEquals(null, budget.exhaustionReason());
        assertTrue(budget.reserveCall(0, 10));
        assertEquals(2, budget.snapshot().modelCalls());
    }

    @Test
    void tokenReservationStopsCallsBeforeTheConfiguredLimitCanBeExceeded() {
        var limits = new TauRetailAgentPolicy.BudgetLimits(3, 3, 100, 1_000, 16);
        var budget = new TauRetailAgentPolicy.SharedBudget(limits, false);

        assertTrue(budget.reserveCall(0, 70));
        budget.recordUsage(60, true, 70);
        assertFalse(budget.reserveCall(0, 41));
        assertEquals("MAX_TOTAL_TOKENS", budget.exhaustionReason());
        assertEquals(60, budget.snapshot().observedTokens());
    }

    @Test
    void realOrchestratorExecutesRetailToolAndContinuesWithSimulatedUserHistory() throws Exception {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        AtomicInteger modelCalls = new AtomicInteger();
        List<String> prompts = new ArrayList<>();
        when(model.stream(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0, Prompt.class);
            String text = prompt.getInstructions().stream().map(this::messageText)
                    .reduce((left, right) -> left + "\n" + right).orElse("");
            prompts.add(text);
            modelCalls.incrementAndGet();
            boolean returnedTool = prompt.getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance);
            if (text.contains("forgot-email-secret")) {
                return Flux.just(response("The order has been checked."));
            }
            if (!returnedTool) {
                AssistantMessage call = AssistantMessage.builder().content("")
                        .toolCalls(List.of(new ToolCall("retail-call-1", "function", "get_order_details",
                                "{\"order_id\":\"#W2378156\"}"))).build();
                return Flux.just(new ChatResponse(List.of(new Generation(call))));
            }
            return Flux.just(response("Please tell me your email address."));
        });

        TauRetailEnvironment environment = TauRetailEnvironment.fromResource();
        TauRetailTools tools = new TauRetailTools(environment);
        TauRetailUserSimulator.Session user = new TauRetailUserSimulator().start(
                new TauRetailTaskLoader.Scenario("policy-test", TauRetailTaskLoader.Split.BOOTSTRAP,
                        "hidden-task-instructions", "Please check order #W2378156.",
                        "known-user-secret", "forgot-email-secret"));
        TauRetailAgentPolicy policy = new TauRetailAgentPolicy(factory, "test", "offline", "not-a-key",
                "deterministic-test");

        policy.execute("public-retail-policy", user.initialMessage(), "", tools, user);

        TauRetailAgentPolicy.RunResult result = policy.lastRun();
        assertEquals(3, result.modelCalls());
        assertFalse(result.tokenUsageKnown());
        assertEquals(1, result.toolCalls().size());
        assertEquals("get_order_details", result.toolCalls().getFirst().name());
        assertTrue(result.toolCalls().getFirst().success());
        assertEquals(2, result.rounds().size());
        assertEquals("forgot-email-secret", result.rounds().getLast().userMessage());
        assertEquals(3, modelCalls.get());
        assertFalse(prompts.getFirst().contains("hidden-task-instructions"));
        assertFalse(prompts.getFirst().contains("known-user-secret"));
        assertFalse(prompts.getFirst().contains("forgot-email-secret"));
        assertFalse(prompts.stream().anyMatch(prompt -> prompt.contains("expectedOutcome")));
        assertTrue(prompts.getLast().contains("forgot-email-secret"));
        assertTrue(prompts.getLast().contains("public-retail-policy"));
        assertTrue(prompts.getFirst().contains("request is authorization"));
        assertTrue(prompts.getFirst().contains("read the affected state back"));
        assertTrue(environment.calls().getFirst().success());
    }

    @Test
    void simulatorAffirmsConfirmationRequests() throws Exception {
        TauRetailUserSimulator.Session user = new TauRetailUserSimulator().start(
                new TauRetailTaskLoader.Scenario("confirm-test", TauRetailTaskLoader.Split.BOOTSTRAP,
                        "hidden", "Please cancel order #W2378156.", "known", "unknown"));
        user.initialMessage();

        assertEquals("Yes, please proceed.", user.answer("Should I proceed with the cancellation?"));
    }

    @Test
    void simulatorRepliesToIdentityAndPersonaSpecificReasonRequests() {
        TauRetailUserSimulator.Session user = new TauRetailUserSimulator().start(
                new TauRetailTaskLoader.Scenario("reason-test", TauRetailTaskLoader.Split.BOOTSTRAP,
                        "You are discreet. If asked for reason, say you ordered the items by mistake.",
                        "Cancel pending orders.", "Yara Muller, ZIP 85041", "I do not remember my email."));
        user.initialMessage();

        assertEquals("Yara Muller, ZIP 85041", user.answer("Could you please provide your full name and zip code."));
        assertEquals("you ordered the items by mistake", user.answer("To proceed, I need a cancellation reason."));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private String messageText(Message message) {
        if (message instanceof UserMessage user) {
            return user.getText();
        }
        if (message instanceof AssistantMessage assistant) {
            return assistant.getText();
        }
        return message.toString();
    }
}
