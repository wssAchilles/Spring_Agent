package tech.qiantong.qknow.hermes.eval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qknow.rag.eval")
public class RagasEvalConfig {
    private boolean enabled = true;
    private double threshold = 0.85;
    private String platform = "DeepSeek";
    private String modelName = "deepseek-chat";
    private String baseUrl;
    private String apiKey;
}
