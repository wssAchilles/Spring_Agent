package tech.qiantong.qknow.hermes.flow.bo;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 外部接口请求节点
 * <p>
 * 支持向外部服务发送 HTTP 请求，并将响应结果存入工作流上下文
 * <p>
 * config 格式:
 * {
 *   "url": "https://api.example.com/endpoint",
 *   "method": "GET",
 *   "headers": { "Authorization": "Bearer xxx" },
 *   "body": "{\"key\": \"value\"}"
 * }
 */
@Slf4j
public class HttpNodeBO extends BaseNodeBO {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HttpNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList) {
        super(nodeDefinition, edgeList);
    }

    @Override
    protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
        KbFlowNodeDO nodeDefinition = getNodeDefinition();
        JSONObject configJson = JSONObject.parseObject(nodeDefinition.getConfig());

        String url = configJson.getString("url");
        if (StrUtil.isBlank(url)) {
            return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                    "HTTP 节点 URL 不能为空");
        }

        String method = configJson.getString("method");
        if (StrUtil.isBlank(method)) {
            method = "GET";
        }

        log.info("执行 HTTP 节点: {} {} ", method, url);

        try {
            // 构建请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            // 设置请求头
            JSONObject headers = configJson.getJSONObject("headers");
            if (headers != null) {
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            // 设置请求方法和请求体
            String body = configJson.getString("body");
            switch (method.toUpperCase()) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> {
                    String requestBody = StrUtil.isNotBlank(body) ? body : "";
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody));
                }
                case "PUT" -> {
                    String requestBody = StrUtil.isNotBlank(body) ? body : "";
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(requestBody));
                }
                case "DELETE" -> requestBuilder.DELETE();
                default -> {
                    return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                            "不支持的 HTTP 方法: " + method);
                }
            }

            // 执行请求
            HttpResponse<String> response = HTTP_CLIENT.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            // 构建输出
            Map<String, Object> outputData = new HashMap<>();
            JSONObject responseJson = new JSONObject();
            responseJson.put("statusCode", response.statusCode());
            responseJson.put("body", response.body());
            outputData.put(nodeDefinition.getUuid() + ".response", responseJson.toJSONString());

            log.info("HTTP 节点执行完成: {} {}, 状态码: {}", method, url, response.statusCode());
            return NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), outputData);

        } catch (IllegalArgumentException e) {
            log.error("HTTP 请求 URL 无效: {}", url, e);
            return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                    "HTTP 请求 URL 无效: " + e.getMessage());
        } catch (java.io.IOException e) {
            log.error("HTTP 请求 IO 异常: {}", url, e);
            return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                    "HTTP 请求失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("HTTP 请求被中断: {}", url, e);
            return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                    "HTTP 请求被中断");
        }
    }
}
