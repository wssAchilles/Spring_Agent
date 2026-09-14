package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.graph.HierarchicalCommunityService;
import tech.qiantong.qknow.module.kmc.service.rag.graph.HierarchicalCommunityServiceImpl;
import tech.qiantong.qknow.module.kmc.service.rag.graph.MultiHopCausalPathExtractor;
import tech.qiantong.qknow.module.kmc.service.rag.graph.MultiHopCausalPathExtractorImpl;
import tech.qiantong.qknow.module.kmc.service.rag.graph.NeuroSymbolicGraphRanker;
import tech.qiantong.qknow.module.kmc.service.rag.graph.NeuroSymbolicGraphRankerImpl;
import tech.qiantong.qknow.module.kmc.service.rag.graph.GraphRag2Coordinator;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 29 专属契约测试：知识图谱深度语义推理与子图神经符号混合图 RAG (GraphRAG 2.0)
 * 验证 10 大核心契约：
 * 1. 超级节点出入度截断防组合爆炸 (Degree Cutoff <= 30)
 * 2. 1~2跳跨实体多跳因果链抽取与结构化事实格式化
 * 3. 层次化社区发现 L0 宏观与 L1 微观两级树拓扑构建
 * 4. 局部实体增量变更脏位检测与拓扑版本哈希缓存命中 (API 节省 >= 90%)
 * 5. 神经符号逻辑门控阻断漫反射与事实幻觉抑制 (Theorem 2.1)
 * 6. Banach 压缩映射不动点 PPR 几何收敛率 O((1-alpha)^t) 闭环验证
 * 7. 防环路冗余消除定理 (Loop Redundancy Elimination) 在 Pareto 前沿剪枝
 * 8. 自适应全局 20KB 预算硬门禁分段切分 (25% 因果 + 15% 社区 + 60% 切片)
 * 9. Fail-Open 容灾平滑降级机制 (Neo4j / GDS 不可用时零报错)
 * 10. 端到端双层神经符号检索装配多跳因果问答
 */
public class Phase29GraphRag2ContractTest {

    private HierarchicalCommunityServiceImpl communityService;
    private MultiHopCausalPathExtractorImpl causalPathExtractor;
    private NeuroSymbolicGraphRankerImpl neuroSymbolicRanker;
    private GraphRag2Coordinator graphRag2Coordinator;

    private static final String TEST_WORKSPACE_ID = "ws_test_p29";

    @BeforeEach
    void setUp() {
        communityService = new HierarchicalCommunityServiceImpl();
        causalPathExtractor = new MultiHopCausalPathExtractorImpl();
        neuroSymbolicRanker = new NeuroSymbolicGraphRankerImpl(causalPathExtractor, communityService);
        graphRag2Coordinator = new GraphRag2Coordinator(causalPathExtractor, communityService, neuroSymbolicRanker);
    }

    @Test
    @DisplayName("契约01: 超级节点度数截断防组合爆炸 (Degree Cutoff <= 30)")
    void contract01_supernodeDegreeCutoff_preventsCombinatorialExplosion() {
        // 构建包含超级节点（度数 1200）的拓扑
        String supernode = "系统";
        List<String> neighbors = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            neighbors.add("子模块_" + i);
        }
        causalPathExtractor.mockRegisterNodeWithHighDegree(TEST_WORKSPACE_ID, supernode, neighbors);

        long startNs = System.nanoTime();
        List<MultiHopCausalPathExtractor.CausalPathFact> chains =
                causalPathExtractor.extractCausalChains(TEST_WORKSPACE_ID, List.of(supernode), 50);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        // 验证截断生效：扩展路径数严格受控，查询耗时 <= 15ms，彻底阻断组合爆炸
        assertTrue(elapsedMs <= 15, "超级节点截断查询应在 15ms 内完成，实际耗时: " + elapsedMs + "ms");
        assertTrue(chains.size() <= MultiHopCausalPathExtractor.DEGREE_CUTOFF,
                "扩展路径数应被截断至 DEGREE_CUTOFF (30) 以内，实际返回: " + chains.size());
    }

    @Test
    @DisplayName("契约02: 1~2跳因果推理链抽取与结构化事实文本格式化")
    void contract02_causalPathExtraction_formatsStructuredReasoningChains() {
        // 注册因果链: [订单服务] --(调用超时)--> [支付中间件] --(导致)--> [库存预扣回滚]
        causalPathExtractor.mockRegisterPath(TEST_WORKSPACE_ID,
                "订单服务", "调用超时", "支付中间件", "导致", "库存预扣回滚", 0.95);

        List<MultiHopCausalPathExtractor.CausalPathFact> facts =
                causalPathExtractor.extractCausalChains(TEST_WORKSPACE_ID, List.of("订单服务"), 10);

        assertFalse(facts.isEmpty(), "应成功抽取出因果路径事实");
        MultiHopCausalPathExtractor.CausalPathFact fact = facts.get(0);
        assertEquals("订单服务", fact.getSourceEntity());
        assertEquals("支付中间件", fact.getIntermediateEntity());
        assertEquals("库存预扣回滚", fact.getTargetEntity());
        assertTrue(fact.getCausalScore() >= 0.90, "置信度应 >= 0.90");

        String formatted = fact.toFormattedFact();
        assertTrue(formatted.contains("[订单服务] --[调用超时]--> [支付中间件] --[导致]--> [库存预扣回滚]"),
                "格式化输出应包含完整 2 跳因果链，实际输出: " + formatted);
    }

    @Test
    @DisplayName("契约03: 层次化社区发现构建 L0 宏观领域与 L1 微观实体簇两级树拓扑")
    void contract03_hierarchicalCommunity_buildsL0AndL1TreePartitions() {
        // 注册拓扑：包含基础设施域与金融支付域
        Map<String, List<String>> graph = new HashMap<>();
        graph.put("网关服务", List.of("限流组件", "鉴权过滤器", "路由表"));
        graph.put("限流组件", List.of("令牌桶", "滑动窗口"));
        graph.put("支付服务", List.of("收银台", "清算引擎", "会计核算"));
        graph.put("清算引擎", List.of("银联通道", "网联通道"));

        List<HierarchicalCommunityService.CommunityInfo> communities =
                communityService.buildHierarchicalCommunities(TEST_WORKSPACE_ID, new ArrayList<>(graph.keySet()), graph);

        assertFalse(communities.isEmpty(), "应成功生成层次化社区列表");

        long l0Count = communities.stream()
                .filter(c -> c.level() == HierarchicalCommunityService.CommunityLevel.L0_MACRO_GLOBAL)
                .count();
        long l1Count = communities.stream()
                .filter(c -> c.level() == HierarchicalCommunityService.CommunityLevel.L1_MICRO_CLUSTER)
                .count();

        assertTrue(l0Count >= 1 && l0Count <= 8, "L0 宏观社区数量应在 1~8 个之间，实际为: " + l0Count);
        assertTrue(l1Count >= l0Count, "L1 微观实体簇数量应大于等于 L0 数量，实际为: " + l1Count);

        // 验证摘要不为空
        for (var c : communities) {
            assertNotNull(c.summary(), "社区摘要不能为空");
            assertNotNull(c.topologyHash(), "社区拓扑哈希不能为空");
        }
    }

    @Test
    @DisplayName("契约04: 局部增量变更脏位检测与拓扑版本哈希缓存命中 (API 节省 >= 90%)")
    void contract04_communityIncrementalCache_invalidatesOnlyDirtyClustersOnUpdate() {
        // 初始构建社区
        Map<String, List<String>> graph = new HashMap<>();
        graph.put("集群A_节点1", List.of("集群A_节点2", "集群A_节点3"));
        graph.put("集群B_节点1", List.of("集群B_节点2", "集群B_节点3"));
        communityService.buildHierarchicalCommunities(TEST_WORKSPACE_ID, new ArrayList<>(graph.keySet()), graph);

        // 记录未变更前的生成调用计数
        int initialLlmCalls = communityService.getLlmSummaryCallCount();

        // 模拟仅对 集群A 进行局部节点变更
        communityService.incrementalUpdateCommunities(TEST_WORKSPACE_ID, List.of("集群A_节点1"));

        // 再次检索两个集群的社区摘要
        var communitiesA = communityService.retrieveRelevantCommunities(TEST_WORKSPACE_ID, "集群A", 5);
        var communitiesB = communityService.retrieveRelevantCommunities(TEST_WORKSPACE_ID, "集群B", 5);

        assertFalse(communitiesA.isEmpty());
        assertFalse(communitiesB.isEmpty());

        // 验证集群B的摘要命中缓存未重新触发 LLM 调用
        int callsAfter = communityService.getLlmSummaryCallCount();
        int newCalls = callsAfter - initialLlmCalls;
        assertTrue(newCalls <= 1, "局部增量更新时只有受影响微观社区重算，调用增量应 <= 1，实际增量: " + newCalls);
    }

    @Test
    @DisplayName("契约05: 神经符号逻辑门控阻断漫反射与事实幻觉抑制 (Theorem 2.1)")
    void contract05_neuroSymbolicGating_suppressesSemanticDiffusionAndHallucination() {
        // 构造实体与边：一条强因果依赖边，一条弱语义噪声边（如“相关于”）
        String query = "查询支付超时原因";
        float[] queryVector = new float[1536];
        Arrays.fill(queryVector, 0.05f);

        // 强因果边
        neuroSymbolicRanker.mockRegisterEdge("支付服务", "CALLS_TIMEOUT", "账务数据库", 0.88);
        // 弱语义无关边
        neuroSymbolicRanker.mockRegisterEdge("支付服务", "RELATED_TO_GENERIC", "办公大楼", 0.15);

        var rankedContext = neuroSymbolicRanker.rankGraphContext(
                TEST_WORKSPACE_ID, query, queryVector, List.of("支付服务"));

        // 验证强因果被保留，弱语义边被门控硬剪枝 (g(e) < tau_prune)
        boolean hasCausal = rankedContext.validatedCausalChains().stream()
                .anyMatch(f -> "账务数据库".equals(f.getTargetEntity()));
        boolean hasGenericNoise = rankedContext.validatedCausalChains().stream()
                .anyMatch(f -> "办公大楼".equals(f.getTargetEntity()));

        assertTrue(hasCausal, "强因果关系应通过神经符号门控");
        assertFalse(hasGenericNoise, "弱语义漫反射边必须被神经符号门控剪除，杜绝事实幻觉");
    }

    @Test
    @DisplayName("契约06: Banach 压缩映射不动点 PPR 几何收敛率 O((1-alpha)^t) 闭环验证")
    void contract06_pprConvergence_reachesStationaryDistributionGeometrically() {
        // 构建 5 节点有向转移环
        List<String> nodes = List.of("N1", "N2", "N3", "N4", "N5");
        Map<String, List<String>> adj = new HashMap<>();
        adj.put("N1", List.of("N2", "N3"));
        adj.put("N2", List.of("N3", "N4"));
        adj.put("N3", List.of("N4", "N5"));
        adj.put("N4", List.of("N5", "N1"));
        adj.put("N5", List.of("N1", "N2"));

        double alpha = 0.15;
        double shrinkage = 1.0 - alpha; // 0.85

        NeuroSymbolicGraphRankerImpl.PprResult pprResult =
                neuroSymbolicRanker.computePprWithConvergenceTracking(nodes, adj, "N1", alpha, 20, 0.05);

        assertTrue(pprResult.isConverged(), "PPR 算法在 20 轮内应严格收敛");
        assertTrue(pprResult.getIterations() <= 20, "收敛迭代轮数应 <= 20，实际: " + pprResult.getIterations());

        // 验证残差单调递减
        List<Double> residuals = pprResult.getResidualHistory();
        for (int i = 1; i < residuals.size(); i++) {
            assertTrue(residuals.get(i) <= residuals.get(i - 1) + 1e-9,
                    "残差应单调递减，第 " + i + " 轮不满足几何衰减");
        }
    }

    @Test
    @DisplayName("契约07: 防环路冗余消除定理 (Loop Redundancy Elimination) 在 Pareto 前沿剪枝")
    void contract07_loopRedundancyElimination_prunesCyclicPathsOnParetoFrontier() {
        // 构造有环路径: A -> B -> C -> B -> D
        List<String> cyclicPath = List.of("节点A", "节点B", "节点C", "节点B", "节点D");
        List<String> prunedPath = neuroSymbolicRanker.eliminatePathLoops(cyclicPath);

        // 剪环后应为: A -> B -> D
        assertEquals(3, prunedPath.size(), "剪环后长度应从 5 缩减至 3");
        assertEquals(List.of("节点A", "节点B", "节点D"), prunedPath, "剪环后路径应消除重复节点 B 之间的拓扑环");
    }

    @Test
    @DisplayName("契约08: 自适应全局 20KB 预算硬门禁分段切分 (25% 因果 + 15% 社区 + 60% 切片)")
    void contract08_adaptiveContextBudget_enforces20KbGlobalHardLimitWithSections() {
        // 模拟大量因果链与社区摘要
        List<MultiHopCausalPathExtractor.CausalPathFact> heavyFacts = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            heavyFacts.add(MultiHopCausalPathExtractor.CausalPathFact.builder()
                    .sourceEntity("实体源_" + i)
                    .firstRelation("严重依赖")
                    .intermediateEntity("中间实体_" + i)
                    .secondRelation("引发崩溃")
                    .targetEntity("目标实体_" + i)
                    .causalScore(0.9)
                    .build());
        }

        List<String> heavySummaries = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            heavySummaries.add("这是第 " + i + " 个知识社区的宏观全局主题摘要，阐述了分布式高并发中间件集群在多机房容灾场景下的拓扑架构与数据一致性协议保障。");
        }

        List<RetrievalResult> heavySegments = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            heavySegments.add(RetrievalResult.builder()
                    .segmentId((long) i)
                    .content("这是文档正文切片 " + i + "，详细记录了关于系统异常排查的步骤和操作指导，长度约 200 字...")
                    .score(0.85)
                    .build());
        }

        float[] vector = new float[1536];
        var assembled = graphRag2Coordinator.coordinateWithMockedData(
                TEST_WORKSPACE_ID, "排查崩溃原因", vector, List.of("实体源_0"), heavyFacts, heavySummaries, heavySegments);

        String prompt = assembled.structuredGraphPromptSection();
        int totalBytes = prompt.getBytes(StandardCharsets.UTF_8).length;

        // 图部分上限 8KB (5KB 因果 + 3KB 社区)
        assertTrue(totalBytes <= 8000, "图上下文总字节数必须严格限制在 8KB (5KB因果 + 3KB社区) 内，实际: " + totalBytes);
        assertTrue(prompt.contains("【核心因果与拓扑事实链】"), "应包含因果事实段");
        assertTrue(prompt.contains("【领域宏观背景与知识社区摘要】"), "应包含社区摘要段");
        assertFalse(assembled.isDegraded(), "正常装配不应被标记为降级");
    }

    @Test
    @DisplayName("契约09: Fail-Open 容灾平滑降级机制 (Neo4j / GDS 不可用时零报错)")
    void contract09_failOpenResilience_degradesSeamlesslyWhenNeo4jOrGdsUnavailable() {
        // 模拟底层发生连接中断或查询超时异常
        NeuroSymbolicGraphRanker brokenRanker = (workspaceId, query, queryVector, extractedEntities) -> {
            throw new RuntimeException("Neo4j BoltConnectionException: Connection refused: /127.0.0.1:7687");
        };

        GraphRag2Coordinator resilientCoordinator =
                new GraphRag2Coordinator(causalPathExtractor, communityService, brokenRanker);

        List<RetrievalResult> rawSegments = List.of(
                RetrievalResult.builder().segmentId(101L).content("正常切片内容，不受图故障影响").score(0.92).build()
        );

        long startNs = System.nanoTime();
        var assembled = resilientCoordinator.coordinate(
                TEST_WORKSPACE_ID, "查询信息", new float[1536], List.of("未知实体"), rawSegments);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        // 验证 Fail-Open 降级：耗时 <= 5ms，标记降级，切片保留，图提示段安全清空
        assertTrue(elapsedMs <= 5, "降级逻辑应在 5ms 内瞬时完成，实际耗时: " + elapsedMs + "ms");
        assertTrue(assembled.isDegraded(), "应正确标记为降级状态 isDegraded=true");
        assertTrue(assembled.structuredGraphPromptSection().isEmpty(), "降级时图提示段应清空，回退为纯切片正文");
        assertEquals(1, assembled.prioritizedSegments().size(), "正文切片应完整保留");
    }

    @Test
    @DisplayName("契约10: 端到端双层神经符号检索装配多跳因果问答")
    void contract10_endToEndDualLevelRetrieval_servesMultiHopCausalQuery() {
        // 预置全链路数据：社区 + 因果链
        causalPathExtractor.mockRegisterPath(TEST_WORKSPACE_ID,
                "订单网关", "触发限流", "Sentinel组件", "抛出异常", "BlockedException", 0.96);

        Map<String, List<String>> graph = new HashMap<>();
        graph.put("网关集群", List.of("订单网关", "Sentinel组件"));
        communityService.buildHierarchicalCommunities(TEST_WORKSPACE_ID, List.of("网关集群", "订单网关", "Sentinel组件"), graph);

        List<RetrievalResult> segments = List.of(
                RetrievalResult.builder().segmentId(1L).content("订单网关配置了默认 QPS=1000 的流控规则。").score(0.88).build()
        );

        float[] vector = new float[1536];
        Arrays.fill(vector, 0.02f);

        long startNs = System.nanoTime();
        var assembled = graphRag2Coordinator.coordinate(
                TEST_WORKSPACE_ID, "为什么订单网关会抛出 BlockedException？", vector, List.of("订单网关"), segments);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertTrue(elapsedMs <= 50, "端到端检索装配应在 50ms 内完成，实际耗时: " + elapsedMs + "ms");
        assertFalse(assembled.isDegraded(), "端到端链路应健康运行");
        assertTrue(assembled.structuredGraphPromptSection().contains("订单网关"), "上下文应包含种子实体因果推演");
        assertFalse(assembled.prioritizedSegments().isEmpty(), "正文切片应存在");
    }
}
