package tech.qiantong.qknow.hermes.agent.harness.kernel;

import org.springframework.ai.tool.ToolCallback;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.ParetoFrontierComposer;
import tech.qiantong.qknow.hermes.agent.harness.evolution.TrajectoryAttributionAnalyzer;
import tech.qiantong.qknow.hermes.agent.harness.manifold.HypersphericalManifoldProjector;
import tech.qiantong.qknow.hermes.agent.harness.manifold.ReversibleRingColdBuffer;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.HoareTripleToolGuard;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.LocallyDenseCycleAutomaton;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Explicit opt-in facade; constructing it does not change AgentOrchestrator behavior. */
public final class HermesHarnessKernel {
    public record PreparedExecution(HermesHarnessVoucher voucher, String fingerprint, String systemPrompt,
                                    CapabilityContextAssembler.CapabilityContextRecord context,
                                    ReversibleRingColdBuffer.Statistics coldBufferSnapshot) {
        public PreparedExecution(HermesHarnessVoucher voucher, String fingerprint, String systemPrompt,
                                 CapabilityContextAssembler.CapabilityContextRecord context) {
            this(voucher, fingerprint, systemPrompt, context,
                    new ReversibleRingColdBuffer.Statistics(0, 0, 0, 0,
                            ReversibleRingColdBuffer.DEFAULT_MAX_ARCHIVE_CAPACITY, 0, 0, 0));
        }

        public PreparedExecution {
            Objects.requireNonNull(voucher, "voucher");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(systemPrompt, "systemPrompt");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(coldBufferSnapshot, "coldBufferSnapshot");
        }

        public ChatRequest.Builder applyTo(ChatRequest.Builder builder) {
            return context.applyTo(builder).setSystemPrompt(systemPrompt);
        }
    }

    public record GuardedToolOutcome(String toolName, boolean executed, String output,
                                     HoareTripleToolGuard.Decision preDecision,
                                     HoareTripleToolGuard.Decision postDecision,
                                     LocallyDenseCycleAutomaton.Inspection cycleInspection,
                                     int stepIndex) {}

    public record TrajectoryEvaluationResult(
            TrajectoryAttributionAnalyzer.AttributionVector attribution,
            Optional<HermesHarnessVoucher> mutationProposal,
            List<ParetoFrontierComposer.Candidate> paretoFrontier) {}

    private final HypersphericalManifoldProjector projector = new HypersphericalManifoldProjector();
    private final ReversibleRingColdBuffer<String> coldBuffer;
    private final HoareTripleToolGuard toolGuard = new HoareTripleToolGuard();
    private final LocallyDenseCycleAutomaton cycleAutomaton;
    private final TrajectoryAttributionAnalyzer attributionAnalyzer = new TrajectoryAttributionAnalyzer();
    private final AlgebraicHarnessMutator mutator = new AlgebraicHarnessMutator();
    private final ParetoFrontierComposer paretoComposer = new ParetoFrontierComposer();
    private final CapabilityContextAssembler capabilityAssembler = new CapabilityContextAssembler();
    private final HermesHarnessAssembler harnessAssembler = new HermesHarnessAssembler();

    public HermesHarnessKernel(int hotCapacity, int warmCapacity, int coldCapacity,
                               int cycleWindow, int cycleThreshold) {
        coldBuffer = new ReversibleRingColdBuffer<>(hotCapacity, warmCapacity, coldCapacity, projector);
        cycleAutomaton = new LocallyDenseCycleAutomaton(cycleWindow, cycleThreshold);
    }

    public PreparedExecution prepare(HermesHarnessVoucher voucher, CapabilityTag tag,
                                     List<RAGContext> ragContexts, Map<String, ToolCallback> tools,
                                     List<ChatMessage> history) {
        Objects.requireNonNull(voucher, "voucher");
        var context = capabilityAssembler.assemble(tag, ragContexts, tools, history);
        return new PreparedExecution(voucher, voucher.computeFingerprint(),
                harnessAssembler.assembleSystemPrompt(voucher, List.copyOf(context.toolCallbacks().values())),
                context);
    }

    public ExecutionSession openSession(String requestId, HermesHarnessVoucher voucher) {
        return new ExecutionSession(requestId, voucher);
    }

    public final class ExecutionSession {
        private final String requestId;
        private final HermesHarnessVoucher voucher;
        private final ReversibleRingColdBuffer<String> sessionBuffer;
        private final LocallyDenseCycleAutomaton sessionCycles;
        private final List<HarnessEvidenceRecord.ToolExecutionEntry> toolExecutions = new ArrayList<>();
        private int stepIndex;

        private ExecutionSession(String requestId, HermesHarnessVoucher voucher) {
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId is required");
            }
            this.requestId = requestId;
            this.voucher = Objects.requireNonNull(voucher, "voucher");
            int threshold = Math.max(2, voucher.guardrails().loopThreshold());
            sessionCycles = new LocallyDenseCycleAutomaton(
                    Math.max(threshold, voucher.guardrails().maxCycleSteps()), threshold);
            sessionBuffer = new ReversibleRingColdBuffer<>(
                    Math.max(1, voucher.memoryContext().shortTermWindowSize()), 4, 8, projector);
        }

        public String requestId() { return requestId; }
        public ReversibleRingColdBuffer.Statistics coldBufferSnapshot() { return sessionBuffer.statistics(); }
        public LocallyDenseCycleAutomaton.Inspection cycleState() { return sessionCycles.state(); }

        public PreparedExecution prepare(CapabilityTag tag, List<RAGContext> ragContexts,
                                         Map<String, ToolCallback> tools, List<ChatMessage> history) {
            var context = capabilityAssembler.assemble(tag, ragContexts, tools, history);
            int index = 0;
            for (ChatMessage message : context.history()) {
                byte[] bytes = message.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] digest;
                try {
                    digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new IllegalStateException("SHA-256 unavailable", e);
                }
                double[] vector = new double[HypersphericalManifoldProjector.QWEN_EMBEDDING_DIM];
                for (int i = 0; i < digest.length; i++) {
                    vector[i] = (digest[i] & 0xff) - 127.5D;
                }
                sessionBuffer.add(new ReversibleRingColdBuffer.Episode<>(
                        requestId + ":" + index++, message.getContent(), vector));
            }
            return new PreparedExecution(voucher, voucher.computeFingerprint(),
                    harnessAssembler.assembleSystemPrompt(voucher, List.copyOf(context.toolCallbacks().values())),
                    context, sessionBuffer.statistics());
        }

        public synchronized GuardedToolOutcome invokeTool(String toolName, Map<String, Object> arguments,
                                             String canonicalArguments, HoareTripleToolGuard.Contract contract,
                                             long estimatedTokens, double estimatedCostCny,
                                             Supplier<String> rawToolInvoker) {
            Objects.requireNonNull(rawToolInvoker, "rawToolInvoker");
            int currentStep = ++stepIndex;
            var inspection = sessionCycles.inspect(toolName, canonicalArguments);
            var state = new HoareTripleToolGuard.ExecutionState(currentStep, inspection.frequency(),
                    estimatedTokens, estimatedCostCny);
            var before = toolGuard.verifyBefore(toolName, arguments, voucher.guardrails(), contract, state);
            if (inspection.rejected() || !before.allowed()) {
                toolExecutions.add(new HarnessEvidenceRecord.ToolExecutionEntry(toolName, 0L, false, "", ""));
                return new GuardedToolOutcome(toolName, false, "", before, null, inspection, currentStep);
            }
            String output;
            try {
                output = rawToolInvoker.get();
            } catch (RuntimeException failure) {
                toolExecutions.add(new HarnessEvidenceRecord.ToolExecutionEntry(toolName, 0L, false, "", ""));
                throw failure;
            }
            var after = toolGuard.verifyAfter(output, contract);
            toolExecutions.add(new HarnessEvidenceRecord.ToolExecutionEntry(toolName, 0L, after.allowed(), "", ""));
            return new GuardedToolOutcome(toolName, true, after.allowed() ? output : "",
                    before, after, inspection, currentStep);
        }

        public synchronized TrajectoryEvaluationResult evaluateTrajectory(List<ParetoFrontierComposer.Candidate> candidates) {
            return evaluateTrajectory(0, 0, 0, 0, 0.0, candidates);
        }

        public synchronized TrajectoryEvaluationResult evaluateTrajectory(long reasoningTokens, long answerTokens,
                int ragProvided, int ragUsed, double ragScore,
                List<ParetoFrontierComposer.Candidate> candidates) {
            var attribution = attributionAnalyzer.analyze(
                    new TrajectoryAttributionAnalyzer.TrajectoryEvidence(reasoningTokens, answerTokens,
                            ragProvided, ragUsed, ragScore, toolExecutions));
            Optional<HermesHarnessVoucher> proposal = Optional.empty();
            if (attribution.toolErrorRate() > 0.0D) {
                try {
                    proposal = Optional.of(mutator.mutate(voucher,
                            AlgebraicHarnessMutator.Operator.GUARDRAIL_TIGHTENING));
                } catch (IllegalStateException ignored) {
                    // Already at the immutable guardrail lower bound.
                }
            }
            return new TrajectoryEvaluationResult(attribution, proposal,
                    paretoComposer.frontier(candidates == null ? List.of() : candidates));
        }
    }

    public <T> T execute(PreparedExecution prepared, Function<PreparedExecution, T> execution) {
        Objects.requireNonNull(prepared, "prepared");
        return Objects.requireNonNull(execution, "execution").apply(prepared);
    }

    public HypersphericalManifoldProjector projector() { return projector; }
    public ReversibleRingColdBuffer<String> coldBuffer() { return coldBuffer; }
    public HoareTripleToolGuard toolGuard() { return toolGuard; }
    public LocallyDenseCycleAutomaton cycleAutomaton() { return cycleAutomaton; }
    public TrajectoryAttributionAnalyzer attributionAnalyzer() { return attributionAnalyzer; }
    public AlgebraicHarnessMutator mutator() { return mutator; }
    public ParetoFrontierComposer paretoComposer() { return paretoComposer; }
}
