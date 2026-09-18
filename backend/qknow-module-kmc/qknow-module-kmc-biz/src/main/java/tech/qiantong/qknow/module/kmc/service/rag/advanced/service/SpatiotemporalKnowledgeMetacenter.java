package tech.qiantong.qknow.module.kmc.service.rag.advanced.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.GraphRagEventFrame;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.GraphRagExecutionReceipt;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.GraphRagOrchestrationControlBus;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.GraphRagSubgraphReasoner;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.HierarchicalDocumentChunker;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.StreamingTypewriterAlignBuffer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 105 高保真知识中枢总控编排服务 (Spatiotemporal Knowledge Metacenter)
 * <p>
 * 统筹四大工业级防线：
 * 1. 自适应四级树状切片与父级上下文动态无损展开 (HierarchicalDocumentChunker)；
 * 2. 测地加权 2-跳局部诱导子图 Personalized PageRank 幂迭代极速推理 (GraphRagSubgraphReasoner)；
 * 3. 1000Hz 定长 4096 槽位 Disruptor 无锁控制总线与不可变密码学存证 (GraphRagOrchestrationControlBus & GraphRagExecutionReceipt)；
 * 4. 自适应闭环泊松 JitterBuffer 流式打字机恒速对齐中枢 (StreamingTypewriterAlignBuffer)。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class SpatiotemporalKnowledgeMetacenter {

    private static final Logger log = LoggerFactory.getLogger(SpatiotemporalKnowledgeMetacenter.class);

    private final HierarchicalDocumentChunker chunker;
    private final GraphRagSubgraphReasoner subgraphReasoner;
    private final GraphRagOrchestrationControlBus controlBus;

    /**
     * 知识中枢全流程查询与推理结果 Record
     */
    public record MetacenterQueryResult(
            String sessionId,
            String queryText,
            HierarchicalDocumentChunker.ExpandedContext expandedContext,
            GraphRagSubgraphReasoner.SubgraphReasoningResult reasoningResult,
            GraphRagExecutionReceipt receipt,
            StreamingTypewriterAlignBuffer alignBuffer
    ) {}

    public SpatiotemporalKnowledgeMetacenter() {
        this(new HierarchicalDocumentChunker(), new GraphRagSubgraphReasoner(), new GraphRagOrchestrationControlBus());
    }

    public SpatiotemporalKnowledgeMetacenter(
            HierarchicalDocumentChunker chunker,
            GraphRagSubgraphReasoner subgraphReasoner,
            GraphRagOrchestrationControlBus controlBus
    ) {
        this.chunker = Objects.requireNonNull(chunker, "chunker 不能为空");
        this.subgraphReasoner = Objects.requireNonNull(subgraphReasoner, "subgraphReasoner 不能为空");
        this.controlBus = Objects.requireNonNull(controlBus, "controlBus 不能为空");
    }

    /**
     * 摄取并解析长文档，构建四级树状结构
     *
     * @param docId   文档唯一标识
     * @param content 文档全文
     * @return 根节点
     */
    public HierarchicalDocumentChunker.ChunkNode ingestDocument(String docId, String content) {
        return chunker.buildHierarchyTree(docId, content);
    }

    /**
     * 执行全链路知识检索、上下文展开、子图推理与凭单签发
     *
     * @param sessionId       会话唯一 ID
     * @param queryText       用户查询文本
     * @param queryEmbedding  千问 1536 维超球面单位向量
     * @param targetChunkId   命中的叶子切片 ID
     * @param allEntities     知识图谱实体池
     * @param allEdges        知识图谱边池
     * @return 完备结果封装
     */
    public MetacenterQueryResult processQuery(
            String sessionId,
            String queryText,
            float[] queryEmbedding,
            String targetChunkId,
            List<GraphRagSubgraphReasoner.EntityNode> allEntities,
            List<GraphRagSubgraphReasoner.RelationEdge> allEdges
    ) {
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(queryText, "queryText 不能为空");

        long startNs = System.nanoTime();

        // 1. 防线一：向上展开父级富上下文
        HierarchicalDocumentChunker.ExpandedContext expanded = null;
        if (targetChunkId != null) {
            expanded = chunker.expandParentContext(targetChunkId);
        }

        // 发布树节点展开事件帧
        controlBus.publishFrame(new GraphRagEventFrame(
                "frame-" + UUID.randomUUID().toString().substring(0, 8),
                sessionId,
                queryText,
                "CHUNK_EXPANDED",
                queryEmbedding,
                expanded != null ? expanded.contextPreservationScore() : 0.0,
                System.currentTimeMillis(),
                0L
        ));

        // 2. 防线二：局部 2-跳测地加权子图拓扑推理
        GraphRagSubgraphReasoner.SubgraphReasoningResult reasoning = subgraphReasoner.reasonSubgraph(
                queryEmbedding,
                allEntities,
                allEdges
        );

        // 发布子图推理事件帧
        double topPpr = reasoning.pprScoreMap().values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        controlBus.publishFrame(new GraphRagEventFrame(
                "frame-" + UUID.randomUUID().toString().substring(0, 8),
                sessionId,
                queryText,
                "SUBGRAPH_REASONED",
                queryEmbedding,
                topPpr,
                System.currentTimeMillis(),
                1L
        ));

        // 3. 防线三：创建自适应泊松 JitterBuffer 流式打字机缓冲
        StreamingTypewriterAlignBuffer alignBuffer = new StreamingTypewriterAlignBuffer();

        long latencyUs = (System.nanoTime() - startNs) / 1000;

        // 4. 统筹签发 SHA-256 密码学防篡改不可变存证凭单
        int expandedCount = expanded != null ? 1 : 0;
        int subNodesCount = reasoning.totalVisitedNodes();

        GraphRagExecutionReceipt receipt = controlBus.issueReceipt(
                sessionId,
                queryText,
                expandedCount,
                subNodesCount,
                topPpr,
                alignBuffer.getPlaybackVariance(),
                latencyUs
        );

        return new MetacenterQueryResult(sessionId, queryText, expanded, reasoning, receipt, alignBuffer);
    }

    public HierarchicalDocumentChunker getChunker() {
        return chunker;
    }

    public GraphRagSubgraphReasoner getSubgraphReasoner() {
        return subgraphReasoner;
    }

    public GraphRagOrchestrationControlBus getControlBus() {
        return controlBus;
    }
}
