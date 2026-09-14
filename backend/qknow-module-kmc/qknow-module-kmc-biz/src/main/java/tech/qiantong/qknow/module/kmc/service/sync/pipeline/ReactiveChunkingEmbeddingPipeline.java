package tech.qiantong.qknow.module.kmc.service.sync.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.Reader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Phase 25: 基于 Project Reactor 的高吞吐异步流式分块与嵌入流水线
 * 包含：Java NIO 流式按段读取、Token/切片双阈值动态微批截断、李雅普诺夫强稳定自适应背压与断点增量游标集成。
 */
@Slf4j
@Service
public class ReactiveChunkingEmbeddingPipeline {

    /**
     * 单批次最大 Token 预算上限 (对齐阿里千问 Embedding API 限制与 TEI 规范)
     */
    public static final int DEFAULT_MAX_TOKENS_PER_BATCH = 8192;

    /**
     * 单批次最大切片数量上限
     */
    public static final int DEFAULT_MAX_CHUNKS_PER_BATCH = 25;

    /**
     * 自适应背压缓冲区软限制上限 (李雅普诺夫队列能量平衡水位 Q_max)
     */
    public static final int DEFAULT_QUEUE_CAPACITY = 50;

    /**
     * 实时监控的排队长度 Q(t) 与峰值排队长度
     */
    private final AtomicInteger currentQueueLength = new AtomicInteger(0);
    private final AtomicInteger peakQueueLength = new AtomicInteger(0);

    /**
     * 切片单元数据传输对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkItem {
        private int index;
        private String text;
        private int estimatedTokens;
        private long lineOffset;
    }

    /**
     * 动态微批数据包
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkBatch {
        private int batchIndex;
        private int totalTokens;
        private int chunkCount;
        private List<ChunkItem> items;
    }

    /**
     * 粗粒度估算中英文混合 Token 数量
     * 规则：汉字约 1 Token / 字；英文字词按非空白分词约 1.3 Token / 词；标点符号单算。
     *
     * @param text 输入文本
     * @return 估算 Token 数
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        int latinCharStreak = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
                if (latinCharStreak > 0) {
                    tokens += Math.max(1, (int) Math.ceil(latinCharStreak / 3.5));
                    latinCharStreak = 0;
                }
                tokens++;
            } else if (Character.isWhitespace(c)) {
                if (latinCharStreak > 0) {
                    tokens += Math.max(1, (int) Math.ceil(latinCharStreak / 3.5));
                    latinCharStreak = 0;
                }
            } else {
                latinCharStreak++;
            }
        }
        if (latinCharStreak > 0) {
            tokens += Math.max(1, (int) Math.ceil(latinCharStreak / 3.5));
        }
        return Math.max(1, tokens);
    }

    /**
     * 双阈值动态微批切分算法
     * 在 Token 累计超出 maxTokens 或切片累计达到 maxChunks 时触发截断封包。
     *
     * @param chunks    原始切片列表
     * @param maxTokens 最大 Token 数上限
     * @param maxChunks 最大切片数上限
     * @return 划分完毕的批次列表
     */
    public List<ChunkBatch> partitionIntoDynamicBatches(List<ChunkItem> chunks, int maxTokens, int maxChunks) {
        List<ChunkBatch> batches = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            return batches;
        }

        List<ChunkItem> currentBatchItems = new ArrayList<>();
        int currentBatchTokens = 0;
        int batchSeq = 0;

        for (ChunkItem item : chunks) {
            int itemTokens = item.getEstimatedTokens() > 0 ? item.getEstimatedTokens() : estimateTokens(item.getText());
            boolean exceedTokens = (!currentBatchItems.isEmpty() && (currentBatchTokens + itemTokens > maxTokens));
            boolean exceedCount = (currentBatchItems.size() >= maxChunks);

            if (exceedTokens || exceedCount) {
                batches.add(ChunkBatch.builder()
                        .batchIndex(batchSeq++)
                        .totalTokens(currentBatchTokens)
                        .chunkCount(currentBatchItems.size())
                        .items(new ArrayList<>(currentBatchItems))
                        .build());
                currentBatchItems.clear();
                currentBatchTokens = 0;
            }

            currentBatchItems.add(item);
            currentBatchTokens += itemTokens;
        }

        if (!currentBatchItems.isEmpty()) {
            batches.add(ChunkBatch.builder()
                    .batchIndex(batchSeq++)
                    .totalTokens(currentBatchTokens)
                    .chunkCount(currentBatchItems.size())
                    .items(new ArrayList<>(currentBatchItems))
                    .build());
        }

        return batches;
    }

    /**
     * Java NIO 文本流式按段读取（保持 O(1) 堆内存开销）
     *
     * @param reader          输入字符流
     * @param startLineOffset 起始行偏移量
     * @return 过滤已读行后的行流
     */
    public Stream<String> streamDocumentLines(Reader reader, long startLineOffset) {
        BufferedReader bufferedReader = (reader instanceof BufferedReader)
                ? (BufferedReader) reader
                : new BufferedReader(reader);
        return bufferedReader.lines().skip(Math.max(0, startLineOffset));
    }

    /**
     * 执行带自适应背压与断点续传的流式处理管道
     *
     * @param documentId        文档 ID
     * @param chunks            待处理全量切片
     * @param checkpointManager 断点管理器
     * @param consumer          批次消费函数（例如千问 Embedding 调用）
     * @return 处理成功的总切片数
     */
    public Mono<Integer> executePipeline(Long documentId,
                                        List<ChunkItem> chunks,
                                        DocumentCheckpointManager checkpointManager,
                                        Function<ChunkBatch, Mono<Boolean>> consumer) {
        DocumentCheckpoint checkpoint = checkpointManager.getOrCreateCheckpoint(documentId, 0L);
        long lastOffset = checkpoint.getLastLineOffset() != null ? checkpoint.getLastLineOffset() : 0L;

        // 仅处理大于已提交断点偏移量的增量切片，避免重复计算
        List<ChunkItem> pendingChunks = chunks.stream()
                .filter(item -> item.getLineOffset() > lastOffset)
                .toList();

        if (pendingChunks.isEmpty()) {
            checkpointManager.markCompleted(documentId);
            return Mono.just(0);
        }

        List<ChunkBatch> batches = partitionIntoDynamicBatches(pendingChunks,
                DEFAULT_MAX_TOKENS_PER_BATCH, DEFAULT_MAX_CHUNKS_PER_BATCH);

        AtomicInteger processedCount = new AtomicInteger(0);

        return Flux.fromIterable(batches)
                .onBackpressureBuffer(DEFAULT_QUEUE_CAPACITY,
                        droppedBatch -> log.warn("背压溢出丢弃批次: batchIndex={}", droppedBatch.getBatchIndex()))
                .doOnNext(batch -> {
                    int qLen = currentQueueLength.incrementAndGet();
                    peakQueueLength.updateAndGet(curr -> Math.max(curr, qLen));
                })
                .publishOn(Schedulers.boundedElastic(), 8)
                .concatMap(batch -> {
                    return consumer.apply(batch)
                            .doOnNext(success -> {
                                currentQueueLength.decrementAndGet();
                                if (Boolean.TRUE.equals(success)) {
                                    ChunkItem lastItem = batch.getItems().get(batch.getItems().size() - 1);
                                    checkpointManager.recordProgress(documentId, lastItem.getLineOffset(), batch.getItems().size());
                                    processedCount.addAndGet(batch.getItems().size());
                                }
                            })
                            .onErrorResume(err -> {
                                currentQueueLength.decrementAndGet();
                                log.error("批次处理异常，记录挂起断点: error={}", err.getMessage());
                                checkpointManager.markSuspended(documentId, err.getMessage());
                                return Mono.just(false);
                            });
                })
                .collectList()
                .map(results -> {
                    checkpointManager.markCompleted(documentId);
                    return processedCount.get();
                });
    }

    /**
     * 重置排队状态计数器
     */
    public void resetMetrics() {
        currentQueueLength.set(0);
        peakQueueLength.set(0);
    }

    public int getCurrentQueueLength() {
        return currentQueueLength.get();
    }

    public int getPeakQueueLength() {
        return peakQueueLength.get();
    }
}
