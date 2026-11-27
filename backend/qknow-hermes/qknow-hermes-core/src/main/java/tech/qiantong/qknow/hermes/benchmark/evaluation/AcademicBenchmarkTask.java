package tech.qiantong.qknow.hermes.benchmark.evaluation;

import java.util.List;

/**
 * 学术标准评测任务样本契约（支持 HotpotQA / MuSiQue / LongMemEval / TableBench，Java 21 Record）。
 */
public record AcademicBenchmarkTask(
        String taskId,
        BenchmarkDataset dataset,
        String question,
        List<String> expectedAnswerKeywords,
        List<String> contextPassages,
        String expectedToolHint,
        boolean highRiskAction,
        int expectedMinHops
) {
    public enum BenchmarkDataset {
        HOTPOT_QA,
        MUSIQUE,
        LONG_MEM_EVAL,
        TABLE_BENCH
    }
}
