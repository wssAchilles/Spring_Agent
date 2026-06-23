package tech.qiantong.qknow.hermes.agent;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 主管智能体
 * 负责分解任务、分发到 Worker、聚合结果
 */
@Slf4j
public class SupervisorAgent extends BaseAgent {

    private final String systemPrompt;
    private final List<ToolCallback> tools;
    private final ChatModel chatModel;
    private final List<WorkerAgent> workers;

    public SupervisorAgent(String name, String description, String systemPrompt,
                           List<ToolCallback> tools, ChatModel chatModel, List<WorkerAgent> workers) {
        super(name, description);
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.chatModel = chatModel;
        this.workers = workers != null ? workers : List.of();
    }

    public List<WorkerAgent> getWorkers() {
        return workers;
    }

    @Override
    public String chat(String question, Map<String, Object> context) {
        try {
            // 1. 用 LLM 分解任务
            List<Subtask> subtasks = decomposeTask(question);

            // 2. 并行分发到对应 worker
            Map<String, String> workerResults = dispatchToWorkers(subtasks);

            // 3. 用 LLM 聚合结果
            return aggregateResults(question, workerResults);
        } catch (Exception e) {
            log.error("SupervisorAgent {} 执行失败", getName(), e);
            return "执行失败: " + getName() + " - " + e.getMessage();
        }
    }

    /**
     * 用 LLM 将用户问题分解为子任务列表
     */
    private List<Subtask> decomposeTask(String question) {
        String workerNames = workers.stream()
                .map(BaseAgent::getName)
                .collect(Collectors.joining(", "));

        String decomposePrompt = String.format("""
                你是一个任务分解专家。请将用户的任务分解为子任务，分配给以下 Worker：
                [%s]

                请以 JSON 数组格式返回，每个元素包含 worker 和 subtask 字段：
                [{"worker":"WorkerName","subtask":"子任务描述"}]

                只返回 JSON，不要其他内容。
                用户任务：%s
                """, workerNames, question);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(decomposePrompt));

        ChatResponse response = chatModel.call(new Prompt(messages));
        String responseText = response.getResult().getOutput().getText();

        return parseSubtasks(responseText);
    }

    /**
     * 解析 LLM 返回的子任务 JSON
     */
    private List<Subtask> parseSubtasks(String responseText) {
        List<Subtask> subtasks = new ArrayList<>();
        try {
            String jsonStr = responseText;
            int start = responseText.indexOf("[");
            int end = responseText.lastIndexOf("]");
            if (start >= 0 && end > start) {
                jsonStr = responseText.substring(start, end + 1);
            }

            JSONArray arr = JSONArray.parseArray(jsonStr);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                subtasks.add(new Subtask(obj.getString("worker"), obj.getString("subtask")));
            }
        } catch (Exception e) {
            log.warn("解析子任务 JSON 失败，原始响应: {}", responseText, e);
        }
        return subtasks;
    }

    /**
     * 并行分发子任务到对应 Worker
     */
    private Map<String, String> dispatchToWorkers(List<Subtask> subtasks) {
        Map<String, String> results = new ConcurrentHashMap<>();
        Map<String, WorkerAgent> workerMap = workers.stream()
                .collect(Collectors.toMap(BaseAgent::getName, w -> w));

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Subtask subtask : subtasks) {
            WorkerAgent worker = workerMap.get(subtask.worker);
            if (worker == null) {
                log.warn("Worker {} 未找到，跳过子任务: {}", subtask.worker, subtask.subtask);
                continue;
            }

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String result = worker.chat(subtask.subtask, null);
                    results.put(subtask.worker, result);
                } catch (Exception e) {
                    log.error("Worker {} 执行子任务失败", subtask.worker, e);
                    results.put(subtask.worker, "执行失败: " + e.getMessage());
                }
            });
            futures.add(future);
        }

        // 等待所有 worker 完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return results;
    }

    /**
     * 用 LLM 聚合所有 Worker 的结果
     */
    private String aggregateResults(String originalQuestion, Map<String, String> workerResults) {
        StringBuilder contextBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : workerResults.entrySet()) {
            contextBuilder.append("## ").append(entry.getKey()).append(" 的结果\n");
            contextBuilder.append(entry.getValue()).append("\n\n");
        }

        String aggregatePrompt = String.format("""
                请根据以下各 Worker 的结果，综合回答用户的问题。

                用户问题：%s

                %s
                请给出综合、准确的回答。
                """, originalQuestion, contextBuilder);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(aggregatePrompt));

        ChatResponse response = chatModel.call(new Prompt(messages));
        return response.getResult().getOutput().getText();
    }

    /**
     * 子任务描述
     */
    private static class Subtask {
        final String worker;
        final String subtask;

        Subtask(String worker, String subtask) {
            this.worker = worker;
            this.subtask = subtask;
        }
    }
}
