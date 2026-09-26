package tech.qiantong.qknow.mcp.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpToolDescriptor;
import tech.qiantong.qknow.mcp.distill.StreamingToolCentroidDistiller;
import tech.qiantong.qknow.mcp.dto.McpToolRoutingAuditReceipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 大规模企业级 MCP 工具超球面测地线 Voronoi 层次化索引与自适应 UCB 动态路由中枢
 * 在阿里千问 1536 维超球面单位向量空间上构建两级测地 Voronoi 树，实现 O(log M) 极速对数路由；
 * 融合有界多臂老虎机 (Bounded MAB) 探索利用自适应避障，结合三级冷备隔离环形仓与不可变自验真凭单
 * 采用纯 Java 21 原生 Record 与集合，无第三方 Lombok 侵入
 */
public class McpHierarchicalVoronoiRouter {

    private static final Logger log = LoggerFactory.getLogger(McpHierarchicalVoronoiRouter.class);

    public static final int EMBEDDING_DIMENSION = 1536;
    public static final int DEFAULT_TOP_K = 5;
    public static final int DEFAULT_BRANCH_CELLS = 2; // 粗筛选取的最近胞腔数
    public static final int L3_RING_BUFFER_CAPACITY = 256;
    public static final double DEFAULT_ALPHA_SEMANTIC = 0.65; // 语义相似度权重
    public static final double UCB_EXPLORATION_FACTOR = 1.414; // 老虎机置信上界探索系数
    public static final int FAILURE_THRESHOLD_TO_DEGRADE = 3; // 触发降级阈值

    /**
     * 测地线 Voronoi 胞腔
     */
    public static class VoronoiCell {
        private final int cellId;
        private double[] centroid; // 1536 维超球面单位质心 (||c||_2 = 1.0)
        private double coverageRadius; // 测地覆盖半角 (rad)
        private final List<McpToolDescriptor> memberTools = new ArrayList<>();

        public VoronoiCell(int cellId, double[] initialCentroid) {
            this.cellId = cellId;
            this.centroid = initialCentroid != null ? initialCentroid.clone() : generateZeroCentroid();
            this.coverageRadius = 0.0;
        }

        public int getCellId() {
            return cellId;
        }

        public double[] getCentroid() {
            return centroid;
        }

        public double getCoverageRadius() {
            return coverageRadius;
        }

        public List<McpToolDescriptor> getMemberTools() {
            return memberTools;
        }

        public synchronized void addTool(McpToolDescriptor tool) {
            memberTools.add(tool);
            recomputeCentroidAndRadius();
        }

        public synchronized void recomputeCentroidAndRadius() {
            if (memberTools.isEmpty()) {
                this.coverageRadius = 0.0;
                return;
            }
            double[] sum = new double[EMBEDDING_DIMENSION];
            for (McpToolDescriptor t : memberTools) {
                double[] emb = t.embedding();
                for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                    sum[i] += emb[i];
                }
            }
            double sumSq = 0.0;
            for (double val : sum) {
                sumSq += val * val;
            }
            double norm = Math.sqrt(sumSq);
            if (norm > 1e-12) {
                double inv = 1.0 / norm;
                for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                    centroid[i] = sum[i] * inv;
                }
            }
            // 计算最大测地大圆弧距离
            double maxGeo = 0.0;
            for (McpToolDescriptor t : memberTools) {
                double dot = t.cosineSimilarity(centroid);
                double geo = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
                if (geo > maxGeo) {
                    maxGeo = geo;
                }
            }
            this.coverageRadius = maxGeo;
        }

        private static double[] generateZeroCentroid() {
            double[] c = new double[EMBEDDING_DIMENSION];
            c[0] = 1.0;
            return c;
        }
    }

    /**
     * 工具臂 UCB 状态遥测
     */
    public static class ToolArmTelemetry {
        private final String toolId;
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong successCalls = new AtomicLong(0);
        private final AtomicLong failureCalls = new AtomicLong(0);
        private final DoubleAdder cumulativeLatencyMs = new DoubleAdder();
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile long lastFailureTimestamp = 0;

        public ToolArmTelemetry(String toolId) {
            this.toolId = toolId;
        }

        public void recordSuccess(double latencyMs) {
            totalCalls.incrementAndGet();
            successCalls.incrementAndGet();
            consecutiveFailures.set(0);
            cumulativeLatencyMs.add(latencyMs);
        }

        public void recordFailure(double latencyMs) {
            totalCalls.incrementAndGet();
            failureCalls.incrementAndGet();
            consecutiveFailures.incrementAndGet();
            lastFailureTimestamp = System.currentTimeMillis();
            cumulativeLatencyMs.add(latencyMs);
        }

        public double computeUcbScore(double cosineSim, long totalSystemCalls, double alpha) {
            long total = totalCalls.get();
            long success = successCalls.get();
            int failStreak = consecutiveFailures.get();

            // 基础成功率
            double empiricalMean = total > 0 ? (double) success / total : 0.5;

            // 探索置信区间 (Upper Confidence Bound)
            double exploration = Math.sqrt((2.0 * Math.log(Math.max(1, totalSystemCalls) + 1.0)) / (total + 1.0)) * UCB_EXPLORATION_FACTOR;

            // 延迟惩罚
            double avgLat = total > 0 ? cumulativeLatencyMs.doubleValue() / total : 20.0;
            double latencyPenalty = Math.min(0.2, avgLat / 500.0);

            // 严重故障惩罚
            double failurePenalty = 0.0;
            if (failStreak >= FAILURE_THRESHOLD_TO_DEGRADE) {
                failurePenalty = 0.45 * Math.min(failStreak, 5);
            }

            double ucbComponent = empiricalMean + exploration - latencyPenalty - failurePenalty;
            return alpha * cosineSim + (1.0 - alpha) * ucbComponent;
        }

        public boolean isDegraded() {
            return consecutiveFailures.get() >= FAILURE_THRESHOLD_TO_DEGRADE;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures.get();
        }

        public long getTotalCalls() {
            return totalCalls.get();
        }

        public long getSuccessCalls() {
            return successCalls.get();
        }
    }

    /**
     * 三级冷备可逆隔离环形仓 (L3 Cold Ring Buffer)
     */
    public static class L3ColdStandbyRingBuffer {
        private final McpToolDescriptor[] buffer = new McpToolDescriptor[L3_RING_BUFFER_CAPACITY];
        private final String[] reasonBuffer = new String[L3_RING_BUFFER_CAPACITY];
        private final long[] timestampBuffer = new long[L3_RING_BUFFER_CAPACITY];
        private final AtomicInteger head = new AtomicInteger(0);
        private final AtomicInteger size = new AtomicInteger(0);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        public void put(McpToolDescriptor tool, String reason) {
            lock.writeLock().lock();
            try {
                int idx = head.getAndIncrement() % L3_RING_BUFFER_CAPACITY;
                buffer[idx] = tool;
                reasonBuffer[idx] = reason;
                timestampBuffer[idx] = System.currentTimeMillis();
                if (size.get() < L3_RING_BUFFER_CAPACITY) {
                    size.incrementAndGet();
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        public Optional<McpToolDescriptor> extractAndRevoke(String toolId) {
            lock.writeLock().lock();
            try {
                for (int i = 0; i < L3_RING_BUFFER_CAPACITY; i++) {
                    if (buffer[i] != null && buffer[i].toolId().equals(toolId)) {
                        McpToolDescriptor match = buffer[i];
                        buffer[i] = null;
                        reasonBuffer[i] = null;
                        size.decrementAndGet();
                        return Optional.of(match);
                    }
                }
                return Optional.empty();
            } finally {
                lock.writeLock().unlock();
            }
        }

        public int size() {
            return size.get();
        }

        public List<McpToolDescriptor> listAll() {
            lock.readLock().lock();
            try {
                List<McpToolDescriptor> list = new ArrayList<>();
                for (McpToolDescriptor t : buffer) {
                    if (t != null) {
                        list.add(t);
                    }
                }
                return list;
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    // ================================= 核心路由器状态与索引 =================================

    private final String tenantId;
    private final Map<String, McpToolDescriptor> activeToolsRegistry = new ConcurrentHashMap<>();
    private final Map<String, McpToolDescriptor> degradedToolsRegistry = new ConcurrentHashMap<>();
    private final Map<String, ToolArmTelemetry> telemetryMap = new ConcurrentHashMap<>();
    private final L3ColdStandbyRingBuffer l3ColdRingBuffer = new L3ColdStandbyRingBuffer();

    private final List<VoronoiCell> voronoiCells = new ArrayList<>();
    private final AtomicLong totalSystemInvocations = new AtomicLong(0);
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public McpHierarchicalVoronoiRouter(String tenantId) {
        this.tenantId = tenantId != null ? tenantId : "default-tenant";
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getTotalRegisteredTools() {
        rwLock.readLock().lock();
        try {
            return activeToolsRegistry.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getVoronoiCellsCount() {
        rwLock.readLock().lock();
        try {
            return voronoiCells.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public L3ColdStandbyRingBuffer getL3ColdRingBuffer() {
        return l3ColdRingBuffer;
    }

    public ToolArmTelemetry getTelemetry(String toolId) {
        return telemetryMap.computeIfAbsent(toolId, ToolArmTelemetry::new);
    }

    // ================================= 注册与索引重构 =================================

    /**
     * 注册工具并动态更新 Voronoi 胞腔分配
     */
    public void registerTool(McpToolDescriptor tool) {
        Objects.requireNonNull(tool, "MCP 工具描述符不可为空");
        rwLock.writeLock().lock();
        try {
            activeToolsRegistry.put(tool.toolId(), tool);
            telemetryMap.computeIfAbsent(tool.toolId(), ToolArmTelemetry::new);
            assignToolToVoronoiCells(tool);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 批量注册工具并全量重建平衡 Voronoi 树
     */
    public void registerToolsBatch(List<McpToolDescriptor> tools) {
        if (tools == null || tools.isEmpty()) return;
        rwLock.writeLock().lock();
        try {
            for (McpToolDescriptor t : tools) {
                activeToolsRegistry.put(t.toolId(), t);
                telemetryMap.computeIfAbsent(t.toolId(), ToolArmTelemetry::new);
            }
            rebuildVoronoiIndex();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 软删除并隔离工具至 L3 冷备环形仓
     */
    public boolean softQuarantineTool(String toolId, String reason) {
        rwLock.writeLock().lock();
        try {
            McpToolDescriptor removed = activeToolsRegistry.remove(toolId);
            if (removed != null) {
                l3ColdRingBuffer.put(removed, reason != null ? reason : "ADMIN_MANUAL_QUARANTINE");
                rebuildVoronoiIndex();
                return true;
            }
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 可逆撤销隔离：从 L3 冷备环形仓恢复工具到 L1 活跃注册表
     */
    public boolean unquarantineTool(String toolId) {
        rwLock.writeLock().lock();
        try {
            Optional<McpToolDescriptor> opt = l3ColdRingBuffer.extractAndRevoke(toolId);
            if (opt.isPresent()) {
                McpToolDescriptor tool = opt.get();
                activeToolsRegistry.put(tool.toolId(), tool);
                ToolArmTelemetry telem = telemetryMap.get(tool.toolId());
                if (telem != null) {
                    telem.consecutiveFailures.set(0);
                }
                rebuildVoronoiIndex();
                return true;
            }
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 标记工具调用成功
     */
    public void recordToolSuccess(String toolId, double latencyMs) {
        ToolArmTelemetry telem = telemetryMap.get(toolId);
        if (telem != null) {
            telem.recordSuccess(latencyMs);
            totalSystemInvocations.incrementAndGet();
            // 若从降级状态自愈
            degradedToolsRegistry.remove(toolId);
        }
    }

    /**
     * 标记工具调用失败，若连续失败达到阈值则自动降级移入 L2 仓
     */
    public void recordToolFailure(String toolId, double latencyMs) {
        ToolArmTelemetry telem = telemetryMap.get(toolId);
        if (telem != null) {
            telem.recordFailure(latencyMs);
            totalSystemInvocations.incrementAndGet();
            if (telem.isDegraded()) {
                McpToolDescriptor desc = activeToolsRegistry.get(toolId);
                if (desc != null) {
                    degradedToolsRegistry.put(toolId, desc);
                }
            }
        }
    }

    /**
     * 动态分配单个工具至最接近的 Voronoi 胞腔
     */
    private void assignToolToVoronoiCells(McpToolDescriptor tool) {
        if (voronoiCells.isEmpty()) {
            voronoiCells.add(new VoronoiCell(0, tool.embedding()));
        }
        int targetK = Math.max(2, Math.min(32, (int) Math.ceil(Math.sqrt(activeToolsRegistry.size()))));
        if (voronoiCells.size() < targetK) {
            // 胞腔尚未满，裂变出新胞腔
            voronoiCells.add(new VoronoiCell(voronoiCells.size(), tool.embedding()));
            rebuildVoronoiIndex();
            return;
        }

        // 寻找最近胞腔并加入
        VoronoiCell bestCell = null;
        double maxCos = -2.0;
        for (VoronoiCell cell : voronoiCells) {
            double cos = tool.cosineSimilarity(cell.getCentroid());
            if (cos > maxCos) {
                maxCos = cos;
                bestCell = cell;
            }
        }
        if (bestCell != null) {
            bestCell.addTool(tool);
        }
    }

    /**
     * 全量超球面 k-means 聚类重构两级 Voronoi 索引树
     */
    public void rebuildVoronoiIndex() {
        List<McpToolDescriptor> tools = new ArrayList<>(activeToolsRegistry.values());
        voronoiCells.clear();
        if (tools.isEmpty()) {
            return;
        }

        int k = Math.max(2, Math.min(32, (int) Math.ceil(Math.sqrt(tools.size()))));
        if (tools.size() <= k) {
            k = Math.max(1, tools.size());
        }

        // 1. 初始化 K 个质心
        for (int i = 0; i < k; i++) {
            voronoiCells.add(new VoronoiCell(i, tools.get(i).embedding()));
        }

        // 2. 迭代 5 轮 Spherical k-means 快速收敛
        for (int iter = 0; iter < 5; iter++) {
            for (VoronoiCell c : voronoiCells) {
                c.memberTools.clear();
            }
            for (McpToolDescriptor tool : tools) {
                VoronoiCell best = voronoiCells.get(0);
                double maxSim = -2.0;
                for (VoronoiCell c : voronoiCells) {
                    double sim = tool.cosineSimilarity(c.getCentroid());
                    if (sim > maxSim) {
                        maxSim = sim;
                        best = c;
                    }
                }
                best.memberTools.add(tool);
            }
            for (VoronoiCell c : voronoiCells) {
                c.recomputeCentroidAndRadius();
            }
        }
    }

    // ================================= 路由请求与结果契约 (纯 Java 21 Record) =================================

    public record RoutingRequest(
            String taskId,
            String traceId,
            String userQuery,
            double[] queryEmbedding,
            Integer topK,
            Integer branchCells,
            Double alphaSemantic
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String taskId;
            private String traceId;
            private String userQuery;
            private double[] queryEmbedding;
            private Integer topK;
            private Integer branchCells;
            private Double alphaSemantic;

            public Builder taskId(String taskId) { this.taskId = taskId; return this; }
            public Builder traceId(String traceId) { this.traceId = traceId; return this; }
            public Builder userQuery(String userQuery) { this.userQuery = userQuery; return this; }
            public Builder queryEmbedding(double[] queryEmbedding) { this.queryEmbedding = queryEmbedding; return this; }
            public Builder topK(Integer topK) { this.topK = topK; return this; }
            public Builder branchCells(Integer branchCells) { this.branchCells = branchCells; return this; }
            public Builder alphaSemantic(Double alphaSemantic) { this.alphaSemantic = alphaSemantic; return this; }

            public RoutingRequest build() {
                return new RoutingRequest(taskId, traceId, userQuery, queryEmbedding, topK, branchCells, alphaSemantic);
            }
        }
    }

    public record RoutingResult(
            List<McpToolDescriptor> candidateTools,
            McpToolDescriptor selectedTool,
            double bestUcbScore,
            double bestCosineSimilarity,
            int scannedVoronoiCells,
            long routingLatencyNanos,
            McpToolRoutingAuditReceipt receipt
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<McpToolDescriptor> candidateTools;
            private McpToolDescriptor selectedTool;
            private double bestUcbScore;
            private double bestCosineSimilarity;
            private int scannedVoronoiCells;
            private long routingLatencyNanos;
            private McpToolRoutingAuditReceipt receipt;

            public Builder candidateTools(List<McpToolDescriptor> candidateTools) { this.candidateTools = candidateTools; return this; }
            public Builder selectedTool(McpToolDescriptor selectedTool) { this.selectedTool = selectedTool; return this; }
            public Builder bestUcbScore(double bestUcbScore) { this.bestUcbScore = bestUcbScore; return this; }
            public Builder bestCosineSimilarity(double bestCosineSimilarity) { this.bestCosineSimilarity = bestCosineSimilarity; return this; }
            public Builder scannedVoronoiCells(int scannedVoronoiCells) { this.scannedVoronoiCells = scannedVoronoiCells; return this; }
            public Builder routingLatencyNanos(long routingLatencyNanos) { this.routingLatencyNanos = routingLatencyNanos; return this; }
            public Builder receipt(McpToolRoutingAuditReceipt receipt) { this.receipt = receipt; return this; }

            public RoutingResult build() {
                return new RoutingResult(candidateTools, selectedTool, bestUcbScore, bestCosineSimilarity, scannedVoronoiCells, routingLatencyNanos, receipt);
            }
        }
    }

    /**
     * 执行两级测地 Voronoi 层次化动态路由并签发不可变凭单
     */
    public RoutingResult routeAndIssueReceipt(RoutingRequest request) {
        Objects.requireNonNull(request, "路由请求不可为空");
        long startNanos = System.nanoTime();

        rwLock.readLock().lock();
        try {
            double[] qVec = request.queryEmbedding();
            if (qVec == null || qVec.length != EMBEDDING_DIMENSION) {
                String queryText = request.userQuery() != null ? request.userQuery() : "DEFAULT_QUERY";
                qVec = StreamingToolCentroidDistiller.generateDeterministicSphericalEmbedding(queryText);
            }

            int k = request.topK() != null && request.topK() > 0 ? request.topK() : DEFAULT_TOP_K;
            int branchLimit = request.branchCells() != null && request.branchCells() > 0 ? request.branchCells() : DEFAULT_BRANCH_CELLS;
            double alpha = request.alphaSemantic() != null ? request.alphaSemantic() : DEFAULT_ALPHA_SEMANTIC;
            long sysCalls = totalSystemInvocations.get();

            // 若注册工具极少，直接全量返回
            if (activeToolsRegistry.size() <= k) {
                List<McpToolDescriptor> candidates = new ArrayList<>(activeToolsRegistry.values());
                McpToolDescriptor selected = candidates.isEmpty() ? null : candidates.get(0);
                double sim = selected != null ? selected.cosineSimilarity(qVec) : 0.0;
                long latency = System.nanoTime() - startNanos;

                McpToolRoutingAuditReceipt receipt = McpToolRoutingAuditReceipt.create(
                        "RCP-MCP-ROUTER-" + UUID.randomUUID().toString().substring(0, 8),
                        tenantId,
                        request.taskId() != null ? request.taskId() : "task-001",
                        request.traceId() != null ? request.traceId() : "trace-001",
                        computeSha256Hex(request.userQuery()),
                        activeToolsRegistry.size(),
                        voronoiCells.size(),
                        voronoiCells.size(),
                        candidates.stream().map(McpToolDescriptor::toolId).toList(),
                        selected != null ? selected.toolId() : "NONE",
                        sim,
                        sim,
                        0,
                        0,
                        1.0,
                        1.0,
                        latency,
                        0,
                        System.currentTimeMillis()
                );

                return RoutingResult.builder()
                        .candidateTools(candidates)
                        .selectedTool(selected)
                        .bestUcbScore(sim)
                        .bestCosineSimilarity(sim)
                        .scannedVoronoiCells(voronoiCells.size())
                        .routingLatencyNanos(latency)
                        .receipt(receipt)
                        .build();
            }

            // 阶段 1: 粗筛 Voronoi 胞腔 (基于测地大圆弧三角不等式的 Branch-and-Bound 分支定界)
            List<VoronoiCell> rankedCells = new ArrayList<>(voronoiCells);
            final double[] finalQVec = qVec;
            rankedCells.sort((a, b) -> {
                double dotA = computeCosineDot(a.getCentroid(), finalQVec);
                double geoA = Math.acos(Math.max(-1.0, Math.min(1.0, dotA)));
                double boundA = Math.cos(Math.max(0.0, geoA - a.getCoverageRadius()));

                double dotB = computeCosineDot(b.getCentroid(), finalQVec);
                double geoB = Math.acos(Math.max(-1.0, Math.min(1.0, dotB)));
                double boundB = Math.cos(Math.max(0.0, geoB - b.getCoverageRadius()));

                return Double.compare(boundB, boundA);
            });

            int selectedCellsCount = Math.min(rankedCells.size(), branchLimit);
            Set<McpToolDescriptor> candidatePool = new HashSet<>();
            for (int i = 0; i < selectedCellsCount; i++) {
                candidatePool.addAll(rankedCells.get(i).getMemberTools());
            }

            // 阶段 2: 细筛工具并结合 UCB 动态打分排序
            record ScoredTool(McpToolDescriptor tool, double ucbScore, double cosineSim) {}
            List<ScoredTool> scoredList = new ArrayList<>();

            for (McpToolDescriptor tool : candidatePool) {
                double cos = tool.cosineSimilarity(qVec);
                ToolArmTelemetry telem = telemetryMap.computeIfAbsent(tool.toolId(), ToolArmTelemetry::new);
                double ucb = telem.computeUcbScore(cos, sysCalls, alpha);
                scoredList.add(new ScoredTool(tool, ucb, cos));
            }

            scoredList.sort((a, b) -> Double.compare(b.ucbScore(), a.ucbScore()));

            List<McpToolDescriptor> topCandidates = new ArrayList<>();
            int limit = Math.min(scoredList.size(), k);
            for (int i = 0; i < limit; i++) {
                topCandidates.add(scoredList.get(i).tool());
            }

            ScoredTool bestScored = scoredList.isEmpty() ? null : scoredList.get(0);
            McpToolDescriptor selected = bestScored != null ? bestScored.tool() : null;
            double bestUcb = bestScored != null ? bestScored.ucbScore() : 0.0;
            double bestCos = bestScored != null ? bestScored.cosineSim() : 0.0;

            long latencyNanos = System.nanoTime() - startNanos;

            // 阶段 3: 签发不可变自验真凭单
            String queryHash = computeSha256Hex(request.userQuery());
            String receiptId = "RCP-MCP-ROUTER-" + UUID.randomUUID().toString().substring(0, 8);
            McpToolRoutingAuditReceipt receipt = McpToolRoutingAuditReceipt.create(
                    receiptId,
                    tenantId,
                    request.taskId() != null ? request.taskId() : "task-001",
                    request.traceId() != null ? request.traceId() : "trace-001",
                    queryHash,
                    activeToolsRegistry.size(),
                    voronoiCells.size(),
                    selectedCellsCount,
                    topCandidates.stream().map(McpToolDescriptor::toolId).toList(),
                    selected != null ? selected.toolId() : "NONE",
                    bestUcb,
                    bestCos,
                    0,
                    0,
                    1.0,
                    1.0,
                    latencyNanos,
                    0,
                    System.currentTimeMillis()
            );

            return RoutingResult.builder()
                    .candidateTools(topCandidates)
                    .selectedTool(selected)
                    .bestUcbScore(bestUcb)
                    .bestCosineSimilarity(bestCos)
                    .scannedVoronoiCells(selectedCellsCount)
                    .routingLatencyNanos(latencyNanos)
                    .receipt(receipt)
                    .build();

        } finally {
            rwLock.readLock().unlock();
        }
    }

    private static double computeCosineDot(double[] u, double[] v) {
        if (u == null || v == null || u.length != EMBEDDING_DIMENSION || v.length != EMBEDDING_DIMENSION) {
            return 0.0;
        }
        double dot = 0.0;
        int limit = EMBEDDING_DIMENSION - 7;
        int i = 0;
        for (; i < limit; i += 8) {
            dot += u[i] * v[i]
                    + u[i + 1] * v[i + 1]
                    + u[i + 2] * v[i + 2]
                    + u[i + 3] * v[i + 3]
                    + u[i + 4] * v[i + 4]
                    + u[i + 5] * v[i + 5]
                    + u[i + 6] * v[i + 6]
                    + u[i + 7] * v[i + 7];
        }
        for (; i < EMBEDDING_DIMENSION; i++) {
            dot += u[i] * v[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private static String computeSha256Hex(String text) {
        if (text == null) text = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "hash-fallback";
        }
    }
}
