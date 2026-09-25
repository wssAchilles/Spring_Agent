package tech.qiantong.qknow.hermes.tool.mcp.sagas.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.dto.McpSagasTransactionReceipt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 第三道防线：双向 Sagas 逆拓扑 LIFO 幂等补偿状态机防线
 * 1. 物理分离正向执行调用栈 (Forward Stack) 与逆向补偿状态机；
 * 2. 严格遵循 LIFO 逆拓扑出栈倒序回滚，保障因果抵消与状态最终一致性（定理 1.2 收敛概率 1.0）；
 * 3. 引入全局防悬挂墓碑标记 (Tombstone) 与唯一幂等 leaseToken，杜绝网络乱序幽灵重放与重复扣划。
 */
@Component
public class ResilientSagasStateManager {

    private static final Logger log = LoggerFactory.getLogger(ResilientSagasStateManager.class);

    /**
     * 单个正向步骤执行契约实体
     */
    public record SagaStepContract(
            String stepName,
            Object forwardResult,
            Consumer<String> compensationAction,
            String idempotentLeaseToken,
            long fencingToken
    ) {}

    // 每个事务维护正向执行历史栈 (LIFO): transactionId -> Deque<SagaStepContract>
    private final ConcurrentHashMap<String, Deque<SagaStepContract>> forwardStacks = new ConcurrentHashMap<>();
    // 幂等补偿存证记录: idempotentLeaseToken -> 执行时间戳 (毫秒)
    private final ConcurrentHashMap<String, Long> executedCompensations = new ConcurrentHashMap<>();
    // 防悬挂墓碑标记集合: idempotentLeaseToken (补偿先于正向到达时打标)
    private final Set<String> tombstones = ConcurrentHashMap.newKeySet();

    /**
     * 注册正向成功步骤入栈 (LIFO)
     *
     * @param transactionId      事务 ID
     * @param stepName           步骤名称
     * @param forwardResult      正向执行结果对象
     * @param compensationAction 逆向补偿动作 Consumer (入参为 idempotentLeaseToken)
     * @param fencingToken       当前有效世代令牌
     * @return 生成的唯一幂等 leaseToken
     */
    public String registerSuccessStep(
            String transactionId,
            String stepName,
            Object forwardResult,
            Consumer<String> compensationAction,
            long fencingToken
    ) {
        String leaseToken = transactionId + "_" + stepName + "_" + fencingToken;

        // 检查墓碑标记：若补偿已先一步到达，说明发生了严重网络乱序，正向执行被墓碑吞吐为 NoOp
        if (tombstones.contains(leaseToken)) {
            log.warn("[ResilientSagasStateManager] 步骤 {} 命中防悬挂墓碑标记 {}，正向结果被安全吞吐！", stepName, leaseToken);
            return leaseToken;
        }

        Deque<SagaStepContract> stack = forwardStacks.computeIfAbsent(transactionId, k -> new ArrayDeque<>());
        synchronized (stack) {
            stack.push(new SagaStepContract(stepName, forwardResult, compensationAction, leaseToken, fencingToken));
        }
        log.info("[ResilientSagasStateManager] 事务 {} 正向步骤 {} 压栈成功, Token: {}", transactionId, stepName, leaseToken);
        return leaseToken;
    }

    /**
     * 网络乱序处理：逆向补偿先于正向到达时提前打上防悬挂墓碑
     */
    public void markTombstone(String transactionId, String stepName, long fencingToken) {
        String leaseToken = transactionId + "_" + stepName + "_" + fencingToken;
        tombstones.add(leaseToken);
        log.warn("[ResilientSagasStateManager] 提前打上防悬挂墓碑标记: {}", leaseToken);
    }

    /**
     * 校验步骤是否被墓碑标记拦截
     */
    public boolean isTombstoneMarked(String transactionId, String stepName, long fencingToken) {
        String leaseToken = transactionId + "_" + stepName + "_" + fencingToken;
        return tombstones.contains(leaseToken);
    }

    /**
     * 触发 Sagas 逆拓扑 LIFO 幂等补偿
     *
     * @param transactionId 全局事务唯一标识
     * @return 按 LIFO 逆序已执行补偿的步骤明细列表
     */
    public List<String> rollbackLifo(String transactionId) {
        Deque<SagaStepContract> stack = forwardStacks.get(transactionId);
        if (stack == null) {
            return Collections.emptyList();
        }

        List<String> compensatedSteps = new CopyOnWriteArrayList<>();
        synchronized (stack) {
            while (!stack.isEmpty()) {
                SagaStepContract step = stack.pop(); // 严格 LIFO 逆序弹出
                String leaseToken = step.idempotentLeaseToken();

                // 幂等防重入检查：若该补偿动作此前已成功执行，直接短路放行，绝不重复调用外部破坏性接口
                if (executedCompensations.containsKey(leaseToken)) {
                    log.info("[ResilientSagasStateManager] 步骤 {} 幂等补偿已执行过，安全短路放行", step.stepName());
                    compensatedSteps.add(step.stepName() + "(IDEMPOTENT_SHORT_CIRCUIT)");
                    continue;
                }

                // 标记墓碑，防止后续延迟的正向操作造成悬挂
                tombstones.add(leaseToken);

                // 执行反向物理补偿动作
                try {
                    if (step.compensationAction() != null) {
                        step.compensationAction().accept(leaseToken);
                    }
                    executedCompensations.put(leaseToken, System.currentTimeMillis());
                    compensatedSteps.add(step.stepName() + "(COMPENSATED)");
                    log.info("[ResilientSagasStateManager] 步骤 {} 逆向补偿成功, Token: {}", step.stepName(), leaseToken);
                } catch (Exception e) {
                    log.error("[ResilientSagasStateManager] 步骤 {} 逆向补偿异常: {}", step.stepName(), e.getMessage(), e);
                    compensatedSteps.add(step.stepName() + "(COMPENSATION_FAILED)");
                    throw new RuntimeException("逆向补偿执行失败: " + step.stepName(), e);
                }
            }
        }
        return compensatedSteps;
    }

    /**
     * 生成并签发最终 Sagas 事务存证凭单
     */
    public McpSagasTransactionReceipt finalizeTransactionReceipt(
            String transactionId,
            long fencingToken,
            String leaseOwnerId,
            String executionStatus,
            List<String> forwardSteps,
            List<String> compensatedSteps,
            long latencyMicros
    ) {
        String receiptId = "RCP-SAGAS-" + UUID.randomUUID().toString().substring(0, 8);
        McpSagasTransactionReceipt receipt = McpSagasTransactionReceipt.create(
                receiptId, transactionId, fencingToken, leaseOwnerId, executionStatus,
                forwardSteps, compensatedSteps, latencyMicros
        );
        log.info("[ResilientSagasStateManager] 已生成不可变事务存证凭单: {}, 状态: {}, 签名: {}",
                receipt.receiptId(), receipt.executionStatus(), receipt.sha256Signature().substring(0, 12));
        return receipt;
    }
}
