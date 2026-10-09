package tech.qiantong.qknow.kb.biz.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.service.conversation.ConversationContextCondenser;
import tech.qiantong.qknow.module.kb.service.conversation.ConversationContextCondenser.MessagePayload;
import tech.qiantong.qknow.module.kb.service.conversation.ConversationContextCondenser.CondensedContextResult;
import tech.qiantong.qknow.module.kb.service.conversation.ConversationContextCondenser.CondensationReceipt;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 长程对话滑动窗口与动态语义重凝缩契约测试
 * 验证：DeepSeek 64-token 确定性块对齐、短历史无损透传、长历史滑动窗口与语义凝缩、常量时间自验真
 */
public class ConversationContextCondenserTest {

    private ConversationContextCondenser condenser;

    @BeforeEach
    public void setup() {
        condenser = new ConversationContextCondenser();
    }

    @Test
    @DisplayName("验证短对话历史：消息数未超滑动窗口时全量透传，无损失")
    public void testShortConversationPassThrough() {
        String systemPrompt = "你是企业级 RAG 知识库与智能体助手。请遵守各项安全与工程规范。";
        List<MessagePayload> history = List.of(
                new MessagePayload("user", "你好，请问如何配置工作流？"),
                new MessagePayload("assistant", "您可以在 Bot 画布中拖入各种节点并进行连线配置。"),
                new MessagePayload("user", "支持哪些类型的节点呢？"),
                new MessagePayload("assistant", "支持大模型、知识库、工具调用、条件判断以及回复节点。")
        );
        String currentQuery = "明白了，那工具节点支持 MCP 协议吗？";

        CondensedContextResult result = condenser.condense(systemPrompt, history, currentQuery, 4);

        // 验证：
        // 1. 系统提示词被对齐
        assertNotNull(result.alignedSystemPrompt());
        assertTrue(result.alignedSystemPrompt().startsWith(systemPrompt));

        // 2. 消息未被压缩，保留原始 4 条历史 + 1 条当前查询 = 5 条
        assertEquals(5, result.effectiveMessages().size());
        assertEquals("user", result.effectiveMessages().get(4).role());
        assertEquals(currentQuery, result.effectiveMessages().get(4).content());

        // 3. 审计凭单生成且自验真成功
        CondensationReceipt receipt = result.receipt();
        assertNotNull(receipt);
        assertEquals(5, receipt.rawMessageCount());
        assertEquals(5, receipt.condensedMessageCount());
        assertTrue(receipt.isCacheAligned());
        assertTrue(receipt.verifySignature(receipt.auditSignature()));
    }

    @Test
    @DisplayName("验证超长对话历史：超过滑动窗口时自动提炼早期语义骨架，保留最近关键交互")
    public void testLongConversationCondensation() {
        String systemPrompt = "系统前缀：严格遵循 DeepSeek 思考链与 Context Caching 规范。";
        List<MessagePayload> longHistory = new ArrayList<>();

        // 模拟 10 轮交互 (共 20 条消息，包含真实的充实长答复)
        for (int i = 1; i <= 10; i++) {
            longHistory.add(new MessagePayload("user", "问题-" + i + ": 关于企业级分布式长事务与断点自愈的详细技术方案？"));
            longHistory.add(new MessagePayload("assistant", "回答-" + i + ": 在多智能体异步编排场景下，推荐采用 Saga 异步补偿模式结合不可变状态快照。系统通过记录正向事务执行日志与逆向补偿操作，当发生不可恢复的网络超时或节点崩溃故障时，触发逆向补偿管道依次执行回滚动作，确保最终数据状态一致。同时配合轻量级租约机制防止脑裂与死锁。"));
        }

        String currentQuery = "请问在上述方案中，如何结合 Checkpointing 实现断点自愈？";

        // 滑动窗口设为 3 轮 (最近 6 条消息)
        CondensedContextResult result = condenser.condense(systemPrompt, longHistory, currentQuery, 3);

        List<MessagePayload> effective = result.effectiveMessages();

        // 验证：
        // 1. 有效消息包含：1 条重凝缩摘要 + 6 条最近消息 + 1 条当前提问 = 8 条
        assertEquals(8, effective.size());

        // 2. 第一条消息必须是系统角色注入的早期语义摘要
        assertEquals("system", effective.get(0).role());
        assertTrue(effective.get(0).content().contains("<historical_conversation_summary>"));
        assertTrue(effective.get(0).content().contains("用户前期提问点 #1"));
        assertTrue(effective.get(0).content().contains("问题-1"));
        assertTrue(effective.get(0).content().contains("共识状态：已完成 7 轮早期交互"));

        // 3. 后续 6 条是最近的原汁原味交互 (轮次 8, 9, 10)
        assertEquals("user", effective.get(1).role());
        assertTrue(effective.get(1).content().contains("问题-8"));
        assertEquals("assistant", effective.get(6).role());
        assertTrue(effective.get(6).content().contains("回答-10"));

        // 4. 最后一条是当前提问
        assertEquals("user", effective.get(7).role());
        assertEquals(currentQuery, effective.get(7).content());

        // 5. 凭单中记录的压缩比 < 1.0 (实现了显著上下文压缩与降耗)
        CondensationReceipt receipt = result.receipt();
        assertTrue(receipt.compressionRatio() < 1.0);
        assertTrue(receipt.verifySignature(receipt.auditSignature()));
        assertFalse(receipt.verifySignature("tampered-invalid-sig"));
    }

    @Test
    @DisplayName("验证 64-Token 确定性块对齐：对齐后 Token 估算数必为 64 的整数倍")
    public void testCacheAlignment() {
        String shortPrompt = "Short System Prompt";
        String aligned = condenser.alignTo64TokenBoundary(shortPrompt);

        int estimatedTokens = (int) Math.ceil(aligned.length() / ConversationContextCondenser.CHARS_PER_TOKEN);
        assertEquals(0, estimatedTokens % ConversationContextCondenser.DEEPSEEK_CACHE_BLOCK_TOKENS,
                "对齐后的系统提示词 Token 必须严格满足 64-token 块整除");
    }
}
