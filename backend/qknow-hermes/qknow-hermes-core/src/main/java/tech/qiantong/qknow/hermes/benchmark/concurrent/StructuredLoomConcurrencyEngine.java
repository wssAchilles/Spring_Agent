package tech.qiantong.qknow.hermes.benchmark.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Java 21 现代运行时的结构化虚拟线程并发引擎 (StructuredLoomConcurrencyEngine)
 * <p>
 * 核心理论契约与系统工程防线 (AGENTS.md 第七章 & JEP 453/446 规范)：
 * 1. 结构化生命周期闭环 (Structured Lifetime)：子任务生命周期严格绑定在词法作用域内，
 *    彻底消除非结构化 CompletableFuture 导致的后台孤儿任务 (Orphan Tasks) 与无界 Token 泄漏；
 * 2. 原子短路级联取消 (Shutdown On Failure)：当某并行子任务发生异常、超时或护栏熔断时，
 *    毫秒级触发级联中断向同作用域所有活跃兄弟虚拟线程传播，实现零延迟快速失败；
 * 3. 竞速短路截断 (Shutdown On Success)：支持首胜快速截断，一旦首个高置信度召回/推理达成，立即截断慢速分支；
 * 4. 零拷贝不可变上下文传播：采用不可变执行上下文对象，杜绝数万虚拟线程下传统 ThreadLocal 内存暴涨。
 * </p>
 *
 * @author Achilles
 * @since Phase 150
 */
@Component
public class StructuredLoomConcurrencyEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoomConcurrencyEngine.class);

    /**
     * 作用域不可变上下文对象 (代替高内存开销的 ThreadLocal)
     */
    public record ScopedExecutionContext(
            String tenantId,
            String sessionId,
            String traceId,
            long requestTimestamp
    ) {
        public static ScopedExecutionContext of(String tenantId, String sessionId) {
            return new ScopedExecutionContext(
                    tenantId != null ? tenantId : "default_tenant",
                    sessionId != null ? sessionId : UUID.randomUUID().toString(),
                    "trace-" + System.nanoTime(),
                    System.currentTimeMillis()
            );
        }
    }

    /**
     * 结构化执行度量总结
     */
    public record StructuredExecutionResult<T>(
            List<T> results,
            int completedTasks,
            int cancelledOrphanTasks,
            long durationUs,
            boolean allSucceeded
    ) {}

    private final ExecutorService virtualThreadExecutor;
    private final AtomicInteger orphanTasksPreventedTotal = new AtomicInteger(0);
    private final AtomicLong totalTasksScheduled = new AtomicLong(0);

    public StructuredLoomConcurrencyEngine() {
        // 创建无界极速虚拟线程池 (Project Loom)
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        log.info("初始化 Java 21 结构化虚拟线程并发引擎，底层调度器: VirtualThreadPerTaskExecutor");
    }

    /**
     * 【模式一：原子短路快速失败 (All-Or-Nothing / ShutdownOnFailure)】
     * 并行执行全部任务，一旦任一任务抛出异常或超时，立即原子级联中断取消其余仍在执行的全部子任务
     *
     * @param tasks 待并行子任务列表
     * @param timeoutMs 超时时间毫秒
     * @param <T> 返回类型
     * @return 结构化执行结果与孤儿任务阻断计数
     * @throws Exception 若有子任务失败或超时则抛出异常
     */
    public <T> StructuredExecutionResult<T> executeAllOrShortCircuit(
            List<Callable<T>> tasks,
            long timeoutMs
    ) throws Exception {
        if (tasks == null || tasks.isEmpty()) {
            return new StructuredExecutionResult<>(List.of(), 0, 0, 0, true);
        }

        long startNs = System.nanoTime();
        int taskCount = tasks.size();
        totalTasksScheduled.addAndGet(taskCount);

        List<Future<T>> futures = new ArrayList<>(taskCount);
        List<T> results = new ArrayList<>(taskCount);
        AtomicInteger cancelledCount = new AtomicInteger(0);
        Exception capturedFailure = null;

        // 使用 CompletionService 模拟结构化作用域快速短路监听
        CompletionService<T> completionService = new ExecutorCompletionService<>(virtualThreadExecutor);

        for (Callable<T> task : tasks) {
            futures.add(completionService.submit(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw e;
                }
            }));
        }

        int completed = 0;
        long deadlineNs = startNs + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        try {
            while (completed < taskCount) {
                long remainingNs = deadlineNs - System.nanoTime();
                if (remainingNs <= 0) {
                    throw new TimeoutException("结构化并发作用域超时，触发级联短路截断");
                }

                Future<T> pollFuture = completionService.poll(remainingNs, TimeUnit.NANOSECONDS);
                if (pollFuture == null) {
                    throw new TimeoutException("结构化并发等待超时");
                }

                try {
                    T value = pollFuture.get();
                    results.add(value);
                    completed++;
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause();
                    capturedFailure = (cause instanceof Exception ex) ? ex : new RuntimeException(cause);
                    log.warn("结构化子任务发生异常，触发快速短路取消传播: {}", capturedFailure.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            capturedFailure = e;
        }

        // 若发生异常或超时，执行原子级联取消，清除所有未完成的孤儿任务
        if (capturedFailure != null || completed < taskCount) {
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    if (f.cancel(true)) {
                        cancelledCount.incrementAndGet();
                        orphanTasksPreventedTotal.incrementAndGet();
                    }
                }
            }
        }

        long durationUs = (System.nanoTime() - startNs) / 1000;

        if (capturedFailure != null) {
            log.info("结构化作用域短路完成，耗时: {}us, 成功截断清理孤儿任务数: {}", durationUs, cancelledCount.get());
            throw capturedFailure;
        }

        return new StructuredExecutionResult<>(
                results,
                completed,
                cancelledCount.get(),
                durationUs,
                true
        );
    }

    /**
     * 【模式二：首胜竞速截断 (Race-To-First / ShutdownOnSuccess)】
     * 并行分发多个候选分支，一旦首个高质量分支完成，立即取消其余全部并行任务
     *
     * @param tasks 候选分支任务列表
     * @param timeoutMs 超时时间毫秒
     * @param <T> 返回类型
     * @return 最先完成的任务产出与截断的孤儿任务数
     */
    public <T> T executeRaceToFirst(List<Callable<T>> tasks, long timeoutMs) throws Exception {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("竞速任务列表不可为空");
        }

        long startNs = System.nanoTime();
        int taskCount = tasks.size();
        totalTasksScheduled.addAndGet(taskCount);

        List<Future<T>> futures = new ArrayList<>(taskCount);
        CompletionService<T> completionService = new ExecutorCompletionService<>(virtualThreadExecutor);

        for (Callable<T> task : tasks) {
            futures.add(completionService.submit(task));
        }

        T firstResult = null;
        Exception lastException = null;
        int completed = 0;
        long deadlineNs = startNs + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        while (completed < taskCount && firstResult == null) {
            long remainingNs = deadlineNs - System.nanoTime();
            if (remainingNs <= 0) break;

            Future<T> future = completionService.poll(remainingNs, TimeUnit.NANOSECONDS);
            if (future == null) break;

            completed++;
            try {
                firstResult = future.get();
            } catch (ExecutionException ee) {
                lastException = ee.getCause() instanceof Exception ex ? ex : new RuntimeException(ee.getCause());
            }
        }

        // 首胜达成后立即截断取消所有其余正在计算的分支
        int cancelled = 0;
        for (Future<T> f : futures) {
            if (!f.isDone()) {
                if (f.cancel(true)) {
                    cancelled++;
                    orphanTasksPreventedTotal.incrementAndGet();
                }
            }
        }

        if (firstResult != null) {
            log.debug("竞速短路完成，首胜达成，截断慢速任务数: {}", cancelled);
            return firstResult;
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new TimeoutException("首胜竞速作用域未能在时限内获取到有效结果");
    }

    public int getOrphanTasksPreventedTotal() {
        return orphanTasksPreventedTotal.get();
    }

    public long getTotalTasksScheduled() {
        return totalTasksScheduled.get();
    }

    @Override
    public void close() {
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
