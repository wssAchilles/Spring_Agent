package tech.qiantong.qknow.hermes.agent.debate.tripartite;

/**
 * 三方博弈单轮发言记录
 * 严格基于 Java 21 Record 构建
 */
public record TripartiteDebateTurnRecord(
        int roundNumber,
        TripartiteDebateRole role,
        String argumentClaim,
        String counterFactualCritique,
        double confidenceScore,
        String qwenEmbeddingDigest
) {}
