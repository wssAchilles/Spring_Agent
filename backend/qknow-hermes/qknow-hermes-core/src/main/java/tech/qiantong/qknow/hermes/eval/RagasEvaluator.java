package tech.qiantong.qknow.hermes.eval;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class RagasEvaluator {
    private static final ExecutorService SAMPLE_EXECUTOR = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 8),
            r -> { Thread t = new Thread(r, "ragas-sample"); t.setDaemon(true); return t; });
    private static final ExecutorService METRIC_EXECUTOR = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 8),
            r -> { Thread t = new Thread(r, "ragas-metric"); t.setDaemon(true); return t; });

    private final ChatModelFactory chatModelFactory;
    private final RagasEvalConfig config;

    /** 指标名称 -> 评估 prompt（使用 LinkedHashMap 保持顺序） */
    private static final Map<String, String> METRIC_PROMPTS = new LinkedHashMap<>();
    static {
        METRIC_PROMPTS.put("faithfulness",
                "评估以下回答相对于给定上下文的忠实度。只返回JSON: {\"score\": 0.0-1.0, \"feedback\": \"...\"}");
        METRIC_PROMPTS.put("answer_relevance",
                "评估以下回答与问题的相关性。只返回JSON: {\"score\": 0.0-1.0, \"feedback\": \"...\"}");
        METRIC_PROMPTS.put("context_precision",
                "评估给定上下文对回答问题的精确度。只返回JSON: {\"score\": 0.0-1.0, \"feedback\": \"...\"}");
        METRIC_PROMPTS.put("context_recall",
                "评估给定上下文对回答的召回率。只返回JSON: {\"score\": 0.0-1.0, \"feedback\": \"...\"}");
        METRIC_PROMPTS.put("factual_correctness",
                "评估以下回答中的事实是否与上下文中的事实一致。检查数字、日期、名称等关键事实。只返回JSON: {\"score\": 0.0-1.0, \"feedback\": \"...\"}");
        METRIC_PROMPTS.put("noise_sensitivity",
                "评估回答对上下文中噪声（不相关信息）的敏感程度。回答是否被不相关内容误导？只返回JSON: {\"score\": 0.0-1.0, \"feedback\": \"...\"}");
        METRIC_PROMPTS.put("negative_rejection",
                "当上下文中不包含答案时，评估系统是否正确拒绝回答而非编造答案。如果上下文包含答案则给1.0分。只返回JSON: {\"score\": 0.0-1.0, \"feedback\": \"...\"}");
    }

    public RagasEvaluator(ChatModelFactory chatModelFactory, RagasEvalConfig config) {
        this.chatModelFactory = chatModelFactory;
        this.config = config;
    }

    public EvaluationReport evaluate(EvaluationDataset dataset) {
        EvaluationReport report = new EvaluationReport();
        report.setRunId(UUID.randomUUID().toString());
        report.setDatasetName(dataset.getName());

        List<ItemEvaluation> evaluated = dataset.getItems().stream()
                .map(item -> CompletableFuture.supplyAsync(() -> evaluateItem(item), SAMPLE_EXECUTOR))
                .map(CompletableFuture::join)
                .toList();
        List<EvaluationReport.ItemResult> results = evaluated.stream().map(ItemEvaluation::result).toList();
        List<MetricScores> allScores = evaluated.stream().map(ItemEvaluation::scores).toList();
        report.setItemResults(results);

        // 计算汇总统计 (mean, p50, p90 对每项指标)
        report.setSummary(computeSummary(allScores));
        return report;
    }

    /**
     * 对单条数据执行四项 Ragas 指标评估 (4 次 ChatModel 调用)
     */
    public MetricScores evaluateSingle(String query, String answer, List<String> contexts, String expectedAnswer) {
        MetricScores scores = new MetricScores();
        contexts = contexts != null ? contexts : List.of();
        String contextStr = String.join("\n", contexts);

        Map<String, String> metricPrompts = resolveMetricPrompts();
        Map<String, CompletableFuture<Double>> futures = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : metricPrompts.entrySet()) {
            futures.put(entry.getKey(), CompletableFuture.supplyAsync(
                    () -> judgeMetric(query, contextStr, answer, entry.getValue()), METRIC_EXECUTOR));
        }
        for (Map.Entry<String, CompletableFuture<Double>> entry : futures.entrySet()) {
            try {
                setScore(scores, entry.getKey(), entry.getValue().join());
            } catch (Exception e) {
                log.warn("Metric evaluation failed: {}", entry.getKey(), e);
            }
        }

        scores.setPassed(scores.isAboveThreshold(config != null ? config.getThreshold() : MetricScores.DEFAULT_GATE_THRESHOLD));
        return scores;
    }

    /**
     * 调用 ChatModel 生成回答 (1 次调用)
     */
    private String generateAnswer(String query, List<String> contexts) {
        contexts = contexts != null ? contexts : List.of();
        String contextStr = contexts.isEmpty() ? "" : String.join("\n", contexts);
        String prompt = "基于以下知识回答问题。\n\n知识:\n" + contextStr + "\n\n问题: " + query;
        ChatModel chatModel = createChatModel();
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(prompt));
        ChatResponse response = chatModel.call(new Prompt(messages));
        return response.getResult().getOutput().getText();
    }

    private ItemEvaluation evaluateItem(EvaluationDataset.EvalItem item) {
        String answer = generateAnswer(item.getQuery(), item.getGroundTruthContexts());
        MetricScores scores = evaluateSingle(item.getQuery(), answer,
                item.getGroundTruthContexts(), item.getExpectedAnswer());
        EvaluationReport.ItemResult result = new EvaluationReport.ItemResult();
        result.setQuery(item.getQuery());
        result.setAnswer(answer);
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        scoreMap.put("faithfulness", scores.getFaithfulness());
        scoreMap.put("answer_relevance", scores.getAnswerRelevance());
        scoreMap.put("context_precision", scores.getContextPrecision());
        scoreMap.put("context_recall", scores.getContextRecall());
        scoreMap.put("factual_correctness", scores.getFactualCorrectness());
        scoreMap.put("noise_sensitivity", scores.getNoiseSensitivity());
        scoreMap.put("negative_rejection", scores.getNegativeRejection());
        result.setScores(scoreMap);
        return new ItemEvaluation(result, scores);
    }

    private void setScore(MetricScores scores, String metricName, double score) {
        switch (metricName) {
            case "faithfulness" -> scores.setFaithfulness(score);
            case "answer_relevance" -> scores.setAnswerRelevance(score);
            case "context_precision" -> scores.setContextPrecision(score);
            case "context_recall" -> scores.setContextRecall(score);
            case "factual_correctness" -> scores.setFactualCorrectness(score);
            case "noise_sensitivity" -> scores.setNoiseSensitivity(score);
            case "negative_rejection" -> scores.setNegativeRejection(score);
            default -> log.debug("Unknown RAGAS metric: {}", metricName);
        }
    }

    /**
     * 对单个指标调用 ChatModel 评分 (1 次调用)，返回 0.0-1.0 分数
     */
    private double judgeMetric(String query, String context, String answer, String metricPrompt) {
        String evalPrompt = "问题: " + query + "\n上下文: " + context + "\n回答: " + answer;
        ChatModel chatModel = createChatModel();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(decoratePrompt(metricPrompt)));
        messages.add(new UserMessage(evalPrompt));
        ChatResponse response = chatModel.call(new Prompt(messages));
        String responseText = response.getResult().getOutput().getText();
        return parseScore(responseText);
    }

    private Map<String, String> resolveMetricPrompts() {
        Map<String, String> prompts = new LinkedHashMap<>(METRIC_PROMPTS);
        if (config != null && config.getMetricPrompts() != null && !config.getMetricPrompts().isEmpty()) {
            for (Map.Entry<String, String> entry : config.getMetricPrompts().entrySet()) {
                if (METRIC_PROMPTS.containsKey(entry.getKey()) && entry.getValue() != null && !entry.getValue().isBlank()) {
                    prompts.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return prompts;
    }

    private String decoratePrompt(String prompt) {
        StringBuilder builder = new StringBuilder(prompt != null ? prompt : "");
        if (config != null && config.getPromptVersion() != null && !config.getPromptVersion().isBlank()) {
            builder.append("\n\nPrompt version: ").append(config.getPromptVersion());
        }
        if (config != null && config.getPromptExamples() != null && !config.getPromptExamples().isEmpty()) {
            builder.append("\n\nCalibration examples:\n");
            for (String example : config.getPromptExamples()) {
                if (example != null && !example.isBlank()) {
                    builder.append("- ").append(example.trim()).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private ChatModel createChatModel() {
        return chatModelFactory.getChatModel(
                config.getPlatform(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModelName());
    }

    private double parseScore(String responseText) {
        try {
            String jsonStr = responseText;
            int start = responseText.indexOf("{");
            int end = responseText.lastIndexOf("}");
            if (start >= 0 && end > start) {
                jsonStr = responseText.substring(start, end + 1);
            }
            JSONObject json = JSONObject.parseObject(jsonStr);
            return json.getDoubleValue("score");
        } catch (Exception e) {
            log.warn("解析评分响应失败: {}", responseText, e);
            return 0.0;
        }
    }

    private EvaluationReport.ReportSummary computeSummary(List<MetricScores> allScores) {
        String[] metricNames = {"faithfulness", "answer_relevance", "context_precision", "context_recall",
                "factual_correctness", "noise_sensitivity", "negative_rejection"};

        Map<String, Double> mean = new LinkedHashMap<>();
        Map<String, Double> p50 = new LinkedHashMap<>();
        Map<String, Double> p90 = new LinkedHashMap<>();

        for (String name : metricNames) {
            List<Double> values = new ArrayList<>();
            for (MetricScores s : allScores) {
                values.add(s.getScore(name));
            }
            Collections.sort(values);

            mean.put(name, values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
            p50.put(name, percentile(values, 50));
            p90.put(name, percentile(values, 90));
        }

        return new EvaluationReport.ReportSummary(mean, p50, p90);
    }

    private double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);
        double index = (pct / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        double fraction = index - lower;
        return sorted.get(lower) * (1 - fraction) + sorted.get(upper) * fraction;
    }

    private record ItemEvaluation(EvaluationReport.ItemResult result, MetricScores scores) {
    }
}
