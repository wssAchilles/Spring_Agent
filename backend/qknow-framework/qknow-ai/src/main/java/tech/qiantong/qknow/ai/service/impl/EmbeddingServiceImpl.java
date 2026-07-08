
package tech.qiantong.qknow.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tech.qiantong.qknow.ai.service.IEmbeddingService;
import tech.qiantong.qknow.common.exception.ServiceException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量化模型服务
 * [溯源] 算法优化指南 §5.2: "EmbeddingServiceImpl 实例缓存 — P1"
 *
 * 使用 ConcurrentHashMap 缓存已创建的 EmbeddingModel 实例，
 * 避免每次调用都重新创建重量级对象。
 *
 * @author fabian
 */
@Service
public class EmbeddingServiceImpl implements IEmbeddingService {

    private final ConcurrentHashMap<String, EmbeddingModel> cache = new ConcurrentHashMap<>();

    /**
     * 获取 向量化模型（带缓存）
     *
     * @param platForm  平台名称
     * @param baseUrl   baseUrl
     * @param apiKey    apiKey
     * @param modelName 模型名称
     * @return embeddingModel
     */
    @Override
    public EmbeddingModel getEmbeddingModel(String platForm, String baseUrl, String apiKey, String modelName) {
        String cacheKey = buildCacheKey(platForm, baseUrl, apiKey, modelName);
        return cache.computeIfAbsent(cacheKey, k -> createModel(platForm, baseUrl, apiKey, modelName));
    }

    private EmbeddingModel createModel(String platForm, String baseUrl, String apiKey, String modelName) {
        return switch (platForm) {
            case "OpenAI" -> this.getOpenAiModel(baseUrl, apiKey, modelName);
            case "TongYi" -> this.getDashScopeModel(baseUrl, apiKey, modelName);
            case "Ollama" -> this.getOllamaModel(baseUrl, modelName);
            default -> this.getOpenAiModel(baseUrl, apiKey, modelName);
        };
    }

    private String buildCacheKey(String platForm, String baseUrl, String apiKey, String modelName) {
        return platForm + "|" + (baseUrl != null ? baseUrl : "") + "|"
                + (apiKey != null ? apiKey.hashCode() : "") + "|"
                + (modelName != null ? modelName : "");
    }

    /**
     * 获取 OpenAi 向量化模型
     */
    private OpenAiEmbeddingModel getOpenAiModel(String baseUrl, String apiKey, String modelName) {
        if (StrUtil.hasBlank(baseUrl, apiKey, modelName)) {
            throw new ServiceException("必要字段不能为空");
        }
        return new OpenAiEmbeddingModel(
                OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build(),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(modelName).build());
    }

    /**
     * 通义千问 Embedding 通过 OpenAI 兼容模式接入
     */
    private OpenAiEmbeddingModel getDashScopeModel(String baseUrl, String apiKey, String modelName) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new ServiceException("必要字段不能为空");
        }
        String dashScopeBaseUrl = StrUtil.blankToDefault(baseUrl, "https://dashscope.aliyuncs.com/compatible-mode");
        if (dashScopeBaseUrl.endsWith("/v1")) {
            dashScopeBaseUrl = dashScopeBaseUrl.substring(0, dashScopeBaseUrl.length() - 3);
        }
        return new OpenAiEmbeddingModel(
                OpenAiApi.builder()
                        .baseUrl(dashScopeBaseUrl)
                        .apiKey(apiKey)
                        .restClientBuilder(timeoutRestClientBuilder())
                        .build(),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(modelName).build());
    }

    private RestClient.Builder timeoutRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        return RestClient.builder().requestFactory(requestFactory);
    }

    /**
     * 获取 ollama 向量化模型
     */
    private OllamaEmbeddingModel getOllamaModel(String baseUrl, String modelName) {
        if (StrUtil.hasBlank(baseUrl, modelName)) {
            throw new ServiceException("必要字段不能为空");
        }
        return OllamaEmbeddingModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .defaultOptions(OllamaEmbeddingOptions.builder().model(modelName).build())
                .build();
    }
}
