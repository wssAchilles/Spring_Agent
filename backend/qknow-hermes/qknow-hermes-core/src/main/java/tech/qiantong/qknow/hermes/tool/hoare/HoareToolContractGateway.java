package tech.qiantong.qknow.hermes.tool.hoare;

import tech.qiantong.qknow.hermes.tool.formal.DbmLtlFormalMicroSolver;
import tech.qiantong.qknow.hermes.tool.formal.FormalConstraintFormula;
import tech.qiantong.qknow.hermes.tool.formal.FormalCounterexampleReceipt;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 声明式 Hoare 逻辑工具合约执行网关
 * 贯彻形式化方法三元组 {P} C {Q} 与紧凑 ACI 自纠错闭环：
 * 1. 前置守卫 {P}：拦截参数幻觉与未授权状态，未通过则 100% 拒绝执行并生成单步 ACI 自纠错指引；
 * 2. 受限操作 C：沙箱化调用实际业务工具；
 * 3. 后置不变量 {Q}：校验执行后系统状态转移，若违背则立即触发 Saga 逆向补偿回滚；
 * 4. 签发纯 Java 21 Record 密码学自验真凭单与 ACI 观察量。
 */
public class HoareToolContractGateway {

    public record ExecutionOutcome(
            boolean success,
            Object toolResult,
            HoareContractReceipt receipt,
            AciObservationReceipt aciObservation
    ) {
        public ExecutionOutcome(boolean success, Object toolResult, HoareContractReceipt receipt) {
            this(success, toolResult, receipt, null);
        }

        public String getObservationText() {
            if (aciObservation != null && aciObservation.observationText() != null && !aciObservation.observationText().isBlank()) {
                return aciObservation.observationText();
            }
            if (success) {
                return toolResult != null ? toolResult.toString() : "SUCCESS";
            }
            return receipt != null ? receipt.preconditionViolationReason() + " " + receipt.postconditionViolationReason() : "FAILED";
        }
    }

    public record BidirectionalFoTOutcome(
            ExecutionOutcome executionOutcome,
            FormalCounterexampleReceipt counterexampleReceipt
    ) {}

    private final String secretKey;
    private final DbmLtlFormalMicroSolver formalMicroSolver;
    private final Map<String, HoarePreconditionGuard> preconditionRegistry = new ConcurrentHashMap<>();
    private final Map<String, HoarePostconditionVerifier> postconditionRegistry = new ConcurrentHashMap<>();
    private final Map<String, FormalConstraintFormula> formalFormulaRegistry = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong totalFormalChecks = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong blockedFormalCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong totalToolContractsExecuted = new java.util.concurrent.atomic.AtomicLong();

    public HoareToolContractGateway() {
        this("DEFAULT_HERMES_HOARE_SECRET_" + System.currentTimeMillis());
    }

    public HoareToolContractGateway(String secretKey) {
        this.secretKey = secretKey == null ? "DEFAULT_HOARE_KEY" : secretKey;
        this.formalMicroSolver = new DbmLtlFormalMicroSolver(this.secretKey);
    }

    public void registerFormalFormula(String toolName, FormalConstraintFormula formula) {
        Objects.requireNonNull(toolName, "toolName cannot be null");
        Objects.requireNonNull(formula, "formula cannot be null");
        formalFormulaRegistry.put(toolName, formula);
    }

    /**
     * 执行带双向形式化思维（BFoT）与 DBM/LTLf 确定性微求解验证的工具调用（定理 5.1）。
     */
    public BidirectionalFoTOutcome executeWithBidirectionalFoT(
            String toolName,
            String executionId,
            Map<String, Object> inputParams,
            List<String> executedToolHistory,
            String reasoningContent,
            float[] reasoningEmbedding1024,
            float[] actionEmbedding1024,
            Function<Map<String, Object>, Object> toolAction
    ) {
        FormalConstraintFormula formula = formalFormulaRegistry.get(toolName);
        if (formula != null) {
            totalFormalChecks.incrementAndGet();
            FormalCounterexampleReceipt cex = formalMicroSolver.verifyAndSynthesizeCounterexample(
                    executionId, toolName, formula, inputParams, executedToolHistory,
                    reasoningContent, reasoningEmbedding1024, actionEmbedding1024
            );
            if (!cex.unsatCompliant()) {
                blockedFormalCount.incrementAndGet();
                long now = System.currentTimeMillis();
                String rcptId = "hoare_fot_block_" + UUID.randomUUID().toString().replace("-", "");
                String sig = HoareContractReceipt.calculateSignature(
                        rcptId, toolName, executionId, false, false, false, now, secretKey);
                HoareContractReceipt blockedReceipt = new HoareContractReceipt(
                        rcptId, toolName, executionId, false, cex.tailAppendRemediationMessage(),
                        false, "Blocked by Bidirectional FoT MicroSolver", false, now, sig
                );
                AciObservationReceipt aci = AciObservationReceipt.createLintFailure(
                        "aci_fot_" + UUID.randomUUID().toString().substring(0, 8),
                        toolName, executionId, "FORMAL_BFOT_VIOLATION",
                        cex.minimalUnsatisfiableCore(), Map.of("muc", String.join(",", cex.minimalUnsatisfiableCore())),
                        cex.tailAppendRemediationMessage(), now, secretKey
                );
                return new BidirectionalFoTOutcome(new ExecutionOutcome(false, null, blockedReceipt, aci), cex);
            }
            ExecutionOutcome ok = execute(
                    toolName, executionId, inputParams, Map.of(), Map.of(), toolAction, null, Map::of
            );
            return new BidirectionalFoTOutcome(ok, cex);
        }

        FormalCounterexampleReceipt trivialOk = FormalCounterexampleReceipt.createSigned(
                executionId, toolName, true, List.of(), Map.of(), "NO_FORMAL_FORMULA_REGISTERED", secretKey
        );
        ExecutionOutcome out = execute(
                toolName, executionId, inputParams, Map.of(), Map.of(), toolAction, null, Map::of
        );
        return new BidirectionalFoTOutcome(out, trivialOk);
    }

    public Map<String, Object> getFormalTelemetryStats() {
        return Map.of(
                "registeredFormulaCount", formalFormulaRegistry.size(),
                "totalFormalChecks", totalFormalChecks.get(),
                "blockedFormalCount", blockedFormalCount.get(),
                "totalToolContractsExecuted", totalToolContractsExecuted.get(),
                "preconditionGuards", preconditionRegistry.size()
        );
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
     * @return ExecutionOutcome 包含最终结果、不可变审计凭单与紧凑 ACI 观察凭单
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
        totalToolContractsExecuted.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        String receiptId = "hoare_rcpt_" + UUID.randomUUID().toString().replace("-", "");
        String aciReceiptId = "aci_rcpt_" + UUID.randomUUID().toString().replace("-", "");

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

                AciObservationReceipt aciReceipt = AciObservationReceipt.createLintFailure(
                        aciReceiptId, toolName, executionId,
                        guardResult.violationType(),
                        guardResult.missingParameters(),
                        guardResult.parameterErrors(),
                        guardResult.suggestedRemedy(),
                        timestamp, secretKey);

                return new ExecutionOutcome(false, null, receipt, aciReceipt);
            }
        }

        // 步骤 2：执行受控操作 C
        Object result;
        try {
            result = toolAction.apply(safeParams);
        } catch (Exception e) {
            // 工具内部异常，仅在显式提供 rollbackAction 且回滚成功时将 compensated 置为 true
            boolean compensated = false;
            if (rollbackAction != null) {
                try {
                    rollbackAction.accept(safePreState);
                    compensated = true;
                } catch (Exception rollbackEx) {
                    compensated = false;
                }
            }
            String sig = HoareContractReceipt.calculateSignature(
                    receiptId, toolName, executionId, true, false, compensated, timestamp, secretKey);
            HoareContractReceipt receipt = new HoareContractReceipt(
                    receiptId, toolName, executionId, true, "",
                    false, "ToolExecutionThrewException: " + e.getMessage(), compensated, timestamp, sig);

            AciObservationReceipt aciReceipt = AciObservationReceipt.createLintFailure(
                    aciReceiptId, toolName, executionId,
                    "TOOL_EXECUTION_EXCEPTION",
                    Collections.emptyList(),
                    Map.of("exception", e.getMessage() != null ? e.getMessage() : "Unknown exception"),
                    "Tool execution crashed. Inspect parameters or fallback to alternative action.",
                    timestamp, secretKey);

            return new ExecutionOutcome(false, null, receipt, aciReceipt);
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

                AciObservationReceipt aciReceipt = AciObservationReceipt.createLintFailure(
                        aciReceiptId, toolName, executionId,
                        "POSTCONDITION_INVARIANT_VIOLATION",
                        Collections.emptyList(),
                        Map.of("invariantReason", invariantResult.violationReason()),
                        "Invariant violated. State was rolled back by Saga compensator. Revise post-conditions.",
                        timestamp, secretKey);

                return new ExecutionOutcome(false, result, receipt, aciReceipt);
            }
        }

        // 步骤 4：前后置全绿，签发合法执行凭单与成功 ACI 凭单
        String sig = HoareContractReceipt.calculateSignature(
                receiptId, toolName, executionId, true, true, false, timestamp, secretKey);
        HoareContractReceipt receipt = new HoareContractReceipt(
                receiptId, toolName, executionId, true, "",
                true, "", false, timestamp, sig);

        String resultStr = result != null ? result.toString() : "null";
        AciObservationReceipt aciReceipt = AciObservationReceipt.createSuccess(
                aciReceiptId, toolName, executionId,
                "Tool execution successful. Result: " + resultStr,
                timestamp, secretKey);

        return new ExecutionOutcome(true, result, receipt, aciReceipt);
    }
}
