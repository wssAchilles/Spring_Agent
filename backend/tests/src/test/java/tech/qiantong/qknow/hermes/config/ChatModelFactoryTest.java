package tech.qiantong.qknow.hermes.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import tech.qiantong.qknow.ai.service.impl.ChatModelServiceImpl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatModelFactoryTest {

    private ChatModelServiceImpl chatModelService;

    @BeforeEach
    void setUp() {
        chatModelService = new ChatModelServiceImpl();
    }

    @Test
    void createOpenAiChatModel_doesNotThrow() {
        assertDoesNotThrow(() -> {
            ChatModel model = chatModelService.getChatModel(
                    "OpenAI", "https://api.openai.com/v1", "test-api-key", "gpt-4");
            assertNotNull(model);
        });
    }

    @Test
    void createDashScopeChatModel_doesNotThrow() {
        assertDoesNotThrow(() -> {
            ChatModel model = chatModelService.getChatModel(
                    "TongYi", null, "test-api-key", "qwen-turbo");
            assertNotNull(model);
        });
    }

    @Test
    void createOllamaChatModel_doesNotThrow() {
        assertDoesNotThrow(() -> {
            ChatModel model = chatModelService.getChatModel(
                    "Ollama", "http://localhost:11434", null, "llama3");
            assertNotNull(model);
        });
    }

    @Test
    void createDeepSeekChatModel_doesNotThrow() {
        assertDoesNotThrow(() -> {
            ChatModel model = chatModelService.getChatModel(
                    "DeepSeek", null, "test-api-key", "deepseek-chat");
            assertNotNull(model);
        });
    }
}
