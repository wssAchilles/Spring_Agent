package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qknow.rag.dynamic-top-k")
public class DynamicTopKConfig {

    private boolean enabled = true;
    private int defaultTopK = 10;
    private int minTopK = 3;
    private int maxTopK = 80;
    private int complexMinTopK = 12;
    private double mediumMultiplier = 1.0D;
    private double complexMultiplier = 1.8D;
    private double temporalMultiplier = 1.3D;
    private double keywordMultiplierStep = 0.08D;
    private double maxKeywordBonus = 0.5D;
}
