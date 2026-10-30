package tech.qiantong.qknow.hermes.agent.harness.neurosymbolic;

import tech.qiantong.qknow.hermes.agent.harness.GuardrailsSpecificationRecord;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Deterministic P(args, G) and Q(result, G) checks; execution remains with the caller. */
public final class HoareTripleToolGuard {
    public enum Violation {
        MISSING_CONTRACT, PERMISSION, SCHEMA, BOUNDARY,
        GUARDRAIL_STEP_LIMIT, GUARDRAIL_LOOP_THRESHOLD,
        GUARDRAIL_TOKEN_BUDGET, GUARDRAIL_COST_BUDGET,
        RESULT_INTEGRITY, SENSITIVE_OUTPUT
    }

    public record ExecutionState(int currentStep, int loopFrequency,
                                 long estimatedTokens, double estimatedCostCny) {
        public ExecutionState {
            if (currentStep < 0 || loopFrequency < 0 || estimatedTokens < 0L
                    || !Double.isFinite(estimatedCostCny) || estimatedCostCny < 0.0D) {
                throw new IllegalArgumentException("ExecutionState metrics must be non-negative and finite");
            }
        }

        public static ExecutionState initial() {
            return new ExecutionState(1, 1, 0L, 0.0D);
        }

        public static ExecutionState fromArguments(Map<String, Object> arguments) {
            if (arguments == null || arguments.isEmpty()) {
                return initial();
            }
            int step = toInt(arguments.get("_step"), 1);
            int frequency = toInt(arguments.get("_loopFrequency"), 1);
            long tokens = toLong(arguments.get("_estimatedTokens"), 0L);
            double cost = toDouble(arguments.get("_estimatedCostCny"), 0.0D);
            return new ExecutionState(Math.max(0, step), Math.max(0, frequency),
                    Math.max(0L, tokens), Math.max(0.0D, cost));
        }

        private static int toInt(Object value, int fallback) {
            return value == null ? fallback : new BigDecimal(value.toString()).intValueExact();
        }

        private static long toLong(Object value, long fallback) {
            return value == null ? fallback : new BigDecimal(value.toString()).longValueExact();
        }

        private static double toDouble(Object value, double fallback) {
            return value == null ? fallback : Double.parseDouble(value.toString());
        }
    }

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
        return verifyBefore(toolName, arguments, guardrails, contract, ExecutionState.fromArguments(arguments));
    }

    public Decision verifyBefore(String toolName, Map<String, Object> arguments,
                                 GuardrailsSpecificationRecord guardrails, Contract contract,
                                 ExecutionState state) {
        Objects.requireNonNull(guardrails, "guardrails");
        Objects.requireNonNull(state, "state");
        if (contract == null || toolName == null || toolName.isBlank() || arguments == null) {
            return Decision.reject(Violation.MISSING_CONTRACT, "Tool, arguments and contract are required");
        }
        if (state.currentStep() > guardrails.maxCycleSteps()) {
            return Decision.reject(Violation.GUARDRAIL_STEP_LIMIT, "Cycle step limit exceeded");
        }
        if (state.loopFrequency() >= guardrails.loopThreshold()) {
            return Decision.reject(Violation.GUARDRAIL_LOOP_THRESHOLD, "Loop threshold reached");
        }
        if (state.estimatedTokens() > guardrails.maxTokenBudget()) {
            return Decision.reject(Violation.GUARDRAIL_TOKEN_BUDGET, "Token budget exceeded");
        }
        if (state.estimatedCostCny() > guardrails.maxCostCny()) {
            return Decision.reject(Violation.GUARDRAIL_COST_BUDGET, "Cost budget exceeded");
        }
        String normalizedTool = toolName.trim();
        if (!contract.permittedTools().contains(normalizedTool)) {
            return Decision.reject(Violation.PERMISSION, "Tool is not permitted: " + toolName);
        }
        Map<String, Object> safeArgs = arguments;
        for (Map.Entry<String, Class<?>> field : contract.requiredArguments().entrySet()) {
            if (field.getValue() == null || !field.getValue().isInstance(safeArgs.get(field.getKey()))) {
                return Decision.reject(Violation.SCHEMA, "Invalid argument: " + field.getKey());
            }
        }
        try {
            if (!contract.boundary().test(Map.copyOf(safeArgs))) {
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
