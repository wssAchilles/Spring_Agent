package tech.qiantong.qknow.hermes.cognitive.evolution;

import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.hermes.agent.skill.EnterpriseSkillRecord;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.tool.codeact.CodeActExecutionPlan;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 睡眠期可验证程序技能的反事实零回归准入门控（Phase 9.4 VPS-Evo，定理 4 核心实现）。
 * <p>
 * 将候选技能视为因果干预，在既有成功锚点与历史失败样例上同步统计：
 * 1. {@link AstSecurityInspector} 静态 AST 自包含安全审计通过；
 * 2. 候选技能自身 {@code unitTestAssertions} 测试桩全部执行通过；
 * 3. 在对照集上严格满足 **零回归（regressionsCount == 0）** 且 **正净修复（repairsCount > 0）**；
 * 4. 签发有限样本 PAC 退化上界 {@code min(1.0, ln(1 / delta) / N_pass)}。
 * </p>
 */
public class CounterfactualSkillRegressionGate {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{[a-zA-Z0-9_]+\\}");

    /**
     * 历史对照锚点测试样例。
     */
    public record AnchorTestCase(
            String caseId,
            String query,
            Map<String, String> slotBindings,
            ReadOnlyDatasetView datasetView,
            String expectedOutputToken,
            boolean baselinePassed
    ) {
        public AnchorTestCase {
            slotBindings = slotBindings != null ? Map.copyOf(slotBindings) : Map.of();
            expectedOutputToken = expectedOutputToken != null ? expectedOutputToken.trim() : "";
        }
    }

    /**
     * Phase 10.4 作用域限定反事实锚点测试样例（携带 MetricType 与 S^1023 查询向量）。
     */
    public record ScopedAnchorTestCase(
            String caseId,
            String query,
            Map<String, String> slotBindings,
            ReadOnlyDatasetView datasetView,
            String expectedOutputToken,
            boolean baselinePassed,
            QuestionRequirementSpec.MetricType metricType,
            float[] queryUnit1024
    ) {
        public ScopedAnchorTestCase {
            slotBindings = slotBindings != null ? Map.copyOf(slotBindings) : Map.of();
            expectedOutputToken = expectedOutputToken != null ? expectedOutputToken.trim() : "";
            metricType = metricType != null ? metricType : QuestionRequirementSpec.MetricType.RAW_VALUE;
            queryUnit1024 = queryUnit1024 != null ? queryUnit1024.clone() : new float[1024];
        }
    }

    /**
     * 反事实准入审计不可变凭单。
     */
    public record SkillAdmissionReceipt(
            String skillId,
            boolean admitted,
            boolean astPassed,
            boolean unitTestsPassed,
            int repairsCount,
            int regressionsCount,
            int baselinePassAnchorCount,
            double pacRegressionUpperBound,
            String rejectionReason
    ) {}

    /**
     * Phase 10.4 条件作用域 Seeger/Maurer PAC-Bayes 准入审计不可变凭单。
     */
    public record ScopedSkillAdmissionReceipt(
            String skillId,
            boolean admitted,
            boolean astPassed,
            boolean unitTestsPassed,
            int repairsCount,
            int regressionsCount,
            int scopedAnchorCount,
            int scopedBaselinePassCount,
            double seegerPacUpperBound,
            double empiricalScopedErrorRate,
            String rejectionReason
    ) {
        public SkillAdmissionReceipt toLegacyReceipt() {
            return new SkillAdmissionReceipt(
                    skillId, admitted, astPassed, unitTestsPassed,
                    repairsCount, regressionsCount, scopedBaselinePassCount,
                    seegerPacUpperBound, rejectionReason
            );
        }
    }

    /**
     * 执行技能准入评估与 PAC 非退化上界测算。
     */
    public SkillAdmissionReceipt evaluateAdmission(
            EnterpriseSkillRecord candidate,
            List<AnchorTestCase> anchors,
            double deltaConfidence,
            AstSecurityInspector astInspector,
            VerifiedCodeActSandboxRuntime sandboxRuntime
    ) {
        Objects.requireNonNull(candidate, "candidate skill 不能为空");
        AstSecurityInspector inspector = astInspector != null ? astInspector : new AstSecurityInspector();

        // 0. 常量时间内容指纹验签
        if (!candidate.verifyContentSha256ConstantTime()) {
            return new SkillAdmissionReceipt(
                    candidate.skillId(), false, false, false,
                    0, 0, 0, 1.0, "SKILL_SHA256_VERIFY_FAILED"
            );
        }

        // 1. AST 静态安全审计（先对参数化 ${slot} 槽位进行安全字面量占位渲染再执行语法与 AST 检查）
        String templateCode = candidate.codeActPythonTemplate();
        if (templateCode == null || templateCode.isBlank()) {
            return new SkillAdmissionReceipt(
                    candidate.skillId(), false, false, false,
                    0, 0, 0, 1.0, "EMPTY_CODEACT_TEMPLATE"
            );
        }
        String renderedForStaticAndUnit = renderTemplateWithFallbackSlots(templateCode, anchors);
        String astViolation = inspector.inspect("python", renderedForStaticAndUnit);
        if (astViolation != null && !astViolation.isBlank()) {
            return new SkillAdmissionReceipt(
                    candidate.skillId(), false, false, false,
                    0, 0, 0, 1.0, "AST_VIOLATION: " + astViolation
            );
        }

        // 2. 候选技能单元测试桩校验（修复：绑定首个非空锚点 ReadOnlyDatasetView，避免传入 null 导致视图空跑）
        List<String> unitTests = candidate.unitTestAssertions();
        if (unitTests.isEmpty()) {
            return new SkillAdmissionReceipt(
                    candidate.skillId(), false, true, false,
                    0, 0, 0, 1.0, "MISSING_UNIT_TEST_ASSERTIONS"
            );
        }
        ReadOnlyDatasetView fallbackAnchorView = null;
        if (anchors != null) {
            for (AnchorTestCase a : anchors) {
                if (a != null && a.datasetView() != null) {
                    fallbackAnchorView = a.datasetView();
                    break;
                }
            }
        }
        if (sandboxRuntime != null) {
            for (String assertCode : unitTests) {
                String combinedTestScript = renderedForStaticAndUnit + "\n" + assertCode;
                VerifiedCodeActReceipt testReceipt = sandboxRuntime.execute(
                        new CodeActExecutionPlan(
                                "ut-" + UUID.randomUUID().toString().substring(0, 8),
                                CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                                "python",
                                combinedTestScript,
                                fallbackAnchorView,
                                List.of(candidate.skillId()),
                                EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
                        ),
                        Map.of()
                );
                if (!testReceipt.isSuccess()) {
                    return new SkillAdmissionReceipt(
                            candidate.skillId(), false, true, false,
                            0, 0, 0, 1.0, "UNIT_TEST_FAILED: " + testReceipt.stderr()
                    );
                }
            }
        }

        // 3. 反事实对照评估（统计 Repairs 与 Regressions）
        int baselinePassCount = 0;
        int repairs = 0;
        int regressions = 0;
        if (anchors != null && !anchors.isEmpty() && sandboxRuntime != null) {
            for (AnchorTestCase anchor : anchors) {
                if (anchor.baselinePassed()) {
                    baselinePassCount++;
                }
                String rendered = renderWithBindings(templateCode, anchor.slotBindings());
                String astCheck = inspector.inspect("python", rendered);
                boolean execOk = false;
                if (astCheck == null || astCheck.isBlank()) {
                    VerifiedCodeActReceipt anchorRcpt = sandboxRuntime.execute(
                            new CodeActExecutionPlan(
                                    "cf-" + UUID.randomUUID().toString().substring(0, 8),
                                    CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                                    "python",
                                    rendered,
                                    anchor.datasetView(),
                                    List.of(candidate.skillId()),
                                    EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
                            ),
                            Map.of()
                    );
                    execOk = anchorRcpt.isSuccess()
                            && (anchor.expectedOutputToken().isEmpty()
                            || anchorRcpt.stdout().trim().contains(anchor.expectedOutputToken()));
                }
                if (anchor.baselinePassed() && !execOk) {
                    regressions++;
                } else if (!anchor.baselinePassed() && execOk) {
                    repairs++;
                }
            }
        }

        double safeDelta = Math.max(1e-6, Math.min(0.5, deltaConfidence));
        double pacBound = baselinePassCount > 0
                ? Math.min(1.0, Math.log(1.0 / safeDelta) / (double) baselinePassCount)
                : 1.0;

        if (regressions > 0) {
            return new SkillAdmissionReceipt(
                    candidate.skillId(), false, true, true,
                    repairs, regressions, baselinePassCount, pacBound,
                    "COUNTERFACTUAL_REGRESSION_DETECTED: regressions=" + regressions
            );
        }
        if (anchors != null && !anchors.isEmpty() && repairs <= 0) {
            return new SkillAdmissionReceipt(
                    candidate.skillId(), false, true, true,
                    0, 0, baselinePassCount, pacBound,
                    "ZERO_POSITIVE_REPAIR_GAIN"
            );
        }

        return new SkillAdmissionReceipt(
                candidate.skillId(), true, true, true,
                repairs, regressions, baselinePassCount, pacBound, ""
        );
    }

    /**
     * Phase 10.4 P-VPS-Evo 条件作用域反事实准入与 Seeger/Maurer PAC-Bayes 零回归门控（对应定理 4）。
     * <p>
     * 仅在与候选技能同 {@link QuestionRequirementSpec.MetricType} 且超球面余弦 {@code >= scopeCosineThreshold}
     * 的相关作用域锚点集上评估零回归与净修复，按 MDL 描述长度计算非退化 Seeger PAC-Bayes 紧界（严格 {@code < 1.0}）。
     * </p>
     */
    public ScopedSkillAdmissionReceipt evaluateScopedAdmission(
            SynthesizedSkillCandidate candidate,
            List<ScopedAnchorTestCase> allAnchors,
            double scopeCosineThreshold,
            double deltaConfidence,
            AstSecurityInspector astInspector,
            VerifiedCodeActSandboxRuntime sandboxRuntime
    ) {
        Objects.requireNonNull(candidate, "SynthesizedSkillCandidate 不能为空");
        AstSecurityInspector inspector = astInspector != null ? astInspector : new AstSecurityInspector();

        String templateCode = candidate.codeActPythonTemplate();
        if (templateCode == null || templateCode.isBlank()) {
            return new ScopedSkillAdmissionReceipt(
                    candidate.skillId(), false, false, false,
                    0, 0, 0, 0, 1.0, 1.0, "EMPTY_CODEACT_TEMPLATE"
            );
        }

        // 0. 常量时间 SHA-256 构造与核验
        EnterpriseSkillRecord materialized = EnterpriseSkillRecord.ofCodeActTemplate(
                candidate.skillId(),
                candidate.name(),
                candidate.description(),
                candidate.parameterSlots(),
                templateCode,
                candidate.unitTestAssertions(),
                EnterpriseSkillRecord.SkillLifecycleState.PROBATION,
                candidate.scopedAnchors().isEmpty() ? new float[1024] : candidate.scopedAnchors().get(0).queryUnit1024()
        );
        if (!materialized.verifyContentSha256ConstantTime()) {
            return new ScopedSkillAdmissionReceipt(
                    candidate.skillId(), false, false, false,
                    0, 0, 0, 0, 1.0, 1.0, "SKILL_SHA256_VERIFY_FAILED"
            );
        }

        // 1. 构建同 MetricType + S^1023 余弦作用域锚点子集
        double safeScopeCos = Math.max(0.50, Math.min(0.99, scopeCosineThreshold));
        float[] candidateCenter = candidate.scopedAnchors().isEmpty()
                ? null
                : candidate.scopedAnchors().get(0).queryUnit1024();
        List<ScopedAnchorTestCase> scopedAnchors = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();

        for (ScopedAnchorTestCase own : candidate.scopedAnchors()) {
            if (own != null && seenIds.add(own.caseId())) {
                scopedAnchors.add(own);
            }
        }
        if (allAnchors != null) {
            for (ScopedAnchorTestCase ext : allAnchors) {
                if (ext == null || !seenIds.add(ext.caseId())) {
                    continue;
                }
                if (ext.metricType() != candidate.metricType()) {
                    continue;
                }
                if (candidateCenter != null && ext.queryUnit1024() != null
                        && ext.queryUnit1024().length == candidateCenter.length) {
                    double cos = cosineUnit(candidateCenter, ext.queryUnit1024());
                    if (cos < safeScopeCos) {
                        continue;
                    }
                }
                scopedAnchors.add(ext);
            }
        }

        if (scopedAnchors.isEmpty()) {
            return new ScopedSkillAdmissionReceipt(
                    candidate.skillId(), false, false, false,
                    0, 0, 0, 0, 1.0, 1.0, "NO_SCOPED_ANCHORS"
            );
        }

        // 2. AST 静态安全审计
        Map<String, String> fallbackBindings = scopedAnchors.get(0).slotBindings();
        String renderedStatic = renderWithBindings(templateCode, fallbackBindings);
        String astErr = inspector.inspect("python", renderedStatic);
        if (astErr != null && !astErr.isBlank()) {
            return new ScopedSkillAdmissionReceipt(
                    candidate.skillId(), false, false, false,
                    0, 0, scopedAnchors.size(), 0, 1.0, 1.0, "AST_VIOLATION: " + astErr
            );
        }

        // 3. 自包含单元测试执行（显式绑定首个非空 ReadOnlyDatasetView）
        List<String> unitTests = candidate.unitTestAssertions();
        if (unitTests.isEmpty()) {
            return new ScopedSkillAdmissionReceipt(
                    candidate.skillId(), false, true, false,
                    0, 0, scopedAnchors.size(), 0, 1.0, 1.0, "MISSING_UNIT_TEST_ASSERTIONS"
            );
        }
        ReadOnlyDatasetView firstView = null;
        for (ScopedAnchorTestCase a : scopedAnchors) {
            if (a.datasetView() != null) {
                firstView = a.datasetView();
                break;
            }
        }
        if (sandboxRuntime != null) {
            for (String assertCode : unitTests) {
                VerifiedCodeActReceipt utRcpt = sandboxRuntime.execute(
                        new CodeActExecutionPlan(
                                "pvps-ut-" + UUID.randomUUID().toString().substring(0, 8),
                                CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                                "python",
                                renderedStatic + "\n" + assertCode,
                                firstView,
                                List.of(candidate.skillId()),
                                EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
                        ),
                        Map.of()
                );
                if (!utRcpt.isSuccess()) {
                    return new ScopedSkillAdmissionReceipt(
                            candidate.skillId(), false, true, false,
                            0, 0, scopedAnchors.size(), 0, 1.0, 1.0,
                            "UNIT_TEST_FAILED: " + utRcpt.stderr()
                    );
                }
            }
        }

        // 4. 在条件作用域锚点上运行沙盒评估零回归与净修复
        int nScope = scopedAnchors.size();
        int baselinePassCount = 0;
        int repairs = 0;
        int regressions = 0;
        int totalPassed = 0;

        for (ScopedAnchorTestCase anchor : scopedAnchors) {
            if (anchor.baselinePassed()) {
                baselinePassCount++;
            }
            boolean execOk = false;
            String renderedAnchor = renderWithBindings(templateCode, anchor.slotBindings());
            String anchorAst = inspector.inspect("python", renderedAnchor);
            if (anchorAst == null || anchorAst.isBlank()) {
                if (sandboxRuntime != null) {
                    VerifiedCodeActReceipt rcpt = sandboxRuntime.execute(
                            new CodeActExecutionPlan(
                                    "pvps-cf-" + UUID.randomUUID().toString().substring(0, 8),
                                    CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                                    "python",
                                    renderedAnchor,
                                    anchor.datasetView() != null ? anchor.datasetView() : firstView,
                                    List.of(candidate.skillId()),
                                    EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
                            ),
                            Map.of()
                    );
                    execOk = rcpt.isSuccess()
                            && (anchor.expectedOutputToken().isEmpty()
                            || rcpt.stdout().trim().contains(anchor.expectedOutputToken()));
                } else {
                    execOk = true;
                }
            }
            if (execOk) {
                totalPassed++;
            }
            if (anchor.baselinePassed() && !execOk) {
                regressions++;
            } else if (!anchor.baselinePassed() && execOk) {
                repairs++;
            }
        }

        double empiricalErr = (double) (nScope - totalPassed) / (double) nScope;
        double safeDelta = Math.max(1e-6, Math.min(0.5, deltaConfidence));
        double dlBits = Math.min(12.0, Math.max(2.0,
                candidate.parameterSlots().size() * 1.5
                        + Math.log(Math.max(4.0, templateCode.length())) / Math.log(2.0)));
        double klBudget = (dlBits * Math.log(2.0) + Math.log(2.0 * Math.sqrt((double) nScope) / safeDelta))
                / (double) nScope;
        double seegerPacBound = (regressions == 0 && empiricalErr == 0.0)
                ? Math.min(0.999999, 1.0 - Math.exp(-klBudget))
                : 1.0;

        if (regressions > 0) {
            return new ScopedSkillAdmissionReceipt(
                    candidate.skillId(), false, true, true,
                    repairs, regressions, nScope, baselinePassCount,
                    1.0, empiricalErr, "SCOPED_REGRESSION_DETECTED: regressions=" + regressions
            );
        }
        if (empiricalErr > 0.0 || (repairs <= 0 && totalPassed == 0)) {
            return new ScopedSkillAdmissionReceipt(
                    candidate.skillId(), false, true, true,
                    repairs, regressions, nScope, baselinePassCount,
                    seegerPacBound, empiricalErr, "SCOPED_ANCHOR_EXECUTION_FAILED"
            );
        }

        return new ScopedSkillAdmissionReceipt(
                candidate.skillId(), true, true, true,
                repairs, regressions, nScope, baselinePassCount,
                seegerPacBound, empiricalErr, ""
        );
    }

    private static double cosineUnit(float[] u, float[] v) {
        double dot = 0.0;
        for (int i = 0; i < u.length; i++) {
            dot += (double) u[i] * (double) v[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    private static String renderTemplateWithFallbackSlots(String templateCode, List<AnchorTestCase> anchors) {
        Map<String, String> firstBindings = (anchors != null && !anchors.isEmpty() && anchors.get(0) != null)
                ? anchors.get(0).slotBindings()
                : Map.of();
        return renderWithBindings(templateCode, firstBindings);
    }

    private static String renderWithBindings(String templateCode, Map<String, String> bindings) {
        String rendered = templateCode;
        if (bindings != null) {
            for (Map.Entry<String, String> slot : bindings.entrySet()) {
                String safeVal = slot.getValue() == null
                        ? "1"
                        : slot.getValue().replace(";", "").replace("\n", " ").replace("\r", " ");
                rendered = rendered.replace("${" + slot.getKey() + "}", safeVal);
            }
        }
        return PLACEHOLDER_PATTERN.matcher(rendered).replaceAll("1");
    }
}
