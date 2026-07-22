package tech.qiantong.qknow.hermes.tool.function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestToolTest {

    private static HttpServer server;
    private static ExecutorService serverExecutor;
    private static String baseUrl;

    private final HttpRequestToolFunction tool = new HttpRequestToolFunction();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newFixedThreadPool(2);
        server.setExecutor(serverExecutor);
        server.createContext("/get", exchange -> respond(exchange, 200, "httpbin-local"));
        server.createContext("/post", exchange -> respond(exchange, 200,
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        server.createContext("/bytes", exchange -> respond(exchange, 200, "x".repeat(3000)));
        server.createContext("/headers", exchange -> respond(exchange, 200,
                exchange.getRequestHeaders().getFirst("X-Custom-Header")));
        server.createContext("/status", exchange -> respond(exchange, 418, "teapot"));
        server.createContext("/timeout", exchange -> {
            try {
                Thread.sleep(10_500);
                respond(exchange, 200, "too late");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void getReturnsSuccess() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl(baseUrl + "/get");
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
        req.setUrl(baseUrl + "/post");
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
        req.setUrl(baseUrl + "/bytes/3000");
        req.setMethod("GET");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals("x".repeat(2000) + "...", resp.getBody());
    }

    @Test
    void customHeadersParsed() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl(baseUrl + "/headers");
        req.setMethod("GET");
        req.setHeaders("{\"X-Custom-Header\":\"test-value\"}");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertTrue(resp.getBody().contains("test-value"));
    }

    @Test
    void nonSuccessStatusReturned() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl(baseUrl + "/status");
        req.setMethod("GET");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getError());
        assertEquals(418, resp.getStatusCode());
        assertEquals("teapot", resp.getBody());
    }

    @Test
    void timeoutReturnsError() {
        HttpRequestToolFunction.Request req = new HttpRequestToolFunction.Request();
        req.setUrl(baseUrl + "/timeout");
        req.setMethod("GET");

        HttpRequestToolFunction.Response resp = tool.apply(req);

        assertNull(resp.getStatusCode());
        assertNotNull(resp.getError());
        assertTrue(resp.getError().contains("timed out"));
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
