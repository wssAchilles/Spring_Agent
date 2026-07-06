package tech.qiantong.qknow.module.kmc.service.rag;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
import java.util.Map;

@Slf4j
@Component
public class CragWebSearchClient {

    private final CragWebSearchConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CragWebSearchClient(CragWebSearchConfig config) {
        this.config = config;
    }

    public List<RetrievalResult> search(String query, int maxResults) {
        if (!config.isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("query", query);
            requestBody.put("summary", true);
            requestBody.put("count", maxResults);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                    .timeout(Duration.ofMillis(config.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));

            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + config.getApiKey());
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                RagFallbackMonitor.record("crag_web_search", "empty_results", "bocha status " + response.statusCode());
                log.warn("Bocha web search failed with status {}. Body: {}", response.statusCode(), response.body());
                return List.of();
            }
            
            JSONObject json = JSONObject.parseObject(response.body());
            int code = json.getIntValue("code", -1);
            if (code != 200 && code != 0) { // Some APIs use 0 as success
                RagFallbackMonitor.record("crag_web_search", "empty_results", "bocha code " + code);
                log.warn("Bocha API returned error code {}. Body: {}", code, response.body());
                return List.of();
            }
            
            return parseBocha(response.body(), maxResults);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            RagFallbackMonitor.record("crag_web_search", "empty_results", "bocha interrupted");
            log.warn("CRAG web search interrupted");
            return List.of();
        } catch (Exception e) {
            RagFallbackMonitor.record("crag_web_search", "empty_results", "bocha failed: " + e.getMessage());
            log.warn("CRAG web search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RetrievalResult> parseBocha(String body, int maxResults) {
        JSONObject json = JSONObject.parseObject(body);
        List<RetrievalResult> results = new ArrayList<>();
        
        JSONObject data = json.getJSONObject("data");
        if (data == null) return results;
        
        JSONObject webPages = data.getJSONObject("webPages");
        if (webPages == null) return results;
        
        JSONArray values = webPages.getJSONArray("value");
        if (values == null) return results;
        
        for (int i = 0; i < values.size() && results.size() < maxResults; i++) {
            JSONObject item = values.getJSONObject(i);
            addResult(results, item.getString("name"), item.getString("snippet"), item.getString("url"), "bocha", maxResults);
        }
        return results;
    }

    private void addResult(List<RetrievalResult> results, String title, String text, String url, String provider, int maxResults) {
        if (results.size() >= maxResults || text == null || text.isBlank()) {
            return;
        }
        String safeTitle = title != null && !title.isBlank() ? title : "web-search";
        long id = -Math.abs((safeTitle + text + url).hashCode());
        results.add(RetrievalResult.builder()
                .segmentId(id)
                .documentName(safeTitle)
                .content(text)
                .score(6.0D)
                .source("web_search")
                .metadata(Map.of(
                        "url", url != null ? url : "",
                        "provider", provider))
                .build());
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "qknow.rag.crag.web-search")
    public static class CragWebSearchConfig {
        private boolean enabled = true;
        private String endpoint = "https://api.bochaai.com/v1/web-search";
        private String apiKey;
        private long timeoutMs = 10000L;
    }
}
