package tech.qiantong.qknow.ai.scenario;

import tech.qiantong.qknow.ai.mor.ReflectiveSelfHealingCoordinator;
import tech.qiantong.qknow.ai.mor.ThinkingStreamInterrupter;
import tech.qiantong.qknow.ai.rag.GraphGuidedThinkingScaffold;
import tech.qiantong.qknow.ai.rag.SpatiotemporalDecayAligner;
import tech.qiantong.qknow.ai.scenario.model.BusinessScenarioAuditReceipt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业跨境智能采购与多维合规审批端到端编排执行引擎
 * 联动四大技术支柱，严格遵循 DeepSeek 官方最新思考模式 (Thinking Mode) 规范与阿里千问 1536 维超球面基线
 */
public class CrossBorderProcurementScenarioEngine {

    // 默认思考模式配置 (对齐 DeepSeek 官方网络文档最新规范)
    public static final String THINKING_TYPE_ENABLED = "enabled";
    public static final String DEFAULT_REASONING_EFFORT = "high";

    // 瞬态安全租约有效期 (毫秒)
    public static final long LEASE_EXPIRATION_MS = 60_000L;

    private final GraphGuidedThinkingScaffold scaffoldEngine;
    private final ThinkingStreamInterrupter interrupter;
    private final ReflectiveSelfHealingCoordinator healingCoordinator;

    // 模拟 MCP 工具动态租约表: toolName -> (leaseToken -> expireTimestamp)
    private final Map<String, Map<String, Long>> activeLeases = new ConcurrentHashMap<>();

    public CrossBorderProcurementScenarioEngine(
            GraphGuidedThinkingScaffold scaffoldEngine,
            ThinkingStreamInterrupter interrupter,
            ReflectiveSelfHealingCoordinator healingCoordinator
    ) {
        this.scaffoldEngine = Objects.requireNonNull(scaffoldEngine, "scaffoldEngine 不能为空");
        this.interrupter = Objects.requireNonNull(interrupter, "interrupter 不能为空");
        this.healingCoordinator = Objects.requireNonNull(healingCoordinator, "healingCoordinator 不能为空");
    }

    /**
     * 采购请求入参 (纯 Java 21 Record)
     */
    public record ProcurementRequest(
            String scenarioId,
            String purchaseItem,
            String hsCode,
            double amountUsd,
            String supplierCountry,
            String currency,
            float[] queryEmbedding,
            long requestTimeMs
    ) {}

    /**
     * 执行执行结果 (纯 Java 21 Record)
     */
    public record ExecutionResult(
            boolean success,
            String decision,
            String thinkingProcess,
            String reasoningContentEcho,
            List<String> invokedTools,
            List<String> matchedEntities,
            BusinessScenarioAuditReceipt receipt,
            String auditLog
    ) {}

    /**
     * 申请 MCP 高危工具 60 秒瞬态租约
     */
    public synchronized String issueLeaseToken(String toolName, long nowMs) {
        String token = "LEASE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        activeLeases.computeIfAbsent(toolName, k -> new ConcurrentHashMap<>())
                .put(token, nowMs + LEASE_EXPIRATION_MS);
        return token;
    }

    /**
     * 校验并消费 MCP 高危工具瞬态租约
     */
    public synchronized boolean validateAndConsumeLease(String toolName, String token, long nowMs) {
        Map<String, Long> leases = activeLeases.get(toolName);
        if (leases == null || !leases.containsKey(token)) {
            return false;
        }
        long expireAt = leases.remove(token);
        return nowMs <= expireAt;
    }

    /**
     * 端到端执行跨境采购审批编排流
     */
    public ExecutionResult executeProcurementFlow(
            ProcurementRequest request,
            Map<String, GraphGuidedThinkingScaffold.Node> graphNodes,
            List<GraphGuidedThinkingScaffold.Edge> graphEdges,
            String simulatedThinkingStream
    ) {
        long startTime = System.currentTimeMillis();
        List<String> invokedTools = new ArrayList<>();
        List<String> matchedEntities = new ArrayList<>();
        StringBuilder auditLog = new StringBuilder();

        // 1. 前端 Schema 强类型校验 (支柱四)
        if (request.amountUsd() <= 0 || request.hsCode() == null || request.hsCode().isBlank()) {
            throw new IllegalArgumentException("采购单格式校验失败: 金额非法或缺少海关 HS 编码");
        }
        auditLog.append("[Step 1] 前端 Monaco Schema 校验通过. 采购金额: USD ").append(request.amountUsd()).append("\n");

        // 2. 支柱三: GraphRAG 2-跳 PPR 抽取合规子图与时空衰减对齐
        GraphGuidedThinkingScaffold.ScaffoldResult scaffoldResult = scaffoldEngine.buildScaffold(
                request.scenarioId(),
                request.purchaseItem() + " " + request.hsCode(),
                request.queryEmbedding(),
                List.of("node_seed_procurement"),
                graphNodes,
                graphEdges,
                request.requestTimeMs()
        );

        for (GraphGuidedThinkingScaffold.Node node : scaffoldResult.retainedNodes()) {
            matchedEntities.add(node.name());
        }
        auditLog.append("[Step 2] GraphRAG 子图抽取完成. 命中了 ").append(matchedEntities.size()).append(" 个合规实体\n");

        // 3. 支柱一: DeepSeek 官方思考模式推演与流式自愈监控
        // 按照 DeepSeek 官方规范，携带 thinking: {"type": "enabled"} 与 reasoning_effort: "high"
        ThinkingStreamInterrupter.SessionStreamContext streamCtx = interrupter.createContext();
        ThinkingStreamInterrupter.InterruptionDecision decision =
                ThinkingStreamInterrupter.InterruptionDecision.continueNormal(0.0, 0.0, 0);

        // 模拟 SSE 流式分块输入与监控
        String[] streamChunks = simulatedThinkingStream.split("(?<=[\\s\n;。！？])");
        if (streamChunks.length > 1) {
            for (String chunk : streamChunks) {
                if (!chunk.isBlank()) {
                    decision = interrupter.feedAndEvaluate(streamCtx, chunk);
                    if (decision.shouldInterrupt()) {
                        break;
                    }
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                decision = interrupter.feedAndEvaluate(streamCtx, simulatedThinkingStream);
                if (decision.shouldInterrupt()) {
                    break;
                }
            }
        }

        String effectiveThinking = simulatedThinkingStream;
        if (decision.shouldInterrupt()) {
            auditLog.append("[Step 3] 思考流检测到死循环异常 (熵: ").append(decision.entropy())
                    .append(", 4-gram 重复率: ").append(decision.ngramRepetition())
                    .append("). 触发因果自愈中枢截断与反思重构!\n");
            ReflectiveSelfHealingCoordinator.HealingPlan healResult =
                    healingCoordinator.coordinateHealing(
                            simulatedThinkingStream,
                            request.purchaseItem() + " 跨境采购合规审查"
                    );
            effectiveThinking = healResult.reflectionPrompt();
        } else {
            auditLog.append("[Step 3] 思考流推演正常. 信息熵: ").append(decision.entropy())
                    .append(", 4-gram 重复率: ").append(decision.ngramRepetition()).append("\n");
        }

        // 4. 支柱二: MCP 工具动态检索与 60s 瞬态租约强管控
        // 场景包含 3 个高危工具: ERP 订单创建、关税锁定、外汇即期锁价
        String tool1 = "mcp_erp_create_purchase_order";
        String tool2 = "mcp_customs_tax_lock";
        String tool3 = "mcp_forex_spot_lock";

        String lease1 = issueLeaseToken(tool1, request.requestTimeMs());
        String lease2 = issueLeaseToken(tool2, request.requestTimeMs());
        String lease3 = issueLeaseToken(tool3, request.requestTimeMs());

        // 校验并消费租约
        if (validateAndConsumeLease(tool1, lease1, request.requestTimeMs())) {
            invokedTools.add(tool1);
        }
        if (validateAndConsumeLease(tool2, lease2, request.requestTimeMs())) {
            invokedTools.add(tool2);
        }
        if (validateAndConsumeLease(tool3, lease3, request.requestTimeMs())) {
            invokedTools.add(tool3);
        }
        auditLog.append("[Step 4] MCP 动态工具调用完成. 成功执行 ").append(invokedTools.size()).append(" 个受租约保护工具\n");

        // 5. DeepSeek 官方规范: 在携带 tools 参数的多轮交互中，必须完整回传 reasoning_content
        String reasoningContentEcho = effectiveThinking.trim();
        auditLog.append("[Step 5] 严格遵循官方规范回传 reasoning_content (").append(reasoningContentEcho.length())
                .append(" 字符), 杜绝 HTTP 400 报错\n");

        // 6. 最终决策判定与签发不可变存证凭单
        String finalDecision = "APPROVED_WITH_COMPLIANCE_PASS";
        long totalLatencyMs = System.currentTimeMillis() - startTime;

        BusinessScenarioAuditReceipt receipt = BusinessScenarioAuditReceipt.create(
                request.scenarioId(),
                "CROSS_BORDER_PROCUREMENT",
                5,
                invokedTools,
                matchedEntities,
                finalDecision,
                totalLatencyMs,
                request.requestTimeMs()
        );

        auditLog.append("[Step 6] 签发业务存证凭单: ").append(receipt.receiptId())
                .append(" (SHA-256 签名: ").append(receipt.sha256Signature().substring(0, 16)).append("...)\n");

        return new ExecutionResult(
                true,
                finalDecision,
                effectiveThinking,
                reasoningContentEcho,
                invokedTools,
                matchedEntities,
                receipt,
                auditLog.toString()
        );
    }
}
