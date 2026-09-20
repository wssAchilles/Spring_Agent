package tech.qiantong.qknow.ai.rag.hierarchical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.rag.hierarchical.model.HierarchicalGraphRagReceipt;

import java.util.*;

/**
 * Phase 123 核心资产：高保真 GraphRAG 深度融合总控中枢 (HierarchicalGraphRagMetacenter)
 * 落实“支柱三：高保真 RAG 知识引擎与多模态图谱”战略定位：
 * 1. 统一调度四级文档金字塔分支定界剪枝与自底向上语义质心聚合；
 * 2. 调度千问 1536 维测地 PPR 与 Steiner 最小因果树剪枝，消除多跳组合爆炸；
 * 3. 构造双轨因果骨架 Prompt 并注入 DeepSeek API 思考流规范；
 * 4. 实施 \tau_{ground} >= 0.88 反幻觉接地安全硬门禁；
 * 5. 全链路自动化签发并校验不可变存证凭单 {@link HierarchicalGraphRagReceipt}。
 */
@Component
public class HierarchicalGraphRagMetacenter {

    private static final Logger log = LoggerFactory.getLogger(HierarchicalGraphRagMetacenter.class);

    public static final String DEFAULT_HMAC_SECRET = "qknow_graphrag_fusion_secret_phase123";

    private final HierarchicalPyramidDocumentChunker chunker;
    private final SteinerCausalSubgraphEngine steinerEngine;
    private final GraphFactGroundingGate groundingGate;

    public HierarchicalGraphRagMetacenter() {
        this(new HierarchicalPyramidDocumentChunker(), new SteinerCausalSubgraphEngine(), new GraphFactGroundingGate());
    }

    public HierarchicalGraphRagMetacenter(
            HierarchicalPyramidDocumentChunker chunker,
            SteinerCausalSubgraphEngine steinerEngine,
            GraphFactGroundingGate groundingGate
    ) {
        this.chunker = Objects.requireNonNull(chunker, "文档切分器不能为空");
        this.steinerEngine = Objects.requireNonNull(steinerEngine, "Steiner 因果树引擎不能为空");
        this.groundingGate = Objects.requireNonNull(groundingGate, "接地门禁不能为空");
    }

    /**
     * 端到端执行流水线产物
     */
    public record GraphRagContext(
            String alignedPrompt,
            List<HierarchicalPyramidDocumentChunker.PyramidNode> hitChunks,
            HierarchicalPyramidDocumentChunker.SearchResult searchResult,
            SteinerCausalSubgraphEngine.SteinerSubgraphResult steinerSubgraph,
            GraphFactGroundingGate.GroundingResult groundingResult,
            HierarchicalGraphRagReceipt receipt
    ) {
        public boolean isPassed() {
            return receipt != null && HierarchicalGraphRagReceipt.STATUS_PASSED.equals(receipt.executionStatus());
        }
    }

    /**
     * 执行高保真 GraphRAG 深度融合全链路调度流水线
     *
     * @param sessionId                会话唯一标识
     * @param query                    用户查询文本
     * @param queryEmbedding1536       阿里千问 1536 维超球面单位查询向量
     * @param docRoot                  长文档四级金字塔树根节点
     * @param allGraphNodes            知识图谱候选实体全集
     * @param allGraphEdges            知识图谱候选边全集
     * @param seedEntityIds            查询命中的种子实体集合
     * @param candidateTriplesToVerify 生成文本中提取的三元组事实 (可为空)
     * @param secretKey                存证凭单签名密钥
     * @return 深度融合上下文及安全存证凭单
     */
    public GraphRagContext executeFusionPipeline(
            String sessionId,
            String query,
            float[] queryEmbedding1536,
            HierarchicalPyramidDocumentChunker.PyramidNode docRoot,
            Collection<SteinerCausalSubgraphEngine.GraphEntity> allGraphNodes,
            Collection<SteinerCausalSubgraphEngine.GraphEdge> allGraphEdges,
            Set<String> seedEntityIds,
            List<GraphFactGroundingGate.FactTriple> candidateTriplesToVerify,
            String secretKey
    ) {
        long startNano = System.nanoTime();
        String receiptId = "rcpt_rag_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestampEpochMs = System.currentTimeMillis();
        String effectiveSecret = (secretKey != null && !secretKey.isBlank()) ? secretKey : DEFAULT_HMAC_SECRET;

        // 1. 长文档四级金字塔分支定界剪枝搜索 (Step 1: Multi-scale Branch & Bound Search)
        HierarchicalPyramidDocumentChunker.SearchResult searchResult = null;
        List<HierarchicalPyramidDocumentChunker.PyramidNode> hitChunks = Collections.emptyList();
        List<String> representativePath = List.of("UNKNOWN_DOC_PATH");

        if (docRoot != null) {
            searchResult = chunker.searchByBranchAndBound(docRoot, queryEmbedding1536, 0.20, 5);
            hitChunks = searchResult.hitLeafNodes();
            if (!hitChunks.isEmpty()) {
                representativePath = hitChunks.get(0).getPath();
            }
        }

        // 2. 测地 PPR 与 Steiner 最小因果树紧致提取 (Step 2: Geodesic PPR & Steiner Causal Tree)
        SteinerCausalSubgraphEngine.SteinerSubgraphResult steinerResult = steinerEngine.extractSteinerCausalSubgraph(
                allGraphNodes, allGraphEdges, seedEntityIds, queryEmbedding1536
        );

        // 3. 构建双轨对齐结构化 Prompt (Step 3: Dual-track Prompt Construction)
        String alignedPrompt = groundingGate.buildDualTrackPrompt(query, hitChunks, steinerResult);

        // 4. 执行反幻觉接地安全门禁判定 (Step 4: Anti-hallucination Grounding Gate Evaluation)
        GraphFactGroundingGate.GroundingResult groundingResult = groundingGate.evaluateGrounding(
                candidateTriplesToVerify, steinerResult
        );

        long latencyMicros = (System.nanoTime() - startNano) / 1000L;

        // 5. 签发不可变存证凭单 (Step 5: Issue Immutable Receipt)
        HierarchicalGraphRagReceipt receipt = HierarchicalGraphRagReceipt.create(
                receiptId,
                sessionId,
                query,
                representativePath,
                steinerResult.steinerTopologyHash(),
                steinerResult.nodes().size(),
                steinerResult.pprEntropy(),
                groundingResult.groundingScore(),
                groundingResult.executionStatus(),
                latencyMicros,
                timestampEpochMs,
                effectiveSecret
        );

        return new GraphRagContext(
                alignedPrompt,
                hitChunks,
                searchResult,
                steinerResult,
                groundingResult,
                receipt
        );
    }

    /**
     * 检验融合存证凭单有效性 (常量时间防篡改)
     *
     * @param receipt   待验真凭单
     * @param secretKey 签名密钥
     * @return true-合法有效, false-被篡改或非法
     */
    public boolean verifyReceipt(HierarchicalGraphRagReceipt receipt, String secretKey) {
        if (receipt == null) return false;
        String effectiveSecret = (secretKey != null && !secretKey.isBlank()) ? secretKey : DEFAULT_HMAC_SECRET;
        return receipt.verifySignature(effectiveSecret);
    }

    public HierarchicalPyramidDocumentChunker getChunker() {
        return chunker;
    }

    public SteinerCausalSubgraphEngine getSteinerEngine() {
        return steinerEngine;
    }

    public GraphFactGroundingGate getGroundingGate() {
        return groundingGate;
    }
}
