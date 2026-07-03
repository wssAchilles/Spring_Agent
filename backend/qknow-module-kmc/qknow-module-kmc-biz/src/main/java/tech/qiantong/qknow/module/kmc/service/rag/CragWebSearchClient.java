package tech.qiantong.qknow.module.kmc.service.rag;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                log.warn("Bocha web search failed with status {}. Body: {}. Falling back to Exa...", response.statusCode(), response.body());
                return fallbackToExa(query, maxResults);
            }
            
            JSONObject json = JSONObject.parseObject(response.body());
            int code = json.getIntValue("code", -1);
            if (code != 200 && code != 0) { // Some APIs use 0 as success
                log.warn("Bocha API returned error code {}. Body: {}. Falling back to Exa...", code, response.body());
                return fallbackToExa(query, maxResults);
            }
            
            return parseBocha(response.body(), maxResults);
        } catch (Exception e) {
            log.warn("CRAG web search failed: {}. Falling back to Exa...", e.getMessage());
            return fallbackToExa(query, maxResults);
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

    private List<RetrievalResult> fallbackToExa(String query, int maxResults) {
        List<RetrievalResult> results = new ArrayList<>();
        try {
            String safeQuery = query.replace("\"", "\\\"");
            String cmd = String.format("mcporter call 'exa.web_search_exa(query: \"%s\", numResults: %d)'", safeQuery, maxResults);
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
            pb.environment().put("PATH", "/usr/local/bin:/usr/bin:/bin:/opt/homebrew/bin:" + System.getenv("PATH"));
            Process p = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            p.waitFor();
            
            String outStr = output.toString();
            if (outStr.isBlank()) {
                log.warn("Exa fallback returned empty");
                return results;
            }
            
            // Parse mcporter Exa output format
            String[] blocks = outStr.split("---");
            for (String block : blocks) {
                if (results.size() >= maxResults) break;
                
                String title = extractMatch(block, "Title: (.*)");
                String url = extractMatch(block, "URL: (.*)");
                String text = extractMatch(block, "(?s)Highlights:\\s*(.*)");
                
                if (text != null && !text.isBlank()) {
                    addResult(results, title, text.trim(), url, "exa", maxResults);
                }
            }
            log.info("Exa fallback succeeded, fetched {} results", results.size());
        } catch (Exception e) {
            log.error("Exa fallback failed: {}", e.getMessage());
        }
        return results;
    }

    private String extractMatch(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
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
