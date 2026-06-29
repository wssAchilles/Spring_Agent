package tech.qiantong.qknow.module.kb.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kb.tool.mcp.McpToolAdapter;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "系统健康检查")
@RestController
@RequestMapping("/kb/health")
public class HealthCheckController {

    @Resource
    private DataSource dataSource;

    @Resource
    private McpToolAdapter mcpToolAdapter;

    @Operation(summary = "系统健康检查 (类似 claw doctor)")
    @GetMapping
    public CommonResult<Map<String, Object>> health() {
        Map<String, Object> checks = new HashMap<>();

        // 数据库连接检查
        checks.put("database", checkDatabase());

        // MCP 服务检查
        checks.put("mcp", checkMcp());

        // LangFuse 状态
        checks.put("langfuse", checkLangFuse());

        // 系统信息
        checks.put("system", getSystemInfo());

        return CommonResult.success(checks);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> db = new HashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            db.put("status", "ok");
            db.put("url", conn.getMetaData().getURL());
            db.put("driver", conn.getMetaData().getDriverName());
        } catch (Exception e) {
            db.put("status", "error");
            db.put("error", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> checkMcp() {
        Map<String, Object> mcp = new HashMap<>();
        var configs = mcpToolAdapter.getServerConfigs();
        mcp.put("servers", configs.size());
        mcp.put("tools", mcpToolAdapter.getMcpTools().size());

        // 每个 server 的详细状态
        java.util.List<Map<String, Object>> serverDetails = new java.util.ArrayList<>();
        for (var config : configs.values()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("name", config.getName());
            detail.put("url", config.getUrl());
            detail.put("command", config.getCommand());
            detail.put("enabled", config.isEnabled());

            // 检查连接状态
            var client = mcpToolAdapter.getClient(config.getName());
            if (client != null) {
                detail.put("connected", client.isConnected());
            } else {
                detail.put("connected", false);
            }
            serverDetails.add(detail);
        }
        mcp.put("details", serverDetails);
        return mcp;
    }

    private Map<String, Object> checkLangFuse() {
        Map<String, Object> langfuse = new HashMap<>();
        String enabled = envOrDotenv("LANGFUSE_ENABLED");
        String baseUrl = envOrDotenv("LANGFUSE_BASE_URL");
        langfuse.put("enabled", "true".equalsIgnoreCase(enabled));
        langfuse.put("baseUrl", baseUrl != null ? baseUrl : "https://cloud.langfuse.com");
        return langfuse;
    }

    private String envOrDotenv(String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        for (Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path env = dir.resolve(".env");
            if (!Files.isRegularFile(env)) {
                continue;
            }
            try (var lines = Files.lines(env)) {
                return lines
                        .filter(line -> line.startsWith(key + "="))
                        .map(line -> line.substring(key.length() + 1).trim())
                        .findFirst()
                        .orElse(null);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> getSystemInfo() {
        Map<String, Object> sys = new HashMap<>();
        sys.put("javaVersion", System.getProperty("java.version"));
        sys.put("osName", System.getProperty("os.name"));
        sys.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        sys.put("maxMemoryMB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        sys.put("freeMemoryMB", Runtime.getRuntime().freeMemory() / 1024 / 1024);
        return sys;
    }

    @Operation(summary = "获取 LangFuse 追踪数据")
    @GetMapping("/traces")
    public CommonResult<Map<String, Object>> getTraces() {
        Map<String, Object> result = new HashMap<>();
        String langfuseEnabled = envOrDotenv("LANGFUSE_ENABLED");
        String langfuseBaseUrl = envOrDotenv("LANGFUSE_BASE_URL");
        String langfusePublicKey = envOrDotenv("LANGFUSE_PUBLIC_KEY");
        String langfuseSecretKey = envOrDotenv("LANGFUSE_SECRET_KEY");

        if (!"true".equalsIgnoreCase(langfuseEnabled) ||
            langfusePublicKey == null || langfusePublicKey.isBlank() ||
            langfuseSecretKey == null || langfuseSecretKey.isBlank()) {
            result.put("enabled", false);
            result.put("traces", java.util.List.of());
            result.put("metrics", Map.of(
                "totalConversations", 0,
                "avgLatency", 0,
                "totalTokens", 0
            ));
            return CommonResult.success(result);
        }

        try {
            String baseUrl = langfuseBaseUrl != null && !langfuseBaseUrl.isBlank()
                ? langfuseBaseUrl : "https://cloud.langfuse.com";
            String auth = java.util.Base64.getEncoder()
                .encodeToString((langfusePublicKey + ":" + langfuseSecretKey).getBytes());

            // 获取最近 traces
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest tracesReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl + "/api/public/traces?limit=20"))
                .header("Authorization", "Basic " + auth)
                .header("Accept", "application/json")
                .GET()
                .build();
            java.net.http.HttpResponse<String> tracesResp = client.send(tracesReq, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (tracesResp.statusCode() == 200) {
                com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(tracesResp.body());
                result.put("enabled", true);
                result.put("baseUrl", baseUrl);

                // 获取 traces 并聚合 observations
                com.alibaba.fastjson2.JSONArray traces = json.getJSONArray("data");
                com.alibaba.fastjson2.JSONArray enrichedTraces = new com.alibaba.fastjson2.JSONArray();
                int totalTraces = traces != null ? traces.size() : 0;
                long totalLatency = 0;
                int totalTokens = 0;

                if (traces != null) {
                    for (int i = 0; i < traces.size(); i++) {
                        com.alibaba.fastjson2.JSONObject trace = traces.getJSONObject(i);
                        String traceId = trace.getString("id");
                        int traceTokens = 0;
                        long observationLatency = 0;

                        // 获取该 trace 的 observations（spans/generations）
                        try {
                            java.net.http.HttpRequest obsReq = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(baseUrl + "/api/public/observations?traceId=" + traceId))
                                .header("Authorization", "Basic " + auth)
                                .header("Accept", "application/json")
                                .GET()
                                .build();
                            java.net.http.HttpResponse<String> obsResp = client.send(obsReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                            if (obsResp.statusCode() == 200) {
                                com.alibaba.fastjson2.JSONObject obsJson = com.alibaba.fastjson2.JSON.parseObject(obsResp.body());
                                com.alibaba.fastjson2.JSONArray observations = obsJson.getJSONArray("data");
                                trace.put("observations", observations != null ? observations : new com.alibaba.fastjson2.JSONArray());

                                // 从 observations 中提取 token 和 latency
                                if (observations != null) {
                                    for (int j = 0; j < observations.size(); j++) {
                                        com.alibaba.fastjson2.JSONObject obs = observations.getJSONObject(j);
                                        int observationTokens = readTokenTotal(obs);
                                        traceTokens += observationTokens;
                                        totalTokens += observationTokens;
                                        observationLatency = Math.max(observationLatency, readLatency(obs));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 忽略 observation 获取失败
                            trace.put("observations", new com.alibaba.fastjson2.JSONArray());
                        }

                        // 提取 latency
                        long traceLatency = readLatency(trace);
                        if (traceLatency == 0) {
                            traceLatency = observationLatency;
                            trace.put("latency", traceLatency);
                        }
                        if (traceTokens > 0 && !trace.containsKey("usage")) {
                            trace.put("usage", Map.of("totalTokens", traceTokens));
                        }
                        totalLatency += traceLatency;

                        enrichedTraces.add(trace);
                    }
                }

                result.put("traces", enrichedTraces);
                result.put("meta", json.getJSONObject("meta"));

                Map<String, Object> metrics = new HashMap<>();
                metrics.put("totalConversations", totalTraces);
                metrics.put("avgLatency", totalTraces > 0 ? totalLatency / totalTraces : 0);
                metrics.put("totalTokens", totalTokens);
                result.put("metrics", metrics);
            } else {
                result.put("enabled", true);
                result.put("error", "LangFuse API 返回 " + tracesResp.statusCode());
                result.put("traces", java.util.List.of());
            }
        } catch (Exception e) {
            result.put("enabled", true);
            result.put("error", e.getMessage());
            result.put("traces", java.util.List.of());
        }

        return CommonResult.success(result);
    }

    private int readTokenTotal(com.alibaba.fastjson2.JSONObject observation) {
        if (observation == null) {
            return 0;
        }
        Long direct = readLong(observation, "totalTokens");
        if (direct != null && direct > 0) {
            return direct.intValue();
        }
        int usageTotal = readTokenTotalFromObject(observation.getJSONObject("usage"));
        if (usageTotal > 0) {
            return usageTotal;
        }
        return readTokenTotalFromObject(observation.getJSONObject("usageDetails"));
    }

    private int readTokenTotalFromObject(com.alibaba.fastjson2.JSONObject usage) {
        if (usage == null) {
            return 0;
        }
        Long totalTokens = readLong(usage, "totalTokens");
        if (totalTokens != null && totalTokens > 0) {
            return totalTokens.intValue();
        }
        Long total = readLong(usage, "total");
        if (total != null && total > 0) {
            return total.intValue();
        }
        long input = defaultLong(readLong(usage, "input"), readLong(usage, "promptTokens"));
        long output = defaultLong(readLong(usage, "output"), readLong(usage, "completionTokens"));
        return (int) Math.max(0, input + output);
    }

    private long readLatency(com.alibaba.fastjson2.JSONObject object) {
        if (object == null) {
            return 0;
        }
        Long latency = readLong(object, "latency");
        if (latency != null && latency > 0) {
            return latency;
        }
        Long duration = readLong(object, "duration");
        return duration != null && duration > 0 ? duration : 0;
    }

    private Long readLong(com.alibaba.fastjson2.JSONObject object, String key) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private long defaultLong(Long first, Long second) {
        if (first != null) {
            return first;
        }
        return second != null ? second : 0;
    }
}
