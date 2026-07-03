package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;

import static org.junit.jupiter.api.Assertions.*;

class CragRetrievalEvaluatorTest {

    private CragRetrievalEvaluator.CragConfig config;

    @BeforeEach
    void setUp() {
        config = new CragRetrievalEvaluator.CragConfig();
        config.setEnabled(true);
    }

    @Test
    @DisplayName("禁用时返回CORRECT")
    void evaluate_disabled_returnsCorrect() {
        config.setEnabled(false);
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        CragRetrievalEvaluation result = evaluator.evaluate("test query", RagResult.builder().build());

        assertEquals(CragRetrievalEvaluation.Label.CORRECT, result.getLabel());
        assertEquals(1.0, result.getConfidence());
    }

    @Test
    @DisplayName("空上下文返回INCORRECT且confidence=1.0")
    void evaluate_nullContext_returnsIncorrect() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        CragRetrievalEvaluation result = evaluator.evaluate("test query", RagResult.builder().context(null).build());

        assertEquals(CragRetrievalEvaluation.Label.INCORRECT, result.getLabel());
        assertEquals(1.0, result.getConfidence());
        assertEquals("test query", result.getRewrittenQuery());
    }

    @Test
    @DisplayName("空白上下文返回INCORRECT")
    void evaluate_blankContext_returnsIncorrect() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        CragRetrievalEvaluation result = evaluator.evaluate("test query", RagResult.builder().context("  ").build());

        assertEquals(CragRetrievalEvaluation.Label.INCORRECT, result.getLabel());
    }

    @Test
    @DisplayName("null RagResult返回INCORRECT")
    void evaluate_nullRagResult_returnsIncorrect() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        CragRetrievalEvaluation result = evaluator.evaluate("test query", null);

        assertEquals(CragRetrievalEvaluation.Label.INCORRECT, result.getLabel());
        assertEquals(1.0, result.getConfidence());
    }

    @Test
    @DisplayName("解析JSON响应 - label/confidence/reason/rewrittenQuery")
    void parse_validJson_parsesAllFields() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        String json = "{\"label\":\"CORRECT\",\"confidence\":0.95,\"reason\":\"context is relevant\",\"rewrittenQuery\":\"improved query\"}";
        CragRetrievalEvaluation result = evaluator.parse(json, "fallback");

        assertEquals(CragRetrievalEvaluation.Label.CORRECT, result.getLabel());
        assertEquals(0.95, result.getConfidence());
        assertEquals("context is relevant", result.getReason());
        assertEquals("improved query", result.getRewrittenQuery());
    }

    @Test
    @DisplayName("解析带markdown fence的响应")
    void parse_markdownFence_parsesSuccessfully() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        String fenced = "```json\n{\"label\":\"INCORRECT\",\"confidence\":0.8,\"reason\":\"not found\",\"rewrittenQuery\":\"rewrite\"}\n```";
        CragRetrievalEvaluation result = evaluator.parse(fenced, "fallback");

        assertEquals(CragRetrievalEvaluation.Label.INCORRECT, result.getLabel());
        assertEquals(0.8, result.getConfidence());
    }

    @Test
    @DisplayName("解析失败返回AMBIGUOUS")
    void parse_invalidJson_returnsAmbiguous() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        CragRetrievalEvaluation result = evaluator.parse("not json at all", "fallback");

        assertEquals(CragRetrievalEvaluation.Label.AMBIGUOUS, result.getLabel());
        assertEquals(0.0, result.getConfidence());
        assertEquals("fallback", result.getRewrittenQuery());
    }

    @Test
    @DisplayName("解析空响应返回AMBIGUOUS")
    void parse_emptyResponse_returnsAmbiguous() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        CragRetrievalEvaluation result = evaluator.parse("", "fallback");

        assertEquals(CragRetrievalEvaluation.Label.AMBIGUOUS, result.getLabel());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    @DisplayName("缺少rewrittenQuery时使用fallback")
    void parse_missingRewrittenQuery_usesFallback() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        String json = "{\"label\":\"CORRECT\",\"confidence\":0.9,\"reason\":\"ok\"}";
        CragRetrievalEvaluation result = evaluator.parse(json, "myFallback");

        assertEquals("myFallback", result.getRewrittenQuery());
    }

    @Test
    @DisplayName("缺少label时默认AMBIGUOUS")
    void parse_missingLabel_defaultsAmbiguous() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, config);

        String json = "{\"confidence\":0.5,\"reason\":\"no label\"}";
        CragRetrievalEvaluation result = evaluator.parse(json, "fallback");

        assertEquals(CragRetrievalEvaluation.Label.AMBIGUOUS, result.getLabel());
    }
}
