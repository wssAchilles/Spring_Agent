package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.ai.service.IEmbeddingService;
import tech.qiantong.qknow.module.ai.api.modelMarket.AiModelApiServiceImpl;
import tech.qiantong.qknow.module.ai.dal.dataobject.modelMarket.AiApiKeyDO;
import tech.qiantong.qknow.module.ai.dal.mapper.modelMarket.AiApiKeyMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP3 回归防护：控制面语义工具路由必须能按平台解析出千问 1536 维 EmbeddingModel，
 * 不再因缺失 EmbeddingModel 静默降级为关键词匹配。
 */
class McpEmbeddingWiringTest {

    @Test
    @DisplayName("按平台解析 embedding 模型：使用同平台 ai_api_key 构建千问向量模型")
    void resolveEmbeddingModelByPlatform() {
        AiApiKeyMapper apiKeyMapper = mock(AiApiKeyMapper.class);
        IEmbeddingService embeddingService = mock(IEmbeddingService.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        AiApiKeyDO qwenKey = new AiApiKeyDO();
        qwenKey.setId(7L);
        qwenKey.setPlatform("TongYi");
        qwenKey.setUrl("https://dashscope.aliyuncs.com/compatible-mode");
        qwenKey.setApiKey("sk-qwen-unit-test");

        AiApiKeyDO fallbackKey = new AiApiKeyDO();
        fallbackKey.setId(8L);
        fallbackKey.setPlatform("DeepSeek");
        fallbackKey.setApiKey("sk-deepseek-unit-test");

        when(apiKeyMapper.selectList(any())).thenReturn(List.of(fallbackKey, qwenKey));
        when(apiKeyMapper.selectById(7L)).thenReturn(qwenKey);
        when(embeddingService.getEmbeddingModel(
                eq("TongYi"), eq(qwenKey.getUrl()), eq("sk-qwen-unit-test"), eq("text-embedding-v2")))
                .thenReturn(embeddingModel);

        AiModelApiServiceImpl service = new AiModelApiServiceImpl();
        ReflectionTestUtils.setField(service, "apiKeyMapper", apiKeyMapper);
        ReflectionTestUtils.setField(service, "embeddingService", embeddingService);

        EmbeddingModel resolved = service.getEmbeddingModelByPlatform("TongYi", "text-embedding-v2");

        assertSame(embeddingModel, resolved);
        verify(embeddingService).getEmbeddingModel(
                eq("TongYi"), eq(qwenKey.getUrl()), eq("sk-qwen-unit-test"), eq("text-embedding-v2"));
    }

    @Test
    @DisplayName("平台缺省时回退到任意可用 Key，但必须选择非空密钥")
    void fallbackToAvailableKeyWhenPlatformMissing() {
        AiApiKeyMapper apiKeyMapper = mock(AiApiKeyMapper.class);
        IEmbeddingService embeddingService = mock(IEmbeddingService.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        AiApiKeyDO emptyKey = new AiApiKeyDO();
        emptyKey.setId(1L);
        emptyKey.setPlatform("TongYi");
        emptyKey.setApiKey("");

        AiApiKeyDO usableKey = new AiApiKeyDO();
        usableKey.setId(2L);
        usableKey.setPlatform("DeepSeek");
        usableKey.setUrl("https://api.deepseek.com");
        usableKey.setApiKey("sk-usable");

        when(apiKeyMapper.selectList(any())).thenReturn(List.of(emptyKey, usableKey));
        when(apiKeyMapper.selectById(2L)).thenReturn(usableKey);
        when(embeddingService.getEmbeddingModel(
                eq("DeepSeek"), eq("https://api.deepseek.com"), eq("sk-usable"), eq("text-embedding-v2")))
                .thenReturn(embeddingModel);

        AiModelApiServiceImpl service = new AiModelApiServiceImpl();
        ReflectionTestUtils.setField(service, "apiKeyMapper", apiKeyMapper);
        ReflectionTestUtils.setField(service, "embeddingService", embeddingService);

        assertSame(embeddingModel, service.getEmbeddingModelByPlatform("TongYi", "text-embedding-v2"));
    }
}
