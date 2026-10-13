package tech.qiantong.qknow.hermes.agent.planner.mcts;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * MCTS 认知树搜索节点不可变凭单 (纯 Java 21 Record)
 * 记录单步推演思考、拟调用工具、前瞻价值与阿里千问 1536 维超球面单位向量。
 */
public record MctsSearchNodeRecord(
        String nodeId,
        String parentNodeId,
        int depth,
        String actionThought,
        String plannedToolName,
        Map<String, Object> toolArguments,
        String observationPayload,
        int visitCount,
        double accumulatedValue,
        double ucb1Score,
        float[] hypersphericalEmbedding, // 阿里千问 1536 维超球面单位向量 (L2 = 1.0)
        boolean isPruned,
        boolean isTerminal,
        String stateChecksumSha256,
        long createdAtTimestampMs
) implements Serializable {

    public MctsSearchNodeRecord {
        Objects.requireNonNull(nodeId, "nodeId 不可为空");
        Objects.requireNonNull(stateChecksumSha256, "stateChecksumSha256 不可为空");
        toolArguments = toolArguments != null ? Map.copyOf(toolArguments) : Map.of();
        actionThought = actionThought != null ? actionThought : "";
        observationPayload = observationPayload != null ? observationPayload : "";
        if (hypersphericalEmbedding != null && hypersphericalEmbedding.length != 1536) {
            throw new IllegalArgumentException("千问超球面向量维度必须严格为 1536 维，当前为: " + hypersphericalEmbedding.length);
        }
        hypersphericalEmbedding = hypersphericalEmbedding != null ? hypersphericalEmbedding.clone() : null;
    }

    public float[] hypersphericalEmbedding() {
        return hypersphericalEmbedding != null ? hypersphericalEmbedding.clone() : null;
    }

    /**
     * 计算并派生更新访问次数与累积价值的新不可变节点
     */
    public MctsSearchNodeRecord withUpdatedValue(double reward, double newUcb1) {
        return new MctsSearchNodeRecord(
                nodeId, parentNodeId, depth, actionThought, plannedToolName,
                toolArguments, observationPayload, visitCount + 1,
                accumulatedValue + reward, newUcb1, hypersphericalEmbedding,
                isPruned, isTerminal, stateChecksumSha256, createdAtTimestampMs
        );
    }

    /**
     * 标记剪枝的新不可变节点
     */
    public MctsSearchNodeRecord withPruned(boolean pruned) {
        return new MctsSearchNodeRecord(
                nodeId, parentNodeId, depth, actionThought, plannedToolName,
                toolArguments, observationPayload, visitCount,
                accumulatedValue, ucb1Score, hypersphericalEmbedding,
                pruned, isTerminal, stateChecksumSha256, createdAtTimestampMs
        );
    }
}
