package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.nlp.ChineseDictionaryService;
import tech.qiantong.qknow.module.kmc.service.rag.nlp.JiebaNative;
import tech.qiantong.qknow.module.kmc.service.rag.KeywordRetriever;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 06: 中文 IR 与检索底座原生加速契约测试")
class ChineseIrEnhancementContractTest {

    @Test
    @DisplayName("契约1: 领域专有词库能准确识别复合术语并防止机械切碎")
    void testExtractDomainTerms() {
        ChineseDictionaryService dictService = new ChineseDictionaryService();
        String query = "请问如何基于知识图谱与检索增强生成进行微调训练？";

        List<String> domainTerms = dictService.extractDomainTerms(query);

        assertNotNull(domainTerms);
        assertTrue(domainTerms.contains("知识图谱"));
        assertTrue(domainTerms.contains("检索增强生成"));
        assertTrue(domainTerms.contains("微调训练"));
    }

    @Test
    @DisplayName("契约2: 对话噪音与高频停用词精准过滤")
    void testStopWordsFiltering() {
        ChineseDictionaryService dictService = new ChineseDictionaryService();

        assertTrue(dictService.isStopWord("请问"));
        assertTrue(dictService.isStopWord("帮我查一下"));
        assertTrue(dictService.isStopWord("详细说明"));
        assertTrue(dictService.isStopWord("关于"));
        assertFalse(dictService.isStopWord("知识图谱"));
        assertFalse(dictService.isStopWord("RAG"));
    }

    @Test
    @DisplayName("契约3: 同义词扩展受控截断，防止 SQL 语句膨胀")
    void testControlledSynonymExpansion() {
        ChineseDictionaryService dictService = new ChineseDictionaryService();
        List<String> terms = List.of("RAG", "知识图谱", "向量检索");

        List<String> expanded = dictService.expandSynonyms(terms, 2, 8);

        assertNotNull(expanded);
        assertTrue(expanded.contains("RAG"));
        assertTrue(expanded.size() <= 8, "总扩展数不得超过限制上限 8");
    }

    @Test
    @DisplayName("契约4: KeywordRetriever 检索词组装集成领域词库")
    void testKeywordRetrieverBuildSearchTerms() {
        ChineseDictionaryService dictService = new ChineseDictionaryService();
        String query = "请问大语言模型的向量检索微调机制是什么";

        List<String> searchTerms = KeywordRetriever.buildSearchTerms(query, false);
        assertNotNull(searchTerms);
        assertFalse(searchTerms.isEmpty());

        // 无 JNI 环境下平滑降级验证
        String[] jiebaTokens = JiebaNative.safeCut(query);
        // 不应抛出异常，若 JNI 未加载则返回 null，降级机制平稳运行
        assertTrue(jiebaTokens == null || jiebaTokens.length > 0);
    }
}
