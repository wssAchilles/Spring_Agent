package tech.qiantong.qknow.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.eval.RAGChecker;
import tech.qiantong.qknow.hermes.eval.RagasEvalConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("RAGChecker 完整评估集成测试")
class RAGCheckerFullEvaluationTest {

    @Test
    @DisplayName("加载数据库数据集并运行 RAGChecker 评估")
    void loadFromDbAndEvaluate() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:postgresql://localhost:5432/ai_agent");
        ds.setUsername("postgres");
        ds.setPassword("postgres");
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT query, expected_answer, ground_truth_contexts FROM eval_dataset_item WHERE dataset_id = 1");

        log.info("Loaded {} items from database", items.size());

        RagasEvalConfig config = new RagasEvalConfig();
        config.setPlatform("deepseek");
        config.setModelName("deepseek-chat");
        config.setApiKey(System.getenv().getOrDefault("HERMES_OPENAI_API_KEY", "REDACTED"));
        config.setBaseUrl(System.getenv().getOrDefault("HERMES_OPENAI_BASE_URL", "https://api.deepseek.com"));

        ChatModelFactory factory = new ChatModelFactory();
        RAGChecker checker = new RAGChecker(factory, config);

        log.info("Starting RAGChecker evaluation...");
        long start = System.currentTimeMillis();

        List<RAGChecker.RAGCheckerReport> reports = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> row = items.get(i);
            String query = (String) row.get("query");
            String contextsJson = (String) row.get("ground_truth_contexts");

            List<String> contexts = new ArrayList<>();
            if (contextsJson != null && !contextsJson.isBlank()) {
                JSONArray arr = JSON.parseArray(contextsJson);
                for (int j = 0; j < arr.size(); j++) {
                    contexts.add(arr.getString(j));
                }
            }

            // 用 LLM 生成答案
            String answer = generateAnswer(factory, config, query, contexts);
            log.info("[{}/{}] {}", i + 1, items.size(), query);
            log.info("  生成答案: {}", answer.substring(0, Math.min(80, answer.length())));

            // RAGChecker 评估
            RAGChecker.RAGCheckerReport report = checker.evaluate(query, answer, contexts);
            reports.add(report);
            log.info("  precision={} recall={} f1={} hallucination={} claims={}",
                    fmt(report.getPrecision()), fmt(report.getRecall()),
                    fmt(report.getF1()), fmt(report.getHallucination()),
                    report.getTotalClaims());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("RAGChecker evaluation completed in {} seconds", elapsed / 1000);

        // 汇总
        RAGChecker checker2 = new RAGChecker(factory, config);
        RAGChecker.RAGCheckerSummary summary = checker2.summarize(reports);

        log.info("=== RAGChecker Summary ===");
        log.info("avg_precision:    {}", fmt(summary.getAvgPrecision()));
        log.info("avg_recall:       {}", fmt(summary.getAvgRecall()));
        log.info("avg_f1:           {}", fmt(summary.getAvgF1()));
        log.info("avg_hallucination:{}", fmt(summary.getAvgHallucination()));

        assertTrue(summary.getAvgPrecision() > 0.0, "precision should be > 0");
        assertTrue(summary.getAvgRecall() > 0.0, "recall should be > 0");
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
