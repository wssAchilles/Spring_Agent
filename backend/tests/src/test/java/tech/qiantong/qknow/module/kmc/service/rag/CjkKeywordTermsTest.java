package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CjkKeywordTermsTest {

    @Test
    @DisplayName("短中文查询生成 2-gram 检索词")
    void shortCjkQueryYieldsBigrams() {
        List<String> terms = KeywordRetriever.buildSearchTerms("库管理");
        assertTrue(terms.contains("库管理"));
        assertTrue(terms.contains("库管") || terms.contains("管理"));
    }

    @Test
    @DisplayName("哈希分桶拆出二元组")
    void hashBucketQueryYieldsBigrams() {
        List<String> terms = KeywordRetriever.buildSearchTerms("哈希分桶");
        assertTrue(terms.stream().anyMatch(t -> t.contains("哈希") || t.contains("分桶")));
    }

    @Test
    @DisplayName("纯英文查询仍提取英文词")
    void englishQueryStillExtractsWords() {
        List<String> terms = KeywordRetriever.buildSearchTerms("Catalyst optimizer");
        assertTrue(terms.stream().anyMatch(t -> t.equalsIgnoreCase("catalyst") || t.contains("catalyst")));
    }

    @Test
    @DisplayName("问候语不会仅因停用词过滤丢掉整句")
    void greetingKeepsOriginal() {
        List<String> terms = KeywordRetriever.buildSearchTerms("你好");
        assertTrue(terms.contains("你好"));
    }
}
