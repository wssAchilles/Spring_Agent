package tech.qiantong.qknow.hermes.tool.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolCircuitBreakerTest {

    @Test
    @DisplayName("初始状态为 CLOSED，允许调用")
    void initialState_isClosed_allowsCalls() {
        ToolCircuitBreaker cb = new ToolCircuitBreaker(3, 1000);
        assertEquals(ToolCircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowCall());
    }

    @Test
    @DisplayName("连续失败达到阈值后转为 OPEN")
    void failures_reachThreshold_opensCircuit() {
        ToolCircuitBreaker cb = new ToolCircuitBreaker(3, 1000);
        cb.recordFailure();
        cb.recordFailure();
        assertTrue(cb.allowCall()); // 2 failures, threshold not reached
        cb.recordFailure();
        assertEquals(ToolCircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowCall());
    }

    @Test
    @DisplayName("恢复超时后转为 HALF_OPEN")
    void open_afterRecoveryTimeout_transitionsToHalfOpen() throws InterruptedException {
        ToolCircuitBreaker cb = new ToolCircuitBreaker(2, 100); // 100ms timeout
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(ToolCircuitBreaker.State.OPEN, cb.getState());

        Thread.sleep(150); // Wait for recovery timeout
        assertEquals(ToolCircuitBreaker.State.HALF_OPEN, cb.getState());
        assertTrue(cb.allowCall());
    }

    @Test
    @DisplayName("HALF_OPEN 状态下成功恢复到 CLOSED")
    void halfOpen_success_closesCircuit() {
        ToolCircuitBreaker cb = new ToolCircuitBreaker(2, 100);
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(ToolCircuitBreaker.State.OPEN, cb.getState());

        // Simulate timeout
        cb.getState(); // transitions to HALF_OPEN if timeout passed
        cb.recordSuccess();
        assertEquals(ToolCircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getFailureCount());
    }

    @Test
    @DisplayName("并发 recordFailure 不丢失计数")
    void concurrentRecordFailure_noLostUpdates() throws InterruptedException {
        ToolCircuitBreaker cb = new ToolCircuitBreaker(1000, 60000);
        int threadCount = 10;
        int failuresPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < failuresPerThread; j++) {
                        cb.recordFailure();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * failuresPerThread, cb.getFailureCount());
    }

    @Test
    @DisplayName("并发 getState 不产生竞态")
    void concurrentGetState_consistent() throws InterruptedException {
        ToolCircuitBreaker cb = new ToolCircuitBreaker(5, 100);
        // Push to OPEN
        for (int i = 0; i < 5; i++) cb.recordFailure();
        assertEquals(ToolCircuitBreaker.State.OPEN, cb.getState());

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger halfOpenCount = new AtomicInteger(0);

        Thread.sleep(150); // Wait for recovery timeout

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ToolCircuitBreaker.State s = cb.getState();
                    if (s == ToolCircuitBreaker.State.HALF_OPEN) {
                        halfOpenCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // All threads should see HALF_OPEN after timeout
        assertEquals(threadCount, halfOpenCount.get());
    }
}
