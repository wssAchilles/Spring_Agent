package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地 Cross-Encoder Reranker Provider
 * 通过 HTTP 调用 Python rerank_server.py 提供本地精排
 * 作为 DashScope API 不可用时的轻量兜底方案
 *
 * [溯源] 算法优化指南 §2.2: 本地 Rerank 兜底
 */
@Slf4j
@Component
public class LocalRerankerProvider implements RerankerProvider {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final LocalRerankerConfig config;

    public LocalRerankerProvider(LocalRerankerConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "local-cross-encoder";
    }

    @Override
    public boolean supports(RerankRequestContext context) {
        return config.isEnabled() && isServiceAlive();
    }

    @Override
    public List<RetrievalResult> rerank(RerankRequestContext context,
                                         List<RetrievalResult> candidates,
                                         QueryIntent queryIntent, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        String query = context.getQuery();
        long start = System.currentTimeMillis();

        try {
            // 构建请求体
            JSONArray passages = new JSONArray();
            for (int i = 0; i < candidates.size(); i++) {
                JSONObject p = new JSONObject();
                p.put("id", i);
                p.put("text", truncate(candidates.get(i).getContent(), 512));
                passages.add(p);
            }

            JSONObject body = new JSONObject();
            body.put("query", query);
            body.put("passages", passages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServiceUrl() + "/rerank"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Local reranker returned status {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Rerank service error: " + response.statusCode());
            }

            JSONArray results = JSONArray.parse(response.body());
            List<RetrievalResult> reranked = new ArrayList<>();

            for (Object obj : results) {
                JSONObject r = (JSONObject) obj;
                int idx = r.getIntValue("id");
                float score = r.getFloatValue("score");

                if (idx >= 0 && idx < candidates.size()) {
                    RetrievalResult orig = candidates.get(idx);
                    reranked.add(RetrievalResult.builder()
                            .segmentId(orig.getSegmentId())
                            .qmSegmentId(orig.getQmSegmentId())
                            .parentSegmentId(orig.getParentSegmentId())
                            .documentId(orig.getDocumentId())
                            .documentName(orig.getDocumentName())
                            .content(orig.getContent())
                            .answer(orig.getAnswer())
                            .score(score)
                            .source(orig.getSource())
                            .metadata(orig.getMetadata())
                            .build());
                }
            }

            // 取 topK
            if (reranked.size() > topK) {
                reranked = reranked.subList(0, topK);
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("Local rerank: {} -> {} docs in {}ms", candidates.size(), reranked.size(), elapsed);

            return reranked;

        } catch (Exception e) {
            log.warn("Local rerank failed: {}", e.getMessage());
            throw new RuntimeException("Local rerank failed", e);
        }
    }

    private boolean isServiceAlive() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getServiceUrl() + "/rerank"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(2))
                    .build();
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "qknow.rag.local-reranker")
    public static class LocalRerankerConfig {
        private boolean enabled = false;
        private String serviceUrl = "http://127.0.0.1:8765";
    }
}
