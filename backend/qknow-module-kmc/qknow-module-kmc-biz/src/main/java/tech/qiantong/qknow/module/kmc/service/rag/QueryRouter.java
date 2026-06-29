package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.service.IChatClientService;

import java.util.regex.Pattern;

@Slf4j
@Component
public class QueryRouter {

    private static final String CLASSIFY_SYSTEM = """
            You are a query complexity classifier. Given a user query, classify it into one of three levels:
            
            SIMPLE: Factual questions that can be answered directly from general knowledge (e.g., "what is X?", "when did Y happen?")
            MEDIUM: Questions that need document retrieval but are straightforward (e.g., "what does the document say about X?", "summarize Y")
            COMPLEX: Multi-hop reasoning, comparison, synthesis, or analysis questions (e.g., "compare X and Y", "analyze the trend", "what are the implications of X on Y?")
            
            Return ONLY one word: SIMPLE, MEDIUM, or COMPLEX""";

    private static final Pattern SIMPLE_PATTERNS = Pattern.compile(
            ".*(^|\\s)(你好|hello|hi|谢谢|thanks|时间|日期|今天|天气|几点|what time|what date).*",
            Pattern.CASE_INSENSITIVE);

    private final IChatClientService chatClientService;
    private final QueryRouterConfig config;

    public QueryRouter(IChatClientService chatClientService, QueryRouterConfig config) {
        this.chatClientService = chatClientService;
        this.config = config;
    }

    public QueryRoute classify(String query) {
        if (!config.isEnabled()) {
            return QueryRoute.MEDIUM;
        }
        if (query == null || query.isBlank()) {
            return QueryRoute.SIMPLE;
        }

        // Rule-based fast path for obvious simple queries
        if (query.length() < 10 || SIMPLE_PATTERNS.matcher(query).matches()) {
            return QueryRoute.SIMPLE;
        }

        // LLM-based classification for ambiguous cases
        try {
            ChatClient chatClient = chatClientService.getChatClient(
                    config.getPlatform(), config.getBaseUrl(), config.getApiKey(), config.getModelName());
            String result = chatClient.prompt()
                    .system(CLASSIFY_SYSTEM)
                    .user(query)
                    .call()
                    .content();
            return parseRoute(result);
        } catch (Exception e) {
            log.warn("Query classification failed, defaulting to MEDIUM: {}", e.getMessage());
            return QueryRoute.MEDIUM;
        }
    }

    private QueryRoute parseRoute(String result) {
        if (result == null) return QueryRoute.MEDIUM;
        String normalized = result.trim().toUpperCase();
        if (normalized.contains("SIMPLE")) return QueryRoute.SIMPLE;
        if (normalized.contains("COMPLEX")) return QueryRoute.COMPLEX;
        return QueryRoute.MEDIUM;
    }

    public enum QueryRoute {
        SIMPLE,   // LLM direct answer, no retrieval
        MEDIUM,   // Standard RAG retrieval + generation
        COMPLEX   // Multi-step reasoning (Plan-and-Solve or SupervisorAgent)
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "hermes.rag.router")
    public static class QueryRouterConfig {
        private boolean enabled = true;
        private String platform = "DeepSeek";
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey;
        private String modelName = "deepseek-chat";
    }
}
