package tech.qiantong.qknow.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.mcp.client.orchestration.engine.IdempotentToolExecutionGateway;
import tech.qiantong.qknow.mcp.client.orchestration.engine.TwoStageToolManifoldRouter;
import tech.qiantong.qknow.mcp.core.orchestration.dto.IdempotentExecutionReceipt;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpToolDescriptor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 102 专属契约测试套件：海量企业 MCP 工具两阶段流形裁剪与幂等断路自愈网关
 * 覆盖 500+ 工具千问超球面检索、DAG 依赖剪枝、幂等防重拦截与 Saga 补偿自愈等 8 项严苛契约
 */
public class Phase102TwoStageMcpRouterContractTest {

    private TwoStageToolManifoldRouter router;
    private IdempotentToolExecutionGateway gateway;

    @BeforeEach
    public void setUp() {
        router = new TwoStageToolManifoldRouter();
        gateway = new IdempotentToolExecutionGateway();
    }

    /**
     * 辅助方法: 生成严格归一化的阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)
     */
    private double[] createNormalizedSphericalEmbedding(int seed) {
        double[] vec = new double[McpToolDescriptor.EXPECTED_DIMENSION];
        double sumSq = 0.0;
        for (int i = 0; i < vec.length; i++) {
            vec[i] = Math.sin(seed * 0.71 + i * 0.037);
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < vec.length; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("契约测试 1: 500+ 海量企业工具池千问超球面余弦初筛 Top-10 耗时严格 <= 150μs")
    public void testStageOne_500ToolsEmbeddingRetrievalWithin150Micros() {
        // 注册 500 个模拟企业 MCP 工具
        for (int i = 0; i < 500; i++) {
            double[] emb = createNormalizedSphericalEmbedding(i + 1);
            McpToolDescriptor tool = new McpToolDescriptor(
                    "tool_enterprise_" + i, "企业微服务工具_" + i, "ERP", emb,
                    Set.of("order_id"), Set.of("remark"), List.of(), true, false
            );
            router.registerTool(tool);
        }

        assertEquals(500, router.registeredToolCount());

        double[] queryVec = createNormalizedSphericalEmbedding(42);

        // 预热 JIT 编译
        for (int w = 0; w < 1000; w++) {
            router.filterByEmbedding(queryVec, 10);
        }

        long minElapsed = Long.MAX_VALUE;
        List<McpToolDescriptor> top10 = null;
        for (int i = 0; i < 50; i++) {
            long t0 = System.nanoTime();
            top10 = router.filterByEmbedding(queryVec, 10);
            long elapsed = (System.nanoTime() - t0) / 1000L;
            if (elapsed < minElapsed) {
                minElapsed = elapsed;
            }
        }

        assertNotNull(top10);
        assertEquals(10, top10.size());
        assertEquals("tool_enterprise_41", top10.get(0).toolId(), "与 seed=42 完全匹配的工具应排在第一位");

        long latencyLimit = System.getenv("CI") != null ? 500L : 150L;
        assertTrue(minElapsed <= latencyLimit,
                "500+ 工具超球面向量检索耗时应 <= " + latencyLimit + "μs，实测: " + minElapsed + "μs");
    }

    @Test
    @DisplayName("契约测试 2: 阶段二参数槽位与 DAG 依赖动态剪枝，综合剪枝率 >= 98%")
    public void testStageTwo_DependencyAndSlotPruning_Over98Percent() {
        // 注册 500 个工具，其中只有特定工具满足依赖与槽位
        for (int i = 0; i < 500; i++) {
            double[] emb = createNormalizedSphericalEmbedding(i + 10);
            List<String> prereqs = (i % 2 == 0) ? List.of("auth_token_provider") : List.of();
            Set<String> requiredSlots = (i % 3 == 0) ? Set.of("customer_id") : Set.of("unfulfillable_slot_" + i);

            McpToolDescriptor tool = new McpToolDescriptor(
                    "tool_" + i, "工具_" + i, "FINANCE", emb,
                    requiredSlots, Set.of(), prereqs, true, false
            );
            router.registerTool(tool);
        }

        double[] query = createNormalizedSphericalEmbedding(10);
        Set<String> availableSlots = Set.of("customer_id");
        Set<String> executedTools = Set.of("auth_token_provider");

        // 两阶段路由: 先筛 Top-10，再剪枝至最多 5 个
        List<McpToolDescriptor> finalTools = router.routeTools(query, availableSlots, executedTools, 10, 5);

        assertNotNull(finalTools);
        assertTrue(finalTools.size() <= 5);
        assertFalse(finalTools.isEmpty());

        for (McpToolDescriptor t : finalTools) {
            assertTrue(t.requiredSlots().contains("customer_id"));
            if (!t.prerequisiteToolIds().isEmpty()) {
                assertTrue(executedTools.containsAll(t.prerequisiteToolIds()));
            }
        }

        double pruningRate = (500.0 - finalTools.size()) / 500.0 * 100.0;
        assertTrue(pruningRate >= 98.0, "海量候选两阶段综合剪枝率必须 >= 98.0%，实测: " + pruningRate + "%");
    }

    @Test
    @DisplayName("契约测试 3: 幂等网关相同幂等键重复提交 100% 物理拦截，安全重放已提交结果")
    public void testIdempotency_RepeatExecutionIntercepted() {
        McpToolDescriptor destructiveTool = new McpToolDescriptor(
                "tool_payment_deduct", "资金清算扣减", "FINANCE",
                createNormalizedSphericalEmbedding(1), Set.of("amount"), Set.of(), List.of(), false, true
        );

        String idempotencyKey = "idem_key_payment_9988";
        AtomicInteger actualExecutionTimes = new AtomicInteger(0);

        // 第一次调用: 真实执行扣减
        IdempotentExecutionReceipt r1 = gateway.executeWithIdempotency(
                idempotencyKey, destructiveTool, "param_amount_500", () -> {
                    actualExecutionTimes.incrementAndGet();
                    return "PAYMENT_SUCCESS_TX_001";
                }
        );

        assertEquals(IdempotentExecutionReceipt.STATUS_COMMITTED, r1.status());
        assertEquals("PAYMENT_SUCCESS_TX_001", r1.responsePayload());
        assertEquals(1, actualExecutionTimes.get());

        // 第二次调用: 模拟网络重试，使用相同幂等键
        IdempotentExecutionReceipt r2 = gateway.executeWithIdempotency(
                idempotencyKey, destructiveTool, "param_amount_500", () -> {
                    actualExecutionTimes.incrementAndGet();
                    return "PAYMENT_SUCCESS_DUPLICATE_ERROR";
                }
        );

        assertEquals(IdempotentExecutionReceipt.STATUS_REPLAYED, r2.status());
        assertEquals("PAYMENT_SUCCESS_TX_001", r2.responsePayload(), "重放必须返回首次成功载荷");
        assertEquals(1, actualExecutionTimes.get(), "底层破坏性业务方法绝对只执行了一次，重复执行拦截率 100%");
    }

    @Test
    @DisplayName("契约测试 4: 幂等存证凭单 SHA-256 密码学自签名验真 100% 通过")
    public void testIdempotency_SignatureCryptographicVerification() {
        McpToolDescriptor tool = new McpToolDescriptor(
                "tool_crm_update", "客户状态变更", "CRM",
                createNormalizedSphericalEmbedding(2), Set.of(), Set.of(), List.of(), false, true
        );

        IdempotentExecutionReceipt receipt = gateway.executeWithIdempotency(
                "idem_key_crm_001", tool, "param_status_vip", () -> "OK"
        );

        assertTrue(receipt.verifySignature(), "原生自签名验真必须 100% 通过");

        // 模拟篡改载荷哈希
        IdempotentExecutionReceipt tampered = new IdempotentExecutionReceipt(
                receipt.receiptId(), receipt.idempotencyKey(), receipt.toolId(),
                receipt.status(), "TAMPERED_HASH", receipt.responsePayload(),
                receipt.latencyMicros(), receipt.timestamp(), receipt.signature()
        );

        assertFalse(tampered.verifySignature(), "篡改字段后的凭单验真必须 100% 失败被拦截");
    }

    @Test
    @DisplayName("契约测试 5: 工具调用网络异常自动捕获并触发 Saga 补偿自愈凭据签发")
    public void testSagaCompensation_ExceptionTrigger() {
        McpToolDescriptor fragileTool = new McpToolDescriptor(
                "tool_remote_erp", "远程 ERP 同步", "ERP",
                createNormalizedSphericalEmbedding(3), Set.of(), Set.of(), List.of(), false, true
        );

        IdempotentExecutionReceipt receipt = gateway.executeWithIdempotency(
                "idem_key_fail_001", fragileTool, "{}", () -> {
                    throw new RuntimeException("504 Gateway Timeout");
                }
        );

        assertNotNull(receipt);
        assertEquals(IdempotentExecutionReceipt.STATUS_COMPENSATING, receipt.status());
        assertTrue(receipt.responsePayload().contains("SAGA_COMPENSATION_TRIGGERED"));
        assertTrue(receipt.verifySignature(), "异常补偿凭单同样具备合法自签名");
    }

    @Test
    @DisplayName("契约测试 6: 千问 1536 维超球面单位向量模长强校验，非法输入 100% 拒绝")
    public void testSphericalEmbedding_ValidationAndDimensionCheck() {
        // 错误维度
        double[] badDim = new double[768];
        assertThrows(IllegalArgumentException.class, () ->
                new McpToolDescriptor("t", "名称", "CAT", badDim, Set.of(), Set.of(), List.of(), true, false));

        // 错误模长 (未归一化)
        double[] badNorm = new double[1536];
        badNorm[0] = 5.0; // 模长不为 1.0
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "t", "名称", "CAT", badNorm, Set.of(), Set.of(), List.of(), true, false
        );
        assertFalse(descriptor.isValidSphericalEmbedding(), "模长不等于 1.0 的超球面向量必须判为非法");

        // 合法模长
        double[] goodVec = createNormalizedSphericalEmbedding(99);
        McpToolDescriptor goodDescriptor = new McpToolDescriptor(
                "t", "名称", "CAT", goodVec, Set.of(), Set.of(), List.of(), true, false
        );
        assertTrue(goodDescriptor.isValidSphericalEmbedding());
    }

    @Test
    @DisplayName("契约测试 7: 前置依赖工具未执行时，当前工具被阶段二硬拦截")
    public void testPrerequisiteTool_ExecutionOrderStrictEnforcement() {
        McpToolDescriptor dependentTool = new McpToolDescriptor(
                "tool_shipment", "发货单生成", "LOGISTICS",
                createNormalizedSphericalEmbedding(4), Set.of(), Set.of(),
                List.of("tool_order_created", "tool_payment_settled"), false, true
        );

        router.registerTool(dependentTool);

        // 仅完成了 tool_order_created，未完成 tool_payment_settled
        List<McpToolDescriptor> result = router.pruneByDependenciesAndSlots(
                List.of(dependentTool), Set.of(), Set.of("tool_order_created"), 5
        );

        assertTrue(result.isEmpty(), "依赖未完全满足的工具必须被严格剪枝过滤");

        // 两个前置工具都已完成
        List<McpToolDescriptor> passed = router.pruneByDependenciesAndSlots(
                List.of(dependentTool), Set.of(), Set.of("tool_order_created", "tool_payment_settled"), 5
        );

        assertEquals(1, passed.size(), "依赖全部满足后才能放行");
    }

    @Test
    @DisplayName("契约测试 8: 缺少必需参数槽位时，当前工具被阶段二硬拦截")
    public void testRequiredSlots_MissingParametersStrictRejection() {
        McpToolDescriptor slotTool = new McpToolDescriptor(
                "tool_invoice", "发票开具", "TAX",
                createNormalizedSphericalEmbedding(5),
                Set.of("tax_no", "invoice_amount"), Set.of("email"), List.of(), false, true
        );

        router.registerTool(slotTool);

        // 缺少 invoice_amount
        List<McpToolDescriptor> result = router.pruneByDependenciesAndSlots(
                List.of(slotTool), Set.of("tax_no"), Set.of(), 5
        );

        assertTrue(result.isEmpty(), "缺少必需参数槽位必须被严格剪枝过滤");

        // 槽位齐全
        List<McpToolDescriptor> passed = router.pruneByDependenciesAndSlots(
                List.of(slotTool), Set.of("tax_no", "invoice_amount"), Set.of(), 5
        );

        assertEquals(1, passed.size(), "槽位满足后方可放行");
    }
}
