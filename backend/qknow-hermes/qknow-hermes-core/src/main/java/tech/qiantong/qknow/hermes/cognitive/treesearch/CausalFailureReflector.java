package tech.qiantong.qknow.hermes.cognitive.treesearch;

import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 因果失败反思器与错题本（{@link HippocampalVertexTier#ERROR_BOOK}）持久化写回闭环（Phase 8.3）。
 * <p>
 * 彻底取代无真反思能力的伪模板空壳 {@code ReflectionTreeEngineImpl}：
 * 1. 基于沙盒 stderr / Hoare 安全格拒签 / 双时态矛盾提取客观失败根因，合成 {@link CounterfactualBranchPatch}；
 * 2. 强制通过尾部增量（Append-Only Tail Delta）注入下一兄弟分支，绝不改写 Zone-0 前缀；
 * 3. 将反事实防错警示写入 {@link BiTemporalHippocampalGraphEngine} 的 {@code ERROR_BOOK} 节点，
 *    打通“失败剪枝 → 错题沉淀 → 跨题 PPR 自动排斥召回”的真实在线自演化闭环。
 * </p>
 */
public class CausalFailureReflector {

    /**
     * 反思与错题本持久化回写结果。
     */
    public record ReflectionOutcome(
            CounterfactualBranchPatch patch,
            String errorBookNodeId,
            boolean persistedToErrorBook
    ) {}

    private final BiTemporalHippocampalGraphEngine hippocampalEngine;
    private final UnitSphereEmbeddingResolver embeddingResolver;

    public CausalFailureReflector() {
        this(null, new UnitSphereEmbeddingResolver());
    }

    public CausalFailureReflector(
            BiTemporalHippocampalGraphEngine hippocampalEngine,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        this.hippocampalEngine = hippocampalEngine;
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
    }

    /**
     * 从失败分支的客观见证提取因果诊断，生成不可变纠偏补丁，并写回海马体 ERROR_BOOK。
     */
    public ReflectionOutcome reflectAndRecord(
            String executionId,
            String failedBranchId,
            String taskContext,
            NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome evalOutcome,
            VerifiedCodeActReceipt failedCodeActReceipt,
            List<String> failedToolNames
    ) {
        String safeExec = (executionId == null || executionId.isBlank()) ? "exec-bcbt" : executionId;
        String safeBranch = (failedBranchId == null || failedBranchId.isBlank()) ? "branch-unknown" : failedBranchId;
        String safeTask = taskContext != null ? taskContext : "";

        CounterfactualBranchPatch.FailureRootCause rootCause =
                (evalOutcome != null && evalOutcome.detectedFailureCause() != null)
                        ? evalOutcome.detectedFailureCause()
                        : CounterfactualBranchPatch.FailureRootCause.CONSISTENCY_DRIFT;

        String rawDiag = buildFailureDiagnosis(evalOutcome, failedCodeActReceipt);
        String correctivePrompt = buildCorrectiveConstraintPrompt(rootCause, rawDiag);
        String patchId = "cfp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        List<String> avoidTools = failedToolNames != null ? List.copyOf(failedToolNames) : List.of();
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("last_failed_branch", safeBranch);
        overrides.put("causal_patch_id", patchId);
        overrides.put("failure_root_cause", rootCause.name());
        overrides.put("corrective_constraint", correctivePrompt);
        if (!avoidTools.isEmpty()) {
            overrides.put("avoid_tools", String.join(",", avoidTools));
        }

        CounterfactualBranchPatch patch = new CounterfactualBranchPatch(
                patchId,
                safeBranch,
                rootCause,
                rawDiag,
                correctivePrompt,
                overrides,
                avoidTools
        );

        String errorNodeId = null;
        boolean persisted = false;
        if (hippocampalEngine != null) {
            long nowMs = System.currentTimeMillis();
            String semanticPayload = (safeTask + " " + rawDiag).trim();
            float[] unitVec1024 = embeddingResolver.resolveUnit1024(semanticPayload);

            // 使用任务锚点实名短语节点挂载 ERROR_BOOK 边，确保同主题检索通过 PPR 稳定召回防错警示
            String anchorNodeId = "bcbt_anchor_" + shortShaHex(safeTask);
            String anchorContent = safeTask.isBlank() ? "bcbt_task_anchor" : safeTask;
            hippocampalEngine.upsertVertex(new HippocampalVertex(
                    anchorNodeId,
                    anchorContent,
                    HippocampalVertexTier.PHRASE_ENTITY,
                    embeddingResolver.resolveUnit1024(anchorContent)
            ));

            errorNodeId = "err_bcbt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            String errorWarningText = String.format(
                    "[ERROR_BOOK][%s] exec=%s branch=%s diagnosis=%s | remedy=%s",
                    rootCause.name(), safeExec, safeBranch, rawDiag, correctivePrompt
            );
            hippocampalEngine.upsertVertex(new HippocampalVertex(
                    errorNodeId,
                    errorWarningText,
                    HippocampalVertexTier.ERROR_BOOK,
                    unitVec1024
            ));
            hippocampalEngine.ingestFactEdge(
                    anchorNodeId,
                    errorNodeId,
                    "AVOID_FAILURE_PATTERN",
                    false,
                    unitVec1024,
                    nowMs,
                    nowMs,
                    "bcbt:" + safeExec
            );
            persisted = true;
        }

        return new ReflectionOutcome(patch, errorNodeId, persisted);
    }

    private static String buildFailureDiagnosis(
            NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome evalOutcome,
            VerifiedCodeActReceipt failedReceipt
    ) {
        if (failedReceipt != null && !failedReceipt.isSuccess()) {
            String err = failedReceipt.stderr();
            if (err != null && !err.isBlank()) {
                return "CodeAct exitCode=" + failedReceipt.exitCode() + ": " + err.trim();
            }
            return "CodeAct execution failed with exitCode=" + failedReceipt.exitCode()
                    + ", timedOut=" + failedReceipt.timedOut();
        }
        if (evalOutcome != null && evalOutcome.failureDiagnostic() != null && !evalOutcome.failureDiagnostic().isBlank()) {
            return evalOutcome.failureDiagnostic().trim();
        }
        return "Suboptimal reasoning trajectory detected below composite acceptance threshold";
    }

    private static String buildCorrectiveConstraintPrompt(
            CounterfactualBranchPatch.FailureRootCause rootCause,
            String diagnosis
    ) {
        return switch (rootCause) {
            case TOOL_CONTRACT_VIOLATION ->
                    "Enforce Hoare and AST security invariants; avoid restricted OS/FS imports or unverified risky actions. Prior failure: " + diagnosis;
            case CODEACT_ASSERTION_FAILED ->
                    "Inspect previous script stderr and fix boundary/syntax/assertion logic before re-running CodeAct verification. Prior failure: " + diagnosis;
            case TEMPORAL_CONTRADICTION ->
                    "Respect bi-temporal [t_valid, t_invalid) windows and Kahn causal ordering; reject superseded facts. Prior failure: " + diagnosis;
            case CONSISTENCY_DRIFT ->
                    "Align reasoning chain tightly with attested tool evidence and eliminate unsupported leaps. Prior failure: " + diagnosis;
        };
    }

    private static String shortShaHex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((text != null ? text : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            return Integer.toHexString(text != null ? text.hashCode() : 0);
        }
    }
}
