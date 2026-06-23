package tech.qiantong.qknow.hermes.tool.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolResilienceTest {

    @Mock
    private ToolCallback mockCallback;

    private ToolDefinition toolDefinition;

    @BeforeEach
    void setUp() {
        toolDefinition = ToolDefinition.builder()
                .name("testTool")
                .description("A test tool")
                .inputSchema("{}")
                .build();
        lenient().when(mockCallback.getToolDefinition()).thenReturn(toolDefinition);
    }

    @Test
    void normalCallSucceedsImmediately() {
        when(mockCallback.call("{\"query\":\"test\"}")).thenReturn("success");

        ToolCircuitBreaker cb = new ToolCircuitBreaker(5, 60000);
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(mockCallback, 5000, 3, cb);

        String result = decorator.call("{\"query\":\"test\"}");

        assertEquals("success", result);
        verify(mockCallback, times(1)).call("{\"query\":\"test\"}");
        assertEquals(ToolCircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void timeoutTriggersRetry() {
        when(mockCallback.call("{\"query\":\"slow\"}"))
                .thenThrow(new RuntimeException("timeout"));

        ToolCircuitBreaker cb = new ToolCircuitBreaker(5, 60000);
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(mockCallback, 50, 3, cb);

        String result = decorator.call("{\"query\":\"slow\"}");

        assertEquals("工具调用失败，请稍后重试", result);
        verify(mockCallback, times(3)).call("{\"query\":\"slow\"}");
    }

    @Test
    void successOnSecondRetry() {
        when(mockCallback.call("{\"query\":\"flaky\"}"))
                .thenThrow(new RuntimeException("transient error"))
                .thenReturn("recovered");

        ToolCircuitBreaker cb = new ToolCircuitBreaker(5, 60000);
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(mockCallback, 5000, 3, cb);

        String result = decorator.call("{\"query\":\"flaky\"}");

        assertEquals("recovered", result);
        verify(mockCallback, times(2)).call("{\"query\":\"flaky\"}");
        assertEquals(ToolCircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void allRetriesExhaustedReturnsFallback() {
        when(mockCallback.call("{\"query\":\"broken\"}"))
                .thenThrow(new RuntimeException("permanent error"));

        ToolCircuitBreaker cb = new ToolCircuitBreaker(10, 60000);
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(
                mockCallback, 5000, 3, cb, "自定义兜底消息");

        String result = decorator.call("{\"query\":\"broken\"}");

        assertEquals("自定义兜底消息", result);
        verify(mockCallback, times(3)).call("{\"query\":\"broken\"}");
    }

    @Test
    void circuitBreakerOpensAfterThresholdFailures() {
        when(mockCallback.call("{\"query\":\"fail\"}"))
                .thenThrow(new RuntimeException("error"));

        ToolCircuitBreaker cb = new ToolCircuitBreaker(5, 60000);
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(mockCallback, 100, 1, cb);

        for (int i = 0; i < 5; i++) {
            decorator.call("{\"query\":\"fail\"}");
        }

        assertEquals(ToolCircuitBreaker.State.OPEN, cb.getState());

        String rejected = decorator.call("{\"query\":\"fail\"}");
        assertNotNull(rejected);
        assertFalse(rejected.isEmpty());
    }

    @Test
    void circuitBreakerRecoveryTimeoutTransitionsToHalfOpenThenClosed() throws Exception {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(mockCallback.call("{\"query\":\"test\"}")).thenAnswer(invocation -> {
            int n = callCount.incrementAndGet();
            if (n <= 2) {
                throw new RuntimeException("failure " + n);
            }
            return "recovered";
        });

        ToolCircuitBreaker cb = new ToolCircuitBreaker(2, 100);
        ToolResilienceDecorator decorator = new ToolResilienceDecorator(mockCallback, 5000, 1, cb);

        // 2 calls with 1 attempt each = 2 failures -> OPEN
        decorator.call("{\"query\":\"test\"}");
        decorator.call("{\"query\":\"test\"}");

        assertEquals(ToolCircuitBreaker.State.OPEN, cb.getState());

        // Wait for recovery timeout
        Thread.sleep(150);

        // getState() transitions to HALF_OPEN, allowCall returns true
        assertTrue(cb.allowCall());
        assertEquals(ToolCircuitBreaker.State.HALF_OPEN, cb.getState());

        // Successful call in HALF_OPEN -> CLOSED
        String result = decorator.call("{\"query\":\"test\"}");
        assertEquals("recovered", result);
        assertEquals(ToolCircuitBreaker.State.CLOSED, cb.getState());
    }
}
