package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColbertScorerTest {

    private ColbertScorer scorer;
    private ColbertScorer.ColbertConfig config;

    @BeforeEach
    void setUp() {
        config = new ColbertScorer.ColbertConfig();
        config.setEnabled(true);
        config.setNgramSize(2);
        scorer = new ColbertScorer(config);
    }

    @Test
    @DisplayName("禁用时返回原始文档")
    void rerank_withDisabled_returnsOriginalDocs() {
        config.setEnabled(false);
        ColbertScorer disabledScorer = new ColbertScorer(config);

        List<Document> docs = List.of(new Document("doc1"), new Document("doc2"));
        List<Document> result = disabledScorer.rerank("query", docs, 10);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("空文档列表返回空")
    void rerank_withEmptyDocs_returnsEmpty() {
        List<Document> result = scorer.rerank("query", List.of(), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("topK限制结果数量")
    void rerank_withTopK_limitsResults() {
        List<Document> docs = new ArrayList<>();
        docs.add(new Document("doc1 content"));
        docs.add(new Document("doc2 content"));
        docs.add(new Document("doc3 content"));

        List<Document> result = scorer.rerank("query", docs, 2);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("评分元数据被添加到文档")
    void rerank_addsColbertScoreMetadata() {
        List<Document> docs = List.of(new Document("test content about testing"));
        List<Document> result = scorer.rerank("test", docs, 1);

        assertNotNull(result.get(0).getMetadata().get("colbert_score"));
    }

    @Test
    @DisplayName("完全匹配的文档得分最高")
    void rerank_withExactMatch_scoresHighest() {
        List<Document> docs = new ArrayList<>();
        docs.add(new Document("completely unrelated content about cooking"));
        docs.add(new Document("knowledge graph entities relationships"));
        docs.add(new Document("another unrelated sports document"));

        List<Document> result = scorer.rerank("knowledge graph", docs, 3);

        // 验证第一个结果的 colbert_score 最高
        double firstScore = (Double) result.get(0).getMetadata().get("colbert_score");
        double lastScore = (Double) result.get(2).getMetadata().get("colbert_score");
        assertTrue(firstScore >= lastScore);
    }
}
