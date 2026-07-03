package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SleepTimeMemoryAgent 测试")
class SleepTimeMemoryAgentTest {

    @Mock
    private MemoryManager memoryManager;

    @Mock
    private ShortTermMemory shortTermMemory;

    private SleepTimeMemoryAgent agent;

    private static final long IDLE_THRESHOLD_MS = 1800000L; // 30 分钟
    private static final int SCAN_COUNT = 500;

    @BeforeEach
    void setUp() {
        when(memoryManager.getShortTerm()).thenReturn(shortTermMemory);
        agent = new SleepTimeMemoryAgent(memoryManager, IDLE_THRESHOLD_MS, SCAN_COUNT);
    }

    @Test
    @DisplayName("空闲超时的会话被整合并清理")
    void consolidateIdleConversations_idleSession_consolidatedAndCleared() {
        long now = System.currentTimeMillis();
        long idleTimestamp = now - IDLE_THRESHOLD_MS - 1000; // 超过阈值

        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(List.of("sess-1"));
        when(shortTermMemory.getLastActivityAt("sess-1")).thenReturn(idleTimestamp);
        when(shortTermMemory.getSessionUserId("sess-1")).thenReturn("user-1");
        when(shortTermMemory.getSessionScope("sess-1")).thenReturn("default");

        agent.consolidateIdleConversations();

        verify(memoryManager).onConversationEnd("sess-1", "user-1", "default");
        verify(shortTermMemory).clearSession("sess-1");
    }

    @Test
    @DisplayName("活跃会话不被打捞")
    void consolidateIdleConversations_activeSession_notConsolidated() {
        long now = System.currentTimeMillis();
        long activeTimestamp = now - 1000; // 远未超过阈值

        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(List.of("sess-active"));
        when(shortTermMemory.getLastActivityAt("sess-active")).thenReturn(activeTimestamp);

        agent.consolidateIdleConversations();

        verify(memoryManager, never()).onConversationEnd(anyString(), anyString(), anyString());
        verify(shortTermMemory, never()).clearSession(anyString());
    }

    @Test
    @DisplayName("空会话列表不触发任何操作")
    void consolidateIdleConversations_emptySessionList_noInteractions() {
        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(Collections.emptyList());

        agent.consolidateIdleConversations();

        verify(memoryManager, never()).onConversationEnd(anyString(), anyString(), anyString());
        verify(shortTermMemory, never()).clearSession(anyString());
    }

    @Test
    @DisplayName("单个会话异常不中断循环，其他会话仍被处理")
    void consolidateIdleConversations_singleFailure_doesNotBlockOthers() {
        long now = System.currentTimeMillis();
        long idleTimestamp = now - IDLE_THRESHOLD_MS - 1000;

        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(List.of("sess-fail", "sess-ok"));
        when(shortTermMemory.getLastActivityAt("sess-fail")).thenReturn(idleTimestamp);
        when(shortTermMemory.getLastActivityAt("sess-ok")).thenReturn(idleTimestamp);
        when(shortTermMemory.getSessionUserId(anyString())).thenReturn("user-1");
        when(shortTermMemory.getSessionScope(anyString())).thenReturn("default");

        // 第一个会话抛异常
        doThrow(new RuntimeException("boom"))
                .when(memoryManager).onConversationEnd(eq("sess-fail"), anyString(), anyString());
        // 第二个会话正常
        doNothing().when(memoryManager).onConversationEnd(eq("sess-ok"), anyString(), anyString());

        agent.consolidateIdleConversations();

        // 两个会话都尝试整合
        verify(memoryManager).onConversationEnd("sess-fail", "user-1", "default");
        verify(memoryManager).onConversationEnd("sess-ok", "user-1", "default");
        // 只有成功的被清理
        verify(shortTermMemory, never()).clearSession("sess-fail");
        verify(shortTermMemory).clearSession("sess-ok");
    }

    @Test
    @DisplayName("lastActivityAt 为 0 或负数时会话不被打捞")
    void consolidateIdleConversations_zeroOrNegativeTimestamp_skipped() {
        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(List.of("sess-unknown"));
        when(shortTermMemory.getLastActivityAt("sess-unknown")).thenReturn(0L);

        agent.consolidateIdleConversations();

        verify(memoryManager, never()).onConversationEnd(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sessionId 为 null 的 userId/scope 降级为 unknown/default")
    void consolidateIdleConversations_nullUserIdScope_defaultsToUnknown() {
        long now = System.currentTimeMillis();
        long idleTimestamp = now - IDLE_THRESHOLD_MS - 1000;

        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(List.of("sess-null"));
        when(shortTermMemory.getLastActivityAt("sess-null")).thenReturn(idleTimestamp);
        when(shortTermMemory.getSessionUserId("sess-null")).thenReturn(null);
        when(shortTermMemory.getSessionScope("sess-null")).thenReturn(null);

        agent.consolidateIdleConversations();

        verify(memoryManager).onConversationEnd("sess-null", "unknown", "default");
        verify(shortTermMemory).clearSession("sess-null");
    }
}
