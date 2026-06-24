package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.eval.MetricScores;
import tech.qiantong.qknow.hermes.eval.RagasEvalConfig;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmAsAJudge 评估器测试 (基于 RagasEvaluator)")
class LlmAsAJudgeEvaluatorTest {

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ChatModel chatModel;

    @Mock
    private RagasEvalConfig config;

    private RagasEvaluator evaluator;

    @BeforeEach
    void setUp() {
        when(config.getPlatform()).thenReturn("deepseek");
        when(config.getBaseUrl()).thenReturn(null);
        when(config.getApiKey()).thenReturn(null);
        when(config.getModelName()).thenReturn("deepseek-chat");
        evaluator = new RagasEvaluator(chatModelFactory, config);
    }

    @Test
    @DisplayName("Faithfulness 评分: 忠实于上下文的回答应得高分 (>=0.85)")
    void faithfulness_faithfulAnswer_scoresHigh() {
        mockAllMetrics("{\"score\": 0.92, \"feedback\": \"highly faithful\"}");

        MetricScores scores = evaluator.evaluateSingle(
                "什么是知识图谱?",
                "知识图谱是一种结构化的知识表示方式。",
                List.of("知识图谱是一种结构化的知识表示方式，用于描述实体之间的关系。"),
                "知识图谱是一种结构化的知识表示方式。"
        );

        assertTrue(scores.getFaithfulness() >= 0.85, "faithfulness 分数应 >= 0.85");
    }

    @Test
    @DisplayName("Answer Relevance 评分: 相关回答应得高分 (>=0.85)")
    void answerRelevance_relevantAnswer_scoresHigh() {
        mockAllMetrics("{\"score\": 0.90, \"feedback\": \"highly relevant\"}");

        MetricScores scores = evaluator.evaluateSingle(
                "什么是RAG?",
                "RAG是检索增强生成技术。",
                List.of("RAG是一种结合检索和生成的AI技术。"),
                "RAG是一种检索增强生成技术。"
        );

        assertTrue(scores.getAnswerRelevance() >= 0.85, "answer_relevance 分数应 >= 0.85");
    }

    @Test
    @DisplayName("Context Precision 评分: 精确上下文应得高分 (>=0.85)")
    void contextPrecision_preciseContext_scoresHigh() {
        mockAllMetrics("{\"score\": 0.88, \"feedback\": \"precise context\"}");

        MetricScores scores = evaluator.evaluateSingle(
                "Python是什么?",
                "Python是一种编程语言。",
                List.of("Python是一种广泛使用的高级编程语言。"),
                "Python是一种编程语言。"
        );

        assertTrue(scores.getContextPrecision() >= 0.85, "context_precision 分数应 >= 0.85");
    }

    @Test
    @DisplayName("高分回答应通过 allAboveThreshold 检查")
    void highScorePasses_allAboveThreshold() {
        mockAllMetrics("{\"score\": 0.95, \"feedback\": \"excellent\"}");

        MetricScores scores = evaluator.evaluateSingle(
                "测试问题",
                "测试回答",
                List.of("测试上下文"),
                "期望答案"
        );

        assertTrue(scores.isAllAboveThreshold(), "所有指标均 >= 0.85 时应通过");
    }

    @Test
    @DisplayName("低分回答不应通过 allAboveThreshold 检查")
    void lowScoreFails_allAboveThreshold() {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(buildResponse("{\"score\": 0.3, \"feedback\": \"poor\"}"))
                .thenReturn(buildResponse("{\"score\": 0.9, \"feedback\": \"ok\"}"))
                .thenReturn(buildResponse("{\"score\": 0.9, \"feedback\": \"ok\"}"))
                .thenReturn(buildResponse("{\"score\": 0.9, \"feedback\": \"ok\"}"));

        MetricScores scores = evaluator.evaluateSingle(
                "测试问题", "测试回答", List.of("上下文"), "期望答案");

        assertFalse(scores.isAllAboveThreshold(), "faithfulness < 0.85 时不应通过");
    }

    private void mockAllMetrics(String responseJson) {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(buildResponse(responseJson))
                .thenReturn(buildResponse(responseJson))
                .thenReturn(buildResponse(responseJson))
                .thenReturn(buildResponse(responseJson));
    }

    private ChatResponse buildResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
