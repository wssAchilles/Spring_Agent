package tech.qiantong.qknow.ai.consensus.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Worker 智能体产出的原始提案实体
 */
public record WorkerProposal(
        String workerId,
        String taskId,
        String rawContent,
        Map<String, Object> structuredData,
        List<String> citations,
        double selfConfidence,
        long latencyMs,
        Instant timestamp
) {
    public static WorkerProposal of(String workerId, String taskId, String rawContent) {
        return new WorkerProposal(
                workerId,
                taskId,
                rawContent,
                Collections.emptyMap(),
                Collections.emptyList(),
                0.90,
                100L,
                Instant.now()
        );
    }
}
