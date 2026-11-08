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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TauRetailAgentPolicyTest {
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
            return Flux.just(response("Have you forgotten your email address?"));
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
        assertTrue(environment.calls().getFirst().success());
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
