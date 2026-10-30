package tech.qiantong.qknow.hermes.agent.harness;

public record RagAttributionSummary(
        RagAttributionStatus status,
        int ragProvided,
        int ragUsed,
        double ragScore,
        double ragUnderutilization
) {
    public RagAttributionSummary {
        ragProvided = Math.max(0, ragProvided);
        ragUsed = Math.max(0, Math.min(ragProvided, ragUsed));
        status = RagAttributionStatus.resolve(ragProvided, ragUsed);
        ragScore = Double.isFinite(ragScore) ? Math.max(0.0, ragScore) : 0.0;
        ragUnderutilization = Double.isFinite(ragUnderutilization)
                ? Math.max(0.0, Math.min(1.0, ragUnderutilization)) : 0.0;
    }

    public static RagAttributionSummary noRag() {
        return new RagAttributionSummary(RagAttributionStatus.NO_RAG, 0, 0, 0.0, 0.0);
    }

    public static RagAttributionSummary of(int ragProvided, int ragUsed, double ragScore) {
        if (ragProvided <= 0) {
            return noRag();
        }
        int clampedUsed = Math.max(0, Math.min(ragProvided, ragUsed));
        return new RagAttributionSummary(RagAttributionStatus.resolve(ragProvided, clampedUsed),
                ragProvided, clampedUsed, ragScore, 1.0 - (double) clampedUsed / ragProvided);
    }

    public String canonical() {
        return status.name() + ":" + ragProvided + ":" + ragUsed + ":" + ragScore + ":" + ragUnderutilization;
    }
}
