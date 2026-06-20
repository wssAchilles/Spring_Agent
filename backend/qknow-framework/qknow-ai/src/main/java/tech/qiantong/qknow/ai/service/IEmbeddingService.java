package tech.qiantong.qknow.ai.service;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 向量化模型服务
 *
 * @author fabian
 */
public interface IEmbeddingService {

    /**
     * 获取 向量化模型
     *
     * @param platForm  平台名称
     * @param baseUrl   baseUrl
     * @param apiKey    apiKey
     * @param modelName 模型名称
     * @return embeddingModel
     */
    EmbeddingModel getEmbeddingModel(String platForm, String baseUrl, String apiKey, String modelName);
}
