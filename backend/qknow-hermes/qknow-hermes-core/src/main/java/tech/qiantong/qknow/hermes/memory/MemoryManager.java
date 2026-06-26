package tech.qiantong.qknow.hermes.memory;

import org.springframework.ai.chat.messages.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 三层记忆统一管理门面。
 */
public class MemoryManager {

    private final ShortTermMemory shortTerm;
    private final LongTermMemory longTerm;
    private final WorkingMemory working;

    public MemoryManager(ShortTermMemory shortTerm, LongTermMemory longTerm, WorkingMemory working) {
        this.shortTerm = shortTerm;
        this.longTerm = longTerm;
        this.working = working;
    }

    public ShortTermMemory getShortTerm() {
        return shortTerm;
    }

    public LongTermMemory getLongTerm() {
        return longTerm;
    }

    public WorkingMemory getWorking() {
        return working;
    }

    /**
     * 会话结束时：压缩短期记忆摘要 → 存储到长期记忆 → 清空工作记忆。
     */
    public void onConversationEnd(String sessionId, String userId) {
        shortTerm.summarize(sessionId, 5);

        List<Message> context = shortTerm.getContext(sessionId, Integer.MAX_VALUE);
        String summary = context.isEmpty() ? "" : context.get(0).getText();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("userId", userId);
        longTerm.store(summary, metadata);

        working.clear(sessionId);
    }
}
