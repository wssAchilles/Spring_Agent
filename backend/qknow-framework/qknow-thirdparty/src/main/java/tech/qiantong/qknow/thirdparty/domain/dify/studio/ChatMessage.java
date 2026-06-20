package tech.qiantong.qknow.thirdparty.domain.dify.studio;

import lombok.Data;

import java.util.Map;

/**
 * 知识问答
 * @program: qknow
 * @author wang
 * @date 2025/02/19 16:11
 **/
@Data
public class ChatMessage {

    /** 对话id */
    private String conversationId;

    /** 问题 */
    private String query;

    /** 用户 */
    private String user;

    /** url */
    private String url;

    /** apikey */
    private String apiKey;

    /** 其他参数 */
    private Map<String, Object> map;

}
