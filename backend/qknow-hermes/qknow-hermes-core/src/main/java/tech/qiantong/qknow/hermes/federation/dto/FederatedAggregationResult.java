package tech.qiantong.qknow.hermes.federation.dto;

/**
 * 跨域知识联邦特征聚合结果 Record
 */
public record FederatedAggregationResult(
        String federatedSessionId,
        double[] aggregatedEmbedding,
        double theoreticalFidelity,
        int participantDomainsCount,
        double totalNoiseVariance,
        long durationNanos,
        boolean success
) {}
