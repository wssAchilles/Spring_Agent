package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHyperEdge;
import tech.qiantong.qknow.hermes.memory.bitemporal.ColdStandbyTier;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalEvolutionRetriever;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalRetrievalReceipt;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.CausalEvolutionStep;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.PredicateCardinality;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorSpec;
import tech.qiantong.qknow.hermes.tool.function.SearchKnowledgeTool;
import tech.qiantong.qknow.hermes.tool.function.query.knowledgeQuery;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8.1 双时态状态转移与因果演化记忆层契约测试（严格验证定理 8.1 三大不变量）。
 */
class Phase8_1BiTemporalCausalEvolutionContractTest {

    private static long utcMs(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static float[] unitVec(String seed) {
        return LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(seed, 1024);
    }

    @Test
    void temporalAnchorParserDetectsPointAsOfEvolutionWindowAndLatestDeterministically() {
        TemporalAnchorQueryParser parser = new TemporalAnchorQueryParser();
        long nowMs = utcMs(2026, 6, 1);

        TemporalAnchorSpec pointSpec = parser.parse("截至2024年3月公司的CEO是谁？", nowMs);
        assertEquals(TemporalAnchorSpec.QueryTemporalMode.POINT_AS_OF, pointSpec.mode());
        assertTrue(pointSpec.validToEpochMs() < utcMs(2024, 4, 1));
        assertTrue(pointSpec.validToEpochMs() >= utcMs(2024, 3, 1));

        TemporalAnchorSpec engPoint = parser.parse("Who lived in Berlin as of 2023-08-15?", nowMs);
        assertEquals(TemporalAnchorSpec.QueryTemporalMode.POINT_AS_OF, engPoint.mode());
        assertFalse(engPoint.parsedTimeExpressions().isEmpty());

        TemporalAnchorSpec windowSpec = parser.parse("请对比从2023年到2025年用户的超市偏好的演变过程", nowMs);
        assertEquals(TemporalAnchorSpec.QueryTemporalMode.EVOLUTION_WINDOW, windowSpec.mode());
        assertTrue(windowSpec.validFromEpochMs() <= utcMs(2023, 1, 1));
        assertTrue(windowSpec.validToEpochMs() >= utcMs(2025, 12, 31));

        TemporalAnchorSpec latestSpec = parser.parse("What is the company flagship product?", nowMs);
        assertEquals(TemporalAnchorSpec.QueryTemporalMode.LATEST, latestSpec.mode());
        assertEquals(nowMs, latestSpec.validToEpochMs());
    }

    @Test
    void biTemporalFunctionalSupersedeSurvivesFifteenOverridesWithoutBlindSpotOrMultiValueCollateral() {
        BiTemporalHippocampalGraphEngine engine = new BiTemporalHippocampalGraphEngine("p81-secret");
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        BiTemporalCausalEvolutionRetriever retriever = new BiTemporalCausalEvolutionRetriever(
                engine, resolver, new TemporalAnchorQueryParser(), "p81-secret"
        );

        engine.upsertVertex(new HippocampalVertex("alice", "Alice", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Alice")));
        // 连续 15 次单值属性 LIVES_IN 覆写（远超 Graphiti 默认 Top-10 历史挤占盲区）
        for (int i = 1; i <= 15; i++) {
            String cityId = "city_" + i;
            String cityName = "City_" + i;
            engine.upsertVertex(new HippocampalVertex(cityId, cityName, HippocampalVertexTier.PHRASE_ENTITY, unitVec("Alice residence " + cityName)));
            long validFrom = utcMs(2020, 1, 1) + (long) i * 30L * 24L * 3600L * 1000L;
            long txNow = 10_000L + i * 100L;
            engine.ingestCausalFactEdge(
                    "alice", cityId, "LIVES_IN", PredicateCardinality.FUNCTIONAL,
                    unitVec("Alice lives in " + cityName), validFrom, txNow, "session_" + i, null
            );
        }

        // 校验最新截面上对 FUNCTIONAL 键有且仅有最后第 15 条唯一活跃，彻底消除多重影分身（定理 8.1(2)）
        long latestValid = utcMs(2026, 1, 1);
        long latestTx = 100_000L;
        List<BiTemporalHyperEdge> activeLatest = engine.queryCrossSectionAsOf("alice", "LIVES_IN", latestValid, latestTx);
        assertEquals(1, activeLatest.size(), "连续15次覆写后最新截面必须严格只有1条活跃边（唯一活性）");
        assertEquals("city_15", activeLatest.get(0).targetNodeId());

        // 校验历史回溯截面（第 5 次生效之后、第 6 次生效之前）精确命中 city_5
        long validAt5 = utcMs(2020, 1, 1) + 5L * 30L * 24L * 3600L * 1000L + 1000L;
        List<BiTemporalHyperEdge> activeHist5 = engine.queryCrossSectionAsOf("alice", "LIVES_IN", validAt5, latestTx);
        assertEquals(1, activeHist5.size(), "任意历史截面必须精确唯一命中当时有效状态");
        assertEquals("city_5", activeHist5.get(0).targetNodeId());

        // 校验 MULTI_VALUED 多值属性绝不跨目标误杀（根治 Graphiti #1728 49.2% 误杀故障）
        engine.upsertVertex(new HippocampalVertex("skill_java", "Java 21", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Java 21")));
        engine.upsertVertex(new HippocampalVertex("skill_rust", "Rust SIMD", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Rust SIMD")));
        engine.ingestCausalFactEdge("alice", "skill_java", "USES_SKILL", PredicateCardinality.MULTI_VALUED,
                unitVec("Alice uses Java 21"), utcMs(2024, 1, 1), 20_000L, "doc_a", null);
        engine.ingestCausalFactEdge("alice", "skill_rust", "USES_SKILL", PredicateCardinality.MULTI_VALUED,
                unitVec("Alice uses Rust SIMD"), utcMs(2024, 2, 1), 21_000L, "doc_b", null);
        List<BiTemporalHyperEdge> activeSkills = engine.queryCrossSectionAsOf("alice", "USES_SKILL", latestValid, latestTx);
        assertEquals(2, activeSkills.size(), "MULTI_VALUED 多值谓词禁止跨目标误杀，必须全部并存");

        // 校验演化窗口联合检索输出完整变迁且 ReadOnlyDatasetView + MAC 有效
        BiTemporalCausalRetrievalReceipt evoReceipt = retriever.retrieveAt(
                "q-evo",
                "Alice LIVES_IN evolution",
                TemporalAnchorSpec.evolutionWindow(utcMs(2020, 1, 1), latestValid, latestTx, List.of("2020..2026")),
                30
        );
        assertTrue(evoReceipt.verifyMac("p81-secret"), "双时态检索凭单 MAC 验签必须通过");
        assertTrue(evoReceipt.orderedCausalChain().size() >= 10, "演化窗口必须无损检索出历史演化过程");
        assertNotNull(evoReceipt.datasetView());
        assertEquals(ReadOnlyDatasetView.CANONICAL_COLUMNS, evoReceipt.datasetView().columns());
    }

    @Test
    void erroneousSupersedeRevertsLosslesslyFromColdStandbyRing() {
        BiTemporalHippocampalGraphEngine engine = new BiTemporalHippocampalGraphEngine("p81-revert");
        engine.upsertVertex(new HippocampalVertex("acme", "Acme Corp", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Acme")));
        engine.upsertVertex(new HippocampalVertex("ceo_a", "Dr. A", HippocampalVertexTier.PHRASE_ENTITY, unitVec("CEO Dr A")));
        engine.upsertVertex(new HippocampalVertex("ceo_b", "Fake CEO B", HippocampalVertexTier.PHRASE_ENTITY, unitVec("CEO Fake B")));

        BiTemporalHyperEdge truthEdge = engine.ingestCausalFactEdge(
                "acme", "ceo_a", "HAS_CEO", PredicateCardinality.FUNCTIONAL,
                unitVec("Acme CEO is Dr A"), utcMs(2024, 1, 1), 1000L, "official_doc", null
        );
        BiTemporalHyperEdge noiseEdge = engine.ingestCausalFactEdge(
                "acme", "ceo_b", "HAS_CEO", PredicateCardinality.FUNCTIONAL,
                unitVec("Acme CEO is Fake B"), utcMs(2025, 1, 1), 2000L, "rumor_doc", null
        );

        assertEquals(List.of(truthEdge.edgeId()), engine.getSupersededEdgeIds(noiseEdge.edgeId()));
        assertEquals(1, engine.queryCrossSectionAsOf("acme", "HAS_CEO", utcMs(2025, 6, 1), 3000L).size());
        assertEquals("ceo_b", engine.queryCrossSectionAsOf("acme", "HAS_CEO", utcMs(2025, 6, 1), 3000L).get(0).targetNodeId());

        // 执行定理 8.1(3) 无损可逆撤销算子：误边进入三级冷备，原边 100% 恢复活跃态
        assertTrue(engine.revertSupersedeEdge(noiseEdge.edgeId(), 4000L));
        List<BiTemporalHyperEdge> afterRevert = engine.queryCrossSectionAsOf("acme", "HAS_CEO", utcMs(2025, 6, 1), 5000L);
        assertEquals(1, afterRevert.size());
        assertEquals("ceo_a", afterRevert.get(0).targetNodeId(), "撤销误覆写后必须精确恢复原真相 Dr. A");
        assertTrue(engine.getColdStandbyArchiveCount() >= 1, "误边必须转入三级冷备可逆环（严禁物理硬删除）");
    }

    @Test
    void causalTopologicalProjectionPreservesOrderBreaksCyclesAndVerifiesTamperProofMac() {
        BiTemporalHippocampalGraphEngine engine = new BiTemporalHippocampalGraphEngine("p81-topo");
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        BiTemporalCausalEvolutionRetriever retriever = new BiTemporalCausalEvolutionRetriever(
                engine, resolver, new TemporalAnchorQueryParser(), "p81-topo"
        );

        engine.upsertVertex(new HippocampalVertex("n1", "Event 1: Tariff Hike", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Tariff Hike")));
        engine.upsertVertex(new HippocampalVertex("n2", "Event 2: Port Delay", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Port Delay")));
        engine.upsertVertex(new HippocampalVertex("n3", "Event 3: Stockout", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Stockout")));

        BiTemporalHyperEdge e1 = engine.ingestCausalFactEdge(
                "n1", "n2", "CAUSES", PredicateCardinality.COUNTABLE_EVENT,
                unitVec("Tariff causes port delay"), utcMs(2025, 1, 10), 100L, "d1", null
        );
        BiTemporalHyperEdge e2 = engine.ingestCausalFactEdge(
                "n2", "n3", "CAUSES", PredicateCardinality.COUNTABLE_EVENT,
                unitVec("Port delay causes stockout"), utcMs(2025, 2, 10), 200L, "d2", e1.edgeId()
        );
        // 故意注入一条形成因果环的反向边 e3，测试 Kahn 破环不死循环
        engine.ingestCausalFactEdge(
                "n3", "n1", "RETRIGGERED", PredicateCardinality.COUNTABLE_EVENT,
                unitVec("Stockout triggers tariff review"), utcMs(2025, 3, 10), 300L, "d3", e2.edgeId()
        );

        BiTemporalCausalRetrievalReceipt receipt = retriever.retrieveAt(
                "q-causal",
                "Tariff Hike Port Delay Stockout",
                TemporalAnchorSpec.evolutionWindow(utcMs(2025, 1, 1), utcMs(2025, 4, 1), 1000L, List.of()),
                10
        );
        List<CausalEvolutionStep> chain = receipt.orderedCausalChain();
        assertEquals(3, chain.size());
        assertEquals(e1.edgeId(), chain.get(0).edgeId(), "Kahn 时间升序拓扑必须把最早因果起点排在第1步");
        assertEquals(e2.edgeId(), chain.get(1).edgeId(), "下游因果边必须严格排在因果前驱之后");
        assertTrue(receipt.verifyMac("p81-topo"));

        // 篡改凭单字段后 MAC 常量时间验证必返回 false
        BiTemporalCausalRetrievalReceipt tampered = new BiTemporalCausalRetrievalReceipt(
                receipt.receiptId(),
                "tampered-query-id",
                receipt.anchorSpec(),
                receipt.pprReceipt(),
                receipt.orderedCausalChain(),
                receipt.datasetView(),
                receipt.recalledErrorBookWarnings(),
                receipt.elapsedMicros(),
                receipt.provenanceMac()
        );
        assertFalse(tampered.verifyMac("p81-topo"), "篡改任意字段后 MAC 验签必须失败");

        // 校验 SearchKnowledgeTool 新工厂方法可端到端调用双时态演化检索
        SearchKnowledgeTool tool = SearchKnowledgeTool.withBiTemporalCausalRetriever(
                "k1", "供应链因果库", "", retriever
        );
        knowledgeQuery q = new knowledgeQuery();
        q.setQuery("From 2025-01-01 to 2025-04-01 Tariff Hike Stockout");
        String out = tool.apply(q, null);
        assertNotNull(out);
        assertTrue(out.contains("EVOLUTION_WINDOW") && out.contains("Event 1: Tariff Hike"));
    }
}
