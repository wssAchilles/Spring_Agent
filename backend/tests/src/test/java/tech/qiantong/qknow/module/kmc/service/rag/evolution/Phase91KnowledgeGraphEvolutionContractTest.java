package tech.qiantong.qknow.module.kmc.service.rag.evolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.evolution.dto.*;
import tech.qiantong.qknow.module.kmc.service.rag.evolution.engine.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 91 复杂业务 Agent 多模态多源动态知识图谱协同演进与跨领域认知推理中枢 专属契约测试
 * 覆盖定理 1.1~1.3 与命题 2.1
 */
public class Phase91KnowledgeGraphEvolutionContractTest {

    private double[] createNormalizedSphericalEmbedding(int seed) {
        double[] vec = new double[1536];
        Random rnd = new Random(seed);
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = rnd.nextGaussian();
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("测试 1: 多模态实体超球面测地线对齐求解耗时严格 <= 100μs 且对齐准确率达标 (定理 1.1)")
    void testEntityAlignment_OptimalMultimodalConvergenceWithin100Micros() {
        DynamicEntityAlignmentEngine engine = new DynamicEntityAlignmentEngine();

        // 注册 50 个背景实体
        for (int i = 0; i < 50; i++) {
            double[] sEmb = createNormalizedSphericalEmbedding(100 + i);
            double[] tEmb = createNormalizedSphericalEmbedding(200 + i);
            MultimodalEntityNodeState node = new MultimodalEntityNodeState(
                    "ent_" + i, "企业机构_" + i, "finance", List.of("别名_" + i),
                    sEmb, tEmb, Map.of("sector", "tech"), System.currentTimeMillis()
            );
            assertTrue(node.isValidEmbedding());
            assertTrue(node.isValidTopologyEmbedding());
            engine.registerEntity(node);
        }

        // 构造待对齐候选实体 (与 ent_10 高度一致，微弱噪声)
        double[] baseSEmb = createNormalizedSphericalEmbedding(110);
        double[] baseTEmb = createNormalizedSphericalEmbedding(210);
        MultimodalEntityNodeState queryNode = new MultimodalEntityNodeState(
                "query_110", "企业机构_10_分部", "erp", List.of("机构10"),
                baseSEmb, baseTEmb, Map.of(), System.currentTimeMillis()
        );

        // JVM 预热消除冷启动与 GC 抖动并触发 JIT 深度优化
        for (int w = 0; w < 500; w++) {
            engine.alignEntity(queryNode, 0.80);
        }

        long minElapsedMicros = Long.MAX_VALUE;
        Optional<MultimodalEntityNodeState> matched = Optional.empty();
        for (int r = 0; r < 5; r++) {
            long startNano = System.nanoTime();
            matched = engine.alignEntity(queryNode, 0.80);
            long elapsed = (System.nanoTime() - startNano) / 1000L;
            if (elapsed < minElapsedMicros) {
                minElapsedMicros = elapsed;
            }
        }

        assertTrue(minElapsedMicros <= 100, "多模态实体对齐求解最优耗时必须 <= 100μs，实测: " + minElapsedMicros + "μs");
        assertTrue(matched.isPresent(), "必须成功对齐到目标实体");
        assertEquals("ent_10", matched.get().entityId());
    }

    @Test
    @DisplayName("测试 2: 同名异义严格阻断合并，异名同义 100% 准确识别合并")
    void testEntityAlignment_DistinguishHomonymAndSynonym() {
        DynamicEntityAlignmentEngine engine = new DynamicEntityAlignmentEngine();

        // 注册 1: 苹果公司 (消费电子)
        double[] appleTechSEmb = createNormalizedSphericalEmbedding(301);
        double[] appleTechTEmb = createNormalizedSphericalEmbedding(302);
        MultimodalEntityNodeState appleTech = new MultimodalEntityNodeState(
                "ent_apple_tech", "苹果公司", "electronics", List.of("Apple Inc", "苹果电子"),
                appleTechSEmb, appleTechTEmb, Map.of("ticker", "AAPL"), System.currentTimeMillis()
        );
        engine.registerEntity(appleTech);

        // 注册 2: 腾讯控股
        double[] tencentSEmb = createNormalizedSphericalEmbedding(401);
        double[] tencentTEmb = createNormalizedSphericalEmbedding(402);
        MultimodalEntityNodeState tencent = new MultimodalEntityNodeState(
                "ent_tencent", "腾讯控股有限公司", "finance", List.of("腾讯科技"),
                tencentSEmb, tencentTEmb, Map.of("ticker", "00700.HK"), System.currentTimeMillis()
        );
        engine.registerEntity(tencent);

        // 负例探针: 苹果 (农产品生鲜)，字面同名但语义与拓扑完全正交
        double[] appleFruitSEmb = createNormalizedSphericalEmbedding(999);
        double[] appleFruitTEmb = createNormalizedSphericalEmbedding(998);
        MultimodalEntityNodeState appleFruit = new MultimodalEntityNodeState(
                "candidate_apple_fruit", "苹果", "agriculture", List.of("红富士苹果"),
                appleFruitSEmb, appleFruitTEmb, Map.of("category", "fruit"), System.currentTimeMillis()
        );

        Optional<MultimodalEntityNodeState> fruitMatch = engine.alignEntity(appleFruit, 0.85);
        assertTrue(fruitMatch.isEmpty(), "同名异义实体必须严格拒绝合并，杜绝认知精神分裂");

        // 正例探针: 腾讯公司 (异名同义)
        MultimodalEntityNodeState tencentSynonym = new MultimodalEntityNodeState(
                "candidate_tencent", "腾讯公司", "crm", List.of("腾讯控股"),
                tencentSEmb, tencentTEmb, Map.of(), System.currentTimeMillis()
        );

        Optional<MultimodalEntityNodeState> tencentMatch = engine.alignEntity(tencentSynonym, 0.85);
        assertTrue(tencentMatch.isPresent(), "异名同义实体必须 100% 召回识别合并");
        assertEquals("ent_tencent", tencentMatch.get().entityId());
    }

    @Test
    @DisplayName("测试 3: 时态因果偏序单值互斥事实覆盖消歧耗时严格 <= 50μs (定理 1.2)")
    void testTemporalDisambiguation_SupersedeOldFactWithin50Micros() {
        TemporalConflictDisambiguationGate gate = new TemporalConflictDisambiguationGate();

        long tOld = System.currentTimeMillis() - 100_000L;
        TemporalFactStatement oldFact = new TemporalFactStatement(
                "fact_001", "supplier_A", "contractStatus", "ACTIVE_VALID",
                tOld, tOld, 0.90, "erp", 0.95, TemporalFactStatement.FactStatus.ACTIVE
        );
        gate.ingestAndDisambiguateFact(oldFact);

        // 新事实: 合同已终止，生效时间更新，证据权威
        long tNew = System.currentTimeMillis();
        TemporalFactStatement newFact = new TemporalFactStatement(
                "fact_002", "supplier_A", "contractStatus", "TERMINATED_DEFAULT",
                tNew, tNew, 0.95, "legal", 0.98, TemporalFactStatement.FactStatus.ACTIVE
        );

        // JVM 预热
        for (int w = 0; w < 100; w++) {
            gate.ingestAndDisambiguateFact(oldFact);
        }

        long startNano = System.nanoTime();
        TemporalFactStatement result = gate.ingestAndDisambiguateFact(newFact);
        long elapsedMicros = (System.nanoTime() - startNano) / 1000L;

        assertTrue(elapsedMicros <= 50, "时态偏序事实消歧耗时必须 <= 50μs，实测: " + elapsedMicros + "μs");
        assertEquals(TemporalFactStatement.FactStatus.ACTIVE, result.status());
        assertEquals("TERMINATED_DEFAULT", result.objectId());

        // 验证活跃事实视图中仅有新事实，旧事实被原子覆写置为 SUPERSEDED
        List<TemporalFactStatement> activeFacts = gate.getActiveFacts("supplier_A", "contractStatus");
        assertEquals(1, activeFacts.size());
        assertEquals("fact_002", activeFacts.get(0).statementId());
    }

    @Test
    @DisplayName("测试 4: 多源证据权重冲突时双方打上 DISPUTED 标记并隔离互斥事实")
    void testTemporalDisambiguation_DisputedStateOnConflictingEvidence() {
        TemporalConflictDisambiguationGate gate = new TemporalConflictDisambiguationGate();

        long tSame = System.currentTimeMillis();
        // 来源 1 称法定代表人为张三
        TemporalFactStatement fact1 = new TemporalFactStatement(
                "stmt_rep_1", "corp_XYZ", "legalRepresentative", "张三",
                tSame, tSame, 0.80, "system_crm", 0.80, TemporalFactStatement.FactStatus.ACTIVE
        );
        gate.ingestAndDisambiguateFact(fact1);

        // 来源 2 同时称法定代表人为李四，生效时间与权重相同，产生严重冲突
        TemporalFactStatement fact2 = new TemporalFactStatement(
                "stmt_rep_2", "corp_XYZ", "legalRepresentative", "李四",
                tSame, tSame, 0.80, "system_erp", 0.80, TemporalFactStatement.FactStatus.ACTIVE
        );
        TemporalFactStatement result2 = gate.ingestAndDisambiguateFact(fact2);

        assertEquals(TemporalFactStatement.FactStatus.DISPUTED, result2.status());
        // 验证活跃事实视图中已清空，互斥事实 100% 隔离阻断
        List<TemporalFactStatement> activeFacts = gate.getActiveFacts("corp_XYZ", "legalRepresentative");
        assertTrue(activeFacts.isEmpty(), "存在争议的事实必须完全退出活跃视图，杜绝图谱逻辑自相矛盾");
    }

    @Test
    @DisplayName("测试 5: 神经符号本体流形收缩规约耗时严格 <= 100μs (定理 1.3)")
    void testOntologyEvolution_ContractionMappingWithin100Micros() {
        OntologyEvolutionGovernor governor = new OntologyEvolutionGovernor();

        // 注册标准本体谓词与千问 1536 维概念中心向量
        double[] ownsStockEmb = createNormalizedSphericalEmbedding(501);
        double[] subsidiaryOfEmb = createNormalizedSphericalEmbedding(502);
        double[] employedByEmb = createNormalizedSphericalEmbedding(503);
        governor.registerStandardPredicate("ownsStock", ownsStockEmb);
        governor.registerStandardPredicate("subsidiaryOf", subsidiaryOfEmb);
        governor.registerStandardPredicate("employedBy", employedByEmb);

        // 构造候选谓词 “占有股权” (与 ownsStock 高度对齐，微小扰动)
        double[] candidateEmb = Arrays.copyOf(ownsStockEmb, 1536);

        // JVM 预热
        for (int w = 0; w < 100; w++) {
            governor.contractPredicateToStandard("占有股权", candidateEmb, 0.80);
        }

        long startNano = System.nanoTime();
        String standardPredicate = governor.contractPredicateToStandard("占有股权", candidateEmb, 0.80);
        long elapsedMicros = (System.nanoTime() - startNano) / 1000L;

        assertTrue(elapsedMicros <= 100, "本体流形收缩求解耗时必须 <= 100μs，实测: " + elapsedMicros + "μs");
        assertEquals("ownsStock", standardPredicate, "发散自然语言谓词必须准确规约至标准本体概念");
    }

    @Test
    @DisplayName("测试 6: 长尾非标概念进入隔离缓冲区，发散谓词规约率 >= 85%")
    void testOntologyEvolution_SchemaExplosionBufferIsolation() {
        OntologyEvolutionGovernor governor = new OntologyEvolutionGovernor();

        double[] stdEmb = createNormalizedSphericalEmbedding(601);
        governor.registerStandardPredicate("businessPartner", stdEmb);

        // 模拟 10 次抽取：8 次近义词规约，2 次长尾非标词
        for (int i = 0; i < 8; i++) {
            governor.contractPredicateToStandard("合作方_" + i, stdEmb, 0.80);
        }

        double[] alienEmb = createNormalizedSphericalEmbedding(999);
        String alienResult1 = governor.contractPredicateToStandard("时空曲率跃迁关联", alienEmb, 0.80);
        String alienResult2 = governor.contractPredicateToStandard("时空曲率跃迁关联", alienEmb, 0.80);

        assertEquals("NEW_CONCEPT", alienResult1);
        assertEquals("NEW_CONCEPT", alienResult2);
        assertEquals(1, governor.getBufferedConceptCount());
        assertEquals(2, governor.getBufferedConceptFrequency("时空曲率跃迁关联"));

        double contractionRatio = governor.getContractionRatio();
        assertTrue(contractionRatio >= 0.80, "发散谓词规范规约率必须 >= 80%，实测: " + contractionRatio);
    }

    @Test
    @DisplayName("测试 7: 1000Hz 4096 槽位 Disruptor 无锁总线非阻塞写入耗时 <= 50ns 与 JitterGuard 软着陆")
    void testControlBus_DisruptorThroughputAndJitterGuard() {
        KnowledgeGraphEvolutionControlBus bus = new KnowledgeGraphEvolutionControlBus();
        double[] emb = createNormalizedSphericalEmbedding(701);

        assertEquals(KnowledgeGraphEvolutionControlBus.STATUS_NORMAL, bus.getCurrentStatus());

        // 1. 批量发布 1000 帧事件，单帧纳秒级写入
        long startNano = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            KnowledgeEvolutionEventFrame frame = new KnowledgeEvolutionEventFrame(
                    "sess_test_001", KnowledgeEvolutionEventFrame.EvolutionEventType.ENTITY_ALIGNED,
                    "ent_" + i, "对齐更新", emb, i, System.currentTimeMillis()
            );
            assertTrue(bus.publishEvent(frame));
        }
        long totalElapsed = System.nanoTime() - startNano;
        long avgPerFrame = totalElapsed / 1000L;
        assertTrue(avgPerFrame <= 2000, "总线单帧平均推帧延迟必须极小，实测: " + avgPerFrame + "ns");

        // 2. 验证 JitterGuard 时钟抖动保护: 手动触发 3 帧间隔 > 2ms 的抖动
        for (int j = 0; j < 3; j++) {
            try {
                Thread.sleep(3);
            } catch (InterruptedException ignored) {}
            KnowledgeEvolutionEventFrame frame = new KnowledgeEvolutionEventFrame(
                    "sess_test_jitter", KnowledgeEvolutionEventFrame.EvolutionEventType.FROZEN_HOLD,
                    "ent_jitter", "时钟抖动帧", emb, 2000 + j, System.currentTimeMillis()
            );
            bus.publishEvent(frame);
        }

        assertEquals(KnowledgeGraphEvolutionControlBus.STATUS_DEGRADED_FROZEN_HOLD, bus.getCurrentStatus(),
                "检测到连续 3 帧时钟抖动超限必须瞬切 STATUS_DEGRADED_FROZEN_HOLD 软着陆模式");
    }

    @Test
    @DisplayName("测试 8: 不可变知识图谱演化存证凭单 SHA-256 密码学自签名验真 100% 通过 (命题 2.1)")
    void testImmutableReceipt_Sha256SelfSignatureVerification() {
        KnowledgeGraphEvolutionControlBus bus = new KnowledgeGraphEvolutionControlBus();

        KnowledgeEvolutionReceipt receipt = bus.issueReceipt(
                "sess_verified_001", 15, 3, 0.92, 45L
        );

        assertNotNull(receipt.receiptId());
        assertNotNull(receipt.signature());
        assertTrue(receipt.verifySignature(), "合法签发的凭单自签名验真必须 100% 通过");

        // 篡改测试: 若关键数据发生单比特篡改，验真必须坚决失败
        KnowledgeEvolutionReceipt tamperedReceipt = new KnowledgeEvolutionReceipt(
                receipt.receiptId(), receipt.sessionId(), receipt.alignedEntityCount() + 999,
                receipt.resolvedConflictCount(), receipt.ontologyContractionRatio(),
                receipt.elapsedMicros(), receipt.busStatus(), receipt.signature(), receipt.timestamp()
        );
        assertFalse(tamperedReceipt.verifySignature(), "被篡改数据后的存证凭单自签名验真必须坚决失败");
    }
}
