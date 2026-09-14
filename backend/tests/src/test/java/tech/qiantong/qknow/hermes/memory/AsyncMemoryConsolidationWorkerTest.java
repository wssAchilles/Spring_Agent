package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import tech.qiantong.qknow.redis.service.IRedisService;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncMemoryConsolidationWorker 异步固化引擎与并发防丢锁测试")
class AsyncMemoryConsolidationWorkerTest {

    @Mock
    private MemoryManager memoryManager;

    @Mock
    private ShortTermMemory shortTermMemory;

    @Mock
    private IRedisService redisService;

    private SleepTimeMemoryAgent worker;

    @BeforeEach
    void setUp() {
        lenient().when(memoryManager.getShortTerm()).thenReturn(shortTermMemory);
        worker = new SleepTimeMemoryAgent(memoryManager, 1800000L, 500, redisService);
    }

    @Test
    @DisplayName("前台并发冲突防丢测试：处理期间前台追加新消息，仅裁剪旧切片，新消息0丢失")
    void testConcurrentWriteSafetyNoMessageLost() {
        String sessionId = "1001";
        String userId = "2001";
        String scope = "workspace:1:bot:2";

        when(shortTermMemory.listSessionIds(anyInt())).thenReturn(List.of(sessionId));
        when(shortTermMemory.getLastActivityAt(sessionId)).thenReturn(System.currentTimeMillis() - 2000000L);
        when(shortTermMemory.getSessionUserId(sessionId)).thenReturn(userId);
        when(shortTermMemory.getSessionScope(sessionId)).thenReturn(scope);

        // 获取分布式锁成功
        when(redisService.setNx(contains("memory:lock:session:1001"), anyString(), eq(60L))).thenReturn(true);

        // 模拟开始处理时有 5 条消息
        when(shortTermMemory.size(sessionId)).thenReturn(5);

        // 模拟处理结束时前台追加了 2 条新消息，变为 7 条
        when(redisService.getListSize("memory:short:1001")).thenReturn(7L);

        worker.consolidateIdleConversations();

        // 验证调用了 onConversationEnd
        verify(memoryManager, atLeastOnce()).onConversationEnd(sessionId, userId, scope);

        // 必须通过 lTrim 仅裁剪前 5 条 (保留 index 5 到 -1)，严禁直接 clearSession 清空所有！
        verify(redisService, atLeastOnce()).lTrim("memory:short:1001", 5, -1);
        verify(shortTermMemory, never()).clearSession(sessionId);

        // 必须释放分布式锁
        verify(redisService, atLeastOnce()).delete(contains("memory:lock:session:1001"));
    }

    @Test
    @DisplayName("前台正在聊天写锁避让：获取锁失败时立即跳过，避免阻塞用户体验")
    void testLockContentionYieldImmediately() {
        String sessionId = "1002";
        when(shortTermMemory.listSessionIds(anyInt())).thenReturn(List.of(sessionId));
        when(shortTermMemory.getLastActivityAt(sessionId)).thenReturn(System.currentTimeMillis() - 2000000L);
        when(shortTermMemory.getSessionUserId(sessionId)).thenReturn("2002");
        when(shortTermMemory.getSessionScope(sessionId)).thenReturn("workspace:1:bot:2");

        // 模拟前台持有锁
        when(redisService.setNx(contains("memory:lock:session:1002"), anyString(), eq(60L))).thenReturn(false);

        worker.consolidateIdleConversations();

        // 绝不触发固化逻辑
        verify(memoryManager, never()).onConversationEnd(anyString(), anyString(), anyString());
    }
}
