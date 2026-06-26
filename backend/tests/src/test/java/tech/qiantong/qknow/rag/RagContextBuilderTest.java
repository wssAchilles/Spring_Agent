package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tech.qiantong.qknow.module.kmc.service.rag.RagContextBuilder;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagContextBuilder 上下文构建测试")
class RagContextBuilderTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RagContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        contextBuilder = new RagContextBuilder();
        try {
            var field = RagContextBuilder.class.getDeclaredField("jdbcTemplate");
            field.setAccessible(true);
            field.set(contextBuilder, jdbcTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("expandWithParentSegments: 子块有 parentSegmentId 时应返回父块内容")
    void buildContext_whenChildHasParent_expandsParentContent() {
        RetrievalResult child = RetrievalResult.builder()
                .segmentId(2L)
                .parentSegmentId("p1")
                .documentId(1L)
                .documentName("Day01.txt")
                .content("子块内容")
                .score(0.9)
                .build();

        RetrievalResult parent = RetrievalResult.builder()
                .segmentId(1L)
                .qmSegmentId("p1")
                .documentId(1L)
                .documentName("Day01.txt")
                .content("父块完整内容用于上下文扩展")
                .score(0.0)
                .source("parent")
                .build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenReturn(List.of(parent));

        String context = contextBuilder.buildContext(List.of(child), true);

        assertTrue(context.contains("父块完整内容用于上下文扩展"), "上下文应包含父块内容");
    }

    @Test
    @DisplayName("expandWithAdjacentSegments: 无 parentSegmentId 时应回退到相邻分块")
    void buildContext_whenNoParentIds_fallsBackToAdjacent() {
        RetrievalResult result = RetrievalResult.builder()
                .segmentId(10L)
                .documentId(1L)
                .documentName("Day01.txt")
                .content("当前分块内容")
                .score(0.8)
                .build();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(RetrievalResult.builder()
                        .segmentId(11L)
                        .documentId(1L)
                        .documentName("Day01.txt")
                        .content("相邻分块内容")
                        .score(0.0)
                        .source("adjacent")
                        .build()));

        String context = contextBuilder.buildContext(List.of(result), true);

        assertNotNull(context);
        assertTrue(context.contains("当前分块内容"), "上下文应包含原始分块内容");
    }

    @Test
    @DisplayName("deduplicateByContent: 重复内容应被去重")
    void buildContext_duplicateContentIsDeduplicated() {
        RetrievalResult r1 = RetrievalResult.builder()
                .segmentId(1L).documentName("Doc1").content("完全相同的内容").score(0.9).build();
        RetrievalResult r2 = RetrievalResult.builder()
                .segmentId(2L).documentName("Doc2").content("完全相同的内容").score(0.8).build();
        RetrievalResult r3 = RetrievalResult.builder()
                .segmentId(3L).documentName("Doc3").content("不同的内容").score(0.7).build();

        String context = contextBuilder.buildContext(List.of(r1, r2, r3), false);

        int count = countOccurrences(context, "完全相同的内容");
        assertEquals(1, count, "重复内容应被去重，只保留一份");
        assertTrue(context.contains("不同的内容"));
    }

    @Test
    @DisplayName("配置字节预算后上下文自动截断")
    void buildContext_respectsConfiguredByteBudget() throws Exception {
        var field = RagContextBuilder.class.getDeclaredField("maxContextBytes");
        field.setAccessible(true);
        field.set(contextBuilder, 80);

        RetrievalResult r1 = RetrievalResult.builder()
                .segmentId(1L).documentName("Doc1").content("短内容").score(0.9).build();
        RetrievalResult r2 = RetrievalResult.builder()
                .segmentId(2L).documentName("Doc2").content("这是一段会超过预算的长内容").score(0.8).build();

        String context = contextBuilder.buildContext(List.of(r1, r2), false);

        assertTrue(context.contains("短内容"));
        assertFalse(context.contains("这是一段会超过预算的长内容"));
    }

    @Test
    @DisplayName("空结果列表应返回空字符串")
    void buildContext_emptyResults_returnsEmptyString() {
        String context = contextBuilder.buildContext(Collections.emptyList(), false);
        assertEquals("", context);
    }

    @Test
    @DisplayName("null 结果列表应返回空字符串")
    void buildContext_nullResults_returnsEmptyString() {
        String context = contextBuilder.buildContext(null, false);
        assertEquals("", context);
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
