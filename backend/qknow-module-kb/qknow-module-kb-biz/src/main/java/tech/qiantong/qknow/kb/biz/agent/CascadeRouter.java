package tech.qiantong.qknow.kb.biz.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CascadeRouter {

    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, float[]> routeCentroids = new HashMap<>();
    private static final double L1_THRESHOLD = 0.85;
    private static final double L2_CONFIDENCE_THRESHOLD = 0.6;

    public CascadeRouter(EmbeddingModel embeddingModel, ChatClient chatClient) {
        this.embeddingModel = embeddingModel;
        this.chatClient = chatClient;
    }

    public void addRouteCentroid(String routeName, float[] centroid) {
        routeCentroids.put(routeName, centroid);
    }

    public enum RouteLayer {
        L1_SEMANTIC, L2_LOGICAL
    }

    @Data
    public static class RouteResult {
        private String routeName;
        private RouteLayer hitLayer;
        private double score;

        public RouteResult(String routeName, RouteLayer hitLayer, double score) {
            this.routeName = routeName;
            this.hitLayer = hitLayer;
            this.score = score;
        }
    }

    @Data
    private static class DeepSeekEvaluation {
        private String intent;
        private double confidence;
        @JsonProperty("clarification_options")
        private List<String> clarificationOptions;
    }

    public RouteResult route(String userQuery) {
        // L1 Semantic Layer (千问 Embedding)
        EmbeddingResponse er = embeddingModel.embedForResponse(List.of(userQuery));
        float[] queryVector = er.getResult().getOutput();

        String bestL1Route = null;
        double bestL1Score = -1.0;

        for (Map.Entry<String, float[]> entry : routeCentroids.entrySet()) {
            double score = cosineSimilarity(queryVector, entry.getValue());
            if (score > bestL1Score) {
                bestL1Score = score;
                bestL1Route = entry.getKey();
            }
        }

        if (bestL1Score >= L1_THRESHOLD && bestL1Route != null) {
            log.info("L1 Route Hit: {} with score {}", bestL1Route, bestL1Score);
            return new RouteResult(bestL1Route, RouteLayer.L1_SEMANTIC, bestL1Score);
        }

        // L2 Logical Layer (DeepSeek Chat API)
        log.info("L1 Missed (Best Score: {}). Escalating to L2 DeepSeek Evaluator...", bestL1Score);
        String prompt = "你是一个精确的意图分类路由。请评估以下用户请求的意图。\n" +
                "请返回严格的 JSON 格式，包含以下字段：\n" +
                "- intent: 字符串，表示你推断的意图名称\n" +
                "- confidence: 浮点数，表示你的置信度 (0.0 到 1.0 之间)\n" +
                "- clarification_options: 字符串数组。如果你不确定（置信度低），请提供几个可能的细分选项供用户澄清。\n\n" +
                "用户请求: " + userQuery;

        String deepSeekResponse = chatClient.prompt(prompt).call().content();
        
        try {
            DeepSeekEvaluation eval = objectMapper.readValue(deepSeekResponse, DeepSeekEvaluation.class);
            if (eval.getConfidence() < L2_CONFIDENCE_THRESHOLD) {
                log.warn("L2 Ambiguous Intent detected. Confidence: {}. Suspending graph.", eval.getConfidence());
                throw new ClarificationRequiredException("意图不明确，请求澄清", eval.getClarificationOptions());
            }
            
            log.info("L2 Route Hit: {} with confidence {}", eval.getIntent(), eval.getConfidence());
            return new RouteResult(eval.getIntent(), RouteLayer.L2_LOGICAL, eval.getConfidence());

        } catch (JsonProcessingException e) {
            log.error("Failed to parse DeepSeek evaluation JSON", e);
            // 兜底回退
            return new RouteResult("UNKNOWN", RouteLayer.L2_LOGICAL, 0.0);
        }
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += Math.pow(v1[i], 2);
            normB += Math.pow(v2[i], 2);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
