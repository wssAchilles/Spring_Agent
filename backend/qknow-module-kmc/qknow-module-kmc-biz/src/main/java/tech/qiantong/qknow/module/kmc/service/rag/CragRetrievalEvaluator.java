package tech.qiantong.qknow.module.kmc.service.rag;

import cn.hutool.core.util.StrUtil;
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
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CragRetrievalEvaluator {

    private static final String SYSTEM_PROMPT = """
            You are a retrieval evaluator in a corrective RAG pipeline.
            Classify whether the retrieved context can answer the user question.
            Return only one raw JSON object. Do not return Markdown, code fences, or explanations outside JSON.
            Schema:
            {"label":"CORRECT|INCORRECT|AMBIGUOUS","confidence":0.0,"reason":"short reason","rewrittenQuery":"optional rewrite"}
            """;

    private final IChatModelService chatModelService;
    private final CragConfig config;

    public CragRetrievalEvaluator(IChatModelService chatModelService, CragConfig config) {
        this.chatModelService = chatModelService;
        this.config = config;
    }

    public CragRetrievalEvaluation evaluate(String query, RagResult ragResult) {
        if (!config.isEnabled()) {
            return correct("CRAG disabled", query);
        }
        if (ragResult == null || StrUtil.isBlank(ragResult.getContext())) {
            return CragRetrievalEvaluation.builder()
                    .label(CragRetrievalEvaluation.Label.INCORRECT)
                    .confidence(1.0D)
                    .reason("No retrieved context")
                    .rewrittenQuery(query)
                    .build();
        }

        try {
            ChatModel chatModel = chatModelService.getChatModel(
                    config.getPlatform(), config.getBaseUrl(), config.getApiKey(),
                    config.getModel(), config.getTemperature());
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SYSTEM_PROMPT));
            messages.add(new UserMessage(buildPrompt(query, ragResult.getContext())));
            ChatResponse response = chatModel.call(new Prompt(messages));
            return parse(response.getResult().getOutput().getText(), query);
        } catch (Exception e) {
            log.warn("CRAG retrieval evaluation failed, treating retrieval as ambiguous: {}", e.getMessage());
            return CragRetrievalEvaluation.builder()
                    .label(CragRetrievalEvaluation.Label.AMBIGUOUS)
                    .confidence(0.0D)
                    .reason("Evaluator failed")
                    .rewrittenQuery(query)
                    .build();
        }
    }

    CragRetrievalEvaluation parse(String responseText, String fallbackQuery) {
        try {
            String jsonText = extractJsonObject(stripMarkdownFence(responseText));
            JSONObject json = JSONObject.parseObject(jsonText);
            String labelText = StrUtil.blankToDefault(json.getString("label"), "AMBIGUOUS");
            CragRetrievalEvaluation.Label label =
                    CragRetrievalEvaluation.Label.valueOf(labelText.trim().toUpperCase());
            return CragRetrievalEvaluation.builder()
                    .label(label)
                    .confidence(json.getDoubleValue("confidence"))
                    .reason(json.getString("reason"))
                    .rewrittenQuery(StrUtil.blankToDefault(json.getString("rewrittenQuery"), fallbackQuery))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse CRAG evaluation response: {}", responseText);
            return CragRetrievalEvaluation.builder()
                    .label(CragRetrievalEvaluation.Label.AMBIGUOUS)
                    .confidence(0.0D)
                    .reason("Evaluator JSON parse failed")
                    .rewrittenQuery(fallbackQuery)
                    .build();
        }
    }

    private CragRetrievalEvaluation correct(String reason, String query) {
        return CragRetrievalEvaluation.builder()
                .label(CragRetrievalEvaluation.Label.CORRECT)
                .confidence(1.0D)
                .reason(reason)
                .rewrittenQuery(query)
                .build();
    }

    private String buildPrompt(String query, String context) {
        return "Question:\n" + query + "\n\nRetrieved context:\n" + context;
    }

    private String stripMarkdownFence(String text) {
        String stripped = StrUtil.blankToDefault(text, "").trim();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "");
        }
        return stripped.trim();
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("CRAG evaluation response is not a JSON object");
        }
        return text.substring(start, end + 1);
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "qknow.rag.crag")
    public static class CragConfig {
        private boolean enabled = true;
        private String platform = "DeepSeek";
        private String baseUrl;
        private String apiKey;
        private String model = "deepseek-chat";
        private Double temperature = 0.0D;
    }
}
