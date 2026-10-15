package tech.qiantong.qknow.hermes.search.graphrag.causal;

import tech.qiantong.qknow.hermes.agent.speculative.ConfidenceGatedPrefetchFilter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 时间感知语义三元组因果知识图谱与 GraphRAG 深度融合引擎
 * (Temporal Causal GraphRAG Engine)
 * 遵循超球面余弦几何律、因果 DAG 拓扑偏序与冷备隔离工程铁律：
 * 1. 活跃三元组索引与实体倒排；
 * 2. 第一阶段：千问 1536 维超球面测地线内积粗筛 + 时间有效性半衰期衰减联合打分；
 * 3. 第二阶段：因果前向可达子图寻径 (限定 2 跳)，杜绝全局子图发散；
 * 4. 第三阶段：Kahn 算法拓扑偏序投影，生成严格保序的因果命题链，节点规模钳位 <= 16；
 * 5. 三级冷备环形仓软删除隔离，杜绝物理硬删除；
 * 6. 签发纯 Java 21 Record 格式的密码学自验真凭单 TemporalGraphRetrievalReceipt。
 */
public class TemporalCausalGraphRagEngine {

    public static final int VECTOR_DIMENSION = 1536;
    public static final int DEFAULT_TOP_SEEDS = 5;
    public static final int DEFAULT_MAX_PROPOSITION_NODES = 16;
    public static final int COLD_RING_CAPACITY = 50;

    // 活跃索引集合
    private final Map<String, TemporalCausalTriplet> tripletRegistry = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> entityToTripletIds = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> causalForwardEdges = new ConcurrentHashMap<>();

    // 三级冷备环形仓 (容量 50，软删除隔离防硬删除)
    private final Deque<TemporalCausalTriplet> coldRingBuffer = new ConcurrentLinkedDeque<>();
    private final String secretKey;

    private final AtomicInteger totalSearches = new AtomicInteger(0);

    public record RetrievalResult(
            List<TemporalCausalTriplet> matchedTriplets,
            List<String> propositions,
            TemporalGraphRetrievalReceipt receipt
    ) {}

    public TemporalCausalGraphRagEngine() {
        this("HERMES_TKG_ENGINE_SECRET_" + System.currentTimeMillis());
    }

    public TemporalCausalGraphRagEngine(String secretKey) {
        this.secretKey = secretKey == null ? "HERMES_TKG_DEFAULT_SECRET" : secretKey;
    }

    /**
     * 注册/更新时间感知因果三元组
     */
    public void registerTriplet(TemporalCausalTriplet triplet) {
        if (triplet == null) return;
        tripletRegistry.put(triplet.tripletId(), triplet);

        // 维护实体双向倒排索引
        entityToTripletIds.computeIfAbsent(triplet.subject(), k -> ConcurrentHashMap.newKeySet()).add(triplet.tripletId());
        entityToTripletIds.computeIfAbsent(triplet.object(), k -> ConcurrentHashMap.newKeySet()).add(triplet.tripletId());

        // 维护因果前向图边: 前驱 ID -> 后继三元组 ID
        if (triplet.causalPredecessorId() != null && !triplet.causalPredecessorId().isBlank()) {
            causalForwardEdges.computeIfAbsent(triplet.causalPredecessorId(), k -> ConcurrentHashMap.newKeySet())
                    .add(triplet.tripletId());
        }
    }

    public void registerTriplets(List<TemporalCausalTriplet> triplets) {
        if (triplets != null) {
            for (TemporalCausalTriplet t : triplets) {
                registerTriplet(t);
            }
        }
    }

    /**
     * 软删除与冷备隔离 (防物理硬删除)
     */
    public void softDeleteTriplet(String tripletId) {
        TemporalCausalTriplet removed = tripletRegistry.remove(tripletId);
        if (removed != null) {
            while (coldRingBuffer.size() >= COLD_RING_CAPACITY) {
                coldRingBuffer.pollFirst();
            }
            coldRingBuffer.addLast(removed);
        }
    }

    /**
     * 执行两阶段双轨融合因果检索
     *
     * @param queryText              查询语句
     * @param queryVector            千问 1536 维超球面单位向量
     * @param queryTimestampEpochMs  查询目标物理时间戳
     * @param topSeeds               第一阶段粗筛种子数 (默认 5)
     * @param maxPropositionNodes    命题节点上限 (默认 16)
     * @return 包含拓扑排序三元组、因果命题与不可变存证凭单的检索结果
     */
    public RetrievalResult retrieveCausalSubgraph(
            String queryText,
            float[] queryVector,
            long queryTimestampEpochMs,
            int topSeeds,
            int maxPropositionNodes
    ) {
        long startNano = System.nanoTime();
        totalSearches.incrementAndGet();

        if (tripletRegistry.isEmpty() || queryVector == null || queryVector.length != VECTOR_DIMENSION) {
            long latency = System.nanoTime() - startNano;
            TemporalGraphRetrievalReceipt emptyReceipt = TemporalGraphRetrievalReceipt.createSigned(
                    queryText != null ? queryText : "",
                    queryTimestampEpochMs,
                    0,
                    0,
                    List.of(),
                    0.0,
                    latency,
                    secretKey
            );
            return new RetrievalResult(List.of(), List.of(), emptyReceipt);
        }

        float[] normQuery = ConfidenceGatedPrefetchFilter.validateAndNormalize(queryVector);

        // 阶段 1：超球面测地线内积 + 时空半衰期衰减联合打分粗筛
        record ScoredTriplet(TemporalCausalTriplet triplet, double score) {}
        List<ScoredTriplet> candidatePool = new ArrayList<>();

        for (TemporalCausalTriplet t : tripletRegistry.values()) {
            double decay = t.validTimeWindow().computeDecayFactor(queryTimestampEpochMs);
            if (decay <= 0.0) {
                // 尚未生效或超期彻底失效的事实直接硬阻断
                continue;
            }

            double dot = computeDotProduct8Way(normQuery, t.tripletVector());
            double clamped = Math.max(-1.0, Math.min(1.0, dot));
            double cosSim = (clamped + 1.0) / 2.0; // 投影至 [0.0, 1.0] 非负空间
            double compositeScore = cosSim * decay * t.confidence();

            if (compositeScore >= 0.05) {
                candidatePool.add(new ScoredTriplet(t, compositeScore));
            }
        }

        candidatePool.sort((a, b) -> Double.compare(b.score(), a.score()));
        int safeSeeds = Math.min(topSeeds > 0 ? topSeeds : DEFAULT_TOP_SEEDS, candidatePool.size());
        List<TemporalCausalTriplet> seeds = candidatePool.subList(0, safeSeeds).stream()
                .map(ScoredTriplet::triplet)
                .toList();

        // 阶段 2：因果前向可达性遍历 (有界 2 跳扩散)
        Set<String> collectedTripletIds = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> depthMap = new HashMap<>();

        for (TemporalCausalTriplet seed : seeds) {
            collectedTripletIds.add(seed.tripletId());
            queue.add(seed.tripletId());
            depthMap.put(seed.tripletId(), 0);
        }

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            int depth = depthMap.getOrDefault(currId, 0);
            if (depth < 2) {
                Set<String> nextTriplets = causalForwardEdges.getOrDefault(currId, Collections.emptySet());
                for (String nextId : nextTriplets) {
                    if (!collectedTripletIds.contains(nextId) && tripletRegistry.containsKey(nextId)) {
                        collectedTripletIds.add(nextId);
                        depthMap.put(nextId, depth + 1);
                        queue.add(nextId);
                    }
                }
            }
        }

        // 阶段 3：Kahn 算法拓扑偏序投影
        int limit = maxPropositionNodes > 0 ? maxPropositionNodes : DEFAULT_MAX_PROPOSITION_NODES;
        List<TemporalCausalTriplet> subgraphTriplets = collectedTripletIds.stream()
                .map(tripletRegistry::get)
                .filter(Objects::nonNull)
                .toList();

        List<TemporalCausalTriplet> topoOrdered = orderTopologically(subgraphTriplets, limit);
        List<String> propositions = projectPropositions(topoOrdered);

        double avgSim = candidatePool.isEmpty() ? 0.0 :
                candidatePool.stream().limit(safeSeeds).mapToDouble(ScoredTriplet::score).average().orElse(0.0);
        long latencyNanos = System.nanoTime() - startNano;

        // 阶段 4：签发密码学不可变存证凭单
        TemporalGraphRetrievalReceipt receipt = TemporalGraphRetrievalReceipt.createSigned(
                queryText != null ? queryText : "",
                queryTimestampEpochMs,
                seeds.size(),
                topoOrdered.size(),
                propositions,
                avgSim,
                latencyNanos,
                secretKey
        );

        return new RetrievalResult(topoOrdered, propositions, receipt);
    }

    /**
     * 基于 Kahn 算法生成确定性因果拓扑偏序
     */
    private List<TemporalCausalTriplet> orderTopologically(List<TemporalCausalTriplet> list, int limit) {
        if (list.isEmpty()) return List.of();

        Map<String, TemporalCausalTriplet> map = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (TemporalCausalTriplet t : list) {
            map.put(t.tripletId(), t);
            inDegree.put(t.tripletId(), 0);
            adj.put(t.tripletId(), new ArrayList<>());
        }

        for (TemporalCausalTriplet t : list) {
            String pred = t.causalPredecessorId();
            if (pred != null && map.containsKey(pred)) {
                adj.get(pred).add(t.tripletId());
                inDegree.put(t.tripletId(), inDegree.get(t.tripletId()) + 1);
            }
        }

        // 优先队列：入度为 0 的节点按时间戳升序处理，保证历史因果在前
        PriorityQueue<TemporalCausalTriplet> zeroInQueue = new PriorityQueue<>(
                Comparator.comparingLong(TemporalCausalTriplet::timestampEpochMs)
        );

        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                zeroInQueue.add(map.get(entry.getKey()));
            }
        }

        List<TemporalCausalTriplet> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        while (!zeroInQueue.isEmpty() && result.size() < limit) {
            TemporalCausalTriplet curr = zeroInQueue.poll();
            visited.add(curr.tripletId());
            result.add(curr);

            for (String nextId : adj.get(curr.tripletId())) {
                inDegree.put(nextId, inDegree.get(nextId) - 1);
                if (inDegree.get(nextId) == 0) {
                    zeroInQueue.add(map.get(nextId));
                }
            }
        }

        // 环路破除兜底：防止循环依赖导致遗漏
        if (result.size() < Math.min(list.size(), limit)) {
            for (TemporalCausalTriplet t : list) {
                if (!visited.contains(t.tripletId()) && result.size() < limit) {
                    result.add(t);
                    visited.add(t.tripletId());
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * 投射结构化因果断言命题
     */
    private List<String> projectPropositions(List<TemporalCausalTriplet> ordered) {
        List<String> list = new ArrayList<>();
        int rank = 1;
        for (TemporalCausalTriplet t : ordered) {
            String prop = String.format(Locale.ROOT, "【因果阶 %d】(ID: %s) [%s] -(%s)-> [%s] (置信度: %.2f)",
                    rank++, t.tripletId(), t.subject(), t.predicate(), t.object(), t.confidence());
            if (t.causalPredecessorId() != null && !t.causalPredecessorId().isBlank()) {
                prop += " [前置诱因 ID: " + t.causalPredecessorId() + "]";
            }
            list.add(prop);
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 8 路循环展开向量点乘加速
     */
    private static double computeDotProduct8Way(float[] a, float[] b) {
        if (a == null || b == null) return 0.0;
        int length = Math.min(a.length, b.length);
        double sum0 = 0.0, sum1 = 0.0, sum2 = 0.0, sum3 = 0.0;
        double sum4 = 0.0, sum5 = 0.0, sum6 = 0.0, sum7 = 0.0;
        int limit = length - (length % 8);

        for (int i = 0; i < limit; i += 8) {
            sum0 += (double) a[i] * b[i];
            sum1 += (double) a[i + 1] * b[i + 1];
            sum2 += (double) a[i + 2] * b[i + 2];
            sum3 += (double) a[i + 3] * b[i + 3];
            sum4 += (double) a[i + 4] * b[i + 4];
            sum5 += (double) a[i + 5] * b[i + 5];
            sum6 += (double) a[i + 6] * b[i + 6];
            sum7 += (double) a[i + 7] * b[i + 7];
        }

        double total = (sum0 + sum1) + (sum2 + sum3) + (sum4 + sum5) + (sum6 + sum7);
        for (int i = limit; i < length; i++) {
            total += (double) a[i] * b[i];
        }
        return total;
    }

    public List<TemporalCausalTriplet> getColdBufferSnapshot() {
        return List.copyOf(coldRingBuffer);
    }

    public int getRegistrySize() {
        return tripletRegistry.size();
    }

    public int getTotalSearches() {
        return totalSearches.get();
    }

    public void reset() {
        tripletRegistry.clear();
        entityToTripletIds.clear();
        causalForwardEdges.clear();
        coldRingBuffer.clear();
        totalSearches.set(0);
    }
}
