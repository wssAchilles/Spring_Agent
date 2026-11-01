package tech.qiantong.qknow.hermes.agent.harness.ppc.recursive;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.GuardrailsSpecificationRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.InvariantSystemPromptRecord;
import tech.qiantong.qknow.hermes.agent.harness.MemoryContextStrategyRecord;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.experience.DefaultExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.EvidenceReference;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceStore;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcCandidate;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.ExperienceGuidedProposer.OperatorProvenance;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.OfflineRecursivePpc.CaseScore;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.OfflineRecursivePpc.Decision;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.OfflineRecursivePpc.ProbeResult;
import tech.qiantong.qknow.hermes.agent.harness.ppc.recursive.OfflineRecursivePpc.StopReason;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OfflineRecursivePpcTest {
    private static final ExperienceDomain DOMAIN = new ExperienceDomain(7, 11, "synthetic");
    private static final String CUE = "synthetic offline cue";
    private static final String RUN = "synthetic-run";
    private final AlgebraicHarnessMutator mutator = new AlgebraicHarnessMutator();

    @Test
    void onlyAdoptedSameDomainUnretiredExperienceIsSuggested() {
        HermesHarnessVoucher baseline = baseline(20);
        OperatorProvenance memory = provenance(baseline, Operator.ADAPTIVE_MEMORY_WINDOW);
        ExperienceDomain other = new ExperienceDomain(7, 12, "synthetic");
        List<ExperienceEntry> entries = List.of(
                entry("valid", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.9,
                        memory.candidate().voucherFingerprint()),
                entry("observed", DOMAIN, ExperienceDisposition.OBSERVED, false, 0.9,
                        memory.candidate().voucherFingerprint()),
                entry("rejected", DOMAIN, ExperienceDisposition.REJECTED, false, 0.9,
                        memory.candidate().voucherFingerprint()),
                entry("unresolved", DOMAIN, ExperienceDisposition.OBSERVED, true, 0.9,
                        memory.candidate().voucherFingerprint()),
                entry("low", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.1,
                        memory.candidate().voucherFingerprint()),
                entry("retired", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.9,
                        memory.candidate().voucherFingerprint()).retired("superseded"),
                entry("cross-domain", other, ExperienceDisposition.ADOPTED, false, 0.9,
                        memory.candidate().voucherFingerprint()));
        Setup setup = setup(entries);
        Map<String, OperatorProvenance> bindings = Map.of("valid", memory, "observed", memory,
                "rejected", memory, "unresolved", memory, "low", memory, "retired", memory,
                "cross-domain", memory);

        assertEquals(List.of("valid"), setup.proposer().propose(DOMAIN, CUE, bindings).stream()
                .map(ExperienceGuidedProposer.Suggestion::entryId).toList());
        verify(setup.store(), never()).save(any());
    }

    @Test
    void unscopedReplayNeverReadsTheStore() {
        Setup setup = setup(List.of());
        assertTrue(setup.proposer().propose(ExperienceDomain.unscopedReplay(), CUE, Map.of()).isEmpty());
        verifyNoInteractions(setup.store());
    }

    @Test
    void provenanceMustMatchRunFingerprintBotAndSingleOperator() {
        HermesHarnessVoucher baseline = baseline(20);
        OperatorProvenance memory = provenance(baseline, Operator.ADAPTIVE_MEMORY_WINDOW);
        PpcCandidate candidate = memory.candidate();
        OperatorProvenance multi = new OperatorProvenance(RUN, PpcCandidate.of("multi", candidate.voucher(),
                List.of(Operator.ADAPTIVE_MEMORY_WINDOW, Operator.GUARDRAIL_TIGHTENING)));
        HermesHarnessVoucher otherBot = new HermesHarnessAssembler().createDefaultProductionVoucher(8L);
        OperatorProvenance wrongBot = new OperatorProvenance(RUN,
                PpcCandidate.of("wrong-bot", otherBot, List.of(Operator.ADAPTIVE_MEMORY_WINDOW)));
        Setup setup = setup(List.of(
                entry("wrong-run", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.8,
                        candidate.voucherFingerprint()),
                entry("wrong-fingerprint", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.8,
                        "f".repeat(64)),
                entry("multi", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.8,
                        candidate.voucherFingerprint()),
                entry("wrong-bot", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.8,
                        wrongBot.candidate().voucherFingerprint())));

        assertTrue(setup.proposer().propose(DOMAIN, CUE, Map.of(
                "wrong-run", new OperatorProvenance("another-run", candidate),
                "wrong-fingerprint", memory, "multi", multi, "wrong-bot", wrongBot)).isEmpty());
    }

    @Test
    void unchangedScoresStopAfterOneRoundWithoutPromotionOrWrites() {
        HermesHarnessVoucher baseline = baseline(20);
        OperatorProvenance memory = provenance(baseline, Operator.ADAPTIVE_MEMORY_WINDOW);
        Setup setup = setup(List.of(entry("memory", DOMAIN, ExperienceDisposition.ADOPTED, false,
                0.8, memory.candidate().voucherFingerprint())));
        AtomicInteger calls = new AtomicInteger();

        OfflineRecursivePpc.Result result = setup.engine().run(DOMAIN, CUE, baseline,
                Map.of("memory", memory), voucher -> {
                    calls.incrementAndGet();
                    return scores(0.5, 0.5, true);
                });

        assertEquals("OFFLINE_CANDIDATE", result.executionMode());
        assertEquals(StopReason.NO_IMPROVEMENT, result.stopReason());
        assertEquals(Decision.REJECTED, result.attempts().getFirst().decision());
        assertEquals(baseline.computeFingerprint(), result.selected().computeFingerprint());
        assertEquals(1, result.attempts().size());
        assertEquals(2, calls.get());
        verify(setup.store(), never()).save(any());
    }

    @Test
    void twoStrictImprovementsReachTheHardRoundLimit() {
        HermesHarnessVoucher baseline = baseline(20);
        OperatorProvenance memory = provenance(baseline, Operator.ADAPTIVE_MEMORY_WINDOW);
        OperatorProvenance guardrail = provenance(baseline, Operator.GUARDRAIL_TIGHTENING);
        Setup setup = setup(List.of(
                entry("memory", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.9,
                        memory.candidate().voucherFingerprint()),
                entry("guardrail", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.8,
                        guardrail.candidate().voucherFingerprint())));
        AtomicInteger calls = new AtomicInteger();

        OfflineRecursivePpc.Result result = setup.engine().run(DOMAIN, CUE, baseline,
                Map.of("memory", memory, "guardrail", guardrail), voucher -> {
                    calls.incrementAndGet();
                    double score = 0.5 + (20 - voucher.memoryContext().shortTermWindowSize()) * 0.1
                            + (3 - voucher.guardrails().maxCycleSteps()) * 0.1;
                    return scores(score, 0.5, true);
                });

        assertEquals(StopReason.ROUND_LIMIT, result.stopReason());
        assertEquals(List.of(Decision.ACCEPTED, Decision.ACCEPTED), result.attempts().stream()
                .map(OfflineRecursivePpc.Attempt::decision).toList());
        assertEquals(19, result.selected().memoryContext().shortTermWindowSize());
        assertEquals(2, result.selected().guardrails().maxCycleSteps());
        assertEquals(3, calls.get());
    }

    @Test
    void secondRoundRegressionKeepsTheLastVerifiedVoucher() {
        HermesHarnessVoucher baseline = baseline(20);
        OperatorProvenance memory = provenance(baseline, Operator.ADAPTIVE_MEMORY_WINDOW);
        OperatorProvenance guardrail = provenance(baseline, Operator.GUARDRAIL_TIGHTENING);
        Setup setup = setup(List.of(
                entry("memory", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.9,
                        memory.candidate().voucherFingerprint()),
                entry("guardrail", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.8,
                        guardrail.candidate().voucherFingerprint())));

        OfflineRecursivePpc.Result result = setup.engine().run(DOMAIN, CUE, baseline,
                Map.of("memory", memory, "guardrail", guardrail), voucher -> {
                    if (voucher.guardrails().maxCycleSteps() < 3) {
                        return scores(0.4, 0.5, true);
                    }
                    return scores(voucher.memoryContext().shortTermWindowSize() < 20 ? 0.6 : 0.5,
                            0.5, true);
                });

        assertEquals(StopReason.NO_IMPROVEMENT, result.stopReason());
        assertEquals(List.of(Decision.ACCEPTED, Decision.REJECTED), result.attempts().stream()
                .map(OfflineRecursivePpc.Attempt::decision).toList());
        assertEquals(19, result.selected().memoryContext().shortTermWindowSize());
        assertEquals(3, result.selected().guardrails().maxCycleSteps());
    }

    @Test
    void failedOrIncompleteCandidateProbeFallsBackToBaseline() {
        HermesHarnessVoucher baseline = baseline(20);
        OperatorProvenance memory = provenance(baseline, Operator.ADAPTIVE_MEMORY_WINDOW);
        Setup setup = setup(List.of(entry("memory", DOMAIN, ExperienceDisposition.ADOPTED, false,
                0.8, memory.candidate().voucherFingerprint())));

        OfflineRecursivePpc.Result failed = setup.engine().run(DOMAIN, CUE, baseline,
                Map.of("memory", memory), voucher -> {
                    if (voucher.memoryContext().shortTermWindowSize() < 20) {
                        throw new IllegalStateException("synthetic probe failure");
                    }
                    return scores(0.5, 0.5, true);
                });
        OfflineRecursivePpc.Result incomplete = setup.engine().run(DOMAIN, CUE, baseline,
                Map.of("memory", memory), voucher -> voucher.memoryContext().shortTermWindowSize() < 20
                        ? new ProbeResult(Map.of("case-a", new CaseScore(0.9, true)))
                        : scores(0.5, 0.5, true));

        assertEquals(StopReason.PROBE_FAILED, failed.stopReason());
        assertTrue(failed.attempts().getFirst().probe().isEmpty());
        assertEquals(StopReason.INCOMPARABLE_PROBE, incomplete.stopReason());
        assertEquals(baseline.computeFingerprint(), failed.selected().computeFingerprint());
        assertEquals(baseline.computeFingerprint(), incomplete.selected().computeFingerprint());
    }

    @Test
    void unsafeOrNonFiniteCandidateCannotBePromoted() {
        HermesHarnessVoucher baseline = baseline(20);
        OperatorProvenance memory = provenance(baseline, Operator.ADAPTIVE_MEMORY_WINDOW);
        Setup setup = setup(List.of(entry("memory", DOMAIN, ExperienceDisposition.ADOPTED, false,
                0.8, memory.candidate().voucherFingerprint())));

        OfflineRecursivePpc.Result unsafe = setup.engine().run(DOMAIN, CUE, baseline,
                Map.of("memory", memory), voucher -> scores(
                        voucher.memoryContext().shortTermWindowSize() < 20 ? 0.9 : 0.5, 0.5,
                        voucher.memoryContext().shortTermWindowSize() == 20));
        OfflineRecursivePpc.Result invalid = setup.engine().run(DOMAIN, CUE, baseline,
                Map.of("memory", memory), voucher -> scores(
                        voucher.memoryContext().shortTermWindowSize() < 20 ? Double.NaN : 0.5,
                        0.5, true));

        assertEquals(StopReason.NO_IMPROVEMENT, unsafe.stopReason());
        assertEquals(StopReason.PROBE_FAILED, invalid.stopReason());
        assertEquals(baseline.computeFingerprint(), unsafe.selected().computeFingerprint());
        assertEquals(baseline.computeFingerprint(), invalid.selected().computeFingerprint());
    }

    @Test
    void unavailableOperatorIsSkippedBeforeTheFirstProbeRound() {
        HermesHarnessVoucher source = baseline(20);
        HermesHarnessVoucher baseline = baseline(4);
        OperatorProvenance memory = provenance(source, Operator.ADAPTIVE_MEMORY_WINDOW);
        OperatorProvenance guardrail = provenance(source, Operator.GUARDRAIL_TIGHTENING);
        Setup setup = setup(List.of(
                entry("memory", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.9,
                        memory.candidate().voucherFingerprint()),
                entry("guardrail", DOMAIN, ExperienceDisposition.ADOPTED, false, 0.8,
                        guardrail.candidate().voucherFingerprint())));

        OfflineRecursivePpc.Result result = setup.engine().run(DOMAIN, CUE, baseline,
                Map.of("memory", memory, "guardrail", guardrail), voucher -> scores(
                        voucher.guardrails().maxCycleSteps() < 3 ? 0.6 : 0.5, 0.5, true));

        assertEquals(1, result.attempts().size());
        assertEquals(Operator.GUARDRAIL_TIGHTENING, result.attempts().getFirst().suggestion().operator());
        assertEquals(StopReason.NO_ELIGIBLE_OPERATOR, result.stopReason());
        assertEquals(2, result.selected().guardrails().maxCycleSteps());
    }

    @Test
    void unsafeBaselineCannotProduceAnOfflineCandidate() {
        HermesHarnessVoucher baseline = baseline(20);
        Setup setup = setup(List.of());
        assertThrows(IllegalStateException.class, () -> setup.engine().run(DOMAIN, CUE, baseline,
                Map.of(), voucher -> scores(0.5, 0.5, false)));
        verifyNoInteractions(setup.store());
    }

    @Test
    void invalidCueIsRejectedBeforeAnyProbe() {
        Setup setup = setup(List.of());
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> setup.engine().run(DOMAIN, " ", baseline(20),
                Map.of(), voucher -> {
                    calls.incrementAndGet();
                    return scores(0.5, 0.5, true);
                }));
        assertEquals(0, calls.get());
        verifyNoInteractions(setup.store());
    }

    private Setup setup(List<ExperienceEntry> entries) {
        ExperienceStore store = mock(ExperienceStore.class);
        when(store.list(DOMAIN)).thenReturn(entries);
        ExperienceGuidedProposer proposer = new ExperienceGuidedProposer(new DefaultExperienceLibrary(store));
        return new Setup(store, proposer, new OfflineRecursivePpc(proposer, mutator));
    }

    private OperatorProvenance provenance(HermesHarnessVoucher source, Operator operator) {
        PpcCandidate candidate = PpcCandidate.of(operator.name(), mutator.mutate(source, operator),
                List.of(operator));
        return new OperatorProvenance(RUN, candidate);
    }

    private ExperienceEntry entry(String id, ExperienceDomain domain, ExperienceDisposition disposition,
                                  boolean failureObserved, double confidence, String fingerprint) {
        return new ExperienceEntry(id, domain, CUE, "Synthetic lesson from a fixed offline run",
                "Synthetic evidence only", new EvidenceReference("evidence-1", "request-1", "a".repeat(64),
                "OFFLINE"), confidence, RUN, fingerprint, disposition, failureObserved,
                failureObserved ? ExperienceEntry.AttributionStatus.UNRESOLVED
                        : ExperienceEntry.AttributionStatus.NOT_APPLICABLE,
                1, false, null, Instant.EPOCH);
    }

    private ProbeResult scores(double first, double second, boolean safe) {
        return new ProbeResult(Map.of("case-a", new CaseScore(first, safe),
                "case-b", new CaseScore(second, safe)));
    }

    private HermesHarnessVoucher baseline(int memoryWindow) {
        HermesHarnessVoucher defaults = new HermesHarnessAssembler().createDefaultProductionVoucher(7L);
        return new HermesHarnessVoucher("synthetic-baseline", 7L, "offline-v1", Instant.EPOCH,
                new InvariantSystemPromptRecord("assistant", "Give  clear   grounded  answers", "Keep safety"),
                new GuardrailsSpecificationRecord(3, 2, 200_000L, 10.0D),
                new MemoryContextStrategyRecord(memoryWindow, 5, 1), defaults.toolGuidance(),
                defaults.subagents(), defaults.skillBank(), defaults.sandboxedExecutionPlan(),
                defaults.mcpMiddlewarePlugins());
    }

    private record Setup(ExperienceStore store, ExperienceGuidedProposer proposer,
                         OfflineRecursivePpc engine) { }
}
