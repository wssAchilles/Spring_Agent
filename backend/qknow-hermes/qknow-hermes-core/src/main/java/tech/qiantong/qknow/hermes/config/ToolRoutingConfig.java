package tech.qiantong.qknow.hermes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hermes.routing")
public class ToolRoutingConfig {

    private ToolCallingModel toolCallingModel = new ToolCallingModel();

    @Data
    public static class ToolCallingModel {
        /** 是否启用工具调用模型路由 */
        private boolean enabled = true;
        /** 平台名称（openai / deepseek / dashscope） */
        private String platform = "openai";
        /** API 端点 */
        private String baseUrl = "https://api.openai.com";
        /** API Key */
        private String apiKey = "";
        /** 模型名称 */
        private String modelName = "gpt-4o";
    }
}
