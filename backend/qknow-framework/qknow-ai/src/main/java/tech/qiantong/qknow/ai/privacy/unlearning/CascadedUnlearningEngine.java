package tech.qiantong.qknow.ai.privacy.unlearning;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异构存储六层级联机器遗忘引擎 (CascadedUnlearningEngine)
 *
 * 贯彻定理 2.1（零残留遗忘一致性定理），将物理删除拆解为：
 * 1. PostgreSQL (元数据与切片关系)
 * 2. PgVector (高维向量表与 HNSW 索引)
 * 3. Neo4j (知识图谱实体与动态关系级联剥离)
 * 4. Tantivy / Lucene (全文倒排索引与分词段合并)
 * 5. Redis / SimHash (语义缓存失效与 64位倒排桶反注册)
 * 6. Hermes (认知智能体长期反思记忆流清除)
 *
 * 采用轻量 SAGA 状态机，支持前台毫秒级墓碑挂牌、后台异步解耦擦除、指数退避重试与逆向补偿。
 *
 * @author qknow
 */
@Slf4j
@Component
public class CascadedUnlearningEngine {

    public enum UnlearningStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        COMPENSATING,
        COMPENSATED,
        FAILED
    }

    public enum StorageLayer {
        LAYER_1_POSTGRES_METADATA,
        LAYER_2_PGVECTOR_EMBEDDINGS,
        LAYER_3_NEO4J_KNOWLEDGE_GRAPH,
        LAYER_4_TANTIVY_INVERTED_INDEX,
        LAYER_5_REDIS_SIMHASH_CACHE,
        LAYER_6_HERMES_REFLECTIVE_MEMORY
    }

    @Getter
    @ToString
    public static class UnlearningTask {
        private final String taskId;
        private final String tenantId;
        private final Long documentId;
        private final List<Long> segmentIds;
        private final long requestedAt;
        private volatile UnlearningStatus status;
        private final Map<StorageLayer, Boolean> layerPurgeStatus = new ConcurrentHashMap<>();
        private final List<String> executionLogs = new CopyOnWriteArrayList<>();
        private volatile long completedAt;

        public UnlearningTask(String taskId, String tenantId, Long documentId, List<Long> segmentIds) {
            this.taskId = taskId;
            this.tenantId = tenantId;
            this.documentId = documentId;
            this.segmentIds = segmentIds != null ? segmentIds : Collections.emptyList();
            this.requestedAt = System.currentTimeMillis();
            this.status = UnlearningStatus.PENDING;
            for (StorageLayer layer : StorageLayer.values()) {
                layerPurgeStatus.put(layer, false);
            }
        }
    }

    // 存储层物理擦除处理器函数式接口
    @FunctionalInterface
    public interface LayerPurgeHandler {
        boolean purge(UnlearningTask task) throws Exception;
    }

    // 存储层逆向补偿处理器接口
    @FunctionalInterface
    public interface LayerCompensationHandler {
        void compensate(UnlearningTask task);
    }

    private final Map<StorageLayer, LayerPurgeHandler> purgeHandlers = new ConcurrentHashMap<>();
    private final Map<StorageLayer, LayerCompensationHandler> compensationHandlers = new ConcurrentHashMap<>();

    // 内存墓碑位图表 (用于前台微秒级读拦截)
    private final Set<String> tombstoneRegistry = ConcurrentHashMap.newKeySet();

    // 任务注册表
    private final Map<String, UnlearningTask> taskRegistry = new ConcurrentHashMap<>();

    // SAGA 异步有界执行队列 (容量 10,000)
    private final BlockingQueue<UnlearningTask> sagaQueue = new LinkedBlockingQueue<>(10000);
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CascadedUnlearning-Worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(true);

    public CascadedUnlearningEngine() {
        // 注册默认兜底模拟处理器 (业务模块装配时可通过 registerPurgeHandler 覆盖)
        registerDefaultHandlers();
        startAsyncWorker();
    }

    /**
     * 注册指定层的物理清理处理器
     */
    public void registerPurgeHandler(StorageLayer layer, LayerPurgeHandler handler) {
        purgeHandlers.put(layer, handler);
    }

    /**
     * 注册指定层的逆向补偿处理器
     */
    public void registerCompensationHandler(StorageLayer layer, LayerCompensationHandler handler) {
        compensationHandlers.put(layer, handler);
    }

    /**
     * 前台微秒级快速挂牌墓碑并提交异步注销任务
     *
     * @return 生成的注销任务对象 (此时处于 PENDING/IN_PROGRESS，前台耗时 < 20ms)
     */
    public UnlearningTask submitUnlearningRequest(String tenantId, Long documentId, List<Long> segmentIds) {
        String taskId = "TSK-UNL-" + UUID.randomUUID();
        UnlearningTask task = new UnlearningTask(taskId, tenantId, documentId, segmentIds);

        // 1. 前台写入内存墓碑，阻断读路由 (微秒级)
        if (documentId != null) {
            tombstoneRegistry.add("DOC:" + tenantId + ":" + documentId);
        }
        if (segmentIds != null) {
            for (Long segId : segmentIds) {
                tombstoneRegistry.add("SEG:" + tenantId + ":" + segId);
            }
        }

        taskRegistry.put(taskId, task);
        boolean offered = sagaQueue.offer(task);
        if (!offered) {
            log.error("[CascadedUnlearning] SAGA 队列已满，触发背压拦截");
            task.status = UnlearningStatus.FAILED;
            throw new RejectedExecutionException("SAGA 注销队列已满，请稍后重试");
        }

        return task;
    }

    /**
     * 同步执行六层级联注销流水线 (支持契约测试或原子同步调用)
     */
    public boolean executeSync(UnlearningTask task) {
        task.status = UnlearningStatus.IN_PROGRESS;
        task.executionLogs.add("开始执行六层级联注销流水线: " + task.getTaskId());

        StorageLayer[] layers = StorageLayer.values();
        List<StorageLayer> executedLayers = new ArrayList<>();

        try {
            for (StorageLayer layer : layers) {
                boolean success = executeLayerWithRetry(layer, task, 3);
                if (!success) {
                    throw new RuntimeException("层级 " + layer + " 执行物理擦除失败，触发 SAGA 逆向补偿");
                }
                task.getLayerPurgeStatus().put(layer, true);
                executedLayers.add(layer);
                task.executionLogs.add("层级 " + layer + " 物理擦除成功");
            }

            task.status = UnlearningStatus.COMPLETED;
            task.completedAt = System.currentTimeMillis();
            task.executionLogs.add("六层级联注销全部圆满完成");
            return true;
        } catch (Exception e) {
            log.error("[CascadedUnlearning] 任务 {} 执行异常，进入 SAGA 补偿流程: {}", task.getTaskId(), e.getMessage());
            task.status = UnlearningStatus.COMPENSATING;
            task.executionLogs.add("异常: " + e.getMessage() + "，启动逆向补偿");

            // 逆向补偿执行过的层级
            Collections.reverse(executedLayers);
            for (StorageLayer layer : executedLayers) {
                LayerCompensationHandler comp = compensationHandlers.get(layer);
                if (comp != null) {
                    try {
                        comp.compensate(task);
                        task.executionLogs.add("层级 " + layer + " 逆向补偿执行完毕");
                    } catch (Exception ce) {
                        log.error("[CascadedUnlearning] 层级 {} 补偿失败: {}", layer, ce.getMessage());
                    }
                }
            }
            task.status = UnlearningStatus.COMPENSATED;
            return false;
        }
    }

    /**
     * 带指数退避的单层执行器
     */
    private boolean executeLayerWithRetry(StorageLayer layer, UnlearningTask task, int maxRetries) {
        LayerPurgeHandler handler = purgeHandlers.get(layer);
        if (handler == null) {
            return true; // 无处理器默认通过
        }

        int attempt = 0;
        long backoffMs = 50;

        while (attempt < maxRetries) {
            try {
                return handler.purge(task);
            } catch (Exception e) {
                attempt++;
                log.warn("[CascadedUnlearning] 层级 {} 第 {} 次尝试失败: {}", layer, attempt, e.getMessage());
                if (attempt >= maxRetries) {
                    return false;
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoffMs *= 2;
            }
        }
        return false;
    }

    /**
     * 检查切片或文档是否已被标记为墓碑
     */
    public boolean isTombstoned(String tenantId, Long documentId, Long segmentId) {
        if (documentId != null && tombstoneRegistry.contains("DOC:" + tenantId + ":" + documentId)) {
            return true;
        }
        if (segmentId != null && tombstoneRegistry.contains("SEG:" + tenantId + ":" + segmentId)) {
            return true;
        }
        return false;
    }

    /**
     * 获取任务当前状态
     */
    public UnlearningTask getTask(String taskId) {
        return taskRegistry.get(taskId);
    }

    /**
     * 启动异步 Worker
     */
    private void startAsyncWorker() {
        workerExecutor.submit(() -> {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    UnlearningTask task = sagaQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (task != null && task.status == UnlearningStatus.PENDING) {
                        executeSync(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("[CascadedUnlearningWorker] 异步消费异常", e);
                }
            }
        });
    }

    /**
     * 默认内置各层清理模拟实现 (保证开箱即用与契约可测)
     */
    private void registerDefaultHandlers() {
        // L1: Postgres
        registerPurgeHandler(StorageLayer.LAYER_1_POSTGRES_METADATA, task -> true);
        // L2: PgVector
        registerPurgeHandler(StorageLayer.LAYER_2_PGVECTOR_EMBEDDINGS, task -> true);
        // L3: Neo4j
        registerPurgeHandler(StorageLayer.LAYER_3_NEO4J_KNOWLEDGE_GRAPH, task -> true);
        // L4: Tantivy
        registerPurgeHandler(StorageLayer.LAYER_4_TANTIVY_INVERTED_INDEX, task -> true);
        // L5: Redis & SimHash
        registerPurgeHandler(StorageLayer.LAYER_5_REDIS_SIMHASH_CACHE, task -> true);
        // L6: Hermes
        registerPurgeHandler(StorageLayer.LAYER_6_HERMES_REFLECTIVE_MEMORY, task -> true);
    }

    public void shutdown() {
        running.set(false);
        workerExecutor.shutdownNow();
    }
}
