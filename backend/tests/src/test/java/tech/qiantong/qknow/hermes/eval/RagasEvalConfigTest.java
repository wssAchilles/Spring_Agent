package tech.qiantong.qknow.hermes.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagasEvalConfigTest {

    @Test
    @DisplayName("评估配置支持Prompt程序字段")
    void config_supportsPromptProgramFields() {
        RagasEvalConfig config = new RagasEvalConfig();
        config.setPromptVersion("optimized-v1");
        config.setPromptExamples(List.of("faithfulness high => 0.95"));
        config.setMetricPrompts(Map.of("faithfulness", "custom faithfulness prompt"));
        config.setClaimExtractionPrompt("claims: ");
        config.setEntailmentSystemPrompt("entailment judge");
        config.setEntailmentUserPrompt("Context: %s\nClaim: %s");

        assertEquals("optimized-v1", config.getPromptVersion());
        assertEquals("custom faithfulness prompt", config.getMetricPrompts().get("faithfulness"));
        assertEquals("claims: ", config.getClaimExtractionPrompt());
        assertEquals("entailment judge", config.getEntailmentSystemPrompt());
    }
}
