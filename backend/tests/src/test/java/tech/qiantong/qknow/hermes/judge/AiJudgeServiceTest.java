package tech.qiantong.qknow.hermes.judge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AiJudgeServiceTest {

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ChatModel chatModel;

    @Mock
    private JudgeConfig config;

    private AiJudgeService aiJudgeService;

    @BeforeEach
    void setUp() {
        when(config.getPlatform()).thenReturn("deepseek");
        when(config.getBaseUrl()).thenReturn(null);
        when(config.getApiKey()).thenReturn(null);
        when(config.getModelName()).thenReturn("deepseek-chat");
        lenient().when(config.getThreshold()).thenReturn(0.7);
        aiJudgeService = new AiJudgeService(chatModelFactory, config);
    }

    @Test
    @DisplayName("评分服务异常时默认拒绝 - judge() catch block")
    void judge_serviceException_returnsFailed() {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenThrow(new RuntimeException("服务不可用"));

        JudgeResult result = aiJudgeService.judge("test query", "context", "answer");

        assertFalse(result.isPassed(), "服务异常时应返回 REJECT");
        assertEquals(0.0, result.getFactualityScore());
        assertEquals(0.0, result.getRelevanceScore());
        assertEquals(0.0, result.getInstructionScore());
        assertTrue(result.getFeedback().contains("默认拒绝"));
    }

    @Test
    @DisplayName("评分结果解析失败时默认拒绝 - parseJudgeResponse() catch block")
    void judge_invalidJsonResponse_returnsFailed() {
        AssistantMessage assistantMessage = new AssistantMessage("这不是有效的JSON");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));

        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        JudgeResult result = aiJudgeService.judge("test query", "context", "answer");

        assertFalse(result.isPassed(), "解析失败时应返回 REJECT");
        assertEquals(0.0, result.getFactualityScore());
        assertEquals(0.0, result.getRelevanceScore());
        assertEquals(0.0, result.getInstructionScore());
        assertTrue(result.getFeedback().contains("解析失败"));
    }

    @Test
    @DisplayName("所有失败场景 isPassed 均为 false")
    void allFailureScenarios_isPassedReturnsFalse() {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        JudgeResult result = aiJudgeService.judge("q", "c", "a");

        assertFalse(result.isPassed());
    }
}
