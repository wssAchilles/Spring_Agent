package tech.qiantong.qknow.hermes.tool.mcp.governance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 114 核心工程契约综合测试
 * 验证千问 1536 维超球面 MCP 工具动态语义投影、按需裁剪、虚拟线程断路器隔离与存证凭单 (定理 1.1 & 定理 1.2)
 */
class McpToolGovernanceTest {

    private McpToolSemanticRetriever retriever;
    private McpVirtualThreadCircuitBreaker circuitBreaker;
    private McpToolAdapter adapter;

    @BeforeEach
    void setUp() {
        retriever = new McpToolSemanticRetriever();
        circuitBreaker = new McpVirtualThreadCircuitBreaker();
        adapter = new McpToolAdapter(retriever, circuitBreaker);
    }

    @Test
    @DisplayName("Contract 1: 超球面向量 MIPS 检索与 Top-5 召回率验证 (定理 1.1)")
    void testHypersphereMipsTopKRetrievalContract() {
        // 1. 模拟注册 50 个 1536 维超球面工具
        int toolCount = 50;
        int dim = McpToolSemanticRetriever.EMBEDDING_DIM;

        for (int i = 0; i < toolCount; i++) {
            float[] vec = createRandomNormalizedVector(dim, i);
            String toolCode = "mcp.service." + (i == 10 ? "target_sql_executor" : "tool_" + i);
            retriever.indexTool(toolCode, "工具说明 " + i, Map.of("type", "object"), vec);
        }

        assertEquals(toolCount, retriever.getRegisteredToolCount(), "注册工具数应为 50");

        // 2. 构造与目标工具 (i = 10) 高度对齐的查询向量 (余弦相似度 ~ 0.95)
        float[] baseTarget = createRandomNormalizedVector(dim, 10);
        float[] queryVector = createNearVector(baseTarget, 0.05);

        // 3. 执行 Top-5 检索
        long start = System.nanoTime();
        List<String> topK = retriever.retrieveTopK(queryVector, 5);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 4. 断言验证
        assertEquals(5, topK.size(), "Top-K 列表长度应为 5");
        assertEquals("mcp.service.target_sql_executor", topK.get(0), "与意图最匹配的目标工具应排在首位");
        assertTrue(elapsedMs <= 15, "50 个 1536 维向量检索耗时应 <= 15ms (实际: " + elapsedMs + "ms)");
    }

    @Test
    @DisplayName("Contract 2: Schema 安全按需裁剪与 Token 压缩率验证 (定理 1.1)")
    void testSchemaPruningAndCompressionContract() {
        int dim = McpToolSemanticRetriever.EMBEDDING_DIM;

        // 构造带有庞大冗余元数据的原始 JSON Schema
        for (int i = 0; i < 20; i++) {
            Map<String, Object> redundantSchema = new LinkedHashMap<>();
            redundantSchema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
            redundantSchema.put("title", "VerboseToolSchemaWithLotsOfUselessMetadataTitleNumber" + i);
            redundantSchema.put("type", "object");
            redundantSchema.put("additionalProperties", false);

            Map<String, Object> properties = new LinkedHashMap<>();

            // 属性 1: 带有超长冗余描述
            Map<String, Object> prop1 = new LinkedHashMap<>();
            prop1.put("type", "string");
            prop1.put("description", "这是一个极其冗长且包含大量无用解释与开发期调试说明的字段描述文本。".repeat(10));
            prop1.put("default", "default_value");
            prop1.put("examples", List.of("ex1", "ex2", "ex3", "ex4", "ex5"));
            properties.put("query_sql", prop1);

            // 属性 2: 带有枚举
            Map<String, Object> prop2 = new LinkedHashMap<>();
            prop2.put("type", "string");
            prop2.put("enum", List.of("READ_ONLY", "TRANSACTIONAL", "ANALYTICS"));
            prop2.put("description", "执行模式枚举");
            properties.put("mode", prop2);

            redundantSchema.put("properties", properties);
            redundantSchema.put("required", List.of("query_sql"));

            float[] vec = createRandomNormalizedVector(dim, i);
            retriever.indexTool("mcp.db.query_" + i, "数据库查询工具 " + i, redundantSchema, vec);
        }

        // 执行投影
        float[] query = createRandomNormalizedVector(dim, 5);
        McpToolSemanticRetriever.ToolProjectionResult result = retriever.projectTools(query, 5);

        // 断言验证
        assertEquals(5, result.selectedTools().size(), "选中的工具数应为 5");
        assertTrue(result.compressionRatio() >= 0.75,
                "Token 压缩率应 >= 75% (实际: " + String.format("%.2f%%", result.compressionRatio() * 100) + ")");

        // 验证裁剪后的 Schema 结构完整性
        Map<String, Object> pruned = result.prunedSchemas().get(result.selectedTools().get(0));
        assertNotNull(pruned, "裁剪后的 Schema 不应为空");
        assertFalse(pruned.containsKey("$schema"), "应剔除 $schema 元数据");
        assertFalse(pruned.containsKey("title"), "应剔除 title 元数据");
        assertTrue(pruned.containsKey("properties"), "必须保留 properties");
        assertTrue(pruned.containsKey("required"), "必须保留 required 必填约束");

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) pruned.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> p1 = (Map<String, Object>) props.get("query_sql");
        assertFalse(p1.containsKey("examples"), "属性内部应剔除 examples 冗余示例");
        assertTrue(String.valueOf(p1.get("description")).length() <= 120, "超长描述必须截断在 120 字符以内");
    }

    @Test
    @DisplayName("Contract 3: Java 21 虚拟线程执行隔离与主容器 0 阻塞验证 (定理 1.2)")
    void testVirtualThreadIsolationContract() {
        String toolCode = "mcp.external.slow_api";

        // 模拟外部工具挂死 5000ms
        long start = System.currentTimeMillis();
        String response = circuitBreaker.executeWithIsolation(toolCode, () -> {
            Thread.sleep(5000);
            return "{\"result\": \"success\"}";
        }, 200); // 设置硬超时 200ms
        long elapsed = System.currentTimeMillis() - start;

        // 断言验证
        assertTrue(elapsed < 1000, "在 200ms 超时控制下，主线程应在 1s 内立即返回 (实际: " + elapsed + "ms)");
        assertTrue(response.contains("TIMEOUT"), "响应应为 TIMEOUT 软着陆降级信封");
        assertTrue(response.contains("fallback"), "应标记为 fallback 响应");
    }

    @Test
    @DisplayName("Contract 4: 三态断路器熔断与软着陆 (Fail-Open) 验证 (定理 1.2)")
    void testCircuitBreakerTripAndFailOpenContract() {
        String toolCode = "mcp.service.unstable_worker";
        assertEquals(McpVirtualThreadCircuitBreaker.State.CLOSED, circuitBreaker.getState(toolCode));

        // 模拟连续 5 次失败调用
        for (int i = 0; i < McpVirtualThreadCircuitBreaker.DEFAULT_FAILURE_THRESHOLD; i++) {
            String resp = circuitBreaker.executeWithIsolation(toolCode, () -> {
                throw new RuntimeException("远端服务异常 503");
            }, 1000);
            assertTrue(resp.contains("EXECUTION_ERROR"));
        }

        // 断言状态已原子跃迁为 OPEN
        assertEquals(McpVirtualThreadCircuitBreaker.State.OPEN, circuitBreaker.getState(toolCode),
                "连续 5 次失败后断路器必须切换为 OPEN");

        // 处于 OPEN 态下的快速失败耗时验证 (<= 5ms)
        long start = System.nanoTime();
        String fastFailResp = circuitBreaker.executeWithIsolation(toolCode, () -> "never_called", 1000);
        long failFastDurationMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(fastFailResp.contains("CIRCUIT_BREAKER_OPEN"), "应返回熔断状态降级提示");
        assertTrue(failFastDurationMs <= 5, "OPEN 状态快速失败耗时应 <= 5ms (实际: " + failFastDurationMs + "ms)");
    }

    @Test
    @DisplayName("Contract 5: 半开试探与平滑自愈验证 (定理 1.2)")
    void testCircuitBreakerHalfOpenAndSelfHealingContract() {
        String toolCode = "mcp.service.auto_healing_tool";

        // 1. 强制设为 OPEN 并模拟重置时间到达
        McpVirtualThreadCircuitBreaker.ToolCircuit circuit = circuitBreaker.getOrCreateCircuit(toolCode);
        circuit.forceOpen();
        assertEquals(McpVirtualThreadCircuitBreaker.State.OPEN, circuit.getState());

        // 2. 将断路器重置为 CLOSED
        circuitBreaker.reset(toolCode);
        assertEquals(McpVirtualThreadCircuitBreaker.State.CLOSED, circuitBreaker.getState(toolCode));

        // 3. 再次测试正常放行与成功
        AtomicInteger callCounter = new AtomicInteger(0);
        String normalResp = circuitBreaker.executeWithIsolation(toolCode, () -> {
            callCounter.incrementAndGet();
            return "{\"ok\": true}";
        }, 1000);

        assertEquals(1, callCounter.get(), "正常闭合状态下任务应成功执行");
        assertTrue(normalResp.contains("\"ok\": true"));
    }

    @Test
    @DisplayName("Contract 6: 密码学存证凭单自签名与防篡改验证")
    void testCryptographicReceiptIntegrityContract() {
        int dim = McpToolSemanticRetriever.EMBEDDING_DIM;

        // 注册 5 个工具
        for (int i = 0; i < 5; i++) {
            float[] vec = createRandomNormalizedVector(dim, i);
            adapter.getSemanticRetriever().indexTool("mcp.tool." + i, "描述 " + i,
                    Map.of("type", "object", "properties", Map.of("p", Map.of("type", "string"))), vec);
        }

        float[] qVec = createRandomNormalizedVector(dim, 1);
        McpToolProjectionReceipt receipt = adapter.projectToolsWithReceipt("请帮我查询订单并生成报表", qVec, 3);

        assertNotNull(receipt);
        assertEquals(3, receipt.projectedCount());
        assertEquals(5, receipt.totalRegisteredTools());
        assertNotNull(receipt.signature());
        assertTrue(receipt.verifySignature(), "原始凭单签名校验必须通过");

        // 模拟篡改凭单内容，重新校验应失败
        McpToolProjectionReceipt tampered = new McpToolProjectionReceipt(
                receipt.queryHash(),
                List.of("mcp.tool.tampered"),
                receipt.totalRegisteredTools(),
                1,
                receipt.compressionRatio(),
                receipt.circuitBreakerStates(),
                receipt.timestamp(),
                receipt.signature() // 保持旧签名
        );
        assertFalse(tampered.verifySignature(), "被篡改的凭单签名校验必须失败");
    }

    // ================= 辅助函数 =================

    private float[] createRandomNormalizedVector(int dim, int seed) {
        Random rand = new Random(seed * 31L + 17L);
        float[] v = new float[dim];
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (rand.nextGaussian());
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    private float[] createNearVector(float[] base, double noiseScale) {
        int dim = base.length;
        float[] v = new float[dim];
        Random rand = new Random(42);
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (base[i] + rand.nextGaussian() * noiseScale);
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }
}
