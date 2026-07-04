package tech.qiantong.qknow.hermes.config;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatModel 工厂（带实例缓存）
 * [溯源] 算法优化指南 §5.4: "ChatModelFactory 实例缓存 — P1"
 *
 * 根据平台名称和连接参数创建对应的 ChatModel 实例。
 * 使用 ConcurrentHashMap 缓存已创建的实例，避免重复创建重量级对象。
 */
@Component
public class ChatModelFactory {

    @jakarta.annotation.Resource
    private org.springframework.core.env.Environment environment;

    private final ConcurrentHashMap<String, ChatModel> cache = new ConcurrentHashMap<>();

    private String resolveApiKey(String apiKey, String platform) {
        if (apiKey != null && apiKey.contains("placeholder")) {
            String upperPlatform = platform.toUpperCase().replace("-", "_");
            String envKey1 = "HERMES_" + upperPlatform + "_API_KEY";
            String envKey2 = upperPlatform + "_API_KEY";
            String envValue = environment.getProperty(envKey1);
            if (StrUtil.isBlank(envValue)) {
                envValue = environment.getProperty(envKey2);
            }
            if (StrUtil.isNotBlank(envValue)) {
                return envValue;
            }
        }
        return apiKey;
    }

    public ChatModel getChatModel(String platform, String baseUrl, String apiKey, String modelName) {
        return getChatModel(platform, baseUrl, apiKey, modelName, null);
    }

    public ChatModel getChatModel(String platform, String baseUrl, String apiKey, String modelName, Double temperature) {
        String resolvedApiKey = resolveApiKey(apiKey, platform);
        String cacheKey = buildCacheKey(platform, baseUrl, resolvedApiKey, modelName, temperature);
        return cache.computeIfAbsent(cacheKey, k ->
                switch (AiPlatformEnum.validatePlatform(platform)) {
                    case OPENAI -> getOpenAiChatModel(baseUrl, resolvedApiKey, modelName, temperature);
                    case TONG_YI -> getDashScopeChatModel(resolvedApiKey, modelName);
                    case OLLAMA -> getOllamaChatModel(baseUrl, modelName);
                    case DEEP_SEEK -> getDeepSeekCompatibleChatModel(baseUrl, resolvedApiKey, modelName, temperature);
                    default -> throw new IllegalArgumentException("暂时不支持该平台: " + platform);
                }
        );
    }

    private String buildCacheKey(String platform, String baseUrl, String apiKey, String modelName, Double temperature) {
        return platform + "|" + (baseUrl != null ? baseUrl : "") + "|"
                + (apiKey != null ? apiKey.hashCode() : "") + "|"
                + (modelName != null ? modelName : "") + "|"
                + (temperature != null ? temperature : "");
    }

    private OpenAiChatModel getOpenAiChatModel(String baseUrl, String apiKey, String modelName, Double temperature) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new IllegalArgumentException("OpenAI 平台 apiKey, modelName 字段不能为空");
        }
        if (StrUtil.isBlank(baseUrl)) {
            baseUrl = "https://api.openai.com";
        }
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(modelName);
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build())
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    private OpenAiChatModel getDashScopeChatModel(String apiKey, String modelName) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new IllegalArgumentException("DashScope 平台必要字段不能为空");
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
    }

    private OllamaChatModel getOllamaChatModel(String baseUrl, String modelName) {
        if (StrUtil.hasBlank(baseUrl, modelName)) {
            throw new IllegalArgumentException("Ollama 平台必要字段不能为空");
        }
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .defaultOptions(OllamaChatOptions.builder().model(modelName).build())
                .build();
    }

    private DeepSeekCompatibleChatModel getDeepSeekCompatibleChatModel(String baseUrl, String apiKey, String modelName, Double temperature) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new IllegalArgumentException("DeepSeek 平台必要字段不能为空");
        }
        return new DeepSeekCompatibleChatModel(baseUrl, apiKey, modelName, temperature);
    }
}
