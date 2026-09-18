package tech.qiantong.qknow.mcp.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tech.qiantong.qknow.mcp.client.model.McpExecutionReceipt;
import tech.qiantong.qknow.mcp.client.routing.SemanticToolRegistry;
import tech.qiantong.qknow.mcp.client.routing.ToolEmbeddingEntry;
import tech.qiantong.qknow.mcp.client.routing.ToolRagFilter;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor.RiskLevel;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor.SafetyDecisionBO;
import tech.qiantong.qknow.mcp.client.transport.StdioEnterpriseTransport;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 103 全链路协同集成测试 (Phase103McpIntegrationTest)")
class Phase103McpIntegrationTest {

    private static float[] createUnitHypersphereVector(double angleRad) {
        float[] v = new float[1536];
        v[0] = (float) Math.cos(angleRad);
        v[1] = (float) Math.sin(angleRad);
        return v;
    }

    @Test
    @DisplayName("全链路场景1: 测地剪枝 -> 安全门禁放行 -> 进程传输调用 -> 签发凭据与验真")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testFullChainSafeToolExecution() throws Exception {
        // 1. 初始化千问超球面注册表并注册 100+ 个工具元数据
        SemanticToolRegistry registry = new SemanticToolRegistry();

        // 注册目标高相关只读工具 (角度 0.05 弧度，测地散度很小)
        registry.registerTool(new ToolEmbeddingEntry(
                "query_financial_reports",
                "finance-srv",
                "查询企业季度财务与收入报表",
                "{\"type\":\"object\",\"properties\":{\"quarter\":{\"type\":\"string\"}}}",
                createUnitHypersphereVector(0.05),
                RiskLevel.READ_ONLY
        ));

        // 注册 100 个无关干扰工具 (角度在 1.5 ~ 2.8 弧度，测地散度 > 0.45)
        for (int i = 1; i <= 100; i++) {
            registry.registerTool(new ToolEmbeddingEntry(
                    "irrelevant_tool_" + i,
                    "distractor-srv",
                    "无关业务工具 " + i,
                    "{}",
                    createUnitHypersphereVector(1.5 + (i % 20) * 0.05),
                    RiskLevel.READ_ONLY
            ));
        }

        // 2. 执行 Tool RAG 测地动态剪枝 (Top-5)
        ToolRagFilter filter = new ToolRagFilter(registry);
        float[] queryVec = createUnitHypersphereVector(0.0);
        long startScan = System.nanoTime();
        var filterResult = filter.pruneTools(queryVec, 5, 0.35);
        long scanDurationMs = (System.nanoTime() - startScan) / 1_000_000;

        assertTrue(scanDurationMs <= 5, "千问超球面 100+ 工具内存扫描应 <= 5ms");
        assertNotNull(filterResult);
        assertFalse(filterResult.selectedTools().isEmpty(), "应命中相关候选工具");
        assertEquals("query_financial_reports", filterResult.selectedTools().get(0).toolName(), "首选必须为目标财务查询工具");
        assertTrue(filterResult.tokenSavingsRatio() >= 0.80, "上下文体积压缩率必须 >= 80%");

        // 3. 安全门禁 RBAC 鉴权
        HighRiskToolSafetyGovernor governor = new HighRiskToolSafetyGovernor();
        SafetyDecisionBO decision = governor.evaluateAndIntercept(
                "finance-srv",
                "query_financial_reports",
                Map.of("quarter", "2026-Q3"),
                RiskLevel.READ_ONLY,
                "ROLE_OPERATOR",
                "alice"
        );
        assertTrue(decision.allowed(), "只读安全工具应直接放行");
        assertFalse(decision.approvalRequired());

        // 4. 启动轻量 MCP Stdio 进程执行真实通信
        List<String> command = List.of(
                "python3", "-u", "-c",
                "import sys, json\n" +
                "for line in sys.stdin:\n" +
                "    req = json.loads(line.strip())\n" +
                "    resp = {'jsonrpc': '2.0', 'id': req.get('id'), 'result': {'total_revenue': '98.5M', 'net_profit': '24.2M'}}\n" +
                "    print(json.dumps(resp), flush=True)\n"
        );
        StdioEnterpriseTransport transport = new StdioEnterpriseTransport(command);
        transport.start();

        try {
            long callStart = System.currentTimeMillis();
            JsonRpcResponse response = transport.sendRequest("tools/call", Map.of(
                    "name", "query_financial_reports",
                    "arguments", Map.of("quarter", "2026-Q3")
            )).get(3, TimeUnit.SECONDS);

            long callDurationMs = System.currentTimeMillis() - callStart;
            assertNotNull(response);
            assertNull(response.error());

            // 5. 签发 SHA-256 不可变存证审计凭单
            McpExecutionReceipt receipt = McpExecutionReceipt.createSigned(
                    "RCP-PHASE-103-001",
                    "STDIO",
                    "finance-srv",
                    "query_financial_reports",
                    HighRiskToolSafetyGovernor.computeArgumentHash(Map.of("quarter", "2026-Q3")),
                    RiskLevel.READ_ONLY.name(),
                    "NOT_REQUIRED",
                    "SYSTEM_AUTO",
                    "SUCCESS",
                    callDurationMs * 1_000_000L,
                    System.currentTimeMillis()
            );

            assertNotNull(receipt.receiptId());
            assertNotNull(receipt.signature());
            assertEquals(64, receipt.signature().length());
            assertTrue(receipt.verifySignature(), "存证凭单自签名验真必须 100% 通过");
        } finally {
            transport.close();
        }
    }

    @Test
    @DisplayName("全链路场景2: 破坏性高危操作拦截 -> HITL 审批挂起 -> 管理员审批通过 -> 执行并签发审计凭单")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testFullChainDestructiveToolSuspensionAndApproval() throws Exception {
        HighRiskToolSafetyGovernor governor = new HighRiskToolSafetyGovernor();

        // 1. 尝试直接执行高危破坏性操作
        Map<String, Object> dangerousArgs = Map.of("targetCluster", "prod-primary-db", "force", true);
        SafetyDecisionBO decision = governor.evaluateAndIntercept(
                "srv-db",
                "purge_production_database",
                dangerousArgs,
                RiskLevel.HIGH_RISK_DESTRUCTIVE,
                "ROLE_ADMIN",
                "bob"
        );

        // 验证 100% 物理拦截与工单挂起
        assertFalse(decision.allowed(), "高危操作绝不能直接放行");
        assertTrue(decision.approvalRequired(), "必须要求人机二次审批");
        assertNotNull(decision.ticketId(), "必须生成待审批工单号");

        // 2. 模拟管理员审核并批准工单
        boolean approved = governor.submitApprovalDecision(decision.ticketId(), true, "chief-security-officer");
        assertTrue(approved, "工单应被批准");

        // 3. 执行经人机批准的 MCP 任务
        List<String> command = List.of(
                "python3", "-u", "-c",
                "import sys, json\n" +
                "for line in sys.stdin:\n" +
                "    req = json.loads(line.strip())\n" +
                "    resp = {'jsonrpc': '2.0', 'id': req.get('id'), 'result': {'purged_rows': 0, 'dryRun': True}}\n" +
                "    print(json.dumps(resp), flush=True)\n"
        );
        StdioEnterpriseTransport transport = new StdioEnterpriseTransport(command);
        transport.start();

        try {
            JsonRpcResponse response = transport.sendRequest("tools/call", Map.of(
                    "name", "purge_production_database",
                    "arguments", dangerousArgs,
                    "approvalTicket", decision.ticketId()
            )).get(3, TimeUnit.SECONDS);

            assertNotNull(response);

            // 4. 签发高危操作审计凭单并验真
            McpExecutionReceipt receipt = McpExecutionReceipt.createSigned(
                    "RCP-PHASE-103-HITL",
                    "STDIO",
                    "srv-db",
                    "purge_production_database",
                    HighRiskToolSafetyGovernor.computeArgumentHash(dangerousArgs),
                    RiskLevel.HIGH_RISK_DESTRUCTIVE.name(),
                    "APPROVED",
                    "chief-security-officer",
                    "SUCCESS",
                    15_000_000L,
                    System.currentTimeMillis()
            );

            assertTrue(receipt.verifySignature(), "高危操作审计凭单必须密码学验真通过");
            assertEquals(RiskLevel.HIGH_RISK_DESTRUCTIVE.name(), receipt.riskLevel());
            assertEquals("APPROVED", receipt.approvalStatus());
            assertEquals("chief-security-officer", receipt.approverUserId());
        } finally {
            transport.close();
        }
    }

    @Test
    @DisplayName("全链路场景3: RBAC 角色权限拒绝 -> 彻底阻断不进入传输通道")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testFullChainRbacUnauthorizedDenial() {
        HighRiskToolSafetyGovernor governor = new HighRiskToolSafetyGovernor();

        // 访客角色尝试调用需权限的低危写工具
        SafetyDecisionBO decision = governor.evaluateAndIntercept(
                "srv-gw",
                "modify_production_gateway",
                Map.of("route", "/api/v2"),
                RiskLevel.LOW_RISK,
                "ROLE_GUEST",
                "charlie_anonymous"
        );

        assertFalse(decision.allowed(), "越权操作必须被直接阻断拒绝");
        assertFalse(decision.approvalRequired(), "越权操作不得进入二次审批挂起");
        assertEquals("ERR_MCP_ACCESS_DENIED", decision.rejectionCode(), "拒绝码应为 ERR_MCP_ACCESS_DENIED");
    }
}
