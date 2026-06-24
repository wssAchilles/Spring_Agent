package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RagContextBuilder {

    private static final int MAX_CONTEXT_BYTES = 12000;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public String buildContext(List<RetrievalResult> results, boolean expandAdjacent) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        List<RetrievalResult> expanded = results;
        if (expandAdjacent) {
            expanded = expandWithAdjacentSegments(results);
        }

        expanded = deduplicateByContent(expanded);

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (RetrievalResult result : expanded) {
            String entry = formatEntry(index, result);
            if (sb.length() + entry.getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES) {
                break;
            }
            sb.append(entry);
            index++;
        }
        return sb.toString();
    }

    private List<RetrievalResult> expandWithAdjacentSegments(List<RetrievalResult> results) {
        Set<Long> existingIds = results.stream()
                .map(RetrievalResult::getSegmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Long> segmentIds = results.stream()
                .map(RetrievalResult::getSegmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (segmentIds.isEmpty()) {
            return results;
        }

        String idList = segmentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String sql = "SELECT s.id, s.content, s.document_id, s.document_name, s.answer, s.position " +
                "FROM kmc_document_segment s " +
                "WHERE s.del_flag = 0 " +
                "AND s.id IN (" +
                "  SELECT CASE WHEN s2.position > 0 THEN " +
                "    (SELECT id FROM kmc_document_segment WHERE document_id = s2.document_id AND position = s2.position - 1 AND del_flag = 0 LIMIT 1) " +
                "  ELSE NULL END " +
                "  FROM kmc_document_segment s2 WHERE s2.id IN (" + idList + ") AND s2.position IS NOT NULL " +
                "  UNION " +
                "  SELECT (SELECT id FROM kmc_document_segment WHERE document_id = s2.document_id AND position = s2.position + 1 AND del_flag = 0 LIMIT 1) " +
                "  FROM kmc_document_segment s2 WHERE s2.id IN (" + idList + ") AND s2.position IS NOT NULL " +
                ")";

        try {
            List<RetrievalResult> adjacent = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Long segId = rs.getLong("id");
                if (existingIds.contains(segId)) {
                    return null;
                }
                return RetrievalResult.builder()
                        .segmentId(segId)
                        .documentId(rs.getLong("document_id"))
                        .documentName(rs.getString("document_name"))
                        .content(rs.getString("content"))
                        .answer(rs.getString("answer"))
                        .score(0.0)
                        .source("adjacent")
                        .build();
            });

            List<RetrievalResult> merged = new ArrayList<>(results);
            if (adjacent != null) {
                for (RetrievalResult r : adjacent) {
                    if (r != null) {
                        merged.add(r);
                    }
                }
            }
            return merged;
        } catch (Exception e) {
            log.warn("Failed to expand adjacent segments, returning original results", e);
            return results;
        }
    }

    private List<RetrievalResult> deduplicateByContent(List<RetrievalResult> results) {
        Set<String> seen = new LinkedHashSet<>();
        List<RetrievalResult> deduplicated = new ArrayList<>(results.size());
        for (RetrievalResult result : results) {
            String hash = contentHash(result.getContent());
            if (seen.add(hash)) {
                deduplicated.add(result);
            }
        }
        return deduplicated;
    }

    private String contentHash(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        return String.valueOf(content.hashCode());
    }

    private String formatEntry(int index, RetrievalResult result) {
        String docName = StrUtil.blankToDefault(result.getDocumentName(), "未知文档");
        String segmentId = result.getSegmentId() != null ? String.valueOf(result.getSegmentId()) : "?";
        String content = StrUtil.blankToDefault(result.getContent(), "(空内容)");

        StringBuilder sb = new StringBuilder();
        sb.append("[来源 ").append(index).append("] ").append(docName)
                .append(" / segmentId=").append(segmentId).append("\n");
        sb.append("内容：").append(content).append("\n\n");
        return sb.toString();
    }
}
