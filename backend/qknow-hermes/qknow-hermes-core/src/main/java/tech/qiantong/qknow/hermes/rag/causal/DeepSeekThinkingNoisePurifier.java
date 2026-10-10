package tech.qiantong.qknow.hermes.rag.causal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 面向 DeepSeek 官方思考模式的抗噪与因果显著性净化器 (DeepSeekThinkingNoisePurifier)
 * 剔除低相关漂移切片，为 reasoning_content 思维链提供因果紧凑骨架，防止 RAG 噪音诱发思考链无休止推演与 Token 爆炸。
 */
public class DeepSeekThinkingNoisePurifier {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekThinkingNoisePurifier.class);

    public record CandidateChunk(
            String chunkId,
            String text,
            double relevanceScore,
            int estimatedTokens
    ) {}

    public record PurifiedContextPayload(
            List<CandidateChunk> salientChunks,
            List<CandidateChunk> filteredNoiseChunks,
            String compressedCausalScaffold,
            int originalTotalTokens,
            int purifiedTokens,
            double noiseRejectionRatio
    ) {}

    /**
     * 对检索召回切片进行抗噪净化
     *
     * @param queryText          用户查询
     * @param candidates         检索候选切片列表
     * @param relevanceThreshold 显著性最低门限 (例如 0.65)
     * @param maxTokensBudget    因果骨架最大 Token 预算
     * @return 净化后的因果载荷
     */
    public PurifiedContextPayload purifyForThinkingStream(
            String queryText,
            List<CandidateChunk> candidates,
            double relevanceThreshold,
            int maxTokensBudget
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new PurifiedContextPayload(Collections.emptyList(), Collections.emptyList(), "", 0, 0, 0.0);
        }

        int origTokens = 0;
        List<CandidateChunk> salient = new ArrayList<>();
        List<CandidateChunk> noise = new ArrayList<>();

        for (CandidateChunk chunk : candidates) {
            origTokens += chunk.estimatedTokens();
            if (chunk.relevanceScore() >= relevanceThreshold) {
                salient.add(chunk);
            } else {
                noise.add(chunk);
            }
        }

        // 按相关度从高到低排序
        salient.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));

        // 按 Token 预算截断
        List<CandidateChunk> budgetSalient = new ArrayList<>();
        int currentTokens = 0;
        StringBuilder scaffoldBuilder = new StringBuilder();

        for (CandidateChunk chunk : salient) {
            if (currentTokens + chunk.estimatedTokens() <= maxTokensBudget) {
                budgetSalient.add(chunk);
                currentTokens += chunk.estimatedTokens();
                scaffoldBuilder.append("【证据 ").append(chunk.chunkId()).append("】")
                        .append(chunk.text().trim()).append("\n");
            } else {
                noise.add(chunk); // 超预算切片归入旁路
            }
        }

        double rejectionRatio = origTokens > 0 ? (double) (origTokens - currentTokens) / origTokens : 0.0;
        log.info("[ThinkingPurifier] 思维链上下文净化完毕: 原始切片={}, 保留显著切片={}, 剪除噪声={}, Token削减率={}%",
                candidates.size(), budgetSalient.size(), noise.size(), String.format("%.1f", rejectionRatio * 100));

        return new PurifiedContextPayload(
                Collections.unmodifiableList(budgetSalient),
                Collections.unmodifiableList(noise),
                scaffoldBuilder.toString().trim(),
                origTokens,
                currentTokens,
                rejectionRatio
        );
    }
}
