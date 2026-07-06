package tech.qiantong.qknow.hermes.flow.rag;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.common.core.domain.CommonResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 知识检索服务
 * 负责从知识库中检索相关文档片段
 * 实际实现会调用 KMC 模块的向量检索
 */
@Slf4j
@Component
public class RagRetrievalService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String recallUrl;
    private final String tokenHeaderName;
    private final String token;

    public RagRetrievalService(
            @Value("${hermes.flow.rag.recall-url:http://localhost:8099/internal/api/rag/recall}") String recallUrl,
            @Value("${hermes.flow.rag.header-name:Authorization}") String tokenHeaderName,
            @Value("${hermes.flow.rag.token:}") String token) {
        this.recallUrl = recallUrl;
        this.tokenHeaderName = tokenHeaderName;
        this.token = token;
    }

    /**
     * 从指定知识库中检索与查询相关的文档
     *
     * @param knowledgeBaseId 知识库 ID
     * @param query           查询文本
     * @return 检索到的文档片段列表
     */
    public List<String> retrieve(String knowledgeBaseId, String query) {
        log.info("RAG 检索 - knowledgeBaseId: {}, query: {}", knowledgeBaseId, query);
        try {
            JSONObject body = new JSONObject();
            body.put("knowledgeId", knowledgeBaseId);
            body.put("query", StrUtil.blankToDefault(query, ""));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(recallUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()));
            if (StrUtil.isNotBlank(token) && StrUtil.isNotBlank(tokenHeaderName)) {
                requestBuilder.header(tokenHeaderName, token);
            }

            HttpResponse<String> response = HTTP_CLIENT.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("RAG recall HTTP failed: status={}, knowledgeBaseId={}",
                        response.statusCode(), knowledgeBaseId);
                return List.of();
            }

            return parseRecallResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("RAG recall interrupted: knowledgeBaseId={}", knowledgeBaseId, e);
            return List.of();
        } catch (Exception e) {
            log.warn("RAG recall failed: knowledgeBaseId={}", knowledgeBaseId, e);
            return List.of();
        }
    }

    private List<String> parseRecallResponse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return List.of();
        }

        JSONObject result = JSON.parseObject(responseBody);
        Integer code = result.getInteger("code");
        if (!CommonResult.isSuccess(code)) {
            log.warn("RAG recall business failed: code={}, msg={}", code, result.getString("msg"));
            return List.of();
        }

        Object data = result.get("data");
        if (data == null || (data instanceof String dataText && StrUtil.isBlank(dataText))) {
            return List.of();
        }

        Object payload = data instanceof String dataText ? JSON.parse(dataText) : data;
        List<String> texts = new ArrayList<>();
        collectReadableTexts(payload, texts);
        return texts;
    }

    private void collectReadableTexts(Object payload, List<String> texts) {
        if (payload instanceof JSONArray array) {
            for (Object item : array) {
                collectReadableTexts(item, texts);
            }
            return;
        }

        if (payload instanceof JSONObject object) {
            String text = StrUtil.blankToDefault(object.getString("content"), object.getString("text"));
            if (StrUtil.isNotBlank(text)) {
                texts.add(text);
            }
        }
    }
}
