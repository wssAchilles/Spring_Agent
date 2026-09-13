package tech.qiantong.qknow.module.kb.service.agent.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.MultiKbRetrievalCoordinator.SingleKbRecallResult;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 跨知识库得分校准与全局 RRF 精排去重引擎 (Phase 17)
 * <p>
 * 核心特性：
 * 1. 置信度底线门禁 (Score Floor $\ge 0.40$)，直接剔除噪点库低分碎片
 * 2. 倒数排名融合 (RRF, $k=60$)，消除不同知识库容量与分布漂移带来的极值虚高
 * 3. 全局内容指纹 (MD5) 去重，消除跨库复制段落冗余
 * 4. 统一跨库来源溯源打标与全局 20KB 硬预算熔断截断控制
 * </p>
 */
@Slf4j
@Component
public class CrossKbScoreCalibrator {

    public static final int RRF_K = 60;

    @Value("${qknow.rag.multi-kb.score-floor:0.40}")
    private double scoreFloor = 0.40D;

    @Value("${hermes.rag.context.max-bytes:20000}")
    private int maxGlobalContextBytes = 20000;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalibratedSegment {
        private Long knowledgeId;
        private String knowledgeName;
        private String documentId;
        private String documentName;
        private String segmentId;
        private String content;
        private Double rawScore;
        private double rrfScore;
        private String contentFingerprint;
        private String ragContext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MergedContextResult {
        private String formattedContext;
        private List<CalibratedSegment> emittedSegments;
        private int totalUsedBytes;
        private int totalCandidates;
    }

    /**
     * 执行跨库结果校准、RRF 精排与 20KB 预算上下文生成
     */
    public MergedContextResult calibrateAndAssemble(List<SingleKbRecallResult> kbRecallList) {
        if (kbRecallList == null || kbRecallList.isEmpty()) {
            return MergedContextResult.builder()
                    .formattedContext("")
                    .emittedSegments(Collections.emptyList())
                    .totalUsedBytes(0)
                    .totalCandidates(0)
                    .build();
        }

        // 1. 过滤底线门禁，按库提取有序候选
        Map<String, CalibratedSegment> uniqueSegmentMap = new LinkedHashMap<>();
        Map<String, Map<Long, Integer>> segmentRanksPerKb = new HashMap<>();

        int totalRawHits = 0;
        for (SingleKbRecallResult kbResult : kbRecallList) {
            if (!kbResult.isSuccess() || kbResult.getResults() == null) {
                continue;
            }
            List<RetrieveResult> hits = kbResult.getResults();
            totalRawHits += hits.size();

            int rank = 1;
            for (RetrieveResult hit : hits) {
                Double score = hit.getScore() != null ? hit.getScore().doubleValue() : 0.0D;
                // 门禁：低于底线相似度直接丢弃
                if (score < scoreFloor) {
                    continue;
                }

                String fingerprint = calculateFingerprint(hit.getContent());
                String uniqueKey = hit.getId() != null ? String.valueOf(hit.getId()) : fingerprint;

                CalibratedSegment seg = uniqueSegmentMap.computeIfAbsent(uniqueKey, k -> CalibratedSegment.builder()
                        .knowledgeId(kbResult.getKnowledgeId())
                        .knowledgeName(kbResult.getKnowledgeName())
                        .documentId(hit.getDocumentId())
                        .documentName(hit.getDocumentName())
                        .segmentId(hit.getId())
                        .content(hit.getContent())
                        .rawScore(score)
                        .contentFingerprint(fingerprint)
                        .ragContext(hit.getRagContext())
                        .build());

                segmentRanksPerKb.computeIfAbsent(uniqueKey, k -> new HashMap<>())
                        .put(kbResult.getKnowledgeId(), rank);
                rank++;
            }
        }

        if (uniqueSegmentMap.isEmpty()) {
            return MergedContextResult.builder()
                    .formattedContext("")
                    .emittedSegments(Collections.emptyList())
                    .totalUsedBytes(0)
                    .totalCandidates(totalRawHits)
                    .build();
        }

        // 2. 计算各切片的 RRF 得分: RRF(d) = sum( 1 / (60 + rank) )
        for (Map.Entry<String, CalibratedSegment> entry : uniqueSegmentMap.entrySet()) {
            String key = entry.getKey();
            CalibratedSegment seg = entry.getValue();
            Map<Long, Integer> ranks = segmentRanksPerKb.get(key);

            double rrf = 0.0D;
            if (ranks != null) {
                for (int rank : ranks.values()) {
                    rrf += 1.0D / (RRF_K + rank);
                }
            }
            seg.setRrfScore(rrf);
        }

        // 3. 全局按 RRF 得分倒序排序
        List<CalibratedSegment> sortedList = uniqueSegmentMap.values().stream()
                .sorted(Comparator.comparingDouble(CalibratedSegment::getRrfScore).reversed())
                .toList();

        // 4. 内容指纹去重（消除不同文档中复制的相同段落）
        Set<String> seenFingerprints = new HashSet<>();
        List<CalibratedSegment> deduplicated = new ArrayList<>();
        for (CalibratedSegment seg : sortedList) {
            if (seenFingerprints.add(seg.getContentFingerprint())) {
                deduplicated.add(seg);
            }
        }

        // 5. 严格装入全局 20KB 预算控制
        StringBuilder sb = new StringBuilder();
        List<CalibratedSegment> emitted = new ArrayList<>();
        int usedBytes = 0;
        int index = 1;

        for (CalibratedSegment seg : deduplicated) {
            String entry = formatEntry(index, seg);
            byte[] entryBytes = entry.getBytes(StandardCharsets.UTF_8);

            // 硬预算截断校验
            if (usedBytes + entryBytes.length > maxGlobalContextBytes) {
                log.info("[CrossKB] 切片 (id={}) 达到全局 20KB 预算上限 (used={} + need={} > max={})，执行优雅截断",
                        seg.getSegmentId(), usedBytes, entryBytes.length, maxGlobalContextBytes);
                break;
            }

            sb.append(entry);
            usedBytes += entryBytes.length;
            emitted.add(seg);
            index++;
        }

        log.info("[CrossKB] 全局 RRF 精排完成: 原始候选={}, 去重后={}, 实际装配={}, 总占用字节={}/{}",
                totalRawHits, deduplicated.size(), emitted.size(), usedBytes, maxGlobalContextBytes);

        return MergedContextResult.builder()
                .formattedContext(sb.toString())
                .emittedSegments(emitted)
                .totalUsedBytes(usedBytes)
                .totalCandidates(totalRawHits)
                .build();
    }

    private String formatEntry(int index, CalibratedSegment seg) {
        return String.format("[来源 %d] [KB: %s] %s / segmentId=%s\n内容：%s\n\n",
                index,
                seg.getKnowledgeName() != null ? seg.getKnowledgeName() : "UnknownKB",
                seg.getDocumentName() != null ? seg.getDocumentName() : "UnknownDoc",
                seg.getSegmentId() != null ? seg.getSegmentId() : 0L,
                seg.getContent() != null ? seg.getContent().trim() : ""
        );
    }

    private String calculateFingerprint(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", "").toLowerCase();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(normalized.hashCode());
        }
    }
}
