package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.service.IChatClientService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class ContextualEnrichmentService {

    private static final String SYSTEM_PROMPT =
            "You are a helpful assistant. Given a document and a chunk from that document, " +
            "provide a short succinct context (50-100 words) to situate this chunk within the overall document. " +
            "This context will be prepended to the chunk before embedding. " +
            "Return ONLY the context, nothing else.";

    private final IChatClientService chatClientService;
    private final ContextualConfig config;
    private final Semaphore semaphore;

    public ContextualEnrichmentService(IChatClientService chatClientService, ContextualConfig config) {
        this.chatClientService = chatClientService;
        this.config = config;
        this.semaphore = new Semaphore(config.getMaxConcurrent());
    }

    public String enrich(String fullDocument, String chunkContent) {
        if (!config.isEnabled()) {
            return chunkContent;
        }
        try {
            semaphore.acquire();
            try {
                String context = generateContext(fullDocument, chunkContent);
                if (context != null && !context.isBlank()) {
                    return context + "\n\n" + chunkContent;
                }
                return chunkContent;
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Contextual enrichment interrupted, returning original chunk");
            return chunkContent;
        }
    }

    public CompletableFuture<String> enrichAsync(String fullDocument, String chunkContent) {
        if (!config.isEnabled()) {
            return CompletableFuture.completedFuture(chunkContent);
        }
        return CompletableFuture.supplyAsync(() -> enrich(fullDocument, chunkContent));
    }

    private String generateContext(String fullDocument, String chunkContent) {
        String truncatedDoc = truncate(fullDocument, config.getMaxDocChars());
        String userPrompt = String.format(
                "<document>%s</document>\n\nHere is the chunk we want to situate within the whole document:\n<chunk>%s</chunk>\n\nPlease give a short succinct context to situate this chunk within the overall document.",
                truncatedDoc, chunkContent);

        try {
            ChatClient chatClient = chatClientService.getChatClient(
                    config.getPlatform(), config.getBaseUrl(), config.getApiKey(), config.getModelName());
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            return result != null ? result.trim() : null;
        } catch (Exception e) {
            log.warn("Context generation failed for chunk (length={}): {}", chunkContent.length(), e.getMessage());
            return null;
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "hermes.rag.contextual")
    public static class ContextualConfig {
        private boolean enabled = false;
        private String platform = "DeepSeek";
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey;
        private String modelName = "deepseek-chat";
        private int maxDocChars = 8000;
        private int maxConcurrent = 3;
    }
}
