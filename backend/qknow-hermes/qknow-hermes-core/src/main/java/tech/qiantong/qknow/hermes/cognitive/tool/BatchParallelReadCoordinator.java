package tech.qiantong.qknow.hermes.cognitive.tool;

import tech.qiantong.qknow.hermes.transaction.frontier.ToolSideEffectTier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 基于 Java 21 虚拟线程的确定性批量并发只读工具调度器（Phase 11.3 BPR-LM，定理 3）。
 * <p>
 * 严格遵守“只读并发扇出 + 副作用隔离 + 规范化批内去重 + 严格按序归并”四大不变量：
 * 1. 仅允许 {@link ToolSideEffectTier#BUFFERABLE} 只读任务提交至 {@code Executors.newVirtualThreadPerTaskExecutor()}；
 * 2. 任何 {@code EXTERNALIZED_REVERSIBLE} 或 {@code IRREVERSIBLE} 任务必须在主线程按原始顺序严格串行执行；
 * 3. 在批内并发派发前按 {@code toolName + "|" + normalize(argumentsJson)} 做去重，彻底消除 LlamaIndex 式的重复并发浪费；
 * 4. 最终结果列表严格按输入任务下标 {@code 0..N-1} 一一对应返回，保障多轮轨迹可复现性。
 * </p>
 */
public class BatchParallelReadCoordinator {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 单个待调度工具调用描述。
     */
    public record ToolInvocationTask(
            String callId,
            String toolName,
            String argumentsJson,
            ToolSideEffectTier tier,
            Supplier<String> executor
    ) {
        public ToolInvocationTask {
            callId = callId != null ? callId : "";
            toolName = toolName != null ? toolName : "";
            argumentsJson = argumentsJson != null ? argumentsJson : "{}";
            tier = tier != null ? tier : ToolSideEffectTier.BUFFERABLE;
        }
    }

    /**
     * 批量并发执行不可变审验凭单。
     */
    public record BatchToolExecutionReceipt(
            String requestId,
            int totalTasks,
            int parallelReadTasks,
            int serialGuardedTasks,
            int deduplicatedTasks,
            long wallClockMs,
            long sumTaskMs,
            double speedupRatio,
            long timestampMs
    ) {}

    /**
     * 批量执行结果封装（输出严格与输入下标保序）。
     */
    public record BatchExecutionOutcome(
            List<String> orderedResults,
            BatchToolExecutionReceipt receipt
    ) {}

    private record SingleTaskResult(String output, long durationMs) {}

    private final AtomicLong totalBatches = new AtomicLong(0L);
    private final AtomicLong totalParallelReadCalls = new AtomicLong(0L);
    private final AtomicLong totalSerialGuardedCalls = new AtomicLong(0L);
    private final AtomicLong totalDeduplicatedBatchCalls = new AtomicLong(0L);
    private final AtomicLong cumulativeWallTimeSavedMs = new AtomicLong(0L);

    /**
     * 执行一批工具调用，并发调度 {@code BUFFERABLE} 只读任务，串行隔离副作用任务，并按原始顺序归并结果。
     */
    public BatchExecutionOutcome executeOrdered(
            String requestId,
            List<ToolInvocationTask> tasks,
            Duration perTaskTimeout
    ) {
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-bpr" : requestId;
        if (tasks == null || tasks.isEmpty()) {
            BatchToolExecutionReceipt emptyReceipt = new BatchToolExecutionReceipt(
                    safeReq, 0, 0, 0, 0, 0L, 0L, 1.0, System.currentTimeMillis()
            );
            return new BatchExecutionOutcome(List.of(), emptyReceipt);
        }

        Duration safeTimeout = (perTaskTimeout == null || perTaskTimeout.isNegative() || perTaskTimeout.isZero())
                ? DEFAULT_TIMEOUT
                : perTaskTimeout;

        long wallStartNs = System.nanoTime();
        int n = tasks.size();
        String[] results = new String[n];
        long[] taskDurationsMs = new long[n];
        int parallelCount = 0;
        int serialCount = 0;
        int dedupCount = 0;

        // 1. 统计并筛选可并发任务，执行规范化批内去重（相同 toolName + argumentsJson 仅执行一次）
        Map<String, Integer> firstIdxByDedupKey = new LinkedHashMap<>();
        Map<Integer, CompletableFuture<SingleTaskResult>> futureByPrimaryIdx = new LinkedHashMap<>();
        Map<Integer, Integer> duplicateTargetByIdx = new LinkedHashMap<>();
        List<Integer> serialIndices = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ToolInvocationTask task = tasks.get(i);
            if (task != null && task.tier() == ToolSideEffectTier.BUFFERABLE) {
                parallelCount++;
                String dedupKey = normalizeDedupKey(task.toolName(), task.argumentsJson());
                Integer primaryIdx = firstIdxByDedupKey.get(dedupKey);
                if (primaryIdx != null) {
                    duplicateTargetByIdx.put(i, primaryIdx);
                    dedupCount++;
                } else {
                    firstIdxByDedupKey.put(dedupKey, i);
                }
            } else {
                serialCount++;
                serialIndices.add(i);
            }
        }

        // 2. 使用 Java 21 虚拟线程并发执行去重后的 BUFFERABLE 只读任务
        if (!firstIdxByDedupKey.isEmpty()) {
            try (ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Integer primaryIdx : firstIdxByDedupKey.values()) {
                    ToolInvocationTask task = tasks.get(primaryIdx);
                    CompletableFuture<SingleTaskResult> future = CompletableFuture.supplyAsync(() -> {
                        long t0 = System.nanoTime();
                        try {
                            String out = task.executor() != null ? task.executor().get() : "";
                            long dur = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
                            return new SingleTaskResult(out != null ? out : "", dur);
                        } catch (Throwable ex) {
                            long dur = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
                            return new SingleTaskResult("[TOOL_ERROR: " + ex.getMessage() + "]", dur);
                        }
                    }, vtExecutor);
                    futureByPrimaryIdx.put(primaryIdx, future);
                }

                long timeoutMs = Math.max(100L, safeTimeout.toMillis());
                for (Map.Entry<Integer, CompletableFuture<SingleTaskResult>> entry : futureByPrimaryIdx.entrySet()) {
                    int idx = entry.getKey();
                    try {
                        SingleTaskResult res = entry.getValue().get(timeoutMs, TimeUnit.MILLISECONDS);
                        results[idx] = res.output();
                        taskDurationsMs[idx] = res.durationMs();
                    } catch (Exception e) {
                        entry.getValue().cancel(true);
                        results[idx] = "[TOOL_ERROR: timeout after " + timeoutMs + "ms]";
                        taskDurationsMs[idx] = timeoutMs;
                    }
                }
            }
        }

        // 复用去重槽位输出
        for (Map.Entry<Integer, Integer> dup : duplicateTargetByIdx.entrySet()) {
            int dupIdx = dup.getKey();
            int srcIdx = dup.getValue();
            results[dupIdx] = results[srcIdx];
            taskDurationsMs[dupIdx] = 0L;
        }

        // 3. 对需要事务门控或不可逆的任务，严格在主线程按原序串行执行
        for (int sIdx : serialIndices) {
            ToolInvocationTask task = tasks.get(sIdx);
            long t0 = System.nanoTime();
            try {
                String out = (task != null && task.executor() != null) ? task.executor().get() : "";
                results[sIdx] = out != null ? out : "";
            } catch (Throwable ex) {
                results[sIdx] = "[TOOL_ERROR: " + ex.getMessage() + "]";
            }
            taskDurationsMs[sIdx] = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
        }

        long wallClockMs = Math.max(1L, (System.nanoTime() - wallStartNs) / 1_000_000L);
        long sumTaskMs = 0L;
        for (long d : taskDurationsMs) {
            sumTaskMs += d;
        }
        double speedup = (double) Math.max(sumTaskMs, wallClockMs) / wallClockMs;
        long savedMs = Math.max(0L, sumTaskMs - wallClockMs);

        totalBatches.incrementAndGet();
        totalParallelReadCalls.addAndGet(parallelCount);
        totalSerialGuardedCalls.addAndGet(serialCount);
        totalDeduplicatedBatchCalls.addAndGet(dedupCount);
        cumulativeWallTimeSavedMs.addAndGet(savedMs);

        List<String> orderedList = new ArrayList<>(n);
        Collections.addAll(orderedList, results);

        BatchToolExecutionReceipt receipt = new BatchToolExecutionReceipt(
                safeReq, n, parallelCount, serialCount, dedupCount,
                wallClockMs, sumTaskMs, speedup, System.currentTimeMillis()
        );
        return new BatchExecutionOutcome(Collections.unmodifiableList(orderedList), receipt);
    }

    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalBatches", totalBatches.get());
        map.put("totalParallelReadCalls", totalParallelReadCalls.get());
        map.put("totalSerialGuardedCalls", totalSerialGuardedCalls.get());
        map.put("totalDeduplicatedBatchCalls", totalDeduplicatedBatchCalls.get());
        map.put("cumulativeWallTimeSavedMs", cumulativeWallTimeSavedMs.get());
        return Collections.unmodifiableMap(map);
    }

    private static String normalizeDedupKey(String toolName, String argsJson) {
        String normTool = toolName != null ? toolName.trim().toLowerCase(Locale.ROOT) : "";
        String normArgs = argsJson != null
                ? argsJson.trim().replaceAll("\\s+", " ").replaceAll("\\s*([\\{\\}\\[\\]:,])\\s*", "$1")
                : "{}";
        return normTool + "|" + normArgs;
    }
}
