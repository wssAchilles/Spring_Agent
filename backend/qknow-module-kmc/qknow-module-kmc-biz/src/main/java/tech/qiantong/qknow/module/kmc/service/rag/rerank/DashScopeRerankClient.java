package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义 DashScope Rerank HTTP 客户端
 * 替代 spring-ai-alibaba-dashscope 的 DashScopeRerankModel
 * 直接调用 DashScope API，消除对 Alibaba SDK 的依赖
 *
 * API 文档：https://help.aliyun.com/zh/model-studio/developer-reference/rerank
 */
@Slf4j
public class DashScopeRerankClient {

    private static final String RERANK_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-ranking/rerank";

    private final String apiKey;
    private final String modelName;
    private final HttpClient httpClient;

    public DashScopeRerankClient(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 执行重排序
     */
    public List<RerankResult> rerank(String query, List<Document> documents, int topN) {
        try {
            JSONArray docsArray = new JSONArray();
            for (Document doc : documents) {
                docsArray.add(doc.getText());
            }

            JSONObject body = new JSONObject();
            body.put("model", modelName);
            JSONObject input = new JSONObject();
            input.put("query", query);
            input.put("documents", docsArray);
            body.put("input", input);
            JSONObject parameters = new JSONObject();
            parameters.put("top_n", topN);
            parameters.put("return_documents", false);
            body.put("parameters", parameters);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RERANK_ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("DashScope rerank API error: status={}, body={}", response.statusCode(), response.body());
                return fallbackRerank(documents, topN);
            }

            return parseResponse(response.body(), documents, topN);
        } catch (Exception e) {
            log.error("DashScope rerank failed", e);
            return fallbackRerank(documents, topN);
        }
    }

    private List<RerankResult> parseResponse(String responseBody, List<Document> originalDocs, int topN) {
        JSONObject json = JSON.parseObject(responseBody);
        JSONArray results = json.getJSONObject("output").getJSONArray("results");

        List<RerankResult> reranked = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            int index = item.getIntValue("index");
            double score = item.getDoubleValue("relevance_score");

            if (index >= 0 && index < originalDocs.size()) {
                reranked.add(new RerankResult(originalDocs.get(index), score));
            }
        }
        return reranked;
    }

    private List<RerankResult> fallbackRerank(List<Document> documents, int topN) {
        log.warn("Using fallback rerank (no scoring)");
        List<RerankResult> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, documents.size()); i++) {
            results.add(new RerankResult(documents.get(i), 1.0 - i * 0.01));
        }
        return results;
    }

    public record RerankResult(Document document, double score) {}
}
