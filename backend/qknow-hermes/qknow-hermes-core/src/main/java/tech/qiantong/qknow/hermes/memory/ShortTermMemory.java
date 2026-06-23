package tech.qiantong.qknow.hermes.memory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话上下文短期记忆管理器。
 */
public class ShortTermMemory {

    private final ChatModel chatModel;
    private final List<Message> messages;

    public ShortTermMemory(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.messages = new ArrayList<>();
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    /**
     * 返回最后 maxMessages 条消息。
     */
    public List<Message> getContext(int maxMessages) {
        int fromIndex = Math.max(0, messages.size() - maxMessages);
        return new ArrayList<>(messages.subList(fromIndex, messages.size()));
    }

    public int size() {
        return messages.size();
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
}
