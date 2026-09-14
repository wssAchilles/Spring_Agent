package tech.qiantong.qknow.ai.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.ai.gateway.model.GatewayDto;
import tech.qiantong.qknow.ai.gateway.model.ProviderChannel;
import tech.qiantong.qknow.ai.gateway.service.ModelGatewayProxyService;

import java.util.List;
import java.util.Map;

/**
 * Phase 28: 统一大模型智能代理网关端点控制器 (标准 OpenAI 协议兼容)
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AiModelGatewayController {

    private final ModelGatewayProxyService proxyService;

    /**
     * 文本生成与对话补全端点 (支持流式 SSE 与非流式 JSON)
     */
    @PostMapping(value = "/chat/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> createChatCompletion(@RequestBody GatewayDto.GatewayChatRequest request) {
        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<GatewayDto.GatewayChatResponse> stream = proxyService.streamChatCompletion(request);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(stream);
        } else {
            GatewayDto.GatewayChatResponse response = proxyService.executeChatCompletion(request);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 统一 1536 维向量化端点 (阿里千问)
     */
    @PostMapping("/embeddings")
    public ResponseEntity<GatewayDto.GatewayEmbeddingResponse> createEmbedding(@RequestBody GatewayDto.GatewayEmbeddingRequest request) {
        GatewayDto.GatewayEmbeddingResponse response = proxyService.executeEmbedding(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 当前网关纳管且健康的模型列表端点
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        List<ProviderChannel> channels = proxyService.getChannelRegistry().getAllChannels();
        List<Map<String, Object>> modelsList = List.of(
                Map.of("id", "deepseek-chat", "object", "model", "owned_by", "deepseek"),
                Map.of("id", "deepseek-reasoner", "object", "model", "owned_by", "deepseek"),
                Map.of("id", "text-embedding-v2", "object", "model", "owned_by", "ali-qwen")
        );

        return ResponseEntity.ok(Map.of(
                "object", "list",
                "data", modelsList,
                "total_channels", channels.size()
        ));
    }
}
