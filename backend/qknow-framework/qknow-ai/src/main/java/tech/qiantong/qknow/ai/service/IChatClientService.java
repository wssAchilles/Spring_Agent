package tech.qiantong.qknow.ai.service;

import org.springframework.ai.chat.client.ChatClient;

/**
 * springAi chatClient 服务
 *
 * @author fabian
 */
public interface IChatClientService {

    /**
     * 获取 chatClient 对象
     *
     * @param platForm  平台名称
     * @param baseUrl   baseUrl
     * @param apiKey    apiKey
     * @param modelName 模型名称
     * @return chatClient
     */
    ChatClient getChatClient(String platForm, String baseUrl, String apiKey, String modelName);
}
