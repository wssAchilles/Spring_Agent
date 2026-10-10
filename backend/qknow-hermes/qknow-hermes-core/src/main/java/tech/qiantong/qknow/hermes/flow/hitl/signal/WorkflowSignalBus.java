package tech.qiantong.qknow.hermes.flow.hitl.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 响应式工作流异步信号总线 (对标 Temporal Signals)
 * 支持基于事件驱动的审批放行、Webhook 注入与长异步任务回调唤醒，彻底告别物理线程阻塞与低效轮询。
 */
public class WorkflowSignalBus {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSignalBus.class);

    public record SignalDeliveryReceipt(
            String signalId,
            String runtimeId,
            String signalType,
            boolean resumedSuccessfully,
            long deliveredTimestampMs,
            String errorReason
    ) {}

    // key: runtimeId:expectedSignalType -> 等待中的 CompletableFuture 契约
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingSignals = new ConcurrentHashMap<>();

    /**
     * 向挂起的工作流实例投递异步信号
     */
    public SignalDeliveryReceipt sendSignal(String runtimeId, String signalType, Map<String, Object> signalPayload) {
        String key = buildWaitKey(runtimeId, signalType);
        CompletableFuture<Map<String, Object>> future = pendingSignals.remove(key);

        long now = System.currentTimeMillis();
        String signalId = UUID.randomUUID().toString();

        if (future == null) {
            log.warn("[SignalBus] 信号未匹配到任何挂起的等待者: runtimeId={}, signalType={}", runtimeId, signalType);
            return new SignalDeliveryReceipt(
                    signalId, runtimeId, signalType, false, now, "NO_PENDING_WAIT_REGISTERED"
            );
        }

        boolean completed = future.complete(signalPayload != null ? signalPayload : Map.of());
        log.info("[SignalBus] 成功投递信号并唤醒挂起流程: runtimeId={}, signalType={}, signalId={}",
                runtimeId, signalType, signalId);

        return new SignalDeliveryReceipt(
                signalId, runtimeId, signalType, completed, now, null
        );
    }

    /**
     * 注册长任务外部等待锁 (返回 CompletableFuture 供状态机异步 await)
     */
    public CompletableFuture<Map<String, Object>> registerPendingWait(String runtimeId, String expectedSignalType, long timeoutMillis) {
        String key = buildWaitKey(runtimeId, expectedSignalType);
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

        // 设置超时防护
        if (timeoutMillis > 0) {
            future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        pendingSignals.remove(key);
                        log.warn("[SignalBus] 等待信号超时: runtimeId={}, signalType={}, timeoutMs={}",
                                runtimeId, expectedSignalType, timeoutMillis);
                        return Map.of("error", "SIGNAL_TIMEOUT", "message", ex.getMessage());
                    });
        }

        pendingSignals.put(key, future);
        log.debug("[SignalBus] 成功注册异步信号等待挂起点: key={}, timeoutMs={}", key, timeoutMillis);
        return future;
    }

    /**
     * 查询是否存在指定等待中的信号锁
     */
    public boolean hasPendingWait(String runtimeId, String expectedSignalType) {
        return pendingSignals.containsKey(buildWaitKey(runtimeId, expectedSignalType));
    }

    private String buildWaitKey(String runtimeId, String signalType) {
        return runtimeId + ":" + signalType;
    }
}
