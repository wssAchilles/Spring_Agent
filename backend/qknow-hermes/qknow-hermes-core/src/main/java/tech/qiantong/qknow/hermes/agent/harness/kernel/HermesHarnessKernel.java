package tech.qiantong.qknow.hermes.agent.harness.kernel;

import org.springframework.ai.tool.ToolCallback;
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
import java.util.function.Function;

/** Explicit opt-in facade; constructing it does not change AgentOrchestrator behavior. */
public final class HermesHarnessKernel {
    public record PreparedExecution(HermesHarnessVoucher voucher, String fingerprint, String systemPrompt,
                                    CapabilityContextAssembler.CapabilityContextRecord context) {
        public PreparedExecution {
            Objects.requireNonNull(voucher, "voucher");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(systemPrompt, "systemPrompt");
            Objects.requireNonNull(context, "context");
        }

        public ChatRequest.Builder applyTo(ChatRequest.Builder builder) {
            return context.applyTo(builder).setSystemPrompt(systemPrompt);
        }
    }

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
