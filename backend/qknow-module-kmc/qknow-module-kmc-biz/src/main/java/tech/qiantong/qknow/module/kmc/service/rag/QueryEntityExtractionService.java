package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.service.IChatModelService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Component
public class QueryEntityExtractionService {

    private static final String SYSTEM_PROMPT = """
            Extract core named entities from a retrieval query.
            Return only one raw JSON object. Do not return Markdown or explanations.
            Schema: {"entities":["entity"]}
            """;

    private final IChatModelService chatModelService;
    private final QueryEntityConfig config;

    public QueryEntityExtractionService(IChatModelService chatModelService, QueryEntityConfig config) {
        this.chatModelService = chatModelService;
        this.config = config;
    }

    public List<String> extract(String query, List<String> fallbackKeywords) {
        if (!config.isEnabled() || StrUtil.isBlank(query)) {
            return fallback(fallbackKeywords);
        }
        try {
            ChatModel chatModel = chatModelService.getChatModel(
                    config.getPlatform(), config.getBaseUrl(), config.getApiKey(),
                    config.getModel(), config.getTemperature());
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SYSTEM_PROMPT));
            messages.add(new UserMessage("Query:\n" + query));
            ChatResponse response = chatModel.call(new Prompt(messages));
            List<String> entities = parse(response.getResult().getOutput().getText());
            return entities.isEmpty() ? fallback(fallbackKeywords) : entities;
        } catch (Exception e) {
            log.warn("Query entity extraction failed, falling back to keywords: {}", e.getMessage());
            return fallback(fallbackKeywords);
        }
    }

    List<String> parse(String responseText) {
        String text = stripMarkdownFence(responseText);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        JSONObject json = JSONObject.parseObject(text.substring(start, end + 1));
        JSONArray array = json.getJSONArray("entities");
        if (array == null) {
            return List.of();
        }
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        for (Object item : array) {
            String entity = StrUtil.blankToDefault(String.valueOf(item), "").trim();
            if (entity.length() >= 2) {
                entities.add(entity);
            }
            if (entities.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(entities);
    }

    private List<String> fallback(List<String> fallbackKeywords) {
        return fallbackKeywords == null ? List.of() : fallbackKeywords.stream()
                .filter(StrUtil::isNotBlank)
                .limit(8)
                .toList();
    }

    private String stripMarkdownFence(String text) {
        String stripped = StrUtil.blankToDefault(text, "").trim();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "");
        }
        return stripped.trim();
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "qknow.rag.query-entity")
    public static class QueryEntityConfig {
        private boolean enabled = true;
        private String platform = "DeepSeek";
        private String baseUrl;
        private String apiKey;
        private String model = "deepseek-chat";
        private Double temperature = 0.0D;
    }
}
