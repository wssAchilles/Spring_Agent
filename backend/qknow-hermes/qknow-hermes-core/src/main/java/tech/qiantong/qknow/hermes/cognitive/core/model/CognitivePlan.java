package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.util.List;

public record CognitivePlan(List<PlanStep> steps) {
    public CognitivePlan {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
