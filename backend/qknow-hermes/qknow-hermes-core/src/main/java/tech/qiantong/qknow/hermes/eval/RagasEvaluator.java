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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RagasEvaluator {
    private static final long EVALUATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final ExecutorService SAMPLE_EXECUTOR = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 4),
            r -> { Thread t = new Thread(r, "ragas-sample"); t.setDaemon(true); return t; });
    private static final ExecutorService METRIC_EXECUTOR = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 4),
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

        int total = dataset.getItems().size();
        int errorCount = 0;
        long startTime = System.currentTimeMillis();
        log.info("Starting RAGAS evaluation: {} items", total);

        List<CompletableFuture<ItemEvaluation>> futures = dataset.getItems().stream()
                .map(item -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return evaluateItem(item);
                    } catch (Exception e) {
                        log.error("Failed to evaluate item: {}", item.getQuery(), e);
                        return createErrorEvaluation(item.getQuery());
                    }
                }, SAMPLE_EXECUTOR))
                .toList();

        List<ItemEvaluation> evaluated = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                ItemEvaluation result = futures.get(i).join();
                evaluated.add(result);
                if (result.result().getAnswer().contains("[EVALUATION_ERROR]")
                        || result.result().getAnswer().contains("[GENERATION_ERROR]")) {
                    errorCount++;
                }
                // 每条都输出进度（含耗时和预计剩余）
                long elapsed = System.currentTimeMillis() - startTime;
                double avgPerItem = elapsed / (double) (i + 1);
                long eta = (long) (avgPerItem * (total - i - 1)) / 1000;
                if ((i + 1) % 5 == 0 || i == total - 1) {
                    log.info("Evaluation progress: {}/{} (elapsed={}s, ETA={}s, errors={})",
                            i + 1, total, elapsed / 1000, eta, errorCount);
                }
            } catch (Exception e) {
                log.error("Evaluation failed at item {}/{}", i + 1, total, e);
                evaluated.add(createErrorEvaluation(dataset.getItems().get(i).getQuery()));
                errorCount++;
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        List<EvaluationReport.ItemResult> results = evaluated.stream().map(ItemEvaluation::result).toList();
        List<MetricScores> allScores = evaluated.stream().map(ItemEvaluation::scores).toList();
        report.setItemResults(results);

        // 计算汇总统计 (mean, p50, p90 对每项指标)
        report.setSummary(computeSummary(allScores));
        log.info("RAGAS evaluation completed: {} items in {}s (avg={}s/item, errors={})",
                evaluated.size(), totalTime / 1000, String.format("%.1f", totalTime / 1000.0 / evaluated.size()), errorCount);
        return report;
    }

    private ItemEvaluation createErrorEvaluation(String query) {
        MetricScores scores = new MetricScores();
        scores.markAllInvalid(EvaluationError.ITEM_EVALUATION_FAILED);
        EvaluationReport.ItemResult result = new EvaluationReport.ItemResult();
        result.setQuery(query);
        result.setAnswer("[EVALUATION_ERROR]");
        result.setScores(scores.toReportMetrics());
        return new ItemEvaluation(result, scores);
    }

    /**
     * 对单条数据执行四项 Ragas 指标评估 (4 次 ChatModel 调用)
     */
    public MetricScores evaluateSingle(String query, String answer, List<String> contexts, String expectedAnswer) {
        MetricScores scores = new MetricScores();
        long deadlineNanos = System.nanoTime() + EVALUATION_TIMEOUT_NANOS;
        contexts = contexts != null ? contexts : List.of();
        String contextStr = String.join("\n", contexts);

        Map<String, String> metricPrompts = resolveMetricPrompts();
        Map<String, Future<Double>> futures = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : metricPrompts.entrySet()) {
            futures.put(entry.getKey(), METRIC_EXECUTOR.submit(
                    () -> judgeMetric(entry.getKey(), query, contextStr, answer, expectedAnswer, entry.getValue())));
        }
        collectMetricsUntil(scores, futures, deadlineNanos);

        scores.setPassed(scores.isAboveThreshold(config != null ? config.getThreshold() : MetricScores.DEFAULT_GATE_THRESHOLD));
        return scores;
    }

    /**
     * 调用 ChatModel 生成回答 (1 次调用)
     */
    private String generateAnswer(String query, List<String> contexts) {
        try {
            contexts = contexts != null ? contexts : List.of();
            String contextStr = contexts.isEmpty() ? "" : String.join("\n", contexts);
            String prompt = "基于以下知识回答问题。\n\n知识:\n" + contextStr + "\n\n问题: " + query;
            ChatModel chatModel = createChatModel();
            List<Message> messages = new ArrayList<>();
            messages.add(new UserMessage(prompt));
            ChatResponse response = chatModel.call(new Prompt(messages));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("Failed to generate answer for query: {}", query, e);
            return "[GENERATION_ERROR]";
        }
    }

    private ItemEvaluation evaluateItem(EvaluationDataset.EvalItem item) {
        String answer = generateAnswer(item.getQuery(), item.getGroundTruthContexts());
        if ("[GENERATION_ERROR]".equals(answer)) {
            MetricScores scores = new MetricScores();
            scores.markAllInvalid(EvaluationError.GENERATION_FAILED);
            EvaluationReport.ItemResult result = new EvaluationReport.ItemResult();
            result.setQuery(item.getQuery());
            result.setAnswer(answer);
            result.setScores(scores.toReportMetrics());
            return new ItemEvaluation(result, scores);
        }
        MetricScores scores = evaluateSingle(item.getQuery(), answer,
                item.getGroundTruthContexts(), item.getExpectedAnswer());
        EvaluationReport.ItemResult result = new EvaluationReport.ItemResult();
        result.setQuery(item.getQuery());
        result.setAnswer(answer);
        result.setScores(scores.toReportMetrics());
        return new ItemEvaluation(result, scores);
    }

    void collectMetric(MetricScores scores, String metricName, Future<Double> future,
                       long timeout, TimeUnit unit) {
        Map<String, Future<Double>> futures = new LinkedHashMap<>();
        futures.put(metricName, future);
        collectMetrics(scores, futures, timeout, unit);
    }

    void collectMetrics(MetricScores scores, Map<String, ? extends Future<Double>> futures,
                        long timeout, TimeUnit unit) {
        if (timeout < 0 || unit == null) {
            throw new IllegalArgumentException("valid timeout is required");
        }
        collectMetricsUntil(scores, futures, System.nanoTime() + unit.toNanos(timeout));
    }

    private void collectMetricsUntil(MetricScores scores, Map<String, ? extends Future<Double>> futures,
                                     long deadlineNanos) {
        for (Map.Entry<String, ? extends Future<Double>> entry : futures.entrySet()) {
            if (scores.getStatus(entry.getKey()) != EvaluationStatus.NOT_EVALUATED) {
                continue;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                finishAtDeadline(scores, futures);
                return;
            }
            try {
                setScore(scores, entry.getKey(), entry.getValue().get(remainingNanos, TimeUnit.NANOSECONDS));
            } catch (TimeoutException e) {
                finishAtDeadline(scores, futures);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelPending(scores, futures, EvaluationError.INTERRUPTED);
                return;
            } catch (ExecutionException e) {
                markMetricFailure(scores, entry.getKey(), e.getCause());
            }
        }
    }

    private void finishAtDeadline(MetricScores scores, Map<String, ? extends Future<Double>> futures) {
        for (Map.Entry<String, ? extends Future<Double>> entry : futures.entrySet()) {
            if (scores.getStatus(entry.getKey()) != EvaluationStatus.NOT_EVALUATED) {
                continue;
            }
            Future<Double> future = entry.getValue();
            if (future.isDone() && !future.isCancelled()) {
                try {
                    setScore(scores, entry.getKey(), future.get());
                } catch (ExecutionException e) {
                    markMetricFailure(scores, entry.getKey(), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancelPending(scores, futures, EvaluationError.INTERRUPTED);
                    return;
                }
            } else {
                future.cancel(true);
                scores.markInvalid(entry.getKey(), EvaluationError.TIMEOUT);
                log.warn("Metric evaluation timed out: {}", entry.getKey());
            }
        }
    }

    private void cancelPending(MetricScores scores, Map<String, ? extends Future<Double>> futures,
                               EvaluationError error) {
        for (Map.Entry<String, ? extends Future<Double>> entry : futures.entrySet()) {
            if (scores.getStatus(entry.getKey()) == EvaluationStatus.NOT_EVALUATED) {
                entry.getValue().cancel(true);
                scores.markInvalid(entry.getKey(), error);
            }
        }
    }

    private void markMetricFailure(MetricScores scores, String metricName, Throwable failure) {
        EvaluationError error = failure instanceof MetricParseException
                ? EvaluationError.PARSE_FAILED : EvaluationError.MODEL_CALL_FAILED;
        scores.markInvalid(metricName, error);
        log.warn("Metric evaluation failed: {}, code={}", metricName, error.getCode());
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
    private double judgeMetric(String metricName, String query, String context, String answer,
                               String expectedAnswer, String metricPrompt) {
        StringBuilder evalPrompt = new StringBuilder("问题: ").append(query)
                .append("\n上下文: ").append(context)
                .append("\n回答: ").append(answer);
        if (expectedAnswer != null && !expectedAnswer.isBlank()
                && ("factual_correctness".equals(metricName)
                || "context_recall".equals(metricName)
                || "context_precision".equals(metricName))) {
            evalPrompt.append("\n参考答案: ").append(expectedAnswer);
        }
        ChatModel chatModel = createChatModel();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(decoratePrompt(metricPrompt)));
        messages.add(new UserMessage(evalPrompt.toString()));
        ChatResponse response = chatModel.call(new Prompt(messages));
        return parseScore(response.getResult().getOutput().getText());
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

    private static final Pattern SCORE_PATTERN = Pattern.compile(
            "\"score\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)(?=\\s*[,}])");

    private double parseScore(String responseText) {
        if (responseText == null) {
            throw new MetricParseException();
        }
        // 优先用正则提取 score — 避免 LLM feedback 中中文引号导致 JSON 解析失败
        Matcher m = SCORE_PATTERN.matcher(responseText);
        if (m.find()) {
            return validateScore(Double.parseDouble(m.group(1)));
        }
        // fallback: 将中文引号替换后再用 JSON 解析
        try {
            String cleaned = responseText.replace("\u201c", "'").replace("\u201d", "'");
            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}");
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            JSONObject json = JSONObject.parseObject(cleaned);
            if (json == null || !json.containsKey("score")) {
                throw new MetricParseException();
            }
            return validateScore(json.getDouble("score"));
        } catch (MetricParseException e) {
            throw e;
        } catch (Exception e) {
            throw new MetricParseException();
        }
    }

    private double validateScore(Double score) {
        if (score == null || !Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new MetricParseException();
        }
        return score;
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
                if (s.isValid(name)) {
                    values.add(s.getScore(name));
                }
            }
            Collections.sort(values);

            if (!values.isEmpty()) {
                mean.put(name, values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
                p50.put(name, percentile(values, 50));
                p90.put(name, percentile(values, 90));
            }
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

    private static final class MetricParseException extends IllegalArgumentException {
    }
}
