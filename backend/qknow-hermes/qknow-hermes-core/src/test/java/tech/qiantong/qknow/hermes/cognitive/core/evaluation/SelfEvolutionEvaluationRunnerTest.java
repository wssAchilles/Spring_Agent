package tech.qiantong.qknow.hermes.cognitive.core.evaluation;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfEvolutionEvaluationRunnerTest {
    @Test
    void taskAndExpectedOutcomeResourcesHaveAnIsolatedThirtyTaskSplit() throws Exception {
        EvaluationTaskLoader.Dataset dataset = new EvaluationTaskLoader().load();

        assertEquals(30, dataset.tasks().size());
        assertEquals(30, dataset.expectedOutcomes().size());
        assertEquals(18, dataset.tasks().stream()
                .filter(task -> task.split() == EvaluationTaskLoader.Split.BOOTSTRAP).count());
        assertEquals(12, dataset.tasks().stream()
                .filter(task -> task.split() == EvaluationTaskLoader.Split.HOLDOUT).count());
        assertEquals(10, dataset.tasks().stream()
                .filter(task -> task.familyId().equals("STRUCTURED_OUTPUT_ASSERTION")).count());
        assertEquals(10, dataset.tasks().stream()
                .filter(task -> task.familyId().equals("TOOL_STATE_ASSERTION")).count());
        assertEquals(10, dataset.tasks().stream()
                .filter(task -> task.familyId().equals("DETERMINISTIC_LOGIC_ASSERTION")).count());
        assertTrue(dataset.tasks().stream().allMatch(task ->
                !JSON.toJSONString(task).contains("expectedOutcome")));
    }

    @Test
    void fourOfflineArmsRunAcrossEpisodesAndWriteMachineReadableEvidence() throws Exception {
        Path reportPath = Path.of("target/reasoning-eval-report.json");
        EvaluationReport report = new SelfEvolutionEvaluationRunner().runAndWrite(reportPath);
        Map<EvaluationArm, EvaluationReport.ArmMetrics> arms = report.arms().stream()
                .collect(Collectors.toMap(EvaluationReport.ArmMetrics::arm, Function.identity()));

        assertEquals(30, report.uniqueTasks());
        assertEquals(3, report.episodesPerArm());
        assertEquals(360, report.taskResults().size());
        assertEquals(0, report.realApiCalls());
        assertEquals(0, report.expectedOutcomePromptLeaks());
        assertEquals(0, report.expectedEvidencePromptLeaks());
        assertEquals(0, report.expectedOutcomeExperienceLeaks());
        assertEquals(0, report.expectedEvidenceExperienceLeaks());
        assertEquals(18, arms.get(EvaluationArm.BASELINE).successes());
        assertEquals(36, arms.get(EvaluationArm.LESSON_ONLY).successes());
        assertEquals(54, arms.get(EvaluationArm.STRATEGY_TEMPLATE).successes());
        assertEquals(84, arms.get(EvaluationArm.TEMPLATE_VARIANT_PROBATION).successes());
        assertEquals(0.0, arms.get(EvaluationArm.BASELINE).holdoutSuccessRate());
        assertEquals(0.8333333333333334,
                arms.get(EvaluationArm.TEMPLATE_VARIANT_PROBATION).holdoutSuccessRate());
        assertEquals(1, arms.get(EvaluationArm.TEMPLATE_VARIANT_PROBATION).variantPromotions());
        assertEquals(0, arms.get(EvaluationArm.TEMPLATE_VARIANT_PROBATION).variantRetirements());
        assertEquals(36, report.variantVsTemplateHoldout().taskEpisodePairs());
        assertEquals(30, report.variantVsTemplateHoldout().variantWins());
        assertEquals(6, report.variantVsTemplateHoldout().ties());
        assertEquals(0, report.variantVsTemplateHoldout().variantLosses());
        assertTrue(report.taskResults().stream().anyMatch(result ->
                result.variantLifecycleStatus() == StrategyLifecycleStatus.ADOPTED
                        && result.variantProbationUses() == 3
                        && result.variantProbationSuccesses() >= 2));
        assertTrue(report.thresholdReplay().stream().allMatch(EvaluationReport.ThresholdReplay::promoted));
        assertEquals(2, report.thresholdReplay().get(0).promotionUse());
        assertEquals(3, report.thresholdReplay().get(1).promotionUse());
        assertTrue(report.taskResults().stream().anyMatch(result -> result.modelSelfAccepted()
                && result.outcomeStatus() == EvaluationReport.OutcomeStatus.FAIL));
        assertTrue(arms.values().stream()
                .allMatch(metrics -> metrics.averageFakePromptTokenEstimate() > 0));
        assertTrue(Files.exists(reportPath));
        JSONObject json = JSON.parseObject(Files.readString(reportPath));
        assertNotNull(json.getJSONArray("arms"));
        assertTrue(report.interpretation().contains("does not establish real-model gains"));
    }
}
