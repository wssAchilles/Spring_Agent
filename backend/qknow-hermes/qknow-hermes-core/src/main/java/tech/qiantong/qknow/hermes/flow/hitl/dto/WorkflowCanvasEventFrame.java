package tech.qiantong.qknow.hermes.flow.hitl.dto;

/**
 * 1000Hz 工作流画布流式事件帧 Record
 * 封装节点状态跃迁、进度百分比、日志片段与超球面向量
 */
public record WorkflowCanvasEventFrame(
        String eventId,
        String workflowId,
        String branchId,
        String nodeUuid,
        CanvasNodeState nodeState,
        int progressPercentage,
        String logChunk,
        double[] sphericalEmbedding,
        long epochMicros,
        long sequenceNumber
) {
    public enum CanvasNodeState {
        PENDING,
        RUNNING,
        SUSPENDED_WAITING_APPROVAL,
        COMPLETED,
        FAILED,
        PRUNED
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
