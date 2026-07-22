package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.eval.EvaluationError;
import tech.qiantong.qknow.hermes.eval.MetricScores;
import tech.qiantong.qknow.hermes.observability.LangFuseTracingService;

import static org.mockito.Mockito.*;

class AgentOrchestratorRagasEvaluationTest {

    @Test
    void recordValidRagasScores_writesOnlyValidMetrics() {
        LangFuseTracingService langFuse = mock(LangFuseTracingService.class);
        MetricScores scores = new MetricScores();
        scores.setFaithfulness(0.9);
        scores.setAnswerRelevance(0.8);
        scores.markInvalid("answer_relevance", EvaluationError.PARSE_FAILED);
        scores.setContextPrecision(0.7);
        scores.markInvalid("context_precision", EvaluationError.MODEL_CALL_FAILED);
        scores.setFactualCorrectness(0.6);

        AgentOrchestrator.recordValidRagasScores(langFuse, "trace-1", scores);

        verify(langFuse).recordScore("trace-1", "faithfulness", 0.9);
        verify(langFuse).recordScore("trace-1", "factual_correctness", 0.6);
        verify(langFuse, never()).recordScore(eq("trace-1"), eq("answer_relevance"), anyDouble());
        verify(langFuse, never()).recordScore(eq("trace-1"), eq("context_precision"), anyDouble());
        verifyNoMoreInteractions(langFuse);
    }
}
