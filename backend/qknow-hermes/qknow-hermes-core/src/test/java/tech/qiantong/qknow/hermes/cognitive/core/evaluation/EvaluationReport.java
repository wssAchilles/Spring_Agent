package tech.qiantong.qknow.hermes.cognitive.core.evaluation;

import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyLifecycleStatus;

import java.util.List;

public record EvaluationReport(
        String evaluationMode,
        String interpretation,
        int uniqueTasks,
        int episodesPerArm,
        int realApiCalls,
        int expectedOutcomePromptLeaks,
        int expectedEvidencePromptLeaks,
        int expectedOutcomeExperienceLeaks,
        int expectedEvidenceExperienceLeaks,
        List<ArmMetrics> arms,
        PairedComparison variantVsTemplateHoldout,
        List<ThresholdReplay> thresholdReplay,
        List<TaskResult> taskResults) {

    public EvaluationReport {
        arms = List.copyOf(arms);
        thresholdReplay = List.copyOf(thresholdReplay);
        taskResults = List.copyOf(taskResults);
    }

    public record ArmMetrics(EvaluationArm arm, int runs, int successes, double successRate,
                             double bootstrapSuccessRate, double holdoutSuccessRate,
                             double strategyReuseRate, int variantPromotions, int variantRetirements,
                             int regressions, double averageFakePromptTokenEstimate,
                             double averageLocalLatencyMs,
                             List<EpisodeMetrics> learningCurve) {
        public ArmMetrics {
            learningCurve = List.copyOf(learningCurve);
        }
    }

    public record EpisodeMetrics(int episode, double successRate,
                                 double bootstrapSuccessRate, double holdoutSuccessRate) { }

    public record PairedComparison(int taskEpisodePairs, int variantWins, int ties,
                                   int variantLosses, int variantRegressions) { }

    public record ThresholdReplay(int minUses, int minSuccesses, Integer promotionUse,
                                  int successesAtPromotion, boolean promoted) { }

    public record TaskResult(EvaluationArm arm, int episode, String taskId, String familyId,
                             EvaluationTaskLoader.Split split, OutcomeStatus outcomeStatus,
                             String reasonCode, String selectedStrategyId, boolean modelSelfAccepted,
                             long promptTokenEstimate, double localLatencyMs, boolean variantPromoted,
                             StrategyLifecycleStatus variantLifecycleStatus, int variantProbationUses,
                             int variantProbationSuccesses,
                             boolean variantRetired, String expectedDigest, String observedDigest,
                             String evidenceDigest) { }

    public enum OutcomeStatus {
        PASS,
        FAIL,
        UNKNOWN
    }
}
