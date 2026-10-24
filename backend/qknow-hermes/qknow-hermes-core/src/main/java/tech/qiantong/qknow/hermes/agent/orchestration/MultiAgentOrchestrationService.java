package tech.qiantong.qknow.hermes.agent.orchestration;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.LlmDebateCoordinator;
import tech.qiantong.qknow.hermes.agent.SupervisorAgent;
import tech.qiantong.qknow.hermes.agent.WorkerAgent;
import tech.qiantong.qknow.hermes.agent.blackboard.SharedBlackboard;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.OrchestrationMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 多智能体受控编排核心服务 (P0-15 解耦与治理组件)
 *
 * <p>职责边界：
 * <ul>
 *   <li>解析全局开关 {@link OrchestrationProperties} 与 Bot 级 {@code parameters.orchestrationMode}；</li>
 *   <li>基于 DeepSeek 官方定价表（缓存命中输入、缓存未命中输入、输出）执行前置成本预估与运行期 Usage 实时熔断；</li>
 *   <li>执行请求级隔离的 SWARM（真实 {@link WorkerAgent}/{@code ReactAgent} + 差异化角色 SystemPrompt + 白名单工具）与 DEBATE（三角色真实 LLM 辩论）；</li>
 *   <li>维护有界 LRU+TTL 审计凭单与黑板快照缓存，杜绝无界 Map 内存泄漏；</li>
 *   <li>提供可取消 {@link ChatModel} 装饰器与活跃线程中断传播，确保超时回退 {@code SINGLE} 后底层多智能体调用立即终止。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class MultiAgentOrchestrationService {

    private static final ExecutorService ORCHESTRATION_EXECUTOR =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "hermes-orchestration");
                t.setDaemon(true);
                return t;
            });

    private static final String[][] WORKER_ROLE_SPECS = new String[][]{
            {
                    "RAGWorker",
                    "知识库检索与事实核验专家",
                    """
                    【角色定位：知识库检索与事实核验专家 (RAGWorker)】
                    你的核心职责是优先调用当前请求已授权的知识库检索与查询工具，精准核实用户问题涉及的原始事实、数据指标与文档依据。
                    规则：必须依据工具返回结果与背景证据作答，严禁脱离事实凭空臆造。"""
            },
            {
                    "AnalysisWorker",
                    "多步逻辑推理与对比分析专家",
                    """
                    【角色定位：多步逻辑推理与对比分析专家 (AnalysisWorker)】
                    你的核心职责是基于上游检索事实与必要的工具辅助，执行多步因果链推导、维度对比、边界条件检验与定量核算。
                    规则：清晰列出推理步骤与对比维度，指出潜在假设与约束条件。"""
            },
            {
                    "SynthesisWorker",
                    "结构化汇总与结论校验专家",
                    """
                    【角色定位：结构化汇总与结论校验专家 (SynthesisWorker)】
                    你的核心职责是整合多源证据与分析结论，交叉核验前后逻辑一致性，剔除冗余或矛盾表述，输出结构清晰、结论明确的综合答复。"""
            }
    };

    @Getter
    private final OrchestrationProperties properties;
    private final Semaphore llmCallSemaphore;
    private final BoundedTtlMap<String, AgentOrchestrator.OrchestrationExecutionReceipt> orchestrationReceipts;
    private final BoundedTtlMap<String, Map<String, String>> lastSwarmBlackboardByRequest;
    private final BoundedTtlMap<String, LlmDebateCoordinator.DebateReceipt> lastDebateReceiptByRequest;

    @Autowired
    public MultiAgentOrchestrationService(OrchestrationProperties properties) {
        this.properties = properties != null ? properties : new OrchestrationProperties();
        int maxEntries = Math.max(1, this.properties.getReceiptCacheMaxEntries());
        long ttlMs = Math.max(1000L, this.properties.getReceiptCacheTtlMs());
        this.llmCallSemaphore = new Semaphore(Math.max(1, this.properties.getMaxConcurrentLlmCalls()), true);
        this.orchestrationReceipts = new BoundedTtlMap<>(maxEntries, ttlMs);
        this.lastSwarmBlackboardByRequest = new BoundedTtlMap<>(maxEntries, ttlMs);
        this.lastDebateReceiptByRequest = new BoundedTtlMap<>(maxEntries, ttlMs);
    }

    public MultiAgentOrchestrationService() {
        this(new OrchestrationProperties());
    }

    /**
     * 编排模式解析结果
     */
    public record ModeResolution(
            OrchestrationMode requestedMode,
            OrchestrationMode effectiveMode,
            boolean fallbackTriggered,
            String errorCode
    ) {
    }

    /**
     * 多智能体执行结果（包含是否成功处理或需回退 SINGLE）
     */
    public record OrchestrationOutcome(
            boolean handledByMultiAgent,
            boolean fallbackTriggered,
            String answer,
            long promptTokens,
            long completionTokens,
            double estimatedOrActualCostCny,
            String errorCode
    ) {
        public static OrchestrationOutcome handled(String answer, long promptTokens, long completionTokens, double costCny) {
            return new OrchestrationOutcome(true, false, answer, promptTokens, completionTokens, costCny, AgentOrchestrator.ERR_NONE);
        }

        public static OrchestrationOutcome fallbackToSingle(boolean fallbackTriggered, String errorCode, double costCny) {
            return new OrchestrationOutcome(false, fallbackTriggered, null, 0L, 0L, costCny, errorCode);
        }
    }

    public Optional<AgentOrchestrator.OrchestrationExecutionReceipt> getLastOrchestrationReceipt(String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(orchestrationReceipts.get(requestId));
    }

    public Map<String, String> getLastSwarmBlackboardFacts(String requestId) {
        if (requestId == null) {
            return Map.of();
        }
        return lastSwarmBlackboardByRequest.getOrDefault(requestId, Map.of());
    }

    public Optional<LlmDebateCoordinator.DebateReceipt> getLastDebateReceipt(String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(lastDebateReceiptByRequest.get(requestId));
    }

    public int getReceiptCacheSize() {
        return orchestrationReceipts.size();
    }

    public int getSwarmBlackboardCacheSize() {
        return lastSwarmBlackboardByRequest.size();
    }

    public int getDebateReceiptCacheSize() {
        return lastDebateReceiptByRequest.size();
    }

    public void recordOrchestrationReceipt(String requestId,
                                           OrchestrationMode requestedMode,
                                           OrchestrationMode effectiveMode,
                                           boolean fallbackTriggered,
                                           String errorCode,
                                           int workerCount,
                                           long durationMs) {
        String key = (requestId != null && !requestId.isBlank()) ? requestId : "anonymous";
        AgentOrchestrator.OrchestrationExecutionReceipt receipt = new AgentOrchestrator.OrchestrationExecutionReceipt(
                key,
                requestedMode != null ? requestedMode : OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                effectiveMode != null ? effectiveMode : OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                fallbackTriggered,
                errorCode != null ? errorCode : AgentOrchestrator.ERR_NONE,
                workerCount,
                durationMs
        );
        orchestrationReceipts.put(key, receipt);
    }

    /**
     * 解析当前请求的编排策略模式：
     * 1. 解析 request.parameters JSON 中的 orchestrationMode（若非法则记录 ORCHESTRATION_UNKNOWN_MODE 并降级 SINGLE）；
     * 2. 读取 proto 枚举 request.getOrchestrationMode()；
     * 3. 校验 Spring 全局配置 {@code hermes.orchestration.enabled}（默认 false）与请求级开关，若关闭则记录 DISABLED 并降级 SINGLE。
     */
    public ModeResolution resolveOrchestrationMode(ChatRequest request) {
        if (request == null) {
            return new ModeResolution(
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    false,
                    AgentOrchestrator.ERR_NONE
            );
        }

        Boolean paramEnabled = null;
        String rawModeFromParams = null;

        String paramsJson = request.getParameters();
        if (paramsJson != null && !paramsJson.isBlank()) {
            try {
                JSONObject json = JSONObject.parseObject(paramsJson);
                if (json != null) {
                    if (json.containsKey("orchestrationEnabled")) {
                        paramEnabled = json.getBoolean("orchestrationEnabled");
                    }
                    rawModeFromParams = json.getString("orchestrationMode");
                }
            } catch (Exception e) {
                log.warn("[{}] 解析 request.parameters JSON 失败, 降级为 SINGLE: {}",
                        AgentOrchestrator.ERR_ORCHESTRATION_UNKNOWN_MODE, e.getMessage());
                return new ModeResolution(
                        OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                        OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                        true,
                        AgentOrchestrator.ERR_ORCHESTRATION_UNKNOWN_MODE
                );
            }
        }

        OrchestrationMode protoMode = request.getOrchestrationMode();
        if (protoMode == OrchestrationMode.UNRECOGNIZED) {
            log.warn("[{}] Proto orchestration_mode 为 UNRECOGNIZED, 自动降级为 SINGLE",
                    AgentOrchestrator.ERR_ORCHESTRATION_UNKNOWN_MODE);
            return new ModeResolution(
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    true,
                    AgentOrchestrator.ERR_ORCHESTRATION_UNKNOWN_MODE
            );
        }

        OrchestrationMode requested = protoMode != null ? protoMode : OrchestrationMode.ORCHESTRATION_MODE_SINGLE;
        if (rawModeFromParams != null && !rawModeFromParams.isBlank()) {
            String normalized = rawModeFromParams.trim().toUpperCase(Locale.ROOT);
            switch (normalized) {
                case "SINGLE", "ORCHESTRATION_MODE_SINGLE" ->
                        requested = OrchestrationMode.ORCHESTRATION_MODE_SINGLE;
                case "SWARM", "ORCHESTRATION_MODE_SWARM" ->
                        requested = OrchestrationMode.ORCHESTRATION_MODE_SWARM;
                case "DEBATE", "ORCHESTRATION_MODE_DEBATE" ->
                        requested = OrchestrationMode.ORCHESTRATION_MODE_DEBATE;
                default -> {
                    log.warn("[{}] 未知编排模式 '{}', 自动降级为 SINGLE",
                            AgentOrchestrator.ERR_ORCHESTRATION_UNKNOWN_MODE, rawModeFromParams);
                    return new ModeResolution(
                            OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                            OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                            true,
                            AgentOrchestrator.ERR_ORCHESTRATION_UNKNOWN_MODE
                    );
                }
            }
        }

        boolean globallyEnabled = properties.isEnabled();
        if (requested != OrchestrationMode.ORCHESTRATION_MODE_SINGLE
                && (!globallyEnabled || Boolean.FALSE.equals(paramEnabled))) {
            log.info("[{}] 多智能体编排开关未开启 (globalEnabled={}, paramEnabled={}), 请求 {} 降级为 SINGLE",
                    AgentOrchestrator.ERR_ORCHESTRATION_DISABLED, globallyEnabled, paramEnabled, request.getRequestId());
            return new ModeResolution(
                    requested,
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    true,
                    AgentOrchestrator.ERR_ORCHESTRATION_DISABLED
            );
        }

        return new ModeResolution(requested, requested, false, AgentOrchestrator.ERR_NONE);
    }

    /**
     * 执行受控多智能体编排（若为 SINGLE、开关关闭、超出成本预算、超时或执行异常，则安全返回 fallbackToSingle）
     */
    public OrchestrationOutcome executeIfEnabled(ChatRequest request,
                                                 String finalUserPrompt,
                                                 String systemPrompt,
                                                 List<String> enabledToolNames,
                                                 List<ToolCallback> guardedTools,
                                                 ToolCallbackResolver guardedResolver,
                                                 ChatModel agentModel,
                                                 boolean globalTokenBudgetExceeded) {
        ModeResolution resolution = resolveOrchestrationMode(request);
        if (resolution.effectiveMode() == OrchestrationMode.ORCHESTRATION_MODE_SINGLE) {
            recordOrchestrationReceipt(
                    request != null ? request.getRequestId() : "anonymous",
                    resolution.requestedMode(),
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    resolution.fallbackTriggered(),
                    resolution.errorCode(),
                    1,
                    0L
            );
            return OrchestrationOutcome.fallbackToSingle(resolution.fallbackTriggered(), resolution.errorCode(), 0.0D);
        }

        List<ToolCallback> whitelistedTools = buildWhitelistedWorkerTools(enabledToolNames, guardedTools, guardedResolver);
        int maxWorkers = resolveMaxWorkers(request.getParameters());
        int debateRounds = resolveDebateRounds(request.getParameters());
        double maxCostCny = resolveMaxCostCny(request.getParameters());
        String effectiveUserPrompt = resolveEffectiveOrchestrationPrompt(
                resolution.effectiveMode(), request, finalUserPrompt, whitelistedTools);
        double estimatedCostCny = estimateOrchestrationCostCny(
                resolution.effectiveMode(),
                systemPrompt,
                effectiveUserPrompt,
                whitelistedTools,
                maxWorkers,
                debateRounds,
                request.getParameters()
        );

        if (globalTokenBudgetExceeded || maxCostCny <= 0.0D || estimatedCostCny > maxCostCny) {
            log.warn("[{}] 请求 {} 预估多智能体成本 {} 元超出预算上限 {} 元 (globalBudgetExceeded={}), 自动降级为 SINGLE",
                    AgentOrchestrator.ERR_BUDGET_EXCEEDED, request.getRequestId(),
                    String.format(Locale.ROOT, "%.6f", estimatedCostCny),
                    String.format(Locale.ROOT, "%.6f", maxCostCny),
                    globalTokenBudgetExceeded);
            recordOrchestrationReceipt(
                    request.getRequestId(),
                    resolution.requestedMode(),
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    true,
                    AgentOrchestrator.ERR_BUDGET_EXCEEDED,
                    1,
                    0L
            );
            return OrchestrationOutcome.fallbackToSingle(true, AgentOrchestrator.ERR_BUDGET_EXCEEDED, estimatedCostCny);
        }

        if (resolution.effectiveMode() == OrchestrationMode.ORCHESTRATION_MODE_SWARM) {
            return executeSwarmMode(request, effectiveUserPrompt, systemPrompt, enabledToolNames,
                    whitelistedTools, guardedResolver, agentModel, maxWorkers, maxCostCny);
        }
        return executeDebateMode(request, effectiveUserPrompt, systemPrompt, agentModel, debateRounds, maxCostCny);
    }

    /**
     * 针对真实 RAG 上下文与工具挂载场景优化多智能体输入：
     * <ul>
     *   <li>当请求未携带预检索 {@code ragContexts}（如单元契约测试或纯工具请求）时，100% 原样透传 {@code finalUserPrompt}；</li>
     *   <li>SWARM 模式下，知识库已封装为 {@code knowledgeQuery}/{@code knowledgeBase*} 工具挂载到 Worker，
     *       Planner 仅需用户原始问题并引导并行检索与分析，避免将近万字 {@code <knowledge_base>} 重复塞入 Planner 与每个子调用；</li>
     *   <li>DEBATE 模式下，若内联 {@code <knowledge_base>} 过长，保留高相关前 1800 字符摘要与完整用户问题，保障 3 轮串行抗辩在预算与时延门禁内完成。</li>
     * </ul>
     */
    private String resolveEffectiveOrchestrationPrompt(OrchestrationMode mode,
                                                       ChatRequest request,
                                                       String finalUserPrompt,
                                                       List<ToolCallback> whitelistedTools) {
        if (request == null || request.getRagContextsCount() == 0 || StrUtil.isBlank(request.getQuestion())) {
            return finalUserPrompt;
        }
        if (mode == OrchestrationMode.ORCHESTRATION_MODE_SWARM && hasKnowledgeTool(whitelistedTools)) {
            return request.getQuestion()
                    + "\n（编排要求：请拆解为2个无前置依赖的子任务，dependencies必须为空数组[]，其中 task-1 的 requiredCapability 设为 KNOWLEDGE_RETRIEVAL 并仅调用一次 knowledgeQuery 工具获取知识库内容）";
        }
        if (mode == OrchestrationMode.ORCHESTRATION_MODE_DEBATE
                && finalUserPrompt != null && finalUserPrompt.length() > 2200) {
            String prefix = finalUserPrompt.substring(0, 1800);
            return prefix + "\n...</knowledge_base>\n\n" + request.getQuestion();
        }
        return finalUserPrompt;
    }

    private boolean hasKnowledgeTool(List<ToolCallback> tools) {
        if (tools == null || tools.isEmpty()) {
            return false;
        }
        for (ToolCallback tc : tools) {
            if (tc != null && tc.getToolDefinition() != null && tc.getToolDefinition().name() != null) {
                String name = tc.getToolDefinition().name();
                if ("knowledgeQuery".equals(name) || name.startsWith("knowledgeBase")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 基于 DeepSeek 官方价格表计算多智能体全链路预估成本（人民币：元）
     *
     * <p>保守预估口径（预算判断阶段一律按“缓存未命中”价格估算，不假设 seq&gt;1 的调用必然命中缓存）：
     * <ul>
     *   <li>缓存未命中输入单价：{@code effectiveInputCacheMissPricePerMillionCny()}（默认高峰期 2.00 元/M，空闲期 1.00 元/M）；</li>
     *   <li>输出单价：{@code effectiveOutputPricePerMillionCny()}（默认高峰期 8.00 元/M，空闲期 4.00 元/M）；</li>
     *   <li>SWARM 调用次数：1 次 Planner + K 次 Worker + 1 次 Aggregator（共 K+2 次调用，全部输入按 cache miss 保守估算）；</li>
     *   <li>DEBATE 调用次数：1 次 Proposer + R 次 Critic + (R-1) 次 Refine + 1 次 Judge（共 2R+1 次调用，全部输入按 cache miss 保守估算）。</li>
     * </ul>
     * </p>
     */
    public double estimateOrchestrationCostCny(OrchestrationMode mode,
                                               String systemPrompt,
                                               String finalUserPrompt,
                                               List<ToolCallback> whitelistedTools,
                                               int maxWorkers,
                                               int debateRounds,
                                               String parametersJson) {
        long sysTokens = estimateTokenCount(systemPrompt);
        long userTokens = estimateTokenCount(finalUserPrompt);
        long toolTokens = whitelistedTools != null ? whitelistedTools.size() * 128L : 0L;
        long expectedOutputPerCall = Math.max(64L, resolveLongParam(
                parametersJson, "expectedOutputTokensPerCall", properties.getEstimatedOutputTokensPerCall()));

        double missPricePerToken = properties.effectiveInputCacheMissPricePerMillionCny() / 1_000_000.0D;
        double outputPricePerToken = properties.effectiveOutputPricePerMillionCny() / 1_000_000.0D;

        if (mode == OrchestrationMode.ORCHESTRATION_MODE_SWARM) {
            int workers = Math.max(1, maxWorkers);
            // 1. Planner 调用：Planner 独立系统提示词 (~256 tokens) + 用户问题（未命中缓存）+ 规划 JSON 输出
            long plannerInputMiss = 256L + userTokens;
            long plannerOutput = Math.min(expectedOutputPerCall, 256L);

            // 2. Worker 调用 (workers 次)：保守按全部输入未命中缓存估算（不假设后续 Worker 必然命中前缀缓存）
            long staticPrefix = sysTokens + toolTokens;
            long firstWorkerMiss = staticPrefix + userTokens;
            long subsequentWorkersMiss = (staticPrefix + userTokens + expectedOutputPerCall / 2) * Math.max(0, workers - 1);
            long workersOutput = expectedOutputPerCall * workers;

            // 3. Supervisor Aggregator 调用：保守按未命中缓存估算
            long aggInputMiss = sysTokens + userTokens + workersOutput;
            long aggOutput = expectedOutputPerCall;

            long totalCacheMissInput = plannerInputMiss + firstWorkerMiss + subsequentWorkersMiss + aggInputMiss;
            long totalOutput = plannerOutput + workersOutput + aggOutput;

            return totalCacheMissInput * missPricePerToken
                    + totalOutput * outputPricePerToken;
        }

        if (mode == OrchestrationMode.ORCHESTRATION_MODE_DEBATE) {
            int rounds = Math.max(1, Math.min(debateRounds, 2));
            int totalCalls = 2 * rounds + 1;
            // 保守口径：所有调用（Proposer / Critic / Refine / Judge）的输入均按缓存未命中价格估算
            long totalCacheMissInput = sysTokens + userTokens;
            long totalOutput = expectedOutputPerCall;

            for (int callIdx = 2; callIdx <= totalCalls; callIdx++) {
                totalCacheMissInput += sysTokens + userTokens + (callIdx - 1) * expectedOutputPerCall;
                totalOutput += expectedOutputPerCall;
            }

            return totalCacheMissInput * missPricePerToken
                    + totalOutput * outputPricePerToken;
        }

        return (sysTokens + userTokens) * missPricePerToken + expectedOutputPerCall * outputPricePerToken;
    }

    private OrchestrationOutcome executeSwarmMode(ChatRequest request,
                                                  String finalUserPrompt,
                                                  String systemPrompt,
                                                  List<String> enabledToolNames,
                                                  List<ToolCallback> whitelistedTools,
                                                  ToolCallbackResolver guardedResolver,
                                                  ChatModel agentModel,
                                                  int maxWorkers,
                                                  double maxCostCny) {
        long startMs = System.currentTimeMillis();
        long timeoutMs = resolveTimeoutMs(request.getParameters());
        String sessionId = StrUtil.isNotBlank(request.getRequestId())
                ? request.getRequestId()
                : UUID.randomUUID().toString();

        OrchestrationCallTracker tracker = new OrchestrationCallTracker(
                maxCostCny,
                estimateTokenCount(systemPrompt),
                timeoutMs
        );
        ChatModel cancellableModel = new CancellableBudgetedChatModel(agentModel, tracker);

        // 绑定请求级租户/工作区/用户隔离元信息与可取消工具包装器
        String scopedSystemPrompt = appendTenantContextHeader(systemPrompt, request);
        List<ToolCallback> cancellableTools = whitelistedTools.stream()
                .map(tc -> wrapCancellableTool(tc, tracker))
                .toList();
        Set<String> allowedToolSet = new HashSet<>(enabledToolNames != null ? enabledToolNames : List.of());
        ToolCallbackResolver whitelistedResolver = name -> {
            if (name == null || !allowedToolSet.contains(name)) {
                log.warn("[SWARM_TOOL_WHITELIST_BLOCKED] 拦截未授权工具访问: {}", name);
                return null;
            }
            ToolCallback resolved = guardedResolver != null ? guardedResolver.resolve(name) : null;
            return wrapCancellableTool(resolved, tracker);
        };

        // 每次请求新建独立 WorkerAgent、独立 SharedBlackboard、独立 SupervisorAgent（P0-1：真实走 ReactAgent 与工具链）
        List<WorkerAgent> requestWorkers = createRequestScopedSwarmWorkers(
                maxWorkers, scopedSystemPrompt, cancellableTools, cancellableModel, whitelistedResolver);
        SharedBlackboard requestBlackboard = new SharedBlackboard();
        SupervisorAgent supervisor = new SupervisorAgent(
                "RequestSupervisor-" + sessionId,
                "请求级隔离蜂群主管智能体",
                compactWorkerBaseSystemPrompt(scopedSystemPrompt),
                cancellableTools,
                cancellableModel,
                requestWorkers,
                null, null, null, requestBlackboard, null, null,
                properties.getReceiptCacheMaxEntries(),
                properties.getReceiptCacheTtlMs()
        );

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            Thread workerThread = Thread.currentThread();
            tracker.registerThread(workerThread);
            try {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("sessionId", sessionId);
                ctx.put("requestId", request.getRequestId());
                ctx.put("workspaceId", request.getWorkspaceId());
                ctx.put("userId", request.getUserId());
                ctx.put("botId", request.getBotId());
                ctx.put("conversationId", request.getConversationId());
                ctx.put("blackboard", requestBlackboard);
                return supervisor.chat(finalUserPrompt, ctx);
            } finally {
                tracker.unregisterThread(workerThread);
            }
        }, ORCHESTRATION_EXECUTOR);

        try {
            String swarmAnswer = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            long durationMs = System.currentTimeMillis() - startMs;

            if (tracker.isBudgetExceeded()) {
                tracker.cancelAll();
                recordOrchestrationReceipt(request.getRequestId(),
                        OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                        OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                        true, AgentOrchestrator.ERR_BUDGET_EXCEEDED, requestWorkers.size(), durationMs);
                return OrchestrationOutcome.fallbackToSingle(true, AgentOrchestrator.ERR_BUDGET_EXCEEDED, tracker.getAccumulatedCostCny());
            }

            Map<String, String> blackboardFacts = new LinkedHashMap<>();
            requestBlackboard.getFactSnapshot().forEach((k, v) -> blackboardFacts.put(k, v.value()));
            if (StrUtil.isNotBlank(request.getRequestId())) {
                lastSwarmBlackboardByRequest.put(request.getRequestId(), Collections.unmodifiableMap(blackboardFacts));
            }

            if (isInvalidMultiAgentAnswer(swarmAnswer) || hasAllWorkersFailed(blackboardFacts)) {
                log.warn("[{}] 请求 {} SWARM 产出无效或子任务全部失败, 自动降级为 SINGLE",
                        AgentOrchestrator.ERR_SUPERVISOR_FAILED, request.getRequestId());
                recordOrchestrationReceipt(request.getRequestId(),
                        OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                        OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                        true, AgentOrchestrator.ERR_SUPERVISOR_FAILED, requestWorkers.size(), durationMs);
                return OrchestrationOutcome.fallbackToSingle(true, AgentOrchestrator.ERR_SUPERVISOR_FAILED, tracker.getAccumulatedCostCny());
            }

            recordOrchestrationReceipt(request.getRequestId(),
                    OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                    OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                    false, AgentOrchestrator.ERR_NONE, requestWorkers.size(), durationMs);
            long pt = tracker.getPromptTokens() > 0
                    ? tracker.getPromptTokens()
                    : estimateTokenCount(finalUserPrompt) * Math.max(2, requestWorkers.size());
            long ct = tracker.getCompletionTokens() > 0
                    ? tracker.getCompletionTokens()
                    : estimateTokenCount(swarmAnswer);
            log.info("[ORCHESTRATION_SUMMARY] requestId={}, requestedMode=SWARM, effectiveMode=SWARM, fallback=false, errorCode=NONE, durationMs={}ms, llmCalls={}, promptTokens={}, completionTokens={}, reportedCacheHitTokens={}, costCny={}",
                    request.getRequestId(), durationMs, tracker.getCallCount(), pt, ct,
                    tracker.getReportedCacheHitTokens(), String.format(Locale.ROOT, "%.6f", tracker.getAccumulatedCostCny()));
            return OrchestrationOutcome.handled(swarmAnswer, pt, ct, tracker.getAccumulatedCostCny());
        } catch (TimeoutException te) {
            // P0-4: 超时第一时间置位取消令牌并中断全部在途线程，阻断后续多智能体 LLM 调用
            tracker.cancelAll();
            future.cancel(true);
            long durationMs = System.currentTimeMillis() - startMs;
            log.warn("[{}] 请求 {} SWARM 超过 {}ms 硬超时上限, 已中断底层调用并降级为 SINGLE (durationMs={}ms, llmCalls={}, promptTokens={}, completionTokens={}, costCny={})",
                    AgentOrchestrator.ERR_SUPERVISOR_TIMEOUT, request.getRequestId(), timeoutMs,
                    durationMs, tracker.getCallCount(), tracker.getPromptTokens(), tracker.getCompletionTokens(),
                    String.format(Locale.ROOT, "%.6f", tracker.getAccumulatedCostCny()));
            recordOrchestrationReceipt(request.getRequestId(),
                    OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    true, AgentOrchestrator.ERR_SUPERVISOR_TIMEOUT, requestWorkers.size(), durationMs);
            return OrchestrationOutcome.fallbackToSingle(true, AgentOrchestrator.ERR_SUPERVISOR_TIMEOUT, tracker.getAccumulatedCostCny());
        } catch (ExecutionException ee) {
            tracker.cancelAll();
            future.cancel(true);
            long durationMs = System.currentTimeMillis() - startMs;
            String errCode = tracker.isBudgetExceeded()
                    ? AgentOrchestrator.ERR_BUDGET_EXCEEDED
                    : AgentOrchestrator.ERR_SUPERVISOR_FAILED;
            log.warn("[{}] 请求 {} SWARM 执行异常 ({}), 自动降级为 SINGLE",
                    errCode, request.getRequestId(), ee.getCause() != null ? ee.getCause().getMessage() : ee.getMessage());
            recordOrchestrationReceipt(request.getRequestId(),
                    OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    true, errCode, requestWorkers.size(), durationMs);
            return OrchestrationOutcome.fallbackToSingle(true, errCode, tracker.getAccumulatedCostCny());
        } catch (Exception e) {
            tracker.cancelAll();
            future.cancel(true);
            long durationMs = System.currentTimeMillis() - startMs;
            String errCode = tracker.isBudgetExceeded()
                    ? AgentOrchestrator.ERR_BUDGET_EXCEEDED
                    : AgentOrchestrator.ERR_SUPERVISOR_FAILED;
            log.warn("[{}] 请求 {} SWARM 异常 ({}), 自动降级为 SINGLE", errCode, request.getRequestId(), e.getMessage());
            recordOrchestrationReceipt(request.getRequestId(),
                    OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    true, errCode, requestWorkers.size(), durationMs);
            return OrchestrationOutcome.fallbackToSingle(true, errCode, tracker.getAccumulatedCostCny());
        }
    }

    private OrchestrationOutcome executeDebateMode(ChatRequest request,
                                                   String finalUserPrompt,
                                                   String systemPrompt,
                                                   ChatModel agentModel,
                                                   int debateRounds,
                                                   double maxCostCny) {
        long startMs = System.currentTimeMillis();
        long timeoutMs = resolveTimeoutMs(request.getParameters());
        String sessionId = StrUtil.isNotBlank(request.getRequestId())
                ? request.getRequestId()
                : UUID.randomUUID().toString();

        OrchestrationCallTracker tracker = new OrchestrationCallTracker(
                maxCostCny,
                estimateTokenCount(systemPrompt),
                timeoutMs
        );
        ChatModel cancellableModel = new CancellableBudgetedChatModel(agentModel, tracker);
        String scopedSystemPrompt = appendTenantContextHeader(systemPrompt, request);
        LlmDebateCoordinator coordinator = new LlmDebateCoordinator(
                cancellableModel,
                scopedSystemPrompt,
                debateRounds,
                properties.getReceiptCacheMaxEntries(),
                properties.getReceiptCacheTtlMs()
        );

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            Thread workerThread = Thread.currentThread();
            tracker.registerThread(workerThread);
            try {
                return coordinator.coordinateDebate(sessionId, finalUserPrompt);
            } finally {
                tracker.unregisterThread(workerThread);
            }
        }, ORCHESTRATION_EXECUTOR);

        try {
            String debateAnswer = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            long durationMs = System.currentTimeMillis() - startMs;

            if (tracker.isBudgetExceeded()) {
                tracker.cancelAll();
                recordOrchestrationReceipt(request.getRequestId(),
                        OrchestrationMode.ORCHESTRATION_MODE_DEBATE,
                        OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                        true, AgentOrchestrator.ERR_BUDGET_EXCEEDED, 3, durationMs);
                return OrchestrationOutcome.fallbackToSingle(true, AgentOrchestrator.ERR_BUDGET_EXCEEDED, tracker.getAccumulatedCostCny());
            }

            if (isInvalidMultiAgentAnswer(debateAnswer)) {
                log.warn("[{}] 请求 {} DEBATE 产出无效, 自动降级为 SINGLE",
                        AgentOrchestrator.ERR_DEBATE_FAILED, request.getRequestId());
                recordOrchestrationReceipt(request.getRequestId(),
                        OrchestrationMode.ORCHESTRATION_MODE_DEBATE,
                        OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                        true, AgentOrchestrator.ERR_DEBATE_FAILED, 3, durationMs);
                return OrchestrationOutcome.fallbackToSingle(true, AgentOrchestrator.ERR_DEBATE_FAILED, tracker.getAccumulatedCostCny());
            }

            coordinator.getLastReceipt(sessionId).ifPresent(r -> {
                if (StrUtil.isNotBlank(request.getRequestId())) {
                    lastDebateReceiptByRequest.put(request.getRequestId(), r);
                }
            });
            recordOrchestrationReceipt(request.getRequestId(),
                    OrchestrationMode.ORCHESTRATION_MODE_DEBATE,
                    OrchestrationMode.ORCHESTRATION_MODE_DEBATE,
                    false, AgentOrchestrator.ERR_NONE, 3, durationMs);
            long pt = tracker.getPromptTokens() > 0
                    ? tracker.getPromptTokens()
                    : estimateTokenCount(finalUserPrompt) * 3L;
            long ct = tracker.getCompletionTokens() > 0
                    ? tracker.getCompletionTokens()
                    : estimateTokenCount(debateAnswer) * 2L;
            log.info("[ORCHESTRATION_SUMMARY] requestId={}, requestedMode=DEBATE, effectiveMode=DEBATE, fallback=false, errorCode=NONE, durationMs={}ms, llmCalls={}, promptTokens={}, completionTokens={}, reportedCacheHitTokens={}, costCny={}",
                    request.getRequestId(), durationMs, tracker.getCallCount(), pt, ct,
                    tracker.getReportedCacheHitTokens(), String.format(Locale.ROOT, "%.6f", tracker.getAccumulatedCostCny()));
            return OrchestrationOutcome.handled(debateAnswer, pt, ct, tracker.getAccumulatedCostCny());
        } catch (TimeoutException te) {
            tracker.cancelAll();
            future.cancel(true);
            long durationMs = System.currentTimeMillis() - startMs;
            log.warn("[{}] 请求 {} DEBATE 超过 {}ms 硬超时上限, 已中断底层调用并降级为 SINGLE (durationMs={}ms, llmCalls={}, promptTokens={}, completionTokens={}, costCny={})",
                    AgentOrchestrator.ERR_SUPERVISOR_TIMEOUT, request.getRequestId(), timeoutMs,
                    durationMs, tracker.getCallCount(), tracker.getPromptTokens(), tracker.getCompletionTokens(),
                    String.format(Locale.ROOT, "%.6f", tracker.getAccumulatedCostCny()));
            recordOrchestrationReceipt(request.getRequestId(),
                    OrchestrationMode.ORCHESTRATION_MODE_DEBATE,
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    true, AgentOrchestrator.ERR_SUPERVISOR_TIMEOUT, 3, durationMs);
            return OrchestrationOutcome.fallbackToSingle(true, AgentOrchestrator.ERR_SUPERVISOR_TIMEOUT, tracker.getAccumulatedCostCny());
        } catch (Exception e) {
            tracker.cancelAll();
            future.cancel(true);
            long durationMs = System.currentTimeMillis() - startMs;
            String errCode = tracker.isBudgetExceeded()
                    ? AgentOrchestrator.ERR_BUDGET_EXCEEDED
                    : AgentOrchestrator.ERR_DEBATE_FAILED;
            log.warn("[{}] 请求 {} DEBATE 执行异常 ({}), 自动降级为 SINGLE",
                    errCode, request.getRequestId(), e.getMessage());
            recordOrchestrationReceipt(request.getRequestId(),
                    OrchestrationMode.ORCHESTRATION_MODE_DEBATE,
                    OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                    true, errCode, 3, durationMs);
            return OrchestrationOutcome.fallbackToSingle(true, errCode, tracker.getAccumulatedCostCny());
        }
    }

    /**
     * 构建请求级隔离的真实 WorkerAgent 列表（P0-1 修复）：
     * <ul>
     *   <li>严禁使用匿名子类覆盖 {@code chat()} 绕过 {@code ReactAgent}；</li>
     *   <li>每个 Worker 挂载差异化的角色专属 {@code systemPrompt}（RAG 检索核验 / 逻辑分析 / 结构化综合）；</li>
     *   <li>每个 Worker 绑定当前请求的白名单工具集 {@code whitelistedTools} 与 {@code whitelistedResolver}。</li>
     * </ul>
     */
    private static final List<Set<String>> WORKER_ROLE_CAPABILITIES = List.of(
            Set.of("GENERAL", "KNOWLEDGE_RETRIEVAL", "WEB_SEARCH"),
            Set.of("GENERAL", "DATA_ANALYSIS", "CODE_SYNTHESIS"),
            Set.of("GENERAL", "REPORT_SYNTHESIS")
    );

    public List<WorkerAgent> createRequestScopedSwarmWorkers(int maxWorkers,
                                                             String baseSystemPrompt,
                                                             List<ToolCallback> whitelistedTools,
                                                             ChatModel chatModel,
                                                             ToolCallbackResolver whitelistedResolver) {
        List<WorkerAgent> workers = new ArrayList<>();
        int count = Math.max(1, Math.min(maxWorkers, WORKER_ROLE_SPECS.length));
        List<ToolCallback> nonKnowledgeTools = whitelistedTools == null ? List.of() : whitelistedTools.stream()
                .filter(tc -> {
                    if (tc == null || tc.getToolDefinition() == null || tc.getToolDefinition().name() == null) {
                        return false;
                    }
                    String n = tc.getToolDefinition().name();
                    return !"knowledgeQuery".equals(n) && !n.startsWith("knowledgeBase");
                })
                .toList();
        String compactBasePrompt = compactWorkerBaseSystemPrompt(baseSystemPrompt);
        for (int i = 0; i < count; i++) {
            String workerName = WORKER_ROLE_SPECS[i][0];
            String workerDesc = WORKER_ROLE_SPECS[i][1];
            String roleInstruction = WORKER_ROLE_SPECS[i][2];
            Set<String> explicitCapabilities = i < WORKER_ROLE_CAPABILITIES.size()
                    ? WORKER_ROLE_CAPABILITIES.get(i)
                    : Set.of("GENERAL");
            String differentiatedPrompt = (compactBasePrompt != null && !compactBasePrompt.isBlank())
                    ? compactBasePrompt + "\n\n" + roleInstruction
                    : roleInstruction;
            List<ToolCallback> roleScopedTools = (i == 0 || count == 1) ? whitelistedTools : nonKnowledgeTools;
            ToolCallbackResolver roleScopedResolver = (i == 0 || count == 1) ? whitelistedResolver : name -> {
                if (name != null && ("knowledgeQuery".equals(name) || name.startsWith("knowledgeBase"))) {
                    return null;
                }
                return whitelistedResolver != null ? whitelistedResolver.resolve(name) : null;
            };
            workers.add(new WorkerAgent(
                    workerName,
                    workerDesc,
                    differentiatedPrompt,
                    roleScopedTools,
                    chatModel,
                    roleScopedResolver,
                    explicitCapabilities
            ));
        }
        return workers;
    }

    private String compactWorkerBaseSystemPrompt(String baseSystemPrompt) {
        if (baseSystemPrompt == null || baseSystemPrompt.length() <= 600) {
            return baseSystemPrompt;
        }
        int tenantIdx = baseSystemPrompt.lastIndexOf("[租户与权限上下文:");
        String tenantSuffix = tenantIdx >= 0 ? "\n" + baseSystemPrompt.substring(tenantIdx) : "";
        return baseSystemPrompt.substring(0, 450) + "..." + tenantSuffix;
    }

    public List<ToolCallback> buildWhitelistedWorkerTools(List<String> enabledToolNames,
                                                          List<ToolCallback> guardedTools,
                                                          ToolCallbackResolver guardedResolver) {
        Map<String, ToolCallback> toolByName = new LinkedHashMap<>();
        Set<String> whitelist = new HashSet<>(enabledToolNames != null ? enabledToolNames : List.of());
        if (guardedTools != null) {
            for (ToolCallback tc : guardedTools) {
                if (tc != null && tc.getToolDefinition() != null) {
                    String name = tc.getToolDefinition().name();
                    if (whitelist.contains(name)) {
                        toolByName.put(name, tc);
                    }
                }
            }
        }
        if (guardedResolver != null && enabledToolNames != null) {
            for (String toolName : enabledToolNames) {
                if (toolName != null && !toolByName.containsKey(toolName)) {
                    ToolCallback resolved = guardedResolver.resolve(toolName);
                    if (resolved != null) {
                        toolByName.put(toolName, resolved);
                    }
                }
            }
        }
        return List.copyOf(toolByName.values());
    }

    private String appendTenantContextHeader(String basePrompt, ChatRequest request) {
        String base = basePrompt != null ? basePrompt : "";
        if (request == null) {
            return base;
        }
        if (request.getWorkspaceId() <= 0L && request.getUserId() <= 0L && request.getBotId() <= 0L) {
            return base;
        }
        String header = String.format(
                Locale.ROOT,
                "[租户与权限上下文: workspaceId=%d, userId=%d, botId=%d, requestId=%s]",
                request.getWorkspaceId(),
                request.getUserId(),
                request.getBotId(),
                request.getRequestId() != null ? request.getRequestId() : ""
        );
        return base.isBlank() ? header : base + "\n" + header;
    }

    private ToolCallback wrapCancellableTool(ToolCallback delegate, OrchestrationCallTracker tracker) {
        if (delegate == null) {
            return null;
        }
        String toolName = delegate.getToolDefinition() != null ? delegate.getToolDefinition().name() : "unknown";
        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                if (tracker.isBudgetExceeded()) {
                    throw new CancellationException(AgentOrchestrator.ERR_BUDGET_EXCEEDED);
                }
                if (tracker.isCancelled() || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("多智能体编排请求已超时或取消，阻断后续工具调用");
                }
                log.info("[SWARM_TOOL_CALL] tool={}, input={}", toolName, toolInput);
                String result = delegate.call(toolInput);
                log.info("[SWARM_TOOL_RESULT] tool={}, outputLen={}", toolName, result != null ? result.length() : 0);
                return compactKnowledgeToolResult(toolName, result);
            }

            @Override
            public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                if (tracker.isBudgetExceeded()) {
                    throw new CancellationException(AgentOrchestrator.ERR_BUDGET_EXCEEDED);
                }
                if (tracker.isCancelled() || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("多智能体编排请求已超时或取消，阻断后续工具调用");
                }
                log.info("[SWARM_TOOL_CALL] tool={}, input={}", toolName, toolInput);
                String result = delegate.call(toolInput, toolContext);
                log.info("[SWARM_TOOL_RESULT] tool={}, outputLen={}", toolName, result != null ? result.length() : 0);
                return compactKnowledgeToolResult(toolName, result);
            }
        };
    }

    private String compactKnowledgeToolResult(String toolName, String result) {
        if (result == null || toolName == null) {
            return result;
        }
        if (("knowledgeQuery".equals(toolName) || toolName.startsWith("knowledgeBase")) && result.length() > 1600) {
            return result.substring(0, 1600)
                    + "\n\n[注：以上为知识库预检索核心命中片段，无需再次重复调用任何知识库工具，请直接基于上述内容精炼作答]";
        }
        return result;
    }

    private boolean isInvalidMultiAgentAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String trimmed = answer.trim();
        return trimmed.startsWith("执行失败:")
                || trimmed.startsWith("Exception:")
                || "聚合结果为空".equals(trimmed)
                || "处理计划为空且无可用对话模型".equals(trimmed)
                || "Empty response from model for unknown reason".equals(trimmed);
    }

    private boolean hasAllWorkersFailed(Map<String, String> blackboardFacts) {
        if (blackboardFacts == null || blackboardFacts.isEmpty()) {
            return false;
        }
        return blackboardFacts.values().stream()
                .allMatch(v -> v == null || v.isBlank()
                        || v.startsWith("执行失败:")
                        || v.startsWith("Exception:")
                        || v.startsWith("【执行降级】")
                        || v.startsWith("【执行错误】"));
    }

    private long resolveTimeoutMs(String parametersJson) {
        long defaultTimeout = Math.max(50L, properties.getTimeoutMs());
        long timeout = resolveLongParam(parametersJson, "timeoutMs", -1L);
        if (timeout <= 0) {
            timeout = resolveLongParam(parametersJson, "swarmTimeoutMs", -1L);
        }
        if (timeout <= 0) {
            timeout = resolveLongParam(parametersJson, "orchestrationTimeoutMs", defaultTimeout);
        }
        return Math.max(50L, timeout);
    }

    private int resolveMaxWorkers(String parametersJson) {
        int defaultWorkers = Math.max(1, properties.getMaxWorkers());
        int configured = resolveIntParam(parametersJson, "maxWorkers", -1);
        if (configured <= 0) {
            configured = resolveIntParam(parametersJson, "swarmMaxWorkers", defaultWorkers);
        }
        return Math.max(1, Math.min(configured, WORKER_ROLE_SPECS.length));
    }

    private int resolveDebateRounds(String parametersJson) {
        int defaultRounds = Math.max(1, properties.getDebateRounds());
        int rounds = resolveIntParam(parametersJson, "debateRounds", defaultRounds);
        return Math.max(1, Math.min(rounds, 2));
    }

    private double resolveMaxCostCny(String parametersJson) {
        double defaultMaxCost = properties.getMaxCostCny();
        double cost = resolveDoubleParam(parametersJson, "maxCostCny", Double.NaN);
        if (Double.isNaN(cost)) {
            cost = resolveDoubleParam(parametersJson, "swarmMaxCostCny", defaultMaxCost);
        }
        return cost;
    }

    private int resolveIntParam(String parametersJson, String key, int defaultValue) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return defaultValue;
        }
        try {
            JSONObject json = JSONObject.parseObject(parametersJson);
            if (json != null && json.containsKey(key) && json.get(key) != null) {
                return json.getIntValue(key, defaultValue);
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private long resolveLongParam(String parametersJson, String key, long defaultValue) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return defaultValue;
        }
        try {
            JSONObject json = JSONObject.parseObject(parametersJson);
            if (json != null && json.containsKey(key) && json.get(key) != null) {
                return json.getLongValue(key, defaultValue);
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private double resolveDoubleParam(String parametersJson, String key, double defaultValue) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return defaultValue;
        }
        try {
            JSONObject json = JSONObject.parseObject(parametersJson);
            if (json != null && json.containsKey(key) && json.get(key) != null) {
                return json.getDoubleValue(key);
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    public static long estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        long cjk = text.codePoints()
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                .count();
        long other = text.length() - cjk;
        return Math.max(1L, Math.round(cjk / 1.5D + other / 4.0D));
    }

    /**
     * 请求级多智能体调用追踪器（负责取消令牌、活跃线程中断、运行期 Token 与人民币成本累加熔断）
     */
    private final class OrchestrationCallTracker {
        private final double maxCostCny;
        private final long staticSystemTokens;
        private final long timeoutMs;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean budgetExceeded = new AtomicBoolean(false);
        private final Set<Thread> activeThreads = ConcurrentHashMap.newKeySet();
        private final AtomicInteger callSequence = new AtomicInteger(0);
        private final AtomicLong promptTokens = new AtomicLong(0L);
        private final AtomicLong completionTokens = new AtomicLong(0L);
        private final AtomicLong reportedCacheHitTokens = new AtomicLong(0L);
        private final DoubleAdder accumulatedCostCny = new DoubleAdder();

        OrchestrationCallTracker(double maxCostCny, long staticSystemTokens, long timeoutMs) {
            this.maxCostCny = maxCostCny;
            this.staticSystemTokens = staticSystemTokens;
            this.timeoutMs = timeoutMs;
        }

        void registerThread(Thread thread) {
            if (thread != null) {
                activeThreads.add(thread);
                if (cancelled.get()) {
                    thread.interrupt();
                }
            }
        }

        void unregisterThread(Thread thread) {
            if (thread != null) {
                activeThreads.remove(thread);
            }
        }

        boolean isCancelled() {
            return cancelled.get();
        }

        boolean isBudgetExceeded() {
            return budgetExceeded.get();
        }

        long getPromptTokens() {
            return promptTokens.get();
        }

        int getCallCount() {
            return callSequence.get();
        }

        long getCompletionTokens() {
            return completionTokens.get();
        }

        double getAccumulatedCostCny() {
            return accumulatedCostCny.sum();
        }

        void cancelAll() {
            cancelled.set(true);
            Thread current = Thread.currentThread();
            for (Thread t : activeThreads) {
                if (t != current) {
                    try {
                        t.interrupt();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        void recordCallUsageAndCheckBudget(Prompt prompt, ChatResponse response) {
            int seq = callSequence.incrementAndGet();
            long inTokens = 0L;
            long outTokens = 0L;
            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                Usage usage = response.getMetadata().getUsage();
                if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
                    inTokens = usage.getPromptTokens();
                }
                if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) {
                    outTokens = usage.getCompletionTokens();
                }
            }
            if (inTokens <= 0L && prompt != null) {
                inTokens = estimateTokenCount(prompt.getContents());
            }
            if (outTokens <= 0L && response != null && response.getResult() != null
                    && response.getResult().getOutput() != null) {
                outTokens = estimateTokenCount(response.getResult().getOutput().getText());
            }

            promptTokens.addAndGet(inTokens);
            completionTokens.addAndGet(outTokens);

            // 保守记账口径：仅当底层响应真实返回 DeepSeek 缓存命中字段（prompt_cache_hit_tokens）时才按命中价计费，
            // 否则一律按“缓存未命中”价格计费，杜绝把后续调用默认视为缓存命中而低估成本。
            long reportedCacheHit = extractReportedCacheHitTokens(response);
            long cacheHitTokens = reportedCacheHit > 0L ? Math.min(inTokens, reportedCacheHit) : 0L;
            long cacheMissTokens = Math.max(0L, inTokens - cacheHitTokens);
            if (reportedCacheHit > 0L) {
                reportedCacheHitTokens.addAndGet(cacheHitTokens);
            }
            callSequence.get();

            double callCostCny = (cacheHitTokens * properties.effectiveInputCacheHitPricePerMillionCny()
                    + cacheMissTokens * properties.effectiveInputCacheMissPricePerMillionCny()
                    + outTokens * properties.effectiveOutputPricePerMillionCny()) / 1_000_000.0D;
            accumulatedCostCny.add(callCostCny);

            if (accumulatedCostCny.sum() > maxCostCny) {
                budgetExceeded.set(true);
                cancelAll();
                throw new CancellationException(AgentOrchestrator.ERR_BUDGET_EXCEEDED
                        + ": 运行期累计成本 " + accumulatedCostCny.sum() + " 元超过预算上限 " + maxCostCny + " 元");
            }
        }

        long getReportedCacheHitTokens() {
            return reportedCacheHitTokens.get();
        }
    }

    /**
     * 从 ChatResponse 的原生 Usage 中读取 DeepSeek 官方返回的缓存命中 Token 数（prompt_cache_hit_tokens）。
     * 原生 Usage 可能为 Jackson JsonNode（DeepSeekCompatibleChatModel）或 Map；读取失败或缺失时返回 0（按未命中保守计价）。
     */
    static long extractReportedCacheHitTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return 0L;
        }
        Object nativeUsage;
        try {
            nativeUsage = response.getMetadata().getUsage().getNativeUsage();
        } catch (Exception e) {
            return 0L;
        }
        if (nativeUsage instanceof com.fasterxml.jackson.databind.JsonNode node) {
            com.fasterxml.jackson.databind.JsonNode hit = node.path("prompt_cache_hit_tokens");
            return hit.isNumber() ? Math.max(0L, hit.asLong()) : 0L;
        }
        if (nativeUsage instanceof Map<?, ?> map) {
            Object hit = map.get("prompt_cache_hit_tokens");
            if (hit instanceof Number n) {
                return Math.max(0L, n.longValue());
            }
        }
        return 0L;
    }

    /**
     * 请求级受控模型探针（用于验收测试与诊断）：暴露取消/预算受控模型及其运行期累计成本。
     */
    public record GuardedModelProbe(ChatModel model,
                                    java.util.function.DoubleSupplier accumulatedCostCny,
                                    java.util.function.BooleanSupplier budgetExceeded,
                                    java.util.function.LongSupplier reportedCacheHitTokens,
                                    Runnable cancelAll) {
    }

    /**
     * 构建与生产路径完全相同的请求级取消/预算受控 ChatModel（生产路径 executeSwarmMode/executeDebateMode 同样使用该装饰器）。
     */
    public GuardedModelProbe createGuardedModel(ChatModel delegate, double maxCostCny, long staticSystemTokens, long timeoutMs) {
        OrchestrationCallTracker tracker = new OrchestrationCallTracker(maxCostCny, staticSystemTokens, timeoutMs);
        ChatModel model = new CancellableBudgetedChatModel(delegate, tracker);
        return new GuardedModelProbe(model, tracker::getAccumulatedCostCny, tracker::isBudgetExceeded,
                tracker::getReportedCacheHitTokens, tracker::cancelAll);
    }

    /**
     * 支持超时立即中断与运行期成本熔断的 ChatModel 包装器 (P0-3 / P0-4)
     */
    private final class CancellableBudgetedChatModel implements ChatModel {
        private final ChatModel delegate;
        private final OrchestrationCallTracker tracker;

        CancellableBudgetedChatModel(ChatModel delegate, OrchestrationCallTracker tracker) {
            this.delegate = delegate;
            this.tracker = tracker;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            ensureActive();
            Thread current = Thread.currentThread();
            tracker.registerThread(current);
            boolean acquired = false;
            try {
                acquired = llmCallSemaphore.tryAcquire(tracker.timeoutMs, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    throw new CancellationException("获取多智能体 LLM 并发许可超时");
                }
                ensureActive();
                Prompt effectivePrompt = prompt;
                if (prompt != null && prompt.getOptions() == null
                        && delegate instanceof tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel) {
                    effectivePrompt = new Prompt(
                            prompt.getInstructions(),
                            tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions.builder()
                                    .thinkingEnabled(false)
                                    .build()
                    );
                }
                ChatResponse response = delegate.call(effectivePrompt);
                ensureActive();
                tracker.recordCallUsageAndCheckBudget(prompt, response);
                return response;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new CancellationException("多智能体 LLM 调用等待被中断");
            } finally {
                if (acquired) {
                    llmCallSemaphore.release();
                }
                tracker.unregisterThread(current);
            }
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                ensureActive();
                Thread subThread = Thread.currentThread();
                tracker.registerThread(subThread);
                AtomicBoolean permitReleased = new AtomicBoolean(false);
                boolean acquired;
                try {
                    acquired = llmCallSemaphore.tryAcquire(tracker.timeoutMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    tracker.unregisterThread(subThread);
                    return Flux.error(new CancellationException("多智能体流式 LLM 调用等待被中断"));
                }
                if (!acquired) {
                    tracker.unregisterThread(subThread);
                    return Flux.error(new CancellationException("获取多智能体流式 LLM 并发许可超时"));
                }
                try {
                    ensureActive();
                } catch (RuntimeException ex) {
                    if (permitReleased.compareAndSet(false, true)) {
                        llmCallSemaphore.release();
                    }
                    tracker.unregisterThread(subThread);
                    return Flux.error(ex);
                }
                return delegate.stream(prompt)
                        .doOnSubscribe(sub -> ensureActive())
                        .doOnNext(resp -> {
                            ensureActive();
                            tracker.recordCallUsageAndCheckBudget(prompt, resp);
                        })
                        .doFinally(signalType -> {
                            if (permitReleased.compareAndSet(false, true)) {
                                llmCallSemaphore.release();
                            }
                            tracker.unregisterThread(subThread);
                        });
            });
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return delegate.getDefaultOptions();
        }

        private void ensureActive() {
            if (tracker.isBudgetExceeded()) {
                throw new CancellationException(AgentOrchestrator.ERR_BUDGET_EXCEEDED);
            }
            if (tracker.isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("多智能体编排请求已超时或取消，阻断后续 LLM 调用");
            }
        }
    }
}
