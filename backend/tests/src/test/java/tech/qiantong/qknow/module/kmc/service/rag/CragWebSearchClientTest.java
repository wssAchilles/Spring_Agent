package tech.qiantong.qknow.module.kmc.service.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CragWebSearchClientTest {

    @Test
    @DisplayName("Web Search客户端解析Bocha API结构为检索结果")
    void search_parsesBochaResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/web-search", exchange -> {
            byte[] body = """
                    {
                      "code": 200,
                      "data": {
                        "webPages": {
                          "value": [
                            {"name": "OpenAI", "snippet": "OpenAI is an AI research company.", "url": "https://openai.com"},
                            {"name": "RAG", "snippet": "RAG combines retrieval and generation.", "url": "https://example.com/rag"}
                          ]
                        }
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            CragWebSearchClient.CragWebSearchConfig config = new CragWebSearchClient.CragWebSearchConfig();
            config.setEnabled(true);
            config.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/web-search");
            config.setTimeoutMs(5000);
            CragWebSearchClient client = new CragWebSearchClient(config);

            List<RetrievalResult> results = client.search("OpenAI", 2);

            assertEquals(2, results.size());
            assertEquals("web_search", results.get(0).getSource());
            assertEquals("bocha", results.get(0).getMetadata().get("provider"));
            assertEquals("OpenAI", results.get(0).getDocumentName());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("禁用时返回空结果")
    void search_whenDisabled_returnsEmpty() {
        CragWebSearchClient.CragWebSearchConfig config = new CragWebSearchClient.CragWebSearchConfig();
        config.setEnabled(false);
        CragWebSearchClient client = new CragWebSearchClient(config);

        List<RetrievalResult> results = client.search("test", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Bocha失败时返回空结果")
    void search_whenBochaFails_returnsEmpty() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/web-search", exchange -> {
            byte[] body = "failed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            CragWebSearchClient.CragWebSearchConfig config = new CragWebSearchClient.CragWebSearchConfig();
            config.setEnabled(true);
            config.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/web-search");
            config.setTimeoutMs(5000);
            CragWebSearchClient client = new CragWebSearchClient(config);

            List<RetrievalResult> results = client.search("OpenAI", 2);

            assertTrue(results.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("调用线程被中断时恢复中断标记并返回空结果")
    void search_whenInterrupted_restoresInterruptFlag() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/web-search", exchange -> {
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

        CragWebSearchClient.CragWebSearchConfig config = new CragWebSearchClient.CragWebSearchConfig();
        config.setEnabled(true);
        config.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/web-search");
        config.setTimeoutMs(30000);
        CragWebSearchClient client = new CragWebSearchClient(config);
        AtomicBoolean interruptedAfterCall = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertTrue(client.search("OpenAI", 2).isEmpty());
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
}
