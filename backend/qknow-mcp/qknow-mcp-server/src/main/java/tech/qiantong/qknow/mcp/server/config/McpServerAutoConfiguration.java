package tech.qiantong.qknow.mcp.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.qiantong.qknow.mcp.server.provider.CodeSandboxMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.DataAgentMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.KnowledgeBaseMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.KnowledgeGraphMcpProvider;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

/**
 * 原生企业级 MCP Server 自动装配配置类
 */
@Configuration
public class McpServerAutoConfiguration {

    @Bean
    public McpServerRegistry mcpServerRegistry() {
        McpServerRegistry registry = new McpServerRegistry("qknow-mcp-server", "2.2.1");
        new KnowledgeBaseMcpProvider().registerTo(registry);
        new KnowledgeGraphMcpProvider().registerTo(registry);
        new DataAgentMcpProvider().registerTo(registry);
        new CodeSandboxMcpProvider().registerTo(registry);
        return registry;
    }
}
