package tech.qiantong.qknow.mcp.server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tech.qiantong.qknow.mcp.server.provider.CodeSandboxMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.DataAgentMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.KnowledgeBaseMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.KnowledgeGraphMcpProvider;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

/**
 * 原生企业级 MCP Server 自动装配配置类
 */
@AutoConfiguration
@ConditionalOnClass(McpServerRegistry.class)
public class McpServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpServerRegistry mcpServerRegistry() {
        McpServerRegistry registry = new McpServerRegistry("qknow-mcp-server", "2.2.1");
        new KnowledgeBaseMcpProvider().registerTo(registry);
        new KnowledgeGraphMcpProvider().registerTo(registry);
        new DataAgentMcpProvider().registerTo(registry);
        new CodeSandboxMcpProvider().registerTo(registry);
        return registry;
    }
}
