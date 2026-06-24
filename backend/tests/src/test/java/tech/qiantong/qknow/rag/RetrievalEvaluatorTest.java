package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.RetrievalEvaluation;
import tech.qiantong.qknow.hermes.agent.RetrievalEvaluator;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetrievalEvaluator 检索评估测试")
class RetrievalEvaluatorTest {

    @Test
    @DisplayName("CORRECT 标签：相关上下文应返回 CORRECT")
    void parse_correctLabel_returnsCorrect() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse(
                "{\"label\":\"CORRECT\",\"confidence\":0.95,\"reason\":\"highly relevant\",\"rewrittenQuery\":\"optimized query\"}",
                "original query");

        assertEquals(RetrievalEvaluation.Label.CORRECT, result.getLabel());
        assertEquals(0.95, result.getConfidence());
        assertEquals("optimized query", result.getRewrittenQuery());
    }

    @Test
    @DisplayName("INCORRECT 标签：不相关上下文应返回 INCORRECT")
    void parse_incorrectLabel_returnsIncorrect() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse(
                "{\"label\":\"INCORRECT\",\"confidence\":0.92,\"reason\":\"completely unrelated\"}",
                "fallback query");

        assertEquals(RetrievalEvaluation.Label.INCORRECT, result.getLabel());
        assertEquals(0.92, result.getConfidence());
        assertEquals("fallback query", result.getRewrittenQuery());
    }

    @Test
    @DisplayName("AMBIGUOUS 标签：部分相关上下文应返回 AMBIGUOUS")
    void parse_ambiguousLabel_returnsAmbiguous() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse(
                "{\"label\":\"AMBIGUOUS\",\"confidence\":0.6,\"reason\":\"partially relevant\"}",
                "query");

        assertEquals(RetrievalEvaluation.Label.AMBIGUOUS, result.getLabel());
        assertEquals(0.6, result.getConfidence());
    }

    @Test
    @DisplayName("纯 JSON 响应应正确解析")
    void parse_pureJson_parsesCorrectly() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse(
                "{\"label\":\"CORRECT\",\"confidence\":0.91,\"reason\":\"matched\",\"rewrittenQuery\":\"q\"}",
                "fallback");

        assertEquals(RetrievalEvaluation.Label.CORRECT, result.getLabel());
        assertEquals(0.91, result.getConfidence());
        assertEquals("q", result.getRewrittenQuery());
    }

    @Test
    @DisplayName("Markdown 围栏 JSON 应正确解析")
    void parse_markdownFencedJson_parsesCorrectly() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse(
                "```json\n{\"label\":\"INCORRECT\",\"confidence\":0.88,\"reason\":\"unrelated\"}\n```",
                "fallback");

        assertEquals(RetrievalEvaluation.Label.INCORRECT, result.getLabel());
        assertEquals(0.88, result.getConfidence());
        assertEquals("fallback", result.getRewrittenQuery());
    }

    @Test
    @DisplayName("非法 JSON 应回退到 AMBIGUOUS")
    void parse_invalidJson_fallsBackToAmbiguous() {
        RetrievalEvaluator evaluator = new RetrievalEvaluator(null);
        RetrievalEvaluation result = evaluator.parse("not json at all", "fallback");

        assertEquals(RetrievalEvaluation.Label.AMBIGUOUS, result.getLabel());
        assertEquals(0.0, result.getConfidence());
        assertEquals("fallback", result.getRewrittenQuery());
    }
}
