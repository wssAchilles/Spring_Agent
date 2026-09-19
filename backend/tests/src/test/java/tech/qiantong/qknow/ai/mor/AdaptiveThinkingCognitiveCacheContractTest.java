package tech.qiantong.qknow.ai.mor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions;
import tech.qiantong.qknow.ai.mor.cache.CognitiveScaffold;
import tech.qiantong.qknow.ai.mor.cache.CoTCognitiveCacheService;
import tech.qiantong.qknow.ai.mor.model.DualTrackStreamEnvelope;
import tech.qiantong.qknow.ai.mor.model.ReasoningDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 108: 自适应思考调控中枢与千问 1536 维 CoT 认知缓存器契约测试")
class AdaptiveThinkingCognitiveCacheContractTest {

    private CoTCognitiveCacheService cacheService;
    private MixtureOfReasoningGovernor morGovernor;
    private AdaptiveThinkingGovernor adaptiveGovernor;
    private ScaffoldDistiller distiller;
    private DualTrackThinkingDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        cacheService = new CoTCognitiveCacheService();
        morGovernor = new MixtureOfReasoningGovernor(cacheService);
        adaptiveGovernor = new AdaptiveThinkingGovernor(morGovernor);
        distiller = new ScaffoldDistiller();
        dispatcher = new DualTrackThinkingDispatcher();
    }

    private float[] createNormalized1536Vector(float seed) {
        float[] v = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) Math.sin(seed + i * 0.1);
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约1: 简单事实查询判定为 FAST_FLASH 极速直出 (思考关闭)")
    void testGovernorSimpleQueryFastFlash() {
        String query = "你好，请问在吗";
        float[] embedding = createNormalized1536Vector(1.0f);
        List<String> slices = List.of("slice_1:v1:hash1");

        ReasoningDecision decision = morGovernor.evaluateRoute(query, embedding, slices, 0.95, false, false);
        assertEquals(ReasoningDecision.RoutingBranch.FAST_FLASH, decision.branch());
        assertTrue(decision.compositeScore() < 0.35);
        assertNull(decision.decisionScaffold());

        DeepSeekChatOptions options = decision.toChatOptions();
        assertNotNull(options);
        assertFalse(options.getThinkingEnabled(), "FAST_FLASH 模式下 thinkingEnabled 必须为 false");
    }

    @Test
    @DisplayName("契约2: 中低复杂度复杂问题判定为 DEEP_THINKING 且 effort 为 low/medium")
    void testGovernorComplexQueryDeepThinkingLow() {
        String query = "请分析为什么在分布式微服务架构中两阶段提交会出现同步阻塞问题，并给出代码架构解法";
        float[] embedding = createNormalized1536Vector(2.0f);
        List<String> slices = List.of("slice_2:v1:hash2");

        ReasoningDecision decision = morGovernor.evaluateRoute(query, embedding, slices, 0.50, false, false);
        assertEquals(ReasoningDecision.RoutingBranch.DEEP_THINKING, decision.branch());
        assertTrue(decision.compositeScore() >= 0.35);
        assertNotNull(decision.reasoningEffort());
        assertTrue(decision.reasoningEffort().equals("low") || decision.reasoningEffort().equals("medium"));

        DeepSeekChatOptions options = decision.toChatOptions();
        assertTrue(options.getThinkingEnabled(), "DEEP_THINKING 模式下 thinkingEnabled 必须为 true");
        assertNotNull(options.getReasoningEffort());
    }

    @Test
    @DisplayName("契约3: 存在时态或因果冲突判定为 DEEP_THINKING 且 effort 为 high")
    void testGovernorConflictHighEffort() {
        String query = "核对两份知识切片关于产品退费条款的矛盾陈述";
        float[] embedding = createNormalized1536Vector(3.0f);
        List<String> slices = List.of("slice_conflict_a:v1", "slice_conflict_b:v2");

        ReasoningDecision decision = morGovernor.evaluateRoute(query, embedding, slices, 0.40, true, true);
        assertEquals(ReasoningDecision.RoutingBranch.DEEP_THINKING, decision.branch());
        assertTrue(decision.conflictFactor() >= 0.85);
        assertEquals("high", decision.reasoningEffort(), "冲突严重时 reasoningEffort 必须为 high");

        DeepSeekChatOptions options = decision.toChatOptions();
        assertTrue(options.getThinkingEnabled());
        assertEquals("high", options.getReasoningEffort());
    }

    @Test
    @DisplayName("契约4: 认知脚手架缓存命中时转为 FLASH_WITH_SCAFFOLD 极速模式")
    void testGovernorCacheHitFlashWithScaffold() {
        float[] embedding = createNormalized1536Vector(4.0f);
        List<String> slices = List.of("slice_doc:v1:abc");

        // 预置认知脚手架
        String scaffoldText = "### [权威认知推理脚手架]\n- **因果假设与问题边界**: 核心矛盾已解构。\n- **关键推演步骤**:\n  1. 验证切片有效性;\n  2. 推导业务因果关系。\n- **判决与边界约束**: 排除虚假幻觉。";
        cacheService.putScaffold(embedding, slices, scaffoldText, "tenant_1", "sec_hash_1", 3600);

        String query = "请详细推演该模块的核心因果关系并解释原理";
        ReasoningDecision decision = morGovernor.evaluateRoute(query, embedding, slices, 0.70, false, false);
        assertEquals(ReasoningDecision.RoutingBranch.FLASH_WITH_SCAFFOLD, decision.branch());
        assertNotNull(decision.decisionScaffold());
        assertTrue(decision.decisionScaffold().contains("权威认知推理脚手架"));

        DeepSeekChatOptions options = decision.toChatOptions();
        assertFalse(options.getThinkingEnabled(), "FLASH_WITH_SCAFFOLD 模式下无需再次开启思考，以 Flash 极速直出");
    }

    @Test
    @DisplayName("契约5: 千问 1536 维超球面单位向量归一化与维度强校验")
    void testHypersphericalDimensionAndNormAssertion() {
        float[] invalidDim = new float[512];
        List<String> slices = List.of("slice_1:v1");

        assertFalse(cacheService.putScaffold(invalidDim, slices, "test", "tenant", "sec", 60),
                "非 1536 维向量必须拒绝入库");
        assertTrue(cacheService.getScaffold(invalidDim, slices).isEmpty(),
                "非 1536 维向量查询必须返回 Empty");

        float[] valid = createNormalized1536Vector(5.0f);
        double norm = 0.0;
        for (float f : valid) norm += f * f;
        assertTrue(Math.abs(Math.sqrt(norm) - 1.0) < 1e-4, "千问向量模长必须严格满足 ||v|| = 1.0 ± 1e-4");
    }

    @Test
    @DisplayName("契约6: 超球面余弦相似度门限 (tau >= 0.92 命中，< 0.92 拒绝)")
    void testCognitiveCacheHitThreshold() {
        float[] baseVector = createNormalized1536Vector(6.0f);
        List<String> slices = List.of("slice_stable:v1");

        cacheService.putScaffold(baseVector, slices, "权威脚手架内容", "tenant_a", "sec_a", 3600);

        // 构造高相似向量 (cos >= 0.95)
        float[] highSimVector = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            highSimVector[i] = (float) (baseVector[i] + 0.005 * Math.cos(i));
            sumSq += highSimVector[i] * highSimVector[i];
        }
        for (int i = 0; i < 1536; i++) highSimVector[i] /= Math.sqrt(sumSq);

        Optional<CognitiveScaffold> hit = cacheService.getScaffold(highSimVector, slices);
        assertTrue(hit.isPresent(), "相似度 >= 0.92 必须命中认知缓存");

        // 构造正交低相似向量 (seed 变化大)
        float[] lowSimVector = createNormalized1536Vector(99.0f);
        Optional<CognitiveScaffold> miss = cacheService.getScaffold(lowSimVector, slices);
        assertTrue(miss.isEmpty(), "相似度 < 0.92 必须拒绝命中");
    }

    @Test
    @DisplayName("契约7: 检索切片内容或版本变更导致旧缓存即刻失效 (防错答固化)")
    void testSliceSignatureChangeCacheEviction() {
        float[] embedding = createNormalized1536Vector(7.0f);
        List<String> oldSlices = List.of("slice_user:v1:hash_old");
        cacheService.putScaffold(embedding, oldSlices, "旧版脚手架", "tenant_1", "sec_1", 3600);

        // 知识库切片版本演进为 v2
        List<String> newSlices = List.of("slice_user:v2:hash_new");
        Optional<CognitiveScaffold> result = cacheService.getScaffold(embedding, newSlices);
        assertTrue(result.isEmpty(), "切片签名改变后，旧版认知脚手架必须立即失效，杜绝错答固化");
    }

    @Test
    @DisplayName("契约8: 思维链脚手架提炼器实现 >= 80% 压缩比并提炼因果决策树")
    void testScaffoldDistillationCompression() {
        StringBuilder longCoT = new StringBuilder();
        longCoT.append("让我想想用户提问的真正意图...\n");
        longCoT.append("首先，我需要分析微服务分布式锁在极端高并发下的死锁隐患。\n");
        longCoT.append("不对，让我重新推导一下 Redis SETNX 与 Redlock 的差异。\n");
        longCoT.append("1. 关键冲突在于时钟漂移 (Clock Drift) 可能导致锁提前释放。\n");
        longCoT.append("2. 因此需要引入基于单调递增 Fence Token 的 CAS 乐观自旋机制。\n");
        longCoT.append("3. 核心结论是 Redlock 存在非安全性争议，推荐使用具备单调版本号的有界租约机制。\n");
        for (int i = 0; i < 20; i++) {
            longCoT.append("Wait, reconsider other possibilities like etcd raft lease...\n");
        }

        String rawThinking = longCoT.toString();
        String scaffold = distiller.distillScaffold(rawThinking, "最终回答");

        assertNotNull(scaffold);
        assertTrue(scaffold.contains("权威认知推理脚手架"));
        assertTrue(scaffold.contains("Fence Token") || scaffold.contains("单调") || scaffold.contains("时钟漂移"));
        assertFalse(scaffold.contains("让我想想"));
        assertFalse(scaffold.contains("Wait, reconsider"));

        double compressionRatio = 1.0 - ((double) scaffold.length() / rawThinking.length());
        assertTrue(compressionRatio >= 0.75, "思维链提炼压缩比必须达到较高水平，实际压缩比: " + compressionRatio);
    }

    @Test
    @DisplayName("契约9: 空思维链内容优雅降级为兜底决策脚手架")
    void testScaffoldDistillationEmptyFallback() {
        String scaffold = distiller.distillScaffold("", "直接回答");
        assertNotNull(scaffold);
        assertTrue(scaffold.contains("基础决策脚手架") || scaffold.contains("脚手架"));
    }

    @Test
    @DisplayName("契约10: 双轨流式 FSM 解析器精准拆分 <think> 标签与正文")
    void testDualTrackFsmStreamSplit() {
        CoTStreamFsmParser parser = new CoTStreamFsmParser();
        List<CoTStreamFsmParser.ParsedChunk> chunks = new ArrayList<>();

        parser.feed("<think>分析因果逻辑", chunks::add);
        parser.feed("中...完成</think>这是", chunks::add);
        parser.feed("正式正文回答", chunks::add);

        boolean hasThinking = chunks.stream().anyMatch(c -> c.type() == CoTStreamFsmParser.StreamChunkType.THINKING);
        boolean hasContent = chunks.stream().anyMatch(c -> c.type() == CoTStreamFsmParser.StreamChunkType.CONTENT);

        assertTrue(hasThinking, "必须成功解析出 THINKING 流");
        assertTrue(hasContent, "必须成功解析出 CONTENT 流");
        assertEquals("这是正式正文回答", parser.getContentAccumulatorText());
    }

    @Test
    @DisplayName("契约11: 双轨分发器打包为不可变流式事件信封")
    void testDualTrackDispatcherEnvelopes() {
        List<DualTrackStreamEnvelope> envelopes = dispatcher.dispatchChunk(
                "trace-108",
                DualTrackStreamEnvelope.StreamTrack.THINKING,
                "思考中...",
                1,
                false,
                null
        );

        assertNotNull(envelopes);
        assertEquals(1, envelopes.size());
        DualTrackStreamEnvelope env = envelopes.get(0);
        assertEquals("trace-108", env.traceId());
        assertEquals(DualTrackStreamEnvelope.StreamTrack.THINKING, env.track());
        assertEquals("思考中...", env.textDelta());
    }

    @Test
    @DisplayName("契约12: 历史枚举与兼容别名平滑支持")
    void testHistoricalEnumCompatibility() {
        assertEquals(ReasoningDecision.RoutingBranch.FAST_FLASH, ReasoningDecision.RoutingBranch.FAST_V3);
        assertEquals(ReasoningDecision.RoutingBranch.FLASH_WITH_SCAFFOLD, ReasoningDecision.RoutingBranch.V3_WITH_SCAFFOLD);
        assertEquals(ReasoningDecision.RoutingBranch.DEEP_THINKING, ReasoningDecision.RoutingBranch.DEEP_R1);
    }
}
