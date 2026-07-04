package tech.qiantong.qknow.ai.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

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
        assertThrows(Exception.class, () ->
                service.getEmbeddingModel("Unsupported", "http://x", "key", "model"));
    }
}
