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
            你是一个纠错型RAG（Corrective RAG）流水线中的检索结果评估员。
            你的任务是判断给定的检索上下文（Context）是否能够回答用户的问题（Question）。
            注意：请智能识别同义词、缩写和中英文日期/时间指代（例如：“第七天”与“Day 07”或“Day07”是完全等价的，“第一天”与“Day01”等价等）。只要上下文中包含该对应时间点或相关意图的内容，就必须判定为 CORRECT。
            
            只能返回一个原生的JSON对象。不要返回Markdown语法（不要使用```json），也不要在JSON之外写任何解释。
            返回格式必须严格为：
            {"label":"CORRECT|INCORRECT|AMBIGUOUS","confidence":0.0,"reason":"简短的原因","rewrittenQuery":"可选的查询改写"}
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
