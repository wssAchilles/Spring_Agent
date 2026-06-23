package tech.qiantong.qknow.hermes.eval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hermes.eval.ragas")
public class RagasEvalConfig {
    private boolean enabled = true;
    private double threshold = 0.7;
    private String platform = "deepseek";
    private String modelName = "deepseek-chat";
    private String baseUrl;
    private String apiKey;
}
