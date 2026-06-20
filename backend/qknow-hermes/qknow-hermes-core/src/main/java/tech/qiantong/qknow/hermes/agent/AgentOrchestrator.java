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

        // 3. 获取工具名称列表
        String[] toolNames = request.getToolMethodIdsList().toArray(new String[0]);

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
}
