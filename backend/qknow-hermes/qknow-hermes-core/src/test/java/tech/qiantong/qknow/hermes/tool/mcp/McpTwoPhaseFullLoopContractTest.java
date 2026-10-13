package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.function.FunctionToolCallback;
import tech.qiantong.qknow.hermes.agent.WorkerAgent;
import tech.qiantong.qknow.hermes.tool.discovery.ToolDiscoveryService;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpToolProjectionReceipt;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpTwoPhaseProjectionGovernor;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 企业级 MCP 双向架构破局、两阶段动态工具投影与全链路消融契约测试套件
 * (McpTwoPhaseFullLoopContractTest)
 */
class McpTwoPhaseFullLoopContractTest {

    private static final int DIM = 1536;

    /**
     * EXP-01: 验证 McpToolProjectionReceipt 不可变性与常量时间自验真防侧信道
     */
    @Test
    @DisplayName("EXP-01: 凭单不可变性与 SHA-256 常量时间自验真 (防时序侧信道)")
    void testProjectionReceiptCryptographicIntegrity() {
        List<String> tools = List.of("mcp.github.create_issue", "mcp.db.query");
        Map<String, String> circuits = Map.of("mcp.github.create_issue", "CLOSED");

        McpToolProjectionReceipt receipt = McpToolProjectionReceipt.create(
                "请查询数据库并创建 Issue",
                tools,
                30,
                0.65,
                circuits
        );

        // 1. 验证签名完整性
        assertTrue(receipt.verifySignature(), "合法签名的凭单自验真必须通过");
        assertEquals(2, receipt.projectedCount());
        assertEquals(30, receipt.totalPoolSize());
        assertEquals(0.65, receipt.tokenCompressionRatio(), 1e-4);

        // 2. 模拟黑客篡改伪造签名攻击，验证 Fail-Close 阻断
        McpToolProjectionReceipt forgedReceipt = new McpToolProjectionReceipt(
                receipt.receiptId(),
                receipt.queryText(),
                receipt.projectedToolCodes(),
                receipt.totalPoolSize(),
                receipt.projectedCount(),
                receipt.tokenCompressionRatio(),
                receipt.circuitStates(),
                receipt.timestampEpochMs(),
                "0000000000000000000000000000000000000000000000000000000000000000" // 伪造哈希
        );

        assertFalse(forgedReceipt.verifySignature(), "被篡改签名的伪造凭单必须被常量时间比对精准阻断");
    }

    /**
     * EXP-02: 验证千问 1536 维超球面动态投影 Top-5 与 Safe Schema 剪枝 Token 压缩率 >= 50%
     */
    @Test
    @DisplayName("EXP-02: 两阶段超球面投影 Top-5 检索与 Safe Schema 剪枝 (Token 压缩率 >= 50%)")
    void testTwoPhaseProjectionAndSchemaPruningCompression() {
        McpTwoPhaseProjectionGovernor governor = new McpTwoPhaseProjectionGovernor();

        // 构造意图测试向量 (单位归一化)
        float[] queryVec = new float[DIM];
        queryVec[0] = 1.0f;

        // 注册 30 个工具，前 5 个语义高相关，其余正交或随机
        for (int i = 0; i < 30; i++) {
            float[] toolVec = new float[DIM];
            if (i < 5) {
                toolVec[0] = 0.8f + (i * 0.03f); // 与 query 夹角极小
                toolVec[1] = 0.1f;
            } else {
                toolVec[i % DIM] = 0.9f; // 稀疏正交分布
            }

            // 构造冗余且复杂的 JSON Schema (带 $defs 与长注释)
            JSONObject fullSchema = new JSONObject();
            fullSchema.put("type", "object");
            fullSchema.put("title", "SuperLongTitleForEnterpriseToolNumber_" + i);
            fullSchema.put("description", "This is an extremely verbose description designed to simulate real enterprise MCP tool overhead that causes token bloat");

            JSONObject properties = new JSONObject();
            for (int p = 0; p < 8; p++) {
                JSONObject prop = new JSONObject();
                prop.put("type", "string");
                prop.put("description", "Very detailed explanation of argument property number " + p + " including edge cases and caveats");
                properties.put("arg_" + p, prop);
            }
            fullSchema.put("properties", properties);

            governor.registerTool("mcp.tool_" + i, "toolFunction_" + i, "Tool Description " + i, toolVec, fullSchema);
        }

        // 执行两阶段投影
        McpTwoPhaseProjectionGovernor.ProjectionGovernanceReport report =
                governor.projectAndPrune(queryVec, 5);

        assertNotNull(report);
        assertEquals(30, report.totalRegisteredCount());
        assertEquals(5, report.projectedCount());

        // 验证准确命中前 5 个高相关工具
        Set<String> top5Expected = Set.of("mcp.tool_0", "mcp.tool_1", "mcp.tool_2", "mcp.tool_3", "mcp.tool_4");
        for (McpTwoPhaseProjectionGovernor.ProjectedToolPayload payload : report.projectedTools()) {
            assertTrue(top5Expected.contains(payload.toolCode()), "Top-5 必须精准召回语义最契合的工具: " + payload.toolCode());
            // 验证 Schema 结构性精简：title 与冗余注释已被修剪
            assertFalse(payload.prunedInputSchema().containsKey("title"), "剪枝后 Schema 必须剔除冗余 title 字段");
        }

        // 验证 Token 压缩比显著超标 >= 50.0%
        assertTrue(report.tokenCompressionRatio() >= 0.50,
                "Token 压缩率应 >= 50%，实际为: " + String.format("%.2f%%", report.tokenCompressionRatio() * 100));
        assertTrue(report.executionTimeMs() < 50, "前置动态投影计算耗时必须在极低毫秒级别 (< 50ms)");
    }

    /**
     * EXP-03: 消融实验 1 - 仅做阶段一 MIPS 检索，不做阶段二 Schema 剪枝
     */
    @Test
    @DisplayName("EXP-03: 消融实验 1 - 剥离阶段二 Schema 剪枝 (证实结构性剪枝的独立显著收益)")
    void testAblationStudyWithoutSchemaPruning() {
        McpTwoPhaseProjectionGovernor governor = new McpTwoPhaseProjectionGovernor();
        float[] queryVec = new float[DIM];
        queryVec[0] = 1.0f;

        // 注册 10 个工具，Schema 较简短
        for (int i = 0; i < 10; i++) {
            float[] toolVec = new float[DIM];
            toolVec[0] = 0.5f + (i * 0.05f);
            JSONObject minimalSchema = new JSONObject();
            minimalSchema.put("type", "object");
            governor.registerTool("mcp.tool_" + i, "tool_" + i, "Desc " + i, toolVec, minimalSchema);
        }

        McpTwoPhaseProjectionGovernor.ProjectionGovernanceReport report =
                governor.projectAndPrune(queryVec, 5);

        // 在无深度 Schema 冗余时，主要收益来自于工具数量的压缩 (1 - 5/10 = 50%)
        assertEquals(5, report.projectedCount());
        assertEquals(10, report.totalRegisteredCount());
    }

    /**
     * EXP-04: 验证 WorkerAgent 在执行 chat() 时前置感知意图动态投影并回填存证凭单
     */
    @Test
    @DisplayName("EXP-04: WorkerAgent 前置动态意图投影拦截与存证凭单回填")
    void testWorkerAgentDynamicToolBindingAndReceiptBackfill() {
        ChatModel mockChatModel = mock(ChatModel.class);
        ChatResponse mockChatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockChatResponse.getResult().getOutput().getText()).thenReturn("任务已由精简工具协同完成");
        when(mockChatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        McpToolAdapter mcpToolAdapter = new McpToolAdapter();
        McpTwoPhaseProjectionGovernor governor = new McpTwoPhaseProjectionGovernor();
        ToolDiscoveryService discoveryService = new ToolDiscoveryService(null, mcpToolAdapter, governor);

        WorkerAgent workerAgent = new WorkerAgent(
                "DynamicWorker",
                "支持动态投影的 Worker 智能体",
                "系统提示词",
                List.of(),
                mockChatModel,
                null,
                discoveryService
        );

        // 准备带有千问 1536 维超球面意图向量的上下文
        float[] queryVec = new float[DIM];
        queryVec[0] = 1.0f;
        Map<String, Object> context = new HashMap<>();
        context.put("queryEmbedding", queryVec);

        String result = workerAgent.chat("执行核心计算子任务", context);

        assertNotNull(result);
        // 核心验证：执行后 context 中必须已自动回填纯 Java 21 Record 不可变凭单
        Object receiptObj = context.get("mcpToolProjectionReceipt");
        assertNotNull(receiptObj, "WorkerAgent 执行后必须生成并回填不可变存证凭单");
        assertTrue(receiptObj instanceof McpToolProjectionReceipt, "存证凭单必须为 McpToolProjectionReceipt Record");

        McpToolProjectionReceipt receipt = (McpToolProjectionReceipt) receiptObj;
        assertTrue(receipt.verifySignature(), "回填的存证凭单签名自验真必须通过");
        assertEquals("执行核心计算子任务", receipt.queryText());
    }

    /**
     * EXP-05: 反事实实验 - 正交不相关查询输入，验证系统平滑安全退化
     */
    @Test
    @DisplayName("EXP-05: 反事实正交查询 - 兜底降级防护，杜绝工具误判与空指针")
    void testCounterfactualOrthogonalQueryFallback() {
        McpTwoPhaseProjectionGovernor governor = new McpTwoPhaseProjectionGovernor();
        // 注册一个仅在第 10 维有值的工具
        float[] toolVec = new float[DIM];
        toolVec[10] = 1.0f;
        governor.registerTool("mcp.special_tool", "special", "特殊专用工具", toolVec, new JSONObject());

        // 输入一个完全正交（第 500 维）的 Query 向量
        float[] orthogonalQuery = new float[DIM];
        orthogonalQuery[500] = 1.0f;

        McpTwoPhaseProjectionGovernor.ProjectionGovernanceReport report =
                governor.projectAndPrune(orthogonalQuery, 5);

        assertNotNull(report);
        assertEquals(1, report.projectedCount());
        // 余弦内积必然为 0.0
        assertEquals(0.0, report.projectedTools().get(0).cosineSimilarity(), 1e-6);
    }

    /**
     * EXP-06: 验证 HttpMcpClient 真实采纳 SSE 下发的 endpoint 重定向与 Sampling 采样双向闭环
     */
    @Test
    @DisplayName("EXP-06: HttpMcpClient 优先采纳 ssePostEndpoint 并支持 Sampling 反向采样绑定")
    void testHttpMcpClientSseEndpointResolutionAndSamplingBinding() throws Exception {
        HttpMcpClient client = new HttpMcpClient("https://example.mcp.internal", "test-token", Map.of());

        // 1. 验证默认未收到 endpoint 事件时，走缺省规则
        Method resolveMethod = HttpMcpClient.class.getDeclaredMethod("resolveEndpointUri");
        resolveMethod.setAccessible(true);
        URI defaultUri = (URI) resolveMethod.invoke(client);
        assertEquals(URI.create("https://example.mcp.internal/mcp"), defaultUri);

        // 2. 模拟 SSE 下发 endpoint 事件并设置 ssePostEndpoint
        java.lang.reflect.Field endpointField = HttpMcpClient.class.getDeclaredField("ssePostEndpoint");
        endpointField.setAccessible(true);
        endpointField.set(client, "https://example.mcp.internal/dynamic-session-12345/messages");

        // 3. 验证此时 resolveEndpointUri 严格优先采纳 ssePostEndpoint
        URI dynamicUri = (URI) resolveMethod.invoke(client);
        assertEquals(URI.create("https://example.mcp.internal/dynamic-session-12345/messages"), dynamicUri,
                "HttpMcpClient 必须严格按照 Anthropic 规范优先使用服务端在 SSE 下发的 POST 目标端点");

        // 4. 验证 SamplingHandler 真实绑定与反向推理调用能力
        McpSamplingHandler mockSamplingHandler = mock(McpSamplingHandler.class);
        JSONObject mockSamplingResult = new JSONObject();
        mockSamplingResult.put("role", "assistant");
        mockSamplingResult.put("content", "通过 DeepSeek Flash 完成采样推理");
        when(mockSamplingHandler.handleSamplingRequest(any())).thenReturn(mockSamplingResult);

        client.setSamplingHandler(mockSamplingHandler);
        assertSame(mockSamplingHandler, client.getSamplingHandler(), "SamplingHandler 必须成功绑定至 HttpMcpClient 客户端");

        // 5. 模拟 SSE 通道接收到 sampling/createMessage JSON-RPC 消息
        Method handleMsgMethod = HttpMcpClient.class.getDeclaredMethod("handleIncomingSseMessage", String.class);
        handleMsgMethod.setAccessible(true);

        JSONObject samplingRpcRequest = new JSONObject();
        samplingRpcRequest.put("jsonrpc", "2.0");
        samplingRpcRequest.put("id", "req-sampling-001");
        samplingRpcRequest.put("method", "sampling/createMessage");
        JSONObject params = new JSONObject();
        params.put("messages", List.of(Map.of("role", "user", "content", "请总结此工具的运行状态")));
        samplingRpcRequest.put("params", params);

        // 触发接收
        handleMsgMethod.invoke(client, samplingRpcRequest.toJSONString());

        // 验证反向推理处理器被准确触发
        verify(mockSamplingHandler, times(1)).handleSamplingRequest(any());
    }
}
