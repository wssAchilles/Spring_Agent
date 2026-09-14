package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.clean.SimHashEntropyPruningCleaner;
import tech.qiantong.qknow.module.kmc.service.rag.conflict.DocumentConflictResolutionService;
import tech.qiantong.qknow.module.kmc.service.rag.conflict.DocumentConflictResolutionService.ArbitrationAction;
import tech.qiantong.qknow.module.kmc.service.rag.conflict.DocumentConflictResolutionService.ArbitrationResult;
import tech.qiantong.qknow.module.kmc.service.rag.conflict.DocumentConflictResolutionService.ConflictStatus;
import tech.qiantong.qknow.module.kmc.service.rag.conflict.DocumentConflictResolutionService.SegmentMetadata;
import tech.qiantong.qknow.module.kmc.service.rag.eval.synthetic.SyntheticGoldenBootstrapEngine;
import tech.qiantong.qknow.module.kmc.service.rag.eval.synthetic.SyntheticGoldenBootstrapEngine.QuestionType;
import tech.qiantong.qknow.module.kmc.service.rag.eval.synthetic.SyntheticGoldenBootstrapEngine.SyntheticGoldenSample;
import tech.qiantong.qknow.module.kmc.service.rag.temporal.TimeAwareRetrievalFilter;
import tech.qiantong.qknow.module.kmc.service.rag.temporal.TimeAwareRetrievalFilter.CandidateSegment;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 26 专属自动化契约测试
 * 覆盖：
 * 1. 香农信息熵双阈值过滤低熵模板 (H < 2.5) 与高熵乱码 (H > 6.8)
 * 2. 64位 SimHash 近似聚类去重 (微调标点/微词下汉明距离 <= 3)
 * 3. 4段16位内存倒排桶查重吞吐量严格超过 50,000 切片/秒
 * 4. 两阶段极性与数值预检快速捕获谓词否定与关键参数翻转
 * 5. 四态状态机依据时序/版本偏序仲裁取代 (SUPERSEDE) 并绑定指针链
 * 6. 时态感知门禁在查询时间戳 t_q 处严格阻断过期与未生效切片
 * 7. 指数时间衰减算子在半衰期 tau_{1/2} 处满足严格单调下凸衰减
 * 8. 合成问答引擎完整支持 4 类问答演化与高保真元数据
 * 9. 双向自我一致性反向验证有效清洗幻觉假真值 (保真度门禁 >= 0.85)
 * 10. 端到端检索管道严格阻断被取代及冲突切片，实现 100% 冲突隔离
 */
public class Phase26KnowledgeEvolutionContractTest {

    private SimHashEntropyPruningCleaner cleaner;
    private DocumentConflictResolutionService conflictService;
    private TimeAwareRetrievalFilter temporalFilter;
    private SyntheticGoldenBootstrapEngine syntheticEngine;

    @BeforeEach
    void setUp() {
        cleaner = new SimHashEntropyPruningCleaner();
        conflictService = new DocumentConflictResolutionService();
        temporalFilter = new TimeAwareRetrievalFilter();
        syntheticEngine = new SyntheticGoldenBootstrapEngine();
    }

    @Test
    @DisplayName("Contract 01: 字符级香农信息熵双阈值校验，过滤低熵占位模板与高熵乱码")
    void contract01_shannonEntropy_prunesLowEntropyTemplatesAndHighEntropyNoise() {
        // 低熵模板文本 (高度重复字符)
        String lowEntropyText = "--------------------------------------------------------";
        double lowH = cleaner.calculateShannonEntropy(lowEntropyText);
        assertTrue(lowH < 2.5, "重复横线占位符香农熵应严格低于 2.5 bits/char，实际: " + lowH);
        assertFalse(cleaner.isValidTextByEntropy(lowEntropyText), "低熵模板应被判定为无效切片");

        // 正常自然语言切片 (典型中文政策文本)
        String normalText = "在分布式知识库配置中，KMCSegment的最大Token阈值默认设置为1024，支持混合检索融合与时态门禁。";
        double normalH = cleaner.calculateShannonEntropy(normalText);
        assertTrue(normalH >= 2.5 && normalH <= 6.8, "正常业务文本香农熵应处于 [2.5, 6.8] bits/char 范围内，实际: " + normalH);
        assertTrue(cleaner.isValidTextByEntropy(normalText), "正常业务切片应被判定为有效合格文本");

        // 高熵乱码 (随机高离散符号与不可读密文)
        StringBuilder highEntropySb = new StringBuilder();
        for (int i = 33; i <= 126; i++) {
            highEntropySb.append((char) i);
        }
        for (int i = 19968; i < 20100; i++) {
            highEntropySb.append((char) i);
        }
        double highH = cleaner.calculateShannonEntropy(highEntropySb.toString());
        assertTrue(highH > 6.8, "极端密集离散乱码香农熵应大于 6.8 bits/char，实际: " + highH);
        assertFalse(cleaner.isValidTextByEntropy(highEntropySb.toString()), "极端高熵乱码应被判定为噪声切片");
    }

    @Test
    @DisplayName("Contract 02: 64位 SimHash 对标点与微弱修饰语微调文本实现准确近似聚类 (汉明距离 <= 3)")
    void contract02_simHash64_clustersNearDuplicatesWithHammingDistanceUnderThree() {
        String textOriginal = "尊敬的客户您好！自2026年1月1日起，生鲜退款时效调整为48小时，请及时关注官方通知。";
        String textModified = "尊敬的客户您好，自2026年1月1日起，生鲜退款时效调整为48小时。请及时关注官方通知！"; // 仅标点差异

        long hash1 = cleaner.computeSimHash(textOriginal);
        long hash2 = cleaner.computeSimHash(textModified);

        int hammingDist = cleaner.calculateHammingDistance(hash1, hash2);
        assertTrue(hammingDist <= 3, "标点微调文本的 64 位 SimHash 汉明距离应 <= 3，实际: " + hammingDist);

        // 与完全不同主题切片对比
        String textUnrelated = "PostgreSQL 向量插件 pgvector 支持 HNSW 与 IVFFlat 两种高性能近似最近邻检索索引。";
        long hashUnrelated = cleaner.computeSimHash(textUnrelated);
        int distanceUnrelated = cleaner.calculateHammingDistance(hash1, hashUnrelated);
        assertTrue(distanceUnrelated > 10, "完全异构主题文本汉明距离应显著大于 10，实际: " + distanceUnrelated);
    }

    @Test
    @DisplayName("Contract 03: 4段16位内存倒排桶查重吞吐量严格超过 50,000 切片/秒")
    void contract03_simHashThroughput_exceedsFiftyThousandPerSecond() {
        int totalSegments = 50_000;
        List<Long> mockHashes = new ArrayList<>(totalSegments);
        Random rand = new Random(42);

        for (int i = 0; i < totalSegments; i++) {
            mockHashes.add(rand.nextLong());
        }

        // 预热并批量注册
        for (int i = 0; i < 1000; i++) {
            cleaner.indexSimHash(mockHashes.get(i));
        }

        // 吞吐测试
        long startTime = System.nanoTime();
        int duplicateCount = 0;
        for (Long hash : mockHashes) {
            if (cleaner.isNearDuplicate(hash)) {
                duplicateCount++;
            }
            cleaner.indexSimHash(hash);
        }
        long durationNanos = System.nanoTime() - startTime;
        double durationSeconds = durationNanos / 1_000_000_000.0;
        double throughput = totalSegments / durationSeconds;

        assertTrue(throughput >= 50_000,
                String.format("4段16位倒排分桶查重吞吐量应 >= 50,000 ops/s，实际: %.2f ops/s (耗时: %.4fs)", throughput, durationSeconds));
    }

    @Test
    @DisplayName("Contract 04: 两阶段轻量极性预检准确定位谓词否定反转与关键数值翻转")
    void contract04_conflictPolarityPrecheck_detectsNegationAndInversion() {
        // 谓词对立否定 (允许 vs 禁止)
        String policyV1 = "根据2024版规定，生产数据库允许研发人员直接通过内网只读账户访问。";
        String policyV2 = "根据2026版规定，生产数据库严禁研发人员直接通过内网只读账户访问，须走堡垒机。";
        assertTrue(conflictService.precheckPolarityConflict(policyV1, policyV2),
                "应当检测出【允许】与【严禁】之间的直接极性对立");

        // 关键时效数值翻转 (7天 vs 48小时)
        String refundOld = "生鲜冷链商品在签收后支持 7天 内申请售后无理由退款。";
        String refundNew = "生鲜冷链商品在签收后支持 48小时 内申请售后极速退款。";
        assertTrue(conflictService.precheckPolarityConflict(refundOld, refundNew),
                "应当检测出 7天 到 48小时 的时效数值变更翻转");

        // 和谐互补语义 (不冲突)
        String featureA = "系统现已全面支持企业微信应用扫码快捷登录。";
        String featureB = "系统现已全面支持短信验证码一次性认证登录。";
        assertFalse(conflictService.precheckPolarityConflict(featureA, featureB),
                "互补功能表述不应误报为极性冲突");
    }

    @Test
    @DisplayName("Contract 05: 冲突消歧状态机依据时序版本偏序执行 SUPERSEDE 流转并绑定指针链")
    void contract05_conflictStateMachine_transitionsToSupersededWithPointer() {
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:00Z");

        SegmentMetadata oldSeg = SegmentMetadata.builder()
                .segmentId(101L)
                .documentId(10L)
                .text("生鲜类商品售后退款时效为 7天。")
                .effectiveStart(t1)
                .versionTag("v1.0")
                .conflictStatus(ConflictStatus.ACTIVE)
                .build();

        SegmentMetadata newSeg = SegmentMetadata.builder()
                .segmentId(202L)
                .documentId(20L)
                .text("生鲜类商品售后退款时效调整为 48小时。")
                .effectiveStart(t2)
                .versionTag("v2.0")
                .conflictStatus(ConflictStatus.ACTIVE)
                .build();

        ArbitrationResult result = conflictService.arbitrateConflict(oldSeg, newSeg);

        assertEquals(ArbitrationAction.SUPERSEDE, result.getAction(), "新版本发布应判定为 SUPERSEDE 取代操作");
        assertTrue(result.isPolarityConflictDetected(), "应检测到数值/极性翻转");
        assertEquals(ConflictStatus.SUPERSEDED, result.getOldSegmentNewStatus(), "旧切片状态应变更为 SUPERSEDED");
        assertEquals(ConflictStatus.ACTIVE, result.getNewSegmentStatus(), "新切片状态应保持为 ACTIVE");
        assertEquals(202L, result.getSupersededById(), "旧切片应被正确绑定 superseded_by_id = 202");
    }

    @Test
    @DisplayName("Contract 06: 时态感知门禁在查询时间戳 t_q 处严格阻断已过期切片并放行有效切片")
    void contract06_timeAwareFilter_blocksExpiredSegmentsAtQueryTimestamp() {
        Instant t2024 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2025End = Instant.parse("2025-12-31T23:59:59Z");
        Instant t2026 = Instant.parse("2026-01-01T00:00:00Z");
        Instant tQuery = Instant.parse("2026-06-15T12:00:00Z"); // 2026 年中查询

        CandidateSegment expiredSegment = CandidateSegment.builder()
                .segmentId(1001L)
                .content("旧版政策：退款时效为7天。")
                .effectiveStart(t2024)
                .effectiveEnd(t2025End) // 已于 2025 年底失效
                .conflictStatus(ConflictStatus.ACTIVE)
                .build();

        CandidateSegment activeSegment = CandidateSegment.builder()
                .segmentId(1002L)
                .content("2026新版政策：退款时效为48小时。")
                .effectiveStart(t2026)
                .effectiveEnd(null) // 永久有效
                .conflictStatus(ConflictStatus.ACTIVE)
                .build();

        // 验证过期切片阻断
        assertFalse(temporalFilter.isEffectiveAt(expiredSegment, tQuery), "已过有效期的切片应被时序门禁判定为无效并阻断");
        // 验证有效切片放行
        assertTrue(temporalFilter.isEffectiveAt(activeSegment, tQuery), "处于有效时序区间内的切片应被时序门禁顺利放行");
    }

    @Test
    @DisplayName("Contract 07: 指数时间衰减因子随发布时间差严格单调下凸递减并在半衰期处对齐理论值")
    void contract07_timeDecayWeight_strictlyMonotonicDecayWithHalfLife() {
        Instant queryTime = Instant.parse("2026-07-01T00:00:00Z");
        double halfLifeDays = 180.0;
        double alpha = 0.4;

        // 1. Delta_t = 0 时，权重应为 1.0
        double w0 = temporalFilter.calculateTemporalDecayWeight(queryTime, queryTime, halfLifeDays, alpha);
        assertEquals(1.0, w0, 1e-6, "发布时间等于查询时间时衰减加权应精确等于 1.0");

        // 2. Delta_t = 180 天 (半衰期) 时，exp(-lambda * 180) = 0.5, W = 1 - 0.5 * 0.4 = 0.8
        Instant timeHalfLife = queryTime.minus(180, ChronoUnit.DAYS);
        double wHalf = temporalFilter.calculateTemporalDecayWeight(timeHalfLife, queryTime, halfLifeDays, alpha);
        assertEquals(0.8, wHalf, 1e-3, "半衰期处衰减加权应精确等于理论值 0.8");

        // 3. 验证严格单调性 W(0) > W(90) > W(180) > W(360) > (1 - alpha)
        Instant time90 = queryTime.minus(90, ChronoUnit.DAYS);
        Instant time360 = queryTime.minus(360, ChronoUnit.DAYS);
        double w90 = temporalFilter.calculateTemporalDecayWeight(time90, queryTime, halfLifeDays, alpha);
        double w360 = temporalFilter.calculateTemporalDecayWeight(time360, queryTime, halfLifeDays, alpha);

        assertTrue(w0 > w90, "时间衰减应满足严格单调递减 W(0) > W(90)");
        assertTrue(w90 > wHalf, "时间衰减应满足严格单调递减 W(90) > W(180)");
        assertTrue(wHalf > w360, "时间衰减应满足严格单调递减 W(180) > W(360)");
        assertTrue(w360 >= (1.0 - alpha), "衰减加权下界应不低于保底值 (1 - alpha) = 0.6");
    }

    @Test
    @DisplayName("Contract 08: 合成黄金问答自进化引擎完整支持 4 类样本分类并成功解析基准数据")
    void contract08_syntheticQAGenerator_producesFourDiverseQuestionTypes() throws Exception {
        InputStream is = getClass().getResourceAsStream("/fixtures/rag-synthetic-golden-v1.jsonl");
        if (is == null) {
            is = getClass().getClassLoader().getResourceAsStream("fixtures/rag-synthetic-golden-v1.jsonl");
        }
        String content;
        if (is != null) {
            try (InputStream stream = is) {
                content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            java.nio.file.Path path = java.nio.file.Path.of("fixtures/rag-synthetic-golden-v1.jsonl");
            if (!java.nio.file.Files.exists(path)) {
                path = java.nio.file.Path.of("tests/fixtures/rag-synthetic-golden-v1.jsonl");
            }
            if (!java.nio.file.Files.exists(path)) {
                path = java.nio.file.Path.of("backend/tests/fixtures/rag-synthetic-golden-v1.jsonl");
            }
            assertTrue(java.nio.file.Files.exists(path), "未能找到 fixtures/rag-synthetic-golden-v1.jsonl 评测基准文件");
            content = java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
        }

        List<SyntheticGoldenSample> samples = syntheticEngine.bootstrapAndVerifySuite(content, Collections.emptyMap());
        assertFalse(samples.isEmpty(), "解析出的黄金评测样本集不应为空");

        Set<QuestionType> parsedTypes = new HashSet<>();
        for (SyntheticGoldenSample s : samples) {
            parsedTypes.add(s.getType());
        }

        assertTrue(parsedTypes.contains(QuestionType.SINGLE_HOP), "评测集应包含单跳事实问答 (SINGLE_HOP)");
        assertTrue(parsedTypes.contains(QuestionType.MULTI_HOP), "评测集应包含多跳推理问答 (MULTI_HOP)");
        assertTrue(parsedTypes.contains(QuestionType.TEMPORAL_COMPARATIVE), "评测集应包含时序对比问答 (TEMPORAL_COMPARATIVE)");
        assertTrue(parsedTypes.contains(QuestionType.COUNTERFACTUAL_UNANSWERABLE), "评测集应包含反事实拒答问答 (COUNTERFACTUAL_UNANSWERABLE)");
    }

    @Test
    @DisplayName("Contract 09: 双向自我一致性反向验证有效清洗幻觉假真值 (保真度门禁 >= 0.85)")
    void contract09_selfConsistencyVerification_filtersHallucinatorySamples() {
        Map<String, String> contexts = Map.of(
                "ctx_01", "系统在触发李雅普诺夫自适应背压时，会通过有界队列监控实时长度，超过上限50时执行节流。"
        );

        // 真实高保真样本
        SyntheticGoldenSample goodSample = SyntheticGoldenSample.builder()
                .id("sample_good")
                .type(QuestionType.SINGLE_HOP)
                .query("系统何时触发节流？")
                .expectedAnswer("有界队列监控实时长度超过上限50时执行节流。")
                .contextIds(List.of("ctx_01"))
                .build();

        boolean goodVerified = syntheticEngine.verifySelfConsistency(goodSample, contexts);
        assertTrue(goodVerified, "高保真样本应通过自我一致性验证");
        assertTrue(goodSample.isVerified(), "验证标志应置为 true");
        assertTrue(goodSample.getGroundingScore() >= 0.85, "事实支撑度得分应 >= 0.85");

        // 捏造幻觉样本 (答案中的事实在原文完全不存在)
        SyntheticGoldenSample hallucinatedSample = SyntheticGoldenSample.builder()
                .id("sample_hallucinated")
                .type(QuestionType.SINGLE_HOP)
                .query("系统采用哪种加密？")
                .expectedAnswer("采用由火星量子纠缠协议提供的256位实时动态超维密钥加密。")
                .contextIds(List.of("ctx_01"))
                .build();

        boolean badVerified = syntheticEngine.verifySelfConsistency(hallucinatedSample, contexts);
        assertFalse(badVerified, "包含虚构幻觉事实的样本应被自我一致性反向验证坚决剔除");
        assertFalse(hallucinatedSample.isVerified(), "幻觉样本验证标志应为 false");
    }

    @Test
    @DisplayName("Contract 10: 候选检索流水线端到端执行冲突硬阻断与时态加权重排，实现 100% 冲突隔离")
    void contract10_endToEndConflictBlock_preventsConflictingContextInjection() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");

        // 构造候选集：包含最新切片、被取代切片、已过期切片、冲突未决切片
        CandidateSegment sNew = CandidateSegment.builder()
                .segmentId(1L)
                .content("最新退款政策：生鲜48小时内退款")
                .baseScore(0.92)
                .effectiveStart(now.minus(10, ChronoUnit.DAYS))
                .effectiveEnd(null)
                .publishTime(now.minus(10, ChronoUnit.DAYS))
                .conflictStatus(ConflictStatus.ACTIVE)
                .build();

        CandidateSegment sSuperseded = CandidateSegment.builder()
                .segmentId(2L)
                .content("旧版退款政策：生鲜7天内退款")
                .baseScore(0.95) // 原始相关度甚至更高
                .effectiveStart(now.minus(400, ChronoUnit.DAYS))
                .effectiveEnd(now.plus(100, ChronoUnit.DAYS))
                .publishTime(now.minus(400, ChronoUnit.DAYS))
                .conflictStatus(ConflictStatus.SUPERSEDED) // 但已被取代！
                .supersededById(1L)
                .build();

        CandidateSegment sExpired = CandidateSegment.builder()
                .segmentId(3L)
                .content("2023年早期临时退款办法")
                .baseScore(0.88)
                .effectiveStart(now.minus(600, ChronoUnit.DAYS))
                .effectiveEnd(now.minus(200, ChronoUnit.DAYS)) // 已过期
                .conflictStatus(ConflictStatus.ACTIVE)
                .build();

        CandidateSegment sConflicted = CandidateSegment.builder()
                .segmentId(4L)
                .content("未经仲裁的对立退款规则")
                .baseScore(0.90)
                .effectiveStart(now.minus(5, ChronoUnit.DAYS))
                .conflictStatus(ConflictStatus.CONFLICTED) // 未决冲突切片
                .build();

        List<CandidateSegment> rawCandidates = List.of(sNew, sSuperseded, sExpired, sConflicted);

        List<CandidateSegment> finalResults = temporalFilter.filterAndRerank(rawCandidates, now, 180.0, 0.4);

        // 验证阻断率：被取代、已过期、冲突未决的切片必须 100% 被阻断，杜绝污染输入上下文
        assertEquals(1, finalResults.size(), "最终通过门禁的候选切片应仅保留 1 条最新合法切片");
        assertEquals(1L, finalResults.getFirst().getSegmentId(), "通过的切片必须为最新有效切片 sNew");

        for (CandidateSegment seg : finalResults) {
            assertNotEquals(ConflictStatus.SUPERSEDED, seg.getConflictStatus(), "最终检索上下文中严禁包含 SUPERSEDED 切片");
            assertNotEquals(ConflictStatus.CONFLICTED, seg.getConflictStatus(), "最终检索上下文中严禁包含 CONFLICTED 切片");
            assertNotEquals(2L, seg.getSegmentId(), "被取代的旧切片必须彻底阻断");
        }
    }
}
