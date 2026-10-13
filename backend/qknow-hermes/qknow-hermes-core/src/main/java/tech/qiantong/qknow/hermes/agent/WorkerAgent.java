package tech.qiantong.qknow.hermes.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import tech.qiantong.qknow.hermes.tool.discovery.ToolDiscoveryService;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpToolProjectionReceipt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 工作体智能体
 * 包装 ReactAgent，执行具体的子任务，支持千问 1536 维超球面动态工具投影与 Safe Schema 剪枝绑定
 */
@Slf4j
public class WorkerAgent extends BaseAgent {

    private final String systemPrompt;
    @Getter
    private final List<ToolCallback> tools;
    private final ChatModel chatModel;
    private final ToolCallbackResolver resolver;
    private final ToolDiscoveryService toolDiscoveryService;

    public WorkerAgent(String name, String description, String systemPrompt,
                       List<ToolCallback> tools, ChatModel chatModel, ToolCallbackResolver resolver) {
        this(name, description, systemPrompt, tools, chatModel, resolver, null);
    }

    public WorkerAgent(String name, String description, String systemPrompt,
                       List<ToolCallback> tools, ChatModel chatModel, ToolCallbackResolver resolver,
                       ToolDiscoveryService toolDiscoveryService) {
        super(name, description);
        this.systemPrompt = systemPrompt;
        this.tools = tools != null ? List.copyOf(tools) : List.of();
        this.chatModel = chatModel;
        this.resolver = resolver;
        this.toolDiscoveryService = toolDiscoveryService;
    }

    /**
     * 派生携带新工具集的工作体智能体
     */
    public WorkerAgent withTools(List<ToolCallback> newTools) {
        return new WorkerAgent(getName(), getDescription(), systemPrompt, newTools, chatModel, resolver, toolDiscoveryService);
    }

    @Override
    public String chat(String question, Map<String, Object> context) {
        try {
            List<Message> messages = new ArrayList<>();

            // 追加历史消息
            if (context != null && context.containsKey("history")) {
                @SuppressWarnings("unchecked")
                List<Message> history = (List<Message>) context.get("history");
                if (history != null) {
                    messages.addAll(history);
                }
            }
            String previousResults = formatPreviousResults(context);
            if (!previousResults.isBlank()) {
                messages.add(new UserMessage("已有前置子任务结果：\n" + previousResults));
            }

            messages.add(new UserMessage(question));

            // 前置动态意图投影决策
            List<ToolCallback> activeTools = resolveActiveTools(question, context);

            ReactAgent reactAgent = createReactAgent(activeTools);
            Object result = reactAgent.call(messages);

            return extractText(result);
        } catch (Exception e) {
            log.error("WorkerAgent {} 执行失败", getName(), e);
            return "执行失败: " + getName() + " - " + e.getMessage();
        }
    }

    /**
     * 根据当前意图与上下文动态投影工具集
     */
    private List<ToolCallback> resolveActiveTools(String question, Map<String, Object> context) {
        if (toolDiscoveryService != null && context != null && context.containsKey("queryEmbedding")) {
            Object embObj = context.get("queryEmbedding");
            if (embObj instanceof float[] queryEmb) {
                ToolDiscoveryService.DynamicToolSet dynamicSet =
                        toolDiscoveryService.discoverProjectedTools(question, queryEmb, 5);

                // 回填不可变存证凭单
                context.put("mcpToolProjectionReceipt", dynamicSet.projectionReceipt());

                List<ToolCallback> combined = new ArrayList<>(this.tools);
                combined.addAll(dynamicSet.activeToolCallbacks());
                return Collections.unmodifiableList(combined);
            }
        }
        return this.tools;
    }

    private String formatPreviousResults(Map<String, Object> context) {
        if (context == null || !context.containsKey("previousResults")) {
            return "";
        }
        Object value = context.get("previousResults");
        if (!(value instanceof Map<?, ?> results) || results.isEmpty()) {
            return "";
        }
        return results.entrySet().stream()
                .map(entry -> "## " + entry.getKey() + "\n" + entry.getValue())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    /**
     * 从调用结果中提取文本
     * ReactAgent.call() 返回 AssistantMessage，但测试中可能 mock 为 ChatResponse
     */
    private String extractText(Object result) {
        if (result instanceof AssistantMessage am) {
            return am.getText();
        } else if (result instanceof ChatResponse cr) {
            if (cr.getResult() != null && cr.getResult().getOutput() != null) {
                return cr.getResult().getOutput().getText();
            }
            return "";
        } else if (result != null) {
            return result.toString();
        }
        return "";
    }

    /**
     * 创建 ReactAgent（兼容默认工具集，public 以便测试跨包 mock）
     */
    public ReactAgent createReactAgent() {
        return createReactAgent(this.tools);
    }

    /**
     * 基于动态工具集创建当次 ReactAgent 实例
     */
    public ReactAgent createReactAgent(List<ToolCallback> activeTools) {
        return ReactAgent.builder()
                .name(getName())
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .tools(activeTools != null ? activeTools : List.of())
                .resolver(resolver)
                .build();
    }
}
