package tech.qiantong.qknow.hermes.agent.harness;

/**
 * 短期/长期记忆与反思策略契约。
 */
public record MemoryContextStrategyRecord(
        int shortTermWindowSize,
        int longTermRecallTopK,
        int reflexionDepth
) {
    public MemoryContextStrategyRecord {
        if (shortTermWindowSize <= 0) {
            throw new IllegalArgumentException("shortTermWindowSize 必须大于 0");
        }
        if (longTermRecallTopK <= 0) {
            throw new IllegalArgumentException("longTermRecallTopK 必须大于 0");
        }
        if (reflexionDepth < 0) {
            throw new IllegalArgumentException("reflexionDepth 不能为负数");
        }
    }

    public String canonical() {
        return shortTermWindowSize + "\u001f" + longTermRecallTopK + "\u001f" + reflexionDepth;
    }

    public static MemoryContextStrategyRecord defaults() {
        return new MemoryContextStrategyRecord(20, 5, 1);
    }
}
