package tech.qiantong.qknow.hermes.grpc;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * gRPC ServerCall 与 Reactor Flux 双向取消桥接器
 * 保证客户端取消（RST_STREAM）向服务端 Reactor 订阅级联传播，杜绝算力与 Token 空跑
 */
@Slf4j
public class GrpcReactorBridge {

    /**
     * 将 Reactor Flux 安全绑定到 gRPC Server 响应流，支持双向 Cancellation 级联传播
     *
     * @param source           Reactor 数据流
     * @param responseObserver gRPC 响应观察者
     * @param requestId        请求标识（用于跟踪日志）
     * @param <T>              响应类型
     */
    public static <T> void bindStream(Flux<T> source, StreamObserver<T> responseObserver, String requestId) {
        if (!(responseObserver instanceof ServerCallStreamObserver)) {
            // 防御性兼容：若非 ServerCallStreamObserver，降级为常规消费
            source.subscribe(
                    responseObserver::onNext,
                    responseObserver::onError,
                    responseObserver::onCompleted
            );
            return;
        }

        ServerCallStreamObserver<T> serverObserver = (ServerCallStreamObserver<T>) responseObserver;
        AtomicBoolean isCompleted = new AtomicBoolean(false);

        // 1. 挂载 Reactor 管道取消时的动作
        Flux<T> monitoredFlux = source
                .doOnCancel(() -> {
                    log.info("[gRPC-Reactor] 收到流 Cancel 信号: requestId={}", requestId);
                })
                .doFinally(signalType -> {
                    log.debug("[gRPC-Reactor] 流终态终止信号: signal={}, requestId={}", signalType, requestId);
                });

        // 2. 订阅 Flux，获取 Disposable 句柄
        Disposable subscription = monitoredFlux.subscribe(
                item -> {
                    // 必须先检查 client 是否已经取消，避免在已取消的 channel 上写入引发异常
                    if (!serverObserver.isCancelled()) {
                        serverObserver.onNext(item);
                    }
                },
                error -> {
                    if (!serverObserver.isCancelled() && isCompleted.compareAndSet(false, true)) {
                        log.error("[gRPC-Reactor] 流异常终止: requestId={}", requestId, error);
                        serverObserver.onError(error);
                    }
                },
                () -> {
                    if (!serverObserver.isCancelled() && isCompleted.compareAndSet(false, true)) {
                        log.info("[gRPC-Reactor] 流正常完成: requestId={}", requestId);
                        serverObserver.onCompleted();
                    }
                }
        );

        // 3. 核心枢纽：向 gRPC ServerCall 注册取消监听器，级联触发 dispose
        serverObserver.setOnCancelHandler(() -> {
            log.warn("[gRPC-Server] 客户端取消请求 (RST_STREAM): requestId={}, 立即级联释放底层执行资源！", requestId);
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });
    }
}
