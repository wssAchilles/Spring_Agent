package tech.qiantong.qknow.hermes.cognitive.evolution;

import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.hermes.agent.skill.EnterpriseSkillRecord;
import tech.qiantong.qknow.hermes.agent.skill.LifelongSkillBankEngine;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.supernet.DeepSeekReasoningEffort;
import tech.qiantong.qknow.hermes.cognitive.supernet.SupernetTopologyOperator;
import tech.qiantong.qknow.hermes.memory.bitemporal.SleepTimeSpectralDistiller;
import tech.qiantong.qknow.hermes.memory.bitemporal.SpectralDistillationReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 睡眠期离线自演化慢轨统一协调器（Phase 9.4 VPS-Evo，定理 4 闭环总控）。
 * <p>
 * 依次串联：
 * 1. {@link SleepTimeSpectralDistiller} 真实 {@code L_sym} 正交幂迭代谱蒸馏与弱边冷备；
 * 2. {@link CounterfactualSkillRegressionGate} 对待审 {@code PROBATION} 技能执行反事实零回归准入（通过晋升 {@code VERIFIED}，回归隔离）；
 * 3. {@link LifelongSkillBankEngine#consolidateByCoveringRadius} {@code S^1023} 超球面有界合并；
 * 4. {@link RhoSelfPreferenceOptimizer} 更新超网切空间原型与防错节点。
 * </p>
 */
public class SleepTimeEvolutionCoordinator {

    public static final double DEFAULT_COVERING_RADIUS_COSINE = 0.92;
    public static final double DEFAULT_PAC_DELTA = 0.05;

    public record SleepTimeEvolutionReceipt(
            boolean executed,
            int promotedSkillsCount,
            int quarantinedSkillsCount,
            int radiusMergedSkillsCount,
            SpectralDistillationReceipt spectralReceipt,
            RhoEvolutionReceipt rhoReceipt,
            List<CounterfactualSkillRegressionGate.SkillAdmissionReceipt> admissionReceipts,
            long timestampMs
    ) {
        public SleepTimeEvolutionReceipt {
            admissionReceipts = admissionReceipts != null ? List.copyOf(admissionReceipts) : List.of();
        }
    }

    private final SleepTimeSpectralDistiller spectralDistiller;
    private final LifelongSkillBankEngine skillBankEngine;
    private final CounterfactualSkillRegressionGate regressionGate;
    private final RhoSelfPreferenceOptimizer rhoOptimizer;
    private final AstSecurityInspector astSecurityInspector;
    private final VerifiedCodeActSandboxRuntime sandboxRuntime;

    private final AtomicLong totalCycles = new AtomicLong(0L);
    private final AtomicLong totalPromoted = new AtomicLong(0L);
    private final AtomicLong totalQuarantined = new AtomicLong(0L);
    private final AtomicLong totalRadiusMerged = new AtomicLong(0L);
    private final AtomicLong totalScopedCycles = new AtomicLong(0L);
    private final AtomicLong totalScopedPromoted = new AtomicLong(0L);
    private volatile double lastSeegerPacUpperBound = 1.0;

    public SleepTimeEvolutionCoordinator(
            SleepTimeSpectralDistiller spectralDistiller,
            LifelongSkillBankEngine skillBankEngine,
            CounterfactualSkillRegressionGate regressionGate,
            RhoSelfPreferenceOptimizer rhoOptimizer,
            AstSecurityInspector astSecurityInspector,
            VerifiedCodeActSandboxRuntime sandboxRuntime
    ) {
        this.spectralDistiller = Objects.requireNonNull(spectralDistiller, "spectralDistiller 不能为空");
        this.skillBankEngine = Objects.requireNonNull(skillBankEngine, "skillBankEngine 不能为空");
        this.regressionGate = regressionGate != null ? regressionGate : new CounterfactualSkillRegressionGate();
        this.rhoOptimizer = rhoOptimizer;
        this.astSecurityInspector = astSecurityInspector != null ? astSecurityInspector : new AstSecurityInspector();
        this.sandboxRuntime = sandboxRuntime;
    }

    /**
     * 执行一轮完整的睡眠期离线自演化与技能验证巩固闭环。
     */
    public SleepTimeEvolutionReceipt runSleepCycle(
            List<CounterfactualSkillRegressionGate.AnchorTestCase> counterfactualAnchors,
            List<SelfPreferencePair> extraPreferencePairs,
            double maxSpectralPruneRatio,
            HnscdsAblationConfig ablationConfig
    ) {
        HnscdsAblationConfig cfg = ablationConfig != null ? ablationConfig : HnscdsAblationConfig.defaults();
        long nowMs = System.currentTimeMillis();
        if (!cfg.sleepTimeRhoEnabled() && !cfg.verifiedProgrammaticSkillEvolutionEnabled()) {
            return new SleepTimeEvolutionReceipt(false, 0, 0, 0, null, null, List.of(), nowMs);
        }

        totalCycles.incrementAndGet();
        SpectralDistillationReceipt spectralReceipt = cfg.sleepTimeRhoEnabled()
                ? spectralDistiller.distill(maxSpectralPruneRatio, nowMs)
                : null;

        int promoted = 0;
        int quarantined = 0;
        int merged = 0;
        List<CounterfactualSkillRegressionGate.SkillAdmissionReceipt> admissions = new ArrayList<>();
        List<SelfPreferencePair> generatedPairs = new ArrayList<>();
        if (extraPreferencePairs != null) {
            generatedPairs.addAll(extraPreferencePairs);
        }

        if (cfg.verifiedProgrammaticSkillEvolutionEnabled()) {
            List<EnterpriseSkillRecord> snapshot = new ArrayList<>(skillBankEngine.getActiveSkillsSnapshot().values());
            for (EnterpriseSkillRecord skill : snapshot) {
                if (skill.lifecycleState() == EnterpriseSkillRecord.SkillLifecycleState.PROBATION
                        && skill.skillKind() == EnterpriseSkillRecord.SkillKind.CODEACT_PYTHON_TEMPLATE) {
                    var adm = regressionGate.evaluateAdmission(
                            skill, counterfactualAnchors, DEFAULT_PAC_DELTA, astSecurityInspector, sandboxRuntime
                    );
                    admissions.add(adm);
                    if (adm.admitted()) {
                        if (skillBankEngine.promoteProbationSkill(skill.skillId())) {
                            promoted++;
                            totalPromoted.incrementAndGet();
                        }
                    } else {
                        if (skillBankEngine.quarantineSkill(skill.skillId(), adm.rejectionReason())) {
                            quarantined++;
                            totalQuarantined.incrementAndGet();
                        }
                        generatedPairs.add(new SelfPreferencePair(
                                "sleep-" + skill.skillId(),
                                skill.description(),
                                SupernetTopologyOperator.PLAN_SOLVE,
                                DeepSeekReasoningEffort.HIGH,
                                0.85,
                                adm.rejectionReason(),
                                0.10
                        ));
                    }
                }
            }
            merged = skillBankEngine.consolidateByCoveringRadius(DEFAULT_COVERING_RADIUS_COSINE);
            totalRadiusMerged.addAndGet(merged);
        }

        RhoEvolutionReceipt rhoReceipt = (rhoOptimizer != null && !generatedPairs.isEmpty())
                ? rhoOptimizer.evolveFromPreferencePairs(generatedPairs, 0.12)
                : new RhoEvolutionReceipt(0, 0, 0, 0.0, nowMs);

        return new SleepTimeEvolutionReceipt(
                true, promoted, quarantined, merged, spectralReceipt, rhoReceipt, admissions, nowMs
        );
    }

    /**
     * Phase 10.4 P-VPS-Evo 作用域闭环睡眠演化：
     * 对 {@link SynthesizedSkillCandidate} 与待审技能执行同 MetricType 且 {@code cos >= scopeCosineThreshold}
     * 的条件作用域反事实评估，结合 Seeger/Maurer PAC-Bayes 零错紧界 {@code < 1.0} 晋升 {@code VERIFIED}，
     * 对回归或失配候选仅做软隔离进冷备仓。
     */
    public SleepTimeEvolutionReceipt runScopedSleepCycle(
            List<SynthesizedSkillCandidate> synthesizedCandidates,
            List<CounterfactualSkillRegressionGate.ScopedAnchorTestCase> allAnchors,
            double scopeCosineThreshold,
            double deltaConfidence,
            double maxSpectralPruneRatio,
            HnscdsAblationConfig ablationConfig
    ) {
        HnscdsAblationConfig cfg = ablationConfig != null ? ablationConfig : HnscdsAblationConfig.defaults();
        long nowMs = System.currentTimeMillis();
        if (!cfg.sleepTimeRhoEnabled()
                && !cfg.verifiedProgrammaticSkillEvolutionEnabled()
                && !cfg.parameterizedScopedSkillEvoEnabled()) {
            return new SleepTimeEvolutionReceipt(false, 0, 0, 0, null, null, List.of(), nowMs);
        }

        totalCycles.incrementAndGet();
        totalScopedCycles.incrementAndGet();
        SpectralDistillationReceipt spectralReceipt = cfg.sleepTimeRhoEnabled()
                ? spectralDistiller.distill(maxSpectralPruneRatio, nowMs)
                : null;

        int promoted = 0;
        int quarantined = 0;
        int merged = 0;
        List<CounterfactualSkillRegressionGate.SkillAdmissionReceipt> admissions = new ArrayList<>();
        List<SelfPreferencePair> generatedPairs = new ArrayList<>();

        List<SynthesizedSkillCandidate> safeCandidates = synthesizedCandidates != null ? synthesizedCandidates : List.of();
        for (SynthesizedSkillCandidate cand : safeCandidates) {
            if (cand == null) {
                continue;
            }
            var scopedReceipt = regressionGate.evaluateScopedAdmission(
                    cand, allAnchors, scopeCosineThreshold, deltaConfidence, astSecurityInspector, sandboxRuntime
            );
            admissions.add(scopedReceipt.toLegacyReceipt());
            if (scopedReceipt.admitted()) {
                lastSeegerPacUpperBound = scopedReceipt.seegerPacUpperBound();
                // 确保该候选已登记至技能库后立即晋升 VERIFIED
                if (skillBankEngine.getActiveSkillsSnapshot().containsKey(cand.skillId())) {
                    if (skillBankEngine.promoteProbationSkill(cand.skillId())) {
                        promoted++;
                        totalPromoted.incrementAndGet();
                        totalScopedPromoted.incrementAndGet();
                    }
                } else {
                    float[] vec = cand.scopedAnchors().isEmpty()
                            ? new float[1024]
                            : cand.scopedAnchors().get(0).queryUnit1024();
                    EnterpriseSkillRecord rec = EnterpriseSkillRecord.ofCodeActTemplate(
                            cand.skillId(),
                            cand.name(),
                            cand.description(),
                            cand.parameterSlots(),
                            cand.codeActPythonTemplate(),
                            cand.unitTestAssertions(),
                            EnterpriseSkillRecord.SkillLifecycleState.VERIFIED,
                            vec
                    );
                    skillBankEngine.registerSkill(rec);
                    promoted++;
                    totalPromoted.incrementAndGet();
                    totalScopedPromoted.incrementAndGet();
                }
            } else {
                if (skillBankEngine.quarantineSkill(cand.skillId(), scopedReceipt.rejectionReason())) {
                    quarantined++;
                    totalQuarantined.incrementAndGet();
                } else {
                    quarantined++;
                }
                generatedPairs.add(new SelfPreferencePair(
                        "scoped-sleep-" + cand.skillId(),
                        cand.description(),
                        SupernetTopologyOperator.PLAN_SOLVE,
                        DeepSeekReasoningEffort.HIGH,
                        0.85,
                        scopedReceipt.rejectionReason(),
                        0.10
                ));
            }
        }

        if (cfg.verifiedProgrammaticSkillEvolutionEnabled() || cfg.parameterizedScopedSkillEvoEnabled()) {
            merged = skillBankEngine.consolidateByCoveringRadius(DEFAULT_COVERING_RADIUS_COSINE);
            totalRadiusMerged.addAndGet(merged);
        }

        RhoEvolutionReceipt rhoReceipt = (rhoOptimizer != null && !generatedPairs.isEmpty())
                ? rhoOptimizer.evolveFromPreferencePairs(generatedPairs, 0.12)
                : new RhoEvolutionReceipt(0, 0, 0, 0.0, nowMs);

        return new SleepTimeEvolutionReceipt(
                true, promoted, quarantined, merged, spectralReceipt, rhoReceipt, admissions, nowMs
        );
    }

    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalSleepCycles", totalCycles.get());
        map.put("totalPromotedSkills", totalPromoted.get());
        map.put("totalQuarantinedSkills", totalQuarantined.get());
        map.put("totalRadiusMergedSkills", totalRadiusMerged.get());
        map.put("totalScopedSleepCycles", totalScopedCycles.get());
        map.put("totalScopedPromotedSkills", totalScopedPromoted.get());
        map.put("lastSeegerPacUpperBound", lastSeegerPacUpperBound);
        return Collections.unmodifiableMap(map);
    }
}
