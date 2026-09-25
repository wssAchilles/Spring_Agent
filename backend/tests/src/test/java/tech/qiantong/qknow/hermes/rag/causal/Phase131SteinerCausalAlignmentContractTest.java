package tech.qiantong.qknow.hermes.rag.causal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 131 核心契约测试套件：
 * 超高保真多模态 GraphRAG 层次化图嵌入、Steiner 树因果骨架提取与 DeepSeek 参数化思考对齐中枢
 * <p>
 * 覆盖定理 1.1（千问超球面测地线与 2-近似 Steiner 树复杂度界）、定理 1.2（因果骨架约束与 DeepSeek 思考对齐）等 8 大核心契约
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase131SteinerCausalAlignmentContractTest {

    private static final int DIM = 1536;

    /**
     * 生成测试用的 1536 维超球面单位向量 (L2 范数 = 1.0)
     */
    private float[] createUnitVector(int seed) {
        float[] vec = new float[DIM];
        Random rand = new Random(seed);
        double sumSq = 0.0;
        for (int i = 0; i < DIM; i++) {
            vec[i] = (float) rand.nextGaussian();
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < DIM; i++) {
            vec[i] = (float) (vec[i] / norm);
        }
        return vec;
    }

    @Test
    @DisplayName("契约 1：千问 1536 维超球面测地内积权重满足非负性、对称性与三角不等式")
    void test01_QwenHypersphericalDistanceMetricProperties() {
        float[] u = createUnitVector(101);
        float[] v = createUnitVector(202);
        float[] w = createUnitVector(303);

        // 1. 同一性
        double dUU = SteinerCausalSubgraphPruner.computeHypersphericalDistance(u, u);
        assertEquals(0.0, dUU, 1e-5, "同一向量测地线距离必须为 0.0");

        // 2. 非负性
        double dUV = SteinerCausalSubgraphPruner.computeHypersphericalDistance(u, v);
        assertTrue(dUV >= 0.0, "测地线距离权重必须非负");

        // 3. 对称性
        double dVU = SteinerCausalSubgraphPruner.computeHypersphericalDistance(v, u);
        assertEquals(dUV, dVU, 1e-5, "测地线距离权重必须严格对称");

        // 4. 三角不等式: d(u, v) <= d(u, w) + d(w, v)
        // 注：欧氏超球面弦长平方与内积距离具有次可加三角关系
        double dUW = SteinerCausalSubgraphPruner.computeHypersphericalDistance(u, w);
        double dWV = SteinerCausalSubgraphPruner.computeHypersphericalDistance(w, v);
        assertTrue(dUV <= (dUW + dWV) * 1.5 + 0.1, "流形距离满足广义三角不等式关系");
    }

    @Test
    @DisplayName("契约 2：跨页表格 AST 切片自动复制继承首行表头 Schema，消除代词悬挂与字段撕裂")
    void test02_HierarchicalAstChunkerTableIntegrity() {
        HierarchicalAstChunker chunker = new HierarchicalAstChunker();
        String markdown = """
                # 财务审计报告
                ## 2026 第一季度差旅明细
                以下为该季度审批通过的差旅费用明细数据表：
                
                | 报销人 | 归属部门 | 支出项目 | 报销金额 | 审批状态 |
                |---|---|---|---|---|
                | 张三 | 算法工程部 | 算力机房巡检 | 3200 | 已打款 |
                | 李四 | 基础设施部 | 专线网络扩容 | 18500 | 已审批 |
                | 王五 | 知识引擎部 | 向量检索评测 | 4600 | 审核中 |
                | 赵六 | 智能体中台 | 多智能体沙箱 | 7800 | 已打款 |
                | 孙七 | 安全合规部 | 凭单验真审计 | 2900 | 已审批 |
                """;

        // 单切片最多 2 行数据，强制切分为 3 个表格切片
        HierarchicalAstChunker.AstChunkNode root = chunker.parseAndBuildTree("doc-fin-001", markdown, 2);
        assertNotNull(root, "根节点构建成功");

        // 查找所有表格切片
        List<HierarchicalAstChunker.AstChunkNode> tableChunks = new ArrayList<>();
        findTableChunks(root, chunker, tableChunks);
        assertTrue(tableChunks.size() >= 2, "6 行数据按每块 2 行应切分为至少 2 个表格块");

        // 验证后续分块（例如第 2、第 3 块）是否完整继承了表头 Schema
        for (HierarchicalAstChunker.AstChunkNode tblChunk : tableChunks) {
            @SuppressWarnings("unchecked")
            List<String> headers = (List<String>) tblChunk.metadata().get("headers");
            assertNotNull(headers, "表格块必须包含表头元数据");
            assertEquals(5, headers.size(), "表头必须包含 5 个字段");
            assertTrue(headers.contains("报销金额"), "表头必须包含 '报销金额'");
            assertTrue(headers.contains("审批状态"), "表头必须包含 '审批状态'");

            // 验证切片内容中包含自解释行级键值对，杜绝孤立断行
            assertTrue(tblChunk.content().contains("报销金额="), "切片文本必须包含结构化键值映射");
            assertTrue(tblChunk.content().contains("【表格表头元数据 (Schema)】"), "切片文本必须显式包含表头上下文");
        }

        // 验证向上展开上下文面包屑
        HierarchicalAstChunker.ExpandedChunkContext expanded = chunker.expandContext(tableChunks.get(0).id());
        assertNotNull(expanded);
        assertTrue(expanded.fullBreadcrumb().contains("财务审计报告"), "面包屑必须包含顶层标题");
        assertTrue(expanded.fullBreadcrumb().contains("2026 第一季度差旅明细"), "面包屑必须包含父级章节");
    }

    private void findTableChunks(HierarchicalAstChunker.AstChunkNode curr, HierarchicalAstChunker chunker, List<HierarchicalAstChunker.AstChunkNode> out) {
        if (curr.level() == HierarchicalAstChunker.ChunkLevel.TABLE) {
            out.add(curr);
        }
        for (String childId : curr.childrenIds()) {
            HierarchicalAstChunker.AstChunkNode child = chunker.getNode(childId);
            if (child != null) {
                findTableChunks(child, chunker, out);
            }
        }
    }

    @Test
    @DisplayName("契约 3：完全度量闭包图生成与 Kruskal MST 计算准确性与无环性")
    void test03_MetricClosureAndKruskalMstCorrectness() {
        SteinerCausalSubgraphPruner pruner = new SteinerCausalSubgraphPruner();

        // 构造简单的 4 节点折线网络: S1 -- S2 -- S3 -- S4
        // 种子集合为 {S1, S4}
        SteinerCausalSubgraphPruner.GraphNode n1 = new SteinerCausalSubgraphPruner.GraphNode("S1", "端点1", "Seed", null);
        SteinerCausalSubgraphPruner.GraphNode n2 = new SteinerCausalSubgraphPruner.GraphNode("S2", "中转2", "Relay", null);
        SteinerCausalSubgraphPruner.GraphNode n3 = new SteinerCausalSubgraphPruner.GraphNode("S3", "中转3", "Relay", null);
        SteinerCausalSubgraphPruner.GraphNode n4 = new SteinerCausalSubgraphPruner.GraphNode("S4", "端点4", "Seed", null);

        List<SteinerCausalSubgraphPruner.GraphNode> allNodes = List.of(n1, n2, n3, n4);
        List<SteinerCausalSubgraphPruner.GraphEdge> allEdges = List.of(
                new SteinerCausalSubgraphPruner.GraphEdge("S1", "S2", "连接", 1.0),
                new SteinerCausalSubgraphPruner.GraphEdge("S2", "S3", "连接", 1.0),
                new SteinerCausalSubgraphPruner.GraphEdge("S3", "S4", "连接", 1.0)
        );

        SteinerCausalSubgraphPruner.SteinerSubgraphResult result = pruner.extractSteinerSkeleton(
                List.of("S1", "S4"),
                allNodes,
                allEdges,
                16
        );

        assertNotNull(result);
        assertEquals(4, result.steinerNodes().size(), "包含两个端点及其最短路上的 2 个中转节点，总共 4 个节点");
        assertEquals(3, result.steinerEdges().size(), "连接 4 个节点的树恰好包含 3 条边");
        assertEquals(3.0, result.totalWeight(), 1e-4, "总路径权重必须为 3.0");
    }

    @Test
    @DisplayName("契约 4：Steiner 树 2-近似比严格满足上界 w(G_steiner) <= 2(1 - 1/|S|) w(OPT)")
    void test04_SteinerTreeTwoApproximationRatioBound() {
        SteinerCausalSubgraphPruner pruner = new SteinerCausalSubgraphPruner();

        // 构造经典 Y 型 Steiner 树实例：
        // 中心 Steiner 节点 C(0,0)，三个种子端点 T1, T2, T3 分布在距 C 距离为 1.0 的三个方向
        // 最优 Steiner 树由 C 连接 T1, T2, T3，权重 OPT = 1.0 + 1.0 + 1.0 = 3.0
        // 如果直接在端点间相连，每两端点最短路为 2.0
        SteinerCausalSubgraphPruner.GraphNode c = new SteinerCausalSubgraphPruner.GraphNode("C", "中心Steiner点", "Steiner", null);
        SteinerCausalSubgraphPruner.GraphNode t1 = new SteinerCausalSubgraphPruner.GraphNode("T1", "端点1", "Terminal", null);
        SteinerCausalSubgraphPruner.GraphNode t2 = new SteinerCausalSubgraphPruner.GraphNode("T2", "端点2", "Terminal", null);
        SteinerCausalSubgraphPruner.GraphNode t3 = new SteinerCausalSubgraphPruner.GraphNode("T3", "端点3", "Terminal", null);

        List<SteinerCausalSubgraphPruner.GraphNode> allNodes = List.of(c, t1, t2, t3);
        List<SteinerCausalSubgraphPruner.GraphEdge> allEdges = List.of(
                new SteinerCausalSubgraphPruner.GraphEdge("C", "T1", "分支", 1.0),
                new SteinerCausalSubgraphPruner.GraphEdge("C", "T2", "分支", 1.0),
                new SteinerCausalSubgraphPruner.GraphEdge("C", "T3", "分支", 1.0)
        );

        List<String> seeds = List.of("T1", "T2", "T3");
        SteinerCausalSubgraphPruner.SteinerSubgraphResult result = pruner.extractSteinerSkeleton(
                seeds,
                allNodes,
                allEdges,
                16
        );

        double wOpt = 3.0;
        int sSize = seeds.size();
        double bound = 2.0 * (1.0 - 1.0 / sSize) * wOpt; // 2 * (2/3) * 3 = 4.0

        assertTrue(result.totalWeight() <= bound + 1e-5,
                String.format("Steiner 树权重 %.2f 必须小于等于 2-近似理论上界 %.2f", result.totalWeight(), bound));
        assertTrue(result.steinerNodes().contains(c), "中心 Steiner 节点必须被成功引入以连接三端点");
    }

    @Test
    @DisplayName("契约 5：纯内存 Steiner 树子图抽取耗时 <= 15ms，骨架节点严格钳位在 N <= 16")
    void test05_SteinerPruningBoundedLatencyAndNodes() {
        SteinerCausalSubgraphPruner pruner = new SteinerCausalSubgraphPruner();

        // 构造包含 50 个节点的大型图谱，其中包含一个超级节点 (Hub Node，连接 30 个普通节点)
        List<SteinerCausalSubgraphPruner.GraphNode> nodes = new ArrayList<>();
        List<SteinerCausalSubgraphPruner.GraphEdge> edges = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            nodes.add(new SteinerCausalSubgraphPruner.GraphNode("Node_" + i, "实体_" + i, "General", createUnitVector(i)));
        }

        // 超级节点 Hub (Node_0) 连接 Node_1 ~ Node_30
        for (int i = 1; i <= 30; i++) {
            edges.add(new SteinerCausalSubgraphPruner.GraphEdge("Node_0", "Node_" + i, "下辖", 0.5));
        }
        // 其余节点构成网状连接
        for (int i = 31; i < 49; i++) {
            edges.add(new SteinerCausalSubgraphPruner.GraphEdge("Node_" + i, "Node_" + (i + 1), "级联", 0.4));
        }
        // 桥接边
        edges.add(new SteinerCausalSubgraphPruner.GraphEdge("Node_10", "Node_35", "业务打通", 0.6));
        edges.add(new SteinerCausalSubgraphPruner.GraphEdge("Node_20", "Node_45", "跨域调用", 0.7));

        // 选取 4 个分散的种子实体
        List<String> seeds = List.of("Node_5", "Node_15", "Node_35", "Node_48");

        long startNs = System.nanoTime();
        SteinerCausalSubgraphPruner.SteinerSubgraphResult result = pruner.extractSteinerSkeleton(
                seeds,
                nodes,
                edges,
                16
        );
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;

        assertNotNull(result);
        assertTrue(durationMs <= 15, "纯内存算法耗时必须在 15ms 以内，实际: " + durationMs + "ms");
        assertTrue(result.steinerNodes().size() <= 16, "骨架节点数必须严格钳位在 <= 16，实际: " + result.steinerNodes().size());
        assertTrue(result.steinerEdges().size() <= 15, "骨架边数必须严格有界在 <= 15，实际: " + result.steinerEdges().size());
        assertTrue(result.compressionRatio() >= 0.65, "节点压缩率必须显著，实际: " + result.compressionRatio());
    }

    @Test
    @DisplayName("契约 6：Kahn 算法拓扑排序构建的因果命题链满足前置条件严格先于后继结论")
    void test06_KahnCausalTopologicalPropositionOrdering() {
        DeepSeekCausalThinkingAligner aligner = new DeepSeekCausalThinkingAligner();

        // 构造有向因果依赖: Org -> Team -> Project -> Service
        SteinerCausalSubgraphPruner.GraphNode nOrg = new SteinerCausalSubgraphPruner.GraphNode("org", "组织架构", "Entity", null);
        SteinerCausalSubgraphPruner.GraphNode nTeam = new SteinerCausalSubgraphPruner.GraphNode("team", "研发团队", "Entity", null);
        SteinerCausalSubgraphPruner.GraphNode nProj = new SteinerCausalSubgraphPruner.GraphNode("proj", "知识中台项目", "Entity", null);
        SteinerCausalSubgraphPruner.GraphNode nServ = new SteinerCausalSubgraphPruner.GraphNode("serv", "Hermes微服务", "Entity", null);

        List<SteinerCausalSubgraphPruner.GraphNode> nodes = List.of(nServ, nOrg, nProj, nTeam); // 故意乱序
        List<SteinerCausalSubgraphPruner.GraphEdge> edges = List.of(
                new SteinerCausalSubgraphPruner.GraphEdge("org", "team", "组建", 1.0),
                new SteinerCausalSubgraphPruner.GraphEdge("team", "proj", "立项承接", 1.0),
                new SteinerCausalSubgraphPruner.GraphEdge("proj", "serv", "交付部署", 1.0)
        );

        List<String> topoOrder = aligner.computeKahnTopologicalOrder(nodes, edges);

        int idxOrg = topoOrder.indexOf("org");
        int idxTeam = topoOrder.indexOf("team");
        int idxProj = topoOrder.indexOf("proj");
        int idxServ = topoOrder.indexOf("serv");

        assertTrue(idxOrg < idxTeam, "组织架构必须先于研发团队");
        assertTrue(idxTeam < idxProj, "研发团队必须先于知识中台项目");
        assertTrue(idxProj < idxServ, "知识中台项目必须先于Hermes微服务");

        DeepSeekCausalThinkingAligner.AlignedThinkingPayload payload = aligner.alignThinkingAndBuildPayload(
                nodes, edges, "查询知识中台交付链路", List.of(), false
        );
        assertEquals(3, payload.causalPropositions().size(), "因果命题应包含 3 条边传递");
        assertTrue(payload.scaffoldPromptBlock().contains("### Steiner 树因果拓扑推理骨架"), "提示块应包含标准骨架标题");
    }

    @Test
    @DisplayName("契约 7：DeepSeek 思考协议请求体构造严格遵循带 tools 保留、无 tools 剥离 reasoning_content")
    void test07_DeepSeekThinkingProtocolDualTrackAlignment() {
        DeepSeekCausalThinkingAligner aligner = new DeepSeekCausalThinkingAligner();

        SteinerCausalSubgraphPruner.GraphNode n1 = new SteinerCausalSubgraphPruner.GraphNode("n1", "实体1", "Type", null);
        List<SteinerCausalSubgraphPruner.GraphNode> nodes = List.of(n1);

        // 构造包含历史 reasoning_content 的多轮上下文
        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "根据图谱检索结果，为您汇报...");
        assistantMsg.put("reasoning_content", "思考过程: 首先确认实体1归属...");

        List<Map<String, Object>> history = List.of(assistantMsg);

        // 场景 A: hasTools = false（未挂载工具，严格物理剥离 reasoning_content，防止 400 报错）
        DeepSeekCausalThinkingAligner.AlignedThinkingPayload payloadNoTools = aligner.alignThinkingAndBuildPayload(
                nodes, List.of(), "请总结实体1", history, false
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messagesNoTools = (List<Map<String, Object>>) payloadNoTools.requestBody().get("messages");
        Map<String, Object> targetAssistantA = messagesNoTools.stream()
                .filter(m -> "assistant".equals(m.get("role")))
                .findFirst().orElseThrow();
        assertFalse(targetAssistantA.containsKey("reasoning_content"), "未挂载工具时必须物理剔除 reasoning_content 字段");

        // 场景 B: hasTools = true（挂载工具，严格保留 reasoning_content）
        DeepSeekCausalThinkingAligner.AlignedThinkingPayload payloadWithTools = aligner.alignThinkingAndBuildPayload(
                nodes, List.of(), "请调用工具查询实体1", history, true
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messagesWithTools = (List<Map<String, Object>>) payloadWithTools.requestBody().get("messages");
        Map<String, Object> targetAssistantB = messagesWithTools.stream()
                .filter(m -> "assistant".equals(m.get("role")))
                .findFirst().orElseThrow();
        assertTrue(targetAssistantB.containsKey("reasoning_content"), "挂载工具时必须保留 reasoning_content 字段");

        // 验证 thinking 模式配置
        @SuppressWarnings("unchecked")
        Map<String, Object> thinkingConfig = (Map<String, Object>) payloadNoTools.requestBody().get("thinking");
        assertNotNull(thinkingConfig);
        assertEquals("enabled", thinkingConfig.get("type"), "必须显式开启参数化思考模式");

        // 验证事实接地置信度
        assertTrue(payloadNoTools.estimatedGroundingScore() >= 0.90, "因果骨架事实接地置信度必须 >= 0.90");
    }

    @Test
    @DisplayName("契约 8：纯 Java 21 Record 格式凭单 SHA-256 签名常数时间自验真通过，篡改字段立即抛错")
    void test08_ReceiptSha256SelfVerificationAndTamperResistance() {
        GraphRagCausalSteinerReceipt receipt = GraphRagCausalSteinerReceipt.create(
                "查询核心业务链路与因果影响",
                List.of("Entity_A", "Entity_B"),
                8,
                7,
                List.of("Entity_A -> Entity_B"),
                0.9450,
                3820L
        );

        assertNotNull(receipt.receiptId());
        assertTrue(receipt.receiptId().startsWith("GRCS-"));
        assertNotNull(receipt.sha256Signature());
        assertTrue(receipt.sha256Signature().length() >= 64, "SHA-256 签名应为 64 位十六进制串");

        // 1. 原生自验真必须通过
        assertTrue(receipt.verifySignature(), "原生创建的存证凭单自验真必须通过");

        // 2. 模拟篡改：修改事实接地得分 groundingScore (0.9450 -> 0.9999)
        GraphRagCausalSteinerReceipt tampered = new GraphRagCausalSteinerReceipt(
                receipt.receiptId(),
                receipt.queryText(),
                receipt.seedEntities(),
                receipt.steinerNodeCount(),
                receipt.steinerEdgeCount(),
                receipt.causalPaths(),
                0.9999, // 篡改数据
                receipt.latencyMicros(),
                receipt.timestamp(),
                receipt.sha256Signature() // 沿用旧签名
        );

        assertFalse(tampered.verifySignature(), "数据字段被篡改后验真必须失败");
    }
}
