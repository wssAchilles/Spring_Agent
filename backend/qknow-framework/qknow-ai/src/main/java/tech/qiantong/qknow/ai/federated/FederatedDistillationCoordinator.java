package tech.qiantong.qknow.ai.federated;

import java.util.*;

/**
 * 分布式联邦记忆蒸馏总控中枢
 * <p>
 * 统筹拓扑流形收缩 -> 跨智能体语义压缩 -> 本地见解提炼 -> 差分联邦聚合 -> 签发存证凭单
 *
 * @author Qknow AI Team
 * @since 2026-09-15
 */
public class FederatedDistillationCoordinator {

    public record DistillationRequest(
            String roundId,
            List<String> participatingAgentIds,
            List<TopologicalManifoldContractor.Edge> rawTopologyEdges,
            String rawBroadcastContext,
            String publicAnchorQuery,
            Map<String, List<String>> agentPrivateMemories,
            Map<String, float[]> agentBaseEmbeddings
    ) {}

    public record DistillationResult(
            boolean success,
            String roundId,
            TopologicalManifoldContractor.ContractedGraph contractedGraph,
            InformationBottleneckCompressor.CompressedContext compressedContext,
            FederatedMemoryAggregator.GlobalMetaMemoryCard globalMetaMemoryCard,
            FederatedDistillationReceipt receipt,
            String failureReason
    ) {}

    private final TopologicalManifoldContractor manifoldContractor;
    private final InformationBottleneckCompressor ibCompressor;
    private final LocalMemoryDistiller localDistiller;
    private final FederatedMemoryAggregator aggregator;

    public FederatedDistillationCoordinator(
            TopologicalManifoldContractor manifoldContractor,
            InformationBottleneckCompressor ibCompressor,
            LocalMemoryDistiller localDistiller,
            FederatedMemoryAggregator aggregator
    ) {
        this.manifoldContractor = Objects.requireNonNull(manifoldContractor, "流形收缩器不能为空");
        this.ibCompressor = Objects.requireNonNull(ibCompressor, "语义压缩器不能为空");
        this.localDistiller = Objects.requireNonNull(localDistiller, "本地提炼器不能为空");
        this.aggregator = Objects.requireNonNull(aggregator, "联邦聚合器不能为空");
    }

    /**
     * 协调执行一轮联邦记忆蒸馏
     */
    public DistillationResult executeRound(DistillationRequest request) {
        try {
            // 1. 通信拓扑流形收缩（目标压缩率 70%）
            TopologicalManifoldContractor.ContractedGraph contractedGraph =
                    manifoldContractor.contractTopology(
                            request.participatingAgentIds(),
                            request.rawTopologyEdges(),
                            0.70
                    );

            // 2. 跨智能体通信语义压缩
            InformationBottleneckCompressor.CompressedContext compressedContext =
                    ibCompressor.compress(request.rawBroadcastContext());

            // 3. 各智能体本地私有记忆蒸馏
            List<LocalMemoryDistiller.LocalInsight> localInsights = new ArrayList<>();
            for (String agentId : request.participatingAgentIds()) {
                List<String> privMem = request.agentPrivateMemories().getOrDefault(agentId, List.of());
                float[] baseEmb = request.agentBaseEmbeddings().get(agentId);
                LocalMemoryDistiller.LocalInsight insight =
                        localDistiller.distill(agentId, request.publicAnchorQuery(), privMem, baseEmb);
                localInsights.add(insight);
            }

            // 4. 局部差分隐私加噪与超球面加权聚合
            FederatedMemoryAggregator.GlobalMetaMemoryCard metaMemoryCard =
                    aggregator.aggregate(request.roundId(), request.publicAnchorQuery(), localInsights);

            // 5. 签发不可变密码学存证凭单
            String receiptId = "FED-RCPT-" + UUID.randomUUID().toString().substring(0, 8);
            FederatedDistillationReceipt receipt = FederatedDistillationReceipt.create(
                    receiptId,
                    request.roundId(),
                    request.participatingAgentIds(),
                    contractedGraph.originalEdgesCount(),
                    contractedGraph.contractedEdgesCount(),
                    compressedContext.compressionRatio(),
                    metaMemoryCard.metaMemoryHash(),
                    System.currentTimeMillis()
            );

            return new DistillationResult(
                    true, request.roundId(), contractedGraph, compressedContext,
                    metaMemoryCard, receipt, null
            );
        } catch (Exception e) {
            return new DistillationResult(
                    false, request.roundId(), null, null, null, null,
                    "联邦记忆蒸馏执行异常: " + e.getMessage()
            );
        }
    }
}
