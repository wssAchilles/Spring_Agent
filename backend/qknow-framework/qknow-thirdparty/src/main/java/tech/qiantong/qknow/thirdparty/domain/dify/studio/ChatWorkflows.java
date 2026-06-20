package tech.qiantong.qknow.thirdparty.domain.dify.studio;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

/**
 * 工作流
 * @program: qknow
 * @author wang
 * @date 2025/02/19 16:11
 **/
@Data
public class ChatWorkflows {

    /** 用户 */
    private String user;

    /** url */
    private String url;

    /** apikey */
    private String apiKey;

    /** 自定义参数 */
    private JSONObject jsonObject;

}
