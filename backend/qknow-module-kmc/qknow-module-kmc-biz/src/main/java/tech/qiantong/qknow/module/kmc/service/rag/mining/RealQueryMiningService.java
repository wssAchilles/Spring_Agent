package tech.qiantong.qknow.module.kmc.service.rag.mining;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.sanitizer.QuerySanitizer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 线上真实 Query 四维漏斗难例挖掘服务 (Phase 16 Real Query Mining Service)
 *
 * 负责从召回日志中按照：
 * 1. 零召回 (Zero-Recall)
 * 2. 低置信度 (Low-Confidence, Cosine < 0.60)
 * 3. CRAG 歧义与扩展重写 (CRAG-Ambiguous)
 * 4. 多轮追问澄清 (Follow-up)
 * 四维漏斗挖掘代表性难例，并结合 QuerySanitizer 执行语法保真脱敏，生成不可变评测样本。
 *
 * @author qknow
 */
@Slf4j
@Service
public class RealQueryMiningService {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private QuerySanitizer querySanitizer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 难例分类枚举
     */
    public enum MiningCategory {
        ZERO_RECALL("zero_recall", "零召回难例"),
        LOW_CONFIDENCE("low_confidence", "低置信度召回"),
        CRAG_AMBIGUOUS("crag_ambiguous", "CRAG歧义重写难例"),
        FOLLOW_UP("follow_up", "多轮追问长尾难例"),
        NEGATIVE_FEEDBACK("negative_feedback", "用户负反馈强监督难例"),
        GENERAL("general", "通用样本");

        private final String code;
        private final String desc;

        MiningCategory(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 原始召回日志 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawRecallLogDTO {
        private Long id;
        private Long workspaceId;
        private Long knowledgeBaseId;
        private String query;
        private String result;
        private Double topScore;
        private Boolean cragAmbiguous;
        private Boolean isFollowUp;
        private Date recallTime;
    }

    /**
     * 挖掘出的脱敏评测用例 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MinedQueryItem {
        private String id;
        private String query;
        private String rawQuery;
        private String category;
        private Long expectedKbId;
        private List<String> expectedContexts;
        private Double confidenceScore;
        private String split;
    }

    /**
     * 对内存中的日志批次执行难例筛选与脱敏
     */
    public List<MinedQueryItem> mineAndSanitize(List<RawRecallLogDTO> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        QuerySanitizer sanitizer = this.querySanitizer != null ? this.querySanitizer : new QuerySanitizer();
        Map<String, String> sessionMapping = new ConcurrentHashMap<>();
        List<MinedQueryItem> results = new ArrayList<>();

        for (RawRecallLogDTO logItem : logs) {
            if (logItem.getQuery() == null || logItem.getQuery().isBlank()) {
                continue;
            }

            MiningCategory category = classifyCategory(logItem);
            // 脱敏处理
            String sanitizedQuery = sanitizer.sanitize(logItem.getQuery(), QuerySanitizer.MaskMode.STRUCTURE_PRESERVING, sessionMapping);

            List<String> contexts = extractContextsFromResult(logItem.getResult());

            MinedQueryItem item = MinedQueryItem.builder()
                    .id("real-q-" + (logItem.getId() != null ? logItem.getId() : UUID.randomUUID().toString().substring(0, 8)))
                    .query(sanitizedQuery)
                    .rawQuery(logItem.getQuery())
                    .category(category.getCode())
                    .expectedKbId(logItem.getKnowledgeBaseId())
                    .expectedContexts(contexts)
                    .confidenceScore(logItem.getTopScore() != null ? logItem.getTopScore() : 0.0)
                    .split("real-holdout")
                    .build();

            results.add(item);
        }

        return results;
    }

    /**
     * 四维漏斗难例分类核心逻辑
     */
    public MiningCategory classifyCategory(RawRecallLogDTO logItem) {
        String result = logItem.getResult();

        // 漏斗 1: 零召回判断
        if (result == null || result.isBlank() || "[]".equals(result.trim()) || "{}".equals(result.trim())
                || result.contains("\"sources\":[]") || result.contains("\"context\":\"\"")) {
            return MiningCategory.ZERO_RECALL;
        }

        // 漏斗 2: 低置信度判断 (Cosine 相似度低于 0.60)
        if (logItem.getTopScore() != null && logItem.getTopScore() > 0 && logItem.getTopScore() < 0.60) {
            return MiningCategory.LOW_CONFIDENCE;
        }

        // 漏斗 3: CRAG 歧义与扩展判断
        if (Boolean.TRUE.equals(logItem.getCragAmbiguous())
                || (result != null && (result.contains("AMBIGUOUS") || result.contains("\"ambiguous\":true")))) {
            return MiningCategory.CRAG_AMBIGUOUS;
        }

        // 漏斗 4: 多轮追问与长尾判断
        if (Boolean.TRUE.equals(logItem.getIsFollowUp()) || isFollowUpQueryPattern(logItem.getQuery())) {
            return MiningCategory.FOLLOW_UP;
        }

        return MiningCategory.GENERAL;
    }

    private boolean isFollowUpQueryPattern(String query) {
        if (query == null) return false;
        String q = query.trim();
        return q.startsWith("然后呢") || q.startsWith("接着说") || q.startsWith("那如果")
                || q.startsWith("之前说的") || q.startsWith("继续") || q.contains("上面提到的");
    }

    private List<String> extractContextsFromResult(String result) {
        if (result == null || result.isBlank()) {
            return Collections.emptyList();
        }
        // 简单提取上下文标识或切片片段
        List<String> contexts = new ArrayList<>();
        if (result.contains("doc_") || result.contains("seg_")) {
            Matcher m = Pattern.compile("(doc_[a-zA-Z0-9_]+|seg_[a-zA-Z0-9_]+)").matcher(result);
            while (m.find()) {
                contexts.add(m.group(1));
            }
        }
        return contexts;
    }

    /**
     * 导出为不可变 JSONL 格式字符串
     */
    public String exportToJsonl(List<MinedQueryItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MinedQueryItem item : items) {
            try {
                sb.append(objectMapper.writeValueAsString(item)).append("\n");
            } catch (Exception e) {
                log.warn("[RealQueryMiningService] 序列化失败: {}", item.getId(), e);
            }
        }
        return sb.toString();
    }

    /**
     * 第 5 漏斗：用户负反馈 (Downvote / Correction) 强监督难例直接入库与脱敏沉淀
     */
    public MinedQueryItem ingestNegativeFeedback(String rawQuery, Long kbId, String expectedContext, String comment) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        QuerySanitizer sanitizer = this.querySanitizer != null ? this.querySanitizer : new QuerySanitizer();
        String sanitizedQuery = sanitizer.sanitize(rawQuery);

        List<String> contexts = new ArrayList<>();
        if (expectedContext != null && !expectedContext.isBlank()) {
            contexts.add(expectedContext);
        }

        return MinedQueryItem.builder()
                .id("mine-neg-" + UUID.randomUUID().toString().substring(0, 8))
                .rawQuery(rawQuery)
                .query(sanitizedQuery)
                .category(MiningCategory.NEGATIVE_FEEDBACK.getCode())
                .expectedKbId(kbId)
                .expectedContexts(contexts)
                .confidenceScore(0.1)
                .split("test")
                .build();
    }
}
