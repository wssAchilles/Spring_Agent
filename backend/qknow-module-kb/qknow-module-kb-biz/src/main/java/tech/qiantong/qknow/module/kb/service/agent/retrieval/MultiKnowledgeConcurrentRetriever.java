package tech.qiantong.qknow.module.kb.service.agent.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.*;
import java.util.concurrent.*;

/**
 * 企业级多知识库并发检索与自适应超时兜底引擎 (Multi-Knowledge Concurrent Retriever)
 * <p>
 * 基于 Java 21 原生虚拟线程并发：
 * 1. 支持同时向多个企业知识库发起并行切片召回，延迟不随知识库数量线性叠加；
 * 2. 具备单库独立软超时 (Soft Timeout) 隔离，防止单一慢库拖死整条响应链；
 * 3. 具备全局硬超时 (Hard Timeout) 截断兜底，保证严苛的 P99 响应时间 SLA；
 * 4. 产出结构化不可变汇总凭单 Record，包含各库切片、去重列表与降级指标。
 * </p>
 */
@Slf4j
@Component
public class MultiKnowledgeConcurrentRetriever {

    private final IKmcApiService kmcApiService;

    /**
     * 单知识库默认软超时 (毫秒)
     */
    public static final long DEFAULT_SINGLE_KB_TIMEOUT_MS = 2000L;

    /**
     * 全局默认硬超时 (毫秒)
     */
    public static final long DEFAULT_GLOBAL_TIMEOUT_MS = 3000L;

    @Autowired
    public MultiKnowledgeConcurrentRetriever(IKmcApiService kmcApiService) {
        this.kmcApiService = kmcApiService;
    }

    /**
     * 单知识库召回结果 Record
     */
    public record SingleKbRecall(
            Long knowledgeId,
            boolean success,
            boolean timedOut,
            List<RetrieveResult> results,
            String errorMessage,
            long elapsedMillis
    ) {
        public static SingleKbRecall success(Long kbId, List<RetrieveResult> results, long elapsed) {
            return new SingleKbRecall(kbId, true, false, results != null ? results : Collections.emptyList(), null, elapsed);
        }

        public static SingleKbRecall timeout(Long kbId, long elapsed) {
            return new SingleKbRecall(kbId, false, true, Collections.emptyList(), "检索超时超过门限", elapsed);
        }

        public static SingleKbRecall failure(Long kbId, String error, long elapsed) {
            return new SingleKbRecall(kbId, false, false, Collections.emptyList(), error, elapsed);
        }
    }

    /**
     * 多知识库并发检索汇总凭单 Record
     */
    public record MultiKbRetrievalResult(
            String query,
            List<RetrieveResult> mergedChunks,
            Map<Long, Integer> recallCountPerKb,
            List<Long> timedOutKbIds,
            List<Long> failedKbIds,
            int totalRecallCount,
            long totalElapsedMillis
    ) {}

    /**
     * 并发检索多个知识库
     *
     * @param knowledgeBaseIds 目标知识库 ID 列表
     * @param query            检索查询词
     * @return 汇总检索结果凭单
     */
    public MultiKbRetrievalResult retrieveConcurrently(List<Long> knowledgeBaseIds, String query) {
        return retrieveConcurrently(knowledgeBaseIds, query, DEFAULT_SINGLE_KB_TIMEOUT_MS, DEFAULT_GLOBAL_TIMEOUT_MS);
    }

    /**
     * 并发检索多个知识库 (支持自定义超时)
     *
     * @param knowledgeBaseIds  目标知识库 ID 列表
     * @param query             检索查询词
     * @param singleTimeoutMs   单库超时时间 (毫秒)
     * @param globalTimeoutMs   全局硬超时时间 (毫秒)
     * @return 汇总检索结果凭单
     */
    public MultiKbRetrievalResult retrieveConcurrently(
            List<Long> knowledgeBaseIds,
            String query,
            long singleTimeoutMs,
            long globalTimeoutMs
    ) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return new MultiKbRetrievalResult(query, Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptyList(), Collections.emptyList(), 0, 0L);
        }

        // 去重并滤除空 ID
        List<Long> targetIds = knowledgeBaseIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (targetIds.isEmpty()) {
            return new MultiKbRetrievalResult(query, Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptyList(), Collections.emptyList(), 0, 0L);
        }

        long startNs = System.currentTimeMillis();
        Map<Long, SingleKbRecall> recallMap = new ConcurrentHashMap<>();

        // 使用 Java 21 虚拟线程原生并发派发
        try (ExecutorService vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (Long kbId : targetIds) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    long taskStart = System.currentTimeMillis();
                    try {
                        // 利用 Future 绑定单库软超时
                        Future<List<RetrieveResult>> singleFuture = vThreadExecutor.submit(() ->
                                kmcApiService.recallTest(kbId, query)
                        );

                        List<RetrieveResult> hits = singleFuture.get(singleTimeoutMs, TimeUnit.MILLISECONDS);
                        long elapsed = System.currentTimeMillis() - taskStart;
                        recallMap.put(kbId, SingleKbRecall.success(kbId, hits, elapsed));
                        log.debug("[多库检索] 知识库 ID={} 召回成功, 切片数={}, 耗时={}ms", kbId, hits != null ? hits.size() : 0, elapsed);

                    } catch (TimeoutException te) {
                        long elapsed = System.currentTimeMillis() - taskStart;
                        log.warn("[多库检索] 知识库 ID={} 发生单库软超时降级 ({}ms)", kbId, singleTimeoutMs);
                        recallMap.put(kbId, SingleKbRecall.timeout(kbId, elapsed));
                    } catch (Throwable t) {
                        long elapsed = System.currentTimeMillis() - taskStart;
                        log.warn("[多库检索] 知识库 ID={} 检索异常: {}", kbId, t.getMessage());
                        recallMap.put(kbId, SingleKbRecall.failure(kbId, t.getMessage(), elapsed));
                    }
                }, vThreadExecutor);

                futures.add(future);
            }

            // 等待所有虚拟线程完成或全局硬超时截断
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(Math.max(1000L, globalTimeoutMs), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                log.warn("[多库检索] 触发全局硬超时截断 ({}ms)，提前汇聚已完成的知识库切片", globalTimeoutMs);
                for (Long kbId : targetIds) {
                    recallMap.putIfAbsent(kbId, SingleKbRecall.timeout(kbId, globalTimeoutMs));
                }
            } catch (Exception e) {
                log.error("[多库检索] 并发调度异常", e);
            }
        }

        // 汇聚切片并按 id 去重（保留最高得分副本），并按相似度得分降序排序
        Map<String, RetrieveResult> deduplicatedMap = new LinkedHashMap<>();
        Map<Long, Integer> recallCountPerKb = new LinkedHashMap<>();
        List<Long> timedOutKbIds = new ArrayList<>();
        List<Long> failedKbIds = new ArrayList<>();

        for (Long kbId : targetIds) {
            SingleKbRecall singleRecall = recallMap.get(kbId);
            if (singleRecall != null && singleRecall.success()) {
                int count = 0;
                for (RetrieveResult item : singleRecall.results()) {
                    if (item == null) continue;
                    count++;
                    String key = item.getId();
                    if (key == null || key.isBlank()) {
                        key = item.getContent() != null ? String.valueOf(item.getContent().hashCode()) : UUID.randomUUID().toString();
                    }
                    if (!deduplicatedMap.containsKey(key)) {
                        deduplicatedMap.put(key, item);
                    } else {
                        // 保留较高 score 的副本
                        RetrieveResult existing = deduplicatedMap.get(key);
                        double existingScore = existing.getScore() != null ? existing.getScore() : 0.0;
                        double currentScore = item.getScore() != null ? item.getScore() : 0.0;
                        if (currentScore > existingScore) {
                            deduplicatedMap.put(key, item);
                        }
                    }
                }
                recallCountPerKb.put(kbId, count);
            } else if (singleRecall != null && singleRecall.timedOut()) {
                timedOutKbIds.add(kbId);
                recallCountPerKb.put(kbId, 0);
            } else {
                failedKbIds.add(kbId);
                recallCountPerKb.put(kbId, 0);
            }
        }

        List<RetrieveResult> mergedChunks = new ArrayList<>(deduplicatedMap.values());
        mergedChunks.sort((a, b) -> {
            double sa = a.getScore() != null ? a.getScore() : -1.0;
            double sb = b.getScore() != null ? b.getScore() : -1.0;
            return Double.compare(sb, sa);
        });

        long totalElapsed = System.currentTimeMillis() - startNs;
        log.info("[多库检索] 并发检索完成: 计划库数={}, 成功切片总数={}, 超时库={}, 失败库={}, 总耗时={}ms",
                targetIds.size(), mergedChunks.size(), timedOutKbIds.size(), failedKbIds.size(), totalElapsed);

        return new MultiKbRetrievalResult(
                query,
                Collections.unmodifiableList(mergedChunks),
                Collections.unmodifiableMap(recallCountPerKb),
                Collections.unmodifiableList(timedOutKbIds),
                Collections.unmodifiableList(failedKbIds),
                mergedChunks.size(),
                totalElapsed
        );
    }
}
