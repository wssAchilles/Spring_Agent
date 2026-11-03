package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline;

import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;

/** Harness component changed by a built-in operator. */
public enum OperatorTarget {
    SYSTEM_PROMPT, MEMORY_CONTEXT, TOOL_GUIDANCE, GUARDRAILS;

    public static OperatorTarget of(Operator operator) {
        return switch (Objects.requireNonNull(operator, "operator")) {
            case CONCISE_SYSTEM_PROMPT -> SYSTEM_PROMPT;
            case ADAPTIVE_MEMORY_WINDOW -> MEMORY_CONTEXT;
            case TOOL_GUIDANCE_SYNTHESIS -> TOOL_GUIDANCE;
            case GUARDRAIL_TIGHTENING -> GUARDRAILS;
        };
    }
}
