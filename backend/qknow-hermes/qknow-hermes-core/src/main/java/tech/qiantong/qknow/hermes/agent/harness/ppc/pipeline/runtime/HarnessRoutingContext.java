package tech.qiantong.qknow.hermes.agent.harness.ppc.pipeline.runtime;

import java.util.Objects;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.proto.ChatRequest;

/** Request-observable routing inputs; null tag means no trusted classification. */
public record HarnessRoutingContext(String requestId, String domain, CapabilityTag capabilityTag) {
    public static HarnessRoutingContext fromRequest(ChatRequest request, ToolCallbackResolver resolver) {
        Objects.requireNonNull(request, "request");
        boolean rag = request.getRagContextsList().stream().anyMatch(context -> {
            String body = context.getPreRetrievedContent();
            return !body.isBlank() && !"null".equalsIgnoreCase(body.trim());
        });
        boolean tool = resolver != null && request.getToolMethodIdsList().stream()
                .filter(name -> !name.isBlank() && !name.equals("knowledgeQuery")
                        && !name.startsWith("knowledgeBase"))
                .anyMatch(name -> {
                    try {
                        return resolver.resolve(name) != null;
                    } catch (RuntimeException unavailable) {
                        return false;
                    }
                });
        boolean history = request.getHistoryList().stream()
                .anyMatch(message -> !message.getContent().isBlank());
        CapabilityTag tag = rag && tool ? CapabilityTag.COMPOSITE
                : rag ? CapabilityTag.RAG
                : tool ? CapabilityTag.TOOL
                : history ? CapabilityTag.HISTORY : CapabilityTag.PROMPT_ONLY;
        return new HarnessRoutingContext(request.getRequestId(), HarnessProfileRegistry.DEFAULT_DOMAIN, tag);
    }
}
