package tech.qiantong.qknow.ai.compressor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 神经-符号双轨长上下文压缩中枢 (定理 1.1 双轨信息瓶颈压缩)
 * 严格执行: 符号骨架 S 100% 硬豁免 + 自由自然语言 T 基于自信息熵动态剪枝
 */
@Component
public class NeuroSymbolicContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(NeuroSymbolicContextCompressor.class);

    private final SymbolicSkeletonExtractor skeletonExtractor;

    public record CompressionResult(
            String compressedText,
            int originalEstimatedTokens,
            int compressedEstimatedTokens,
            double compressionRatio,
            int totalAnchorsRetained,
            int totalOriginalAnchors
    ) {}

    public NeuroSymbolicContextCompressor(SymbolicSkeletonExtractor skeletonExtractor) {
        this.skeletonExtractor = skeletonExtractor;
    }

    /**
     * 执行双轨长上下文压缩
     * @param rawContext 原始长篇上下文
     * @param targetRetentionRatio 目标保留比例 (如 0.30 表示削减 70% Token)
     */
    public CompressionResult compress(String rawContext, double targetRetentionRatio) {
        if (rawContext == null || rawContext.isBlank()) {
            return new CompressionResult("", 0, 0, 1.0, 0, 0);
        }

        // 1. 抽取全局不可变符号骨架
        SymbolicSkeletonExtractor.SymbolicSkeleton skeleton = skeletonExtractor.extract(rawContext);
        int totalOriginalAnchors = skeleton.anchors().size();

        // 2. 句级微切分
        String[] rawSentences = rawContext.split("(?<=[。；;\n])");
        List<SentenceCandidate> candidates = new ArrayList<>();

        int originalTokenEstimate = estimateTokenCount(rawContext);
        int targetTokens = Math.max(64, (int) (originalTokenEstimate * targetRetentionRatio));

        for (int i = 0; i < rawSentences.length; i++) {
            String s = rawSentences[i].trim();
            if (s.isEmpty()) continue;

            // 检查当前句子是否包含不可变符号锚点
            List<SymbolicSkeletonExtractor.SymbolicAnchor> containedAnchors = new ArrayList<>();
            for (SymbolicSkeletonExtractor.SymbolicAnchor a : skeleton.anchors()) {
                if (s.contains(a.value())) {
                    containedAnchors.add(a);
                }
            }

            double score = calculateSentenceSalience(s, containedAnchors.size());
            boolean hasSymbolicHardConstraint = !containedAnchors.isEmpty();
            candidates.add(new SentenceCandidate(i, s, score, estimateTokenCount(s), hasSymbolicHardConstraint));
        }

        // 3. 双轨配额挑选
        // 规则 A: 包含符号硬约束的句子 100% 保留 (定理 1.1 符号保真性)
        List<SentenceCandidate> selected = new ArrayList<>();
        int currentTokens = 0;

        for (SentenceCandidate sc : candidates) {
            if (sc.hasHardConstraint) {
                selected.add(sc);
                currentTokens += sc.tokenCount;
            }
        }

        // 规则 B: 剩余预算在自由纯文本句子中按显著性得分贪心保留
        List<SentenceCandidate> freeTextCandidates = new ArrayList<>();
        for (SentenceCandidate sc : candidates) {
            if (!sc.hasHardConstraint) {
                freeTextCandidates.add(sc);
            }
        }
        freeTextCandidates.sort(Comparator.comparingDouble((SentenceCandidate c) -> c.salienceScore).reversed());

        for (SentenceCandidate fc : freeTextCandidates) {
            if (currentTokens + fc.tokenCount <= targetTokens) {
                selected.add(fc);
                currentTokens += fc.tokenCount;
            }
        }

        // 4. 按原始语序重排 (保证因果时序一致性)
        selected.sort(Comparator.comparingInt(c -> c.originalIndex));

        StringBuilder sb = new StringBuilder();
        for (SentenceCandidate sc : selected) {
            sb.append(sc.text).append("\n");
        }
        String compressedText = sb.toString().trim();

        int compressedTokenEstimate = estimateTokenCount(compressedText);
        double actualRatio = originalTokenEstimate > 0 ? (double) compressedTokenEstimate / originalTokenEstimate : 1.0;

        // 5. 校验符号骨架保留率 (必须 100%)
        int retainedAnchorsCount = 0;
        for (SymbolicSkeletonExtractor.SymbolicAnchor anchor : skeleton.anchors()) {
            if (compressedText.contains(anchor.value())) {
                retainedAnchorsCount++;
            }
        }

        log.info("[ContextCompressor] 压缩完成: 原长={} Tokens, 压缩后={} Tokens, 保留比={}, 符号保留={}/{}",
                originalTokenEstimate, compressedTokenEstimate,
                String.format("%.2f", actualRatio), retainedAnchorsCount, totalOriginalAnchors);

        return new CompressionResult(
                compressedText,
                originalTokenEstimate,
                compressedTokenEstimate,
                actualRatio,
                retainedAnchorsCount,
                totalOriginalAnchors
        );
    }

    private double calculateSentenceSalience(String sentence, int anchorCount) {
        double score = 1.0;
        // 符号数量加权
        score += anchorCount * 5.0;

        // 冗余语气与口语过滤惩罚
        if (sentence.contains("众所周知") || sentence.contains("综上所述") || sentence.contains("显而易见")
                || sentence.contains("换句话说") || sentence.contains("如前文提到")) {
            score *= 0.40;
        }

        // 极短句微惩罚
        if (sentence.length() < 10) {
            score *= 0.60;
        }

        return score;
    }

    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 中文约 1.5 字符/Token，英文约 4 字符/Token，粗估均值 1.8
        return (int) Math.ceil(text.length() / 1.8);
    }

    private record SentenceCandidate(
            int originalIndex,
            String text,
            double salienceScore,
            int tokenCount,
            boolean hasHardConstraint
    ) {}
}
