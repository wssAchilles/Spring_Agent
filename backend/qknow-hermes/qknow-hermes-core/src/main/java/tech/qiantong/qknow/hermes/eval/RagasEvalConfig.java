package tech.qiantong.qknow.hermes.eval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private String promptVersion = "manual-v1";
    private List<String> promptExamples = new ArrayList<>();
    private Map<String, String> metricPrompts = new LinkedHashMap<>();
    private String claimExtractionPrompt;
    private String entailmentSystemPrompt;
    private String entailmentUserPrompt;
}
