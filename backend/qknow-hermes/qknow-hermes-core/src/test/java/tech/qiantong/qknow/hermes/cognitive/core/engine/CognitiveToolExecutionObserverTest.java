package tech.qiantong.qknow.hermes.cognitive.core.engine;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.cognitive.core.model.CognitiveTrace;
import tech.qiantong.qknow.hermes.cognitive.core.verification.CognitiveToolOutcomeEvidence;
import tech.qiantong.qknow.hermes.cognitive.core.verification.CognitiveToolOutcomeProvider;
import tech.qiantong.qknow.hermes.cognitive.core.verification.CognitiveToolOutcomeRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CognitiveToolExecutionObserverTest {
    @Test
    void capturesRedactedDeterministicBusinessOutcomeFromRegisteredProvider() {
        CognitiveToolOutcomeEvidence expected = CognitiveToolOutcomeEvidence.fromCanonicalState(
                CognitiveToolOutcomeEvidence.Status.APPLIED, "ORDER_CANCELLED", "order-1:cancelled");
        CognitiveToolOutcomeRegistry registry = new CognitiveToolOutcomeRegistry(List.of(
                new CognitiveToolOutcomeProvider() {
                    @Override
                    public String toolName() { return "cancel_pending_order"; }

                    @Override
                    public CognitiveToolOutcomeEvidence evaluate(String argumentsJson, String result) {
                        assertEquals("{\"orderId\":\"order-1\"}", argumentsJson);
                        assertEquals("{\"status\":\"cancelled\"}", result);
                        return expected;
                    }
                }));
        CognitiveToolExecutionObserver observer = new CognitiveToolExecutionObserver("s1", registry);
        ToolCallRequest request = mock(ToolCallRequest.class);
        when(request.getToolName()).thenReturn("cancel_pending_order");
        when(request.getArguments()).thenReturn("{\"orderId\":\"order-1\"}");
        ToolCallResponse response = mock(ToolCallResponse.class);
        when(response.isError()).thenReturn(false);
        when(response.getResult()).thenReturn("{\"status\":\"cancelled\"}");

        observer.interceptToolCall(request, ignored -> response);

        CognitiveTrace.ToolExecutionEvent event = observer.eventsSnapshot().getFirst();
        assertEquals(CognitiveTrace.ToolExecutionEvent.BusinessOutcome.APPLIED, event.businessOutcome());
        assertEquals("ORDER_CANCELLED", event.reasonCode());
        assertEquals(expected.evidenceDigest(), event.outcomeEvidenceDigest());
        org.junit.jupiter.api.Assertions.assertFalse(event.toString().contains("order-1"));
        org.junit.jupiter.api.Assertions.assertFalse(event.toString().contains("cancelled\"}"));
    }

    @Test
    void recordsExecutionResultWithoutPayloadOrExceptionMessage() {
        CognitiveToolExecutionObserver observer = new CognitiveToolExecutionObserver("s1");
        ToolCallRequest request = mock(ToolCallRequest.class);
        when(request.getToolName()).thenReturn("cancel_pending_order");
        ToolCallResponse response = mock(ToolCallResponse.class);
        when(response.isError()).thenReturn(false);
        ToolCallResponse errorResponse = mock(ToolCallResponse.class);
        when(errorResponse.isError()).thenReturn(true);

        observer.interceptToolCall(request, ignored -> response);
        observer.interceptToolCall(request, ignored -> errorResponse);
        assertThrows(IllegalStateException.class,
                () -> observer.interceptToolCall(request, ignored -> {
                    throw new IllegalStateException("private arguments and response");
                }));

        assertEquals(3, observer.eventsSnapshot().size());
        assertEquals(CognitiveTrace.ToolExecutionEvent.Status.RESPONSE_RECEIVED,
                observer.eventsSnapshot().getFirst().status());
        assertEquals("s1", observer.eventsSnapshot().getFirst().stepId());
        assertEquals("cancel_pending_order", observer.eventsSnapshot().getFirst().toolName());
        assertEquals(CognitiveTrace.ToolExecutionEvent.Status.ERROR,
                observer.eventsSnapshot().get(1).status());
        assertEquals("TOOL_REPORTED_ERROR", observer.eventsSnapshot().get(1).reasonCode());
        assertEquals(CognitiveTrace.ToolExecutionEvent.Status.ERROR,
                observer.eventsSnapshot().get(2).status());
        assertEquals("TOOL_EXECUTION_EXCEPTION", observer.eventsSnapshot().get(2).reasonCode());
        assertEquals(2, observer.eventsSnapshot().get(2).callIndex());
    }
}
