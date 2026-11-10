package tech.qiantong.qknow.hermes.cognitive.core.engine;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveTrace;
import tech.qiantong.qknow.hermes.cognitive.core.verification.CognitiveToolOutcomeEvidence;
import tech.qiantong.qknow.hermes.cognitive.core.verification.CognitiveToolOutcomeRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class CognitiveToolExecutionObserver extends ToolInterceptor {
    private final String stepId;
    private final CognitiveToolOutcomeRegistry outcomeRegistry;
    private final AtomicInteger nextCallIndex = new AtomicInteger();
    private final ConcurrentLinkedQueue<CognitiveTrace.ToolExecutionEvent> events = new ConcurrentLinkedQueue<>();

    public CognitiveToolExecutionObserver(String stepId) {
        this(stepId, CognitiveToolOutcomeRegistry.empty());
    }

    public CognitiveToolExecutionObserver(String stepId, CognitiveToolOutcomeRegistry outcomeRegistry) {
        this.stepId = stepId == null || stepId.isBlank() ? "unknown" : stepId;
        this.outcomeRegistry = outcomeRegistry == null ? CognitiveToolOutcomeRegistry.empty() : outcomeRegistry;
    }

    @Override
    public String getName() {
        return "cognitive-tool-execution-observer";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        int callIndex = nextCallIndex.getAndIncrement();
        String toolName = boundedName(request.getToolName());
        try {
            ToolCallResponse response = handler.call(request);
            boolean responseReceived = response != null && !response.isError();
            CognitiveToolOutcomeEvidence outcome = responseReceived
                    ? outcomeRegistry.evaluate(toolName, request.getArguments(), response.getResult())
                    : CognitiveToolOutcomeEvidence.unknown("TOOL_PROTOCOL_ERROR");
            events.add(new CognitiveTrace.ToolExecutionEvent(callIndex, stepId, toolName,
                    responseReceived ? CognitiveTrace.ToolExecutionEvent.Status.RESPONSE_RECEIVED
                            : CognitiveTrace.ToolExecutionEvent.Status.ERROR,
                    businessOutcome(outcome.status()),
                    response == null ? "TOOL_RESPONSE_NULL" : responseReceived
                            ? outcome.reasonCode() : "TOOL_REPORTED_ERROR",
                    outcome.evidenceDigest()));
            return response;
        } catch (RuntimeException exception) {
            events.add(new CognitiveTrace.ToolExecutionEvent(callIndex, stepId, toolName,
                    CognitiveTrace.ToolExecutionEvent.Status.ERROR,
                    CognitiveTrace.ToolExecutionEvent.BusinessOutcome.UNKNOWN,
                    "TOOL_EXECUTION_EXCEPTION", ""));
            throw exception;
        }
    }

    private CognitiveTrace.ToolExecutionEvent.BusinessOutcome businessOutcome(
            CognitiveToolOutcomeEvidence.Status status) {
        return switch (status) {
            case APPLIED -> CognitiveTrace.ToolExecutionEvent.BusinessOutcome.APPLIED;
            case REJECTED -> CognitiveTrace.ToolExecutionEvent.BusinessOutcome.REJECTED;
            case UNKNOWN -> CognitiveTrace.ToolExecutionEvent.BusinessOutcome.UNKNOWN;
        };
    }

    public List<CognitiveTrace.ToolExecutionEvent> eventsSnapshot() {
        return events.stream().sorted(Comparator.comparingInt(CognitiveTrace.ToolExecutionEvent::callIndex)).toList();
    }

    private static String boundedName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.length() <= 128 ? name : name.substring(0, 128);
    }
}
