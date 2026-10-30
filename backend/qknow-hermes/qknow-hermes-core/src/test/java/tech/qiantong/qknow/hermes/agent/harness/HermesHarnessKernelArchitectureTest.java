package tech.qiantong.qknow.hermes.agent.harness;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.ParetoFrontierComposer;
import tech.qiantong.qknow.hermes.agent.harness.evolution.TrajectoryAttributionAnalyzer;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityContextAssembler;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HermesHarnessKernel;
import tech.qiantong.qknow.hermes.agent.harness.manifold.HypersphericalManifoldProjector;
import tech.qiantong.qknow.hermes.agent.harness.manifold.ReversibleRingColdBuffer;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.HoareTripleToolGuard;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.LocallyDenseCycleAutomaton;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HermesHarnessKernelArchitectureTest {
    private static double[] axis(int coordinate, double value) {
        double[] vector = new double[HypersphericalManifoldProjector.QWEN_EMBEDDING_DIM];
        vector[coordinate] = value;
        return vector;
    }

    @Test
    void sphericalGeometryIsNormalizedAndConsistent() {
        HypersphericalManifoldProjector projector = new HypersphericalManifoldProjector();
        double[] unit = projector.normalize(axis(0, 4.0));
        assertEquals(1.0, unit[0], 1.0e-12);
        assertEquals(0.0, projector.cosine(axis(0, 1), axis(1, 1)), 1.0e-12);
        assertEquals(2.0, projector.chordSquared(axis(0, 1), axis(1, 1)), 1.0e-12);
        assertEquals(Math.PI / 2, projector.geodesic(axis(0, 1), axis(1, 1)), 1.0e-12);
        assertEquals(4.0, projector.chordSquared(axis(0, 1), axis(0, -1)), 1.0e-12);
        var centroid = projector.extrinsicCentroid(List.of(axis(0, 1), axis(1, 1)));
        assertEquals(centroid.chordalLoss(),
                projector.chordalCentroid(List.of(axis(0, 1), axis(1, 1))).chordalLoss(), 1.0e-12);
        assertTrue(projector.geodesicDistortionUpperBound(centroid, Math.PI / 2) >= centroid.chordalLoss());
        assertTrue(projector.geodesicDistortionUpperBound(centroid, Math.PI / 2)
                <= Math.PI * Math.PI / 4 * centroid.chordalLoss());
        assertEquals(1.0, Math.hypot(centroid.vector()[0], centroid.vector()[1]), 1.0e-12);
        assertEquals(2.0 * (1.0 - Math.sqrt(0.5)), centroid.chordalLoss(), 1.0e-12);
        assertTrue(centroid.chordalLoss() >= 0.0);
        double[] escaped = centroid.vector();
        escaped[0] = 0.0;
        assertNotEquals(0.0, centroid.vector()[0]);
        assertThrows(IllegalArgumentException.class, () -> projector.normalize(new double[1536]));
        assertThrows(IllegalArgumentException.class, () -> projector.normalize(new double[1024]));
        assertThrows(IllegalArgumentException.class,
                () -> projector.frechetMean(List.of(axis(0, 1), axis(0, -1))));
    }

    @Test
    void coldRingRetainsOverflowAndRestoresByTokenOrGeodesic() {
        var buffer = new ReversibleRingColdBuffer<String>(1, 2, 1, new HypersphericalManifoldProjector());
        assertTrue(buffer.add(new ReversibleRingColdBuffer.Episode<>("a", "first", axis(0, 1))).isEmpty());
        var firstToken = buffer.add(new ReversibleRingColdBuffer.Episode<>("b", "second", axis(1, 1))).orElseThrow();
        var secondToken = buffer.add(new ReversibleRingColdBuffer.Episode<>("c", "third", axis(2, 1))).orElseThrow();
        assertEquals(1, buffer.statistics().cold());
        assertEquals(1, buffer.statistics().archived());
        assertTrue(buffer.softDelete(secondToken));
        assertEquals(2, buffer.statistics().softDeleted());
        assertEquals("first", buffer.restore(firstToken).orElseThrow().payload());
        assertEquals("first", buffer.restoreNearest(axis(0, 1), 0.01).orElseThrow().payload());
        assertEquals("second", buffer.restore(secondToken).orElseThrow().payload());
        assertTrue(buffer.statistics().restored() >= 3);
        assertTrue(buffer.warmCentroid().isPresent());
    }

    @Test
    void coldArchiveCapacityRejectsWithoutLosingRestorableEpisode() {
        var buffer = new ReversibleRingColdBuffer<String>(1, 1, 1, 1,
                new HypersphericalManifoldProjector());
        buffer.add(new ReversibleRingColdBuffer.Episode<>("a", "first", axis(0, 1)));
        var first = buffer.add(new ReversibleRingColdBuffer.Episode<>("b", "second", axis(1, 1))).orElseThrow();
        var second = buffer.add(new ReversibleRingColdBuffer.Episode<>("c", "third", axis(2, 1))).orElseThrow();
        assertThrows(IllegalStateException.class,
                () -> buffer.add(new ReversibleRingColdBuffer.Episode<>("d", "fourth", axis(3, 1))));
        assertEquals(1, buffer.statistics().archived());
        assertFalse(buffer.canArchive());
        assertFalse(buffer.softDelete(second));
        assertEquals("second", buffer.restore(second).orElseThrow().payload());
        assertEquals("first", buffer.restore(first).orElseThrow().payload());
        assertTrue(buffer.statistics().rejectedArchive() >= 1);
    }

    @Test
    void hoareGuardFailsClosedAndReturnsCounterexamples() {
        HoareTripleToolGuard guard = new HoareTripleToolGuard();
        var contract = new HoareTripleToolGuard.Contract(Map.of("amount", Number.class),
                args -> ((Number) args.get("amount")).doubleValue() > 0
                        && ((Number) args.get("amount")).doubleValue() <= 100,
                Set.of("balance_query"), Set.of("status"), Set.of("secret-key"));
        var limits = GuardrailsSpecificationRecord.defaults();
        assertTrue(guard.verifyBefore("balance_query", Map.of("amount", 5), limits, contract).allowed());
        assertEquals(HoareTripleToolGuard.Violation.PERMISSION,
                guard.verifyBefore("write", Map.of("amount", 5), limits, contract).counterexample().violation());
        assertEquals(HoareTripleToolGuard.Violation.SCHEMA,
                guard.verifyBefore("balance_query", Map.of("amount", "five"), limits, contract).counterexample().violation());
        assertEquals(HoareTripleToolGuard.Violation.BOUNDARY,
                guard.verifyBefore("balance_query", Map.of("amount", 200), limits, contract).counterexample().violation());
        assertTrue(guard.verifyAfter(Map.of("status", "ok"), contract).allowed());
        assertEquals(HoareTripleToolGuard.Violation.RESULT_INTEGRITY,
                guard.verifyAfter(Map.of("value", "ok"), contract).counterexample().violation());
        assertEquals(HoareTripleToolGuard.Violation.SENSITIVE_OUTPUT,
                guard.verifyAfter(Map.of("status", "secret-key"), contract).counterexample().violation());
        assertFalse(guard.verifyAfter(null, contract).allowed());
        assertEquals(HoareTripleToolGuard.Violation.MISSING_CONTRACT,
                guard.verifyBefore(" ", Map.of(), limits, contract).counterexample().violation());
        assertEquals(HoareTripleToolGuard.Violation.GUARDRAIL_STEP_LIMIT,
                guard.verifyBefore("balance_query", Map.of("amount", 5), limits, contract,
                        new HoareTripleToolGuard.ExecutionState(11, 1, 0, 0)).counterexample().violation());
        assertEquals(HoareTripleToolGuard.Violation.GUARDRAIL_LOOP_THRESHOLD,
                guard.verifyBefore("balance_query", Map.of("amount", 5), limits, contract,
                        new HoareTripleToolGuard.ExecutionState(1, 3, 0, 0)).counterexample().violation());
        assertEquals(HoareTripleToolGuard.Violation.GUARDRAIL_TOKEN_BUDGET,
                guard.verifyBefore("balance_query", Map.of("amount", 5), limits, contract,
                        new HoareTripleToolGuard.ExecutionState(1, 1, 100_001, 0)).counterexample().violation());
        assertEquals(HoareTripleToolGuard.Violation.GUARDRAIL_COST_BUDGET,
                guard.verifyBefore("balance_query", Map.of("amount", 5), limits, contract,
                        new HoareTripleToolGuard.ExecutionState(1, 1, 0, 0.06)).counterexample().violation());
    }

    @Test
    void strictWindowEvictsBeforeCountingWithoutGhostEntry() {
        var automaton = new LocallyDenseCycleAutomaton(2, 2);
        assertFalse(automaton.inspect("tool", "A").rejected());
        assertFalse(automaton.inspect("tool", "B").rejected());
        var third = automaton.inspect("tool", "A");
        assertFalse(third.rejected());
        assertEquals(1, third.frequency());
        assertEquals(2, third.window().size());
        assertTrue(automaton.inspect("tool", "A").rejected());
        automaton.inspect("tool", "B");
        assertFalse(automaton.inspect("tool", "C").rejected());
        assertFalse(automaton.inspect("tool", "A").rejected());
        assertEquals(2, automaton.state().window().size());
    }

    @Test
    void attributionSeparatesThreeDegradationSignals() {
        var analyzer = new TrajectoryAttributionAnalyzer();
        var failedTool = new HarnessEvidenceRecord.ToolExecutionEntry("tool", 1, false, "in", "out");
        var reasoning = analyzer.analyze(new TrajectoryAttributionAnalyzer.TrajectoryEvidence(20, 5, 0, 0, List.of()));
        var tool = analyzer.analyze(new TrajectoryAttributionAnalyzer.TrajectoryEvidence(0, 5, 0, 0,
                List.of(failedTool)));
        var rag = analyzer.analyze(new TrajectoryAttributionAnalyzer.TrajectoryEvidence(0, 5, 4, 1, List.of()));
        assertEquals(4.0, reasoning.reasoningExpansion());
        assertEquals(1.0, tool.toolErrorRate());
        assertEquals(0.75, rag.ragUnderutilization());
        assertNotEquals(reasoning, tool);
        assertEquals(analyzer.analyze(null), analyzer.analyze(null));
        assertThrows(IllegalArgumentException.class,
                () -> new TrajectoryAttributionAnalyzer.TrajectoryEvidence(0, 0, 1, 2, List.of()));
    }

    @Test
    void everyMutationIsBoundedAndChangesFingerprint() {
        var source = new HermesHarnessAssembler().createDefaultProductionVoucher(42L);
        var mutator = new AlgebraicHarnessMutator();
        for (var operator : AlgebraicHarnessMutator.Operator.values()) {
            var candidate = mutator.mutate(source, operator);
            assertNotSame(source, candidate);
            assertNotEquals(source.computeFingerprint(), candidate.computeFingerprint());
            assertEquals(source.subagents(), candidate.subagents());
            assertEquals(source.sandboxedExecutionPlan(), candidate.sandboxedExecutionPlan());
        }
        var memory = mutator.mutate(source, AlgebraicHarnessMutator.Operator.ADAPTIVE_MEMORY_WINDOW);
        assertEquals(source.memoryContext().shortTermWindowSize() - 1,
                memory.memoryContext().shortTermWindowSize());
        assertThrows(IllegalArgumentException.class,
                () -> new InvariantSystemPromptRecord("role {session}", "", ""));
    }

    @Test
    void paretoFrontierIsStableAndRejectsDominatedCandidate() {
        var composer = new ParetoFrontierComposer();
        var a = new ParetoFrontierComposer.Candidate("a", 0.9, 100, 30);
        var b = new ParetoFrontierComposer.Candidate("b", 0.8, 120, 40);
        var c = new ParetoFrontierComposer.Candidate("c", 0.95, 130, 20);
        assertTrue(composer.dominates(a, b));
        assertFalse(composer.dominates(a, c));
        assertEquals(List.of(a, c), composer.frontier(List.of(c, b, a)));
        assertEquals(composer.frontier(List.of(a, b, c)), composer.frontier(List.of(c, b, a)));
    }

    @Test
    void capabilityAssemblerRejectsMissingPayloadsAndMountsValidOnes() {
        var assembler = new CapabilityContextAssembler();
        assertEquals(CapabilityTag.PROMPT_ONLY,
                assembler.assemble(CapabilityTag.PROMPT_ONLY, null, null, null).tag());
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(CapabilityTag.PROMPT_ONLY,
                        List.of(RAGContext.newBuilder().setPreRetrievedContent("evidence").build()),
                        Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(CapabilityTag.RAG, List.of(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(CapabilityTag.TOOL, List.of(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(CapabilityTag.HISTORY, List.of(), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(CapabilityTag.COMPOSITE, List.of(), Map.of(), List.of()));

        RAGContext rag = RAGContext.newBuilder().setKnowledgeId("kb")
                .setPreRetrievedContent("retrieved evidence").build();
        ToolCallback callback = FunctionToolCallback.builder("lookup", (String input) -> input)
                .inputType(String.class).description("Look up a fact").build();
        ChatMessage history = ChatMessage.newBuilder().setRole("user").setContent("prior question").build();
        var context = assembler.assemble(CapabilityTag.COMPOSITE, List.of(rag), Map.of("lookup", callback),
                List.of(history));
        ChatRequest request = context.applyTo(ChatRequest.newBuilder()).build();
        assertEquals(1, request.getRagContextsCount());
        assertEquals(List.of("lookup"), request.getToolMethodIdsList());
        assertEquals(1, request.getHistoryCount());
    }

    @Test
    void kernelPreparesWithoutExternalExecution() {
        var kernel = new HermesHarnessKernel(2, 2, 2, 3, 2);
        var voucher = new HermesHarnessAssembler().createDefaultProductionVoucher(7L);
        var prepared = kernel.prepare(voucher, CapabilityTag.PROMPT_ONLY, List.of(), Map.of(), List.of());
        assertEquals(voucher.computeFingerprint(), prepared.fingerprint());
        assertTrue(prepared.applyTo(ChatRequest.newBuilder()).build().getSystemPrompt()
                .contains("<security_instructions>"));
        assertEquals(prepared.fingerprint(), kernel.execute(prepared, HermesHarnessKernel.PreparedExecution::fingerprint));
        assertNotNull(kernel.projector());
        assertNotNull(kernel.coldBuffer());
        assertNotNull(kernel.toolGuard());
        assertNotNull(kernel.cycleAutomaton());
        assertNotNull(kernel.attributionAnalyzer());
        assertNotNull(kernel.mutator());
        assertNotNull(kernel.paretoComposer());
        assertThrows(IllegalArgumentException.class,
                () -> kernel.prepare(voucher, CapabilityTag.RAG, List.of(), Map.of(), List.of()));
    }

    @Test
    void kernelSessionOrchestratesComponentsAndIsolatesRequests() {
        var kernel = new HermesHarnessKernel(2, 2, 2, 3, 2);
        var voucher = new HermesHarnessAssembler().createDefaultProductionVoucher(7L);
        var callback = FunctionToolCallback.builder("lookup", (String input) -> input)
                .inputType(String.class).description("Look up a fact").build();
        var contract = new HoareTripleToolGuard.Contract(Map.of(), args -> true,
                Set.of("lookup"), Set.of(), Set.of());
        AtomicInteger calls = new AtomicInteger();
        var first = kernel.openSession("a", voucher);
        var prepared = first.prepare(CapabilityTag.HISTORY, List.of(), Map.of(),
                List.of(ChatMessage.newBuilder().setRole("user").setContent("prior question").build()));
        assertEquals(1, prepared.coldBufferSnapshot().hot());
        assertEquals(CapabilityTag.HISTORY, prepared.context().tag());
        for (int i = 0; i < voucher.guardrails().loopThreshold() - 1; i++) {
            assertTrue(first.invokeTool("lookup", Map.of(), "{}", contract, 1, 0,
                    () -> "result-" + calls.incrementAndGet()).executed());
        }
        var rejected = first.invokeTool("lookup", Map.of(), "{}", contract, 1, 0,
                () -> "result-" + calls.incrementAndGet());
        assertFalse(rejected.executed());
        assertEquals(voucher.guardrails().loopThreshold() - 1, calls.get());
        assertTrue(first.evaluateTrajectory(List.of(new ParetoFrontierComposer.Candidate("base", 0.8, 10, 1)))
                .mutationProposal().isPresent());
        assertEquals(1.0 / voucher.guardrails().loopThreshold(),
                first.evaluateTrajectory(List.of()).attribution().toolErrorRate(), 1.0e-12);

        var second = kernel.openSession("b", voucher);
        second.prepare(CapabilityTag.TOOL, List.of(), Map.of("lookup", callback), List.of());
        var allowed = second.invokeTool("lookup", Map.of(), "{}", contract, 1, 0,
                () -> "result-" + calls.incrementAndGet());
        assertTrue(allowed.executed());
        assertEquals(1, allowed.cycleInspection().frequency());
        assertEquals(0, second.coldBufferSnapshot().hot());

        var sensitiveContract = new HoareTripleToolGuard.Contract(Map.of(), args -> true,
                Set.of("lookup"), Set.of(), Set.of("secret-key"));
        var sensitive = second.invokeTool("lookup", Map.of(), "different", sensitiveContract, 1, 0,
                () -> "secret-key");
        assertTrue(sensitive.executed());
        assertEquals("", sensitive.output());
        assertEquals(HoareTripleToolGuard.Violation.SENSITIVE_OUTPUT,
                sensitive.postDecision().counterexample().violation());
    }
}
