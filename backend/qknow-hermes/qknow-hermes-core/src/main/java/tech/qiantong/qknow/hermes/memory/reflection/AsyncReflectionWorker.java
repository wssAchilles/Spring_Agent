package tech.qiantong.qknow.hermes.memory.reflection;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.memory.model.MemoryNode;
import tech.qiantong.qknow.hermes.memory.model.ReflectiveInsightVO;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 异步反思折叠 Worker (AsyncReflectionWorker)
 * 具备：
 * 1. PriorityBlockingQueue 优先级队列调度
 * 2. JVM 内存双水位保护 (Soft 75% 预警, Hard 90% 熔断拦截)
 * 3. 异步后台折叠任务消费
 */
@Slf4j
public class AsyncReflectionWorker {

    @Getter
    public static class ReflectionTask implements Comparable<ReflectionTask> {
        private final String userId;
        private final String sessionId;
        private final int priority; // 越大越优先
        private final List<MemoryNode> observations;
        private final long createdAt;
        private final Consumer<List<ReflectiveInsightVO>> callback;

        public ReflectionTask(String userId, String sessionId, int priority,
                              List<MemoryNode> observations, Consumer<List<ReflectiveInsightVO>> callback) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.priority = priority;
            this.observations = observations != null ? observations : Collections.emptyList();
            this.createdAt = System.currentTimeMillis();
            this.callback = callback;
        }

        @Override
        public int compareTo(ReflectionTask other) {
            // 优先级降序，同优先级按创建时间升序 (FIFO)
            int cmp = Integer.compare(other.priority, this.priority);
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(this.createdAt, other.createdAt);
        }
    }

    private static final double MEMORY_SOFT_WATERMARK = 0.75;
    private static final double MEMORY_HARD_WATERMARK = 0.90;
    private static final int MAX_QUEUE_CAPACITY = 2000;

    private final ReflectionTreeEngine reflectionEngine;
    private final PriorityBlockingQueue<ReflectionTask> taskQueue;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public AsyncReflectionWorker(ReflectionTreeEngine reflectionEngine) {
        this(reflectionEngine, 2);
    }

    public AsyncReflectionWorker(ReflectionTreeEngine reflectionEngine, int workerThreads) {
        this.reflectionEngine = reflectionEngine != null ? reflectionEngine : new ReflectionTreeEngineImpl();
        this.taskQueue = new PriorityBlockingQueue<>(128);
        this.executor = Executors.newFixedThreadPool(Math.max(1, workerThreads), r -> {
            Thread t = new Thread(r, "async-reflection-worker");
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < workerThreads; i++) {
            executor.submit(this::processLoop);
        }
    }

    /**
     * 提交异步反思任务，带 JVM 内存水位防护
     */
    public boolean submitTask(String userId, String sessionId, int priority,
                              List<MemoryNode> observations, Consumer<List<ReflectiveInsightVO>> callback) {
        if (!running.get()) {
            log.warn("AsyncReflectionWorker is stopped, rejecting task for user {}", userId);
            return false;
        }

        // JVM 内存水位保护检查
        double memUsage = getJvmMemoryUsage();
        if (memUsage >= MEMORY_HARD_WATERMARK) {
            log.error("JVM Memory hard watermark exceeded: {}% >= {}%, dropping reflection task for session {}",
                    String.format("%.2f", memUsage * 100), String.format("%.2f", MEMORY_HARD_WATERMARK * 100), sessionId);
            return false;
        } else if (memUsage >= MEMORY_SOFT_WATERMARK) {
            log.warn("JVM Memory soft watermark exceeded: {}% >= {}%, throttling reflection task",
                    String.format("%.2f", memUsage * 100), String.format("%.2f", MEMORY_SOFT_WATERMARK * 100));
        }

        if (taskQueue.size() >= MAX_QUEUE_CAPACITY) {
            log.warn("Reflection task queue full (capacity {}), dropping lowest priority tasks", MAX_QUEUE_CAPACITY);
            return false;
        }

        ReflectionTask task = new ReflectionTask(userId, sessionId, priority, observations, callback);
        return taskQueue.offer(task);
    }

    private void processLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ReflectionTask task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
                if (task != null) {
                    List<ReflectiveInsightVO> insights = reflectionEngine.foldReflections(task.getUserId(), task.getObservations());
                    if (task.getCallback() != null) {
                        task.getCallback().accept(insights);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing reflection task", e);
            }
        }
    }

    public int getQueueSize() {
        return taskQueue.size();
    }

    public double getJvmMemoryUsage() {
        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory();
        long freeMem = rt.freeMemory();
        long usedMem = totalMem - freeMem;
        long maxMem = rt.maxMemory();
        return (double) usedMem / (double) maxMem;
    }

    public void shutdown() {
        running.set(false);
        executor.shutdownNow();
    }
}
