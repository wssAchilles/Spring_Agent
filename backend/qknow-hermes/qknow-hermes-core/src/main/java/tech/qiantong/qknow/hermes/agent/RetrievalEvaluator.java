package tech.qiantong.qknow.hermes.agent;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.ModelCredentials;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RetrievalEvaluator {

    private static final String SYSTEM_PROMPT = """
            You are a retrieval evaluator in a corrective RAG pipeline.
            Classify whether the retrieved context can answer the user question.
            Return only one raw JSON object. Do not return Markdown, code fences, or explanations outside JSON.
            Schema:
            {"label":"CORRECT|INCORRECT|AMBIGUOUS","confidence":0.0,"reason":"short reason","rewrittenQuery":"optional rewrite"}
            """;

    private final ChatModelFactory chatModelFactory;

    public RetrievalEvaluator(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    public RetrievalEvaluation evaluate(String question, List<RAGContext> ragContexts,
                                        ModelConfig modelConfig, ModelCredentials credentials) {
        if (ragContexts == null || ragContexts.isEmpty() || ragContexts.stream()
                .allMatch(ctx -> StrUtil.isBlank(ctx.getPreRetrievedContent()))) {
            return RetrievalEvaluation.builder()
                    .label(RetrievalEvaluation.Label.INCORRECT)
                    .confidence(1.0D)
                    .reason("No retrieved context")
                    .rewrittenQuery(question)
                    .build();
        }

        try {
            ChatModel chatModel = createEvaluationModel(modelConfig, credentials);
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SYSTEM_PROMPT));
            messages.add(new UserMessage(buildPrompt(question, ragContexts)));
            ChatResponse response = chatModel.call(new Prompt(messages));
            return parse(response.getResult().getOutput().getText(), question);
        } catch (Exception e) {
            log.warn("Retrieval evaluation failed, treating result as ambiguous", e);
            return RetrievalEvaluation.builder()
                    .label(RetrievalEvaluation.Label.AMBIGUOUS)
                    .confidence(0.0D)
                    .reason("Evaluator failed: " + e.getMessage())
                    .build();
        }
    }

    public RetrievalEvaluation parse(String responseText, String fallbackQuery) {
        try {
            String jsonText = stripMarkdownFence(responseText);
            int start = jsonText.indexOf('{');
            int end = jsonText.lastIndexOf('}');
            if (start >= 0 && end > start) {
                jsonText = jsonText.substring(start, end + 1);
            }
            JSONObject json = JSONObject.parseObject(jsonText);
            String labelText = StrUtil.blankToDefault(json.getString("label"), "AMBIGUOUS");
            RetrievalEvaluation.Label label = RetrievalEvaluation.Label.valueOf(labelText.trim().toUpperCase());
            return RetrievalEvaluation.builder()
                    .label(label)
                    .confidence(json.getDoubleValue("confidence"))
                    .reason(json.getString("reason"))
                    .rewrittenQuery(StrUtil.blankToDefault(json.getString("rewrittenQuery"), fallbackQuery))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse retrieval evaluation response: {}", responseText);
            return RetrievalEvaluation.builder()
                    .label(RetrievalEvaluation.Label.AMBIGUOUS)
                    .confidence(0.0D)
                    .reason("Evaluator JSON parse failed")
                    .rewrittenQuery(fallbackQuery)
                    .build();
        }
    }

    private ChatModel createEvaluationModel(ModelConfig modelConfig, ModelCredentials credentials) {
        String apiKey = null;
        if (modelConfig != null && StrUtil.isNotBlank(modelConfig.getApiKey())) {
            apiKey = modelConfig.getApiKey();
        } else if (credentials != null && StrUtil.isNotBlank(credentials.getApiKey())) {
            apiKey = credentials.getApiKey();
        }
        return chatModelFactory.getChatModel("DeepSeek", null, apiKey, "deepseek-chat", 0.0D);
    }

    private String buildPrompt(String question, List<RAGContext> ragContexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question:\n").append(question).append("\n\nRetrieved context:\n");
        for (RAGContext context : ragContexts) {
            if (StrUtil.isBlank(context.getPreRetrievedContent())) {
                continue;
            }
            sb.append("Knowledge: ").append(context.getKnowledgeName())
                    .append(" (").append(context.getKnowledgeId()).append(")\n")
                    .append(context.getPreRetrievedContent()).append("\n\n");
        }
        return sb.toString();
    }

    private String stripMarkdownFence(String text) {
        String stripped = StrUtil.blankToDefault(text, "").trim();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "");
        }
        return stripped.trim();
    }
}
