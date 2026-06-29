package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ColBERT 风格粗排层
 * 使用 token-level 延迟交互（MaxSim）进行粗排
 * 作为 DashScope Rerank 的前置过滤层
 */
@Slf4j
@Component
public class ColbertScorer {

    private final ColbertConfig config;

    public ColbertScorer(ColbertConfig config) {
        this.config = config;
    }

    /**
     * 对文档进行 ColBERT 风格粗排
     * 使用 n-gram 重叠度作为 MaxSim 的近似
     */
    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (!config.isEnabled() || documents == null || documents.isEmpty()) {
            return documents;
        }

        List<String> queryNgrams = extractNgrams(query, config.getNgramSize());

        List<ScoredDocument> scored = new ArrayList<>();
        for (Document doc : documents) {
            List<String> docNgrams = extractNgrams(doc.getText(), config.getNgramSize());
            double maxSim = computeMaxSim(queryNgrams, docNgrams);
            scored.add(new ScoredDocument(doc, maxSim));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<Document> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            Document doc = scored.get(i).document;
            doc.getMetadata().put("colbert_score", scored.get(i).score);
            result.add(doc);
        }

        log.debug("ColBERT rerank: {} -> {} documents", documents.size(), result.size());
        return result;
    }

    /**
     * MaxSim 近似：查询 n-gram 与文档 n-gram 的最大重叠率
     */
    private double computeMaxSim(List<String> queryNgrams, List<String> docNgrams) {
        if (queryNgrams.isEmpty() || docNgrams.isEmpty()) return 0.0;

        int matches = 0;
        for (String qn : queryNgrams) {
            for (String dn : docNgrams) {
                if (qn.equals(dn)) {
                    matches++;
                    break;
                }
            }
        }
        return (double) matches / queryNgrams.size();
    }

    private List<String> extractNgrams(String text, int n) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.toLowerCase().replaceAll("[^\\w\\s]", "").replaceAll("\\s+", " ").trim();
        String[] words = normalized.split(" ");
        List<String> ngrams = new ArrayList<>();
        for (int i = 0; i <= words.length - n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j > 0) sb.append(" ");
                sb.append(words[i + j]);
            }
            ngrams.add(sb.toString());
        }
        return ngrams;
    }

    @Data
    private static class ScoredDocument {
        private final Document document;
        private final double score;
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "hermes.rag.colbert")
    public static class ColbertConfig {
        private boolean enabled = false;
        private int ngramSize = 3;
    }
}
