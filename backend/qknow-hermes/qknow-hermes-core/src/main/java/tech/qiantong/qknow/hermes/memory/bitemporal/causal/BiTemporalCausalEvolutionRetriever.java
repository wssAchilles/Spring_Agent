package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHyperEdge;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalProbeReceipt;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 双时态因果演化联合检索器（Phase 8.1 核心创新组件）。
 * <p>
 * 统合以下三项能力解决 LongMemEval 与跨期多跳推理失配：
 * 1. 通过 {@link TemporalAnchorQueryParser} 解除查询端时间盲视，精确映射二维截面/演化窗；
 * 2. 在千问 {@code S^1023} 单位超球面上结合 Rust FFM CSR-PPR 粗召回、时间半衰期衰减核与
 *    时间戳升序 Kahn 拓扑投影，还原严格因果演化链（含含环安全破环兜底）；
 * 3. 输出标准化 {@link ReadOnlyDatasetView} 与 {@link BiTemporalCausalRetrievalReceipt} 凭单。
 * </p>
 */
public class BiTemporalCausalEvolutionRetriever {

    private final BiTemporalHippocampalGraphEngine graphEngine;
    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final TemporalAnchorQueryParser anchorParser;
    private final String hmacSecretKey;
    private final AtomicLong totalRetrievals = new AtomicLong(0L);
    private final AtomicLong temporalAnchorBoundedRetrievals = new AtomicLong(0L);
    private final AtomicLong totalSupersededFiltered = new AtomicLong(0L);
    private final AtomicLong totalCausalStepsReturned = new AtomicLong(0L);

    public BiTemporalCausalEvolutionRetriever(
            BiTemporalHippocampalGraphEngine graphEngine,
            UnitSphereEmbeddingResolver embeddingResolver,
            TemporalAnchorQueryParser anchorParser
    ) {
        this(graphEngine, embeddingResolver, anchorParser, "hermes-bitemporal-causal-secret");
    }

    public BiTemporalCausalEvolutionRetriever(
            BiTemporalHippocampalGraphEngine graphEngine,
            UnitSphereEmbeddingResolver embeddingResolver,
            TemporalAnchorQueryParser anchorParser,
            String hmacSecretKey
    ) {
        this.graphEngine = Objects.requireNonNull(graphEngine, "graphEngine 不能为空");
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
        this.anchorParser = anchorParser != null ? anchorParser : new TemporalAnchorQueryParser();
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-bitemporal-causal-secret"
                : hmacSecretKey;
    }

    /**
     * 基于自然语言查询自动解析双时态锚点并执行因果演化检索。
     */
    public BiTemporalCausalRetrievalReceipt retrieve(
            String queryId,
            String queryText,
            long referenceNowMs,
            int topK
    ) {
        TemporalAnchorSpec anchor = anchorParser.parse(queryText, referenceNowMs);
        return retrieveAt(queryId, queryText, anchor, topK);
    }

    /**
     * 在显式给定的 {@link TemporalAnchorSpec} 上执行双时态因果演化检索。
     */
    public BiTemporalCausalRetrievalReceipt retrieveAt(
            String queryId,
            String queryText,
            TemporalAnchorSpec anchor,
            int topK
    ) {
        long startNs = System.nanoTime();
        String safeQueryId = (queryId == null || queryId.isBlank())
                ? "btq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                : queryId;
        String safeQuery = queryText != null ? queryText : "";
        TemporalAnchorSpec safeAnchor = Objects.requireNonNull(anchor, "anchor 不能为空");
        int limit = Math.max(1, topK);

        float[] qVec = embeddingResolver.resolveUnit1024(safeQuery);
        long probeValidTime = safeAnchor.validToEpochMs();
        long probeTxTime = safeAnchor.txAtEpochMs();

        // 1. 执行双时态截面上的海马体 CSR-PPR 联想召回（同时获取 Error-Book 防错警示）
        HippocampalProbeReceipt pprReceipt = graphEngine.probeIterativeSubgraph(
                safeQueryId, safeQuery, qVec, probeValidTime, probeTxTime, limit
        );

        // 2. 在目标双时态窗口/截面上抽取活跃事实边（自动跳过不在此时间窗口的边，避免历史脏边挤占）
        List<BiTemporalHyperEdge> windowEdges = safeAnchor.mode() == TemporalAnchorSpec.QueryTemporalMode.EVOLUTION_WINDOW
                ? graphEngine.queryEvolutionWindow(safeAnchor.validFromEpochMs(), safeAnchor.validToEpochMs(), safeAnchor.txAtEpochMs())
                : graphEngine.queryCrossSectionAsOf(null, null, probeValidTime, probeTxTime);

        int supersededCount = countSupersededEdgesInWindow(windowEdges);

        // 3. 结合超球面余弦亲和与时间半衰期衰减核打分并选取 Top-K 候选项
        Map<String, Double> edgeScores = new HashMap<>(windowEdges.size() * 2);
        Set<String> recalledPassageIds = new HashSet<>(pprReceipt.recalledPassageIds());
        for (BiTemporalHyperEdge e : windowEdges) {
            double cos = cosine(qVec, e.unitEmbedding1024());
            double cosineAffinity = Math.max(0.0, 0.5 * (1.0 + cos));
            long ageMs = Math.max(0L, safeAnchor.validToEpochMs() - e.validFromEpochMs());
            double temporalDecay = safeAnchor.mode() == TemporalAnchorSpec.QueryTemporalMode.EVOLUTION_WINDOW
                    ? 1.0
                    : Math.pow(0.5, (double) ageMs / safeAnchor.halfLifeMs());
            double pprBoost = (recalledPassageIds.contains(e.sourceNodeId()) || recalledPassageIds.contains(e.targetNodeId()))
                    ? 0.25 : 0.0;
            double score = cosineAffinity * (0.35 + 0.65 * temporalDecay) + pprBoost;
            edgeScores.put(e.edgeId(), score);
        }

        List<BiTemporalHyperEdge> rankedCandidates = new ArrayList<>(windowEdges);
        rankedCandidates.sort((a, b) -> Double.compare(
                edgeScores.getOrDefault(b.edgeId(), 0.0),
                edgeScores.getOrDefault(a.edgeId(), 0.0)
        ));
        List<BiTemporalHyperEdge> topEdges = rankedCandidates.size() > limit
                ? new ArrayList<>(rankedCandidates.subList(0, limit))
                : rankedCandidates;

        // 4. 若当前边存在因果前驱仍在 windowEdges 中且未超限，纳入关联以保持因果链路完整
        topEdges = expandCausalPredecessorsIfPresent(topEdges, windowEdges, edgeScores, limit * 2);

        // 5. 时间戳升序优先队列的 Kahn 拓扑偏序投影（含破环安全兜底）
        List<BiTemporalHyperEdge> orderedEdges = kahnTemporalTopologicalSort(topEdges);

        // 6. 组装有序 CausalEvolutionStep 与不可变只读表 ReadOnlyDatasetView
        List<CausalEvolutionStep> steps = new ArrayList<>(orderedEdges.size());
        for (int i = 0; i < orderedEdges.size(); i++) {
            BiTemporalHyperEdge e = orderedEdges.get(i);
            String srcLabel = resolveVertexLabel(e.sourceNodeId());
            String tgtLabel = resolveVertexLabel(e.targetNodeId());
            String predEdgeId = graphEngine.getCausalPredecessor(e.edgeId());
            PredicateCardinality card = graphEngine.getCardinality(e.edgeId());
            double score = edgeScores.getOrDefault(e.edgeId(), 0.0);
            String prop = String.format(Locale.ROOT, "(%s) -[%s]-> (%s) [valid=%d..%s]",
                    srcLabel, e.relationPredicate(), tgtLabel,
                    e.validFromEpochMs(),
                    e.validToEpochMs() != null ? String.valueOf(e.validToEpochMs()) : "INF");
            steps.add(new CausalEvolutionStep(
                    i + 1,
                    e.edgeId(),
                    e.sourceNodeId(),
                    e.relationPredicate(),
                    e.targetNodeId(),
                    card,
                    e.validFromEpochMs(),
                    e.validToEpochMs(),
                    e.txFromEpochMs(),
                    predEdgeId,
                    score,
                    prop
            ));
        }

        String receiptId = "btcr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ReadOnlyDatasetView datasetView = ReadOnlyDatasetView.fromCausalSteps(
                "view_" + receiptId, safeAnchor, steps, supersededCount
        );
        long elapsedMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
        totalRetrievals.incrementAndGet();
        if (safeAnchor.mode() != TemporalAnchorSpec.QueryTemporalMode.LATEST
                || !safeAnchor.parsedTimeExpressions().isEmpty()) {
            temporalAnchorBoundedRetrievals.incrementAndGet();
        }
        totalSupersededFiltered.addAndGet(supersededCount);
        totalCausalStepsReturned.addAndGet(steps.size());

        return BiTemporalCausalRetrievalReceipt.createSigned(
                receiptId,
                safeQueryId,
                safeAnchor,
                pprReceipt,
                steps,
                datasetView,
                pprReceipt.recalledErrorBookWarnings(),
                elapsedMicros,
                hmacSecretKey
        );
    }

    /**
     * 输出供博士工作台监控消费的双时态因果演化检索遥测指标。
     */
    public Map<String, Object> getTelemetryStats() {
        return Map.of(
                "totalRetrievals", totalRetrievals.get(),
                "temporalAnchorBoundedRetrievals", temporalAnchorBoundedRetrievals.get(),
                "totalSupersededFiltered", totalSupersededFiltered.get(),
                "totalCausalStepsReturned", totalCausalStepsReturned.get()
        );
    }

    private List<BiTemporalHyperEdge> expandCausalPredecessorsIfPresent(
            List<BiTemporalHyperEdge> initial,
            List<BiTemporalHyperEdge> pool,
            Map<String, Double> scores,
            int hardCap
    ) {
        Map<String, BiTemporalHyperEdge> byId = new HashMap<>(pool.size() * 2);
        for (BiTemporalHyperEdge e : pool) {
            byId.put(e.edgeId(), e);
        }
        Map<String, BiTemporalHyperEdge> selected = new LinkedHashMap<>();
        for (BiTemporalHyperEdge e : initial) {
            selected.put(e.edgeId(), e);
        }
        List<BiTemporalHyperEdge> frontier = new ArrayList<>(initial);
        while (!frontier.isEmpty() && selected.size() < hardCap) {
            List<BiTemporalHyperEdge> next = new ArrayList<>();
            for (BiTemporalHyperEdge e : frontier) {
                String predId = graphEngine.getCausalPredecessor(e.edgeId());
                if (predId != null && !selected.containsKey(predId)) {
                    BiTemporalHyperEdge pred = byId.get(predId);
                    if (pred != null) {
                        selected.put(predId, pred);
                        scores.putIfAbsent(predId, 0.5);
                        next.add(pred);
                        if (selected.size() >= hardCap) {
                            break;
                        }
                    }
                }
            }
            frontier = next;
        }
        return new ArrayList<>(selected.values());
    }

    /**
     * 基于有效时间升序优先队列的 Kahn 拓扑排序（当存在因果前驱或首尾实体衔接形成偏序时严格保序；成环时破环推进）。
     */
    private List<BiTemporalHyperEdge> kahnTemporalTopologicalSort(List<BiTemporalHyperEdge> edges) {
        if (edges.size() <= 1) {
            return edges;
        }
        int n = edges.size();
        Map<String, Integer> idToIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            idToIndex.put(edges.get(i).edgeId(), i);
        }

        List<List<Integer>> adj = new ArrayList<>(n);
        int[] inDegree = new int[n];
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            BiTemporalHyperEdge curr = edges.get(i);
            // 显式因果前驱边约束: pred -> curr
            String predId = graphEngine.getCausalPredecessor(curr.edgeId());
            if (predId != null) {
                Integer predIdx = idToIndex.get(predId);
                if (predIdx != null && predIdx != i) {
                    adj.get(predIdx).add(i);
                    inDegree[i]++;
                }
            }
            // 同实体桥接且时间早者先于晚者的隐式因果衔接: e_a.target == e_b.source 且 validFrom(a) <= validFrom(b)
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                BiTemporalHyperEdge other = edges.get(j);
                if (curr.targetNodeId().equals(other.sourceNodeId())
                        && curr.validFromEpochMs() < other.validFromEpochMs()
                        && !adj.get(i).contains(j)) {
                    adj.get(i).add(j);
                    inDegree[j]++;
                }
            }
        }

        Comparator<Integer> byTimeThenIndex = Comparator
                .<Integer>comparingLong(idx -> edges.get(idx).validFromEpochMs())
                .thenComparingLong(idx -> edges.get(idx).txFromEpochMs())
                .thenComparingInt(idx -> idx);

        PriorityQueue<Integer> ready = new PriorityQueue<>(byTimeThenIndex);
        boolean[] visited = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (inDegree[i] == 0) {
                ready.offer(i);
            }
        }

        List<BiTemporalHyperEdge> ordered = new ArrayList<>(n);
        while (ordered.size() < n) {
            if (ready.isEmpty()) {
                // 破环兜底：存在人为有向环时挑选最早时间戳的未访问节点强制破环
                int bestBreakIdx = -1;
                for (int i = 0; i < n; i++) {
                    if (!visited[i] && (bestBreakIdx < 0 || byTimeThenIndex.compare(i, bestBreakIdx) < 0)) {
                        bestBreakIdx = i;
                    }
                }
                if (bestBreakIdx < 0) {
                    break;
                }
                inDegree[bestBreakIdx] = 0;
                ready.offer(bestBreakIdx);
            }
            int u = ready.poll();
            if (visited[u]) {
                continue;
            }
            visited[u] = true;
            ordered.add(edges.get(u));
            for (int v : adj.get(u)) {
                if (!visited[v]) {
                    inDegree[v] = Math.max(0, inDegree[v] - 1);
                    if (inDegree[v] == 0) {
                        ready.offer(v);
                    }
                }
            }
        }
        return ordered;
    }

    private int countSupersededEdgesInWindow(List<BiTemporalHyperEdge> activeEdges) {
        int count = 0;
        for (BiTemporalHyperEdge e : activeEdges) {
            if (!graphEngine.getSupersededEdgeIds(e.edgeId()).isEmpty() || e.validToEpochMs() != null) {
                count++;
            }
        }
        return count;
    }

    private String resolveVertexLabel(String nodeId) {
        return graphEngine.getVertex(nodeId).map(HippocampalVertex::content).orElse(nodeId);
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }
}
