package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.PipelineMutation;

/** Opt-in runtime selection; SHADOW never changes the executable voucher. */
@Component
public final class PipelineRuntimeBridge {
    private static final int RECEIPT_CAPACITY = 256;
    private final PipelineRuntimeMode mode;
    private final HarnessRouter router;
    private final HarnessSessionBinder binder;
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
        this(enabled, configuredMode, router, new HarnessSessionBinder());
    }

    public PipelineRuntimeBridge(boolean enabled, PipelineRuntimeMode configuredMode,
                                 HarnessRouter router, HarnessSessionBinder binder) {
        this.mode = enabled ? Objects.requireNonNull(configuredMode, "configuredMode") : PipelineRuntimeMode.OFF;
        this.router = Objects.requireNonNull(router, "router");
        this.binder = Objects.requireNonNull(binder, "binder");
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
        HarnessBindingAction action = HarnessBindingAction.NONE;
        HarnessSessionBinding bound = null;
        try {
            Objects.requireNonNull(context, "context");
            if (context.requestId() == null || context.requestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required");
            }
            decision = router.route(context);
            if (kernelBridgeActive) {
                throw new IllegalStateException("Kernel bridge and pipeline runtime cannot run together");
            }
            if (mode == PipelineRuntimeMode.ENFORCE && context.sessionId() != null
                    && !context.sessionId().isBlank() && context.capabilityTag() != null
                    && context.domain() != null && !context.domain().isBlank()) {
                HarnessSessionKey key = new HarnessSessionKey(context.sessionId(),
                        context.capabilityTag(), context.domain());
                if (decision.fallback() && !"PROFILE_DEPRECATED".equals(decision.reason())) {
                    boolean invalidated = binder.invalidate(key);
                    action = HarnessBindingAction.FALLBACK;
                    if (invalidated && "PROFILE_RETIRED".equals(decision.reason())) {
                        decision = router.decisionFor(context, router.baseline(), "RETIRED_FALLBACK", true);
                    }
                } else {
                    HarnessProfile candidate = decision.fallback() ? router.routedProfile(context) : decision.profile();
                    HarnessSessionBinder.Resolution resolution = binder.resolve(key,
                            candidate == null ? router.baseline() : candidate, router::profile);
                    action = resolution.action();
                    bound = resolution.binding();
                    if (resolution.profile() != null && resolution.action() != HarnessBindingAction.FALLBACK) {
                        String reason = action == HarnessBindingAction.PINNED
                                ? decision.reason() : resolution.reason();
                        decision = router.decisionFor(context, resolution.profile(), reason, false);
                    } else {
                        String reason = action == HarnessBindingAction.FALLBACK
                                && resolution.reason().equals("NO_SELECTABLE_PROFILE")
                                ? decision.reason() : resolution.reason();
                        decision = router.decisionFor(context, router.baseline(), reason, true);
                    }
                }
            } else if (mode == PipelineRuntimeMode.ENFORCE && context.sessionId() != null
                    && decision.fallback()) {
                action = HarnessBindingAction.FALLBACK;
            }
            HarnessProfile profile = decision.profile();
            if (decision.fallback() || profile.pipeline() == null) {
                store(receipt(context, decision, action, baselineFingerprint, "", baselineFingerprint,
                        true, false, ""));
                return new Selection(baseline, false, "");
            }
            HermesHarnessVoucher candidate = mutation.apply(baseline, profile.pipeline());
            HermesHarnessVoucher effective = mode == PipelineRuntimeMode.ENFORCE ? candidate : baseline;
            store(receipt(context, decision, action, baselineFingerprint, candidate.computeFingerprint(),
                    effective.computeFingerprint(), true, false, ""));
            return new Selection(effective, false, "");
        } catch (RuntimeException failure) {
            if (bound != null) {
                binder.invalidate(bound);
            }
            String reason = "PIPELINE_PREPARE_FAILED:" + failure.getClass().getSimpleName();
            if (context != null && context.requestId() != null && !context.requestId().isBlank()) {
                store(receipt(context, decision, action, baselineFingerprint,
                        "", baselineFingerprint, false, mode == PipelineRuntimeMode.ENFORCE, reason));
            }
            return new Selection(baseline, mode == PipelineRuntimeMode.ENFORCE, reason);
        }
    }

    private Receipt receipt(HarnessRoutingContext context, HarnessRouteDecision decision,
                            HarnessBindingAction action, String baselineFingerprint,
                            String candidateFingerprint, String executedFingerprint,
                            boolean preparedSuccess, boolean failClosed, String failureCode) {
        HarnessProfile profile = decision == null ? null : decision.profile();
        return new Receipt(context.requestId(), context.sessionId(), mode, context.capabilityTag(), context.domain(),
                profile == null ? "" : profile.profileId(),
                profile == null ? "" : profile.version(),
                profile == null ? "" : profile.fingerprint(),
                profile == null ? null : profile.status(), action,
                profile == null || profile.pipeline() == null ? "" : profile.pipeline().fingerprint(),
                decision == null ? "ROUTE_FAILED" : decision.reason(),
                decision != null && decision.fallback(),
                decision == null ? "" : decision.routeFingerprint(),
                baselineFingerprint, candidateFingerprint, executedFingerprint,
                preparedSuccess, failClosed, failureCode);
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

    public record Receipt(String requestId, String sessionId, PipelineRuntimeMode mode,
                          CapabilityTag capabilityTag, String domain, String profileId,
                          String profileVersion, String profileFingerprint, HarnessProfileStatus profileStatus,
                          HarnessBindingAction bindingAction, String pipelineFingerprint,
                          String routeReason, boolean fallback, String routeFingerprint,
                          String baselineFingerprint, String candidateFingerprint, String executedFingerprint,
                          boolean preparedSuccess, boolean failClosed, String failureCode) {
    }
}
