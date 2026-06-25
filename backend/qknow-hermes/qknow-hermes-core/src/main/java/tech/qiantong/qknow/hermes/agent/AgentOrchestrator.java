package tech.qiantong.qknow.hermes.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
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
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.config.PlanSolveConfig;
import tech.qiantong.qknow.hermes.judge.AiJudgeService;
import tech.qiantong.qknow.hermes.judge.JudgeResult;
import tech.qiantong.qknow.hermes.proto.*;
import tech.qiantong.qknow.hermes.tool.function.SearchKnowledgeTool;
import tech.qiantong.qknow.hermes.tool.function.query.knowledgeQuery;
import tech.qiantong.qknow.hermes.util.NodeUtils;
import cn.hutool.core.util.StrUtil;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Hermes Agent 编排器
 * 替代原 KbAgentConfigServiceImpl.chatMessage() 中的 ReactAgent 构建和执行逻辑
 */
@Slf4j
@Component
public class AgentOrchestrator {

    private final ChatModelFactory chatModelFactory;
    private final ToolCallbackResolver resolver;
    private final RetrievalEvaluator retrievalEvaluator;
    private final PlanSolveConfig planSolveConfig;
    private final AiJudgeService aiJudgeService;

    @Autowired
    public AgentOrchestrator(ChatModelFactory chatModelFactory, ToolCallbackResolver resolver,
                             RetrievalEvaluator retrievalEvaluator, PlanSolveConfig planSolveConfig,
                             AiJudgeService aiJudgeService) {
        this.chatModelFactory = chatModelFactory;
        this.resolver = resolver;
        this.retrievalEvaluator = retrievalEvaluator;
        this.planSolveConfig = planSolveConfig;
        this.aiJudgeService = aiJudgeService;
    }

    public AgentOrchestrator(ChatModelFactory chatModelFactory, ToolCallbackResolver resolver,
                             RetrievalEvaluator retrievalEvaluator) {
        this(chatModelFactory, resolver, retrievalEvaluator, new PlanSolveConfig(), null);
    }

    /**
     * 执行 Agent 对话（核心方法）
     */
    public Flux<ChatEvent> chat(ChatRequest request) {
        return Flux.create(emitter -> {
            try {
                executeAgent(request, emitter);
            } catch (Exception e) {
                log.error("Hermes Agent 执行失败", e);
                emitter.next(ChatEvent.newBuilder()
                        .setRequestId(request.getRequestId())
                        .setError(ErrorEvent.newBuilder()
                                .setCode(500)
                                .setMessage(e.getMessage())
                                .build())
                        .build());
                emitter.complete();
            }
        });
    }

    private void executeAgent(ChatRequest request, reactor.core.publisher.FluxSink<ChatEvent> emitter) throws GraphRunnerException {
        // 1. 创建 ChatModel
        ModelConfig modelConfig = request.getModelConfig();
        ChatModel chatModel;
        if (modelConfig != null && !modelConfig.getModelName().isEmpty()) {
            chatModel = chatModelFactory.getChatModel(
                    modelConfig.getPlatform(),
                    modelConfig.getBaseUrl(),
                    modelConfig.getApiKey(),
                    modelConfig.getModelName()
            );
        } else if (request.hasModelCredentials()) {
            ModelCredentials creds = request.getModelCredentials();
            chatModel = chatModelFactory.getChatModel(
                    creds.getPlatform(),
                    creds.getBaseUrl(),
                    creds.getApiKey(),
                    "default"
            );
        } else {
            throw new IllegalArgumentException("缺少模型配置");
        }

        // 2. CRAG 检索评估
        RetrievalEvaluation retrievalEvaluation = retrievalEvaluator.evaluate(
                request.getQuestion(), request.getRagContextsList(), modelConfig,
                request.hasModelCredentials() ? request.getModelCredentials() : null);
        List<RAGContext> effectiveRagContexts = retrievalEvaluation.isIncorrect()
                ? List.of()
                : request.getRagContextsList();

        // 3. 构建知识库工具（使用预检索的 RAG 结果）
        List<ToolCallback> tools = new ArrayList<>();
        for (RAGContext ragCtx : effectiveRagContexts) {
            FunctionToolCallback<knowledgeQuery, String> toolCallback = FunctionToolCallback
                    .builder("knowledgeBase" + ragCtx.getKnowledgeId(),
                            new SearchKnowledgeTool(ragCtx.getKnowledgeId(),
                                    ragCtx.getKnowledgeName(),
                                    ragCtx.getPreRetrievedContent()))
                    .inputType(knowledgeQuery.class)
                    .description("当需要查询" + ragCtx.getKnowledgeName() + "相关的信息时调用")
                    .build();
            tools.add(toolCallback);
        }

        // 4. 获取工具名称列表，并确保知识库工具可被调用
        List<String> enabledToolNames = new ArrayList<>(request.getToolMethodIdsList());
        for (RAGContext ragCtx : effectiveRagContexts) {
            enabledToolNames.add("knowledgeBase" + ragCtx.getKnowledgeId());
        }
        String[] toolNames = enabledToolNames.toArray(new String[0]);

        // 5. 构建消息历史
        List<Message> messages = new ArrayList<>();
        for (ChatMessage historyMsg : request.getHistoryList()) {
            if ("user".equals(historyMsg.getRole())) {
                messages.add(new UserMessage(historyMsg.getContent()));
            } else if ("assistant".equals(historyMsg.getRole())) {
                messages.add(new AssistantMessage(historyMsg.getContent()));
            }
        }

        // 6. 构建系统提示词
        String systemPrompt = NodeUtils.replacePlaceholder(request.getSystemPrompt(), request.getInputParams());
        systemPrompt = appendRagContext(systemPrompt, effectiveRagContexts, retrievalEvaluation);
        messages.add(new UserMessage(request.getQuestion()));

        String planAnswer = planAndSolve(request.getQuestion(), systemPrompt, tools, chatModel);
        if (planAnswer != null) {
            emitSingleAnswer(request, emitter, planAnswer);
            return;
        }

        // 7. 构建 ReactAgent
        ReactAgent agent = ReactAgent.builder()
                .name("hermes_agent")
                .model(chatModel)
                .hooks(ModelCallLimitHook.builder().runLimit(10).build())
                .systemPrompt(systemPrompt)
                .toolNames(toolNames)
                .tools(tools)
                .resolver(resolver)
                .build();

        // 8. 执行推理并映射为 ChatEvent 流
        Flux<NodeOutput> stream = agent.stream(messages);
        stream.subscribe(
                output -> {
                    try {
                        if (output instanceof StreamingOutput streamingOutput) {
                            OutputType type = streamingOutput.getOutputType();

                            if (type == OutputType.AGENT_MODEL_STREAMING) {
                                String text = streamingOutput.message().getText();
                                emitter.next(ChatEvent.newBuilder()
                                        .setRequestId(request.getRequestId())
                                        .setChunk(StreamingChunk.newBuilder()
                                                .setText(text != null ? text : "")
                                                .setUserQuestion(request.getQuestion())
                                                .build())
                                        .build());
                            } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                String text = streamingOutput.message().getText();
                                emitter.next(ChatEvent.newBuilder()
                                        .setRequestId(request.getRequestId())
                                        .setFinished(ModelFinished.newBuilder()
                                                .setFullText(text != null ? text : "")
                                                .build())
                                        .build());
                            } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                emitter.next(ChatEvent.newBuilder()
                                        .setRequestId(request.getRequestId())
                                        .setToolInvoked(ToolInvoked.newBuilder()
                                                .setToolName(output.node())
                                                .setStatus("success")
                                                .build())
                                        .build());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("处理流式输出异常", e);
                    }
                },
                error -> {
                    log.error("Agent 推理错误", error);
                    emitter.next(ChatEvent.newBuilder()
                            .setRequestId(request.getRequestId())
                            .setError(ErrorEvent.newBuilder()
                                    .setCode(500)
                                    .setMessage(error.getMessage())
                                    .build())
                            .build());
                    emitter.complete();
                },
                () -> {
                    emitter.next(ChatEvent.newBuilder()
                            .setRequestId(request.getRequestId())
                            .setDone(DoneSignal.newBuilder().build())
                            .build());
                    emitter.complete();
                }
        );
    }

    private String planAndSolve(String question, String systemPrompt, List<ToolCallback> tools, ChatModel chatModel) {
        if (planSolveConfig == null || !planSolveConfig.isEnabled() || !isComplexQuestion(question)) {
            return null;
        }
        try {
            List<PlanTask> tasks = createPlan(question, systemPrompt, chatModel);
            if (tasks.isEmpty()) {
                return null;
            }
            Map<String, String> workerResults = executePlanTasks(tasks, systemPrompt, tools, chatModel);
            if (workerResults.isEmpty()) {
                return null;
            }
            String answer = aggregatePlanResults(question, systemPrompt, workerResults, chatModel);
            return reflectOnce(question, systemPrompt, answer, workerResults, chatModel);
        } catch (Exception e) {
            log.warn("Plan-and-Solve failed, falling back to ReAct", e);
            return null;
        }
    }

    private boolean isComplexQuestion(String question) {
        if (question == null) {
            return false;
        }
        if (question.length() > 100) {
            return true;
        }
        List<String> keywords = planSolveConfig.getComplexKeywords() != null
                ? planSolveConfig.getComplexKeywords()
                : List.of();
        for (String keyword : keywords) {
            if (StrUtil.isNotBlank(keyword) && question.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<PlanTask> createPlan(String question, String systemPrompt, ChatModel chatModel) {
        String prompt = """
                请将用户复杂任务拆解为最多 %d 个可执行子任务。
                只返回 JSON 数组，不要 Markdown 或解释。
                字段: taskId, objective, worker, dependencies。
                worker 可使用 rag_worker。
                dependencies 是前置 taskId 数组。

                用户任务：%s
                """.formatted(planSolveConfig.getMaxTasks(), question);
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt != null ? systemPrompt : ""),
                new UserMessage(prompt)
        )));
        String text = response.getResult().getOutput().getText();
        JSONArray array = extractJsonArray(text);
        List<PlanTask> tasks = new ArrayList<>();
        for (int i = 0; i < array.size() && i < planSolveConfig.getMaxTasks(); i++) {
            JSONObject object = array.getJSONObject(i);
            String taskId = defaultString(object.getString("taskId"), "task-" + (i + 1));
            String objective = defaultString(object.getString("objective"), object.getString("subtask"));
            if (StrUtil.isBlank(objective)) {
                continue;
            }
            List<String> dependencies = parseDependencies(object.getJSONArray("dependencies"));
            tasks.add(new PlanTask(taskId, objective, defaultString(object.getString("worker"), "rag_worker"), dependencies));
        }
        if (hasDependencyCycle(tasks)) {
            return List.of();
        }
        return tasks;
    }

    private Map<String, String> executePlanTasks(List<PlanTask> tasks, String systemPrompt,
                                                 List<ToolCallback> tools, ChatModel chatModel) {
        Map<String, String> results = new LinkedHashMap<>();
        List<PlanTask> remaining = new ArrayList<>(tasks);
        while (!remaining.isEmpty()) {
            List<PlanTask> ready = remaining.stream()
                    .filter(task -> results.keySet().containsAll(task.dependencies()))
                    .toList();
            if (ready.isEmpty()) {
                throw new IllegalStateException("Plan contains unresolved dependencies");
            }
            List<CompletableFuture<Map.Entry<String, String>>> futures = ready.stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> {
                        WorkerAgent worker = new WorkerAgent(task.worker(), "Plan task worker", systemPrompt, tools, chatModel, resolver);
                        String result = worker.chat(task.objective(), Map.of("previousResults", new LinkedHashMap<>(results)));
                        return Map.entry(task.taskId(), result);
                    }))
                    .toList();
            for (CompletableFuture<Map.Entry<String, String>> future : futures) {
                Map.Entry<String, String> entry = future.join();
                results.put(entry.getKey(), entry.getValue());
            }
            remaining.removeAll(ready);
        }
        return results;
    }

    private String aggregatePlanResults(String question, String systemPrompt, Map<String, String> workerResults, ChatModel chatModel) {
        String context = workerResults.entrySet().stream()
                .map(entry -> "## " + entry.getKey() + "\n" + entry.getValue())
                .collect(Collectors.joining("\n\n"));
        String prompt = """
                请基于以下子任务结果回答原始问题。

                原始问题：%s

                子任务结果：
                %s
                """.formatted(question, context);
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt != null ? systemPrompt : ""),
                new UserMessage(prompt)
        )));
        return response.getResult().getOutput().getText();
    }

    private String reflectOnce(String question, String systemPrompt, String answer,
                               Map<String, String> workerResults, ChatModel chatModel) {
        if (aiJudgeService == null) {
            return answer;
        }
        String context = formatWorkerResults(workerResults);
        String currentAnswer = answer;
        int maxRetries = Math.max(0, planSolveConfig.getMaxReflectionRetries());
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            JudgeResult result = aiJudgeService.judge(question, context, currentAnswer);
            if (result.isPassed() || attempt == maxRetries) {
                return currentAnswer;
            }
            String prompt = """
                    上一版回答未通过质量检查，请根据反馈重写一次。

                    原始问题：%s
                    子任务结果：
                    %s
                    反馈：%s
                    上一版回答：%s
                    """.formatted(question, context, result.getFeedback(), currentAnswer);
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt != null ? systemPrompt : ""),
                    new UserMessage(prompt)
            )));
            currentAnswer = response.getResult().getOutput().getText();
        }
        return currentAnswer;
    }

    private List<String> parseDependencies(JSONArray dependenciesJson) {
        if (dependenciesJson == null || dependenciesJson.isEmpty()) {
            return List.of();
        }
        List<String> dependencies = new ArrayList<>();
        for (Object value : dependenciesJson) {
            String dependency = value instanceof String text ? text : String.valueOf(value);
            if (StrUtil.isNotBlank(dependency)) {
                dependencies.add(dependency);
            }
        }
        return dependencies;
    }

    private String formatWorkerResults(Map<String, String> workerResults) {
        if (workerResults == null || workerResults.isEmpty()) {
            return "";
        }
        return workerResults.entrySet().stream()
                .map(entry -> "## " + entry.getKey() + "\n" + entry.getValue())
                .collect(Collectors.joining("\n\n"));
    }

    private JSONArray extractJsonArray(String text) {
        if (text == null) {
            return new JSONArray();
        }
        int start = text.indexOf("[");
        int end = text.lastIndexOf("]");
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return JSONArray.parseArray(text);
    }

    private boolean hasDependencyCycle(List<PlanTask> tasks) {
        Map<String, PlanTask> taskMap = tasks.stream().collect(Collectors.toMap(PlanTask::taskId, task -> task, (a, b) -> a));
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (PlanTask task : tasks) {
            if (visitCycle(task, taskMap, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean visitCycle(PlanTask task, Map<String, PlanTask> taskMap, Set<String> visiting, Set<String> visited) {
        if (visited.contains(task.taskId())) {
            return false;
        }
        if (!visiting.add(task.taskId())) {
            return true;
        }
        for (String dependency : task.dependencies()) {
            PlanTask dependencyTask = taskMap.get(dependency);
            if (dependencyTask != null && visitCycle(dependencyTask, taskMap, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(task.taskId());
        visited.add(task.taskId());
        return false;
    }

    private void emitSingleAnswer(ChatRequest request, reactor.core.publisher.FluxSink<ChatEvent> emitter, String answer) {
        String text = answer != null ? answer : "";
        emitter.next(ChatEvent.newBuilder()
                .setRequestId(request.getRequestId())
                .setChunk(StreamingChunk.newBuilder()
                        .setText(text)
                        .setUserQuestion(request.getQuestion())
                        .build())
                .build());
        emitter.next(ChatEvent.newBuilder()
                .setRequestId(request.getRequestId())
                .setFinished(ModelFinished.newBuilder().setFullText(text).build())
                .build());
        emitter.next(ChatEvent.newBuilder()
                .setRequestId(request.getRequestId())
                .setDone(DoneSignal.newBuilder().build())
                .build());
        emitter.complete();
    }

    private String defaultString(String value, String defaultValue) {
        return StrUtil.isBlank(value) ? defaultValue : value;
    }

    private String appendRagContext(String systemPrompt, List<RAGContext> ragContexts,
                                    RetrievalEvaluation retrievalEvaluation) {
        StringBuilder builder = new StringBuilder(systemPrompt != null ? systemPrompt : "");
        if (retrievalEvaluation != null && retrievalEvaluation.isIncorrect()) {
            builder.append("\n\n<retrieval_evaluation label=\"INCORRECT\" confidence=\"")
                    .append(retrievalEvaluation.getConfidence())
                    .append("\">")
                    .append(escapeXml(retrievalEvaluation.getReason()))
                    .append("</retrieval_evaluation>\n")
                    .append("知识库召回结果未通过可靠性评估。回答时不要引用召回内容；如果缺少依据，请说明无法从知识库确认。");
            String rewrittenQuery = retrievalEvaluation.getRewrittenQuery();
            if (StrUtil.isNotBlank(rewrittenQuery)) {
                builder.append("\n\n<retrieval_rewrite>\n")
                        .append("原始问题可能不够精确，建议考虑以下改写版本：\"")
                        .append(escapeXml(rewrittenQuery))
                        .append("\"\n</retrieval_rewrite>");
            }
            return builder.toString();
        }

        if (ragContexts == null || ragContexts.isEmpty()) {
            return systemPrompt != null ? systemPrompt : "";
        }

        boolean hasRag = false;
        for (RAGContext ragCtx : ragContexts) {
            String content = ragCtx.getPreRetrievedContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (!hasRag) {
                builder.append("\n\n<knowledge_base>\n");
                if (retrievalEvaluation != null && retrievalEvaluation.isAmbiguous()) {
                    builder.append("<retrieval_evaluation label=\"AMBIGUOUS\" confidence=\"")
                            .append(retrievalEvaluation.getConfidence())
                            .append("\">")
                            .append(escapeXml(retrievalEvaluation.getReason()))
                            .append("</retrieval_evaluation>\n");
                }
                hasRag = true;
            }
            builder.append("  <document knowledge_id=\"").append(escapeXml(ragCtx.getKnowledgeId()))
                    .append("\" source=\"").append(escapeXml(ragCtx.getKnowledgeName())).append("\">\n")
                    .append(escapeXml(content))
                    .append("\n  </document>\n");
        }
        if (hasRag) {
            builder.append("</knowledge_base>\n")
                    .append("优先依据 <knowledge_base> 中的内容回答；如果内容不足，请明确说明不确定性，不要编造。");
        }
        return builder.toString();
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record PlanTask(String taskId, String objective, String worker, List<String> dependencies) {
    }
}
