package tech.qiantong.qknow.hermes.agent.governor;

import tech.qiantong.qknow.hermes.agent.BaseAgent;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 阿里千问 1536 维超球面动态角色涌现与修剪治理器接口 (SphericalDyLANRoleGovernor)
 * 遵循 ICLR 2024 DyLAN 范式与超球面余弦度量定理，消除多轮复读与角色冗余。
 */
public interface SphericalDyLANRoleGovernor {

    /**
     * 动态角色调度决策产物 (纯 Java 21 Record)
     */
    record DynamicRoleDecision(
            List<String> activeAgentIds,
            List<String> prunedAgentIds,
            boolean hasEmergedNewRole,
            String emergedRolePrompt,
            Map<String, Double> agentImportanceScores
    ) implements Serializable {
        public DynamicRoleDecision {
            activeAgentIds = List.copyOf(activeAgentIds != null ? activeAgentIds : List.of());
            prunedAgentIds = List.copyOf(prunedAgentIds != null ? prunedAgentIds : List.of());
            agentImportanceScores = Map.copyOf(agentImportanceScores != null ? agentImportanceScores : Map.of());
            emergedRolePrompt = emergedRolePrompt != null ? emergedRolePrompt : "";
        }
    }

    /**
     * 评估并治理当前轮次的智能体角色拓扑流变
     *
     * @param currentTaskEmbedding 当前演进任务的千问 1536 维单位向量 (L2 = 1.0)
     * @param candidates 当前候选 Worker 集合
     * @param historicalContributions 历史轮次信息增益度
     * @param pruneThreshold 修剪阈值 (tau_prune，如 0.35)
     * @param emergeThreshold 涌现阈值 (tau_emerge，如 0.50)
     * @return 动态角色调度决策
     */
    DynamicRoleDecision governRoles(
            float[] currentTaskEmbedding,
            List<? extends BaseAgent> candidates,
            Map<String, Double> historicalContributions,
            double pruneThreshold,
            double emergeThreshold
    );
}
