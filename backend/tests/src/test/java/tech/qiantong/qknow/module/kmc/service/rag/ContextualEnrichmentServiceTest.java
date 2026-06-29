package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextualEnrichmentServiceTest {

    @Test
    @DisplayName("禁用时返回原始chunk")
    void enrich_withDisabled_returnsOriginalChunk() {
        ContextualEnrichmentService.ContextualConfig config = new ContextualEnrichmentService.ContextualConfig();
        config.setEnabled(false);
        ContextualEnrichmentService service = new ContextualEnrichmentService(null, config);

        String result = service.enrich("Full document", "Chunk content");
        assertEquals("Chunk content", result);
    }

    @Test
    @DisplayName("enrichAsync禁用时返回原始chunk")
    void enrichAsync_withDisabled_returnsOriginalChunk() {
        ContextualEnrichmentService.ContextualConfig config = new ContextualEnrichmentService.ContextualConfig();
        config.setEnabled(false);
        ContextualEnrichmentService service = new ContextualEnrichmentService(null, config);

        var result = service.enrichAsync("Full document", "Chunk content");
        assertEquals("Chunk content", result.join());
    }

    @Test
    @DisplayName("默认配置值正确")
    void config_hasCorrectDefaults() {
        ContextualEnrichmentService.ContextualConfig config = new ContextualEnrichmentService.ContextualConfig();
        assertFalse(config.isEnabled());
        assertEquals("DeepSeek", config.getPlatform());
        assertEquals("deepseek-chat", config.getModelName());
        assertEquals(8000, config.getMaxDocChars());
        assertEquals(3, config.getMaxConcurrent());
    }
}
