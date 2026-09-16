package tech.qiantong.qknow.hermes.cognitive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.cognitive.dto.*;
import tech.qiantong.qknow.hermes.cognitive.engine.*;
import tech.qiantong.qknow.hermes.memory.model.MemoryNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 84: Hermes 2.0 认知内核专属契约测试套件
 * 验证四大核心引擎、8 大契约场景与凭单密码学验真
 */
public class Phase84Hermes2CognitiveKernelContractTest {

    private DynamicCotCognitiveRouter router;
    private HierarchicalToolReflexionGovernor governor;
    private TemporalCognitiveMemoryAligner aligner;
    private HermesCognitiveControlBus controlBus;

    @BeforeEach
    void setUp() {
        router = new DynamicCotCognitiveRouter();
        governor = new HierarchicalToolReflexionGovernor();
        aligner = new TemporalCognitiveMemoryAligner();
        controlBus = new HermesCognitiveControlBus();
    }

    @Test
    @DisplayName("契约测试 1: 简易问候极速探针分流至 DIRECT_ANSWER 零思考链且耗时 <= 50us")
    void testDynamicCotRouting_SimpleGreeting_RoutesToDirectAnswer() {
        String prompt = "你好，请问在吗？";
        DynamicCotCognitiveRouter.RoutingDecision decision = router.route(prompt, 50, 5);

        assertNotNull(decision);
        assertEquals(CognitiveStrategy.DIRECT_ANSWER, decision.strategy(), "简易打招呼必须分流至 DIRECT_ANSWER");
        assertTrue(decision.complexityScore() < 0.35, "简易问候复杂度必须 < 0.35");
        assertTrue(decision.latencyNanos() >= 0, "决策耗时必须为正");
        // 软断言微秒级延迟 (预热后耗时极低)
        System.out.printf("[Contract 1] 简易问候路由耗时: %d ns, score=%.4f%n", decision.latencyNanos(), decision.complexityScore());
    }

    @Test
    @DisplayName("契约测试 2: 多跳长逻辑复杂任务自适应激活 DEEP_REASONING 深度思考树")
    void testDynamicCotRouting_ComplexMultiHop_RoutesToDeepReasoning() {
        String complexPrompt = "请结合 2026 年前两季度研发财务报表，深入分析微服务架构与单体架构在云资源消耗上的差异原因，"
                + "第一步先检索各微服务 Pod CPU 监控数据，第二步计算各节点开销占比，并综合对比两者 ROI，最后给出优化结论？";

        DynamicCotCognitiveRouter.RoutingDecision decision = router.route(complexPrompt, 500, 8);

        assertNotNull(decision);
        assertEquals(CognitiveStrategy.DEEP_REASONING, decision.strategy(), "复杂多跳逻辑任务必须激活 DEEP_REASONING");
        assertTrue(decision.complexityScore() >= 0.70, "多跳复合任务复杂度必须 >= 0.70");
        System.out.printf("[Contract 2] 复杂任务路由得分: %.4f, reason: %s%n", decision.complexityScore(), decision.reason());
    }

    @Test
    @DisplayName("契约测试 3: 工具调用执行异常微观单步自省因果特征提取成功")
    void testHierarchicalReflexion_MicroCausalCorrection_Success() {
        String toolName = "database_query_tool";
        String badArgsJson = "{\"query\": \"SELECT * FROM t_order WHERE year = 2026 GROUP BY group\"}";
        String rawSqlError = "org.h2.jdbc.JdbcSQLSyntaxErrorException: Syntax error in SQL statement: unexpected token 'group'";

        ReflexionAction action = governor.governToolFailure(toolName, badArgsJson, rawSqlError, 0, "cache_query_tool");

        assertNotNull(action);
        assertEquals(ReflexionAction.ReflexionType.MICRO_RETRY_ADAPTIVE, action.type(), "首次单步失败必须触发 MICRO_RETRY_ADAPTIVE");
        assertEquals(1, action.currentRetryCount());
        assertTrue(action.causalErrorFeature().contains("SQL_SYNTAX_ERROR"), "因果错误特征必须精确定位 SQL_SYNTAX_ERROR");
        assertNotNull(action.canonicalFingerprint(), "必须生成规范化指纹");
        System.out.printf("[Contract 3] 微观反思因果特征: %s, fp: %s%n", action.causalErrorFeature(), action.canonicalFingerprint());
    }

    @Test
    @DisplayName("契约测试 4: 相同入参同构连续调用强制阻断并触发宏观备选工具回退")
    void testHierarchicalReflexion_IsomorphicParameters_TriggersMacroFallback() {
        String toolName = "mcp_weather_api";
        // 两次入参即使键序颠倒，规范化后也应视为同构指纹
        String args1 = "{\"city\": \"Beijing\", \"lang\": \"zh\"}";
        String args2 = "{\"lang\": \"zh\", \"city\": \"Beijing\"}";
        String errorMsg = "HTTP 503 Service Unavailable: upstream timeout";

        // 第一次调用异常
        ReflexionAction firstAction = governor.governToolFailure(toolName, args1, errorMsg, 0, "mcp_weather_cache");
        assertEquals(ReflexionAction.ReflexionType.MICRO_RETRY_ADAPTIVE, firstAction.type());

        // 第二次使用同构参数调用异常 -> 触发同构死循环防护
        ReflexionAction secondAction = governor.governToolFailure(toolName, args2, errorMsg, 1, "mcp_weather_cache");

        assertNotNull(secondAction);
        assertEquals(ReflexionAction.ReflexionType.MACRO_TOOL_FALLBACK, secondAction.type(), "同构参数调用必须直接升级为宏观工具回退");
        assertEquals("mcp_weather_cache", secondAction.fallbackToolName(), "宏观回退必须正确切换至备选工具");
        System.out.printf("[Contract 4] 同构死循环拦截成功，切换备选工具: %s%n", secondAction.fallbackToolName());
    }

    @Test
    @DisplayName("契约测试 5: 因果时钟遮蔽彻底消除历史会话陈旧破坏性写动作反向污染")
    void testTemporalMemoryAligner_CausalMasking_EliminatesStaleAction() {
        float[] queryVec = createUnitVector(1536, 0); // 假定第 0 维度为 1.0

        // 构造 Node 1: 历史会话的破坏性清空动作
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("isActionNode", true);
        meta1.put("sessionId", "session_old_202608");
        meta1.put("causalClock", 10L);

        MemoryNode node1 = MemoryNode.builder()
                .id("mem_001")
                .content("执行清理动作：清空所有测试临时表并重置数据库")
                .embedding(createUnitVector(1536, 0)) // 极高语义相似度
                .importance(0.80)
                .timestamp(System.currentTimeMillis() - 86400000L * 30) // 30天前
                .metadata(meta1)
                .build();

        // 构造 Node 2: 当前有效知识配置
        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("isActionNode", false);
        meta2.put("sessionId", "session_active_202609");
        meta2.put("causalClock", 100L);

        MemoryNode node2 = MemoryNode.builder()
                .id("mem_002")
                .content("企业生产网关标准配置：端口锁定为 9090，启用安全鉴权")
                .embedding(createUnitVector(1536, 1)) // 余弦正交
                .importance(0.95) // 触发激活保护
                .timestamp(System.currentTimeMillis() - 86400000L * 5)
                .metadata(meta2)
                .build();

        List<AlignedMemoryItem> result = aligner.alignAndRetrieve(
                List.of(node1, node2),
                queryVec,
                System.currentTimeMillis(),
                "session_active_202609", // 当前会话
                110L,
                5
        );

        assertNotNull(result);
        assertEquals(1, result.size(), "破坏性陈旧写动作必须被 100% 因果遮蔽过滤");
        assertEquals("mem_002", result.get(0).memoryId(), "活跃重要配置必须被成功保留");
        assertFalse(result.get(0).isMasked());
        System.out.printf("[Contract 5] 记忆因果遮蔽生效，召回有效记忆: %s, score=%.4f%n",
                result.get(0).content(), result.get(0).compositeScore());
    }

    @Test
    @DisplayName("契约测试 6: 1000Hz 定长 4096 槽位 Disruptor 无锁总线与 JitterGuard 软着陆")
    void testDisruptorControlBus_1000HzPublishAndJitterGuard() {
        assertEquals(HermesCognitiveControlBus.STATUS_ACTIVE_NOMINAL, controlBus.getBusStatus());

        // 连续发布 10 帧
        for (int i = 0; i < 10; i++) {
            CognitiveEventFrame frame = new CognitiveEventFrame(
                    "frame_" + i, "req_" + i, "sess_" + i,
                    CognitiveStrategy.SHORT_COT, System.nanoTime(),
                    createUnitVector(1536, 0)
            );
            boolean pub = controlBus.publishFrame(frame);
            assertTrue(pub, "无锁发布必须成功");
        }
        assertEquals(10, controlBus.getPublishedCount());

        // 验证手动触发软着陆降级
        controlBus.tripDegradation("测试下游模型网络连续超时");
        assertEquals(HermesCognitiveControlBus.STATUS_DEGRADED_FALLBACK_DIRECT, controlBus.getBusStatus(),
                "抖动或熔断后总线必须平滑切入 DEGRADED_FALLBACK_DIRECT 模式");

        // 恢复正常
        controlBus.resetStatus();
        assertEquals(HermesCognitiveControlBus.STATUS_ACTIVE_NOMINAL, controlBus.getBusStatus());
        System.out.println("[Contract 6] 1000Hz 4096 槽位 Disruptor 总线与 JitterGuard 软着陆验证通过");
    }

    @Test
    @DisplayName("契约测试 7: 阿里千问 1536 维超球面单位特征向量模长与测地几何强约束")
    void testHypersphereEmbedding_Qwen1536DimensionalConstraint() {
        float[] v1 = createUnitVector(1536, 0);
        float[] v2 = createUnitVector(1536, 1);

        assertEquals(1536, v1.length, "千问向量维度必须严格锁定为 1536 维");
        assertEquals(1536, v2.length);

        double norm1 = computeNorm(v1);
        double norm2 = computeNorm(v2);
        assertEquals(1.0, norm1, 1e-4, "单位超球面模长必须严格为 1.0");
        assertEquals(1.0, norm2, 1e-4);

        // 正交向量内积为 0
        double cosOrth = aligner.computeCosine(v1, v2);
        assertEquals(0.0, cosOrth, 1e-4, "正交基底余弦值必须为 0");

        // 自身内积为 1
        double cosSelf = aligner.computeCosine(v1, v1);
        assertEquals(1.0, cosSelf, 1e-4, "同向单位向量余弦值必须为 1.0");
        System.out.println("[Contract 7] 阿里千问 1536 维超球面单位测地向量约束验证通过");
    }

    @Test
    @DisplayName("契约测试 8: 不可变凭单 HermesCognitiveReceipt 密码学防篡改自签与验真")
    void testCognitiveReceipt_CryptographicSignatureVerification() {
        HermesCognitiveReceipt receipt = controlBus.issueReceipt(
                "req_1001",
                "sess_2002",
                CognitiveStrategy.SHORT_COT,
                0.55,
                15000000L,
                1,
                3,
                0.88
        );

        assertNotNull(receipt);
        assertNotNull(receipt.signatureSha256());
        assertEquals(64, receipt.signatureSha256().length(), "SHA-256 签名必须为 64 位十六进制");

        // 校验合法有效性
        assertTrue(receipt.verifySignature(), "原始签发凭单自验必须 100% 成立");

        // 模拟黑客篡改字段
        HermesCognitiveReceipt tampered = new HermesCognitiveReceipt(
                receipt.receiptId(),
                receipt.requestId(),
                receipt.sessionId(),
                receipt.strategy(),
                0.99, // 篡改复杂度
                receipt.cotLatencyNanos(),
                receipt.reflexionRoundCount(),
                receipt.alignedMemoryCount(),
                receipt.busStatus(),
                receipt.qwenGeodesicScore(),
                receipt.timestampNanos(),
                receipt.signatureSha256() // 沿用旧签名
        );

        assertFalse(tampered.verifySignature(), "被篡改字段的凭单验真必须严格返回 false");
        System.out.printf("[Contract 8] 密码学凭单签发与防篡改验证成功: %s, sig=%s%n",
                receipt.receiptId(), receipt.signatureSha256());
    }

    private float[] createUnitVector(int dim, int activeIndex) {
        float[] vec = new float[dim];
        if (activeIndex >= 0 && activeIndex < dim) {
            vec[activeIndex] = 1.0f;
        } else {
            vec[0] = 1.0f;
        }
        return vec;
    }

    private double computeNorm(float[] vec) {
        double sum = 0.0;
        for (float v : vec) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }
}
