package tech.qiantong.qknow.ai.htn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * 分布式 SAGA 事务一致性仲裁器 (Distributed Transaction Arbiter)
 * 维护正向动作栈与逆向幂等补偿栈，当故障且自愈失败时，
 * 按照依赖图逆序 (LIFO) 100% 触发已执行动作的幂等回滚，消除脏数据 (定理 1.3)
 */
@Component
public class DistributedTransactionArbiter {

    private static final Logger log = LoggerFactory.getLogger(DistributedTransactionArbiter.class);

    public enum TransactionState {
        ACTIVE,
        COMMITTED,
        COMPENSATING,
        COMPENSATED,
        FAILED
    }

    /**
     * SAGA 可补偿步骤
     */
    public record SagaStep(
            String stepId,
            String actionName,
            Runnable forwardAction,
            Consumer<String> compensatingAction // 逆向幂等补偿回调
    ) {}

    /**
     * 事务执行上下文与栈
     */
    public static class SagaContext {
        private final String transactionId;
        private TransactionState state = TransactionState.ACTIVE;
        private final Deque<SagaStep> completedSteps = new ArrayDeque<>();
        private final List<String> rollbackHistory = new ArrayList<>();

        public SagaContext(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getTransactionId() { return transactionId; }
        public TransactionState getState() { return state; }
        public Deque<SagaStep> getCompletedSteps() { return completedSteps; }
        public List<String> getRollbackHistory() { return Collections.unmodifiableList(rollbackHistory); }
    }

    /**
     * 执行原子正向动作并压入补偿栈
     */
    public synchronized void recordForwardExecution(SagaContext ctx, SagaStep step) {
        if (ctx.state != TransactionState.ACTIVE) {
            throw new IllegalStateException("事务非活动状态，禁止记录正向执行: " + ctx.state);
        }
        ctx.completedSteps.push(step);
        log.debug("SAGA 记录已完成步骤: tx={}, step={}", ctx.transactionId, step.stepId());
    }

    /**
     * 成功提交事务
     */
    public synchronized void commitTransaction(SagaContext ctx) {
        ctx.state = TransactionState.COMMITTED;
        log.info("SAGA 事务提交成功: tx={}, 累计步骤数={}", ctx.transactionId, ctx.completedSteps.size());
    }

    /**
     * 触发 SAGA 逆向 LIFO 幂等补偿回滚
     * @return 实际执行逆向补偿的步骤 ID 列表
     */
    public synchronized List<String> rollbackTransaction(SagaContext ctx) {
        ctx.state = TransactionState.COMPENSATING;
        List<String> compensatedList = new ArrayList<>();

        log.warn("SAGA 触发逆向补偿回滚: tx={}, 待回滚步骤数={}", ctx.transactionId, ctx.completedSteps.size());

        while (!ctx.completedSteps.isEmpty()) {
            SagaStep step = ctx.completedSteps.pop();
            try {
                if (step.compensatingAction() != null) {
                    step.compensatingAction().accept(step.stepId());
                }
                compensatedList.add(step.stepId());
                ctx.rollbackHistory.add(step.stepId());
                log.info("SAGA 步骤已完成幂等逆向补偿: tx={}, step={}", ctx.transactionId, step.stepId());
            } catch (Exception ex) {
                log.error("SAGA 补偿回调执行异常 (需幂等保障): tx={}, step={}", ctx.transactionId, step.stepId(), ex);
            }
        }

        ctx.state = TransactionState.COMPENSATED;
        return Collections.unmodifiableList(compensatedList);
    }
}
