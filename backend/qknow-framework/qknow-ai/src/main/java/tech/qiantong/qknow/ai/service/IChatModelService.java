package tech.qiantong.qknow.ai.service;

import org.springframework.ai.chat.model.ChatModel;

/**
 * springAi chatModel 服务
 *
 * @author fabian
 */
public interface IChatModelService {
    /**
     * 获取 chatModel
     *
     * @param platForm  平台名称
     * @param baseUrl   baseUrl
     * @param apiKey    apiKey
     * @param modelName 模型名称
     * @return chatModel
     */
    ChatModel getChatModel(String platForm, String baseUrl, String apiKey, String modelName);

    default ChatModel getChatModel(String platForm, String baseUrl, String apiKey, String modelName, Double temperature) {
        return getChatModel(platForm, baseUrl, apiKey, modelName);
    }
}
