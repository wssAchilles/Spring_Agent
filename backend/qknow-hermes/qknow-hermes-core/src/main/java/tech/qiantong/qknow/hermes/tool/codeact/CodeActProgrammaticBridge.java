package tech.qiantong.qknow.hermes.tool.codeact;

import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalEvolutionRetriever;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalRetrievalReceipt;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicDatasetViewMaterializer;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;
import tech.qiantong.qknow.hermes.transaction.frontier.ToolSideEffectTier;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * CodeAct 程序化桥接器（Phase 8.1 × Phase 8.2 协同桥梁）。
 * <p>
 * 打通双时态因果演化只读视图 {@code ReadOnlyDatasetView} 与安全格程序化计算沙盒的零副作用闭环，
 * 并通过 {@link HoareToolContractGateway} 与 {@link ToolSideEffectTier} 禁止在程序化脚本内调用任何有副作用工具。
 * </p>
 */
public class CodeActProgrammaticBridge {

    private final VerifiedCodeActSandboxRuntime sandboxRuntime;
    private final BiTemporalCausalEvolutionRetriever causalRetriever;
    private final HoareToolContractGateway hoareGateway;

    public CodeActProgrammaticBridge(
            VerifiedCodeActSandboxRuntime sandboxRuntime,
            BiTemporalCausalEvolutionRetriever causalRetriever,
            HoareToolContractGateway hoareGateway
    ) {
        this.sandboxRuntime = Objects.requireNonNull(sandboxRuntime, "sandboxRuntime 不能为空");
        this.causalRetriever = Objects.requireNonNull(causalRetriever, "causalRetriever 不能为空");
        this.hoareGateway = hoareGateway != null ? hoareGateway : new HoareToolContractGateway();
    }

    /**
     * 先执行双时态因果演化检索获取冻结只读视图，再送入安全格受控 Python 沙盒执行精确程序化分析/计数。
     */
    public VerifiedCodeActReceipt queryTemporalAndExecuteCode(
            String queryId,
            String queryText,
            long referenceNowMs,
            String pythonAnalysisCode
    ) {
        BiTemporalCausalRetrievalReceipt retrieval = causalRetriever.retrieve(queryId, queryText, referenceNowMs, 16);
        CodeActExecutionPlan plan = new CodeActExecutionPlan(
                "bridge-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                "python",
                pythonAnalysisCode,
                retrieval.datasetView(),
                java.util.List.of("hippocampal_subgraph_probe"),
                EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
        );
        return sandboxRuntime.execute(plan, Map.of());
    }

    /**
     * Phase 9.3 BTER-MV 新增：将情景-关系物化视图直接注入安全格 Python 沙盒，实现零算术/零计数误差求解。
     */
    public VerifiedCodeActReceipt queryEpisodicViewAndExecuteCode(
            String scopeId,
            String queryText,
            long referenceNowMs,
            boolean includeSuperseded,
            String pythonAnalysisCode,
            EpisodicDatasetViewMaterializer materializer
    ) {
        Objects.requireNonNull(materializer, "materializer 不能为空");
        var matReceipt = materializer.materializeViewForQuery(scopeId, queryText, referenceNowMs, includeSuperseded);
        CodeActExecutionPlan plan = new CodeActExecutionPlan(
                "e2r-bridge-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                "python",
                pythonAnalysisCode,
                matReceipt.datasetView(),
                java.util.List.of("episodic_relational_materializer"),
                EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
        );
        return sandboxRuntime.execute(plan, Map.of());
    }

    /**
     * 安全格门控的只读工具反向调用：严禁在程序化沙盒桥接路径中调用任何非 {@link ToolSideEffectTier#BUFFERABLE} 工具。
     */
    public String invokeReadOnlyToolSafely(
            String requestId,
            String toolName,
            ToolSideEffectTier tier,
            Map<String, Object> args,
            Function<Map<String, Object>, String> delegate
    ) {
        if (tier != ToolSideEffectTier.BUFFERABLE) {
            throw new SecurityException("CodeAct 安全格拒绝对非只读副作用工具执行程序化桥接调用: tool=" + toolName + ", tier=" + tier);
        }
        Objects.requireNonNull(delegate, "delegate 不能为空");
        var res = hoareGateway.execute(
                toolName,
                requestId,
                args != null ? args : Map.of(),
                Map.of(),
                Map.of(),
                delegate::apply,
                null,
                Map::of
        );
        if (!res.success()) {
            throw new SecurityException("Hoare 契约门禁拒绝工具调用: " + res.getObservationText());
        }
        return res.toolResult() != null ? String.valueOf(res.toolResult()) : "";
    }
}
