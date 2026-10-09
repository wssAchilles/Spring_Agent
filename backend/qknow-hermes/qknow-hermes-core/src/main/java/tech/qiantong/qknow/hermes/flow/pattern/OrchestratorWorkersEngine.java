package tech.qiantong.qknow.hermes.flow.pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * Anthropic Orchestrator-Workers 主从任务动态分工与并发执行引擎
 * <p>
 * 参考 Anthropic《Building Effective Agents》权威架构：
 * 1. Orchestrator 将宏观复杂任务拆解为若干独立子任务 (Subtasks)；
 * 2. 借助 Java 21 原生虚拟线程 (Virtual Threads) 并发分发给各专业领域 Worker 并行执行；
 * 3. 具备单个 Worker 超时熔断与隔离保护，防止单点故障阻塞全局；
 * 4. 汇总器 (Reducer) 统一聚合并综合各 Worker 产出，生成结构化决策结果。
 * </p>
 */
@Slf4j
@Component
public class OrchestratorWorkersEngine {

    /**
     * 单个子任务最大超时时间 (毫秒)
     */
    public static final long DEFAULT_WORKER_TIMEOUT_MILLIS = 30_000L;

    /**
     * 子任务定义 Record
     */
    public record SubTask(
            String taskId,
            String title,
            String workerType,
            Map<String, Object> inputParams
    ) {
        public SubTask {
            Objects.requireNonNull(taskId, "taskId 不能为空");
            Objects.requireNonNull(title, "title 不能为空");
            if (workerType == null) {
                workerType = "DEFAULT";
            }
            if (inputParams == null) {
                inputParams = Collections.emptyMap();
            }
        }
    }

    /**
     * 子任务执行产出 Record
     */
    public record WorkerOutput(
            String taskId,
            boolean success,
            Map<String, Object> output,
            String errorMessage,
            long durationMillis
    ) {
        public static WorkerOutput success(String taskId, Map<String, Object> output, long duration) {
            return new WorkerOutput(taskId, true, output != null ? output : Collections.emptyMap(), null, duration);
        }

        public static WorkerOutput failure(String taskId, String error, long duration) {
            return new WorkerOutput(taskId, false, Collections.emptyMap(), error, duration);
        }
    }

    /**
     * 主从任务协同执行最终凭单 Record
     */
    public record OrchestratedExecutionResult(
            boolean allSucceeded,
            String summary,
            Map<String, WorkerOutput> workerOutputs,
            long totalDurationMillis
    ) {}

    /**
     * 编排器任务拆解 Planner 接口
     */
    @FunctionalInterface
    public interface OrchestratorPlanner {
        List<SubTask> planSubTasks(String rootTask);
    }

    /**
     * 专业 Worker 执行器接口
     */
    @FunctionalInterface
    public interface WorkerExecutor {
        Map<String, Object> executeSubTask(SubTask subTask) throws Exception;
    }

    /**
     * 结果聚合器 Reducer 接口
     */
    @FunctionalInterface
    public interface AggregatorReducer {
        String reduce(String rootTask, Map<String, WorkerOutput> workerOutputs);
    }

    /**
     * 执行 Orchestrator-Workers 动态分工与并发求解
     *
     * @param rootTask 宏观根任务提示词
     * @param planner  任务拆解器
     * @param worker   子任务执行器
     * @param reducer  成果综合聚合器
     * @return 最终编排结果
     */
    public OrchestratedExecutionResult execute(
            String rootTask,
            OrchestratorPlanner planner,
            WorkerExecutor worker,
            AggregatorReducer reducer
    ) {
        return execute(rootTask, planner, worker, reducer, DEFAULT_WORKER_TIMEOUT_MILLIS);
    }

    /**
     * 执行 Orchestrator-Workers 动态分工与并发求解 (指定单任务超时时间)
     *
     * @param rootTask      宏观根任务提示词
     * @param planner       任务拆解器
     * @param worker        子任务执行器
     * @param reducer       成果综合聚合器
     * @param timeoutMillis 单个 Worker 超时毫秒数
     * @return 最终编排结果
     */
    public OrchestratedExecutionResult execute(
            String rootTask,
            OrchestratorPlanner planner,
            WorkerExecutor worker,
            AggregatorReducer reducer,
            long timeoutMillis
    ) {
        Objects.requireNonNull(rootTask, "rootTask 不能为空");
        Objects.requireNonNull(planner, "planner 不能为空");
        Objects.requireNonNull(worker, "worker 不能为空");
        Objects.requireNonNull(reducer, "reducer 不能为空");

        long startTotal = System.currentTimeMillis();

        // 1. 拆解子任务
        List<SubTask> subTasks = planner.planSubTasks(rootTask);
        if (subTasks == null || subTasks.isEmpty()) {
            log.warn("[Orchestrator-Workers] 规划器未拆解出任何子任务，直接返回空结果: rootTask={}", rootTask);
            return new OrchestratedExecutionResult(true, "未拆解出子任务", Collections.emptyMap(), 0);
        }

        log.info("[Orchestrator-Workers] 成功拆解为 {} 个并发子任务: rootTask={}", subTasks.size(), rootTask);

        Map<String, WorkerOutput> outputs = new ConcurrentHashMap<>();

        // 2. 利用 Java 21 虚拟线程并发派发
        try (ExecutorService vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (SubTask task : subTasks) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    long taskStart = System.currentTimeMillis();
                    try {
                        Map<String, Object> result = worker.executeSubTask(task);
                        long duration = System.currentTimeMillis() - taskStart;
                        outputs.put(task.taskId(), WorkerOutput.success(task.taskId(), result, duration));
                        log.debug("[Worker] 子任务 {} 执行成功，耗时 {}ms", task.taskId(), duration);
                    } catch (Throwable t) {
                        long duration = System.currentTimeMillis() - taskStart;
                        log.warn("[Worker] 子任务 {} 执行失败: {}", task.taskId(), t.getMessage());
                        outputs.put(task.taskId(), WorkerOutput.failure(task.taskId(), t.getMessage(), duration));
                    }
                }, vThreadExecutor);

                futures.add(future);
            }

            // 等待所有虚拟线程完成或超时拦截
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(Math.max(1000L, timeoutMillis), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("[Orchestrator-Workers] 整体并发执行超时 ({}ms)，对未完成任务进行兜底收敛", timeoutMillis);
                for (SubTask task : subTasks) {
                    outputs.putIfAbsent(task.taskId(), WorkerOutput.failure(task.taskId(), "任务执行超时熔断", timeoutMillis));
                }
            } catch (Exception e) {
                log.error("[Orchestrator-Workers] 并发等待异常", e);
            }
        }

        // 3. 聚合综合成果
        boolean allSucceeded = outputs.values().stream().allMatch(WorkerOutput::success);
        String aggregatedSummary = reducer.reduce(rootTask, Collections.unmodifiableMap(outputs));
        long totalDuration = System.currentTimeMillis() - startTotal;

        log.info("[Orchestrator-Workers] 编排完成: allSucceeded={}, 子任务数={}, 总耗时={}ms",
                allSucceeded, subTasks.size(), totalDuration);

        return new OrchestratedExecutionResult(
                allSucceeded,
                aggregatedSummary,
                Collections.unmodifiableMap(outputs),
                totalDuration
        );
    }
}
