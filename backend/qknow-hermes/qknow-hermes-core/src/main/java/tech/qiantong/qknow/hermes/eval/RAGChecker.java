package tech.qiantong.qknow.hermes.eval;

import com.alibaba.fastjson2.JSONArray;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Future;

/**
 * RAGChecker 风格离线评估框架（Java 等效实现）
 * 基于 Claim-level Entailment 的细粒度 RAG 评估
 * 参考：Amazon Science, NeurIPS 2024
 */
@Slf4j
@Component
public class RAGChecker {
    private static final long DEFAULT_EVALUATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final ExecutorService SAMPLE_EXECUTOR = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 8),
            r -> { Thread t = new Thread(r, "rag-checker-sample"); t.setDaemon(true); return t; });
    private static final ExecutorService CLAIM_EXECUTOR = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), 8),
            r -> { Thread t = new Thread(r, "rag-checker-claim"); t.setDaemon(true); return t; });


    private static final String CLAIM_EXTRACTION_PROMPT =
            "Extract all factual claims from the following answer. " +
            "Return as JSON array of strings, each string is one claim. " +
            "Example: [\"claim 1\", \"claim 2\"]\n\nAnswer: ";

    private static final String ENTAILMENT_PROMPT =
            "Given a context and a claim, determine if the claim is supported by the context. " +
            "Return ONLY one of: ENTAILED, CONTRADICTED, NOT_FOUND\n\n" +
            "Context: %s\nClaim: %s";

    private final ChatModelFactory chatModelFactory;
    private final RagasEvalConfig config;
    private final long evaluationTimeoutNanos;

    public RAGChecker(ChatModelFactory chatModelFactory, RagasEvalConfig config) {
        this(chatModelFactory, config, DEFAULT_EVALUATION_TIMEOUT_NANOS, TimeUnit.NANOSECONDS);
    }

    RAGChecker(ChatModelFactory chatModelFactory, RagasEvalConfig config, long timeout, TimeUnit unit) {
        if (timeout <= 0 || unit == null) {
            throw new IllegalArgumentException("positive timeout is required");
        }
        this.chatModelFactory = chatModelFactory;
        this.config = config;
        this.evaluationTimeoutNanos = unit.toNanos(timeout);
    }

    /**
     * 执行 RAGChecker 评估
     */
    public RAGCheckerReport evaluate(String query, String answer, List<String> contexts) {
        long deadlineNanos = System.nanoTime() + evaluationTimeoutNanos;
        RAGCheckerReport report = new RAGCheckerReport();
        report.setQuery(query);
        report.setAnswer(answer);
        contexts = contexts != null ? contexts : List.of();

        String contextStr = String.join("\n", contexts);

        // 1. 提取 claims
        Future<List<String>> extraction = CLAIM_EXECUTOR.submit(() -> extractClaims(answer));
        List<String> claims;
        try {
            claims = await(extraction, deadlineNanos);
        } catch (TimeoutException e) {
            extraction.cancel(true);
            report.markInvalid(EvaluationError.TIMEOUT);
            return report;
        } catch (InterruptedException e) {
            extraction.cancel(true);
            Thread.currentThread().interrupt();
            report.markInvalid(EvaluationError.INTERRUPTED);
            return report;
        } catch (ExecutionException e) {
            report.markInvalid(errorFrom(e.getCause(), EvaluationError.ITEM_EVALUATION_FAILED));
            return report;
        }
        report.setTotalClaims(claims.size());

        if (claims.isEmpty()) {
            report.markInvalid(EvaluationError.NO_CLAIMS_EXTRACTED);
            return report;
        }

        // 2. 对每个 claim 做蕴含判断（并行）
        int entailed = 0;
        int contradicted = 0;
        int notFound = 0;

        var futures = claims.stream()
                .map(claim -> CLAIM_EXECUTOR.submit(() -> judgeEntailment(contextStr, claim)))
                .toList();
        for (var future : futures) {
            try {
                String judgment = await(future, deadlineNanos);
                switch (judgment) {
                    case "ENTAILED" -> entailed++;
                    case "CONTRADICTED" -> contradicted++;
                    case "NOT_FOUND" -> notFound++;
                    default -> throw new EvaluationFailure(EvaluationError.ENTAILMENT_PARSE_FAILED);
                }
            } catch (TimeoutException e) {
                cancelAll(futures);
                report.setEntailedClaims(entailed);
                report.setContradictedClaims(contradicted);
                report.setNotFoundClaims(notFound);
                report.markInvalid(EvaluationError.TIMEOUT);
                return report;
            } catch (InterruptedException e) {
                cancelAll(futures);
                Thread.currentThread().interrupt();
                report.setEntailedClaims(entailed);
                report.setContradictedClaims(contradicted);
                report.setNotFoundClaims(notFound);
                report.markInvalid(EvaluationError.INTERRUPTED);
                return report;
            } catch (ExecutionException e) {
                cancelAll(futures);
                report.setEntailedClaims(entailed);
                report.setContradictedClaims(contradicted);
                report.setNotFoundClaims(notFound);
                report.markInvalid(errorFrom(e.getCause(), EvaluationError.ENTAILMENT_MODEL_FAILED));
                return report;
            } catch (EvaluationFailure e) {
                cancelAll(futures);
                report.setEntailedClaims(entailed);
                report.setContradictedClaims(contradicted);
                report.setNotFoundClaims(notFound);
                report.markInvalid(e.error());
                return report;
            }
        }

        // 3. 计算指标
        report.setEntailedClaims(entailed);
        report.setContradictedClaims(contradicted);
        report.setNotFoundClaims(notFound);
        report.setEntailedRate((double) entailed / claims.size());
        report.setContradictedRate((double) contradicted / claims.size());
        report.setNotFoundRate((double) notFound / claims.size());
        report.setStatus(EvaluationStatus.VALID);

        return report;
    }

    /**
     * 批量评估
     */
    public List<RAGCheckerReport> batchEvaluate(List<EvalSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<RAGCheckerReport>> futures = samples.stream()
                .map(sample -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return evaluate(sample.query, sample.answer, sample.contexts);
                    } catch (Exception e) {
                        log.warn("RAGChecker evaluation failed for one sample");
                        return invalidReport(sample, EvaluationError.ITEM_EVALUATION_FAILED);
                    }
                }, SAMPLE_EXECUTOR))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * 计算批量评估的汇总统计
     */
    public RAGCheckerSummary summarize(List<RAGCheckerReport> reports) {
        RAGCheckerSummary summary = new RAGCheckerSummary();
        if (reports == null || reports.isEmpty()) return summary;

        List<RAGCheckerReport> attempted = reports.stream().filter(Objects::nonNull).toList();
        List<RAGCheckerReport> valid = attempted.stream()
                .filter(report -> report.getStatus() == EvaluationStatus.VALID)
                .toList();
        summary.setTotalSamples(attempted.size());
        summary.setValidSamples(valid.size());
        summary.setInvalidSamples((int) attempted.stream()
                .filter(report -> report.getStatus() == EvaluationStatus.INVALID).count());
        summary.setAvgEntailedRate(valid.stream().mapToDouble(RAGCheckerReport::getEntailedRate).average().orElse(0));
        summary.setAvgContradictedRate(valid.stream().mapToDouble(RAGCheckerReport::getContradictedRate).average().orElse(0));
        summary.setAvgNotFoundRate(valid.stream().mapToDouble(RAGCheckerReport::getNotFoundRate).average().orElse(0));
        summary.setTotalClaims(valid.stream().mapToInt(RAGCheckerReport::getTotalClaims).sum());
        summary.setTotalEntailed(valid.stream().mapToInt(RAGCheckerReport::getEntailedClaims).sum());
        summary.setTotalContradicted(valid.stream().mapToInt(RAGCheckerReport::getContradictedClaims).sum());
        summary.setTotalNotFound(valid.stream().mapToInt(RAGCheckerReport::getNotFoundClaims).sum());
        return summary;
    }

    private <T> T await(Future<T> future, long deadlineNanos)
            throws InterruptedException, ExecutionException, TimeoutException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new TimeoutException();
        }
        return future.get(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private void cancelAll(List<? extends Future<?>> futures) {
        futures.forEach(future -> future.cancel(true));
    }

    private EvaluationError errorFrom(Throwable failure, EvaluationError fallback) {
        return failure instanceof EvaluationFailure evaluationFailure
                ? evaluationFailure.error() : fallback;
    }

    private RAGCheckerReport invalidReport(EvalSample sample, EvaluationError error) {
        RAGCheckerReport report = new RAGCheckerReport();
        report.setQuery(sample.query);
        report.setAnswer(sample.answer);
        report.markInvalid(error);
        return report;
    }

    private List<String> extractClaims(String answer) {
        String text;
        try {
            ChatModel chatModel = createChatModel();
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new UserMessage(resolveClaimExtractionPrompt() + answer)
            )));
            text = response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("Claim extraction model call failed");
            throw new EvaluationFailure(EvaluationError.CLAIM_EXTRACTION_MODEL_FAILED);
        }
        try {
            JSONArray arr = parseJsonArray(text);
            List<String> claims = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                String claim = arr.getString(i);
                if (claim != null && !claim.isBlank()) {
                    claims.add(claim.trim());
                }
            }
            return claims;
        } catch (Exception e) {
            log.warn("Claim extraction response could not be parsed");
            throw new EvaluationFailure(EvaluationError.CLAIM_EXTRACTION_PARSE_FAILED);
        }
    }

    private String judgeEntailment(String context, String claim) {
        String result;
        try {
            ChatModel chatModel = createChatModel();
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(resolveEntailmentSystemPrompt()),
                    new UserMessage(resolveEntailmentUserPrompt(context, claim))
            )));
            result = response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("Entailment model call failed");
            throw new EvaluationFailure(EvaluationError.ENTAILMENT_MODEL_FAILED);
        }
        if (result == null) {
            throw new EvaluationFailure(EvaluationError.ENTAILMENT_PARSE_FAILED);
        }
        return switch (result.trim().toUpperCase(Locale.ROOT)) {
            case "ENTAILED" -> "ENTAILED";
            case "CONTRADICTED" -> "CONTRADICTED";
            case "NOT_FOUND" -> "NOT_FOUND";
            default -> throw new EvaluationFailure(EvaluationError.ENTAILMENT_PARSE_FAILED);
        };
    }

    private ChatModel createChatModel() {
        return chatModelFactory.getChatModel(
                config.getPlatform(), config.getBaseUrl(), config.getApiKey(), config.getModelName());
    }

    private String resolveClaimExtractionPrompt() {
        String prompt = config != null ? config.getClaimExtractionPrompt() : null;
        if (prompt == null || prompt.isBlank()) {
            prompt = CLAIM_EXTRACTION_PROMPT;
        }
        return decoratePrompt(prompt);
    }

    private String resolveEntailmentSystemPrompt() {
        String prompt = config != null ? config.getEntailmentSystemPrompt() : null;
        if (prompt == null || prompt.isBlank()) {
            prompt = "You are an entailment judge. Return ONLY: ENTAILED, CONTRADICTED, or NOT_FOUND";
        }
        return decoratePrompt(prompt);
    }

    private String resolveEntailmentUserPrompt(String context, String claim) {
        String prompt = config != null ? config.getEntailmentUserPrompt() : null;
        if (prompt == null || prompt.isBlank()) {
            prompt = ENTAILMENT_PROMPT;
        }
        return String.format(prompt, context, claim);
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

    private JSONArray parseJsonArray(String text) {
        if (text == null) throw new IllegalArgumentException("missing response");
        int start = text.indexOf("[");
        int end = text.lastIndexOf("]");
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return JSONArray.parseArray(text);
    }

    @Data
    public static class EvalSample {
        private String query;
        private String answer;
        private List<String> contexts;
        private String expectedAnswer;
    }

    @Data
    public static class RAGCheckerReport {
        private String query;
        private String answer;
        private int totalClaims;
        private int entailedClaims;
        private int contradictedClaims;
        private int notFoundClaims;
        private double entailedRate;
        private double contradictedRate;
        private double notFoundRate;
        private EvaluationStatus status = EvaluationStatus.NOT_EVALUATED;
        private String errorCode;
        private String reason;

        private void markInvalid(EvaluationError error) {
            status = EvaluationStatus.INVALID;
            errorCode = error.getCode();
            reason = error.getReason();
        }
    }

    @Data
    public static class RAGCheckerSummary {
        private int totalSamples;
        private int validSamples;
        private int invalidSamples;
        private double avgEntailedRate;
        private double avgContradictedRate;
        private double avgNotFoundRate;
        private int totalClaims;
        private int totalEntailed;
        private int totalContradicted;
        private int totalNotFound;
    }

    private static final class EvaluationFailure extends RuntimeException {
        private final EvaluationError error;

        private EvaluationFailure(EvaluationError error) {
            this.error = error;
        }

        private EvaluationError error() {
            return error;
        }
    }
}
