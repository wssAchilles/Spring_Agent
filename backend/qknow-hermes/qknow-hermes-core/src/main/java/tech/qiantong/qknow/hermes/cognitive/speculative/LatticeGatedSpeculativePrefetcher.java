package tech.qiantong.qknow.hermes.cognitive.speculative;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.hermes.transaction.frontier.ToolSideEffectTier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 安全格约束的模式感知投机工具预取器（LG-STP，对应 Phase 7 原创架构机制三）。
 * <p>
 * 核心机制：
 * 1. 仅允许对 {@link ToolSideEffectTier#BUFFERABLE} 只读检索类工具开启思考期并发预取；
 * 2. 一旦匹配到 {@code EXTERNALIZED_REVERSIBLE} 或 {@code IRREVERSIBLE} 工具模式，确定性拦截并记录安全阻断计数；
 * 3. 基于 Java 21 虚拟线程（{@code newVirtualThreadPerTaskExecutor}）在思考输出空隙异步投机执行，
 *    当主推理环下一轮请求相同工具且参数匹配时 {@code O(1)} 即刻命中，显著对冲长思维链的端到端延迟。
 * </p>
 */
public class LatticeGatedSpeculativePrefetcher {

    private static final class SpeculativeSlot {
        final CompletableFuture<String> future;
        final long startTimeMs;
        final ToolSideEffectTier tier;

        SpeculativeSlot(CompletableFuture<String> future, long startTimeMs, ToolSideEffectTier tier) {
            this.future = future;
            this.startTimeMs = startTimeMs;
            this.tier = tier;
        }
    }

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Map<String, SpeculativeSlot>> slotsByRequest = new ConcurrentHashMap<>();
    private final Map<String, List<SpeculativePrefetchReceipt>> receiptsByRequest = new ConcurrentHashMap<>();
    private final AtomicLong totalScheduledCount = new AtomicLong();
    private final AtomicLong totalCacheHits = new AtomicLong();
    private final AtomicLong totalSafetyLatticeBlocks = new AtomicLong();
    private final AtomicLong totalSavedLatencyMs = new AtomicLong();

    /**
     * 提交模式感知投机预取任务：严格校安全格，只有 BUFFERABLE 工具方允许进入虚拟线程异步预执行。
     *
     * @return true 表示合法进入虚拟线程异步预取，false 表示因安全格约束或置信度不足被拒绝
     */
    public boolean scheduleSpeculativePrefetch(
            String requestId,
            SpeculativeToolPattern pattern,
            String normalizedArgsKey,
            Supplier<String> readOnlySupplier
    ) {
        Objects.requireNonNull(pattern, "SpeculativeToolPattern 不能为空");
        Objects.requireNonNull(readOnlySupplier, "readOnlySupplier 不能为空");
        String safeReq = normalizeReqId(requestId);
        String argsKey = normalizeArgs(normalizedArgsKey);

        // 安全格前置不变量检查：非 BUFFERABLE 一律禁止投机，根绝外部状态污染
        if (pattern.sideEffectTier() != ToolSideEffectTier.BUFFERABLE || pattern.confidence() < 0.65) {
            totalSafetyLatticeBlocks.incrementAndGet();
            recordReceipt(safeReq, new SpeculativePrefetchReceipt(
                    safeReq, pattern.targetToolName(), argsKey, false, true, 0L, System.currentTimeMillis()
            ));
            return false;
        }

        String slotKey = buildSlotKey(pattern.targetToolName(), argsKey);
        Map<String, SpeculativeSlot> reqSlots = slotsByRequest.computeIfAbsent(safeReq, k -> new ConcurrentHashMap<>());
        long startMs = System.currentTimeMillis();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(readOnlySupplier, virtualExecutor);
        reqSlots.putIfAbsent(slotKey, new SpeculativeSlot(future, startMs, pattern.sideEffectTier()));
        totalScheduledCount.incrementAndGet();
        return true;
    }

    /**
     * 主控制面消费投机槽位：若命中合法只读预取且在超时窗口内完成，直接 O(1) 返回结果并统计节省的毫秒数。
     */
    public String consumeIfReady(
            String requestId,
            String toolName,
            String normalizedArgsKey,
            ToolSideEffectTier actualTier,
            long maxWaitMs
    ) {
        String safeReq = normalizeReqId(requestId);
        String argsKey = normalizeArgs(normalizedArgsKey);
        if (actualTier != ToolSideEffectTier.BUFFERABLE) {
            return null;
        }
        Map<String, SpeculativeSlot> reqSlots = slotsByRequest.get(safeReq);
        if (reqSlots == null) {
            return null;
        }
        SpeculativeSlot slot = reqSlots.remove(buildSlotKey(toolName, argsKey));
        if (slot == null || slot.tier != ToolSideEffectTier.BUFFERABLE) {
            return null;
        }
        try {
            long waitStart = System.currentTimeMillis();
            String val = slot.future.get(Math.max(10L, maxWaitMs), TimeUnit.MILLISECONDS);
            long savedMs = Math.max(0L, waitStart - slot.startTimeMs);
            totalCacheHits.incrementAndGet();
            totalSavedLatencyMs.addAndGet(savedMs);
            recordReceipt(safeReq, new SpeculativePrefetchReceipt(
                    safeReq, toolName, argsKey, true, false, savedMs, System.currentTimeMillis()
            ));
            return val;
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<SpeculativePrefetchReceipt> getReceipts(String requestId) {
        if (requestId == null) {
            return List.of();
        }
        List<SpeculativePrefetchReceipt> list = receiptsByRequest.get(requestId);
        return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    public long getTotalSafetyLatticeBlocks() {
        return totalSafetyLatticeBlocks.get();
    }

    public long getTotalCacheHits() {
        return totalCacheHits.get();
    }

    public long getTotalSavedLatencyMs() {
        return totalSavedLatencyMs.get();
    }

    public void clearRequest(String requestId) {
        if (requestId == null) {
            return;
        }
        Map<String, SpeculativeSlot> removed = slotsByRequest.remove(requestId);
        if (removed != null) {
            for (SpeculativeSlot slot : removed.values()) {
                slot.future.cancel(true);
            }
        }
    }

    private void recordReceipt(String reqId, SpeculativePrefetchReceipt receipt) {
        receiptsByRequest.computeIfAbsent(reqId, k -> Collections.synchronizedList(new ArrayList<>())).add(receipt);
    }

    private static String buildSlotKey(String toolName, String argsKey) {
        return (toolName != null ? toolName.trim() : "tool") + "::" + argsKey;
    }

    private static String normalizeArgs(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        // 对 JSON 参数执行键序规范化，消除 {"title": "A"} 与 {"title":"A"} 及不同键顺序造成的投机缓存失配
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JSONObject obj = JSON.parseObject(trimmed);
                if (obj != null && !obj.isEmpty()) {
                    TreeMap<String, Object> sorted = new TreeMap<>();
                    for (Map.Entry<String, Object> entry : obj.entrySet()) {
                        String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
                        Object val = entry.getValue();
                        if (val instanceof String s) {
                            sorted.put(key, s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
                        } else {
                            sorted.put(key, val);
                        }
                    }
                    return JSON.toJSONString(sorted);
                }
            } catch (Exception ignored) {
            }
        }
        return trimmed.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeReqId(String reqId) {
        return (reqId == null || reqId.isBlank()) ? "req-speculative" : reqId;
    }
}
