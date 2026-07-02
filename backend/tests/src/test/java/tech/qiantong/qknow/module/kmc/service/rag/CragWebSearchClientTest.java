package tech.qiantong.qknow.module.kmc.service.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CragWebSearchClientTest {

    @Test
    @DisplayName("Web Search客户端解析DuckDuckGo结构为检索结果")
    void search_parsesDuckDuckGoResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            byte[] body = """
                    {
                      "Heading": "OpenAI",
                      "AbstractText": "OpenAI is an AI research and deployment company.",
                      "AbstractURL": "https://openai.com",
                      "RelatedTopics": [
                        {"FirstURL": "https://example.com/rag", "Text": "RAG combines retrieval and generation."}
                      ]
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
            config.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/search?q=%s");
            CragWebSearchClient client = new CragWebSearchClient(config);

            List<RetrievalResult> results = client.search("OpenAI", 2);

            assertEquals(2, results.size());
            assertEquals("web_search", results.get(0).getSource());
            assertEquals("duckduckgo", results.get(0).getMetadata().get("provider"));
        } finally {
            server.stop(0);
        }
    }
}
