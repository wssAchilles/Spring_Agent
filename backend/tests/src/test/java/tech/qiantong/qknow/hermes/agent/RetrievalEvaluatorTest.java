package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetrievalEvaluatorTest {

    @Test
    void parsesRawJson() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse(
                "{\"label\":\"CORRECT\",\"confidence\":0.91,\"reason\":\"matched\",\"rewrittenQuery\":\"q\"}",
                "fallback");

        assertEquals(RetrievalEvaluation.Label.CORRECT, result.getLabel());
        assertEquals(0.91, result.getConfidence());
        assertEquals("q", result.getRewrittenQuery());
    }

    @Test
    void parsesMarkdownFencedJson() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse(
                "```json\n{\"label\":\"INCORRECT\",\"confidence\":0.88,\"reason\":\"unrelated\"}\n```",
                "fallback");

        assertEquals(RetrievalEvaluation.Label.INCORRECT, result.getLabel());
        assertEquals("fallback", result.getRewrittenQuery());
    }

    @Test
    void invalidJsonFallsBackToAmbiguous() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse("not json", "fallback");

        assertEquals(RetrievalEvaluation.Label.AMBIGUOUS, result.getLabel());
        assertEquals("fallback", result.getRewrittenQuery());
    }
}
