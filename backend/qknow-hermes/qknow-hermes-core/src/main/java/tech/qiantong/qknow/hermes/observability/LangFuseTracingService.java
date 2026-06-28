package tech.qiantong.qknow.hermes.observability;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * LangFuse 可观测性服务（基于 HTTP API）
 * 参考：LangFuse MIT 开源、Spring AI 集成文档
 * 文档：https://langfuse.com/docs/api-reference
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hermes.observability.langfuse.enabled", havingValue = "true")
public class LangFuseTracingService {

    @Value("${hermes.observability.langfuse.public-key:}")
    private String publicKey;

    @Value("${hermes.observability.langfuse.secret-key:}")
    private String secretKey;

    @Value("${hermes.observability.langfuse.base-url:https://cloud.langfuse.com}")
    private String baseUrl;

    private HttpClient httpClient;
    private String authHeader;

    @PostConstruct
    public void init() {
        if (publicKey.isBlank() || secretKey.isBlank()) {
            log.warn("LangFuse keys not configured, tracing disabled");
            return;
        }
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes());
        log.info("LangFuse tracing initialized: {}", baseUrl);
    }

    /**
     * 创建对话追踪
     */
    public String trace(String sessionId, String userId, String query) {
        if (httpClient == null) return null;
        try {
            String body = String.format("""
                    {"id":"%s","name":"agent-chat","sessionId":"%s","userId":"%s","input":"%s","metadata":{"source":"qknow"}}
                    """, java.util.UUID.randomUUID(), sessionId, userId, escapeJson(query));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/public/traces"))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return extractId(response.body());
            }
            log.debug("LangFuse trace failed: {}", response.statusCode());
        } catch (Exception e) {
            log.debug("LangFuse trace creation failed", e);
        }
        return null;
    }

    /**
     * 记录 LLM 生成
     */
    public void recordGeneration(String traceId, String model, String input,
                                  String output, Long promptTokens, Long completionTokens,
                                  Long latencyMs) {
        if (httpClient == null || traceId == null) return;
        try {
            long pt = promptTokens != null ? promptTokens : 0;
            long ct = completionTokens != null ? completionTokens : 0;
            long total = pt + ct;
            long latency = latencyMs != null ? latencyMs : 0;
            String body = String.format("""
                    {"id":"%s","traceId":"%s","type":"GENERATION","name":"%s","input":"%s","output":"%s","model":"%s","usage":{"input":%d,"output":%d,"total":%d},"promptTokens":%d,"completionTokens":%d,"totalTokens":%d,"latency":%d}
                    """, java.util.UUID.randomUUID(), traceId, model,
                    escapeJson(input), escapeJson(output), model,
                    pt, ct, total, pt, ct, total, latency);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/public/generations"))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("LangFuse generation recording failed: status={}, body={}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.debug("LangFuse generation recording failed", e);
        }
    }

    /**
     * 记录 RAG 检索
     */
    public void recordRetrieval(String traceId, String query, int resultCount,
                                 long latencyMs) {
        if (httpClient == null || traceId == null) return;
        try {
            String body = String.format("""
                    {"id":"%s","traceId":"%s","type":"SPAN","name":"rag-retrieval","input":"%s","output":"Retrieved %d segments","latency":%d}
                    """, java.util.UUID.randomUUID(), traceId,
                    escapeJson(query), resultCount, latencyMs);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/public/spans"))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("LangFuse retrieval recording failed", e);
        }
    }

    public boolean isEnabled() {
        return httpClient != null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractId(String json) {
        int idx = json.indexOf("\"id\"");
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx + 4) + 1;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
