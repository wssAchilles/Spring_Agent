package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import tech.qiantong.qknow.ai.transformer.StructureAwareMarkdownSplitter;
import tech.qiantong.qknow.module.kmc.service.rag.RagContextBuilder;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 14 门禁契约测试：结构感知分块与 Parent-Child Small-to-Big 检索闭环")
public class Phase14StructureAwareAndParentChildGateTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RagContextBuilder contextBuilder;

    @BeforeEach
    void setUp() throws Exception {
        contextBuilder = new RagContextBuilder();
        Field field = RagContextBuilder.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        field.set(contextBuilder, jdbcTemplate);
    }

    @Test
    @DisplayName("门禁 1: Parent 继承命中 Child 中的最高检索得分 (Max-Pooling，杜绝 score=0.0)")
    void testParentInheritsMaxScore_maxPooling() throws Exception {
        // 构造两个指向同一个 Parent "parent-001" 的 Child
        RetrievalResult child1 = RetrievalResult.builder()
                .segmentId(101L)
                .parentSegmentId("parent-001")
                .documentId(1L)
                .documentName("架构设计.md")
                .content("子块1: 核心引擎介绍")
                .score(0.82)
                .build();

        RetrievalResult child2 = RetrievalResult.builder()
                .segmentId(102L)
                .parentSegmentId("parent-001")
                .documentId(1L)
                .documentName("架构设计.md")
                .content("子块2: 高级特性与配置")
                .score(0.96) // 最高分
                .build();

        // Mock 数据库查询 Parent 内容
        RetrievalResult parent = RetrievalResult.builder()
                .segmentId(1L)
                .qmSegmentId("parent-001")
                .documentId(1L)
                .documentName("架构设计.md")
                .content("这是父块完整语义内容，包含了核心引擎介绍以及高级特性与配置的完整背景说明。")
                .score(0.0) // 初始为 0
                .source("parent")
                .build();

        mockJdbcQueryParent(List.of(parent));

        RagContextBuilder.ContextBuildResult result = contextBuilder.buildContextWithEmitted(
                List.of(child1, child2), false
        );

        assertNotNull(result);
        List<RetrievalResult> emitted = result.getEmittedResults();
        assertEquals(1, emitted.size(), "同一 Parent 下的多个 Child 应聚合为单个 Parent，不冗余展开");

        RetrievalResult emittedParent = emitted.get(0);
        assertEquals(0.96, emittedParent.getScore(), 1e-4,
                "Parent 必须继承所有命中 Child 中的最高检索得分 (Max-Pooling)，严禁为 0.0！");
        assertTrue(result.getContext().contains("score=0.9600"),
                "上下文格式化文本中必须反映继承的 Max-Pooling 分数");
    }

    @Test
    @DisplayName("门禁 2: 严格保持 Child 精排顺位保序装配，杜绝盲目置顶与乱序")
    void testPreservesStrictRankingOrderAndDedupes() throws Exception {
        // Child 排序为：Child_A (score 0.95) -> Child_B (score 0.88) -> Child_C (指向 A，score 0.70)
        RetrievalResult childA = RetrievalResult.builder()
                .segmentId(101L)
                .parentSegmentId("parent-A")
                .documentId(1L)
                .documentName("A.md")
                .content("内容A")
                .score(0.95)
                .build();

        RetrievalResult childB = RetrievalResult.builder()
                .segmentId(201L)
                .parentSegmentId("parent-B")
                .documentId(2L)
                .documentName("B.md")
                .content("内容B")
                .score(0.88)
                .build();

        RetrievalResult childC = RetrievalResult.builder()
                .segmentId(102L)
                .parentSegmentId("parent-A")
                .documentId(1L)
                .documentName("A.md")
                .content("内容A的次级段落")
                .score(0.70)
                .build();

        RetrievalResult parentA = RetrievalResult.builder()
                .segmentId(1L)
                .qmSegmentId("parent-A")
                .documentId(1L)
                .documentName("A.md")
                .content("父块A完整长文本")
                .score(0.0)
                .build();

        RetrievalResult parentB = RetrievalResult.builder()
                .segmentId(2L)
                .qmSegmentId("parent-B")
                .documentId(2L)
                .documentName("B.md")
                .content("父块B完整长文本")
                .score(0.0)
                .build();

        mockJdbcQueryParent(List.of(parentA, parentB));

        RagContextBuilder.ContextBuildResult result = contextBuilder.buildContextWithEmitted(
                List.of(childA, childB, childC), false
        );

        List<RetrievalResult> emitted = result.getEmittedResults();
        assertEquals(2, emitted.size(), "父块A去重，应仅有 Parent-A 与 Parent-B 两个实体");
        assertEquals("parent-A", emitted.get(0).getQmSegmentId(), "排在第 1 的必须是高分 Child 对应的 Parent-A");
        assertEquals("parent-B", emitted.get(1).getQmSegmentId(), "排在第 2 的必须是次高分对应的 Parent-B");

        // 验证文本中的顺位
        String context = result.getContext();
        int idxA = context.indexOf("[来源 1] A.md");
        int idxB = context.indexOf("[来源 2] B.md");
        assertTrue(idxA != -1 && idxB != -1 && idxA < idxB,
                "格式化上下文文本中必须严格按照精排顺序先后呈现");
    }

    @Test
    @DisplayName("门禁 3: 20KB 预算硬约束熔断与超限自适应优雅降级回 Child")
    void testBudgetAwareGracefulDegradation() throws Exception {
        // 设置较小预算 350 字节
        Field field = RagContextBuilder.class.getDeclaredField("maxContextBytes");
        field.setAccessible(true);
        field.set(contextBuilder, 350);

        // Parent-A 文本较大（约 300 字节），装填 Parent-A 之后剩余预算仅约 50 字节
        // Parent-B 文本很大（约 400 字节），若装填 Parent-B 则必定超过 350 字节总预算
        // 但 Child-B 文本很短（约 60 字节），可以装下！
        RetrievalResult childA = RetrievalResult.builder()
                .segmentId(101L)
                .parentSegmentId("parent-A")
                .documentId(1L)
                .documentName("DocA.md")
                .content("短ChildA")
                .score(0.95)
                .build();

        RetrievalResult childB = RetrievalResult.builder()
                .segmentId(201L)
                .parentSegmentId("parent-B")
                .documentId(2L)
                .documentName("DocB.md")
                .content("短ChildB关键证据") // 约 20 字符
                .score(0.90)
                .build();

        String largeParentContent = "这是一段非常非常长的父块内容，用来模拟超出预算的情况。" +
                "这是一段非常非常长的父块内容，用来模拟超出预算的情况。" +
                "这是一段非常非常长的父块内容，用来模拟超出预算的情况。";

        RetrievalResult parentA = RetrievalResult.builder()
                .segmentId(1L)
                .qmSegmentId("parent-A")
                .documentId(1L)
                .documentName("DocA.md")
                .content("这是ParentA的适中文本内容，大约几十个字，可以成功放入预算中。")
                .score(0.0)
                .build();

        RetrievalResult parentB = RetrievalResult.builder()
                .segmentId(2L)
                .qmSegmentId("parent-B")
                .documentId(2L)
                .documentName("DocB.md")
                .content(largeParentContent) // 超长 Parent
                .score(0.0)
                .build();

        mockJdbcQueryParent(List.of(parentA, parentB));

        RagContextBuilder.ContextBuildResult result = contextBuilder.buildContextWithEmitted(
                List.of(childA, childB), false
        );

        String context = result.getContext();
        int totalBytes = context.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(totalBytes <= 350, "总生成字节数必须严格 <= 350 字节预算！当前为: " + totalBytes);

        // 验证优雅降级：DocB 的关键证据必须依然存在于上下文中（以降级的 Child 原文形式注入）！
        assertTrue(context.contains("短ChildB关键证据"),
                "ParentB 超出预算时，应优雅降级保留其高分 ChildB 原文，而不是直接丢弃！");
    }

    @Test
    @DisplayName("门禁 4: 结构感知分块器 (StructureAwareMarkdownSplitter) 表格与表头传播契约")
    void testTablePreservationAndPropagationContract() {
        String md = """
# 财务季度报告
| 季度 | 营业总收入 (亿元) | 研发投入 (亿元) | 净利润 (亿元) |
| :--- | :--- | :--- | :--- |
| 2024Q1 | 120.5 | 18.2 | 25.1 |
| 2024Q2 | 135.0 | 20.1 | 28.3 |
| 2024Q3 | 142.8 | 21.5 | 30.2 |
| 2024Q4 | 158.4 | 24.0 | 33.6 |
""";

        StructureAwareMarkdownSplitter splitter = new StructureAwareMarkdownSplitter(180, 0, true);
        List<Document> docs = splitter.apply(List.of(new Document(md)));

        assertFalse(docs.isEmpty());
        for (Document doc : docs) {
            String text = doc.getText();
            assertTrue(text.contains("【财务季度报告】"), "每个表格切片都应注入标题面包屑");
            assertTrue(text.contains("| 季度 | 营业总收入 (亿元) | 研发投入 (亿元) | 净利润 (亿元) |"),
                    "切片必须包含完整表头，杜绝断头数据行！");
            assertTrue(text.contains("| :--- | :--- | :--- | :--- |"),
                    "切片必须包含对齐分隔线！");
        }
    }

    @SuppressWarnings("unchecked")
    private void mockJdbcQueryParent(List<RetrievalResult> parents) {
        lenient().doAnswer(invocation -> {
            RowMapper<RetrievalResult> rm = invocation.getArgument(1);
            List<RetrievalResult> list = new ArrayList<>();
            int rowNum = 0;
            for (RetrievalResult p : parents) {
                var rs = mock(java.sql.ResultSet.class);
                lenient().when(rs.getLong("id")).thenReturn(p.getSegmentId());
                lenient().when(rs.getString("qm_segment_id")).thenReturn(p.getQmSegmentId());
                lenient().when(rs.getString("content")).thenReturn(p.getContent());
                lenient().when(rs.getLong("document_id")).thenReturn(p.getDocumentId());
                lenient().when(rs.getString("document_name")).thenReturn(p.getDocumentName());
                lenient().when(rs.getString("answer")).thenReturn(p.getAnswer());
                list.add(rm.mapRow(rs, rowNum++));
            }
            return list;
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }
}
