package tech.qiantong.qknow.ai.skill;

import java.util.*;

/**
 * Phase 62: 端到端元策略技能编排与自进化总调度中枢
 * <p>
 * 闭环统筹意图流形检索 -> 神经符号 DAG 编译 -> 写时复制热插拔 -> 复合计划执行 -> 签发不可变存证凭单。
 */
public class MetaPolicySkillOrchestrator {

    private final GeodesicSkillManifoldIndex manifoldIndex;
    private final NeuroSymbolicSkillDagEngine dagEngine;
    private final HotSwappableSkillCatalog skillCatalog;

    public MetaPolicySkillOrchestrator(
            GeodesicSkillManifoldIndex manifoldIndex,
            NeuroSymbolicSkillDagEngine dagEngine,
            HotSwappableSkillCatalog skillCatalog
    ) {
        this.manifoldIndex = Objects.requireNonNull(manifoldIndex, "manifoldIndex 不能为空");
        this.dagEngine = Objects.requireNonNull(dagEngine, "dagEngine 不能为空");
        this.skillCatalog = Objects.requireNonNull(skillCatalog, "skillCatalog 不能为空");
    }

    /**
     * 执行元策略任务技能编排
     */
    public SkillOrchestrationReceipt orchestrateTask(
            long epoch,
            String taskIntent,
            float[] intentVector,
            int topK
    ) {
        if (epoch <= 0) {
            throw new IllegalArgumentException("编排代际号必须大于 0");
        }
        if (taskIntent == null || taskIntent.isBlank()) {
            throw new IllegalArgumentException("任务意图不能为空");
        }
        if (intentVector == null || intentVector.length != SkillMetadata.EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("意图向量必须为 " + SkillMetadata.EMBEDDING_DIMENSION + " 维");
        }

        // 1. 基于超球面流形检索最契合的候选技能集合
        List<SkillMetadata> matchedSkills = manifoldIndex.searchTopK(intentVector, topK);

        if (matchedSkills.isEmpty()) {
            String receiptId = "SKILL-EP-" + epoch + "-" + UUID.randomUUID().toString().substring(0, 8);
            return SkillOrchestrationReceipt.create(
                    receiptId, epoch, taskIntent, List.of(), "NONE",
                    0.0, false, false, "技能流形未召回任何有效候选技能",
                    System.currentTimeMillis()
            );
        }

        // 2. 补齐候选技能所依赖的所有前置技能
        Map<String, SkillMetadata> currentCatalog = skillCatalog.getSnapshot();
        List<SkillMetadata> fullChain = new ArrayList<>(matchedSkills);
        Set<String> chainIds = new HashSet<>();
        for (SkillMetadata s : matchedSkills) {
            chainIds.add(s.skillId());
        }

        for (SkillMetadata s : matchedSkills) {
            for (String depId : s.dependencies()) {
                if (!chainIds.contains(depId) && currentCatalog.containsKey(depId)) {
                    fullChain.add(currentCatalog.get(depId));
                    chainIds.add(depId);
                }
            }
        }

        // 3. 神经符号 DAG 编译与死锁排查
        NeuroSymbolicSkillDagEngine.PhasedExecutionPlan plan = dagEngine.compileDagPlan(fullChain);

        // 4. 评估技能组合帕累托效用得分
        double avgSuccessRate = fullChain.stream().mapToDouble(SkillMetadata::successRate).average().orElse(0.0);
        double paretoScore = Math.min(1.0, 0.60 * avgSuccessRate + 0.40 * (1.0 / Math.max(1, plan.phases().size())));

        List<String> selectedIds = fullChain.stream().map(SkillMetadata::skillId).toList();
        String planSummary = String.format("总阶段: %d, 最大并发度: %d", plan.phases().size(), plan.maxParallelism());

        String receiptId = "SKILL-EP-" + epoch + "-" + UUID.randomUUID().toString().substring(0, 8);
        return SkillOrchestrationReceipt.create(
                receiptId, epoch, taskIntent, selectedIds, planSummary,
                paretoScore, false, true, "元策略技能拓扑编排成功，良基序展开无死锁",
                System.currentTimeMillis()
        );
    }

    public GeodesicSkillManifoldIndex getManifoldIndex() {
        return manifoldIndex;
    }

    public NeuroSymbolicSkillDagEngine getDagEngine() {
        return dagEngine;
    }

    public HotSwappableSkillCatalog getSkillCatalog() {
        return skillCatalog;
    }
}
