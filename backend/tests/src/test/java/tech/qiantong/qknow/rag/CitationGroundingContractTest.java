package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.RagContextBuilder;
import tech.qiantong.qknow.module.kmc.service.rag.citation.CitationExtractor;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 08: 生成侧评估与精准溯源 (Citation) 契约测试")
public class CitationGroundingContractTest {

    @Test
    @DisplayName("契约 1: 预算溢出截断严格对齐 (杜绝幽灵引用泄露)")
    void testBudgetContextStrictAlignment() {
        RagContextBuilder contextBuilder = new RagContextBuilder();
        // 设置精准预算上限：2500 字节（单条约 1150 字节，可容纳 2 条，第 3 条 3450 字节发生截断）
        ReflectionTestUtils.setField(contextBuilder, "maxContextBytes", 2500);

        // 构造 4 个不同段落（避免被 deduplicateByContent 过滤），每个段落正文约 1000 字节
        String longText = "深度学习与检索增强生成（RAG）核心技术规范。".repeat(15);
        List<RetrievalResult> candidates = List.of(
                createResult(1L, 101L, "规范文档1.pdf", "【文档1章节】" + longText),
                createResult(2L, 102L, "规范文档2.pdf", "【文档2章节】" + longText),
                createResult(3L, 103L, "规范文档3.pdf", "【文档3章节】" + longText),
                createResult(4L, 104L, "规范文档4.pdf", "【文档4章节】" + longText)
        );

        RagContextBuilder.ContextBuildResult buildResult = contextBuilder.buildContextWithEmitted(candidates, false);
        String context = buildResult.getContext();
        List<RetrievalResult> emitted = buildResult.getEmittedResults();

        // 验证：由于单条 entry 约 450 字节，装入 2 条后累计近 900 字节，第 3 条无法塞入而截断
        assertNotNull(context);
        assertTrue(context.contains("[来源 1]"));
        assertTrue(context.contains("[来源 2]"));
        assertFalse(context.contains("[来源 3]"), "超预算的第 3 条文档绝不应出现在格式化上下文中");
        assertFalse(context.contains("[来源 4]"));

        // 关键断言：实际装入的 emitted 列表大小严格与 context 中的来源数量一致（均为 2）
        assertEquals(2, emitted.size(), "实际导出的装入段落必须严格为 2，杜绝幽灵来源");
        assertEquals(1L, emitted.get(0).getSegmentId());
        assertEquals(2L, emitted.get(1).getSegmentId());
    }

    @Test
    @DisplayName("契约 2: 结构化引用角标解析与段落精准锚定")
    void testStructuredCitationExtractionAndGrounding() {
        CitationExtractor extractor = new CitationExtractor();

        List<RetrievalResult> emittedSources = List.of(
                createResult(101L, 10L, "知识图谱架构.pdf", "知识图谱包含实体与关系三元组建模。"),
                createResult(102L, 20L, "向量检索技术.pdf", "向量检索通过近似最近邻（ANN）算法实现高效语义匹配。")
        );

        // 模拟大模型输出的混合角标回答
        String answer = "根据系统资料，知识图谱能够表达复杂的实体语义关系[来源 1]；"
                + "而在底座层面，向量检索提供了高效的高维空间匹配[2]。";

        CitationExtractor.CitationReport report = extractor.extractAndEvaluate(answer, emittedSources);

        assertNotNull(report);
        assertEquals(2, report.getTotalCitations(), "应提取到 2 处引用");
        assertEquals(2, report.getValidCitations(), "有效引用应为 2 处");
        assertEquals(1.0, report.getCitationPrecision(), 1e-6, "引用精准率应为 100%");
        assertEquals(1.0, report.getCitationRecall(), 1e-6, "来源覆盖率应为 100%");
        assertFalse(report.isHasHallucinatedCitation(), "不应包含虚假引用");

        List<CitationExtractor.CitationRef> citations = report.getCitations();
        assertEquals(2, citations.size());

        // 验证第一处引用锚定
        CitationExtractor.CitationRef c1 = citations.get(0);
        assertEquals(1, c1.getIndex());
        assertEquals("[来源 1]", c1.getRawMarker());
        assertEquals(101L, c1.getSegmentId());
        assertEquals(10L, c1.getDocumentId());
        assertEquals("知识图谱架构.pdf", c1.getDocumentName());
        assertTrue(c1.isValid());
        assertNotNull(c1.getSnippet());

        // 验证第二处引用锚定
        CitationExtractor.CitationRef c2 = citations.get(1);
        assertEquals(2, c2.getIndex());
        assertEquals("[2]", c2.getRawMarker());
        assertEquals(102L, c2.getSegmentId());
        assertEquals(20L, c2.getDocumentId());
        assertEquals("向量检索技术.pdf", c2.getDocumentName());
        assertTrue(c2.isValid());
    }

    @Test
    @DisplayName("契约 3: 越界与虚构引用精准拦截 (Hallucination Detection)")
    void testHallucinatedCitationInterception() {
        CitationExtractor extractor = new CitationExtractor();

        // 实际上下文中仅装入了 2 个段落
        List<RetrievalResult> emittedSources = List.of(
                createResult(201L, 30L, "本地政策A.docx", "政策 A 规定了常规审批流程。"),
                createResult(202L, 40L, "本地政策B.docx", "政策 B 说明了节假日考勤制度。")
        );

        // 模拟大模型凭空捏造了不存在的 [来源 3] 和 【9】
        String answer = "常规审批应当遵循标准时限[来源 1]，而对于海外分支机构则遵循特别规定[来源 3]，并且满足反洗钱条例【9】。";

        CitationExtractor.CitationReport report = extractor.extractAndEvaluate(answer, emittedSources);

        assertNotNull(report);
        assertEquals(3, report.getTotalCitations(), "总共匹配到 3 处引用标记");
        assertEquals(1, report.getValidCitations(), "仅有 1 处有效引用");
        assertTrue(report.isHasHallucinatedCitation(), "检测出虚假越界引用，标记必须为 true");
        assertEquals(1.0 / 3.0, report.getCitationPrecision(), 1e-6);

        // 检查无效引用的元数据
        List<CitationExtractor.CitationRef> citations = report.getCitations();
        assertTrue(citations.get(0).isValid(), "第 1 处引用有效");
        assertFalse(citations.get(1).isValid(), "第 2 处引用 [来源 3] 越界无效");
        assertEquals("INVALID_SOURCE", citations.get(1).getDocumentName());
        assertFalse(citations.get(2).isValid(), "第 3 处引用 【9】 越界无效");
    }

    @Test
    @DisplayName("契约 4: 空文本与无引用场景平滑防御")
    void testEmptyAndNoCitationHandling() {
        CitationExtractor extractor = new CitationExtractor();

        List<RetrievalResult> emittedSources = List.of(
                createResult(301L, 50L, "文档.pdf", "内容正文")
        );

        // 无引用正文
        CitationExtractor.CitationReport report = extractor.extractAndEvaluate("这是一段普通回答，没有携带任何引用标记。", emittedSources);
        assertEquals(0, report.getTotalCitations());
        assertEquals(0, report.getValidCitations());
        assertEquals(1.0, report.getCitationPrecision());
        assertEquals(0.0, report.getCitationRecall());
        assertFalse(report.isHasHallucinatedCitation());

        // 空正文
        CitationExtractor.CitationReport emptyReport = extractor.extractAndEvaluate("", emittedSources);
        assertEquals(0, emptyReport.getTotalCitations());
        assertFalse(emptyReport.isHasHallucinatedCitation());
    }

    private static RetrievalResult createResult(Long segmentId, Long docId, String docName, String content) {
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .documentId(docId)
                .documentName(docName)
                .content(content)
                .score(0.9)
                .source("vector")
                .metadata(Map.of("segmentId", segmentId))
                .build();
    }
}
