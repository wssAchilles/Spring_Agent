package tech.qiantong.qknow.hermes.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能体注册中心
 * 管理所有 Agent 的注册、查找和生命周期
 */
@Slf4j
public class AgentRegistry {

    private final Map<String, BaseAgent> agents = new ConcurrentHashMap<>();

    /**
     * 构造函数 - 使用 ToolCallbackResolver
     */
    public AgentRegistry(Object config, ToolCallbackResolver resolver) {
        initDefaultAgents(resolver);
    }

    /**
     * 构造函数 - 使用 ChatModel
     */
    public AgentRegistry(Object config, ChatModel chatModel) {
        initDefaultAgents(chatModel);
    }

    /**
     * 初始化默认 Worker 智能体
     */
    private void initDefaultAgents(Object modelOrResolver) {
        ToolCallbackResolver resolver = null;
        ChatModel chatModel = null;

        if (modelOrResolver instanceof ToolCallbackResolver r) {
            resolver = r;
        } else if (modelOrResolver instanceof ChatModel cm) {
            chatModel = cm;
        }

        WorkerAgent ragAgent = new WorkerAgent(
                "RAGAgent", "知识库检索智能体", "你是一个 RAG 检索智能体",
                List.of(), chatModel, resolver);
        WorkerAgent searchAgent = new WorkerAgent(
                "SearchAgent", "搜索智能体", "你是一个搜索智能体",
                List.of(), chatModel, resolver);
        WorkerAgent codeAgent = new WorkerAgent(
                "CodeAgent", "代码智能体", "你是一个代码智能体",
                List.of(), chatModel, resolver);

        agents.put(ragAgent.getName(), ragAgent);
        agents.put(searchAgent.getName(), searchAgent);
        agents.put(codeAgent.getName(), codeAgent);
    }

    /**
     * 注册智能体（覆盖同名智能体）
     */
    public void registerAgent(BaseAgent agent) {
        agents.put(agent.getName(), agent);
    }

    /**
     * 获取指定名称的智能体
     */
    public Optional<BaseAgent> getAgent(String name) {
        return Optional.ofNullable(agents.get(name));
    }

    /**
     * 注销智能体
     *
     * @return 被移除的智能体，不存在时返回 null
     */
    public BaseAgent unregisterAgent(String name) {
        return agents.remove(name);
    }

    /**
     * 获取所有智能体
     */
    public List<BaseAgent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    /**
     * 获取所有 Worker 智能体
     */
    public List<WorkerAgent> getWorkers() {
        return agents.values().stream()
                .filter(a -> a instanceof WorkerAgent)
                .map(a -> (WorkerAgent) a)
                .collect(Collectors.toList());
    }

    /**
     * 创建主管智能体（包含所有已注册的 Worker），并注册到中心
     */
    public SupervisorAgent createSupervisor(String name, String systemPrompt, ChatModel chatModel) {
        SupervisorAgent supervisor = new SupervisorAgent(
                name, "主管智能体", systemPrompt,
                List.of(), chatModel, getWorkers());
        registerAgent(supervisor);
        return supervisor;
    }
}
