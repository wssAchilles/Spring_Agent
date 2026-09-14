package tech.qiantong.qknow.ai.audit.causal;

/**
 * 因果拓扑图节点类型枚举
 *
 * @author qknow
 */
public enum CausalNodeType {
    /** 用户原始或脱敏查询 */
    QUERY,
    /** 意图分解与子任务规划 */
    INTENT_DECOMPOSITION,
    /** 检索召回且采纳的知识切片 */
    KNOWLEDGE_RETAINED,
    /** 知识图谱多跳子图因果路径 */
    SUBGRAPH_PATHS,
    /** 多智能体拜占庭共识裁决 */
    BFT_CONSENSUS,
    /** 模型最终生成的合规响应 */
    FINAL_OUTPUT
}
