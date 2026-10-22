package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.StreamSubscriptionGuard;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-08 回归防护：阻塞式模型流的订阅必须异步返回并先落位 Disposable，
 * 使断连时能够级联 dispose 内层订阅；同时验证取消信号可传播、正常完成不受影响。
 */
class StreamCancellationContractTest {

    @Test
    @DisplayName("阻塞源：subscribe 立即返回且句柄已落位，可被 dispose")
    void blockingSourceSubscribeReturnsPromptlyWithHandle() throws Exception {
        CountDownLatch sourceSubscribed = new CountDownLatch(1);
        AtomicReference<Disposable> ref = new AtomicReference<>();
        Flux<Integer> blocking = Flux.create(sink -> {
            sourceSubscribed.countDown();
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sink.next(1);
            sink.complete();
        });

        long start = System.nanoTime();
        StreamSubscriptionGuard.subscribeAsync(blocking, ref, v -> { }, e -> { }, () -> { });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMs < 800L,
                "阻塞源下 subscribe 必须异步返回，实际耗时 " + elapsedMs + "ms");
        assertNotNull(ref.get(), "subscribe 返回前必须已落位 Disposable 句柄");
        assertTrue(sourceSubscribed.await(1, TimeUnit.SECONDS));

        ref.get().dispose();
        assertTrue(ref.get().isDisposed());
    }

    @Test
    @DisplayName("取消传播：dispose 后源收到 cancel 信号")
    void disposeMustPropagateCancelToSource() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CountDownLatch subscribed = new CountDownLatch(1);
        AtomicReference<Disposable> ref = new AtomicReference<>();

        StreamSubscriptionGuard.subscribeAsync(
                Flux.<Integer>never()
                        .doOnSubscribe(s -> subscribed.countDown())
                        .doOnCancel(() -> cancelled.set(true)),
                ref, v -> { }, e -> { }, () -> { });

        assertTrue(subscribed.await(2, TimeUnit.SECONDS), "订阅未及时建立");
        StreamSubscriptionGuard.disposeIfPresent(ref);
        assertTrue(cancelled.get(), "dispose 必须把取消信号传播到源");
    }

    @Test
    @DisplayName("正常完成：非阻塞源事件与完成信号不丢失")
    void normalCompletionStillWorks() {
        AtomicInteger nextCount = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<Disposable> ref = new AtomicReference<>();

        StreamSubscriptionGuard.subscribeAsync(
                Flux.just(1, 2, 3, 4).delayElements(Duration.ofMillis(1)),
                ref, v -> nextCount.incrementAndGet(), e -> { }, () -> completed.set(true));

        long deadline = System.currentTimeMillis() + 3000L;
        while (!completed.get() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(completed.get(), "正常流必须完成");
        assertEquals(4, nextCount.get());
    }
}
