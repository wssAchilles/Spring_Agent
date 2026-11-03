package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.PipelineMutation;

/** Opt-in runtime selection; SHADOW never changes the executable voucher. */
@Component
public final class PipelineRuntimeBridge {
    private static final int RECEIPT_CAPACITY = 256;
    private final PipelineRuntimeMode mode;
    private final HarnessRouter router;
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
        this(enabled, configuredMode, HarnessRouter.defaults());
    }

    public PipelineRuntimeBridge(boolean enabled, PipelineRuntimeMode configuredMode, HarnessRouter router) {
        this.mode = enabled ? Objects.requireNonNull(configuredMode, "configuredMode") : PipelineRuntimeMode.OFF;
        this.router = Objects.requireNonNull(router, "router");
    }

    public static PipelineRuntimeBridge disabledInstance() {
        return new PipelineRuntimeBridge(false, PipelineRuntimeMode.OFF);
    }

    public boolean isOff() {
        return mode == PipelineRuntimeMode.OFF;
    }

    public Selection prepare(HarnessRoutingContext context, HermesHarnessVoucher baseline,
                             boolean kernelBridgeActive) {
        Objects.requireNonNull(baseline, "baseline");
        if (isOff()) {
            return new Selection(baseline, false, "");
        }
        String baselineFingerprint = baseline.computeFingerprint();
        HarnessRouteDecision decision = null;
        try {
            Objects.requireNonNull(context, "context");
            String requestId = context.requestId();
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId is required");
            }
            decision = router.route(context);
            if (kernelBridgeActive) {
                throw new IllegalStateException("Kernel bridge and pipeline runtime cannot run together");
            }
            HarnessProfile profile = decision.profile();
            if (decision.fallback() || profile.pipeline() == null) {
                store(new Receipt(requestId, mode, profile.profileId(), profile.version(),
                        profile.fingerprint(), "", decision.reason(), decision.fallback(),
                        decision.routeFingerprint(), baselineFingerprint, "", baselineFingerprint,
                        true, false, ""));
                return new Selection(baseline, false, "");
            }
            HermesHarnessVoucher candidate = mutation.apply(baseline, profile.pipeline());
            HermesHarnessVoucher effective = mode == PipelineRuntimeMode.ENFORCE ? candidate : baseline;
            store(new Receipt(requestId, mode, profile.profileId(), profile.version(),
                    profile.fingerprint(), profile.pipeline().fingerprint(), decision.reason(), false,
                    decision.routeFingerprint(), baselineFingerprint, candidate.computeFingerprint(),
                    effective.computeFingerprint(), true, false, ""));
            return new Selection(effective, false, "");
        } catch (RuntimeException failure) {
            String reason = "PIPELINE_PREPARE_FAILED:" + failure.getClass().getSimpleName();
            String requestId = context == null ? null : context.requestId();
            if (requestId != null && !requestId.isBlank()) {
                HarnessProfile profile = decision == null ? null : decision.profile();
                store(new Receipt(requestId, mode,
                        profile == null ? "" : profile.profileId(),
                        profile == null ? "" : profile.version(),
                        profile == null ? "" : profile.fingerprint(),
                        profile == null || profile.pipeline() == null ? "" : profile.pipeline().fingerprint(),
                        decision == null ? "ROUTE_FAILED" : decision.reason(),
                        decision != null && decision.fallback(),
                        decision == null ? "" : decision.routeFingerprint(),
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

    public record Receipt(String requestId, PipelineRuntimeMode mode, String profileId,
                          String profileVersion, String profileFingerprint, String pipelineFingerprint,
                          String routeReason, boolean fallback, String routeFingerprint,
                          String baselineFingerprint, String candidateFingerprint, String executedFingerprint,
                          boolean preparedSuccess,
                          boolean failClosed, String failureCode) {
    }
}
