package tech.qiantong.qknow.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-07 回归防护：流式工具调用的空参数必须统一为合法 JSON {@code "{}"}，
 * 且名称未到达的中间态不得被当作可执行工具调用。
 */
class StreamToolCallArgumentsContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("无参函数的空 arguments 在 ToolCall 与 raw 列表中统一为 {}")
    void emptyArgumentsNormalizedToJsonObject() throws Exception {
        DeepSeekCompatibleChatModel.StreamToolCallAccumulator accumulator =
                new DeepSeekCompatibleChatModel.StreamToolCallAccumulator();

        JsonNode delta = objectMapper.readTree("""
                {"tool_calls":[
                  {"index":0,"id":"call_empty","type":"function",
                   "function":{"name":"listFiles","arguments":""}}
                ]}
                """);
        accumulator.accumulate(delta);

        List<AssistantMessage.ToolCall> toolCalls = accumulator.getCurrentToolCalls();
        assertEquals(1, toolCalls.size());
        assertEquals("{}", toolCalls.get(0).arguments());

        List<Map<String, Object>> rawCalls = accumulator.toRawToolCallsList();
        assertEquals(1, rawCalls.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) rawCalls.get(0).get("function");
        assertEquals("{}", function.get("arguments"), "raw tool_calls 的空参数必须与 ToolCall 语义一致");
    }

    @Test
    @DisplayName("名称未到达的中间态不产生可执行工具调用")
    void incompleteToolCallNotExecutable() throws Exception {
        DeepSeekCompatibleChatModel.StreamToolCallAccumulator accumulator =
                new DeepSeekCompatibleChatModel.StreamToolCallAccumulator();

        accumulator.accumulate(objectMapper.readTree("""
                {"tool_calls":[{"index":0,"function":{"arguments":"{\\"q\\":"}}]}
                """));

        assertFalse(accumulator.hasToolCalls());
        assertTrue(accumulator.getCurrentToolCalls().isEmpty());
        assertTrue(accumulator.toRawToolCallsList().isEmpty());
    }
}
