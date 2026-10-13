package tech.qiantong.qknow.hermes.flow.mesh.governance.impl;

import tech.qiantong.qknow.hermes.flow.mesh.governance.AIMDConcurrencyGovernor;
import tech.qiantong.qknow.hermes.flow.mesh.governance.AdaptiveBackpressureMeshCoordinator;
import tech.qiantong.qknow.hermes.flow.mesh.governance.MeshBackpressureTelemetry;
import tech.qiantong.qknow.hermes.flow.mesh.governance.MeshSchedulingTicket;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自适应背压网格调度器默认实现
 * 基于 Java 21 虚拟线程友好型信号量与滑动窗口分位数采样
 */
public class DefaultAdaptiveBackpressureMeshCoordinator implements AdaptiveBackpressureMeshCoordinator {

    private static class AdjustableSemaphore extends Semaphore {
        public AdjustableSemaphore(int permits, boolean fair) {
            super(permits, fair);
        }
        public void reduce(int reduction) {
            reducePermits(reduction);
        }
    }

    private final AIMDConcurrencyGovernor governor;
    private final AdjustableSemaphore slotSemaphore;
    private final byte[] secretKey;
    private final AtomicInteger activeSlotsCount = new AtomicInteger(0);
    private final AtomicInteger queuedTasksCount = new AtomicInteger(0);

    // 滑动窗口采样统计 (最近 100 次调用的延迟与状态)
    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    private final AtomicInteger window429Count = new AtomicInteger(0);
    private final AtomicInteger windowErrorCount = new AtomicInteger(0);
    private final AtomicInteger windowTotalCount = new AtomicInteger(0);
    private final AtomicLong lastWindowResetMs = new AtomicLong(System.currentTimeMillis());

    private final ConcurrentHashMap<String, MeshSchedulingTicket> activeTickets = new ConcurrentHashMap<>();

    public DefaultAdaptiveBackpressureMeshCoordinator(int initialSlots, int minSlots, int maxSlots,
                                                       long targetP99LatencyMs, byte[] secretKey) {
        this.governor = new AIMDConcurrencyGovernor(initialSlots, minSlots, maxSlots, 0.5, targetP99LatencyMs);
        this.slotSemaphore = new AdjustableSemaphore(initialSlots, true);
        if (secretKey != null && secretKey.length > 0) {
            this.secretKey = secretKey.clone();
        } else {
            this.secretKey = new byte[32];
            new SecureRandom().nextBytes(this.secretKey);
        }
    }

    @Override
    public CompletableFuture<Optional<MeshSchedulingTicket>> acquireSlotAsync(String runtimeId, String nodeId, long timeoutMs) {
        queuedTasksCount.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean acquired = slotSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    return Optional.empty();
                }
                int currentActive = activeSlotsCount.incrementAndGet();
                String ticketId = "ticket_" + UUID.randomUUID().toString().replace("-", "");
                long now = System.currentTimeMillis();
                long expireAt = now + Math.max(5000L, timeoutMs * 2);

                MeshSchedulingTicket ticket = new MeshSchedulingTicket(
                        ticketId, runtimeId, nodeId, now, currentActive, expireAt, null
                );
                byte[] sig = ticket.computeHmac(secretKey);
                MeshSchedulingTicket signedTicket = new MeshSchedulingTicket(
                        ticketId, runtimeId, nodeId, now, currentActive, expireAt, sig
                );
                activeTickets.put(ticketId, signedTicket);
                return Optional.of(signedTicket);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } finally {
                queuedTasksCount.decrementAndGet();
            }
        });
    }

    @Override
    public void releaseSlot(MeshSchedulingTicket ticket, long latencyMs, boolean isSuccess, boolean isRateLimited) {
        if (ticket != null && activeTickets.remove(ticket.ticketId()) != null) {
            activeSlotsCount.decrementAndGet();
            slotSemaphore.release();

            // 采样记录
            recordTelemetrySample(latencyMs, isSuccess, isRateLimited);
        }
    }

    private void recordTelemetrySample(long latencyMs, boolean isSuccess, boolean isRateLimited) {
        latencySamples.add(latencyMs);
        if (latencySamples.size() > 200) {
            latencySamples.poll();
        }
        windowTotalCount.incrementAndGet();
        if (!isSuccess) {
            windowErrorCount.incrementAndGet();
        }
        if (isRateLimited) {
            window429Count.incrementAndGet();
        }

        long now = System.currentTimeMillis();
        if (now - lastWindowResetMs.get() >= 1000L) {
            synchronized (this) {
                if (now - lastWindowResetMs.get() >= 1000L) {
                    MeshBackpressureTelemetry telemetry = getTelemetrySnapshot();
                    int newLimit = governor.adjustOnFeedback(telemetry);
                    adjustPermits(newLimit);

                    // 重置部分窗口累加器
                    window429Count.set(0);
                    windowErrorCount.set(0);
                    windowTotalCount.set(0);
                    lastWindowResetMs.set(now);
                }
            }
        }
    }

    private void adjustPermits(int newLimit) {
        int currentPermits = slotSemaphore.availablePermits() + activeSlotsCount.get();
        int delta = newLimit - currentPermits;
        if (delta > 0) {
            slotSemaphore.release(delta);
        } else if (delta < 0) {
            slotSemaphore.reduce(-delta);
        }
    }

    @Override
    public MeshBackpressureTelemetry getTelemetrySnapshot() {
        List<Long> latencies = new ArrayList<>(latencySamples);
        double p99 = 0.0;
        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            int p99Idx = (int) Math.ceil(latencies.size() * 0.99) - 1;
            p99 = latencies.get(Math.max(0, Math.min(p99Idx, latencies.size() - 1))).doubleValue();
        }
        int total = windowTotalCount.get();
        double errorRate = total > 0 ? (double) windowErrorCount.get() / total : 0.0;

        return new MeshBackpressureTelemetry(
                System.currentTimeMillis(),
                activeSlotsCount.get(),
                queuedTasksCount.get(),
                p99,
                errorRate,
                window429Count.get(),
                governor.getCurrentLimit()
        );
    }

    public AIMDConcurrencyGovernor getGovernor() {
        return governor;
    }
}
