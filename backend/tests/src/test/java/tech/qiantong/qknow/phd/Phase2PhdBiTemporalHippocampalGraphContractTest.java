package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.memory.bitemporal.*;
import tech.qiantong.qknow.hermes.memory.hippocampus.EpisodicTurnRecord;
import tech.qiantong.qknow.hermes.memory.hippocampus.HippocampalSleepConsolidator;
import tech.qiantong.qknow.hermes.tool.function.SearchKnowledgeTool;
import tech.qiantong.qknow.hermes.tool.function.query.knowledgeQuery;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 博士论文代际升级 · 阶段 2（论文第 4 章）核心契约测试套件
 * 覆盖：
 * 1. 定理 4.1(3)：双时态 [validFrom, validTo) × [txFrom, txTo) 零物理硬删除矛盾边截断、历史时间点无倒挂查询与源感知引用计数；
 * 2. 定理 4.1(1)-(2)：S^1023 多簇球面睡眠整合平方弦失真恒等式 2(1-R_k) 严格优于单质心坍缩，及本征测地紧上界验证；
 * 3. 定理 4.2：识别记忆过滤 + 稠密-稀疏二分图 PPR（含 Error-Book 负向排斥势场）与 SearchKnowledgeTool 动态 IRCoT 子查询消费验证。
 */
class Phase2PhdBiTemporalHippocampalGraphContractTest {

    private static final int DIM = 1024;

    private static float[] basisUnitVec(int axisA, double weightA, int axisB, double weightB) {
        float[] v = new float[DIM];
        v[axisA] = (float) weightA;
        v[axisB] = (float) weightB;
        LocalitySensitiveFeatureHasher.normalizeInPlace(v);
        return v;
    }

    @Test
    @DisplayName("2.1 定理 4.1(3)：双时态非破坏性矛盾边失效、任意时间截面单值无倒挂不变量与文档引用计数")
    void test01_BiTemporalZeroHardDeleteContradictionResolutionAndReferenceCounting() {
        BiTemporalHippocampalGraphEngine graph = new BiTemporalHippocampalGraphEngine("hippo-mac-secret");

        float[] vChiefOld = basisUnitVec(0, 1.0, 1, 0.0);
        float[] vChiefNew = basisUnitVec(0, 0.0, 1, 1.0); // 正交目标：平方弦距离 d^2 = 2.0 > tau_contra

        graph.upsertVertex(new HippocampalVertex("ent_acme", "星辰科技", HippocampalVertexTier.PHRASE_ENTITY, basisUnitVec(2, 1.0, 3, 0.0)));
        graph.upsertVertex(new HippocampalVertex("ent_ceo_zhang", "张伟", HippocampalVertexTier.PHRASE_ENTITY, vChiefOld));
        graph.upsertVertex(new HippocampalVertex("ent_ceo_li", "李娜", HippocampalVertexTier.PHRASE_ENTITY, vChiefNew));

        // 在事务时刻 tx=1000 摄入事实 1：2024 年起（valid=100）星辰科技 CEO 为张伟（来自 doc_1 与 doc_2 两篇文档）
        graph.ingestFactEdge("ent_acme", "ent_ceo_zhang", "HAS_CEO", true, vChiefOld, 100L, 1000L, "doc_1");
        BiTemporalHyperEdge sharedEdge = graph.ingestFactEdge("ent_acme", "ent_ceo_zhang", "HAS_CEO", true, vChiefOld, 100L, 1050L, "doc_2");
        assertEquals(2, sharedEdge.sourceReferenceCount(), "多文档共现同一事实必须累加 fact_source_counts 引用计数");

        // 删除单篇文档 doc_1：引用计数从 2 降为 1，共享三元组严禁被误删！
        graph.detachDocumentSource("doc_1", 1100L);
        List<BiTemporalHyperEdge> afterSingleDocDelete = graph.queryCrossSectionAsOf("ent_acme", "HAS_CEO", 150L, 1150L);
        assertEquals(1, afterSingleDocDelete.size());
        assertEquals("ent_ceo_zhang", afterSingleDocDelete.get(0).targetNodeId());

        // 在事务时刻 tx=2000 摄入新矛盾事实：2025 年起（valid=500）星辰科技 CEO 变更为李娜
        graph.ingestFactEdge("ent_acme", "ent_ceo_li", "HAS_CEO", true, vChiefNew, 500L, 2000L, "doc_3");

        // 双时态截面查询验证（定理 4.1(3) 无倒挂单值性）：
        // (a) 事后（tx=2500）查询当前有效时刻（valid=600）：唯一活跃目标必须为李娜
        List<BiTemporalHyperEdge> currentView = graph.queryCrossSectionAsOf("ent_acme", "HAS_CEO", 600L, 2500L);
        assertEquals(1, currentView.size());
        assertEquals("ent_ceo_li", currentView.get(0).targetNodeId());

        // (b) 事后（tx=2500）回溯历史有效时刻（valid=300）：修正历史视图精确还原当时 CEO 为张伟！
        List<BiTemporalHyperEdge> historicalValidView = graph.queryCrossSectionAsOf("ent_acme", "HAS_CEO", 300L, 2500L);
        assertEquals(1, historicalValidView.size());
        assertEquals("ent_ceo_zhang", historicalValidView.get(0).targetNodeId());

        // (c) 当时所知（tx=1500，早于新事实入库时刻 2000）查询 valid=600：还原系统当时的认知快照（张伟）
        List<BiTemporalHyperEdge> pastTxView = graph.queryCrossSectionAsOf("ent_acme", "HAS_CEO", 600L, 1500L);
        assertEquals(1, pastTxView.size());
        assertEquals("ent_ceo_zhang", pastTxView.get(0).targetNodeId());

        // (d) 零物理硬删除验证：被事务封账的旧边必须完整保留在三级冷备环中且 MAC 验签通过
        assertTrue(graph.getColdStandbyArchiveCount() >= 1, "失效旧边必须转入三级冷备隔离区而非物理删除");
        assertTrue(currentView.get(0).verifyMac("hippo-mac-secret"));
    }

    @Test
    @DisplayName("2.2 定理 4.1(1)-(2)：S^1023 多簇睡眠整合精确平方弦失真 2(1-R_k) 严格小于单质心坍缩，且受本征测地界约束")
    void test02_Theorem4_1_MultiClusterSphericalConsolidationDistortionBounds() {
        HippocampalSleepConsolidator consolidator = new HippocampalSleepConsolidator("sleep-secret");

        // 构造两个近似正交话题簇的 4 条会话经验（簇 A 围绕维度 0，簇 B 围绕维度 10）
        EpisodicTurnRecord t1 = new EpisodicTurnRecord("t1", "sess-phd", "user", "超球面黎曼流形公理",
                basisUnitVec(0, 0.98, 1, 0.20), Map.of("topic", "geometry"), 1000L, "sig1");
        EpisodicTurnRecord t2 = new EpisodicTurnRecord("t2", "sess-phd", "assistant", "弦距离满足三角不等式",
                basisUnitVec(0, 0.96, 1, -0.28), Map.of("topic", "geometry"), 1010L, "sig2");
        EpisodicTurnRecord t3 = new EpisodicTurnRecord("t3", "sess-phd", "user", "PostgreSQL 预写日志崩溃恢复",
                basisUnitVec(10, 0.97, 11, 0.24), Map.of("topic", "storage", "errorTrap", "禁止使用/tmp易失目录"), 1020L, "sig3");
        EpisodicTurnRecord t4 = new EpisodicTurnRecord("t4", "sess-phd", "assistant", "Step Journal 保证精确一次重放",
                basisUnitVec(10, 0.95, 11, -0.31), Map.of("topic", "storage"), 1030L, "sig4");

        MultiClusterSleepConsolidationReceipt multiReceipt =
                consolidator.consolidateMultiClusterWithWiki("sess-phd", List.of(t1, t2, t3, t4), 2);

        assertEquals(2, multiReceipt.clusters().size(), "两个正交话题必须被凝聚为 2 个独立球冠簇");
        assertTrue(multiReceipt.totalMultiClusterChordDistortion() < multiReceipt.singleCentroidCollapseDistortion() - 0.50,
                String.format("多簇整合失真 %.4f 必须显著低于单质心坍缩失真 %.4f",
                        multiReceipt.totalMultiClusterChordDistortion(), multiReceipt.singleCentroidCollapseDistortion()));

        // 验证每个簇严格满足定理 4.1(1)-(2) 双边不等式：2(1-R_k) <= GeodesicRisk <= (halfTheta / sin(halfTheta))^2 * 2(1-R_k)
        for (MultiClusterSleepConsolidationReceipt.SphericalClusterSummary c : multiReceipt.clusters()) {
            double exactChordDistortion = 2.0 * (1.0 - c.meanResultantLength());
            assertEquals(exactChordDistortion, c.squaredChordDistortion(), 1e-4, "平方弦失真必须精确等于 2(1 - R_k)");
            assertTrue(c.intrinsicGeodesicDistortion() + 1e-6 >= exactChordDistortion, "测地平方失真下界 >= 2(1 - R_k)");
            assertTrue(c.intrinsicGeodesicDistortion() <= c.geodesicUpperBound() + 1e-5, "测地平方失真必须受球冠紧上界控制");
        }
        assertFalse(multiReceipt.compiledErrorBookNotes().isEmpty(), "包含 errorTrap 标记的经验必须自动编译为持久错题本节点");
    }

    @Test
    @DisplayName("2.3 定理 4.2：识别记忆双轨二分图 PPR + Error-Book 排斥势场 + SearchKnowledgeTool 动态子查询")
    void test03_Theorem4_2_BipartitePprWithErrorBookAndDynamicSearchKnowledgeTool() {
        BiTemporalHippocampalGraphEngine graph = new BiTemporalHippocampalGraphEngine("ppr-secret");

        float[] qVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("星辰科技现任首席执行官负责的核心战略项目", DIM);
        float[] eAcme = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("星辰科技", DIM);
        float[] eLi = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("李娜 现任首席执行官", DIM);
        float[] pTargetPassage = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "李娜出任星辰科技首席执行官后，亲自挂帅启动超球面认知操作系统天枢计划。", DIM);
        float[] wTrapError = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(
                "历史错误陷阱：切勿将已卸任的旧负责人混淆为天枢计划负责人。", DIM);

        graph.upsertVertex(new HippocampalVertex("ent_acme", "星辰科技", HippocampalVertexTier.PHRASE_ENTITY, eAcme));
        graph.upsertVertex(new HippocampalVertex("ent_li", "李娜", HippocampalVertexTier.PHRASE_ENTITY, eLi));
        graph.upsertVertex(new HippocampalVertex("pass_tianshu",
                "李娜出任星辰科技首席执行官后，亲自挂帅启动超球面认知操作系统天枢计划。",
                HippocampalVertexTier.PASSAGE_EPISODE, pTargetPassage));
        graph.upsertVertex(new HippocampalVertex("err_trap_01",
                "错题警示：严禁引用已失效的旧任期负责人信息",
                HippocampalVertexTier.ERROR_BOOK, wTrapError));

        // 构建短语实体跳与短语-段落二分图包含边
        graph.ingestFactEdge("ent_acme", "ent_li", "CURRENT_CEO", true, eLi, 100L, 100L, "doc_a");
        graph.ingestFactEdge("ent_li", "pass_tianshu", "MENTIONED_IN_PASSAGE", false, pTargetPassage, 100L, 100L, "doc_a");
        graph.ingestFactEdge("ent_acme", "err_trap_01", "HAS_ERROR_WARNING", false, wTrapError, 100L, 100L, "err_book");

        HippocampalProbeReceipt probeReceipt = graph.probeIterativeSubgraph(
                "q-ircot-01", "星辰科技现任首席执行官负责的核心战略项目", qVec, 200L, 200L, 3
        );

        assertTrue(probeReceipt.pprL1Residual() < 1e-4, "CSR 稀疏二分图 PPR 必须在有界步数内几何收敛");
        assertTrue(probeReceipt.recalledPassageIds().contains("pass_tianshu"),
                "经 2-hop 实体-段落二分图 PPR 扩散必须成功召回目标深层段落 pass_tianshu！");
        assertTrue(probeReceipt.recalledErrorBookWarnings().stream().anyMatch(w -> w.contains("错题警示")),
                "命中关联错题本节点时必须返回防错警示且不混入正文事实段落");

        // 验证重构后的 SearchKnowledgeTool：彻底告别无视 query.getQuery() 的静态复读机！
        SearchKnowledgeTool dynamicTool = SearchKnowledgeTool.withHippocampalEngine(
                "kb-1", "企业战略库", "首轮静态预检索无关摘要", graph, 200L, 200L
        );

        knowledgeQuery subQuery = new knowledgeQuery();
        subQuery.setQuery("天枢计划 首席执行官 李娜");
        String toolOutput = dynamicTool.apply(subQuery, null);

        assertTrue(toolOutput.contains("天枢计划"), "SearchKnowledgeTool 必须真实消费动态子查询参数召回新段落，而非复读静态预检索文本！");
        assertFalse(toolOutput.equals("根据知识库检索结果：\n首轮静态预检索无关摘要"), "严禁无视子查询原样复读预检索内容");
    }
}
