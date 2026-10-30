package tech.qiantong.qknow.hermes.agent.harness.kernel;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.manifold.ReversibleRingColdBuffer;
import tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.HoareTripleToolGuard;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Request-scoped kernel adapter; OFF never opens a session or changes execution inputs. */
@Component
public final class HarnessKernelBridge {
    private static final int RECEIPT_CAPACITY = 256;
    private final HermesHarnessKernel kernel;
    private final KernelBridgeMode mode;
    private final Map<String, KernelBridgeReceipt> receiptsByRequestId = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, KernelBridgeReceipt> eldest) {
            return size() > RECEIPT_CAPACITY;
        }
    };

    public record KernelBridgeReceipt(String requestId, KernelBridgeMode effectiveMode,
                                      CapabilityTag capabilityTag, boolean preparedSuccess,
                                      boolean failClosed, String errorReason, String voucherFingerprint,
                                      int guardedToolInvocationCount, int blockedToolInvocationCount,
                                      List<String> guardViolations,
                                      ReversibleRingColdBuffer.Statistics coldBufferSnapshot,
                                      Optional<HermesHarnessKernel.TrajectoryEvaluationResult> trajectoryEvaluation) {
        public KernelBridgeReceipt {
            guardViolations = List.copyOf(guardViolations);
            trajectoryEvaluation = trajectoryEvaluation == null ? Optional.empty() : trajectoryEvaluation;
        }
    }

    public record BridgeExecutionHandle(KernelBridgeMode effectiveMode, boolean failClosed,
                                        String failCloseReason, String effectiveSystemPrompt,
                                        List<ToolCallback> effectiveTools,
                                        ToolCallbackResolver effectiveResolver,
                                        HermesHarnessKernel.ExecutionSession session,
                                        CapabilityTag capabilityTag, String voucherFingerprint,
                                        boolean preparedSuccess, Counters counters) {
        public static BridgeExecutionHandle off(String prompt, List<ToolCallback> tools,
                                                ToolCallbackResolver resolver) {
            return new BridgeExecutionHandle(KernelBridgeMode.OFF, false, "", prompt, tools,
                    resolver, null, null, "", false, null);
        }
    }

    private static final class Counters {
        private final AtomicInteger guarded = new AtomicInteger();
        private final AtomicInteger blocked = new AtomicInteger();
        private final List<String> violations = java.util.Collections.synchronizedList(new ArrayList<>());
    }

    @Autowired
    public HarnessKernelBridge(@Value("${hermes.harness.kernel.enabled:false}") boolean enabled,
                               @Value("${hermes.harness.kernel.mode:OFF}") String configuredMode) {
        this(new HermesHarnessKernel(8, 4, 8, 8, 3), new HermesHarnessAssembler(),
                enabled, KernelBridgeMode.resolveEffective(enabled, configuredMode));
    }

    public HarnessKernelBridge(HermesHarnessKernel kernel, HermesHarnessAssembler assembler,
                               boolean enabled, KernelBridgeMode mode) {
        this.kernel = kernel;
        this.mode = enabled && mode != null ? mode : KernelBridgeMode.OFF;
    }

    public static HarnessKernelBridge disabledInstance() {
        return new HarnessKernelBridge(null, null, false, KernelBridgeMode.OFF);
    }

    public boolean isOff() { return mode == KernelBridgeMode.OFF; }

    public BridgeExecutionHandle bridgeExecution(ChatRequest request, HermesHarnessVoucher voucher,
                                                 String systemPrompt, List<RAGContext> ragContexts,
                                                 List<ToolCallback> tools, ToolCallbackResolver resolver,
                                                 List<ChatMessage> history) {
        if (isOff()) {
            return BridgeExecutionHandle.off(systemPrompt, tools, resolver);
        }
        String requestId = request.getRequestId();
        CapabilityTag tag = null;
        HermesHarnessKernel.ExecutionSession session = null;
        String fingerprint = "";
        Counters counters = new Counters();
        try {
            tag = resolveCapabilityTag(request, ragContexts, tools, resolver, history);
            session = kernel.openSession(requestId, voucher);
            Map<String, ToolCallback> mounted = new LinkedHashMap<>();
            if (tools != null) {
                for (ToolCallback tool : tools) {
                    mounted.put(tool.getToolDefinition().name(), tool);
                }
            }
            if (resolver != null) {
                for (String name : request.getToolMethodIdsList()) {
                    ToolCallback resolved = resolver.resolve(name);
                    if (resolved != null) {
                        mounted.putIfAbsent(name, resolved);
                    }
                }
            }
            var prepared = session.prepare(tag, ragContexts, mounted, history);
            fingerprint = prepared.fingerprint();
            if (mode == KernelBridgeMode.SHADOW) {
                var handle = new BridgeExecutionHandle(mode, false, "", systemPrompt, tools,
                        resolver, session, tag, fingerprint, true, counters);
                storeReceipt(requestId, receipt(requestId, handle, Optional.empty()));
                return handle;
            }
            HermesHarnessKernel.ExecutionSession preparedSession = session;
            List<ToolCallback> wrapped = tools == null ? null : tools.stream()
                    .map(tool -> guard(tool, preparedSession, counters)).toList();
            Set<String> allowedTools = Set.copyOf(mounted.keySet());
            ToolCallbackResolver wrappedResolver = resolver == null ? null : name -> {
                if (!allowedTools.contains(name)) {
                    return null;
                }
                ToolCallback original = resolver.resolve(name);
                return original == null ? null : guard(original, preparedSession, counters);
            };
            return new BridgeExecutionHandle(mode, false, "", prepared.systemPrompt(), wrapped,
                    wrappedResolver, session, tag, fingerprint, true, counters);
        } catch (RuntimeException failure) {
            String reason = "CAPABILITY_PRECHECK_FAILED:" + failure.getClass().getSimpleName();
            boolean failClosed = mode == KernelBridgeMode.ENFORCE;
            var handle = new BridgeExecutionHandle(mode, failClosed, reason, systemPrompt, tools,
                    resolver, session, tag, fingerprint, false, counters);
            storeReceipt(requestId, receipt(requestId, handle, Optional.empty()));
            return handle;
        }
    }

    public void completeExecution(String requestId, BridgeExecutionHandle handle, double ragScore) {
        if (handle == null || handle.effectiveMode() == KernelBridgeMode.OFF) {
            return;
        }
        Optional<HermesHarnessKernel.TrajectoryEvaluationResult> evaluation = Optional.empty();
        try {
            if (handle.session() != null && handle.preparedSuccess()) {
                evaluation = Optional.of(handle.session().evaluateTrajectory(List.of()));
            }
        } catch (RuntimeException failure) {
            handle.counters().violations.add("TRAJECTORY:" + failure.getClass().getSimpleName());
        } finally {
            storeReceipt(requestId, receipt(requestId, handle, evaluation));
        }
    }

    public Optional<KernelBridgeReceipt> getLatestReceipt(String requestId) {
        synchronized (receiptsByRequestId) {
            return Optional.ofNullable(receiptsByRequestId.get(requestId));
        }
    }

    public CapabilityTag resolveCapabilityTag(ChatRequest request, List<RAGContext> ragContexts,
                                              List<ToolCallback> tools, ToolCallbackResolver resolver,
                                              List<ChatMessage> history) {
        for (String json : List.of(request.getInputParams(), request.getParameters())) {
            if (!json.isBlank()) {
                JSONObject object = JSONObject.parseObject(json);
                String declared = object.getString("harness.capabilityTag");
                if (declared == null) {
                    declared = object.getString("capabilityTag");
                }
                if (declared != null) {
                    return CapabilityTag.valueOf(declared.trim().toUpperCase(java.util.Locale.ROOT));
                }
            }
        }
        boolean rag = ragContexts != null && !ragContexts.isEmpty();
        boolean mountedTools = tools != null && !tools.isEmpty()
                || resolver != null && !request.getToolMethodIdsList().isEmpty();
        boolean hasHistory = history != null && !history.isEmpty();
        if (rag && mountedTools) return CapabilityTag.COMPOSITE;
        if (rag) return CapabilityTag.RAG;
        if (mountedTools) return CapabilityTag.TOOL;
        if (hasHistory) return CapabilityTag.HISTORY;
        return CapabilityTag.PROMPT_ONLY;
    }

    public List<ChatMessage> resolveEffectiveHistory(ChatRequest request, List<Message> memoryMessages) {
        if (!request.getHistoryList().isEmpty()) {
            return request.getHistoryList();
        }
        if (memoryMessages == null || memoryMessages.isEmpty()) {
            return List.of();
        }
        return memoryMessages.stream().map(message -> ChatMessage.newBuilder()
                .setRole(message.getMessageType().name().toLowerCase(java.util.Locale.ROOT))
                .setContent(message.getText() == null ? "" : message.getText()).build()).toList();
    }

    private ToolCallback guard(ToolCallback original, HermesHarnessKernel.ExecutionSession session,
                               Counters counters) {
        HoareTripleToolGuard.Contract contract = contractFor(original.getToolDefinition());
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() { return original.getToolDefinition(); }

            @Override
            public String call(String toolInput) {
                counters.guarded.incrementAndGet();
                String name = getToolDefinition().name();
                Map<String, Object> arguments;
                try {
                    JSONObject parsed = JSONObject.parseObject(toolInput);
                    arguments = parsed == null ? Map.of() : parsed;
                } catch (RuntimeException invalid) {
                    arguments = null;
                }
                HoareTripleToolGuard.ExecutionState estimates;
                try {
                    estimates = arguments == null ? HoareTripleToolGuard.ExecutionState.initial()
                            : HoareTripleToolGuard.ExecutionState.fromArguments(arguments);
                } catch (RuntimeException invalidEstimate) {
                    counters.blocked.incrementAndGet();
                    counters.violations.add("SCHEMA");
                    return "[HARNESS_GUARD_REJECTED:SCHEMA]";
                }
                var outcome = session.invokeTool(name, arguments, toolInput, contract,
                        estimates.estimatedTokens(), estimates.estimatedCostCny(),
                        () -> original.call(toolInput));
                if (!outcome.executed() || outcome.postDecision() != null && !outcome.postDecision().allowed()) {
                    counters.blocked.incrementAndGet();
                    String violation = outcome.cycleInspection().rejected() ? "CYCLE" :
                            !outcome.preDecision().allowed() ? outcome.preDecision().counterexample().violation().name() :
                                    outcome.postDecision().counterexample().violation().name();
                    counters.violations.add(violation);
                    return "[HARNESS_GUARD_REJECTED:" + violation + "]";
                }
                return outcome.output();
            }
        };
    }

    private HoareTripleToolGuard.Contract contractFor(ToolDefinition definition) {
        if (definition == null || definition.inputSchema() == null) {
            return null;
        }
        try {
            JSONObject schema = JSONObject.parseObject(definition.inputSchema());
            if (schema == null || !"object".equals(schema.getString("type"))) {
                return null;
            }
            JSONObject properties = schema.getJSONObject("properties");
            JSONArray required = schema.getJSONArray("required");
            Map<String, Class<?>> fields = new HashMap<>();
            Map<String, String> propertyTypes = new HashMap<>();
            if (properties != null) {
                for (String field : properties.keySet()) {
                    JSONObject property = properties.getJSONObject(field);
                    String type = property == null ? null : property.getString("type");
                    if (type == null || !Set.of("string", "integer", "number", "boolean", "object", "array")
                            .contains(type)) return null;
                    propertyTypes.put(field, type);
                }
            }
            if (required != null) {
                for (Object item : required) {
                    String field = (String) item;
                    String type = propertyTypes.get(field);
                    Class<?> javaType = switch (type == null ? "" : type) {
                        case "string" -> String.class;
                        case "integer", "number" -> Number.class;
                        case "boolean" -> Boolean.class;
                        case "object" -> Map.class;
                        case "array" -> List.class;
                        default -> null;
                    };
                    if (javaType == null) return null;
                    fields.put(field, javaType);
                }
            }
            boolean allowExtra = !Boolean.FALSE.equals(schema.getBoolean("additionalProperties"));
            return new HoareTripleToolGuard.Contract(fields, args -> args.entrySet().stream().allMatch(entry -> {
                String type = propertyTypes.get(entry.getKey());
                if (type == null) return allowExtra;
                Object value = entry.getValue();
                return switch (type) {
                    case "string" -> value instanceof String;
                    case "integer" -> value instanceof Number number
                            && Double.isFinite(number.doubleValue())
                            && number.doubleValue() == Math.rint(number.doubleValue());
                    case "number" -> value instanceof Number number && Double.isFinite(number.doubleValue());
                    case "boolean" -> value instanceof Boolean;
                    case "object" -> value instanceof Map;
                    case "array" -> value instanceof List;
                    default -> false;
                };
            }),
                    Set.of(definition.name()), Set.of(), Set.of());
        } catch (RuntimeException invalidSchema) {
            return null;
        }
    }

    private KernelBridgeReceipt receipt(String requestId, BridgeExecutionHandle handle,
                                        Optional<HermesHarnessKernel.TrajectoryEvaluationResult> evaluation) {
        Counters counters = handle.counters();
        List<String> violations;
        synchronized (counters.violations) {
            violations = List.copyOf(counters.violations);
        }
        return new KernelBridgeReceipt(requestId, handle.effectiveMode(), handle.capabilityTag(),
                handle.preparedSuccess(), handle.failClosed(), handle.failCloseReason(),
                handle.voucherFingerprint(), counters.guarded.get(), counters.blocked.get(),
                violations, handle.session() == null ? null :
                handle.session().coldBufferSnapshot(), evaluation);
    }

    private void storeReceipt(String requestId, KernelBridgeReceipt receipt) {
        synchronized (receiptsByRequestId) {
            receiptsByRequestId.put(requestId, receipt);
        }
    }
}
