package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class KeywordRetriever {

    private static final Set<String> STOP_WORDS = Set.of(
            "请", "请告诉我", "告诉我", "在", "时候", "的时候", "我", "主要",
            "哪些", "什么", "关于", "一下", "信息", "了解");

    @Resource
    private JdbcTemplate jdbcTemplate;

    public List<RetrievalResult> retrieve(Long knowledgeBaseId, String query, int topK) {
        if (StrUtil.isBlank(query)) {
            return new ArrayList<>();
        }

        List<String> searchTerms = buildSearchTerms(query);
        List<String> dayTerms = extractDayTerms(query);

        StringBuilder sql = new StringBuilder(
                "SELECT s.id, s.content, d.knowledge_base_id, s.document_id, " +
                "s.document_name, s.answer, s.position " +
                "FROM kmc_document_segment s " +
                "JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0 " +
                "WHERE d.knowledge_base_id = ? " +
                "AND s.del_flag = 0 ");

        List<Object> params = new ArrayList<>();
        params.add(knowledgeBaseId);

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

        sql.append("AND (");
        List<String> conditions = new ArrayList<>();
        conditions.add("s.content_tsv @@ plainto_tsquery('simple', ?)");
        params.add(query);
        for (String term : searchTerms) {
            conditions.add("d.name ILIKE ?");
            params.add("%" + term + "%");
            conditions.add("s.content ILIKE ?");
            params.add("%" + term + "%");
        }
        sql.append(String.join(" OR ", conditions));
        sql.append(") ");

        sql.append("ORDER BY d.id ASC, s.position ASC NULLS LAST, s.id ASC LIMIT ?");
        params.add(Math.max(topK * 20, topK));

        try {
            List<RetrievalResult> results = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                String documentName = rs.getString("document_name");
                String content = rs.getString("content");
                float score = calculateScore(documentName, content, searchTerms);
                return RetrievalResult.builder()
                        .segmentId(rs.getLong("id"))
                        .documentId(rs.getLong("document_id"))
                        .documentName(documentName)
                        .content(content)
                        .answer(rs.getString("answer"))
                        .score(score)
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

    private List<String> buildSearchTerms(String queryText) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(queryText.trim());
        String normalized = queryText.replaceAll("[^\\p{IsHan}A-Za-z0-9]+", " ");
        for (String token : normalized.split("\\s+")) {
            if (StrUtil.isBlank(token) || token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            terms.add(token);
            if (token.matches("(?i)day0?\\d+")) {
                String number = token.replaceAll("(?i)day0?", "");
                try {
                    terms.add(String.format("Day%02d", Integer.parseInt(number)));
                    terms.add("Day " + String.format("%02d", Integer.parseInt(number)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new ArrayList<>(terms);
    }

    private List<String> extractDayTerms(String queryText) {
        LinkedHashSet<String> dayTerms = new LinkedHashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)day\\s*0?(\\d{1,2})|第\\s*0?(\\d{1,2})\\s*[天日]")
                .matcher(queryText);
        while (matcher.find()) {
            String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                int day = Integer.parseInt(number);
                dayTerms.add(String.format("Day%02d", day));
                dayTerms.add("Day " + String.format("%02d", day));
                dayTerms.add("Day" + day);
                dayTerms.add("Day " + day);
            } catch (NumberFormatException ignored) {
            }
        }
        return new ArrayList<>(dayTerms);
    }

    private float calculateScore(String documentName, String content, List<String> searchTerms) {
        float score = 0F;
        String safeDocName = StrUtil.blankToDefault(documentName, "");
        String safeContent = StrUtil.blankToDefault(content, "");
        for (String term : searchTerms) {
            if (StrUtil.isBlank(term)) {
                continue;
            }
            if (safeDocName.contains(term)) {
                score += 3F;
            }
            if (safeContent.contains(term)) {
                score += 1F;
            }
        }
        return score;
    }
}
