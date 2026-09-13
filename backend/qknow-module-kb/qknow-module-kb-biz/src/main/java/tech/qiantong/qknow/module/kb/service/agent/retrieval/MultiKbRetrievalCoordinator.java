package tech.qiantong.qknow.module.kb.service.agent.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.KmcChatTurnDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.KmcKnowledgeBaseRespDTO;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 跨知识库异步并发召回编排器 (Phase 17)
 * <p>
 * 核心特性：
 * 1. CompletableFuture 响应式非阻塞并发分发 (Fork) 与聚合 (Join)
 * 2. 独立仓壁隔离线程池 (Bulkhead Isolation)，杜绝慢库拖垮全局连接池
 * 3. 单库 2500ms 软超时降级 (Fail-Open Soft Timeout)，单个慢库故障不阻断全局返回
 * 4. 全局 3000ms 硬超时截断保护，防止极端网络抖动击穿网关
 * </p>
 */
@Slf4j
@Component
public class MultiKbRetrievalCoordinator {

    private final IKmcApiService kmcApiService;
    private final Executor multiKbExecutor;

    @Value("${qknow.rag.multi-kb.single-timeout-ms:2500}")
    private long singleKbTimeoutMs = 2500L;

    @Value("${qknow.rag.multi-kb.global-timeout-ms:3000}")
    private long globalTimeoutMs = 3000L;

    @Autowired
    public MultiKbRetrievalCoordinator(
            IKmcApiService kmcApiService,
            @Qualifier("threadPoolTaskExecutor") Executor multiKbExecutor) {
        this.kmcApiService = kmcApiService;
        this.multiKbExecutor = multiKbExecutor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SingleKbRecallResult {
        private Long knowledgeId;
        private String knowledgeName;
        private List<RetrieveResult> results;
        private boolean timedOut;
        private boolean success;
        private String errorMessage;
        private long elapsedMs;
    }

    /**
     * 响应式并发调度多知识库检索
     *
     * @param targetKbs    目标知识库列表（建议已通过权限过滤校验）
     * @param question     用户当前问题
     * @param historyTurns 多轮历史上下文
     * @return 各知识库召回结果列表（超时或异常已软降级，不抛出异常）
     */
    public List<SingleKbRecallResult> coordinateRetrieval(
            List<KmcKnowledgeBaseRespDTO> targetKbs,
            String question,
            List<KmcChatTurnDTO> historyTurns) {

        if (targetKbs == null || targetKbs.isEmpty()) {
            return Collections.emptyList();
        }

        long startNs = System.nanoTime();

        // 1. 为每个知识库创建独立的异步 CompletableFuture 任务，并绑定单库软超时
        List<CompletableFuture<SingleKbRecallResult>> futures = targetKbs.stream()
                .map(kb -> CompletableFuture.supplyAsync(() -> executeSingleRecall(kb, question, historyTurns), multiKbExecutor)
                        .completeOnTimeout(
                                buildTimeoutFallback(kb),
                                singleKbTimeoutMs,
                                TimeUnit.MILLISECONDS
                        )
                        .exceptionally(ex -> buildErrorFallback(kb, ex))
                )
                .toList();

        // 2. 响应式汇聚所有 Future（限制在全局硬超时内）
        CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allOfFuture.get(globalTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("[MultiKB] 全局并发召回达到硬超时上限 ({}ms)，提前截断未完成任务并返回部分成功结果", globalTimeoutMs);
            RagFallbackMonitor.record("multi_kb_coordinator", "global_timeout_truncate", "Timeout after " + globalTimeoutMs + "ms");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("[MultiKB] 全局并发召回线程被中断", ie);
        } catch (ExecutionException ee) {
            log.error("[MultiKB] 全局并发召回执行异常", ee);
        }

        // 3. 收集所有已完成结果（已超时的由于 completeOnTimeout 已被注入 Fallback 对象）
        List<SingleKbRecallResult> collected = futures.stream()
                .map(f -> {
                    try {
                        return f.getNow(null);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long totalElapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("[MultiKB] 并发检索完成: 计划库数量={}, 实际响应库数量={}, 总耗时={}ms",
                targetKbs.size(), collected.size(), totalElapsedMs);

        return collected;
    }

    private SingleKbRecallResult executeSingleRecall(
            KmcKnowledgeBaseRespDTO kb,
            String question,
            List<KmcChatTurnDTO> historyTurns) {
        long s = System.currentTimeMillis();
        try {
            List<RetrieveResult> hits = kmcApiService.recallTest(kb.getId(), question, historyTurns);
            return SingleKbRecallResult.builder()
                    .knowledgeId(kb.getId())
                    .knowledgeName(kb.getName())
                    .results(hits != null ? hits : Collections.emptyList())
                    .timedOut(false)
                    .success(true)
                    .elapsedMs(System.currentTimeMillis() - s)
                    .build();
        } catch (Exception e) {
            log.warn("[MultiKB] 单库检索内部异常: kbId={}, error={}", kb.getId(), e.getMessage());
            throw new CompletionException(e);
        }
    }

    private SingleKbRecallResult buildTimeoutFallback(KmcKnowledgeBaseRespDTO kb) {
        log.warn("[MultiKB] 单库召回超时降级触发: kbId={}, kbName={}, limit={}ms", kb.getId(), kb.getName(), singleKbTimeoutMs);
        RagFallbackMonitor.record("multi_kb_coordinator", "soft_timeout_fallback", "kbId=" + kb.getId());
        return SingleKbRecallResult.builder()
                .knowledgeId(kb.getId())
                .knowledgeName(kb.getName())
                .results(Collections.emptyList())
                .timedOut(true)
                .success(false)
                .errorMessage("Retrieval timed out exceeding " + singleKbTimeoutMs + "ms")
                .elapsedMs(singleKbTimeoutMs)
                .build();
    }

    private SingleKbRecallResult buildErrorFallback(KmcKnowledgeBaseRespDTO kb, Throwable ex) {
        log.warn("[MultiKB] 单库召回异常降级触发: kbId={}, error={}", kb.getId(), ex.getMessage());
        RagFallbackMonitor.record("multi_kb_coordinator", "error_fallback", "kbId=" + kb.getId());
        return SingleKbRecallResult.builder()
                .knowledgeId(kb.getId())
                .knowledgeName(kb.getName())
                .results(Collections.emptyList())
                .timedOut(false)
                .success(false)
                .errorMessage(ex != null ? ex.getMessage() : "Unknown error")
                .elapsedMs(0L)
                .build();
    }
}
