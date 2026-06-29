package tech.qiantong.qknow.hermes.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 三层记忆统一管理门面。
 * 增强：Scope 组织 + 重要性评分 + Consolidation
 */
@Slf4j
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
        onConversationEnd(sessionId, userId, "default");
    }

    /**
     * 会话结束时：压缩短期记忆摘要 → 存储到长期记忆（带 Scope） → 清空工作记忆。
     */
    public void onConversationEnd(String sessionId, String userId, String scope) {
        shortTerm.summarize(sessionId, 5);

        List<Message> context = shortTerm.getContext(sessionId, Integer.MAX_VALUE);
        String summary = context.isEmpty() ? "" : context.get(0).getText();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("userId", userId);
        metadata.put("scope", scope);
        metadata.put("created_at", System.currentTimeMillis());
        metadata.put("importance", 0.5); // 默认重要性
        longTerm.store(summary, metadata);

        working.clear(sessionId);
        log.debug("Conversation ended: sessionId={}, scope={}, summary_length={}", sessionId, scope, summary.length());
    }

    /**
     * 存储重要记忆（高重要性分数，不易被遗忘）
     */
    public void storeImportant(String content, String sessionId, String userId, String scope, double importance) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("userId", userId);
        metadata.put("scope", scope);
        metadata.put("created_at", System.currentTimeMillis());
        metadata.put("importance", Math.min(1.0, Math.max(0.0, importance)));
        longTerm.store(content, metadata);
    }

    /**
     * 按 Scope 召回长期记忆
     */
    public List<Document> recallByScope(String query, String scope, int topK) {
        return longTerm.recall(query, topK, scope);
    }
}
