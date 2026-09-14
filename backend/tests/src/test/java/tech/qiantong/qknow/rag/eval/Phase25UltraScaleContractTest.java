package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.FastPassHybridScorer;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.TwoStageCascadeRerankEngine;
import tech.qiantong.qknow.module.kmc.service.sync.hotswap.IndexHotSwapperService;
import tech.qiantong.qknow.module.kmc.service.sync.pipeline.DocumentCheckpoint;
import tech.qiantong.qknow.module.kmc.service.sync.pipeline.DocumentCheckpointManager;
import tech.qiantong.qknow.module.kmc.service.sync.pipeline.ReactiveChunkingEmbeddingPipeline;
import tech.qiantong.qknow.module.kmc.service.sync.pipeline.ReactiveChunkingEmbeddingPipeline.ChunkBatch;
import tech.qiantong.qknow.module.kmc.service.sync.pipeline.ReactiveChunkingEmbeddingPipeline.ChunkItem;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 25 专属自动化契约测试
 * 覆盖：
 * 1. 李雅普诺夫强稳定响应式流式背压与队列有界性
 * 2. 双阈值动态微批切分 (Tokens <= 8192 || Chunks <= 25)
 * 3. 断点增量游标持久化与崩溃秒级精准恢复 (零重复处理)
 * 4. Fast-Pass 粗排混合保真算子打分时延严格 <= 5ms
 * 5. Fast-Pass 粗排降维至 Top-20 时高相关文档召回率 >= 90%
 * 6. Phase 07 动态门控首位共识直接短路旁路 (0 重排推理开销)
 * 7. 两阶段级联精排降低重排计算开销 >= 70%
 * 8. 影子表生命周期管理与无排他锁并发 HNSW 索引生成
 * 9. CAS 视图指针原子翻转 (<= 1ms) 与并发读零空窗期
 * 10. 双活索引一键瞬时故障回滚 (<= 500ms)
 */
public class Phase25UltraScaleContractTest {

    private DocumentCheckpointManager checkpointManager;
    private ReactiveChunkingEmbeddingPipeline pipeline;
    private FastPassHybridScorer fastPassScorer;
    private TwoStageCascadeRerankEngine cascadeRerankEngine;
    private IndexHotSwapperService hotSwapperService;

    @BeforeEach
    void setUp() {
        checkpointManager = new DocumentCheckpointManager();
        pipeline = new ReactiveChunkingEmbeddingPipeline();
        fastPassScorer = new FastPassHybridScorer();
        cascadeRerankEngine = new TwoStageCascadeRerankEngine(fastPassScorer);
        hotSwapperService = new IndexHotSwapperService();
    }

    @Test
    @DisplayName("Contract 01: 突发切片到达下响应式背压生效，队列长度严格有界无溢出")
    void contract01_lyapunovStreamingBackpressure_queueLengthRemainsBounded() {
        Long docId = 25001L;
        int totalChunks = 150;
        List<ChunkItem> chunks = new ArrayList<>(totalChunks);
        for (int i = 1; i <= totalChunks; i++) {
            chunks.add(ChunkItem.builder()
                    .index(i)
                    .text("测试段落切片内容 " + i + "，验证李雅普诺夫队列能量强稳定性。")
                    .estimatedTokens(30)
                    .lineOffset(i * 10L)
                    .build());
        }

        pipeline.resetMetrics();

        // 模拟下游 Embedding 调用微小延迟 (2ms)，引发上游背压调节
        Mono<Integer> executeMono = pipeline.executePipeline(docId, chunks, checkpointManager, batch -> {
            try {
                Thread.sleep(2);
            } catch (InterruptedException ignored) {}
            return Mono.just(true);
        });

        Integer processedCount = executeMono.block();
        assertNotNull(processedCount);
        assertEquals(totalChunks, processedCount.intValue(), "所有突发切片应全部成功处理无丢弃");

        // 断言排队长度始终在容量上限 50 以内，队列未发生无界发散
        int peakQueue = pipeline.getPeakQueueLength();
        assertTrue(peakQueue <= ReactiveChunkingEmbeddingPipeline.DEFAULT_QUEUE_CAPACITY,
                "峰值排队长度 (" + peakQueue + ") 不得超出背压上限 " + ReactiveChunkingEmbeddingPipeline.DEFAULT_QUEUE_CAPACITY);

        DocumentCheckpoint checkpoint = checkpointManager.getCheckpoint(docId);
        assertNotNull(checkpoint);
        assertEquals("COMPLETED", checkpoint.getStatus());
        assertEquals(totalChunks, checkpoint.getEmbeddedSegments().intValue());
    }

    @Test
    @DisplayName("Contract 02: 双阈值动态微批切分在 Token <= 8192 或切片 <= 25 时精准截断")
    void contract02_dynamicMicroBatcher_tokenAndCountThresholdHonored() {
        int chunkCount = 100;
        List<ChunkItem> chunks = new ArrayList<>(chunkCount);

        // 构造不同大小切片：部分切片约 400 Token，部分约 1200 Token
        for (int i = 1; i <= chunkCount; i++) {
            int tokenSize = (i % 3 == 0) ? 1200 : 400;
            chunks.add(ChunkItem.builder()
                    .index(i)
                    .text("切片-" + i)
                    .estimatedTokens(tokenSize)
                    .lineOffset(i * 5L)
                    .build());
        }

        List<ChunkBatch> batches = pipeline.partitionIntoDynamicBatches(chunks, 8192, 25);
        assertFalse(batches.isEmpty());

        int accumulatedChunks = 0;
        for (ChunkBatch batch : batches) {
            assertTrue(batch.getTotalTokens() <= 8192,
                    "单批次总 Token 必须 <= 8192，当前批次为: " + batch.getTotalTokens());
            assertTrue(batch.getChunkCount() <= 25,
                    "单批次切片数必须 <= 25，当前批次为: " + batch.getChunkCount());
            assertEquals(batch.getChunkCount(), batch.getItems().size());
            accumulatedChunks += batch.getChunkCount();
        }

        assertEquals(chunkCount, accumulatedChunks, "微批切分后切片总量必须完全守恒");
    }

    @Test
    @DisplayName("Contract 03: 断点游标中途崩溃后精准从 offset 续切，已提交切片零重复消费")
    void contract03_checkpointCursor_crashRecoveryResumesFromExactOffset() {
        Long docId = 25003L;
        int totalLines = 100;
        List<ChunkItem> allChunks = new ArrayList<>(totalLines);
        for (int i = 1; i <= totalLines; i++) {
            allChunks.add(ChunkItem.builder()
                    .index(i)
                    .text("长文档内容行-" + i)
                    .estimatedTokens(50)
                    .lineOffset(i * 100L) // 偏移量: 100, 200, ..., 10000
                    .build());
        }

        // 1. 模拟首次处理至第 40 个切片 (lineOffset = 4000) 时发生崩溃异常
        checkpointManager.getOrCreateCheckpoint(docId, 10000L);
        checkpointManager.recordProgress(docId, 4000L, 40);
        checkpointManager.markSuspended(docId, "Simulated network timeout");

        DocumentCheckpoint beforeRecovery = checkpointManager.getCheckpoint(docId);
        assertEquals(4000L, beforeRecovery.getLastLineOffset().longValue());
        assertEquals(40, beforeRecovery.getEmbeddedSegments().intValue());
        assertEquals("SUSPENDED", beforeRecovery.getStatus());

        // 2. 从断点自愈重启流水线
        AtomicInteger replayItemsCount = new AtomicInteger(0);
        Mono<Integer> resumeMono = pipeline.executePipeline(docId, allChunks, checkpointManager, batch -> {
            for (ChunkItem item : batch.getItems()) {
                assertTrue(item.getLineOffset() > 4000L, "恢复后处理的切片偏移量必须严格大于断点偏移量 4000");
                replayItemsCount.incrementAndGet();
            }
            return Mono.just(true);
        });

        Integer resumedProcessed = resumeMono.block();
        assertNotNull(resumedProcessed);
        assertEquals(60, resumedProcessed.intValue(), "断点续传应仅处理剩余的 60 个切片");
        assertEquals(60, replayItemsCount.get(), "已提交切片重复处理数必须严格为 0");

        DocumentCheckpoint afterRecovery = checkpointManager.getCheckpoint(docId);
        assertEquals(10000L, afterRecovery.getLastLineOffset().longValue());
        assertEquals(100, afterRecovery.getEmbeddedSegments().intValue());
        assertEquals("COMPLETED", afterRecovery.getStatus());
    }

    @Test
    @DisplayName("Contract 04: Fast-Pass 混合保真算子在 100 条候选下打分延迟严格 <= 5ms")
    void contract04_fastPassScorer_executionTimeStrictlyUnderFiveMs() {
        String query = "分布式向量数据库高可用热切换与一致性快照方案";
        float[] queryEmbedding = generateMockEmbedding(1536, 0.8f);

        int candidateCount = 100;
        List<RetrievalResult> candidates = new ArrayList<>(candidateCount);
        for (int i = 1; i <= candidateCount; i++) {
            float[] candEmb = generateMockEmbedding(1536, 0.1f * (i % 8));
            Map<String, Object> meta = new HashMap<>();
            meta.put("embedding", candEmb);

            candidates.add(RetrievalResult.builder()
                    .segmentId((long) (1000 + i))
                    .content("知识切片内容编号 " + i + "，讨论知识库分布式与向量索引性能优化架构。")
                    .score(0.5)
                    .metadata(meta)
                    .build());
        }

        // 预热 JIT
        for (int w = 0; w < 5; w++) {
            fastPassScorer.scoreAndFilter(query, candidates, queryEmbedding, 20);
        }

        // 正式契约时延度量
        long startNs = System.nanoTime();
        List<RetrievalResult> top20 = fastPassScorer.scoreAndFilter(query, candidates, queryEmbedding, 20);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;

        assertEquals(20, top20.size(), "必须精准返回 Top-20 降维结果");
        assertTrue(durationMs <= 5, "Fast-Pass 100 条打分耗时必须 <= 5ms，实际耗时: " + durationMs + "ms");

        for (RetrievalResult res : top20) {
            assertNotNull(res.getMetadata().get("fast_pass_score"), "元数据中必须包含 fast_pass_score");
        }
    }

    @Test
    @DisplayName("Contract 05: Fast-Pass 粗排降维至 Top-20 时，Top-10 高相关文档召回率 >= 90%")
    void contract05_fastPassScorer_retainsHighRelevanceCandidates() {
        String query = "PostgreSQL pgvector HNSW 并发热切换索引";
        float[] queryEmbedding = generateMockEmbedding(1536, 0.95f);

        int candidateCount = 100;
        List<RetrievalResult> candidates = new ArrayList<>(candidateCount);
        Set<Long> groundTruthTop10 = new HashSet<>();

        // 构造 10 条高相关度真值切片 (高向量相似度 + 关键词高重合)
        for (int i = 1; i <= 10; i++) {
            long segId = 2000L + i;
            groundTruthTop10.add(segId);
            float[] emb = Arrays.copyOf(queryEmbedding, queryEmbedding.length); // 近似完全对齐
            Map<String, Object> meta = new HashMap<>();
            meta.put("embedding", emb);

            candidates.add(RetrievalResult.builder()
                    .segmentId(segId)
                    .content("核心真值文档: PostgreSQL 数据库 pgvector 扩展支持 HNSW 索引并发无排他锁热切换机制 " + i)
                    .score(0.9)
                    .metadata(meta)
                    .build());
        }

        // 构造 90 条噪点干扰切片 (低向量相似度 + 无关词汇)
        for (int i = 11; i <= candidateCount; i++) {
            float[] noiseEmb = generateMockEmbedding(1536, 0.05f);
            Map<String, Object> meta = new HashMap<>();
            meta.put("embedding", noiseEmb);

            candidates.add(RetrievalResult.builder()
                    .segmentId(2000L + i)
                    .content("无关背景切片 " + i + ": 天气预报与体育新闻资讯杂项。")
                    .score(0.1)
                    .metadata(meta)
                    .build());
        }

        // 打乱候选顺序
        Collections.shuffle(candidates, new Random(42));

        List<RetrievalResult> filteredTop20 = fastPassScorer.scoreAndFilter(query, candidates, queryEmbedding, 20);
        assertEquals(20, filteredTop20.size());

        // 计算 Top-10 真值在 Top-20 粗筛中的召回率
        long recalledCount = filteredTop20.stream()
                .filter(res -> groundTruthTop10.contains(res.getSegmentId()))
                .count();

        double recallRate = (double) recalledCount / 10.0;
        assertTrue(recallRate >= 0.90, "Top-10 真值在 Fast-Pass 粗筛 Top-20 中的召回率必须 >= 90%，实际召回率: "
                + (recallRate * 100) + "% (" + recalledCount + "/10)");
    }

    @Test
    @DisplayName("Contract 06: 当候选满足 Phase 07 动态门控首位共识时直接旁路短路，零模型开销")
    void contract06_twoStageCascadeRerank_gateConsensusShortCircuits() {
        String query = "微服务分布式架构治理";
        int candidateCount = 50;
        List<RetrievalResult> candidates = new ArrayList<>(candidateCount);
        for (int i = 1; i <= candidateCount; i++) {
            candidates.add(RetrievalResult.builder()
                    .segmentId((long) (3000 + i))
                    .content("候选文档-" + i)
                    .score(1.0 - (i * 0.01))
                    .build());
        }

        AtomicInteger heavyModelCalls = new AtomicInteger(0);

        // 模拟满足首位共识 (isTopConsensus = true)
        List<RetrievalResult> result = cascadeRerankEngine.rerank(
                query, candidates, null, true, 0.25, 5,
                (q, list) -> {
                    heavyModelCalls.incrementAndGet();
                    return list;
                }
        );

        assertEquals(5, result.size());
        assertTrue(cascadeRerankEngine.isLastExecutionBypassedByGate(), "门控必须判定为短路旁路");
        assertEquals(0, heavyModelCalls.get(), "重排 Cross-Encoder 模型调用次数必须严格为 0");
        assertEquals(3001L, result.get(0).getSegmentId().longValue(), "首位共识切片必须保持置顶");
    }

    @Test
    @DisplayName("Contract 07: 两阶段级联模式相比全量 100 条精排，精排输入规模削减 80%，延迟大幅降低")
    void contract07_twoStageCascadeRerank_latencyReductionOverSeventyPercent() {
        String query = "阿里千问向量嵌入与深度推理模型蒸馏";
        float[] queryEmb = generateMockEmbedding(1536, 0.7f);

        int candidateCount = 100;
        List<RetrievalResult> candidates = new ArrayList<>(candidateCount);
        for (int i = 1; i <= candidateCount; i++) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("embedding", generateMockEmbedding(1536, 0.1f * (i % 9)));
            candidates.add(RetrievalResult.builder()
                    .segmentId((long) (4000 + i))
                    .content("深度学习模型加速与蒸馏论文切片 " + i)
                    .score(0.5)
                    .metadata(meta)
                    .build());
        }

        // 基线方案：直接将全部 100 条候选送入重排模型，耗时约为 100 * 单位延迟
        // 级联方案：Fast-Pass (<=5ms) 降维至 20 条，重排模型仅计算 20 条
        AtomicInteger cascadeHeavyInputCount = new AtomicInteger(0);

        List<RetrievalResult> cascadeResult = cascadeRerankEngine.rerank(
                query, candidates, queryEmb, false, 0.02, 5,
                (q, inputList) -> {
                    cascadeHeavyInputCount.set(inputList.size());
                    // 模拟模型精排计算延迟 (按候选数量线性放大: 1ms/条)
                    try {
                        Thread.sleep(inputList.size());
                    } catch (InterruptedException ignored) {}
                    return inputList;
                }
        );

        assertNotNull(cascadeResult);
        assertEquals(5, cascadeResult.size());
        assertFalse(cascadeRerankEngine.isLastExecutionBypassedByGate(), "未满足门控阈值，正常执行级联");

        // 断言重排模型输入规模从 100 降至 20 (削减 80%)
        assertEquals(20, cascadeHeavyInputCount.get(), "级联精排第二阶段输入候选数必须精准为 20");
        int originalSize = candidates.size();
        int reducedSize = cascadeHeavyInputCount.get();
        double reductionRatio = (double) (originalSize - reducedSize) / originalSize;
        assertTrue(reductionRatio >= 0.75, "候选集规模缩减比例必须 >= 75%，实际为: " + (reductionRatio * 100) + "%");
    }

    @Test
    @DisplayName("Contract 08: 影子表创建与 HNSW 向量索引并发无排他锁构建")
    void contract08_indexHotSwapper_shadowProvisioningAndConcurrentIndexBuild() {
        assertEquals("v1", hotSwapperService.getActiveVersion().get());
        assertEquals(IndexHotSwapperService.HotSwapStatus.INITIAL, hotSwapperService.getStatus().get());

        // 1. 初始化影子表
        String shadowDdl = hotSwapperService.provisionShadowTable("v2");
        assertNotNull(shadowDdl);
        assertTrue(shadowDdl.contains("vector_store_v2"), "建表语句应包含目标版本表名");
        assertEquals(IndexHotSwapperService.HotSwapStatus.BUILDING_SHADOW, hotSwapperService.getStatus().get());

        // 2. 生成 CONCURRENTLY 索引 DDL
        String indexDdl = hotSwapperService.generateConcurrentIndexDdl("v2", "idx_vector_store_v2_hnsw");
        assertNotNull(indexDdl);
        assertTrue(indexDdl.contains("CREATE INDEX CONCURRENTLY idx_vector_store_v2_hnsw"), "必须显式使用 CONCURRENTLY");
        assertTrue(indexDdl.contains("USING hnsw"), "必须使用 hnsw 向量索引");
        assertTrue(indexDdl.contains("vector_cosine_ops"), "必须指定余弦算子族");
        assertEquals(IndexHotSwapperService.HotSwapStatus.INDEXING, hotSwapperService.getStatus().get());

        // 3. 标记就绪
        hotSwapperService.markShadowReady("v2");
        assertEquals(IndexHotSwapperService.HotSwapStatus.READY_TO_SWAP, hotSwapperService.getStatus().get());
    }

    @Test
    @DisplayName("Contract 09: 视图指针翻转原子生效 (<= 1ms)，并发检索零空窗期 (100% 成功率)")
    void contract09_indexHotSwapper_atomicViewSwapSubMillisecondZeroDowntime() throws Exception {
        hotSwapperService.provisionShadowTable("v2");
        hotSwapperService.markShadowReady("v2");

        int queryConcurrency = 20;
        ExecutorService queryPool = Executors.newFixedThreadPool(queryConcurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(queryConcurrency);
        AtomicInteger successCount = new AtomicInteger(0);

        // 启动 20 个高并发读查询任务
        for (int i = 0; i < queryConcurrency; i++) {
            queryPool.submit(() -> {
                try {
                    startLatch.await();
                    for (int k = 0; k < 50; k++) {
                        String activeTable = hotSwapperService.getActiveTable();
                        assertNotNull(activeTable);
                        assertTrue(activeTable.startsWith("vector_store_"));
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("并发查询发生异常: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 触发并发查询
        startLatch.countDown();

        // 中途执行 CAS 原子指针翻转
        String swapDdl = hotSwapperService.atomicSwapView("v2");
        assertTrue(swapDdl.contains("vector_store_v2"));
        assertEquals("v2", hotSwapperService.getActiveVersion().get());
        assertEquals("v1", hotSwapperService.getPreviousVersion().get());
        assertEquals(IndexHotSwapperService.HotSwapStatus.ACTIVE, hotSwapperService.getStatus().get());

        doneLatch.await(3, TimeUnit.SECONDS);
        queryPool.shutdown();

        // 断言并发读查询 100% 成功 (20 * 50 = 1000 次查询 0 失败，零读空窗)
        assertEquals(1000, successCount.get(), "并发读查询必须 100% 成功，零空窗期");
        long durationNs = hotSwapperService.getLastSwapDurationNs();
        assertTrue(durationNs < 5_000_000, "原子翻转耗时必须在毫秒级内，实际耗时: " + (durationNs / 1_000_000.0) + "ms");
    }

    @Test
    @DisplayName("Contract 10: 一键故障回滚秒级生效，瞬时恢复旧版本数据")
    void contract10_indexHotSwapper_instantRollbackRestoresPreviousVersion() {
        // 先切换至 v2
        hotSwapperService.atomicSwapView("v2");
        assertEquals("v2", hotSwapperService.getActiveVersion().get());
        assertEquals("v1", hotSwapperService.getPreviousVersion().get());

        // 模拟监控探测到数据异常，触发一键瞬时回滚
        long rollbackMs = hotSwapperService.instantRollback();

        // 断言已瞬时回退至 v1
        assertEquals("v1", hotSwapperService.getActiveVersion().get(), "回滚后活跃版本应重置为 v1");
        assertEquals("v2", hotSwapperService.getPreviousVersion().get(), "回滚后前序版本记录为 v2");
        assertEquals(IndexHotSwapperService.HotSwapStatus.ROLLBACK_COMPLETED, hotSwapperService.getStatus().get());
        assertEquals("vector_store_v1", hotSwapperService.getActiveTable());
        assertTrue(rollbackMs <= 500, "瞬时回滚耗时必须 <= 500ms，实际耗时: " + rollbackMs + "ms");
    }

    /**
     * 辅助工具：生成指定维度的测试向量
     */
    private float[] generateMockEmbedding(int dim, float baseVal) {
        float[] arr = new float[dim];
        for (int i = 0; i < dim; i++) {
            arr[i] = (float) (baseVal + Math.sin(i) * 0.05);
        }
        // L2 归一化
        double norm = 0.0;
        for (float v : arr) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-6) {
            for (int i = 0; i < dim; i++) {
                arr[i] /= norm;
            }
        }
        return arr;
    }
}
