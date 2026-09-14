package tech.qiantong.qknow.ai.mor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.mor.cache.CognitiveScaffold;
import tech.qiantong.qknow.ai.mor.cache.CoTCognitiveCacheService;
import tech.qiantong.qknow.ai.mor.model.ReasoningDecision;
import tech.qiantong.qknow.module.kg.event.DocumentSlicesIngestedEvent;
import tech.qiantong.qknow.module.kg.event.DocumentSlicesIngestedListener;
import tech.qiantong.qknow.module.kg.rag.GraphRagCoordinator;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 48: 双核混合推理中枢 (MoR)、思考链 (CoT) 认知缓存与自进化图谱 3.0 核心契约测试
 */
public class Phase48MixtureOfReasoningContractTest {

    private CoTCognitiveCacheService cacheService;
    private ScaffoldDistiller scaffoldDistiller;
    private MixtureOfReasoningGovernor governor;

    @BeforeEach
    void setUp() {
        cacheService = new CoTCognitiveCacheService();
        scaffoldDistiller = new ScaffoldDistiller();
        governor = new MixtureOfReasoningGovernor(cacheService);
    }

    private float[] createHypersphericalVector(float seed) {
        float[] vec = new float[1536];
        double normSq = 0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) Math.sin(seed + i * 0.05);
            normSq += vec[i] * vec[i];
        }
        float norm = (float) Math.sqrt(normSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("Contract 1: 低复杂度且高置信度时三维选路策略决策为 FAST_V3 并生成规整前缀")
    void test1_FastV3RoutingUnderLowComplexityAndConfidence() {
        String query = "查询2026年9月公司考勤打卡制度规定";
        float[] qVec = createHypersphericalVector(1.0f);
        List<String> sliceSigs = List.of("slice_policy_001", "slice_policy_002");

        // 简单意图，高置信度 0.95，无时间冲突，无 CRAG 歧义
        ReasoningDecision decision = governor.evaluateRoute(query, qVec, sliceSigs, 0.95, false, false);

        assertNotNull(decision);
        assertEquals(ReasoningDecision.RoutingBranch.FAST_V3, decision.branch());
        assertTrue(decision.compositeScore() < 0.35, "综合得分应小于 0.35");
        assertNull(decision.decisionScaffold(), "FAST_V3 策略不应挂载脚手架");

        String formattedPrompt = governor.buildFastV3OptimizedPrompt("你是智能助理。", query);
        assertNotNull(formattedPrompt);
        assertTrue(formattedPrompt.contains(query));
        assertTrue(formattedPrompt.startsWith("[System: QKnow-V3-Fast]"), "应遵循 64-token 规整前缀优化对齐");
    }

    @Test
    @DisplayName("Contract 2: 中高复杂度且认知脚手架缓存命中时决策为 V3_WITH_SCAFFOLD")
    void test2_V3WithScaffoldRoutingWhenCacheHit() {
        String query = "深度分析为什么研发部门Q2服务器资源消耗增长30%且如何进行架构优化，给出因果溯源与演进方案";
        float[] qVec = createHypersphericalVector(2.5f);
        List<String> sliceSigs = List.of("slice_infra_metric_01", "slice_infra_metric_02");

        // 预热认知脚手架入缓存
        String distilledScaffold = "### [权威认知推理脚手架]\n- **关键步骤**: 1.定位并发增长; 2.识别认知缓存缺位; 3.落地动态选路";
        cacheService.putScaffold(qVec, sliceSigs, distilledScaffold, 3600);

        // 复杂分析，中等置信度 0.65，存在冲突
        ReasoningDecision decision = governor.evaluateRoute(query, qVec, sliceSigs, 0.65, false, false);

        assertNotNull(decision);
        assertEquals(ReasoningDecision.RoutingBranch.V3_WITH_SCAFFOLD, decision.branch());
        assertNotNull(decision.decisionScaffold(), "V3_WITH_SCAFFOLD 策略必须挂载脚手架");
        assertTrue(decision.decisionScaffold().contains("权威认知推理脚手架"));

        String scaffoldPrompt = governor.buildScaffoldAugmentedPrompt("系统基础设定", decision.decisionScaffold(), query);
        assertTrue(scaffoldPrompt.contains("[Cognitive Scaffold:"));
        assertTrue(scaffoldPrompt.contains(decision.decisionScaffold()));
    }

    @Test
    @DisplayName("Contract 3: 极高复杂度或严重事实冲突且无缓存时严格选路为 DEEP_R1")
    void test3_DeepR1RoutingUnderHighComplexityOrFactConflict() {
        String query = "论证量子密钥分发与后量子密码学在零信任多智能体拜占庭容错中的跨域兼容性与时态矛盾";
        float[] qVec = createHypersphericalVector(7.7f);
        List<String> sliceSigs = List.of("slice_crypto_pqc", "slice_crypto_qkd");

        // 存在时态冲突，CRAG 歧义高，且无认知缓存命中
        ReasoningDecision decision = governor.evaluateRoute(query, qVec, sliceSigs, 0.50, true, true);

        assertNotNull(decision);
        assertEquals(ReasoningDecision.RoutingBranch.DEEP_R1, decision.branch());
        assertNull(decision.decisionScaffold(), "未命中缓存且需深度思考，不挂载脚手架");
        assertTrue(decision.compositeScore() >= 0.35);
        assertTrue(decision.conflictFactor() >= 0.85);
    }

    @Test
    @DisplayName("Contract 4: CoTStreamFsmParser 字符级有限状态机零拷贝切分思考流与回答流")
    void test4_CoTStreamFsmParserNonBlockingZeroCopy() {
        CoTStreamFsmParser parser = new CoTStreamFsmParser();
        StringBuilder thinkBuf = new StringBuilder();
        StringBuilder answerBuf = new StringBuilder();

        // 模拟不规则的跨分块流式输入，包括标签跨块边界
        List<String> incomingChunks = List.of(
                "答复前置说明：",
                "<th", "ink>",
                "第一步：解析因果依赖链条。",
                "\n第二步：", "排除无效反事实分支。",
                "</thi", "nk>",
                "最终推演结论：经由定理1.2，因果充分性成立。"
        );

        for (String chunk : incomingChunks) {
            parser.feed(chunk, parsedChunk -> {
                if (parsedChunk.type() == CoTStreamFsmParser.StreamChunkType.THINKING) {
                    thinkBuf.append(parsedChunk.text());
                } else if (parsedChunk.type() == CoTStreamFsmParser.StreamChunkType.CONTENT) {
                    answerBuf.append(parsedChunk.text());
                }
            });
        }

        String fullThink = parser.getFullThinkingProcess();
        String fullAnswer = parser.getFullContent();

        assertTrue(fullThink.contains("第一步：解析因果依赖链条。"));
        assertTrue(fullThink.contains("第二步：排除无效反事实分支。"));
        assertFalse(fullThink.contains("<think>"));
        assertFalse(fullThink.contains("</think>"));

        assertTrue(fullAnswer.contains("答复前置说明："));
        assertTrue(fullAnswer.contains("最终推演结论：经由定理1.2，因果充分性成立。"));
    }

    @Test
    @DisplayName("Contract 5: ScaffoldDistiller 从长思考链中提炼因果脚手架并正确存入双重键缓存")
    void test5_ScaffoldDistillationAndDoubleKeyCacheStorage() {
        String r1ThinkingChain = "让我想想... 思考过程开始：首先检查网络监控指标，发现带宽未满。\n"
                + "1. 批处理事务过长持有排他锁时间过长引发队列堆积。\n"
                + "2. 确认由于缺乏语义脚手架缓存导致重复调用。\n"
                + "核心结论：建议拆分大事务为微批处理，限制单事务时长不超过500ms。";
        String finalAnswer = "系统吞吐量下降的原因是大事务锁阻塞，已建议优化方案。";

        String scaffold = scaffoldDistiller.distillScaffold(r1ThinkingChain, finalAnswer);

        assertNotNull(scaffold);
        assertTrue(scaffold.contains("权威认知推理脚手架"));
        assertTrue(scaffold.contains("关键推演步骤"));
        assertFalse(scaffold.contains("让我想想")); // 发散性语言被过滤

        float[] qVec = createHypersphericalVector(3.3f);
        List<String> sliceSigs = List.of("slice_metric_net_01");

        cacheService.putScaffold(qVec, sliceSigs, scaffold, 3600);

        Optional<CognitiveScaffold> cachedOpt = cacheService.getScaffold(qVec, sliceSigs);
        assertTrue(cachedOpt.isPresent(), "双重键精准匹配时必须能召回脚手架");
        assertEquals(scaffold, cachedOpt.get().scaffoldContent());
    }

    @Test
    @DisplayName("Contract 6: 切片变更导致签名改变引发认知缓存自然失效（定理1.3 零幻觉不变量）")
    void test6_SliceChangeCausesScaffoldCacheInvalidation() {
        float[] qVec = createHypersphericalVector(4.4f);
        List<String> oldSlices = List.of("slice_v1_001_hash_abc");

        String scaffoldContent = "### [权威认知推理脚手架]\n- **推演步骤**: 旧政策补贴为200元";
        cacheService.putScaffold(qVec, oldSlices, scaffoldContent, 3600);

        // 验证旧切片命中
        assertTrue(cacheService.getScaffold(qVec, oldSlices).isPresent());

        // 知识库切片发生更新 (版本演进导致签名突变)
        List<String> newSlices = List.of("slice_v1_001_hash_xyz_mutated");

        // 验证切片签名突变后，旧脚手架自然失效，防止虚假事实
        Optional<CognitiveScaffold> invalidOpt = cacheService.getScaffold(qVec, newSlices);
        assertFalse(invalidOpt.isPresent(), "切片签名突变时必须强力未命中旧脚手架，杜绝过期幻觉");
    }

    @Test
    @DisplayName("Contract 7: DocumentSlicesIngestedListener 异步事件驱动入图解耦")
    void test7_GraphRagCoordinatorAsyncEventDecoupling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean asyncExecuted = new AtomicBoolean(false);

        GraphRagCoordinator mockCoordinator = new GraphRagCoordinator() {
            @Override
            public List<GraphCausalEvidence> retrieveCausalPaths(Long workspaceId, List<String> seedEntities, int topK) {
                return Collections.emptyList();
            }

            @Override
            public List<CommunitySummaryEvidence> retrieveCommunitySummaries(Long workspaceId, String query, int topK) {
                return Collections.emptyList();
            }

            @Override
            public void onSlicesIngestedAsync(Long workspaceId, Long docId, List<String> sliceIds, List<String> texts) {
                asyncExecuted.set(true);
                latch.countDown();
            }
        };

        DocumentSlicesIngestedListener listener = new DocumentSlicesIngestedListener(mockCoordinator);

        DocumentSlicesIngestedEvent event = new DocumentSlicesIngestedEvent(
                "doc_contract_007",
                List.of("slice_007_a", "slice_007_b"),
                List.of("智能体网络定义", "A2A通信协议规范")
        );

        long startTime = System.currentTimeMillis();
        // 发布事件
        listener.onDocumentSlicesIngested(event);
        long publishCost = System.currentTimeMillis() - startTime;

        // 发布过程必须瞬时返回（严格小于 50ms），证明不霸占调用方线程/数据库长事务
        assertTrue(publishCost < 50, "事件分发必须非阻塞瞬时返回");

        // 等待异步任务完成
        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertTrue(completed, "异步入图任务应在后台线程成功完成");
        assertTrue(asyncExecuted.get(), "异步处理器必须被执行");
    }

    @Test
    @DisplayName("Contract 8: Banach不动点个性化PageRank(PPR)迭代计算收敛性与权重归一化（定理1.4）")
    void test8_BanachPprConvergenceContract() {
        // 构建小规模测试图拓扑：4个节点，部分有向连接
        Map<String, List<String>> adjacency = Map.of(
                "A", List.of("B", "C"),
                "B", List.of("C"),
                "C", List.of("A", "D"),
                "D", List.of("C")
        );

        Map<String, Double> teleport = Map.of(
                "A", 0.70,
                "B", 0.10,
                "C", 0.10,
                "D", 0.10
        );

        double dampingFactor = 0.85;
        double tolerance = 1e-5;
        int maxIterations = 50;

        Map<String, Double> pprScores = GraphRagCoordinator.computePpr(adjacency, teleport, dampingFactor, tolerance, maxIterations);

        assertNotNull(pprScores);
        assertEquals(4, pprScores.size());

        // 验证权重概率分布和归一化 (Sum = 1.0)
        double sum = pprScores.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-4, "PPR 权重向量总和应严格归一化为 1.0");

        // 验证传送起始点 A 在高阻尼与优先传送下的中心度特征
        assertTrue(pprScores.get("A") > 0.20, "起始跳转节点 A 应具备较高显著性");
        assertTrue(pprScores.get("C") > 0.20, "作为多节点汇聚中心的 C 应具备较高显著性");
    }
}
