package tech.qiantong.qknow;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.qiantong.qknow.hermes.memory.MemoryManager;

import java.util.List;
import java.util.Map;

@SpringBootTest(classes = tech.qiantong.qknow.server.QKnowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ManualMemoryTriggerTest {

    @Autowired
    private MemoryManager memoryManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testManualConsolidationFromDb() {
        System.out.println("====== 开始执行手动记忆打捞脚本 ======");

        // 1. 从 chat_message 表里捞出 conversation_id = 1 的对话
        String sql = "SELECT role, content FROM chat_message WHERE conversation_id = 1 ORDER BY id ASC LIMIT 50";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        if (rows.isEmpty()) {
            System.out.println("未找到对话记录！");
            return;
        }

        System.out.println("成功捞取到 " + rows.size() + " 条历史聊天记录。");

        // 2. 将记录塞入短时记忆层 (ShortTermMemory)
        for (Map<String, Object> row : rows) {
            String role = (String) row.get("role");
            String content = (String) row.get("content");
            if ("user".equalsIgnoreCase(role)) {
                memoryManager.getShortTerm().addMessage("manual_session_1", new UserMessage(content));
            } else if ("assistant".equalsIgnoreCase(role)) {
                memoryManager.getShortTerm().addMessage("manual_session_1", new AssistantMessage(content));
            }
        }

        // 3. 核心触发：模拟 Sleep-time 判定会话结束，执行摘要提纯并入库
        System.out.println("正在调用 LLM 进行压缩提纯，并写入 PostgreSQL vector_store...");
        memoryManager.onConversationEnd("manual_session_1", "user_1", "user:1");

        System.out.println("====== 打捞完成！请去 vector_store 查看最新记录 ======");
    }
}
