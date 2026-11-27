package tech.qiantong.qknow.hermes.transaction.frontier;

/**
 * 工具副作用三级安全全序格（定义 6.1：Three-Tier Side-Effect Lattice）。
 * <p>
 * 全序偏序关系：{@code BUFFERABLE < EXTERNALIZED_REVERSIBLE < IRREVERSIBLE}。
 * </p>
 */
public enum ToolSideEffectTier {
    /** 可缓冲级：只读查询或分支私有沙盒更新，提交前对外部环境零可见 */
    BUFFERABLE,
    /** 外部化可逆级：与外部服务交互但绑定经过验证的幂等逆向补偿算子 c^{-1} */
    EXTERNALIZED_REVERSIBLE,
    /** 物理不可逆级：一旦触发不可撤回，投机分支绝对禁止预执行，且必须满足前沿就绪与 HITL 审批 */
    IRREVERSIBLE
}
