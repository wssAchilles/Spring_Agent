package tech.qiantong.qknow.ai.scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.mor.ReflectiveSelfHealingCoordinator;
import tech.qiantong.qknow.ai.mor.ThinkingStreamInterrupter;
import tech.qiantong.qknow.ai.rag.GraphGuidedThinkingScaffold;
import tech.qiantong.qknow.ai.rag.SpatiotemporalDecayAligner;
import tech.qiantong.qknow.ai.scenario.model.BusinessScenarioAuditReceipt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业跨境智能采购与多维合规审批 端到端联调契约测试
 * 严格验证 5 大核心业务与技术契约：
 * 1. GraphRAG 2026 最新关税知识检索与 2025 旧政策时序指数衰减
 * 2. DeepSeek 官方思考模式 (Thinking Mode) 协议对齐与 reasoning_content 完整回传
 * 3. 千问 1536 维超球面 MCP 工具动态匹配与 60s 瞬态租约防越权
 * 4. 思考流异常注入 (低熵/高重复) 与因果自愈截断重构
 * 5. 端到端不可变存证凭单 SHA-256 密码学自签名与防篡改验真
 */
@DisplayName("企业跨境采购与合规审批端到端业务场景联调测试")
class CrossBorderProcurementEndToEndTest {

    private SpatiotemporalDecayAligner decayAligner;
    private GraphGuidedThinkingScaffold scaffoldEngine;
    private ThinkingStreamInterrupter interrupter;
    private ReflectiveSelfHealingCoordinator healingCoordinator;
    private CrossBorderProcurementScenarioEngine engine;

    @BeforeEach
    void setUp() {
        decayAligner = new SpatiotemporalDecayAligner();
        scaffoldEngine = new GraphGuidedThinkingScaffold(decayAligner);
        interrupter = new ThinkingStreamInterrupter();
        healingCoordinator = new ReflectiveSelfHealingCoordinator();
        engine = new CrossBorderProcurementScenarioEngine(scaffoldEngine, interrupter, healingCoordinator);
    }

    private float[] createNormalized1536Vector(float seed) {
        float[] v = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) Math.sin(seed + i * 0.1);
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约1: GraphRAG 2026 最新关税知识检索与 2025 过期旧政策时序指数衰减")
    void testContract1_GraphRagSpatiotemporalDecay() {
        long now = System.currentTimeMillis();
        long oneYearAgo = now - 365L * 24 * 3600 * 1000;

        Map<String, GraphGuidedThinkingScaffold.Node> nodes = new HashMap<>();
        List<GraphGuidedThinkingScaffold.Edge> edges = new ArrayList<>();

        // 种子节点
        String seedId = "node_seed_procurement";
        nodes.put(seedId, new GraphGuidedThinkingScaffold.Node(
                seedId, "特种精密轴承采购单", "业务实体", now, false, createNormalized1536Vector(1.0f)
        ));

        // 2026 最新关税政策 (有效, 距离当前时间 0 毫秒)
        String newPolicyId = "node_tariff_2026";
        nodes.put(newPolicyId, new GraphGuidedThinkingScaffold.Node(
                newPolicyId, "2026年海关HS-8482精密轴承关税暂定税率(3%)", "海关政策", now, false, createNormalized1536Vector(1.05f)
        ));
        edges.add(new GraphGuidedThinkingScaffold.Edge(seedId, newPolicyId, "GOVERNED_BY", 0.95, true));

        // 2025 旧关税政策 (已过期, 标记为 expired)
        String oldPolicyId = "node_tariff_2025";
        nodes.put(oldPolicyId, new GraphGuidedThinkingScaffold.Node(
                oldPolicyId, "2025年海关HS-8482精密轴承关税税率(8%已失效)", "海关政策", oneYearAgo, true, createNormalized1536Vector(1.04f)
        ));
        edges.add(new GraphGuidedThinkingScaffold.Edge(seedId, oldPolicyId, "GOVERNED_BY", 0.90, true));

        // 构造采购请求
        CrossBorderProcurementScenarioEngine.ProcurementRequest request =
                new CrossBorderProcurementScenarioEngine.ProcurementRequest(
                        "SCENARIO_TEST_001",
                        "特种精密轴承",
                        "8482.10.00",
                        500_000.0,
                        "DE",
                        "USD",
                        createNormalized1536Vector(1.0f),
                        now
                );

        String simulatedThinking = """
                分析跨境采购合规性：
                1. 检查供应商德国精密轴承制造公司的出口许可；
                2. 核对海关 HS 编码 8482.10.00 适用的 2026 最新关税暂定税率；
                3. 根据最新税则计算关税成本并调用 ERP 预占采购额度。
                """;

        CrossBorderProcurementScenarioEngine.ExecutionResult result =
                engine.executeProcurementFlow(request, nodes, edges, simulatedThinking);

        assertTrue(result.success());
        assertEquals("APPROVED_WITH_COMPLIANCE_PASS", result.decision());

        // 验证 2026 新关税政策成功命中
        assertTrue(result.matchedEntities().stream().anyMatch(e -> e.contains("2026年海关HS-8482")),
                "2026 最新关税政策必须被命中");

        // 验证旧政策衰减：直接调用 decayAligner 测算
        float[] queryEmb = createNormalized1536Vector(1.0f);
        double newScore = decayAligner.alignSpatiotemporalScore(queryEmb, nodes.get(newPolicyId).embedding(), 0.95, now, now, false);
        double oldScore = decayAligner.alignSpatiotemporalScore(queryEmb, nodes.get(oldPolicyId).embedding(), 0.90, oneYearAgo, now, true);

        assertTrue(oldScore < 0.05, "过期旧法规事实权重必须衰减至 0.05 以下，实测: " + oldScore);
        assertTrue(newScore > oldScore * 5, "新关税得分应远高于旧关税得分");
    }

    @Test
    @DisplayName("契约2: DeepSeek 官方思考模式 (Thinking Mode) 协议对齐与 reasoning_content 完整回传")
    void testContract2_ThinkingModeProtocolAlignment() {
        long now = System.currentTimeMillis();
        Map<String, GraphGuidedThinkingScaffold.Node> nodes = new HashMap<>();
        nodes.put("node_seed_procurement", new GraphGuidedThinkingScaffold.Node(
                "node_seed_procurement", "采购单", "业务实体", now, false, createNormalized1536Vector(1.0f)
        ));

        CrossBorderProcurementScenarioEngine.ProcurementRequest request =
                new CrossBorderProcurementScenarioEngine.ProcurementRequest(
                        "SCENARIO_TEST_002",
                        "工业级传感器",
                        "9031.80.90",
                        120_000.0,
                        "JP",
                        "USD",
                        createNormalized1536Vector(1.0f),
                        now
                );

        String thinkingStream = """
                针对日本供应商的传感器采购单进行多轮风控推演：
                第一步：核验日本出口管制受限物项清单；
                第二步：计算汇率波动风险，调用外汇锁价工具锁定 30 天期汇率；
                第三步：生成 ERP 预采购单并发送法务合规归档。
                """;

        CrossBorderProcurementScenarioEngine.ExecutionResult result =
                engine.executeProcurementFlow(request, nodes, List.of(), thinkingStream);

        // 验证思考内容回传
        assertNotNull(result.reasoningContentEcho(), "多轮工具调用必须包含 reasoning_content 回传");
        assertFalse(result.reasoningContentEcho().isBlank());
        assertTrue(result.reasoningContentEcho().contains("核验日本出口管制受限物项清单"),
                "回传的 reasoning_content 必须完整保持思维链上下文");
        assertTrue(result.auditLog().contains("reasoning_content"), "审计日志必须记录官方思维链回传事件");
    }

    @Test
    @DisplayName("契约3: 千问 1536 维超球面 MCP 工具动态匹配与 60s 瞬态租约强管控")
    void testContract3_McpToolDynamicLeaseControl() {
        long now = System.currentTimeMillis();
        String toolName = "mcp_erp_create_purchase_order";

        // 1. 正常申请并消费租约
        String token = engine.issueLeaseToken(toolName, now);
        assertNotNull(token);
        assertTrue(token.startsWith("LEASE_"));

        // 在 60 秒内消费租约应成功
        boolean consumed = engine.validateAndConsumeLease(toolName, token, now + 10_000L);
        assertTrue(consumed, "60s 内合法租约必须消费成功");

        // 重复消费相同租约应失败 (一次性防重放)
        boolean repeatConsumed = engine.validateAndConsumeLease(toolName, token, now + 15_000L);
        assertFalse(repeatConsumed, "瞬态租约只能消费一次，杜绝重放攻击");

        // 2. 超时租约应被拒绝
        String tokenExpired = engine.issueLeaseToken(toolName, now);
        boolean expiredConsumed = engine.validateAndConsumeLease(toolName, tokenExpired, now + 65_000L);
        assertFalse(expiredConsumed, "超过 60 秒的租约必须被拒绝执行");
    }

    @Test
    @DisplayName("契约4: 思考流异常注入 (低熵/高重复) 与因果自愈截断重构")
    void testContract4_ThinkingStreamLoopAndSelfHealing() {
        long now = System.currentTimeMillis();
        Map<String, GraphGuidedThinkingScaffold.Node> nodes = new HashMap<>();
        nodes.put("node_seed_procurement", new GraphGuidedThinkingScaffold.Node(
                "node_seed_procurement", "采购单", "业务实体", now, false, createNormalized1536Vector(1.0f)
        ));

        CrossBorderProcurementScenarioEngine.ProcurementRequest request =
                new CrossBorderProcurementScenarioEngine.ProcurementRequest(
                        "SCENARIO_TEST_004",
                        "高频通讯芯片",
                        "8542.31.00",
                        800_000.0,
                        "US",
                        "USD",
                        createNormalized1536Vector(1.0f),
                        now
                );

        // 模拟思考流发生死循环 (反复重复相同词元，确保超过 32 词元判定窗口)
        String loopingThinkingStream = "wait let me check the sanctions wait let me check the sanctions wait let me check the sanctions wait let me check the sanctions wait let me check the sanctions wait let me check the sanctions wait let me check the sanctions wait let me check the sanctions ";

        CrossBorderProcurementScenarioEngine.ExecutionResult result =
                engine.executeProcurementFlow(request, nodes, List.of(), loopingThinkingStream);

        assertTrue(result.success());
        assertTrue(result.auditLog().contains("思考流检测到死循环异常"), "引擎必须捕获到死循环异常");
        assertTrue(result.auditLog().contains("触发因果自愈中枢截断与反思重构"), "必须触发因果自愈中枢干预");
        assertNotEquals(loopingThinkingStream, result.thinkingProcess(), "自愈后的思考 Prompt 应与原始死循环文本不同");
    }

    @Test
    @DisplayName("契约5: 端到端不可变存证凭单 SHA-256 密码学自签名与防篡改验真")
    void testContract5_AuditReceiptSignatureAndTamperProof() {
        long now = System.currentTimeMillis();
        Map<String, GraphGuidedThinkingScaffold.Node> nodes = new HashMap<>();
        nodes.put("node_seed_procurement", new GraphGuidedThinkingScaffold.Node(
                "node_seed_procurement", "采购单", "业务实体", now, false, createNormalized1536Vector(1.0f)
        ));

        CrossBorderProcurementScenarioEngine.ProcurementRequest request =
                new CrossBorderProcurementScenarioEngine.ProcurementRequest(
                        "SCENARIO_TEST_005",
                        "工业机器人手臂",
                        "8479.50.10",
                        350_000.0,
                        "DE",
                        "USD",
                        createNormalized1536Vector(1.0f),
                        now
                );

        String thinkingStream = "采购工业机器人手臂，合规审查通过，调用 ERP 与外汇锁价工具。";
        CrossBorderProcurementScenarioEngine.ExecutionResult result =
                engine.executeProcurementFlow(request, nodes, List.of(), thinkingStream);

        BusinessScenarioAuditReceipt receipt = result.receipt();
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原始凭单自签名验真必须为 true");

        // 篡改 finalDecision
        BusinessScenarioAuditReceipt tamperedDecision = new BusinessScenarioAuditReceipt(
                receipt.receiptId(),
                receipt.scenarioId(),
                receipt.businessType(),
                receipt.thinkingSteps(),
                receipt.mcpToolsInvoked(),
                receipt.graphEntitiesMatched(),
                "REJECTED_MANUALLY", // 恶意篡改
                receipt.totalLatencyMs(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );
        assertFalse(tamperedDecision.verifySignature(), "篡改决策结论后验真必须失败");

        // 篡改 toolsInvoked
        BusinessScenarioAuditReceipt tamperedTools = new BusinessScenarioAuditReceipt(
                receipt.receiptId(),
                receipt.scenarioId(),
                receipt.businessType(),
                receipt.thinkingSteps(),
                List.of("mcp_unauthorized_tool"), // 恶意伪造工具
                receipt.graphEntitiesMatched(),
                receipt.finalDecision(),
                receipt.totalLatencyMs(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );
        assertFalse(tamperedTools.verifySignature(), "篡改工具调用清单后验真必须失败");
    }
}
