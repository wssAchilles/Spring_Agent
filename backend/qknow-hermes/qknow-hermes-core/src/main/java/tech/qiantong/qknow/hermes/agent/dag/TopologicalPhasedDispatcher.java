package tech.qiantong.qknow.hermes.agent.dag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.bidding.ContractNetDispatcher;
import tech.qiantong.qknow.hermes.agent.blackboard.SharedBlackboard;
import tech.qiantong.qknow.hermes.agent.guard.SwarmLoopGuard;

import java.util.*;
import java.util.concurrent.*;

/**
 * 拓扑分层调度引擎：负责阶段推进、并发控制、上下文自动注入与硬超时防护
 */
@Slf4j
@Component
public class TopologicalPhasedDispatcher {

    // 工业级隔离专用线程池，拒绝策略采用 CallerRunsPolicy，杜绝污染 commonPool
    private final ExecutorService agentTaskExecutor = new ThreadPoolExecutor(
            8, 32,
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public synchronized Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "agent-swarm-worker-" + (++counter));
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final ContractNetDispatcher contractNetDispatcher;
    private final SwarmLoopGuard swarmLoopGuard;

    public TopologicalPhasedDispatcher(ContractNetDispatcher contractNetDispatcher, SwarmLoopGuard swarmLoopGuard) {
        this.contractNetDispatcher = contractNetDispatcher;
        this.swarmLoopGuard = swarmLoopGuard;
    }

    /**
     * 顺序执行各 Phase，每个 Phase 内部任务高度并行
     */
    public void dispatch(String sessionId, PhasedExecutionPlan plan, SharedBlackboard blackboard) {
        if (plan == null || plan.isEmpty()) {
            log.warn("[Dispatcher] 会话 {} 执行计划为空，跳过调度", sessionId);
            return;
        }

        log.info("[Dispatcher] 开始会话 {} 的 DAG 阶段调度，总任务数: {}, 总执行阶段: {}",
                sessionId, plan.totalTasks(), plan.phases().size());

        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            List<DagTaskNode> currentPhase = plan.phases().get(phaseIndex);
            log.info("[Dispatcher] 启动执行 Phase [{}], 包含并行任务数: {}", phaseIndex, currentPhase.size());

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (DagTaskNode task : currentPhase) {
                CompletableFuture<Void> taskFuture = CompletableFuture.runAsync(() -> {
                    executeSingleTask(sessionId, task, blackboard);
                }, agentTaskExecutor)
                // 生产级硬超时防护：单任务超过限定时间强制熔断抛出 TimeoutException
                .orTimeout(task.timeoutSeconds(), TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    log.error("[Dispatcher] 任务 [{}] 执行超时或异常失败: {}", task.taskId(), throwable.getMessage());
                    // 软降级记录（Fail-Open）：写入半成品/降级结果，避免阻塞下游依赖
                    blackboard.commitFact(task.taskId(), "【执行降级】：子任务在 " + task.timeoutSeconds() + "s 内超时未响应，提供降级上下文。", "SYSTEM_FALLBACK");
                    return null;
                });

                futures.add(taskFuture);
            }

            // 同步栅栏等待当前 Phase 所有任务完全完成（或超时降级完成）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("[Dispatcher] Phase [{}] 阶段执行栅栏已通过，进入下一阶段", phaseIndex);
        }
    }

    private void executeSingleTask(String sessionId, DagTaskNode task, SharedBlackboard blackboard) {
        // 1. 蜂群环路防死锁检查
        if (swarmLoopGuard != null) {
            swarmLoopGuard.preCheck(sessionId, task.taskId());
        }

        // 2. 自动抽取上游依赖上下文（Context Injection）
        Map<String, String> upstreamResults = blackboard.getFactsByKeys(task.dependencies());
        StringBuilder contextPayload = new StringBuilder();
        upstreamResults.forEach((depId, fact) -> {
            contextPayload.append("## 上游任务 [").append(depId).append("] 的输出：\n").append(fact).append("\n\n");
        });

        // 3. 通过合同网动态竞标分配最佳 Worker
        String workerOutput = (contractNetDispatcher != null)
                ? contractNetDispatcher.bidAndExecute(sessionId, task, contextPayload.toString())
                : "【执行错误】：合同网调度器未注入";

        // 4. 将产出通过 CAS 乐观锁写入共享黑板
        blackboard.commitFact(task.taskId(), workerOutput, task.requiredCapability());

        // 5. 记录执行轨迹
        blackboard.recordTrace(sessionId, task.taskId(), "COMPLETED", "执行成功");
    }
}
