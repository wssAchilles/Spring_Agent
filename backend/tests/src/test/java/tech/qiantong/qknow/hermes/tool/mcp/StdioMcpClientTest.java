package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StdioMcpClientTest {

    @Test
    void exchangesInitializeListAndCallMessagesOverJsonRpc() throws Exception {
        PipedInputStream clientInput = new PipedInputStream();
        PipedOutputStream serverOutput = new PipedOutputStream(clientInput);
        PipedInputStream serverInput = new PipedInputStream();
        PipedOutputStream clientOutput = new PipedOutputStream(serverInput);

        CompletableFuture<Void> server = CompletableFuture.runAsync(() -> respondToRequests(serverInput, serverOutput));
        StdioMcpClient client = new StdioMcpClient(
                new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8)),
                new BufferedWriter(new OutputStreamWriter(clientOutput, StandardCharsets.UTF_8)));

        client.initialize();
        List<JSONObject> tools = client.listTools();
        JSONObject result = client.callTool("echo", Map.of("text", "hello"));
        client.disconnect();
        server.join();

        assertEquals("echo", tools.get(0).getString("name"));
        assertEquals("hello", result.getJSONArray("content").getJSONObject(0).getString("text"));
    }

    private static void respondToRequests(PipedInputStream input, PipedOutputStream output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject request = JSON.parseObject(line);
                Long id = request.getLong("id");
                if (id == null) {
                    continue;
                }

                JSONObject response = new JSONObject();
                response.put("jsonrpc", "2.0");
                response.put("id", id);
                JSONObject result = new JSONObject();
                switch (request.getString("method")) {
                    case "initialize" -> result.put("protocolVersion", "2024-11-05");
                    case "tools/list" -> {
                        JSONObject tool = new JSONObject();
                        tool.put("name", "echo");
                        tool.put("description", "Echo text");
                        result.put("tools", new JSONArray(List.of(tool)));
                    }
                    case "tools/call" -> {
                        JSONObject content = new JSONObject();
                        content.put("type", "text");
                        content.put("text", request.getJSONObject("params")
                                .getJSONObject("arguments").getString("text"));
                        result.put("content", new JSONArray(List.of(content)));
                    }
                    default -> throw new IllegalStateException("Unexpected method: " + request.getString("method"));
                }
                response.put("result", result);
                writer.write(response.toJSONString());
                writer.newLine();
                writer.flush();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
