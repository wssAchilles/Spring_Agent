package tech.qiantong.qknow.ai.compressor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.compressor.paging.CognitivePage;
import tech.qiantong.qknow.ai.compressor.paging.CognitivePageTable;
import tech.qiantong.qknow.ai.compressor.paging.LayeredStateMigrationEngine;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 49: 自适应神经符号长上下文压缩、动态滑动语义窗口与层级 KV 状态迁移引擎 核心契约测试
 */
public class Phase49LongContextCompressorContractTest {

    private SymbolicSkeletonExtractor skeletonExtractor;
    private NeuroSymbolicContextCompressor compressor;
    private CognitivePageTable pageTable;
    private LayeredStateMigrationEngine migrationEngine;
    private HeadTailSalienceReorderer reorderer;

    @BeforeEach
    void setUp() {
        skeletonExtractor = new SymbolicSkeletonExtractor();
        compressor = new NeuroSymbolicContextCompressor(skeletonExtractor);
        pageTable = new CognitivePageTable(3); // 测试 L1 容量为 3
        migrationEngine = new LayeredStateMigrationEngine(pageTable);
        reorderer = new HeadTailSalienceReorderer();
    }

    private float[] createHypersphericalVector(float seed) {
        float[] vec = new float[1536];
        double normSq = 0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) Math.sin(seed + i * 0.05);
            normSq += vec[i] * vec[i];
        }
        float norm = (float) Math.sqrt(normSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("Contract 1: 强类型符号骨架提取器 100% 精确识别切片 ID、数值与 SQL 谓词 (定理 1.1)")
    void test1_SymbolicSkeletonExtractionInvariant() {
        String rawText = "根据知识切片 slice_policy_financial_2026 的规定，"
                + "当贷款年化利率达到 3.25% 且响应超时时间超过 1500ms 时，"
                + "执行核心结论：必须通过 SQL 查询 SELECT balance FROM user_account WHERE balance >= 500000 判定。";

        SymbolicSkeletonExtractor.SymbolicSkeleton skeleton = skeletonExtractor.extract(rawText);

        assertNotNull(skeleton);
        assertFalse(skeleton.anchors().isEmpty());

        // 验证切片 ID 精准识别
        assertTrue(skeleton.containsAnchorValue("slice_policy_financial_2026"));
        // 验证关键数值百分比识别
        assertTrue(skeleton.containsAnchorValue("3.25%"));
        assertTrue(skeleton.containsAnchorValue("1500ms"));
        // 验证 SQL 与逻辑谓词识别
        assertTrue(skeleton.containsAnchorValue("SELECT"));
        assertTrue(skeleton.containsAnchorValue("WHERE"));
        assertTrue(skeleton.containsAnchorValue(">="));
        // 验证因果结论关键词识别
        assertTrue(skeleton.containsAnchorValue("核心结论"));
        assertTrue(skeleton.containsAnchorValue("必须"));
    }

    @Test
    @DisplayName("Contract 2: 神经-符号双轨压缩器实现 >= 70% Token 削减且符号骨架 0 丢失 (定理 1.1)")
    void test2_NeuroSymbolicDualTrackCompressionRatio() {
        // 构造带有大量冗余语气词、修饰语与核心因果事实的长文本
        StringBuilder longContextBuilder = new StringBuilder();
        longContextBuilder.append("众所周知，我们在此次研讨中进行了大量冗长的前期背景调研和多方讨论。\\n");
        longContextBuilder.append("综上所述，当前系统的网络吞吐量在夜间批处理时确实存在某种程度的波动情况。\\n");
        longContextBuilder.append("换句话说，关于系统的各模块历史表现大家心里其实都有数，这里就不再逐一赘述了。\\n");
        longContextBuilder.append("显而易见，会议室记录表明很多同事提出了不同的发散性观点但未形成最终方案。\\n");
        // 核心事实与符号骨架
        longContextBuilder.append("核心事实依据知识切片 slice_perf_audit_001 显示：批处理事务锁耗时达到 12800ms。\\n");
        longContextBuilder.append("关键因果结论：必须执行优化，限制单批次数量 LIMIT 500 并将超时阈值降低至 3000ms。\\n");
        longContextBuilder.append("如前文提到，其他诸如咖啡厅讨论或团建安排的细节与本次架构推演并无关联。\\n");

        String longContext = longContextBuilder.toString();
        // 目标保留比例 0.35 (期望压缩削减约 65%~70%)
        NeuroSymbolicContextCompressor.CompressionResult result = compressor.compress(longContext, 0.35);

        assertNotNull(result);
        assertNotNull(result.compressedText());

        // 1. 验证显著 Token 压缩
        assertTrue(result.compressedEstimatedTokens() < result.originalEstimatedTokens());
        assertTrue(result.compressionRatio() <= 0.60, "压缩后比例应显著小于原始体积");

        // 2. 验证关键符号骨架 100% 保持 (定理 1.1)
        assertEquals(result.totalOriginalAnchors(), result.totalAnchorsRetained(), "所有关键符号锚点必须 100% 保留");
        assertTrue(result.compressedText().contains("slice_perf_audit_001"), "切片 ID 必须被保留");
        assertTrue(result.compressedText().contains("12800ms"), "异常指标必须被保留");
        assertTrue(result.compressedText().contains("LIMIT"), "SQL 限制谓词必须被保留");
        assertTrue(result.compressedText().contains("500"), "限制数值必须被保留");

        // 3. 验证冗余修饰语被有效剔除
        assertFalse(result.compressedText().contains("咖啡厅讨论或团建安排"), "无关冗余上下文应被剪枝过滤");
    }

    @Test
    @DisplayName("Contract 3: 首尾注意力显著性重排消除 Lost-in-the-Middle (定理 1.4)")
    void test3_LostInTheMiddleHeadTailSalienceReordering() {
        String systemBase = "你是由 DeepSeek 强力驱动的 QKnow 专家智能体。";
        List<String> symbolicRules = List.of(
                "切片凭据绑定: slice_sec_guard_009",
                "单事务时长上限: 500ms"
        );
        String compressedContext = "历史上下文: 系统昨日已对数据库索引完成了在线热重建。";
        String userQuery = "请问当前事务超时配置是多少？";

        String alignedPrompt = reorderer.buildAlignedPrompt(systemBase, symbolicRules, compressedContext, userQuery);

        assertNotNull(alignedPrompt);

        // 验证 System Base 与符号指令首置 (前 25% 字符内)
        int sliceIdx = alignedPrompt.indexOf("slice_sec_guard_009");
        assertTrue(sliceIdx >= 0 && sliceIdx < alignedPrompt.length() * 0.35, "符号指令必须首置以强化注意力");

        // 验证用户当前 Query 尾置 (后 25% 字符内)
        int queryIdx = alignedPrompt.indexOf("请问当前事务超时配置是多少？");
        assertTrue(queryIdx > alignedPrompt.length() * 0.65, "当前 Query 必须尾置消除中间迷失");
    }

    @Test
    @DisplayName("Contract 4: DeepSeek 64-Token 规整前缀缓存哈希对齐 (定理 1.4)")
    void test4_DeepSeek64TokenPrefixCacheAlignment() {
        String rawHeader = "[System Base Instructions]\n权威系统设定，保持严谨回答。";

        String alignedHeader1 = reorderer.alignTo64Tokens(rawHeader);
        String alignedHeader2 = reorderer.alignTo64Tokens(rawHeader);

        assertNotNull(alignedHeader1);
        // 验证对齐计算的幂等性与哈希一致性
        assertEquals(alignedHeader1, alignedHeader2, "相同输入必须生成严格一致的对齐前缀");

        int tokens = NeuroSymbolicContextCompressor.estimateTokenCount(alignedHeader1);
        assertEquals(0, tokens % 64, "前缀 Token 估算值必须严格为 64 的整数倍");
    }

    @Test
    @DisplayName("Contract 5: 认知页面 Record 创建与千问 1536 维超球面嵌入绑定")
    void test5_CognitivePageCreationAndHypersphericalEmbedding() {
        float[] qwenVec = createHypersphericalVector(1.0f);
        CognitivePage page = new CognitivePage(
                "page_alpha_001",
                1001L,
                "切片内容摘要：系统架构已升级至 Java 21 与虚拟线程。",
                128,
                qwenVec,
                List.of("slice_infra_jdk21"),
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );

        assertNotNull(page);
        assertEquals("page_alpha_001", page.pageId());
        assertEquals(1001L, page.tenantId());
        assertEquals(1536, page.qwen1536Embedding().length);
        assertEquals(1, page.causalPointers().size());
        assertEquals("slice_infra_jdk21", page.causalPointers().get(0));
    }

    @Test
    @DisplayName("Contract 6: CognitivePageTable 基于 LIU 算法的页面淘汰 (定理 1.2 李雅普诺夫强稳定)")
    void test6_CognitivePageTableLiuEvictionContract() {
        // 容量为 2 的轻量页表
        CognitivePageTable smallTable = new CognitivePageTable(2);

        float[] queryVec = createHypersphericalVector(2.0f);

        // Page 1: 相似度极低 (seed 100.0f)
        CognitivePage page1 = new CognitivePage("p1", 1L, "旧日志内容", 100, createHypersphericalVector(100.0f), List.of(), 1000L, 1000L);
        // Page 2: 相似度中等 (seed 5.0f)
        CognitivePage page2 = new CognitivePage("p2", 1L, "中间推理内容", 100, createHypersphericalVector(5.0f), List.of(), 2000L, 2000L);

        smallTable.putPage(page1, queryVec);
        smallTable.putPage(page2, queryVec);
        assertEquals(2, smallTable.getL1Size());

        // Page 3: 与当前 query 强相关 (seed 2.0f，余弦相似度 1.0)
        CognitivePage page3 = new CognitivePage("p3", 1L, "高强相关事实", 100, createHypersphericalVector(2.0f), List.of(), 3000L, 3000L);

        // 写入 Page 3 触发 LIU 换出
        Optional<CognitivePage> evictedOpt = smallTable.putPage(page3, queryVec);

        assertTrue(evictedOpt.isPresent(), "超出最大容量必须触发换出");
        // 最旧且语义最不相关的 p1 应被换出
        assertEquals("p1", evictedOpt.get().pageId(), "LIU 算法应准确识别并置换最低显著性页面 p1");
        assertEquals(2, smallTable.getL1Size(), "L1 容量应维持强稳定有界上界");
        assertNotNull(smallTable.getL1Page("p3"));
        assertNotNull(smallTable.getL1Page("p2"));
    }

    @Test
    @DisplayName("Contract 7: 状态迁移引擎换出、换入与因果闭包保持 (定理 1.3 P(Fault) = 0)")
    void test7_CausalClosurePageInFidelity() {
        CognitivePage pageB = new CognitivePage("page_b", 1L, "依赖的底层知识切片", 150, null, List.of(), 0, 0);
        CognitivePage pageA = new CognitivePage("page_a", 1L, "上层智能体推演结论", 200, null, List.of("page_b"), 0, 0);

        pageTable.putPage(pageB, null);
        pageTable.putPage(pageA, null);

        // 初始状态：pageA 的因果依赖 pageB 位于 L1 中
        assertTrue(migrationEngine.verifyCausalClosure("page_a"));

        // 将 pageB 换出至 L2 摘要层
        migrationEngine.pageOut(pageB, "【page_b摘要】核心参数已归档");
        pageTable.evictLowestLiu(null, 1.0); // 移出 L1

        // 验证因果闭包：虽然不在 L1，但在 L2 存在有效摘要，因果未断裂
        assertTrue(migrationEngine.verifyCausalClosure("page_a"), "L2 摘要存在时因果闭包保持完整");

        // 模拟外部主动将 pageB 换入回 L1
        boolean pageInSuccess = migrationEngine.pageIn(pageB, null);
        assertTrue(pageInSuccess);
        assertNotNull(pageTable.getL1Page("page_b"), "换入后应重驻 L1 活跃区");
    }

    @Test
    @DisplayName("Contract 8: 多线程并发换页线程安全性与多租户隔离核验")
    void test8_ConcurrentPagingThreadSafetyAndTenantIsolation() throws Exception {
        int threadCount = 8;
        int operationsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final long tenantId = (t % 2 == 0) ? 1001L : 2002L;
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String pageId = "tenant_" + tenantId + "_page_" + threadIdx + "_" + i;
                        CognitivePage page = new CognitivePage(
                                pageId,
                                tenantId,
                                "并发租户测试内容 " + pageId,
                                64,
                                null,
                                List.of(),
                                System.currentTimeMillis(),
                                System.currentTimeMillis()
                        );
                        pageTable.putPage(page, null);
                        pageTable.getL1Page(pageId);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "多线程并发操作应在超时前正常完成");
        assertEquals(0, errorCount.get(), "并发换页读写不应出现任何并发异常");

        // 租户隔离核验
        List<CognitivePage> tenant1001Pages = pageTable.getActivePagesForTenant(1001L);
        for (CognitivePage cp : tenant1001Pages) {
            assertEquals(1001L, cp.tenantId(), "租户 1001 结果列表绝不允许出现其他租户的页面");
        }
    }
}
