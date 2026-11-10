package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.ArrayList;
import java.util.List;

/** Applies deterministic insight edits while keeping strategy memory bounded. */
public final class StrategyInsightConsolidator {
    private static final int MAX_REFLECTION_RULES = 12;

    private StrategyInsightConsolidator() {
    }

    public static StrategyTemplate.Content apply(StrategyTemplate.Content base,
                                                 List<StrategyInsightEdit> edits) {
        if (base == null || edits == null || edits.isEmpty()) {
            return base;
        }
        List<String> rules = new ArrayList<>(base.reflectionRules());
        for (StrategyInsightEdit edit : edits) {
            if (edit == null) {
                continue;
            }
            switch (edit.operation()) {
                case ADD, AGREE -> addIfAbsent(rules, edit.insight());
                case EDIT -> replace(rules, edit.target(), edit.insight());
                case REMOVE -> removeIfSafe(rules, edit.target());
            }
        }
        return new StrategyTemplate.Content(base.preconditions(), base.planSkeleton(), base.toolPolicy(),
                rules, base.successCriteria());
    }

    private static void addIfAbsent(List<String> rules, String insight) {
        if (insight != null && !insight.isBlank() && !rules.contains(insight)
                && rules.size() < MAX_REFLECTION_RULES) {
            rules.add(insight);
        }
    }

    private static void replace(List<String> rules, String target, String replacement) {
        int index = rules.indexOf(target);
        if (index >= 0) {
            rules.set(index, replacement);
        } else {
            addIfAbsent(rules, replacement);
        }
    }

    private static void removeIfSafe(List<String> rules, String target) {
        if (rules.size() > 1) {
            rules.remove(target);
        }
    }
}
