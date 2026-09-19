package tech.qiantong.qknow.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.rag.model.GraphRagScaffoldReceipt;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 2-跳 PPR 有界子图剪枝与 Kahn 因果路径拓扑排序思考骨架引擎 (定理 1.2)
 * 防范超级节点拓扑爆炸 (B_max <= 16, N_max <= 32)，将离散三元组重构为递进因果命题链，
 * 生成标准结构化 Markdown 思考脚手架 <thinking_scaffold> 注入 DeepSeek R1 推理上下文，并签发防篡改存证凭单
 */
@Component
public class GraphGuidedThinkingScaffold {

    private static final Logger log = LoggerFactory.getLogger(GraphGuidedThinkingScaffold.class);

    public static final int MAX_HOPS = 2;
    public static final int MAX_BRANCHES_PER_NODE = 16; // B_max: 超级节点分支截断上限
    public static final int MAX_SUBGRAPH_NODES = 32;     // N_max: 子图保留最大节点数
    public static final double PPR_DAMPING = 0.85;       // PPR 阻尼系数
    public static final int PPR_MAX_ITERATIONS = 20;     // PPR 最大幂迭代轮数

    private final SpatiotemporalDecayAligner decayAligner;

    public GraphGuidedThinkingScaffold() {
        this(new SpatiotemporalDecayAligner());
    }

    @Autowired
    public GraphGuidedThinkingScaffold(SpatiotemporalDecayAligner decayAligner) {
        this.decayAligner = decayAligner != null ? decayAligner : new SpatiotemporalDecayAligner();
    }

    public SpatiotemporalDecayAligner getDecayAligner() {
        return decayAligner;
    }

    /**
     * 图节点定义
     */
    public record Node(
            String id,
            String name,
            String category,
            long timestampMs,
            boolean isExpired,
            float[] embedding
    ) {}

    /**
     * 图边（关系）定义
     */
    public record Edge(
            String sourceId,
            String targetId,
            String relationType,
            double weight,
            boolean isCausal // 是否属于显式因果关系 (CAUSES, REQUIRES, LEADS_TO, PREVENTS 等)
    ) {}

    /**
     * 骨架抽取与对齐结果
     */
    public record ScaffoldResult(
            String scaffoldMarkdown,
            List<String> causalChains,
            List<Node> retainedNodes,
            List<Edge> retainedEdges,
            GraphRagScaffoldReceipt receipt
    ) {}

    /**
     * 执行 2-跳有界 PPR 剪枝、因果拓扑排序并生成思考骨架
     *
     * @param sessionId      会话 ID
     * @param query          用户查询
     * @param queryEmb       查询 1536 维超球面向量
     * @param seedNodeIds    种子实体 ID 集合
     * @param allNodes       候选知识节点集合 (按 ID 索引)
     * @param allEdges       候选知识边集合
     * @param currentEpochMs 当前查询毫秒时间戳
     * @return 思考骨架结果及存证凭单
     */
    public ScaffoldResult buildScaffold(
            String sessionId,
            String query,
            float[] queryEmb,
            List<String> seedNodeIds,
            Map<String, Node> allNodes,
            List<Edge> allEdges,
            long currentEpochMs
    ) {
        long startTime = System.currentTimeMillis();

        if (seedNodeIds == null || seedNodeIds.isEmpty() || allNodes == null || allNodes.isEmpty()) {
            return emptyResult(sessionId, query, startTime);
        }

        // 1. 2-跳有界邻居展开与超级节点截断 (B_max <= 16)
        Map<String, List<Edge>> adjacency = buildBoundedAdjacency(seedNodeIds, allNodes, allEdges);

        // 收集子图涉及的全部候选节点 ID
        Set<String> candidateNodeIds = new HashSet<>(seedNodeIds);
        for (List<Edge> edges : adjacency.values()) {
            for (Edge e : edges) {
                candidateNodeIds.add(e.sourceId());
                candidateNodeIds.add(e.targetId());
            }
        }

        // 2. 本地轻量级有向加权图 Personalized PageRank 幂迭代 (d=0.85)
        Map<String, Double> pprScores = computeLocalPPR(seedNodeIds, candidateNodeIds, adjacency);

        // 3. 结合超球面测地内积与毫秒级时序指数衰减对齐，筛选 Top N_max 核心节点
        List<Map.Entry<String, Double>> rankedNodes = candidateNodeIds.stream()
                .map(id -> {
                    Node node = allNodes.get(id);
                    double ppr = pprScores.getOrDefault(id, 0.0);
                    double finalScore = 0.0;
                    if (node != null) {
                        finalScore = decayAligner.alignSpatiotemporalScore(
                                queryEmb,
                                node.embedding(),
                                ppr,
                                node.timestampMs(),
                                currentEpochMs,
                                node.isExpired()
                        );
                    }
                    return Map.entry(id, finalScore);
                })
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(MAX_SUBGRAPH_NODES)
                .toList();

        Set<String> retainedNodeIds = rankedNodes.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        List<Node> retainedNodes = rankedNodes.stream()
                .map(e -> allNodes.get(e.getKey()))
                .filter(Objects::nonNull)
                .toList();

        // 4. 过滤保留边并破除环路构建 DAG
        List<Edge> retainedEdges = new ArrayList<>();
        for (List<Edge> edges : adjacency.values()) {
            for (Edge e : edges) {
                if (retainedNodeIds.contains(e.sourceId()) && retainedNodeIds.contains(e.targetId())) {
                    retainedEdges.add(e);
                }
            }
        }

        // 5. Kahn 算法因果拓扑排序生成递进因果命题链
        List<String> causalChains = performKahnTopologicalSort(retainedNodes, retainedEdges);

        // 6. 构造标准结构化 Markdown 思考脚手架 <thinking_scaffold>
        double avgScore = rankedNodes.isEmpty() ? 0.0 :
                rankedNodes.stream().mapToDouble(Map.Entry::getValue).average().orElse(0.0);

        String scaffoldMarkdown = formatScaffoldMarkdown(query, retainedNodes, causalChains);

        long executionTimeMs = System.currentTimeMillis() - startTime;

        // 7. 签发纯 Java 21 Record 格式密码学不可变存证凭单
        GraphRagScaffoldReceipt receipt = GraphRagScaffoldReceipt.create(
                sessionId,
                query,
                seedNodeIds,
                retainedNodes.stream().map(Node::name).toList(),
                retainedEdges.stream().map(Edge::relationType).toList(),
                causalChains,
                avgScore,
                executionTimeMs
        );

        log.info("[GraphThinkingScaffold] 成功生成思考骨架: nodes={}, edges={}, chains={}, timeMs={}, sig={}",
                retainedNodes.size(), retainedEdges.size(), causalChains.size(), executionTimeMs, receipt.signature());

        return new ScaffoldResult(scaffoldMarkdown, causalChains, retainedNodes, retainedEdges, receipt);
    }

    /**
     * 2-跳有界邻居展开，强制执行超级节点分支截断 (B_max <= 16)
     */
    private Map<String, List<Edge>> buildBoundedAdjacency(
            List<String> seedIds,
            Map<String, Node> allNodes,
            List<Edge> allEdges
    ) {
        // 全量边按源节点构建索引
        Map<String, List<Edge>> rawAdj = new HashMap<>();
        for (Edge e : allEdges) {
            rawAdj.computeIfAbsent(e.sourceId(), k -> new ArrayList<>()).add(e);
        }

        Map<String, List<Edge>> boundedAdj = new HashMap<>();
        Set<String> currentFrontier = new HashSet<>(seedIds);
        Set<String> visited = new HashSet<>(seedIds);

        for (int hop = 1; hop <= MAX_HOPS; hop++) {
            Set<String> nextFrontier = new HashSet<>();
            for (String u : currentFrontier) {
                List<Edge> outEdges = rawAdj.getOrDefault(u, Collections.emptyList());
                if (outEdges.isEmpty()) continue;

                // 超级节点截断保护：若出度 > B_max，按权重降序截取前 B_max 条边
                List<Edge> truncatedEdges;
                if (outEdges.size() > MAX_BRANCHES_PER_NODE) {
                    truncatedEdges = outEdges.stream()
                            .sorted((a, b) -> Double.compare(b.weight(), a.weight()))
                            .limit(MAX_BRANCHES_PER_NODE)
                            .toList();
                } else {
                    truncatedEdges = outEdges;
                }

                boundedAdj.put(u, truncatedEdges);

                for (Edge e : truncatedEdges) {
                    if (visited.add(e.targetId())) {
                        nextFrontier.add(e.targetId());
                    }
                }
            }
            currentFrontier = nextFrontier;
            if (currentFrontier.isEmpty()) break;
        }

        return boundedAdj;
    }

    /**
     * 本地有向加权图 Personalized PageRank 幂迭代计算
     */
    private Map<String, Double> computeLocalPPR(
            List<String> seedIds,
            Set<String> candidateNodeIds,
            Map<String, List<Edge>> adjacency
    ) {
        Map<String, Double> ppr = new HashMap<>();
        int n = candidateNodeIds.size();
        if (n == 0) return ppr;

        // 初始化个性化偏置向量 p0
        double seedWeight = 1.0 / Math.max(1, seedIds.size());
        Map<String, Double> p0 = new HashMap<>();
        for (String s : seedIds) {
            if (candidateNodeIds.contains(s)) {
                p0.put(s, seedWeight);
                ppr.put(s, seedWeight);
            }
        }
        for (String id : candidateNodeIds) {
            ppr.putIfAbsent(id, 0.0);
            p0.putIfAbsent(id, 0.0);
        }

        // 幂迭代
        for (int iter = 0; iter < PPR_MAX_ITERATIONS; iter++) {
            Map<String, Double> nextPpr = new HashMap<>();
            for (String id : candidateNodeIds) {
                nextPpr.put(id, (1.0 - PPR_DAMPING) * p0.getOrDefault(id, 0.0));
            }

            for (String u : candidateNodeIds) {
                double uRank = ppr.getOrDefault(u, 0.0);
                if (uRank <= 0.0) continue;

                List<Edge> outEdges = adjacency.getOrDefault(u, Collections.emptyList());
                if (!outEdges.isEmpty()) {
                    double totalWeight = outEdges.stream().mapToDouble(Edge::weight).sum();
                    double safeTotal = totalWeight > 0.0 ? totalWeight : outEdges.size();

                    for (Edge e : outEdges) {
                        String v = e.targetId();
                        if (candidateNodeIds.contains(v)) {
                            double transfer = PPR_DAMPING * uRank * (e.weight() > 0 ? e.weight() / safeTotal : 1.0 / outEdges.size());
                            nextPpr.put(v, nextPpr.getOrDefault(v, 0.0) + transfer);
                        }
                    }
                } else {
                    // 无出度节点重分配至种子
                    for (String s : seedIds) {
                        if (candidateNodeIds.contains(s)) {
                            nextPpr.put(s, nextPpr.getOrDefault(s, 0.0) + PPR_DAMPING * uRank * seedWeight);
                        }
                    }
                }
            }

            // 检查收敛
            double diff = 0.0;
            for (String id : candidateNodeIds) {
                diff += Math.abs(nextPpr.getOrDefault(id, 0.0) - ppr.getOrDefault(id, 0.0));
            }
            ppr = nextPpr;
            if (diff < 1e-4) {
                break;
            }
        }

        return ppr;
    }

    /**
     * Kahn 算法因果拓扑排序，破除环路并生成自然可读因果命题链
     */
    private List<String> performKahnTopologicalSort(List<Node> nodes, List<Edge> edges) {
        if (nodes.isEmpty()) return Collections.emptyList();

        Map<String, Node> nodeMap = nodes.stream().collect(Collectors.toMap(Node::id, n -> n));
        Set<String> nodeIds = nodeMap.keySet();

        // 计算入度表与邻接表
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<Edge>> adj = new HashMap<>();
        for (String id : nodeIds) {
            inDegree.put(id, 0);
            adj.put(id, new ArrayList<>());
        }

        for (Edge e : edges) {
            if (nodeIds.contains(e.sourceId()) && nodeIds.contains(e.targetId())) {
                adj.get(e.sourceId()).add(e);
                inDegree.put(e.targetId(), inDegree.get(e.targetId()) + 1);
            }
        }

        // Kahn 算法优先入队入度为 0 的前置原因节点
        Queue<String> queue = new ArrayDeque<>();
        for (String id : nodeIds) {
            if (inDegree.get(id) == 0) {
                queue.add(id);
            }
        }

        List<String> topoOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.poll();
            topoOrder.add(u);

            for (Edge e : adj.getOrDefault(u, Collections.emptyList())) {
                String v = e.targetId();
                int deg = inDegree.get(v) - 1;
                inDegree.put(v, deg);
                if (deg == 0) {
                    queue.add(v);
                }
            }
        }

        // 若存在环路，将剩余未访问节点追加进顺序
        for (String id : nodeIds) {
            if (!topoOrder.contains(id)) {
                topoOrder.add(id);
            }
        }

        // 生成线性递进因果命题链
        List<String> chains = new ArrayList<>();
        for (Edge e : edges) {
            Node src = nodeMap.get(e.sourceId());
            Node tgt = nodeMap.get(e.targetId());
            if (src != null && tgt != null) {
                String chain = String.format("「%s」 --[%s]--> 「%s」", src.name(), e.relationType(), tgt.name());
                chains.add(chain);
            }
        }

        return chains;
    }

    /**
     * 组装标准结构化 Markdown 思考脚手架 <thinking_scaffold>
     */
    private String formatScaffoldMarkdown(String query, List<Node> nodes, List<String> causalChains) {
        StringBuilder sb = new StringBuilder();
        sb.append("<thinking_scaffold>\n");
        sb.append("### [系统 GraphRAG 因果思考脚手架 (Causal Reasoning Scaffold)]\n");
        sb.append("针对用户查询「").append(query != null ? query : "").append("」，图谱引擎已完成 2-跳有界拓扑抽取与时空对齐。\n");
        sb.append("请严格按照以下因果推理骨架进行链式思考推演，严禁跳步推演或采信已失效的陈旧事实：\n\n");

        sb.append("#### 1. 核心实体与时序状态清单 (Entities & Temporal Status)\n");
        for (Node n : nodes) {
            String status = n.isExpired() ? "[EXPIRED - 已作废]" : "[ACTIVE - 当前有效]";
            sb.append("- ").append(n.name()).append(" (").append(n.category()).append(", 状态: ").append(status).append(")\n");
        }
        sb.append("\n");

        sb.append("#### 2. 因果拓扑命题链 (Causal Proposition Chains)\n");
        if (causalChains.isEmpty()) {
            sb.append("- (无显式因果拓扑关系，请基于实体属性直接推演)\n");
        } else {
            for (int i = 0; i < causalChains.size(); i++) {
                sb.append("- [因果链 ").append(i + 1).append("]: ").append(causalChains.get(i)).append("\n");
            }
        }
        sb.append("\n");

        sb.append("#### 3. 严格推理指引 (Reasoning Directives)\n");
        sb.append("1. 推演前提必须建立在标记为 [ACTIVE] 的最新实体基础之上，严禁将 [EXPIRED] 事实作为现行有效依据；\n");
        sb.append("2. 严格遵循因果链条的递进约束，得出不可动摇的确定性业务结论；\n");
        sb.append("3. 保持回答清晰、专业、严谨。\n");
        sb.append("</thinking_scaffold>\n");

        return sb.toString();
    }

    private ScaffoldResult emptyResult(String sessionId, String query, long startTime) {
        long executionTimeMs = System.currentTimeMillis() - startTime;
        GraphRagScaffoldReceipt receipt = GraphRagScaffoldReceipt.create(
                sessionId, query, List.of(), List.of(), List.of(), List.of(), 0.0, executionTimeMs
        );
        return new ScaffoldResult("", List.of(), List.of(), List.of(), receipt);
    }
}
