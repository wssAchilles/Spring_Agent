package tech.qiantong.qknow.hermes.judge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component("hermesHermesToolPermissionEnforcer")
@ConfigurationProperties(prefix = "hermes.judge")
public class JudgeConfig {

    private String platform = "deepseek";

    private String baseUrl;

    private String apiKey;

    private String modelName = "deepseek-chat";

    private double threshold = 0.7;
}
