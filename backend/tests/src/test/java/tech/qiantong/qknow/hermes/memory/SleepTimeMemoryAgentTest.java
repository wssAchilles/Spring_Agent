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

        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(List.of("30"));
        when(shortTermMemory.getLastActivityAt("30")).thenReturn(idleTimestamp);
        when(shortTermMemory.getSessionUserId("30")).thenReturn("40");
        when(shortTermMemory.getSessionScope("30")).thenReturn("workspace:10:bot:20");

        agent.consolidateIdleConversations();

        verify(memoryManager).onConversationEnd("30", "40", "workspace:10:bot:20");
        verify(shortTermMemory).clearSession("30");
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

        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(List.of("31", "32"));
        when(shortTermMemory.getLastActivityAt("31")).thenReturn(idleTimestamp);
        when(shortTermMemory.getLastActivityAt("32")).thenReturn(idleTimestamp);
        when(shortTermMemory.getSessionUserId(anyString())).thenReturn("40");
        when(shortTermMemory.getSessionScope(anyString())).thenReturn("workspace:10:bot:20");

        // 第一个会话抛异常
        doThrow(new RuntimeException("boom"))
                .when(memoryManager).onConversationEnd(eq("31"), anyString(), anyString());
        // 第二个会话正常
        doNothing().when(memoryManager).onConversationEnd(eq("32"), anyString(), anyString());

        agent.consolidateIdleConversations();

        // 两个会话都尝试整合
        verify(memoryManager).onConversationEnd("31", "40", "workspace:10:bot:20");
        verify(memoryManager).onConversationEnd("32", "40", "workspace:10:bot:20");
        // 只有成功的被清理
        verify(shortTermMemory, never()).clearSession("31");
        verify(shortTermMemory).clearSession("32");
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
    @DisplayName("身份元数据缺失的会话不进入共享记忆汇总")
    void consolidateIdleConversations_nullUserIdScope_skipsSharedMemory() {
        long now = System.currentTimeMillis();
        long idleTimestamp = now - IDLE_THRESHOLD_MS - 1000;

        when(shortTermMemory.listSessionIds(SCAN_COUNT)).thenReturn(List.of("sess-null"));
        when(shortTermMemory.getLastActivityAt("sess-null")).thenReturn(idleTimestamp);
        when(shortTermMemory.getSessionUserId("sess-null")).thenReturn(null);
        when(shortTermMemory.getSessionScope("sess-null")).thenReturn(null);

        agent.consolidateIdleConversations();

        verify(memoryManager, never()).onConversationEnd(anyString(), anyString(), anyString());
        verify(shortTermMemory, never()).clearSession(anyString());
    }
}
