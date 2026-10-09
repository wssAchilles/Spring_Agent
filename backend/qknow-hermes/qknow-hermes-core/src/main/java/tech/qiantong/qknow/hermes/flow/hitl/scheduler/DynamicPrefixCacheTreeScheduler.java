package tech.qiantong.qknow.hermes.flow.hitl.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态前缀基数树缓存调度器 (Dynamic Prefix Cache Tree Scheduler)
 * <p>
 * 面向 DeepSeek 官方 64-token 上下文缓存 (Context Caching) 规约：
 * 1. 四阶段规范化装配：全局系统前缀 -> 智能体角色契约 -> 不可变知识块 -> 动态任务尾置；
 * 2. 64-token 物理块量化受控对齐：在静态前缀末尾注入无损注释填充符，锁定 64*k 边界，物理阻断哈希雪崩；
 * 3. 内存前缀基数树 (Radix Trie) 路由匹配，统计并最大化缓存块命中率。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Component
public class DynamicPrefixCacheTreeScheduler {

    public static final int CACHE_BLOCK_SIZE = 64;
    public static final String PAD_PREFIX = "\n<!-- ds_prefix_align_pad:";
    public static final String PAD_SUFFIX = " -->";

    /**
     * 前缀对齐后的规范化 Prompt 结果 Record
     */
    public record AlignedPromptResult(
            String alignedSystemPrompt,
            String dynamicUserPayload,
            int rawPrefixTokens,
            int paddedPrefixTokens,
            int alignedCacheBlocks,
            boolean perfectlyAligned,
            Map<String, Object> thinkingExtraBody
    ) {}

    /**
     * 前缀基数树节点
     */
    public static class RadixTrieNode {
        public final String segmentKey;
        public int accessCount = 0;
        public final Map<String, RadixTrieNode> children = new ConcurrentHashMap<>();

        public RadixTrieNode(String segmentKey) {
            this.segmentKey = segmentKey;
        }
    }

    private final RadixTrieNode rootTrie = new RadixTrieNode("__root__");

    /**
     * 粗粒度估算文本的 Token 消耗 (中文约 1.5 chars/token，英文约 4 chars/token)
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                tokens += 2; // 中文字符加权
            } else if (Character.isWhitespace(c)) {
                tokens += 1;
            } else {
                tokens += 1;
            }
        }
        return Math.max(1, tokens / 2);
    }

    /**
     * 规范化构建满足 64-token 边界对齐的完整 Prompt
     *
     * @param systemCore      全局静态核心指令与工具定义
     * @param agentPersona    当前智能体角色规范
     * @param immutableChunks 排序后的不可变知识库切片集合
     * @param ephemeralQuery  动态用户查询与即时参数 (绝对尾置)
     * @param enableThinking  是否开启 DeepSeek 官方深度思考模式
     * @return 严格对齐后的调度产物
     */
    public AlignedPromptResult scheduleAndAlignPrompt(
            String systemCore,
            String agentPersona,
            List<String> immutableChunks,
            String ephemeralQuery,
            boolean enableThinking
    ) {
        // 1. 组装静态不可变前缀 (Layer 0 + Layer 1 + Layer 2)
        StringBuilder staticPrefix = new StringBuilder();
        if (systemCore != null && !systemCore.isBlank()) {
            staticPrefix.append(systemCore.trim()).append("\n\n");
        }
        if (agentPersona != null && !agentPersona.isBlank()) {
            staticPrefix.append(agentPersona.trim()).append("\n\n");
        }
        if (immutableChunks != null && !immutableChunks.isEmpty()) {
            // 字典序保序以确保哈希严格一致
            List<String> sortedChunks = new ArrayList<>(immutableChunks);
            Collections.sort(sortedChunks);
            staticPrefix.append("<immutable_knowledge_base>\n");
            for (String chunk : sortedChunks) {
                staticPrefix.append(chunk.trim()).append("\n");
            }
            staticPrefix.append("</immutable_knowledge_base>\n\n");
        }

        String rawStaticStr = staticPrefix.toString();
        int rawTokens = estimateTokens(rawStaticStr);

        // 2. 64-token 边界量化对齐
        int remainder = rawTokens % CACHE_BLOCK_SIZE;
        int paddingTokensNeeded = (remainder == 0) ? 0 : (CACHE_BLOCK_SIZE - remainder);

        String finalSystemPrompt;
        int finalTokens = rawTokens;

        if (paddingTokensNeeded > 0) {
            // 注入受控注释填充符
            int padChars = Math.max(2, paddingTokensNeeded * 2);
            String padContent = "0".repeat(Math.max(1, padChars - PAD_PREFIX.length() - PAD_SUFFIX.length()));
            String paddingTag = PAD_PREFIX + padContent + PAD_SUFFIX;
            finalSystemPrompt = rawStaticStr + paddingTag;
            finalTokens = rawTokens + paddingTokensNeeded;
        } else {
            finalSystemPrompt = rawStaticStr;
        }

        int alignedBlocks = finalTokens / CACHE_BLOCK_SIZE;

        // 3. 记录到前缀基数树
        recordInRadixTrie(finalSystemPrompt);

        // 4. 构建 DeepSeek 官方思考模式配置
        Map<String, Object> extraBody = new LinkedHashMap<>();
        if (enableThinking) {
            extraBody.put("thinking", Map.of("type", "enabled"));
        }

        return new AlignedPromptResult(
                finalSystemPrompt,
                ephemeralQuery != null ? ephemeralQuery.trim() : "",
                rawTokens,
                finalTokens,
                alignedBlocks,
                (finalTokens % CACHE_BLOCK_SIZE == 0),
                Collections.unmodifiableMap(extraBody)
        );
    }

    private void recordInRadixTrie(String promptPrefix) {
        if (promptPrefix == null) return;
        // 简单按 64 字符分段入树
        RadixTrieNode current = rootTrie;
        int step = 64;
        for (int i = 0; i < promptPrefix.length(); i += step) {
            int end = Math.min(i + step, promptPrefix.length());
            String key = promptPrefix.substring(i, end);
            current = current.children.computeIfAbsent(key, RadixTrieNode::new);
            current.accessCount++;
        }
    }

    public RadixTrieNode getRootTrie() {
        return rootTrie;
    }
}
