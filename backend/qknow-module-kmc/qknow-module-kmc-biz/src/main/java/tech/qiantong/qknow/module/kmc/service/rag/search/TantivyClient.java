package tech.qiantong.qknow.module.kmc.service.rag.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * Tantivy BM25 检索客户端
 * 通过 HTTP 调用 Rust Tantivy 服务
 * [溯源] 算法优化指南 Phase 5: BM25 Tantivy gRPC
 *
 * 降级策略：Tantivy 不可用时回退到 PostgreSQL pg_trgm
 */
@Slf4j
@Component
public class TantivyClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${qknow.rag.tantivy.enabled:false}")
    private boolean enabled;

    @Value("${qknow.rag.tantivy.service-url:http://127.0.0.1:50051}")
    private String serviceUrl;

    /**
     * BM25 检索
     * @param query 查询文本
     * @param topK 返回数量
     * @param knowledgeBaseId 知识库 ID
     * @return 检索结果列表
     */
    public List<RetrievalResult> search(String query, int topK, long knowledgeBaseId) {
        if (!enabled || !isServiceAlive()) {
            RagFallbackMonitor.record("tantivy", "postgres_keyword", "service disabled or not alive");
            log.debug("Tantivy service not available, falling back to PostgreSQL");
            return null; // 返回 null 表示降级
        }

        try {
            JSONObject body = new JSONObject();
            body.put("query", query);
            body.put("top_k", topK);
            body.put("knowledge_base_id", knowledgeBaseId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                RagFallbackMonitor.record("tantivy", "postgres_keyword", "search status " + response.statusCode());
                log.warn("Tantivy search returned status {}: {}", response.statusCode(), response.body());
                return null;
            }

            JSONObject result = JSONObject.parse(response.body());
            JSONArray resultsArray = result.getJSONArray("results");
            
            List<RetrievalResult> results = new ArrayList<>();
            for (Object obj : resultsArray) {
                JSONObject r = (JSONObject) obj;
                results.add(RetrievalResult.builder()
                        .segmentId(r.getLong("segment_id"))
                        .content(r.getString("content"))
                        .score(r.getFloatValue("score"))
                        .documentName(r.getString("document_name"))
                        .source("tantivy_bm25")
                        .build());
            }

            log.info("Tantivy search: '{}' -> {} results in {}ms", 
                    query, results.size(), 0); // TODO: 拿到实际耗时
            return results;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RagFallbackMonitor.record("tantivy", "postgres_keyword", "search interrupted");
            log.warn("Tantivy search interrupted");
            return null; // 降级
        } catch (Exception e) {
            RagFallbackMonitor.record("tantivy", "postgres_keyword", "search failed: " + e.getMessage());
            log.warn("Tantivy search failed: {}", e.getMessage());
            return null; // 降级
        }
    }

    /**
     * 索引文档
     */
    public boolean indexDocument(long segmentId, String content, String documentName, long knowledgeBaseId) {
        if (!enabled || !isServiceAlive()) {
            return false;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("segment_id", segmentId);
            body.put("content", content);
            body.put("document_name", documentName);
            body.put("knowledge_base_id", knowledgeBaseId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/index"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Tantivy index interrupted");
            return false;
        } catch (Exception e) {
            log.warn("Tantivy index failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean isServiceAlive() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
