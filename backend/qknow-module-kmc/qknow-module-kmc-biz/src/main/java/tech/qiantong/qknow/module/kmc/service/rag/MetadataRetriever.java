package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MetadataRetriever {

    private static final double HIGH_CONFIDENCE_SCORE = 10.0;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public List<RetrievalResult> retrieve(Long knowledgeBaseId, QueryIntent queryIntent, int topK) {
        if (queryIntent == null) {
            return new ArrayList<>();
        }

        boolean hasDayNo = queryIntent.getDayNo() != null;
        boolean hasDocName = StrUtil.isNotBlank(queryIntent.getDocName());
        boolean hasEntities = queryIntent.getKeywords() != null && !queryIntent.getKeywords().isEmpty();

        if (!hasDayNo && !hasDocName && !hasEntities) {
            return new ArrayList<>();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT s.id, s.content, d.knowledge_base_id, s.document_id, " +
                "s.document_name, s.answer, s.position, s.qm_segment_id, s.parent_id " +
                "FROM kmc_document_segment s " +
                "JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0 " +
                "WHERE d.knowledge_base_id = ? " +
                "AND s.del_flag = 0 ");

        List<Object> params = new ArrayList<>();
        params.add(knowledgeBaseId);

        if (hasDayNo) {
            String dayPattern = String.format("Day%02d%%", queryIntent.getDayNo());
            sql.append("AND d.name ILIKE ? ");
            params.add(dayPattern);
        } else if (hasDocName) {
            sql.append("AND d.name ILIKE ? ");
            params.add("%" + queryIntent.getDocName() + "%");
        }

        if (hasEntities) {
            List<String> entityTerms = queryIntent.getKeywords().stream()
                    .filter(StrUtil::isNotBlank)
                    .limit(5)
                    .collect(Collectors.toList());
            if (!entityTerms.isEmpty()) {
                sql.append("AND EXISTS (SELECT 1 FROM kmc_segment_entity_metadata em ")
                        .append("WHERE em.segment_id = s.id AND (");
                List<String> entityConditions = new ArrayList<>();
                for (String term : entityTerms) {
                    entityConditions.add("em.entities::text ILIKE ?");
                    params.add("%" + term + "%");
                }
                sql.append(String.join(" OR ", entityConditions)).append(")) ");
            }
        }

        sql.append("ORDER BY d.id ASC, s.position ASC NULLS LAST, s.id ASC LIMIT ?");
        params.add(topK);

        try {
            List<RetrievalResult> results = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                return RetrievalResult.builder()
                        .segmentId(rs.getLong("id"))
                        .qmSegmentId(rs.getString("qm_segment_id"))
                        .parentSegmentId(rs.getString("parent_id"))
                        .documentId(rs.getLong("document_id"))
                        .documentName(rs.getString("document_name"))
                        .content(rs.getString("content"))
                        .answer(rs.getString("answer"))
                        .score(HIGH_CONFIDENCE_SCORE)
                        .source("metadata")
                        .build();
            }, params.toArray());

            return results != null ? results : new ArrayList<>();
        } catch (Exception e) {
            log.error("Metadata retrieval failed for knowledgeBaseId={}", knowledgeBaseId, e);
            return new ArrayList<>();
        }
    }
}
