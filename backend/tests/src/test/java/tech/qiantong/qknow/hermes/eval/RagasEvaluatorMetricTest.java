package tech.qiantong.qknow.hermes.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagasEvaluatorMetricTest {

    @Mock
    private ChatModelFactory chatModelFactory;
    @Mock
    private RagasEvalConfig config;

    private RagasEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RagasEvaluator(chatModelFactory, config);
    }

    @Nested
    @DisplayName("parseScore 测试")
    class ParseScoreTests {

        @Test
        @DisplayName("正常 JSON 解析")
        void parseScore_normalJson_parsesCorrectly() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("parseScore", String.class);
            method.setAccessible(true);

            double score = (double) method.invoke(evaluator, "{\"score\": 0.85, \"feedback\": \"good\"}");
            assertEquals(0.85, score, 0.001);
        }

        @Test
        @DisplayName("带 markdown fence 的响应")
        void parseScore_markdownFence_parsesCorrectly() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("parseScore", String.class);
            method.setAccessible(true);

            String response = "```json\n{\"score\": 0.72, \"feedback\": \"ok\"}\n```";
            double score = (double) method.invoke(evaluator, response);
            assertEquals(0.72, score, 0.001);
        }

        @Test
        @DisplayName("无效 JSON 返回 0.0")
        void parseScore_invalidJson_returnsZero() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("parseScore", String.class);
            method.setAccessible(true);

            double score = (double) method.invoke(evaluator, "this is not json");
            assertEquals(0.0, score, 0.001);
        }

        @Test
        @DisplayName("缺少 score 字段返回 0.0")
        void parseScore_missingScoreField_returnsZero() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("parseScore", String.class);
            method.setAccessible(true);

            double score = (double) method.invoke(evaluator, "{\"feedback\": \"no score\"}");
            assertEquals(0.0, score, 0.001);
        }
    }

    @Nested
    @DisplayName("percentile 测试")
    class PercentileTests {

        @Test
        @DisplayName("空列表返回 0.0")
        void percentile_emptyList_returnsZero() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("percentile", List.class, int.class);
            method.setAccessible(true);

            double result = (double) method.invoke(evaluator, new ArrayList<>(), 50);
            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("单元素返回该元素")
        void percentile_singleElement_returnsThatElement() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("percentile", List.class, int.class);
            method.setAccessible(true);

            double result = (double) method.invoke(evaluator, List.of(0.8), 50);
            assertEquals(0.8, result, 0.001);
        }

        @Test
        @DisplayName("多元素 p50 计算正确")
        void percentile_multipleElements_p50Correct() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("percentile", List.class, int.class);
            method.setAccessible(true);

            List<Double> sorted = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
            double p50 = (double) method.invoke(evaluator, sorted, 50);
            assertEquals(0.3, p50, 0.001);
        }

        @Test
        @DisplayName("多元素 p90 计算正确")
        void percentile_multipleElements_p90Correct() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("percentile", List.class, int.class);
            method.setAccessible(true);

            List<Double> sorted = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
            double p90 = (double) method.invoke(evaluator, sorted, 90);
            assertEquals(0.46, p90, 0.01);
        }
    }

    @Nested
    @DisplayName("computeSummary 测试")
    class ComputeSummaryTests {

        @Test
        @DisplayName("统计正确 - mean/p50/p90")
        void computeSummary_statisticsCorrect() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("computeSummary", List.class);
            method.setAccessible(true);

            MetricScores scores1 = new MetricScores();
            scores1.setFaithfulness(0.8);
            scores1.setAnswerRelevance(0.9);
            scores1.setContextPrecision(0.7);
            scores1.setContextRecall(0.6);
            scores1.setFactualCorrectness(0.85);
            scores1.setNoiseSensitivity(0.75);
            scores1.setNegativeRejection(0.95);

            MetricScores scores2 = new MetricScores();
            scores2.setFaithfulness(0.6);
            scores2.setAnswerRelevance(0.7);
            scores2.setContextPrecision(0.5);
            scores2.setContextRecall(0.4);
            scores2.setFactualCorrectness(0.65);
            scores2.setNoiseSensitivity(0.55);
            scores2.setNegativeRejection(0.75);

            @SuppressWarnings("unchecked")
            EvaluationReport.ReportSummary summary = (EvaluationReport.ReportSummary)
                    method.invoke(evaluator, List.of(scores1, scores2));

            assertNotNull(summary);
            assertEquals(0.7, summary.getMean().get("faithfulness"), 0.001);
            assertEquals(0.8, summary.getMean().get("answer_relevance"), 0.001);
            assertNotNull(summary.getP50());
            assertNotNull(summary.getP90());
            assertEquals(0.7, summary.getP50().get("faithfulness"), 0.001);
        }
    }

    @Nested
    @DisplayName("decoratePrompt 测试")
    class DecoratePromptTests {

        @Test
        @DisplayName("包含 promptVersion")
        void decoratePrompt_includesPromptVersion() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("decoratePrompt", String.class);
            method.setAccessible(true);

            when(config.getPromptVersion()).thenReturn("v2.0");
            when(config.getPromptExamples()).thenReturn(null);

            String result = (String) method.invoke(evaluator, "base prompt");

            assertTrue(result.contains("base prompt"));
            assertTrue(result.contains("v2.0"));
        }

        @Test
        @DisplayName("包含 promptExamples")
        void decoratePrompt_includesPromptExamples() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("decoratePrompt", String.class);
            method.setAccessible(true);

            when(config.getPromptVersion()).thenReturn(null);
            when(config.getPromptExamples()).thenReturn(List.of("示例1", "示例2"));

            String result = (String) method.invoke(evaluator, "base prompt");

            assertTrue(result.contains("示例1"));
            assertTrue(result.contains("示例2"));
            assertTrue(result.contains("Calibration examples"));
        }

        @Test
        @DisplayName("null prompt 安全处理")
        void decoratePrompt_nullPrompt_handlesSafely() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("decoratePrompt", String.class);
            method.setAccessible(true);

            when(config.getPromptVersion()).thenReturn("v1");
            when(config.getPromptExamples()).thenReturn(null);

            String result = (String) method.invoke(evaluator, (String) null);

            assertNotNull(result);
            assertTrue(result.contains("v1"));
        }
    }

    @Nested
    @DisplayName("resolveMetricPrompts 测试")
    class ResolveMetricPromptsTests {

        @Test
        @DisplayName("默认 7 个指标")
        void resolveMetricPrompts_default7Metrics() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("resolveMetricPrompts");
            method.setAccessible(true);

            when(config.getMetricPrompts()).thenReturn(null);

            @SuppressWarnings("unchecked")
            Map<String, String> prompts = (Map<String, String>) method.invoke(evaluator);

            assertEquals(7, prompts.size());
            assertTrue(prompts.containsKey("faithfulness"));
            assertTrue(prompts.containsKey("answer_relevance"));
            assertTrue(prompts.containsKey("context_precision"));
            assertTrue(prompts.containsKey("context_recall"));
            assertTrue(prompts.containsKey("factual_correctness"));
            assertTrue(prompts.containsKey("noise_sensitivity"));
            assertTrue(prompts.containsKey("negative_rejection"));
        }

        @Test
        @DisplayName("自定义覆盖")
        void resolveMetricPrompts_customOverride() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("resolveMetricPrompts");
            method.setAccessible(true);

            Map<String, String> custom = new LinkedHashMap<>();
            custom.put("faithfulness", "自定义忠实度提示");
            when(config.getMetricPrompts()).thenReturn(custom);

            @SuppressWarnings("unchecked")
            Map<String, String> prompts = (Map<String, String>) method.invoke(evaluator);

            assertEquals(7, prompts.size());
            assertEquals("自定义忠实度提示", prompts.get("faithfulness"));
            // 其他指标保持默认
            assertNotNull(prompts.get("answer_relevance"));
        }

        @Test
        @DisplayName("自定义覆盖不改变不存在的指标")
        void resolveMetricPrompts_unknownMetric_ignored() throws Exception {
            Method method = RagasEvaluator.class.getDeclaredMethod("resolveMetricPrompts");
            method.setAccessible(true);

            Map<String, String> custom = new LinkedHashMap<>();
            custom.put("unknown_metric", "不应生效");
            when(config.getMetricPrompts()).thenReturn(custom);

            @SuppressWarnings("unchecked")
            Map<String, String> prompts = (Map<String, String>) method.invoke(evaluator);

            assertEquals(7, prompts.size());
            assertFalse(prompts.containsKey("unknown_metric"));
        }
    }
}
