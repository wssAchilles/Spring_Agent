package tech.qiantong.qknow.hermes.agent.harness.ppc.validity;

import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.InvariantSystemPromptRecord;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;

/** Isolated Stage 15 candidates; frozen Stage 13 operators and fingerprints stay unchanged. */
public final class ControlledValidityCandidates {
    private static final String EVIDENCE_INSTRUCTION = "\n逐字段核对证据与取值；疑问、否定和引用不是肯定事实。"
            + "布尔字段只能依据显式证据赋值，不得从问题措辞推断。仅输出 taskId/facts JSON。";

    private ControlledValidityCandidates() { }

    public static HermesHarnessVoucher evidenceBoundPrompt(HermesHarnessVoucher baseline) {
        Objects.requireNonNull(baseline, "baseline");
        InvariantSystemPromptRecord original = baseline.invariantSystemPrompt();
        InvariantSystemPromptRecord revised = new InvariantSystemPromptRecord(original.roleDefinition(),
                original.corePrinciples() + EVIDENCE_INSTRUCTION, original.safetyInstructions());
        return new HermesHarnessVoucher(baseline.voucherId() + "-h15-a", baseline.botId(),
                baseline.version() + "-h15-a", baseline.createdAt(), revised, baseline.guardrails(),
                baseline.memoryContext(), baseline.toolGuidance(), baseline.subagents(),
                baseline.skillBank(), baseline.sandboxedExecutionPlan(), baseline.mcpMiddlewarePlugins());
    }

    public static HermesHarnessVoucher triggeredMemoryWindow(HermesHarnessVoucher baseline) {
        Objects.requireNonNull(baseline, "baseline");
        if (baseline.memoryContext().shortTermWindowSize() != 20) {
            throw new IllegalArgumentException("Stage 15 B requires the frozen 20-turn baseline window");
        }
        return new AlgebraicHarnessMutator().mutate(baseline,
                AlgebraicHarnessMutator.Operator.ADAPTIVE_MEMORY_WINDOW);
    }
}
