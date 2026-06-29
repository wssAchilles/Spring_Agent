package tech.qiantong.qknow.module.kmc.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.service.IChatClientService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HyPEIndexer {

    private static final String SYSTEM_PROMPT =
            "Given a text passage, generate diverse questions that this passage could answer. " +
            "Return ONLY a JSON array of strings, nothing else. " +
            "Example: [\"What is X?\", \"How does Y work?\", \"When did Z happen?\"]";

    private final IChatClientService chatClientService;
    private final HyPEConfig config;

    public HyPEIndexer(IChatClientService chatClientService, HyPEConfig config) {
        this.chatClientService = chatClientService;
        this.config = config;
    }

    public List<Document> generateHypotheticalDocuments(Document originalChunk, String fullDocument) {
        if (!config.isEnabled()) {
            return List.of();
        }

        String chunkContent = originalChunk.getText();
        if (chunkContent == null || chunkContent.isBlank() || chunkContent.length() < 50) {
            return List.of();
        }

        List<String> questions = generateQuestions(chunkContent);
        if (questions.isEmpty()) {
            return List.of();
        }

        List<Document> hypotheticalDocs = new ArrayList<>();
        Map<String, Object> originalMetadata = originalChunk.getMetadata();

        for (String question : questions) {
            Document hypoDoc = new Document(question);
            Map<String, Object> metadata = hypoDoc.getMetadata();
            // Copy key metadata from original chunk
            metadata.putAll(originalMetadata);
            metadata.put("chunk_type", "hypothetical");
            metadata.put("original_segment_id", originalChunk.getId());
            hypotheticalDocs.add(hypoDoc);
        }

        return hypotheticalDocs;
    }

    private List<String> generateQuestions(String chunkContent) {
        String truncated = chunkContent.length() > config.getMaxChunkChars()
                ? chunkContent.substring(0, config.getMaxChunkChars())
                : chunkContent;

        String userPrompt = String.format(
                "Generate %d diverse questions that the following passage could answer.\n\nPassage: %s",
                config.getQuestionCount(), truncated);

        try {
            ChatClient chatClient = chatClientService.getChatClient(
                    config.getPlatform(), config.getBaseUrl(), config.getApiKey(), config.getModelName());
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            return parseQuestions(result);
        } catch (Exception e) {
            log.warn("HyPE question generation failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseQuestions(String response) {
        if (response == null || response.isBlank()) return List.of();
        try {
            String jsonStr = response;
            int start = response.indexOf("[");
            int end = response.lastIndexOf("]");
            if (start >= 0 && end > start) {
                jsonStr = response.substring(start, end + 1);
            }
            JSONArray arr = JSONArray.parseArray(jsonStr);
            List<String> questions = new ArrayList<>();
            for (int i = 0; i < arr.size() && i < config.getQuestionCount(); i++) {
                String q = arr.getString(i);
                if (q != null && !q.isBlank()) {
                    questions.add(q.trim());
                }
            }
            return questions;
        } catch (Exception e) {
            log.warn("Failed to parse HyPE questions: {}", response, e);
            return List.of();
        }
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "hermes.rag.hype")
    public static class HyPEConfig {
        private boolean enabled = false;
        private String platform = "DeepSeek";
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey;
        private String modelName = "deepseek-chat";
        private int questionCount = 3;
        private int maxChunkChars = 2000;
    }
}
