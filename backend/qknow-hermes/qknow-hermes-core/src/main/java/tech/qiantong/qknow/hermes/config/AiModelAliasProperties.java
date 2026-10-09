package tech.qiantong.qknow.hermes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 集中化模型逻辑别名与平滑演化配置
 * 作为全系统模型名称与定价策略的单一真理源 (Single Source of Truth)
 *
 * @author Achilles
 * @since Phase 153
 */
@Data
@Component
@ConfigurationProperties(prefix = "hermes.ai.model")
public class AiModelAliasProperties {

    /**
     * 默认 AI 平台名称
     */
    private String defaultPlatform = "DeepSeek";

    /**
     * 主干主力推荐模型（官方当前活跃推荐模型）
     */
    private String primaryModel = "deepseek-flash";

    /**
     * 逻辑能力语义别名映射表（例如: primary -> deepseek-flash, fast -> deepseek-flash）
     */
    private Map<String, String> aliases = new HashMap<>(Map.of(
            "primary", "deepseek-flash",
            "fast", "deepseek-flash",
            "default", "deepseek-flash",
            "reasoning", "deepseek-flash",
            "chat", "deepseek-flash"
    ));

    /**
     * 历史退役/废弃模型向后兼容平滑升级表
     */
    private Map<String, String> legacyFallbacks = new HashMap<>(Map.of(
            "deepseek-chat", "deepseek-flash",
            "deepseek-reasoner", "deepseek-flash",
            "deepseek-coder", "deepseek-flash",
            "deepseek-v3", "deepseek-flash",
            "deepseek-r1", "deepseek-flash"
    ));

    /**
     * 模型计费定价配置（纳元/Token）
     */
    private Map<String, ModelPricingConfig> pricing = new HashMap<>(Map.of(
            "deepseek-flash", new ModelPricingConfig(100L, 500L, 2_000L)
    ));

    @Data
    public static class ModelPricingConfig {
        private long hitPromptNano = 100L;
        private long missPromptNano = 500L;
        private long completionNano = 2_000L;

        public ModelPricingConfig() {}

        public ModelPricingConfig(long hitPromptNano, long missPromptNano, long completionNano) {
            this.hitPromptNano = hitPromptNano;
            this.missPromptNano = missPromptNano;
            this.completionNano = completionNano;
        }
    }
}
