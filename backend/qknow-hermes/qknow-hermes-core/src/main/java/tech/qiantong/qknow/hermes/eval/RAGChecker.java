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

/**
 * RAGChecker 风格离线评估框架（Java 等效实现）
 * 基于 Claim-level Entailment 的细粒度 RAG 评估
 * 参考：Amazon Science, NeurIPS 2024
 */
@Slf4j
@Component
public class RAGChecker {

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

    public RAGChecker(ChatModelFactory chatModelFactory, RagasEvalConfig config) {
        this.chatModelFactory = chatModelFactory;
        this.config = config;
    }

    /**
     * 执行 RAGChecker 评估
     */
    public RAGCheckerReport evaluate(String query, String answer, List<String> contexts) {
        RAGCheckerReport report = new RAGCheckerReport();
        report.setQuery(query);
        report.setAnswer(answer);

        String contextStr = String.join("\n", contexts);

        // 1. 提取 claims
        List<String> claims = extractClaims(answer);
        report.setTotalClaims(claims.size());

        if (claims.isEmpty()) {
            report.setPrecision(1.0);
            report.setRecall(1.0);
            report.setF1(1.0);
            return report;
        }

        // 2. 对每个 claim 做蕴含判断
        int entailed = 0;
        int contradicted = 0;
        int notFound = 0;

        for (String claim : claims) {
            String judgment = judgeEntailment(contextStr, claim);
            switch (judgment) {
                case "ENTAILED" -> entailed++;
                case "CONTRADICTED" -> contradicted++;
                default -> notFound++;
            }
        }

        // 3. 计算指标
        double precision = claims.isEmpty() ? 1.0 : (double) entailed / claims.size();
        double recall = contexts.isEmpty() ? 1.0 : Math.min(1.0, (double) entailed / Math.max(1, contexts.size()));
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
        double hallucination = claims.isEmpty() ? 0.0 : (double) (contradicted + notFound) / claims.size();

        report.setPrecision(precision);
        report.setRecall(recall);
        report.setF1(f1);
        report.setHallucination(hallucination);
        report.setEntailedClaims(entailed);
        report.setContradictedClaims(contradicted);
        report.setNotFoundClaims(notFound);

        return report;
    }

    /**
     * 批量评估
     */
    public List<RAGCheckerReport> batchEvaluate(List<EvalSample> samples) {
        List<RAGCheckerReport> reports = new ArrayList<>();
        for (EvalSample sample : samples) {
            try {
                reports.add(evaluate(sample.query, sample.answer, sample.contexts));
            } catch (Exception e) {
                log.warn("RAGChecker evaluation failed for query: {}", sample.query, e);
            }
        }
        return reports;
    }

    /**
     * 计算批量评估的汇总统计
     */
    public RAGCheckerSummary summarize(List<RAGCheckerReport> reports) {
        RAGCheckerSummary summary = new RAGCheckerSummary();
        if (reports.isEmpty()) return summary;

        summary.setTotalSamples(reports.size());
        summary.setAvgPrecision(reports.stream().mapToDouble(RAGCheckerReport::getPrecision).average().orElse(0));
        summary.setAvgRecall(reports.stream().mapToDouble(RAGCheckerReport::getRecall).average().orElse(0));
        summary.setAvgF1(reports.stream().mapToDouble(RAGCheckerReport::getF1).average().orElse(0));
        summary.setAvgHallucination(reports.stream().mapToDouble(RAGCheckerReport::getHallucination).average().orElse(0));
        summary.setTotalClaims(reports.stream().mapToInt(RAGCheckerReport::getTotalClaims).sum());
        summary.setTotalEntailed(reports.stream().mapToInt(RAGCheckerReport::getEntailedClaims).sum());
        return summary;
    }

    private List<String> extractClaims(String answer) {
        try {
            ChatModel chatModel = createChatModel();
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new UserMessage(CLAIM_EXTRACTION_PROMPT + answer)
            )));
            String text = response.getResult().getOutput().getText();
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
            log.warn("Claim extraction failed", e);
            return List.of();
        }
    }

    private String judgeEntailment(String context, String claim) {
        try {
            ChatModel chatModel = createChatModel();
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage("You are an entailment judge. Return ONLY: ENTAILED, CONTRADICTED, or NOT_FOUND"),
                    new UserMessage(String.format("Context: %s\nClaim: %s", context, claim))
            )));
            String result = response.getResult().getOutput().getText();
            if (result == null) return "NOT_FOUND";
            result = result.trim().toUpperCase();
            if (result.contains("ENTAILED")) return "ENTAILED";
            if (result.contains("CONTRADICTED")) return "CONTRADICTED";
            return "NOT_FOUND";
        } catch (Exception e) {
            log.warn("Entailment judgment failed", e);
            return "NOT_FOUND";
        }
    }

    private ChatModel createChatModel() {
        return chatModelFactory.getChatModel(
                config.getPlatform(), config.getBaseUrl(), config.getApiKey(), config.getModelName());
    }

    private JSONArray parseJsonArray(String text) {
        if (text == null) return new JSONArray();
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
        private double precision;
        private double recall;
        private double f1;
        private double hallucination;
    }

    @Data
    public static class RAGCheckerSummary {
        private int totalSamples;
        private double avgPrecision;
        private double avgRecall;
        private double avgF1;
        private double avgHallucination;
        private int totalClaims;
        private int totalEntailed;
    }
}
