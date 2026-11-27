package tech.qiantong.qknow.hermes.cognitive.treesearch;

import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningConsistencyReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;

import java.util.List;
import java.util.Objects;

/**
 * 神经符号混合搜索分支价值评估器（Phase 8.3 核心打分组件，规避纯 LLM 主观判分失灵问题）。
 * <p>
 * 综合四项客观可验证信号计算分支复合价值 {@code V(s) in [0, 1]}：
 * 1. Phase 8.2 {@link VerifiedCodeActReceipt} 确定性沙盒执行通过率与 AST 安全格门控；
 * 2. {@link ReasoningChainConsistencyAuditor} 思维链双向忠实度与一致性评分；
 * 3. 千问 1024 维 {@code S^1023} 超球面查询相关性与相对既有最优兄弟节点的测地新息；
 * 4. Token 预算消耗惩罚项。
 * </p>
 */
public class NeuroSymbolicBranchEvaluator {

    public static final double DEFAULT_C_PUCT = 1.40;
    public static final double DEFAULT_DRIFT_TRIGGER_THRESHOLD = 0.35;

    /**
     * 不可变分支评估输入上下文。
     */
    public record BranchEvaluationInput(
            String requestId,
            String question,
            String reasoningContent,
            String candidateOutput,
            String evidenceSummary,
            VerifiedCodeActReceipt codeActReceipt,
            List<AttestedToolWitness> witnesses,
            float[] queryUnit1024,
            float[] branchUnit1024,
            float[] previousBestUnit1024,
            long tokensUsed,
            long tokenBudget,
            boolean temporalContradictionDetected,
            HnscdsAblationConfig ablationConfig
    ) {}

    /**
     * 不可变分支评估结果（含分项得分与客观硬失败诊断）。
     */
    public record BranchEvaluationOutcome(
            double compositeValue,
            double codeActScore,
            double consistencyScore,
            double geodesicNovelty,
            double tokenCostPenalty,
            boolean hardFailureDetected,
            CounterfactualBranchPatch.FailureRootCause detectedFailureCause,
            String failureDiagnostic
    ) {}

    private final ReasoningChainConsistencyAuditor consistencyAuditor;
    private final UnitSphereEmbeddingResolver embeddingResolver;

    public NeuroSymbolicBranchEvaluator() {
        this(new ReasoningChainConsistencyAuditor(new UnitSphereEmbeddingResolver()),
                new UnitSphereEmbeddingResolver());
    }

    public NeuroSymbolicBranchEvaluator(
            ReasoningChainConsistencyAuditor consistencyAuditor,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
        this.consistencyAuditor = consistencyAuditor != null
                ? consistencyAuditor
                : new ReasoningChainConsistencyAuditor(this.embeddingResolver);
    }

    /**
     * 对候选分支执行神经符号混合价值评估。
     */
    public BranchEvaluationOutcome evaluate(BranchEvaluationInput input) {
        Objects.requireNonNull(input, "BranchEvaluationInput 不能为空");

        boolean hardFailure = false;
        CounterfactualBranchPatch.FailureRootCause failureCause = null;
        StringBuilder diagBuilder = new StringBuilder();

        // 1. Phase 8.2 VerifiedCodeAct 客观执行见证评分
        double codeActScore = 0.55;
        VerifiedCodeActReceipt codeAct = input.codeActReceipt();
        if (codeAct != null) {
            if (codeAct.isSuccess()) {
                codeActScore = 1.0;
            } else {
                codeActScore = 0.0;
                hardFailure = true;
                boolean astPassed = codeAct.isolationAttestation() != null
                        && codeAct.isolationAttestation().astInspectorPassed();
                failureCause = astPassed
                        ? CounterfactualBranchPatch.FailureRootCause.CODEACT_ASSERTION_FAILED
                        : CounterfactualBranchPatch.FailureRootCause.TOOL_CONTRACT_VIOLATION;
                diagBuilder.append("CodeAct failed (exitCode=").append(codeAct.exitCode())
                        .append(", timedOut=").append(codeAct.timedOut())
                        .append(", isolation=").append(codeAct.isolationAttestation().isolationLevel())
                        .append("): ").append(codeAct.stderr());
            }
        }

        // 2. Ariadne / ConsisGuard 推理链双向一致性审计（开启 FRSV 时同步执行六维需求规格校验）
        boolean useFrsv = input.ablationConfig() != null
                && input.ablationConfig().requirementSpecVerificationEnabled();
        ReasoningConsistencyReceipt consReceipt;
        String specViolationHint = "";
        boolean specHardViolation = false;
        boolean codeActSpecMismatch = false;
        if (useFrsv) {
            var specAudit = consistencyAuditor.auditWithSpec(
                    input.requestId(),
                    input.question(),
                    input.reasoningContent(),
                    input.candidateOutput(),
                    input.witnesses(),
                    input.evidenceSummary(),
                    codeAct,
                    null,
                    false,
                    input.ablationConfig()
            );
            consReceipt = specAudit.consistencyReceipt();
            specHardViolation = specAudit.hasHardViolations();
            codeActSpecMismatch = !specAudit.codeActWitnessAligned();
            specViolationHint = specAudit.correctiveSpecHint();
        } else {
            consReceipt = consistencyAuditor.audit(
                    input.requestId(),
                    input.question(),
                    input.reasoningContent(),
                    input.candidateOutput(),
                    input.witnesses(),
                    input.evidenceSummary(),
                    false,
                    input.ablationConfig()
            );
        }
        double consistencyScore = consReceipt.overallConsistencyScore();
        if (consReceipt.causalDecoupledBlock()) {
            hardFailure = true;
            if (failureCause == null) {
                failureCause = CounterfactualBranchPatch.FailureRootCause.TOOL_CONTRACT_VIOLATION;
            }
            if (!diagBuilder.isEmpty()) {
                diagBuilder.append(" | ");
            }
            diagBuilder.append("Causal decoupled action blocked (phi=")
                    .append(consReceipt.counterfactualSensitivityPhi()).append(")");
        } else if (specHardViolation) {
            hardFailure = true;
            if (failureCause == null) {
                failureCause = codeActSpecMismatch
                        ? CounterfactualBranchPatch.FailureRootCause.CODEACT_ASSERTION_FAILED
                        : CounterfactualBranchPatch.FailureRootCause.CONSISTENCY_DRIFT;
            }
            if (!diagBuilder.isEmpty()) {
                diagBuilder.append(" | ");
            }
            diagBuilder.append("FRSV spec violation: ").append(specViolationHint);
        } else if (!hardFailure && !consReceipt.passed() && consistencyScore < 0.45) {
            hardFailure = true;
            failureCause = CounterfactualBranchPatch.FailureRootCause.CONSISTENCY_DRIFT;
            diagBuilder.append("Reasoning-evidence consistency drift (score=").append(consistencyScore).append(")");
        }

        // 3. 双时态因果矛盾校验
        if (input.temporalContradictionDetected()) {
            hardFailure = true;
            if (failureCause == null) {
                failureCause = CounterfactualBranchPatch.FailureRootCause.TEMPORAL_CONTRADICTION;
            }
            if (!diagBuilder.isEmpty()) {
                diagBuilder.append(" | ");
            }
            diagBuilder.append("Bi-temporal causal contradiction detected along reasoning trajectory");
        }

        // 4. S^1023 测地语义相关度与兄弟分支新息
        float[] qVec = input.queryUnit1024() != null
                ? input.queryUnit1024()
                : embeddingResolver.resolveUnit1024(input.question());
        float[] bVec = input.branchUnit1024() != null
                ? input.branchUnit1024()
                : embeddingResolver.resolveUnit1024(input.candidateOutput());

        double queryRelevance = clamp01(0.5 * (1.0 + cosine(qVec, bVec)));
        double siblingNovelty = input.previousBestUnit1024() == null
                ? 0.5
                : clamp01(0.5 * (1.0 - cosine(bVec, input.previousBestUnit1024())));
        double geodesicNovelty = clamp01(0.65 * queryRelevance + 0.35 * siblingNovelty);

        // 5. Token 预算惩罚与复合价值汇聚
        double budgetRatio = (double) Math.max(0L, input.tokensUsed()) / Math.max(1L, input.tokenBudget());
        double tokenCostPenalty = 0.15 * clamp01(budgetRatio);

        double rawValue = clamp01(
                0.45 * codeActScore + 0.35 * consistencyScore + 0.20 * geodesicNovelty - tokenCostPenalty
        );
        // 若出现硬失败，强制压低分支复合价值，确保触发反思与冷备隔离
        double finalValue = hardFailure ? Math.min(0.25, rawValue * 0.35) : rawValue;

        return new BranchEvaluationOutcome(
                finalValue,
                codeActScore,
                consistencyScore,
                geodesicNovelty,
                tokenCostPenalty,
                hardFailure,
                failureCause,
                diagBuilder.toString()
        );
    }

    /**
     * 标准 PUCT 上界选路分：{@code Q + c_puct * P * sqrt(N_parent + 1) / (1 + N_child)}。
     */
    public double computePuctScore(
            double meanValue,
            double priorProb,
            int parentVisits,
            int childVisits,
            double cPuct
    ) {
        double safeQ = clamp01(meanValue);
        double safeP = Math.max(0.05, Math.min(1.0, priorProb));
        double safeC = cPuct > 0.0 ? cPuct : DEFAULT_C_PUCT;
        double exploration = safeC * safeP * Math.sqrt(Math.max(1, parentVisits) + 1.0) / (1.0 + Math.max(0, childVisits));
        return safeQ + exploration;
    }

    /**
     * 超球面按需树搜索触发谓词 {@code Gamma(s_t)}：仅在存在显着测地漂移、沙盒报错或时态矛盾时方才展开树搜索。
     */
    public boolean shouldTriggerTreeSearch(
            float[] queryUnit1024,
            float[] observationUnit1024,
            VerifiedCodeActReceipt latestCodeActReceipt,
            boolean temporalContradictionDetected,
            double driftThreshold
    ) {
        if (temporalContradictionDetected) {
            return true;
        }
        if (latestCodeActReceipt != null && !latestCodeActReceipt.isSuccess()) {
            return true;
        }
        if (queryUnit1024 != null && observationUnit1024 != null && queryUnit1024.length == observationUnit1024.length) {
            double threshold = driftThreshold > 0.0 ? driftThreshold : DEFAULT_DRIFT_TRIGGER_THRESHOLD;
            double geodesicDrift = 1.0 - cosine(queryUnit1024, observationUnit1024);
            return geodesicDrift > threshold;
        }
        return false;
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }
}
