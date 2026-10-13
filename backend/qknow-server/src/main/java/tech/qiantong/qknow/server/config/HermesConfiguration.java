package tech.qiantong.qknow.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.qiantong.qknow.hermes.config.AiModelAliasProperties;
import tech.qiantong.qknow.hermes.config.ModelNameResolver;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;

/**
 * Hermes 桥接配置类
 * 提供主容器与 Hermes 核心组件之间的 Bean 供给与桥接
 */
@Configuration
public class HermesConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AiModelAliasProperties aiModelAliasProperties() {
        return new AiModelAliasProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelNameResolver modelNameResolver(AiModelAliasProperties properties) {
        return new ModelNameResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolAdapter mcpToolAdapter() {
        return new McpToolAdapter();
    }
}
