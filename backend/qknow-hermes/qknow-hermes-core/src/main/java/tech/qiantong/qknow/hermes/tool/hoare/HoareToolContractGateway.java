package tech.qiantong.qknow.hermes.tool.hoare;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 声明式 Hoare 逻辑工具合约执行网关
 * 贯彻形式化方法三元组 {P} C {Q}：
 * 1. 前置守卫 {P}：拦截参数幻觉与未授权状态，未通过则 100% 拒绝执行并防御短路；
 * 2. 受限操作 C：沙箱化调用实际业务工具；
 * 3. 后置不变量 {Q}：校验执行后系统状态转移，若违背则立即触发 Saga 逆向补偿回滚；
 * 4. 签发纯 Java 21 Record 密码学自验真凭单。
 */
public class HoareToolContractGateway {

    public record ExecutionOutcome(
            boolean success,
            Object toolResult,
            HoareContractReceipt receipt
    ) {}

    private final String secretKey;
    private final Map<String, HoarePreconditionGuard> preconditionRegistry = new ConcurrentHashMap<>();
    private final Map<String, HoarePostconditionVerifier> postconditionRegistry = new ConcurrentHashMap<>();

    public HoareToolContractGateway() {
        this("DEFAULT_HERMES_HOARE_SECRET_" + System.currentTimeMillis());
    }

    public HoareToolContractGateway(String secretKey) {
        this.secretKey = secretKey == null ? "DEFAULT_HOARE_KEY" : secretKey;
    }

    public void registerContract(String toolName, HoarePreconditionGuard guard, HoarePostconditionVerifier verifier) {
        Objects.requireNonNull(toolName, "toolName cannot be null");
        if (guard != null) {
            preconditionRegistry.put(toolName, guard);
        }
        if (verifier != null) {
            postconditionRegistry.put(toolName, verifier);
        }
    }

    /**
     * 执行受控工具调用
     *
     * @param toolName          工具标识
     * @param executionId       执行追踪上下文 ID
     * @param inputParams       输入参数集合
     * @param systemContext     系统只读上下文
     * @param preState          执行前状态快照
     * @param toolAction        实际业务工具调用逻辑
     * @param rollbackAction    违背不变量时的 Saga 补偿动作
     * @param postStateSupplier 执行后状态提供者
     * @return ExecutionOutcome 包含最终结果与不可变审计凭单
     */
    public ExecutionOutcome execute(
            String toolName,
            String executionId,
            Map<String, Object> inputParams,
            Map<String, Object> systemContext,
            Map<String, Object> preState,
            Function<Map<String, Object>, Object> toolAction,
            Consumer<Map<String, Object>> rollbackAction,
            Supplier<Map<String, Object>> postStateSupplier
    ) {
        Objects.requireNonNull(toolName, "toolName cannot be null");
        Objects.requireNonNull(executionId, "executionId cannot be null");
        long timestamp = System.currentTimeMillis();
        String receiptId = "hoare_rcpt_" + UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> safeParams = inputParams == null ? Collections.emptyMap() : inputParams;
        Map<String, Object> safeCtx = systemContext == null ? Collections.emptyMap() : systemContext;
        Map<String, Object> safePreState = preState == null ? Collections.emptyMap() : preState;

        // 步骤 1：前置守卫 {P} 校验
        HoarePreconditionGuard guard = preconditionRegistry.get(toolName);
        if (guard != null) {
            HoarePreconditionGuard.GuardCheckResult guardResult = guard.evaluate(safeParams, safeCtx);
            if (!guardResult.passed()) {
                String sig = HoareContractReceipt.calculateSignature(
                        receiptId, toolName, executionId, false, false, false, timestamp, secretKey);
                HoareContractReceipt receipt = new HoareContractReceipt(
                        receiptId, toolName, executionId, false, guardResult.violationReason(),
                        false, "Precondition failed, execution skipped", false, timestamp, sig);
                return new ExecutionOutcome(false, null, receipt);
            }
        }

        // 步骤 2：执行受控操作 C
        Object result;
        try {
            result = toolAction.apply(safeParams);
        } catch (Exception e) {
            // 工具内部异常，视同失败，触发回滚
            if (rollbackAction != null) {
                rollbackAction.accept(safePreState);
            }
            String sig = HoareContractReceipt.calculateSignature(
                    receiptId, toolName, executionId, true, false, true, timestamp, secretKey);
            HoareContractReceipt receipt = new HoareContractReceipt(
                    receiptId, toolName, executionId, true, "",
                    false, "ToolExecutionThrewException: " + e.getMessage(), true, timestamp, sig);
            return new ExecutionOutcome(false, null, receipt);
        }

        // 步骤 3：后置不变量 {Q} 校验
        Map<String, Object> postState = postStateSupplier != null ? postStateSupplier.get() : Collections.emptyMap();
        HoarePostconditionVerifier verifier = postconditionRegistry.get(toolName);
        if (verifier != null) {
            HoarePostconditionVerifier.InvariantCheckResult invariantResult =
                    verifier.evaluate(safePreState, postState, result);
            if (!invariantResult.passed()) {
                // 违背后置不变量，立即启动 Saga 逆向补偿回滚
                boolean compensated = false;
                if (rollbackAction != null) {
                    try {
                        rollbackAction.accept(safePreState);
                        compensated = true;
                    } catch (Exception re) {
                        compensated = false;
                    }
                }
                String sig = HoareContractReceipt.calculateSignature(
                        receiptId, toolName, executionId, true, false, compensated, timestamp, secretKey);
                HoareContractReceipt receipt = new HoareContractReceipt(
                        receiptId, toolName, executionId, true, "",
                        false, invariantResult.violationReason(), compensated, timestamp, sig);
                return new ExecutionOutcome(false, result, receipt);
            }
        }

        // 步骤 4：前后置全绿，签发合法执行凭单
        String sig = HoareContractReceipt.calculateSignature(
                receiptId, toolName, executionId, true, true, false, timestamp, secretKey);
        HoareContractReceipt receipt = new HoareContractReceipt(
                receiptId, toolName, executionId, true, "",
                true, "", false, timestamp, sig);
        return new ExecutionOutcome(true, result, receipt);
    }
}
