package tech.qiantong.qknow.module.kmc.service.rag.search;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.api.rag.RagFallbackMonitor;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tantivy BM25 检索客户端
 * 通过 HTTP REST 调用 Rust Tantivy 原生服务
 * [溯源] Phase 18: 原生加速深水区 (N1+N2)
 *
 * 生产级韧性契约：
 * 1. 250ms 软超时自动截断与 Fail-Open 降级到 PostgreSQL pg_trgm；
 * 2. 探活结果轻量缓存（2s TTL），杜绝探活 RPC 翻倍检索延迟；
 * 3. 接入 RagFallbackMonitor 全生命周期降级遥测；
 * 4. 支持单文档、批量文档索引与切片删除。
 */
@Slf4j
@Component
public class TantivyClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Value("${qknow.rag.tantivy.enabled:false}")
    private boolean enabled;

    @Value("${qknow.rag.tantivy.service-url:http://127.0.0.1:50051}")
    private String serviceUrl;

    @Value("${qknow.rag.tantivy.timeout-ms:250}")
    private long timeoutMs = 250;

    /** 探活缓存状态 */
    private volatile long lastHealthCheckTime = 0L;
    private volatile boolean lastHealthCheckResult = false;
    private static final long HEALTH_CHECK_CACHE_MS = 2000L;

    /**
     * 单个切片索引请求项
     */
    public record TantivyIndexItem(long segmentId, String content, String documentName, long knowledgeBaseId) {}

    /**
     * Tantivy 原生 BM25 检索
     *
     * @param query 查询文本
     * @param topK 期望返回数量
     * @param knowledgeBaseId 知识库 ID
     * @return 检索结果列表；当服务不可用或超时返回 null 以触发 Fail-Open 降级
     */
    public List<RetrievalResult> search(String query, int topK, long knowledgeBaseId) {
        if (!enabled) {
            return null;
        }
        if (!isServiceAlive()) {
            RagFallbackMonitor.record("tantivy", "postgres_keyword", "service not alive");
            log.debug("Tantivy service not alive, falling back to PostgreSQL");
            return null;
        }

        long startNs = System.nanoTime();
        try {
            JSONObject body = new JSONObject();
            body.put("query", query);
            body.put("top_k", topK);
            body.put("knowledge_base_id", knowledgeBaseId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                RagFallbackMonitor.record("tantivy", "postgres_keyword", "search status " + response.statusCode());
                log.warn("Tantivy search returned status {}: {}", response.statusCode(), response.body());
                return null;
            }

            JSONObject result = JSONObject.parse(response.body());
            JSONArray resultsArray = result.getJSONArray("results");
            if (resultsArray == null) {
                return new ArrayList<>();
            }

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

            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("Tantivy BM25 search completed: query='{}', kb={}, results={}, elapsed={}ms",
                    query, knowledgeBaseId, results.size(), elapsedMs);
            return results;

        } catch (HttpTimeoutException e) {
            RagFallbackMonitor.record("tantivy", "postgres_keyword", "search timeout (" + timeoutMs + "ms)");
            log.warn("Tantivy search timed out after {}ms for kb={}: {}", timeoutMs, knowledgeBaseId, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RagFallbackMonitor.record("tantivy", "postgres_keyword", "search interrupted");
            log.warn("Tantivy search interrupted");
            return null;
        } catch (Exception e) {
            RagFallbackMonitor.record("tantivy", "postgres_keyword", "search error: " + e.getMessage());
            log.warn("Tantivy search failed for kb={}: {}", knowledgeBaseId, e.getMessage());
            return null;
        }
    }

    /**
     * 单个切片同步建立索引
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
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Tantivy index interrupted for segmentId={}", segmentId);
            return false;
        } catch (Exception e) {
            log.warn("Tantivy index failed for segmentId={}: {}", segmentId, e.getMessage());
            return false;
        }
    }

    /**
     * 批量切片索引
     */
    public boolean batchIndexDocuments(List<TantivyIndexItem> items) {
        if (!enabled || items == null || items.isEmpty() || !isServiceAlive()) {
            return false;
        }

        try {
            JSONArray array = new JSONArray();
            for (TantivyIndexItem item : items) {
                JSONObject doc = new JSONObject();
                doc.put("segment_id", item.segmentId());
                doc.put("content", item.content());
                doc.put("document_name", item.documentName());
                doc.put("knowledge_base_id", item.knowledgeBaseId());
                array.add(doc);
            }

            JSONObject body = new JSONObject();
            body.put("documents", array);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/batch_index"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Tantivy batch_index interrupted for {} items", items.size());
            return false;
        } catch (Exception e) {
            log.warn("Tantivy batch_index failed for {} items: {}", items.size(), e.getMessage());
            return false;
        }
    }

    /**
     * 删除指定切片索引
     */
    public boolean deleteDocument(long segmentId) {
        if (!enabled || !isServiceAlive()) {
            return false;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("segment_id", segmentId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/delete"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(2))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Tantivy delete interrupted for segmentId={}", segmentId);
            return false;
        } catch (Exception e) {
            log.warn("Tantivy delete failed for segmentId={}: {}", segmentId, e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * 探活检查（带 2 秒轻量缓存）
     */
    public boolean isServiceAlive() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckTime < HEALTH_CHECK_CACHE_MS && lastHealthCheckResult) {
            return true;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/health"))
                    .GET()
                    .timeout(Duration.ofMillis(500))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            boolean alive = (response.statusCode() == 200);
            lastHealthCheckTime = now;
            lastHealthCheckResult = alive;
            return alive;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastHealthCheckResult = false;
            return false;
        } catch (Exception e) {
            lastHealthCheckResult = false;
            return false;
        }
    }
}
