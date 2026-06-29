package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HyPEIndexerTest {

    @Test
    @DisplayName("禁用时返回空列表")
    void generateHypotheticalDocuments_withDisabled_returnsEmpty() {
        HyPEIndexer.HyPEConfig config = new HyPEIndexer.HyPEConfig();
        config.setEnabled(false);
        HyPEIndexer indexer = new HyPEIndexer(null, config);

        Document original = new Document("Some content");
        List<Document> result = indexer.generateHypotheticalDocuments(original, "Full doc");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("短chunk不生成问题")
    void generateHypotheticalDocuments_withShortChunk_returnsEmpty() {
        HyPEIndexer.HyPEConfig config = new HyPEIndexer.HyPEConfig();
        config.setEnabled(true);
        HyPEIndexer indexer = new HyPEIndexer(null, config);

        Document original = new Document("Short");
        List<Document> result = indexer.generateHypotheticalDocuments(original, "Full doc");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("默认配置值正确")
    void config_hasCorrectDefaults() {
        HyPEIndexer.HyPEConfig config = new HyPEIndexer.HyPEConfig();
        assertFalse(config.isEnabled());
        assertEquals("DeepSeek", config.getPlatform());
        assertEquals("deepseek-chat", config.getModelName());
        assertEquals(3, config.getQuestionCount());
        assertEquals(2000, config.getMaxChunkChars());
    }
}
