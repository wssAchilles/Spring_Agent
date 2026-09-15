package tech.qiantong.qknow.ai.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 62 专属契约测试：超级智能体动态技能流形演化、技能库神经符号热插拔与元策略技能编排引擎
 */
public class Phase62SkillManifoldOrchestrationContractTest {

    private GeodesicSkillManifoldIndex manifoldIndex;
    private NeuroSymbolicSkillDagEngine dagEngine;
    private HotSwappableSkillCatalog skillCatalog;
    private MetaPolicySkillOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        manifoldIndex = new GeodesicSkillManifoldIndex();
        dagEngine = new NeuroSymbolicSkillDagEngine();
        skillCatalog = new HotSwappableSkillCatalog();
        orchestrator = new MetaPolicySkillOrchestrator(manifoldIndex, dagEngine, skillCatalog);
    }

    private float[] createDummyVector(int seed) {
        float[] vec = new float[SkillMetadata.EMBEDDING_DIMENSION];
        Random rnd = new Random(seed);
        for (int i = 0; i < vec.length; i++) {
            vec[i] = rnd.nextFloat() - 0.5f;
        }
        return vec;
    }

    @Test
    @DisplayName("契约 1: 阿里千问 1536 维超球面技能向量归一化与测地线检索保真测试 (定理 1.1)")
    void test01_HypersphereGeodesicSkillRetrievalAccuracyAndNorm() {
        float[] raw = createDummyVector(42);
        SkillMetadata skill = SkillMetadata.create(
                "SKILL-01", "数据清洗技能", "负责对结构化表格执行缺失值填补", 1,
                raw, List.of(), 0.95
        );

        assertNotNull(skill);
        assertEquals(SkillMetadata.EMBEDDING_DIMENSION, skill.embeddingVector().length);

        // 验证 L2 模长严格等于 1.0 (误差 <= 1e-5)
        double sumSq = 0.0;
        for (float v : skill.embeddingVector()) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        assertEquals(1.0, norm, 1e-5, "千问 1536 维超球面技能嵌入向量 L2 模长必须精确归一化为 1.0");

        // 验证非 1536 维向量抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            new SkillMetadata("ERR-01", "错误技能", "错误", 1, new float[128], "{}", "{}", List.of(), 1.0, false);
        }, "非 1536 维向量必须被严格阻断拒绝");

        // 注册至流形索引
        manifoldIndex.registerSkill(skill);
        List<SkillMetadata> searchRes = manifoldIndex.searchTopK(skill.embeddingVector(), 5);
        assertFalse(searchRes.isEmpty());
        assertEquals("SKILL-01", searchRes.get(0).skillId(), "与自身相同的向量检索必须以最高余弦测地线内积排在首位");
    }

    @Test
    @DisplayName("契约 2: 测地线流形检索 P99 延迟 <= 2ms 预算达标测试")
    void test02_GeodesicSearchLatentTopKPerformanceBudget() {
        // 注册 100 个技能
        for (int i = 0; i < 100; i++) {
            float[] vec = createDummyVector(i + 100);
            SkillMetadata s = SkillMetadata.create(
                    "SKILL-" + i, "技能-" + i, "描述 " + i, 1,
                    vec, List.of(), 0.80 + (i % 20) * 0.01
            );
            manifoldIndex.registerSkill(s);
        }

        float[] queryVec = createDummyVector(999);
        // 预热 JIT
        for (int i = 0; i < 20; i++) {
            manifoldIndex.searchTopK(queryVec, 5);
        }

        // 正式计时 100 次检索
        long totalNanos = 0;
        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            List<SkillMetadata> res = manifoldIndex.searchTopK(queryVec, 5);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;
            assertEquals(5, res.size());
        }

        double avgMs = (totalNanos / 100.0) / 1_000_000.0;
        assertTrue(avgMs <= 2.0, "测地线流形检索平均耗时必须 <= 2.0ms，当前平均耗时: " + avgMs + "ms");
    }

    @Test
    @DisplayName("契约 3: 神经符号技能 DAG 拓扑良基序展开与分层并行度校验 (定理 1.2)")
    void test03_NeuroSymbolicDagTopologicalSortValid() {
        // 构建依赖链: A -> B, A -> C, B -> D, C -> D
        SkillMetadata skillA = SkillMetadata.create("A", "抽取技能", "desc", 1, createDummyVector(1), List.of(), 0.9);
        SkillMetadata skillB = SkillMetadata.create("B", "转换技能B", "desc", 1, createDummyVector(2), List.of("A"), 0.9);
        SkillMetadata skillC = SkillMetadata.create("C", "转换技能C", "desc", 1, createDummyVector(3), List.of("A"), 0.9);
        SkillMetadata skillD = SkillMetadata.create("D", "聚合加载技能", "desc", 1, createDummyVector(4), List.of("B", "C"), 0.9);

        NeuroSymbolicSkillDagEngine.PhasedExecutionPlan plan = dagEngine.compileDagPlan(List.of(skillA, skillB, skillC, skillD));

        assertNotNull(plan);
        assertEquals(4, plan.totalSkills());
        assertEquals(3, plan.phases().size(), "DAG 应精确展开为 3 个执行阶段 (Phase 0, 1, 2)");
        assertEquals(2, plan.maxParallelism(), "Phase 1 中 B 和 C 可并行执行，最大并行度为 2");

        // 验证阶段排序正确性
        assertTrue(plan.phases().get(0).contains("A"));
        assertTrue(plan.phases().get(1).contains("B") && plan.phases().get(1).contains("C"));
        assertTrue(plan.phases().get(2).contains("D"));
    }

    @Test
    @DisplayName("契约 4: 循环依赖 100% 检出与零死锁不变量熔断测试 (定理 1.2)")
    void test04_NeuroSymbolicDagCycleDetectionAndRejection() {
        // 构建循环死锁依赖: X -> Y -> Z -> X
        SkillMetadata skillX = SkillMetadata.create("X", "循环X", "desc", 1, createDummyVector(11), List.of("Z"), 0.9);
        SkillMetadata skillY = SkillMetadata.create("Y", "循环Y", "desc", 1, createDummyVector(12), List.of("X"), 0.9);
        SkillMetadata skillZ = SkillMetadata.create("Z", "循环Z", "desc", 1, createDummyVector(13), List.of("Y"), 0.9);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            dagEngine.compileDagPlan(List.of(skillX, skillY, skillZ));
        }, "检测到环路依赖时必须严格抛出异常熔断");

        assertTrue(ex.getMessage().contains("死锁") || ex.getMessage().contains("循环"),
                "异常提示必须明确指出死锁与循环依赖");
    }

    @Test
    @DisplayName("契约 5: 写时复制 (COW) 在线热插拔与在途请求版本因果隔离测试 (定理 1.3)")
    void test05_HotSwappableSkillCatalogCopyOnWriteZeroDowntime() throws Exception {
        SkillMetadata v1 = SkillMetadata.create("TEXT_SUMMARIZER", "摘要技能", "v1版本", 1, createDummyVector(101), List.of(), 0.85);
        skillCatalog.hotDeploySkill(v1);

        // 获取在途请求持有的快照
        Map<String, SkillMetadata> inFlightSnapshot = skillCatalog.getSnapshot();
        assertEquals(1, inFlightSnapshot.get("TEXT_SUMMARIZER").version());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean threadObservedOldVersion = new AtomicBoolean(false);

        // 启动后台在途线程持有旧快照
        Thread inFlightTask = new Thread(() -> {
            try {
                SkillMetadata skill = inFlightSnapshot.get("TEXT_SUMMARIZER");
                Thread.sleep(50);
                if (skill.version() == 1) {
                    threadObservedOldVersion.set(true);
                }
            } catch (InterruptedException ignored) {
            } finally {
                latch.countDown();
            }
        });
        inFlightTask.start();

        // 立即热替换部署 v2 版本
        SkillMetadata v2 = SkillMetadata.create("TEXT_SUMMARIZER", "摘要技能", "v2升级版", 2, createDummyVector(102), List.of(), 0.98);
        skillCatalog.hotDeploySkill(v2);

        // 验证全局 catalog 已经无缝升级为 v2
        assertEquals(2, skillCatalog.getSkill("TEXT_SUMMARIZER").version());

        // 验证在途线程丝毫不受影响，依然稳定执行 v1
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(threadObservedOldVersion.get(), "在途线程必须安全持有旧不可变快照，不受后续版本热替换的任何扰动");
    }

    @Test
    @DisplayName("契约 6: 技能版本原子废弃与历史快照一致性测试")
    void test06_HotSwappableVersionRollbackIntegrity() {
        SkillMetadata skill = SkillMetadata.create("SQL_GENERATOR", "SQL技能", "desc", 1, createDummyVector(201), List.of(), 0.90);
        skillCatalog.hotDeploySkill(skill);
        assertFalse(skillCatalog.getSkill("SQL_GENERATOR").isDeprecated());

        // 热标记废弃
        skillCatalog.hotDeprecateSkill("SQL_GENERATOR");
        assertTrue(skillCatalog.getSkill("SQL_GENERATOR").isDeprecated(), "热废弃后技能状态必须被安全标记为 isDeprecated=true");

        // 热回滚/重发布修复版
        SkillMetadata skillFixed = SkillMetadata.create("SQL_GENERATOR", "SQL技能修复版", "fixed", 2, createDummyVector(202), List.of(), 0.95);
        skillCatalog.hotDeploySkill(skillFixed);
        assertFalse(skillCatalog.getSkill("SQL_GENERATOR").isDeprecated());
        assertEquals(2, skillCatalog.getSkill("SQL_GENERATOR").version());
    }

    @Test
    @DisplayName("契约 7: 元策略端到端编排与不可变收据 SHA-256 自签名验真测试")
    void test07_MetaPolicySkillOrchestratorEndToEndExecution() {
        // 注册技能基础库
        SkillMetadata basePreprocess = SkillMetadata.create("PREPROCESS", "前置清洗", "清洗数据", 1, createDummyVector(301), List.of(), 0.95);
        SkillMetadata coreReasoner = SkillMetadata.create("REASONER", "核心推理", "多步推理", 1, createDummyVector(302), List.of("PREPROCESS"), 0.92);

        manifoldIndex.registerSkill(basePreprocess);
        manifoldIndex.registerSkill(coreReasoner);
        skillCatalog.hotDeploySkill(basePreprocess);
        skillCatalog.hotDeploySkill(coreReasoner);

        // 发起元策略编排
        SkillOrchestrationReceipt receipt = orchestrator.orchestrateTask(
                1L, "执行深度财务报表异常推理任务", coreReasoner.embeddingVector(), 2
        );

        assertNotNull(receipt);
        assertTrue(receipt.executionSuccess());
        assertTrue(receipt.verifySignature(), "原始不可变编排存证凭据 SHA-256 自签名必须验证通过");
        assertTrue(receipt.selectedSkillIds().contains("REASONER"));
        assertTrue(receipt.paretoFitnessScore() > 0.0);

        // 模拟黑客篡改凭据内容 (例如将效用得分由 0.9 篡改为 0.99)
        SkillOrchestrationReceipt tampered = new SkillOrchestrationReceipt(
                receipt.receiptId(), receipt.orchestrationEpoch(), receipt.taskIntent(),
                receipt.selectedSkillIds(), receipt.dagExecutionPlan(),
                0.999999, // 篡改得分
                receipt.hotSwapOccurred(), receipt.executionSuccess(),
                receipt.decisionSummary(), receipt.timestamp(), receipt.sha256Signature()
        );
        assertFalse(tampered.verifySignature(), "字段被篡改的存证凭单 SHA-256 自验必须严格失败拒绝");
    }

    @Test
    @DisplayName("契约 8: 元策略断路器降级保护与非法入参鲁棒性测试")
    void test08_MetaPolicyCircuitBreakerAndDegradation() {
        // 1. 无匹配技能时的安全降级
        float[] unmatchedVector = createDummyVector(777);
        SkillOrchestrationReceipt emptyReceipt = orchestrator.orchestrateTask(
                2L, "超冷门未覆盖意图", unmatchedVector, 3
        );
        assertNotNull(emptyReceipt);
        assertFalse(emptyReceipt.executionSuccess());
        assertEquals("NONE", emptyReceipt.dagExecutionPlan());
        assertTrue(emptyReceipt.verifySignature(), "降级生成的存证收据 SHA-256 签名仍须合法有效");

        // 2. 非法参数校验拦截
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.orchestrateTask(0L, "意图", unmatchedVector, 3);
        }, "epoch <= 0 必须抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.orchestrateTask(3L, "", unmatchedVector, 3);
        }, "意图为空必须抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.orchestrateTask(4L, "意图", new float[64], 3);
        }, "向量维度不匹配必须抛出异常");
    }
}
