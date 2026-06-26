package tech.qiantong.qknow.hermes.config;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * ChatModel 工厂
 * <p>
 * 根据平台名称和连接参数创建对应的 ChatModel 实例。
 * 从 {@code ChatModelServiceImpl} 迁移而来，去除了对数据库 IAiModelApiService 的依赖，
 * 改为接收 protobuf 消息传入的参数直接构建 ChatModel。
 * </p>
 *
 * @author fabian
 */
@Component
public class ChatModelFactory {

    @jakarta.annotation.Resource
    private org.springframework.core.env.Environment environment;

    /**
     * 解析环境变量中的 apiKey（如果数据库配置了占位符）
     */
    private String resolveApiKey(String apiKey, String platform) {
        if (apiKey != null && apiKey.contains("placeholder")) {
            // 优先检查 HERMES_平台_API_KEY，再检查 平台_API_KEY
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

    /**
     * 获取 chatModel
     *
     * @param platform  平台名称（对应 AiPlatformEnum.platform）
     * @param baseUrl   baseUrl（Ollama/OpenAI 等平台必需）
     * @param apiKey    apiKey（OpenAI/DashScope/DeepSeek 等平台必需）
     * @param modelName 模型名称
     * @return chatModel
     */
    public ChatModel getChatModel(String platform, String baseUrl, String apiKey, String modelName) {
        return getChatModel(platform, baseUrl, apiKey, modelName, null);
    }

    public ChatModel getChatModel(String platform, String baseUrl, String apiKey, String modelName, Double temperature) {
        apiKey = resolveApiKey(apiKey, platform);
        return switch (AiPlatformEnum.validatePlatform(platform)) {
            case OPENAI -> getOpenAiChatModel(baseUrl, apiKey, modelName);
            case TONG_YI -> getDashScopeChatModel(apiKey, modelName);
            case OLLAMA -> getOllamaChatModel(baseUrl, modelName);
            case DEEP_SEEK -> getDeepSeekChatModel(apiKey, modelName, temperature);
            default -> throw new IllegalArgumentException("暂时不支持该平台: " + platform);
        };
    }

    /**
     * 获取 OpenAi 聊天模型
     *
     * @param baseUrl   baseUrl（必需）
     * @param apiKey    apiKey（必需）
     * @param modelName modelName（必需）
     * @return OpenAiChatModel
     */
    private OpenAiChatModel getOpenAiChatModel(String baseUrl, String apiKey, String modelName) {
        System.out.println("============= OPENAI MODEL NAME IS: " + modelName + " =============");
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new IllegalArgumentException("OpenAI 平台 apiKey, modelName 字段不能为空");
        }
        if (StrUtil.isBlank(baseUrl)) {
            baseUrl = "https://api.openai.com";
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build())
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
    }

    /**
     * 获取 阿里百炼 聊天模型
     *
     * @param apiKey    apiKey（必需）
     * @param modelName modelName（必需）
     * @return DashScopeChatModel
     */
    /**
     * 通义千问通过 OpenAI 兼容模式接入
     * DashScope 兼容端点：https://dashscope.aliyuncs.com/compatible-mode/v1
     */
    private OpenAiChatModel getDashScopeChatModel(String apiKey, String modelName) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new IllegalArgumentException("DashScope 平台必要字段不能为空");
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
    }

    /**
     * 获取 ollama 聊天模型
     *
     * @param baseUrl   baseUrl（必需）
     * @param modelName modelName（必需）
     * @return OllamaChatModel
     */
    private OllamaChatModel getOllamaChatModel(String baseUrl, String modelName) {
        if (StrUtil.hasBlank(baseUrl, modelName)) {
            throw new IllegalArgumentException("Ollama 平台必要字段不能为空");
        }
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .defaultOptions(OllamaChatOptions.builder().model(modelName).build())
                .build();
    }

    /**
     * 获取 deepseek 聊天模型
     *
     * @param apiKey    apiKey（必需）
     * @param modelName modelName（必需）
     * @return DeepSeekChatModel
     */
    private DeepSeekChatModel getDeepSeekChatModel(String apiKey, String modelName) {
        return getDeepSeekChatModel(apiKey, modelName, null);
    }

    private DeepSeekChatModel getDeepSeekChatModel(String apiKey, String modelName, Double temperature) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new IllegalArgumentException("DeepSeek 平台必要字段不能为空");
        }
        DeepSeekChatOptions.Builder optionsBuilder = DeepSeekChatOptions.builder().model(modelName);
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        return DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder().apiKey(apiKey).build())
                .defaultOptions(optionsBuilder.build())
                .build();
    }
}
