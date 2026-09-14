package tech.qiantong.qknow.ai.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Phase 28: 统一大模型网关标准 OpenAI 协议兼容传输对象集合
 */
public class GatewayDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatMessageDto {
        private String role;
        private String content;

        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GatewayChatRequest {
        private String model;
        private List<ChatMessageDto> messages;
        private Boolean stream;
        private Double temperature;

        @JsonProperty("max_tokens")
        private Integer maxTokens;

        /**
         * 业务租户与场景透传头 (可选)
         */
        private String tenantId;
        private RoutingScenario scenario;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatUsageDto {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("completion_tokens")
        private int completionTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;

        @JsonProperty("prompt_cache_hit_tokens")
        private int promptCacheHitTokens;

        @JsonProperty("prompt_cache_miss_tokens")
        private int promptCacheMissTokens;

        @JsonProperty("reasoning_tokens")
        private int reasoningTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatChoiceDto {
        private int index;
        private ChatMessageDto message;
        private ChatMessageDto delta;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GatewayChatResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<ChatChoiceDto> choices;
        private ChatUsageDto usage;

        /**
         * 内部网关路由跟踪元数据
         */
        private String routedChannelId;
        private String routedProvider;
        private Long ttftMillis;
        private Long totalLatencyMillis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GatewayEmbeddingRequest {
        private String model;
        private Object input; // 可以是 String 或 List<String>
        private String tenantId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmbeddingDataDto {
        private String object;
        private int index;
        private List<Float> embedding;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GatewayEmbeddingResponse {
        private String object;
        private String model;
        private List<EmbeddingDataDto> data;
        private ChatUsageDto usage;
    }
}
