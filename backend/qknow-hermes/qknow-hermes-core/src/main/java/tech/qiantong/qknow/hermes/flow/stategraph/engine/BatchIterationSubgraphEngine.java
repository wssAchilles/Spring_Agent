package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * 批处理迭代子图引擎 (BatchIterationSubgraphEngine)
 * 具备 max_map_length 上限拦截、信号量微批背压与 100% 槽位单调保序聚拢能力
 */
@Slf4j
@Component
public class BatchIterationSubgraphEngine {

    @FunctionalInterface
    public interface SubgraphItemProcessor<T> {
        NodeRunResultBO process(T item, int index, StateGraphContext context) throws Exception;
    }

    private final int maxMapLength;
    private final int concurrencyLimit;
    private final ExecutorService executorService;

    public BatchIterationSubgraphEngine() {
        this(1024, 16);
    }

    public BatchIterationSubgraphEngine(int maxMapLength, int concurrencyLimit) {
        this.maxMapLength = Math.max(maxMapLength, 1);
        this.concurrencyLimit = Math.max(concurrencyLimit, 1);
        this.executorService = Executors.newFixedThreadPool(
                this.concurrencyLimit,
                r -> {
                    Thread t = new Thread(r, "batch-subgraph-worker-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    /**
     * 并发批处理 Map-Reduce 算子
     *
     * @param items 输入集合
     * @param context 执行上下文
     * @param processor 单项处理算子
     * @param tolerant 是否容忍单项失败（true: 容忍并占位; false: 快速失败抛出异常）
     * @return 严格保序的结果列表
     */
    public <T> List<NodeRunResultBO> executeMapReduce(
            List<T> items,
            StateGraphContext context,
            SubgraphItemProcessor<T> processor,
            boolean tolerant) {

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 防线一：输入集合大小上限硬拦截，防止 OOM 事故
        int size = items.size();
        if (size > maxMapLength) {
            String msg = String.format("[BatchSubgraph] 输入列表长度 %d 超过安全上限 %d (ERR_SUBGRAPH_LENGTH_EXCEEDED)",
                    size, maxMapLength);
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }

        // 2. 预分配固定长度槽位数组，实现代数保序聚拢
        NodeRunResultBO[] slotArray = new NodeRunResultBO[size];
        Semaphore semaphore = new Semaphore(concurrencyLimit);
        List<CompletableFuture<Void>> futures = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            final int index = i;
            final T item = items.get(i);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    try {
                        NodeRunResultBO res = processor.process(item, index, context);
                        slotArray[index] = res;
                    } catch (Exception e) {
                        log.error("[BatchSubgraph] 分片 [{}] 执行异常: {}", index, e.getMessage());
                        if (!tolerant) {
                            throw new CompletionException(e);
                        }
                        NodeRunResultBO errBO = new NodeRunResultBO();
                        errBO.setNodeUuid("subgraph-err-slot-" + index);
                        errBO.setErrorMessage(e.getMessage());
                        slotArray[index] = errBO;
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
            }, executorService);

            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException ce) {
            log.error("[BatchSubgraph] 批处理任务并发执行失败: {}", ce.getMessage());
            throw ce;
        }

        // 3. 槽位保序规约转换为 List
        List<NodeRunResultBO> resultList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            resultList.add(slotArray[i]);
        }
        return Collections.unmodifiableList(resultList);
    }
}
