package tech.qiantong.qknow.hermes.cognitive.core.model;

import java.util.List;

public record PlanStep(String stepId, String objective, List<String> dependencies) {
    public PlanStep {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
