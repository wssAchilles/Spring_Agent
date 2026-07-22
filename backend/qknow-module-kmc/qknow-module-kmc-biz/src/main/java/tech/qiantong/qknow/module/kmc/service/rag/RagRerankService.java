package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DeterministicRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.LocalRerankerProvider;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankRequestContext;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.RerankerProvider;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RagRerankService {

    @Resource
    private List<RerankerProvider> rerankerProviders;

    @Resource
    private DeterministicRerankerProvider deterministicRerankerProvider;

    @Resource
    private ColbertScorer colbertScorer;

    @Value("${qknow.rag.rerank.identifier-consistency-enabled:false}")
    private boolean identifierConsistencyEnabled;

    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates,
                                         QueryIntent queryIntent, int topK,
                                         Long rerankingProviderName, String rerankingModelName) {
        if (CollUtil.isEmpty(candidates)) {
            return new ArrayList<>();
        }

        // [溯源] 算法优化指南 §2.2: 轻量级相关性过滤 — 剔除零关键词命中的 chunk
        candidates = filterIrrelevant(query, candidates, queryIntent);

        // ColBERT 粗排层：在主 reranker 之前进行 token-level MaxSim 粗排
        candidates = colbertCoarseRerank(query, candidates, topK * 3);

        RerankRequestContext context = RerankRequestContext.builder()
                .query(query)
                .providerName(rerankingProviderName)
                .modelName(rerankingModelName)
                .build();

        // Phase 1: 尝试 API 精排 (DashScope 等)
        for (RerankerProvider provider : rerankerProviders) {
            if (provider instanceof DeterministicRerankerProvider
                    || provider instanceof LocalRerankerProvider
                    || !provider.supports(context)) {
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                List<RetrievalResult> results = provider.rerank(context, candidates, queryIntent, topK);
                log.debug("Reranker provider '{}' finished in {}ms", provider.name(), System.currentTimeMillis() - start);
                return results;
            } catch (Exception e) {
                RagFallbackMonitor.record("reranker", "local_or_deterministic", provider.name() + " failed: " + e.getMessage());
                log.warn("Reranker provider '{}' failed, trying local rerank fallback", provider.name(), e);
            }
        }

        // Phase 2: 本地 Cross-Encoder 兜底 (API 不可用时)
        for (RerankerProvider provider : rerankerProviders) {
            if (provider instanceof LocalRerankerProvider && provider.supports(context)) {
                try {
                    List<RetrievalResult> results = provider.rerank(context, candidates, queryIntent, topK);
                    log.info("Local rerank fallback succeeded");
                    return results;
                } catch (Exception e) {
                    RagFallbackMonitor.record("reranker", "deterministic", "local rerank failed: " + e.getMessage());
                    log.warn("Local rerank fallback also failed, falling back to deterministic", e);
                }
            }
        }

        // Phase 3: 规则兜底 (最后手段)
        RagFallbackMonitor.record("reranker", "deterministic", "no remote or local reranker completed");
        return identifierConsistencyRerank(context, candidates, queryIntent, topK);
    }

    private List<RetrievalResult> identifierConsistencyRerank(
            RerankRequestContext context,
            List<RetrievalResult> candidates,
            QueryIntent queryIntent,
            int topK) {
        if (!identifierConsistencyEnabled) {
            return deterministicRerankerProvider.rerank(
                    context, candidates, queryIntent, topK);
        }

        List<Pattern> identifierPatterns = KeywordRetriever
                .extractIdentifierTerms(context.getQuery()).stream()
                .map(identifier -> Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(identifier)
                                + "(?![\\p{L}\\p{N}])"))
                .toList();
        if (identifierPatterns.isEmpty()
                || candidates.stream().noneMatch(
                        candidate -> matchesIdentifier(candidate, identifierPatterns))) {
            return deterministicRerankerProvider.rerank(
                    context, candidates, queryIntent, topK);
        }

        List<RetrievalResult> detached = candidates.stream()
                .map(RagRerankService::copyRetrievalResult)
                .collect(Collectors.toCollection(ArrayList::new));
        List<RetrievalResult> fullRanking = deterministicRerankerProvider.rerank(
                context, detached, queryIntent, detached.size());
        List<Double> rankScores = fullRanking.stream()
                .map(RetrievalResult::getScore)
                .toList();

        List<RetrievalResult> reordered = new ArrayList<>(fullRanking.size());
        fullRanking.stream()
                .filter(candidate -> matchesIdentifier(candidate, identifierPatterns))
                .forEach(reordered::add);
        fullRanking.stream()
                .filter(candidate -> !matchesIdentifier(candidate, identifierPatterns))
                .forEach(reordered::add);
        for (int index = 0; index < reordered.size(); index++) {
            reordered.get(index).setScore(rankScores.get(index));
        }
        return reordered.stream().limit(topK).collect(Collectors.toList());
    }

    private static boolean matchesIdentifier(
            RetrievalResult candidate, List<Pattern> identifierPatterns) {
        return candidate != null
                && candidate.getDocumentName() != null
                && identifierPatterns.stream().anyMatch(
                        pattern -> pattern.matcher(candidate.getDocumentName()).find());
    }

    private static RetrievalResult copyRetrievalResult(RetrievalResult result) {
        return RetrievalResult.builder()
                .segmentId(result.getSegmentId())
                .qmSegmentId(result.getQmSegmentId())
                .parentSegmentId(result.getParentSegmentId())
                .documentId(result.getDocumentId())
                .documentName(result.getDocumentName())
                .content(result.getContent())
                .answer(result.getAnswer())
                .score(result.getScore())
                .source(result.getSource())
                .metadata(result.getMetadata() == null
                        ? null : new LinkedHashMap<>(result.getMetadata()))
                .build();
    }

    /**
     * 轻量级相关性过滤：双条件（关键词 + 向量分数）过滤低质量 chunk
     * [溯源] 算法优化指南 §2.2: 关键词重叠 + 分数阈值双条件过滤
     *
     * 过滤条件：关键词命中为 0 且向量分数低于中位数的 75%
     * 保留策略：命中 ≥1 个关键词 OR 分数 ≥ 阈值 → 保留
     */
    private List<RetrievalResult> filterIrrelevant(String query, List<RetrievalResult> candidates, QueryIntent queryIntent) {
        List<String> keywords = extractKeywords(query, queryIntent);
        if (keywords.isEmpty()) {
            return candidates;
        }

        // 计算分数阈值：中位数的 50%
        double scoreThreshold = computeScoreThreshold(candidates);

        List<RetrievalResult> filtered = new ArrayList<>();
        for (RetrievalResult candidate : candidates) {
            String content = candidate.getContent();
            if (StrUtil.isBlank(content)) {
                continue;
            }

            // 条件 1：关键词命中
            int hits = countKeywordHits(keywords, content);

            // 条件 2：向量分数
            double score = candidate.getScore();

            // 保留：命中 ≥1 OR 分数 ≥ 阈值
            if (hits > 0 || score >= scoreThreshold) {
                filtered.add(candidate);
            } else {
                log.debug("Filtered: 0 hits, score={:.3f} < {:.3f}: {}",
                        score, scoreThreshold, content.substring(0, Math.min(60, content.length())));
            }
        }

        if (filtered.isEmpty()) {
            log.warn("All candidates filtered out, falling back to original set");
            return candidates;
        }
        return filtered;
    }

    private int countKeywordHits(List<String> keywords, String content) {
        String contentLower = content.toLowerCase();
        int hits = 0;
        for (String kw : keywords) {
            if (contentLower.contains(kw.toLowerCase())) {
                hits++;
            }
        }
        return hits;
    }

    private double computeScoreThreshold(List<RetrievalResult> candidates) {
        if (candidates.isEmpty()) return 0.0;
        List<Double> scores = candidates.stream()
                .map(RetrievalResult::getScore)
                .sorted()
                .collect(Collectors.toList());
        double median = scores.get(scores.size() / 2);
        return median * 0.75;
    }

    private List<String> extractKeywords(String query, QueryIntent queryIntent) {
        List<String> keywords = new ArrayList<>();

        // 从 QueryIntent 获取关键词
        if (queryIntent != null && CollUtil.isNotEmpty(queryIntent.getKeywords())) {
            keywords.addAll(queryIntent.getKeywords());
        }

        // 从 query 中提取中文词和英文词
        if (StrUtil.isNotBlank(query)) {
            // 提取中文词（2-4 字），使用滑动窗口避免长词合并
            for (int len = 4; len >= 2; len--) {
                for (int i = 0; i <= query.length() - len; i++) {
                    String sub = query.substring(i, i + len);
                    if (sub.matches("[\\u4e00-\\u9fa5]{" + len + "}")) {
                        keywords.add(sub);
                    }
                }
            }
            // 提取英文词
            Pattern enPattern = Pattern.compile("[a-zA-Z]{2,}");
            Matcher enMatcher = enPattern.matcher(query);
            while (enMatcher.find()) {
                keywords.add(enMatcher.group().toLowerCase());
            }
        }

        // 去重
        return keywords.stream().distinct().collect(Collectors.toList());
    }

    /**
     * ColBERT 粗排：使用 token-level MaxSim 进行快速粗排
     */
    private List<RetrievalResult> colbertCoarseRerank(String query, List<RetrievalResult> candidates, int limit) {
        try {
            Map<Long, RetrievalResult> originalBySegmentId = candidates.stream()
                    .filter(r -> r.getSegmentId() != null)
                    .collect(Collectors.toMap(
                            RetrievalResult::getSegmentId,
                            r -> r,
                            (left, right) -> left,
                            LinkedHashMap::new));
            List<org.springframework.ai.document.Document> docs = candidates.stream()
                    .map(r -> {
                        org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(r.getContent());
                        doc.getMetadata().put("segmentId", r.getSegmentId());
                        doc.getMetadata().put("score", r.getScore());
                        doc.getMetadata().put("source", r.getSource());
                        return doc;
                    })
                    .collect(Collectors.toList());

            List<org.springframework.ai.document.Document> reranked = colbertScorer.rerank(query, docs, limit);

            return reranked.stream()
                    .map(doc -> toRetrievalResult(doc, originalBySegmentId))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            RagFallbackMonitor.record("jni", "java_colbert_order", "colbert coarse rerank failed: " + e.getMessage());
            log.debug("ColBERT coarse rerank failed, returning original candidates", e);
            return candidates;
        }
    }

    private RetrievalResult toRetrievalResult(org.springframework.ai.document.Document doc,
                                              Map<Long, RetrievalResult> originalBySegmentId) {
        Map<String, Object> meta = doc.getMetadata() != null ? new LinkedHashMap<>(doc.getMetadata()) : new LinkedHashMap<>();
        Long segmentId = meta.get("segmentId") instanceof Long value ? value : null;
        RetrievalResult original = segmentId != null ? originalBySegmentId.get(segmentId) : null;
        double score = meta.get("score") instanceof Number number
                ? number.doubleValue()
                : original != null ? original.getScore() : 0.0D;
        String source = meta.get("source") instanceof String value
                ? value
                : original != null ? original.getSource() : "colbert";

        if (original == null) {
            return RetrievalResult.builder()
                    .segmentId(segmentId)
                    .content(doc.getText())
                    .score(score)
                    .source(source)
                    .metadata(meta)
                    .build();
        }

        return RetrievalResult.builder()
                .segmentId(original.getSegmentId())
                .qmSegmentId(original.getQmSegmentId())
                .parentSegmentId(original.getParentSegmentId())
                .documentId(original.getDocumentId())
                .documentName(original.getDocumentName())
                .content(original.getContent())
                .answer(original.getAnswer())
                .score(score)
                .source(source)
                .metadata(meta)
                .build();
    }
}
