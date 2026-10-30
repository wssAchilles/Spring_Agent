package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic;

import tech.qiantong.qknow.hermes.agent.harness.GuardrailsSpecificationRecord;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Deterministic P(args, G) and Q(result, G) checks; execution remains with the caller. */
public final class HoareTripleToolGuard {
    public enum Violation { MISSING_CONTRACT, PERMISSION, SCHEMA, BOUNDARY, RESULT_INTEGRITY, SENSITIVE_OUTPUT }

    public record Counterexample(Violation violation, String detail) {}

    public record Decision(boolean allowed, Counterexample counterexample) {
        public static Decision pass() {
            return new Decision(true, null);
        }

        public static Decision reject(Violation violation, String detail) {
            return new Decision(false, new Counterexample(violation, detail));
        }
    }

    public record Contract(Map<String, Class<?>> requiredArguments,
                           Predicate<Map<String, Object>> boundary,
                           Set<String> permittedTools,
                           Set<String> requiredResultFields,
                           Set<String> forbiddenOutputFragments) {
        public Contract {
            requiredArguments = Map.copyOf(Objects.requireNonNull(requiredArguments, "requiredArguments"));
            boundary = Objects.requireNonNull(boundary, "boundary");
            permittedTools = Set.copyOf(Objects.requireNonNull(permittedTools, "permittedTools"));
            requiredResultFields = Set.copyOf(Objects.requireNonNull(requiredResultFields, "requiredResultFields"));
            forbiddenOutputFragments = Set.copyOf(Objects.requireNonNull(forbiddenOutputFragments, "forbiddenOutputFragments"));
        }
    }

    public Decision verifyBefore(String toolName, Map<String, Object> arguments,
                                 GuardrailsSpecificationRecord guardrails, Contract contract) {
        if (contract == null || guardrails == null || toolName == null || arguments == null) {
            return Decision.reject(Violation.MISSING_CONTRACT, "Tool, arguments, guardrails and contract are required");
        }
        if (!contract.permittedTools().contains(toolName)) {
            return Decision.reject(Violation.PERMISSION, "Tool is not permitted: " + toolName);
        }
        for (Map.Entry<String, Class<?>> field : contract.requiredArguments().entrySet()) {
            if (field.getValue() == null || !field.getValue().isInstance(arguments.get(field.getKey()))) {
                return Decision.reject(Violation.SCHEMA, "Invalid argument: " + field.getKey());
            }
        }
        try {
            if (!contract.boundary().test(Map.copyOf(arguments))) {
                return Decision.reject(Violation.BOUNDARY, "Argument boundary violated");
            }
        } catch (RuntimeException e) {
            return Decision.reject(Violation.BOUNDARY, "Argument boundary could not be verified");
        }
        return Decision.pass();
    }

    public Decision verifyAfter(Object result, Contract contract) {
        if (contract == null) {
            return Decision.reject(Violation.MISSING_CONTRACT, "Postcondition contract is required");
        }
        String output;
        try {
            output = result == null ? "" : result.toString();
        } catch (RuntimeException e) {
            return Decision.reject(Violation.RESULT_INTEGRITY, "Tool result could not be inspected");
        }
        if (output.isBlank()) {
            return Decision.reject(Violation.RESULT_INTEGRITY, "Tool result is empty");
        }
        if (!contract.requiredResultFields().isEmpty()) {
            if (!(result instanceof Map<?, ?> fields)) {
                return Decision.reject(Violation.RESULT_INTEGRITY, "Structured result is required");
            }
            try {
                for (String field : contract.requiredResultFields()) {
                    if (!fields.containsKey(field) || fields.get(field) == null) {
                        return Decision.reject(Violation.RESULT_INTEGRITY, "Missing result field: " + field);
                    }
                }
            } catch (RuntimeException e) {
                return Decision.reject(Violation.RESULT_INTEGRITY, "Tool result could not be verified");
            }
        }
        output = output.toLowerCase(java.util.Locale.ROOT);
        for (String forbidden : contract.forbiddenOutputFragments()) {
            if (!forbidden.isBlank() && output.contains(forbidden.toLowerCase(java.util.Locale.ROOT))) {
                return Decision.reject(Violation.SENSITIVE_OUTPUT, "Sensitive output invariant violated");
            }
        }
        return Decision.pass();
    }
}
