package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.nlp.JiebaNative;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 关键词检索器 — pg_trgm + 应用层中文分词增强版
 * [溯源] 算法优化指南 §2.2: BM25 增强 — pg_trgm + jieba 方案
 *
 * 增强点：
 * 1. pg_trgm similarity() 模糊匹配（trigram 索引）
 * 2. 应用层中文分词（2-4 字滑动窗口 + 停用词过滤）
 * 3. 同义词扩展（应用层词典）
 * 4. BM25 风格 TF*IDF 打分
 */
@Slf4j
@Component
public class KeywordRetriever {

    // [溯源] 算法优化指南 §2.2: 中文停用词
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "里", "为", "什么", "怎么", "如何",
            "吗", "呢", "吧", "啊", "哦", "嗯", "呀", "哈", "嘛", "啦",
            "请", "请告诉我", "告诉我", "时候", "的时候", "主要",
            "哪些", "关于", "一下", "信息", "了解");

    // [溯源] 算法优化指南 §2.2: 同义词词典
    private static final Map<String, List<String>> SYNONYMS = new HashMap<>();
    static {
        SYNONYMS.put("RAG", List.of("检索增强生成", "Retrieval Augmented Generation"));
        SYNONYMS.put("大模型", List.of("LLM", "大语言模型", "Large Language Model"));
        SYNONYMS.put("向量检索", List.of("语义检索", "embedding search"));
        SYNONYMS.put("知识图谱", List.of("KG", "Knowledge Graph"));
        SYNONYMS.put("实体识别", List.of("NER", "命名实体识别"));
        SYNONYMS.put("分块", List.of("chunking", "chunk"));
        SYNONYMS.put("嵌入", List.of("embedding"));
        SYNONYMS.put("提示词", List.of("prompt"));
        SYNONYMS.put("微调", List.of("fine-tuning", "finetune"));
        SYNONYMS.put("幻觉", List.of("hallucination"));
        SYNONYMS.put("召回率", List.of("recall"));
        SYNONYMS.put("精确率", List.of("precision"));
        SYNONYMS.put("机器学习", List.of("ML"));
        SYNONYMS.put("深度学习", List.of("DL"));
        SYNONYMS.put("自然语言处理", List.of("NLP"));
        SYNONYMS.put("计算机视觉", List.of("CV"));
    }

    @Resource
    private JdbcTemplate jdbcTemplate;

    public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query, int topK) {
        if (StrUtil.isBlank(query)) {
            return new ArrayList<>();
        }

        // [溯源] 算法优化指南 §2.2: 应用层中文分词 + 同义词扩展
        List<String> searchTerms = buildSearchTerms(query);
        List<String> dayTerms = extractDayTerms(query);
        List<String> expandedTerms = expandWithSynonyms(searchTerms);

        StringBuilder sql = new StringBuilder(
                "SELECT s.id, s.content, d.knowledge_base_id, s.document_id, " +
                "s.document_name, s.answer, s.position, s.qm_segment_id, s.parent_id, " +
                // [溯源] 算法优化指南 §2.2: pg_trgm similarity 打分
                "GREATEST(" +
                "  COALESCE(similarity(s.content, ?), 0), " +
                "  COALESCE(similarity(d.name, ?), 0) " +
                ") AS trgm_score " +
                "FROM kmc_document_segment s " +
                "JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0 " +
                "WHERE d.knowledge_base_id = ? " +
                "AND s.del_flag = 0 ");

        List<Object> params = new ArrayList<>();
        // pg_trgm similarity 参数
        String normalizedQuery = normalizeForTrgm(query);
        params.add(normalizedQuery);
        params.add(normalizedQuery);
        // knowledge_base_id
        params.add(knowledgeBaseId);

        // Day 文档过滤
        if (!dayTerms.isEmpty()) {
            sql.append("AND (");
            List<String> dayConditions = new ArrayList<>();
            for (String dayTerm : dayTerms) {
                dayConditions.add("d.name ILIKE ?");
                params.add("%" + dayTerm + "%");
            }
            sql.append(String.join(" OR ", dayConditions));
            sql.append(") ");
        }

        // 检索条件：pg_trgm similarity OR ILIKE
        sql.append("AND (");
        List<String> conditions = new ArrayList<>();
        // pg_trgm trigram 匹配（threshold=0.1）
        conditions.add("similarity(s.content, ?) > 0.1");
        params.add(normalizedQuery);
        // ILIKE 兜底
        for (String term : expandedTerms) {
            if (term.length() >= 2) {
                conditions.add("s.content ILIKE ?");
                params.add("%" + term + "%");
            }
        }
        sql.append(String.join(" OR ", conditions));
        sql.append(") ");

        // 排序：trgm_score DESC + position
        sql.append("ORDER BY trgm_score DESC, d.id ASC, s.position ASC NULLS LAST LIMIT ?");
        params.add(Math.max(topK * 10, topK));

        try {
            List<RetrievalResult> results = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                String documentName = rs.getString("document_name");
                String content = rs.getString("content");
                float trgmScore = rs.getFloat("trgm_score");
                float keywordScore = calculateKeywordScore(documentName, content, expandedTerms);
                // [溯源] 算法优化指南 §2.2: BM25 风格混合打分
                float finalScore = trgmScore * 0.6f + keywordScore * 0.4f;
                return RetrievalResult.builder()
                        .segmentId(rs.getLong("id"))
                        .qmSegmentId(rs.getString("qm_segment_id"))
                        .parentSegmentId(rs.getString("parent_id"))
                        .documentId(rs.getLong("document_id"))
                        .documentName(documentName)
                        .content(content)
                        .answer(rs.getString("answer"))
                        .score(finalScore)
                        .source("keyword")
                        .build();
            }, params.toArray());

            if (results == null || results.isEmpty()) {
                return new ArrayList<>();
            }

            return results.stream()
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .toList();
        } catch (Exception e) {
            log.error("Keyword retrieval failed for knowledgeBaseId={}", knowledgeBaseId, e);
            return new ArrayList<>();
        }
    }

    /**
     * [溯源] 算法优化指南 §2.2: 应用层中文分词
     * 滑动窗口 2-4 字切分 + 停用词过滤
     */
    /**
     * [溯源] 算法优化指南 Phase 2: 应用层中文分词 + jieba-rs JNI
     * 优先使用 jieba-rs（20-27x 快于 Java），降级到滑动窗口
     */
    private List<String> buildSearchTerms(String queryText) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(queryText.trim());

        // 提取英文词
        Pattern enPattern = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{2,}");
        Matcher enMatcher = enPattern.matcher(queryText);
        while (enMatcher.find()) {
            String word = enMatcher.group().toLowerCase();
            if (!STOP_WORDS.contains(word)) {
                terms.add(word);
            }
        }

        // [溯源] Phase 2: 优先使用 jieba-rs 中文分词
        String[] jiebaTokens = JiebaNative.safeCut(queryText);
        if (jiebaTokens != null && jiebaTokens.length > 0) {
            for (String token : jiebaTokens) {
                if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                    terms.add(token);
                }
            }
        } else {
            // 降级：滑动窗口 2-4 字
            String chineseOnly = queryText.replaceAll("[^\\p{IsHan}]", " ").trim();
            for (int len = 4; len >= 2; len--) {
                for (int i = 0; i <= chineseOnly.length() - len; i++) {
                    String token = chineseOnly.substring(i, i + len);
                    if (!STOP_WORDS.contains(token)) {
                        terms.add(token);
                    }
                }
            }
        }

        // DayXX 标准化
        for (String token : new ArrayList<>(terms)) {
            if (token.matches("(?i)day0?\\d+")) {
                String number = token.replaceAll("(?i)day0?", "");
                try {
                    terms.add(String.format("Day%02d", Integer.parseInt(number)));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return new ArrayList<>(terms);
    }

    /**
     * [溯源] 算法优化指南 §2.2: 同义词扩展
     */
    private List<String> expandWithSynonyms(List<String> terms) {
        Set<String> expanded = new LinkedHashSet<>(terms);
        for (String term : terms) {
            List<String> synonyms = SYNONYMS.get(term);
            if (synonyms != null) {
                expanded.addAll(synonyms);
            }
            // 大小写不敏感匹配
            for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(term)) {
                    expanded.addAll(entry.getValue());
                }
            }
        }
        return new ArrayList<>(expanded);
    }

    private List<String> extractDayTerms(String queryText) {
        LinkedHashSet<String> dayTerms = new LinkedHashSet<>();
        Matcher matcher = Pattern
                .compile("(?i)day\\s*0?(\\d{1,2})|第\\s*0?(\\d{1,2})\\s*[天日]")
                .matcher(queryText);
        while (matcher.find()) {
            String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                int day = Integer.parseInt(number);
                dayTerms.add(String.format("Day%02d", day));
                dayTerms.add("Day" + day);
            } catch (NumberFormatException ignored) {
            }
        }
        return new ArrayList<>(dayTerms);
    }

    /**
     * [溯源] 算法优化指南 §2.2: BM25 风格 TF*IDF 打分
     * 文档名命中权重 3x，内容命中使用 log(1+count) 作为 TF 近似
     */
    private float calculateKeywordScore(String documentName, String content, List<String> searchTerms) {
        float score = 0F;
        String safeDocName = StrUtil.blankToDefault(documentName, "").toLowerCase();
        String safeContent = StrUtil.blankToDefault(content, "").toLowerCase();
        for (String term : searchTerms) {
            if (StrUtil.isBlank(term)) continue;
            String termLower = term.toLowerCase();
            if (safeDocName.contains(termLower)) {
                score += 3F;
            }
            int occurrences = countOccurrences(safeContent, termLower);
            if (occurrences > 0) {
                score += (float) (1.0 + Math.log(1 + occurrences));
            }
        }
        return score;
    }

    private int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private String normalizeForTrgm(String query) {
        if (query == null) return "";
        return query.replaceAll("\\s+", " ").trim();
    }
}
