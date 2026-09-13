package tech.qiantong.qknow.kb.biz.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CascadeRouterTest {

    private EmbeddingModel embeddingModel;
    private ChatClient chatClient;
    private CascadeRouter cascadeRouter;
    private ChatModel chatModel;

    @BeforeEach
    public void setup() {
        embeddingModel = mock(EmbeddingModel.class);
        chatModel = mock(ChatModel.class);
        
        // 模拟 Spring AI ChatClient.Builder
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        chatClient = builder.build();
        
        cascadeRouter = new CascadeRouter(embeddingModel, chatClient);
        
        // 预设质心向量 (模拟业务边界)
        cascadeRouter.addRouteCentroid("chitchat", new float[]{1.0f, 0.0f, 0.0f});
        cascadeRouter.addRouteCentroid("knowledge", new float[]{0.0f, 1.0f, 0.0f});
    }

    @Test
    public void testL1SemanticHit() {
        // Mock L1 极高相似度命中
        EmbeddingResponse er = mock(EmbeddingResponse.class);
        Embedding emb = new Embedding(new float[]{0.9f, 0.1f, 0.0f}, 0);
        when(er.getResult()).thenReturn(emb);
        when(embeddingModel.embedForResponse(any(List.class))).thenReturn(er);

        CascadeRouter.RouteResult result = cascadeRouter.route("你好啊，今天天气真不错");
        
        assertEquals("chitchat", result.getRouteName());
        assertEquals(CascadeRouter.RouteLayer.L1_SEMANTIC, result.getHitLayer());
        // 确保没有去调用 L2 的大模型
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    public void testL2DeepSeekAmbiguous() {
        // Mock L1 未命中
        EmbeddingResponse er = mock(EmbeddingResponse.class);
        Embedding emb = new Embedding(new float[]{0.1f, 0.1f, 0.8f}, 0);
        when(er.getResult()).thenReturn(emb);
        when(embeddingModel.embedForResponse(any(List.class))).thenReturn(er);

        // Mock L2 DeepSeek 返回低置信度 JSON
        String deepSeekJson = "{\"intent\": \"UNKNOWN\", \"confidence\": 0.3, \"clarification_options\": [\"选项A\", \"选项B\"]}";
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(deepSeekJson))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        assertThrows(ClarificationRequiredException.class, () -> {
            cascadeRouter.route("帮我查一下那个东西");
        });
    }

    @Test
    public void testL2DeepSeekConfident() {
        // Mock L1 未命中
        EmbeddingResponse er = mock(EmbeddingResponse.class);
        Embedding emb = new Embedding(new float[]{0.1f, 0.1f, 0.8f}, 0);
        when(er.getResult()).thenReturn(emb);
        when(embeddingModel.embedForResponse(any(List.class))).thenReturn(er);

        // Mock L2 DeepSeek 返回高置信度 JSON
        String deepSeekJson = "{\"intent\": \"knowledge\", \"confidence\": 0.9, \"clarification_options\": []}";
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(deepSeekJson))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        CascadeRouter.RouteResult result = cascadeRouter.route("请问公司的报销流程是什么？");
        
        assertEquals("knowledge", result.getRouteName());
        assertEquals(CascadeRouter.RouteLayer.L2_LOGICAL, result.getHitLayer());
    }
}
