package tech.qiantong.qknow.hermes.agent.harness.ppc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.GuardrailsSpecificationRecord;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.InvariantSystemPromptRecord;
import tech.qiantong.qknow.hermes.agent.harness.RagAttributionSummary;
import tech.qiantong.qknow.hermes.agent.harness.UsageStatus;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;

import static org.junit.jupiter.api.Assertions.*;

class PpcCostAndSelectionContractTest {
    private final PpcComposer composer = new PpcComposer();

    @Test
    void generatesOnlySpecifiedComponentChangesAndRejectsQrelLeakage() {
        assertThrows(IllegalArgumentException.class,
                () -> new PpcRun.Budget(21, 60, 200_000L, 2_000, 10.0D));
        HermesHarnessVoucher source = baselineVoucher();
        List<PpcCandidate> candidates = composer.createCandidates(source);
        assertEquals(List.of("baseline", "candidate-a", "candidate-b"),
                candidates.stream().map(PpcCandidate::candidateId).toList());
        assertEquals(source.guardrails(), candidates.get(1).voucher().guardrails());
        assertEquals(source.version(), candidates.get(1).voucher().version());
        assertEquals(source.voucherId(), candidates.get(1).voucher().voucherId());
        assertEquals(source.createdAt(), candidates.get(1).voucher().createdAt());
        assertEquals(source.invariantSystemPrompt().safetyInstructions(),
                candidates.get(1).voucher().invariantSystemPrompt().safetyInstructions());
        assertTrue(candidates.get(1).voucher().invariantSystemPrompt().canonical().length()
                < source.invariantSystemPrompt().canonical().length());
        assertEquals(source.invariantSystemPrompt(), candidates.get(2).voucher().invariantSystemPrompt());
        assertEquals(2, candidates.get(2).voucher().guardrails().maxCycleSteps());
        assertEquals(source.guardrails().loopThreshold(), candidates.get(2).voucher().guardrails().loopThreshold());
        PpcCandidate combined = composer.createCombinedCandidate(candidates.get(1), candidates.get(2));
        assertEquals(candidates.get(1).voucher().invariantSystemPrompt(), combined.voucher().invariantSystemPrompt());
        assertEquals(candidates.get(2).voucher().guardrails(), combined.voucher().guardrails());
        assertThrows(IllegalStateException.class,
                () -> composer.assertNoQrelLeakage("hidden expected answer", List.of("expected answer")));
    }

    @Test
    void dropsUnknownUsageAndSelectsOnlyFiveCaseConsistentImprovement() {
        List<PpcCandidate> candidates = composer.createCandidates(baselineVoucher());
        List<PpcProbeResult> probes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String id = "case-" + i;
            probes.add(probe("baseline", id, UsageStatus.KNOWN, true, 100, 0.50));
            probes.add(probe("candidate-a", id, UsageStatus.KNOWN, true, 80, 0.40));
            probes.add(probe("candidate-b", id, i == 2 ? UsageStatus.UNKNOWN : UsageStatus.KNOWN,
                    i != 2, 70, i == 2 ? 0.0 : 0.35));
        }
        PpcComposer.SelectionResult result = composer.select("run-1", candidates, probes);
        assertEquals("candidate-a", result.selectedCandidateId());
        assertEquals(PpcComposer.ADOPTED, result.finalVerdict());
        assertEquals(PpcComposer.DROPPED_UNKNOWN_USAGE_OR_COST, result.droppedReasons().get("candidate-b"));
        assertEquals(5, result.audits().size());
        assertTrue(result.audits().stream().allMatch(PpcComposer.PositionSwapAudit::positionSwapConsistent));
        assertTrue(result.audits().stream().allMatch(audit -> "CANDIDATE".equals(audit.outcome())));
    }

    private HermesHarnessVoucher baselineVoucher() {
        HermesHarnessVoucher original = new HermesHarnessAssembler().createDefaultProductionVoucher(7L);
        return new HermesHarnessVoucher("baseline", 7L, "stage9", Instant.EPOCH,
                new InvariantSystemPromptRecord("assistant", "Give  clear   grounded  answers", "Keep safety"),
                new GuardrailsSpecificationRecord(3, 2, 200_000L, 10.0D), original.memoryContext(),
                original.toolGuidance(), original.subagents(), original.skillBank(), original.sandboxedExecutionPlan(),
                original.mcpMiddlewarePlugins());
    }

    private PpcProbeResult probe(String candidateId, String caseId, UsageStatus status, boolean costKnown,
                                 long tokens, double cost) {
        HarnessEvidenceRecord evidence = new HarnessEvidenceRecord(candidateId + "-" + caseId, caseId, 7L,
                "fingerprint", "stage9", Instant.EPOCH, "question", 0, 0.0, List.of(), tokens, cost,
                10L, "answer", HarnessEvidenceRecord.ExecutionMode.REAL_PRODUCTION);
        return new PpcProbeResult(candidateId, caseId, CapabilityTag.PROMPT_ONLY, Instant.EPOCH, false,
                HarnessEvidenceRecord.ExecutionMode.REAL_PRODUCTION, status, costKnown,
                tokens - 20, 20, tokens, 0, 0, cost, 1, 10, true, false, false,
                List.of(), RagAttributionSummary.noRag(), false, evidence);
    }
}
