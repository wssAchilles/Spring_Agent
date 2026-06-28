
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
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.service.IEmbeddingService;
import tech.qiantong.qknow.common.exception.ServiceException;

/**
 * 向量化模型服务
 *
 * @author fabian
 */
@Service
public class EmbeddingServiceImpl implements IEmbeddingService {

    /**
     * 获取 向量化模型
     *
     * @param platForm  平台名称
     * @param baseUrl   baseUrl
     * @param apiKey    apiKey
     * @param modelName 模型名称
     * @return embeddingModel
     */
    @Override
    public EmbeddingModel getEmbeddingModel(String platForm, String baseUrl, String apiKey, String modelName) {
        EmbeddingModel embeddingModel;
        switch (platForm) {
            case "OpenAI" -> embeddingModel = this.getOpenAiModel(baseUrl, apiKey, modelName);
            case "TongYi" -> embeddingModel = this.getDashScopeModel(apiKey, modelName);
            case "Ollama" -> embeddingModel = this.getOllamaModel(baseUrl, modelName);
            default -> throw new ServiceException("暂时不支持该平台");

        }
        return embeddingModel;
    }

    /**
     * 获取 OpenAi 向量化模型
     *
     * @param baseUrl   baseUrl（必需）
     * @param apiKey    apiKey（必需）
     * @param modelName modelName（必需）
     * @return OpenAiEmbeddingModel
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
     * DashScope 兼容端点：https://dashscope.aliyuncs.com/compatible-mode/v1
     */
    private OpenAiEmbeddingModel getDashScopeModel(String apiKey, String modelName) {
        if (StrUtil.hasBlank(apiKey, modelName)) {
            throw new ServiceException("必要字段不能为空");
        }
        return new OpenAiEmbeddingModel(
                OpenAiApi.builder()
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .apiKey(apiKey)
                        .build(),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(modelName).build());
    }

    /**
     * 获取 ollama 向量化模型
     *
     * @param baseUrl   baseUrl（必需）
     * @param modelName modelName（必需）
     * @return OllamaEmbeddingModel
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
