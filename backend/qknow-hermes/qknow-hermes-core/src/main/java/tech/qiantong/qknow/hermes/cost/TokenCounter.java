package tech.qiantong.qknow.hermes.cost;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 计数器，按模型聚合统计。
 */
public class TokenCounter {

    private final Map<String, TokenUsage> perModelUsage = new ConcurrentHashMap<>();

    public TokenCounter() {
    }

    /**
     * 记录一次 ChatResponse 的 token 使用量。
     */
    public void recordUsage(String modelName, ChatResponse response) {
        Usage usage = response.getMetadata().getUsage();
        long prompt = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : 0L;
        long completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0L;

        perModelUsage.merge(modelName, new TokenUsage(prompt, completion), (existing, added) -> {
            return new TokenUsage(
                    existing.getPromptTokens() + added.getPromptTokens(),
                    existing.getCompletionTokens() + added.getCompletionTokens());
        });
    }

    /**
     * 聚合所有模型的 token 使用总量。
     */
    public TokenUsage getTotal() {
        long totalPrompt = 0;
        long totalCompletion = 0;
        for (TokenUsage usage : perModelUsage.values()) {
            totalPrompt += usage.getPromptTokens();
            totalCompletion += usage.getCompletionTokens();
        }
        return new TokenUsage(totalPrompt, totalCompletion);
    }

    /**
     * 返回指定模型的 token 使用量，不存在则返回零值。
     */
    public TokenUsage getPerModel(String modelName) {
        TokenUsage usage = perModelUsage.get(modelName);
        return usage != null ? usage : new TokenUsage(0, 0);
    }
}
