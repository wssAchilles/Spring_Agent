package tech.qiantong.qknow.hermes.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作体智能体
 * 包装 ReactAgent，执行具体的子任务
 */
@Slf4j
public class WorkerAgent extends BaseAgent {

    private final String systemPrompt;
    private final List<ToolCallback> tools;
    private final ChatModel chatModel;
    private final ToolCallbackResolver resolver;

    public WorkerAgent(String name, String description, String systemPrompt,
                       List<ToolCallback> tools, ChatModel chatModel, ToolCallbackResolver resolver) {
        super(name, description);
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.chatModel = chatModel;
        this.resolver = resolver;
    }

    @Override
    public String chat(String question, Map<String, Object> context) {
        try {
            List<Message> messages = new ArrayList<>();

            // 追加历史消息
            if (context != null && context.containsKey("history")) {
                @SuppressWarnings("unchecked")
                List<Message> history = (List<Message>) context.get("history");
                messages.addAll(history);
            }

            messages.add(new UserMessage(question));

            ReactAgent reactAgent = createReactAgent();
            Object result = reactAgent.call(messages);

            return extractText(result);
        } catch (Exception e) {
            log.error("WorkerAgent {} 执行失败", getName(), e);
            return "执行失败: " + getName() + " - " + e.getMessage();
        }
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
     * 创建 ReactAgent（public 以便测试跨包 mock）
     */
    public ReactAgent createReactAgent() {
        return ReactAgent.builder()
                .name(getName())
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .tools(tools)
                .resolver(resolver)
                .build();
    }
}
