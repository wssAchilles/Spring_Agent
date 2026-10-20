package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.CrossKbScoreCalibrator;
import tech.qiantong.qknow.module.kb.service.agent.retrieval.MultiKbRetrievalCoordinator;
import tech.qiantong.qknow.module.kmc.service.rag.CandidateFusionService;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N1 回归防护：RRF 得分量纲必须显式生产并透传到跨库门禁。
 * 该测试锁定"融合产生 RRF → 跨库门禁不施加余弦阈值"的生产契约，避免再次以测试内手工 setScoreType 自证。
 */
class ScoreTypeContractTest {

    @Test
    @DisplayName("RRF 生产者契约：CandidateFusionService 融合结果必须标注 scoreType=RRF")
    void fusionMustTagRrfScoreType() {
        CandidateFusionService fusionService = new CandidateFusionService();

        RetrievalResult r1 = RetrievalResult.builder()
                .segmentId(101L).documentId(10L).documentName("a.md").content("a").score(0.91).build();
        RetrievalResult r2 = RetrievalResult.builder()
                .segmentId(101L).documentId(10L).documentName("a.md").content("a").score(0.88).build();

        List<RetrievalResult> fused = fusionService.fuse(List.of(List.of(r1), List.of(r2)));

        assertEquals(1, fused.size());
        assertEquals(RetrievalResult.SCORE_TYPE_RRF, fused.get(0).getScoreType(),
                "融合路径必须显式标注 RRF 量纲，否则跨库 0.40 余弦门限会误杀");
    }

    @Test
    @DisplayName("RRF 门禁契约：RRF 量纲的小分（0.066）绝不被 0.40 余弦底线误杀")
    void calibratorMustKeepRrfTaggedResults() {
        CrossKbScoreCalibrator calibrator = new CrossKbScoreCalibrator();
        assertEquals(0.40D, calibrator.getScoreFloor(), 1e-6);

        RetrieveResult rrfHit = new RetrieveResult();
        rrfHit.setId("201");
        rrfHit.setDocumentId("20");
        rrfHit.setDocumentName("consensus.md");
        rrfHit.setContent("多库共识切片");
        rrfHit.setScore(0.066);
        rrfHit.setScoreType(RetrieveResult.SCORE_TYPE_RRF);

        CrossKbScoreCalibrator.MergedContextResult merged =
                calibrator.calibrateAndAssemble(List.of(kb(1L, "kb-consensus", rrfHit)));

        assertEquals(1, merged.getEmittedSegments().size(),
                "RRF 量纲切片即便得分 0.066 也必须保留，禁止套用余弦 0.40 门限");
    }

    @Test
    @DisplayName("COSINE 门禁契约：legacy 原始余弦低于 0.40 被过滤，高于门限保留")
    void calibratorMustApplyFloorOnlyToCosine() {
        CrossKbScoreCalibrator calibrator = new CrossKbScoreCalibrator();

        RetrieveResult lowCosine = new RetrieveResult();
        lowCosine.setId("301");
        lowCosine.setDocumentId("30");
        lowCosine.setDocumentName("low.md");
        lowCosine.setContent("低相关噪点");
        lowCosine.setScore(0.35);
        lowCosine.setScoreType(RetrieveResult.SCORE_TYPE_COSINE);

        RetrieveResult highCosine = new RetrieveResult();
        highCosine.setId("302");
        highCosine.setDocumentId("30");
        highCosine.setDocumentName("high.md");
        highCosine.setContent("高相关正文");
        highCosine.setScore(0.85);
        highCosine.setScoreType(RetrieveResult.SCORE_TYPE_COSINE);

        CrossKbScoreCalibrator.MergedContextResult merged =
                calibrator.calibrateAndAssemble(List.of(kb(2L, "kb-cosine", lowCosine, highCosine)));

        assertEquals(1, merged.getEmittedSegments().size());
        assertEquals("302", merged.getEmittedSegments().get(0).getSegmentId());
    }

    private MultiKbRetrievalCoordinator.SingleKbRecallResult kb(Long id, String name, RetrieveResult... hits) {
        return MultiKbRetrievalCoordinator.SingleKbRecallResult.builder()
                .knowledgeId(id)
                .knowledgeName(name)
                .success(true)
                .results(List.of(hits))
                .build();
    }
}
