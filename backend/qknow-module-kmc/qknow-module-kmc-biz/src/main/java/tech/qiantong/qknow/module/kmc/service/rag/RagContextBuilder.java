package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 上下文组装器 (Phase 14 升级重构)
 * <p>
 * 核心特性：
 * 1. Parent-Child Small-to-Big 展开与 Max-Pooling 得分继承 (彻底消除 score=0.0)
 * 2. 严格遵循精排次序保序装配与去重
 * 3. 20KB 上下文预算自适应优雅降级回 Child (避免大 Parent 挤出后续高置信度切片)
 * </p>
 *
 * @author qknow
 */
@Slf4j
@Component
public class RagContextBuilder {

    // [溯源] 算法优化指南 §2.6: 上下文预算从 12KB 增加到 20KB
    @Value("${hermes.rag.context.max-bytes:20000}")
    private int maxContextBytes = 20000;

    @Value("${hermes.rag.context.max-tokens:0}")
    private int maxContextTokens = 0;

    public int getMaxContextBytes() { return maxContextBytes; }

    @Resource
    private JdbcTemplate jdbcTemplate;

    public String buildContext(List<RetrievalResult> results, boolean expandAdjacent) {
        return buildContextWithEmitted(results, expandAdjacent).getContext();
    }

    public ContextBuildResult buildContextWithEmitted(List<RetrievalResult> results, boolean expandAdjacent) {
        if (results == null || results.isEmpty()) {
            return new ContextBuildResult("", Collections.emptyList());
        }

        // 1. 回溯 Parent 并实施 Max-Pooling 分数继承与保序
        List<CandidateUnit> candidates = expandAndPoolScores(results);

        // 2. 若没有 Parent 且开启了 expandAdjacent，尝试相邻块扩展
        if (expandAdjacent && candidates.stream().noneMatch(u -> "parent".equals(u.parentResult.getSource()))) {
            List<RetrievalResult> adjacentResults = expandWithAdjacentSegments(results);
            if (adjacentResults != results) {
                candidates = adjacentResults.stream().map(r -> new CandidateUnit(r, r)).collect(Collectors.toList());
            }
        }

        // 3. 内容去重
        candidates = deduplicateCandidates(candidates);

        // 4. 严格在 20KB 预算内自适应装配与优雅降级
        StringBuilder sb = new StringBuilder();
        List<RetrievalResult> emittedResults = new ArrayList<>();
        int usedBytes = 0;
        int usedTokens = 0;
        int index = 1;

        for (CandidateUnit unit : candidates) {
            RetrievalResult targetToEmit = unit.parentResult;
            String entry = formatEntry(index, targetToEmit);
            int entryBytes = entry.getBytes(StandardCharsets.UTF_8).length;

            // 优雅降级：若 Parent 超出剩余预算，但其最佳 Child 未超出预算，降级为 Child 原文
            if (usedBytes + entryBytes > maxContextBytes
                    && unit.bestChildResult != null
                    && unit.bestChildResult != unit.parentResult) {
                String childEntry = formatEntry(index, unit.bestChildResult);
                int childBytes = childEntry.getBytes(StandardCharsets.UTF_8).length;
                if (usedBytes + childBytes <= maxContextBytes) {
                    log.info("Parent 段落 (id={}) 超出剩余预算 ({} > {} 字节)，触发优雅降级装配高分 Child (id={})",
                            targetToEmit.getSegmentId(), entryBytes, maxContextBytes - usedBytes, unit.bestChildResult.getSegmentId());
                    targetToEmit = unit.bestChildResult;
                    entry = childEntry;
                    entryBytes = childBytes;
                }
            }

            // 最终硬预算校验
            if (usedBytes + entryBytes > maxContextBytes) {
                break;
            }
            int entryTokens = estimateTokens(entry);
            if (maxContextTokens > 0 && usedTokens + entryTokens > maxContextTokens) {
                break;
            }

            sb.append(entry);
            emittedResults.add(targetToEmit);
            usedBytes += entryBytes;
            usedTokens += entryTokens;
            index++;
        }

        return new ContextBuildResult(sb.toString(), Collections.unmodifiableList(emittedResults));
    }

    public static class ContextBuildResult {
        private final String context;
        private final List<RetrievalResult> emittedResults;

        public ContextBuildResult(String context, List<RetrievalResult> emittedResults) {
            this.context = context;
            this.emittedResults = emittedResults != null ? emittedResults : Collections.emptyList();
        }

        public String getContext() {
            return context;
        }

        public List<RetrievalResult> getEmittedResults() {
            return emittedResults;
        }
    }

    private static class CandidateUnit {
        RetrievalResult parentResult;
        RetrievalResult bestChildResult;

        CandidateUnit(RetrievalResult parentResult, RetrievalResult bestChildResult) {
            this.parentResult = parentResult;
            this.bestChildResult = bestChildResult;
        }
    }

    /**
     * 回溯 Parent 并执行 Max-Pooling 得分继承与保序
     */
    private List<CandidateUnit> expandAndPoolScores(List<RetrievalResult> results) {
        List<String> parentIds = results.stream()
                .map(RetrievalResult::getParentSegmentId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());

        Map<String, RetrievalResult> parentMap = new HashMap<>();
        if (!parentIds.isEmpty() && jdbcTemplate != null) {
            String placeholders = parentIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT s.id, s.qm_segment_id, s.content, s.document_id, s.document_name, s.answer, s.position " +
                    "FROM kmc_document_segment s " +
                    "WHERE s.del_flag = 0 AND s.qm_segment_id IN (" + placeholders + ") " +
                    "ORDER BY s.document_id ASC, s.position ASC NULLS LAST, s.id ASC";

            try {
                List<RetrievalResult> list = jdbcTemplate.query(sql, (rs, rowNum) -> RetrievalResult.builder()
                        .segmentId(rs.getLong("id"))
                        .qmSegmentId(rs.getString("qm_segment_id"))
                        .documentId(rs.getLong("document_id"))
                        .documentName(rs.getString("document_name"))
                        .content(rs.getString("content"))
                        .answer(rs.getString("answer"))
                        .score(0.0)
                        .source("parent")
                        .build(), parentIds.toArray());

                if (list != null) {
                    for (RetrievalResult p : list) {
                        if (p != null && StrUtil.isNotBlank(p.getQmSegmentId())) {
                            parentMap.put(p.getQmSegmentId(), p);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to query parent segments", e);
            }
        }

        Map<String, CandidateUnit> parentGroup = new LinkedHashMap<>();
        List<CandidateUnit> candidates = new ArrayList<>();

        for (RetrievalResult hit : results) {
            String parentId = hit.getParentSegmentId();
            if (StrUtil.isNotBlank(parentId) && parentMap.containsKey(parentId)) {
                CandidateUnit existingUnit = parentGroup.get(parentId);
                if (existingUnit == null) {
                    RetrievalResult parent = parentMap.get(parentId);
                    // 深度克隆一份 Parent，避免污染
                    RetrievalResult clonedParent = RetrievalResult.builder()
                            .segmentId(parent.getSegmentId())
                            .qmSegmentId(parent.getQmSegmentId())
                            .documentId(parent.getDocumentId())
                            .documentName(parent.getDocumentName())
                            .content(parent.getContent())
                            .answer(parent.getAnswer())
                            .source("parent")
                            .score(hit.getScore()) // Max-Pooling 初值赋为该命中 Child 的得分
                            .build();

                    CandidateUnit unit = new CandidateUnit(clonedParent, hit);
                    parentGroup.put(parentId, unit);
                    candidates.add(unit);
                } else {
                    // 同一 Parent 被多次命中，执行 Max-Pooling 聚合最高分
                    double currentMax = existingUnit.parentResult.getScore();
                    double hitScore = hit.getScore();
                    if (hitScore > currentMax) {
                        existingUnit.parentResult.setScore(hitScore);
                        existingUnit.bestChildResult = hit;
                    }
                }
            } else {
                candidates.add(new CandidateUnit(hit, hit));
            }
        }

        return candidates;
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

        if (segmentIds.isEmpty() || jdbcTemplate == null) {
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

    private List<CandidateUnit> deduplicateCandidates(List<CandidateUnit> list) {
        Set<String> seen = new LinkedHashSet<>();
        List<CandidateUnit> deduped = new ArrayList<>(list.size());
        for (CandidateUnit unit : list) {
            String hash = contentHash(unit.parentResult.getContent());
            if (seen.add(hash)) {
                deduped.add(unit);
            }
        }
        return deduped;
    }

    private String contentHash(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        return String.valueOf(content.hashCode());
    }

    private int estimateTokens(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        int tokens = 0;
        StringBuilder asciiWord = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < 128 && Character.isLetterOrDigit(ch)) {
                asciiWord.append(ch);
                continue;
            }
            if (!asciiWord.isEmpty()) {
                tokens++;
                asciiWord.setLength(0);
            }
            if (!Character.isWhitespace(ch)) {
                tokens += 2;
            }
        }
        if (!asciiWord.isEmpty()) {
            tokens++;
        }
        return tokens;
    }

    private String formatEntry(int index, RetrievalResult result) {
        String docName = StrUtil.blankToDefault(result.getDocumentName(), "未知文档");
        String segmentId = result.getSegmentId() != null ? String.valueOf(result.getSegmentId()) : "?";
        String content = StrUtil.blankToDefault(result.getContent(), "(空内容)");

        StringBuilder sb = new StringBuilder();
        sb.append("[来源 ").append(index).append("] ").append(docName)
                .append(" / segmentId=").append(segmentId);
        if (result.getScore() > 0.0) {
            sb.append(String.format(Locale.ROOT, " / score=%.4f", result.getScore()));
        }
        if (StrUtil.isNotBlank(result.getParentSegmentId())) {
            sb.append(" / parentId=").append(result.getParentSegmentId());
        }
        sb.append("\n");
        sb.append("内容：").append(content).append("\n\n");
        return sb.toString();
    }
}
