package tech.qiantong.qknow.mcp.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tech.qiantong.qknow.mcp.server.provider.CodeSandboxMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.DataAgentMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.KnowledgeBaseMcpProvider;
import tech.qiantong.qknow.mcp.server.provider.KnowledgeGraphMcpProvider;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;
import tech.qiantong.qknow.mcp.server.security.McpTransientLeaseManager;
import tech.qiantong.qknow.mcp.server.security.QuadDefenseSecurityPipeline;

/**
 * 原生企业级 MCP Server 自动装配配置类（默认强制挂载四级纵深安全防线）
 */
@AutoConfiguration
@ConditionalOnClass(McpServerRegistry.class)
public class McpServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpTransientLeaseManager mcpTransientLeaseManager() {
        return new McpTransientLeaseManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public QuadDefenseSecurityPipeline quadDefenseSecurityPipeline(McpTransientLeaseManager leaseManager) {
        return new QuadDefenseSecurityPipeline(leaseManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpServerRegistry mcpServerRegistry(
            QuadDefenseSecurityPipeline securityPipeline,
            ObjectProvider<McpBuiltinProviderDelegates> delegatesProvider
    ) {
        return mcpServerRegistry(securityPipeline, delegatesProvider != null ? delegatesProvider.getIfAvailable() : null);
    }

    /**
     * 编程式构造带业务委托与四级防线的 MCP Server 注册中心
     */
    public McpServerRegistry mcpServerRegistry(
            QuadDefenseSecurityPipeline securityPipeline,
            McpBuiltinProviderDelegates delegates
    ) {
        McpServerRegistry registry = new McpServerRegistry("qknow-mcp-server", "2.2.1", securityPipeline);
        KnowledgeBaseMcpProvider kbProvider = new KnowledgeBaseMcpProvider();
        KnowledgeGraphMcpProvider kgProvider = new KnowledgeGraphMcpProvider();
        DataAgentMcpProvider dataProvider = new DataAgentMcpProvider();
        CodeSandboxMcpProvider sandboxProvider = new CodeSandboxMcpProvider();

        if (delegates != null) {
            kbProvider.setSearchDelegate(delegates.knowledgeBaseSearchDelegate());
            kgProvider.setQueryDelegate(delegates.knowledgeGraphQueryDelegate());
            dataProvider.setSqlExecutorDelegate(delegates.sqlExecutorDelegate());
            sandboxProvider.setSandboxRunnerDelegate(delegates.sandboxRunnerDelegate());
        }

        kbProvider.registerTo(registry);
        kgProvider.registerTo(registry);
        dataProvider.registerTo(registry);
        sandboxProvider.registerTo(registry);
        return registry;
    }

    /**
     * 单参数便捷工厂方法（兼容既有单测直接构造）
     */
    public McpServerRegistry mcpServerRegistry(QuadDefenseSecurityPipeline securityPipeline) {
        return mcpServerRegistry(securityPipeline, (McpBuiltinProviderDelegates) null);
    }

    /**
     * 无参便捷工厂方法（供独立测试直接构造带完整四级防线的注册中心）
     */
    public McpServerRegistry mcpServerRegistry() {
        return mcpServerRegistry(new QuadDefenseSecurityPipeline(new McpTransientLeaseManager()), (McpBuiltinProviderDelegates) null);
    }
}
