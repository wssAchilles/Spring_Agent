package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.Objects;

/** A bounded, replayable edit to a strategy's reflection rules. */
public record StrategyInsightEdit(Operation operation, String target, String insight) {
    public enum Operation { ADD, EDIT, AGREE, REMOVE }

    public StrategyInsightEdit {
        Objects.requireNonNull(operation, "operation");
        target = normalize(target);
        insight = normalize(insight);
        if ((operation == Operation.ADD || operation == Operation.AGREE) && insight.isBlank()) {
            throw new IllegalArgumentException("ADD and AGREE require an insight");
        }
        if ((operation == Operation.EDIT || operation == Operation.REMOVE) && target.isBlank()) {
            throw new IllegalArgumentException("EDIT and REMOVE require a target");
        }
        if (operation == Operation.EDIT && insight.isBlank()) {
            throw new IllegalArgumentException("EDIT requires a replacement insight");
        }
        if (target.length() > 1000 || insight.length() > 1000) {
            throw new IllegalArgumentException("strategy insight is too long");
        }
    }

    public static StrategyInsightEdit add(String insight) {
        return new StrategyInsightEdit(Operation.ADD, "", insight);
    }

    public static StrategyInsightEdit edit(String target, String insight) {
        return new StrategyInsightEdit(Operation.EDIT, target, insight);
    }

    public static StrategyInsightEdit agree(String insight) {
        return new StrategyInsightEdit(Operation.AGREE, insight, insight);
    }

    public static StrategyInsightEdit remove(String target) {
        return new StrategyInsightEdit(Operation.REMOVE, target, "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
