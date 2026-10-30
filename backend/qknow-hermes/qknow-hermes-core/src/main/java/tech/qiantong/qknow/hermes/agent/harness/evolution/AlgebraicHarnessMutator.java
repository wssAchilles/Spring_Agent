package tech.qiantong.qknow.hermes.agent.harness.evolution;

import tech.qiantong.qknow.hermes.agent.harness.GuardrailsSpecificationRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.InvariantSystemPromptRecord;
import tech.qiantong.qknow.hermes.agent.harness.MemoryContextStrategyRecord;
import tech.qiantong.qknow.hermes.agent.harness.ToolGuidancePolicyRecord;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** One bounded, immutable voucher-component mutation per invocation. */
public final class AlgebraicHarnessMutator {
    public enum Operator {
        CONCISE_SYSTEM_PROMPT, ADAPTIVE_MEMORY_WINDOW,
        TOOL_GUIDANCE_SYNTHESIS, GUARDRAIL_TIGHTENING
    }

    public HermesHarnessVoucher mutate(HermesHarnessVoucher source, Operator operator) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(operator, "operator");
        InvariantSystemPromptRecord prompt = source.invariantSystemPrompt();
        MemoryContextStrategyRecord memory = source.memoryContext();
        ToolGuidancePolicyRecord guidance = source.toolGuidance();
        GuardrailsSpecificationRecord guardrails = source.guardrails();

        switch (operator) {
            case CONCISE_SYSTEM_PROMPT -> {
                String concise = prompt.corePrinciples().replaceAll("\\s+", " ").trim();
                if (concise.equals(prompt.corePrinciples())) {
                    throw new IllegalStateException("System prompt is already concise");
                }
                prompt = new InvariantSystemPromptRecord(prompt.roleDefinition(), concise,
                        prompt.safetyInstructions());
            }
            case ADAPTIVE_MEMORY_WINDOW -> {
                if (memory.shortTermWindowSize() <= 4) {
                    throw new IllegalStateException("Memory window lower bound reached");
                }
                memory = new MemoryContextStrategyRecord(memory.shortTermWindowSize() - 1,
                        memory.longTermRecallTopK(), memory.reflexionDepth());
            }
            case TOOL_GUIDANCE_SYNTHESIS -> {
                if (guidance.autoToolSelectionEnabled() && guidance.hoareVerificationEnabled()) {
                    throw new IllegalStateException("Tool guidance upper bound reached");
                }
                guidance = new ToolGuidancePolicyRecord(true, true, guidance.twoPhaseApprovalEnabled());
            }
            case GUARDRAIL_TIGHTENING -> {
                if (guardrails.maxCycleSteps() <= 1 && guardrails.loopThreshold() <= 1) {
                    throw new IllegalStateException("Guardrail lower bound reached");
                }
                guardrails = new GuardrailsSpecificationRecord(
                        Math.max(1, guardrails.maxCycleSteps() - 1),
                        Math.max(1, guardrails.loopThreshold() - 1),
                        guardrails.maxTokenBudget(), guardrails.maxCostCny());
            }
        }

        HermesHarnessVoucher candidate = new HermesHarnessVoucher(
                source.voucherId() + "-" + operator.name().toLowerCase(Locale.ROOT),
                source.botId(), source.version() + "-" + operator.name().toLowerCase(Locale.ROOT),
                Instant.now(), prompt, guardrails, memory, guidance, source.subagents(),
                source.skillBank(), source.sandboxedExecutionPlan(), source.mcpMiddlewarePlugins());
        if (candidate.computeFingerprint().equals(source.computeFingerprint())) {
            throw new IllegalStateException("Mutation produced no change");
        }
        return candidate;
    }
}
