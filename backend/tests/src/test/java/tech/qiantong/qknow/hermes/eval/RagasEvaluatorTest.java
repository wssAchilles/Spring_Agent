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
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        org.mockito.Mockito.lenient().when(config.getPlatform()).thenReturn("deepseek");
        org.mockito.Mockito.lenient().when(config.getBaseUrl()).thenReturn(null);
        org.mockito.Mockito.lenient().when(config.getApiKey()).thenReturn(null);
        org.mockito.Mockito.lenient().when(config.getModelName()).thenReturn("deepseek-chat");
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

    @Test
    @DisplayName("模型调用失败标记 INVALID 且不伪装为有效零分")
    void modelFailure_marksMetricsInvalid() {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("secret-answer-and-key"));

        MetricScores scores = evaluator.evaluateSingle("q", "a", List.of("ctx"), "expected");

        assertEquals(EvaluationStatus.INVALID, scores.getStatus("faithfulness"));
        assertEquals("MODEL_CALL_FAILED", scores.getErrorCode("faithfulness"));
        assertEquals("Metric model call failed", scores.getReason("faithfulness"));
        assertFalse(scores.getReason("faithfulness").contains("secret"));
        assertFalse(scores.isPassed());
    }

    @Test
    @DisplayName("无效模型响应标记解析失败")
    void parseFailure_marksMetricsInvalid() {
        mockJudgeOnlyCalls("not-json");

        MetricScores scores = evaluator.evaluateSingle("q", "a", List.of("ctx"), "expected");

        assertEquals(EvaluationStatus.INVALID, scores.getStatus("faithfulness"));
        assertEquals("PARSE_FAILED", scores.getErrorCode("faithfulness"));
        assertEquals("Metric response could not be parsed", scores.getReason("faithfulness"));
    }

    @Test
    @DisplayName("评估超时使用独立固定错误码")
    void timeout_marksMetricInvalid() {
        MetricScores scores = new MetricScores();

        evaluator.collectMetric(scores, "faithfulness", new CompletableFuture<>(), 1, TimeUnit.MILLISECONDS);

        assertEquals(EvaluationStatus.INVALID, scores.getStatus("faithfulness"));
        assertEquals("EVAL_TIMEOUT", scores.getErrorCode("faithfulness"));
        assertEquals("Metric evaluation timed out", scores.getReason("faithfulness"));
    }

    @Test
    @DisplayName("多个未完成指标共享一次总超时预算")
    void collectMetrics_usesSingleTotalTimeoutBudget() {
        MetricScores scores = new MetricScores();
        Map<String, CompletableFuture<Double>> futures = new LinkedHashMap<>();
        futures.put("faithfulness", new CompletableFuture<>());
        futures.put("answer_relevance", new CompletableFuture<>());
        futures.put("context_precision", new CompletableFuture<>());
        long started = System.nanoTime();

        evaluator.collectMetrics(scores, futures, 30, TimeUnit.MILLISECONDS);

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(elapsedMillis < 200, "timeout budget must not accumulate per metric");
        assertEquals("EVAL_TIMEOUT", scores.getErrorCode("faithfulness"));
        assertEquals("EVAL_TIMEOUT", scores.getErrorCode("answer_relevance"));
        assertEquals("EVAL_TIMEOUT", scores.getErrorCode("context_precision"));
        assertTrue(futures.values().stream().allMatch(CompletableFuture::isCancelled));
    }

    @Test
    @DisplayName("超时 cancel(true) 必须中断实际运行的 FutureTask")
    void collectMetric_timeoutInterruptsRunningFutureTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            Future<Double> future = executor.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                    return 1.0;
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw e;
                }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            MetricScores scores = new MetricScores();

            evaluator.collectMetric(scores, "faithfulness", future, 20, TimeUnit.MILLISECONDS);

            assertEquals("EVAL_TIMEOUT", scores.getErrorCode("faithfulness"));
            assertTrue(future.isCancelled());
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("调用线程中断时恢复标志并标记所有 pending 指标")
    void collectMetrics_interruptionMarksAllPendingAndRestoresFlag() {
        MetricScores scores = new MetricScores();
        Map<String, CompletableFuture<Double>> futures = new LinkedHashMap<>();
        futures.put("faithfulness", new CompletableFuture<>());
        futures.put("answer_relevance", new CompletableFuture<>());
        Thread.currentThread().interrupt();

        try {
            evaluator.collectMetrics(scores, futures, 1, TimeUnit.SECONDS);

            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals("EVALUATION_INTERRUPTED", scores.getErrorCode("faithfulness"));
            assertEquals("EVALUATION_INTERRUPTED", scores.getErrorCode("answer_relevance"));
            assertTrue(futures.values().stream().allMatch(CompletableFuture::isCancelled));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("参考答案只进入三个允许的 judge prompt")
    void expectedAnswer_onlyEntersAllowedPrompts() {
        String canary = "CANARY-REF-20260715";
        List<String> prompts = Collections.synchronizedList(new ArrayList<>());
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0, Prompt.class).toString());
            return buildChatResponse(scoreJson(0.9));
        });

        evaluator.evaluateSingle("q", "a", List.of("ctx"), canary);

        List<String> canaryPrompts = prompts.stream().filter(prompt -> prompt.contains(canary)).toList();
        assertEquals(3, canaryPrompts.size());
        assertTrue(canaryPrompts.stream().allMatch(prompt -> prompt.contains("精确度")
                || prompt.contains("召回率") || prompt.contains("事实是否")));
        assertTrue(prompts.stream().filter(prompt -> prompt.contains("忠实度"))
                .noneMatch(prompt -> prompt.contains(canary)));
        assertTrue(prompts.stream().filter(prompt -> prompt.contains("相关性"))
                .noneMatch(prompt -> prompt.contains(canary)));
        assertTrue(prompts.stream().filter(prompt -> prompt.contains("噪声"))
                .noneMatch(prompt -> prompt.contains(canary)));
        assertTrue(prompts.stream().filter(prompt -> prompt.contains("正确拒绝"))
                .noneMatch(prompt -> prompt.contains(canary)));
    }

    @Test
    @DisplayName("报告将非 VALID 指标的 value 显式序列化为 null")
    void reportJson_serializesInvalidMetricValueAsNull() {
        MetricScores scores = new MetricScores();
        scores.markInvalid("faithfulness", EvaluationError.PARSE_FAILED);
        EvaluationReport.ItemResult item = new EvaluationReport.ItemResult();
        item.setScores(scores.toReportMetrics());
        EvaluationReport report = new EvaluationReport();
        report.setItemResults(List.of(item));

        JSONObject metric = JSON.parseObject(report.toJson())
                .getJSONArray("itemResults").getJSONObject(0)
                .getJSONObject("scores").getJSONObject("faithfulness");

        assertEquals("INVALID", metric.getString("status"));
        assertTrue(metric.containsKey("value"));
        assertNull(metric.get("value"));
        assertEquals("PARSE_FAILED", metric.getString("errorCode"));
        assertEquals("Metric response could not be parsed", metric.getString("reason"));
    }

    @Test
    @DisplayName("历史数值 scores JSON 反序列化为 VALID MetricResult")
    void reportJson_deserializesLegacyNumericScores() {
        String json = "{\"itemResults\":[{\"scores\":{\"faithfulness\":0.9}}]}";

        EvaluationReport report = JSON.parseObject(json, EvaluationReport.class);
        EvaluationReport.MetricResult metric = report.getItemResults().get(0)
                .getScores().get("faithfulness");

        assertEquals(EvaluationStatus.VALID, metric.status());
        assertEquals(0.9, metric.value(), 0.001);
        assertNull(metric.errorCode());
        assertNull(metric.reason());
    }

    @Test
    @DisplayName("新状态对象 scores JSON 反序列化保持语义")
    void reportJson_deserializesStructuredScores() {
        String json = "{\"itemResults\":[{\"scores\":{\"faithfulness\":"
                + "{\"status\":\"INVALID\",\"value\":null,"
                + "\"errorCode\":\"PARSE_FAILED\","
                + "\"reason\":\"Metric response could not be parsed\"}}}]}";

        EvaluationReport report = JSON.parseObject(json, EvaluationReport.class);
        EvaluationReport.MetricResult metric = report.getItemResults().get(0)
                .getScores().get("faithfulness");

        assertEquals(EvaluationStatus.INVALID, metric.status());
        assertNull(metric.value());
        assertEquals("PARSE_FAILED", metric.errorCode());
    }

    @Test
    @DisplayName("MetricResult 直接构造也强制状态不变量")
    void metricResult_directConstructionEnforcesInvariants() {
        EvaluationReport.MetricResult invalid = new EvaluationReport.MetricResult(
                EvaluationStatus.INVALID, 0.8, "FAILED", "fixed reason");
        EvaluationReport.MetricResult notEvaluated = new EvaluationReport.MetricResult(
                EvaluationStatus.NOT_EVALUATED, 0.7, "IGNORED", "ignored reason");

        assertNull(invalid.value());
        assertNull(notEvaluated.value());
        assertNull(notEvaluated.errorCode());
        assertNull(notEvaluated.reason());
        assertThrows(IllegalArgumentException.class, () -> new EvaluationReport.MetricResult(
                null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new EvaluationReport.MetricResult(
                EvaluationStatus.VALID, Double.NaN, null, null));
        assertThrows(IllegalArgumentException.class, () -> new EvaluationReport.MetricResult(
                EvaluationStatus.VALID, 0.8, "ERROR", "not allowed"));
        assertThrows(IllegalArgumentException.class, () -> new EvaluationReport.MetricResult(
                EvaluationStatus.INVALID, null, "", ""));
    }

    @Test
    @DisplayName("非 VALID 直接构造时即使传值 JSON 也必须输出 null")
    void metricResult_nonValidConstructionSerializesValueAsNull() {
        EvaluationReport.ItemResult item = new EvaluationReport.ItemResult();
        item.setScores(Map.of("faithfulness", new EvaluationReport.MetricResult(
                EvaluationStatus.INVALID, 0.9, "FAILED", "fixed reason")));
        EvaluationReport report = new EvaluationReport();
        report.setItemResults(List.of(item));

        JSONObject metric = JSON.parseObject(report.toJson())
                .getJSONArray("itemResults").getJSONObject(0)
                .getJSONObject("scores").getJSONObject("faithfulness");

        assertTrue(metric.containsKey("value"));
        assertNull(metric.get("value"));
    }

    @Test
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

    @Test
    @DisplayName("生成失败不会继续调用 judge 且所有指标显式 INVALID")
    void generationFailure_invalidatesItemWithoutJudgeCalls() {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("offline"));
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setName("generation-failure");
        dataset.setItems(List.of(new EvaluationDataset.EvalItem("q", "reference", List.of("ctx"))));

        EvaluationReport report = evaluator.evaluate(dataset);

        EvaluationReport.ItemResult item = report.getItemResults().get(0);
        assertEquals("[GENERATION_ERROR]", item.getAnswer());
        assertTrue(item.getScores().values().stream()
                .allMatch(metric -> metric.status() == EvaluationStatus.INVALID
                        && "GENERATION_FAILED".equals(metric.errorCode())));
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.times(1)).call(any(Prompt.class));
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
