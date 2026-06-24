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
import cn.hutool.core.util.StrUtil;
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
    private final RetrievalEvaluator retrievalEvaluator;

    public AgentOrchestrator(ChatModelFactory chatModelFactory, ToolCallbackResolver resolver,
                             RetrievalEvaluator retrievalEvaluator) {
        this.chatModelFactory = chatModelFactory;
        this.resolver = resolver;
        this.retrievalEvaluator = retrievalEvaluator;
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
}
