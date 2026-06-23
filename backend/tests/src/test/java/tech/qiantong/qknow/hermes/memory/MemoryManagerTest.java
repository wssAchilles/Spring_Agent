package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryManagerTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    private ShortTermMemory shortTermMemory;
    private LongTermMemory longTermMemory;
    private WorkingMemory workingMemory;
    private MemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        shortTermMemory = new ShortTermMemory(chatModel);
        longTermMemory = new LongTermMemory(vectorStore, embeddingModel);
        workingMemory = new WorkingMemory();
        memoryManager = new MemoryManager(shortTermMemory, longTermMemory, workingMemory);
    }

    // ========== ShortTermMemory Tests ==========

    @Test
    @DisplayName("添加 25 条消息，getContext(20) 返回最后 20 条")
    void shortTerm_getContext_returnsLastNMessages() {
        for (int i = 0; i < 25; i++) {
            shortTermMemory.addMessage(new UserMessage("msg-" + i));
        }

        List<Message> context = shortTermMemory.getContext(20);

        assertEquals(20, context.size());
        assertEquals("msg-5", context.get(0).getText());
        assertEquals("msg-24", context.get(19).getText());
    }

    @Test
    @DisplayName("消息数未超过窗口时 summarize 不触发 LLM 调用")
    void shortTerm_summarize_noOpWhenWithinWindow() {
        for (int i = 0; i < 10; i++) {
            shortTermMemory.addMessage(new UserMessage("msg-" + i));
        }

        shortTermMemory.summarize(20);

        assertEquals(10, shortTermMemory.size());
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("summarize 压缩旧消息为摘要")
    void shortTerm_summarize_compressesOldMessages() {
        // 添加 25 条消息
        for (int i = 0; i < 25; i++) {
            shortTermMemory.addMessage(new UserMessage("msg-" + i));
        }

        // Mock LLM 返回摘要
        AssistantMessage summaryMsg = new AssistantMessage("这是一段对话摘要");
        Generation generation = new Generation(summaryMsg);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        doReturn(chatResponse).when(chatModel).call(any(Prompt.class));

        shortTermMemory.summarize(20);

        // 摘要 + 最近 20 条 = 21
        assertEquals(21, shortTermMemory.size());
        // 第一条是摘要
        List<Message> all = shortTermMemory.getContext(21);
        assertTrue(all.get(0) instanceof SystemMessage);
        assertTrue(all.get(0).getText().contains("对话摘要"));
        // 最后一条是 msg-24
        assertEquals("msg-24", all.get(20).getText());
    }

    // ========== LongTermMemory Tests ==========

    @Test
    @DisplayName("store 调用 vectorStore.add 存储文档")
    void longTerm_store_callsVectorStoreAdd() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", "sess-1");
        metadata.put("userId", "user-1");

        longTermMemory.store("重要信息", metadata);

        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    @DisplayName("recall 返回语义相似的文档")
    void longTerm_recall_returnsSimilarDocuments() {
        Document doc1 = new Document("doc-1 content", Map.of("sessionId", "s1"));
        Document doc2 = new Document("doc-2 content", Map.of("sessionId", "s2"));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc1, doc2));

        List<Document> results = longTermMemory.recall("查询内容", 5);

        assertEquals(2, results.size());
        assertEquals("doc-1 content", results.get(0).getText());
        assertEquals("doc-2 content", results.get(1).getText());
        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("recall vectorStore 返回 null 时返回空列表")
    void longTerm_recall_nullSafe() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(null);

        List<Document> results = longTermMemory.recall("查询", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ========== WorkingMemory Tests ==========

    @Test
    @DisplayName("set + get + clear 生命周期")
    void working_setGetClearLifecycle() {
        // set
        workingMemory.set("intent", "查询天气");
        workingMemory.set("entities", List.of("北京"));

        // get
        assertEquals("查询天气", workingMemory.get("intent"));
        assertEquals(List.of("北京"), workingMemory.get("entities"));
        assertEquals(2, workingMemory.size());
        assertTrue(workingMemory.containsKey("intent"));

        // getOrDefault
        assertEquals("default", workingMemory.getOrDefault("missing", "default"));

        // remove
        workingMemory.remove("entities");
        assertEquals(1, workingMemory.size());
        assertNull(workingMemory.get("entities"));

        // clear
        workingMemory.clear();
        assertEquals(0, workingMemory.size());
        assertFalse(workingMemory.containsKey("intent"));
    }

    // ========== MemoryManager Facade Tests ==========

    @Test
    @DisplayName("MemoryManager 正确委托到三层记忆")
    void memoryManager_delegatesToAllThreeLayers() {
        assertNotNull(memoryManager.getShortTerm());
        assertNotNull(memoryManager.getLongTerm());
        assertNotNull(memoryManager.getWorking());

        assertSame(shortTermMemory, memoryManager.getShortTerm());
        assertSame(longTermMemory, memoryManager.getLongTerm());
        assertSame(workingMemory, memoryManager.getWorking());
    }

    @Test
    @DisplayName("onConversationEnd 触发摘要 + 长期存储 + 清空工作记忆")
    void memoryManager_onConversationEnd_fullLifecycle() {
        // 准备短期记忆数据
        for (int i = 0; i < 25; i++) {
            shortTermMemory.addMessage(new UserMessage("msg-" + i));
        }
        // 准备工作记忆数据
        workingMemory.set("key", "value");

        // Mock LLM 摘要
        AssistantMessage summaryMsg = new AssistantMessage("对话结束摘要");
        Generation generation = new Generation(summaryMsg);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        doReturn(chatResponse).when(chatModel).call(any(Prompt.class));

        memoryManager.onConversationEnd("sess-1", "user-1");

        // 验证工作记忆已清空
        assertEquals(0, workingMemory.size());
        // 验证长期记忆被调用
        verify(vectorStore, atLeastOnce()).add(anyList());
    }
}
