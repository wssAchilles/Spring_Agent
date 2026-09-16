package tech.qiantong.qknow.module.kmc.service.rag.evolution.dto;

/**
 * 1000Hz 图谱演化流式事件单帧
 */
public record KnowledgeEvolutionEventFrame(
        String sessionId,
        EvolutionEventType eventType,
        String affectedEntityId,
        String payloadSummary,
        double[] sphericalEmbedding,
        long sequenceNumber,
        long timestamp
) {
    public enum EvolutionEventType {
        ENTITY_ALIGNED,
        FACT_SUPERSEDED,
        ONTOLOGY_CONTRACTED,
        DISPUTE_RAISED,
        FROZEN_HOLD
    }

    public boolean isValidEmbedding() {
        if (sphericalEmbedding == null || sphericalEmbedding.length != 1536) {
            return false;
        }
        double sumSq = 0.0;
        for (double v : sphericalEmbedding) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= 1e-4;
    }
}
