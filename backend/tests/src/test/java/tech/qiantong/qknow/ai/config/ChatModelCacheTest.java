package tech.qiantong.qknow.ai.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import tech.qiantong.qknow.ai.service.impl.ChatModelServiceImpl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ChatModelCacheTest {

    private ChatModelServiceImpl chatModelService;

    @BeforeEach
    void setUp() {
        chatModelService = new ChatModelServiceImpl();
    }

    @Test
    void sameParamsReturnCachedInstance() {
        ChatModel first = chatModelService.getChatModel("DeepSeek", "", "sk-test", "deepseek-chat");
        ChatModel second = chatModelService.getChatModel("DeepSeek", "", "sk-test", "deepseek-chat");

        assertSame(first, second, "Same parameters should return the cached instance");
    }

    @Test
    void concurrentCallsReturnSameInstance() throws Exception {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        Set<ChatModel> instances = ConcurrentHashMap.newKeySet();

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ChatModel model = chatModelService.getChatModel("DeepSeek", "", "sk-test", "deepseek-chat");
                        instances.add(model);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            startLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, instances.size(), "All concurrent calls should return the same cached instance");
    }

    @Test
    void differentParamsReturnDifferentInstances() {
        ChatModel deepseek = chatModelService.getChatModel("DeepSeek", "", "sk-test", "deepseek-chat");
        ChatModel ollama = chatModelService.getChatModel("Ollama", "http://localhost:11434", "", "llama3");
        ChatModel deepseekCoder = chatModelService.getChatModel("DeepSeek", "", "sk-test", "deepseek-coder");

        assertNotSame(deepseek, ollama, "Different platforms should return different instances");
        assertNotSame(deepseek, deepseekCoder, "Different models should return different instances");
        assertNotSame(ollama, deepseekCoder, "Different platform+model should return different instances");
    }
}
