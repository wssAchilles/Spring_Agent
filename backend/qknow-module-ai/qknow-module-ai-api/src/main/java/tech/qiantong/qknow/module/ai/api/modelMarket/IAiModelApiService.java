package tech.qiantong.qknow.module.ai.api.modelMarket;

import com.alibaba.fastjson2.JSONArray;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;


/**
 * AI 模型Service接口
 *
 * @author qknow
 * @date 2025-12-23
 */
public interface IAiModelApiService {

    ChatModel getChatModel(Long keyId, String modelName);

    EmbeddingModel getEmbeddingModel(Long keyId, String modelName);

    JSONArray getModelDict(Integer type);

    /**
     * 获取指定平台的 API Key
     */
    String getApiKey(String platform);

    /**
     * 根据 Key ID 获取 API Key
     */
    String getApiKeyById(Long keyId);

    tech.qiantong.qknow.module.ai.api.dto.AiModelRespDTO getAiModel(Long id);

    tech.qiantong.qknow.module.ai.api.dto.AiApiKeyRespDTO getAiApiKey(Long id);
}
