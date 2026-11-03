package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.BoundedOperatorPipeline;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.PipelineMutation;

/** Opt-in runtime selection; SHADOW never changes the executable voucher. */
@Component
public final class PipelineRuntimeBridge {
    private static final int RECEIPT_CAPACITY = 256;
    private static final BoundedOperatorPipeline REGISTERED_PIPELINE = new BoundedOperatorPipeline(
            List.of(Operator.CONCISE_SYSTEM_PROMPT, Operator.ADAPTIVE_MEMORY_WINDOW));

    private final PipelineRuntimeMode mode;
    private final PipelineMutation mutation = new PipelineMutation();
    private final Map<String, Receipt> receipts = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Receipt> eldest) {
            return size() > RECEIPT_CAPACITY;
        }
    };

    @Autowired
    public PipelineRuntimeBridge(@Value("${hermes.harness.pipeline.enabled:false}") boolean enabled,
                                 @Value("${hermes.harness.pipeline.mode:OFF}") String configuredMode) {
        this(enabled, PipelineRuntimeMode.resolveEffective(enabled, configuredMode));
    }

    public PipelineRuntimeBridge(boolean enabled, PipelineRuntimeMode configuredMode) {
        this.mode = enabled ? Objects.requireNonNull(configuredMode, "configuredMode") : PipelineRuntimeMode.OFF;
    }

    public static PipelineRuntimeBridge disabledInstance() {
        return new PipelineRuntimeBridge(false, PipelineRuntimeMode.OFF);
    }

    public boolean isOff() {
        return mode == PipelineRuntimeMode.OFF;
    }

    public Selection prepare(String requestId, HermesHarnessVoucher baseline, boolean kernelBridgeActive) {
        Objects.requireNonNull(baseline, "baseline");
        if (isOff()) {
            return new Selection(baseline, false, "");
        }
        String baselineFingerprint = baseline.computeFingerprint();
        try {
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId is required");
            }
            if (kernelBridgeActive) {
                throw new IllegalStateException("Kernel bridge and pipeline runtime cannot run together");
            }
            HermesHarnessVoucher candidate = mutation.apply(baseline, REGISTERED_PIPELINE);
            HermesHarnessVoucher effective = mode == PipelineRuntimeMode.ENFORCE ? candidate : baseline;
            store(new Receipt(requestId, mode, REGISTERED_PIPELINE.fingerprint(),
                    baselineFingerprint, candidate.computeFingerprint(), effective.computeFingerprint(),
                    true, false, ""));
            return new Selection(effective, false, "");
        } catch (RuntimeException failure) {
            String reason = "PIPELINE_PREPARE_FAILED:" + failure.getClass().getSimpleName();
            if (requestId != null && !requestId.isBlank()) {
                store(new Receipt(requestId, mode, REGISTERED_PIPELINE.fingerprint(),
                        baselineFingerprint, "", baselineFingerprint, false,
                        mode == PipelineRuntimeMode.ENFORCE, reason));
            }
            return new Selection(baseline, mode == PipelineRuntimeMode.ENFORCE, reason);
        }
    }

    public Optional<Receipt> getLatestReceipt(String requestId) {
        synchronized (receipts) {
            return Optional.ofNullable(receipts.get(requestId));
        }
    }

    private void store(Receipt receipt) {
        synchronized (receipts) {
            receipts.put(receipt.requestId(), receipt);
        }
    }

    public record Selection(HermesHarnessVoucher effectiveVoucher, boolean failClosed, String failureCode) {
        public Selection {
            Objects.requireNonNull(effectiveVoucher, "effectiveVoucher");
            failureCode = failureCode == null ? "" : failureCode;
        }
    }

    public record Receipt(String requestId, PipelineRuntimeMode mode, String pipelineFingerprint,
                          String baselineFingerprint, String candidateFingerprint,
                          String executedFingerprint, boolean preparedSuccess,
                          boolean failClosed, String failureCode) {
    }
}
