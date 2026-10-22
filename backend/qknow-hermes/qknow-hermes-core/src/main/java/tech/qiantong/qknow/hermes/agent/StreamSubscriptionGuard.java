package tech.qiantong.qknow.hermes.agent;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * P0-08：阻塞式模型流的订阅与取消桥。
 * <p>
 * Reactor 的 {@code subscribe()} 在同步阻塞源上会一直占用调用线程，导致
 * {@code AtomicReference<Disposable>} 只能在流结束后才被赋值，断连时无法 dispose。
 * 这里把订阅调度到 {@code boundedElastic}，确保 subscribe 立即返回、句柄先落位，
 * 使取消信号能够级联 dispose 内层订阅（DeepSeek HTTP lines 随之 close）。
 */
public final class StreamSubscriptionGuard {

    private StreamSubscriptionGuard() {
    }

    public static <T> Disposable subscribeAsync(Flux<T> stream,
                                                AtomicReference<Disposable> subscriptionRef,
                                                Consumer<? super T> onNext,
                                                Consumer<? super Throwable> onError,
                                                Runnable onComplete) {
        Disposable disposable = stream
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(onNext, onError, onComplete);
        subscriptionRef.set(disposable);
        return disposable;
    }

    /**
     * 幂等释放；句柄尚未赋值时安全跳过。
     */
    public static void disposeIfPresent(AtomicReference<Disposable> subscriptionRef) {
        Disposable disposable = subscriptionRef.get();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}
