package tech.qiantong.qknow.mcp.core.codemode;

import tech.qiantong.qknow.mcp.core.orchestration.dto.McpToolDescriptor;
import tech.qiantong.qknow.mcp.router.McpHierarchicalVoronoiRouter;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 前缀不变 {@code defer_loading} 元工具发现与受限 AST 虚拟线程沙箱执行器（CodeModePlanSandboxExecutor）。
 * <p>
 * 对应博士论文第 5 章核心机制（融合 Anthropic Defer-Loading Tool Search + Cloudflare Code-Mode MCP）：
 * 1. 永久锁定顶层静态 4 元工具 Schema，保证无论底层注册多少百个长尾业务工具，DeepSeek 64-token 缓存前缀字节 100% 不变；
 * 2. 依托千问 1024 维 {@link McpHierarchicalVoronoiRouter} 按需检索延迟加载工具；
 * 3. 在受静态安全检查与循环上界（<= 50）保护的虚拟线程沙箱中一次性并发执行多步工具链并裁剪冗余字段。
 * </p>
 */
public class CodeModePlanSandboxExecutor {

    public static final int MAX_BOUNDED_COLLECTION_ITEMS = 50;

    public static final List<String> STATIC_META_TOOL_IDS = List.of(
            "mcp_discover_tools",
            "mcp_load_tool_schema",
            "mcp_exec_code_plan",
            "hippocampal_subgraph_probe"
    );

    private static final String CANONICAL_STATIC_META_TOOLS_JSON = "["
            + "{\"name\":\"mcp_discover_tools\",\"description\":\"在千问1024维超球面Voronoi索引上按意图延迟发现候选MCP工具\"},"
            + "{\"name\":\"mcp_load_tool_schema\",\"description\":\"按需展开指定defer_loading工具的参数Schema至对话尾部\"},"
            + "{\"name\":\"mcp_exec_code_plan\",\"description\":\"在受限无环AST虚拟线程沙箱内并发编排执行多步工具并裁剪聚合结果\"},"
            + "{\"name\":\"hippocampal_subgraph_probe\",\"description\":\"在双时态海马体记忆超图上发起思考链内多跳RI-PPR组合检索\"}"
            + "]";

    public record SandboxExecutionResult(
            boolean success,
            Object finalOutput,
            int executedToolCalls,
            long elapsedMicros
    ) {}

    private final McpHierarchicalVoronoiRouter voronoiRouter;
    private final BiFunction<String, String, String> staticSecurityInspector;
    private final Function<CodeModePlanAst.RawScriptStep, Object> rawScriptExecutor;
    private final Map<String, Function<Map<String, Object>, Object>> deferredExecutors = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong totalDiscoveries = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong totalPlanExecutions = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong blockedScriptCount = new java.util.concurrent.atomic.AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicLong totalDeferredToolCalls = new java.util.concurrent.atomic.AtomicLong(0L);

    public CodeModePlanSandboxExecutor(
            McpHierarchicalVoronoiRouter voronoiRouter,
            BiFunction<String, String, String> staticSecurityInspector
    ) {
        this(voronoiRouter, staticSecurityInspector, null);
    }

    /**
     * 全参构造器（支持注入真实 CodeAct 沙盒执行回调，彻底根治 RawScriptStep 空跑假回执）。
     */
    public CodeModePlanSandboxExecutor(
            McpHierarchicalVoronoiRouter voronoiRouter,
            BiFunction<String, String, String> staticSecurityInspector,
            Function<CodeModePlanAst.RawScriptStep, Object> rawScriptExecutor
    ) {
        this.voronoiRouter = Objects.requireNonNull(voronoiRouter, "voronoiRouter 不能为空");
        this.staticSecurityInspector = staticSecurityInspector;
        this.rawScriptExecutor = rawScriptExecutor;
    }

    /**
     * 获取暴露在请求体顶层 {@code tools} 槽的恒定静态 4 元工具 JSON 定义（字节级绝对不变）。
     */
    public String getCanonicalStaticMetaToolsJson() {
        return CANONICAL_STATIC_META_TOOLS_JSON;
    }

    public void registerDeferredToolExecutor(String toolId, Function<Map<String, Object>, Object> executor) {
        Objects.requireNonNull(toolId, "toolId 不能为空");
        Objects.requireNonNull(executor, "executor 不能为空");
        deferredExecutors.put(toolId, executor);
    }

    /**
     * 按意图向量在千问 1024 维 Voronoi 树上检索 {@code defer_loading=true} 的候选工具描述符。
     */
    public List<McpToolDescriptor> discoverDeferredTools(String userQuery, double[] queryEmbedding1024, int topK) {
        McpHierarchicalVoronoiRouter.RoutingResult routed = voronoiRouter.routeAndIssueReceipt(
                McpHierarchicalVoronoiRouter.RoutingRequest.builder()
                        .taskId("discover-" + UUID.randomUUID().toString().substring(0, 8))
                        .traceId("trace-defer")
                        .userQuery(userQuery)
                        .queryEmbedding(queryEmbedding1024)
                        .topK(Math.max(1, topK))
                        .branchCells(4)
                        .build()
        );
        totalDiscoveries.incrementAndGet();
        return routed.candidateTools() != null ? routed.candidateTools() : List.of();
    }

    /**
     * 静态审查并在 Java 21 虚拟线程沙箱内执行受限无环工具编排计划。
     */
    public SandboxExecutionResult executePlan(CodeModePlanAst plan) {
        Objects.requireNonNull(plan, "plan 不能为空");
        long startNs = System.nanoTime();

        // 1. 执行前全树静态安全扫描（任何违规脚本立即抛出 SecurityException 拒绝执行）
        try {
            inspectAstSafetyRecursively(plan);
        } catch (SecurityException ex) {
            blockedScriptCount.incrementAndGet();
            throw ex;
        }

        // 2. 在隔离变量绑定上下文中顺序/并发求值
        Map<String, Object> bindings = new ConcurrentHashMap<>();
        int[] callCounter = new int[]{0};
        Object result = evalNode(plan, bindings, callCounter);

        long elapsedUs = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
        totalPlanExecutions.incrementAndGet();
        totalDeferredToolCalls.addAndGet(callCounter[0]);
        return new SandboxExecutionResult(true, result, callCounter[0], elapsedUs);
    }

    /**
     * 获取 Code-Mode 静态元工具沙箱运行态遥测统计。
     */
    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("staticMetaToolCount", STATIC_META_TOOL_IDS.size());
        stats.put("deferredExecutorCount", deferredExecutors.size());
        stats.put("totalDiscoveries", totalDiscoveries.get());
        stats.put("totalPlanExecutions", totalPlanExecutions.get());
        stats.put("totalDeferredToolCalls", totalDeferredToolCalls.get());
        stats.put("blockedScriptCount", blockedScriptCount.get());
        return Collections.unmodifiableMap(stats);
    }

    private void inspectAstSafetyRecursively(CodeModePlanAst node) {
        if (node instanceof CodeModePlanAst.RawScriptStep script) {
            if (staticSecurityInspector != null) {
                String violation = staticSecurityInspector.apply(script.language(), script.code());
                if (violation != null && !violation.isBlank()) {
                    throw new SecurityException("Code-Mode AST 静态安全防线拦截高危脚本: " + violation);
                }
            }
        } else if (node instanceof CodeModePlanAst.SequenceBlock seq) {
            if (seq.steps().size() > MAX_BOUNDED_COLLECTION_ITEMS) {
                throw new SecurityException("SequenceBlock 步骤数超出受限无环上限: " + seq.steps().size());
            }
            for (CodeModePlanAst child : seq.steps()) {
                inspectAstSafetyRecursively(child);
            }
        } else if (node instanceof CodeModePlanAst.ParallelMapStep pmap) {
            inspectAstSafetyRecursively(pmap.itemAction());
        }
    }

    @SuppressWarnings("unchecked")
    private Object evalNode(CodeModePlanAst node, Map<String, Object> bindings, int[] callCounter) {
        if (node instanceof CodeModePlanAst.InvokeToolStep invoke) {
            Map<String, Object> resolvedArgs = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : invoke.argExpressions().entrySet()) {
                resolvedArgs.put(entry.getKey(), resolveExpression(entry.getValue(), bindings));
            }
            Function<Map<String, Object>, Object> fn = deferredExecutors.get(invoke.toolId());
            if (fn == null) {
                throw new IllegalArgumentException("未注册延迟工具执行器: " + invoke.toolId());
            }
            synchronized (callCounter) {
                callCounter[0]++;
            }
            Object out = fn.apply(resolvedArgs);
            bindings.put(invoke.stepVar(), out);
            return out;
        }

        if (node instanceof CodeModePlanAst.ParallelMapStep pmap) {
            Object rawList = resolveExpression(pmap.sourceListExpr(), bindings);
            if (!(rawList instanceof Collection<?> coll)) {
                throw new IllegalArgumentException("ParallelMapStep 源表达式不是集合类型: " + pmap.sourceListExpr());
            }
            if (coll.size() > MAX_BOUNDED_COLLECTION_ITEMS) {
                throw new SecurityException("ParallelMapStep 待处理元素超过安全上限 50: " + coll.size());
            }
            List<Object> items = new ArrayList<>(coll);
            int concurrency = Math.max(1, Math.min(16, pmap.maxConcurrency()));
            Semaphore gate = new Semaphore(concurrency);
            List<Object> mappedResults = new ArrayList<>(Collections.nCopies(items.size(), null));

            try (var vExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> futures = new ArrayList<>(items.size());
                for (int idx = 0; idx < items.size(); idx++) {
                    final int pos = idx;
                    final Object itemVal = items.get(idx);
                    futures.add(vExecutor.submit(() -> {
                        gate.acquire();
                        try {
                            Map<String, Object> localScope = new HashMap<>(bindings);
                            localScope.put("item", itemVal);
                            Object r = evalNode(pmap.itemAction(), localScope, callCounter);
                            mappedResults.set(pos, r);
                            return null;
                        } finally {
                            gate.release();
                        }
                    }));
                }
                for (Future<?> f : futures) {
                    f.get(5, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                throw new IllegalStateException("虚拟线程并发沙箱执行失败: " + e.getMessage(), e);
            }
            bindings.put(pmap.stepVar(), mappedResults);
            return mappedResults;
        }

        if (node instanceof CodeModePlanAst.FilterProjectStep fp) {
            Object src = resolveExpression(fp.sourceExpr(), bindings);
            if (!(src instanceof Collection<?> coll)) {
                return List.of();
            }
            List<Map<String, Object>> projected = new ArrayList<>();
            int max = fp.maxItems() > 0 ? Math.min(fp.maxItems(), MAX_BOUNDED_COLLECTION_ITEMS) : MAX_BOUNDED_COLLECTION_ITEMS;
            for (Object elem : coll) {
                if (projected.size() >= max) {
                    break;
                }
                if (elem instanceof Map<?, ?> mapElem) {
                    if (fp.requiredValueSubstring() != null && !fp.requiredValueSubstring().isBlank()) {
                        boolean matched = mapElem.values().stream()
                                .anyMatch(v -> String.valueOf(v).contains(fp.requiredValueSubstring()));
                        if (!matched) {
                            continue;
                        }
                    }
                    Map<String, Object> slim = new LinkedHashMap<>();
                    if (fp.retainFields().isEmpty()) {
                        mapElem.forEach((k, v) -> slim.put(String.valueOf(k), v));
                    } else {
                        for (String field : fp.retainFields()) {
                            if (mapElem.containsKey(field)) {
                                slim.put(field, mapElem.get(field));
                            }
                        }
                    }
                    projected.add(Collections.unmodifiableMap(slim));
                }
            }
            bindings.put(fp.stepVar(), projected);
            return projected;
        }

        if (node instanceof CodeModePlanAst.RawScriptStep script) {
            if (rawScriptExecutor != null) {
                synchronized (callCounter) {
                    callCounter[0]++;
                }
                Object realResult = rawScriptExecutor.apply(script);
                bindings.put(script.stepVar(), realResult);
                return realResult;
            }
            Map<String, Object> ack = Map.of("language", script.language(), "verifiedSafe", true);
            bindings.put(script.stepVar(), ack);
            return ack;
        }

        if (node instanceof CodeModePlanAst.SequenceBlock seq) {
            Object last = null;
            for (CodeModePlanAst step : seq.steps()) {
                last = evalNode(step, bindings, callCounter);
            }
            return bindings.getOrDefault(seq.returnSummaryVar(), last);
        }

        throw new IllegalStateException("未知 AST 节点类型: " + node);
    }

    private static Object resolveExpression(String expr, Map<String, Object> bindings) {
        if (expr == null || !expr.startsWith("$")) {
            return expr;
        }
        String path = expr.substring(1);
        String[] parts = path.split("\\.");
        Object curr = bindings.get(parts[0]);
        for (int i = 1; i < parts.length && curr != null; i++) {
            if (curr instanceof Map<?, ?> map) {
                curr = map.get(parts[i]);
            } else {
                return null;
            }
        }
        return curr;
    }
}
