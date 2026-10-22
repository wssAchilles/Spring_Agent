package tech.qiantong.qknow.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;

/**
 * MCP3：控制面 MCP 工具语义路由的 EmbeddingModel 装配器。
 * 使用 ApplicationRunner 在容器就绪后注入，避免 McpToolAdapter 创建期与配置类形成循环依赖。
 */
@Slf4j
@Component
public class McpSemanticEmbeddingInitializer implements ApplicationRunner {

    @Autowired(required = false)
    private McpToolAdapter mcpToolAdapter;

    @Autowired(required = false)
    private IAiModelApiService aiModelApiService;

    @Value("${qknow.mcp.embedding.platform:TongYi}")
    private String embeddingPlatform;

    @Value("${qknow.mcp.embedding.model-name:text-embedding-v4}")
    private String embeddingModelName;

    @Override
    public void run(ApplicationArguments args) {
        if (mcpToolAdapter == null || aiModelApiService == null) {
            log.info("控制面 MCP 语义路由未接入 EmbeddingModel，保持降级路由");
            return;
        }
        try {
            mcpToolAdapter.setEmbeddingModel(
                    aiModelApiService.getEmbeddingModelByPlatform(embeddingPlatform, embeddingModelName));
            log.info("控制面 MCP 语义路由已接入向量模型: platform={}, model={}",
                    embeddingPlatform, embeddingModelName);
        } catch (Exception e) {
            log.warn("控制面 MCP 语义路由 EmbeddingModel 初始化失败，降级为关键词路由: {}", e.getMessage());
        }
    }
}
