package tech.qiantong.qknow.hermes.cognitive.verification;

import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec.EntityGranularity;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec.MetricType;
import tech.qiantong.qknow.hermes.cognitive.verification.RequirementSpecAuditResult.SpecViolation;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ariadne / ConsisGuard 推理链双向一致性、反事实因果灵敏度与六维需求规格审计器（对应论文第 5/6 章 + Phase 9.1 FRSV）。
 * <p>
 * 数学机制：
 * 1. 前向忠实度 {@code Fwd(R, E_t)}：衡量 DeepSeek {@code reasoning_content} 是否在 {@code S^1023} 流形上忠实贴合工具实证见证 {@code E_t}；
 * 2. 反向归因 {@code Bwd(y_t, R U E_t)}：衡量最终答案/输出是否具备充分的推理链与实证依据覆盖（抑制 Reasoning Theater）；
 * 3. 反事实因果灵敏度 {@code phi = 1 - S(a, a*)}：针对高危/不可逆工具调用，通过构造反事实扰动前置条件评估动作敏感度；
 * 4. Phase 9.1 六维形式化需求规格门控（FRSV）：引入 {@code SpecSat(Q, y_t, R, W_code) * 0.45^|V_hard|}，
 *    将单位/百分比、分母口径、差值方向、实体粒度、格式与 CodeAct 数值见证错配刚性压至 <= 0.45。
 * </p>
 */
public class ReasoningChainConsistencyAuditor {

    public static final double DEFAULT_PASS_THRESHOLD = 0.65;
    public static final double DEFAULT_CAUSAL_PHI_THRESHOLD = 0.15;
    public static final double SPEC_HARD_PENALTY_GAMMA = 0.45;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![a-zA-Z])[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?");
    private static final Set<String> DENOM_STOPWORDS = Set.of(
            "the", "of", "in", "on", "to", "a", "an", "and", "or", "all", "total", "both", "each"
    );

    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final String hmacSecretKey;
    private final double passThreshold;
    private final double causalPhiThreshold;
    private final QuestionRequirementSpecCompiler specCompiler = new QuestionRequirementSpecCompiler();
    private volatile PremiseGroundingAuditor premiseGroundingAuditor = new PremiseGroundingAuditor();
    private final AtomicLong totalSpecAudits = new AtomicLong(0L);
    private final AtomicLong totalHardSpecBlocks = new AtomicLong(0L);
    private final AtomicLong totalCodeActBindingsChecked = new AtomicLong(0L);
    private final AtomicLong totalCodeActMismatchIntercepted = new AtomicLong(0L);

    public ReasoningChainConsistencyAuditor(UnitSphereEmbeddingResolver embeddingResolver) {
        this(embeddingResolver, "hermes-consistency-default-secret",
                DEFAULT_PASS_THRESHOLD, DEFAULT_CAUSAL_PHI_THRESHOLD);
    }

    public ReasoningChainConsistencyAuditor(
            UnitSphereEmbeddingResolver embeddingResolver,
            String hmacSecretKey,
            double passThreshold,
            double causalPhiThreshold
    ) {
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-consistency-default-secret"
                : hmacSecretKey;
        this.passThreshold = passThreshold;
        this.causalPhiThreshold = causalPhiThreshold;
    }

    public void setPremiseGroundingAuditor(PremiseGroundingAuditor premiseGroundingAuditor) {
        if (premiseGroundingAuditor != null) {
            this.premiseGroundingAuditor = premiseGroundingAuditor;
        }
    }

    /**
     * 对推理链、工具实证见证集合及最终回答/高危动作执行双向一致性与因果解耦审计。
     */
    public ReasoningConsistencyReceipt audit(
            String requestId,
            String question,
            String reasoningContent,
            String finalAnswerOrAction,
            List<AttestedToolWitness> witnesses,
            String witnessRawEvidenceSummary,
            boolean highRiskAction,
            HnscdsAblationConfig ablationConfig
    ) {
        HnscdsAblationConfig cfg = ablationConfig == null ? HnscdsAblationConfig.defaults() : ablationConfig;
        if (cfg.requirementSpecVerificationEnabled()) {
            return auditWithSpec(
                    requestId, question, reasoningContent, finalAnswerOrAction,
                    witnesses, witnessRawEvidenceSummary, null, null, highRiskAction, cfg
            ).consistencyReceipt();
        }
        return auditInternal(
                requestId, question, reasoningContent, finalAnswerOrAction,
                witnesses, witnessRawEvidenceSummary, highRiskAction, cfg
        );
    }

    /**
     * Phase 9.1 FRSV 六维形式化需求规格审计（定理 4）：
     * 结合前向忠实度、反向归因与零成本六维规格乘性门控，并强绑定 VerifiedCodeAct 客观数值见证。
     */
    public RequirementSpecAuditResult auditWithSpec(
            String requestId,
            String question,
            String reasoningContent,
            String finalAnswerOrAction,
            List<AttestedToolWitness> witnesses,
            String witnessRawEvidenceSummary,
            VerifiedCodeActReceipt codeActReceipt,
            QuestionRequirementSpec precompiledSpec,
            boolean highRiskAction,
            HnscdsAblationConfig ablationConfig
    ) {
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-audit" : requestId;
        long nowMs = System.currentTimeMillis();
        HnscdsAblationConfig cfg = ablationConfig == null ? HnscdsAblationConfig.defaults() : ablationConfig;
        boolean guardEnabled = cfg.ariadneConsisGuardEnabled() || cfg.requirementSpecVerificationEnabled();
        QuestionRequirementSpec spec = precompiledSpec != null ? precompiledSpec : specCompiler.compile(question);

        CoreComponents comp = computeCoreComponents(
                question, reasoningContent, finalAnswerOrAction,
                witnesses, witnessRawEvidenceSummary, highRiskAction, guardEnabled
        );

        String safeReasoning = comp.safeReasoning();
        String safeAnswer = comp.safeAnswer();
        String safeEvidence = comp.safeEvidence();
        List<SpecViolation> violations = new ArrayList<>();
        boolean codeActAligned = true;

        if (cfg.requirementSpecVerificationEnabled()) {
            totalSpecAudits.incrementAndGet();
            String finalSeg = extractFinalAnswerSegment(safeAnswer);
            String finalLower = finalSeg.toLowerCase(Locale.ROOT);
            String reasoningCombined = (safeReasoning + "\n" + safeEvidence).toLowerCase(Locale.ROOT);

            // 7. Phase 10.3 PG-FRSV：复合前提锚定与不可回答主题偷换审计（先判定是否合法拒答以决定是否豁免数值格式约束）
            boolean justifiedAbstentionExempt = false;
            if (cfg.completenessAndPremiseGateEnabled() && premiseGroundingAuditor != null && !safeEvidence.isEmpty()) {
                var premiseReport = premiseGroundingAuditor.auditPremiseGrounding(
                        question, safeEvidence, finalSeg, 3, 3
                );
                if (premiseReport.topicShiftRiskDetected() && !premiseReport.answerIsAbstention()) {
                    violations.add(new SpecViolation(
                            "PREMISE",
                            "PREMISE_TOPIC_SHIFT_UNGROUNDED",
                            premiseReport.diagnosticHint(),
                            true
                    ));
                } else if (premiseReport.abstentionJustified() && premiseReport.answerIsAbstention()) {
                    justifiedAbstentionExempt = true;
                }
            }

            // 1. 单位/百分比与布尔格式硬约束（限定在 Final Answer 最终结论段，避免长思考正文干扰）
            if (!justifiedAbstentionExempt && "%".equals(spec.requiredUnit()) && !finalSeg.isEmpty()) {
                boolean hasPercentMark = finalSeg.contains("%") || finalLower.contains("percent") || finalLower.contains("百分比");
                if (!hasPercentMark) {
                    violations.add(new SpecViolation(
                            "UNIT",
                            "UNIT_PERCENTAGE_MISSING",
                            "问题要求输出百分比 (%)，但候选答案缺失 % 单位（可能误报了绝对差值或裸小数）: " + finalSeg,
                            true
                    ));
                }
            }
            if (!justifiedAbstentionExempt && spec.metricType() == MetricType.BOOLEAN_YES_NO && !finalSeg.isEmpty()) {
                boolean hasBool = finalLower.contains("yes") || finalLower.contains("no")
                        || finalLower.contains("true") || finalLower.contains("false")
                        || finalSeg.contains("是") || finalSeg.contains("否");
                if (!hasBool) {
                    violations.add(new SpecViolation(
                            "FORMAT",
                            "BOOLEAN_FORMAT_MISSING",
                            "问题为布尔判定题，但候选答案未给出明确的 yes/no/是/否 判定",
                            true
                    ));
                }
            }

            // 2. 分母口径硬约束
            if (!spec.requiredDenominatorHint().isBlank()) {
                List<String> denomTerms = extractSignificantTerms(spec.requiredDenominatorHint());
                if (!denomTerms.isEmpty()) {
                    String checkScope = (codeActReceipt != null && !codeActReceipt.stdout().isBlank())
                            ? (reasoningCombined + "\n" + codeActReceipt.stdout().toLowerCase(Locale.ROOT))
                            : reasoningCombined;
                    long matched = denomTerms.stream().filter(checkScope::contains).count();
                    if (matched == 0) {
                        violations.add(new SpecViolation(
                                "DENOMINATOR",
                                "DENOMINATOR_SCOPE_MISMATCH",
                                "推理链与计算过程未覆盖问题指定分母口径 [" + spec.requiredDenominatorHint() + "]",
                                true
                        ));
                    }
                }
            }

            // 3. 增减差值算子硬约束（防止直接抄写终值/初值替代差值）
            if ((spec.metricType() == MetricType.ABSOLUTE_DIFF || spec.metricType() == MetricType.SIGNED_DIFF)
                    && !finalSeg.isEmpty() && codeActReceipt == null) {
                List<Double> ansNums = extractNumbers(finalSeg);
                List<Double> evNums = extractNumbers(safeEvidence);
                if (!ansNums.isEmpty() && evNums.size() >= 2) {
                    double ansVal = Math.abs(ansNums.get(0));
                    boolean matchesSingleEndpoint = evNums.stream().anyMatch(v -> Math.abs(Math.abs(v) - ansVal) <= 1e-6);
                    boolean showsDeltaOp = safeReasoning.contains("-") || safeReasoning.contains("−")
                            || safeReasoning.toLowerCase(Locale.ROOT).contains("difference")
                            || safeReasoning.toLowerCase(Locale.ROOT).contains("subtract")
                            || safeReasoning.contains("差值") || safeReasoning.contains("减去");
                    if (matchesSingleEndpoint && !showsDeltaOp) {
                        violations.add(new SpecViolation(
                                "OPERATOR",
                                "DELTA_RAW_ENDPOINT_COPIED",
                                "问题要求计算差值/增减量，但答案直接复述了单一端点值 [" + ansVal + "] 且缺少减法推导或 CodeAct 见证",
                                true
                        ));
                    }
                }
            }

            // 4. 实体粒度硬约束（防止上层网络/母公司被子频道/子公司替代）
            if (spec.entityGranularity() == EntityGranularity.OVERARCHING_ORG_OR_NETWORK && !finalSeg.isEmpty()) {
                boolean hitForbiddenSub = spec.forbiddenSubEntityHints().stream().anyMatch(finalLower::contains);
                if (hitForbiddenSub && !safeEvidence.isEmpty()) {
                    String evLower = safeEvidence.toLowerCase(Locale.ROOT);
                    // 检测证据中是否存在去除子后缀后的上层主体（例如 BBC Radio 4 -> BBC）或 explicit parent keyword
                    boolean evidenceHasParentCue = evLower.contains("owned by") || evLower.contains("part of")
                            || evLower.contains("operated by") || evLower.contains("network")
                            || evLower.contains("parent company") || evLower.contains("母公司")
                            || hasPrefixParentToken(finalLower, evLower);
                    if (evidenceHasParentCue) {
                        violations.add(new SpecViolation(
                                "GRANULARITY",
                                "ENTITY_GRANULARITY_TOO_NARROW",
                                "问题询问上层机构/网络粒度，候选答案落入子频道/子实体 [" + finalSeg + "]",
                                true
                        ));
                    }
                }
            }

            // 5. y_references 格式硬约束（严格校验最终答案段）
            if ("y_references".equals(spec.outputFormatPattern()) && !finalSeg.isEmpty()) {
                if (!finalLower.contains("y_references") && !finalSeg.contains("[[")) {
                    violations.add(new SpecViolation(
                            "FORMAT",
                            "Y_REFERENCES_FORMAT_MISSING",
                            "图表/预测题要求输出 y_references 序列格式，当前答案未匹配该结构",
                            true
                    ));
                }
            }

            // 6. CodeAct 客观数值见证强绑定（仅校验显式数值/序列问题，且从 Final Answer 段提数防解释文本噪音）
            boolean isNumericMetric = spec.metricType() == MetricType.PERCENTAGE_CHANGE
                    || spec.metricType() == MetricType.RATIO_SHARE
                    || spec.metricType() == MetricType.ABSOLUTE_DIFF
                    || spec.metricType() == MetricType.SIGNED_DIFF
                    || spec.metricType() == MetricType.COUNT
                    || spec.metricType() == MetricType.RAW_VALUE
                    || spec.metricType() == MetricType.SERIES_LIST;
            if (codeActReceipt != null && isNumericMetric) {
                totalCodeActBindingsChecked.incrementAndGet();
                if (!codeActReceipt.isSuccess() && spec.requiresCodeActWitness()) {
                    codeActAligned = false;
                    totalCodeActMismatchIntercepted.incrementAndGet();
                    violations.add(new SpecViolation(
                            "CODEACT_WITNESS",
                            "CODEACT_EXECUTION_FAILED",
                            "必需的 VerifiedCodeAct 计算见证未成功: exitCode=" + codeActReceipt.exitCode(),
                            true
                    ));
                } else if (codeActReceipt.isSuccess() && !finalSeg.isEmpty()) {
                    List<Double> codeNums = extractNumbers(codeActReceipt.stdout());
                    List<Double> ansNums = extractNumbers(finalSeg);
                    if (!codeNums.isEmpty() && !ansNums.isEmpty()) {
                        boolean matchedAny = ansNums.stream()
                                .anyMatch(av -> codeNums.stream().anyMatch(cv -> isNumericallyClose(cv, av)));
                        if (!matchedAny) {
                            codeActAligned = false;
                            totalCodeActMismatchIntercepted.incrementAndGet();
                            violations.add(new SpecViolation(
                                    "CODEACT_WITNESS",
                                    "CODEACT_STDOUT_ANSWER_MISMATCH",
                                    "最终答案数值 " + ansNums + " 与 CodeAct 沙盒输出 " + codeNums + " 不一致（疑似抄错或绕过计算结果）",
                                    true
                            ));
                        }
                    }
                }
            }
        }

        int hardCount = 0;
        StringBuilder hintBuilder = new StringBuilder();
        for (SpecViolation v : violations) {
            if (v.hardViolation()) {
                hardCount++;
            }
            if (!hintBuilder.isEmpty()) {
                hintBuilder.append("; ");
            }
            hintBuilder.append('[').append(v.code()).append("] ").append(v.diagnostic());
        }
        if (hardCount > 0) {
            totalHardSpecBlocks.incrementAndGet();
        }

        double specSatisfaction = violations.isEmpty()
                ? 1.0
                : Math.max(0.0, 1.0 - 0.35 * hardCount - 0.15 * (violations.size() - hardCount));
        double penaltyFactor = Math.pow(SPEC_HARD_PENALTY_GAMMA, hardCount);
        double rawV4 = clamp01(0.30 * comp.fwd() + 0.30 * comp.bwd() + 0.40 * specSatisfaction);
        double overallV4 = clamp01(rawV4 * penaltyFactor);

        boolean passed = !guardEnabled || (!comp.causalDecoupledBlock() && hardCount == 0 && overallV4 >= passThreshold);
        boolean healingRequired = guardEnabled && !passed && !comp.causalDecoupledBlock();

        ReasoningConsistencyReceipt receipt = ReasoningConsistencyReceipt.createSigned(
                safeReq, comp.fwd(), comp.bwd(), comp.phi(), overallV4, comp.latticeGrade(),
                passed, healingRequired, comp.causalDecoupledBlock(), nowMs, hmacSecretKey
        );
        return new RequirementSpecAuditResult(
                receipt,
                spec,
                specSatisfaction,
                penaltyFactor,
                violations,
                codeActAligned,
                hintBuilder.toString()
        );
    }

    /**
     * 输出供博士工作台与控制器消费的 FRSV 规格审计遥测指标。
     */
    public Map<String, Object> getSpecTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalSpecAudits", totalSpecAudits.get());
        map.put("totalHardSpecBlocks", totalHardSpecBlocks.get());
        map.put("totalCodeActBindingsChecked", totalCodeActBindingsChecked.get());
        map.put("totalCodeActMismatchIntercepted", totalCodeActMismatchIntercepted.get());
        return Collections.unmodifiableMap(map);
    }

    private ReasoningConsistencyReceipt auditInternal(
            String requestId,
            String question,
            String reasoningContent,
            String finalAnswerOrAction,
            List<AttestedToolWitness> witnesses,
            String witnessRawEvidenceSummary,
            boolean highRiskAction,
            HnscdsAblationConfig cfg
    ) {
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-audit" : requestId;
        long nowMs = System.currentTimeMillis();
        boolean guardEnabled = cfg.ariadneConsisGuardEnabled();
        CoreComponents comp = computeCoreComponents(
                question, reasoningContent, finalAnswerOrAction,
                witnesses, witnessRawEvidenceSummary, highRiskAction, guardEnabled
        );

        double overall = clamp01(0.5 * comp.fwd() + 0.5 * comp.bwd());
        boolean passed = !guardEnabled || (!comp.causalDecoupledBlock() && overall >= passThreshold);
        boolean healingRequired = guardEnabled && !passed && !comp.causalDecoupledBlock();

        return ReasoningConsistencyReceipt.createSigned(
                safeReq, comp.fwd(), comp.bwd(), comp.phi(), overall, comp.latticeGrade(),
                passed, healingRequired, comp.causalDecoupledBlock(), nowMs, hmacSecretKey
        );
    }

    private record CoreComponents(
            EvidenceGrade latticeGrade,
            String safeReasoning,
            String safeAnswer,
            String safeEvidence,
            double fwd,
            double bwd,
            double phi,
            boolean causalDecoupledBlock
    ) {}

    private CoreComponents computeCoreComponents(
            String question,
            String reasoningContent,
            String finalAnswerOrAction,
            List<AttestedToolWitness> witnesses,
            String witnessRawEvidenceSummary,
            boolean highRiskAction,
            boolean guardEnabled
    ) {
        // 1. EG-VAR 只降级不升级格合并全部见证等级
        EvidenceGrade latticeGrade = EvidenceGrade.UNVERIFIED;
        if (witnesses != null && !witnesses.isEmpty()) {
            latticeGrade = EvidenceGrade.DOUBLE_ATTESTED;
            for (AttestedToolWitness w : witnesses) {
                latticeGrade = EvidenceGrade.meet(latticeGrade, w != null ? w.grade() : EvidenceGrade.UNVERIFIED);
            }
        }

        String safeReasoning = reasoningContent == null ? "" : reasoningContent.trim();
        String safeAnswer = finalAnswerOrAction == null ? "" : finalAnswerOrAction.trim();
        String safeEvidence = witnessRawEvidenceSummary == null ? "" : witnessRawEvidenceSummary.trim();
        String safeQuestion = question == null ? "" : question.trim();

        float[] rVec = embeddingResolver.resolveUnit1024(safeReasoning.isEmpty() ? safeQuestion : safeReasoning);
        float[] aVec = embeddingResolver.resolveUnit1024(safeAnswer.isEmpty() ? safeQuestion : safeAnswer);
        float[] eVec = !safeEvidence.isEmpty()
                ? embeddingResolver.resolveUnit1024(safeEvidence)
                : embeddingResolver.resolveUnit1024(safeQuestion);

        // 2. 前向忠实度 Fwd(R, E_t): [0, 1]
        double cosRE = cosine(rVec, eVec);
        double lexicalRE = tokenOverlap(safeReasoning.isEmpty() ? safeAnswer : safeReasoning,
                safeEvidence.isEmpty() ? safeQuestion : safeEvidence);
        double fwd = clamp01(0.50 * (0.5 * (1.0 + cosRE)) + 0.50 * lexicalRE
                + (latticeGrade.latticeRank() >= 1 ? 0.12 : 0.0));

        // 3. 反向归因 Bwd(y_t, R U E_t): [0, 1]
        double cosAR = cosine(aVec, rVec);
        double cosAE = cosine(aVec, eVec);
        double lexicalAE = tokenOverlap(safeAnswer, (safeReasoning + " " + safeEvidence).trim());
        double bwd = clamp01(0.35 * (0.5 * (1.0 + cosAR))
                + 0.35 * (0.5 * (1.0 + cosAE))
                + 0.30 * lexicalAE);

        // 4. 反事实因果灵敏度 phi = 1 - S(a, a*)
        double phi = 1.0;
        boolean causalDecoupledBlock = false;
        if (guardEnabled && highRiskAction) {
            String counterfactualTarget = "反事实反转约束：严禁执行该危险动作，先挂起并拒绝执行 " + safeReasoning;
            float[] cfVec = embeddingResolver.resolveUnit1024(counterfactualTarget);
            double simToCounterfactual = 0.5 * (1.0 + cosine(aVec, cfVec));
            phi = clamp01(1.0 - Math.abs(simToCounterfactual - 0.5) * 0.4 - (safeReasoning.isEmpty() ? 0.85 : 0.0));
            if (safeReasoning.isEmpty() || phi < causalPhiThreshold) {
                causalDecoupledBlock = true;
            }
        }
        return new CoreComponents(latticeGrade, safeReasoning, safeAnswer, safeEvidence, fwd, bwd, phi, causalDecoupledBlock);
    }

    /**
     * 若回答包含 `Final Answer:` 标记，仅抽取出最后一段最终答案用于精确形式化核查，防止推理过程中的前提条件数字干扰校验。
     */
    private static String extractFinalAnswerSegment(String safeAnswer) {
        if (safeAnswer == null || safeAnswer.isBlank()) {
            return "";
        }
        String lower = safeAnswer.toLowerCase(Locale.ROOT);
        int markerIdx = lower.lastIndexOf("final answer:");
        if (markerIdx >= 0) {
            String tail = safeAnswer.substring(markerIdx + "final answer:".length()).trim();
            if (!tail.isEmpty()) {
                return tail;
            }
        }
        return safeAnswer;
    }

    private static List<String> extractSignificantTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String tok : text.toLowerCase(Locale.ROOT).split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (tok.length() >= 3 && !DENOM_STOPWORDS.contains(tok)) {
                terms.add(tok);
            }
        }
        return terms;
    }

    private static List<Double> extractNumbers(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Double> list = new ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find()) {
            try {
                list.add(Double.parseDouble(m.group().replace(",", "")));
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    private static boolean isNumericallyClose(double a, double b) {
        double diff = Math.abs(a - b);
        double scale = Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
        return diff <= 1e-3 || (diff / scale) <= 1e-2;
    }

    private static boolean hasPrefixParentToken(String ansLower, String evLower) {
        String[] parts = ansLower.split("\\s+");
        if (parts.length < 2) {
            return false;
        }
        String parentToken = parts[0].replaceAll("[^a-z0-9]", "");
        return parentToken.length() >= 3 && evLower.contains(parentToken + " network")
                || (parentToken.length() >= 3 && evLower.contains("network " + parentToken))
                || (parentToken.length() >= 3 && evLower.contains("operated by " + parentToken));
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

    private static double tokenOverlap(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        java.util.Set<String> tokensA = extractBilingualTokensAndBigrams(a);
        java.util.Set<String> tokensB = extractBilingualTokensAndBigrams(b);
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }
        int hits = 0;
        for (String tok : tokensA) {
            if (tokensB.contains(tok)) {
                hits++;
            }
        }
        return clamp01((double) hits / Math.min(tokensA.size(), tokensB.size()));
    }

    /**
     * 提取中英文混合特征集合：长度 >= 2 的英文/数字词元，以及连续汉字的字符级 Bigram。
     */
    private static java.util.Set<String> extractBilingualTokensAndBigrams(String text) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String[] asciiParts = lower.split("[^a-z0-9]+");
        for (String part : asciiParts) {
            if (part.length() >= 2) {
                set.add(part);
            }
        }
        StringBuilder hanSeq = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                hanSeq.append(ch);
            } else {
                flushHanBigrams(hanSeq, set);
            }
        }
        flushHanBigrams(hanSeq, set);
        return set;
    }

    private static void flushHanBigrams(StringBuilder hanSeq, java.util.Set<String> out) {
        int len = hanSeq.length();
        if (len == 1) {
            out.add(hanSeq.toString());
        } else if (len >= 2) {
            for (int i = 0; i < len - 1; i++) {
                out.add(hanSeq.substring(i, i + 2));
            }
        }
        hanSeq.setLength(0);
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }
}
