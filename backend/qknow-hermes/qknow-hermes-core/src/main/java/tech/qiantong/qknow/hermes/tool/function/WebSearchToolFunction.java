package tech.qiantong.qknow.hermes.tool.function;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Component("web_search")
public class WebSearchToolFunction
        implements Function<WebSearchToolFunction.Request, WebSearchToolFunction.Response> {

    private static final String SEARCH_API = "https://api.duckduckgo.com/?q={}&format=json&no_html=1&skip_disambig=1";
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final SearchProvider searchProvider;

    public WebSearchToolFunction() {
        this(new DuckDuckGoSearchProvider());
    }

    public WebSearchToolFunction(SearchProvider searchProvider) {
        this.searchProvider = searchProvider;
    }

    @Data
    @JsonClassDescription("搜索互联网获取公开信息。当用户问最新资讯、公司信息、新闻、官网、公开资料、当前状态、训练知识外信息时使用。返回标题、摘要、URL。")
    public static class Request {

        @JsonProperty(required = true, value = "query")
        @JsonPropertyDescription("搜索关键词，支持中英文")
        private String query;
    }

    @Data
    public static class Response {
        private String query;
        private List<SearchResult> results;
        private String abstractText;
        private String error;

        @Data
        public static class SearchResult {
            private String title;
            private String snippet;
            private String url;
        }

        public static Response success(String query, List<SearchResult> results, String abstractText) {
            Response r = new Response();
            r.query = query;
            r.results = results;
            r.abstractText = abstractText;
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
            List<Response.SearchResult> results = searchProvider.search(
                    request.getQuery(), DEFAULT_MAX_RESULTS);
            return Response.success(request.getQuery(), results, null);
        } catch (Exception e) {
            log.error("搜索失败: {}", request.getQuery(), e);
            return Response.error("搜索失败: " + e.getMessage());
        }
    }

    /**
     * Default DuckDuckGo search provider implementation.
     */
    public static class DuckDuckGoSearchProvider implements SearchProvider {

        @Override
        public String getName() {
            return "duckduckgo";
        }

        @Override
        public List<Response.SearchResult> search(String query, int maxResults) {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = SEARCH_API.replace("{}", encodedQuery);
            String responseStr = HttpUtil.get(url, 10000);

            JSONObject json = JSONUtil.parseObj(responseStr);
            List<Response.SearchResult> results = new ArrayList<>();

            JSONArray relatedTopics = json.getJSONArray("RelatedTopics");
            if (relatedTopics != null) {
                int count = Math.min(relatedTopics.size(), maxResults);
                for (int i = 0; i < count; i++) {
                    JSONObject topic = relatedTopics.getJSONObject(i);
                    if (topic.containsKey("Text")) {
                        Response.SearchResult result = new Response.SearchResult();
                        String text = topic.getStr("Text", "");
                        result.title = text.substring(0, Math.min(text.length(), 100));
                        result.snippet = text;
                        result.url = topic.getStr("FirstURL", "");
                        results.add(result);
                    }
                }
            }

            return results;
        }
    }
}
