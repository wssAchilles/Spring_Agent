package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandidateFusionServiceTest {

    private CandidateFusionService service;

    @BeforeEach
    void setUp() {
        service = new CandidateFusionService();
    }

    @Test
    @DisplayName("空输入返回空列表")
    void fuse_withNullInput_returnsEmptyList() {
        List<RetrievalResult> result = service.fuse(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("空列表输入返回空列表")
    void fuse_withEmptyInput_returnsEmptyList() {
        List<RetrievalResult> result = service.fuse(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("RRF分数计算正确性 - 验证 1/(60+rank+1) 公式")
    void fuse_singlePath_rrfScoreFormula() {
        // rank=0 → 1/(60+0+1) = 1/61, rank=1 → 1/(60+1+1) = 1/62
        RetrievalResult r1 = buildResult(1L, 0.9, "seg1");
        RetrievalResult r2 = buildResult(2L, 0.8, "seg2");
        List<List<RetrievalResult>> input = List.of(List.of(r1, r2));

        List<RetrievalResult> fused = service.fuse(input);

        assertEquals(2, fused.size());
        double expectedRrf0 = 1.0 / (60 + 0 + 1); // 1/61
        double expectedRrf1 = 1.0 / (60 + 1 + 1); // 1/62
        assertEquals(expectedRrf0, findBySegmentId(fused, 1L).getScore(), 1e-10);
        assertEquals(expectedRrf1, findBySegmentId(fused, 2L).getScore(), 1e-10);
    }

    @Test
    @DisplayName("多路径融合后按RRF分数降序排列")
    void fuse_multiPath_sortedByRrfScoreDesc() {
        // segmentId=1 出现在两路的 rank=0，累积 RRF 最高
        // segmentId=2 只出现在一路的 rank=1
        RetrievalResult r1a = buildResult(1L, 0.9, "a");
        RetrievalResult r2a = buildResult(2L, 0.8, "a");
        RetrievalResult r1b = buildResult(1L, 0.85, "b");
        RetrievalResult r3b = buildResult(3L, 0.7, "b");

        List<List<RetrievalResult>> input = List.of(List.of(r1a, r2a), List.of(r1b, r3b));

        List<RetrievalResult> fused = service.fuse(input);

        // segmentId=1: 1/61 + 1/61, segmentId=2: 1/62, segmentId=3: 1/62
        // segmentId=1 最高，segmentId=2 和 3 相等
        assertEquals(1L, fused.get(0).getSegmentId());
        assertTrue(fused.get(0).getScore() > fused.get(1).getScore());
    }

    @Test
    @DisplayName("去重逻辑 - 同一segmentId保留最高原始分数")
    void fuse_duplicateSegmentId_keepsHighestOriginalScore() {
        RetrievalResult r1 = buildResult(1L, 0.5, "pathA");
        RetrievalResult r2 = buildResult(1L, 0.9, "pathB");

        List<List<RetrievalResult>> input = List.of(List.of(r1), List.of(r2));

        List<RetrievalResult> fused = service.fuse(input);

        assertEquals(1, fused.size());
        // 两路都有 segmentId=1，RRF = 1/61 + 1/61
        assertEquals(2.0 / 61, fused.get(0).getScore(), 1e-10);
        // 保留最高原始分数的 content（pathB 的 score 0.9 更高）
        assertEquals("pathB", fused.get(0).getSource());
    }

    @Test
    @DisplayName("弱路径排除 - topScore < 0.3 的路径被过滤")
    void fuse_weakPath_excluded() {
        // 路径A: topScore = 0.1（弱），路径B: topScore = 0.8（强）
        RetrievalResult weak = buildResult(1L, 0.1, "weak");
        RetrievalResult strong = buildResult(2L, 0.8, "strong");

        List<List<RetrievalResult>> input = List.of(List.of(weak), List.of(strong));

        List<RetrievalResult> fused = service.fuse(input);

        // 只有路径B参与融合
        assertEquals(1, fused.size());
        assertEquals(2L, fused.get(0).getSegmentId());
    }

    @Test
    @DisplayName("图谱分数归一化 - graph路径除以12.0")
    void fuse_graphPath_normalizesBy12() {
        // graph 路径 topScore = 3.6, 归一化后 3.6/12.0 = 0.3 >= 阈值，保留
        RetrievalResult graphResult = buildResult(1L, 3.6, "graph");
        List<List<RetrievalResult>> input = List.of(List.of(graphResult));
        List<String> pathNames = List.of("graph");

        List<RetrievalResult> fused = service.fuse(input, pathNames);

        assertEquals(1, fused.size());
    }

    @Test
    @DisplayName("图谱分数归一化后低于阈值被排除")
    void fuse_graphPath_belowThreshold_excluded() {
        // graph 路径 topScore = 1.0, 归一化后 1.0/12.0 ≈ 0.083 < 0.3，排除
        RetrievalResult graphWeak = buildResult(1L, 1.0, "graph");
        RetrievalResult normal = buildResult(2L, 0.8, "normal");

        List<List<RetrievalResult>> input = List.of(List.of(graphWeak), List.of(normal));
        List<String> pathNames = List.of("graph", "normal");

        List<RetrievalResult> fused = service.fuse(input, pathNames);

        assertEquals(1, fused.size());
        assertEquals(2L, fused.get(0).getSegmentId());
    }

    @Test
    @DisplayName("所有路径被弱排除时回退到原始结果")
    void fuse_allPathsWeak_fallbackToOriginal() {
        // 两路 topScore 都 < 0.3
        RetrievalResult weak1 = buildResult(1L, 0.1, "pathA");
        RetrievalResult weak2 = buildResult(2L, 0.2, "pathB");

        List<List<RetrievalResult>> input = List.of(List.of(weak1), List.of(weak2));

        List<RetrievalResult> fused = service.fuse(input);

        // 回退后两路都参与融合
        assertEquals(2, fused.size());
    }

    @Test
    @DisplayName("null子列表被跳过")
    void fuse_withNullSubList_skipped() {
        RetrievalResult r = buildResult(1L, 0.8, "path");
        List<List<RetrievalResult>> input = new ArrayList<>();
        input.add(null);
        input.add(List.of(r));

        List<RetrievalResult> fused = service.fuse(input);

        assertEquals(1, fused.size());
    }

    private RetrievalResult buildResult(Long segmentId, double score, String source) {
        return RetrievalResult.builder()
                .segmentId(segmentId)
                .score(score)
                .source(source)
                .content("content-" + segmentId)
                .build();
    }

    private RetrievalResult findBySegmentId(List<RetrievalResult> list, Long segmentId) {
        return list.stream()
                .filter(r -> segmentId.equals(r.getSegmentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("segmentId " + segmentId + " not found"));
    }
}
