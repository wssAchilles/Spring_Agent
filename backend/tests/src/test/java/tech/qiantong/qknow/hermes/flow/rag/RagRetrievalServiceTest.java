package tech.qiantong.qknow.hermes.flow.rag;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalServiceTest {

    @Test
    void retrievePostsRecallRequestAndParsesArrayData() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        JSONArray data = new JSONArray();
        data.add(JSONObject.of("content", "知识图谱内容"));
        data.add(JSONObject.of("text", "图数据库内容"));

        JSONObject response = JSONObject.of(
                "code", 200,
                "msg", "操作成功",
                "data", data.toJSONString()
        );

        HttpServer server = startServer(200, response.toJSONString(), exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            try {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            RagRetrievalService service = service(server, "X-Internal-Token", "test-token");
            List<String> results = service.retrieve("123", "什么是知识图谱");

            assertEquals(List.of("知识图谱内容", "图数据库内容"), results);
            assertEquals("test-token", token.get());
            assertTrue(requestBody.get().contains("\"knowledgeId\":\"123\""));
            assertTrue(requestBody.get().contains("\"query\":\"什么是知识图谱\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retrieveReturnsEmptyListWhenRecallHttpFails() throws Exception {
        HttpServer server = startServer(500, "failed", exchange -> {
        });

        try {
            RagRetrievalService service = service(server, "Authorization", "");

            assertEquals(List.of(), service.retrieve("123", "query"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retrieveParsesObjectDataWithoutTokenHeader() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        JSONObject response = JSONObject.of(
                "code", 200,
                "msg", "操作成功",
                "data", JSONObject.of("text", "单条内容")
        );

        HttpServer server = startServer(200, response.toJSONString(), exchange ->
                token.set(exchange.getRequestHeaders().getFirst("Authorization")));

        try {
            RagRetrievalService service = service(server, "Authorization", "");

            assertEquals(List.of("单条内容"), service.retrieve("123", "query"));
            assertNull(token.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retrieveInterruptedRestoresInterruptFlag() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/api/rag/recall", exchange -> {
            requestStarted.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        AtomicBoolean interruptedAfterCall = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                RagRetrievalService service = service(server, "Authorization", "");
                assertEquals(List.of(), service.retrieve("123", "query"));
                interruptedAfterCall.set(Thread.currentThread().isInterrupted());
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        try {
            worker.start();
            assertTrue(requestStarted.await(2, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(TimeUnit.SECONDS.toMillis(5));

            assertNull(failure.get());
            assertTrue(interruptedAfterCall.get());
        } finally {
            server.stop(0);
        }
    }

    private RagRetrievalService service(HttpServer server, String headerName, String token) {
        return new RagRetrievalService(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/api/rag/recall",
                headerName,
                token
        );
    }

    private HttpServer startServer(int status, String responseBody, Consumer<HttpExchange> requestConsumer)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/api/rag/recall", exchange -> {
            requestConsumer.accept(exchange);
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        return server;
    }
}
