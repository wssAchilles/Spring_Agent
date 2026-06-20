package tech.qiantong.qknow.thirdparty.service.dify;

import okhttp3.Call;
import tech.qiantong.qknow.thirdparty.api.dify.ChatCallback;
import tech.qiantong.qknow.thirdparty.domain.dify.studio.ChatFile;
import tech.qiantong.qknow.thirdparty.domain.dify.studio.ChatMessage;
import tech.qiantong.qknow.thirdparty.domain.dify.studio.ChatParameter;
import tech.qiantong.qknow.thirdparty.domain.dify.studio.ChatWorkflows;

import java.io.File;
import java.util.List;

/**
 * 灵桐-工作室Service接口
 *
 * @author qknow
 * @date 2025-02-19
 */
public interface IStudioService {

    /**
     * 聊天问答
     * @param chatMessage 请求参数
     * @param callback 流式请求回调
     * @return
     */
    public Call sendChatMessage(ChatMessage chatMessage, ChatCallback callback);

    /**
     * 工作流
     * @param chatWorkflows
     * @param callback
     * @return
     */
    public Call sendChatWorkflow(ChatWorkflows chatWorkflows, ChatCallback callback);

    /**
     * 获取参数
     * @param apiKey
     */
    public List<ChatParameter> getParameters(String url,String apiKey);

    /**
     * 上传文件
     * @param file 文件对象
     * @param userId 用户标识
     * @param apiKey 秘钥
     * @return
     */
    public ChatFile uploadFile(String url,File file, String userId, String apiKey);

    /**
     * 获取建议
     * @param messageId 消息标识
     * @param apiKey 秘钥
     * @return 建议列表
     */
    public List<String> getSuggested(String url,String messageId, String userId, String apiKey);

    /**
     * 停止问答
     * @param taskId 任务标识
     * @param userId 用户标识
     * @param apiKey 秘钥
     * @return
     */
    Boolean stopChatMessage(String url,String taskId, String userId, String apiKey);
}
