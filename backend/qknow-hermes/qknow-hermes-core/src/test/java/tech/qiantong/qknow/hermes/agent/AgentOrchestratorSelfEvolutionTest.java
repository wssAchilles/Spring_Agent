package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.harness.experience.DefaultExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.FileSystemExperienceStore;
import tech.qiantong.qknow.hermes.cognitive.core.engine.CognitiveSelfEvolutionEngine;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.CognitiveEvolutionConfig;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorSelfEvolutionTest {
    @TempDir Path temp;

    @Test
    void enabledEvolutionOwnsComplexRequestAndAutomaticallyPersistsStrategy() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        AtomicInteger modelCalls = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            modelCalls.incrementAndGet();
            String userPrompt = invocation.getArgument(0, Prompt.class).getInstructions().stream()
                    .filter(org.springframework.ai.chat.messages.UserMessage.class::isInstance)
                    .map(org.springframework.ai.chat.messages.UserMessage.class::cast)
                    .map(org.springframework.ai.chat.messages.UserMessage::getText)
                    .reduce((first, next) -> next).orElse("");
            if (userPrompt.contains("拆解为最多")) {
                return response("{\"steps\":[{\"stepId\":\"s1\",\"objective\":\"inspect source\",\"dependencies\":[]}]}");
            }
            if (userPrompt.contains("inspect source")) {
                return response("step result");
            }
            if (userPrompt.contains("根据已完成的步骤结果回答")) {
                return response("final answer");
            }
            if (userPrompt.contains("评估当前答案")) {
                return response("{\"accepted\":true,\"repair\":null,"
                        + "\"strategy\":\"For repeated analysis, compare each claim with its supporting source.\"}");
            }
            throw new AssertionError("unexpected model prompt: " + userPrompt);
        });
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response("step result")));

        PlanSolveConfig planConfig = new PlanSolveConfig();
        planConfig.setEnabled(true);
        planConfig.setComplexKeywords(List.of("分析"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null, planConfig,
                null, null, null, null, null);
        CognitiveEvolutionConfig evolutionConfig = new CognitiveEvolutionConfig();
        evolutionConfig.setEnabled(true);
        orchestrator.setCognitiveEvolution(evolutionConfig,
                new CognitiveSelfEvolutionEngine(new DefaultExperienceLibrary(new FileSystemExperienceStore(temp))));
        ChatRequest request = ChatRequest.newBuilder().setRequestId("self-evolution-request")
                .setBotId(71).setQuestion("请分析这个来源并给出结论")
                .setSystemPrompt("Answer from available evidence")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("test")
                        .setModelName("deterministic-test").build())
                .build();

        var events = orchestrator.chat(request).collectList().block();

        assertEquals("final answer", events.stream().filter(event -> event.hasFinished())
                .findFirst().orElseThrow().getFinished().getFullText());
        assertEquals(4, modelCalls.get(), "old planner supervision and plan-and-solve must not run");
        var trace = orchestrator.getLatestCognitiveTrace(request.getRequestId());
        assertNotNull(trace);
        assertEquals("STRATEGY_ADOPTED", trace.states().get(trace.states().size() - 2));
        ExperienceDomain domain = ExperienceDomain.cognitiveScope(71, List.of());
        var persisted = new FileSystemExperienceStore(temp).list(domain);
        assertEquals(1, persisted.size());
        assertEquals("For repeated analysis, compare each claim with its supporting source.",
                persisted.getFirst().lesson());
    }

    @Test
    void disabledEvolutionLeavesTheReactPathInControl() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel model = mock(ChatModel.class);
        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response("baseline answer")));
        AgentOrchestrator orchestrator = new AgentOrchestrator(factory, null, null);
        CognitiveEvolutionConfig evolutionConfig = new CognitiveEvolutionConfig();
        evolutionConfig.setEnabled(false);
        orchestrator.setCognitiveEvolution(evolutionConfig,
                new CognitiveSelfEvolutionEngine(new DefaultExperienceLibrary(new FileSystemExperienceStore(temp))));
        ChatRequest request = ChatRequest.newBuilder().setRequestId("self-evolution-disabled")
                .setBotId(72).setQuestion("请分析这个来源并给出结论")
                .setSystemPrompt("Answer from available evidence")
                .setModelConfig(ModelConfig.newBuilder().setPlatform("test")
                        .setModelName("deterministic-test").build())
                .build();

        var events = orchestrator.chat(request).collectList().block();

        assertEquals("baseline answer", events.stream().filter(event -> event.hasFinished())
                .findFirst().orElseThrow().getFinished().getFullText());
        assertNull(orchestrator.getLatestCognitiveTrace(request.getRequestId()));
        verify(model, never()).call(any(Prompt.class));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
