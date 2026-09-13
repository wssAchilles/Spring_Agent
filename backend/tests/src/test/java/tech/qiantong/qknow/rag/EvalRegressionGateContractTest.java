package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.service.eval.EvalRegressionGate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11: 难例评测防退化回归门禁契约测试
 */
class EvalRegressionGateContractTest {

    private EvalRegressionGate gate;

    @BeforeEach
    void setUp() {
        gate = new EvalRegressionGate();
    }

    @Test
    @DisplayName("契约验证：各项指标均达标且无明显退化时，门禁判定通过")
    void testGatePassesWhenMetricsHigh() {
        Map<String, Double> current = Map.of(
                "faithfulness", 0.92,
                "answer_relevance", 0.88,
                "context_recall", 0.85
        );
        Map<String, Double> baseline = Map.of(
                "faithfulness", 0.90,
                "answer_relevance", 0.87,
                "context_recall", 0.84
        );

        var decision = gate.evaluateGate(current, baseline);

        assertTrue(decision.isPassed(), "达标且优于基线应判定通过");
    }

    @Test
    @DisplayName("契约验证：某项指标低于绝对红线时，门禁判定阻断")
    void testGateBlocksWhenMetricBelowThreshold() {
        Map<String, Double> current = Map.of(
                "faithfulness", 0.75, // 低于 0.85 红线
                "answer_relevance", 0.88,
                "context_recall", 0.85
        );

        var decision = gate.evaluateGate(current, Map.of());

        assertFalse(decision.isPassed(), "低于绝对红线必须被阻断");
        assertTrue(decision.getReason().contains("Faithfulness"), "原因中必须包含具体未达标指标");
    }

    @Test
    @DisplayName("契约验证：指标相比 Baseline 退化超过 2% 时，门禁判定阻断")
    void testGateBlocksOnRegression() {
        Map<String, Double> current = Map.of(
                "faithfulness", 0.86,
                "answer_relevance", 0.82,
                "context_recall", 0.81 // 相比基线的 0.85 跌落了 0.04 (4% > 2%)
        );
        Map<String, Double> baseline = Map.of(
                "faithfulness", 0.88,
                "answer_relevance", 0.83,
                "context_recall", 0.85
        );

        var decision = gate.evaluateGate(current, baseline);

        assertFalse(decision.isPassed(), "退化超过 2% 必须触发阻断");
        assertTrue(decision.getReason().contains("退化"), "原因中必须包含退化信息");
    }
}
