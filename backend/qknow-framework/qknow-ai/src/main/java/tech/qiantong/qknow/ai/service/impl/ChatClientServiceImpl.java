
package tech.qiantong.qknow.ai.service.impl;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.service.IChatClientService;
import tech.qiantong.qknow.ai.service.IChatModelService;

/**
 * @author fabian
 */
@Service
public class ChatClientServiceImpl implements IChatClientService {

    @Resource
    IChatModelService chatModelService;

    /**
     * 获取 chatClient 对象
     *
     * @param platForm  平台名称
     * @param baseUrl   baseUrl
     * @param apiKey    apiKey
     * @param modelName 模型名称
     * @return chatClient
     */
    @Override
    public ChatClient getChatClient(String platForm, String baseUrl, String apiKey, String modelName) {
        ChatModel chatModel = chatModelService.getChatModel(platForm, baseUrl, apiKey, modelName);
        return ChatClient.builder(chatModel).build();
    }
}
