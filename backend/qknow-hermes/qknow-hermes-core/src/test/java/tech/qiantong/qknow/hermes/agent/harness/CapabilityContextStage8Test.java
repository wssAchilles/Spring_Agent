package tech.qiantong.qknow.hermes.agent.harness;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityContextAssembler;
import tech.qiantong.qknow.hermes.agent.harness.kernel.CapabilityTag;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.RAGContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityContextStage8Test {
    private final CapabilityContextAssembler assembler = new CapabilityContextAssembler();

    @Test
    void cleansInvalidPayloadsBeforeValidatingDeclaredCapability() {
        RAGContext blank = RAGContext.newBuilder().setPreRetrievedContent("  ").build();
        RAGContext nullText = RAGContext.newBuilder().setPreRetrievedContent(" null ").build();
        RAGContext valid = RAGContext.newBuilder().setPreRetrievedContent("evidence").build();
        ChatMessage emptyMessage = ChatMessage.newBuilder().setContent("  ").build();
        ChatMessage validMessage = ChatMessage.newBuilder().setRole("user").setContent("earlier").build();
        ToolCallback callback = FunctionToolCallback.builder("lookup", (String input) -> input)
                .inputType(String.class).description("Look up a fact").build();
        Map<String, ToolCallback> tools = new HashMap<>();
        tools.put(null, callback);
        tools.put("  ", callback);
        tools.put("missing", null);
        tools.put("lookup", callback);
        List<RAGContext> ragContexts = new ArrayList<>(List.of(blank, nullText, valid));
        ragContexts.add(null);

        var context = assembler.assemble(CapabilityTag.COMPOSITE,
                ragContexts, tools,
                List.of(emptyMessage, validMessage));
        assertEquals(List.of(valid), context.ragContexts());
        assertEquals(List.of("lookup"), List.copyOf(context.toolCallbacks().keySet()));
        assertEquals(List.of(validMessage), context.history());

        assertEquals(CapabilityTag.PROMPT_ONLY,
                assembler.assemble(CapabilityTag.PROMPT_ONLY, List.of(blank, nullText),
                        Map.of(), List.of(emptyMessage)).tag());
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(CapabilityTag.RAG, List.of(blank, nullText), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(CapabilityTag.TOOL, List.of(), Map.of(" ", callback), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(CapabilityTag.HISTORY, List.of(), Map.of(), List.of(emptyMessage)));
    }

    @Test
    void applyToReplacesExistingFieldsAndIsIdempotent() {
        RAGContext rag = RAGContext.newBuilder().setPreRetrievedContent("new").build();
        ChatMessage history = ChatMessage.newBuilder().setRole("user").setContent("new").build();
        ToolCallback callback = FunctionToolCallback.builder("lookup", (String input) -> input)
                .inputType(String.class).description("Look up a fact").build();
        var context = assembler.assemble(CapabilityTag.COMPOSITE, List.of(rag),
                Map.of("lookup", callback), List.of(history));
        ChatRequest.Builder builder = ChatRequest.newBuilder()
                .addRagContexts(RAGContext.newBuilder().setPreRetrievedContent("old"))
                .addToolMethodIds("old")
                .addHistory(ChatMessage.newBuilder().setContent("old"));

        ChatRequest once = context.applyTo(builder).build();
        ChatRequest twice = context.applyTo(builder).build();
        assertEquals(once, twice);
        assertEquals(List.of(rag), twice.getRagContextsList());
        assertEquals(List.of("lookup"), twice.getToolMethodIdsList());
        assertEquals(List.of(history), twice.getHistoryList());
    }
}
