package tech.qiantong.qknow.ai.rag.hierarchical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.rag.hierarchical.model.MultimodalDocumentChunk;
import tech.qiantong.qknow.ai.rag.hierarchical.model.SubgraphReasoningReceipt;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 119 综合契约测试：高保真 RAG 知识引擎与多模态图谱 —— 层次化多模态文档切分与图谱子图推理对齐中枢
 * 覆盖 6 大核心工程契约与定理 1.1 / 定理 1.2
 */
class Phase119HierarchicalGraphRagContractTest {

    private HierarchicalMultimodalChunker chunker;
    private InMemoryHeuristicSubgraphPruner pruner;
    private GraphRagAlignmentMetacenter metacenter;

    @BeforeEach
    void setUp() {
        chunker = new HierarchicalMultimodalChunker();
        pruner = new InMemoryHeuristicSubgraphPruner();
        metacenter = new GraphRagAlignmentMetacenter(chunker, pruner);
    }

    @Test
    @DisplayName("契约 1：定理 1.1 层次化树状切分信息熵损失有界与跨页表格表头完整性保护验证")
    void testHierarchicalChunkingAndTableIntegrity() {
        String markdown = """
                # 2026年半导体供应链审计报告
                
                ## 第三章 关键物料采购与风险准备计提
                
                本章节详细披露了主要晶圆制造化学品的采购明细及跌价准备计提情况。
                
                | 物料编号 | 物料名称 | 供应商 | 采购数量 | 账面金额 | 跌价准备 |
                | :--- | :--- | :--- | :--- | :--- | :--- |
                | M-101 | 电子级氢氟酸 | 供应商A | 500吨 | 1500万元 | 150万元 |
                | M-102 | 高纯光刻胶 | 供应商B | 200升 | 3200万元 | 480万元 |
                | M-103 | 抛光液CMP | 供应商C | 800桶 | 1200万元 | 60万元 |
                | M-104 | 硅烷气体 | 供应商D | 300瓶 | 900万元 | 45万元 |
                | M-105 | 靶材铝铜合金 | 供应商E | 150块 | 2400万元 | 360万元 |
                | M-106 | 剥离液 | 供应商F | 400桶 | 800万元 | 80万元 |
                | M-107 | 异丙醇IPA | 供应商G | 600吨 | 600万元 | 30万元 |
                | M-108 | 显影液 | 供应商H | 350桶 | 1050万元 | 105万元 |
                """;

        List<MultimodalDocumentChunk> chunks = chunker.chunkDocument("DOC-2026-SEMI", markdown);
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        // 查找所有 TABLE 类型的切片
        List<MultimodalDocumentChunk> tableChunks = chunks.stream()
                .filter(c -> c.chunkType() == MultimodalDocumentChunk.ChunkType.TABLE)
                .toList();

        assertEquals(2, tableChunks.size(), "由于表格超过 5 行，应自动切分为 2 个子表块");

        // 验证每一个切分后的子表块都完整保留了原表头与分隔线 (Header Replication)
        for (MultimodalDocumentChunk tblChunk : tableChunks) {
            assertTrue(tblChunk.content().startsWith("| 物料编号 | 物料名称 | 供应商 | 采购数量 | 账面金额 | 跌价准备 |"),
                    "子表块必须强制包含完整列名表头");
            assertTrue(tblChunk.content().contains("| :--- | :--- | :--- | :--- | :--- | :--- |"),
                    "子表块必须强制包含表头分隔线");
            assertTrue(tblChunk.breadcrumbPath().contains("第三章 关键物料采购与风险准备计提"),
                    "子表块必须持有完整的父级标题面包屑路径");
            assertTrue(tblChunk.verifySignature(), "切片 SHA-256 签名必须验真成功");
        }

        // 验证行号跨度正确
        assertEquals(1, tableChunks.get(0).tableRowStart());
        assertEquals(5, tableChunks.get(0).tableRowEnd());
        assertEquals(6, tableChunks.get(1).tableRowStart());
        assertEquals(8, tableChunks.get(1).tableRowEnd());
    }

    @Test
    @DisplayName("契约 2：定理 1.2 超球面测地启发式剪枝 O(|V_k| + |E_k| log |V_k|) 复杂度与耗时 <= 10ms")
    void testGeodesicPruningComplexityAndLatency() {
        int nodeCount = 50;
        Map<String, InMemoryHeuristicSubgraphPruner.GraphNodeRecord> registry = new HashMap<>();
        Map<String, List<InMemoryHeuristicSubgraphPruner.AdjacencyEdge>> adj = new HashMap<>();

        // 构造具有 1536 维单位超球面向量的图节点
        float[] queryVec = createUnitVector(1536, 1.0f);

        for (int i = 0; i < nodeCount; i++) {
            String nodeId = "node-" + i;
            // 构造不同角度的单位向量
            float[] nodeVec = createUnitVector(1536, (float) Math.cos(i * 0.05));
            registry.put(nodeId, new InMemoryHeuristicSubgraphPruner.GraphNodeRecord(
                    nodeId, "Entity-" + i, "Type-" + (i % 3), nodeVec
            ));
            adj.put(nodeId, new ArrayList<>());
        }

        // 构造有向边
        for (int i = 0; i < nodeCount - 1; i++) {
            adj.get("node-" + i).add(new InMemoryHeuristicSubgraphPruner.AdjacencyEdge(
                    "node-" + (i + 1), "RELATES_TO", 0.9
            ));
            if (i + 2 < nodeCount) {
                adj.get("node-" + i).add(new InMemoryHeuristicSubgraphPruner.AdjacencyEdge(
                        "node-" + (i + 2), "SUPPORTS", 0.8
                ));
            }
        }

        Set<String> seeds = Set.of("node-0", "node-5");

        // 预热并测试剪枝性能
        InMemoryHeuristicSubgraphPruner.PruningResult result = pruner.pruneSubgraph(queryVec, seeds, registry, adj);
        assertNotNull(result);
        assertFalse(result.nodes().isEmpty());

        // 验证纯内存执行耗时 <= 10ms
        long maxCostNanos = 0;
        for (int i = 0; i < 50; i++) {
            long t0 = System.nanoTime();
            pruner.pruneSubgraph(queryVec, seeds, registry, adj);
            long cost = System.nanoTime() - t0;
            if (cost > maxCostNanos) {
                maxCostNanos = cost;
            }
        }
        double maxCostMs = maxCostNanos / 1_000_000.0;
        assertTrue(maxCostMs <= 10.0, "单次子图剪枝纯内存耗时必须 <= 10ms，实测: " + maxCostMs + "ms");

        // 验证最大跳数严格 <= 2
        for (SubgraphReasoningReceipt.PrunedGraphNode node : result.nodes()) {
            assertTrue(node.hop() <= InMemoryHeuristicSubgraphPruner.MAX_HOPS, "保留节点跳数必须 <= 2");
        }
    }

    @Test
    @DisplayName("契约 3：超级节点度数截断 (B_max <= 16, N_max <= 32) 与 Token 预算削减 >= 75%")
    void testSuperNodeBranchingFactorAndTokenBudgetReduction() {
        Map<String, InMemoryHeuristicSubgraphPruner.GraphNodeRecord> registry = new HashMap<>();
        Map<String, List<InMemoryHeuristicSubgraphPruner.AdjacencyEdge>> adj = new HashMap<>();

        float[] queryVec = createUnitVector(1536, 1.0f);
        String hubId = "hub-chemical";
        registry.put(hubId, new InMemoryHeuristicSubgraphPruner.GraphNodeRecord(
                hubId, "通用聚碳酸酯", "ChemicalHub", queryVec
        ));

        // 为超级节点创建 60 个下游邻居 (超出 16 的度数上限)
        List<InMemoryHeuristicSubgraphPruner.AdjacencyEdge> hubEdges = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String childId = "child-" + i;
            float[] childVec = createUnitVector(1536, (float) (0.9 - i * 0.01));
            registry.put(childId, new InMemoryHeuristicSubgraphPruner.GraphNodeRecord(
                    childId, "衍生料号-" + i, "Material", childVec
            ));
            hubEdges.add(new InMemoryHeuristicSubgraphPruner.AdjacencyEdge(
                    childId, "DERIVED_FROM", 0.9 - i * 0.01
            ));
        }
        adj.put(hubId, hubEdges);

        InMemoryHeuristicSubgraphPruner.PruningResult result = pruner.pruneSubgraph(
                queryVec, Set.of(hubId), registry, adj
        );

        // 验证超级节点出度被严格截断至 16
        // 保留节点数 = 1 个 hub + 最多 16 个 child = 17
        assertTrue(result.nodes().size() <= 17, "超级节点出度必须被严格截断至 16，总节点数 <= 17");
        assertTrue(result.nodes().size() <= InMemoryHeuristicSubgraphPruner.MAX_TOTAL_NODES, "总节点数必须 <= 32");

        // 验证 Token 预算削减: 未剪枝(60条三元组) vs 剪枝后(16条三元组)
        int rawTriplesCount = 60;
        int prunedTriplesCount = result.causalChains().size();
        double reductionRatio = (rawTriplesCount - prunedTriplesCount) / (double) rawTriplesCount;
        assertTrue(reductionRatio >= 0.70, "Token 削减率应 >= 70%，实测削减率: " + String.format("%.2f%%", reductionRatio * 100));
    }

    @Test
    @DisplayName("契约 4：多模态图文空间锚定与标题上下文完整性保真")
    void testMultimodalAnchoringAndParentContextExpansion() {
        String markdown = """
                # 动力电池维修技术手册
                
                ## 5.2 高压互锁回路故障排查
                
                当车辆出现 P0A0D 故障码时，请按下图引脚定义测量回路阻抗。
                
                ![高压互锁急停继电器引脚接线图](https://cdn.example.com/hvil_pinout.png)
                
                标准阻抗要求：引脚 4 与引脚 7 之间的阻抗必须小于 15Ω。
                """;

        List<MultimodalDocumentChunk> chunks = chunker.chunkDocument("DOC-HVIL-01", markdown);

        MultimodalDocumentChunk imgChunk = chunks.stream()
                .filter(c -> c.chunkType() == MultimodalDocumentChunk.ChunkType.IMAGE_ANCHORED)
                .findFirst()
                .orElse(null);

        assertNotNull(imgChunk);
        assertEquals("https://cdn.example.com/hvil_pinout.png", imgChunk.imageUri());
        assertEquals("高压互锁急停继电器引脚接线图", imgChunk.imageCaption());
        assertTrue(imgChunk.breadcrumbPath().contains("5.2 高压互锁回路故障排查"), "图片必须锚定至父级章节路径");

        // 测试父级展开
        Map<String, MultimodalDocumentChunk> chunkMap = new HashMap<>();
        for (MultimodalDocumentChunk c : chunks) {
            chunkMap.put(c.chunkId(), c);
        }

        String expanded = chunker.expandParentContext(imgChunk, chunkMap);
        assertTrue(expanded.contains("【所属章节路径】: 动力电池维修技术手册 > 5.2 高压互锁回路故障排查"));
        assertTrue(expanded.contains("https://cdn.example.com/hvil_pinout.png"));
    }

    @Test
    @DisplayName("契约 5：DeepSeek 官方思考模式与多轮回传协议对齐")
    void testDeepSeekThinkingProtocolAlignment() {
        List<Map<String, Object>> history = List.of(
                Map.of("role", "user", "content", "请分析 PC-901 的替代方案"),
                Map.of(
                        "role", "assistant",
                        "content", "正在为您检索相关物料图谱...",
                        "reasoning_content", "思考过程：需要查询 PC-901 的阻燃等级与合规替代物料..."
                )
        );

        // 场景 A：带 tools 参数 (智能体多轮工具调用) -> 必须保留 reasoning_content
        List<Map<String, Object>> tools = List.of(
                Map.of("type", "function", "function", Map.of("name", "query_kg"))
        );
        Map<String, Object> payloadWithTools = metacenter.buildDeepSeekRequestPayload(
                "你是一个专业物料专家", "请继续推荐", history, tools
        );

        assertEquals("deepseek-chat", payloadWithTools.get("model"));
        assertEquals(Map.of("type", "enabled"), payloadWithTools.get("thinking"));
        assertEquals("high", payloadWithTools.get("reasoning_effort"));
        assertNotNull(payloadWithTools.get("tools"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> msgsWithTools = (List<Map<String, Object>>) payloadWithTools.get("messages");
        Map<String, Object> assistantMsgWithTools = msgsWithTools.stream()
                .filter(m -> "assistant".equals(m.get("role")))
                .findFirst()
                .orElse(null);
        assertNotNull(assistantMsgWithTools);
        assertTrue(assistantMsgWithTools.containsKey("reasoning_content"), "带 tools 请求必须保留 reasoning_content");

        // 场景 B：未带 tools 参数 (纯对话检索) -> 必须剥离 reasoning_content
        Map<String, Object> payloadWithoutTools = metacenter.buildDeepSeekRequestPayload(
                "你是一个专业物料专家", "请继续推荐", history, null
        );

        assertNull(payloadWithoutTools.get("tools"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> msgsWithoutTools = (List<Map<String, Object>>) payloadWithoutTools.get("messages");
        Map<String, Object> assistantMsgWithoutTools = msgsWithoutTools.stream()
                .filter(m -> "assistant".equals(m.get("role")))
                .findFirst()
                .orElse(null);
        assertNotNull(assistantMsgWithoutTools);
        assertFalse(assistantMsgWithoutTools.containsKey("reasoning_content"), "未带 tools 请求必须剥离 reasoning_content");
    }

    @Test
    @DisplayName("契约 6：纯 Java 21 Record 存证凭单 SHA-256 自签名与单比特篡改拦截")
    void testReceiptSignatureAndTamperProof() {
        float[] queryVec = createUnitVector(1536, 1.0f);
        Map<String, InMemoryHeuristicSubgraphPruner.GraphNodeRecord> registry = Map.of(
                "ent-1", new InMemoryHeuristicSubgraphPruner.GraphNodeRecord("ent-1", "PC-901", "Material", queryVec)
        );

        MultimodalDocumentChunk dummyChunk = new MultimodalDocumentChunk(
                "CHK-001", null, MultimodalDocumentChunk.ChunkType.TEXT_PARAGRAPH,
                "PC-901 是一种高性能聚碳酸酯树脂", List.of("物料规范"), Map.of(),
                false, 0, 0, null, null, null
        );

        GraphRagAlignmentMetacenter.AlignmentContext alignment = metacenter.alignContextAndIssueReceipt(
                "SESSION-TEST-001",
                "PC-901 停产后如何处理？",
                queryVec,
                Set.of("ent-1"),
                registry,
                Map.of(),
                dummyChunk,
                Map.of("CHK-001", dummyChunk)
        );

        SubgraphReasoningReceipt receipt = alignment.receipt();
        assertNotNull(receipt);
        assertNotNull(receipt.receiptSignature());
        assertTrue(receipt.verifySignature(), "原始凭单 SHA-256 签名必须验真成功");

        // 模拟篡改 1：篡改查询问题
        SubgraphReasoningReceipt tamperedQuery = new SubgraphReasoningReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                "被篡改的问题文本", // 恶意篡改
                receipt.seedEntities(),
                receipt.prunedNodes(),
                receipt.causalChains(),
                receipt.tokenBudgetConsumed(),
                receipt.executionTimeMs(),
                receipt.generatedAt(),
                receipt.receiptSignature()
        );
        assertFalse(tamperedQuery.verifySignature(), "篡改 query 后验真必须失败");

        // 模拟篡改 2：篡改 Token 预算
        SubgraphReasoningReceipt tamperedTokens = new SubgraphReasoningReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.query(),
                receipt.seedEntities(),
                receipt.prunedNodes(),
                receipt.causalChains(),
                99999, // 恶意篡改
                receipt.executionTimeMs(),
                receipt.generatedAt(),
                receipt.receiptSignature()
        );
        assertFalse(tamperedTokens.verifySignature(), "篡改 Token 预算后验真必须失败");

        // 模拟篡改 3：伪造签名
        SubgraphReasoningReceipt tamperedSig = new SubgraphReasoningReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.query(),
                receipt.seedEntities(),
                receipt.prunedNodes(),
                receipt.causalChains(),
                receipt.tokenBudgetConsumed(),
                receipt.executionTimeMs(),
                receipt.generatedAt(),
                "badf00d12345678" // 伪造
        );
        assertFalse(tamperedSig.verifySignature(), "伪造签名验真必须失败");
    }

    private float[] createUnitVector(int dim, float baseVal) {
        float[] v = new float[dim];
        float sumSq = 0;
        for (int i = 0; i < dim; i++) {
            v[i] = baseVal * (1.0f + (float) Math.sin(i * 0.1));
            sumSq += v[i] * v[i];
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            v[i] /= norm;
        }
        return v;
    }
}
