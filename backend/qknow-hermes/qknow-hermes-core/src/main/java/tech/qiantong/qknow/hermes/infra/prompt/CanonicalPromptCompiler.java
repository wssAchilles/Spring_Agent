package tech.qiantong.qknow.hermes.infra.prompt;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 规范化 Prompt 编译器与 64-Token 前缀树边界对齐引擎
 * 专门适配 DeepSeek 官方 API Context Caching 经济性契约与 RadixAttention 缓存机制
 * 
 * 核心设计：
 * 1. 静态基座规则冻结：消除系统级指令抖动；
 * 2. 字典序确定性工具契约：工具按名称字典序严格升序排列，杜绝哈希无序性；
 * 3. 稳定排序上下文：RAG 召回知识块依据评分与唯一 ID 执行二级确定性排序；
 * 4. 64-Token 边界规整填充：在前缀与动态对话接缝处对齐至 64 的正整数倍，最大化 Context Cache 命中率；
 * 5. 纯不可变凭单与常量时间比对验签。
 */
public class CanonicalPromptCompiler {

    private static final String DEFAULT_SYSTEM_INSTRUCTION = """
            # Role & System Governance Codex
            You are Hermes, an enterprise-grade AI cognitive orchestration agent.
            All generated responses must follow strict factual correctness and verified knowledge boundaries.
            Mathematical geometry calculations are bound to Qwen 1536-dimensional hyperspherical unit space.
            DeepSeek thinking process is structured via official reasoning_content.
            """;

    private final String frozenSystemInstruction;

    public CanonicalPromptCompiler() {
        this(DEFAULT_SYSTEM_INSTRUCTION);
    }

    public CanonicalPromptCompiler(String customSystemInstruction) {
        this.frozenSystemInstruction = (customSystemInstruction != null && !customSystemInstruction.isBlank())
                ? customSystemInstruction.strip()
                : DEFAULT_SYSTEM_INSTRUCTION.strip();
    }

    /**
     * 知识库片段实体
     */
    public record KnowledgeChunk(
            String id,
            String title,
            String content,
            double relevanceScore
    ) implements Comparable<KnowledgeChunk> {
        @Override
        public int compareTo(KnowledgeChunk other) {
            // 优先按相关度降序，若相关度差异小于 1e-4 则强制按 ID 升序以保证确定性
            int scoreCmp = Double.compare(other.relevanceScore, this.relevanceScore);
            if (Math.abs(other.relevanceScore - this.relevanceScore) < 1e-4) {
                return this.id.compareTo(other.id);
            }
            return scoreCmp;
        }
    }

    /**
     * 结构化工具定义
     */
    public record ToolDefinition(
            String name,
            String description,
            String inputSchemaJson
    ) implements Comparable<ToolDefinition> {
        @Override
        public int compareTo(ToolDefinition other) {
            return this.name.compareTo(other.name);
        }
    }

    /**
     * 执行完整编译管道并签发不可变凭单
     *
     * @param tools           候选工具列表
     * @param retrievedChunks 召回知识切片
     * @param dynamicUserTurn 动态用户即时提问或多轮历史
     * @return 编译凭单
     */
    public CanonicalPromptReceipt compile(
            List<ToolDefinition> tools,
            List<KnowledgeChunk> retrievedChunks,
            String dynamicUserTurn
    ) {
        long startNanos = System.nanoTime();

        StringBuilder prefixBuilder = new StringBuilder();

        // 1. Layer 1: 静态基座规则层
        prefixBuilder.append("=== SYSTEM INSTRUCTION ===\n");
        prefixBuilder.append(frozenSystemInstruction).append("\n\n");

        // 2. Layer 2: 字典序确定性工具契约层
        List<ToolDefinition> sortedTools = new ArrayList<>(tools != null ? tools : List.of());
        Collections.sort(sortedTools);
        prefixBuilder.append("=== AVAILABLE TOOLS (CANONICAL CATALOG) ===\n");
        if (sortedTools.isEmpty()) {
            prefixBuilder.append("None.\n\n");
        } else {
            for (ToolDefinition tool : sortedTools) {
                prefixBuilder.append(String.format("- Tool[%s]: %s | Schema: %s\n",
                        tool.name(), tool.description(), tool.inputSchemaJson().replaceAll("\\s+", " ").trim()));
            }
            prefixBuilder.append("\n");
        }

        // 3. Layer 3: 拓扑与 ID 稳定排序知识层
        List<KnowledgeChunk> sortedChunks = new ArrayList<>(retrievedChunks != null ? retrievedChunks : List.of());
        Collections.sort(sortedChunks);
        prefixBuilder.append("=== RETRIEVED KNOWLEDGE CONTEXT ===\n");
        if (sortedChunks.isEmpty()) {
            prefixBuilder.append("No reference documents.\n\n");
        } else {
            for (int i = 0; i < sortedChunks.size(); i++) {
                KnowledgeChunk chunk = sortedChunks.get(i);
                prefixBuilder.append(String.format("### [Doc %d] id=%s title=%s score=%.4f\n```text\n%s\n```\n\n",
                        i + 1, chunk.id(), chunk.title(), chunk.relevanceScore(), chunk.content().trim()));
            }
        }

        String rawPrefix = prefixBuilder.toString();
        int estimatedPrefixTokens = estimateTokens(rawPrefix);

        // 4. Layer 4: 64-Token 边界规整填充器
        // 确保前缀 Token 满足 64 的正整数倍对齐
        int remainder = estimatedPrefixTokens % 64;
        int paddingTokensNeeded = (remainder == 0) ? 0 : (64 - remainder);

        String paddingBlock = "";
        if (paddingTokensNeeded > 0) {
            paddingBlock = generateDeterministicPadding(paddingTokensNeeded);
        }

        String alignedPrefix = rawPrefix + paddingBlock;

        // 5. Layer 5: 动态对话层拼接
        StringBuilder fullPromptBuilder = new StringBuilder(alignedPrefix);
        fullPromptBuilder.append("=== DYNAMIC USER CONVERSATION ===\n");
        if (dynamicUserTurn != null && !dynamicUserTurn.isBlank()) {
            fullPromptBuilder.append(dynamicUserTurn.strip()).append("\n");
        }

        long durationNanos = System.nanoTime() - startNanos;

        return CanonicalPromptReceipt.create(
                fullPromptBuilder.toString(),
                estimatedPrefixTokens,
                paddingTokensNeeded,
                sortedChunks.size(),
                sortedTools.size(),
                durationNanos
        );
    }

    /**
     * 高精度轻量 Token 估算器
     * 对英文字词符号与 CJK 中文字符进行精确加权
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int cjkCount = 0;
        int nonCjkCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                cjkCount++;
            } else {
                nonCjkCount++;
            }
        }

        // CJK 字符通常平均每个字约 0.625 个 token (5 个字 约 3~4 tokens)
        // 英文及符号通常约 3.8 个字符折算 1 个 token
        double estimated = (cjkCount * 0.65) + (nonCjkCount / 3.8);
        return Math.max(1, (int) Math.round(estimated));
    }

    /**
     * 生成确定性的 64-Token 边界对齐 Padding 注释
     */
    private String generateDeterministicPadding(int targetTokens) {
        // 每个重复单元约 1 个 token (例如 " #pad")
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- CANONICAL_64_TOKEN_BOUNDARY_ALIGNMENT_PAD: ");
        int padChars = Math.max(4, (int) Math.round(targetTokens * 3.8));
        for (int i = 0; i < padChars; i++) {
            sb.append('~');
        }
        sb.append(" -->\n\n");
        return sb.toString();
    }
}
