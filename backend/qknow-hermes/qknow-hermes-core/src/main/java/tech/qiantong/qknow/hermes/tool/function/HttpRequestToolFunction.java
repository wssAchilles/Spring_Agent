package tech.qiantong.qknow.hermes.tool.function;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component("http_request")
public class HttpRequestToolFunction
        implements Function<HttpRequestToolFunction.Request, HttpRequestToolFunction.Response> {

    @Data
    @JsonClassDescription("访问已知 URL 或 API。用于获取网页内容、调用 REST API、访问特定链接。不负责搜索，必须提供完整 URL。支持 GET 和 POST，10秒超时，响应截断2000字符。")
    public static class Request {

        @JsonProperty(required = true, value = "url")
        @JsonPropertyDescription("完整的 URL 地址，必须以 http:// 或 https:// 开头")
        private String url;

        @JsonProperty(value = "method")
        @JsonPropertyDescription("请求方法: GET 或 POST，默认 GET")
        private String method;

        @JsonProperty(value = "headers")
        @JsonPropertyDescription("请求头，JSON 字符串格式")
        private String headers;

        @JsonProperty(value = "body")
        @JsonPropertyDescription("POST 请求体")
        private String body;
    }

    @Data
    public static class Response {
        private Integer statusCode;
        private String body;
        private String error;

        public static Response success(int statusCode, String body) {
            Response r = new Response();
            r.statusCode = statusCode;
            r.body = body.length() > 2000 ? body.substring(0, 2000) + "..." : body;
            return r;
        }

        public static Response error(String error) {
            Response r = new Response();
            r.error = error;
            return r;
        }
    }

    @Override
    public Response apply(Request request) {
        try {
            Method httpMethod = "POST".equalsIgnoreCase(request.getMethod()) ? Method.POST : Method.GET;
            HttpRequest httpRequest = new HttpRequest(request.getUrl())
                    .method(httpMethod)
                    .timeout(10000);

            if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
                try {
                    Map<String, String> headerMap = cn.hutool.json.JSONUtil.parseObj(request.getHeaders())
                            .toBean(Map.class);
                    headerMap.forEach(httpRequest::header);
                } catch (Exception e) {
                    log.warn("解析请求头失败: {}", e.getMessage());
                }
            }

            if (httpMethod == Method.POST && request.getBody() != null) {
                httpRequest.body(request.getBody());
            }

            HttpResponse response = httpRequest.execute();
            return Response.success(response.getStatus(), response.body());
        } catch (Exception e) {
            log.error("HTTP 请求失败: {}", request.getUrl(), e);
            return Response.error("请求失败: " + e.getMessage());
        }
    }
}
