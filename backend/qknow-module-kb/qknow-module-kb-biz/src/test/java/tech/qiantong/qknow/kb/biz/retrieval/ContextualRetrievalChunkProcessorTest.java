package tech.qiantong.qknow.kb.biz.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.ContextualRetrievalChunkProcessor;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Anthropic 风格 Contextual Retrieval 语境切片增强契约测试
 */
public class ContextualRetrievalChunkProcessorTest {

    private final ContextualRetrievalChunkProcessor processor = new ContextualRetrievalChunkProcessor();

    @Test
    @DisplayName("测试切片结构化元数据与主旨语境前缀增强")
    void testEnrichChunk_WithMetadataAndSummary() {
        RetrieveResult chunk = new RetrieveResult();
        chunk.setId("chunk-101");
        chunk.setDocumentId("doc-2024-finance");
        chunk.setDocumentName("2024年度财报.pdf");
        chunk.setDocMetadata("{\"section\":\"第三章 研发投入与资本化率\"}");
        chunk.setContent("本期研发支出合计 4.5 亿元，同比增长 12.8%，全部计入当期损益。");

        ContextualRetrievalChunkProcessor.ContextualChunk result = processor.enrichChunk(
                chunk, "ACME集团2024会计年度经审计财务报告及经营业绩展望"
        );

        assertNotNull(result);
        assertEquals("chunk-101", result.chunkId());
        assertTrue(result.contextPrefix().contains("2024年度财报.pdf"));
        assertTrue(result.contextPrefix().contains("第三章 研发投入与资本化率"));
        assertTrue(result.contextPrefix().contains("ACME集团2024会计年度"));
        assertTrue(result.enrichedContent().startsWith(ContextualRetrievalChunkProcessor.CONTEXT_PREFIX_START));
        assertTrue(result.enrichedContent().contains("本期研发支出合计 4.5 亿元"));
        assertTrue(result.prefixTokens() > 0);
        assertTrue(result.totalTokens() > result.originalTokens());
    }

    @Test
    @DisplayName("测试语境前缀幂等性与防止重复追加")
    void testEnrichChunk_Idempotency() {
        RetrieveResult chunk = new RetrieveResult();
        chunk.setId("chunk-idempotent");
        chunk.setDocumentName("员工手册.pdf");
        chunk.setContent("所有员工应在每年12月完成绩效自评。");

        // 第一次增强
        ContextualRetrievalChunkProcessor.ContextualChunk first = processor.enrichChunk(chunk, "公司行政规章");
        String firstEnriched = first.enrichedContent();

        // 将增强后的结果作为输入再次增强
        RetrieveResult alreadyEnrichedChunk = new RetrieveResult();
        alreadyEnrichedChunk.setId("chunk-idempotent");
        alreadyEnrichedChunk.setContent(firstEnriched);
        alreadyEnrichedChunk.setDocumentName("员工手册.pdf");

        ContextualRetrievalChunkProcessor.ContextualChunk second = processor.enrichChunk(alreadyEnrichedChunk, "公司行政规章");

        assertEquals(firstEnriched, second.enrichedContent(), "已增强切片内容应保持一致，严禁二次叠加前缀");
    }

    @Test
    @DisplayName("测试语境摘要内存缓存与批量处理")
    void testBatchEnrich_WithCacheHits() {
        processor.registerDocumentSummary("doc-A", "企业安全合规操作指引文档");

        RetrieveResult c1 = new RetrieveResult();
        c1.setId("c1");
        c1.setDocumentId("doc-A");
        c1.setDocumentName("安全规程.pdf");
        c1.setContent("机房出入需双人刷卡登记。");

        RetrieveResult c2 = new RetrieveResult();
        c2.setId("c2");
        c2.setDocumentId("doc-B");
        c2.setDocumentName("差旅报销.pdf");
        c2.setContent("高铁二等座凭票报销。");

        ContextualRetrievalChunkProcessor.ContextualBatchResult batchResult = processor.enrichBatch(
                List.of(c1, c2), Map.of("doc-B", "差旅费管理办法")
        );

        assertNotNull(batchResult);
        assertEquals(2, batchResult.totalProcessed());
        assertEquals(1, batchResult.cacheHits(), "doc-A 应命中注册的内存缓存");
        List<ContextualRetrievalChunkProcessor.ContextualChunk> enriched = batchResult.enrichedChunks();
        assertTrue(enriched.get(0).enrichedContent().contains("企业安全合规操作指引文档"));
        assertTrue(enriched.get(1).enrichedContent().contains("差旅费管理办法"));
    }

    @Test
    @DisplayName("测试 stripContextPrefix 逆向无损正文还原")
    void testStripContextPrefix() {
        String rawText = "深度学习模型训练需配置 NCCL 通信库以支持多卡分布式训练。";
        RetrieveResult chunk = new RetrieveResult();
        chunk.setId("c-tech");
        chunk.setDocumentName("AI集群部署手册.docx");
        chunk.setContent(rawText);

        ContextualRetrievalChunkProcessor.ContextualChunk enriched = processor.enrichChunk(chunk, "AI大模型基础设施运维");
        String enrichedText = enriched.enrichedContent();

        String restoredText = ContextualRetrievalChunkProcessor.stripContextPrefix(enrichedText);
        assertEquals(rawText, restoredText, "必须能精确还原无损正文");
    }
}
