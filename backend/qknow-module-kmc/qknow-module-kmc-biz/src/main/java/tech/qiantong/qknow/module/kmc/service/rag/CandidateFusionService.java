package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.*;

@Slf4j
@Component
public class CandidateFusionService {

    private static final int RRF_K = 60;
    private static final double WEAK_PATH_THRESHOLD = 0.3;

    /**
     * 融合多路检索结果，自动排除弱检索路径
     * 参考：arXiv 2026 论文 — 弱检索路径拖低整体精度（短板效应）
     */
    public List<RetrievalResult> fuse(List<List<RetrievalResult>> resultLists) {
        return fuse(resultLists, null);
    }

    public List<RetrievalResult> fuse(List<List<RetrievalResult>> resultLists, List<String> pathNames) {
        if (resultLists == null || resultLists.isEmpty()) {
            return new ArrayList<>();
        }

        // 弱路径排除：top-1 score < 阈值的路径不参与融合
        List<List<RetrievalResult>> filtered = new ArrayList<>();
        for (int i = 0; i < resultLists.size(); i++) {
            List<RetrievalResult> results = resultLists.get(i);
            if (results == null || results.isEmpty()) {
                continue;
            }
            double topScore = results.get(0).getScore();
            // 归一化：图谱分数可能 >1，归一化到 [0,1] 后再比较
            String pathName = (pathNames != null && i < pathNames.size()) ? pathNames.get(i) : "path-" + i;
            double normalizedScore = "graph".equals(pathName) ? Math.min(topScore / 12.0, 1.0) : topScore;
            if (normalizedScore < WEAK_PATH_THRESHOLD) {
                log.info("弱检索路径排除: {} (normalized score={} < {})", pathName, normalizedScore, WEAK_PATH_THRESHOLD);
                continue;
            }
            filtered.add(results);
        }

        if (filtered.isEmpty()) {
            log.warn("所有检索路径均被排除，回退使用原始结果");
            filtered = resultLists.stream().filter(r -> r != null && !r.isEmpty()).toList();
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

                double rrfIncrement = 1.0 / (RRF_K + rank + 1);
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

        fused.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return fused;
    }
}
