package tech.qiantong.qknow.ai.rag.hierarchical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.rag.hierarchical.model.MultimodalDocumentChunk;
import tech.qiantong.qknow.ai.rag.hierarchical.model.SubgraphReasoningReceipt;

import java.time.Instant;
import java.util.*;

/**
 * 高保真 RAG 知识引擎与多模态图谱对齐中枢
 * 1. 协调层次化文档切分展开与千问 1536 维超球面子图启发式剪枝；
 * 2. 构造因果拓扑骨架 (Causal Scaffold) 注入 Prompt；
 * 3. 严格遵循 DeepSeek 官方多轮思考回传协议 (带 tools 保留 reasoning_content，不带 tools 剥离)；
 * 4. 签发纯 Java 21 Record 格式的 SHA-256 密码学防篡改存证凭单。
 */
@Component
public class GraphRagAlignmentMetacenter {

    private static final Logger log = LoggerFactory.getLogger(GraphRagAlignmentMetacenter.class);

    private final HierarchicalMultimodalChunker chunker;
    private final InMemoryHeuristicSubgraphPruner pruner;

    public GraphRagAlignmentMetacenter(
            HierarchicalMultimodalChunker chunker,
            InMemoryHeuristicSubgraphPruner pruner
    ) {
        this.chunker = chunker != null ? chunker : new HierarchicalMultimodalChunker();
        this.pruner = pruner != null ? pruner : new InMemoryHeuristicSubgraphPruner();
    }

    /**
     * 构建注入因果拓扑骨架与层次化文档上下文的对齐结构
     */
    public record AlignmentContext(
            String alignedPrompt,
            String causalScaffold,
            String expandedDocContext,
            SubgraphReasoningReceipt receipt
    ) {}

    /**
     * 执行端到端对齐推理：剪枝子图、展开父块并签发存证凭单
     *
     * @param sessionId           会话 ID
     * @param query               用户查询文本
     * @param queryEmbedding1536  千问 1536 维查询向量
     * @param seedEntities        初始种子实体 ID 集合
     * @param nodeRegistry        堆内图节点注册表
     * @param adjacencyList       堆内邻接表
     * @param targetChunk         检索命中的目标文档切片
     * @param chunkMap            文档全局切片索引 (用于父级展开)
     * @return 对齐上下文与存证凭单
     */
    public AlignmentContext alignContextAndIssueReceipt(
            String sessionId,
            String query,
            float[] queryEmbedding1536,
            Set<String> seedEntities,
            Map<String, InMemoryHeuristicSubgraphPruner.GraphNodeRecord> nodeRegistry,
            Map<String, List<InMemoryHeuristicSubgraphPruner.AdjacencyEdge>> adjacencyList,
            MultimodalDocumentChunk targetChunk,
            Map<String, MultimodalDocumentChunk> chunkMap
    ) {
        long startTime = System.currentTimeMillis();

        // 1. 启发式子图剪枝
        InMemoryHeuristicSubgraphPruner.PruningResult pruningResult = pruner.pruneSubgraph(
                queryEmbedding1536,
                seedEntities,
                nodeRegistry,
                adjacencyList
        );

        // 2. 层次化文档父级展开
        String expandedDocContext = chunker.expandParentContext(targetChunk, chunkMap);

        // 3. 构造因果拓扑骨架
        String scaffold = buildCausalScaffold(pruningResult.nodes(), pruningResult.causalChains());

        // 4. 组装最终对齐 Prompt
        StringBuilder promptBuilder = new StringBuilder();
        if (!scaffold.isBlank()) {
            promptBuilder.append(scaffold).append("\n\n");
        }
        if (!expandedDocContext.isBlank()) {
            promptBuilder.append("### [高保真层次化文档上下文]\n").append(expandedDocContext).append("\n\n");
        }
        promptBuilder.append("### [用户问题]\n").append(query);

        long execTimeMs = System.currentTimeMillis() - startTime;
        int tokenBudgetConsumed = estimateTokenCount(promptBuilder.toString());

        // 5. 签发不可变凭单
        String receiptId = "RCP-ALN-" + UUID.randomUUID().toString().substring(0, 8);
        SubgraphReasoningReceipt receipt = SubgraphReasoningReceipt.create(
                receiptId,
                sessionId,
                query,
                seedEntities != null ? List.copyOf(seedEntities) : List.of(),
                pruningResult.nodes(),
                pruningResult.causalChains(),
                tokenBudgetConsumed,
                execTimeMs,
                Instant.now()
        );

        log.info("[GraphRAG 对齐完成] 会话 ID: {}, 凭单 ID: {}, 节点数: {}, 因果链: {}, 耗时: {}ms",
                sessionId, receiptId, pruningResult.nodes().size(), pruningResult.causalChains().size(), execTimeMs);

        return new AlignmentContext(promptBuilder.toString(), scaffold, expandedDocContext, receipt);
    }

    /**
     * 构建结构化因果拓扑骨架 Markdown
     */
    public String buildCausalScaffold(
            List<SubgraphReasoningReceipt.PrunedGraphNode> nodes,
            List<SubgraphReasoningReceipt.CausalProposition> chains
    ) {
        if (nodes.isEmpty() && chains.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<thinking_scaffold>\n");
        sb.append("### [GraphRAG 局部因果拓扑推理骨架]\n");
        sb.append("已基于千问 1536 维超球面测地距离完成启发式剪枝，请严格依据以下经核实的实体因果拓扑链进行思考推导：\n\n");

        sb.append("#### 1. 核心实体上下文\n");
        for (SubgraphReasoningReceipt.PrunedGraphNode node : nodes) {
            sb.append(String.format("- [%s]: %s (类型: %s, 测地得分: %.4f, 跳数: %d)\n",
                    node.entityId(), node.entityName(), node.entityType(), node.geodesicDistanceScore(), node.hop()));
        }

        sb.append("\n#### 2. 因果拓扑命题链\n");
        for (SubgraphReasoningReceipt.CausalProposition chain : chains) {
            sb.append("- ").append(chain.naturalLanguageStatement()).append("\n");
        }

        sb.append("</thinking_scaffold>");
        return sb.toString();
    }

    /**
     * 构建符合 DeepSeek 官方规范的 ChatCompletion 请求体 (严格遵守思考模式与多轮回传协议)
     *
     * @param systemPrompt   系统提示词
     * @param alignedPrompt  经过对齐包装的用户输入 Prompt
     * @param historyMessages 历史消息列表 (包含 role, content, reasoning_content 等)
     * @param tools          工具定义列表 (若包含 tools 则必须回传 reasoning_content，否则剥离)
     * @return 序列化就绪的请求 Map
     */
    public Map<String, Object> buildDeepSeekRequestPayload(
            String systemPrompt,
            String alignedPrompt,
            List<Map<String, Object>> historyMessages,
            List<Map<String, Object>> tools
    ) {
        boolean hasTools = tools != null && !tools.isEmpty();
        List<Map<String, Object>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        // 处理多轮历史消息
        if (historyMessages != null) {
            for (Map<String, Object> msg : historyMessages) {
                Map<String, Object> cleanMsg = new LinkedHashMap<>(msg);
                String role = (String) cleanMsg.get("role");

                if ("assistant".equalsIgnoreCase(role)) {
                    if (hasTools) {
                        // 带 tools 必须完整保留回传 reasoning_content
                        if (!cleanMsg.containsKey("reasoning_content")) {
                            cleanMsg.put("reasoning_content", "");
                        }
                    } else {
                        // 未带 tools 严禁回传 reasoning_content，防止报错与 token 浪费
                        cleanMsg.remove("reasoning_content");
                    }
                }
                messages.add(cleanMsg);
            }
        }

        // 添加当前轮次用户消息
        messages.add(Map.of("role", "user", "content", alignedPrompt));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", "deepseek-chat");
        payload.put("messages", messages);
        payload.put("thinking", Map.of("type", "enabled"));
        payload.put("reasoning_effort", "high");

        if (hasTools) {
            payload.put("tools", tools);
        }

        return payload;
    }

    private int estimateTokenCount(String text) {
        if (text == null) {
            return 0;
        }
        // 简单保守估计：中文与字符数约为 1.2 token/字
        return (int) (text.length() * 1.2);
    }

    public HierarchicalMultimodalChunker getChunker() {
        return chunker;
    }

    public InMemoryHeuristicSubgraphPruner getPruner() {
        return pruner;
    }
}
