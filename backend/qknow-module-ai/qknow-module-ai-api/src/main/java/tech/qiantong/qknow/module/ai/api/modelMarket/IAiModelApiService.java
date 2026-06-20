package tech.qiantong.qknow.module.ai.api.modelMarket;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.fastjson2.JSONArray;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;


/**
 * AI 模型Service接口
 *
 * @author qknow
 * @date 2025-12-23
 */
public interface IAiModelApiService {


    /**
     * 获取具体的聊天模型
     *
     * @param keyId     模型平台id
     * @param modelName 模型名称
     * @return chatModel 对象
     */
    ChatModel getChatModel(Long keyId, String modelName);


    /**
     * 获取具体的 embedding 模型
     *
     * @param keyId     模型平台id
     * @param modelName 模型名称
     * @return EmbeddingModel 对象
     */
    EmbeddingModel getEmbeddingModel(Long keyId, String modelName);

    /**
     * 获取模型字典
     *
     * @param type 模型类型
     */
    JSONArray getModelDict(Integer type);

    /**
     * 获取重排序模型，暂时支持通义平台的
     *
     * @param keyId     模型平台id
     * @param modelName 模型名称
     * @return 重排序模型
     */
    DashScopeRerankModel getRerankModel(Long keyId, String modelName);
}
