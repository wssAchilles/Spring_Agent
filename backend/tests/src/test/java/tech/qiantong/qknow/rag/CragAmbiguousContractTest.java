package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.CragRetrievalEvaluation;
import tech.qiantong.qknow.module.kmc.service.rag.CragRetrievalEvaluator;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 05: CRAG AMBIGUOUS 与反思澄清契约测试")
class CragAmbiguousContractTest {

    @Test
    @DisplayName("契约1: AMBIGUOUS 响应应正确解析出澄清选项并识别歧义状态")
    void testParseAmbiguousWithClarificationOptions() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, new CragRetrievalEvaluator.CragConfig());
        String mockResponse = "{\n" +
                "  \"label\": \"AMBIGUOUS\",\n" +
                "  \"confidence\": 0.55,\n" +
                "  \"reason\": \"Query mentions knowledge base without specifying doc or category\",\n" +
                "  \"rewrittenQuery\": \"知识库详细配置与分类\",\n" +
                "  \"clarificationOptions\": [\"关于知识库分类管理\", \"关于知识库文档上传\"]\n" +
                "}";

        CragRetrievalEvaluation eval = evaluator.parse(mockResponse, "知识库");

        assertNotNull(eval);
        assertTrue(eval.isAmbiguous());
        assertFalse(eval.isCorrect());
        assertFalse(eval.isIncorrect());
        assertEquals(CragRetrievalEvaluation.Label.AMBIGUOUS, eval.getLabel());
        assertEquals(0.55, eval.getConfidence());
        assertEquals("知识库详细配置与分类", eval.getRewrittenQuery());
        assertNotNull(eval.getClarificationOptions());
        assertEquals(2, eval.getClarificationOptions().size());
        assertTrue(eval.getClarificationOptions().contains("关于知识库分类管理"));
    }

    @Test
    @DisplayName("契约2: AMBIGUOUS 响应缺少选项时自动注入默认澄清兜底")
    void testParseAmbiguousFallbackOptions() {
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(null, new CragRetrievalEvaluator.CragConfig());
        String mockResponse = "{\n" +
                "  \"label\": \"AMBIGUOUS\",\n" +
                "  \"confidence\": 0.4,\n" +
                "  \"reason\": \"Borderline context\"\n" +
                "}";

        CragRetrievalEvaluation eval = evaluator.parse(mockResponse, "测试提问");

        assertTrue(eval.isAmbiguous());
        assertNotNull(eval.getClarificationOptions());
        assertFalse(eval.getClarificationOptions().isEmpty());
    }

    @Test
    @DisplayName("契约3: RagResult 支持携带歧义标志与细分澄清列表")
    void testRagResultAmbiguousContract() {
        RetrievalResult doc = new RetrievalResult();
        doc.setSegmentId(101L);
        doc.setContent("知识库核心配置说明文档");

        RagResult result = RagResult.builder()
                .context("知识库核心配置说明文档")
                .sources(List.of(doc))
                .ambiguous(true)
                .clarificationOptions(List.of("选项A", "选项B"))
                .build();

        assertTrue(result.getAmbiguous());
        assertEquals(1, result.getSources().size());
        assertEquals(2, result.getClarificationOptions().size());
        assertFalse(result.getContext().isEmpty());
    }
}
