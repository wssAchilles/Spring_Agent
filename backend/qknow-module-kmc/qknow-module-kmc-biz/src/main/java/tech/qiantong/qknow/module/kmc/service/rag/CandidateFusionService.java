package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.*;

@Slf4j
@Component
public class CandidateFusionService {

    // [溯源] 算法优化指南 §2.1: RRF k 参数配置化
    @Value("${qknow.rag.rrf.k:60}")
    private int rrfK = 60;

    // 0 disables score-based path filtering; RRF is rank-based, while retriever raw scores use different scales.
    @Value("${qknow.rag.rrf.weak-path-threshold:0}")
    private double weakPathThreshold = 0.0;

    /**
     * 融合多路检索结果。
     */
    public List<RetrievalResult> fuse(List<List<RetrievalResult>> resultLists) {
        return fuseWithDiagnostics(resultLists, null).getResults();
    }

    public List<RetrievalResult> fuse(List<List<RetrievalResult>> resultLists, List<String> pathNames) {
        return fuseWithDiagnostics(resultLists, pathNames).getResults();
    }

    public FusionResult fuseWithDiagnostics(List<List<RetrievalResult>> resultLists, List<String> pathNames) {
        if (resultLists == null || resultLists.isEmpty()) {
            return new FusionResult(new ArrayList<>(), List.of(), List.of());
        }

        List<List<RetrievalResult>> filtered = new ArrayList<>();
        List<PathScore> pathScores = new ArrayList<>();
        List<String> excludedPaths = new ArrayList<>();
        for (int i = 0; i < resultLists.size(); i++) {
            List<RetrievalResult> results = resultLists.get(i);
            if (results == null || results.isEmpty()) {
                continue;
            }
            double topScore = results.get(0).getScore();
            String pathName = (pathNames != null && i < pathNames.size()) ? pathNames.get(i) : "path-" + i;
            double normalizedScore = normalizePathScore(pathName, topScore);
            boolean excluded = weakPathThreshold > 0 && normalizedScore < weakPathThreshold;
            pathScores.add(new PathScore(pathName, topScore, normalizedScore, excluded));
            if (excluded) {
                excludedPaths.add(pathName);
                log.info("弱检索路径排除: {} (normalized score={} < {})",
                        pathName, normalizedScore, weakPathThreshold);
                continue;
            }
            filtered.add(results);
        }

        if (filtered.isEmpty()) {
            log.warn("所有检索路径均被排除，回退使用原始结果");
            filtered = resultLists.stream().filter(r -> r != null && !r.isEmpty()).toList();
            excludedPaths = List.of();
            pathScores = pathScores.stream()
                    .map(score -> new PathScore(score.getPathName(), score.getRawTopScore(),
                            score.getNormalizedTopScore(), false))
                    .toList();
        }

        Map<Long, RetrievalResult> bestBySegment = new LinkedHashMap<>();
        Map<Long, Double> rrfScores = new HashMap<>();

        for (List<RetrievalResult> results : filtered) {
            if (results == null || results.isEmpty()) {
                continue;
            }
            for (int rank = 0; rank < results.size(); rank++) {
                RetrievalResult result = results.get(rank);
                Long segmentId = result.getSegmentId();
                if (segmentId == null) {
                    continue;
                }

                double rrfIncrement = 1.0 / (rrfK + rank + 1);
                rrfScores.merge(segmentId, rrfIncrement, Double::sum);

                RetrievalResult existing = bestBySegment.get(segmentId);
                if (existing == null || result.getScore() > existing.getScore()) {
                    bestBySegment.put(segmentId, result);
                }
            }
        }

        List<RetrievalResult> fused = new ArrayList<>(bestBySegment.size());
        for (Map.Entry<Long, RetrievalResult> entry : bestBySegment.entrySet()) {
            RetrievalResult copy = RetrievalResult.builder()
                    .segmentId(entry.getValue().getSegmentId())
                    .qmSegmentId(entry.getValue().getQmSegmentId())
                    .parentSegmentId(entry.getValue().getParentSegmentId())
                    .documentId(entry.getValue().getDocumentId())
                    .documentName(entry.getValue().getDocumentName())
                    .content(entry.getValue().getContent())
                    .answer(entry.getValue().getAnswer())
                    .score(rrfScores.getOrDefault(entry.getKey(), 0.0))
                    .source(entry.getValue().getSource())
                    .metadata(entry.getValue().getMetadata())
                    .build();
            fused.add(copy);
        }

        fused.sort(Comparator.comparingDouble(RetrievalResult::getScore).reversed()
                .thenComparing(CandidateFusionService::compareStableSegmentIds));
        return new FusionResult(fused, pathScores, excludedPaths);
    }

    private static int compareStableSegmentIds(RetrievalResult left, RetrievalResult right) {
        Long leftId = left.getSegmentId();
        Long rightId = right.getSegmentId();
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return 1;
        }
        if (rightId == null) {
            return -1;
        }
        return Long.compare(leftId, rightId);
    }

    private double normalizePathScore(String pathName, double topScore) {
        if (topScore <= 0) {
            return 0;
        }
        return switch (pathName) {
            case "vector" -> Math.min(topScore, 1.0);
            case "metadata" -> Math.min(topScore / 10.0, 1.0);
            case "graph" -> Math.min(topScore / 12.0, 1.0);
            case "keyword" -> 1.0;
            default -> Math.min(topScore, 1.0);
        };
    }

    public static class FusionResult {
        private final List<RetrievalResult> results;
        private final List<PathScore> pathScores;
        private final List<String> excludedPaths;

        FusionResult(List<RetrievalResult> results, List<PathScore> pathScores, List<String> excludedPaths) {
            this.results = results;
            this.pathScores = pathScores;
            this.excludedPaths = excludedPaths;
        }

        public List<RetrievalResult> getResults() {
            return results;
        }

        public List<PathScore> getPathScores() {
            return pathScores;
        }

        public List<String> getExcludedPaths() {
            return excludedPaths;
        }
    }

    public static class PathScore {
        private final String pathName;
        private final double rawTopScore;
        private final double normalizedTopScore;
        private final boolean excluded;

        PathScore(String pathName, double rawTopScore, double normalizedTopScore, boolean excluded) {
            this.pathName = pathName;
            this.rawTopScore = rawTopScore;
            this.normalizedTopScore = normalizedTopScore;
            this.excluded = excluded;
        }

        public String getPathName() {
            return pathName;
        }

        public double getRawTopScore() {
            return rawTopScore;
        }

        public double getNormalizedTopScore() {
            return normalizedTopScore;
        }

        public boolean isExcluded() {
            return excluded;
        }
    }
}
