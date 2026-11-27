package tech.qiantong.qknow.hermes.cognitive.control;

/**
 * 认知推理步级阶段分类枚举（Phase 9.1 ZCEA-SCR，定理 2）。
 * <p>
 * 用于区分同一问题内部不同执行节点的认知难度先验与是否允许采用零思考（{@code NONE}）。
 * </p>
 */
public enum CognitiveStepPhase {

    /** 图谱任务规划与子问题拆解 */
    DECOMPOSE(0.45, false),
    /** 叶子实体检索与片段扫描（低认知开销阶段） */
    EXPLORE_RETRIEVE(0.15, true),
    /** 结构化情景-关系抽取 */
    RELATIONAL_EXTRACT(0.25, true),
    /** CodeAct 脚本编排与关系代数求解 */
    CODEACT_PROGRAM(0.50, false),
    /** 多源证据汇合与终态规格核验 */
    VERIFY_SYNTHESIZE(0.65, false),
    /** 反事实分支反思与定点纠偏 */
    COUNTERFACTUAL_REPAIR(0.85, false),
    /** 纯格式化与只读字段抽取汇总 */
    FORMAT_FINAL(0.10, true);

    private final double phasePrior;
    private final boolean noneEffortEligible;

    CognitiveStepPhase(double phasePrior, boolean noneEffortEligible) {
        this.phasePrior = phasePrior;
        this.noneEffortEligible = noneEffortEligible;
    }

    public double phasePrior() {
        return phasePrior;
    }

    public boolean noneEffortEligible() {
        return noneEffortEligible;
    }
}
