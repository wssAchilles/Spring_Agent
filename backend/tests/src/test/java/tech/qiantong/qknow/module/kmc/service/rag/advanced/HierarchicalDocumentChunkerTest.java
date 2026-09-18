package tech.qiantong.qknow.module.kmc.service.rag.advanced;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.ChunkHierarchyLevel;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.HierarchicalDocumentChunker;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 105 契约测试集一：自适应文档树层次化切片与向上父展开引擎测试
 *
 * @author Achilles
 * @version 1.0
 */
public class HierarchicalDocumentChunkerTest {

    @Test
    @DisplayName("契约 1：四级文档树状结构构建正确性 (Document -> Section -> Paragraph -> Sentence，双向指针)")
    void testBuildHierarchyTree_StructureAndPointers() {
        HierarchicalDocumentChunker chunker = new HierarchicalDocumentChunker();

        String sampleDoc = """
                第一章 智能体认知编排规范
                本规范详细定义了企业级智能体编排的核心原则与边界约束。
                所有智能体在执行任务前必须完成身份鉴权与权限校验。

                第二章 运行时安全隔离与沙箱
                高危操作必须进入沙箱隔离执行。
                拦截并阻断任何未授权的文件修改指令；确保零信任网络隔离。
                """;

        HierarchicalDocumentChunker.ChunkNode rootNode = chunker.buildHierarchyTree("doc-spec-001", sampleDoc);

        assertNotNull(rootNode, "根节点不应为空");
        assertEquals("doc-doc-spec-001", rootNode.id());
        assertEquals(ChunkHierarchyLevel.DOCUMENT, rootNode.level());
        assertNull(rootNode.parentId(), "文档根节点没有 parentId");
        assertFalse(rootNode.childrenIds().isEmpty(), "文档根节点应包含 Section 子节点");

        // 校验 Section 节点
        for (String secId : rootNode.childrenIds()) {
            HierarchicalDocumentChunker.ChunkNode secNode = chunker.getNode(secId);
            assertNotNull(secNode);
            assertEquals(ChunkHierarchyLevel.SECTION, secNode.level());
            assertEquals(rootNode.id(), secNode.parentId(), "Section 的父节点必须是 Root");
            assertFalse(secNode.childrenIds().isEmpty(), "Section 必须包含 Paragraph 子节点");

            // 校验 Paragraph 节点
            for (String paraId : secNode.childrenIds()) {
                HierarchicalDocumentChunker.ChunkNode paraNode = chunker.getNode(paraId);
                assertNotNull(paraNode);
                assertEquals(ChunkHierarchyLevel.PARAGRAPH, paraNode.level());
                assertEquals(secId, paraNode.parentId(), "Paragraph 的父节点必须是 Section");

                // 校验 Sentence 节点
                for (String sentId : paraNode.childrenIds()) {
                    HierarchicalDocumentChunker.ChunkNode sentNode = chunker.getNode(sentId);
                    assertNotNull(sentNode);
                    assertEquals(ChunkHierarchyLevel.SENTENCE, sentNode.level());
                    assertEquals(paraId, sentNode.parentId(), "Sentence 的父节点必须是 Paragraph");
                    assertTrue(sentNode.childrenIds().isEmpty(), "Sentence 叶子节点无子节点");
                }
            }
        }
    }

    @Test
    @DisplayName("契约 2：向上父级展开上下文保真度验证 (章节标题与段落背景 100% 完整关联，保真度得分 >= 0.95)")
    void testExpandParentContext_EnrichedContextAndPreservationScore() {
        HierarchicalDocumentChunker chunker = new HierarchicalDocumentChunker();

        String sampleDoc = """
                第四章 高压动力总成安全设计规范
                高压断电保护响应时间在碰撞事故中必须严格控制在 50 毫秒以内。
                前款所述指标适用于所有出厂车辆；第三方测试机构必须予以公证复核。
                """;

        chunker.buildHierarchyTree("doc-auto-004", sampleDoc);

        // 寻找一个叶子 Sentence 节点
        String targetSentId = null;
        for (int i = 1; i <= 20; i++) {
            String candidateId = "doc-doc-auto-004-sec-1-p-1-s-" + i;
            if (chunker.getNode(candidateId) != null) {
                targetSentId = candidateId;
                break;
            }
        }
        assertNotNull(targetSentId, "必须找到目标句子节点");

        HierarchicalDocumentChunker.ExpandedContext expanded = chunker.expandParentContext(targetSentId);
        assertNotNull(expanded);
        assertEquals(targetSentId, expanded.targetChunkId());
        assertTrue(expanded.parentSectionTitle().contains("高压动力总成安全设计规范"), "父章节标题必须正确提取");
        assertTrue(expanded.fullEnrichedContext().contains("【所属章节："), "富上下文必须包含所属章节信息");
        assertTrue(expanded.fullEnrichedContext().contains("【核心命中文本】："), "富上下文必须包含核心命中文本");
        assertTrue(expanded.contextPreservationScore() >= 0.95, "上下文信息熵保真度得分必须 >= 0.95");
    }

    @Test
    @DisplayName("契约 3：空文档、单段落、特殊标点与长文档边界性能压测 (单步耗时 <= 50us)")
    void testEdgeCasesAndPerformanceBenchmark() {
        HierarchicalDocumentChunker chunker = new HierarchicalDocumentChunker();

        // 1. 空指针防御
        assertThrows(NullPointerException.class, () -> chunker.buildHierarchyTree(null, "content"));
        assertThrows(NullPointerException.class, () -> chunker.buildHierarchyTree("id", null));

        // 2. 空白字符串与单段落
        HierarchicalDocumentChunker.ChunkNode emptyRoot = chunker.buildHierarchyTree("doc-empty", "   \n\n   ");
        assertNotNull(emptyRoot);
        assertTrue(emptyRoot.childrenIds().isEmpty());

        // 3. 构造 500 段中型文档压测耗时
        StringBuilder largeDoc = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            largeDoc.append("第 ").append(i).append(" 章节 关键系统技术要求\n");
            largeDoc.append("这里是段落正文描述内容，包含多种标点符号；例如句号。感叹号！问号？\n\n");
        }

        long startNs = System.nanoTime();
        HierarchicalDocumentChunker.ChunkNode largeRoot = chunker.buildHierarchyTree("doc-perf-500", largeDoc.toString());
        long totalElapsedUs = (System.nanoTime() - startNs) / 1000;

        assertNotNull(largeRoot);
        assertEquals(500, largeRoot.childrenIds().size());

        // 平均每章节切片与树节点构建耗时应 <= 50us
        double avgPerSectionUs = (double) totalElapsedUs / 500.0;
        assertTrue(avgPerSectionUs <= 50.0, "平均单章节树构建耗时应 <= 50us，实际: " + avgPerSectionUs + "us");

        // 查询不存在节点的安全返回
        HierarchicalDocumentChunker.ExpandedContext missing = chunker.expandParentContext("non-existent-id");
        assertNotNull(missing);
        assertEquals(0.0, missing.contextPreservationScore());
    }
}
