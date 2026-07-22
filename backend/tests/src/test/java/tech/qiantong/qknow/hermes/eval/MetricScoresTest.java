package tech.qiantong.qknow.hermes.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.DoubleConsumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetricScores 测试")
class MetricScoresTest {

    @Test
    @DisplayName("新建指标默认为未评估")
    void newScores_areNotEvaluated() {
        MetricScores scores = new MetricScores();

        assertEquals(EvaluationStatus.NOT_EVALUATED, scores.getStatus("faithfulness"));
        assertNull(scores.getErrorCode("faithfulness"));
        assertNull(scores.getReason("faithfulness"));
        assertFalse(scores.isAboveThreshold(0.0));
    }

    @Test
    @DisplayName("通过现有 setter 设置分数后标记为有效")
    void setter_marksMetricValid() {
        MetricScores scores = new MetricScores();

        scores.setFaithfulness(0.91);

        assertEquals(0.91, scores.getFaithfulness());
        assertEquals(EvaluationStatus.VALID, scores.getStatus("faithfulness"));
        assertTrue(scores.isValid("faithfulness"));
    }

    @Test
    @DisplayName("无效核心指标不得通过门禁")
    void invalidCoreMetric_failsGateWithFixedError() {
        MetricScores scores = createAllAbove(0.9);

        scores.markInvalid("faithfulness", EvaluationError.MODEL_CALL_FAILED);

        assertEquals(EvaluationStatus.INVALID, scores.getStatus("faithfulness"));
        assertEquals("MODEL_CALL_FAILED", scores.getErrorCode("faithfulness"));
        assertEquals("Metric model call failed", scores.getReason("faithfulness"));
        assertEquals(0.9, scores.getFaithfulness());
        assertFalse(scores.isAboveThreshold(0.85));
    }

    @Test
    @DisplayName("所有指标 setter 拒绝非有限数和越界值")
    void metricSetters_rejectNonFiniteAndOutOfRangeValues() {
        MetricScores scores = new MetricScores();
        List<DoubleConsumer> setters = List.of(
                scores::setFaithfulness,
                scores::setAnswerRelevance,
                scores::setContextPrecision,
                scores::setContextRecall,
                scores::setFactualCorrectness,
                scores::setNoiseSensitivity,
                scores::setNegativeRejection);
        double[] invalidValues = {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -0.001, 1.001};

        for (DoubleConsumer setter : setters) {
            for (double value : invalidValues) {
                assertThrows(IllegalArgumentException.class, () -> setter.accept(value));
            }
        }
    }

    @Test
    @DisplayName("markInvalid 拒绝未知指标和空错误")
    void markInvalid_rejectsUnknownMetricAndNullError() {
        MetricScores scores = new MetricScores();

        assertThrows(IllegalArgumentException.class,
                () -> scores.markInvalid("unknown_metric", EvaluationError.PARSE_FAILED));
        assertThrows(IllegalArgumentException.class,
                () -> scores.markInvalid("faithfulness", null));
    }

    @Test
    @DisplayName("内部状态映射不得暴露可变 getter")
    void internalStateMaps_areNotPubliclyExposed() {
        assertThrows(NoSuchMethodException.class,
                () -> MetricScores.class.getMethod("getStatuses"));
        assertThrows(NoSuchMethodException.class,
                () -> MetricScores.class.getMethod("getErrors"));
    }

    private MetricScores createAllAbove(double value) {
        MetricScores scores = new MetricScores();
        scores.setFaithfulness(value);
        scores.setAnswerRelevance(value);
        scores.setContextPrecision(value);
        scores.setContextRecall(value);
        scores.setFactualCorrectness(value);
        scores.setNoiseSensitivity(value);
        scores.setNegativeRejection(value);
        return scores;
    }

    // ========== isAboveThreshold ==========

    @Test
    @DisplayName("所有指标高于阈值返回 true")
    void isAboveThreshold_allAbove_returnsTrue() {
        MetricScores scores = createAllAbove(0.9);

        assertTrue(scores.isAboveThreshold(0.85));
    }

    @Test
    @DisplayName("任一核心指标低于阈值返回 false")
    void isAboveThreshold_oneBelowThreshold_returnsFalse() {
        MetricScores scores = createAllAbove(0.9);
        scores.setFaithfulness(0.5);

        assertFalse(scores.isAboveThreshold(0.85));
    }

    // ========== noiseSensitivity / negativeRejection 不影响门控 ==========

    @Test
    @DisplayName("noiseSensitivity 低于阈值不影响门控结果")
    void isAboveThreshold_lowNoiseSensitivity_doesNotAffectGate() {
        MetricScores scores = createAllAbove(0.9);
        scores.setNoiseSensitivity(0.1);

        assertTrue(scores.isAboveThreshold(0.85));
    }

    @Test
    @DisplayName("negativeRejection 低于阈值不影响门控结果")
    void isAboveThreshold_lowNegativeRejection_doesNotAffectGate() {
        MetricScores scores = createAllAbove(0.9);
        scores.setNegativeRejection(0.1);

        assertTrue(scores.isAboveThreshold(0.85));
    }

    // ========== getScore ==========

    @Test
    @DisplayName("getScore 按名称返回正确值")
    void getScore_byName_returnsCorrectValue() {
        MetricScores scores = new MetricScores();
        scores.setFaithfulness(0.95);
        scores.setAnswerRelevance(0.88);
        scores.setContextPrecision(0.77);
        scores.setContextRecall(0.66);
        scores.setFactualCorrectness(0.55);
        scores.setNoiseSensitivity(0.44);
        scores.setNegativeRejection(0.33);

        assertEquals(0.95, scores.getScore("faithfulness"));
        assertEquals(0.88, scores.getScore("answer_relevance"));
        assertEquals(0.77, scores.getScore("context_precision"));
        assertEquals(0.66, scores.getScore("context_recall"));
        assertEquals(0.55, scores.getScore("factual_correctness"));
        assertEquals(0.44, scores.getScore("noise_sensitivity"));
        assertEquals(0.33, scores.getScore("negative_rejection"));
    }

    @Test
    @DisplayName("getScore 未知指标返回 0.0")
    void getScore_unknownMetric_returnsZero() {
        MetricScores scores = createAllAbove(0.9);

        assertEquals(0.0, scores.getScore("unknown_metric"));
    }

    // ========== DEFAULT_GATE_THRESHOLD ==========

    @Test
    @DisplayName("DEFAULT_GATE_THRESHOLD 为 0.85")
    void defaultGateThreshold_is085() {
        assertEquals(0.85, MetricScores.DEFAULT_GATE_THRESHOLD);
    }
}
