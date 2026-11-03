package tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel;

public record PpcStopPolicy(int maxRounds, int maxProbes) {
    public PpcStopPolicy {
        if (maxRounds < 1 || maxRounds > 2 || maxProbes < 1 || maxProbes > 5) {
            throw new IllegalArgumentException("bounded PPC allows at most two rounds and five probes per task");
        }
    }

    public static PpcStopPolicy twoRounds() {
        return new PpcStopPolicy(2, 4);
    }

    public static PpcStopPolicy withFourCandidates() {
        return new PpcStopPolicy(2, 5);
    }

    public boolean canProbe(int completed, int nextBatch) {
        return completed >= 0 && nextBatch >= 0 && completed + nextBatch <= maxProbes;
    }
}
