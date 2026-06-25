package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CragRetrievalEvaluatorTest {

    @Test
    void parsesRawJson() throws Exception {
        CragRetrievalEvaluator evaluator = createEvaluator();

        CragRetrievalEvaluation result = evaluator.parse(
                "{\"label\":\"CORRECT\",\"confidence\":0.91,\"reason\":\"matched\",\"rewrittenQuery\":\"q\"}",
                "fallback");

        assertEquals(CragRetrievalEvaluation.Label.CORRECT, result.getLabel());
        assertEquals(0.91, result.getConfidence());
        assertEquals("q", result.getRewrittenQuery());
    }

    @Test
    void parsesMarkdownFencedJson() throws Exception {
        CragRetrievalEvaluator evaluator = createEvaluator();

        CragRetrievalEvaluation result = evaluator.parse(
                "```json\n{\"label\":\"INCORRECT\",\"confidence\":0.88,\"reason\":\"unrelated\"}\n```",
                "fallback");

        assertEquals(CragRetrievalEvaluation.Label.INCORRECT, result.getLabel());
        assertEquals("fallback", result.getRewrittenQuery());
    }

    @Test
    void invalidJsonFallsBackToAmbiguous() throws Exception {
        CragRetrievalEvaluator evaluator = createEvaluator();

        CragRetrievalEvaluation result = evaluator.parse("not json", "fallback");

        assertEquals(CragRetrievalEvaluation.Label.AMBIGUOUS, result.getLabel());
        assertEquals("fallback", result.getRewrittenQuery());
    }

    private CragRetrievalEvaluator createEvaluator() throws Exception {
        Class<?> chatModelServiceType = Class.forName("tech.qiantong.qknow.ai.service.IChatModelService");
        return (CragRetrievalEvaluator) CragRetrievalEvaluator.class
                .getConstructor(chatModelServiceType, CragRetrievalEvaluator.CragConfig.class)
                .newInstance(null, new CragRetrievalEvaluator.CragConfig());
    }
}
