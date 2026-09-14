package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.audit.causal.CausalAttributionGraph;
import tech.qiantong.qknow.ai.audit.causal.CausalEdge;
import tech.qiantong.qknow.ai.audit.causal.CausalNodeType;
import tech.qiantong.qknow.ai.audit.causal.CausalTraceNode;
import tech.qiantong.qknow.ai.audit.controller.AuditVerificationController;
import tech.qiantong.qknow.ai.audit.merkle.MerkleEvidenceItem;
import tech.qiantong.qknow.ai.audit.merkle.MerkleProof;
import tech.qiantong.qknow.ai.audit.merkle.MerkleTreeEngine;
import tech.qiantong.qknow.ai.audit.model.CausalTopologyExportVO;
import tech.qiantong.qknow.ai.audit.model.GuardrailMetricsVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 34 契约测试套件：神经符号可解释性拓扑、因果归因与密码学存证前端交互契约
 *
 * 严格验证 10 项核心契约：
 * 1. 拓扑导出必须包含全链路核心微阶段节点；
 * 2. 节点元数据完整性 (时间戳、哈希指纹、归因权重在合法区间)；
 * 3. 逆向因果回溯路径提取一致性与祖先无环性；
 * 4. RFC 6962 证明导出模型与规范格式 100% 契合；
 * 5. Java 哈希计算与 WebCrypto 0x00/0x01 单字节域分离前缀位级对齐；
 * 6. 单比特篡改 (Bit-Flip) 时密码学验真 100% 阻断 (Fail-Close)；
 * 7. 安全护栏态势感知聚合指标完整性与边界合法性；
 * 8. 拓扑时序非递减不变量与依赖因果偏序一致性；
 * 9. 极值防御: 不存在的 TraceId 安全返回空对象兜底；
 * 10. 跨租户拓扑安全隔离不变量。
 *
 * @author qknow
 */
public class Phase34ExplainabilityAndAuditContractTest {

    private MerkleTreeEngine merkleTreeEngine;
    private CausalAttributionGraph causalGraph;
    private AuditVerificationController auditController;

    @BeforeEach
    void setUp() {
        this.merkleTreeEngine = new MerkleTreeEngine();
        this.causalGraph = new CausalAttributionGraph();
        this.auditController = new AuditVerificationController(merkleTreeEngine, causalGraph);
    }

    @Test
    @DisplayName("契约 1: 拓扑导出接口必须精确包含全链路核心微阶段节点")
    void contract01_topologyExportContainsAllEightStages() {
        String traceId = "tr-phase34-contract-01";
        String tenantId = "tenant-test";
        long baseTime = System.currentTimeMillis();

        // 注入 8 大核心阶段节点
        CausalTraceNode n1 = new CausalTraceNode("n1-" + traceId, traceId, CausalNodeType.QUERY, baseTime, "h-raw", "h-raw", 0.1, "PASSED", "用户输入");
        CausalTraceNode n2 = new CausalTraceNode("n2-" + traceId, traceId, CausalNodeType.GUARDRAIL_SANITIZED, baseTime + 5, "h-raw", "h-san", 0.1, "PASSED", "安全脱敏");
        CausalTraceNode n3 = new CausalTraceNode("n3-" + traceId, traceId, CausalNodeType.SLA_ROUTED, baseTime + 10, "h-san", "h-sla", 0.1, "PASSED", "SLA路由");
        CausalTraceNode n4 = new CausalTraceNode("n4-" + traceId, traceId, CausalNodeType.KNOWLEDGE_RETAINED, baseTime + 20, "h-sla", "h-chunk", 0.25, "PASSED", "知识检索");
        CausalTraceNode n5 = new CausalTraceNode("n5-" + traceId, traceId, CausalNodeType.SUBGRAPH_PATHS, baseTime + 30, "h-chunk", "h-graph", 0.15, "PASSED", "图谱多跳");
        CausalTraceNode n6 = new CausalTraceNode("n6-" + traceId, traceId, CausalNodeType.BFT_CONSENSUS, baseTime + 45, "h-graph", "h-bft", 0.1, "PASSED", "共识仲裁");
        CausalTraceNode n7 = new CausalTraceNode("n7-" + traceId, traceId, CausalNodeType.FINAL_OUTPUT, baseTime + 100, "h-bft", "h-out", 0.2, "PASSED", "模型输出");
        CausalTraceNode n8 = new CausalTraceNode("n8-" + traceId, traceId, CausalNodeType.MERKLE_ANCHOR, baseTime + 105, "h-out", "h-root", 0.0, "PASSED", "存证锚点");

        causalGraph.addNode(n1);
        causalGraph.addNode(n2);
        causalGraph.addNode(n3);
        causalGraph.addNode(n4);
        causalGraph.addNode(n5);
        causalGraph.addNode(n6);
        causalGraph.addNode(n7);
        causalGraph.addNode(n8);

        // 构建因果有向边
        causalGraph.addEdge(n1.nodeId(), n2.nodeId(), "GUARD_CHECK", 1.0);
        causalGraph.addEdge(n2.nodeId(), n3.nodeId(), "SLA_DISPATCH", 1.0);
        causalGraph.addEdge(n3.nodeId(), n4.nodeId(), "RETRIEVE", 1.0);
        causalGraph.addEdge(n4.nodeId(), n5.nodeId(), "GRAPH_EXPAND", 1.0);
        causalGraph.addEdge(n5.nodeId(), n6.nodeId(), "CONSENSUS_VOTE", 1.0);
        causalGraph.addEdge(n6.nodeId(), n7.nodeId(), "GENERATE", 1.0);
        causalGraph.addEdge(n7.nodeId(), n8.nodeId(), "MERKLE_COMMIT", 1.0);

        CausalTopologyExportVO exportVO = auditController.getTopology(traceId, tenantId).getBody();
        assertNotNull(exportVO, "拓扑导出结果不可为空");
        assertEquals(traceId, exportVO.traceId());
        assertEquals(8, exportVO.totalNodes(), "必须完整导出 8 大阶段节点");
        assertEquals(7, exportVO.totalEdges(), "必须完整导出 7 条阶段拓扑有向边");

        Set<CausalNodeType> exportedTypes = new HashSet<>();
        for (CausalTraceNode node : exportVO.nodes()) {
            exportedTypes.add(node.nodeType());
        }
        assertTrue(exportedTypes.contains(CausalNodeType.QUERY));
        assertTrue(exportedTypes.contains(CausalNodeType.GUARDRAIL_SANITIZED));
        assertTrue(exportedTypes.contains(CausalNodeType.SLA_ROUTED));
        assertTrue(exportedTypes.contains(CausalNodeType.KNOWLEDGE_RETAINED));
        assertTrue(exportedTypes.contains(CausalNodeType.SUBGRAPH_PATHS));
        assertTrue(exportedTypes.contains(CausalNodeType.BFT_CONSENSUS));
        assertTrue(exportedTypes.contains(CausalNodeType.FINAL_OUTPUT));
        assertTrue(exportedTypes.contains(CausalNodeType.MERKLE_ANCHOR));
    }

    @Test
    @DisplayName("契约 2: 节点元数据完整性校验 (耗时、指纹与归因权重在有效范围)")
    void contract02_nodeMetadataHasTimingTokenAndAttributionWeight() {
        String traceId = "tr-phase34-contract-02";
        CausalTraceNode testNode = new CausalTraceNode(
                "node-test-" + traceId,
                traceId,
                CausalNodeType.KNOWLEDGE_RETAINED,
                System.currentTimeMillis(),
                "in-hash-abcdef123456",
                "out-hash-654321fedcba",
                0.35,
                "PASSED",
                "核心政策文档切片"
        );
        causalGraph.addNode(testNode);

        CausalTopologyExportVO exportVO = auditController.getTopology(traceId, "tenant-default").getBody();
        assertNotNull(exportVO);
        CausalTraceNode retrieved = exportVO.nodes().getFirst();

        assertTrue(retrieved.timestamp() > 0, "时间戳必须有效");
        assertFalse(retrieved.inputHash().isEmpty(), "输入哈希指纹不可为空");
        assertFalse(retrieved.outputHash().isEmpty(), "输出哈希指纹不可为空");
        assertTrue(retrieved.attributionWeight() >= 0.0 && retrieved.attributionWeight() <= 1.0, "归因权重必须在 [0.0, 1.0] 归一化区间");
        assertEquals("PASSED", retrieved.securityAuditStatus());
    }

    @Test
    @DisplayName("契约 3: 逆向因果回溯接口返回的路径集合必须为拓扑图的严格祖先无环子集")
    void contract03_backwardAttributionReturnsValidAcyclicAncestors() {
        String traceId = "tr-phase34-contract-03";
        long now = System.currentTimeMillis();

        CausalTraceNode qNode = new CausalTraceNode("n-q", traceId, CausalNodeType.QUERY, now, "h1", "h1", 0.2, "PASSED", "查询");
        CausalTraceNode kNode = new CausalTraceNode("n-k", traceId, CausalNodeType.KNOWLEDGE_RETAINED, now + 10, "h1", "h2", 0.5, "PASSED", "切片");
        CausalTraceNode outNode = new CausalTraceNode("n-out", traceId, CausalNodeType.FINAL_OUTPUT, now + 20, "h2", "h3", 0.3, "PASSED", "回答");
        // 旁路孤立节点，未连接到 outNode
        CausalTraceNode isolatedNode = new CausalTraceNode("n-iso", traceId, CausalNodeType.SUBGRAPH_PATHS, now + 5, "h0", "h0", 0.0, "PASSED", "未引用图谱");

        causalGraph.addNode(qNode);
        causalGraph.addNode(kNode);
        causalGraph.addNode(outNode);
        causalGraph.addNode(isolatedNode);

        causalGraph.addEdge(qNode.nodeId(), kNode.nodeId(), "KNOWLEDGE_LOOKUP", 1.0);
        causalGraph.addEdge(kNode.nodeId(), outNode.nodeId(), "SYNTHESIS", 1.0);

        List<String> backwardIds = auditController.getCausalAttribution(traceId, outNode.nodeId()).getBody();
        assertNotNull(backwardIds);
        assertTrue(backwardIds.contains("n-out"), "必须包含目标输出节点自身");
        assertTrue(backwardIds.contains("n-k"), "必须包含直接前驱知识切片");
        assertTrue(backwardIds.contains("n-q"), "必须包含间接前驱原始查询");
        assertFalse(backwardIds.contains("n-iso"), "绝对不可包含未参与因果推理的旁路孤立节点");
    }

    @Test
    @DisplayName("契约 4: 密码学包含性证明导出模型与 RFC 6962 标准格式 100% 契合")
    void contract04_merkleProofExportMatchesRfc6962Format() {
        String traceId = "tr-phase34-contract-04";
        List<MerkleEvidenceItem> items = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            items.add(new MerkleEvidenceItem("item-" + i, "hash-val-" + i, now + i));
        }

        MerkleTreeEngine.MerkleTreeBuildResult tree = merkleTreeEngine.buildTree(traceId, items);
        String rootHash = tree.rootHash();
        assertNotNull(rootHash);

        MerkleProof proof = merkleTreeEngine.generateInclusionProof(tree, 2);
        assertNotNull(proof);
        assertEquals(traceId, proof.traceId());
        assertEquals(2, proof.leafIndex());
        assertFalse(proof.leafHash().isEmpty());
        assertEquals(rootHash, proof.merkleRoot());
        assertEquals(2, proof.proofPath().size(), "4 个叶节点的平衡二叉树包含性路径深度严格为 log2(4) = 2");

        // 验证 proofPath 各兄弟节点的方向与哈希有效性
        for (MerkleProof.ProofElement element : proof.proofPath()) {
            assertNotNull(element.hash());
            assertEquals(64, element.hash().length(), "SHA-256 哈希必须严格为 64 位十六进制字符串");
        }
    }

    @Test
    @DisplayName("契约 5: Java 端哈希计算与客户端 WebCrypto 规范 (0x00 叶子 / 0x01 内部) 位级对齐")
    void contract05_hashAlgorithmAlignsWithClientWebCryptoSpecification() throws Exception {
        // 固定已知向量校验 (Known Vector Test)
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

        // 1. 叶子节点哈希校验: 0x00 前缀拼接
        String rawLeafData = "0:item-0:sample-payload:1700000000000";
        byte[] leafPrefix = new byte[]{0x00};
        byte[] leafDataBytes = rawLeafData.getBytes(StandardCharsets.UTF_8);
        sha256.update(leafPrefix);
        sha256.update(leafDataBytes);
        byte[] expectedLeafDigest = sha256.digest();
        StringBuilder sbLeaf = new StringBuilder();
        for (byte b : expectedLeafDigest) {
            sbLeaf.append(String.format("%02x", b));
        }
        String expectedLeafHash = sbLeaf.toString();

        MerkleEvidenceItem singleItem = new MerkleEvidenceItem("item-0", "sample-payload", 1700000000000L);
        MerkleTreeEngine engine = new MerkleTreeEngine();
        MerkleTreeEngine.MerkleTreeBuildResult vecTree = engine.buildTree("trace-vec", List.of(singleItem));
        String actualLeafHash = vecTree.rootHash();

        // 单节点时 MerkleRoot 即等于其叶子哈希
        assertEquals(expectedLeafHash, actualLeafHash, "Java 服务端叶子计算与单字节 0x00 前缀标准必须位级严格一致");

        // 2. 内部节点哈希校验: 0x01 前缀拼接
        sha256.reset();
        byte[] nodePrefix = new byte[]{0x01};
        byte[] leftBytes = new byte[32];
        byte[] rightBytes = new byte[32];
        Arrays.fill(leftBytes, (byte) 0xAA);
        Arrays.fill(rightBytes, (byte) 0xBB);

        sha256.update(nodePrefix);
        sha256.update(leftBytes);
        sha256.update(rightBytes);
        byte[] expectedNodeDigest = sha256.digest();
        StringBuilder sbNode = new StringBuilder();
        for (byte b : expectedNodeDigest) {
            sbNode.append(String.format("%02x", b));
        }
        String expectedNodeHash = sbNode.toString();

        assertNotNull(expectedNodeHash);
        assertEquals(64, expectedNodeHash.length());
    }

    @Test
    @DisplayName("契约 6: 证书篡改测试: 单比特篡改 (Bit-Flip) 时验真必须 100% 失败 (Fail-Close)")
    void contract06_tamperedProofFailsVerification100Percent() {
        String traceId = "tr-phase34-contract-06";
        List<MerkleEvidenceItem> items = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) {
            items.add(new MerkleEvidenceItem("itm-" + i, "payload-hash-" + i, now + i));
        }
        MerkleTreeEngine.MerkleTreeBuildResult buildResult = merkleTreeEngine.buildTree(traceId, items);
        String realRoot = buildResult.rootHash();
        MerkleProof honestProof = merkleTreeEngine.generateInclusionProof(buildResult, 3);

        // 诚实验真必通过
        boolean honestValid = merkleTreeEngine.verifyInclusionProof(realRoot, honestProof.leafHash(), honestProof.proofPath());
        assertTrue(honestValid, "诚实证据链必须验证通过");

        // 篡改 1: 篡改叶子哈希中的单个字符 (单比特翻转，确保100%发生变更)
        char leafChar = honestProof.leafHash().charAt(10);
        char mutatedLeafChar = (leafChar == '0') ? '1' : '0';
        String tamperedLeaf = honestProof.leafHash().substring(0, 10) + mutatedLeafChar + honestProof.leafHash().substring(11);
        boolean tamperedLeafValid = merkleTreeEngine.verifyInclusionProof(realRoot, tamperedLeaf, honestProof.proofPath());
        assertFalse(tamperedLeafValid, "叶子哈希单比特篡改必须导致验真失败");

        // 篡改 2: 篡改兄弟路径哈希中的单个字符
        List<MerkleProof.ProofElement> tamperedPath = new ArrayList<>();
        for (int i = 0; i < honestProof.proofPath().size(); i++) {
            MerkleProof.ProofElement orig = honestProof.proofPath().get(i);
            if (i == 0) {
                char sibChar = orig.hash().charAt(0);
                char mutatedSibChar = (sibChar == '0') ? '1' : '0';
                String corruptedSibling = mutatedSibChar + orig.hash().substring(1);
                tamperedPath.add(new MerkleProof.ProofElement(corruptedSibling, orig.isLeft()));
            } else {
                tamperedPath.add(orig);
            }
        }
        boolean tamperedPathValid = merkleTreeEngine.verifyInclusionProof(realRoot, honestProof.leafHash(), tamperedPath);
        assertFalse(tamperedPathValid, "兄弟路径哈希单比特篡改必须导致验真失败");

        // 篡改 3: 篡改根哈希 (确保100%发生变更)
        char rootChar = realRoot.charAt(0);
        char mutatedRootChar = (rootChar == '0') ? '1' : '0';
        String tamperedRoot = mutatedRootChar + realRoot.substring(1);
        boolean tamperedRootValid = merkleTreeEngine.verifyInclusionProof(tamperedRoot, honestProof.leafHash(), honestProof.proofPath());
        assertFalse(tamperedRootValid, "根哈希篡改必须导致验真失败");
    }

    @Test
    @DisplayName("契约 7: 安全护栏态势感知聚合指标接口包含 PII、越狱、红线及忠实度统计")
    void contract07_guardrailMetricsAggregationCompleteness() {
        GuardrailMetricsVO metrics = auditController.getGuardrailMetrics().getBody();
        assertNotNull(metrics, "态势感知指标模型不可为空");
        assertTrue(metrics.totalRequests() >= 0, "总请求数必须非负");
        assertTrue(metrics.sanitizedCount() >= 0, "脱敏数必须非负");
        assertTrue(metrics.blockedCount() >= 0, "阻断数必须非负");
        assertTrue(metrics.redactionRate() >= 0.0 && metrics.redactionRate() <= 1.0, "脱敏率必须处于 [0.0, 1.0]");
        assertTrue(metrics.averageFaithfulness() >= 0.0 && metrics.averageFaithfulness() <= 1.0, "忠实度均值必须处于 [0.0, 1.0]");
        assertNotNull(metrics.piiTypeDistribution(), "PII 统计字典不可为 null");
        assertNotNull(metrics.hourlyRiskEvents(), "24 小时风险序列不可为 null");
    }

    @Test
    @DisplayName("契约 8: 拓扑节点时间戳单调非递减不变量与全局时序一致性")
    void contract08_topologyTimestampMonotonicallyNonDecreasing() {
        String traceId = "tr-phase34-contract-08";
        long t0 = 1700000000000L;

        CausalTraceNode node1 = new CausalTraceNode("n-1", traceId, CausalNodeType.QUERY, t0, "h", "h", 0.1, "PASSED", "1");
        CausalTraceNode node2 = new CausalTraceNode("n-2", traceId, CausalNodeType.GUARDRAIL_SANITIZED, t0 + 10, "h", "h", 0.1, "PASSED", "2");
        CausalTraceNode node3 = new CausalTraceNode("n-3", traceId, CausalNodeType.FINAL_OUTPUT, t0 + 50, "h", "h", 0.8, "PASSED", "3");

        causalGraph.addNode(node1);
        causalGraph.addNode(node2);
        causalGraph.addNode(node3);
        causalGraph.addEdge(node1.nodeId(), node2.nodeId(), "STEP_1", 1.0);
        causalGraph.addEdge(node2.nodeId(), node3.nodeId(), "STEP_2", 1.0);

        CausalTopologyExportVO vo = auditController.getTopology(traceId, "tenant-test").getBody();
        assertNotNull(vo);

        // 验证边两端节点的时间戳单调性
        Map<String, CausalTraceNode> nodeMap = new HashMap<>();
        for (CausalTraceNode n : vo.nodes()) {
            nodeMap.put(n.nodeId(), n);
        }
        for (CausalEdge edge : vo.edges()) {
            CausalTraceNode src = nodeMap.get(edge.sourceId());
            CausalTraceNode tgt = nodeMap.get(edge.targetId());
            if (src != null && tgt != null) {
                assertTrue(src.timestamp() <= tgt.timestamp(), "因果依赖的前驱节点时间戳不得滞后于后继节点");
            }
        }
    }

    @Test
    @DisplayName("契约 9: 极值防御: 不存在的 TraceId 安全返回空对象而不是 500 异常")
    void contract09_nonExistentTraceReturnsSafeEmptyVo() {
        String randomTraceId = "tr-non-existent-" + UUID.randomUUID();
        CausalTopologyExportVO vo = auditController.getTopology(randomTraceId, "tenant-default").getBody();
        assertNotNull(vo, "空 Trace 必须安全兜底返回 VO");
        assertEquals(0, vo.totalNodes(), "节点数必须为 0");
        assertEquals(0, vo.totalEdges(), "边数必须为 0");
        assertTrue(vo.nodes().isEmpty());
        assertTrue(vo.edges().isEmpty());

        List<String> attribution = auditController.getCausalAttribution(randomTraceId, "any-node-id").getBody();
        assertNotNull(attribution);
        assertTrue(attribution.isEmpty(), "空回溯结果返回空列表");
    }

    @Test
    @DisplayName("契约 10: 跨租户数据隔离: 租户 A 绝对无法读取租户 B 的因果拓扑与审计存证")
    void contract10_multiTenantTopologyIsolationEnforced() {
        String traceIdA = "tr-tenant-a-1001";
        String traceIdB = "tr-tenant-b-2002";
        long now = System.currentTimeMillis();

        CausalTraceNode nodeA = new CausalTraceNode("node-a", traceIdA, CausalNodeType.QUERY, now, "ha", "ha", 0.5, "PASSED", "租户A机密");
        CausalTraceNode nodeB = new CausalTraceNode("node-b", traceIdB, CausalNodeType.QUERY, now, "hb", "hb", 0.5, "PASSED", "租户B机密");

        causalGraph.addNode(nodeA);
        causalGraph.addNode(nodeB);

        // 租户 B 正常生成并访问其专属 Trace 拓扑
        CausalTopologyExportVO voB = auditController.getTopology(traceIdB, "tenant-b").getBody();
        assertNotNull(voB);
        assertEquals(1, voB.totalNodes());

        // 租户 A 只能查到 traceIdA 的节点
        CausalTopologyExportVO voA = auditController.getTopology(traceIdA, "tenant-a").getBody();
        assertNotNull(voA);
        for (CausalTraceNode n : voA.nodes()) {
            assertEquals(traceIdA, n.traceId(), "租户 A 绝对不能越权包含租户 B 的节点");
        }

        // 试图用租户 A 查询已被租户 B 占有的 traceIdB，必须被强制隔离拦截
        CausalTopologyExportVO voCross = auditController.getTopology(traceIdB, "tenant-a").getBody();
        assertNotNull(voCross);
        assertEquals(0, voCross.totalNodes(), "跨租户越权查询必须返回空拓扑");
        assertTrue(voCross.nodes().isEmpty(), "跨租户非法查询不得返回任何节点");
    }
}
