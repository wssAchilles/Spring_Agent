package tech.qiantong.qknow.module.kmc.service.rag;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.service.IChatClientService;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Slf4j
@Component
public class QueryTransformService {

    private static final String COMPRESS_SYSTEM = "Given conversation history and follow-up question, rewrite as standalone question. Return ONLY the rewritten question, nothing else.";
    private static final String COMPRESS_TEMPLATE = "History:\n{history}\n\nFollow-up: {query}\n\nStandalone question:";
    private static final String REWRITE_SYSTEM = "Rewrite search queries to be more specific for document retrieval. Return ONLY the rewritten query, nothing else.";
    private static final String REWRITE_TEMPLATE = "Original query: {query}\n\nRewritten query:";
    private static final String HYDE_SYSTEM = "Given a question, write a 3-5 sentence plausible answer paragraph. Return ONLY the paragraph.";
    private static final String MULTI_QUERY_SYSTEM = "Generate semantically diverse search query reformulations. Return ONLY a JSON array of strings.";
    private static final Pattern NUMERIC_QUERY = Pattern.compile(".*(\\d+|Day\\d+|day\\d+|[A-Za-z]+-\\d+|\\d{4}-\\d{1,2}-\\d{1,2}).*");
    private static final Pattern QUESTION_CUES = Pattern.compile(
            ".*(什么|如何|为什么|怎么|是否|哪些|多少|[?？]|where|what|why|how|when|which|explain|describe|compare).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_SYMBOL_QUERY = Pattern.compile(
            ".*([`{}()_=<>]|[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*|[A-Za-z_][A-Za-z0-9_]*\\(\\)).*");

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

        String strategy = normalizedStrategy();
        if ("none".equals(strategy)) {
            return query;
        }
        if ("hyde".equals(strategy) || "multi_query".equals(strategy)) {
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

    public String generateHypotheticalDocument(String query) {
        if (shouldSkipHyde(query)) {
            return query;
        }
        try {
            String result = getChatClient().prompt()
                    .system(HYDE_SYSTEM)
                    .user(query)
                    .call()
                    .content();
            return result != null && !result.isBlank() ? result.trim() : query;
        } catch (Exception e) {
            log.warn("HyDE generation failed, returning original query", e);
            return query;
        }
    }

    public List<String> expandQueries(String query, int count) {
        List<String> queries = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return queries;
        }
        queries.add(query);
        if (!config.isEnabled() || count <= 0 || !"multi_query".equals(normalizedStrategy())) {
            return queries;
        }

        String userPrompt = "Original query: " + query + "\nCount: " + count;
        try {
            String content = getChatClient().prompt()
                    .system(MULTI_QUERY_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .content();
            List<String> variants = JSON.parseArray(content, String.class);
            if (variants != null) {
                for (String variant : variants) {
                    if (variant != null && !variant.isBlank() && queries.size() <= count) {
                        queries.add(variant.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Multi-query expansion failed, returning original query", e);
        }
        return queries.stream().distinct().collect(Collectors.toList());
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public String getStrategy() {
        return config.getStrategy();
    }

    private String normalizedStrategy() {
        String strategy = config.getStrategy();
        return strategy == null ? "none" : strategy.trim().toLowerCase(Locale.ROOT);
    }

    public int getVariantCount() {
        return config.getVariantCount();
    }

    boolean shouldSkipHyde(String query) {
        if (!config.isEnabled() || query == null || query.isBlank() || isNumericQuery(query)
                || isCodeSymbolQuery(query)) {
            return true;
        }
        String compact = query.replaceAll("\\s+", "");
        return compact.length() <= 16 && !QUESTION_CUES.matcher(query).matches();
    }

    boolean isNumericQuery(String query) {
        return query != null && NUMERIC_QUERY.matcher(query).matches();
    }

    private boolean isCodeSymbolQuery(String query) {
        return CODE_SYMBOL_QUERY.matcher(query).matches();
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
        private int variantCount = 3;
    }
}
