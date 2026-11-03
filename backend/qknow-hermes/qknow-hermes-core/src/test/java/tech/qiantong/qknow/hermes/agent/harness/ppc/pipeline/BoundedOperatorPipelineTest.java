package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;

import static org.junit.jupiter.api.Assertions.*;

class BoundedOperatorPipelineTest {
    private static final Operator PROMPT = Operator.CONCISE_SYSTEM_PROMPT;
    private static final Operator MEMORY = Operator.ADAPTIVE_MEMORY_WINDOW;
    private static final Operator TOOL = Operator.TOOL_GUIDANCE_SYNTHESIS;
    private static final Operator GUARD = Operator.GUARDRAIL_TIGHTENING;

    @Test
    void rejectsDuplicateOperatorsAndComponentTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedOperatorPipeline(List.of(PROMPT, PROMPT)));
        assertThrows(NullPointerException.class,
                () -> new BoundedOperatorPipeline(java.util.Arrays.asList(PROMPT, null)));
        assertThrows(IllegalArgumentException.class, () -> new TargetConflictValidator()
                .validateTargets(List.of(OperatorTarget.SYSTEM_PROMPT, OperatorTarget.SYSTEM_PROMPT)));
        assertEquals(OperatorTarget.SYSTEM_PROMPT, OperatorTarget.of(PROMPT));
        assertEquals(OperatorTarget.MEMORY_CONTEXT, OperatorTarget.of(MEMORY));
        assertEquals(OperatorTarget.TOOL_GUIDANCE, OperatorTarget.of(TOOL));
        assertEquals(OperatorTarget.GUARDRAILS, OperatorTarget.of(GUARD));
        assertEquals(4, List.of(PROMPT, MEMORY, TOOL, GUARD).stream()
                .map(OperatorTarget::of).distinct().count());
    }

    @Test
    void enforcesPipelineAndCandidateBounds() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedOperatorPipeline(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedOperatorPipeline(List.of(PROMPT, MEMORY, TOOL, GUARD)));
        assertThrows(IllegalArgumentException.class,
                () -> new BoundedOperatorPipeline(List.of(MEMORY, PROMPT)));

        PipelineMutation mutation = new PipelineMutation();
        assertEquals(4, mutation.generate(List.of(PROMPT, MEMORY, TOOL),
                List.of(List.of(PROMPT, MEMORY))).size());
        assertThrows(IllegalArgumentException.class, () -> mutation.generate(
                List.of(PROMPT, MEMORY, TOOL, GUARD), List.of(List.of(PROMPT, MEMORY))));
        assertThrows(IllegalArgumentException.class,
                () -> mutation.generate(List.of(PROMPT, PROMPT), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> mutation.generate(List.of(), List.of(List.of(MEMORY, PROMPT))));
        assertThrows(IllegalArgumentException.class,
                () -> mutation.generate(List.of(), List.of(List.of(PROMPT))));
    }

    @Test
    void orderedFingerprintAndMutationAreDeterministic() {
        BoundedOperatorPipeline plan = new BoundedOperatorPipeline(List.of(PROMPT, MEMORY));
        assertEquals(plan.fingerprint(), new BoundedOperatorPipeline(List.of(PROMPT, MEMORY)).fingerprint());
        assertNotEquals(plan.fingerprint(), new BoundedOperatorPipeline(List.of(PROMPT)).fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> plan.operators().add(TOOL));

        HermesHarnessVoucher baseline = new HermesHarnessAssembler().createDefaultProductionVoucher(16L);
        HermesHarnessVoucher changed = new PipelineMutation().apply(baseline, plan);
        assertEquals(changed.computeFingerprint(),
                new PipelineMutation().apply(baseline, plan).computeFingerprint());
        assertNotEquals(baseline.invariantSystemPrompt(), changed.invariantSystemPrompt());
        assertEquals(baseline.memoryContext().shortTermWindowSize() - 1,
                changed.memoryContext().shortTermWindowSize());
        assertEquals(baseline.toolGuidance(), changed.toolGuidance());
        assertEquals(baseline.guardrails(), changed.guardrails());
    }
}
