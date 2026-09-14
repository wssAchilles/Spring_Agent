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
import tech.qiantong.qknow.hermes.agent.guard.SwarmLoopGuard;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工业级蜂群主管智能体 (SupervisorAgent)
 * 负责分层任务意图分解 (HTN/DAG)、拓扑阶段调度推进、动态合同网竞标分派、共享黑板协作与最终答复收敛
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
    private final SwarmLoopGuard loopGuard;

    /**
     * 完整依赖注入构造函数
     */
    public SupervisorAgent(String name, String description, String systemPrompt,
                           List<ToolCallback> tools, ChatModel chatModel, List<? extends BaseAgent> workers,
                           HierarchicalTaskPlanner planner,
                           TopologicalPhasedDispatcher dispatcher,
                           ContractNetDispatcher contractNetDispatcher,
                           SharedBlackboard blackboard,
                           SwarmLoopGuard loopGuard) {
        super(name, description);
        this.systemPrompt = systemPrompt;
        this.tools = tools != null ? List.copyOf(tools) : List.of();
        this.chatModel = chatModel;
        this.workers = workers != null ? new ArrayList<>(workers) : new ArrayList<>();
        this.planner = planner != null ? planner : new HierarchicalTaskPlanner(chatModel);
        this.loopGuard = loopGuard != null ? loopGuard : new SwarmLoopGuard();
        this.blackboard = blackboard != null ? blackboard : new SharedBlackboard();
        this.contractNetDispatcher = contractNetDispatcher != null ? contractNetDispatcher : new ContractNetDispatcher();
        this.dispatcher = dispatcher != null ? dispatcher : new TopologicalPhasedDispatcher(this.contractNetDispatcher, this.loopGuard);

        initWorkersInContractNet();
    }

    /**
     * 向后兼容传统构造函数
     */
    public SupervisorAgent(String name, String description, String systemPrompt,
                           List<ToolCallback> tools, ChatModel chatModel, List<WorkerAgent> workers) {
        this(name, description, systemPrompt, tools, chatModel, workers, null, null, null, null, null);
    }

    private void initWorkersInContractNet() {
        for (BaseAgent w : this.workers) {
            if (w instanceof EnhancedBaseAgent eb) {
                this.contractNetDispatcher.registerWorker(eb);
            } else if (w != null) {
                // 将传统 Worker 包装为 EnhancedBaseAgent 适配器
                EnhancedBaseAgent adapter = new EnhancedBaseAgent(w.getName(), w.getDescription(), Set.of("GENERAL", w.getName().toUpperCase()), 4) {
                    @Override
                    public String chat(String question, Map<String, Object> context) {
                        return w.chat(question, context);
                    }
                };
                this.contractNetDispatcher.registerWorker(adapter);
            }
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

        try {
            log.info("[SupervisorAgent] 接收到协同问题，会话 ID: {}", sessionId);

            // 1. 用 LLM 进行分层任务意图分解与 Kahn 算法拓扑分层
            PhasedExecutionPlan plan = planner.plan(question);

            if (plan == null || plan.isEmpty()) {
                log.warn("[SupervisorAgent] 分解计划为空，直接使用 ChatModel 单步回答");
                return chatModel.call(new Prompt(List.of(
                        new SystemMessage(systemPrompt != null ? systemPrompt : ""),
                        new UserMessage(question)
                ))).getResult().getOutput().getText();
            }

            // 2. 拓扑分层推进调度并写入黑板
            dispatcher.dispatch(sessionId, plan, blackboard);

            // 3. 从黑板汇总全阶段事实产出
            Map<String, BlackboardEntry> factsSnapshot = blackboard.getFactSnapshot();
            Map<String, String> results = new LinkedHashMap<>();
            factsSnapshot.forEach((key, entry) -> results.put(key, entry.value()));

            // 4. 用 LLM 聚合所有 Worker 的黑板结果
            String finalAnswer = aggregateResults(question, results);

            // 5. 蜂群输出语义指纹检查
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
     * 用 LLM 聚合所有 Worker 的黑板产出
     */
    private String aggregateResults(String originalQuestion, Map<String, String> workerResults) {
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
