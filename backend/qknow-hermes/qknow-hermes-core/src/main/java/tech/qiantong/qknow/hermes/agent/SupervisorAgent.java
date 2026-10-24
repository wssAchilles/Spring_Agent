package tech.qiantong.qknow.hermes.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import tech.qiantong.qknow.hermes.agent.bidding.ContractNetDispatcher;
import tech.qiantong.qknow.hermes.agent.bidding.TaskCfp;
import tech.qiantong.qknow.hermes.agent.bidding.WorkerBid;
import tech.qiantong.qknow.hermes.agent.blackboard.BlackboardEntry;
import tech.qiantong.qknow.hermes.agent.blackboard.SharedBlackboard;
import tech.qiantong.qknow.hermes.agent.dag.HierarchicalTaskPlanner;
import tech.qiantong.qknow.hermes.agent.dag.PhasedExecutionPlan;
import tech.qiantong.qknow.hermes.agent.dag.TopologicalPhasedDispatcher;
import tech.qiantong.qknow.hermes.agent.governor.DualLoopQualityReceipt;
import tech.qiantong.qknow.hermes.agent.governor.EvaluatorOptimizerGovernor;
import tech.qiantong.qknow.hermes.agent.governor.impl.DualLoopEvaluatorOptimizerEngine;
import tech.qiantong.qknow.hermes.agent.guard.SwarmLoopGuard;
import tech.qiantong.qknow.hermes.agent.orchestration.BoundedTtlMap;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * 工业级蜂群主管智能体 (SupervisorAgent)
 * 负责分层任务意图分解 (HTN/DAG)、拓扑阶段调度推进、动态合同网竞标分派、共享黑板协作、
 * 以及基于 Evaluator-Optimizer 双环自优化的最终答复闭环收敛。
 */
@Slf4j
public class SupervisorAgent extends BaseAgent {

    private final String systemPrompt;
    private final List<ToolCallback> tools;
    private final ChatModel chatModel;
    private final List<BaseAgent> workers;

    // 蜂群编排核心组件
    private final HierarchicalTaskPlanner planner;
    private final TopologicalPhasedDispatcher dispatcher;
    private final ContractNetDispatcher contractNetDispatcher;
    private final SharedBlackboard blackboard;
    private final boolean sharedBlackboardProvided;
    private final boolean customContractNetProvided;
    private final SwarmLoopGuard loopGuard;
    private final EvaluatorOptimizerGovernor evaluatorOptimizerGovernor;

    // 会话最近双环质检凭单映射（有界 LRU + TTL，防高并发内存泄漏 P0-2）
    private final BoundedTtlMap<String, DualLoopQualityReceipt> sessionQualityReceipts;
    // 会话最近黑板快照映射（有界 LRU + TTL，供隔离诊断与审计回溯 P0-2）
    private final BoundedTtlMap<String, Map<String, String>> sessionBlackboardSnapshots;

    /**
     * 完整依赖注入构造函数
     */
    public SupervisorAgent(String name, String description, String systemPrompt,
                           List<ToolCallback> tools, ChatModel chatModel, List<? extends BaseAgent> workers,
                           HierarchicalTaskPlanner planner,
                           TopologicalPhasedDispatcher dispatcher,
                           ContractNetDispatcher contractNetDispatcher,
                           SharedBlackboard blackboard,
                           SwarmLoopGuard loopGuard,
                           EvaluatorOptimizerGovernor evaluatorOptimizerGovernor) {
        this(name, description, systemPrompt, tools, chatModel, workers, planner, dispatcher,
                contractNetDispatcher, blackboard, loopGuard, evaluatorOptimizerGovernor,
                BoundedTtlMap.DEFAULT_MAX_ENTRIES, BoundedTtlMap.DEFAULT_TTL_MS);
    }

    public SupervisorAgent(String name, String description, String systemPrompt,
                           List<ToolCallback> tools, ChatModel chatModel, List<? extends BaseAgent> workers,
                           HierarchicalTaskPlanner planner,
                           TopologicalPhasedDispatcher dispatcher,
                           ContractNetDispatcher contractNetDispatcher,
                           SharedBlackboard blackboard,
                           SwarmLoopGuard loopGuard,
                           EvaluatorOptimizerGovernor evaluatorOptimizerGovernor,
                           int maxSnapshotEntries,
                           long snapshotTtlMs) {
        super(name, description);
        this.systemPrompt = systemPrompt;
        this.tools = tools != null ? List.copyOf(tools) : List.of();
        this.chatModel = chatModel;
        this.workers = workers != null ? new ArrayList<>(workers) : new ArrayList<>();
        this.planner = planner != null ? planner : new HierarchicalTaskPlanner(chatModel);
        this.loopGuard = loopGuard != null ? loopGuard : new SwarmLoopGuard();
        this.sharedBlackboardProvided = (blackboard != null);
        this.blackboard = blackboard != null ? blackboard : new SharedBlackboard();
        this.customContractNetProvided = (contractNetDispatcher != null);
        this.contractNetDispatcher = contractNetDispatcher != null ? contractNetDispatcher : new ContractNetDispatcher();
        this.dispatcher = dispatcher != null ? dispatcher : new TopologicalPhasedDispatcher(this.contractNetDispatcher, this.loopGuard);
        this.evaluatorOptimizerGovernor = evaluatorOptimizerGovernor != null
                ? evaluatorOptimizerGovernor
                : (chatModel == null ? new DualLoopEvaluatorOptimizerEngine(null) : null);
        this.sessionQualityReceipts = new BoundedTtlMap<>(maxSnapshotEntries, snapshotTtlMs);
        this.sessionBlackboardSnapshots = new BoundedTtlMap<>(maxSnapshotEntries, snapshotTtlMs);

        initWorkersInContractNet();
    }

    public SupervisorAgent(String name, String description, String systemPrompt,
                           List<ToolCallback> tools, ChatModel chatModel, List<? extends BaseAgent> workers,
                           HierarchicalTaskPlanner planner,
                           TopologicalPhasedDispatcher dispatcher,
                           ContractNetDispatcher contractNetDispatcher,
                           SharedBlackboard blackboard,
                           SwarmLoopGuard loopGuard) {
        this(name, description, systemPrompt, tools, chatModel, workers, planner, dispatcher,
                contractNetDispatcher, blackboard, loopGuard, null);
    }

    /**
     * 向后兼容传统构造函数
     */
    public SupervisorAgent(String name, String description, String systemPrompt,
                           List<ToolCallback> tools, ChatModel chatModel, List<WorkerAgent> workers) {
        this(name, description, systemPrompt, tools, chatModel, workers, null, null, null, null, null, null);
    }

    public Optional<DualLoopQualityReceipt> getLastQualityReceipt(String sessionId) {
        return Optional.ofNullable(sessionQualityReceipts.get(sessionId));
    }

    public Map<String, String> getLastBlackboardSnapshot(String sessionId) {
        return sessionBlackboardSnapshots.getOrDefault(sessionId, Map.of());
    }

    public int getBlackboardSnapshotCacheSize() {
        return sessionBlackboardSnapshots.size();
    }

    private Set<String> resolveWorkerCapabilities(BaseAgent worker) {
        String wName = worker.getName() != null ? worker.getName().toUpperCase(Locale.ROOT) : "";
        if (worker instanceof WorkerAgent wa && wa.getCapabilities() != null && !wa.getCapabilities().isEmpty()) {
            Set<String> explicit = new LinkedHashSet<>(wa.getCapabilities());
            explicit.add("GENERAL");
            if (!wName.isBlank()) {
                explicit.add(wName);
            }
            return explicit;
        }
        Set<String> capabilities = new LinkedHashSet<>();
        capabilities.add("GENERAL");
        if (wName.contains("RAG") || wName.contains("RETRIEVAL")) {
            capabilities.add("KNOWLEDGE_RETRIEVAL");
            capabilities.add("WEB_SEARCH");
        } else if (wName.contains("ANALYSIS") || wName.contains("DATA") || wName.contains("LOGIC")) {
            capabilities.add("DATA_ANALYSIS");
            capabilities.add("CODE_SYNTHESIS");
        } else if (wName.contains("SYNTHESIS") || wName.contains("SUMMARY") || wName.contains("REPORT")) {
            capabilities.add("REPORT_SYNTHESIS");
        } else {
            capabilities.addAll(Set.of(
                    "KNOWLEDGE_RETRIEVAL", "WEB_SEARCH",
                    "DATA_ANALYSIS", "CODE_SYNTHESIS", "REPORT_SYNTHESIS"
            ));
        }
        if (!wName.isBlank()) {
            capabilities.add(wName);
        }
        return capabilities;
    }

    private void initWorkersInContractNet() {
        EnhancedBaseAgent firstWorker = null;
        for (BaseAgent w : this.workers) {
            if (w instanceof EnhancedBaseAgent eb) {
                this.contractNetDispatcher.registerWorker(eb);
                if (firstWorker == null) {
                    firstWorker = eb;
                }
            } else if (w != null) {
                // 将传统 Worker 包装为 EnhancedBaseAgent 适配器，根据角色绑定差异化能力画像并打通上游黑板上下文
                Set<String> capabilities = resolveWorkerCapabilities(w);
                EnhancedBaseAgent adapter = new EnhancedBaseAgent(w.getName(), w.getDescription(), capabilities, 4) {
                    @Override
                    public String chat(String question, Map<String, Object> context) {
                        Map<String, Object> mergedContext = context != null ? new HashMap<>(context) : new HashMap<>();
                        Object payload = mergedContext.get("contextPayload");
                        if (payload instanceof String payloadStr && !payloadStr.isBlank()
                                && !mergedContext.containsKey("previousResults")) {
                            mergedContext.put("previousResults", Map.of("upstream_blackboard", payloadStr));
                        }
                        return w.chat(question, mergedContext);
                    }
                };
                this.contractNetDispatcher.registerWorker(adapter);
                if (firstWorker == null) {
                    firstWorker = adapter;
                }
            }
        }
        if (!this.customContractNetProvided && firstWorker != null) {
            this.contractNetDispatcher.setDefaultFallbackWorker(firstWorker);
        }
    }

    @SuppressWarnings("unchecked")
    public List<WorkerAgent> getWorkers() {
        List<WorkerAgent> list = new ArrayList<>();
        for (BaseAgent w : workers) {
            if (w instanceof WorkerAgent wa) {
                list.add(wa);
            }
        }
        return list;
    }

    @Override
    public String chat(String question, Map<String, Object> context) {
        String sessionId = (context != null && context.get("sessionId") != null)
                ? String.valueOf(context.get("sessionId"))
                : UUID.randomUUID().toString();

        // 请求级黑板隔离：若未在构造器显式绑定共享黑板，每次请求创建独立黑板实例，杜绝跨请求事实污染
        SharedBlackboard activeBlackboard;
        if (context != null && context.get("blackboard") instanceof SharedBlackboard ctxBlackboard) {
            activeBlackboard = ctxBlackboard;
        } else if (this.sharedBlackboardProvided) {
            activeBlackboard = this.blackboard;
        } else {
            activeBlackboard = new SharedBlackboard();
        }

        try {
            log.info("[SupervisorAgent] 接收到协同问题，会话 ID: {}", sessionId);

            // 1. 用 LLM 进行分层任务意图分解与 Kahn 算法拓扑分层
            PhasedExecutionPlan plan = planner.plan(question);

            String initialAnswer;
            if (plan == null || plan.isEmpty()) {
                log.warn("[SupervisorAgent] 分解计划为空，直接使用 ChatModel 单步回答");
                initialAnswer = chatModel != null ? chatModel.call(new Prompt(List.of(
                        new SystemMessage(systemPrompt != null ? systemPrompt : ""),
                        new UserMessage(question)
                ))).getResult().getOutput().getText() : "处理计划为空且无可用对话模型";
            } else {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("SupervisorAgent interrupted before phase dispatch");
                }
                // 2. 拓扑分层推进调度并写入请求级隔离黑板
                dispatcher.dispatch(sessionId, plan, activeBlackboard);

                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("SupervisorAgent interrupted after phase dispatch");
                }

                // 3. 从黑板汇总全阶段事实产出
                Map<String, BlackboardEntry> factsSnapshot = activeBlackboard.getFactSnapshot();
                Map<String, String> results = new LinkedHashMap<>();
                factsSnapshot.forEach((key, entry) -> results.put(key, entry.value()));
                sessionBlackboardSnapshots.put(sessionId, Collections.unmodifiableMap(new LinkedHashMap<>(results)));

                // 4. 用 LLM 初步聚合所有 Worker 的黑板结果
                initialAnswer = aggregateResults(question, results);
            }

            String finalAnswer = initialAnswer;
            if (evaluatorOptimizerGovernor != null) {
                // 5. 接入 Evaluator-Optimizer 双环自优化闭环 (内环规则清洗 + 外环收敛自愈)
                EvaluatorOptimizerGovernor.OptimizationExecutionResult loopResult =
                        evaluatorOptimizerGovernor.executeOptimizationLoop(sessionId, question, initialAnswer);
                sessionQualityReceipts.put(sessionId, loopResult.receipt());
                finalAnswer = loopResult.finalOutput();
            }

            // 6. 蜂群输出语义指纹检查
            loopGuard.inspectOutput(sessionId, finalAnswer);

            return finalAnswer;
        } catch (Exception e) {
            log.error("[SupervisorAgent] 会话 {} 执行失败", sessionId, e);
            return "执行失败: " + getName() + " - " + e.getMessage();
        } finally {
            loopGuard.cleanSession(sessionId);
        }
    }

    /**
     * 初步聚合所有 Worker 的黑板产出
     */
    private String aggregateResults(String originalQuestion, Map<String, String> workerResults) {
        if (chatModel == null) {
            StringBuilder sb = new StringBuilder("## 汇总执行结果\n");
            workerResults.forEach((k, v) -> sb.append("- **").append(k).append("**: ").append(v).append("\n"));
            return sb.toString();
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : workerResults.entrySet()) {
            contextBuilder.append("## 任务 [").append(entry.getKey()).append("] 产出结果\n");
            contextBuilder.append(entry.getValue()).append("\n\n");
        }

        String aggregatePrompt = String.format("""
                请根据以下各子任务的执行成果，综合回答用户的问题。
                
                用户问题：%s
                
                %s
                请给出结构化、严谨且准确的综合答复。
                """, originalQuestion, contextBuilder);

        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(aggregatePrompt));

        ChatResponse response = chatModel.call(new Prompt(messages));
        return (response != null && response.getResult() != null && response.getResult().getOutput() != null)
                ? response.getResult().getOutput().getText()
                : "聚合结果为空";
    }
}
