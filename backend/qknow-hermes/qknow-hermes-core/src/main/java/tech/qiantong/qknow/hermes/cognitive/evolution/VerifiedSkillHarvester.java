package tech.qiantong.qknow.hermes.cognitive.evolution;

import tech.qiantong.qknow.hermes.agent.skill.EnterpriseSkillRecord;
import tech.qiantong.qknow.hermes.agent.skill.LifelongSkillBankEngine;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpec;
import tech.qiantong.qknow.hermes.cognitive.verification.RequirementSpecAuditResult;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 双客观见证驱动的程序技能收割器（Phase 9.4 VPS-Evo）。
 * <p>
 * 强制双门禁准入：仅当轨迹同时满足
 * 1. {@link VerifiedCodeActReceipt#isSuccess()} 为 {@code true} 且未篡改；
 * 2. {@link RequirementSpecAuditResult#passed()} 为 {@code true} 且无硬规格违约；
 * 方才将其抽象为 {@link EnterpriseSkillRecord.SkillLifecycleState#PROBATION} 待审程序技能注入技能库，
 * 彻底杜绝“错题/分母出错轨迹污染技能库”。
 * </p>
 */
public class VerifiedSkillHarvester {

    private final LifelongSkillBankEngine skillBankEngine;
    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final List<SynthesizedSkillCandidate> pendingScopedCandidates = new CopyOnWriteArrayList<>();
    private final AtomicLong totalHarvestAttempts = new AtomicLong(0L);
    private final AtomicLong totalHarvested = new AtomicLong(0L);
    private final AtomicLong totalRejected = new AtomicLong(0L);
    private final AtomicLong totalSynthesizedSkills = new AtomicLong(0L);

    public VerifiedSkillHarvester(
            LifelongSkillBankEngine skillBankEngine,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        this.skillBankEngine = Objects.requireNonNull(skillBankEngine, "LifelongSkillBankEngine 不能为空");
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
    }

    /**
     * 基于双客观见证尝试收割成功程序技能为 {@code PROBATION} 状态；任一见证失效即拒收。
     */
    public Optional<EnterpriseSkillRecord> harvestIfVerified(
            String skillName,
            String taskDescription,
            Map<String, String> parameterSlots,
            String parameterizedPythonTemplate,
            List<String> unitTestAssertions,
            VerifiedCodeActReceipt codeActReceipt,
            RequirementSpecAuditResult specAuditResult
    ) {
        totalHarvestAttempts.incrementAndGet();
        if (codeActReceipt == null || !codeActReceipt.isSuccess()) {
            totalRejected.incrementAndGet();
            return Optional.empty();
        }
        if (specAuditResult == null || !specAuditResult.passed() || specAuditResult.hasHardViolations()) {
            totalRejected.incrementAndGet();
            return Optional.empty();
        }
        if (parameterizedPythonTemplate == null || parameterizedPythonTemplate.isBlank()) {
            totalRejected.incrementAndGet();
            return Optional.empty();
        }

        String skillId = "cskill_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        float[] vec = embeddingResolver.resolveUnit1024(taskDescription != null ? taskDescription : skillName);
        EnterpriseSkillRecord record = EnterpriseSkillRecord.ofCodeActTemplate(
                skillId,
                skillName != null && !skillName.isBlank() ? skillName : skillId,
                taskDescription != null ? taskDescription : "verified codeact procedure",
                parameterSlots,
                parameterizedPythonTemplate,
                unitTestAssertions,
                EnterpriseSkillRecord.SkillLifecycleState.PROBATION,
                vec
        );
        skillBankEngine.registerSkill(record);
        totalHarvested.incrementAndGet();
        return Optional.of(record);
    }

    /**
     * Phase 10.4 P-VPS-Evo：登记由 {@link ProgrammaticSkillSynthesizer} 自合成的参数化技能候选，
     * 同时写入 {@code PROBATION} 态入库并缓存完整作用域锚点供睡眠期 {@code runScopedSleepCycle} 使用。
     */
    public Optional<EnterpriseSkillRecord> harvestSynthesizedCandidate(
            SynthesizedSkillCandidate candidate,
            float[] queryVec1024
    ) {
        totalHarvestAttempts.incrementAndGet();
        if (candidate == null || candidate.codeActPythonTemplate().isBlank() || candidate.unitTestAssertions().isEmpty()) {
            totalRejected.incrementAndGet();
            return Optional.empty();
        }
        float[] vec = (queryVec1024 != null && queryVec1024.length == 1024)
                ? queryVec1024
                : embeddingResolver.resolveUnit1024(candidate.description());
        EnterpriseSkillRecord record = EnterpriseSkillRecord.ofCodeActTemplate(
                candidate.skillId(),
                candidate.name(),
                candidate.description(),
                candidate.parameterSlots(),
                candidate.codeActPythonTemplate(),
                candidate.unitTestAssertions(),
                EnterpriseSkillRecord.SkillLifecycleState.PROBATION,
                vec
        );
        skillBankEngine.registerSkill(record);
        pendingScopedCandidates.add(candidate);
        totalHarvested.incrementAndGet();
        totalSynthesizedSkills.incrementAndGet();
        return Optional.of(record);
    }

    public List<SynthesizedSkillCandidate> drainPendingScopedCandidates() {
        List<SynthesizedSkillCandidate> copy = new ArrayList<>(pendingScopedCandidates);
        pendingScopedCandidates.clear();
        return copy;
    }

    public Map<String, Object> getTelemetryStats() {
        return Map.of(
                "totalHarvestAttempts", totalHarvestAttempts.get(),
                "totalHarvested", totalHarvested.get(),
                "totalRejected", totalRejected.get(),
                "totalSynthesizedSkills", totalSynthesizedSkills.get(),
                "pendingScopedCandidatesCount", pendingScopedCandidates.size()
        );
    }
}
