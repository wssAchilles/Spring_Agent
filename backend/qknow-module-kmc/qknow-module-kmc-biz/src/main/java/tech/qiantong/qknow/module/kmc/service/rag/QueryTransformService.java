package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.service.IChatClientService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QueryTransformService {

    private static final String COMPRESS_SYSTEM = "Given conversation history and follow-up question, rewrite as standalone question. Return ONLY the rewritten question, nothing else.";
    private static final String COMPRESS_TEMPLATE = "History:\n{history}\n\nFollow-up: {query}\n\nStandalone question:";
    private static final String REWRITE_SYSTEM = "Rewrite search queries to be more specific for document retrieval. Return ONLY the rewritten query, nothing else.";
    private static final String REWRITE_TEMPLATE = "Original query: {query}\n\nRewritten query:";

    private final IChatClientService chatClientService;
    private final QueryTransformConfig config;

    public QueryTransformService(IChatClientService chatClientService, QueryTransformConfig config) {
        this.chatClientService = chatClientService;
        this.config = config;
    }

    public String compressQuery(String currentQuery, List<Message> history) {
        if (!config.isEnabled()) {
            return currentQuery;
        }
        if (history == null || history.isEmpty()) {
            return currentQuery;
        }

        String historyText = history.stream()
                .map(m -> m.getMessageType().name() + ": " + m.getText())
                .collect(Collectors.joining("\n"));

        String userPrompt = COMPRESS_TEMPLATE
                .replace("{history}", historyText)
                .replace("{query}", currentQuery);

        try {
            String result = getChatClient().prompt()
                    .system(COMPRESS_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .content();
            return result != null ? result.trim() : currentQuery;
        } catch (Exception e) {
            log.warn("Query compression failed, returning original query", e);
            return currentQuery;
        }
    }

    public String rewriteQuery(String query) {
        if (!config.isEnabled()) {
            return query;
        }
        if (query == null || query.isBlank()) {
            return query;
        }

        String strategy = config.getStrategy();
        if ("none".equals(strategy)) {
            return query;
        }

        String userPrompt = REWRITE_TEMPLATE.replace("{query}", query);

        try {
            String result = getChatClient().prompt()
                    .system(REWRITE_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .content();
            return result != null ? result.trim() : query;
        } catch (Exception e) {
            log.warn("Query rewrite failed, returning original query", e);
            return query;
        }
    }

    private ChatClient getChatClient() {
        return chatClientService.getChatClient(
                config.getPlatform(), config.getBaseUrl(), config.getApiKey(), config.getModelName());
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "hermes.rag.query-transform")
    public static class QueryTransformConfig {
        private boolean enabled = true;
        private String strategy = "rewrite";
        private String platform = "OpenAI";
        private String baseUrl = "https://api.openai.com";
        private String apiKey;
        private String modelName = "gpt-4o-mini";
    }
}
