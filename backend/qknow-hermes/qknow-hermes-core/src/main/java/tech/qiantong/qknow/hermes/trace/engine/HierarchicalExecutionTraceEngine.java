package tech.qiantong.qknow.hermes.trace.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.trace.model.HierarchicalTraceSpan;
import tech.qiantong.qknow.hermes.trace.model.TraceExecutionReceipt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 层次化轻量因果追踪引擎 (Hierarchical Execution Trace Engine)
 * <p>
 * 遵循 Phase 106 规范：
 * 1. 纯 Java 21 Record 树状拓扑流形；
 * 2. Lamport 逻辑时钟单调强制对齐：T_start(child) >= T_start(parent)，彻底消除因果倒挂；
 * 3. 定长 20 步环形 Trace 池 (RingBufferTracePool)，老旧 Trace 自动淘汰，保障离散李雅普诺夫内存有界；
 * 4. CPM (Critical Path Method) 关键路径拓扑推演与长尾性能定位；
 * 5. 统筹签发 SHA-256 密码学防篡改不可变存证凭单。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class HierarchicalExecutionTraceEngine {

    private static final Logger log = LoggerFactory.getLogger(HierarchicalExecutionTraceEngine.class);

    /**
     * 定长环形 Trace 池容量 (M = 20)
     */
    public static final int RING_BUFFER_CAPACITY = 20;

    /**
     * 单个 Trace 内部活跃 Span 存储 (traceId -> (spanId -> Span))
     */
    private final Map<String, Map<String, HierarchicalTraceSpan>> traceStore = new ConcurrentHashMap<>();

    /**
     * 所属工作流映射 (traceId -> workflowId)
     */
    private final Map<String, String> traceWorkflowMap = new ConcurrentHashMap<>();

    /**
     * 定长 FIFO 队列维护已完成的 Trace ID 顺序，用于环形淘汰
     */
    private final ConcurrentLinkedDeque<String> traceOrderQueue = new ConcurrentLinkedDeque<>();

    /**
     * 开启新追踪调用链，返回根 Span
     */
    public HierarchicalTraceSpan startTrace(String traceId, String workflowId, String rootSpanName, String spanType, String summaryInput) {
        Objects.requireNonNull(traceId, "traceId 不能为空");
        Objects.requireNonNull(workflowId, "workflowId 不能为空");
        Objects.requireNonNull(rootSpanName, "rootSpanName 不能为空");

        // 环形缓冲淘汰检查
        evictIfNecessary(traceId);

        Map<String, HierarchicalTraceSpan> spanMap = traceStore.computeIfAbsent(traceId, k -> new ConcurrentHashMap<>());
        traceWorkflowMap.put(traceId, workflowId);

        String rootSpanId = "span-" + UUID.randomUUID().toString().substring(0, 8);
        long nowNano = System.nanoTime();

        HierarchicalTraceSpan rootSpan = new HierarchicalTraceSpan(
                rootSpanId,
                traceId,
                null,
                rootSpanName,
                spanType != null ? spanType : "AGENT_ROOT",
                nowNano,
                0L,
                0,
                "RUNNING",
                summaryInput,
                "",
                Collections.emptyMap()
        );

        spanMap.put(rootSpanId, rootSpan);
        return rootSpan;
    }

    /**
     * 在指定父 Span 下派生子 Span (Lamport 逻辑时钟单调保序)
     */
    public HierarchicalTraceSpan startChildSpan(String traceId, String parentSpanId, String spanName, String spanType, String summaryInput) {
        Objects.requireNonNull(traceId, "traceId 不能为空");
        Objects.requireNonNull(parentSpanId, "parentSpanId 不能为空");
        Objects.requireNonNull(spanName, "spanName 不能为空");

        Map<String, HierarchicalTraceSpan> spanMap = traceStore.get(traceId);
        if (spanMap == null) {
            throw new IllegalArgumentException("Trace 不存在: " + traceId);
        }

        HierarchicalTraceSpan parentSpan = spanMap.get(parentSpanId);
        long childStartNano = System.nanoTime();

        // Lamport 因果单调性强制对齐: 子节点起始时间绝不能早于父节点起始时间
        if (parentSpan != null && childStartNano < parentSpan.startNano()) {
            childStartNano = parentSpan.startNano() + 1000L; // 至少偏序超前 1us
        }

        String childSpanId = "span-" + UUID.randomUUID().toString().substring(0, 8);
        HierarchicalTraceSpan childSpan = new HierarchicalTraceSpan(
                childSpanId,
                traceId,
                parentSpanId,
                spanName,
                spanType != null ? spanType : "STEP_EXECUTION",
                childStartNano,
                0L,
                0,
                "RUNNING",
                summaryInput,
                "",
                Collections.emptyMap()
        );

        spanMap.put(childSpanId, childSpan);
        return childSpan;
    }

    /**
     * 结束指定 Span 并结算耗时、Token 与状态
     */
    public HierarchicalTraceSpan endSpan(String traceId, String spanId, String status, int tokenCount, String summaryOutput) {
        Map<String, HierarchicalTraceSpan> spanMap = traceStore.get(traceId);
        if (spanMap == null) {
            return null;
        }

        HierarchicalTraceSpan current = spanMap.get(spanId);
        if (current == null) {
            return null;
        }

        long endNano = System.nanoTime();
        long durationUs = Math.max(1L, (endNano - current.startNano()) / 1000L);

        HierarchicalTraceSpan completedSpan = new HierarchicalTraceSpan(
                current.spanId(),
                current.traceId(),
                current.parentSpanId(),
                current.spanName(),
                current.spanType(),
                current.startNano(),
                durationUs,
                tokenCount,
                status != null ? status : "SUCCESS",
                current.summaryInput(),
                summaryOutput,
                current.attributes()
        );

        spanMap.put(spanId, completedSpan);
        return completedSpan;
    }

    /**
     * 结束整个 Trace 并结算签发密码学存证凭单
     */
    public TraceExecutionReceipt endTrace(String traceId) {
        Map<String, HierarchicalTraceSpan> spanMap = traceStore.get(traceId);
        if (spanMap == null || spanMap.isEmpty()) {
            return null;
        }

        String workflowId = traceWorkflowMap.getOrDefault(traceId, "unknown_wf");

        int totalSpans = spanMap.size();
        int totalTokens = 0;
        int errorCount = 0;
        long rootDurationUs = 0L;

        // 寻找根节点并统计指标
        HierarchicalTraceSpan rootSpan = null;
        for (HierarchicalTraceSpan span : spanMap.values()) {
            totalTokens += span.tokenCount();
            if ("FAILED".equalsIgnoreCase(span.status())) {
                errorCount++;
            }
            if (span.parentSpanId() == null) {
                rootSpan = span;
                rootDurationUs = span.durationUs();
            }
        }

        // 计算 CPM 关键路径耗时
        long criticalPathUs = calculateCriticalPathDuration(spanMap, rootSpan);

        String receiptId = "RCPT-TRACE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long nowMs = System.currentTimeMillis();

        return TraceExecutionReceipt.create(
                receiptId,
                traceId,
                workflowId,
                totalSpans,
                rootDurationUs,
                criticalPathUs,
                totalTokens,
                errorCount,
                nowMs
        );
    }

    /**
     * 关键路径拓扑遍历推演 (CPM Critical Path)
     */
    private long calculateCriticalPathDuration(Map<String, HierarchicalTraceSpan> spanMap, HierarchicalTraceSpan rootSpan) {
        if (rootSpan == null) {
            return 0L;
        }

        // 构建父子邻接树
        Map<String, List<HierarchicalTraceSpan>> childrenMap = new HashMap<>();
        for (HierarchicalTraceSpan span : spanMap.values()) {
            if (span.parentSpanId() != null) {
                childrenMap.computeIfAbsent(span.parentSpanId(), k -> new ArrayList<>()).add(span);
            }
        }

        return dfsMaxPath(rootSpan.spanId(), childrenMap, spanMap);
    }

    private long dfsMaxPath(String currentId, Map<String, List<HierarchicalTraceSpan>> childrenMap, Map<String, HierarchicalTraceSpan> spanMap) {
        HierarchicalTraceSpan current = spanMap.get(currentId);
        long currentSelfDuration = current != null ? current.durationUs() : 0L;

        List<HierarchicalTraceSpan> children = childrenMap.get(currentId);
        if (children == null || children.isEmpty()) {
            return currentSelfDuration;
        }

        long maxChildBranch = 0L;
        for (HierarchicalTraceSpan child : children) {
            long childBranch = dfsMaxPath(child.spanId(), childrenMap, spanMap);
            if (childBranch > maxChildBranch) {
                maxChildBranch = childBranch;
            }
        }

        return currentSelfDuration + maxChildBranch;
    }

    /**
     * 定长环形淘汰保护 (容量 M = 20)
     */
    private synchronized void evictIfNecessary(String newTraceId) {
        if (!traceOrderQueue.contains(newTraceId)) {
            traceOrderQueue.offer(newTraceId);
        }

        while (traceOrderQueue.size() > RING_BUFFER_CAPACITY) {
            String oldestTraceId = traceOrderQueue.poll();
            if (oldestTraceId != null) {
                traceStore.remove(oldestTraceId);
                traceWorkflowMap.remove(oldestTraceId);
                log.debug("TraceRingBuffer: 自动淘汰最老 Trace [{}] 以保障内存有界", oldestTraceId);
            }
        }
    }

    public List<HierarchicalTraceSpan> getTraceSpans(String traceId) {
        Map<String, HierarchicalTraceSpan> spanMap = traceStore.get(traceId);
        if (spanMap == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(spanMap.values());
    }

    public int getActiveTraceCount() {
        return traceStore.size();
    }
}
