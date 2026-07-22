package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("KeywordRetriever 纯函数测试")
class KeywordRetrieverTest {

    @Test
    @DisplayName("buildSearchTerms 标准化 Day 编号并保留英文关键词")
    void buildSearchTerms_normalizesDayAndKeepsEnglishKeywords() {
        List<String> terms = KeywordRetriever.buildSearchTerms("请告诉我 Day1 RAG 信息");

        assertTrue(terms.contains("day1"));
        assertTrue(terms.contains("rag"));
        assertTrue(terms.contains("Day01"));
    }

    @Test
    @DisplayName("identifierAware 只保留带实体提示词的原始数字 span")
    void identifierTerms_keepExactIdsAndRejectDatesMeasures() {
        assertEquals(List.of("034", "044"), KeywordRetriever.extractIdentifierTerms(
                "主题 034 topic 044 034"));

        Stream.of("2024-03-04", "3天", "第3天", "10%", "3.14", "1,000", "5kg", "Day07")
                .forEach(query -> assertTrue(KeywordRetriever.extractIdentifierTerms(query).isEmpty(), query));
    }

    @Test
    @DisplayName("identifierAware 开关关闭时保持旧关键词集合")
    void buildSearchTerms_identifierAwareDisabledPreservesLegacyTerms() {
        List<String> terms = KeywordRetriever.buildSearchTerms("主题 034", false);

        assertEquals(KeywordRetriever.buildSearchTerms("主题 034"), terms);
    }

    @Test
    @DisplayName("identifierAware 开启时保留零填充编号且不生成归一化编号")
    void buildSearchTerms_identifierAwareEnabledKeepsRawIdentifier() {
        List<String> terms = KeywordRetriever.buildSearchTerms("主题 034", true);

        assertTrue(terms.contains("034"));
        assertFalse(terms.contains("34"));
    }

    @Test
    @DisplayName("expandWithSynonyms 扩展大小写不敏感同义词")
    void expandWithSynonyms_expandsCaseInsensitiveSynonyms() {
        List<String> expanded = KeywordRetriever.expandWithSynonyms(List.of("rag", "知识图谱"));

        assertTrue(expanded.contains("检索增强生成"));
        assertTrue(expanded.contains("Knowledge Graph"));
    }

    @Test
    @DisplayName("calculateKeywordScore 文档名命中权重大于内容命中")
    void calculateKeywordScore_weightsDocumentNameHigher() {
        float docNameScore = KeywordRetriever.calculateKeywordScore("RAG 架构", "无关内容", List.of("rag"));
        float contentScore = KeywordRetriever.calculateKeywordScore("普通文档", "RAG RAG", List.of("rag"));

        assertTrue(docNameScore > contentScore);
    }

    @Test
    @DisplayName("extractDayTerms 支持英文和中文天数表达")
    void extractDayTerms_supportsEnglishAndChineseDayTerms() {
        List<String> terms = KeywordRetriever.extractDayTerms("day02 和 第3天");

        assertTrue(terms.contains("Day02"));
        assertTrue(terms.contains("Day2"));
        assertTrue(terms.contains("Day03"));
        assertTrue(terms.contains("Day3"));
    }

    @Test
    @DisplayName("normalizeForTrgm 合并空白")
    void normalizeForTrgm_collapsesWhitespace() {
        assertEquals("hello rag", KeywordRetriever.normalizeForTrgm("  hello   rag  "));
        assertEquals("", KeywordRetriever.normalizeForTrgm(null));
    }

    @Test
    @DisplayName("isSelectiveTrigramTerm 跳过短中文内容模糊扫描")
    void isSelectiveTrigramTerm_skipsShortCjkTerms() {
        assertFalse(KeywordRetriever.isSelectiveTrigramTerm("系统"));
        assertTrue(KeywordRetriever.isSelectiveTrigramTerm("知识图谱"));
        assertTrue(KeywordRetriever.isSelectiveTrigramTerm("rag"));
    }

    @Test
    @DisplayName("retrieve 使用可索引的 tsvector 和 pg_trgm 条件")
    @SuppressWarnings("unchecked")
    void retrieve_usesIndexFriendlyKeywordPredicates() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        KeywordRetriever retriever = new KeywordRetriever();
        ReflectionTestUtils.setField(retriever, "jdbcTemplate", jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        retriever.retrieve(8L, "系统 RAG", 5);

        verify(jdbcTemplate).query(argThat(sql ->
                        sql.contains("UNION ALL")
                                && sql.contains("s.content_tsv @@ plainto_tsquery('simple', ?)")
                                && sql.contains("s.content % ?")
                                && sql.contains("d.name % ?")
                                && sql.contains("s.content ILIKE ?")
                                && sql.contains("d.name ILIKE ?")),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    @DisplayName("retrieve 对短中文词不生成内容 ILIKE 或内容 trigram 条件")
    @SuppressWarnings("unchecked")
    void retrieve_shortCjkQuerySkipsBroadContentPredicates() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        KeywordRetriever retriever = new KeywordRetriever();
        ReflectionTestUtils.setField(retriever, "jdbcTemplate", jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        retriever.retrieve(8L, "系统", 5);

        verify(jdbcTemplate).query(argThat(sql ->
                        sql.contains("s.content_tsv @@ plainto_tsquery('simple', ?)")
                                && !sql.contains("s.content % ?")
                                && !sql.contains("s.content ILIKE ?")
                                && sql.contains("d.name ILIKE ?")),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    @DisplayName("identifierAware 将精确编号加入 SQL ILIKE 参数")
    @SuppressWarnings("unchecked")
    void retrieve_identifierAwareAddsExactIdentifierToSqlParameters() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        KeywordRetriever retriever = new KeywordRetriever();
        ReflectionTestUtils.setField(retriever, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(retriever, "identifierAware", true);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        retriever.retrieve(8L, "主题 034", 5);

        var params = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(any(String.class), any(RowMapper.class), params.capture());
        assertTrue(Arrays.stream(params.getValue()).anyMatch("%034%"::equals));
    }

}
