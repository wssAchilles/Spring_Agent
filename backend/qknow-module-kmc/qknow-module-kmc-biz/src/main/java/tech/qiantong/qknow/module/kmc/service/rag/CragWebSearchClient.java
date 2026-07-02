package tech.qiantong.qknow.module.kmc.service.rag;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = config.getEndpoint().formatted(encoded);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(config.getTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("CRAG web search failed: status={}", response.statusCode());
                return List.of();
            }
            return parseDuckDuckGo(response.body(), maxResults);
        } catch (Exception e) {
            log.warn("CRAG web search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RetrievalResult> parseDuckDuckGo(String body, int maxResults) {
        JSONObject json = JSONObject.parseObject(body);
        List<RetrievalResult> results = new ArrayList<>();
        addResult(results, json.getString("Heading"), json.getString("AbstractText"),
                json.getString("AbstractURL"), maxResults);
        collectRelated(results, json.getJSONArray("RelatedTopics"), maxResults);
        return results;
    }

    private void collectRelated(List<RetrievalResult> results, JSONArray array, int maxResults) {
        if (array == null || results.size() >= maxResults) {
            return;
        }
        for (int i = 0; i < array.size() && results.size() < maxResults; i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONArray nested = item.getJSONArray("Topics");
            if (nested != null) {
                collectRelated(results, nested, maxResults);
                continue;
            }
            addResult(results, item.getString("FirstURL"), item.getString("Text"),
                    item.getString("FirstURL"), maxResults);
        }
    }

    private void addResult(List<RetrievalResult> results, String title, String text, String url, int maxResults) {
        if (results.size() >= maxResults || text == null || text.isBlank()) {
            return;
        }
        long id = -Math.abs((title + text + url).hashCode());
        results.add(RetrievalResult.builder()
                .segmentId(id)
                .documentName(title != null && !title.isBlank() ? title : "web-search")
                .content(text)
                .score(6.0D)
                .source("web_search")
                .metadata(Map.of(
                        "url", url != null ? url : "",
                        "provider", "duckduckgo"))
                .build());
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "qknow.rag.crag.web-search")
    public static class CragWebSearchConfig {
        private boolean enabled = true;
        private String endpoint = "https://api.duckduckgo.com/?q=%s&format=json&no_html=1&skip_disambig=1";
        private long timeoutMs = 3000L;
    }
}
