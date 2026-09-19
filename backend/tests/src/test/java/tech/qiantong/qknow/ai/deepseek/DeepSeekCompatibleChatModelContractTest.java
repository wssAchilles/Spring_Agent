package tech.qiantong.qknow.ai.deepseek;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.cost.CostEstimator;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeepSeek 官方最新规范契约测试（Flash主干、思考模式与Tool Calling）")
class DeepSeekCompatibleChatModelContractTest {

    private DeepSeekCompatibleChatModel chatModel;
    private CostEstimator costEstimator;

    @BeforeEach
    void setUp() {
        chatModel = new DeepSeekCompatibleChatModel("https://api.deepseek.com", "sk-test", null, 0.7);
        costEstimator = new CostEstimator();
    }

    @Test
    @DisplayName("契约1: 默认模型自动收敛为 deepseek-flash")
    void defaultModelShouldBeDeepSeekFlash() {
        Prompt prompt = new Prompt("你好");
        Map<String, Object> body = chatModel.buildRequestBody(prompt, false);
        assertEquals("deepseek-flash", body.get("model"), "未显式指定模型时，默认必须锁定主干 deepseek-flash");
    }

    @Test
    @DisplayName("契约2: 多轮对话必须回传上一轮 reasoning_content 与 tool_calls（彻底规避 400 报错）")
    void multiRoundToolCallMustIncludeReasoningContent() {
        // 构造用户首轮输入
        UserMessage userMessage = new UserMessage("帮我查询天气并计算穿衣指数");

        // 构造包含思考链与工具调用的 AssistantMessage
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .properties(Map.of(
                        "reasoning_content", "用户要求查询天气与穿衣指数，我需要先调用 getWeather 工具...",
                        "tool_calls", List.of(Map.of("id", "call_123", "type", "function",
                                "function", Map.of("name", "getWeather", "arguments", "{\"city\":\"Beijing\"}")))
                ))
                .build();

        // 构造工具执行返回消息
        Message toolMessage = new Message() {
            @Override
            public String getText() {
                return "{\"temp\":\"15C\",\"weather\":\"sunny\"}";
            }

            @Override
            public MessageType getMessageType() {
                return MessageType.TOOL;
            }

            @Override
            public Map<String, Object> getMetadata() {
                return Map.of("tool_call_id", "call_123");
            }
        };

        Prompt prompt = new Prompt(List.of(userMessage, assistantMessage, toolMessage));
        Map<String, Object> body = chatModel.buildRequestBody(prompt, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertNotNull(messages);
        assertEquals(3, messages.size());

        // 验证第2条 assistant 消息：必须保留 reasoning_content 与 tool_calls
        Map<String, Object> assistantReq = messages.get(1);
        assertEquals("assistant", assistantReq.get("role"));
        assertEquals("用户要求查询天气与穿衣指数，我需要先调用 getWeather 工具...", assistantReq.get("reasoning_content"),
                "官方铁律: 带工具上下文的历史请求必须完整回传 reasoning_content，否则将触发 HTTP 400 校验拒绝");
        assertNotNull(assistantReq.get("tool_calls"));

        // 验证第3条 tool 消息：必须映射为 tool 角色且挂载 tool_call_id
        Map<String, Object> toolReq = messages.get(2);
        assertEquals("tool", toolReq.get("role"), "工具响应角色必须为 tool，严禁错误降级为 user");
        assertEquals("call_123", toolReq.get("tool_call_id"));
    }

    @Test
    @DisplayName("契约3: 思考模式参数化与工具选择能够动态注入请求体")
    void thinkingOptionsShouldInjectIntoRequestBody() {
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model("deepseek-flash")
                .thinkingEnabled(true)
                .reasoningEffort("high")
                .tools(List.of(Map.of("type", "function", "function", Map.of("name", "calculate"))))
                .toolChoice("auto")
                .maxTokens(4096)
                .build();

        Prompt prompt = new Prompt(List.of(new UserMessage("深度证明黎曼猜想")), options);
        Map<String, Object> body = chatModel.buildRequestBody(prompt, true);

        assertEquals("deepseek-flash", body.get("model"));
        assertTrue((Boolean) body.get("stream"));
        assertEquals(4096, body.get("max_tokens"));

        // 验证 thinking 思考开关
        @SuppressWarnings("unchecked")
        Map<String, Object> thinking = (Map<String, Object>) body.get("thinking");
        assertNotNull(thinking);
        assertEquals("enabled", thinking.get("type"), "开启思考模式必须构造 thinking: {type: enabled}");

        // 验证 reasoning_effort 与 tools
        assertEquals("high", body.get("reasoning_effort"));
        assertNotNull(body.get("tools"));
        assertEquals("auto", body.get("tool_choice"));
    }

    @Test
    @DisplayName("契约4: 非流式响应解析完备提取 reasoning_content 与 tool_calls")
    void parseResponseShouldExtractReasoningContent() throws IOException {
        String jsonResponse = """
                {
                  "id": "chatcmpl-test-01",
                  "model": "deepseek-flash",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "北京明天晴天，气温15度。",
                        "reasoning_content": "分析气象数据得出结论：晴朗舒适。",
                        "tool_calls": [
                          {
                            "id": "call_abc",
                            "type": "function",
                            "function": {
                              "name": "notifyUser",
                              "arguments": "{}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 50,
                    "completion_tokens": 120,
                    "total_tokens": 170
                  }
                }
                """;

        ChatResponse response = chatModel.parseResponse(jsonResponse);
        assertNotNull(response);
        assertEquals("北京明天晴天，气温15度。", response.getResult().getOutput().getText());

        // 验证 reasoning_content 被存入 metadata
        Map<String, Object> metadata = response.getResult().getOutput().getMetadata();
        assertNotNull(metadata);
        assertEquals("分析气象数据得出结论：晴朗舒适。", metadata.get("reasoning_content"));
        assertNotNull(metadata.get("tool_calls"));
    }

    @Test
    @DisplayName("契约5: 流式思考片段绝不返回 null（彻底根除流式打字机假死白屏）")
    void parseStreamChunkShouldNotDropReasoningChunks() throws IOException {
        // 模拟模型在思考期间吐出的片段：content 为空，仅有 reasoning_content
        String thinkingChunk = """
                {
                  "choices": [
                    {
                      "index": 0,
                      "delta": {
                        "content": "",
                        "reasoning_content": "正在对题干进行逻辑演绎..."
                      },
                      "finish_reason": ""
                    }
                  ]
                }
                """;

        ChatResponse response = chatModel.parseStreamChunk(thinkingChunk);
        assertNotNull(response, "思考阶段的流式 chunk 绝不能返回 null，否则会被静默丢弃导致前端白屏假死");
        assertEquals("", response.getResult().getOutput().getText());
        assertEquals("正在对题干进行逻辑演绎...", response.getResult().getOutput().getMetadata().get("reasoning_content"));
    }

    @Test
    @DisplayName("契约6: 计费引擎对 deepseek-flash 进行精准计量")
    void costEstimatorShouldSupportDeepSeekFlash() {
        // deepseek-flash 输入 0.5 美元/M，输出 2.0 美元/M
        double cost = costEstimator.estimate("deepseek-flash", 1_000_000, 1_000_000);
        assertEquals(2.5, cost, 0.001, "deepseek-flash 定价应精确匹配: 0.5 + 2.0 = 2.5");
    }
}
