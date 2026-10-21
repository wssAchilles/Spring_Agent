package tech.qiantong.qknow.server.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.qiantong.qknow.hermes.config.AiModelAliasProperties;
import tech.qiantong.qknow.hermes.config.ModelNameResolver;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;

/**
 * Hermes 桥接配置类
 * 提供主容器与 Hermes 核心组件之间的 Bean 供给与桥接
 */
@Slf4j
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

    @Autowired(required = false)
    private McpToolAdapter mcpToolAdapterForWiring;

    @Autowired(required = false)
    private IAiModelApiService aiModelApiService;

    @org.springframework.beans.factory.annotation.Value("${qknow.mcp.embedding.platform:TongYi}")
    private String mcpEmbeddingPlatform;

    @org.springframework.beans.factory.annotation.Value("${qknow.mcp.embedding.model-name:text-embedding-v2}")
    private String mcpEmbeddingModelName;

    /**
     * MCP3：为控制面 MCP 工具适配器接入千问 1536 维向量模型，
     * 使语义工具路由不再因缺失 EmbeddingModel 而降级为关键词匹配。
     * 无论自建 Bean 还是组件扫描装配的适配器都会被统一补齐。
     */
    @PostConstruct
    public void wireMcpSemanticEmbedding() {
        if (mcpToolAdapterForWiring == null || aiModelApiService == null) {
            log.info("控制面 MCP 语义路由未接入 EmbeddingModel，保持降级路由");
            return;
        }
        try {
            mcpToolAdapterForWiring.setEmbeddingModel(
                    aiModelApiService.getEmbeddingModelByPlatform(mcpEmbeddingPlatform, mcpEmbeddingModelName));
            log.info("控制面 MCP 语义路由已接入向量模型: platform={}, model={}",
                    mcpEmbeddingPlatform, mcpEmbeddingModelName);
        } catch (Exception e) {
            log.warn("控制面 MCP 语义路由 EmbeddingModel 初始化失败，降级为关键词路由: {}", e.getMessage());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager dagCheckpointManager() {
        return new tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public tech.qiantong.qknow.hermes.agent.AgentRegistry agentRegistry(
            org.springframework.beans.factory.ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider) {
        return new tech.qiantong.qknow.hermes.agent.AgentRegistry(null, chatModelProvider.getIfAvailable(), null);
    }
}
