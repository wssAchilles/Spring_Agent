package tech.qiantong.qknow.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tech.qiantong.qknow.mcp.client.McpClientManager;
import tech.qiantong.qknow.mcp.client.adapter.McpSpringAiToolCallbackAdapter;
import tech.qiantong.qknow.mcp.client.lifecycle.McpDiscoveryReport;
import tech.qiantong.qknow.mcp.client.lifecycle.McpErrorSurface;
import tech.qiantong.qknow.mcp.client.lifecycle.McpLifecyclePhase;
import tech.qiantong.qknow.mcp.client.security.McpSecurityFilterPipeline;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.core.model.InitializeResult;
import tech.qiantong.qknow.mcp.core.model.McpTool;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;
import tech.qiantong.qknow.mcp.core.transport.McpSession;
import tech.qiantong.qknow.mcp.core.transport.StdioMcpSession;
import tech.qiantong.qknow.mcp.core.util.McpNamingConvention;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;
import tech.qiantong.qknow.mcp.server.transport.InMemoryMcpSession;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 46.1: 借鉴 ultraworkers/claw-code 核心思想的企业级硬化与增强型 MCP 契约测试
 */
public class Phase46ClawCodeMcpHardenedContractTest {

    private McpServerRegistry registryA;
    private McpServerRegistry registryB;
    private McpClientManager clientManager;
    private McpSecurityFilterPipeline securityPipeline;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 1. 初始化 Server A（知识库服务）
        registryA = new McpServerRegistry("qknow-kb-service", "1.0.0");
        registryA.registerTool("search", "知识库全文搜索", Map.of(), args ->
            CallToolResult.text("来自 Server A 的知识搜索结果: " + args.get("query"))
        );

        // 2. 初始化 Server B（外部爬虫搜索服务）
        registryB = new McpServerRegistry("qknow-crawler-service", "2.0.0");
        registryB.registerTool("search", "互联网网页爬虫搜索", Map.of(), args ->
            CallToolResult.text("来自 Server B 的爬虫网页结果: " + args.get("query"))
        );

        // 3. 初始化 ClientManager 与 SecurityPipeline
        clientManager = new McpClientManager();
        securityPipeline = new McpSecurityFilterPipeline();

        clientManager.registerSession("kb-service", new InMemoryMcpSession(registryA), true);
        clientManager.registerSession("crawler-service", new InMemoryMcpSession(registryB), false);
    }

    @Test
    @DisplayName("Contract 1: McpNamingConvention 工具名称规整与反向路由解析契约")
    void test01_McpNamingConvention_NormalizeAndQualify() {
        // 1. 字符规整清洗
        assertEquals("weather_prod", McpNamingConvention.normalizeName("weather@prod!#$"));
        assertEquals("get_forecast", McpNamingConvention.normalizeName("get:forecast"));
        assertEquals("unnamed", McpNamingConvention.normalizeName("   "));

        // 2. 合格命名空间生成
        String qualified = McpNamingConvention.buildQualifiedToolName("my-server.v1", "query:all");
        assertEquals("mcp__my-server_v1__query_all", qualified);

        // 3. 反向解析路由提取
        McpNamingConvention.ToolRoute route = McpNamingConvention.parseToolRoute(qualified);
        assertEquals("my-server_v1", route.serverName());
        assertEquals("query_all", route.rawToolName());

        // 4. 非标准名称优雅兜底
        McpNamingConvention.ToolRoute fallback = McpNamingConvention.parseToolRoute("raw_tool_name");
        assertEquals("default", fallback.serverName());
        assertEquals("raw_tool_name", fallback.rawToolName());
    }

    @Test
    @DisplayName("Contract 2: 标准 JSON-RPC 2.0 initialize 握手与能力协商契约")
    void test02_McpInitializeHandshake_ProtocolNegotiation() throws Exception {
        InitializeResult result = clientManager.initialize("kb-service");
        assertNotNull(result);
        assertEquals("2024-11-05", result.protocolVersion());
        assertTrue(result.capabilities().containsKey("tools"));
        assertEquals("qknow-kb-service", result.serverInfo().get("name"));

        // 验证握手后客户端状态迁移至 READY
        McpClientManager.ClientSessionHolder holder = clientManager.getHolder("kb-service");
        assertEquals(McpLifecyclePhase.READY, holder.getPhase());
    }

    @Test
    @DisplayName("Contract 3: 11 阶段全生命周期状态机精准迁移契约")
    void test03_McpLifecyclePhases_StateTransitions() throws Exception {
        McpClientManager.ClientSessionHolder holder = clientManager.getHolder("crawler-service");
        assertEquals(McpLifecyclePhase.SPAWN_CONNECT, holder.getPhase());

        // 阶段 1: 握手
        clientManager.initialize("crawler-service");
        assertEquals(McpLifecyclePhase.READY, holder.getPhase());

        // 阶段 2: 动态工具拉取
        List<McpTool> tools = clientManager.refreshTools("crawler-service");
        assertEquals(1, tools.size());
        assertEquals(McpLifecyclePhase.READY, holder.getPhase());

        // 阶段 3: 工具调用
        CallToolResult res = clientManager.callTool("crawler-service", "search", Map.of("query", "AI Agent"));
        assertFalse(res.isError());
        assertEquals(McpLifecyclePhase.READY, holder.getPhase());
    }

    @Test
    @DisplayName("Contract 4: 自省健康报告与非核心 Server 优雅降级契约 (对标 claw mcp / doctor)")
    void test04_McpDiscoveryReport_DegradedStartup() throws Exception {
        clientManager.initialize("kb-service");
        clientManager.refreshTools("kb-service");

        // 此时 crawler-service (required=false) 未连接/关闭
        McpSession sessionB = clientManager.getHolder("crawler-service").getSession();
        sessionB.close();

        McpDiscoveryReport report = clientManager.generateDiscoveryReport();
        assertNotNull(report);
        assertEquals(2, report.totalServers());
        assertEquals(1, report.activeServers());
        assertEquals(1, report.totalTools());
        assertFalse(report.degraded(), "非核心节点失败时不应标记全局 degraded，应保持弹性可用");
        assertEquals("1/2", report.summary().get("servers_ready"));

        // 若核心节点 kb-service (required=true) 也关闭
        clientManager.getHolder("kb-service").getSession().close();
        McpDiscoveryReport degradedReport = clientManager.generateDiscoveryReport();
        assertTrue(degradedReport.degraded(), "核心节点断开必须标记 degraded 预警");
    }

    @Test
    @DisplayName("Contract 5: Spring AI ToolCallback 双下划线隔离命名空间适配契约")
    void test05_SpringAiAdapter_QualifiedNamespace() throws Exception {
        clientManager.refreshTools("kb-service");
        McpTool tool = clientManager.getHolder("kb-service").getCachedTools().get(0);

        // 启用 useQualifiedNamespace = true
        ToolCallback adapter = new McpSpringAiToolCallbackAdapter(
            "kb-service",
            tool,
            clientManager,
            securityPipeline,
            true
        );

        ToolDefinition def = adapter.getToolDefinition();
        // 严格遵循 claw-code mcp__{server}__{tool} 规范
        assertEquals("mcp__kb-service__search", def.name());

        // 调用验证
        String result = adapter.call("{\"query\": \"GraphRAG 理论\"}");
        assertNotNull(result);
        assertTrue(result.contains("来自 Server A 的知识搜索结果"));
    }

    @Test
    @DisplayName("Contract 6: StdioMcpSession 本地子进程标准管道通信与消息循环契约")
    void test06_StdioMcpSession_SubprocessEchoLoop() throws Exception {
        StdioMcpSession stdioSession = new StdioMcpSession(objectMapper);

        // 使用 macOS/Linux 自带的 /bin/cat 作为行式回显管道进程进行测试
        File shellBin = new File("/bin/cat");
        if (shellBin.exists()) {
            stdioSession.start(List.of("/bin/cat"), Map.of(), new File("."));
            assertTrue(stdioSession.isOpen());

            CompletableFuture<JsonRpcResponse> receivedFuture = new CompletableFuture<>();
            stdioSession.setMessageListener(msg -> {
                if (msg instanceof JsonRpcResponse r) {
                    receivedFuture.complete(r);
                }
            });

            // 发送模拟的 JsonRpcResponse 行式流
            JsonRpcResponse echoResp = JsonRpcResponse.success("stdio-101", Map.of("echo", "success"));
            stdioSession.send(echoResp);

            JsonRpcResponse received = receivedFuture.get(3, TimeUnit.SECONDS);
            assertNotNull(received);
            assertEquals("stdio-101", received.id());
            assertEquals("2.0", received.jsonrpc());

            stdioSession.close();
            assertFalse(stdioSession.isOpen());
        }
    }

    @Test
    @DisplayName("Contract 7: McpErrorSurface 结构化富错误上下文记录契约")
    void test07_McpErrorSurface_RichContextLogging() {
        // 模拟向不存在的工具发送调用触发异常
        Exception caught = null;
        try {
            clientManager.callTool("kb-service", "non_existent_tool", Map.of());
        } catch (Exception e) {
            caught = e;
        }
        // 验证错误被记录进 lastError
        McpClientManager.ClientSessionHolder holder = clientManager.getHolder("kb-service");
        McpErrorSurface error = holder.getLastError();
        if (error != null) {
            assertEquals(McpLifecyclePhase.INVOCATION, error.phase());
            assertEquals("kb-service", error.serverName());
            assertTrue(error.recoverable());
            assertTrue(error.context().containsKey("toolName"));
        }
    }

    @Test
    @DisplayName("Contract 8: 双 Server 暴露同名工具 (search) 零冲突命名空间路由契约")
    void test08_DualServerNamespaceCollisionResolution() throws Exception {
        // 两个 Server 都导出了 "search" 工具，但分属不同服务
        // 通过 Qualified 名称直接调用，零冲突分发

        CallToolResult resA = clientManager.callQualifiedTool(
            "mcp__kb-service__search",
            Map.of("query", "向量检索")
        );
        assertFalse(resA.isError());
        assertTrue(resA.getFirstText().contains("来自 Server A 的知识搜索结果"));

        CallToolResult resB = clientManager.callQualifiedTool(
            "mcp__crawler-service__search",
            Map.of("query", "网络热点")
        );
        assertFalse(resB.isError());
        assertTrue(resB.getFirstText().contains("来自 Server B 的爬虫网页结果"));
    }
}
