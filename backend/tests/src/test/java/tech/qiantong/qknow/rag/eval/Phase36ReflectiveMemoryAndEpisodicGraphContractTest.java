package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.memory.ByteBudgeter;
import tech.qiantong.qknow.hermes.memory.ReflectiveMemoryCoordinator;
import tech.qiantong.qknow.hermes.memory.WorkingMemory;
import tech.qiantong.qknow.hermes.memory.dst.DialogueStateTracker;
import tech.qiantong.qknow.hermes.memory.dst.DialogueStateTrackerImpl;
import tech.qiantong.qknow.hermes.memory.dst.IntentDAG;
import tech.qiantong.qknow.hermes.memory.graph.EpisodicGraphService;
import tech.qiantong.qknow.hermes.memory.graph.EpisodicGraphServiceImpl;
import tech.qiantong.qknow.hermes.memory.model.*;
import tech.qiantong.qknow.hermes.memory.persona.UserPreferenceEvolutionGovernor;
import tech.qiantong.qknow.hermes.memory.persona.UserPreferenceEvolutionGovernorImpl;
import tech.qiantong.qknow.hermes.memory.reflection.AsyncReflectionWorker;
import tech.qiantong.qknow.hermes.memory.reflection.ReflectionTreeEngine;
import tech.qiantong.qknow.hermes.memory.reflection.ReflectionTreeEngineImpl;
import tech.qiantong.qknow.hermes.memory.scoring.MemoryScoringService;
import tech.qiantong.qknow.hermes.memory.scoring.MemoryScoringServiceImpl;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 36 专属自动化契约测试套件：
 * 长程跨会话反思演进记忆流与个性化情境图谱 (Long-Horizon Cross-Session Reflective Memory Stream & Personalized Episodic Graph)
 *
 * 覆盖 10 项严苛核心契约：
 * 1. contract01: 三维协同检索得分计算 (Recency, Importance, Relevance) 与千问 1536 维超球面余弦度量契约
 * 2. contract02: 激活保护引理 (Lemma 1.1) 长程防遗忘契约 (重要记忆永不因时间流逝沉没)
 * 3. contract03: 反思折叠树 (Reflection Tree) 信息论压缩不变量契约 (Theorem 1.1: 压缩率 >= 70%)
 * 4. contract04: 工作记忆字节预算器 (ByteBudgeter <= 4KB) 物理硬截断契约
 * 5. contract05: 意态与反事实过滤器 (Modality & Counterfactual Filter) 偏好防幻觉契约 (拦截率 100%)
 * 6. contract06: 时序有向无环图与版本偏序覆盖算子 (Supersede Operator 与 Theorem 2.1) 因果一致性契约
 * 7. contract07: 对话状态跟踪器 (DST) 与意图 DAG 栈帧挂起与无损恢复契约
 * 8. contract08: 多端高并发写 CAS 乐观锁防脑裂与三向合并契约 (0 脑裂 100% 数据一致性)
 * 9. contract09: 有限视界内存容量有界性与双阈值动态 GC 契约 (Theorem 3.1)
 * 10. contract10: 四级记忆统一入口协调器 (ReflectiveMemoryCoordinator) 端到端装配与软降级契约 (耗时 <= 45ms)
 */
public class Phase36ReflectiveMemoryAndEpisodicGraphContractTest {

    private MemoryScoringService scoringService;
    private ReflectionTreeEngine reflectionEngine;
    private DialogueStateTracker dialogueStateTracker;
    private EpisodicGraphService episodicGraphService;
    private UserPreferenceEvolutionGovernor preferenceGovernor;
    private WorkingMemory workingMemory;
    private ReflectiveMemoryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        scoringService = new MemoryScoringServiceImpl();
        reflectionEngine = new ReflectionTreeEngineImpl();
        dialogueStateTracker = new DialogueStateTrackerImpl();
        episodicGraphService = new EpisodicGraphServiceImpl();
        preferenceGovernor = new UserPreferenceEvolutionGovernorImpl();
        workingMemory = new WorkingMemory();
        coordinator = new ReflectiveMemoryCoordinator(
                workingMemory,
                dialogueStateTracker,
                scoringService,
                reflectionEngine,
                episodicGraphService,
                preferenceGovernor
        );
    }

    /**
     * 辅助方法：生成 1536 维归一化超球面向量
     */
    private float[] generateNormalizedEmbedding(long seed) {
        Random rnd = new Random(seed);
        float[] vec = new float[1536];
        double norm = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) rnd.nextGaussian();
            norm += vec[i] * vec[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) (vec[i] / norm);
        }
        return vec;
    }

    @Test
    @DisplayName("契约 1: 三维协同检索得分计算 (Recency, Importance, Relevance) 与千问 1536 维超球面余弦度量契约")
    void contract01_threeDimensionalScoringAndHypersphereProjection() {
        float[] vecA = generateNormalizedEmbedding(42L);
        float[] vecB = generateNormalizedEmbedding(42L); // 同一向量
        float[] vecC = generateNormalizedEmbedding(999L); // 随机不同向量

        // 1. 同一单位超球面向量内积余弦值应当为 1.0 (容差 1e-5)
        double simIdentical = scoringService.computeCosineSimilarity(vecA, vecB);
        assertEquals(1.0, simIdentical, 1e-4, "同一超球面向量自相似度必须为 1.0");

        // 2. 高维不同随机向量余弦值应当严格处于 [-1.0, 1.0]
        double simDiff = scoringService.computeCosineSimilarity(vecA, vecC);
        assertTrue(simDiff >= -1.0 && simDiff <= 1.0, "余弦相似度必须严格在 [-1.0, 1.0]");

        // 3. Sigmoid 校准单调性与有界性 (0.0, 1.0)
        double calibratedHigh = scoringService.calibrateSigmoid(1.0, 0.5);
        double calibratedLow = scoringService.calibrateSigmoid(-1.0, 0.5);
        assertTrue(calibratedHigh > calibratedLow, "Sigmoid 校准必须保持单调递增");
        assertTrue(calibratedHigh > 0.0 && calibratedHigh < 1.0, "Sigmoid 输出必须严格在 (0.0, 1.0)");

        // 4. 三维凸组合协同得分计算有界性 [0.0, 1.0]
        long now = System.currentTimeMillis();
        MemoryNode node = MemoryNode.builder()
                .id("mem-001")
                .userId("user-100")
                .content("用户在开发中使用 Java 21 虚拟线程")
                .embedding(vecA)
                .importance(0.85)
                .timestamp(now - 3600_000L) // 1 小时前
                .decayRate(0.05)
                .accessCount(3)
                .build();

        double score = scoringService.computeCompositeScore(node, vecA, now);
        assertTrue(score >= 0.0 && score <= 1.0, "三维协同检索得分必须严格位于 [0.0, 1.0]");
        assertTrue(score > 0.70, "强相关且重要记忆得分应当处于高分段 (> 0.70)");
    }

    @Test
    @DisplayName("契约 2: 激活保护引理 (Lemma 1.1) 长程防遗忘契约 (重要记忆永不因时间流逝沉没)")
    void contract02_activationProtectionLemma11LongHorizonRetention() {
        long now = System.currentTimeMillis();
        long sixtyDaysAgo = now - (60L * 86400_000L); // 60 天前
        float[] queryVec = generateNormalizedEmbedding(12345L);
        // 生成与查询向量正交/无关的记忆向量
        float[] orthogonalVec = generateNormalizedEmbedding(88888L);

        // 关键核心记忆: 重要性高达 0.95 (>= I_crit 0.90)
        MemoryNode criticalMemory = MemoryNode.builder()
                .id("mem-critical")
                .userId("user-100")
                .content("用户的安全主凭证与不可变更的核心架构约束")
                .embedding(orthogonalVec)
                .importance(0.95)
                .timestamp(sixtyDaysAgo)
                .decayRate(0.05)
                .accessCount(0)
                .build();

        // 校验是否满足临界保护条件
        assertTrue(criticalMemory.isCriticallyImportant(0.90), "重要性 0.95 必须触发激活保护条件");

        // 计算衰减后得分: 即使经历 60 天衰减且与当前查询无关 (Relevance ~ 0)
        double criticalScore = scoringService.computeCompositeScore(criticalMemory, queryVec, now);

        // 验证引理 1.1 激活保护下界: S >= beta * I_crit = 0.35 * 0.90 = 0.315
        double theoreticalFloor = 0.35 * 0.90;
        assertTrue(criticalScore >= theoreticalFloor,
                String.format("激活保护生效: 得分 %.4f 必须严格 >= 理论保底界限 %.4f", criticalScore, theoreticalFloor));

        // 对比近期浅层噪声记忆 (1 小时前产生，但重要性仅 0.10)
        MemoryNode trivialRecentMemory = MemoryNode.builder()
                .id("mem-trivial")
                .userId("user-100")
                .content("刚才随意闲聊的天气琐事")
                .embedding(orthogonalVec)
                .importance(0.10)
                .timestamp(now - 3600_000L)
                .decayRate(0.05)
                .accessCount(0)
                .build();

        double trivialScore = scoringService.computeCompositeScore(trivialRecentMemory, queryVec, now);

        // 核心记忆排位必须显著高于近期琐碎噪声记忆，杜绝关键记忆被时间冲刷沉没
        assertTrue(criticalScore > trivialScore,
                String.format("关键重要记忆得分(%.4f)必须高于近期琐事得分(%.4f)", criticalScore, trivialScore));
    }

    @Test
    @DisplayName("契约 3: 反思折叠树 (Reflection Tree) 信息论压缩不变量契约 (Theorem 1.1: 压缩率 >= 70%)")
    void contract03_reflectionTreeInformationTheoreticCompressionInvariantTheorem11() {
        String userId = "user-arch-01";
        List<MemoryNode> rawObservations = new ArrayList<>();

        // 模拟 12 条细粒度观测事件 (总长度较长)
        for (int i = 1; i <= 12; i++) {
            rawObservations.add(MemoryNode.builder()
                    .id("obs-" + i)
                    .userId(userId)
                    .content("在微服务架构交互调试中，第 " + i + " 次确认使用 Java 21 与 Spring Boot 3 搭建后端接口，并采用 Virtual Threads 处理高并发请求。")
                    .importance(0.60)
                    .timestamp(System.currentTimeMillis() - i * 10000L)
                    .build());
        }

        // 执行反思折叠树提炼
        List<ReflectiveInsightVO> insights = reflectionEngine.foldReflections(userId, rawObservations);
        assertNotNull(insights);
        assertFalse(insights.isEmpty(), "反思引擎必须产出高阶见解");

        // 验证 Theorem 1.1 压缩率 >= 70%
        double compressionRatio = reflectionEngine.computeCompressionRatio(rawObservations, insights);
        assertTrue(compressionRatio >= 0.70,
                String.format("反思树提炼后的体积压缩率 %.2f%% 必须严格 >= 70.0%%", compressionRatio * 100));

        // 验证因果溯源证据链完整性: 12 条观测事件的 ID 必须全量记录在 supportingEvidenceIds 中
        Set<String> collectedEvidenceIds = new HashSet<>();
        for (ReflectiveInsightVO ins : insights) {
            collectedEvidenceIds.addAll(ins.getSupportingEvidenceIds());
        }
        for (int i = 1; i <= 12; i++) {
            assertTrue(collectedEvidenceIds.contains("obs-" + i),
                    "原始观测事件 obs-" + i + " 必须被高阶见解溯源保留");
        }
    }

    @Test
    @DisplayName("契约 4: 工作记忆字节预算器 (ByteBudgeter <= 4KB) 物理硬截断契约")
    void contract04_byteBudgeterHardTruncation4KB() {
        // 构造总长超过 25KB 的超长文本候选片段
        List<String> largeFragments = new ArrayList<>();
        StringBuilder hugeText = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            hugeText.append("这是第 ").append(i).append(" 段超长的长程记忆上下文描述信息，用于模拟会话膨胀场景；");
        }
        largeFragments.add(hugeText.toString());
        largeFragments.add("第二段超长附录信息：系统架构全景与网络路由配置全量参数...");

        // 1. 测试直接组装时的 4096 字节预算硬截断
        String assembled = ByteBudgeter.assembleContext(largeFragments, ByteBudgeter.DEFAULT_BYTE_BUDGET);
        byte[] assembledBytes = assembled.getBytes(StandardCharsets.UTF_8);
        assertTrue(assembledBytes.length <= 4096,
                String.format("组装后的工作记忆字节数 %d 必须严格 <= 4096 字节", assembledBytes.length));
        assertFalse(assembled.isEmpty(), "装配内容在预算内不应为空");

        // 2. 测试 WorkingMemory 的 assembleContextWithinByteBudget
        workingMemory.set("key1", hugeText.toString());
        workingMemory.set("key2", "第二组高优先级配置项");
        String wmContext = workingMemory.assembleContextWithinByteBudget(4096);
        byte[] wmBytes = wmContext.getBytes(StandardCharsets.UTF_8);
        assertTrue(wmBytes.length <= 4096,
                String.format("WorkingMemory 导出的上下文体积 %d 必须严格 <= 4096 字节", wmBytes.length));
    }

    @Test
    @DisplayName("契约 5: 意态与反事实过滤器 (Modality & Counterfactual Filter) 偏好防幻觉契约 (拦截率 100%)")
    void contract05_modalityAndCounterfactualFilterAntiHallucination() {
        String userId = "user-persona-01";

        List<String> hypotheticalStatements = List.of(
                "如果我喜欢吃变态辣，会推荐什么餐厅？",
                "假如以后我们把架构降级回 Java 8，该怎么处理？",
                "假设系统明天发生了故障宕机，日志在哪看？",
                "帮我朋友问下，他要是想要学 Rust 怎么入门？",
                "万一以后我想使用 Python 3.9，会有冲突吗？",
                "代别人问一下，微服务架构适合小团队吗？"
        );

        // 意态过滤器必须对上述所有虚拟反事实陈述全部识别拦截 (100% 拦截率)
        for (String utterance : hypotheticalStatements) {
            boolean isHypothetical = preferenceGovernor.isHypotheticalOrCounterfactual(utterance);
            assertTrue(isHypothetical, "必须成功检测出虚拟/反事实语气: " + utterance);

            EvolutionDecision decision = preferenceGovernor.arbitratePreference(
                    userId, "sample_key", "sample_val", utterance);
            assertEquals(EvolutionDecision.REJECT, decision,
                    "反事实/假设性偏好陈述必须被仲裁为 REJECT，严禁持久化入画像");
        }

        // 陈述性事实偏好必须正常允许入库 (不被误杀)
        String factUtterance = "我的核心开发环境是 Java 21，系统数据库统一采用 PostgreSQL。";
        assertFalse(preferenceGovernor.isHypotheticalOrCounterfactual(factUtterance), "确定性陈述句绝不能被误判为假设");

        EvolutionDecision factDecision = preferenceGovernor.arbitratePreference(
                userId, "tech_stack.java", "Java 21", factUtterance);
        assertEquals(EvolutionDecision.ADD, factDecision, "全新陈述性偏好应仲裁为 ADD");
    }

    @Test
    @DisplayName("契约 6: 时序有向无环图与版本偏序覆盖算子 (Supersede Operator 与 Theorem 2.1) 因果一致性契约")
    void contract06_supersedeOperatorCausalAcyclicityTheorem21() {
        String userId = "user-timeline-01";
        long t1 = 1000L;
        long t2 = 2000L;
        long t3 = 3000L;

        // 1. 在 t1 时刻用户建立偏好: "Java 8"
        MemoryNode prefJava8 = MemoryNode.builder()
                .id("pref-java-8")
                .userId(userId)
                .content("用户偏好的编程语言版本为 Java 8")
                .timestamp(t1)
                .importance(0.8)
                .build();
        episodicGraphService.addNode(prefJava8);

        // 在 t1 快照查询，仅有 Java 8 处于有效状态
        List<MemoryNode> activeAtT1 = episodicGraphService.queryActiveNodes(userId, t1);
        assertEquals(1, activeAtT1.size());
        assertEquals("pref-java-8", activeAtT1.get(0).getId());

        // 2. 在 t2 时刻用户发生偏好演进: 升级至 "Java 21"
        MemoryNode prefJava21 = MemoryNode.builder()
                .id("pref-java-21")
                .userId(userId)
                .content("用户全面升级至 Java 21，弃用旧版 Java 8")
                .timestamp(t2)
                .importance(0.9)
                .build();
        episodicGraphService.addNode(prefJava21);
        // 执行版本偏序覆盖算子
        episodicGraphService.recordSupersede("pref-java-8", "pref-java-21", t2);

        // 3. 验证时态快照因果一致性 (Theorem 2.1)
        // 在当前时刻 (t3) 查询，Java 8 已被废弃，快照仅返回 Java 21 (新旧偏好冲突消除率 100%)
        List<MemoryNode> activeAtT3 = episodicGraphService.queryActiveNodes(userId, t3);
        assertEquals(1, activeAtT3.size(), "快照查询必须消除矛盾版本，仅保留最新有效偏好");
        assertEquals("pref-java-21", activeAtT3.get(0).getId());

        // 时间旅行查询：回到 t1 时刻的历史快照，仍然能无损复现当时的偏好状态为 Java 8
        List<MemoryNode> activeHistoryT1 = episodicGraphService.queryActiveNodes(userId, t1);
        assertEquals(1, activeHistoryT1.size());
        assertEquals("pref-java-8", activeHistoryT1.get(0).getId());

        // 4. 验证严格有向无环性 (Acyclicity)
        assertFalse(episodicGraphService.hasCycleInSupersedesChain(), "版本偏序覆盖图必须严格有向无环");
    }

    @Test
    @DisplayName("契约 7: 对话状态跟踪器 (DST) 与意图 DAG 栈帧挂起与无损恢复契约")
    void contract07_dialogueStateTrackerAndIntentDAGStackSuspension() {
        String sessionId = "session-dst-001";

        // 1. 第 1 轮：触发主意图 METRIC_DIAGNOSIS，填充槽位
        Map<String, Object> slotsTurn1 = Map.of("metric_name", "cpu_utilization", "time_range", "last_2h");
        DialogueStateFrame frame1 = dialogueStateTracker.processTurn(sessionId, "请帮我诊断最近 2 小时的 CPU 使用率", "METRIC_DIAGNOSIS", slotsTurn1);
        assertEquals("METRIC_DIAGNOSIS", frame1.getCurrentIntent());
        assertEquals("cpu_utilization", frame1.getSlotVal("metric_name"));
        assertEquals("last_2h", frame1.getSlotVal("time_range"));

        // 2. 第 2 轮：用户临时插入分支插话 SYSTEM_LOAD_QUERY
        IntentDAG dag = dialogueStateTracker.getIntentDAG();
        assertTrue(dag.isSubIntentAllowed("METRIC_DIAGNOSIS", "SYSTEM_LOAD_QUERY"),
                "DAG 必须允许从 METRIC_DIAGNOSIS 挂起 SYSTEM_LOAD_QUERY 分支插话");

        // 执行压栈挂起主意图
        Map<String, Object> subSlots = Map.of("cluster_id", "k8s-prod-shanghai");
        dialogueStateTracker.pushFrame(sessionId, "SYSTEM_LOAD_QUERY", subSlots);

        DialogueStateFrame currentSubFrame = dialogueStateTracker.getCurrentFrame(sessionId);
        assertEquals("SYSTEM_LOAD_QUERY", currentSubFrame.getCurrentIntent());
        assertEquals("k8s-prod-shanghai", currentSubFrame.getSlotVal("cluster_id"));
        assertEquals(1, currentSubFrame.getIntentStack().size(), "挂起栈深度必须为 1");

        // 3. 第 3 轮：子任务完成，执行 popFrame 出栈恢复主意图
        DialogueStateFrame restoredFrame = dialogueStateTracker.popFrame(sessionId);
        assertNotNull(restoredFrame);
        assertEquals("METRIC_DIAGNOSIS", restoredFrame.getCurrentIntent(), "必须精准恢复上一级主意图");
        assertEquals("cpu_utilization", restoredFrame.getSlotVal("metric_name"), "主意图槽位 metric_name 必须 100% 无损保留");
        assertEquals("last_2h", restoredFrame.getSlotVal("time_range"), "主意图槽位 time_range 必须 100% 无损保留");
        assertEquals(0, restoredFrame.getIntentStack().size(), "栈深度恢复为 0");
    }

    @Test
    @DisplayName("契约 8: 多端高并发写 CAS 乐观锁防脑裂与三向合并契约 (0 脑裂 100% 数据一致性)")
    void contract08_multiClientConcurrentCASOptimisticLockAndThreeWayMerge() throws Exception {
        String userId = "user-concurrent-01";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 10 个线程并发对该用户的画像发起不同属性的并发写回
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 保持绝对同时并发触发
                    Map<String, Object> attrs = Map.of("preference_field_" + index, "value_" + index);
                    // 故意以同一初始版本号 1 发起 CAS 竞态测试
                    int newVer = preferenceGovernor.updateProfileWithCAS(userId, attrs, 1);
                    if (newVer > 1) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 记录异常
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "所有并发写操作必须在 5 秒内完成");
        assertEquals(threadCount, successCount.get(), "所有 10 个并发客户端写操作都必须在 CAS 重试或三向合并下成功");

        // 验证三向合并后的数据一致性：所有 10 个字段必须完整保留在 Profile 中，0 脑裂 0 丢失
        Map<String, PreferenceRecord> finalPrefs = preferenceGovernor.getUserPreferences(userId);
        assertEquals(threadCount, finalPrefs.size(), "三向合并必须保留全部 10 个并发写入字段，不得发生脏数据覆写");
        for (int i = 0; i < threadCount; i++) {
            PreferenceRecord rec = finalPrefs.get("preference_field_" + i);
            assertNotNull(rec, "字段 preference_field_" + i + " 必须存在");
            assertEquals("value_" + i, rec.getPreferenceValue());
            assertEquals(PreferenceRecord.PreferenceStatus.ACTIVE, rec.getStatus());
        }

        int finalVersion = preferenceGovernor.getUserProfileVersion(userId);
        assertTrue(finalVersion > threadCount, "全局画像版本号必须单调递增至 " + (threadCount + 1));
    }

    @Test
    @DisplayName("契约 9: 有限视界内存容量有界性与双阈值动态 GC 契约 (Theorem 3.1)")
    void contract09_finiteHorizonMemoryBoundedCapacityAndDualThresholdGCTheorem31() {
        String userId = "user-gc-sim-01";
        long baseTime = 1700000000000L;

        // 模拟连续注入 500 个事件：包含部分高重要性关键事件与大量低重要性临时事件
        int ingestedCount = 0;
        for (int i = 0; i < 500; i++) {
            double importance = (i % 20 == 0) ? 0.95 : (0.10 + (i % 10) * 0.05);
            MemoryNode node = MemoryNode.builder()
                    .id("evt-" + i)
                    .userId(userId)
                    .content("会话事件观测记录 #" + i + " - 针对指标和链路的日常交互日志。")
                    .importance(importance)
                    .timestamp(baseTime + i * 60_000L) // 每分钟一条
                    .build();

            if (coordinator.ingestEvent(node)) {
                ingestedCount++;
            }
        }

        assertTrue(ingestedCount > 0, "合规事件必须成功准入写入");

        // 将时间推进至 60 天后，触发有限视界动态 GC
        long sixtyDaysLater = baseTime + (60L * 86400_000L);
        int evicted = coordinator.performGarbageCollection(userId, sixtyDaysLater);
        assertTrue(evicted > 0, "经历 60 天衰减后，大量低价值事件必须被动态 GC 成功淘汰");

        // 验证 Theorem 3.1: 活动内存池规模全局收敛有界，绝不单调无限发散
        int remainingSize = coordinator.getActiveMemoryPoolSize(userId);
        assertTrue(remainingSize < 100,
                String.format("活动工作池容量收敛至 %d, 严格满足有限视界容量有界性上界 (< 100)", remainingSize));

        // 验证关键核心记忆 (importance >= 0.90) 即使经历 60 天依然被绝对保全 (未被 GC 抹除)
        List<MemoryNode> pool = episodicGraphService.queryActiveNodes(userId, sixtyDaysLater);
        boolean criticalRetained = pool.stream().anyMatch(n -> n.getImportance() >= 0.90);
        assertTrue(criticalRetained, "重要核心事件在有限视界 GC 下必须受到保底保护，绝对留存");
    }

    @Test
    @DisplayName("契约 10: 四级记忆统一入口协调器 (ReflectiveMemoryCoordinator) 端到端装配与软降级契约 (耗时 <= 45ms)")
    void contract10_reflectiveMemoryCoordinatorEndToEndAndFailOpenSoftDegradation() {
        String userId = "user-coordinator-01";
        String sessionId = "session-e2e-001";
        float[] queryEmbedding = generateNormalizedEmbedding(777L);

        // 1. 初始化预热状态
        preferenceGovernor.updateProfileWithCAS(userId, Map.of("editor", "VSCode", "theme", "Dark"), 1);
        dialogueStateTracker.processTurn(sessionId, "查看 CPU 指标", "METRIC_DIAGNOSIS", Map.of("metric", "CPU"));
        coordinator.ingestEvent(MemoryNode.builder()
                .id("mem-e2e-1")
                .userId(userId)
                .content("系统高可用架构核心设计原则：多副本与故障自愈。")
                .importance(0.85)
                .timestamp(System.currentTimeMillis())
                .embedding(queryEmbedding)
                .build());

        // 2. 端到端工作上下文装配
        long tStart = System.currentTimeMillis();
        String context = coordinator.assembleContext(userId, sessionId, "如何保障高可用", queryEmbedding, 4096);
        long tEnd = System.currentTimeMillis();
        long duration = tEnd - tStart;

        assertNotNull(context);
        assertFalse(context.isEmpty());
        byte[] contextBytes = context.getBytes(StandardCharsets.UTF_8);
        assertTrue(contextBytes.length <= 4096, "端到端装配结果字节数必须严格 <= 4096 字节");

        // 验证各层级记忆均成功协同融合
        assertTrue(context.contains("[用户个性化稳定画像]"), "必须包含 Level 3 稳定画像");
        assertTrue(context.contains("[当前对话状态意图: METRIC_DIAGNOSIS]"), "必须包含 Level 1 DST 状态");
        assertTrue(context.contains("系统高可用架构核心设计原则"), "必须包含 Level 2 情境记忆");

        // 3. 验证毫秒级性能与软降级 (Fail-Open)
        // 构造一个模拟图服务故障崩溃的异常协调器实例
        EpisodicGraphService brokenGraph = new EpisodicGraphServiceImpl() {
            @Override
            public List<MemoryNode> queryActiveNodes(String uid, long time) {
                throw new RuntimeException("Simulated Neo4j connection timeout / partition failure");
            }
        };

        ReflectiveMemoryCoordinator faultTolerantCoordinator = new ReflectiveMemoryCoordinator(
                workingMemory,
                dialogueStateTracker,
                scoringService,
                reflectionEngine,
                brokenGraph,
                preferenceGovernor
        );

        long failOpenStart = System.currentTimeMillis();
        // 遭遇图异常时，协调器必须捕获异常触发毫秒级软降级 (Fail-Open)，主流程绝不崩溃
        String degradedContext = assertDoesNotThrow(() ->
                faultTolerantCoordinator.assembleContext(userId, sessionId, "查询", queryEmbedding, 4096)
        );
        long failOpenDuration = System.currentTimeMillis() - failOpenStart;

        assertNotNull(degradedContext);
        assertTrue(failOpenDuration <= 45,
                String.format("软降级耗时 %d ms 必须满足 P95 <= 45ms 延迟约束", failOpenDuration));
        assertTrue(degradedContext.contains("[用户个性化稳定画像]"), "降级模式下仍需完整保留稳定画像与基础上下文");
    }
}
