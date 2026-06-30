package tech.qiantong.qknow.hermes.tool.function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestToolTest {

    private final HttpRequestToolFunction tool = new HttpRequestToolFunction();

    @Test
    void getReturnsSuccess() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl("https://httpbin.org/get");
        req.setMethod("GET");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertNotNull(resp.getStatusCode());
        assertEquals(200, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("httpbin"));
    }

    @Test
    void postReturnsSuccess() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl("https://httpbin.org/post");
        req.setMethod("POST");
        req.setBody("{\"key\":\"value\"}");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals(200, resp.getStatusCode());
        assertTrue(resp.getBody().contains("value"));
    }

    @Test
    void invalidUrlReturnsError() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl("not-a-valid-url");
        req.setMethod("GET");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        // Invalid URL may return error or non-200 status
        assertTrue(resp.getError() != null || resp.getStatusCode() == null || resp.getStatusCode() >= 400);
    }

    @Test
    void responseTruncatedAt2000Chars() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl("https://httpbin.org/bytes/3000");
        req.setMethod("GET");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertTrue(resp.getBody().length() <= 2004); // 2000 + "..."
    }

    @Test
    void customHeadersParsed() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl("https://httpbin.org/headers");
        req.setMethod("GET");
        req.setHeaders("{\"X-Custom-Header\":\"test-value\"}");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertTrue(resp.getBody().contains("test-value"));
    }
}
