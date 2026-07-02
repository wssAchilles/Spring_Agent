package tech.qiantong.qknow.hermes.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagasEvaluatorTest {

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
    @DisplayName("完美回答在所有指标上得分高")
    void perfectAnswer_scoresHighOnAllMetrics() {
        String highScoreJson = "{\"score\": 0.95, \"feedback\": \"good\"}";
        mockJudgeOnlyCalls(highScoreJson);

        MetricScores scores = evaluator.evaluateSingle(
                "什么是知识图谱?",
                "知识图谱是一种结构化的知识表示方式，用于描述实体之间的关系。",
                List.of("知识图谱是一种结构化的知识表示方式，用于描述实体之间的关系。"),
                "知识图谱是一种结构化的知识表示方式。"
        );

        assertEquals(0.95, scores.getScore("faithfulness"), 0.01);
        assertEquals(0.95, scores.getScore("answer_relevance"), 0.01);
        assertEquals(0.95, scores.getScore("context_precision"), 0.01);
        assertEquals(0.95, scores.getScore("context_recall"), 0.01);
        assertEquals(0.95, scores.getScore("factual_correctness"), 0.01);
        assertEquals(0.95, scores.getScore("noise_sensitivity"), 0.01);
        assertEquals(0.95, scores.getScore("negative_rejection"), 0.01);
        assertTrue(scores.isAllAboveThreshold());
    }

    @Test
    @DisplayName("不相关的回答在相关性上得分低")
    void irrelevantAnswer_scoresLowOnRelevance() {
        mockMetricScores(Map.of(
                "faithfulness", 0.9,
                "answer_relevance", 0.2,
                "context_precision", 0.9,
                "context_recall", 0.9,
                "factual_correctness", 0.8,
                "noise_sensitivity", 0.9,
                "negative_rejection", 0.9));

        MetricScores scores = evaluator.evaluateSingle(
                "今天天气怎么样?",
                "知识图谱是一种结构化的知识表示方式。",
                List.of("知识图谱是一种结构化的知识表示方式。"),
                "今天天气晴朗。"
        );

        assertEquals(0.9, scores.getScore("faithfulness"), 0.01);
        assertEquals(0.2, scores.getScore("answer_relevance"), 0.01);
        assertFalse(scores.isAllAboveThreshold());
    }

    @Test
    @DisplayName("包含幻觉的回答在忠实度上得分低")
    void hallucinatedAnswer_scoresLowOnFaithfulness() {
        mockMetricScores(Map.of(
                "faithfulness", 0.3,
                "answer_relevance", 0.8,
                "context_precision", 0.7,
                "context_recall", 0.6,
                "factual_correctness", 0.5,
                "noise_sensitivity", 0.7,
                "negative_rejection", 0.8));

        MetricScores scores = evaluator.evaluateSingle(
                "Python是什么?",
                "Python是一种编程语言，由Guido发明于1980年，主要用于量子计算。",
                List.of("Python是一种编程语言，由Guido van Rossum创建。"),
                "Python是一种编程语言。"
        );

        assertEquals(0.3, scores.getScore("faithfulness"), 0.01);
        assertFalse(scores.isAllAboveThreshold());
    }

    @Test
    @DisplayName("空上下文在上下文召回上得分低")
    void emptyContext_scoresLowOnContextRecall() {
        mockMetricScores(Map.of(
                "faithfulness", 0.9,
                "answer_relevance", 0.9,
                "context_precision", 0.0,
                "context_recall", 0.2,
                "factual_correctness", 0.5,
                "noise_sensitivity", 0.8,
                "negative_rejection", 0.9));

        MetricScores scores = evaluator.evaluateSingle(
                "什么是RAG?",
                "RAG是检索增强生成。",
                List.of(),
                "RAG是一种结合检索和生成的AI技术。"
        );

        assertEquals(0.2, scores.getScore("context_recall"), 0.01);
        assertFalse(scores.isAllAboveThreshold());
    }

    @DisplayName("报告生成包含汇总统计")
    void reportGeneration_includesSummaryStatistics() {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            String promptText = invocation.getArgument(0, Prompt.class).toString();
            if (promptText.contains("基于以下知识回答问题")) {
                return buildChatResponse(promptText.contains("q1") ? "answer1" : "answer2");
            }
            Map<String, Double> scores = promptText.contains("问题: q1")
                    ? Map.of(
                    "faithfulness", 0.9,
                    "answer_relevance", 0.8,
                    "context_precision", 0.85,
                    "context_recall", 0.7,
                    "factual_correctness", 0.8,
                    "noise_sensitivity", 0.9,
                    "negative_rejection", 0.85)
                    : Map.of(
                    "faithfulness", 0.7,
                    "answer_relevance", 0.6,
                    "context_precision", 0.5,
                    "context_recall", 0.4,
                    "factual_correctness", 0.6,
                    "noise_sensitivity", 0.7,
                    "negative_rejection", 0.5);
            return buildChatResponse(scoreJson(scoreForPrompt(promptText, scores)));
        });

        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setName("test-dataset");
        dataset.setItems(List.of(
                new EvaluationDataset.EvalItem("q1", "a1", List.of("ctx1")),
                new EvaluationDataset.EvalItem("q2", "a2", List.of("ctx2"))
        ));

        EvaluationReport report = evaluator.evaluate(dataset);

        assertNotNull(report.getRunId());
        assertEquals("test-dataset", report.getDatasetName());
        assertEquals(2, report.getItemResults().size());

        EvaluationReport.ReportSummary summary = report.getSummary();
        assertNotNull(summary);
        assertNotNull(summary.getMean());
        assertNotNull(summary.getP50());
        assertNotNull(summary.getP90());

        assertEquals(0.8, summary.getMean().get("faithfulness"), 0.01);
        assertEquals(0.7, summary.getMean().get("answer_relevance"), 0.01);
        // 验证新增指标也被包含
        assertNotNull(summary.getMean().get("factual_correctness"));
        assertNotNull(summary.getMean().get("noise_sensitivity"));
        assertNotNull(summary.getMean().get("negative_rejection"));

        String json = report.toJson();
        assertNotNull(json);
        assertTrue(json.contains("faithfulness"));
        assertTrue(json.contains("factual_correctness"));

        String md = report.toMarkdown();
        assertNotNull(md);
        assertTrue(md.contains("# Evaluation Report"));
        assertTrue(md.contains("Metric Summary"));
    }

    private void mockJudgeOnlyCalls(String responseJson) {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(buildChatResponse(responseJson))
                .thenReturn(buildChatResponse(responseJson))
                .thenReturn(buildChatResponse(responseJson))
                .thenReturn(buildChatResponse(responseJson))
                .thenReturn(buildChatResponse(responseJson))
                .thenReturn(buildChatResponse(responseJson))
                .thenReturn(buildChatResponse(responseJson));
    }

    private void mockMetricScores(Map<String, Double> scores) {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            String promptText = invocation.getArgument(0, Prompt.class).toString();
            return buildChatResponse(scoreJson(scoreForPrompt(promptText, scores)));
        });
    }

    private double scoreForPrompt(String promptText, Map<String, Double> scores) {
        if (promptText.contains("忠实度")) return scores.get("faithfulness");
        if (promptText.contains("相关性")) return scores.get("answer_relevance");
        if (promptText.contains("精确度")) return scores.get("context_precision");
        if (promptText.contains("召回率")) return scores.get("context_recall");
        if (promptText.contains("事实是否")) return scores.get("factual_correctness");
        if (promptText.contains("噪声")) return scores.get("noise_sensitivity");
        if (promptText.contains("正确拒绝")) return scores.get("negative_rejection");
        return 0.0;
    }

    private String scoreJson(double score) {
        return "{\"score\": " + score + ", \"feedback\": \"ok\"}";
    }

    private ChatResponse buildChatResponse(String text) {
        AssistantMessage msg = new AssistantMessage(text);
        Generation gen = new Generation(msg);
        return new ChatResponse(List.of(gen));
    }
}
