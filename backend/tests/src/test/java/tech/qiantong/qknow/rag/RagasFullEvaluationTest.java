package tech.qiantong.qknow.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.eval.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("RAGAS 完整评估集成测试")
class RagasFullEvaluationTest {

    @Test
    @DisplayName("加载数据库数据集并运行评估")
    void loadFromDbAndEvaluate() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:postgresql://localhost:5432/ai_agent");
        ds.setUsername("postgres");
        ds.setPassword("postgres");
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT id, query, expected_answer, ground_truth_contexts FROM eval_dataset_item WHERE dataset_id = 1");

        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setName("RAG Golden Dataset");
        List<EvaluationDataset.EvalItem> evalItems = new ArrayList<>();
        for (Map<String, Object> row : items) {
            String query = (String) row.get("query");
            String expectedAnswer = (String) row.get("expected_answer");
            String contextsJson = (String) row.get("ground_truth_contexts");
            List<String> contexts = new ArrayList<>();
            if (contextsJson != null && !contextsJson.isBlank()) {
                JSONArray arr = JSON.parseArray(contextsJson);
                for (int i = 0; i < arr.size(); i++) {
                    contexts.add(arr.getString(i));
                }
            }
            evalItems.add(new EvaluationDataset.EvalItem(query, expectedAnswer, contexts));
        }
        dataset.setItems(evalItems);
        log.info("Loaded {} evaluation items from database", evalItems.size());

        RagasEvalConfig config = new RagasEvalConfig();
        config.setPlatform("deepseek");
        config.setModelName("deepseek-chat");
        config.setThreshold(0.85);
        config.setApiKey(System.getenv().getOrDefault("HERMES_OPENAI_API_KEY", "REDACTED"));
        config.setBaseUrl(System.getenv().getOrDefault("HERMES_OPENAI_BASE_URL", "https://api.deepseek.com"));

        ChatModelFactory factory = new ChatModelFactory();
        RagasEvaluator evaluator = new RagasEvaluator(factory, config);

        log.info("Starting RAGAS evaluation...");
        long start = System.currentTimeMillis();

        List<MetricScores> allScores = new ArrayList<>();
        for (int i = 0; i < evalItems.size(); i++) {
            var item = evalItems.get(i);
            log.info("[{}/{}] {}", i + 1, evalItems.size(), item.getQuery());
            try {
                String generatedAnswer = generateAnswer(factory, config, item.getQuery(), item.getGroundTruthContexts());
                log.info("  生成答案: {}", generatedAnswer.substring(0, Math.min(80, generatedAnswer.length())));

                MetricScores scores = evaluator.evaluateSingle(
                        item.getQuery(), generatedAnswer,
                        item.getGroundTruthContexts(), item.getExpectedAnswer());
                allScores.add(scores);
                log.info("  faith={} rel={} recall={} prec={} fact={}",
                        fmt(scores.getFaithfulness()), fmt(scores.getAnswerRelevance()),
                        fmt(scores.getContextRecall()), fmt(scores.getContextPrecision()),
                        fmt(scores.getFactualCorrectness()));
            } catch (Exception e) {
                log.error("  FAILED: {}", e.getMessage());
                allScores.add(new MetricScores());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Evaluation completed in {} seconds", elapsed / 1000);

        double faithMean = allScores.stream().mapToDouble(MetricScores::getFaithfulness).average().orElse(0);
        double relMean = allScores.stream().mapToDouble(MetricScores::getAnswerRelevance).average().orElse(0);
        double precMean = allScores.stream().mapToDouble(MetricScores::getContextPrecision).average().orElse(0);
        double recallMean = allScores.stream().mapToDouble(MetricScores::getContextRecall).average().orElse(0);
        double factMean = allScores.stream().mapToDouble(MetricScores::getFactualCorrectness).average().orElse(0);

        log.info("=== RAGAS Summary ===");
        log.info("faithfulness:        {}", fmt(faithMean));
        log.info("answer_relevance:    {}", fmt(relMean));
        log.info("context_precision:   {}", fmt(precMean));
        log.info("context_recall:      {}", fmt(recallMean));
        log.info("factual_correctness: {}", fmt(factMean));

        assertTrue(faithMean > 0.0, "faithfulness should be > 0");
        assertTrue(relMean > 0.0, "answer_relevance should be > 0");
    }

    private String generateAnswer(ChatModelFactory factory, RagasEvalConfig config,
                                   String query, List<String> contexts) {
        String contextStr = String.join("\n\n", contexts);
        String prompt = "基于以下参考内容，用中文简洁回答问题。\n\n参考内容:\n" + contextStr + "\n\n问题: " + query;
        try {
            var chatModel = factory.getChatModel(config.getPlatform(), config.getBaseUrl(),
                    config.getApiKey(), config.getModelName());
            var response = chatModel.call(new Prompt(List.of(new UserMessage(prompt))));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("Failed to generate answer: {}", e.getMessage());
            return "[生成失败]";
        }
    }

    private String fmt(double v) {
        return String.format("%.3f", v);
    }
}
