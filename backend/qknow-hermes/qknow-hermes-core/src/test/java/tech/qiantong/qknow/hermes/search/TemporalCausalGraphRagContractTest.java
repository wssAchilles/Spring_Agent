package tech.qiantong.qknow.hermes.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.speculative.ConfidenceGatedPrefetchFilter;
import tech.qiantong.qknow.hermes.daemon.DaemonProbeDispatcher;
import tech.qiantong.qknow.hermes.search.graphrag.causal.TemporalCausalGraphRagEngine;
import tech.qiantong.qknow.hermes.search.graphrag.causal.TemporalCausalTriplet;
import tech.qiantong.qknow.hermes.search.graphrag.causal.TemporalGraphRetrievalReceipt;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时间感知语义三元组因果知识图谱与 GraphRAG 深度融合引擎专项契约测试
 * (TemporalCausalGraphRagContractTest)
 * 验证 5 项严格物理断言：
 * 1. 纯不可变三元组数据结构、1536 维超球面单位向量与时间有效性半衰期单调衰减；
 * 2. 两阶段检索与 Kahn 算法因果拓扑偏序确定性保序；
 * 3. 时空半衰期衰减对陈旧过期知识倒挂的 100% 阻断；
 * 4. 存证凭单 SHA-256 常量时间自验真与防侧信道比对；
 * 5. 软删除三级冷备环形仓隔离与防硬删除可逆存证。
 */
public class TemporalCausalGraphRagContractTest {

    private static final int DIM = 1536;
    private static final String SECRET_KEY = "CONTRACT_TEST_TKG_SECRET_2026";

    private float[] createUnitVector(int seed) {
        float[] v = new float[DIM];
        Random rnd = new Random(seed);
        double sumSq = 0.0;
        for (int i = 0; i < DIM; i++) {
            v[i] = (float) rnd.nextGaussian();
            sumSq += (double) v[i] * v[i];
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < DIM; i++) {
            v[i] /= norm;
        }
        return v;
    }

    @Test
    @DisplayName("断言一：验证纯不可变三元组、1536 维向量约束与半衰期指数衰减单调性")
    void testTemporalCausalTripletImmutabilityAndDefensiveCopy() {
        long now = System.currentTimeMillis();
        float[] validVec = createUnitVector(101);

        // 1. 验证非 1536 维向量抛出异常
        assertThrows(IllegalArgumentException.class, () ->
                TemporalCausalTriplet.of("err", "s", "p", "o", now, null, now - 1000, now + 1000, 86400000L, 0.9, new float[512])
        );

        TemporalCausalTriplet triplet = TemporalCausalTriplet.of(
                "triplet_test_01",
                "ClusterA",
                "failover_to",
                "ClusterB",
                now,
                null,
                now - 10000,
                now + 3600000,
                86400000L,
                0.95,
                validVec
        );

        // 2. 验证防御性拷贝不可变性
        float[] cloned = triplet.tripletVector();
        assertNotNull(cloned);
        cloned[0] = 999.0f;
        assertNotEquals(999.0f, triplet.tripletVector()[0], "三元组内部向量必须具备严格深拷贝不可变性");

        // 3. 验证时间有效性窗口单调衰减
        TemporalCausalTriplet.ValidTimeWindow window = triplet.validTimeWindow();
        // 尚未生效的事实 (future)
        assertTrue(window.computeDecayFactor(now - 20000) == 0.0, "未生效事实必须硬阻断，衰减因子为 0.0");
        // 当前窗口期有效事实
        assertEquals(1.0, window.computeDecayFactor(now), 1e-6, "有效区间内衰减因子必须为 1.0");
        // 过期半衰期事实 (刚好过去 1 个半衰期 86400000ms)
        double decay1 = window.computeDecayFactor(now + 3600000 + 86400000L);
        assertEquals(0.5, decay1, 1e-4, "过去一个半衰期后衰减因子必须等于 0.5");
        // 过期 2 个半衰期
        double decay2 = window.computeDecayFactor(now + 3600000 + 2 * 86400000L);
        assertEquals(0.25, decay2, 1e-4, "过去两个半衰期后衰减因子必须等于 0.25");
        assertTrue(decay1 > decay2, "衰减因子必须随时间单调递减");
    }

    @Test
    @DisplayName("断言二：验证两阶段检索与 Kahn 算法因果拓扑偏序确定性保序输出")
    void testTwoPhaseRetrievalAndKahnTopologicalOrdering() {
        TemporalCausalGraphRagEngine engine = new TemporalCausalGraphRagEngine(SECRET_KEY);
        long now = System.currentTimeMillis();
        float[] uQuery = createUnitVector(201);

        // 构造因果因果链: Node1 (原因) -> Node2 (中间状态) -> Node3 (最终结果)
        float[] v1 = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uQuery, 0.95, 201L);
        float[] v2 = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uQuery, 0.92, 202L);
        float[] v3 = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uQuery, 0.89, 203L);

        TemporalCausalTriplet t1 = TemporalCausalTriplet.of("node_01", "DatabaseEngine", "disk_full", "FailoverTriggered",
                now - 3000, null, now - 10000, now + 100000, 86400000L, 0.95, v1);

        TemporalCausalTriplet t2 = TemporalCausalTriplet.of("node_02", "FailoverTriggered", "elect_new_leader", "StandbyNodeX",
                now - 2000, "node_01", now - 10000, now + 100000, 86400000L, 0.93, v2);

        TemporalCausalTriplet t3 = TemporalCausalTriplet.of("node_03", "StandbyNodeX", "mount_storage", "Shard09",
                now - 1000, "node_02", now - 10000, now + 100000, 86400000L, 0.91, v3);

        // 乱序注册
        engine.registerTriplets(List.of(t3, t1, t2));

        TemporalCausalGraphRagEngine.RetrievalResult result = engine.retrieveCausalSubgraph(
                "分析数据库故障转移与分片恢复的全因果链路",
                uQuery,
                now,
                5,
                16
        );

        assertNotNull(result);
        List<String> props = result.propositions();
        assertEquals(3, props.size(), "必须遍历出完整的 3 节点因果子图");

        // 验证拓扑因果偏序严格有序: t1 诱发 t2，t2 诱发 t3
        assertTrue(props.get(0).contains("【因果阶 1】") && props.get(0).contains("DatabaseEngine"),
                "第 1 阶因果命题必须为根前驱 DatabaseEngine");
        assertTrue(props.get(1).contains("【因果阶 2】") && props.get(1).contains("StandbyNodeX"),
                "第 2 阶因果命题必须为中间事件 StandbyNodeX");
        assertTrue(props.get(2).contains("【因果阶 3】") && props.get(2).contains("Shard09"),
                "第 3 阶因果命题必须为最终结果 Shard09");
    }

    @Test
    @DisplayName("断言三：验证时空半衰期衰减对陈旧过期知识倒挂的 100% 阻断")
    void testStaleKnowledgeInversionPreventionByDecay() {
        TemporalCausalGraphRagEngine engine = new TemporalCausalGraphRagEngine(SECRET_KEY);
        long now = System.currentTimeMillis();
        float[] uQuery = createUnitVector(301);

        // 构造高文本相似度的陈旧过期规则 (7 天前失效，半衰期 24 小时)
        // 原始点积高达 0.98，若无时间衰减必然排第一发生倒挂
        float[] vStale = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uQuery, 0.98, 301L);
        TemporalCausalTriplet staleTriplet = TemporalCausalTriplet.of(
                "stale_rule_v1",
                "PaymentGateway",
                "enforce_limit_rule",
                "MaxDailyTransfer_OLD_TIER",
                now - 10 * 86400000L,
                null,
                now - 14 * 86400000L,
                now - 7 * 86400000L, // 7 天前已关闭失效
                86400000L,
                0.95,
                vStale
        );

        // 构造当前正在生效的新规则 (语义相似度略低为 0.90，但在有效时间窗口内)
        float[] vCurrent = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uQuery, 0.90, 302L);
        TemporalCausalTriplet currentTriplet = TemporalCausalTriplet.of(
                "current_rule_v2",
                "PaymentGateway",
                "enforce_limit_rule",
                "MaxDailyTransfer_NEW_TIER",
                now - 1000,
                null,
                now - 86400000L,
                now + 30 * 86400000L, // 当前完全有效
                86400000L,
                0.95,
                vCurrent
        );

        engine.registerTriplets(List.of(staleTriplet, currentTriplet));

        TemporalCausalGraphRagEngine.RetrievalResult result = engine.retrieveCausalSubgraph(
                "查询当前支付网关单日转账额度限额规则",
                uQuery,
                now,
                5,
                16
        );

        List<String> props = result.propositions();
        assertFalse(props.isEmpty(), "必须命中有效规则");

        // 严格断言：第 1 命中项必须是最新生效的 current_rule_v2，绝不能是 stale_rule_v1
        assertTrue(props.get(0).contains("current_rule_v2"), "有效新规则必须排在第一位，阻断陈旧知识倒挂");
        assertTrue(props.get(0).contains("MaxDailyTransfer_NEW_TIER"), "有效新规则内容必须在首位");
        boolean containsStaleAtTop = props.get(0).contains("stale_rule_v1") || props.get(0).contains("MaxDailyTransfer_OLD_TIER");
        assertFalse(containsStaleAtTop, "过期陈旧规则绝对不允许在首位发生倒挂");
    }

    @Test
    @DisplayName("断言四：验证存证凭单 SHA-256 常量时间自验真与防篡改侧信道比对")
    void testCryptographicSignatureAndConstantTimeVerification() {
        TemporalCausalGraphRagEngine engine = new TemporalCausalGraphRagEngine(SECRET_KEY);
        long now = System.currentTimeMillis();
        float[] uQuery = createUnitVector(401);

        float[] v1 = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uQuery, 0.91, 401L);
        TemporalCausalTriplet t1 = TemporalCausalTriplet.of("node_sec_01", "AuthService", "issue_token", "UserAdmin",
                now, null, now - 1000, now + 10000, 86400000L, 0.95, v1);
        engine.registerTriplet(t1);

        TemporalCausalGraphRagEngine.RetrievalResult result = engine.retrieveCausalSubgraph(
                "查询鉴权凭证分发记录",
                uQuery,
                now,
                5,
                16
        );

        TemporalGraphRetrievalReceipt receipt = result.receipt();
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(SECRET_KEY), "使用正确密钥自验真签名必须通过");
        assertFalse(receipt.verifySignature("TAMPERED_SECRET_KEY"), "使用篡改密钥验签必须失败");
        assertTrue(receipt.latencyNanos() > 0, "执行延迟记录必须为正整数");
    }

    @Test
    @DisplayName("断言五：验证软删除三级冷备环形仓隔离与常驻调度器端到端分发")
    void testSoftDeleteAndColdBufferIsolation() {
        TemporalCausalGraphRagEngine engine = new TemporalCausalGraphRagEngine(SECRET_KEY);
        long now = System.currentTimeMillis();
        float[] v1 = createUnitVector(501);

        TemporalCausalTriplet t1 = TemporalCausalTriplet.of("node_del_01", "SubSysA", "linked_to", "SubSysB",
                now, null, now - 1000, now + 10000, 86400000L, 0.95, v1);
        engine.registerTriplet(t1);
        assertEquals(1, engine.getRegistrySize());

        // 软删除
        engine.softDeleteTriplet("node_del_01");
        assertEquals(0, engine.getRegistrySize(), "软删除后活跃索引中必须立即不可见");
        assertEquals(1, engine.getColdBufferSnapshot().size(), "软删除三元组必须安全转储入三级冷备环形仓");
        assertEquals("node_del_01", engine.getColdBufferSnapshot().get(0).tripletId());

        // 端到端验证 DaemonProbeDispatcher 中的 TEMPORAL_CAUSAL_GRAPHRAG 探针
        String payload = """
                {
                    "probe_type": "TEMPORAL_CAUSAL_GRAPHRAG",
                    "task_id": "probe_task_tkg_contract"
                }
                """;
        String resp = DaemonProbeDispatcher.dispatch(payload);
        assertNotNull(resp);
        assertTrue(resp.contains("\"status\":\"SUCCESS\""), "调度器探针响应必须为 SUCCESS");
        assertTrue(resp.contains("\"require_temporal_causal_graphrag_hold\":true"), "因果图谱探针核心物理断言必须为 true");
        assertTrue(resp.contains("\"causal_order_verified\":true"), "因果偏序必须严格校验通过");
        assertTrue(resp.contains("\"no_stale_inversion_hold\":true"), "陈旧规则倒挂阻断必须为 true");
        assertTrue(resp.contains("\"seal_verified\":true"), "签名自验真必须通过");
    }
}
