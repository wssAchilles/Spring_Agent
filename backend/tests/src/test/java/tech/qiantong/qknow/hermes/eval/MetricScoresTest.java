package tech.qiantong.qknow.hermes.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetricScores 测试")
class MetricScoresTest {

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
