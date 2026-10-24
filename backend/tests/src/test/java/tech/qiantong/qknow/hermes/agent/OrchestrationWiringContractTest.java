package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.agent.orchestration.BoundedTtlMap;
import tech.qiantong.qknow.hermes.agent.orchestration.MultiAgentOrchestrationService;
import tech.qiantong.qknow.hermes.agent.orchestration.OrchestrationProperties;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.hermes.proto.OrchestrationMode;
import tech.qiantong.qknow.module.kb.service.agent.impl.KbAgentConfigServiceImpl;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P0-15 多智能体生产接线全链路契约测试 (唯一权威测试套件)
 *
 * <p>全面覆盖：
 * <ul>
 *   <li>P0-1: SWARM Worker 真实走 {@link WorkerAgent}/{@code ReactAgent} 并触发真实 {@link ToolCallback}，3 角色差异化 SystemPrompt；</li>
 *   <li>P0-2: 凭单/黑板/辩论缓存全部采用 {@link BoundedTtlMap} 有界 LRU+TTL 结构，且 {@link LlmDebateCoordinator.DebateReceipt} 不保存全文；</li>
 *   <li>P0-3: DeepSeek 官方价格表（缓存命中/未命中输入 + 输出 + Planner/Worker/Aggregator/Debate）预检与运行期 Usage 实时熔断；</li>
 *   <li>P0-4: 超时后通过取消令牌与线程中断立即停止底层多智能体调用，杜绝回退 SINGLE 后继续消耗 Token；</li>
 *   <li>P0-5: 全局开关 {@code hermes.orchestration.enabled} 默认 {@code false} (fail-safe)，通过 {@link OrchestrationProperties} 注入。</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrchestrationWiringContractTest {

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ToolCallbackResolver toolResolver;

    @Mock
    private RetrievalEvaluator retrievalEvaluator;

    @Mock
    private ChatModel chatModel;

    private OrchestrationProperties orchestrationProperties;
    private MultiAgentOrchestrationService orchestrationService;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        PlanSolveConfig planSolveConfig = new PlanSolveConfig();
        planSolveConfig.setEnabled(false);
        planSolveConfig.setRpReactEnabled(false);

        orchestrationProperties = new OrchestrationProperties();
        orchestrationService = new MultiAgentOrchestrationService(orchestrationProperties);

        when(chatModelFactory.getChatModel(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(chatModel);
        orchestrator = new AgentOrchestrator(
                chatModelFactory, toolResolver, retrievalEvaluator, planSolveConfig,
                null, null, null, null, null, orchestrationService);
        assertFalse(orchestrator.isUsingFallbackOrchestrationService(),
                "Spring 主构造器注入 MultiAgentOrchestrationService 时不得处于 fallback 状态");
    }

    private ChatResponse createChatResponse(String text) {
        Generation generation = new Generation(new AssistantMessage(text));
        return new ChatResponse(List.of(generation));
    }

    private ChatResponse createChatResponseWithUsage(String text, int promptTokens, int completionTokens) {
        return createChatResponseWithCacheUsage(text, promptTokens, completionTokens, null);
    }

    private ChatResponse createChatResponseWithCacheUsage(String text, int promptTokens, int completionTokens,
                                                          Integer cacheHitTokens) {
        Generation generation = new Generation(new AssistantMessage(text));
        Usage usage = new Usage() {
            @Override
            public Integer getPromptTokens() {
                return promptTokens;
            }

            @Override
            public Integer getCompletionTokens() {
                return completionTokens;
            }

            @Override
            public Integer getTotalTokens() {
                return promptTokens + completionTokens;
            }

            @Override
            public Object getNativeUsage() {
                if (cacheHitTokens != null) {
                    return Map.of(
                            "prompt_tokens", promptTokens,
                            "completion_tokens", completionTokens,
                            "prompt_cache_hit_tokens", cacheHitTokens
                    );
                }
                return Map.of("prompt_tokens", promptTokens, "completion_tokens", completionTokens);
            }
        };
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(usage)
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    private ChatResponse createToolCallResponse(String callId, String toolName, String argumentsJson) {
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall(callId, "function", toolName, argumentsJson);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private ChatRequest.Builder baseRequestBuilder(String requestId, String question) {
        return ChatRequest.newBuilder()
                .setRequestId(requestId)
                .setWorkspaceId(1001L)
                .setBotId(2002L)
                .setUserId(3003L)
                .setConversationId(4004L)
                .setQuestion(question)
                .setSystemPrompt("你是企业级知识库智能助手")
                .setModelConfig(ModelConfig.newBuilder()
                        .setModelId(1L)
                        .setPlatform("deepseek")
                        .setBaseUrl("https://api.deepseek.com")
                        .setModelName("deepseek-flash")
                        .setKeyId(10L)
                        .build());
    }

    @Test
    @DisplayName("契约 1 (P0-5): 全局开关 hermes.orchestration.enabled 默认 false (fail-safe)，未显式开启时请求 SWARM 自动记录 DISABLED 并回退 SINGLE")
    void globalSwitchDefaultsToFalseAndRequiresExplicitEnablement() {
        // 1. 验证配置类默认 fail-safe 关闭
        OrchestrationProperties defaultProps = new OrchestrationProperties();
        assertFalse(defaultProps.isEnabled(), "hermes.orchestration.enabled 默认必须为 false (fail-safe)");

        // 2. 验证控制面解析器默认 SINGLE 零回归
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                KbAgentConfigServiceImpl.resolveOrchestrationMode(null));
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                KbAgentConfigServiceImpl.resolveOrchestrationMode(""));
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE,
                KbAgentConfigServiceImpl.resolveOrchestrationMode("{\"temperature\":0.7}"));
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                KbAgentConfigServiceImpl.resolveOrchestrationMode("{\"orchestrationMode\":\"SWARM\"}"));
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_DEBATE,
                KbAgentConfigServiceImpl.resolveOrchestrationMode("{\"orchestrationMode\":\"debate\"}"));

        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            llmCallCount.incrementAndGet();
            return createChatResponse("单智能体默认基线回答");
        });

        // 3. 默认 SINGLE 请求：零回归
        ChatRequest singleReq = baseRequestBuilder("req-single-01", "什么是企业知识库？").build();
        List<ChatEvent> singleEvents = orchestrator.chat(singleReq).collectList().block();
        assertNotNull(singleEvents);
        assertFalse(singleEvents.isEmpty());
        assertTrue(singleEvents.get(singleEvents.size() - 1).hasDone());

        AgentOrchestrator.OrchestrationExecutionReceipt singleReceipt =
                orchestrator.getLastOrchestrationReceipt("req-single-01").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, singleReceipt.requestedMode());
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, singleReceipt.effectiveMode());
        assertFalse(singleReceipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_NONE, singleReceipt.errorCode());

        // 4. 全局开关保持默认 false 时请求 SWARM：必须被拦截并记录 DISABLED 回退 SINGLE
        llmCallCount.set(0);
        ChatRequest swarmWhenDisabledReq = baseRequestBuilder("req-disabled-01", "测试全局默认关闭拦截")
                .setParameters("{\"orchestrationMode\":\"SWARM\"}")
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_SWARM)
                .build();
        List<ChatEvent> disabledEvents = orchestrator.chat(swarmWhenDisabledReq).collectList().block();
        assertNotNull(disabledEvents);
        assertEquals(3, disabledEvents.size());
        assertEquals(1, llmCallCount.get(), "全局关闭时绝不允许执行多智能体多次调用，只能执行 1 次 SINGLE 回退");

        AgentOrchestrator.OrchestrationExecutionReceipt disabledReceipt =
                orchestrator.getLastOrchestrationReceipt("req-disabled-01").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SWARM, disabledReceipt.requestedMode());
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, disabledReceipt.effectiveMode());
        assertTrue(disabledReceipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_ORCHESTRATION_DISABLED, disabledReceipt.errorCode());
    }

    @Test
    @DisplayName("契约 2: 传入未知编排模式或非法 JSON 时记录 ORCHESTRATION_UNKNOWN_MODE 并安全降级至 SINGLE")
    void unknownOrchestrationModeOrMalformedJsonSafelyFallsBackToSingle() {
        orchestrationProperties.setEnabled(true);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(createChatResponse("未知模式安全降级后的单智能体回答"));

        ChatRequest unknownModeReq = baseRequestBuilder("req-unknown-02", "测试未知编排模式")
                .setParameters("{\"orchestrationMode\":\"GALAXY_QUANTUM_MODE\"}")
                .build();
        List<ChatEvent> events = orchestrator.chat(unknownModeReq).collectList().block();
        assertNotNull(events);
        assertEquals(3, events.size());
        assertEquals("未知模式安全降级后的单智能体回答", events.get(0).getChunk().getText());

        AgentOrchestrator.OrchestrationExecutionReceipt receipt =
                orchestrator.getLastOrchestrationReceipt("req-unknown-02").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, receipt.effectiveMode());
        assertTrue(receipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_ORCHESTRATION_UNKNOWN_MODE, receipt.errorCode());

        // 非法 JSON 同样安全降级并记录 ORCHESTRATION_UNKNOWN_MODE
        ChatRequest badJsonReq = baseRequestBuilder("req-bad-json-02", "测试损坏JSON参数")
                .setParameters("{malformed_json")
                .build();
        orchestrator.chat(badJsonReq).collectList().block();
        AgentOrchestrator.OrchestrationExecutionReceipt badJsonReceipt =
                orchestrator.getLastOrchestrationReceipt("req-bad-json-02").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, badJsonReceipt.effectiveMode());
        assertTrue(badJsonReceipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_ORCHESTRATION_UNKNOWN_MODE, badJsonReceipt.errorCode());
    }

    @Test
    @DisplayName("契约 3 (P0-1): SWARM Worker 真实通过 WorkerAgent/ReactAgent 调用白名单 ToolCallback，具备差异化 SystemPrompt 并拦截未授权工具")
    void swarmWorkersExecuteRealReactAgentToolCallsAndHaveDifferentiatedPrompts() {
        orchestrationProperties.setEnabled(true);

        // 1. 验证 createRequestScopedSwarmWorkers 创建的是真实 WorkerAgent 实例（非匿名子类），且 3 个 Worker 具备差异化角色 Prompt
        List<WorkerAgent> sampleWorkers = orchestrationService.createRequestScopedSwarmWorkers(
                3, "基础系统指令", List.of(), chatModel, toolResolver);
        assertEquals(3, sampleWorkers.size());
        for (WorkerAgent w : sampleWorkers) {
            assertEquals(WorkerAgent.class, w.getClass(), "严禁使用匿名子类覆盖 WorkerAgent.chat 绕过 ReactAgent");
        }
        Set<String> distinctPrompts = sampleWorkers.stream()
                .map(WorkerAgent::getSystemPrompt)
                .collect(Collectors.toSet());
        assertEquals(3, distinctPrompts.size(), "3 个 Worker 必须拥有差异化的角色专属 SystemPrompt");
        assertTrue(sampleWorkers.get(0).getSystemPrompt().contains("RAGWorker"));
        assertTrue(sampleWorkers.get(1).getSystemPrompt().contains("AnalysisWorker"));
        assertTrue(sampleWorkers.get(2).getSystemPrompt().contains("SynthesisWorker"));
        assertTrue(sampleWorkers.get(0).getCapabilities().contains("KNOWLEDGE_RETRIEVAL"));
        assertTrue(sampleWorkers.get(1).getCapabilities().contains("DATA_ANALYSIS"));
        assertTrue(sampleWorkers.get(2).getCapabilities().contains("REPORT_SYNTHESIS"));

        // 2. 配置白名单工具 weatherQuery 与未授权工具 adminSecretTool
        AtomicInteger weatherToolCallCount = new AtomicInteger(0);
        AtomicReference<String> capturedToolInput = new AtomicReference<>("");
        AtomicBoolean unauthorizedToolCalled = new AtomicBoolean(false);

        ToolCallback weatherTool = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("weatherQuery")
                        .description("查询指定城市实时天气")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                weatherToolCallCount.incrementAndGet();
                capturedToolInput.set(toolInput);
                return "{\"city\":\"杭州\",\"weather\":\"晴\",\"tempC\":24}";
            }
        };

        when(toolResolver.resolve("weatherQuery")).thenReturn(weatherTool);
        when(toolResolver.resolve("adminSecretTool")).thenAnswer(inv -> {
            unauthorizedToolCalled.set(true);
            return mock(ToolCallback.class);
        });

        // 3. 模拟 Supervisor DAG 拆解 -> RAGWorker 触发 ReactAgent 工具调用 -> AnalysisWorker 分析 -> Supervisor 汇总
        String dagPlanJson = """
                [
                  {"taskId": "rag-step", "objective": "调用天气工具核实杭州实时气象数据", "requiredCapability": "KNOWLEDGE_RETRIEVAL", "dependencies": []},
                  {"taskId": "analysis-step", "objective": "基于杭州气象数据分析出行建议", "requiredCapability": "DATA_ANALYSIS", "dependencies": ["rag-step"]}
                ]
                """;
        AtomicBoolean ragWorkerToolRequested = new AtomicBoolean(false);

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String fullPrompt = prompt.getContents();

            // (a) Supervisor Planner 阶段
            if (fullPrompt.contains("有向无环图（DAG）") || fullPrompt.contains("用户目标任务：")) {
                return createChatResponse(dagPlanJson);
            }
            // (b) Supervisor Aggregator 阶段
            if (fullPrompt.contains("请根据以下各子任务的执行成果")) {
                return createChatResponse("【SWARM 协同结论】：经工具实测杭州今日晴（24℃），适宜户外拜访。");
            }
            // (c) RAGWorker 执行阶段：首次调用返回 ToolCall 触发 ReactAgent 的 AgentToolNode，第二次调用基于工具返回结果生成答案
            if (fullPrompt.contains("调用天气工具核实杭州实时气象数据")) {
                if (ragWorkerToolRequested.compareAndSet(false, true)) {
                    return createToolCallResponse("call_weather_01", "weatherQuery", "{\"city\":\"杭州\"}");
                }
                return createChatResponse("RAGWorker 核验事实：工具返回杭州晴、气温 24℃。");
            }
            // (d) AnalysisWorker 执行阶段
            return createChatResponse("AnalysisWorker 分析结论：24℃晴天出行指数优。");
        });

        ChatRequest request = baseRequestBuilder("req-swarm-tool-03", "请核实杭州天气并给出出行分析")
                .addToolMethodIds("weatherQuery")
                .setParameters("{\"orchestrationMode\":\"SWARM\",\"maxWorkers\":3}")
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_SWARM)
                .build();

        List<ChatEvent> events = orchestrator.chat(request).collectList().block();

        assertNotNull(events);
        assertEquals(3, events.size(), "emitSingleAnswer 必须严格输出 Chunk -> Finished -> Done");
        assertTrue(events.get(0).hasChunk());
        assertTrue(events.get(1).hasFinished());
        assertTrue(events.get(2).hasDone());
        assertTrue(events.get(0).getChunk().getText().contains("【SWARM 协同结论】"));

        // 核心 P0-1 断言：ReactAgent 真实调用了 ToolCallback，且未授权工具绝未被触达
        assertEquals(1, weatherToolCallCount.get(), "SWARM Worker 必须通过 ReactAgent 真实执行 ToolCallback.call()");
        assertTrue(capturedToolInput.get().contains("杭州"), "工具回调必须接收到模型下发的 JSON 参数");
        assertFalse(unauthorizedToolCalled.get(), "非白名单工具绝不能被解析或执行");

        // 验证黑板快照记录了两个子任务的真实产出
        Map<String, String> blackboardFacts = orchestrator.getLastSwarmBlackboardFacts("req-swarm-tool-03");
        assertEquals(2, blackboardFacts.size());
        assertTrue(blackboardFacts.get("rag-step").contains("24℃"));

        AgentOrchestrator.OrchestrationExecutionReceipt receipt =
                orchestrator.getLastOrchestrationReceipt("req-swarm-tool-03").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SWARM, receipt.effectiveMode());
        assertFalse(receipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_NONE, receipt.errorCode());
    }

    @Test
    @DisplayName("契约 4 (P0-2): DEBATE 模式执行三角色真实 LLM 辩论，且 DebateReceipt 仅保存长度与 SHA-256 哈希、绝不驻留全文")
    void debateModeExecutesThreeRolesAndStoresHashSummaryWithoutFullText() {
        orchestrationProperties.setEnabled(true);

        // 1. 静态结构校验：确认 DebateReceipt Record 不包含任何保存全文的字段，防止内存与隐私泄漏
        Set<String> componentNames = Arrays.stream(LlmDebateCoordinator.DebateReceipt.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertFalse(componentNames.contains("proposerDraft"), "DebateReceipt 严禁保存 proposerDraft 全文");
        assertFalse(componentNames.contains("criticFeedback"), "DebateReceipt 严禁保存 criticFeedback 全文");
        assertFalse(componentNames.contains("finalVerdict"), "DebateReceipt 严禁保存 finalVerdict 全文");
        assertTrue(componentNames.containsAll(List.of(
                "proposerLength", "criticLength", "finalVerdictLength",
                "proposerSha256", "criticSha256", "finalVerdictSha256")));

        AtomicInteger callSequence = new AtomicInteger(0);
        String proposerText = "正方立论初稿：建议默认启用多智能体。";
        String criticText = "反方审校意见：默认启用会导致成本与延迟翻倍，应按 Bot 配置默认关闭。";
        String verdictText = "终审裁决结论：多智能体编排必须按 Bot 显式开启、默认关闭，确保默认路径零回归。";

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            int step = callSequence.incrementAndGet();
            return switch (step) {
                case 1 -> createChatResponse(proposerText);
                case 2 -> createChatResponse(criticText);
                default -> createChatResponse(verdictText);
            };
        });

        ChatRequest request = baseRequestBuilder("req-debate-04", "多智能体编排是否应该全局默认开启？")
                .setParameters("{\"orchestrationMode\":\"DEBATE\",\"debateRounds\":1}")
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_DEBATE)
                .build();

        List<ChatEvent> events = orchestrator.chat(request).collectList().block();

        assertNotNull(events);
        assertEquals(3, events.size());
        assertEquals(verdictText, events.get(0).getChunk().getText());
        assertEquals(3, callSequence.get(), "1 轮三角色辩论必须精确执行 3 次真实 LLM 调用");

        LlmDebateCoordinator.DebateReceipt debateReceipt =
                orchestrator.getLastDebateReceipt("req-debate-04").orElseThrow();
        assertEquals(1, debateReceipt.roundsExecuted());
        assertEquals(3, debateReceipt.totalLlmCalls());
        assertEquals(proposerText.length(), debateReceipt.proposerLength());
        assertEquals(criticText.length(), debateReceipt.criticLength());
        assertEquals(verdictText.length(), debateReceipt.finalVerdictLength());
        assertEquals(64, debateReceipt.proposerSha256().length(), "SHA-256 摘要必须为 64 位十六进制字符串");
        assertEquals(64, debateReceipt.criticSha256().length());
        assertEquals(64, debateReceipt.finalVerdictSha256().length());
    }

    @Test
    @DisplayName("契约 5 (P0-3): 基于 DeepSeek 官方分时/缓存/输出价格表执行前置预算拦截与运行期 Usage 实时成本熔断")
    void deepSeekOfficialPricingPreCheckAndRuntimeUsageBudgetGate() {
        orchestrationProperties.setEnabled(true);

        // 1. 验证 DeepSeek 官方价格表计算（区分高峰/空闲、缓存命中/未命中输入、输出）
        orchestrationProperties.setUsePeakPricingConservative(true); // 高峰期：命中 0.04/M，未命中 2.00/M，输出 8.00/M
        double peakCost = orchestrationService.estimateOrchestrationCostCny(
                OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                "系统提示词".repeat(100),
                "用户复杂问题".repeat(50),
                List.of(),
                3,
                1,
                "{\"expectedOutputTokensPerCall\":512}"
        );
        orchestrationProperties.setUsePeakPricingConservative(false); // 空闲期：命中 0.02/M，未命中 1.00/M，输出 4.00/M
        double offPeakCost = orchestrationService.estimateOrchestrationCostCny(
                OrchestrationMode.ORCHESTRATION_MODE_SWARM,
                "系统提示词".repeat(100),
                "用户复杂问题".repeat(50),
                List.of(),
                3,
                1,
                "{\"expectedOutputTokensPerCall\":512}"
        );
        assertTrue(peakCost > 0.01D, "计入输出与全链路调用后高峰期预估成本应大于 0.01 元");
        assertEquals(peakCost * 0.5D, offPeakCost, 1e-6, "空闲期半价成本应精确为高峰期的 50%");
        orchestrationProperties.setUsePeakPricingConservative(true);

        // 2. 前置预算门禁：当 maxCostCny 低于预估成本时，不调用多智能体，直接记录 BUDGET_EXCEEDED 并回退 SINGLE
        AtomicInteger preCheckCallCount = new AtomicInteger(0);
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            preCheckCallCount.incrementAndGet();
            return createChatResponse("前置预算超限后回退 SINGLE 的回答");
        });

        ChatRequest preCheckExceededReq = baseRequestBuilder("req-budget-pre-05", "请执行多步对比分析")
                .setParameters("{\"orchestrationMode\":\"SWARM\",\"maxCostCny\":0.0001}")
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_SWARM)
                .build();
        List<ChatEvent> preEvents = orchestrator.chat(preCheckExceededReq).collectList().block();
        assertNotNull(preEvents);
        assertEquals(1, preCheckCallCount.get(), "前置预算超限时多智能体调用次数必须为 0，仅执行 1 次 SINGLE 回退调用");

        AgentOrchestrator.OrchestrationExecutionReceipt preReceipt =
                orchestrator.getLastOrchestrationReceipt("req-budget-pre-05").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, preReceipt.effectiveMode());
        assertTrue(preReceipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_BUDGET_EXCEEDED, preReceipt.errorCode());

        // 3. 运行期 Usage 实时成本熔断：前置预估通过 (maxCostCny=0.05)，但第 1 次多智能体调用返回高额真实 Usage (20k in + 5k out = 0.08 元 > 0.05 元)
        AtomicInteger runtimeMultiAgentCalls = new AtomicInteger(0);
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            Prompt p = inv.getArgument(0);
            if (p.getContents().contains("有向无环图（DAG）") || p.getContents().contains("正方立论专家")) {
                runtimeMultiAgentCalls.incrementAndGet();
                // 20,000 未命中输入 (0.04 元) + 5,000 输出 (0.04 元) = 0.08 元 > 0.05 元预算上限
                return createChatResponseWithUsage("高消耗初稿", 20_000, 5_000);
            }
            return createChatResponse("运行期 Usage 超限熔断后回退 SINGLE 的回答");
        });

        ChatRequest runtimeExceededReq = baseRequestBuilder("req-budget-runtime-05", "测试运行期 Usage 实时熔断")
                .setParameters("{\"orchestrationMode\":\"DEBATE\",\"maxCostCny\":0.05,\"expectedOutputTokensPerCall\":64}")
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_DEBATE)
                .build();
        List<ChatEvent> runtimeEvents = orchestrator.chat(runtimeExceededReq).collectList().block();
        assertNotNull(runtimeEvents);
        assertEquals("运行期 Usage 超限熔断后回退 SINGLE 的回答", runtimeEvents.get(0).getChunk().getText());
        assertEquals(1, runtimeMultiAgentCalls.get(), "第 1 次调用 Usage 超限后必须立即熔断，禁止发起第 2/3 次辩论调用");

        AgentOrchestrator.OrchestrationExecutionReceipt runtimeReceipt =
                orchestrator.getLastOrchestrationReceipt("req-budget-runtime-05").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, runtimeReceipt.effectiveMode());
        assertTrue(runtimeReceipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_BUDGET_EXCEEDED, runtimeReceipt.errorCode());
    }

    @Test
    @DisplayName("契约 6 (P0-4): SWARM/DEBATE 超时后立即中断底层线程并激活取消令牌，严禁回退 SINGLE 后继续发起后续多智能体 LLM 调用")
    void timeoutCancelsUnderlyingMultiAgentTasksAndPreventsSubsequentLlmCalls() throws InterruptedException {
        orchestrationProperties.setEnabled(true);

        AtomicInteger debateRoleCalls = new AtomicInteger(0);
        AtomicInteger subsequentDebateCallsAfterProposer = new AtomicInteger(0);
        AtomicBoolean proposerInterrupted = new AtomicBoolean(false);

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String content = prompt.getContents();
            // 识别 DEBATE 角色调用
            if (content.contains("正方立论专家") || content.contains("反方审校专家") || content.contains("终审裁决官")) {
                int step = debateRoleCalls.incrementAndGet();
                if (step == 1) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        proposerInterrupted.set(true);
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Proposer interrupted on timeout", ie);
                    }
                    return createChatResponse("迟到的正方立论");
                }
                subsequentDebateCallsAfterProposer.incrementAndGet();
                return createChatResponse("绝不应被执行的后续辩论调用");
            }
            return createChatResponse("超时取消后回退 SINGLE 的唯一回答");
        });

        ChatRequest timeoutReq = baseRequestBuilder("req-timeout-cancel-06", "测试超时后底层任务真实取消")
                .setParameters("{\"orchestrationMode\":\"DEBATE\",\"timeoutMs\":100}")
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_DEBATE)
                .build();

        List<ChatEvent> events = orchestrator.chat(timeoutReq).collectList().block();
        assertNotNull(events);
        assertEquals(3, events.size());
        assertEquals("超时取消后回退 SINGLE 的唯一回答", events.get(0).getChunk().getText());

        // 等待原本 500ms 的睡眠周期结束，验证后台绝无后续 Critic / Judge 调用发生
        Thread.sleep(250);

        assertTrue(proposerInterrupted.get(), "超时后底层阻塞的多智能体线程必须收到中断信号");
        assertEquals(0, subsequentDebateCallsAfterProposer.get(),
                "超时取消后严禁继续发起第 2 次 (Critic) 或第 3 次 (Judge) 多智能体 LLM 调用");

        AgentOrchestrator.OrchestrationExecutionReceipt receipt =
                orchestrator.getLastOrchestrationReceipt("req-timeout-cancel-06").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_DEBATE, receipt.requestedMode());
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, receipt.effectiveMode());
        assertTrue(receipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_SUPERVISOR_TIMEOUT, receipt.errorCode());

        // 2. 验证 SWARM 超时取消：Phase 1 Worker 超时被中断后，Phase 2 Worker 与 Supervisor 汇总调用绝不发生
        AtomicBoolean swarmWorkerInterrupted = new AtomicBoolean(false);
        AtomicInteger swarmSubsequentCalls = new AtomicInteger(0);

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            String content = ((Prompt) invocation.getArgument(0)).getContents();
            if (content.contains("有向无环图（DAG）") || content.contains("用户目标任务：")) {
                return createChatResponse("""
                        [
                          {"taskId": "p1", "objective": "慢速子任务P1", "requiredCapability": "KNOWLEDGE_RETRIEVAL", "dependencies": []},
                          {"taskId": "p2", "objective": "后续子任务P2", "requiredCapability": "DATA_ANALYSIS", "dependencies": ["p1"]}
                        ]
                        """);
            }
            if (content.contains("慢速子任务P1")) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    swarmWorkerInterrupted.set(true);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Swarm worker P1 interrupted on timeout", ie);
                }
                return createChatResponse("迟到的P1");
            }
            if (content.contains("后续子任务P2") || content.contains("请根据以下各子任务的执行成果")) {
                swarmSubsequentCalls.incrementAndGet();
                return createChatResponse("绝不应被执行的后续蜂群调用");
            }
            return createChatResponse("SWARM 超时取消后回退 SINGLE 的回答");
        });

        ChatRequest swarmTimeoutReq = baseRequestBuilder("req-swarm-timeout-06", "测试 SWARM 超时后底层 Worker 真实取消")
                .setParameters("{\"orchestrationMode\":\"SWARM\",\"timeoutMs\":100}")
                .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_SWARM)
                .build();

        List<ChatEvent> swarmEvents = orchestrator.chat(swarmTimeoutReq).collectList().block();
        assertNotNull(swarmEvents);
        assertEquals("SWARM 超时取消后回退 SINGLE 的回答", swarmEvents.get(0).getChunk().getText());

        Thread.sleep(250);
        assertTrue(swarmWorkerInterrupted.get(), "SWARM 超时后阻塞在 AGENT_TASK_EXECUTOR 中的 Worker 线程必须收到中断信号");
        assertEquals(0, swarmSubsequentCalls.get(), "SWARM 超时后严禁继续发起后续阶段 Worker 或 Supervisor 汇总调用");

        AgentOrchestrator.OrchestrationExecutionReceipt swarmReceipt =
                orchestrator.getLastOrchestrationReceipt("req-swarm-timeout-06").orElseThrow();
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SWARM, swarmReceipt.requestedMode());
        assertEquals(OrchestrationMode.ORCHESTRATION_MODE_SINGLE, swarmReceipt.effectiveMode());
        assertTrue(swarmReceipt.fallbackTriggered());
        assertEquals(AgentOrchestrator.ERR_SUPERVISOR_TIMEOUT, swarmReceipt.errorCode());
    }

    @Test
    @DisplayName("契约 7 (P0-2): 连续写入超过上限后 BoundedTtlMap 严格有界并淘汰最旧记录，且跨请求黑板状态完全隔离")
    void boundedTtlMapAndCrossRequestBlackboardIsolationEnforceCapacityLimit() {
        // 1. 配置容量上限为 5 的 OrchestrationProperties
        OrchestrationProperties smallCacheProps = new OrchestrationProperties();
        smallCacheProps.setEnabled(true);
        smallCacheProps.setReceiptCacheMaxEntries(5);
        smallCacheProps.setReceiptCacheTtlMs(60_000L);
        MultiAgentOrchestrationService boundedService = new MultiAgentOrchestrationService(smallCacheProps);
        orchestrator.setMultiAgentOrchestrationService(boundedService);

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            String content = ((Prompt) invocation.getArgument(0)).getContents();
            if (content.contains("有向无环图（DAG）") || content.contains("用户目标任务：")) {
                for (int i = 1; i <= 12; i++) {
                    String tag = "任务#" + i + "#";
                    if (content.contains(tag)) {
                        return createChatResponse("[{\"taskId\":\"fact-" + i
                                + "\",\"objective\":\"执行子任务#" + i + "#\",\"requiredCapability\":\"KNOWLEDGE_RETRIEVAL\",\"dependencies\":[]}]");
                    }
                }
            }
            for (int i = 1; i <= 12; i++) {
                if (content.contains("执行子任务#" + i + "#")) {
                    return createChatResponse("专属事实数据-" + i);
                }
            }
            return createChatResponse("汇总完成");
        });

        // 连续发起 12 次 SWARM 请求（超过上限 5）
        for (int i = 1; i <= 12; i++) {
            ChatRequest req = baseRequestBuilder("req-bounded-" + i, "请处理任务#" + i + "#")
                    .setParameters("{\"orchestrationMode\":\"SWARM\",\"maxWorkers\":1}")
                    .setOrchestrationMode(OrchestrationMode.ORCHESTRATION_MODE_SWARM)
                    .build();
            orchestrator.chat(req).collectList().block();
        }

        // 断言缓存大小严格等于上限 5，最旧的 1..7 已被 LRU 淘汰，最新的 8..12 保留且各自黑板隔离
        assertEquals(5, boundedService.getReceiptCacheSize(), "orchestrationReceipts 容量必须严格受限于 maxEntries=5");
        assertEquals(5, boundedService.getSwarmBlackboardCacheSize(), "lastSwarmBlackboardByRequest 容量必须严格受限于 maxEntries=5");

        for (int i = 1; i <= 7; i++) {
            assertTrue(orchestrator.getLastOrchestrationReceipt("req-bounded-" + i).isEmpty(),
                    "旧记录 req-bounded-" + i + " 必须已被 LRU 淘汰");
            assertTrue(orchestrator.getLastSwarmBlackboardFacts("req-bounded-" + i).isEmpty());
        }
        for (int i = 8; i <= 12; i++) {
            assertTrue(orchestrator.getLastOrchestrationReceipt("req-bounded-" + i).isPresent(),
                    "最新记录 req-bounded-" + i + " 必须保留");
            Map<String, String> facts = orchestrator.getLastSwarmBlackboardFacts("req-bounded-" + i);
            assertEquals(Map.of("fact-" + i, "专属事实数据-" + i), facts,
                    "每个请求的黑板快照必须完全隔离，不得混入其他请求事实");
        }
    }

    @Test
    @DisplayName("契约 8 (P0-3 硬化): 保守记账无真实缓存命中字段时一律按 cache miss 计费，真实命中时按命中计费，且 stream() 受信号量与预算熔断控制")
    void conservativeCacheMissAccountingAndGuardedStreamEnforcement() {
        orchestrationProperties.setEnabled(true);
        orchestrationProperties.setUsePeakPricingConservative(true); // 高峰期：miss=2.00/M, hit=0.04/M, out=8.00/M

        // 1. 无真实 prompt_cache_hit_tokens 时：即使 staticSystemTokens=8000 且 seq=1,2,3，也绝不能按缓存命中折扣，必须 100% 按 miss 计费
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(createChatResponseWithUsage("无缓存字段响应", 10_000, 1_000));

        MultiAgentOrchestrationService.GuardedModelProbe missProbe =
                orchestrationService.createGuardedModel(chatModel, 0.06D, 8_000L, 2_000L);

        // 单次全量 miss 成本 = (10,000 * 2.00 + 1,000 * 8.00) / 1e6 = 0.028 元
        missProbe.model().call(new Prompt("第 1 次调用"));
        assertEquals(0.028D, missProbe.accumulatedCostCny().getAsDouble(), 1e-7);
        assertEquals(0L, missProbe.reportedCacheHitTokens().getAsLong());

        // 第 2 次调用：保守口径下仍按全量 miss 计费，累计 = 0.056 元（若按旧版乐观估算 staticSystemTokens=8000 命中，则仅 +0.01232=0.04032 元）
        missProbe.model().call(new Prompt("第 2 次调用"));
        assertEquals(0.056D, missProbe.accumulatedCostCny().getAsDouble(), 1e-7,
                "无真实缓存信息时，seq>1 的累计成本不得低于按 cache miss 计算的值");
        assertFalse(missProbe.budgetExceeded().getAsBoolean());

        // 第 3 次调用：累计达 0.084 元 > 0.06 元预算上限，必须立即触发 BUDGET_EXCEEDED 熔断
        CancellationException ex = assertThrows(CancellationException.class,
                () -> missProbe.model().call(new Prompt("第 3 次调用")));
        assertTrue(ex.getMessage().contains(AgentOrchestrator.ERR_BUDGET_EXCEEDED));
        assertTrue(missProbe.budgetExceeded().getAsBoolean());

        // 2. 当底层返回真实 prompt_cache_hit_tokens=8000 时，才使用命中价 (0.04/M) + 未命中价 (2.00/M)
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(createChatResponseWithCacheUsage("含真实缓存命中响应", 10_000, 1_000, 8_000));
        MultiAgentOrchestrationService.GuardedModelProbe hitProbe =
                orchestrationService.createGuardedModel(chatModel, 0.06D, 8_000L, 2_000L);
        hitProbe.model().call(new Prompt("带真实缓存命中的调用"));
        // 8000 * 0.04 / 1e6 + 2000 * 2.00 / 1e6 + 1000 * 8.00 / 1e6 = 0.00032 + 0.004 + 0.008 = 0.01232 元
        assertEquals(0.01232D, hitProbe.accumulatedCostCny().getAsDouble(), 1e-7);
        assertEquals(8_000L, hitProbe.reportedCacheHitTokens().getAsLong());

        // 3. 验证 CancellableBudgetedChatModel.stream() 接入预算熔断与并发信号量控制
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                createChatResponseWithUsage("chunk-1", 10_000, 1_000), // 0.028 元
                createChatResponseWithUsage("chunk-2", 10_000, 1_000), // 累计 0.056 元 > 0.05 元，触发熔断
                createChatResponseWithUsage("chunk-3", 10_000, 1_000)
        ));
        MultiAgentOrchestrationService.GuardedModelProbe streamProbe =
                orchestrationService.createGuardedModel(chatModel, 0.05D, 0L, 1_000L);
        CancellationException streamBudgetEx = assertThrows(CancellationException.class,
                () -> streamProbe.model().stream(new Prompt("流式预算测试")).collectList().block());
        assertTrue(streamBudgetEx.getMessage().contains(AgentOrchestrator.ERR_BUDGET_EXCEEDED));
        assertTrue(streamProbe.budgetExceeded().getAsBoolean());

        // 取消后的 stream() 必须立即在订阅入口被拒绝
        assertThrows(CancellationException.class,
                () -> streamProbe.model().stream(new Prompt("已熔断后的流式调用")).collectList().block());
    }

    @Configuration
    @EnableConfigurationProperties(OrchestrationProperties.class)
    static class OrchestrationPropertiesTestConfig {
    }

    @Test
    @DisplayName("契约 9: Spring ApplicationContextRunner 验证 hermes.orchestration.* 与环境变量绑定、负值安全边界及构造器必需依赖注入")
    void springBootConfigurationPropertiesBindingAndConstructorInjectionGuard() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(OrchestrationPropertiesTestConfig.class);

        // 1. 默认无任何属性时：enabled=false (fail-safe)
        contextRunner.run(context -> {
            OrchestrationProperties props = context.getBean(OrchestrationProperties.class);
            assertFalse(props.isEnabled(), "缺省 hermes.orchestration.enabled 必须为 false");
            assertEquals(15_000L, props.getTimeoutMs());
            assertEquals(3, props.getMaxWorkers());
            assertEquals(1, props.getDebateRounds());
            assertEquals(0.05D, props.getMaxCostCny(), 1e-7);
        });

        // 2. 通过系统环境变量风格 (HERMES_ORCHESTRATION_ENABLED=true) 与 kebab-case 属性绑定
        contextRunner
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("test-env", Map.of(
                                "HERMES_ORCHESTRATION_ENABLED", "true"
                        ))))
                .withPropertyValues(
                        "hermes.orchestration.enabled=${HERMES_ORCHESTRATION_ENABLED:false}",
                        "hermes.orchestration.timeout-ms=22000",
                        "hermes.orchestration.max-workers=4",
                        "hermes.orchestration.debate-rounds=2",
                        "hermes.orchestration.max-cost-cny=0.12"
                )
                .run(context -> {
                    OrchestrationProperties props = context.getBean(OrchestrationProperties.class);
                    assertTrue(props.isEnabled(), "设置 HERMES_ORCHESTRATION_ENABLED=true 后 enabled 必须绑定为 true");
                    assertEquals(22_000L, props.getTimeoutMs());
                    assertEquals(4, props.getMaxWorkers());
                    assertEquals(2, props.getDebateRounds());
                    assertEquals(0.12D, props.getMaxCostCny(), 1e-7);
                });

        // 3. 非法负值/零值/越界值安全回退验证
        contextRunner
                .withPropertyValues(
                        "hermes.orchestration.timeout-ms=-500",
                        "hermes.orchestration.max-workers=-2",
                        "hermes.orchestration.debate-rounds=0",
                        "hermes.orchestration.max-cost-cny=-1.5"
                )
                .run(context -> {
                    OrchestrationProperties props = context.getBean(OrchestrationProperties.class);
                    assertEquals(OrchestrationProperties.DEFAULT_TIMEOUT_MS, props.getTimeoutMs(), "负数 timeout-ms 必须回退至安全默认值");
                    assertEquals(OrchestrationProperties.DEFAULT_MAX_WORKERS, props.getMaxWorkers(), "负数 max-workers 必须回退至安全默认值");
                    assertEquals(OrchestrationProperties.DEFAULT_DEBATE_ROUNDS, props.getDebateRounds(), "零值 debate-rounds 必须回退至安全默认值");
                    assertEquals(OrchestrationProperties.DEFAULT_MAX_COST_CNY, props.getMaxCostCny(), 1e-7, "负数 max-cost-cny 必须回退至安全默认值");
                });

        // 4. 验证 AgentOrchestrator @Autowired 主构造器拒绝 null MultiAgentOrchestrationService，且兼容测试构造器显式标记 fallback
        assertThrows(IllegalStateException.class, () -> new AgentOrchestrator(
                chatModelFactory, toolResolver, retrievalEvaluator, new PlanSolveConfig(),
                null, null, null, null, null, null),
                "Spring 主构造器注入 null MultiAgentOrchestrationService 时必须立即抛出 IllegalStateException");

        AgentOrchestrator legacyOrchestrator = new AgentOrchestrator(
                chatModelFactory, toolResolver, retrievalEvaluator);
        assertTrue(legacyOrchestrator.isUsingFallbackOrchestrationService(),
                "兼容测试构造器未注入 MultiAgentOrchestrationService 时必须显式标记 fallback 状态并输出告警日志");
    }
}
