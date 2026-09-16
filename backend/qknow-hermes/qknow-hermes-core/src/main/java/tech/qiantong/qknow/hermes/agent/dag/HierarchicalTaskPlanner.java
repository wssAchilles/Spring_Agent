package tech.qiantong.qknow.hermes.agent.dag;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工业级分层任务意图分解器
 */
@Slf4j
@Component
public class HierarchicalTaskPlanner {

    private final ChatModel chatModel;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public HierarchicalTaskPlanner(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public HierarchicalTaskPlanner() {
        this(null);
    }

    private static final String PLANNER_SYSTEM_PROMPT = """
            你是一个工业级多智能体系统的主管任务规划专家。
            你的职责是将用户的复合任务分解为具有显式依赖关系的有向无环图（DAG）子任务集合。
            
            输出规范：必须且仅输出严格的 JSON 数组，严禁包含任何前缀、Markdown 标记或额外解释。
            数组每个元素结构如下：
            {
              "taskId": "task-1",
              "objective": "简明子任务目标，不超过50字",
              "requiredCapability": "所需能力标识（如 KNOWLEDGE_RETRIEVAL, WEB_SEARCH, DATA_ANALYSIS, CODE_SYNTHESIS, REPORT_SYNTHESIS）",
              "dependencies": ["前置依赖的 taskId 列表，若无则为空数组 []"],
              "timeoutSeconds": 30
            }
            
            规则：
            1. 依赖关系必须合法，严禁形成循环依赖！
            2. 能够并发执行的任务不要声明依赖，以提升系统并行度。
            3. 子任务总数控制在 2 到 6 个之间。
            """;

    public PhasedExecutionPlan plan(String userQuery) {
        if (chatModel == null) {
            log.warn("[HierarchicalPlanner] ChatModel 未注入，使用默认单节点执行计划");
            return new PhasedExecutionPlan(List.of(List.of(new DagTaskNode("task-1", userQuery, "GENERAL", List.of(), 30, Map.of()))), 1);
        }
        String prompt = "用户目标任务：" + userQuery;
        Prompt chatPrompt = new Prompt(List.of(
                new SystemMessage(PLANNER_SYSTEM_PROMPT),
                new UserMessage(prompt)
        ));

        ChatResponse response = chatModel.call(chatPrompt);
        String text = (response != null && response.getResult() != null && response.getResult().getOutput() != null)
                ? response.getResult().getOutput().getText() : "[]";
        List<DagTaskNode> taskNodes = parseAndValidateJson(text);

        if (taskNodes.isEmpty()) {
            log.warn("[HierarchicalPlanner] 任务分解为空或解析失败，用户 Query: {}", userQuery);
            return new PhasedExecutionPlan(List.of(), 0);
        }

        // 使用 Kahn 算法进行拓扑分层并检测环路
        return buildPhasedPlan(taskNodes);
    }

    private List<DagTaskNode> parseAndValidateJson(String rawResponse) {
        List<DagTaskNode> nodes = new ArrayList<>();
        try {
            String jsonStr = rawResponse.trim();
            int start = jsonStr.indexOf('[');
            int end = jsonStr.lastIndexOf(']');
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }
            JSONArray array = JSONArray.parseArray(jsonStr);
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String taskId = obj.getString("taskId");
                String objective = obj.getString("objective");
                String capability = obj.getString("requiredCapability");
                JSONArray depArr = obj.getJSONArray("dependencies");
                List<String> deps = depArr != null ? depArr.toJavaList(String.class) : List.of();
                int timeout = obj.getIntValue("timeoutSeconds", 30);

                if (taskId != null && objective != null) {
                    nodes.add(new DagTaskNode(taskId, objective, capability != null ? capability : "GENERAL", deps, timeout, Map.of()));
                }
            }
        } catch (Exception e) {
            log.error("[HierarchicalPlanner] 解析任务 JSON 失败，原始响应: {}", rawResponse, e);
        }
        return nodes;
    }

    /**
     * 基于 Kahn 算法构建分层执行计划（按入度为 0 逐层抽取）
     */
    private PhasedExecutionPlan buildPhasedPlan(List<DagTaskNode> nodes) {
        Map<String, DagTaskNode> nodeMap = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();

        for (DagTaskNode node : nodes) {
            nodeMap.put(node.taskId(), node);
            inDegree.put(node.taskId(), node.dependencies().size());
            adjacency.put(node.taskId(), new ArrayList<>());
        }

        for (DagTaskNode node : nodes) {
            for (String dep : node.dependencies()) {
                if (adjacency.containsKey(dep)) {
                    adjacency.get(dep).add(node.taskId());
                } else {
                    log.warn("[HierarchicalPlanner] 节点 {} 依赖不存在的父任务: {}", node.taskId(), dep);
                    inDegree.put(node.taskId(), Math.max(0, inDegree.get(node.taskId()) - 1));
                }
            }
        }

        List<List<DagTaskNode>> phases = new ArrayList<>();
        Queue<String> readyQueue = new LinkedList<>();

        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                readyQueue.add(entry.getKey());
            }
        }

        int processedCount = 0;

        while (!readyQueue.isEmpty()) {
            int currentPhaseSize = readyQueue.size();
            List<DagTaskNode> currentPhaseNodes = new ArrayList<>();

            List<String> currentBatchIds = new ArrayList<>();
            for (int i = 0; i < currentPhaseSize; i++) {
                String taskId = readyQueue.poll();
                currentBatchIds.add(taskId);
                currentPhaseNodes.add(nodeMap.get(taskId));
                processedCount++;
            }

            phases.add(currentPhaseNodes);

            // 扣减下游节点的入度
            for (String taskId : currentBatchIds) {
                for (String neighbor : adjacency.get(taskId)) {
                    int updatedDegree = inDegree.get(neighbor) - 1;
                    inDegree.put(neighbor, updatedDegree);
                    if (updatedDegree == 0) {
                        readyQueue.add(neighbor);
                    }
                }
            }
        }

        if (processedCount != nodes.size()) {
            log.error("[HierarchicalPlanner] 检测到任务 DAG 存在环路！处理节点数 {} != 总节点数 {}", processedCount, nodes.size());
            throw new IllegalStateException("DAG contains cyclic dependencies, cannot construct phased execution plan");
        }

        return new PhasedExecutionPlan(phases, nodes.size());
    }
}
