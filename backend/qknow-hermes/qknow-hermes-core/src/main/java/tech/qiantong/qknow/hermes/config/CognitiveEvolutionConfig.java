package tech.qiantong.qknow.hermes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qknow.hermes.cognitive-evolution")
public class CognitiveEvolutionConfig {
    private boolean enabled = false;
    private int maxRepairAttempts = 2;
}
