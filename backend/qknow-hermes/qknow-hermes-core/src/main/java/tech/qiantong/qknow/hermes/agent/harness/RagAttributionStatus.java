package tech.qiantong.qknow.hermes.agent.harness;

public enum RagAttributionStatus {
    NO_RAG,
    UNUSED,
    PARTIALLY_USED,
    FULLY_USED;

    public static RagAttributionStatus resolve(int ragProvided, int ragUsed) {
        if (ragProvided <= 0) {
            return NO_RAG;
        }
        if (ragUsed <= 0) {
            return UNUSED;
        }
        return ragUsed >= ragProvided ? FULLY_USED : PARTIALLY_USED;
    }
}
