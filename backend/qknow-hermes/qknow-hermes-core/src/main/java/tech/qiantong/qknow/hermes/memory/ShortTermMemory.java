package tech.qiantong.qknow.hermes.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.redis.service.IRedisService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话上下文短期记忆管理器。
 */
public class ShortTermMemory {

    private final ChatModel chatModel;
    private final List<Message> messages;
    private final IRedisService redisService;

    public ShortTermMemory(ChatModel chatModel) {
        this(chatModel, null);
    }

    public ShortTermMemory(ChatModel chatModel, IRedisService redisService) {
        this.chatModel = chatModel;
        this.messages = new ArrayList<>();
        this.redisService = redisService;
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public void addMessage(String sessionId, Message message) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            addMessage(message);
            return;
        }
        redisService.rightPush(redisKey(sessionId), encode(message));
    }

    /**
     * 返回最后 maxMessages 条消息。
     */
    public List<Message> getContext(int maxMessages) {
        int fromIndex = Math.max(0, messages.size() - maxMessages);
        return new ArrayList<>(messages.subList(fromIndex, messages.size()));
    }

    public List<Message> getContext(String sessionId, int maxMessages) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            return getContext(maxMessages);
        }
        Long size = redisService.getListSize(redisKey(sessionId));
        if (size == null || size <= 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, size.intValue() - maxMessages);
        List<String> encoded = redisService.range(redisKey(sessionId), fromIndex, size.intValue() - 1);
        return encoded == null ? List.of() : encoded.stream().map(this::decode).collect(Collectors.toList());
    }

    public int size() {
        return messages.size();
    }

    public int size(String sessionId) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            return size();
        }
        Long size = redisService.getListSize(redisKey(sessionId));
        return size == null ? 0 : size.intValue();
    }

    /**
     * 如果消息数超过 keepLastN，调用 LLM 生成摘要，替换为 SystemMessage。
     */
    public void summarize(int keepLastN) {
        if (messages.size() <= keepLastN) {
            return;
        }

        int splitIndex = messages.size() - keepLastN;
        List<Message> oldMessages = messages.subList(0, splitIndex);

        String conversationText = oldMessages.stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n"));

        ChatResponse response = chatModel.call(new Prompt(conversationText));
        String summary = response.getResult().getOutput().getText();

        List<Message> retained = new ArrayList<>(messages.subList(splitIndex, messages.size()));

        messages.clear();
        messages.add(new SystemMessage("以下是之前对话的摘要：" + summary));
        messages.addAll(retained);
    }

    public void summarize(String sessionId, int keepLastN) {
        if (redisService == null || sessionId == null || sessionId.isBlank()) {
            summarize(keepLastN);
            return;
        }
        List<Message> context = getContext(sessionId, Integer.MAX_VALUE);
        if (context.size() <= keepLastN) {
            return;
        }

        int splitIndex = context.size() - keepLastN;
        String conversationText = context.subList(0, splitIndex).stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n"));

        ChatResponse response = chatModel.call(new Prompt(conversationText));
        String summary = response.getResult().getOutput().getText();

        String key = redisKey(sessionId);
        redisService.delete(key);
        redisService.rightPush(key, encode(new SystemMessage("以下是之前对话的摘要：" + summary)));
        for (Message retained : context.subList(splitIndex, context.size())) {
            redisService.rightPush(key, encode(retained));
        }
    }

    private String redisKey(String sessionId) {
        return "memory:short:" + sessionId;
    }

    private String encode(Message message) {
        return message.getMessageType().name() + "\t" + message.getText();
    }

    private Message decode(String value) {
        if (value == null) {
            return new SystemMessage("");
        }
        int split = value.indexOf('\t');
        String type = split >= 0 ? value.substring(0, split) : "SYSTEM";
        String text = split >= 0 ? value.substring(split + 1) : value;
        return switch (type) {
            case "USER" -> new UserMessage(text);
            case "ASSISTANT" -> new AssistantMessage(text);
            default -> new SystemMessage(text);
        };
    }
}
