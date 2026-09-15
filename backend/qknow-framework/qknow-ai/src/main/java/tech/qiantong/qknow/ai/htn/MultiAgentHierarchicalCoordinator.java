package tech.qiantong.qknow.ai.htn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 分层任务网络与因果事务总控协调中枢 (Multi-Agent Hierarchical Coordinator)
 * 闭环调度 HTN 分解 -> 拓扑执行 -> 因果元反思自愈 -> SAGA 事务一致性仲裁 -> 密码学存证收据签发
 */
@Component
public class MultiAgentHierarchicalCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentHierarchicalCoordinator.class);

    private final HtnTaskDecomposer decomposer;
    private final CausalMetaReasoningEngine metaReasoningEngine;
    private final DistributedTransactionArbiter transactionArbiter;

    @Autowired
    public MultiAgentHierarchicalCoordinator(
            HtnTaskDecomposer decomposer,
            CausalMetaReasoningEngine metaReasoningEngine,
            DistributedTransactionArbiter transactionArbiter
    ) {
        this.decomposer = decomposer;
        this.metaReasoningEngine = metaReasoningEngine;
        this.transactionArbiter = transactionArbiter;
    }

    /**
     * 协调请求上下文模型
     */
    public record ExecutionRequest(
            String rootTaskId,
            HtnTaskDecomposer.HtnTask rootTask,
            Map<String, String> simulatedFailures // taskId -> errorMessage (模拟特定动作故障)
    ) {}

    /**
     * 端到端执行流水线协调
     */
    public HierarchicalExecutionReceipt coordinateExecution(ExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        String rootTaskId = request.rootTaskId() != null ? request.rootTaskId() : "TASK_ROOT_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. HTN 递归任务分解与 Kahn 拓扑排序
        HtnTaskDecomposer.DecomposedPlan plan = decomposer.decompose(request.rootTask());

        // 2. 初始化 SAGA 事务上下文
        String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8);
        DistributedTransactionArbiter.SagaContext sagaCtx = new DistributedTransactionArbiter.SagaContext(txId);

        List<String> executedActionIds = new ArrayList<>();
        int totalHealingRounds = 0;
        boolean transactionFailed = false;

        // 3. 拓扑有序分步执行原子动作
        for (HtnTaskDecomposer.HtnTask action : plan.orderedPrimitiveTasks()) {
            String actId = action.taskId();

            // 检查是否有模拟故障
            String simulatedErr = request.simulatedFailures() != null ? request.simulatedFailures().get(actId) : null;

            if (simulatedErr != null) {
                // 触发因果元反思诊断
                CausalMetaReasoningEngine.FailureContext failCtx = new CausalMetaReasoningEngine.FailureContext(
                        actId, action.taskName(), simulatedErr, 0, Map.of()
                );
                CausalMetaReasoningEngine.HealingVerdict verdict = metaReasoningEngine.diagnoseAndHeal(failCtx);
                totalHealingRounds += verdict.roundCompleted();

                if (!verdict.canHeal()) {
                    // 无法自愈，中止流水线并触发 SAGA 回滚
                    transactionFailed = true;
                    log.warn("任务执行遇到不可自愈故障，准备回滚 SAGA 事务: task={}", actId);
                    break;
                } else {
                    // 自愈成功，继续
                    log.info("因果元反思自愈成功，动作放行: task={}", actId);
                }
            }

            // 记录成功执行并注册补偿步骤
            DistributedTransactionArbiter.SagaStep step = new DistributedTransactionArbiter.SagaStep(
                    actId,
                    action.taskName(),
                    () -> log.debug("执行正向动作: {}", actId),
                    compActId -> log.info("执行逆向幂等补偿: {}", compActId)
            );
            transactionArbiter.recordForwardExecution(sagaCtx, step);
            executedActionIds.add(actId);
        }

        // 4. 事务状态仲裁与结算
        String finalStatus;
        if (transactionFailed) {
            transactionArbiter.rollbackTransaction(sagaCtx);
            finalStatus = sagaCtx.getState().name(); // COMPENSATED
        } else {
            transactionArbiter.commitTransaction(sagaCtx);
            finalStatus = sagaCtx.getState().name(); // COMMITTED
        }

        long duration = System.currentTimeMillis() - startTime;

        // 5. 签发不可变密码学存证收据
        return HierarchicalExecutionReceipt.createReceipt(
                rootTaskId,
                plan.planTopologyHash(),
                executedActionIds,
                finalStatus,
                totalHealingRounds,
                duration
        );
    }

    public HtnTaskDecomposer getDecomposer() {
        return decomposer;
    }

    public CausalMetaReasoningEngine getMetaReasoningEngine() {
        return metaReasoningEngine;
    }

    public DistributedTransactionArbiter getTransactionArbiter() {
        return transactionArbiter;
    }
}
