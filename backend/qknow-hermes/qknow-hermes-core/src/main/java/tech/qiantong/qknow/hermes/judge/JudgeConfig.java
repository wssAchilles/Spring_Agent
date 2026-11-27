package tech.qiantong.qknow.hermes.judge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component("hermesJudgeConfig")
@ConfigurationProperties(prefix = "hermes.judge")
public class JudgeConfig {

    /**
     * 是否开启 AI Judge 反思评分闭环（对应 hermes.judge.enabled）
     */
    private boolean enabled = false;

    private String platform = "deepseek";

    private String baseUrl;

    private String apiKey;

    private String modelName = "deepseek-flash";

    private double threshold = 0.7;

    /**
     * 兼容 application.yml 中的 min-score 配置并同步映射至 threshold
     */
    private double minScore = 0.7;

    /**
     * 最大反思迭代轮数（对应 hermes.judge.max-reflection-rounds）
     */
    private int maxReflectionRounds = 3;

    public void setMinScore(double minScore) {
        this.minScore = minScore;
        this.threshold = minScore;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
        this.minScore = threshold;
    }
}
