package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;

import static org.junit.jupiter.api.Assertions.*;

class QueryIntentAnalyzerTest {

    private QueryIntentAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new QueryIntentAnalyzer();
    }

    @Test
    @DisplayName("空查询返回空意图")
    void analyze_blankQuery_returnsEmptyIntent() {
        QueryIntent intent = analyzer.analyze("");

        assertNull(intent.getDayNo());
        assertNull(intent.getDocName());
        assertTrue(intent.getKeywords().isEmpty());
    }

    @Test
    @DisplayName("null查询返回空意图")
    void analyze_nullQuery_returnsEmptyIntent() {
        QueryIntent intent = analyzer.analyze(null);

        assertNull(intent.getDayNo());
        assertNull(intent.getDocName());
    }

    @Test
    @DisplayName("dayXX模式提取 - day01 → 1")
    void analyze_day01_extractsDayNo1() {
        QueryIntent intent = analyzer.analyze("day01的内容");

        assertEquals(1, intent.getDayNo());
    }

    @Test
    @DisplayName("dayXX模式提取 - Day5 → 5")
    void analyze_Day5_extractsDayNo5() {
        QueryIntent intent = analyzer.analyze("Day5讲了什么");

        assertEquals(5, intent.getDayNo());
    }

    @Test
    @DisplayName("dayXX模式提取 - day12 → 12")
    void analyze_day12_extractsDayNo12() {
        QueryIntent intent = analyzer.analyze("day12的知识点");

        assertEquals(12, intent.getDayNo());
    }

    @Test
    @DisplayName("中文数字提取 - 第一天 → 1")
    void analyze_firstDay_extractsDayNo1() {
        QueryIntent intent = analyzer.analyze("第一天的内容是什么");

        assertEquals(1, intent.getDayNo());
    }

    @Test
    @DisplayName("中文数字提取 - 第十五天 → 15")
    void analyze_fifteenthDay_extractsDayNo15() {
        QueryIntent intent = analyzer.analyze("第十五天学了什么");

        assertEquals(15, intent.getDayNo());
    }

    @Test
    @DisplayName("中文数字提取 - 第三日 → 3")
    void analyze_thirdDay_extractsDayNo3() {
        QueryIntent intent = analyzer.analyze("第三日的重点");

        assertEquals(3, intent.getDayNo());
    }

    @Test
    @DisplayName("docName生成 - day1 → Day01")
    void analyze_day1_generatesDocNameDay01() {
        QueryIntent intent = analyzer.analyze("day1的内容");

        assertEquals("Day01", intent.getDocName());
    }

    @Test
    @DisplayName("docName生成 - day12 → Day12")
    void analyze_day12_generatesDocNameDay12() {
        QueryIntent intent = analyzer.analyze("day12的内容");

        assertEquals("Day12", intent.getDocName());
    }

    @Test
    @DisplayName("docName生成 - 第五天 → Day05")
    void analyze_chineseFifthDay_generatesDocNameDay05() {
        QueryIntent intent = analyzer.analyze("第五天的内容");

        assertEquals("Day05", intent.getDocName());
    }

    @Test
    @DisplayName("关键词提取 - 去停用词")
    void analyze_removesStopWords() {
        QueryIntent intent = analyzer.analyze("请告诉我知识图谱的信息");

        // "请" 和 "告诉我" 是停用词，被过滤
        assertFalse(intent.getKeywords().contains("请"));
        assertFalse(intent.getKeywords().contains("告诉我"));
        assertFalse(intent.getKeywords().contains("的"));
        // 中文字符被 regex 视为连续块，整段作为一个关键词
        assertFalse(intent.getKeywords().isEmpty());
        assertTrue(intent.getKeywords().stream().anyMatch(k -> k.contains("知识图谱")));
    }

    @Test
    @DisplayName("关键词提取 - 去短词（长度<2）")
    void analyze_removesShortTokens() {
        QueryIntent intent = analyzer.analyze("A B 知识图谱测试");

        // 单字符 token "A" "B" 被过滤
        assertFalse(intent.getKeywords().contains("A"));
        assertFalse(intent.getKeywords().contains("B"));
        // 中文连续块保留
        assertTrue(intent.getKeywords().stream().anyMatch(k -> k.contains("知识图谱")));
    }

    @Test
    @DisplayName("关键词提取 - 保留CJK字符")
    void analyze_preservesCjkTokens() {
        QueryIntent intent = analyzer.analyze("知识图谱实体关系抽取");

        // 中文连续块作为一个 token 保留
        assertFalse(intent.getKeywords().isEmpty());
        assertTrue(intent.getKeywords().stream().anyMatch(k -> k.contains("知识图谱")));
        assertTrue(intent.getKeywords().stream().anyMatch(k -> k.contains("实体关系抽取")));
    }

    @Test
    @DisplayName("混合查询同时提取dayNo和keywords")
    void analyze_mixedQuery_extractsDayNoAndKeywords() {
        QueryIntent intent = analyzer.analyze("day3的知识图谱实体关系");

        assertEquals(3, intent.getDayNo());
        assertEquals("Day03", intent.getDocName());
        // 中文连续块保留
        assertTrue(intent.getKeywords().stream().anyMatch(k -> k.contains("知识图谱")));
        assertTrue(intent.getKeywords().stream().anyMatch(k -> k.contains("实体关系")));
    }

    @Test
    @DisplayName("无dayNo时docName为null")
    void analyze_noDayNo_docNameIsNull() {
        QueryIntent intent = analyzer.analyze("知识图谱是什么");

        assertNull(intent.getDayNo());
        assertNull(intent.getDocName());
    }
}
