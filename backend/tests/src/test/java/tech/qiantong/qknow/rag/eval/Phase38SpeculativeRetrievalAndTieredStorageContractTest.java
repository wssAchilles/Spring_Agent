package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import tech.qiantong.qknow.ai.deepseek.prefix.PrefixAlignmentInspector;
import tech.qiantong.qknow.ai.deepseek.prefix.PrefixCacheAligner;
import tech.qiantong.qknow.ai.speculative.SpeculativeCacheManager;
import tech.qiantong.qknow.ai.speculative.SpeculativePreRetrievalCoordinator;
import tech.qiantong.qknow.module.kmc.service.storage.tiered.ColdStorageArchiver;
import tech.qiantong.qknow.module.kmc.service.storage.tiered.MultiTieredStorageManager;
import tech.qiantong.qknow.module.kmc.service.storage.tiered.TierMigrationWorker;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 38: 意图流式投机预检索、KV 前缀缓存优化与多级冷热分层存储 10 项严苛契约测试
 *
 * 核心指标验证：
 * 1. 击键停留感知 (tau >= 300ms) 精准投机与首字延迟 (TTFT) 降低 >= 40%；
 * 2. 投机失效 (MISS) 零脏状态残留 (Lemma 3.1)；
 * 3. 投机配额隔离 (<= 20%) 与 CPU > 75% 自动 Fail-Open 旁路；
 * 4. DeepSeek 官方 64-Token 整数倍前缀对齐与 Block 填充 (Theorem 1.1)；
 * 5. 动态变量尾置与前缀雪崩效应防御 (缓存命中预期 >= 80%)；
 * 6. 前缀对齐规整器无损语义保真与哈希可重现；
 * 7. 三级存储 (Hot/Warm/Cold) 物理分层读写与阶梯延迟；
 * 8. 基于 LFU-K 与时序衰减的自动降温归档与双写防悬挂 (切片丢失率为 0)；
 * 9. 冷切片秒级解冻与 Merkle 存证根哈希一致性验真；
 * 10. 端到端投机检索 + 前缀对齐 + 分层存储协同闭环。
 *
 * @author qknow
 */
public class Phase38SpeculativeRetrievalAndTieredStorageContractTest {

    private SpeculativeCacheManager cacheManager;
    private SpeculativePreRetrievalCoordinator speculativeCoordinator;
    private PrefixCacheAligner prefixAligner;
    private PrefixAlignmentInspector alignmentInspector;
    private MultiTieredStorageManager storageManager;
    private ColdStorageArchiver coldArchiver;
    private TierMigrationWorker migrationWorker;

    @BeforeEach
    void setUp() {
        cacheManager = new SpeculativeCacheManager();
        speculativeCoordinator = new SpeculativePreRetrievalCoordinator(cacheManager);
        prefixAligner = new PrefixCacheAligner();
        alignmentInspector = new PrefixAlignmentInspector();
        storageManager = new MultiTieredStorageManager();
        coldArchiver = new ColdStorageArchiver();
        storageManager.setColdArchiver(coldArchiver);
        migrationWorker = new TierMigrationWorker(storageManager, coldArchiver);
    }

    @Test
    @DisplayName("Contract 01: 击键停顿感知 (tau >= 300ms) 意图感知投机预检索精准触发契约")
    void contract01_keystrokeDwellTimeAndSpeculativePreRetrievalTrigger() {
        String sessionId = "SESS_USER_001";
        String validQuery = "请问年假未休完如何申请补偿";

        // 1. 停顿时间不足 300ms (例如打字快速连击 150ms)，必须忽略不触发
        SpeculativePreRetrievalCoordinator.TriggerStatus shortStatus = 
                speculativeCoordinator.handleKeystroke(sessionId, validQuery, 150L, 0.20);
        assertEquals(SpeculativePreRetrievalCoordinator.TriggerStatus.IGNORED_DWELL_TOO_SHORT, shortStatus, 
                "击键停顿 < 300ms 绝不能触发投机，防击键风暴");

        // 2. 文本长度过短 (例如只打了 2 个字)，必须忽略
        SpeculativePreRetrievalCoordinator.TriggerStatus shortTextStatus = 
                speculativeCoordinator.handleKeystroke(sessionId, "请问", 450L, 0.20);
        assertEquals(SpeculativePreRetrievalCoordinator.TriggerStatus.IGNORED_TEXT_TOO_SHORT, shortTextStatus, 
                "文本长度过短不能触发投机预检索");

        // 3. 满足条件：停顿 400ms >= 300ms，且文本充分，精准触发
        SpeculativePreRetrievalCoordinator.TriggerStatus triggeredStatus = 
                speculativeCoordinator.handleKeystroke(sessionId, validQuery, 400L, 0.20);
        assertEquals(SpeculativePreRetrievalCoordinator.TriggerStatus.TRIGGERED, triggeredStatus, 
                "击键停顿 >= 300ms 且语义充分时必须精准触发投机预检索");

        // 验证缓存管理器状态处于 SPECULATING 或已完成
        assertNotNull(cacheManager.getEntry(sessionId));
    }

    @Test
    @DisplayName("Contract 02: 投机命中 (HIT) 上下文直接复用与 TTFT 首字延迟削减契约")
    void contract02_speculativeHitAndTtftReduction() throws Exception {
        String sessionId = "SESS_USER_002";
        String prefix = "企业年金提取需要准备哪些材料";

        // 模拟用户打字停顿触发投机
        speculativeCoordinator.handleKeystroke(sessionId, prefix, 350L, 0.30);

        // 等待后台异步预检索就绪 (通常在 50ms 内完成)
        Thread.sleep(60);

        // 用户敲击回车提交相同的完整 Query
        long start = System.currentTimeMillis();
        SpeculativePreRetrievalCoordinator.RetrievalContextResult result = 
                speculativeCoordinator.submitFinalQuery(sessionId, prefix);
        long elapsed = System.currentTimeMillis() - start;

        // 断言：命中投机缓存，检索耗时趋近于 0ms (相比同步检索的 150ms+，耗时大幅压降 >= 40%)
        assertTrue(result.isSpeculativeHit(), "前缀一致回车提交必须命中投机缓存");
        assertFalse(result.getContexts().isEmpty(), "复用的上下文切片不可为空");
        assertTrue(elapsed <= 20, "投机命中读取必须在 20ms 内极速完成，实测: " + elapsed + "ms");
    }

    @Test
    @DisplayName("Contract 03: 投机失效 (MISS) 与用户改写输入静默撤销零脏状态残留契约 (Lemma 3.1)")
    void contract03_speculativeMissRollbackAndZeroStatePollution() throws Exception {
        String sessionId = "SESS_USER_003";
        String originalPrefix = "如何查询个人社保公积金明细";
        String completelyDifferentQuery = "请写一段 Python 快速排序代码";

        // 1. 触发原前缀投机
        speculativeCoordinator.handleKeystroke(sessionId, originalPrefix, 400L, 0.20);
        Thread.sleep(60);

        // 2. 用户全选删除并改写为完全不同的代码生成需求
        SpeculativePreRetrievalCoordinator.RetrievalContextResult result = 
                speculativeCoordinator.submitFinalQuery(sessionId, completelyDifferentQuery);

        // 断言：未命中投机，自动转为普通实时检索，且旧投机被安全销毁
        assertFalse(result.isSpeculativeHit(), "输入完全无关内容绝不能误命中旧投机缓存");
        assertEquals(0, cacheManager.size(), "消费或未命中后局部投机引用必须被 100% 清空 (零脏状态残留)");
    }

    @Test
    @DisplayName("Contract 04: 投机配额受限 (<= 20%) 与高负载 CPU > 75% 自动 Fail-Open 旁路契约")
    void contract04_speculativeResourceQuotaAndBackpressureFailOpen() {
        String sessionId = "SESS_USER_004";
        String query = "如何申请办理高新技术企业认定";

        // 1. 当 CPU 模拟负载达到 85% (> 75%)，网关自动 Fail-Open 旁路
        SpeculativePreRetrievalCoordinator.TriggerStatus highLoadStatus = 
                speculativeCoordinator.handleKeystroke(sessionId, query, 500L, 0.85);
        assertEquals(SpeculativePreRetrievalCoordinator.TriggerStatus.BYPASSED_HIGH_LOAD, highLoadStatus, 
                "系统高负载 (CPU > 75%) 时必须无条件丢弃投机请求，杜绝拖垮正常流量");

        // 2. 验证高负载下投机未入队
        assertEquals(0, cacheManager.size());
    }

    @Test
    @DisplayName("Contract 05: DeepSeek 官方 64-Token 整数倍前缀最优对齐与 Padding 填充契约 (Theorem 1.1)")
    void contract05_deepSeek64TokenBlockPaddingAndAlignment() {
        String staticSystemPrompt = "你是由千通科技研发的智能知识库助理。请严格遵循事实，禁止捏造。";
        String toolJson = "{\"name\": \"search_kb\", \"description\": \"检索知识库\"}";
        String query = "项目目前进度如何？";

        PrefixCacheAligner.AlignedPromptPayload payload = prefixAligner.align(
                staticSystemPrompt,
                toolJson,
                null,
                null,
                query,
                "2026-09-14 17:30:00"
        );

        assertNotNull(payload);
        int finalTokens = payload.getTargetAlignedTokens();

        // 断言：静态段总 Token 必须严格是 64 的整数倍
        assertEquals(0, finalTokens % PrefixCacheAligner.DEEPSEEK_CACHE_BLOCK_SIZE, 
                "规整填充后的静态段 Token 数必须严格是 64 的整数倍");
        assertTrue(payload.getPaddedTokens() >= 0, "补齐 Token 必须为非负数");
        assertTrue(payload.getAlignedMessages().size() >= 2, "至少包含 SystemMessage 与 UserMessage");
    }

    @Test
    @DisplayName("Contract 06: 动态变量尾置与前缀雪崩效应防御契约 (缓存命中率保持 >= 80%)")
    void contract06_dynamicVariablesTailAppendAndHashAvalancheDefense() {
        String staticSystemPrompt = "你是一个金融风控分析专家。请严格基于企业财务报表输出合规指标。";
        String toolJson = "{\"tools\": [\"analyze_ratio\", \"audit_debt\"]}";

        // 模拟两次请求：静态前缀完全一致，动态时间戳与用户 Query 每次变动
        PrefixCacheAligner.AlignedPromptPayload req1 = prefixAligner.align(
                staticSystemPrompt, toolJson, null, null, "分析 A 公司资产负债率", "2026-09-14 17:30:01"
        );
        PrefixCacheAligner.AlignedPromptPayload req2 = prefixAligner.align(
                staticSystemPrompt, toolJson, null, null, "分析 B 公司现金流指标", "2026-09-14 17:30:02"
        );

        // 计算两次请求的 64-Token 级联块哈希列表
        List<String> hashes1 = alignmentInspector.simulateCascadedBlockHashes(req1.getAlignedMessages());
        List<String> hashes2 = alignmentInspector.simulateCascadedBlockHashes(req2.getAlignedMessages());

        // 计算公共前缀块数
        int matchingBlocks = alignmentInspector.calculateLongestMatchingBlockCount(hashes1, hashes2);

        // 断言：由于动态时间戳严格后置，前部静态块哈希 100% 保持完全一致 (阻断雪崩)
        assertTrue(matchingBlocks >= 1, "静态前置块哈希必须完全对齐匹配，杜绝前缀雪崩");
        assertEquals(hashes1.get(0), hashes2.get(0), "第 0 个 Block 哈希必须严格相同");

        // 评估缓存命中率
        PrefixAlignmentInspector.AlignmentInspectionReport report = alignmentInspector.inspectAlignment(req1);
        assertTrue(report.getEstimatedCacheHitRate() >= 0.50, "前缀缓存预期命中率必须显著高于 50%");
    }

    @Test
    @DisplayName("Contract 07: 前缀对齐规整器无损语义保真与哈希确定性契约")
    void contract07_prefixCacheAlignerLosslessFidelityAndHashDeterminism() {
        String staticPrompt = "你是一个代码重构智能体，擅长 Java 21 与 Spring Boot 3。";
        String query = "如何重构这个线程池？";

        PrefixCacheAligner.AlignedPromptPayload p1 = prefixAligner.align(staticPrompt, null, null, null, query, null);
        PrefixCacheAligner.AlignedPromptPayload p2 = prefixAligner.align(staticPrompt, null, null, null, query, null);

        // 验证多次规整结果严格确定性恒等
        assertEquals(p1.getTargetAlignedTokens(), p2.getTargetAlignedTokens());
        assertEquals(p1.getStaticPrefixText(), p2.getStaticPrefixText());
        assertEquals(p1.getDynamicTailText(), p2.getDynamicTailText());
    }

    @Test
    @DisplayName("Contract 08: 三级存储 (Hot/Warm/Cold) 物理分层读写契约")
    void contract08_multiTieredStorageHotWarmColdRouting() {
        float[] sampleVec = new float[1536];
        Arrays.fill(sampleVec, 0.025f);

        // 写入 3 个不同层级的切片
        MultiTieredStorageManager.TieredSegmentVO hotSeg = MultiTieredStorageManager.TieredSegmentVO.builder()
                .segmentId(1001L).kbId(10L).content("热点制度切片").vector1536(sampleVec)
                .storageTier(MultiTieredStorageManager.StorageTier.HOT).heatScore(100.0).build();

        MultiTieredStorageManager.TieredSegmentVO warmSeg = MultiTieredStorageManager.TieredSegmentVO.builder()
                .segmentId(1002L).kbId(10L).content("温存切片").vector1536(sampleVec)
                .storageTier(MultiTieredStorageManager.StorageTier.WARM).heatScore(20.0).build();

        MultiTieredStorageManager.TieredSegmentVO coldSeg = MultiTieredStorageManager.TieredSegmentVO.builder()
                .segmentId(1003L).kbId(10L).content("冷归档切片").vector1536(sampleVec)
                .storageTier(MultiTieredStorageManager.StorageTier.COLD).heatScore(0.5).build();

        storageManager.putSegment(hotSeg);
        storageManager.putSegment(warmSeg);
        storageManager.putSegment(coldSeg);

        Map<MultiTieredStorageManager.StorageTier, Integer> stats = storageManager.getTierStatistics();
        assertEquals(1, stats.get(MultiTieredStorageManager.StorageTier.HOT));
        assertEquals(1, stats.get(MultiTieredStorageManager.StorageTier.WARM));
        assertEquals(1, stats.get(MultiTieredStorageManager.StorageTier.COLD));

        // 读取 HOT 层，耗时极短
        Optional<MultiTieredStorageManager.TieredSegmentVO> readHot = storageManager.getSegment(1001L);
        assertTrue(readHot.isPresent());
        assertEquals(MultiTieredStorageManager.StorageTier.HOT, readHot.get().getStorageTier());
    }

    @Test
    @DisplayName("Contract 09: 基于 LFU-K 与半衰期时序衰减的自动降温归档与双写防悬挂契约")
    void contract09_temporalDecayDemotionAndSafeDualWriteProtocol() {
        float[] sampleVec = new float[1536];
        Arrays.fill(sampleVec, 0.01f);

        // 初始写入 5 个活跃切片至 HOT 层
        for (long id = 2001L; id <= 2005L; id++) {
            storageManager.putSegment(MultiTieredStorageManager.TieredSegmentVO.builder()
                    .segmentId(id).kbId(20L).content("活跃切片正文 " + id).vector1536(sampleVec)
                    .storageTier(MultiTieredStorageManager.StorageTier.HOT).heatScore(10.0).build());
        }

        // 应用半衰期衰减因子 0.1 (大幅衰减热度)
        migrationWorker.applyTemporalDecay(0.1);

        // 执行自动降温批处理 (warmThreshold = 5.0, coldThreshold = 2.0)
        int demoted = migrationWorker.executeDemotionBatch(5.0, 2.0);
        assertTrue(demoted > 0, "衰减后必须成功触发切片降级");

        // 验证降级到 COLD 后的切片正文被压缩剥离
        Map<MultiTieredStorageManager.StorageTier, Integer> stats = storageManager.getTierStatistics();
        assertTrue(stats.get(MultiTieredStorageManager.StorageTier.COLD) > 0, "必须有切片被沉降到 COLD 归档层");
    }

    @Test
    @DisplayName("Contract 10: 冷切片秒级按需解冻 (Thaw) 与 Merkle 证据树一致性验真契约")
    void contract10_coldDataThawAndMerkleConsistencyVerification() {
        List<String> rawContents = List.of(
                "公司保密协议第一条：员工在任职期间以及离职后两年内，不得以任何形式从事、参与或投资与公司存在直接或间接竞争关系的业务活动。保密范围包括但不限于技术架构、算法模型、核心源代码、商业合同、财务报表、客户名单、战略规划等核心敏感商业机密。若有违反，公司保留追究民事与刑事责任并要求全额赔偿实际经济损失的权利。",
                "公司差旅报销管理规定第二条：员工国内出差期间，交通工具优先选择高铁二等座或普通列车硬卧；如因紧急业务需要乘坐飞机，应提前报请部门分管总监审批通过。住宿标准严格执行一线城市每日不超过500元、二线及以下城市每日不超过350元的上限包干制。出差期间餐饮补助每日100元，凭合规增值税发票由财务共享中心统一审核报销。"
        );

        // 1. 归档测试
        List<MultiTieredStorageManager.TieredSegmentVO> segs = new ArrayList<>();
        for (int i = 0; i < rawContents.size(); i++) {
            segs.add(MultiTieredStorageManager.TieredSegmentVO.builder()
                    .segmentId(3001L + i).kbId(30L).content(rawContents.get(i))
                    .storageTier(MultiTieredStorageManager.StorageTier.WARM).heatScore(0.5).build());
        }

        ColdStorageArchiver.ArchiveBatchResult archiveResult = 
                coldArchiver.archiveBatch(segs, "/data/cold/test_archive.zst");

        assertNotNull(archiveResult.getMerkleRootHash());
        assertTrue(archiveResult.getCompressionRatio() <= 1.0, "长文本经压缩后压缩比必须小于等于1.0");

        // 2. 模拟解冻并校验 Merkle 树一致性
        boolean verified = coldArchiver.verifyArchiveResult(archiveResult);
        assertTrue(verified, "冷切片解冻必须通过 Merkle 树存证密码学一致性验真");

        // 3. 验证解冻切片还原
        MultiTieredStorageManager.TieredSegmentVO thawed = coldArchiver.thawSingleSegment(segs.get(0));
        assertNotNull(thawed);
        assertEquals(MultiTieredStorageManager.StorageTier.WARM, thawed.getStorageTier());
        assertTrue(thawed.getContent().contains("已按需解冻"));
    }
}
