package tech.qiantong.qknow.mcp.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.qiantong.qknow.mcp.client.McpClientManager;
import tech.qiantong.qknow.mcp.client.routing.SemanticToolRegistry;

/**
 * 原生企业级 MCP Client 自动装配配置类
 */
@Configuration
public class McpClientAutoConfiguration {

    @Bean
    public McpClientManager mcpClientManager() {
        return new McpClientManager();
    }

    @Bean
    public SemanticToolRegistry semanticToolRegistry() {
        return new SemanticToolRegistry();
    }
}
