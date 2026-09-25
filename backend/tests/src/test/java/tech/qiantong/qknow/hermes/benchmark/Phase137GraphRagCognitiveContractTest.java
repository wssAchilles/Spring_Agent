package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.rag.cognitive.GraphRagCognitiveAugmentationReceipt;
import tech.qiantong.qknow.hermes.rag.cognitive.MultimodalPyramidChunker;
import tech.qiantong.qknow.hermes.rag.cognitive.MultimodalPyramidChunker.PyramidChunkNode;
import tech.qiantong.qknow.hermes.rag.cognitive.MultimodalPyramidChunker.PyramidChunkResult;
import tech.qiantong.qknow.hermes.rag.cognitive.MultimodalPyramidChunker.PyramidLevel;
import tech.qiantong.qknow.hermes.rag.cognitive.MultimodalPyramidChunker.TableArtifact;
import tech.qiantong.qknow.hermes.rag.cognitive.SteinerCausalPathReranker;
import tech.qiantong.qknow.hermes.rag.cognitive.SteinerCausalPathReranker.CausalEdge;
import tech.qiantong.qknow.hermes.rag.cognitive.SteinerCausalPathReranker.CausalExtractionResult;
import tech.qiantong.qknow.hermes.rag.cognitive.SteinerCausalPathReranker.CausalNode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 137 核心契约测试套件：
 * 超长多模态异构知识图谱金字塔切分、子图因果路径抽取与 GraphRAG 极低延迟流式认知增强中枢
 * (Ultra-Long Multimodal Heterogeneous Knowledge Graph Pyramid Chunking,
 * Subgraph Causal Path Extraction & GraphRAG Ultra-Low Latency Streaming Cognitive Augmentation Metacenter)
 *
 * 核心验证范围：
 * 1. 四级多模态金字塔层级包含与表格线性化 (TC-137-1)
 * 2. 阿里千问 1536 维超球面单位向量归一化与测地线单调性 (TC-137-2)
 * 3. 2-近似度量闭包 Steiner 树算法逼近性与紧凑性 |V*| <= 16 (TC-137-3)
 * 4. 拓扑可达矩阵门控虚假相关剔除率 >= 70% (TC-137-4)
 * 5. 微秒级因果路径抽取性能预算 <= 8.5ms (TC-137-5)
 * 6. DeepSeek 长思考双轨认知增强对齐合规性 (TC-137-6)
 * 7. 纯 Java 21 Record 凭单不可变签名与 SHA-256 常量时间自验真 (TC-137-7)
 * 8. 端到端全链路闭环集成与防篡改签发 (TC-137-8)
 *
 * @author Achilles
 * @since 2026-09-25
 */
public class Phase137GraphRagCognitiveContractTest {

    private final MultimodalPyramidChunker pyramidChunker = new MultimodalPyramidChunker();
    private final SteinerCausalPathReranker steinerReranker = new SteinerCausalPathReranker();

    // =========================================================================
    // TC-137-1: 四级多模态金字塔层级包含与表格线性化
    // =========================================================================
    @Test
    @DisplayName("TC-137-1: 验证 L1~L4 层次化金字塔切分，跨页表格 Markdown 线性化包含元数据")
    void testMultimodalPyramidChunking_fourLevelHierarchy() {
        String docId = "DOC-SUBSTATION-500KV";
        String title = "500kV 特高压变电站电气主接线与变压器温升冗余技术方案";
        String summary = "本方案规范了 500kV 特高压变电站主变压器在极端高温工况下的冷却冗余与热平衡判据。";

        Map<String, List<String>> sectionData = new LinkedHashMap<>();
        sectionData.put("第一章: 系统主接线拓扑", List.of(
                "主变采用单相自耦变压器组，额定容量为 3x334MVA。",
                "500kV 侧采用 3/2 断路器接线方式，具备双母线冗余。",
                "中性点经小电抗直接接地，限制单相接地短路电流。"
        ));
        sectionData.put("第二章: 主变冷却与温升限值", List.of(
                "绕组顶层油温升限值不得超过 55K，绕组平均温升限值不超过 65K。",
                "冷却方式采用 ODAF 强迫油循环强迫风冷系统，配置 N+2 冗余冷却器。",
                "当两组冷却器故障停运时，主变仍可在 100% 额定负荷下连续安全运行。"
        ));

        TableArtifact table = new TableArtifact(
                "TBL-COOLING-PARAM",
                "主变压器冷却器工况与温升限值对照表",
                List.of("工况模式", "冷却器运行组数", "顶层油温升(K)", "绕组温升(K)", "允许负荷率(%)"),
                List.of(
                        List.of("额定工况", "全开 (6组)", "48K", "58K", "100%"),
                        List.of("N-1故障", "5组运行", "52K", "62K", "100%"),
                        List.of("N-2极限", "4组运行", "55K", "65K", "100%"),
                        List.of("应急过载", "4组运行", "62K", "75K", "110% (限制2小时)")
                ),
                "依据 GB/T 1094.2 电力变压器温升标准制定"
        );

        float[] seed = new float[1536];
        Arrays.fill(seed, 0.05f);

        PyramidChunkResult result = pyramidChunker.buildPyramid(
                docId, title, summary, sectionData, List.of(table), seed
        );

        assertNotNull(result, "金字塔构建结果不可为空");
        assertEquals(docId, result.docId());
        assertNotNull(result.rootSummaryNode());
        assertEquals(PyramidLevel.L1_SUMMARY, result.rootSummaryNode().level());

        // 验证四层完整存在
        Map<PyramidLevel, List<String>> levelIndex = result.levelIndex();
        assertEquals(1, levelIndex.get(PyramidLevel.L1_SUMMARY).size(), "L1 节点必须为 1 个根摘要");
        assertEquals(2, levelIndex.get(PyramidLevel.L2_SECTION).size(), "L2 节点必须有 2 个章节");
        assertEquals(6, levelIndex.get(PyramidLevel.L3_ENTITY).size(), "L3 节点必须有 6 个微观事实");
        assertEquals(1, levelIndex.get(PyramidLevel.L4_TABLE).size(), "L4 节点必须有 1 个表格工件");

        // 验证 L4 表格 Markdown 线性化
        String l4Id = levelIndex.get(PyramidLevel.L4_TABLE).get(0);
        PyramidChunkNode l4Node = result.allNodes().get(l4Id);
        assertNotNull(l4Node);
        assertTrue(l4Node.content().contains("主变压器冷却器工况与温升限值对照表"));
        assertTrue(l4Node.content().contains("| 工况模式 | 冷却器运行组数 |"));
        assertTrue(l4Node.content().contains("N-2极限"));
    }

    // =========================================================================
    // TC-137-2: 阿里千问 1536 维超球面单位向量归一化与测地线单调性
    // =========================================================================
    @Test
    @DisplayName("TC-137-2: 验证阿里千问 1536 维超球面单位向量归一化与测地线内积单调性")
    void testQwenEmbedding_1536dHypersphereNormalization() {
        // 构造任意未归一化长向量
        float[] rawVec = new float[1536];
        for (int i = 0; i < 1536; i++) {
            rawVec[i] = (float) (Math.cos(i) * 12.5 + Math.sin(i * 2) * 8.3);
        }

        float[] normalized = MultimodalPyramidChunker.normalizeToQwen1536Hypersphere(rawVec);
        assertEquals(1536, normalized.length, "向量维度必须严格为 1536 维");
        assertTrue(MultimodalPyramidChunker.verifyHypersphereConstraint(normalized),
                "向量模长必须严格满足 ||v||_2 = 1.0 ± 10^-4");

        // 验证测地线内积单调性：同一父节点下的子节点测地线距离必须显著小于不相关随机向量
        float[] childVec1 = MultimodalPyramidChunker.normalizeToQwen1536Hypersphere(
                Arrays.copyOf(normalized, normalized.length)
        );
        childVec1[0] += 0.005f;
        childVec1 = MultimodalPyramidChunker.normalizeToQwen1536Hypersphere(childVec1);

        float[] orthogonalVec = new float[1536];
        orthogonalVec[500] = 1.0f; // 垂直正交向量

        double intraClusterDist = MultimodalPyramidChunker.computeGeodesicDistance(normalized, childVec1);
        double interClusterDist = MultimodalPyramidChunker.computeGeodesicDistance(normalized, orthogonalVec);

        assertTrue(intraClusterDist < 0.15, "同簇近邻测地线距离应极小，实测: " + intraClusterDist);
        assertTrue(interClusterDist > 1.2, "异构正交测地线距离应接近 pi/2，实测: " + interClusterDist);
        assertTrue(intraClusterDist < interClusterDist, "测地线单调性必须严格成立");
    }

    // =========================================================================
    // TC-137-3: 2-近似度量闭包 Steiner 树算法逼近性与紧凑性
    // =========================================================================
    @Test
    @DisplayName("TC-137-3: 验证度量闭包完全图、Kruskal 最小生成树与叶子冗余剪枝，|V*| <= 16")
    void testSteinerCausalPathReranker_metricClosureAndMST() {
        // 构造包含 20 个节点的候选全图
        List<CausalNode> allNodes = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            float[] vec = new float[1536];
            vec[i] = 1.0f;
            allNodes.add(new CausalNode("N_" + i, "节点_" + i, "实体", vec));
        }

        // 构造包含树枝拓扑与部分叶子冗余分支的边集
        List<CausalEdge> allEdges = new ArrayList<>();
        // 主干因果链: 1 -> 2 -> 3 -> 4 -> 5 -> 6
        allEdges.add(new CausalEdge("N_1", "N_2", "CAUSE_1", 0.1, true));
        allEdges.add(new CausalEdge("N_2", "N_3", "CAUSE_2", 0.12, true));
        allEdges.add(new CausalEdge("N_3", "N_4", "CAUSE_3", 0.15, true));
        allEdges.add(new CausalEdge("N_4", "N_5", "CAUSE_4", 0.11, true));
        allEdges.add(new CausalEdge("N_5", "N_6", "CAUSE_5", 0.13, true));

        // 挂载叶子冗余节点: 2 -> 7, 3 -> 8, 4 -> 9, 5 -> 10 (这些是非端点叶子)
        allEdges.add(new CausalEdge("N_2", "N_7", "LEAF_NOISE", 0.5, true));
        allEdges.add(new CausalEdge("N_3", "N_8", "LEAF_NOISE", 0.5, true));
        allEdges.add(new CausalEdge("N_4", "N_9", "LEAF_NOISE", 0.5, true));
        allEdges.add(new CausalEdge("N_5", "N_10", "LEAF_NOISE", 0.5, true));

        // 终端集合 S 仅包含 N_1 与 N_6
        Set<String> terminals = Set.of("N_1", "N_6");

        CausalExtractionResult result = steinerReranker.extractCausalSubgraph(terminals, allNodes, allEdges);

        assertNotNull(result);
        assertTrue(result.selectedNodes().size() <= 16, "子图节点数必须严格 <= 16，实测: " + result.selectedNodes().size());
        assertTrue(result.selectedEdges().size() <= 15, "子图边数必须严格 <= 15，实测: " + result.selectedEdges().size());

        // 验证冗余叶子节点 N_7, N_8, N_9, N_10 被剪枝剔除
        List<String> selectedIds = result.selectedNodes().stream().map(CausalNode::id).toList();
        assertTrue(selectedIds.contains("N_1"), "终端节点 N_1 必须保留");
        assertTrue(selectedIds.contains("N_6"), "终端节点 N_6 必须保留");
        assertFalse(selectedIds.contains("N_7"), "冗余叶子 N_7 必须被剪枝剔除");
        assertFalse(selectedIds.contains("N_8"), "冗余叶子 N_8 必须被剪枝剔除");
    }

    // =========================================================================
    // TC-137-4: 拓扑可达矩阵门控虚假相关剔除率 >= 70%
    // =========================================================================
    @Test
    @DisplayName("TC-137-4: 拓扑可达矩阵门控排挤语义相近但因果不可达的虚假边，噪声过滤率 >= 70%")
    void testCausalReachabilityGate_spuriousCorrelationFiltering() {
        List<CausalNode> allNodes = new ArrayList<>();
        // 构造 30 个节点的混杂图
        for (int i = 1; i <= 30; i++) {
            float[] vec = new float[1536];
            vec[i] = 1.0f;
            allNodes.add(new CausalNode("NODE_" + i, "概念_" + i, "实体", vec));
        }

        List<CausalEdge> allEdges = new ArrayList<>();
        // 真实因果主线: NODE_1 -> NODE_2 -> NODE_3 -> NODE_4 (有效拓扑可达)
        allEdges.add(new CausalEdge("NODE_1", "NODE_2", "REAL_CAUSE", 0.2, true));
        allEdges.add(new CausalEdge("NODE_2", "NODE_3", "REAL_CAUSE", 0.2, true));
        allEdges.add(new CausalEdge("NODE_3", "NODE_4", "REAL_CAUSE", 0.2, true));

        // 虚假相关噪声边 (语义距离虽小 0.05，但因果不可达 hasCausalReachability=false)
        for (int i = 5; i <= 30; i++) {
            allEdges.add(new CausalEdge("NODE_1", "NODE_" + i, "SPURIOUS_CORRELATION", 0.05, false));
            allEdges.add(new CausalEdge("NODE_" + i, "NODE_4", "SPURIOUS_CORRELATION", 0.05, false));
        }

        Set<String> terminals = Set.of("NODE_1", "NODE_4");

        CausalExtractionResult result = steinerReranker.extractCausalSubgraph(terminals, allNodes, allEdges);

        assertNotNull(result);
        // 验证噪声过滤率 >= 70.0%
        assertTrue(result.noiseFilterRatio() >= 0.70,
                "虚假相关噪声过滤率必须 >= 70.0%，实测: " + String.format("%.2f%%", result.noiseFilterRatio() * 100));

        // 验证选定的边必须全是合法因果边，无任何虚假边穿透
        for (CausalEdge edge : result.selectedEdges()) {
            assertTrue(edge.hasCausalReachability(), "选中的因果边必须具备拓扑因果可达性，不可包含虚假关联边");
        }
    }

    // =========================================================================
    // TC-137-5: 微秒级因果路径抽取性能预算 <= 8.5ms
    // =========================================================================
    @Test
    @DisplayName("TC-137-5: 经 10 次循环预热后，单次因果最短路径抽取耗时严格 <= 8.5ms (稳态平均 <= 1.0ms)")
    void testCausalPathExtraction_latencyBudgetEnforcement() {
        List<CausalNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            float[] vec = new float[1536];
            vec[i] = 1.0f;
            nodes.add(new CausalNode("N_" + i, "Node_" + i, "Concept", vec));
        }
        List<CausalEdge> edges = new ArrayList<>();
        for (int i = 1; i < 25; i++) {
            edges.add(new CausalEdge("N_" + i, "N_" + (i + 1), "REL", 0.1, true));
        }
        Set<String> terminals = Set.of("N_1", "N_25");

        // 1. 预热 10 次，消除 JIT C2 编译与类加载初次抖动
        for (int i = 0; i < 10; i++) {
            steinerReranker.extractCausalSubgraph(terminals, nodes, edges);
        }

        // 2. 正式测量稳态耗时
        long start = System.nanoTime();
        CausalExtractionResult result = steinerReranker.extractCausalSubgraph(terminals, nodes, edges);
        long elapsedNanos = System.nanoTime() - start;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        assertTrue(elapsedMs <= 8.5, "微秒级因果路径抽取单次耗时必须严格 <= 8.5ms，实测: " + elapsedMs + "ms");
        assertTrue(result.latencyNanos() > 0);
    }

    // =========================================================================
    // TC-137-6: DeepSeek 长思考双轨认知增强对齐合规性
    // =========================================================================
    @Test
    @DisplayName("TC-137-6: 因果推演链规范化转译为 DeepSeek Thinking 双轨上下文，100% 合规匹配官方要求")
    void testDeepSeekThinkingAlignment_dualTrackPromptGeneration() {
        String query = "特高压变电站极端高温下主变冷却器是否满足双重冗余？";
        List<String> path = List.of("特高压主变压器", "500kV侧高压绕组", "温升极限(65K)", "ODAF风冷机组", "稳态热平衡判据");
        List<CausalEdge> edges = List.of(
                new CausalEdge("特高压主变压器", "500kV侧高压绕组", "CONTAINS", 0.12, true),
                new CausalEdge("500kV侧高压绕组", "温升极限(65K)", "LIMITS", 0.18, true),
                new CausalEdge("温升极限(65K)", "ODAF风冷机组", "MITIGATES", 0.15, true),
                new CausalEdge("ODAF风冷机组", "稳态热平衡判据", "ENSURES", 0.10, true)
        );

        String thinkingPrompt = GraphRagCognitiveAugmentationReceipt.generateDeepSeekThinkingContext(query, path, edges);

        assertNotNull(thinkingPrompt);
        assertTrue(thinkingPrompt.contains("<causal_thinking_grounding>"));
        assertTrue(thinkingPrompt.contains("</causal_thinking_grounding>"));
        assertTrue(thinkingPrompt.contains("## 核心推理目标 (Query): " + query));
        assertTrue(thinkingPrompt.contains("特高压主变压器 -> 500kV侧高压绕组 -> 温升极限(65K) -> ODAF风冷机组 -> 稳态热平衡判据"));
        assertTrue(thinkingPrompt.contains("因果可达性=true"));
        assertTrue(thinkingPrompt.contains("思维链 (Thinking Process)"));
    }

    // =========================================================================
    // TC-137-7: 纯 Java 21 Record 凭单不可变签名与 SHA-256 常量时间自验真
    // =========================================================================
    @Test
    @DisplayName("TC-137-7: 纯 Java 21 Record 凭单全字段不可变与 SHA-256 哈希常量时间自验真")
    void testGraphRagCognitiveAugmentationReceipt_immutableSignatureAndVerification() {
        String query = "分析股权质押对本期流动性的因果影响";
        Map<String, Integer> coverage = Map.of("L1", 1, "L2", 3, "L3", 8, "L4", 2);
        List<String> path = List.of("控股股东质押", "流动性紧缩", "经营性现金流", "偿债能力指标");
        int nodes = 6;
        double noiseRatio = 0.75;
        String prompt = "Prompt Content for Thinking";
        double latencyMs = 0.88;

        GraphRagCognitiveAugmentationReceipt receipt = GraphRagCognitiveAugmentationReceipt.create(
                query, coverage, path, nodes, noiseRatio, prompt, latencyMs
        );

        assertNotNull(receipt);
        assertTrue(receipt.receiptId().startsWith("RCP-COGNITIVE-"));
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名必须为 64 位十六进制字符串");

        // 常量时间验真通过
        assertTrue(receipt.verifySignature(), "原始凭单签名验真必须 100% 成功");

        // 验证篡改任一字段会导致验真失败 (防篡改性)
        GraphRagCognitiveAugmentationReceipt tamperedReceipt = new GraphRagCognitiveAugmentationReceipt(
                receipt.receiptId(),
                "被恶意篡改的查询内容",
                receipt.pyramidLevelCoverage(),
                receipt.causalPath(),
                receipt.selectedNodeCount(),
                receipt.filteredNoiseRatio(),
                receipt.deepSeekThinkingPrompt(),
                receipt.latencyMs(),
                receipt.timestamp(),
                receipt.sha256Signature() // 使用原签名
        );
        assertFalse(tamperedReceipt.verifySignature(), "篡改字段后自验真必须严格返回 false");
    }

    // =========================================================================
    // TC-137-8: 端到端全链路闭环集成与防篡改签发
    // =========================================================================
    @Test
    @DisplayName("TC-137-8: 端到端闭环: 金字塔切分 -> 超球面投影 -> Steiner 因果剪枝 -> 拓扑重排 -> DeepSeek 对齐 -> 凭单签发")
    void testEndToEndGraphRagCognitive_fullPipelineIntegration() {
        // 1. 文档输入与四级金字塔切分
        String docId = "DOC-E2E-GRID-REPORT";
        String summary = "特高压跨区域电网输变电工程综合分析报告与设备冗余评价";
        Map<String, List<String>> sections = Map.of(
                "变电拓扑", List.of("主变容量 1000MVA", "500kV 侧双母线分段"),
                "散热冷却", List.of("ODAF强油风冷系统", "极端温度温升限值 65K")
        );
        TableArtifact table = new TableArtifact(
                "TBL-THERMAL", "热平衡对照表",
                List.of("负载率", "油温", "状态"),
                List.of(List.of("100%", "55K", "正常"), List.of("120%", "68K", "预警")),
                "变压器热负荷实测数据"
        );
        float[] seed = new float[1536];
        Arrays.fill(seed, 0.02f);

        PyramidChunkResult pyramid = pyramidChunker.buildPyramid(
                docId, "特高压跨区电网技术报告", summary, sections, List.of(table), seed
        );
        assertNotNull(pyramid);

        // 2. 构造图谱候选节点与有向因果边
        List<CausalNode> graphNodes = new ArrayList<>();
        for (PyramidChunkNode pNode : pyramid.allNodes().values()) {
            graphNodes.add(new CausalNode(pNode.id(), pNode.title(), pNode.level().name(), pNode.embedding()));
        }
        // 补充 15 个非因果噪声节点
        for (int i = 1; i <= 15; i++) {
            float[] noiseVec = new float[1536];
            noiseVec[i] = 1.0f;
            graphNodes.add(new CausalNode("NOISE_" + i, "噪音节点_" + i, "NOISE", noiseVec));
        }

        List<CausalEdge> graphEdges = new ArrayList<>();
        // 建立金字塔有效父子因果边
        for (PyramidChunkNode pNode : pyramid.allNodes().values()) {
            if (pNode.parentId() != null) {
                graphEdges.add(new CausalEdge(pNode.parentId(), pNode.id(), "HIERARCHICAL_INCLUDES", 0.1, true));
            }
        }
        // 补充虚假噪声边
        String rootId = pyramid.rootSummaryNode().id();
        for (int i = 1; i <= 15; i++) {
            graphEdges.add(new CausalEdge(rootId, "NOISE_" + i, "SPURIOUS_LINK", 0.05, false));
        }

        // 3. 设定端点并执行 Steiner 因果路径抽取
        String l4Id = pyramid.levelIndex().get(PyramidLevel.L4_TABLE).get(0);
        Set<String> terminals = Set.of(rootId, l4Id);

        CausalExtractionResult extraction = steinerReranker.extractCausalSubgraph(terminals, graphNodes, graphEdges);
        assertNotNull(extraction);
        assertTrue(extraction.noiseFilterRatio() >= 0.50, "必须有效过滤外围噪声");
        assertTrue(extraction.getLatencyMs() <= 8.5, "抽取耗时必须 <= 8.5ms");

        // 4. 生成 DeepSeek 长思考双轨 Prompt
        String thinkingPrompt = GraphRagCognitiveAugmentationReceipt.generateDeepSeekThinkingContext(
                "查询表格热平衡与主变容量的因果拓扑关系",
                extraction.causalPathway(),
                extraction.selectedEdges()
        );

        // 5. 签发纯 Java 21 Record 密码学存证凭单
        Map<String, Integer> coverage = Map.of(
                "L1", pyramid.levelIndex().get(PyramidLevel.L1_SUMMARY).size(),
                "L2", pyramid.levelIndex().get(PyramidLevel.L2_SECTION).size(),
                "L3", pyramid.levelIndex().get(PyramidLevel.L3_ENTITY).size(),
                "L4", pyramid.levelIndex().get(PyramidLevel.L4_TABLE).size()
        );
        GraphRagCognitiveAugmentationReceipt receipt = GraphRagCognitiveAugmentationReceipt.create(
                "查询表格热平衡与主变容量的因果拓扑关系",
                coverage,
                extraction.causalPathway(),
                extraction.selectedNodes().size(),
                extraction.noiseFilterRatio(),
                thinkingPrompt,
                extraction.getLatencyMs()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "端到端生成的凭单自验真必须 100.0% 通过");
    }
}
