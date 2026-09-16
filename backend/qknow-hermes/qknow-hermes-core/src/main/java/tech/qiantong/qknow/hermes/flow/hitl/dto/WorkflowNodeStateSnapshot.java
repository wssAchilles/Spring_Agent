package tech.qiantong.qknow.hermes.flow.hitl.dto;

import java.util.Collections;
import java.util.Map;

/**
 * 节点级状态微分快照 Record
 * 捕获工作流执行过程中每个节点的增量变量环境、哈希签名与千问 1536 维超球面嵌入
 */
public record WorkflowNodeStateSnapshot(
        String snapshotId,
        String workflowId,
        String branchId,
        String nodeUuid,
        String nodeName,
        String parentSnapshotId,
        Map<String, Object> deltaVariables,
        String inputHash,
        String outputHash,
        double[] sphericalEmbedding,
        long epochMicros
) {
    public WorkflowNodeStateSnapshot {
        deltaVariables = deltaVariables != null ? Collections.unmodifiableMap(deltaVariables) : Collections.emptyMap();
    }

    /**
     * 严格校验阿里千问 1536 维超球面单位向量模长：||v||_2 = 1.0 ± 1e-4
     */
    public boolean isValidEmbedding() {
        if (sphericalEmbedding == null || sphericalEmbedding.length != 1536) {
            return false;
        }
        double normSq = 0.0;
        for (double v : sphericalEmbedding) {
            normSq += v * v;
        }
        return Math.abs(Math.sqrt(normSq) - 1.0) <= 1e-4;
    }
}
