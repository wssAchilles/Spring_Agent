package tech.qiantong.qknow.hermes.config;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.Resource;
import java.net.http.HttpClient;

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

    @Resource
    ObjectProvider<WebClient.Builder> webClientBuilderProvider;

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
        return switch (AiPlatformEnum.validatePlatform(platform)) {
            case OPENAI -> getOpenAiChatModel(baseUrl, apiKey, modelName);
            case TONG_YI -> getDashScopeChatModel(apiKey, modelName);
            case OLLAMA -> getOllamaChatModel(baseUrl, modelName);
            case DEEP_SEEK -> getDeepSeekChatModel(apiKey, modelName);
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
        if (StrUtil.hasBlank(baseUrl, apiKey, modelName)) {
            throw new IllegalArgumentException("OpenAI 平台必要字段不能为空");
        }
        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        webClientBuilder.clientConnector(
                new JdkClientHttpConnector(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                )
        );
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).webClientBuilder(webClientBuilder).build())
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
    private DashScopeChatModel getDashScopeChatModel(String apiKey, String modelName) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new IllegalArgumentException("DashScope 平台必要字段不能为空");
        }
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(apiKey).build())
                .defaultOptions(DashScopeChatOptions.builder().model(modelName).build())
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
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new IllegalArgumentException("DeepSeek 平台必要字段不能为空");
        }
        return DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder().apiKey(apiKey).build())
                .defaultOptions(DeepSeekChatOptions.builder().model(modelName).build())
                .build();
    }
}
