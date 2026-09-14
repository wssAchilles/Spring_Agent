package tech.qiantong.qknow.ai.deepseek.prefix;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * DeepSeek 官方 64-Token 前缀缓存哈希对齐规整器 (PrefixCacheAligner)
 *
 * 严格遵从四阶段装配：
 * 阶段 1: 静态 System Prompt + Tools 定义绝对前置；
 * 阶段 2: 估算 Token 并注入无损受控注释 Padding 填充补齐至 64 * k 整数倍 (Theorem 1.1)；
 * 阶段 3: 租户不可变元数据与高频召回静态切片前置（自然序升序保持确定性）；
 * 阶段 4: 动态 Query、会话历史与当前时间戳严格尾置，彻底阻断哈希雪崩。
 *
 * @author qknow
 */
@Slf4j
@Component
public class PrefixCacheAligner {

    public static final int DEEPSEEK_CACHE_BLOCK_SIZE = 64;
    private static final String PADDING_PREFIX = "\n<!-- ds_prefix_align_pad:";
    private static final String PADDING_SUFFIX = " -->";

    @Data
    @Builder
    public static class AlignedPromptPayload {
        private List<Message> alignedMessages;
        private int estimatedStaticTokens;
        private int paddedTokens;
        private int targetAlignedTokens;
        private String staticPrefixText;
        private String dynamicTailText;
    }

    /**
     * 四阶段前缀对齐装配
     */
    public AlignedPromptPayload align(
            String staticSystemPrompt,
            String toolDefinitionsJson,
            List<String> immutableRecallDocs,
            List<Message> dynamicSessionContext,
            String userQuery,
            String currentTimestampText) {

        List<Message> result = new ArrayList<>();

        // 阶段 1: 静态核心层装配
        StringBuilder staticSb = new StringBuilder();
        if (staticSystemPrompt != null && !staticSystemPrompt.isBlank()) {
            staticSb.append(staticSystemPrompt.trim());
        }
        if (toolDefinitionsJson != null && !toolDefinitionsJson.isBlank()) {
            staticSb.append("\n\n[GLOBAL_TOOLS_SPECIFICATION]\n").append(toolDefinitionsJson.trim());
        }

        // 阶段 2: 估算并对齐至 64 的整数倍
        int rawStaticTokens = estimateTokens(staticSb.toString());
        int remainder = rawStaticTokens % DEEPSEEK_CACHE_BLOCK_SIZE;
        int padTokensNeeded = (remainder == 0) ? 0 : (DEEPSEEK_CACHE_BLOCK_SIZE - remainder);

        if (padTokensNeeded > 0) {
            String paddingComment = generatePaddingComment(padTokensNeeded);
            staticSb.append(paddingComment);
        }

        int finalStaticTokens = rawStaticTokens + padTokensNeeded;
        String staticPrefix = staticSb.toString();
        result.add(new SystemMessage(staticPrefix));

        // 阶段 3: 静态不可变切片装配并对齐
        if (immutableRecallDocs != null && !immutableRecallDocs.isEmpty()) {
            StringBuilder docSb = new StringBuilder();
            docSb.append("[RETRIEVED_IMMUTABLE_KNOWLEDGE_CONTEXT]\n");
            List<String> sortedDocs = new ArrayList<>(immutableRecallDocs);
            Collections.sort(sortedDocs); // 保证顺序确定性
            for (int i = 0; i < sortedDocs.size(); i++) {
                docSb.append(String.format("<<<Doc_%d>>>\n%s\n\n", i + 1, sortedDocs.get(i).trim()));
            }

            int docTokens = estimateTokens(docSb.toString());
            int docRemainder = docTokens % DEEPSEEK_CACHE_BLOCK_SIZE;
            int docPad = (docRemainder == 0) ? 0 : (DEEPSEEK_CACHE_BLOCK_SIZE - docRemainder);
            if (docPad > 0) {
                docSb.append(generatePaddingComment(docPad));
            }
            result.add(new SystemMessage(docSb.toString()));
        }

        // 阶段 4: 动态易变层严格尾置
        if (dynamicSessionContext != null && !dynamicSessionContext.isEmpty()) {
            result.addAll(dynamicSessionContext);
        }

        StringBuilder dynamicTailSb = new StringBuilder();
        dynamicTailSb.append(userQuery.trim());
        if (currentTimestampText != null && !currentTimestampText.isBlank()) {
            dynamicTailSb.append("\n\n[DYNAMIC_TEMPORAL_ANCHOR: ").append(currentTimestampText.trim()).append("]");
        }
        String dynamicTail = dynamicTailSb.toString();
        result.add(new UserMessage(dynamicTail));

        return AlignedPromptPayload.builder()
                .alignedMessages(result)
                .estimatedStaticTokens(rawStaticTokens)
                .paddedTokens(padTokensNeeded)
                .targetAlignedTokens(finalStaticTokens)
                .staticPrefixText(staticPrefix)
                .dynamicTailText(dynamicTail)
                .build();
    }

    private String generatePaddingComment(int padTokensNeeded) {
        int approxChars = Math.max(1, padTokensNeeded * 3);
        String fill = "0".repeat(approxChars);
        return PADDING_PREFIX + fill + PADDING_SUFFIX;
    }

    /**
     * 极速 Token 估算：中文约 0.65 token/字，英文约 1.3 token/词
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        int wordChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FA5) {
                if (wordChars > 0) {
                    count += Math.max(1, (int) Math.ceil(wordChars * 0.35));
                    wordChars = 0;
                }
                count++;
            } else if (Character.isWhitespace(c) || isPunctuation(c)) {
                if (wordChars > 0) {
                    count += Math.max(1, (int) Math.ceil(wordChars * 0.35));
                    wordChars = 0;
                }
                count++;
            } else {
                wordChars++;
            }
        }
        if (wordChars > 0) {
            count += Math.max(1, (int) Math.ceil(wordChars * 0.35));
        }
        return Math.max(1, count);
    }

    private static boolean isPunctuation(char c) {
        return (c >= 33 && c <= 47) || (c >= 58 && c <= 64) || (c >= 91 && c <= 96) || (c >= 123 && c <= 126);
    }
}
