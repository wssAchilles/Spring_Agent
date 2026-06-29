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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class DeepSeekCompatibleChatModel implements ChatModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        this.modelName = modelName;
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

    private Map<String, Object> buildRequestBody(Prompt prompt, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            messages.add(Map.of(
                    "role", toRole(message.getMessageType()),
                    "content", message.getText() == null ? "" : message.getText()
            ));
        }
        if (temperature == null) {
            return Map.of("model", modelName, "messages", messages, "stream", stream);
        }
        return Map.of("model", modelName, "messages", messages, "stream", stream, "temperature", temperature);
    }

    private ChatResponse parseResponse(String responseBody) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asText("");
        String finishReason = choice.path("finish_reason").asText("");

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        Generation generation = new Generation(new AssistantMessage(content), generationMetadata);

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

    private ChatResponse parseStreamChunk(String payload) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(payload);
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("delta").path("content").asText("");
        String finishReason = choice.path("finish_reason").asText("");
        if (content.isEmpty() && finishReason.isEmpty()) {
            return null;
        }

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        Generation generation = new Generation(new AssistantMessage(content), generationMetadata);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id(root.path("id").asText(""))
                .model(root.path("model").asText(modelName))
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
