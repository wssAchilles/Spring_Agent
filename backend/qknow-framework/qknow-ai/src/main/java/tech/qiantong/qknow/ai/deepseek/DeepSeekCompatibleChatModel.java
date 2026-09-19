package tech.qiantong.qknow.ai.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * DeepSeek 官方兼容模型客户端。
 * 遵循官方最新规范：默认使用 deepseek-flash 主干模型，
 * 支持动态 thinking 思考启闭、reasoning_content 保持与回传、原生工具调用以及双轨流式输出。
 */
public class DeepSeekCompatibleChatModel implements ChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_MODEL_NAME = "deepseek-flash";

    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final String modelName;
    private final Double temperature;

    public DeepSeekCompatibleChatModel(String baseUrl, String apiKey, String modelName, Double temperature) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.endpoint = normalizeBaseUrl(baseUrl) + "/chat/completions";
        this.apiKey = apiKey;
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName : DEFAULT_MODEL_NAME;
        this.temperature = temperature;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            Map<String, Object> body = buildRequestBody(prompt, false);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek 调用失败: HTTP " + response.statusCode()
                        + ", body=" + preview(response.body()));
            }
            return parseResponse(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("DeepSeek 响应解析失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DeepSeek 调用被中断", e);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.create(sink -> streamResponse(prompt, sink));
    }

    private void streamResponse(Prompt prompt, FluxSink<ChatResponse> sink) {
        try {
            Map<String, Object> body = buildRequestBody(prompt, true);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                sink.error(new IllegalStateException("DeepSeek 流式调用失败: HTTP " + response.statusCode()));
                return;
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> emitStreamLine(line, sink));
            }
            sink.complete();
        } catch (IOException e) {
            sink.error(new IllegalStateException("DeepSeek 流式响应解析失败", e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.error(new IllegalStateException("DeepSeek 流式调用被中断", e));
        }
    }

    /**
     * 构建发送给 DeepSeek 官方 API 的请求体。
     * 关键防御：在多轮对话中完整回传历史 AssistantMessage 的 reasoning_content 与 tool_calls，
     * 彻底避免工具调用上下文中的 HTTP 400 Bad Request 报错。
     */
    public Map<String, Object> buildRequestBody(Prompt prompt, boolean stream) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (prompt != null && prompt.getInstructions() != null) {
            for (Message message : prompt.getInstructions()) {
                Map<String, Object> msgMap = new LinkedHashMap<>();
                String role = toRole(message.getMessageType());
                msgMap.put("role", role);
                msgMap.put("content", message.getText() == null ? "" : message.getText());

                // 回传 AssistantMessage 中的思考链与工具调用元数据
                if (message instanceof AssistantMessage assistantMessage) {
                    Map<String, Object> metadata = assistantMessage.getMetadata();
                    if (metadata != null) {
                        Object reasoning = metadata.get("reasoning_content");
                        if (reasoning != null && !reasoning.toString().isEmpty()) {
                            msgMap.put("reasoning_content", reasoning.toString());
                        }
                        Object toolCalls = metadata.get("tool_calls");
                        if (toolCalls != null) {
                            msgMap.put("tool_calls", toolCalls);
                        }
                    }
                }

                // 回传 Tool 消息的 tool_call_id
                if (MessageType.TOOL.equals(message.getMessageType())) {
                    Map<String, Object> metadata = message.getMetadata();
                    if (metadata != null && metadata.containsKey("tool_call_id")) {
                        msgMap.put("tool_call_id", metadata.get("tool_call_id"));
                    }
                }

                messages.add(msgMap);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        String targetModel = (this.modelName != null && !this.modelName.isBlank()) ? this.modelName : DEFAULT_MODEL_NAME;
        Double targetTemperature = this.temperature;

        // 从 Prompt Options 中提取运行时覆盖配置与思考控制
        if (prompt != null && prompt.getOptions() != null) {
            ChatOptions options = prompt.getOptions();
            if (options.getModel() != null && !options.getModel().isBlank()) {
                targetModel = options.getModel();
            }
            if (options.getTemperature() != null) {
                targetTemperature = options.getTemperature();
            }
            if (options instanceof DeepSeekChatOptions dsOptions) {
                if (dsOptions.getThinkingEnabled() != null) {
                    body.put("thinking", Map.of("type", dsOptions.getThinkingEnabled() ? "enabled" : "disabled"));
                }
                if (dsOptions.getReasoningEffort() != null && !dsOptions.getReasoningEffort().isBlank()) {
                    body.put("reasoning_effort", dsOptions.getReasoningEffort());
                }
                if (dsOptions.getTools() != null && !dsOptions.getTools().isEmpty()) {
                    body.put("tools", dsOptions.getTools());
                }
                if (dsOptions.getToolChoice() != null) {
                    body.put("tool_choice", dsOptions.getToolChoice());
                }
                if (dsOptions.getMaxTokens() != null) {
                    body.put("max_tokens", dsOptions.getMaxTokens());
                }
            }
        }

        body.put("model", targetModel);
        body.put("messages", messages);
        body.put("stream", stream);
        if (targetTemperature != null) {
            body.put("temperature", targetTemperature);
        }
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        }
        return body;
    }

    /**
     * 解析非流式响应，同时提取 content 与 reasoning_content。
     */
    public ChatResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        JsonNode choice = root.path("choices").path(0);
        JsonNode messageNode = choice.path("message");

        String content = messageNode.path("content").asText("");
        String finishReason = choice.path("finish_reason").asText("");
        String reasoningContent = messageNode.path("reasoning_content").asText("");

        Map<String, Object> metadataMap = new LinkedHashMap<>();
        if (!reasoningContent.isEmpty()) {
            metadataMap.put("reasoning_content", reasoningContent);
        }
        if (messageNode.has("tool_calls")) {
            metadataMap.put("tool_calls", OBJECT_MAPPER.convertValue(messageNode.get("tool_calls"), List.class));
        }

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(content)
                .properties(metadataMap)
                .build();

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .metadata(metadataMap)
                .build();

        Generation generation = new Generation(assistantMessage, generationMetadata);

        JsonNode usageNode = root.path("usage");
        DefaultUsage usage = new DefaultUsage(
                usageNode.path("prompt_tokens").isMissingNode() ? null : usageNode.path("prompt_tokens").asInt(),
                usageNode.path("completion_tokens").isMissingNode() ? null : usageNode.path("completion_tokens").asInt(),
                usageNode.path("total_tokens").isMissingNode() ? null : usageNode.path("total_tokens").asInt(),
                usageNode.isMissingNode() ? null : usageNode
        );
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id(root.path("id").asText(""))
                .model(root.path("model").asText(modelName))
                .usage(usage)
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    private void emitStreamLine(String line, FluxSink<ChatResponse> sink) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if ("[DONE]".equals(payload)) {
            return;
        }
        try {
            ChatResponse response = parseStreamChunk(payload);
            if (response != null) {
                sink.next(response);
            }
        } catch (IOException e) {
            sink.error(new IllegalStateException("DeepSeek 流式响应片段解析失败", e));
        }
    }

    /**
     * 解析流式片段。
     * 关键防御：当模型在思考阶段（content 为空但 reasoning_content 持续输出）时，
     * 绝不返回 null，确保思考流数据包能够实时到达前端，根除流式打字机 10~30 秒白屏假死。
     */
    public ChatResponse parseStreamChunk(String payload) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(payload);
        JsonNode choice = root.path("choices").path(0);
        JsonNode deltaNode = choice.path("delta");

        String content = deltaNode.path("content").asText("");
        String reasoningContent = deltaNode.path("reasoning_content").asText("");
        String finishReason = choice.path("finish_reason").asText("");
        JsonNode usageNode = root.path("usage");
        boolean hasUsage = !usageNode.isMissingNode();

        // 若正文、思考片段、结束标识与用量统计均为空，方视为空片段丢弃
        if (content.isEmpty() && reasoningContent.isEmpty() && finishReason.isEmpty() && !hasUsage) {
            return null;
        }

        Map<String, Object> metadataMap = new LinkedHashMap<>();
        if (!reasoningContent.isEmpty()) {
            metadataMap.put("reasoning_content", reasoningContent);
        }
        if (deltaNode.has("tool_calls")) {
            metadataMap.put("tool_calls", OBJECT_MAPPER.convertValue(deltaNode.get("tool_calls"), List.class));
        }

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(content)
                .properties(metadataMap)
                .build();

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .metadata(metadataMap)
                .build();

        Generation generation = new Generation(assistantMessage, generationMetadata);

        DefaultUsage usage = hasUsage ? new DefaultUsage(
                usageNode.path("prompt_tokens").isMissingNode() ? null : usageNode.path("prompt_tokens").asInt(),
                usageNode.path("completion_tokens").isMissingNode() ? null : usageNode.path("completion_tokens").asInt(),
                usageNode.path("total_tokens").isMissingNode() ? null : usageNode.path("total_tokens").asInt(),
                usageNode
        ) : null;

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id(root.path("id").asText(""))
                .model(root.path("model").asText(modelName))
                .usage(usage)
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    private static String toRole(MessageType messageType) {
        if (MessageType.SYSTEM.equals(messageType)) {
            return "system";
        }
        if (MessageType.ASSISTANT.equals(messageType)) {
            return "assistant";
        }
        if (MessageType.TOOL.equals(messageType)) {
            return "tool";
        }
        return "user";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return normalized;
    }

    private static String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500);
    }
}
