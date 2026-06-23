package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tech.qiantong.qknow.hermes.judge.AiJudgeService;
import tech.qiantong.qknow.hermes.judge.JudgeResult;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelFinished;
import tech.qiantong.qknow.hermes.proto.ReflectionComplete;
import tech.qiantong.qknow.hermes.proto.ReflectionScore;
import tech.qiantong.qknow.hermes.proto.ReflectionStart;
import tech.qiantong.qknow.hermes.proto.DoneSignal;
import tech.qiantong.qknow.hermes.proto.StreamingChunk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReflectiveAgentTest {

    @Mock
    private AgentOrchestrator orchestrator;

    @Mock
    private AiJudgeService judgeService;

    private ReflectiveConfig config;
    private ReflectiveAgent reflectiveAgent;

    private ChatRequest sampleRequest;

    @BeforeEach
    void setUp() {
        config = new ReflectiveConfig();
        config.setEnabled(true);
        config.setMaxRetries(2);
        reflectiveAgent = new ReflectiveAgent(orchestrator, judgeService, config);

        sampleRequest = ChatRequest.newBuilder()
                .setRequestId("test-req-1")
                .setQuestion("什么是AI?")
                .setSystemPrompt("你是AI助手")
                .build();
    }

    @Test
    void passesOnFirstTry() {
        when(orchestrator.chat(any(ChatRequest.class))).thenReturn(Flux.just(
                ChatEvent.newBuilder()
                        .setRequestId("test-req-1")
                        .setFinished(ModelFinished.newBuilder()
                                .setFullText("AI是人工智能")
                                .build())
                        .build(),
                ChatEvent.newBuilder()
                        .setRequestId("test-req-1")
                        .setDone(DoneSignal.newBuilder().build())
                        .build()
        ));

        when(judgeService.judge(anyString(), anyString(), anyString()))
                .thenReturn(JudgeResult.passed(0.9, 0.9, 0.9, "优秀"));

        StepVerifier.create(reflectiveAgent.chat(sampleRequest))
                .assertNext(event -> {
                    assertTrue(event.hasReflectionStart());
                    assertEquals(1, event.getReflectionStart().getRound());
                })
                .assertNext(event -> assertTrue(event.hasFinished()))
                .assertNext(event -> {
                    assertTrue(event.hasReflectionScore());
                    assertTrue(event.getReflectionScore().getPassed());
                })
                .assertNext(event -> {
                    assertTrue(event.hasReflectionComplete());
                    assertEquals(1, event.getReflectionComplete().getTotalRounds());
                    assertTrue(event.getReflectionComplete().getPassed());
                })
                .assertNext(event -> assertTrue(event.hasDone()))
                .verifyComplete();

        verify(orchestrator, times(1)).chat(any(ChatRequest.class));
        verify(judgeService, times(1)).judge(anyString(), anyString(), anyString());
    }

    @Test
    void passesOnSecondTry() {
        when(orchestrator.chat(any(ChatRequest.class)))
                .thenReturn(Flux.just(
                        ChatEvent.newBuilder()
                                .setRequestId("test-req-1")
                                .setFinished(ModelFinished.newBuilder()
                                        .setFullText("不完整的回答")
                                        .build())
                                .build(),
                        ChatEvent.newBuilder()
                                .setRequestId("test-req-1")
                                .setDone(DoneSignal.newBuilder().build())
                                .build()
                ))
                .thenReturn(Flux.just(
                        ChatEvent.newBuilder()
                                .setRequestId("test-req-1")
                                .setFinished(ModelFinished.newBuilder()
                                        .setFullText("改进后的优秀回答")
                                        .build())
                                .build(),
                        ChatEvent.newBuilder()
                                .setRequestId("test-req-1")
                                .setDone(DoneSignal.newBuilder().build())
                                .build()
                ));

        when(judgeService.judge(anyString(), anyString(), eq("不完整的回答")))
                .thenReturn(JudgeResult.failed(0.4, 0.5, 0.4, "回答不够详细"));
        when(judgeService.judge(anyString(), anyString(), eq("改进后的优秀回答")))
                .thenReturn(JudgeResult.passed(0.9, 0.9, 0.9, "很好"));

        StepVerifier.create(reflectiveAgent.chat(sampleRequest))
                .assertNext(event -> {
                    assertTrue(event.hasReflectionStart());
                    assertEquals(1, event.getReflectionStart().getRound());
                })
                .assertNext(event -> assertTrue(event.hasFinished()))
                .assertNext(event -> {
                    assertTrue(event.hasReflectionScore());
                    assertFalse(event.getReflectionScore().getPassed());
                })
                .assertNext(event -> {
                    assertTrue(event.hasReflectionStart());
                    assertEquals(2, event.getReflectionStart().getRound());
                })
                .assertNext(event -> assertTrue(event.hasFinished()))
                .assertNext(event -> {
                    assertTrue(event.hasReflectionScore());
                    assertTrue(event.getReflectionScore().getPassed());
                })
                .assertNext(event -> {
                    assertTrue(event.hasReflectionComplete());
                    assertEquals(2, event.getReflectionComplete().getTotalRounds());
                    assertTrue(event.getReflectionComplete().getPassed());
                })
                .assertNext(event -> assertTrue(event.hasDone()))
                .verifyComplete();

        verify(orchestrator, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void reachesMaxRetries() {
        when(orchestrator.chat(any(ChatRequest.class))).thenReturn(Flux.just(
                ChatEvent.newBuilder()
                        .setRequestId("test-req-1")
                        .setFinished(ModelFinished.newBuilder()
                                .setFullText("低质量回答")
                                .build())
                        .build(),
                ChatEvent.newBuilder()
                        .setRequestId("test-req-1")
                        .setDone(DoneSignal.newBuilder().build())
                        .build()
        ));

        when(judgeService.judge(anyString(), anyString(), anyString()))
                .thenReturn(JudgeResult.failed(0.3, 0.3, 0.3, "需要改进"));

        StepVerifier.create(reflectiveAgent.chat(sampleRequest))
                .assertNext(event -> assertTrue(event.hasReflectionStart()))
                .assertNext(event -> assertTrue(event.hasFinished()))
                .assertNext(event -> assertTrue(event.hasReflectionScore()))
                .assertNext(event -> assertTrue(event.hasReflectionStart()))
                .assertNext(event -> assertTrue(event.hasFinished()))
                .assertNext(event -> assertTrue(event.hasReflectionScore()))
                .assertNext(event -> assertTrue(event.hasReflectionStart()))
                .assertNext(event -> assertTrue(event.hasFinished()))
                .assertNext(event -> assertTrue(event.hasReflectionScore()))
                .assertNext(event -> {
                    assertTrue(event.hasReflectionComplete());
                    assertEquals(3, event.getReflectionComplete().getTotalRounds());
                    assertFalse(event.getReflectionComplete().getPassed());
                })
                .assertNext(event -> assertTrue(event.hasDone()))
                .verifyComplete();

        verify(orchestrator, times(3)).chat(any(ChatRequest.class));
        verify(judgeService, times(3)).judge(anyString(), anyString(), anyString());
    }

    @Test
    void disabledModeDelegatesDirectly() {
        config.setEnabled(false);

        when(orchestrator.chat(any(ChatRequest.class))).thenReturn(Flux.just(
                ChatEvent.newBuilder()
                        .setRequestId("test-req-1")
                        .setFinished(ModelFinished.newBuilder()
                                .setFullText("直接回答")
                                .build())
                        .build(),
                ChatEvent.newBuilder()
                        .setRequestId("test-req-1")
                        .setDone(DoneSignal.newBuilder().build())
                        .build()
        ));

        StepVerifier.create(reflectiveAgent.chat(sampleRequest))
                .assertNext(event -> assertTrue(event.hasFinished()))
                .assertNext(event -> assertTrue(event.hasDone()))
                .verifyComplete();

        verify(orchestrator, times(1)).chat(any(ChatRequest.class));
        verifyNoInteractions(judgeService);
    }
}
