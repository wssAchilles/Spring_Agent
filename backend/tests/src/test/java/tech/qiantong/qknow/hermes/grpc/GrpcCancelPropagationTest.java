package tech.qiantong.qknow.hermes.grpc;

import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * gRPC 响应式双向取消与背压级联契约测试
 */
public class GrpcCancelPropagationTest {

    /**
     * 模拟测试用的 ServerCallStreamObserver
     */
    static class MockServerCallStreamObserver<T> extends ServerCallStreamObserver<T> {
        private Runnable onCancelHandler;
        private boolean cancelled = false;
        private final List<T> emitted = new ArrayList<>();
        private boolean completed = false;
        private Throwable error = null;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setOnCancelHandler(Runnable onCancelHandler) {
            this.onCancelHandler = onCancelHandler;
        }

        @Override
        public void setCompression(String compression) {}

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setOnReadyHandler(Runnable onReadyHandler) {}

        @Override
        public void disableAutoInboundFlowControl() {}

        @Override
        public void request(int count) {}

        @Override
        public void setMessageCompression(boolean enable) {}

        @Override
        public void onNext(T value) {
            if (cancelled) {
                throw new IllegalStateException("call was cancelled");
            }
            emitted.add(value);
        }

        @Override
        public void onError(Throwable t) {
            if (cancelled) {
                throw new IllegalStateException("call was cancelled");
            }
            this.error = t;
        }

        @Override
        public void onCompleted() {
            if (cancelled) {
                throw new IllegalStateException("call was cancelled");
            }
            this.completed = true;
        }

        public void simulateClientCancel() {
            this.cancelled = true;
            if (onCancelHandler != null) {
                onCancelHandler.run();
            }
        }
    }

    @Test
    @DisplayName("契约 1：客户端主动 Cancel 必须级联触发上游 Reactor Flux 的 onCancel 并 dispose 订阅")
    void testClientCancelTriggersFluxDisposal() throws InterruptedException {
        MockServerCallStreamObserver<String> observer = new MockServerCallStreamObserver<>();
        AtomicBoolean fluxCancelled = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Flux<String> sourceFlux = Flux.interval(Duration.ofMillis(10))
                .map(i -> "Chunk-" + i)
                .doOnCancel(() -> {
                    fluxCancelled.set(true);
                    latch.countDown();
                });

        GrpcReactorBridge.bindStream(sourceFlux, observer, "req-cancel-001");

        // 等待几个元素发出
        Thread.sleep(35);
        assertFalse(observer.emitted.isEmpty(), "取消前应有数据发出");

        // 模拟客户端主动断开
        observer.simulateClientCancel();

        // 验证上游 Reactor 流的 onCancel 被成功触发
        boolean onCancelInvoked = latch.await(200, TimeUnit.MILLISECONDS);
        assertTrue(onCancelInvoked, "客户端断开后必须在 200ms 内触发上游 Flux 的 doOnCancel");
        assertTrue(fluxCancelled.get());
    }

    @Test
    @DisplayName("契约 2：Cancel 发生后绝不再向 observer 写入数据或抛出异常")
    void testNoMessageEmittedAfterCancel() throws InterruptedException {
        MockServerCallStreamObserver<String> observer = new MockServerCallStreamObserver<>();

        Flux<String> sourceFlux = Flux.interval(Duration.ofMillis(10))
                .map(i -> "Data-" + i);

        GrpcReactorBridge.bindStream(sourceFlux, observer, "req-cancel-002");

        Thread.sleep(25);
        int countBeforeCancel = observer.emitted.size();
        assertTrue(countBeforeCancel > 0);

        observer.simulateClientCancel();

        // 继续等待 50ms，断言不会有后续数据推入已取消的 observer（且不会抛出 IllegalStateException）
        Thread.sleep(50);
        assertEquals(countBeforeCancel, observer.emitted.size(), "取消后不应再有新元素写入 observer");
        assertFalse(observer.completed, "取消后不应调用 onCompleted");
        assertNull(observer.error, "取消后不应调用 onError");
    }

    @Test
    @DisplayName("契约 3：流正常完成时正确调用 onCompleted")
    void testNormalStreamCompletion() throws InterruptedException {
        MockServerCallStreamObserver<String> observer = new MockServerCallStreamObserver<>();

        Flux<String> sourceFlux = Flux.just("Item1", "Item2", "Item3");

        GrpcReactorBridge.bindStream(sourceFlux, observer, "req-normal-003");

        Thread.sleep(50);
        assertEquals(3, observer.emitted.size());
        assertTrue(observer.completed);
        assertNull(observer.error);
    }
}
