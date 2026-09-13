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
            {"label":"CORRECT|INCORRECT|AMBIGUOUS","confidence":0.0,"reason":"short reason","rewrittenQuery":"optional rewrite","clarificationOptions":["opt1","opt2"]}
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
        if (!shouldEvaluate(query)) {
            return correct("CRAG gated (skip LLM)", query);
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

    /**
     * H3: sample/never gate skips most CRAG LLM calls.
     * Deterministic per query hash so A/B arms are reproducible.
     */
    boolean shouldEvaluate(String query) {
        String mode = config.getGateMode() == null ? "sample" : config.getGateMode().trim().toLowerCase();
        if ("always".equals(mode)) {
            return true;
        }
        if ("never".equals(mode)) {
            return false;
        }
        double rate = config.getSampleRate();
        if (rate <= 0.0D) {
            return false;
        }
        if (rate >= 1.0D) {
            return true;
        }
        if (StrUtil.isBlank(query)) {
            return false;
        }
        int h = mix(query.hashCode());
        double bucket = (h & 0x7fffffff) / (double) Integer.MAX_VALUE;
        return bucket < rate;
    }

    /** Avalanche mix so near-identical sequential queries do not share one bucket. */
    private static int mix(int h) {
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return h;
    }

    public CragRetrievalEvaluation parse(String responseText, String fallbackQuery) {
        try {
            String jsonText = extractJsonObject(stripMarkdownFence(responseText));
            JSONObject json = JSONObject.parseObject(jsonText);
            String labelText = StrUtil.blankToDefault(json.getString("label"), "AMBIGUOUS");
            CragRetrievalEvaluation.Label label =
                    CragRetrievalEvaluation.Label.valueOf(labelText.trim().toUpperCase());
            List<String> options = null;
            if (json.containsKey("clarificationOptions") && json.getJSONArray("clarificationOptions") != null) {
                options = json.getJSONArray("clarificationOptions").toJavaList(String.class);
            }
            if ((options == null || options.isEmpty()) && label == CragRetrievalEvaluation.Label.AMBIGUOUS) {
                options = List.of("请提供更详细的问题背景", "请明确具体的业务或技术模块");
            }
            return CragRetrievalEvaluation.builder()
                    .label(label)
                    .confidence(json.getDoubleValue("confidence"))
                    .reason(json.getString("reason"))
                    .rewrittenQuery(StrUtil.blankToDefault(json.getString("rewrittenQuery"), fallbackQuery))
                    .clarificationOptions(options != null ? options : List.of())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse CRAG evaluation response: {}", responseText);
            return CragRetrievalEvaluation.builder()
                    .label(CragRetrievalEvaluation.Label.AMBIGUOUS)
                    .confidence(0.0D)
                    .reason("Evaluator JSON parse failed")
                    .rewrittenQuery(fallbackQuery)
                    .clarificationOptions(List.of("请提供更详细的问题背景", "请明确具体的业务或技术模块"))
                    .build();
        }
    }

    private CragRetrievalEvaluation correct(String reason, String query) {
        return CragRetrievalEvaluation.builder()
                .label(CragRetrievalEvaluation.Label.CORRECT)
                .confidence(1.0D)
                .reason(reason)
                .rewrittenQuery(query)
                .clarificationOptions(List.of())
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
        /** always | sample | never — H3 default sample */
        private String gateMode = "sample";
        /** Fraction of queries that invoke the LLM evaluator when gateMode=sample */
        private double sampleRate = 0.10D;
        private String platform = "DeepSeek";
        private String baseUrl;
        private String apiKey;
        private String model = "deepseek-chat";
        private Double temperature = 0.0D;
    }
}
