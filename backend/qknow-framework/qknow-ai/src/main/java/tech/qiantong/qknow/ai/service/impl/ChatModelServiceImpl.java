
package tech.qiantong.qknow.ai.service.impl;

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
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tech.qiantong.qknow.ai.enums.model.AiPlatformEnum;
import tech.qiantong.qknow.ai.service.IChatModelService;
import tech.qiantong.qknow.common.exception.ServiceException;

import java.net.http.HttpClient;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatModelServiceImpl implements IChatModelService {

    private final ConcurrentHashMap<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    @Override
    public ChatModel getChatModel(String platForm, String baseUrl, String apiKey, String modelName) {
        String cacheKey = platForm + "|" + (baseUrl != null ? baseUrl : "") + "|" + (modelName != null ? modelName : "");
        return chatModelCache.computeIfAbsent(cacheKey, k -> createChatModel(platForm, baseUrl, apiKey, modelName));
    }

    private ChatModel createChatModel(String platForm, String baseUrl, String apiKey, String modelName) {
        switch (AiPlatformEnum.validatePlatform(platForm)) {
            case OPENAI -> { return this.getOpenAiChatModel(baseUrl, apiKey, modelName); }
            case TONG_YI -> { return this.getDashScopeChatModel(apiKey, modelName); }
            case OLLAMA -> { return this.getOllamaChatModel(baseUrl, modelName); }
            case DEEP_SEEK -> { return this.getDeepSeekChatModel(apiKey, modelName); }
            default -> throw new ServiceException("暂时不支持该平台");
        }
    }

    private OpenAiChatModel getOpenAiChatModel(String baseUrl, String apiKey, String modelName) {
        if (StrUtil.hasBlank(baseUrl, apiKey, modelName)) {
            throw new ServiceException("必要字段不能为空");
        }
        WebClient.Builder webClientBuilder = WebClient.builder();
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).webClientBuilder(webClientBuilder).build())
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
    }

    private DashScopeChatModel getDashScopeChatModel(String apiKey, String modelName) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new ServiceException("必要字段不能为空");
        }
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(apiKey).build())
                .defaultOptions(DashScopeChatOptions.builder().model(modelName).build())
                .build();
    }

    private OllamaChatModel getOllamaChatModel(String baseUrl, String modelName) {
        if (StrUtil.hasBlank(baseUrl, modelName)) {
            throw new ServiceException("必要字段不能为空");
        }
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .defaultOptions(OllamaChatOptions.builder().model(modelName).build())
                .build();
    }

    private DeepSeekChatModel getDeepSeekChatModel(String apiKey, String modelName) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new ServiceException("必要字段不能为空");
        }
        return DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder().apiKey(apiKey).build())
                .defaultOptions(DeepSeekChatOptions.builder().model(modelName).build())
                .build();
    }
}
