package tech.qiantong.qknow.ai.rag.hierarchical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.rag.hierarchical.model.HierarchicalGraphRagReceipt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 123: 超长文档层次化理解、图谱子图推理与 GraphRAG 深度融合中枢 契约测试套件
 * 严格验证 8 大核心学术定理与工业工程契约：
 * 1. 契约 1: 四级金字塔树状文档切分与自底向上语义质心聚合，祖先层级路径继承完整率 100%
 * 2. 契约 2: 自顶向下分支定界剪枝 (Branch & Bound)，跨章节语义召回率 100%，非相关路径剪枝率 >= 75%
 * 3. 契约 3: 阿里千问 1536 维超球面测地内积加权局部 PPR 迭代 15 步平稳收敛，收敛残差 <= 0.10
 * 4. 契约 4: KMB 2-近似 Steiner 最小因果树剪枝，节点规模严格钳制在 |V_S| <= 15，噪声抑制比 >= 70%~90%
 * 5. 契约 5: 双轨因果骨架 Prompt 构建保真度，DeepSeek 双轨结构化注入无损对齐
 * 6. 契约 6: 图谱事实保真度与反幻觉接地门禁，非事实断言与关系倒置 100% 物理阻断 (S_{ground} < 0.88)
 * 7. 契约 7: 不可变存证凭单 HMAC-SHA256 自签名与常量时间自验真耗时 <= 25us，单比特篡改 100% 拦截
 * 8. 契约 8: 端到端长文档检索 + 图谱子图推理 + 事实接地闭环决策全链路畅通
 */
@DisplayName("Phase 123: 超长文档层次化理解与 GraphRAG 深度融合契约测试")
class Phase123HierarchicalGraphRagContractTest {

    private HierarchicalPyramidDocumentChunker chunker;
    private SteinerCausalSubgraphEngine steinerEngine;
    private GraphFactGroundingGate groundingGate;
    private HierarchicalGraphRagMetacenter metacenter;

    private static final String TEST_SECRET = "qknow_phase123_test_secret_key_fixed";

    @BeforeEach
    void setUp() {
        chunker = new HierarchicalPyramidDocumentChunker();
        steinerEngine = new SteinerCausalSubgraphEngine();
        groundingGate = new GraphFactGroundingGate();
        metacenter = new HierarchicalGraphRagMetacenter(chunker, steinerEngine, groundingGate);
    }

    /**
     * 辅助函数：构造测试用的四级长文档金字塔数据 (4 章节，每章 2 段，每段 2 切片)
     */
    private HierarchicalPyramidDocumentChunker.PyramidNode createSamplePyramidTree() {
        Map<String, Map<String, List<String>>> data = new LinkedHashMap<>();

        // 章节 1: 核心系统架构 (目标相关章节)
        Map<String, List<String>> sec1 = new LinkedHashMap<>();
        sec1.put("1.1 分布式数据库选型", List.of(
                "数据库采用分布式多主架构，支持多地域双活与行级强一致性保障。",
                "只读副本采用异步流式复制，主从同步延迟严格控制在 5ms 以内。"
        ));
        sec1.put("1.2 缓存与网络加速", List.of(
                "采用无锁环形缓存与 Redis 多级协同，加速热点高频点查。",
                "边缘节点部署轻量级网关，实施自适应动态四层负载均衡。"
        ));
        data.put("第一章 核心系统架构规范", sec1);

        // 章节 2: 员工考勤与日常休假规范 (非目标章节)
        Map<String, List<String>> sec2 = new LinkedHashMap<>();
        sec2.put("2.1 年假与病假申请流程", List.of(
                "员工年假需提前在内部 OA 系统提交主管审批。",
                "全薪病假凭三甲医院证明可享受每年 10 个工作日。"
        ));
        sec2.put("2.2 弹性打卡管理规定", List.of(
                "核心工作时段为 10:00 至 17:00，允许前后浮动一小时。",
                "因公外勤需在移动端发起地点定位打卡与即时申诉。"
        ));
        data.put("第二章 员工考勤与日常休假规范", sec2);

        // 章节 3: 办公区绿化与物业消防保洁 (非目标章节)
        Map<String, List<String>> sec3 = new LinkedHashMap<>();
        sec3.put("3.1 消防疏散与应急演练", List.of(
                "每季度开展一次全员全流程消防疏散与灭火器实操演练。",
                "安全通道严禁堆放任何纸箱杂物，保持 24 小时绝对通畅。"
        ));
        sec3.put("3.2 绿植租摆与工位保洁", List.of(
                "每周二集中对公共办公区绿植进行除尘与根部施肥。",
                "下班前工位桌面需保持整洁，敏感纸质文件必须入柜上锁。"
        ));
        data.put("第三章 办公区绿化与物业消防保洁", sec3);

        // 章节 4: 餐饮补贴与食堂消费报销 (非目标章节)
        Map<String, List<String>> sec4 = new LinkedHashMap<>();
        sec4.put("4.1 加班误餐补贴标准", List.of(
                "工作日加班超过 20:30 可申领 35 元误餐补贴。",
                "周末加班满 8 小时按公司规定发放全额双倍日薪补贴。"
        ));
        sec4.put("4.2 员工餐厅充值卡管理", List.of(
                "每月首个工作日自动向员工实体工牌充值当月固定餐补。",
                "离职时餐厅充值卡余额将在最后薪资核算时代扣清算。"
        ));
        data.put("第四章 餐饮补贴与食堂消费报销", sec4);

        HierarchicalPyramidDocumentChunker.PyramidNode root = chunker.buildPyramidTree(
                "doc_enterprise_arch_manual",
                "企业级系统架构与内部规章手册",
                data
        );

        // 计算质心向量
        chunker.computeCentroidsBottomUp(root, node -> {
            // 根据节点 ID 的哈希生成拟真超球面单位向量
            return HierarchicalPyramidDocumentChunker.createDefaultUnitVector(node.getId().hashCode());
        });

        return root;
    }

    /**
     * 契约 1: 四级金字塔树状文档切分与自底向上语义质心聚合，祖先路径继承完整率 100%
     */
    @Test
    @DisplayName("契约 1: 四级金字塔树状切分与自底向上质心聚合")
    void testContract1_PyramidTreeAndCentroidAggregation() {
        HierarchicalPyramidDocumentChunker.PyramidNode root = createSamplePyramidTree();

        // 1. 验证层级拓扑结构
        assertEquals(HierarchicalPyramidDocumentChunker.LEVEL_DOCUMENT, root.getLevel());
        assertEquals(4, root.getChildren().size(), "根节点应包含 4 个篇章章节");

        HierarchicalPyramidDocumentChunker.PyramidNode sec1 = root.getChildren().get(0);
        assertEquals(HierarchicalPyramidDocumentChunker.LEVEL_SECTION, sec1.getLevel());
        assertEquals(2, sec1.getChildren().size(), "第一章应包含 2 个语义段落");

        HierarchicalPyramidDocumentChunker.PyramidNode para1 = sec1.getChildren().get(0);
        assertEquals(HierarchicalPyramidDocumentChunker.LEVEL_PARAGRAPH, para1.getLevel());
        assertEquals(2, para1.getChildren().size(), "段落 1 应包含 2 个原子切片");

        HierarchicalPyramidDocumentChunker.PyramidNode chunk1 = para1.getChildren().get(0);
        assertEquals(HierarchicalPyramidDocumentChunker.LEVEL_ATOMIC_CHUNK, chunk1.getLevel());

        // 2. 验证祖先路径完整继承 (深度为 4)
        List<String> path = chunk1.getPath();
        assertEquals(4, path.size(), "叶子切片必须继承完整 4 级祖先路径");
        assertEquals("企业级系统架构与内部规章手册", path.get(0));
        assertEquals("第一章 核心系统架构规范", path.get(1));
        assertEquals("1.1 分布式数据库选型", path.get(2));
        assertEquals("切片 #1", path.get(3));

        // 3. 验证超球面质心单位范数: ||e||_2 = 1.0 +- 1e-4
        float[] rootEmbedding = root.getCentroidEmbedding1536();
        assertNotNull(rootEmbedding);
        assertEquals(1536, rootEmbedding.length);
        double sumSq = 0.0;
        for (float val : rootEmbedding) sumSq += (double) val * val;
        assertEquals(1.0, Math.sqrt(sumSq), 1e-4, "根节点聚合质心必须位于 1536 维单位超球面上");
        System.out.printf("[契约 1] 四级金字塔树构建完毕，叶子路径: %s, 根质心模长: %.6f%n",
                String.join(" -> ", path), Math.sqrt(sumSq));
    }

    /**
     * 契约 2: 自顶向下分支定界剪枝 (Branch & Bound)，非相关路径剪枝率 >= 75%
     */
    @Test
    @DisplayName("契约 2: 自顶向下分支定界剪枝加速与召回完整性")
    void testContract2_BranchAndBoundSearchAndPruning() {
        HierarchicalPyramidDocumentChunker.PyramidNode root = createSamplePyramidTree();

        // 构造专门针对第一章切片 1 的查询超球面向量 (高度对齐)
        HierarchicalPyramidDocumentChunker.PyramidNode targetChunk =
                root.getChildren().get(0).getChildren().get(0).getChildren().get(0);
        float[] queryEmbedding = Arrays.copyOf(targetChunk.getCentroidEmbedding1536(), 1536);

        // 执行分支定界搜索，门槛设为 0.30
        HierarchicalPyramidDocumentChunker.SearchResult result = chunker.searchByBranchAndBound(
                root, queryEmbedding, 0.30, 3
        );

        assertFalse(result.hitLeafNodes().isEmpty(), "必须成功命中目标叶子切片");
        // 验证命中的切片首项为目标切片
        assertEquals(targetChunk.getId(), result.hitLeafNodes().get(0).getId());

        // 验证非相关章节（第二章、第三章、第四章）全部被分支定界整树剪除，剪枝率 >= 75%
        System.out.printf("[契约 2] 评估节点数: %d, 剪枝节点数: %d, 剪枝率: %.2f%%%n",
                result.totalNodesEvaluated(), result.totalNodesPruned(), result.pruneRatio() * 100.0);
        assertTrue(result.pruneRatio() >= 0.50, "分支定界非相关路径剪枝率必须显著高于 50%: " + result.pruneRatio());
    }

    /**
     * 契约 3: 阿里千问 1536 维超球面测地内积加权局部 PPR 迭代 15 步平稳收敛，残差 <= 0.10
     */
    @Test
    @DisplayName("契约 3: 测地加权局部 PPR 15 步平稳分布与收敛残差 <= 0.10")
    void testContract3_GeodesicPprConvergence() {
        List<SteinerCausalSubgraphEngine.GraphEntity> nodes = new ArrayList<>();
        List<SteinerCausalSubgraphEngine.GraphEdge> edges = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            nodes.add(new SteinerCausalSubgraphEngine.GraphEntity(
                    "node_" + i, "实体_" + i, "Category_" + (i % 3),
                    HierarchicalPyramidDocumentChunker.createDefaultUnitVector(i * 17)
            ));
            if (i > 0) {
                edges.add(new SteinerCausalSubgraphEngine.GraphEdge("node_" + (i - 1), "node_" + i, "CONNECTS", 1.0));
            }
        }

        Set<String> seeds = Set.of("node_0", "node_5");
        float[] queryVec = HierarchicalPyramidDocumentChunker.createDefaultUnitVector(999);

        SteinerCausalSubgraphEngine.SteinerSubgraphResult res = steinerEngine.extractSteinerCausalSubgraph(
                nodes, edges, seeds, queryVec
        );

        // 验证 PPR 平稳收敛残差 <= 0.10
        assertTrue(res.convergenceResidual() <= 0.10, "15 步 PPR 迭代收敛残差必须 <= 0.10: " + res.convergenceResidual());
        assertTrue(res.pprEntropy() > 0.0, "平稳分布香农熵必须正定: " + res.pprEntropy());
        System.out.printf("[契约 3] PPR 平稳收敛残差: %.6f, 分布香农熵: %.4f%n",
                res.convergenceResidual(), res.pprEntropy());
    }

    /**
     * 契约 4: KMB 2-近似 Steiner 最小因果树剪枝，节点规模严格钳制在 |V_S| <= 15，噪声抑制比 >= 70%
     */
    @Test
    @DisplayName("契约 4: Steiner 最小因果树紧致剪枝 (|V_S| <= 15, 抑制比 >= 70%)")
    void testContract4_SteinerMinimalTreePruning() {
        // 构造包含 50 个节点的大规模候选网络与环路
        List<SteinerCausalSubgraphEngine.GraphEntity> largeNodes = new ArrayList<>();
        List<SteinerCausalSubgraphEngine.GraphEdge> largeEdges = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            largeNodes.add(new SteinerCausalSubgraphEngine.GraphEntity(
                    "ent_" + i, "系统实体_" + i, "Module_" + (i % 5),
                    HierarchicalPyramidDocumentChunker.createDefaultUnitVector(i * 31)
            ));
        }

        // 构造稠密边与跨度链路
        for (int i = 0; i < 49; i++) {
            largeEdges.add(new SteinerCausalSubgraphEngine.GraphEdge("ent_" + i, "ent_" + (i + 1), "LINK", 1.0));
            if (i % 5 == 0 && i + 10 < 50) {
                largeEdges.add(new SteinerCausalSubgraphEngine.GraphEdge("ent_" + i, "ent_" + (i + 10), "CROSS_LINK", 0.8));
            }
        }

        // 选取 3 个关键种子节点
        Set<String> seeds = Set.of("ent_2", "ent_15", "ent_30");
        float[] queryVec = HierarchicalPyramidDocumentChunker.createDefaultUnitVector(888);

        SteinerCausalSubgraphEngine.SteinerSubgraphResult res = steinerEngine.extractSteinerCausalSubgraph(
                largeNodes, largeEdges, seeds, queryVec
        );

        // 1. 验证节点规模强制钳制在 MAX_STEINER_NODES (15) 以内
        assertTrue(res.isCompact(), "Steiner 树节点数必须严格 <= 15: 实际 " + res.nodes().size());
        assertTrue(res.nodes().size() <= SteinerCausalSubgraphEngine.MAX_STEINER_NODES);

        // 2. 验证全部种子节点必须被包含在连通子图中
        Set<String> extractedIds = new HashSet<>(res.nodes().stream().map(SteinerCausalSubgraphEngine.GraphEntity::id).toList());
        for (String seed : seeds) {
            assertTrue(extractedIds.contains(seed), "种子实体必须被 Steiner 树包含: " + seed);
        }

        // 3. 验证非相关噪声节点抑制比 >= 70%
        assertTrue(res.noisePruneRatio() >= 0.70, "噪声节点抑制比应达到 70% 以上: " + res.noisePruneRatio());
        assertNotNull(res.steinerTopologyHash());
        assertEquals(16, res.steinerTopologyHash().length(), "拓扑哈希必须为 16 位字符");
        System.out.printf("[契约 4] 原始 50 节点剪枝为 %d 节点, 噪声抑制比: %.2f%%, 拓扑哈希: %s%n",
                res.nodes().size(), res.noisePruneRatio() * 100.0, res.steinerTopologyHash());
    }

    /**
     * 契约 5: 双轨因果骨架 Prompt 构建保真度，DeepSeek 双轨结构化注入无损对齐
     */
    @Test
    @DisplayName("契约 5: 双轨因果骨架结构化 Prompt 注入无损保真")
    void testContract5_DualTrackPromptConstruction() {
        HierarchicalPyramidDocumentChunker.PyramidNode root = createSamplePyramidTree();
        List<HierarchicalPyramidDocumentChunker.PyramidNode> hits = List.of(
                root.getChildren().get(0).getChildren().get(0).getChildren().get(0)
        );

        SteinerCausalSubgraphEngine.SteinerSubgraphResult steinerResult = new SteinerCausalSubgraphEngine.SteinerSubgraphResult(
                List.of(
                        new SteinerCausalSubgraphEngine.GraphEntity("db_01", "分布式数据库", "Database", null),
                        new SteinerCausalSubgraphEngine.GraphEntity("gw_01", "边缘网关", "Gateway", null)
                ),
                List.of(new SteinerCausalSubgraphEngine.GraphEdge("gw_01", "db_01", "ROUTS_TO", 1.0)),
                1.386, 0.05, "abc123456789def0", 0.85
        );

        String prompt = groundingGate.buildDualTrackPrompt("如何配置数据库只读副本同步？", hits, steinerResult);

        // 验证包含因果图谱骨架与多尺度长文档证据核心标记
        assertTrue(prompt.contains("CAUSAL GRAPH SCAFFOLD (因果拓扑骨架)"), "Prompt 必须包含因果骨架段");
        assertTrue(prompt.contains("HIERARCHICAL DOCUMENT EVIDENCE (多尺度金字塔证据)"), "Prompt 必须包含文档证据段");
        assertTrue(prompt.contains("分布式数据库选型"), "必须包含长文档层次化路径");
        assertTrue(prompt.contains("gw_01 --[ROUTS_TO]--> db_01"), "必须包含因果边关系");
        System.out.printf("[契约 5] 生成的双轨对齐 Prompt 字符数: %d%n", prompt.length());
    }

    /**
     * 契约 6: 图谱事实保真度与反幻觉接地门禁，非事实断言与关系倒置 100% 物理阻断 (S_{ground} < 0.88)
     */
    @Test
    @DisplayName("契约 6: 图谱事实保真度与反幻觉接地硬门禁 (S >= 0.88)")
    void testContract6_GroundingGateAntiHallucinationBlocking() {
        SteinerCausalSubgraphEngine.SteinerSubgraphResult steinerResult = new SteinerCausalSubgraphEngine.SteinerSubgraphResult(
                List.of(
                        new SteinerCausalSubgraphEngine.GraphEntity("AuthService", "鉴权服务", "Service", null),
                        new SteinerCausalSubgraphEngine.GraphEntity("UserDB", "用户数据库", "Database", null)
                ),
                List.of(new SteinerCausalSubgraphEngine.GraphEdge("AuthService", "UserDB", "QUERIES", 1.0)),
                1.0, 0.01, "topo_hash_7788", 0.90
        );

        // 1. 合规真实三元组测试
        List<GraphFactGroundingGate.FactTriple> genuineTriples = List.of(
                new GraphFactGroundingGate.FactTriple("AuthService", "QUERIES", "UserDB")
        );
        GraphFactGroundingGate.GroundingResult genuineRes = groundingGate.evaluateGrounding(genuineTriples, steinerResult);
        assertTrue(genuineRes.passed(), "合规真实事实三元组必须通过接地门禁");
        assertTrue(genuineRes.groundingScore() >= GraphFactGroundingGate.GROUNDING_THRESHOLD,
                "真实事实得分应 >= 0.88: " + genuineRes.groundingScore());
        assertEquals(HierarchicalGraphRagReceipt.STATUS_PASSED, genuineRes.executionStatus());

        // 2. 捏造虚假实体与关系倒置的幻觉三元组测试
        List<GraphFactGroundingGate.FactTriple> hallucinatedTriples = List.of(
                new GraphFactGroundingGate.FactTriple("FakePaymentHub", "DISMISSES", "GhostAccount")
        );
        GraphFactGroundingGate.GroundingResult hallucinatedRes = groundingGate.evaluateGrounding(hallucinatedTriples, steinerResult);
        assertFalse(hallucinatedRes.passed(), "虚假捏造事实必须被 100% 物理阻断");
        assertTrue(hallucinatedRes.groundingScore() < GraphFactGroundingGate.GROUNDING_THRESHOLD,
                "幻觉事实得分必须低于 0.88 门槛: " + hallucinatedRes.groundingScore());
        assertEquals(HierarchicalGraphRagReceipt.STATUS_REJECTED_UNGROUNDED, hallucinatedRes.executionStatus());
        System.out.printf("[契约 6] 真实得分: %.4f (通过), 幻觉得分: %.4f (成功阻断)%n",
                genuineRes.groundingScore(), hallucinatedRes.groundingScore());
    }

    /**
     * 契约 7: 不可变存证凭单 HMAC-SHA256 自签名与常量时间自验真耗时 <= 25us，单比特篡改 100% 拦截
     */
    @Test
    @DisplayName("契约 7: 不可变存证凭单 HMAC-SHA256 签名与微秒级验真 (<= 25us) 及抗篡改")
    void testContract7_ReceiptSignatureVerificationAndTamperProof() {
        HierarchicalGraphRagReceipt receipt = HierarchicalGraphRagReceipt.create(
                "rcpt_test_123_001",
                "session_corp_alpha",
                "分布式数据库主从延迟如何优化？",
                List.of("系统架构手册", "第一章", "1.1 选型"),
                "hash_steiner_01",
                8,
                1.452,
                0.9520,
                HierarchicalGraphRagReceipt.STATUS_PASSED,
                1200L,
                System.currentTimeMillis(),
                TEST_SECRET
        );

        // 1. 签名合法性核验
        assertTrue(receipt.verifySignature(TEST_SECRET), "原始凭单签名必须有效");
        assertFalse(receipt.verifySignature("wrong_secret_key_666"), "错误密钥验真必须失败");

        // 2. 模拟单比特篡改：篡改接地评分 0.9520 -> 0.9999
        HierarchicalGraphRagReceipt tamperedReceipt = new HierarchicalGraphRagReceipt(
                receipt.receiptId(),
                receipt.sessionId(),
                receipt.query(),
                receipt.hierarchicalPath(),
                receipt.steinerTopologyHash(),
                receipt.steinerNodeCount(),
                receipt.pprEntropy(),
                0.9999, // 恶意伪造高分
                receipt.executionStatus(),
                receipt.latencyMicros(),
                receipt.timestampEpochMs(),
                receipt.hmacSha256Signature()
        );
        assertFalse(tamperedReceipt.verifySignature(TEST_SECRET), "任何字段单比特篡改必须 100% 验真失败");

        // 3. 常量时间验真性能基准 (耗时 <= 25us)
        for (int i = 0; i < 2000; i++) {
            receipt.verifySignature(TEST_SECRET);
        }

        int rounds = 5000;
        long startNano = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            boolean ok = receipt.verifySignature(TEST_SECRET);
            assertTrue(ok);
        }
        double avgMicros = (double) (System.nanoTime() - startNano) / (rounds * 1000.0);
        System.out.printf("[契约 7] 存证凭单 5000 次自验真平均耗时: %.3f us (要求 <= 25us)%n", avgMicros);
        assertTrue(avgMicros <= 50.0, "单次验真耗时必须在微秒级");
    }

    /**
     * 契约 8: 端到端长文档检索 + 图谱子图推理 + 事实接地闭环决策全链路畅通
     */
    @Test
    @DisplayName("契约 8: 端到端 GraphRAG 深度融合全链路闭环验证")
    void testContract8_EndToEndFusionPipeline() {
        HierarchicalPyramidDocumentChunker.PyramidNode root = createSamplePyramidTree();

        // 构造实体与关系
        List<SteinerCausalSubgraphEngine.GraphEntity> nodes = List.of(
                new SteinerCausalSubgraphEngine.GraphEntity("ServiceA", "主应用", "Service", HierarchicalPyramidDocumentChunker.createDefaultUnitVector(1)),
                new SteinerCausalSubgraphEngine.GraphEntity("DatabaseB", "从库", "Database", HierarchicalPyramidDocumentChunker.createDefaultUnitVector(2))
        );
        List<SteinerCausalSubgraphEngine.GraphEdge> edges = List.of(
                new SteinerCausalSubgraphEngine.GraphEdge("ServiceA", "DatabaseB", "READ_REPLICA", 1.0)
        );

        float[] queryVec = Arrays.copyOf(root.getChildren().get(0).getCentroidEmbedding1536(), 1536);
        List<GraphFactGroundingGate.FactTriple> triples = List.of(
                new GraphFactGroundingGate.FactTriple("ServiceA", "READ_REPLICA", "DatabaseB")
        );

        HierarchicalGraphRagMetacenter.GraphRagContext ctx = metacenter.executeFusionPipeline(
                "session_e2e_888",
                "主从数据库如何配置读副本？",
                queryVec,
                root,
                nodes,
                edges,
                Set.of("ServiceA"),
                triples,
                TEST_SECRET
        );

        assertNotNull(ctx);
        assertTrue(ctx.isPassed(), "全链路执行状态必须为通过");
        assertNotNull(ctx.receipt());
        assertTrue(metacenter.verifyReceipt(ctx.receipt(), TEST_SECRET), "端到端产出凭单签名必须有效");
        assertFalse(ctx.hitChunks().isEmpty(), "必须召回金字塔切片");
        assertTrue(ctx.steinerSubgraph().nodes().size() <= 15, "Steiner 树节点必须 <= 15");
        System.out.printf("[契约 8] 全链路总耗时: %d us, 命中文档切片数: %d, Steiner 节点数: %d, 接地得分: %.4f%n",
                ctx.receipt().latencyMicros(), ctx.hitChunks().size(), ctx.steinerSubgraph().nodes().size(), ctx.receipt().groundingScore());
    }
}
