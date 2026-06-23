package tech.qiantong.qknow.hermes.judge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiJudgeConfigTest {

    @Test
    void judgeConfigDefaultValues() {
        JudgeConfig config = new JudgeConfig();

        assertEquals("deepseek", config.getPlatform());
        assertNull(config.getBaseUrl());
        assertNull(config.getApiKey());
        assertEquals("deepseek-chat", config.getModelName());
        assertEquals(0.7, config.getThreshold());
    }

    @Test
    void judgeConfigCustomValues() {
        JudgeConfig config = new JudgeConfig();
        config.setPlatform("openai");
        config.setBaseUrl("https://api.openai.com");
        config.setApiKey("sk-test");
        config.setModelName("gpt-4o");
        config.setThreshold(0.8);

        assertEquals("openai", config.getPlatform());
        assertEquals("https://api.openai.com", config.getBaseUrl());
        assertEquals("sk-test", config.getApiKey());
        assertEquals("gpt-4o", config.getModelName());
        assertEquals(0.8, config.getThreshold());
    }

    @Test
    void aiJudgeServiceUsesConfigValues() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = new AssistantMessage(
                "{\"factuality\":0.9,\"relevance\":0.8,\"instruction\":0.7,\"feedback\":\"good\"}");

        when(factory.getChatModel(eq("openai"), eq("https://api.openai.com"), eq("sk-test"), eq("gpt-4o")))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);

        JudgeConfig config = new JudgeConfig();
        config.setPlatform("openai");
        config.setBaseUrl("https://api.openai.com");
        config.setApiKey("sk-test");
        config.setModelName("gpt-4o");
        config.setThreshold(0.8);

        AiJudgeService service = new AiJudgeService(factory, config);
        JudgeResult result = service.judge("test query", "context", "answer");

        verify(factory).getChatModel("openai", "https://api.openai.com", "sk-test", "gpt-4o");
        assertNotNull(result);
        assertEquals(0.9, result.getFactualityScore());
        assertEquals(0.8, result.getRelevanceScore());
        assertEquals(0.7, result.getInstructionScore());
    }

    @Test
    void aiJudgeServiceUsesDefaultConfig() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = new AssistantMessage(
                "{\"factuality\":0.6,\"relevance\":0.5,\"instruction\":0.4,\"feedback\":\"needs improvement\"}");

        when(factory.getChatModel(eq("deepseek"), isNull(), isNull(), eq("deepseek-chat")))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);

        JudgeConfig config = new JudgeConfig();
        AiJudgeService service = new AiJudgeService(factory, config);
        JudgeResult result = service.judge("test query", "context", "answer");

        verify(factory).getChatModel("deepseek", null, null, "deepseek-chat");
        assertNotNull(result);
        assertFalse(result.isPassed());
    }

    @Test
    void thresholdFromConfigAffectsPassFail() {
        ChatModelFactory factory = mock(ChatModelFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = new AssistantMessage(
                "{\"factuality\":0.7,\"relevance\":0.7,\"instruction\":0.7,\"feedback\":\"ok\"}");

        when(factory.getChatModel(anyString(), any(), any(), anyString())).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);

        JudgeConfig configLowThreshold = new JudgeConfig();
        configLowThreshold.setThreshold(0.5);
        AiJudgeService serviceLow = new AiJudgeService(factory, configLowThreshold);
        assertTrue(serviceLow.judge("q", "c", "a").isPassed());

        JudgeConfig configHighThreshold = new JudgeConfig();
        configHighThreshold.setThreshold(0.9);
        AiJudgeService serviceHigh = new AiJudgeService(factory, configHighThreshold);
        assertFalse(serviceHigh.judge("q", "c", "a").isPassed());
    }
}
