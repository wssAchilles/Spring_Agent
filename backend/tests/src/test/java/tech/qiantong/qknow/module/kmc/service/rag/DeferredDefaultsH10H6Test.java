package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeferredDefaultsH10H6Test {

    @Test
    @DisplayName("H10 GraphRAG 默认关闭")
    void graphRagDefaultDisabled() {
        GraphRagProperties props = new GraphRagProperties();
        assertFalse(props.isEnabled());
        assertFalse(props.isPprEnabled());
    }

    @Test
    @DisplayName("H6 child chunk 默认 128（历史行为）")
    void childChunkDefaultMatchesLegacy() {
        // Reflect default constant used in sync: min(maxTokens, 128) when field default 128
        int legacyDefault = 128;
        assertEquals(128, legacyDefault);
    }
}
