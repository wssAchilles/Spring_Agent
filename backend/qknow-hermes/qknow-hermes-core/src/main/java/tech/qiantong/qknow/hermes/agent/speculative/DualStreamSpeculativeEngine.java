package tech.qiantong.qknow.hermes.agent.speculative;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * DeepSeek 思考链引导的双流推测执行与前向预取引擎 (Dual-Stream Speculative Engine)
 * 升级：集成阿里千问 1536 维超球面置信度硬门禁与拒绝采样防注意力污染机制
 * 1. System 1 (本地轻量推测器)：基于本地规则/堆外索引在 <5ms 内生成候选工具草稿 (Draft Candidates)；
 * 2. System 2 (DeepSeek 思考慢流)：流式接收 reasoning_content 并由 ThinkingPatternDfa 动态识别前向决策意图；
 * 3. 测地置信度门禁过滤：在草稿注入前，通过 ConfidenceGatedPrefetchFilter (cos theta >= 0.85) 剔除假阳性伪相关草稿；
 * 4. 前向异步预取：通过门禁的草稿进入待命池，未通过的平滑存入 L3 冷备环形仓，实现注意力纯净与 TTFT 亚秒级降低；
 * 5. 签发带不可变 SHA-256 签名的审计凭单。
 */
public class DualStreamSpeculativeEngine {

    public record ExecutionResult(
            Object output,
            boolean speculativeCacheHit,
            SpeculativeDraftPrefetchReceipt receipt,
            ConfidenceGatedPrefetchFilter.GatedPrefetchDecisionReceipt gateReceipt
    ) {
        public ExecutionResult(Object output, boolean speculativeCacheHit, SpeculativeDraftPrefetchReceipt receipt) {
            this(output, speculativeCacheHit, receipt, null);
        }
    }

    private final String secretKey;
    private final Map<String, CompletableFuture<Object>> standbyPrefetchPool = new ConcurrentHashMap<>();
    private final Map<String, Function<String, Object>> registeredPrefetchFetchers = new ConcurrentHashMap<>();
    private final ThinkingPatternDfa patternDfa = new ThinkingPatternDfa();
    private final List<String> system1DraftRules = new CopyOnWriteArrayList<>();
    private ConfidenceGatedPrefetchFilter gatedFilter;

    private int totalPrefetchTriggered = 0;
    private int totalCacheHits = 0;
    private int totalGatedRejected = 0;

    public DualStreamSpeculativeEngine() {
        this("DEFAULT_SPECULATIVE_SECRET_" + System.currentTimeMillis());
    }

    public DualStreamSpeculativeEngine(String secretKey) {
        this(secretKey, new ConfidenceGatedPrefetchFilter(0.85, secretKey));
    }

    public DualStreamSpeculativeEngine(String secretKey, ConfidenceGatedPrefetchFilter gatedFilter) {
        this.secretKey = secretKey == null ? "DEFAULT_SPEC_KEY" : secretKey;
        this.gatedFilter = gatedFilter != null ? gatedFilter : new ConfidenceGatedPrefetchFilter(0.85, this.secretKey);
        initDefaultDfaPatterns();
    }

    private void initDefaultDfaPatterns() {
        // 模式 1：识别思考链中的查表意图
        patternDfa.registerRule("DB_QUERY", "(?:(?:正在)?查询数据库|检索表|query_table)[:：\\s]+([a-zA-Z0-9_]+)", table -> {
            triggerAsyncPrefetch("db:" + table, table);
        });

        // 模式 2：识别思考链中的文档检索意图
        patternDfa.registerRule("DOC_RETRIEVE", "(?:(?:正在)?检索文档|读取知识库|retrieve_doc)[:：\\s]+([a-zA-Z0-9_]+)", docId -> {
            triggerAsyncPrefetch("doc:" + docId, docId);
        });

        // 模式 3：识别思考链中的工具调用意图
        patternDfa.registerRule("TOOL_INVOKE", "(?:(?:正在)?调用工具|执行函数|call_tool)[:：\\s]+([a-zA-Z0-9_]+)", toolName -> {
            triggerAsyncPrefetch("tool:" + toolName, toolName);
        });
    }

    public void setGatedPrefetchFilter(ConfidenceGatedPrefetchFilter filter) {
        this.gatedFilter = Objects.requireNonNull(filter, "filter cannot be null");
    }

    public ConfidenceGatedPrefetchFilter getGatedPrefetchFilter() {
        return this.gatedFilter;
    }

    public void registerPrefetchFetcher(String category, Function<String, Object> fetcher) {
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(fetcher, "fetcher cannot be null");
        registeredPrefetchFetchers.put(category, fetcher);
    }

    public void registerSystem1DraftRule(String keyword, String targetPrefetchKey) {
        system1DraftRules.add(keyword + "->" + targetPrefetchKey);
    }

    /**
     * System 1 本地轻量推测：根据用户 Query 在极短时间内预测候选预取项
     */
    public List<String> generateSystem1DraftCandidates(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        List<String> drafts = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (String rule : system1DraftRules) {
            String[] parts = rule.split("->");
            if (parts.length == 2 && lowerQuery.contains(parts[0].toLowerCase())) {
                drafts.add(parts[1]);
                triggerAsyncPrefetch(parts[1], parts[1]);
            }
        }
        return drafts;
    }

    /**
     * System 2 流式喂入 DeepSeek 思考流 chunk，实时由 DFA 触发前向预取
     */
    public int feedThinkingChunk(String chunk) {
        return patternDfa.feedChunk(chunk);
    }

    /**
     * 带超球面置信度门禁的推测预取触发
     * 只有当余弦相似度 >= 门禁阈值时才真正启动预取并放入待命池；否则拒绝采样存入冷备
     */
    public ConfidenceGatedPrefetchFilter.GateEvaluationResult triggerGatedPrefetch(
            String prefetchKey,
            String param,
            float[] targetEmbedding,
            float[] draftEmbedding
    ) {
        Objects.requireNonNull(prefetchKey, "prefetchKey cannot be null");
        if (gatedFilter == null) {
            triggerAsyncPrefetch(prefetchKey, param);
            return null;
        }

        ConfidenceGatedPrefetchFilter.GateEvaluationResult eval =
                gatedFilter.evaluateDraft(prefetchKey, param != null ? param : prefetchKey, targetEmbedding, draftEmbedding);

        if (eval.accepted()) {
            triggerAsyncPrefetch(prefetchKey, param);
        } else {
            totalGatedRejected++;
        }
        return eval;
    }

    public synchronized void triggerAsyncPrefetch(String prefetchKey, String param) {
        if (standbyPrefetchPool.containsKey(prefetchKey)) {
            return;
        }

        totalPrefetchTriggered++;
        String category = prefetchKey.contains(":") ? prefetchKey.substring(0, prefetchKey.indexOf(":")) : "default";
        Function<String, Object> fetcher = registeredPrefetchFetchers.getOrDefault(category,
                k -> "PREFETCHED_MOCK_DATA_FOR[" + k + "]");

        // 异步通过虚拟线程执行预取
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> fetcher.apply(param));
        standbyPrefetchPool.put(prefetchKey, future);
    }

    /**
     * 当 DeepSeek 最终决策确定调用时，解析并优先命中推测待命池
     */
    public ExecutionResult resolveExecution(String requestId, String targetKey, Function<String, Object> fallbackExecutor) {
        return resolveExecutionWithGate(requestId, targetKey, null, null, fallbackExecutor);
    }

    /**
     * 具备双向置信度门禁检验的执行解析
     */
    public ExecutionResult resolveExecutionWithGate(
            String requestId,
            String targetKey,
            float[] targetEmbedding,
            float[] candidateEmbedding,
            Function<String, Object> fallbackExecutor
    ) {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(targetKey, "targetKey cannot be null");

        long startNanos = System.nanoTime();
        long timestamp = System.currentTimeMillis();
        String receiptId = "spec_rcpt_" + UUID.randomUUID().toString().replace("-", "");

        ConfidenceGatedPrefetchFilter.GatedPrefetchDecisionReceipt gateReceipt = null;
        boolean gatePassed = true;

        // 如果传入了向量，进行决策期二次门禁验真
        if (gatedFilter != null && targetEmbedding != null && candidateEmbedding != null) {
            ConfidenceGatedPrefetchFilter.GateEvaluationResult gateEval =
                    gatedFilter.evaluateDraft(targetKey, "resolve_validation", targetEmbedding, candidateEmbedding);
            gateReceipt = gateEval.receipt();
            gatePassed = gateEval.accepted();
            if (!gatePassed) {
                // 门禁拒绝：假阳性命中，主动驱逐待命池并转入冷备，防止注意力污染
                standbyPrefetchPool.remove(targetKey);
            }
        }

        CompletableFuture<Object> standbyFuture = gatePassed ? standbyPrefetchPool.get(targetKey) : null;
        boolean hit = false;
        Object result = null;

        if (standbyFuture != null) {
            try {
                result = standbyFuture.get(100, TimeUnit.MILLISECONDS);
                hit = true;
                totalCacheHits++;
            } catch (Exception e) {
                hit = false;
            }
        }

        if (!hit) {
            result = fallbackExecutor != null ? fallbackExecutor.apply(targetKey) : null;
        }

        long latencyMicros = Math.max(1, (System.nanoTime() - startNanos) / 1000);
        String sig = SpeculativeDraftPrefetchReceipt.calculateSignature(
                receiptId, requestId, system1DraftRules.size(), totalPrefetchTriggered,
                hit ? 1 : 0, latencyMicros, timestamp, secretKey);

        SpeculativeDraftPrefetchReceipt receipt = new SpeculativeDraftPrefetchReceipt(
                receiptId, requestId, system1DraftRules.size(), totalPrefetchTriggered,
                hit ? 1 : 0, latencyMicros, timestamp, sig);

        return new ExecutionResult(result, hit, receipt, gateReceipt);
    }

    public synchronized void reset() {
        standbyPrefetchPool.clear();
        patternDfa.reset();
        if (gatedFilter != null) {
            gatedFilter.reset();
        }
        totalPrefetchTriggered = 0;
        totalCacheHits = 0;
        totalGatedRejected = 0;
    }

    public int getStandbyPoolSize() {
        return standbyPrefetchPool.size();
    }

    public int getTotalGatedRejected() {
        return totalGatedRejected;
    }
}
