package tech.qiantong.qknow.hermes.agent.harness.kernel;

import org.springframework.ai.tool.ToolCallback;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Validates declared capabilities before adding their payloads to a chat request. */
public final class CapabilityContextAssembler {
    public record CapabilityContextRecord(CapabilityTag tag, List<RAGContext> ragContexts,
                                          Map<String, ToolCallback> toolCallbacks,
                                          List<ChatMessage> history) {
        public CapabilityContextRecord {
            tag = Objects.requireNonNull(tag, "tag");
            ragContexts = List.copyOf(ragContexts);
            toolCallbacks = Collections.unmodifiableMap(new TreeMap<>(toolCallbacks));
            history = List.copyOf(history);
        }

        public ChatRequest.Builder applyTo(ChatRequest.Builder builder) {
            Objects.requireNonNull(builder, "builder");
            return builder.clearRagContexts().addAllRagContexts(ragContexts)
                    .clearToolMethodIds()
                    .addAllToolMethodIds(toolCallbacks.keySet())
                    .clearHistory()
                    .addAllHistory(history);
        }
    }

    public CapabilityContextRecord assemble(CapabilityTag tag, List<RAGContext> ragContexts,
                                            Map<String, ToolCallback> toolCallbacks,
                                            List<ChatMessage> history) {
        Objects.requireNonNull(tag, "tag");
        List<RAGContext> rag = ragContexts == null ? List.of() : ragContexts.stream()
                .filter(context -> context != null && !context.getPreRetrievedContent().isBlank()
                        && !"null".equalsIgnoreCase(context.getPreRetrievedContent().trim()))
                .toList();
        Map<String, ToolCallback> tools = new TreeMap<>();
        if (toolCallbacks != null) {
            toolCallbacks.forEach((name, callback) -> {
                if (name != null && !name.isBlank() && callback != null) {
                    tools.put(name, callback);
                }
            });
        }
        List<ChatMessage> messages = history == null ? List.of() : history.stream()
                .filter(message -> message != null && !message.getContent().isBlank())
                .toList();

        if (tag == CapabilityTag.PROMPT_ONLY
                && (!rag.isEmpty() || !tools.isEmpty() || !messages.isEmpty())) {
            throw new IllegalArgumentException("PROMPT_ONLY cannot carry undeclared capabilities");
        }

        if (tag.requiredContexts().contains(CapabilityTag.ContextKind.RAG) && rag.isEmpty()) {
            throw new IllegalArgumentException("RAG capability requires nonempty retrieved context");
        }
        if (tag.requiredContexts().contains(CapabilityTag.ContextKind.TOOL) && tools.isEmpty()) {
            throw new IllegalArgumentException("TOOL capability requires mounted callbacks");
        }
        if (tag.requiredContexts().contains(CapabilityTag.ContextKind.HISTORY)
                && messages.isEmpty()) {
            throw new IllegalArgumentException("HISTORY capability requires conversation messages");
        }
        return new CapabilityContextRecord(tag, rag, tools, messages);
    }
}
