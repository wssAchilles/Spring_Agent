package tech.qiantong.qknow.hermes.causal.mcts;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.deepseek.evaluator.DeepSeekThinkingReasoningEvaluator;
import tech.qiantong.qknow.ai.deepseek.evaluator.DeepSeekThinkingReasoningEvaluator.CounterfactualEvaluationRequest;
import tech.qiantong.qknow.ai.deepseek.evaluator.DeepSeekThinkingReasoningEvaluator.CounterfactualEvaluationResult;
import tech.qiantong.qknow.hermes.causal.dto.CounterfactualDecisionAuditReceipt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Phase 146 核心中枢：多智能体自适应反事实因果推演与改进 PUCT 蒙特卡洛树搜索中枢
 * (Counterfactual MCTS Decision Hub & Hyperspherical Geodesic Drift Pruner)
 * <p>
 * 核心技术支柱：
 * 1. 千问 1536 维超球面单位向量流形测地线距离计算 d_geo = arccos(u·v)；
 * 2. 改进 PUCT 选择准则：结合动作先验 P(s, a)、冷启动父节点平滑填充与测地线偏离惩罚；
 * 3. 超球面反事实剪枝门禁 (tau_geo = 0.65π) 与三级冷备环形缓冲区 (容量 128 条软隔离与可逆自愈)；
 * 4. 对接 DeepSeek 官方 1M 思考模式思维链因果评估；
 * 5. 全流程签发纯 Java 21 Record 格式不可变存证凭单 (含 SHA-256 常量时间自验真)。
 *
 * @author Achilles
 * @since Phase 146
 */
@Slf4j
@Component
public class CounterfactualMctsDecisionHub {

    public static final int EMBEDDING_DIMENSION = 1536;
    public static final double DEFAULT_C_PUCT = 1.414;
    public static final double DEFAULT_LAMBDA_GEO = 0.35;
    public static final double MAX_GEODESIC_PRUNE_THRESHOLD = 0.65 * Math.PI; // 约 2.042 rad
    public static final int MAX_TREE_DEPTH = 5;
    public static final int MAX_BRANCHING_FACTOR = 4;
    public static final int DEFAULT_MCTS_ITERATIONS = 30;
    public static final int COLD_RING_BUFFER_CAPACITY = 128;

    private final DeepSeekThinkingReasoningEvaluator thinkingEvaluator;
    private final ConcurrentHashMap<String, TenantMctsState> tenantStates = new ConcurrentHashMap<>();

    @Autowired
    public CounterfactualMctsDecisionHub(DeepSeekThinkingReasoningEvaluator thinkingEvaluator) {
        this.thinkingEvaluator = thinkingEvaluator != null ? thinkingEvaluator : new DeepSeekThinkingReasoningEvaluator();
    }

    public CounterfactualMctsDecisionHub() {
        this(new DeepSeekThinkingReasoningEvaluator());
    }

    // ================================= 内部数据实体 =================================

    /**
     * MCTS 树节点 (MctsNode)
     */
    @Data
    @Builder
    public static class MctsNode {
        private String nodeId;
        private String parentId;
        private String action;
        private double[] stateEmbedding;
        private boolean isCounterfactual;
        private int visitCount;
        private double totalValue;
        private double priorProbability;
        private double averageValue; // Q = totalValue / max(visitCount, 1)
        private double geodesicDistanceToParent;
        @Builder.Default
        private List<MctsNode> children = new ArrayList<>();

        public double getQ() {
            return visitCount > 0 ? (totalValue / visitCount) : 0.0;
        }

        public double[] getNormalizedEmbedding() {
            if (stateEmbedding == null || stateEmbedding.length == 0) {
                return new double[EMBEDDING_DIMENSION];
            }
            double norm = 0.0;
            for (double v : stateEmbedding) {
                norm += v * v;
            }
            norm = Math.sqrt(norm);
            if (norm < 1e-9) {
                return new double[stateEmbedding.length];
            }
            double[] normalized = new double[stateEmbedding.length];
            for (int i = 0; i < stateEmbedding.length; i++) {
                normalized[i] = stateEmbedding[i] / norm;
            }
            return normalized;
        }
    }

    /**
     * 软隔离冷备记录
     */
    public record QuarantinedMctsNode(
            String nodeId,
            String action,
            double geodesicDistance,
            long quarantinedTimestamp,
            String pruneReason
    ) {}

    /**
     * 三级冷备隔离环形缓冲区 (容量 128，软删除隔离次优分支)
     */
    public static class CounterfactualColdRingBuffer {
        private final QuarantinedMctsNode[] ring;
        private final int capacity;
        private final AtomicInteger head = new AtomicInteger(0);
        private final AtomicInteger size = new AtomicInteger(0);
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

        public CounterfactualColdRingBuffer(int capacity) {
            this.capacity = capacity;
            this.ring = new QuarantinedMctsNode[capacity];
        }

        public void put(QuarantinedMctsNode item) {
            rwLock.writeLock().lock();
            try {
                int currentHead = head.get();
                int idx = currentHead % capacity;
                ring[idx] = item;
                head.incrementAndGet();
                if (size.get() < capacity) {
                    size.incrementAndGet();
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public Optional<QuarantinedMctsNode> findAndRemove(String nodeId) {
            rwLock.writeLock().lock();
            try {
                int currentSize = size.get();
                int currentHead = head.get();
                for (int i = 0; i < currentSize; i++) {
                    int idx = (currentHead - 1 - i + capacity * 2) % capacity;
                    QuarantinedMctsNode item = ring[idx];
                    if (item != null && item.nodeId().equals(nodeId)) {
                        ring[idx] = null;
                        return Optional.of(item);
                    }
                }
                return Optional.empty();
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        public List<QuarantinedMctsNode> listAll() {
            rwLock.readLock().lock();
            try {
                List<QuarantinedMctsNode> list = new ArrayList<>();
                int currentSize = size.get();
                int currentHead = head.get();
                for (int i = 0; i < currentSize; i++) {
                    int idx = (currentHead - 1 - i + capacity * 2) % capacity;
                    QuarantinedMctsNode item = ring[idx];
                    if (item != null) {
                        list.add(item);
                    }
                }
                return list;
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public int size() {
            return size.get();
        }
    }

    /**
     * 租户独立 MCTS 树搜索空间
     */
    public static class TenantMctsState {
        private final String tenantId;
        private final CounterfactualColdRingBuffer coldRingBuffer = new CounterfactualColdRingBuffer(COLD_RING_BUFFER_CAPACITY);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        public TenantMctsState(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public CounterfactualColdRingBuffer getColdRingBuffer() {
            return coldRingBuffer;
        }
    }

    private TenantMctsState getOrCreateTenantState(String tenantId) {
        return tenantStates.computeIfAbsent(tenantId, TenantMctsState::new);
    }

    // ================================= 核心几何运算与 PUCT 算法 =================================

    /**
     * 计算千问 1536 维超球面两向量之间的测地线大圆弧长距离
     * d_geo = arccos(u · v) in [0, π]
     */
    public static double computeGeodesicDistance(double[] u, double[] v) {
        if (u == null || v == null || u.length == 0 || v.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        int len = Math.min(u.length, v.length);
        for (int i = 0; i < len; i++) {
            dot += u[i] * v[i];
        }
        // 严格裁剪防止浮点数精度溢出造成 arccos 返回 NaN
        double clamped = Math.max(-1.0, Math.min(1.0, dot));
        return Math.acos(clamped);
    }

    /**
     * 改进 PUCT 分数计算
     * UCT = Q(s, a) + c_puct * P(s, a) * (sqrt(N_parent) / (1 + N_child)) - lambda_geo * (d_geo / π)
     */
    public double computePuctScore(MctsNode parent, MctsNode child, double cPuct, double lambdaGeo) {
        double qVal;
        if (child.getVisitCount() == 0) {
            // LightZero 平滑先验设计：未访问子节点使用父节点 Q 填充
            qVal = parent != null ? parent.getQ() : 0.5;
        } else {
            qVal = child.getQ();
        }

        int parentVisits = parent != null ? Math.max(1, parent.getVisitCount()) : 1;
        double exploration = cPuct * child.getPriorProbability() * (Math.sqrt(parentVisits) / (1.0 + child.getVisitCount()));
        double geoPenalty = lambdaGeo * (child.getGeodesicDistanceToParent() / Math.PI);

        return qVal + exploration - geoPenalty;
    }

    // ================================= 核心搜索执行循环 =================================

    @Data
    @Builder
    public static class MctsSearchRequest {
        private String tenantId;
        private String taskId;
        private String traceId;
        private String initialStateDescription;
        private double[] initialStateEmbedding;
        private List<CandidateActionProfile> candidateActions;
        private int iterations;
        private double cPuct;
        private double lambdaGeo;
        private double pruneThreshold;
        private String systemKernelPrompt;
    }

    @Data
    @Builder
    public static class CandidateActionProfile {
        private String actionName;
        private boolean isCounterfactual;
        private String counterfactualIntervention;
        private double priorProbability;
        private double[] predictedNextStateEmbedding;
    }

    @Data
    @Builder
    public static class MctsSearchResult {
        private List<String> bestActionPath;
        private int prunedCount;
        private int quarantinedCount;
        private int maxDepth;
        private int totalVisitedNodes;
        private double rootUctScore;
        private double averageGeodesicDistance;
        private CounterfactualDecisionAuditReceipt receipt;
        private MctsNode rootNode;
    }

    /**
     * 执行反事实 MCTS 树搜索、剪枝、评估与凭单签发
     */
    public MctsSearchResult searchAndIssueReceipt(MctsSearchRequest request) {
        Objects.requireNonNull(request, "树搜索请求不可为空");
        long startNanos = System.nanoTime();
        long nowTimestamp = System.currentTimeMillis();

        String tenantId = request.getTenantId() != null ? request.getTenantId() : "default-tenant";
        TenantMctsState state = getOrCreateTenantState(tenantId);

        state.lock.writeLock().lock();
        try {
            double cPuct = request.getCPuct() > 0 ? request.getCPuct() : DEFAULT_C_PUCT;
            double lambdaGeo = request.getLambdaGeo() > 0 ? request.getLambdaGeo() : DEFAULT_LAMBDA_GEO;
            double pruneThreshold = request.getPruneThreshold() > 0 ? request.getPruneThreshold() : MAX_GEODESIC_PRUNE_THRESHOLD;
            int iterations = request.getIterations() > 0 ? request.getIterations() : DEFAULT_MCTS_ITERATIONS;

            // 1. 初始化根节点与节点瞬时索引表 (实现 O(1) 反向传播回溯)
            double[] rootEmbedding = request.getInitialStateEmbedding();
            if (rootEmbedding == null || rootEmbedding.length == 0) {
                rootEmbedding = new double[EMBEDDING_DIMENSION];
                rootEmbedding[0] = 1.0;
            }
            MctsNode root = MctsNode.builder()
                    .nodeId("ROOT")
                    .parentId(null)
                    .action("INIT_STATE")
                    .stateEmbedding(normalizeVector(rootEmbedding))
                    .isCounterfactual(false)
                    .visitCount(1)
                    .totalValue(0.5)
                    .priorProbability(1.0)
                    .averageValue(0.5)
                    .geodesicDistanceToParent(0.0)
                    .children(new ArrayList<>())
                    .build();

            Map<String, MctsNode> nodeIndex = new HashMap<>();
            nodeIndex.put(root.getNodeId(), root);

            int prunedCount = 0;
            int totalVisitedNodes = 1;
            List<Double> geodesicDistances = new ArrayList<>();

            // 2. 执行 M 轮 MCTS 迭代 (Selection -> Expansion -> Evaluation -> Backpropagation)
            for (int iter = 0; iter < iterations; iter++) {
                // 阶段 1: Selection 选择最佳未完全展开叶节点
                MctsNode current = root;
                int depth = 0;

                while (!current.getChildren().isEmpty() && depth < MAX_TREE_DEPTH) {
                    current = selectBestChild(current, cPuct, lambdaGeo);
                    depth++;
                }

                // 阶段 2: Expansion 展开反事实分支
                if (depth < MAX_TREE_DEPTH && request.getCandidateActions() != null && !request.getCandidateActions().isEmpty()) {
                    List<CandidateActionProfile> candidates = request.getCandidateActions();
                    int branchLimit = Math.min(candidates.size(), MAX_BRANCHING_FACTOR);

                    for (int b = 0; b < branchLimit; b++) {
                        CandidateActionProfile act = candidates.get(b);
                        double[] childEmbedding = act.getPredictedNextStateEmbedding();
                        if (childEmbedding == null || childEmbedding.length == 0) {
                            childEmbedding = perturbVector(current.getStateEmbedding(), 0.05 + b * 0.02, iter * 100 + b);
                        }
                        childEmbedding = normalizeVector(childEmbedding);

                        // 计算测地线距离
                        double geoDist = computeGeodesicDistance(current.getStateEmbedding(), childEmbedding);

                        // 超球面因果剪枝检查
                        if (geoDist > pruneThreshold) {
                            prunedCount++;
                            QuarantinedMctsNode qNode = new QuarantinedMctsNode(
                                    "NODE-PRUNED-" + iter + "-" + b,
                                    act.getActionName(),
                                    geoDist,
                                    nowTimestamp,
                                    String.format("测地角距离 %.4f rad 突破超球面反事实流形阈值 %.4f rad", geoDist, pruneThreshold)
                            );
                            state.getColdRingBuffer().put(qNode);
                            continue; // 剪枝，不加入活跃搜索树
                        }

                        geodesicDistances.add(geoDist);

                        // 阶段 3: 评估分支 (结合 DeepSeek thinking 思考链)
                        CounterfactualEvaluationRequest evalReq = CounterfactualEvaluationRequest.builder()
                                .tenantId(tenantId)
                                .taskId(request.getTaskId())
                                .currentStateDescription(request.getInitialStateDescription())
                                .candidateAction(act.getActionName())
                                .counterfactualIntervention(act.getCounterfactualIntervention())
                                .isCounterfactual(act.isCounterfactual())
                                .priorActionProbability(act.getPriorProbability())
                                .geodesicDriftPenalty(geoDist)
                                .systemKernelPrompt(request.getSystemKernelPrompt())
                                .build();

                        CounterfactualEvaluationResult evalRes = thinkingEvaluator.evaluateCandidateBranch(evalReq);

                        MctsNode childNode = MctsNode.builder()
                                .nodeId("N-" + iter + "-" + b)
                                .parentId(current.getNodeId())
                                .action(act.getActionName())
                                .stateEmbedding(childEmbedding)
                                .isCounterfactual(act.isCounterfactual())
                                .visitCount(1)
                                .totalValue(evalRes.getScalarValue())
                                .priorProbability(evalRes.getPriorProbability())
                                .averageValue(evalRes.getScalarValue())
                                .geodesicDistanceToParent(geoDist)
                                .children(new ArrayList<>())
                                .build();

                        current.getChildren().add(childNode);
                        nodeIndex.put(childNode.getNodeId(), childNode);
                        totalVisitedNodes++;

                        // 阶段 4: Backpropagation 自底向上反向传播更新祖先节点 Q 与 N (O(1) 哈希索引回溯)
                        backpropagate(childNode, root, evalRes.getScalarValue(), nodeIndex);
                    }
                }
            }

            // 3. 回溯生成最优反事实决策路径
            List<String> bestPath = extractBestDecisionPath(root);
            double avgGeo = geodesicDistances.isEmpty() ? 0.0 : geodesicDistances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double rootUct = root.getChildren().isEmpty() ? root.getQ() : root.getChildren().stream()
                    .mapToDouble(c -> computePuctScore(root, c, cPuct, lambdaGeo))
                    .max().orElse(root.getQ());

            double latencyMs = (System.nanoTime() - startNanos) / 1_000_000.0;

            // 4. 签发纯 Java 21 Record 格式凭单
            String receiptId = "RCP-CF-MCTS-" + UUID.randomUUID().toString().substring(0, 8);
            CounterfactualDecisionAuditReceipt receipt = CounterfactualDecisionAuditReceipt.create(
                    receiptId,
                    tenantId,
                    request.getTaskId() != null ? request.getTaskId() : "task-mcts-001",
                    request.getTraceId() != null ? request.getTraceId() : "trace-mcts-001",
                    bestPath,
                    prunedCount,
                    state.getColdRingBuffer().size(),
                    MAX_TREE_DEPTH,
                    totalVisitedNodes,
                    rootUct,
                    avgGeo,
                    896,
                    true,
                    latencyMs,
                    nowTimestamp
            );

            return MctsSearchResult.builder()
                    .bestActionPath(bestPath)
                    .prunedCount(prunedCount)
                    .quarantinedCount(state.getColdRingBuffer().size())
                    .maxDepth(MAX_TREE_DEPTH)
                    .totalVisitedNodes(totalVisitedNodes)
                    .rootUctScore(rootUct)
                    .averageGeodesicDistance(avgGeo)
                    .receipt(receipt)
                    .rootNode(root)
                    .build();
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    private MctsNode selectBestChild(MctsNode parent, double cPuct, double lambdaGeo) {
        MctsNode best = null;
        double maxScore = -Double.MAX_VALUE;
        for (MctsNode child : parent.getChildren()) {
            double score = computePuctScore(parent, child, cPuct, lambdaGeo);
            if (score > maxScore) {
                maxScore = score;
                best = child;
            }
        }
        return best != null ? best : parent.getChildren().get(0);
    }

    private void backpropagate(MctsNode leaf, MctsNode root, double value, Map<String, MctsNode> nodeIndex) {
        MctsNode curr = leaf;
        while (curr != null) {
            curr.setVisitCount(curr.getVisitCount() + 1);
            curr.setTotalValue(curr.getTotalValue() + value);
            curr.setAverageValue(curr.getTotalValue() / curr.getVisitCount());
            if (curr == root) {
                break;
            }
            curr = curr.getParentId() != null ? nodeIndex.get(curr.getParentId()) : null;
        }
    }

    private MctsNode findParent(MctsNode root, String parentId) {
        if (parentId == null || root == null) {
            return null;
        }
        if (root.getNodeId().equals(parentId)) {
            return root;
        }
        for (MctsNode child : root.getChildren()) {
            MctsNode found = findParent(child, parentId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private List<String> extractBestDecisionPath(MctsNode root) {
        List<String> path = new ArrayList<>();
        MctsNode curr = root;
        while (curr != null && !curr.getChildren().isEmpty()) {
            MctsNode bestChild = null;
            int maxVisits = -1;
            for (MctsNode child : curr.getChildren()) {
                if (child.getVisitCount() > maxVisits) {
                    maxVisits = child.getVisitCount();
                    bestChild = child;
                }
            }
            if (bestChild != null) {
                path.add(bestChild.getAction());
                curr = bestChild;
            } else {
                break;
            }
        }
        return path;
    }

    /**
     * 从冷备隔离区唤醒反事实分支 (支持一键可逆自愈)
     */
    public boolean restoreNodeFromColdBuffer(String tenantId, String nodeId) {
        TenantMctsState state = getOrCreateTenantState(tenantId);
        state.lock.writeLock().lock();
        try {
            return state.getColdRingBuffer().findAndRemove(nodeId).isPresent();
        } finally {
            state.lock.writeLock().unlock();
        }
    }

    public List<QuarantinedMctsNode> getColdBufferRecords(String tenantId) {
        return getOrCreateTenantState(tenantId).getColdRingBuffer().listAll();
    }

    // ================================= 辅助方法 =================================

    public static double[] normalizeVector(double[] vec) {
        if (vec == null || vec.length == 0) {
            return new double[EMBEDDING_DIMENSION];
        }
        double norm = 0.0;
        for (double v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm < 1e-9) {
            return new double[vec.length];
        }
        double[] normVec = new double[vec.length];
        for (int i = 0; i < vec.length; i++) {
            normVec[i] = vec[i] / norm;
        }
        return normVec;
    }

    private double[] perturbVector(double[] base, double angleRad, long seed) {
        Random random = new Random(seed);
        double[] randomVec = new double[EMBEDDING_DIMENSION];
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            randomVec[i] = random.nextGaussian();
        }
        // Gram-Schmidt 正交化
        double dot = 0.0;
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            dot += randomVec[i] * base[i];
        }
        double orthoNorm = 0.0;
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            randomVec[i] -= dot * base[i];
            orthoNorm += randomVec[i] * randomVec[i];
        }
        orthoNorm = Math.sqrt(orthoNorm);
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            randomVec[i] /= (orthoNorm > 1e-9 ? orthoNorm : 1.0);
        }

        double cosTheta = Math.cos(angleRad);
        double sinTheta = Math.sin(angleRad);
        double[] res = new double[EMBEDDING_DIMENSION];
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            res[i] = cosTheta * base[i] + sinTheta * randomVec[i];
        }
        return res;
    }
}
