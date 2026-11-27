package tech.qiantong.qknow.hermes.benchmark.dataset;

import tech.qiantong.qknow.hermes.benchmark.evaluation.AcademicBenchmarkTask;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 真实公开学术基准标准化样本记录（Java 21 不可变 Record）。
 */
public record RealBenchmarkSample(
        String sampleId,
        AcademicBenchmarkTask.BenchmarkDataset dataset,
        String question,
        List<String> goldAnswers,
        List<ContextChunk> contextChunks,
        List<String> supportingTitles,
        String questionType,
        String tableJson,
        boolean highRiskProbe
) {

    public record ContextChunk(String title, String content, String timestampStr) {
    }

    public RealBenchmarkSample {
        Objects.requireNonNull(sampleId, "sampleId 不能为空");
        Objects.requireNonNull(dataset, "dataset 不能为空");
        question = question == null ? "" : question;
        goldAnswers = goldAnswers == null ? List.of() : List.copyOf(goldAnswers);
        contextChunks = contextChunks == null ? List.of() : List.copyOf(contextChunks);
        supportingTitles = supportingTitles == null ? List.of() : List.copyOf(supportingTitles);
        questionType = questionType == null ? "" : questionType;
        tableJson = tableJson == null ? "" : tableJson;
    }

    public String firstGoldAnswer() {
        return goldAnswers.isEmpty() ? "" : goldAnswers.get(0);
    }

    public Map<String, String> chunksByTitle() {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (ContextChunk c : contextChunks) {
            map.put(c.title(), c.content());
        }
        return map;
    }
}
