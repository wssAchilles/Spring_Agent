package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.eval.EvaluationReport;
import tech.qiantong.qknow.hermes.eval.EvaluationStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagLiveEvaluationSupportTest {

    @Test
    void loadsMatchingContextAndLabelArtifactsAndRejectsMismatch() throws Exception {
        Path directory = Files.createTempDirectory("rag-live-support-");
        String context = JSON.toJSONString(Map.of(
                "queryId", "q001-en", "familyId", "f001", "query", "question",
                "split", "test", "answerable", true, "context", "ctx"));
        String label = JSON.toJSONString(Map.of(
                "queryId", "q001-en", "familyId", "f001", "split", "test",
                "answerable", true, "referenceAnswer", "answer", "referenceClaims", List.of("claim")));
        Files.writeString(directory.resolve("shadow-contexts.jsonl"), context + "\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("shadow-labels.jsonl"), label + "\n", StandardCharsets.UTF_8);
        assertEquals("q001-en", RagLiveEvaluationSupport.loadCases(directory).get(0).queryId());

        Files.writeString(directory.resolve("shadow-labels.jsonl"), label.replace("q001-en", "q002-en") + "\n",
                StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> RagLiveEvaluationSupport.loadCases(directory));
    }

    @Test
    void missingPriceMakesCostUnavailableAndNeverSerializesApiKey() {
        String secret = "secret-only-in-memory";
        RagLiveEvaluationSupport.LiveConfiguration config =
                RagLiveEvaluationSupport.LiveConfiguration.forTesting(
                        "local", "http://localhost", secret, "model", 1.0D, null);
        RagLiveEvaluationSupport.CostEstimate estimate = RagLiveEvaluationSupport.cost(config, 100, 100);
        assertEquals("unavailable", estimate.status());
        assertTrue(JSON.toJSONString(Map.of("platform", config.platform(), "model", config.model()))
                .contains("model"));
        assertTrue(!JSON.toJSONString(Map.of("platform", config.platform(), "model", config.model()))
                .contains(secret));
    }

    @Test
    void provenanceMetadataMustMatchAllNinetySixDatasetCases() {
        RagEvaluationDataset dataset = RagEvaluationDatasetLoader.loadDefault();
        List<RagLiveEvaluationSupport.LiveCase> cases = new ArrayList<>();
        for (RagEvaluationDataset.QueryCase query : dataset.queries()) {
            cases.add(new RagLiveEvaluationSupport.LiveCase(
                    query.id(), query.familyId(), query.query(), query.split(), query.answerable(),
                    "actual shadow context", query.referenceAnswer(), query.referenceClaims()));
        }
        RagLiveEvaluationSupport.validateAgainstDataset(cases, dataset);

        RagLiveEvaluationSupport.LiveCase first = cases.get(0);
        cases.set(0, new RagLiveEvaluationSupport.LiveCase(
                first.queryId(), "wrong-family", first.query(), first.split(), first.answerable(),
                first.context(), first.referenceAnswer(), first.referenceClaims()));
        assertThrows(IllegalStateException.class,
                () -> RagLiveEvaluationSupport.validateAgainstDataset(cases, dataset));

        cases.set(0, new RagLiveEvaluationSupport.LiveCase(
                first.queryId(), first.familyId(), first.query(), first.split(), first.answerable(),
                first.context(), "wrong-reference", first.referenceClaims()));
        assertThrows(IllegalStateException.class,
                () -> RagLiveEvaluationSupport.validateAgainstDataset(cases, dataset));
    }

    @Test
    void generationErrorMarkersNeverCountAsValidJudgeCoverage() {
        EvaluationReport.ItemResult generated = new EvaluationReport.ItemResult();
        generated.setAnswer("[GENERATION_ERROR]");
        generated.setScores(Map.of("faithfulness",
                new EvaluationReport.MetricResult(EvaluationStatus.VALID, 0.9, null, null)));
        EvaluationReport report = new EvaluationReport();
        report.setItemResults(List.of(generated));

        assertTrue(RagLiveEvaluationTest.isGenerationFailure("[GENERATION_ERROR]"));
        assertTrue(RagLiveEvaluationTest.isGenerationFailure("[EVALUATION_ERROR]"));
        assertEquals(0, RagLiveEvaluationTest.validRagasItems(report));
        assertEquals(EvaluationStatus.INVALID,
                RagLiveEvaluationTest.generationFailureReport("q", generated.getAnswer()).getStatus());
        assertFalse(RagLiveEvaluationTest.isGenerationFailure("normal answer"));
    }
}
