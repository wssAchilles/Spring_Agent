package tech.qiantong.qknow.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.ai.service.impl.EmbeddingServiceImpl;
import tech.qiantong.qknow.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceImplTest {

    @Test
    @DisplayName("相同参数返回缓存的同一实例")
    void sameParams_returnsSameCachedInstance() {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl();

        // Ollama 没有 apiKey 依赖，适合测试缓存
        EmbeddingModel m1 = service.getEmbeddingModel("Ollama", "http://localhost:11434", null, "nomic-embed-text");
        EmbeddingModel m2 = service.getEmbeddingModel("Ollama", "http://localhost:11434", null, "nomic-embed-text");

        assertSame(m1, m2, "Same params should return cached instance");
    }

    @Test
    @DisplayName("不同参数返回不同实例")
    void differentParams_returnsDifferentInstance() {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl();

        EmbeddingModel m1 = service.getEmbeddingModel("Ollama", "http://localhost:11434", null, "nomic-embed-text");
        EmbeddingModel m2 = service.getEmbeddingModel("Ollama", "http://localhost:11434", null, "all-minilm");

        assertNotSame(m1, m2, "Different model names should return different instances");
    }

    @Test
    @DisplayName("不支持的平台抛出异常")
    void unsupportedPlatform_throwsException() {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl();
        ServiceException exception = assertThrowsExactly(ServiceException.class, () ->
                service.getEmbeddingModel("Unsupported", "http://x", "key", "model"));
        assertTrue(exception.getMessage().contains("Unsupported"));
    }

    @Test
    @DisplayName("OpenAI 平台仍可创建向量模型")
    void openAiPlatform_remainsSupported() {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl();

        assertInstanceOf(OpenAiEmbeddingModel.class,
                service.getEmbeddingModel("OpenAI", "https://api.openai.com", "test-key", "text-embedding-3-small"));
    }

    @Test
    @DisplayName("text-embedding-v4 默认使用存量 1024 维")
    void dashScopeTextEmbeddingV4_defaultsTo1024Dimensions() {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl();

        OpenAiEmbeddingModel model = (OpenAiEmbeddingModel) service.getEmbeddingModel(
                "TongYi", null, "test-key", "text-embedding-v4");
        OpenAiEmbeddingOptions options = (OpenAiEmbeddingOptions)
                ReflectionTestUtils.getField(model, "defaultOptions");

        assertNotNull(options);
        assertEquals(1024, options.getDimensions());
    }

    @Test
    @DisplayName("显式维度配置覆盖模型默认值")
    void dashScopeExplicitDimension_overridesModelDefault() {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl();
        ReflectionTestUtils.setField(service, "configuredDimension", 1536);

        OpenAiEmbeddingModel model = (OpenAiEmbeddingModel) service.getEmbeddingModel(
                "TongYi", null, "test-key", "text-embedding-v4");
        OpenAiEmbeddingOptions options = (OpenAiEmbeddingOptions)
                ReflectionTestUtils.getField(model, "defaultOptions");

        assertNotNull(options);
        assertEquals(1536, options.getDimensions());
    }

    @Test
    @DisplayName("未配置时不再为其它模型硬编码 1536 维")
    void dashScopeOtherModel_doesNotForceDimension() {
        EmbeddingServiceImpl service = new EmbeddingServiceImpl();

        OpenAiEmbeddingModel model = (OpenAiEmbeddingModel) service.getEmbeddingModel(
                "TongYi", null, "test-key", "text-embedding-v2");
        OpenAiEmbeddingOptions options = (OpenAiEmbeddingOptions)
                ReflectionTestUtils.getField(model, "defaultOptions");

        assertNotNull(options);
        assertNull(options.getDimensions());
    }
}
