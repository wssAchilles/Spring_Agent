package tech.qiantong.qknow.hermes.memory.bitemporal;

import tech.qiantong.qknow.hermes.memory.bitemporal.causal.PredicateCardinality;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双时态四层稠密-稀疏海马体持续记忆超图引擎（对应博士论文第 4 章核心引擎）。
 * <p>
 * 统合 HippoRAG 2（稠密段落-稀疏短语二分图 + 识别记忆过滤 + 源感知引用计数）、
 * Zep/Graphiti（双时态 [t_valid, t_invalid) × [t'_created, t'_expired) 零硬删除矛盾截断）与
 * LLM-Wiki（编译知识页 + Error-Book 错题排斥势场）。
 * </p>
 */
public class BiTemporalHippocampalGraphEngine {

    /** S^1023 平方弦距离矛盾判定阈值（d^2 = 2*(1 - cos) > 0.35 时判定单值属性发生事实更替） */
    public static final double DEFAULT_CONTRADICTION_CHORD_SQ_THRESHOLD = 0.35;
    /** HippoRAG 2 标准段落重置抑制因子 beta = 0.05 */
    public static final double PASSAGE_RESET_DAMPING_BETA = 0.05;
    /** PPR 默认重启阻尼系数 alpha = 0.15 */
    public static final float DEFAULT_PPR_ALPHA = 0.15f;
    private static final int COLD_RING_CAPACITY = 512;

    private final String hmacSecretKey;
    private final javax.sql.DataSource dataSource;
    private volatile boolean schemaEnsured = false;
    private final Map<String, HippocampalVertex> vertices = new ConcurrentHashMap<>();
    // 全量双时态账本边（含历史截断视图，以支持任意 (t*, t'*) 截面回溯）
    private final Map<String, BiTemporalHyperEdge> ledgerEdges = new LinkedHashMap<>();
    // 源感知引用计数表: edgeId -> 贡献该事实边的文档源集合
    private final Map<String, Set<String>> edgeDocumentSources = new HashMap<>();
    // Phase 8.1 扩展索引：谓词基数、因果前驱边映射与 SUPERSEDES 可逆审计账本
    private final Map<String, PredicateCardinality> edgeCardinalities = new HashMap<>();
    private final Map<String, String> edgeCausalPredecessor = new HashMap<>();
    private final Map<String, List<String>> supersedesTargetByNewEdge = new HashMap<>();
    private final Map<String, List<String>> createdHistoryViewsByNewEdge = new HashMap<>();
    // 三级冷备可逆环形归档区（严禁物理硬删除）
    private final Deque<BiTemporalHyperEdge> coldStandbyRing = new ArrayDeque<>(COLD_RING_CAPACITY);

    public BiTemporalHippocampalGraphEngine() {
        this((javax.sql.DataSource) null, "hermes-hippocampal-bitemporal-secret");
    }

    public BiTemporalHippocampalGraphEngine(String hmacSecretKey) {
        this((javax.sql.DataSource) null, hmacSecretKey);
    }

    public BiTemporalHippocampalGraphEngine(javax.sql.DataSource dataSource, String hmacSecretKey) {
        this.dataSource = dataSource;
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-hippocampal-bitemporal-secret"
                : hmacSecretKey;
        if (this.dataSource != null) {
            ensureSchemaIfNeeded();
            loadAllFromJdbc();
        }
    }

    public void upsertVertex(HippocampalVertex vertex) {
        Objects.requireNonNull(vertex, "vertex 不能为空");
        vertices.put(vertex.nodeId(), vertex);
        persistVertexToJdbc(vertex);
    }

    public Optional<HippocampalVertex> getVertex(String nodeId) {
        return Optional.ofNullable(vertices.get(nodeId));
    }

    /**
     * 摄入一条事实关系边，自动执行源感知引用计数累加或双时态非破坏性矛盾边截断（定理 4.1(3)）。
     */
    public synchronized BiTemporalHyperEdge ingestFactEdge(
            String sourceNodeId,
            String targetNodeId,
            String relationPredicate,
            boolean functionalExclusive,
            float[] unitEmbedding1024,
            long validFromEpochMs,
            long txNowEpochMs,
            String sourceDocId
    ) {
        PredicateCardinality card = functionalExclusive
                ? PredicateCardinality.FUNCTIONAL
                : PredicateCardinality.MULTI_VALUED;
        return ingestEdgeInternal(
                sourceNodeId, targetNodeId, relationPredicate, functionalExclusive, card,
                unitEmbedding1024, validFromEpochMs, txNowEpochMs, sourceDocId, null
        );
    }

    /**
     * 摄入带显式谓词基数与因果前驱指向的双时态演化边（Phase 8.1 定理 8.1 核心写入原语）。
     */
    public synchronized BiTemporalHyperEdge ingestCausalFactEdge(
            String sourceNodeId,
            String targetNodeId,
            String relationPredicate,
            PredicateCardinality cardinality,
            float[] unitEmbedding1024,
            long validFromEpochMs,
            long txNowEpochMs,
            String sourceDocId,
            String causalPredecessorEdgeId
    ) {
        PredicateCardinality safeCard = cardinality != null ? cardinality : PredicateCardinality.MULTI_VALUED;
        boolean exclusive = safeCard == PredicateCardinality.FUNCTIONAL;
        return ingestEdgeInternal(
                sourceNodeId, targetNodeId, relationPredicate, exclusive, safeCard,
                unitEmbedding1024, validFromEpochMs, txNowEpochMs, sourceDocId, causalPredecessorEdgeId
        );
    }

    private BiTemporalHyperEdge ingestEdgeInternal(
            String sourceNodeId,
            String targetNodeId,
            String relationPredicate,
            boolean functionalExclusive,
            PredicateCardinality cardinality,
            float[] unitEmbedding1024,
            long validFromEpochMs,
            long txNowEpochMs,
            String sourceDocId,
            String causalPredecessorEdgeId
    ) {
        String effectiveDoc = (sourceDocId == null || sourceDocId.isBlank()) ? "__default_doc__" : sourceDocId;

        // 1. 检查是否已存在完全等价的当前活跃三元组 (u, r, v)：若存在且非 COUNTABLE_EVENT 则累加引用计数并落库
        if (cardinality != PredicateCardinality.COUNTABLE_EVENT) {
            for (BiTemporalHyperEdge existing : ledgerEdges.values()) {
                if (existing.storageTier() == ColdStandbyTier.ACTIVE_HOT
                        && existing.txToEpochMs() == null
                        && existing.validToEpochMs() == null
                        && existing.sourceNodeId().equals(sourceNodeId)
                        && existing.relationPredicate().equals(relationPredicate)
                        && existing.targetNodeId().equals(targetNodeId)) {
                    Set<String> docs = edgeDocumentSources.computeIfAbsent(existing.edgeId(), k -> new LinkedHashSet<>());
                    docs.add(effectiveDoc);
                    BiTemporalHyperEdge updatedCountEdge = BiTemporalHyperEdge.createSigned(
                            existing.edgeId(), existing.sourceNodeId(), existing.targetNodeId(),
                            existing.relationPredicate(), existing.functionalExclusive(),
                            existing.unitEmbedding1024(), existing.validFromEpochMs(), existing.validToEpochMs(),
                            existing.txFromEpochMs(), null, docs.size(), ColdStandbyTier.ACTIVE_HOT, hmacSecretKey
                    );
                    ledgerEdges.put(existing.edgeId(), updatedCountEdge);
                    persistEdgeToJdbc(updatedCountEdge, docs);
                    return updatedCountEdge;
                }
            }
        }

        // 2. 生成新边 ID 并在 FUNCTIONAL 同主语活跃槽位内执行精准双时态非破坏性截断（根治全图误杀与历史Top-10挤占）
        String newEdgeId = "bte_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<String> supersededIds = new ArrayList<>();
        List<String> createdHistIds = new ArrayList<>();

        if (functionalExclusive) {
            List<BiTemporalHyperEdge> activeSameSlot = new ArrayList<>();
            for (BiTemporalHyperEdge e : ledgerEdges.values()) {
                if (e.storageTier() == ColdStandbyTier.ACTIVE_HOT
                        && e.txToEpochMs() == null
                        && e.sourceNodeId().equals(sourceNodeId)
                        && e.relationPredicate().equals(relationPredicate)
                        && (e.validToEpochMs() == null || e.validToEpochMs() > validFromEpochMs)) {
                    activeSameSlot.add(e);
                }
            }

            for (BiTemporalHyperEdge oldEdge : activeSameSlot) {
                double chordSq = squaredChordDistance(unitEmbedding1024, oldEdge.unitEmbedding1024());
                if (!oldEdge.targetNodeId().equals(targetNodeId) || chordSq > DEFAULT_CONTRADICTION_CHORD_SQ_THRESHOLD) {
                    // 零物理硬删除！执行双时态原子截断：
                    // (a) 将原无界记录在事务轴封账 txTo <- txNow，并归入三级冷备环
                    BiTemporalHyperEdge sealedTxOld = BiTemporalHyperEdge.createSigned(
                            oldEdge.edgeId(), oldEdge.sourceNodeId(), oldEdge.targetNodeId(),
                            oldEdge.relationPredicate(), oldEdge.functionalExclusive(),
                            oldEdge.unitEmbedding1024(), oldEdge.validFromEpochMs(), oldEdge.validToEpochMs(),
                            oldEdge.txFromEpochMs(), txNowEpochMs, oldEdge.sourceReferenceCount(),
                            ColdStandbyTier.WARM_QUARANTINE, hmacSecretKey
                    );
                    ledgerEdges.put(oldEdge.edgeId(), sealedTxOld);
                    persistEdgeToJdbc(sealedTxOld, edgeDocumentSources.getOrDefault(oldEdge.edgeId(), Set.of()));
                    pushToColdStandbyRing(sealedTxOld);
                    supersededIds.add(oldEdge.edgeId());

                    // (b) 若旧事实生效起点严格早于新事实生效起点，插入历史截断有效视图 [validOld, validNew) × [txNow, +inf)
                    if (oldEdge.validFromEpochMs() < validFromEpochMs) {
                        String histId = oldEdge.edgeId() + "_hist_" + txNowEpochMs;
                        BiTemporalHyperEdge truncatedHistoryView = BiTemporalHyperEdge.createSigned(
                                histId, oldEdge.sourceNodeId(), oldEdge.targetNodeId(),
                                oldEdge.relationPredicate(), oldEdge.functionalExclusive(),
                                oldEdge.unitEmbedding1024(), oldEdge.validFromEpochMs(), validFromEpochMs,
                                txNowEpochMs, null, oldEdge.sourceReferenceCount(),
                                ColdStandbyTier.ACTIVE_HOT, hmacSecretKey
                        );
                        ledgerEdges.put(histId, truncatedHistoryView);
                        edgeCardinalities.put(histId, cardinality);
                        persistEdgeToJdbc(truncatedHistoryView,
                                edgeDocumentSources.getOrDefault(oldEdge.edgeId(), Set.of()));
                        createdHistIds.add(histId);
                    }
                }
            }
        }

        // 3. 插入新活跃边 [validFrom, +inf) × [txNow, +inf) 并记录基数与因果前驱映射
        Set<String> sources = new LinkedHashSet<>();
        sources.add(effectiveDoc);
        edgeDocumentSources.put(newEdgeId, sources);
        edgeCardinalities.put(newEdgeId, cardinality);
        if (causalPredecessorEdgeId != null && !causalPredecessorEdgeId.isBlank()) {
            edgeCausalPredecessor.put(newEdgeId, causalPredecessorEdgeId);
        }
        if (!supersededIds.isEmpty()) {
            supersedesTargetByNewEdge.put(newEdgeId, supersededIds);
        }
        if (!createdHistIds.isEmpty()) {
            createdHistoryViewsByNewEdge.put(newEdgeId, createdHistIds);
        }

        BiTemporalHyperEdge freshEdge = BiTemporalHyperEdge.createSigned(
                newEdgeId, sourceNodeId, targetNodeId, relationPredicate, functionalExclusive,
                unitEmbedding1024, validFromEpochMs, null, txNowEpochMs, null,
                1, ColdStandbyTier.ACTIVE_HOT, hmacSecretKey
        );
        ledgerEdges.put(newEdgeId, freshEdge);
        persistEdgeToJdbc(freshEdge, sources);
        return freshEdge;
    }

    /**
     * 定理 8.1(3) 无损可逆逆算子：撤销误注入的新覆写边，将其软隔离至三级冷备环并 100% 恢复被截断的原活跃事实边。
     */
    public synchronized boolean revertSupersedeEdge(String erroneousNewEdgeId, long txRevertEpochMs) {
        if (erroneousNewEdgeId == null || erroneousNewEdgeId.isBlank()) {
            return false;
        }
        BiTemporalHyperEdge errEdge = ledgerEdges.get(erroneousNewEdgeId);
        if (errEdge == null || errEdge.txToEpochMs() != null) {
            return false;
        }
        // 1. 软隔离误边本身与它派生的 _hist_ 历史截断视图（严禁物理硬删除）
        BiTemporalHyperEdge quarantinedErr = BiTemporalHyperEdge.createSigned(
                errEdge.edgeId(), errEdge.sourceNodeId(), errEdge.targetNodeId(),
                errEdge.relationPredicate(), errEdge.functionalExclusive(),
                errEdge.unitEmbedding1024(), errEdge.validFromEpochMs(), errEdge.validToEpochMs(),
                errEdge.txFromEpochMs(), txRevertEpochMs, errEdge.sourceReferenceCount(),
                ColdStandbyTier.WARM_QUARANTINE, hmacSecretKey
        );
        ledgerEdges.put(errEdge.edgeId(), quarantinedErr);
        persistEdgeToJdbc(quarantinedErr, edgeDocumentSources.getOrDefault(errEdge.edgeId(), Set.of()));
        pushToColdStandbyRing(quarantinedErr);

        for (String histId : createdHistoryViewsByNewEdge.getOrDefault(erroneousNewEdgeId, List.of())) {
            BiTemporalHyperEdge hist = ledgerEdges.get(histId);
            if (hist != null && hist.txToEpochMs() == null) {
                BiTemporalHyperEdge quarantinedHist = BiTemporalHyperEdge.createSigned(
                        hist.edgeId(), hist.sourceNodeId(), hist.targetNodeId(),
                        hist.relationPredicate(), hist.functionalExclusive(),
                        hist.unitEmbedding1024(), hist.validFromEpochMs(), hist.validToEpochMs(),
                        hist.txFromEpochMs(), txRevertEpochMs, hist.sourceReferenceCount(),
                        ColdStandbyTier.WARM_QUARANTINE, hmacSecretKey
                );
                ledgerEdges.put(histId, quarantinedHist);
                persistEdgeToJdbc(quarantinedHist, edgeDocumentSources.getOrDefault(histId, Set.of()));
                pushToColdStandbyRing(quarantinedHist);
            }
        }

        // 2. 从三级冷备环与历史台账精确还原被覆写的原活跃旧边
        List<String> targets = supersedesTargetByNewEdge.getOrDefault(erroneousNewEdgeId, List.of());
        for (String oldId : targets) {
            BiTemporalHyperEdge sealedOld = findInColdRingOrLedger(oldId);
            if (sealedOld != null) {
                BiTemporalHyperEdge restoredOld = BiTemporalHyperEdge.createSigned(
                        sealedOld.edgeId(), sealedOld.sourceNodeId(), sealedOld.targetNodeId(),
                        sealedOld.relationPredicate(), sealedOld.functionalExclusive(),
                        sealedOld.unitEmbedding1024(), sealedOld.validFromEpochMs(), sealedOld.validToEpochMs(),
                        sealedOld.txFromEpochMs(), null, sealedOld.sourceReferenceCount(),
                        ColdStandbyTier.ACTIVE_HOT, hmacSecretKey
                );
                ledgerEdges.put(oldId, restoredOld);
                persistEdgeToJdbc(restoredOld, edgeDocumentSources.getOrDefault(oldId, Set.of()));
                coldStandbyRing.removeIf(e -> oldId.equals(e.edgeId()));
            }
        }
        return true;
    }

    /**
     * 双时态演化窗口查询算子：提取在事务快照时刻 {@code txAtEpochMs} 有效且有效区间与 {@code [validFrom, validTo]} 重叠的活跃事实边。
     */
    public synchronized List<BiTemporalHyperEdge> queryEvolutionWindow(
            long validFromEpochMs,
            long validToEpochMs,
            long txAtEpochMs
    ) {
        long lo = Math.min(validFromEpochMs, validToEpochMs);
        long hi = Math.max(validFromEpochMs, validToEpochMs);
        List<BiTemporalHyperEdge> result = new ArrayList<>();
        for (BiTemporalHyperEdge e : ledgerEdges.values()) {
            if (e.sourceReferenceCount() <= 0) {
                continue;
            }
            boolean txMatch = txAtEpochMs >= e.txFromEpochMs()
                    && (e.txToEpochMs() == null || txAtEpochMs < e.txToEpochMs());
            if (!txMatch) {
                continue;
            }
            boolean overlaps = e.validFromEpochMs() <= hi
                    && (e.validToEpochMs() == null || e.validToEpochMs() > lo);
            if (overlaps) {
                result.add(e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized String getCausalPredecessor(String edgeId) {
        return edgeId == null ? null : edgeCausalPredecessor.get(edgeId);
    }

    public synchronized PredicateCardinality getCardinality(String edgeId) {
        if (edgeId == null) {
            return PredicateCardinality.MULTI_VALUED;
        }
        PredicateCardinality explicit = edgeCardinalities.get(edgeId);
        if (explicit != null) {
            return explicit;
        }
        BiTemporalHyperEdge e = ledgerEdges.get(edgeId);
        return (e != null && e.functionalExclusive())
                ? PredicateCardinality.FUNCTIONAL
                : PredicateCardinality.MULTI_VALUED;
    }

    public synchronized List<String> getSupersededEdgeIds(String newEdgeId) {
        List<String> list = newEdgeId == null ? null : supersedesTargetByNewEdge.get(newEdgeId);
        return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    private BiTemporalHyperEdge findInColdRingOrLedger(String edgeId) {
        for (BiTemporalHyperEdge e : coldStandbyRing) {
            if (edgeId.equals(e.edgeId())) {
                return e;
            }
        }
        return ledgerEdges.get(edgeId);
    }

    /**
     * 安全卸载单篇文档源：递减关联事实的引用计数，仅当引用计数归零时软隔离至温备层（绝不误伤多文档共享事实）。
     */
    public synchronized int detachDocumentSource(String sourceDocId, long txNowEpochMs) {
        int quarantinedCount = 0;
        List<String> keys = new ArrayList<>(ledgerEdges.keySet());
        for (String edgeId : keys) {
            Set<String> docs = edgeDocumentSources.get(edgeId);
            if (docs == null || !docs.remove(sourceDocId)) {
                continue;
            }
            BiTemporalHyperEdge curr = ledgerEdges.get(edgeId);
            if (curr == null || curr.txToEpochMs() != null) {
                continue;
            }
            if (docs.isEmpty()) {
                BiTemporalHyperEdge retired = BiTemporalHyperEdge.createSigned(
                        curr.edgeId(), curr.sourceNodeId(), curr.targetNodeId(), curr.relationPredicate(),
                        curr.functionalExclusive(), curr.unitEmbedding1024(),
                        curr.validFromEpochMs(), curr.validToEpochMs(), curr.txFromEpochMs(), txNowEpochMs,
                        0, ColdStandbyTier.WARM_QUARANTINE, hmacSecretKey
                );
                ledgerEdges.put(edgeId, retired);
                persistEdgeToJdbc(retired, docs);
                pushToColdStandbyRing(retired);
                quarantinedCount++;
            } else {
                BiTemporalHyperEdge decremented = BiTemporalHyperEdge.createSigned(
                        curr.edgeId(), curr.sourceNodeId(), curr.targetNodeId(), curr.relationPredicate(),
                        curr.functionalExclusive(), curr.unitEmbedding1024(),
                        curr.validFromEpochMs(), curr.validToEpochMs(), curr.txFromEpochMs(), null,
                        docs.size(), curr.storageTier(), hmacSecretKey
                );
                ledgerEdges.put(edgeId, decremented);
                persistEdgeToJdbc(decremented, docs);
            }
        }
        return quarantinedCount;
    }

    /**
     * 双时态二维截面精确查询算子 G_bi |_{(validTime, txTime)}。
     */
    public synchronized List<BiTemporalHyperEdge> queryCrossSectionAsOf(
            String sourceNodeId,
            String relationPredicate,
            long validTimeEpochMs,
            long txTimeEpochMs
    ) {
        List<BiTemporalHyperEdge> result = new ArrayList<>();
        for (BiTemporalHyperEdge e : ledgerEdges.values()) {
            if (sourceNodeId != null && !sourceNodeId.equals(e.sourceNodeId())) {
                continue;
            }
            if (relationPredicate != null && !relationPredicate.equals(e.relationPredicate())) {
                continue;
            }
            if (e.isActiveAt(validTimeEpochMs, txTimeEpochMs)) {
                result.add(e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 思考链内按需迭代组合推理探针（Recognition Memory 测地过滤 + 稠密-稀疏二分图 CSR-PPR + Error-Book 排斥势场）。
     */
    public synchronized HippocampalProbeReceipt probeIterativeSubgraph(
            String queryId,
            String subQueryText,
            float[] queryUnitVec1024,
            long validTimeEpochMs,
            long txTimeEpochMs,
            int topKPassages
    ) {
        long startNs = System.nanoTime();
        List<HippocampalVertex> nodeList = new ArrayList<>(vertices.values());
        int n = nodeList.size();
        if (n == 0) {
            return new HippocampalProbeReceipt(queryId, subQueryText, 0, 0, 0, 0.0, List.of(), List.of(), List.of(), 0L);
        }

        Map<String, Integer> nodeToIdx = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            nodeToIdx.put(nodeList.get(i).nodeId(), i);
        }

        // Step 1: 识别记忆（Recognition Memory）种子过滤——在短语实体层筛选测地内积非负的高相关锚点
        double[] sims = new double[n];
        int rawPhraseCount = 0;
        List<Integer> phraseCandidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            HippocampalVertex v = nodeList.get(i);
            sims[i] = cosine(queryUnitVec1024, v.unitEmbedding1024());
            if (v.tier() == HippocampalVertexTier.PHRASE_ENTITY) {
                rawPhraseCount++;
                phraseCandidates.add(i);
            }
        }
        phraseCandidates.sort((a, b) -> Double.compare(sims[b], sims[a]));
        Set<Integer> filteredSeeds = new LinkedHashSet<>();
        for (int idx : phraseCandidates) {
            if (sims[idx] > 0.05 || filteredSeeds.isEmpty()) {
                filteredSeeds.add(idx);
                if (filteredSeeds.size() >= 6) {
                    break;
                }
            }
        }

        // Step 2: 构造 HippoRAG 2 非对称二分图重置概率向量 p0（段落层乘抑制因子 beta = 0.05，错题本节点重置为 0）
        float[] resetProb = new float[n];
        for (int i = 0; i < n; i++) {
            HippocampalVertex v = nodeList.get(i);
            double vmfAffinity = Math.exp(3.0 * (sims[i] - 1.0));
            if (v.tier() == HippocampalVertexTier.PHRASE_ENTITY && filteredSeeds.contains(i)) {
                resetProb[i] = (float) ((1.0 - PASSAGE_RESET_DAMPING_BETA) * vmfAffinity);
            } else if (v.tier() == HippocampalVertexTier.PASSAGE_EPISODE || v.tier() == HippocampalVertexTier.COMPILED_WIKI) {
                resetProb[i] = (float) (PASSAGE_RESET_DAMPING_BETA * vmfAffinity);
            } else {
                resetProb[i] = 0.0f;
            }
        }

        // Step 3: 提取双时态活跃截面无向二分传播邻接表并压缩为 CSR 稀疏矩阵
        List<List<int[]>> adjIdx = new ArrayList<>(n);
        List<List<Float>> adjWeight = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adjIdx.add(new ArrayList<>());
            adjWeight.add(new ArrayList<>());
        }

        List<String> errorWarnings = new ArrayList<>();
        for (BiTemporalHyperEdge e : ledgerEdges.values()) {
            if (!e.isActiveAt(validTimeEpochMs, txTimeEpochMs)) {
                continue;
            }
            Integer u = nodeToIdx.get(e.sourceNodeId());
            Integer v = nodeToIdx.get(e.targetNodeId());
            if (u == null || v == null) {
                continue;
            }
            HippocampalVertex targetVertex = nodeList.get(v);
            if (targetVertex.tier() == HippocampalVertexTier.ERROR_BOOK) {
                // 关联错题本节点：提取防错警示，并作为负向排斥边界不参与正向段落推荐
                if (filteredSeeds.contains(u) || sims[v] > 0.05) {
                    errorWarnings.add(targetVertex.content());
                }
                continue;
            }
            float w = (float) Math.max(0.1, 0.5 * (1.0 + cosine(queryUnitVec1024, e.unitEmbedding1024())));
            adjIdx.get(u).add(new int[]{v});
            adjWeight.get(u).add(w);
            adjIdx.get(v).add(new int[]{u});
            adjWeight.get(v).add(w);
        }

        int[] rowPtr = new int[n + 1];
        int nnz = 0;
        for (int i = 0; i < n; i++) {
            rowPtr[i] = nnz;
            nnz += adjIdx.get(i).size();
        }
        rowPtr[n] = nnz;
        int[] colIdx = new int[nnz];
        float[] weights = new float[nnz];
        int cursor = 0;
        for (int i = 0; i < n; i++) {
            List<int[]> cols = adjIdx.get(i);
            List<Float> ws = adjWeight.get(i);
            for (int j = 0; j < cols.size(); j++) {
                colIdx[cursor] = cols.get(j)[0];
                weights[cursor] = ws.get(j);
                cursor++;
            }
        }

        // Step 4: 调用 Rust FFM（或纯 Java 等价算子）执行稀疏二分图 PPR 迭代（100 步下 0.85^100 < 1e-7）
        NativeHippocampalSimdBridge.PprResult ppr = NativeHippocampalSimdBridge.solveCsrBipartitePpr(
                rowPtr, colIdx, weights, resetProb, DEFAULT_PPR_ALPHA, 100, 1e-6f
        );

        List<Integer> passageIndices = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            HippocampalVertexTier tier = nodeList.get(i).tier();
            if (tier == HippocampalVertexTier.PASSAGE_EPISODE || tier == HippocampalVertexTier.COMPILED_WIKI) {
                passageIndices.add(i);
            }
        }
        passageIndices.sort((a, b) -> Float.compare(ppr.stationaryScores()[b], ppr.stationaryScores()[a]));

        int limit = Math.max(1, topKPassages);
        List<String> recalledIds = new ArrayList<>();
        List<String> recalledTexts = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, passageIndices.size()); i++) {
            HippocampalVertex pv = nodeList.get(passageIndices.get(i));
            recalledIds.add(pv.nodeId());
            recalledTexts.add(pv.content());
        }

        long elapsedUs = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
        return new HippocampalProbeReceipt(
                queryId,
                subQueryText,
                rawPhraseCount,
                filteredSeeds.size(),
                ppr.executedIterations(),
                ppr.finalL1Residual(),
                recalledIds,
                recalledTexts,
                errorWarnings.stream().distinct().toList(),
                elapsedUs
        );
    }

    public synchronized int getColdStandbyArchiveCount() {
        return coldStandbyRing.size();
    }

    public synchronized Map<String, Object> getTelemetryStats() {
        long activeEdges = 0L;
        long sealedHistoricalEdges = 0L;
        for (BiTemporalHyperEdge edge : ledgerEdges.values()) {
            if (edge.storageTier() == ColdStandbyTier.ACTIVE_HOT && edge.txToEpochMs() == null) {
                activeEdges++;
            } else {
                sealedHistoricalEdges++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("vertexCount", vertices.size());
        stats.put("activeEdgeCount", activeEdges);
        stats.put("historicalEdgeCount", sealedHistoricalEdges);
        stats.put("coldRingCount", coldStandbyRing.size());
        stats.put("pprDampingBeta", PASSAGE_RESET_DAMPING_BETA);
        stats.put("nativeFfmLoaded", NativeHippocampalSimdBridge.isNativeFfmLoaded());
        stats.put("nativeLibPath", NativeHippocampalSimdBridge.getLoadedLibraryPath() != null
                ? NativeHippocampalSimdBridge.getLoadedLibraryPath() : "");
        return Collections.unmodifiableMap(stats);
    }

    private void pushToColdStandbyRing(BiTemporalHyperEdge edge) {
        if (coldStandbyRing.size() >= COLD_RING_CAPACITY) {
            coldStandbyRing.pollFirst();
        }
        coldStandbyRing.addLast(edge);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private static double squaredChordDistance(float[] a, float[] b) {
        return Math.max(0.0, 2.0 * (1.0 - cosine(a, b)));
    }

    /**
     * 获取当前全量顶点只读视图快照（供睡眠期谱蒸馏器分析）。
     */
    public synchronized Map<String, HippocampalVertex> getVerticesSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(vertices));
    }

    /**
     * 获取当前全量双时态账本边只读视图快照。
     */
    public synchronized Map<String, BiTemporalHyperEdge> getLedgerEdgesSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(ledgerEdges));
    }

    /**
     * 将指定冗余或弱关联超边可逆软隔离至温冷备层级并压入定长环形缓冲区（严禁物理硬删除）。
     */
    public synchronized boolean archiveEdgeToColdStandby(String edgeId, ColdStandbyTier targetTier, long txNowEpochMs) {
        if (edgeId == null || targetTier == null || targetTier == ColdStandbyTier.ACTIVE_HOT) {
            return false;
        }
        BiTemporalHyperEdge curr = ledgerEdges.get(edgeId);
        if (curr == null || curr.storageTier() != ColdStandbyTier.ACTIVE_HOT) {
            return false;
        }
        BiTemporalHyperEdge archived = BiTemporalHyperEdge.createSigned(
                curr.edgeId(), curr.sourceNodeId(), curr.targetNodeId(), curr.relationPredicate(),
                curr.functionalExclusive(), curr.unitEmbedding1024(),
                curr.validFromEpochMs(), curr.validToEpochMs(), curr.txFromEpochMs(), txNowEpochMs,
                curr.sourceReferenceCount(), targetTier, hmacSecretKey
        );
        ledgerEdges.put(edgeId, archived);
        pushToColdStandbyRing(archived);
        persistEdgeToJdbc(archived, edgeDocumentSources.getOrDefault(edgeId, Set.of()));
        return true;
    }

    /**
     * 反事实回滚接口：从温冷备软隔离层将指定边完整恢复为 ACTIVE_HOT 活跃状态。
     */
    public synchronized boolean restoreEdgeFromColdStandby(String edgeId, long txNowEpochMs) {
        if (edgeId == null) {
            return false;
        }
        BiTemporalHyperEdge curr = ledgerEdges.get(edgeId);
        if (curr == null || curr.storageTier() == ColdStandbyTier.ACTIVE_HOT) {
            return false;
        }
        int restoredRefs = Math.max(1, curr.sourceReferenceCount());
        BiTemporalHyperEdge restored = BiTemporalHyperEdge.createSigned(
                curr.edgeId(), curr.sourceNodeId(), curr.targetNodeId(), curr.relationPredicate(),
                curr.functionalExclusive(), curr.unitEmbedding1024(),
                curr.validFromEpochMs(), curr.validToEpochMs(), txNowEpochMs, null,
                restoredRefs, ColdStandbyTier.ACTIVE_HOT, hmacSecretKey
        );
        ledgerEdges.put(edgeId, restored);
        coldStandbyRing.removeIf(e -> edgeId.equals(e.edgeId()));
        persistEdgeToJdbc(restored, edgeDocumentSources.getOrDefault(edgeId, Set.of()));
        return true;
    }

    private void ensureSchemaIfNeeded() {
        if (dataSource == null || schemaEnsured) {
            return;
        }
        synchronized (this) {
            if (schemaEnsured) {
                return;
            }
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.Statement st = conn.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS hermes_hippocampal_vertex (
                            node_id VARCHAR(128) PRIMARY KEY,
                            tier VARCHAR(48) NOT NULL,
                            content TEXT NOT NULL,
                            unit_embedding_json TEXT NOT NULL,
                            created_at_ms BIGINT NOT NULL
                        )""");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS hermes_hippocampal_edge (
                            edge_id VARCHAR(128) PRIMARY KEY,
                            source_node_id VARCHAR(128) NOT NULL,
                            target_node_id VARCHAR(128) NOT NULL,
                            relation_predicate VARCHAR(128) NOT NULL,
                            functional_exclusive BOOLEAN NOT NULL DEFAULT FALSE,
                            unit_embedding_json TEXT NOT NULL,
                            valid_from_ms BIGINT NOT NULL,
                            valid_to_ms BIGINT,
                            tx_from_ms BIGINT NOT NULL,
                            tx_to_ms BIGINT,
                            source_reference_count INTEGER NOT NULL DEFAULT 1,
                            cold_standby_tier VARCHAR(48) NOT NULL,
                            source_docs_json TEXT NOT NULL,
                            hmac_signature VARCHAR(128) NOT NULL
                        )""");
                schemaEnsured = true;
            } catch (java.sql.SQLException e) {
                org.slf4j.LoggerFactory.getLogger(BiTemporalHippocampalGraphEngine.class)
                        .debug("[HippocampalPG] 幂等建表跳过或延迟: {}", e.getMessage());
            }
        }
    }

    private void persistVertexToJdbc(HippocampalVertex vertex) {
        if (dataSource == null) {
            return;
        }
        ensureSchemaIfNeeded();
        String sql = """
                INSERT INTO hermes_hippocampal_vertex (node_id, tier, content, unit_embedding_json, created_at_ms)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (node_id) DO UPDATE SET
                    tier = EXCLUDED.tier,
                    content = EXCLUDED.content,
                    unit_embedding_json = EXCLUDED.unit_embedding_json""";
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vertex.nodeId());
            ps.setString(2, vertex.tier().name());
            ps.setString(3, vertex.content());
            ps.setString(4, com.alibaba.fastjson2.JSON.toJSONString(vertex.unitEmbedding1024()));
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            org.slf4j.LoggerFactory.getLogger(BiTemporalHippocampalGraphEngine.class)
                    .debug("[HippocampalPG] 顶点双写落库跳过: {}", e.getMessage());
        }
    }

    private void persistEdgeToJdbc(BiTemporalHyperEdge edge, Set<String> sourceDocs) {
        if (dataSource == null) {
            return;
        }
        ensureSchemaIfNeeded();
        String sql = """
                INSERT INTO hermes_hippocampal_edge (
                    edge_id, source_node_id, target_node_id, relation_predicate, functional_exclusive,
                    unit_embedding_json, valid_from_ms, valid_to_ms, tx_from_ms, tx_to_ms,
                    source_reference_count, cold_standby_tier, source_docs_json, hmac_signature
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (edge_id) DO UPDATE SET
                    valid_to_ms = EXCLUDED.valid_to_ms,
                    tx_to_ms = EXCLUDED.tx_to_ms,
                    source_reference_count = EXCLUDED.source_reference_count,
                    cold_standby_tier = EXCLUDED.cold_standby_tier,
                    source_docs_json = EXCLUDED.source_docs_json,
                    hmac_signature = EXCLUDED.hmac_signature""";
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edge.edgeId());
            ps.setString(2, edge.sourceNodeId());
            ps.setString(3, edge.targetNodeId());
            ps.setString(4, edge.relationPredicate());
            ps.setBoolean(5, edge.functionalExclusive());
            ps.setString(6, com.alibaba.fastjson2.JSON.toJSONString(edge.unitEmbedding1024()));
            ps.setLong(7, edge.validFromEpochMs());
            if (edge.validToEpochMs() != null) {
                ps.setLong(8, edge.validToEpochMs());
            } else {
                ps.setNull(8, java.sql.Types.BIGINT);
            }
            ps.setLong(9, edge.txFromEpochMs());
            if (edge.txToEpochMs() != null) {
                ps.setLong(10, edge.txToEpochMs());
            } else {
                ps.setNull(10, java.sql.Types.BIGINT);
            }
            ps.setInt(11, edge.sourceReferenceCount());
            ps.setString(12, edge.storageTier().name());
            ps.setString(13, com.alibaba.fastjson2.JSON.toJSONString(sourceDocs != null ? sourceDocs : Set.of()));
            ps.setString(14, java.util.HexFormat.of().formatHex(edge.provenanceMac()));
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            org.slf4j.LoggerFactory.getLogger(BiTemporalHippocampalGraphEngine.class)
                    .debug("[HippocampalPG] 超边双写落库跳过: {}", e.getMessage());
        }
    }

    /**
     * 启动热加载：从 PostgreSQL 16 全量恢复双时态顶点与超边，并对每条边执行 MAC 篡改校验。
     */
    public synchronized int loadAllFromJdbc() {
        if (dataSource == null) {
            return 0;
        }
        ensureSchemaIfNeeded();
        int loadedEdges = 0;
        try (java.sql.Connection conn = dataSource.getConnection()) {
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT node_id, tier, content, unit_embedding_json FROM hermes_hippocampal_vertex");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    float[] vec = com.alibaba.fastjson2.JSON.parseObject(
                            rs.getString("unit_embedding_json"), float[].class);
                    HippocampalVertex v = new HippocampalVertex(
                            rs.getString("node_id"),
                            rs.getString("content"),
                            HippocampalVertexTier.valueOf(rs.getString("tier")),
                            vec
                    );
                    vertices.put(v.nodeId(), v);
                }
            }
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT edge_id, source_node_id, target_node_id, relation_predicate, functional_exclusive, "
                            + "unit_embedding_json, valid_from_ms, valid_to_ms, tx_from_ms, tx_to_ms, "
                            + "source_reference_count, cold_standby_tier, source_docs_json, hmac_signature "
                            + "FROM hermes_hippocampal_edge");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    float[] vec = com.alibaba.fastjson2.JSON.parseObject(
                            rs.getString("unit_embedding_json"), float[].class);
                    Long validTo = rs.getObject("valid_to_ms") != null ? rs.getLong("valid_to_ms") : null;
                    Long txTo = rs.getObject("tx_to_ms") != null ? rs.getLong("tx_to_ms") : null;
                    byte[] macBytes = java.util.HexFormat.of().parseHex(rs.getString("hmac_signature"));
                    BiTemporalHyperEdge edge = new BiTemporalHyperEdge(
                            rs.getString("edge_id"),
                            rs.getString("source_node_id"),
                            rs.getString("target_node_id"),
                            rs.getString("relation_predicate"),
                            rs.getBoolean("functional_exclusive"),
                            vec,
                            rs.getLong("valid_from_ms"),
                            validTo,
                            rs.getLong("tx_from_ms"),
                            txTo,
                            rs.getInt("source_reference_count"),
                            ColdStandbyTier.valueOf(rs.getString("cold_standby_tier")),
                            macBytes
                    );
                    if (!edge.verifyMac(hmacSecretKey)) {
                        throw new SecurityException("海马体双时态超边 MAC 验签失败: edgeId=" + edge.edgeId());
                    }
                    ledgerEdges.put(edge.edgeId(), edge);
                    List<String> docs = com.alibaba.fastjson2.JSON.parseArray(
                            rs.getString("source_docs_json"), String.class);
                    edgeDocumentSources.put(edge.edgeId(), new LinkedHashSet<>(docs != null ? docs : List.of()));
                    if (edge.storageTier() != ColdStandbyTier.ACTIVE_HOT) {
                        pushToColdStandbyRing(edge);
                    }
                    loadedEdges++;
                }
            }
        } catch (java.sql.SQLException e) {
            org.slf4j.LoggerFactory.getLogger(BiTemporalHippocampalGraphEngine.class)
                    .debug("[HippocampalPG] 热加载跳过或未初始化: {}", e.getMessage());
        }
        return loadedEdges;
    }
}
