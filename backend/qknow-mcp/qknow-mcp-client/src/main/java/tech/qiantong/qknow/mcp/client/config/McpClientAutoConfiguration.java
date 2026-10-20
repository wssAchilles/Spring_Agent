package tech.qiantong.qknow.mcp.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tech.qiantong.qknow.mcp.client.McpClientManager;
import tech.qiantong.qknow.mcp.client.routing.SemanticToolRegistry;

/**
 * 原生企业级 MCP Client 自动装配配置类
 */
@AutoConfiguration
@ConditionalOnClass(McpClientManager.class)
public class McpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpClientManager mcpClientManager() {
        return new McpClientManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public SemanticToolRegistry semanticToolRegistry() {
        return new SemanticToolRegistry();
    }
}
