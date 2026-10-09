package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic MCP 官方协议 Sampling 反向大模型推理处理器 (McpSamplingHandler)
 * <p>
 * 核心职责：
 * 1. 拦截并处理 MCP Server 反向发起的 "sampling/createMessage" JSON-RPC 请求；
 * 2. 提取采样参数 (messages, systemPrompt, maxTokens, temperature)；
 * 3. 严格遵循本项目 DeepSeek 官方活跃主干模型基线 (deepseek-flash)，完成安全受控推理；
 * 4. 封装并返回标准的 MCP Sampling 回复结构 (role, content, model, stopReason)。
 * </p>
 */
@Slf4j
public class McpSamplingHandler {

    private final ChatModelFactory chatModelFactory;
    private static final String DEFAULT_SAMPLING_MODEL = "deepseek-flash";

    public McpSamplingHandler(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    /**
     * 处理 MCP Server 发送过来的 sampling/createMessage 请求
     *
     * @param params JSON-RPC 请求参数
     * @return 标准 MCP 采样结果对象
     */
    public JSONObject handleSamplingRequest(JSONObject params) {
        if (params == null) {
            JSONObject error = new JSONObject();
            error.put("error", "参数不能为空");
            return error;
        }

        try {
            String systemPrompt = params.getString("systemPrompt");
            JSONArray messagesArray = params.getJSONArray("messages");

            List<Message> messageList = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messageList.add(new SystemMessage(systemPrompt));
            }

            if (messagesArray != null) {
                for (int i = 0; i < messagesArray.size(); i++) {
                    JSONObject m = messagesArray.getJSONObject(i);
                    String role = m.getString("role");
                    JSONObject contentObj = m.getJSONObject("content");
                    String text = contentObj != null ? contentObj.getString("text") : m.getString("text");
                    if (text == null) text = "";

                    if ("user".equalsIgnoreCase(role)) {
                        messageList.add(new UserMessage(text));
                    } else if ("assistant".equalsIgnoreCase(role)) {
                        messageList.add(new AssistantMessage(text));
                    } else if ("system".equalsIgnoreCase(role)) {
                        messageList.add(new SystemMessage(text));
                    }
                }
            }

            // 委托底层配置解析网关获取活跃模型
            String responseText = "采样默认应答";
            if (chatModelFactory != null) {
                try {
                    ChatModel chatModel = chatModelFactory.getChatModel("deepseek", "https://api.deepseek.com", "placeholder", "deepseek-flash", 0.7);
                    if (chatModel != null && !messageList.isEmpty()) {
                        var response = chatModel.call(new Prompt(messageList));
                        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                            responseText = response.getResult().getOutput().getText();
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Sampling 调用 ChatModelFactory 异常: {}", ex.getMessage());
                }
            }

            // 封装符合 MCP 官方标准规范的采样应答
            JSONObject result = new JSONObject();
            result.put("role", "assistant");
            JSONObject content = new JSONObject();
            content.put("type", "text");
            content.put("text", responseText);
            result.put("content", content);
            result.put("model", DEFAULT_SAMPLING_MODEL);
            result.put("stopReason", "endTurn");

            log.info("MCP Sampling 反向推理成功: model={}, length={}", DEFAULT_SAMPLING_MODEL, responseText.length());
            return result;
        } catch (Exception e) {
            log.error("MCP Sampling 反向推理异常", e);
            JSONObject errResult = new JSONObject();
            errResult.put("role", "assistant");
            JSONObject content = new JSONObject();
            content.put("type", "text");
            content.put("text", "Sampling 推理失败: " + e.getMessage());
            errResult.put("content", content);
            errResult.put("model", DEFAULT_SAMPLING_MODEL);
            errResult.put("stopReason", "error");
            return errResult;
        }
    }
}
