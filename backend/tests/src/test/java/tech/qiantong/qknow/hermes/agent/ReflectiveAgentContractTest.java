package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.judge.AiJudgeService;
import tech.qiantong.qknow.hermes.judge.JudgeResult;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelFinished;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Reflexion 反思回路闭环与反馈回填契约测试
 */
@ExtendWith(MockitoExtension.class)
class ReflectiveAgentContractTest {

    @Mock
    private AgentOrchestrator orchestrator;

    @Mock
    private AiJudgeService judgeService;

    private ReflectiveConfig config;
    private ReflectiveAgent reflectiveAgent;

    @BeforeEach
    void setUp() {
        config = new ReflectiveConfig();
        config.setEnabled(true);
        config.setMaxRetries(1); // 允许重试 1 次 (共 2 轮)
        reflectiveAgent = new ReflectiveAgent(orchestrator, judgeService, config);
    }

    @Test
    @DisplayName("契约验证：第 1 轮评分未通过时，必须将 JudgeResult 的 feedback 回填注入到第 2 轮请求中")
    void testFeedbackInjectedOnRetry() {
        ChatRequest originalRequest = ChatRequest.newBuilder()
                .setRequestId("req-refl-001")
                .setQuestion("介绍一下量子计算")
                .setSystemPrompt("你是一个严谨的科学助手")
                .build();

        // 模拟第 1 轮与第 2 轮的模型输出流
        when(orchestrator.chat(any(ChatRequest.class))).thenReturn(Flux.just(
                ChatEvent.newBuilder()
                        .setRequestId("req-refl-001")
                        .setFinished(ModelFinished.newBuilder().setFullText("回答内容").build())
                        .build()
        ));

        // 第 1 轮评分未通过，给出详细反馈；第 2 轮评分通过
        JudgeResult failResult = new JudgeResult(0.5, 0.5, 0.4, 0.45, false, "缺少关键技术细节与具体算法名称");
        JudgeResult passResult = new JudgeResult(0.95, 0.95, 0.9, 0.93, true, "论证严谨，通过");

        when(judgeService.judge(anyString(), anyString(), anyString()))
                .thenReturn(failResult)
                .thenReturn(passResult);

        // 执行
        reflectiveAgent.chat(originalRequest).blockLast();

        // 捕获传递给 orchestrator 的两次调用入参
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator, times(2)).chat(requestCaptor.capture());

        ChatRequest firstCall = requestCaptor.getAllValues().get(0);
        ChatRequest secondCall = requestCaptor.getAllValues().get(1);

        // 断言第 1 次为原始输入
        assertEquals("你是一个严谨的科学助手", firstCall.getSystemPrompt());

        // 核心契约断言：第 2 次重试必须将上一轮的失败评价与反馈注入到系统提示或上下文中！
        String secondPrompt = secondCall.getSystemPrompt();
        assertTrue(secondPrompt.contains("缺少关键技术细节与具体算法名称") || secondPrompt.contains("reflection_critique"),
                "第 2 轮提示词必须包含上一轮 AI Judge 的反馈指导！实际为: " + secondPrompt);
    }
}
