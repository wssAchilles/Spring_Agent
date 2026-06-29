package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryManagerEnhancedTest {

    private ShortTermMemory shortTerm;
    private LongTermMemory longTerm;
    private WorkingMemory working;
    private MemoryManager manager;

    @BeforeEach
    void setUp() {
        shortTerm = mock(ShortTermMemory.class);
        longTerm = mock(LongTermMemory.class);
        working = mock(WorkingMemory.class);
        manager = new MemoryManager(shortTerm, longTerm, working);
    }

    @Test
    @DisplayName("会话结束时带Scope存储到长期记忆")
    void onConversationEnd_withScope_storesWithScope() {
        when(shortTerm.getContext("session1", Integer.MAX_VALUE)).thenReturn(List.of());

        manager.onConversationEnd("session1", "user1", "project/alpha");

        verify(shortTerm).summarize("session1", 5);
        verify(longTerm).store(eq(""), argThat(metadata ->
                "project/alpha".equals(metadata.get("scope")) &&
                "session1".equals(metadata.get("sessionId"))
        ));
        verify(working).clear("session1");
    }

    @Test
    @DisplayName("存储重要记忆带正确的重要性分数")
    void storeImportant_storesWithCorrectImportance() {
        manager.storeImportant("important fact", "session1", "user1", "project/alpha", 0.9);

        verify(longTerm).store(eq("important fact"), argThat(metadata ->
                Double.valueOf(0.9).equals(metadata.get("importance")) &&
                "project/alpha".equals(metadata.get("scope"))
        ));
    }

    @Test
    @DisplayName("重要性分数被限制在0-1范围")
    void storeImportant_clampsImportance() {
        manager.storeImportant("fact", "s1", "u1", "scope", 1.5);
        verify(longTerm).store(anyString(), argThat(m -> Double.valueOf(1.0).equals(m.get("importance"))));

        manager.storeImportant("fact", "s1", "u1", "scope", -0.5);
        verify(longTerm).store(anyString(), argThat(m -> Double.valueOf(0.0).equals(m.get("importance"))));
    }

    @Test
    @DisplayName("按Scope召回调用longTerm.recall")
    void recallByScope_callsLongTermRecall() {
        Document doc = Document.builder().text("memory content").metadata(Map.of()).build();
        when(longTerm.recall("query", 5, "project/alpha")).thenReturn(List.of(doc));

        List<Document> results = manager.recallByScope("query", "project/alpha", 5);

        assertEquals(1, results.size());
        verify(longTerm).recall("query", 5, "project/alpha");
    }
}
