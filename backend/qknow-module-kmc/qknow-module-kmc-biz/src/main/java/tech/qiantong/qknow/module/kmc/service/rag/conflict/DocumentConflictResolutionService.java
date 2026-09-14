package tech.qiantong.qknow.module.kmc.service.rag.conflict;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 26: 知识库两阶段语义冲突消歧与版本演进状态机服务
 * 包含：
 * 1. 阶段 1：轻量极性预检 (Polarity Pre-check)，检测否定词反转、禁止/允许对立及数值翻转；
 * 2. 阶段 2：语义反思仲裁 (Arbitration)，判定取代 (SUPERSEDE)、冲突 (CONFLICT) 或共存 (COEXIST)；
 * 3. 四态生命周期状态机：ACTIVE / DEPRECATED / SUPERSEDED / CONFLICTED，并维护取代指针链。
 */
@Slf4j
@Service
public class DocumentConflictResolutionService {

    /**
     * 四态冲突状态机定义
     */
    public enum ConflictStatus {
        ACTIVE,       // 有效切片：最新生效版本，可正常被向量与混合检索召回
        DEPRECATED,   // 已废弃切片：业务上已主动下线停用，不再参与召回
        SUPERSEDED,   // 被取代切片：已被后继新版本切片覆盖，具有明确 superseded_by_id，召回直接阻断
        CONFLICTED    // 冲突切片：存在对立语义且无法自动按时序消歧，需人工审核仲裁
    }

    /**
     * 语义仲裁决策动作
     */
    public enum ArbitrationAction {
        SUPERSEDE,    // 新版本切片彻底取代旧切片
        CONFLICT,     // 存在无法调和的语义矛盾，标记为 CONFLICTED
        COEXIST       // 无排斥性或属于互补/多条件分支，两者和平共存
    }

    /**
     * 切片元数据载体
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentMetadata {
        private Long segmentId;
        private Long documentId;
        private String text;
        private Instant effectiveStart;
        private Instant effectiveEnd;
        private String versionTag;
        private ConflictStatus conflictStatus;
        private Long supersededById;
        private Long simhash64;
        private Double shannonEntropy;
    }

    /**
     * 仲裁结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArbitrationResult {
        private ArbitrationAction action;
        private String reason;
        private boolean polarityConflictDetected;
        private ConflictStatus oldSegmentNewStatus;
        private ConflictStatus newSegmentStatus;
        private Long supersededById;
    }

    // 对立极性词对 (正向 vs 负向)
    private static final List<String[]> POLARITY_PAIRS = List.of(
            new String[]{"允许", "禁止"},
            new String[]{"允许", "严禁"},
            new String[]{"允许", "不得"},
            new String[]{"允许", "不可"},
            new String[]{"支持", "不支持"},
            new String[]{"支持", "不再支持"},
            new String[]{"开启", "关闭"},
            new String[]{"启用", "禁用"},
            new String[]{"免费", "收费"},
            new String[]{"必须", "无需"},
            new String[]{"必须", "禁止"}
    );

    // 数值与单位提取正则 (支持天、小时、分钟、元、% 等)
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(天|小时|小时内|分钟|秒|元|%|个|条|次)");

    /**
     * 阶段 1：轻量极性预检 (Polarity Pre-check)
     * 识别谓词否定、对立词汇及数值/时效翻转，无需调用昂贵大模型即可快速发现潜在冲突。
     *
     * @param textA 切片 A 文本
     * @param textB 切片 B 文本
     * @return 若存在对立极性或直接数值差异，返回 true
     */
    public boolean precheckPolarityConflict(String textA, String textB) {
        if (textA == null || textB == null || textA.isBlank() || textB.isBlank()) {
            return false;
        }

        // 1. 检查经典极性词对
        for (String[] pair : POLARITY_PAIRS) {
            String term1 = pair[0];
            String term2 = pair[1];
            boolean aHas1 = textA.contains(term1);
            boolean aHas2 = textA.contains(term2);
            boolean bHas1 = textB.contains(term1);
            boolean bHas2 = textB.contains(term2);

            // 若 A 含正向且不含负向，而 B 含负向且不含正向 (或反之)，则存在直接极性对立
            if ((aHas1 && !aHas2 && bHas2 && !bHas1) || (aHas2 && !aHas1 && bHas1 && !bHas2)) {
                log.debug("检测到语义对立极性词对: [{}] vs [{}]", term1, term2);
                return true;
            }
        }

        // 2. 检查否定副词与谓词反转 ("不/无/未/非/免")
        if ((textA.contains("不") || textA.contains("未") || textA.contains("无"))
                != (textB.contains("不") || textB.contains("未") || textB.contains("无"))) {
            // 进一步判断核心关键词是否高度交叠
            if (hasHighLexicalOverlap(textA, textB, 0.4)) {
                log.debug("检测到高重合度切片下的否定极性反转");
                return true;
            }
        }

        // 3. 检查数值时效翻转 (如 "7天" vs "48小时")
        List<String> unitsA = extractNumericUnits(textA);
        List<String> unitsB = extractNumericUnits(textB);
        if (!unitsA.isEmpty() && !unitsB.isEmpty() && !unitsA.equals(unitsB)) {
            if (hasHighLexicalOverlap(textA, textB, 0.35)) {
                log.debug("检测到高重合度切片下的关键数值/时效变更: {} vs {}", unitsA, unitsB);
                return true;
            }
        }

        return false;
    }

    /**
     * 阶段 2：语义反思仲裁 (Arbitration)
     * 依据时序偏序、版本标识及预检极性结果，仲裁新旧切片关系并执行状态机流转。
     *
     * @param oldSegment 旧切片元数据
     * @param newSegment 新入库切片元数据
     * @return 仲裁决策结果
     */
    public ArbitrationResult arbitrateConflict(SegmentMetadata oldSegment, SegmentMetadata newSegment) {
        if (oldSegment == null || newSegment == null) {
            throw new IllegalArgumentException("切片元数据不可为空");
        }

        boolean polarityConflict = precheckPolarityConflict(oldSegment.getText(), newSegment.getText());

        // 1. 判断时序偏序关系 (Temporal Partial Order)
        Instant oldStart = oldSegment.getEffectiveStart();
        Instant newStart = newSegment.getEffectiveStart();

        boolean isNewerByTime = false;
        if (oldStart != null && newStart != null) {
            isNewerByTime = newStart.isAfter(oldStart);
        }

        // 2. 判断版本号标识 (Version Tag)
        boolean isNewerByVersion = false;
        if (oldSegment.getVersionTag() != null && newSegment.getVersionTag() != null) {
            isNewerByVersion = compareVersions(newSegment.getVersionTag(), oldSegment.getVersionTag()) > 0;
        }

        // 3. 执行两阶段综合仲裁
        if (polarityConflict) {
            // 存在命题对立或数值变更
            if (isNewerByTime || isNewerByVersion) {
                // 新切片时间或版本明确晚于旧切片，且表述翻转 -> 判定为自然演进取代 (SUPERSEDE)
                log.info("切片演进仲裁: 新切片 [{}] 时序/版本更新，取代旧切片 [{}]", newSegment.getSegmentId(), oldSegment.getSegmentId());
                return ArbitrationResult.builder()
                        .action(ArbitrationAction.SUPERSEDE)
                        .reason("新版本切片发布，时序晚于旧版本，覆盖取代历史旧切片")
                        .polarityConflictDetected(true)
                        .oldSegmentNewStatus(ConflictStatus.SUPERSEDED)
                        .newSegmentStatus(ConflictStatus.ACTIVE)
                        .supersededById(newSegment.getSegmentId())
                        .build();
            } else if (Objects.equals(oldStart, newStart) || (oldStart == null && newStart == null)) {
                // 生效时间完全相同且无版本优势，但内容对立 -> 判定为未定冲突 (CONFLICT)
                log.warn("切片冲突警告: 切片 [{}] 与 [{}] 处于同一时效区间但内容极性对立，标记为 CONFLICTED 待人工仲裁",
                        oldSegment.getSegmentId(), newSegment.getSegmentId());
                return ArbitrationResult.builder()
                        .action(ArbitrationAction.CONFLICT)
                        .reason("同生效时间区间内存在语义命题极性矛盾，需人工复核")
                        .polarityConflictDetected(true)
                        .oldSegmentNewStatus(ConflictStatus.CONFLICTED)
                        .newSegmentStatus(ConflictStatus.CONFLICTED)
                        .supersededById(null)
                        .build();
            } else {
                // 新切片反而比旧切片更老？标记为 CONFLICTED 防倒退
                return ArbitrationResult.builder()
                        .action(ArbitrationAction.CONFLICT)
                        .reason("反向时间倒退且内容冲突，拒绝直接覆盖")
                        .polarityConflictDetected(true)
                        .oldSegmentNewStatus(oldSegment.getConflictStatus())
                        .newSegmentStatus(ConflictStatus.CONFLICTED)
                        .supersededById(null)
                        .build();
            }
        } else {
            // 无极性矛盾，检查是否属于同主题补充或共存
            return ArbitrationResult.builder()
                    .action(ArbitrationAction.COEXIST)
                    .reason("切片语义无对立排斥，支持多分支和谐共存")
                    .polarityConflictDetected(false)
                    .oldSegmentNewStatus(oldSegment.getConflictStatus() != null ? oldSegment.getConflictStatus() : ConflictStatus.ACTIVE)
                    .newSegmentStatus(ConflictStatus.ACTIVE)
                    .supersededById(null)
                    .build();
        }
    }

    /**
     * 辅助方法：提取文本中的数值与单位
     */
    private List<String> extractNumericUnits(String text) {
        List<String> results = new ArrayList<>();
        Matcher matcher = NUMERIC_PATTERN.matcher(text);
        while (matcher.find()) {
            results.add(matcher.group());
        }
        return results;
    }

    /**
     * 辅助方法：计算文本字符集合 Jaccard 粗粒度交叠率
     */
    private boolean hasHighLexicalOverlap(String textA, String textB, double threshold) {
        Set<Character> setA = new HashSet<>();
        for (char c : textA.toCharArray()) {
            if (!Character.isWhitespace(c)) setA.add(c);
        }
        Set<Character> setB = new HashSet<>();
        for (char c : textB.toCharArray()) {
            if (!Character.isWhitespace(c)) setB.add(c);
        }
        if (setA.isEmpty() || setB.isEmpty()) return false;

        Set<Character> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<Character> union = new HashSet<>(setA);
        union.addAll(setB);

        double jaccard = (double) intersection.size() / union.size();
        return jaccard >= threshold;
    }

    /**
     * 简易版本号大小比较 (如 "v2.0" > "v1.0", "2026-Q1" > "2025-Q4")
     */
    private int compareVersions(String v1, String v2) {
        return v1.compareToIgnoreCase(v2);
    }
}
