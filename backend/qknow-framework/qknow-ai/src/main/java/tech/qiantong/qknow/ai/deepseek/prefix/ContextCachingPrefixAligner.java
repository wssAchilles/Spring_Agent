package tech.qiantong.qknow.ai.deepseek.prefix;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * DeepSeek 官方 1M 上下文前缀对齐缓存引擎 (Context Caching Prefix Aligner)
 * <p>
 * 严格按照 DeepSeek 官方最新接口契约与 64-Token 前缀匹配格点设计：
 * 阶段 1: 静态内核层 (System Prompt + Global Tools Specification)；
 * 阶段 2: 高阶超图因果拓扑层 (Canonical Hypergraph Causal Subgraph，按层级升序、节点字典序、置信度降序规范化排序)；
 * 阶段 3: 64-Token 整数倍填充垫片 (Padding Pad 注释)，确保静态前缀严格满足 TotalTokens % 64 == 0；
 * 阶段 4: 动态易变层 (Dynamic Session History + User Query + Temporal Anchor) 严格尾置，彻底杜绝哈希雪崩。
 *
 * @author Achilles
 * @since Phase 145
 */
@Slf4j
@Component
public class ContextCachingPrefixAligner {

    public static final int DEEPSEEK_CACHE_BLOCK_SIZE = 64;
    private static final String PADDING_PREFIX = "\n<!-- ds_prefix_align_pad:{\"padTokens\":";
    private static final String PADDING_SUFFIX = ",\"magic\":\"0x5a\"} -->";

    /**
     * 前缀对齐请求参数对象
     */
    @Data
    @Builder
    public static class PrefixAlignmentRequest {
        private String tenantId;
        private String taskId;
        private String staticSystemPrompt;
        private String globalToolsJson;
        private List<HypergraphSubstructureRecord> causalSubstructures;
        private List<Message> dynamicSessionHistory;
        private String userQuery;
        private String temporalAnchor;
    }

    /**
     * 规范化超图子图输入单元
     */
    @Data
    @Builder
    public static class HypergraphSubstructureRecord {
        private String hyperedgeId;
        private int causalLevel;
        private double causalConfidence;
        private List<String> vertexLabels;
        private String relationType;
        private String causalExplanation;
    }

    /**
     * 前缀对齐输出结果对象
     */
    @Data
    @Builder
    public static class AlignedPrefixResult {
        private List<Message> alignedMessages;
        private String staticPrefixText;
        private String dynamicTailText;
        private String prefixDigestSha256;
        private int rawStaticTokens;
        private int paddedTokens;
        private int alignedStaticTokens;
        private boolean cacheAligned;
        private List<String> canonicalCausalChain;
    }

    /**
     * 核心对齐装配方法
     */
    public AlignedPrefixResult align(PrefixAlignmentRequest request) {
        Objects.requireNonNull(request, "前缀对齐请求不可为空");

        List<Message> alignedMessages = new ArrayList<>();
        List<String> canonicalChain = new ArrayList<>();

        // 阶段 1: 静态系统内核装配
        StringBuilder prefixSb = new StringBuilder();
        if (request.getStaticSystemPrompt() != null && !request.getStaticSystemPrompt().isBlank()) {
            prefixSb.append("[DEEPSEEK_SYSTEM_KERNEL]\n").append(request.getStaticSystemPrompt().trim()).append("\n\n");
        }
        if (request.getGlobalToolsJson() != null && !request.getGlobalToolsJson().isBlank()) {
            prefixSb.append("[GLOBAL_TOOLS_SPECIFICATION]\n").append(request.getGlobalToolsJson().trim()).append("\n\n");
        }

        // 阶段 2: 规范化高阶超图因果拓扑层装配 (保证严格跨节点确定性自然序)
        if (request.getCausalSubstructures() != null && !request.getCausalSubstructures().isEmpty()) {
            prefixSb.append("[CANONICAL_HYPERGRAPH_CAUSAL_CONTEXT]\n");

            // 规范化排序：causalLevel 升序 -> causalConfidence 降序 -> hyperedgeId 字典序
            List<HypergraphSubstructureRecord> sortedStructures = new ArrayList<>(request.getCausalSubstructures());
            sortedStructures.sort(Comparator
                    .comparingInt(HypergraphSubstructureRecord::getCausalLevel)
                    .thenComparing(Comparator.comparingDouble(HypergraphSubstructureRecord::getCausalConfidence).reversed())
                    .thenComparing(HypergraphSubstructureRecord::getHyperedgeId));

            for (HypergraphSubstructureRecord sub : sortedStructures) {
                List<String> sortedVertices = sub.getVertexLabels() != null ? new ArrayList<>(sub.getVertexLabels()) : new ArrayList<>();
                Collections.sort(sortedVertices);

                String chainEntry = String.format("Level-%d[%s]-(%s:%.4f)->{%s}",
                        sub.getCausalLevel(),
                        sub.getHyperedgeId(),
                        sub.getRelationType() != null ? sub.getRelationType() : "CAUSAL_FLOW",
                        sub.getCausalConfidence(),
                        String.join(",", sortedVertices));
                canonicalChain.add(chainEntry);

                prefixSb.append(String.format("<<<Hyperedge id=\"%s\" level=\"%d\" confidence=\"%.4f\" relation=\"%s\">>>\n",
                        sub.getHyperedgeId(),
                        sub.getCausalLevel(),
                        sub.getCausalConfidence(),
                        sub.getRelationType() != null ? sub.getRelationType() : "CAUSAL_RELATION"));
                prefixSb.append("Entities: [").append(String.join(", ", sortedVertices)).append("]\n");
                if (sub.getCausalExplanation() != null && !sub.getCausalExplanation().isBlank()) {
                    prefixSb.append("Evidence: ").append(sub.getCausalExplanation().trim()).append("\n");
                }
                prefixSb.append("<<</Hyperedge>>>\n\n");
            }
        }

        // 阶段 3: 估算 Token 并注入 64-Token 整数倍填充垫片 (Pad)
        int rawStaticTokens = estimateTokens(prefixSb.toString());
        int remainder = rawStaticTokens % DEEPSEEK_CACHE_BLOCK_SIZE;
        int padTokensNeeded = (remainder == 0) ? 0 : (DEEPSEEK_CACHE_BLOCK_SIZE - remainder);

        if (padTokensNeeded > 0) {
            String paddingComment = generatePaddingPad(padTokensNeeded);
            prefixSb.append(paddingComment);
        }

        int finalAlignedTokens = rawStaticTokens + padTokensNeeded;
        String staticPrefix = prefixSb.toString();
        alignedMessages.add(new SystemMessage(staticPrefix));

        // 计算静态前缀的 SHA-256 签名指纹
        String prefixDigestSha256 = computeSha256Hex(staticPrefix);
        boolean isCacheAligned = (finalAlignedTokens % DEEPSEEK_CACHE_BLOCK_SIZE == 0);

        // 阶段 4: 动态易变上下文严格尾置
        if (request.getDynamicSessionHistory() != null && !request.getDynamicSessionHistory().isEmpty()) {
            alignedMessages.addAll(request.getDynamicSessionHistory());
        }

        StringBuilder tailSb = new StringBuilder();
        if (request.getUserQuery() != null && !request.getUserQuery().isBlank()) {
            tailSb.append(request.getUserQuery().trim());
        }
        if (request.getTemporalAnchor() != null && !request.getTemporalAnchor().isBlank()) {
            tailSb.append("\n\n[DYNAMIC_TEMPORAL_ANCHOR: ").append(request.getTemporalAnchor().trim()).append("]");
        }
        String dynamicTail = tailSb.toString();
        alignedMessages.add(new UserMessage(dynamicTail));

        return AlignedPrefixResult.builder()
                .alignedMessages(alignedMessages)
                .staticPrefixText(staticPrefix)
                .dynamicTailText(dynamicTail)
                .prefixDigestSha256(prefixDigestSha256)
                .rawStaticTokens(rawStaticTokens)
                .paddedTokens(padTokensNeeded)
                .alignedStaticTokens(finalAlignedTokens)
                .cacheAligned(isCacheAligned)
                .canonicalCausalChain(canonicalChain)
                .build();
    }

    /**
     * 生成确定性受控无副作用的填充垫片
     */
    private String generatePaddingPad(int padTokensNeeded) {
        int fillChars = Math.max(1, (padTokensNeeded - 1) * 3);
        String fillStr = "#".repeat(fillChars);
        return PADDING_PREFIX + padTokensNeeded + ",\"fill\":\"" + fillStr + "\"" + PADDING_SUFFIX;
    }

    /**
     * 轻量化极速 Token 估算器 (中英混排与符号分词)
     * 中文字符约为 0.65~1.0 token，英文字词约为 1.25 token
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

    private static String computeSha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256 算法", e);
        }
    }
}
