package tech.qiantong.qknow.hermes.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.proto.*;
import tech.qiantong.qknow.hermes.tool.function.SearchKnowledgeTool;
import tech.qiantong.qknow.hermes.tool.function.query.knowledgeQuery;
import tech.qiantong.qknow.hermes.util.NodeUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Hermes Agent 编排器
 * 替代原 KbAgentConfigServiceImpl.chatMessage() 中的 ReactAgent 构建和执行逻辑
 */
@Slf4j
@Component
public class AgentOrchestrator {

    private final ChatModelFactory chatModelFactory;
    private final ToolCallbackResolver resolver;

    public AgentOrchestrator(ChatModelFactory chatModelFactory, ToolCallbackResolver resolver) {
        this.chatModelFactory = chatModelFactory;
        this.resolver = resolver;
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

        // 2. 构建知识库工具（使用预检索的 RAG 结果）
        List<ToolCallback> tools = new ArrayList<>();
        for (RAGContext ragCtx : request.getRagContextsList()) {
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

        // 3. 获取工具名称列表，并确保知识库工具可被调用
        List<String> enabledToolNames = new ArrayList<>(request.getToolMethodIdsList());
        for (RAGContext ragCtx : request.getRagContextsList()) {
            enabledToolNames.add("knowledgeBase" + ragCtx.getKnowledgeId());
        }
        String[] toolNames = enabledToolNames.toArray(new String[0]);

        // 4. 构建消息历史
        List<Message> messages = new ArrayList<>();
        for (ChatMessage historyMsg : request.getHistoryList()) {
            if ("user".equals(historyMsg.getRole())) {
                messages.add(new UserMessage(historyMsg.getContent()));
            } else if ("assistant".equals(historyMsg.getRole())) {
                messages.add(new AssistantMessage(historyMsg.getContent()));
            }
        }

        // 5. 构建系统提示词
        String systemPrompt = NodeUtils.replacePlaceholder(request.getSystemPrompt(), request.getInputParams());
        systemPrompt = appendRagContext(systemPrompt, request.getRagContextsList());
        messages.add(new UserMessage(request.getQuestion()));

        // 6. 构建 ReactAgent
        ReactAgent agent = ReactAgent.builder()
                .name("hermes_agent")
                .model(chatModel)
                .hooks(ModelCallLimitHook.builder().runLimit(10).build())
                .systemPrompt(systemPrompt)
                .toolNames(toolNames)
                .tools(tools)
                .resolver(resolver)
                .build();

        // 7. 执行推理并映射为 ChatEvent 流
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

    private String appendRagContext(String systemPrompt, List<RAGContext> ragContexts) {
        if (ragContexts == null || ragContexts.isEmpty()) {
            return systemPrompt != null ? systemPrompt : "";
        }

        StringBuilder builder = new StringBuilder(systemPrompt != null ? systemPrompt : "");
        boolean hasRag = false;
        for (RAGContext ragCtx : ragContexts) {
            String content = ragCtx.getPreRetrievedContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (!hasRag) {
                builder.append("\n\n=== 知识库召回内容 ===\n")
                        .append("以下是知识库中召回的相关内容，请优先依据这些内容回答用户问题。\n")
                        .append("重要规则：\n")
                        .append("1. 如果召回内容与问题相关，请基于这些内容回答，不要说'知识库没有找到'。\n")
                        .append("2. 如果问题涉及特定 Day/文档，请优先使用对应 Day 的内容回答。\n")
                        .append("3. 如果内容部分相关，请说明根据知识库中哪部分内容回答，并补充你的理解。\n")
                        .append("4. 只有当召回内容与问题完全无关时，才说'知识库中没有相关信息'。\n\n");
                hasRag = true;
            }
            builder.append("知识库：").append(ragCtx.getKnowledgeName())
                    .append("（ID=").append(ragCtx.getKnowledgeId()).append("）\n")
                    .append(content)
                    .append("\n\n");
        }
        return builder.toString();
    }
}
